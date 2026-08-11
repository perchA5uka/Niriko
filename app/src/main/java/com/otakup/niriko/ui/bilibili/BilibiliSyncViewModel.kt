package com.otakup.niriko.ui.bilibili

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.dao.BilibiliSyncItemDao
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.remote.bilibili.BilibiliSiteMap
import com.otakup.niriko.plugin.bilibili.BILI_MSG_CHECK_LOGIN
import com.otakup.niriko.plugin.bilibili.BILI_MSG_DEBUG_ERROR
import com.otakup.niriko.plugin.bilibili.BILI_MSG_GET_LIST
import com.otakup.niriko.plugin.bilibili.BILI_MSG_GET_REVIEW
import com.otakup.niriko.plugin.bilibili.BILI_MSG_REVIEW_PROGRESS
import com.otakup.niriko.plugin.bilibili.BilibiliFollowItem
import com.otakup.niriko.plugin.bilibili.BilibiliImporter
import com.otakup.niriko.plugin.bilibili.BilibiliImportResult
import com.otakup.niriko.plugin.bilibili.BilibiliMatcher
import com.otakup.niriko.plugin.bilibili.BilibiliReview
import com.otakup.niriko.plugin.bilibili.BilibiliSyncPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 导入工作流阶段。 */
enum class BilibiliSyncStatus {
    /** 等待 WebView 注入脚本回包。 */
    IDLE,
    /** 正在检查登录态。 */
    CHECKING,
    /** 已登录（等待/正在拉取追番列表）。 */
    LOGGED_IN,
    /** 已拿到列表，正在逐个拉取评分/短评。 */
    FETCHING_REVIEWS,
    /** 全量拉取完成，进入匹配/预览/导入。 */
    READY,
}

/** 页面临时 UI 状态（不跨进程持久化）。 */
data class BilibiliSyncUiState(
    val stage: BilibiliSyncStatus = BilibiliSyncStatus.IDLE,
    /** 顶部状态条文案。 */
    val message: String = "等待网页加载…",
    val userName: String? = null,
    val previews: List<BilibiliSyncPreview> = emptyList(),
    val overwrite: Boolean = false,
    val isImporting: Boolean = false,
    val importResult: BilibiliImportResult? = null,
    /** 评分/短评拉取进度（done/total，total=0 时不显示进度条）。 */
    val reviewDone: Int = 0,
    val reviewTotal: Int = 0,
    /** 注入脚本异常（JS 侧 postMessage DEBUG_ERROR）。非空时 UI 弹 Snackbar 展示。 */
    val debugError: String? = null,
) {
    val selectedCount: Int get() = previews.count { it.selected }

    /** 是否有进行中的评分拉取（用于进度条显隐）。 */
    val isFetchingReviews: Boolean get() = stage == BilibiliSyncStatus.FETCHING_REVIEWS
}

/**
 * 哔哩哔哩导入工作流 ViewModel。
 *
 * 接收 WebView 注入脚本经 [BilibiliJsBridge] 回传的 JSON 消息
 * （type ∈ CHECK_LOGIN / GET_LIST / GET_REVIEW），合并数据后做
 * season_id/标题匹配生成预览，最终由 [BilibiliImporter] 落库。
 *
 * @param siteMapLookup site_map 反查注入（BilibiliSiteMap.bgmIdBySeasonId）。
 */
