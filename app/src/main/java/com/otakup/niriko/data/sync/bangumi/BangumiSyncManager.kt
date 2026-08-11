package com.otakup.niriko.data.sync.bangumi

import androidx.room.withTransaction
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.remote.bangumi.BangumiAuthApi
import com.otakup.niriko.data.remote.bangumi.UpdateCollectionRequest
import com.otakup.niriko.data.remote.bangumi.dto.UserCollectionDto
import com.otakup.niriko.data.remote.bangumi.dto.toEntity
import com.otakup.niriko.data.settings.SettingsDataStore
import com.otakup.niriko.data.sync.bangumi.BangumiStatusMapper.toBangumiType
import com.otakup.niriko.data.sync.bangumi.BangumiStatusMapper.toWatchStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException

/** 同步结果摘要。 */
data class BangumiSyncResult(
    val success: Boolean,
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val match: Int = 0,
    val failed: Int = 0,
    val message: String = "",
)

/**
 * Bangumi 账号收藏双向同步引擎。
 *
 * 流程(参照 Kazumi lib/services/sync/bangumi_sync_service.dart):
 * 1. 校验 token → GET /v0/me 拿 username(401 → 「Token 失效」);
 * 2. 全量分页拉取 5 种状态收藏(每页 100,间隔 250ms 限速);
 * 3. BangumiSyncPlanner 生成合并计划;
 * 4. remoteOnly → 占位 Subject + 收藏落库;conflict → 按优先级裁定;localOnly → 上传;
 * 5. 汇总 BangumiSyncResult,失败计数不中断。
 *
 * 安全语义:本期只做「新增/更新/覆盖」,不做删除同步(远端已删的本地条目保留)。
 */
class BangumiSyncManager(
    private val authApi: BangumiAuthApi,
    private val database: NirikoDatabase,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
    private val settings: SettingsDataStore,
) {
    /** 进度回调:阶段文案 + 当前进度 + 总数(null 表示不确定)。 */
    fun interface ProgressCallback {
        fun onProgress(message: String, current: Int, total: Int?)
    }

    private val mutex = Mutex()

    /** 串行执行,防止同步与单项操作并发(参照 Kazumi _runExclusive)。 */
    suspend fun syncOnce(onProgress: ProgressCallback? = null): BangumiSyncResult = mutex.withLock {
        val s = settings.settings.first()
        val token = s.bangumiAccessToken
        if (token.isBlank()) {
            return BangumiSyncResult(success = false, message = "请先填写 Bangumi Access Token")
        }
        if (!s.bangumiSyncEnabled) {
            return BangumiSyncResult(success = false, message = "Bangumi 同步已关闭")
        }

        // 1. 校验 token
        onProgress?.onProgress("正在校验 Token…", 0, null)
        val username = try {
            authApi.me().username.ifBlank {
                return BangumiSyncResult(success = false, message = "Token 校验失败:未返回用户名")
            }
        } catch (e: HttpException) {
            if (e.code() == 401) {
                return BangumiSyncResult(success = false, message = "登录已失效,请重新获取 Token(401)")
            }
            return BangumiSyncResult(success = false, message = "Token 校验失败:${e.code()}")
        } catch (e: Exception) {
            return BangumiSyncResult(success = false, message = "网络错误:${e.message}")
        }
        if (username != s.bangumiUsername) {
            settings.setBangumiUsername(username)
        }

        // 2. 分页拉取 5 种状态
        val remote = mutableListOf<UserCollectionDto>()
        var failed = 0
        for (type in 1..5) {
            var offset = 0
            while (true) {
                if (offset > 0) delay(250) // 限速,防止 429
                val page = try {
                    authApi.collections(username, type = type, limit = 100, offset = offset)
                } catch (e: HttpException) {
                    if (e.code() == 401) {
                        return BangumiSyncResult(success = false, message = "登录已失效,请重新获取 Token(401)")
                    }
                    failed++
                    break
                } catch (e: Exception) {
                    failed++
                    break
                }
                remote += page.data
                val total = page.total
                onProgress?.onProgress("正在拉取收藏(type=$type)…", remote.size, total)
                if (page.data.isEmpty() || offset + page.data.size >= total) break
                offset += page.data.size
            }
        }

        // 3. 合并计划
        val local = collectionDao.getAll()
        val priority = s.bangumiSyncPriority
        val plan = BangumiSyncPlanner.plan(local, remote, priority)
        val totalOps = plan.totalOperations
        if (totalOps == 0) {
            onProgress?.onProgress("未发现状态差异,无需同步", 0, 0)
            return BangumiSyncResult(success = true, match = plan.match, message = "无需同步,完全一致")
        }

        var uploaded = 0
        var downloaded = 0
        var failedOps = 0

        // 4.1 remoteOnly → 落库(先确保 Subject 存在再写收藏)
        for ((i, dl) in plan.remoteOnly.withIndex()) {
            onProgress?.onProgress("正在补全本地缺失状态", i, totalOps)
            try {
                database.withTransaction {
                    ensureSubject(dl.remote)
                    collectionDao.insert(dl.remote.toCollectionEntity())
                }
                downloaded++
            } catch (e: Exception) {
                failedOps++
            }
        }

        // 4.2 conflict → 按优先级:本地优先 → 上传;Bangumi 优先 → 更新本地
        val base = plan.remoteOnly.size
        for ((i, c) in plan.conflict.withIndex()) {
            onProgress?.onProgress(
                if (priority == BangumiSyncPriority.LOCAL_FIRST) "本地优先:正在处理冲突状态" else "Bangumi优先:正在处理冲突状态",
                base + i,
                totalOps,
            )
            if (priority == BangumiSyncPriority.LOCAL_FIRST) {
                if (uploadTo(c.subjectId, c.localStatus.toBangumiType())) uploaded++ else failedOps++
            } else {
                try {
                    database.withTransaction {
                        c.remote.subject?.let { subjectDao.upsert(it.toEntity()) }
                        collectionDao.insert(c.remote.toCollectionEntity())
                    }
                    downloaded++
                } catch (e: Exception) {
                    failedOps++
                }
            }
        }

        // 4.3 localOnly → 上传
        val base2 = base + plan.conflict.size
        for ((i, u) in plan.localOnly.withIndex()) {
            onProgress?.onProgress("正在上传本地新增状态", base2 + i, totalOps)
            if (uploadTo(u.subjectId, u.type)) uploaded++ else failedOps++
        }

        onProgress?.onProgress("Bangumi 状态同步完成", totalOps, totalOps)
        val ok = failedOps == 0 && failed == 0
        return BangumiSyncResult(
            success = ok,
            uploaded = uploaded,
            downloaded = downloaded,
            match = plan.match,
            failed = failed + failedOps,
            message = if (ok) "同步完成:上传 $uploaded / 下载 $downloaded / 一致 ${plan.match}"
            else "同步完成(部分失败):上传 $uploaded / 下载 $downloaded / 失败 ${failed + failedOps}",
        )
    }

    /** 校验登录并返回用户名(设置页「登录 Bangumi」按钮用)。 */
    suspend fun fetchUsername(): String {
        val token = settings.settings.first().bangumiAccessToken
        check(token.isNotBlank()) { "请先填写 Bangumi Access Token" }
        return try {
            authApi.me().username
        } catch (e: HttpException) {
            if (e.code() == 401) throw IllegalStateException("Token 已失效,请重新获取(401)")
            throw IllegalStateException("登录失败:HTTP ${e.code()}")
        } catch (e: Exception) {
            throw IllegalStateException("登录失败:${e.message}")
        }
    }

    private suspend fun uploadTo(subjectId: Long, type: Int): Boolean = try {
        // PUT 为主;502/405 → 退化 POST(见 BangumiAuthApi 注释)
        authApi.updateCollection(subjectId, UpdateCollectionRequest(type = type))
        true
    } catch (e: HttpException) {
        if (e.code() == 502 || e.code() == 405) {
            try {
                authApi.createCollection(subjectId, UpdateCollectionRequest(type = type))
                true
            } catch (e2: Exception) {
                false
            }
        } else {
            false
        }
    } catch (e: Exception) {
        false
    }

    /** 确保 Subject 存在(远端内嵌 → 占位,后续可由详情接口补全)。 */
    private suspend fun ensureSubject(dto: UserCollectionDto) {
        val subject = dto.subject ?: return
        if (subjectDao.getById(dto.subjectId) == null) {
            subjectDao.upsert(subject.toEntity())
        }
    }

    /** 远端收藏 → 本地收藏实体(状态已被 Planner 换算为本地语义)。 */
    private fun UserCollectionDto.toCollectionEntity(): CollectionEntity = CollectionEntity(
        subjectId = subjectId,
        status = status(),
        watchedEpisodes = epStatus.takeIf { it > 0 },
        rating = rate.takeIf { it > 0 }?.toFloat(),
        personalTags = tags,
        personalImpression = comment,
        updateTime = updatedAt?.let { parseTimestamp(it) } ?: System.currentTimeMillis(),
    )

    private fun UserCollectionDto.status(): WatchStatus =
        type.toWatchStatus() ?: WatchStatus.PLAN_TO_WATCH // Planner 已过滤不可映射条目,此为兜底

    private fun parseTimestamp(iso: String): Long = runCatching {
        java.time.OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    }.getOrElse { System.currentTimeMillis() }
}