class BilibiliSyncViewModel(
    private val siteMapLookup: (Int) -> Long?,
    private val bilibiliSyncItemDao: BilibiliSyncItemDao,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BilibiliSyncUiState())
    val uiState: StateFlow<BilibiliSyncUiState> = _uiState.asStateFlow()

    /** 最近一次桥接全量结果（List + Reviews 合并视图，供匹配阶段使用）。 */
    private var followList: List<BilibiliFollowItem> = emptyList()
    private var reviews: Map<Long, BilibiliReview> = emptyMap()

    // ==================== 桥接消息入口 ====================

    /** WebView 注入脚本回传的 JSON 消息（主线程调用）。 */
    fun onBridgeMessage(json: String) {
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (obj.optString("type")) {
            BILI_MSG_CHECK_LOGIN -> handleCheckLogin(obj.opt("data"))
            BILI_MSG_GET_LIST -> handleGetList(obj.opt("data"))
            BILI_MSG_GET_REVIEW -> handleGetReview(obj.opt("data"))
            BILI_MSG_REVIEW_PROGRESS -> handleReviewProgress(obj.opt("data"))
            BILI_MSG_DEBUG_ERROR -> handleDebugError(obj.optString("data"))
        }
    }

    /** 注入脚本内部异常回传（排查拉取链路断裂时定位用）。 */
    private fun handleDebugError(error: String) {
        if (error.isBlank()) return
        _uiState.update { it.copy(message = "网页脚本异常：$error", debugError = error) }
    }

    /** 清空已展示的错误（Snackbar 弹出后调用）。 */
    fun consumeDebugError() {
        _uiState.update { it.copy(debugError = null) }
    }

    private fun handleCheckLogin(data: Any?) {
        val dto = data as? JSONObject ?: return
        val dataObj = dto.optJSONObject("data")
        val loggedIn = dataObj?.optBoolean("isLogin") == true
        if (loggedIn) {
            val mid = dataObj.optLong("mid", 0L).takeIf { it > 0 }
                ?: dataObj.optJSONObject("wallet")?.optLong("mid") ?: 0L
            val name = dataObj.optString("uname").ifBlank { null }
            _uiState.update {
                it.copy(
                    stage = BilibiliSyncStatus.LOGGED_IN,
                    userName = name ?: "用户 $mid",
                    message = "已登录$mid，正在获取追番列表…",
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    stage = BilibiliSyncStatus.IDLE,
                    message = "未检测到登录：请在下方网页中登录哔哩哔哩账号",
                )
            }
        }
    }

    private fun handleGetList(data: Any?) {
        val array = data as? JSONArray ?: return
        val items = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val mediaId = item.optLong("id")
                if (mediaId <= 0) continue
                add(
                    BilibiliFollowItem(
                        mediaId = mediaId,
                        seasonId = if (item.isNull("seasonId")) null else item.optInt("seasonId", 0).takeIf { it > 0 },
                        title = item.optString("title"),
                        cover = item.optString("cover").ifBlank { null },
                        followStatus = item.optInt("status", 1),
                        progress = item.optInt("progress", 0).takeIf { it > 0 },
                        totalEpisodes = item.optInt("total", 0).takeIf { it > 0 },
                    ),
                )
            }
        }
        followList = items
        _uiState.update {
            it.copy(
                stage = BilibiliSyncStatus.FETCHING_REVIEWS,
                message = "已获取 ${items.size} 条追番，正在拉取评分与短评（0/${items.size}）…",
                reviewDone = 0,
                reviewTotal = items.size,
            )
        }
    }

    /** 评分/短评拉取进度消息（每完成一批由 JS 上报 {done, total}）。 */
    private fun handleReviewProgress(data: Any?) {
        val obj = data as? JSONObject ?: return
        val done = obj.optInt("done", 0)
        val total = obj.optInt("total", 0)
        if (total <= 0) return
        val clamped = done.coerceIn(0, total)
        _uiState.update {
            it.copy(
                stage = BilibiliSyncStatus.FETCHING_REVIEWS,
                message = "正在拉取评分与短评（$clamped/$total）…",
                reviewDone = clamped,
                reviewTotal = total,
            )
        }
    }

    private fun handleGetReview(data: Any?) {
        val obj = data as? JSONObject ?: return
        val parsed = mutableMapOf<Long, BilibiliReview>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val mediaId = key.toLongOrNull() ?: continue
            val review = obj.optJSONObject(key) ?: continue
            parsed[mediaId] = BilibiliReview(
                score = review.optDouble("score", 0.0).takeIf { it > 0 }?.toFloat(),
                content = review.optString("content").ifBlank { null },
            )
        }
        reviews = parsed
        _uiState.update {
            it.copy(
                message = "正在匹配 Bangumi 条目…",
                reviewDone = 0,
                reviewTotal = 0,
            )
        }
        viewModelScope.launch { buildPreviews() }
    }

    // ==================== 匹配与预览 ====================

    private suspend fun buildPreviews() {
        // 本地 subjects 标题索引（title/titleCN 都参与，同一条目两个键）
        val localTitles = mutableMapOf<Long, String>()
        subjectDao.getAll().forEach { s ->
            if (s.title.isNotBlank()) localTitles.putIfAbsent(s.subjectId, s.title)
            if (!s.titleCN.isNullOrBlank()) localTitles.putIfAbsent(s.subjectId, s.titleCN)
        }
        val localCollections = collectionDao.getAll().associateBy { it.subjectId }

        val previews = followList.map { item ->
            val bgmSubjectId = BilibiliMatcher.match(
                seasonId = item.seasonId,
                title = item.title,
                siteMapLookup = siteMapLookup,
                localTitles = localTitles,
            )
            val review = reviews[item.mediaId]
            BilibiliSyncPreview(
                mediaId = item.mediaId,
                seasonId = item.seasonId,
                bgmSubjectId = bgmSubjectId,
                title = item.title,
                cover = item.cover,
                followStatus = item.followStatus,
                progress = item.progress,
                totalEpisodes = item.totalEpisodes,
                biliScore = review?.score,
                biliComment = review?.content,
                localCollection = bgmSubjectId?.let { localCollections[it] },
                localSubjectTitle = bgmSubjectId?.let { localTitles[it] },
                // 默认勾选已匹配条目
                selected = bgmSubjectId != null,
            )
        }
        val matched = previews.count { it.isMatched }
        _uiState.update {
            it.copy(
                stage = BilibiliSyncStatus.READY,
                message = "获取完成：${previews.size} 部追番，匹配到 $matched 个 Bangumi 条目",
                previews = previews,
            )
        }
    }

    // ==================== 交互 ====================

    fun setSelected(mediaId: Long, selected: Boolean) {
        _uiState.update { state ->
            state.copy(previews = state.previews.map { p ->
                if (p.mediaId == mediaId) p.copy(selected = selected) else p
            })
        }
    }

    fun setAllSelected(selected: Boolean) {
        _uiState.update { state ->
            state.copy(previews = state.previews.map { if (it.isMatched) it.copy(selected = selected) else it })
        }
    }

    fun setOverwrite(overwrite: Boolean) {
        _uiState.update { it.copy(overwrite = overwrite) }
    }

    /** 一键导入勾选项（在导入页内完成落库）。 */
    fun import() {
        val state = _uiState.value
        if (state.isImporting || state.selectedCount == 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importResult = null) }
            val result = runCatching {
                BilibiliImporter(
                    bilibiliSyncItemDao = bilibiliSyncItemDao,
                    subjectDao = subjectDao,
                    collectionDao = collectionDao,
                ).import(
                    previews = state.previews.filter { it.selected },
                    overwrite = state.overwrite,
                )
            }.getOrElse {
                BilibiliImportResult(
                    total = state.selectedCount,
                    failed = state.selectedCount,
                )
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importResult = result,
                    // 导入成功后清掉勾选，避免重复导入
                    previews = it.previews.map { p -> if (p.selected && p.isMatched) p.copy(selected = false) else p },
                )
            }
        }
    }
}

// ==================== WebView JS 桥 ====================

/** 注入脚本回传通道：仅暴露单个 postMessage 方法。 */
class BilibiliJsBridge(
    private val onMessage: (String) -> Unit,
) {
    @android.webkit.JavascriptInterface
    fun postMessage(json: String) {
        onMessage(json)
    }
}

/** 手写 Factory（沿用项目 ViewModelProvider.Factory 范式，无 DI 框架）。 */
class BilibiliSyncViewModelFactory(
    private val context: Context,
    private val bilibiliSyncItemDao: BilibiliSyncItemDao,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BilibiliSyncViewModel(
            siteMapLookup = { seasonId -> BilibiliSiteMap.bgmIdBySeasonId(context, seasonId) },
            bilibiliSyncItemDao = bilibiliSyncItemDao,
            subjectDao = subjectDao,
            collectionDao = collectionDao,
        ) as T
    }
}