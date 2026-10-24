package com.otakup.niriko.data.rank

import com.otakup.niriko.data.settings.SettingsDataStore
import java.time.LocalDate
import java.time.temporal.WeekFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 排名变化方向。持平用横线（榜单行尾），无快照时整段不显示。 */
enum class RankDirection { UP, DOWN, SAME }

/** 一条排名变化：`▲3` / `▼2` / `—`。 */
data class RankDelta(val direction: RankDirection, val amount: Int) {
    val label: String
        get() = when (direction) {
            RankDirection.UP -> "▲$amount"
            RankDirection.DOWN -> "▼$amount"
            RankDirection.SAME -> "—"
        }
}

/**
 * 榜单周快照（3.7）。**每周一次、≤200 条**，落在 DataStore 的 `ranking_snapshot_v1`
 * （不新增表，见 batch-c-storage.md）。
 *
 * 为什么同时留 [ranks] 与 [previousRanks]：同一周内反复打开榜单时，`ranks` 已经是本周的，
 * 仍然要和「上一周」比较才能继续显示 ▲▼；只留一份就会在第二次打开时全部变成 0。
 */
@Serializable
data class RankingSnapshot(
    /** 本周快照的 ISO 周键，如 `2026-W42`。 */
    val week: String,
    val capturedAt: Long = 0L,
    /** 本周名次：subjectId(字符串) → rank。 */
    val ranks: Map<String, Int> = emptyMap(),
    val previousWeek: String? = null,
    /** 上一周名次：本周的 ▲▼ 就与它比较。 */
    val previousRanks: Map<String, Int> = emptyMap(),
)

/** JSON 编解码 + 纯规则（无 Android 依赖，JVM 可测）。 */
object RankingSnapshotCodec {

    /** 单份快照最多保留的条数：只记榜单前 200 名，再往后差异没有意义。 */
    const val MAX_ENTRIES = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(snapshot: RankingSnapshot): String = json.encodeToString(RankingSnapshot.serializer(), snapshot)

    /** 坏 JSON / 缺键 → null（调用方视为「没有快照」，不显示箭头）。 */
    fun decode(raw: String?): RankingSnapshot? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(RankingSnapshot.serializer(), raw) }.getOrNull()
    }

    /** ISO 周键：跨年周也归到其 week-based year。 */
    fun isoWeekKey(date: LocalDate): String {
        val fields = WeekFields.ISO
        val year = date.get(fields.weekBasedYear())
        val week = date.get(fields.weekOfWeekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    /** 名次表：丢掉 ≤0 / 重复，按名次升序取前 [MAX_ENTRIES]。 */
    fun rankMap(entries: Map<Long, Int>): Map<String, Int> = entries
        // subjectId 必须为正（-1/0 是占位或脏数据）；名次同理。
        .filterKeys { it > 0 }
        .filterValues { it > 0 }
        .entries
        .sortedBy { it.value }
        .take(MAX_ENTRIES)
        .associate { it.key.toString() to it.value }

    /**
     * 排名变化（纯函数）：`previous - current`。
     * 任一侧缺失 / 非法 → null（不显示）；相等 → [RankDirection.SAME]（显示一条横线）。
     */
    fun delta(current: Int?, previous: Int?): RankDelta? {
        if (current == null || previous == null || current <= 0 || previous <= 0) return null
        val diff = previous - current
        return when {
            diff > 0 -> RankDelta(RankDirection.UP, diff)
            diff < 0 -> RankDelta(RankDirection.DOWN, -diff)
            else -> RankDelta(RankDirection.SAME, 0)
        }
    }
}

/** DataStore 读写抽成接口：`RankingSnapshotStore` 因此不依赖 Android，单测给内存实现即可。 */
interface RankingSnapshotStorage {
    suspend fun read(): String?
    suspend fun write(json: String)
}

/** 接 `SettingsDataStore` 的 `ranking_snapshot_v1` 键（唯一新增的偏好键）。 */
class SettingsRankingSnapshotStorage(
    private val settings: SettingsDataStore,
) : RankingSnapshotStorage {
    override suspend fun read(): String? = settings.rankingSnapshotJson()
    override suspend fun write(json: String) = settings.setRankingSnapshotJson(json)
}

/**
 * 周快照读写（3.7）。
 *
 * 采集时机由 UI 在「历史排名加载成功」时调用一次 [captureIfNeeded]；同一周内重复调用是空操作，
 * 因此不需要额外的定时器/Worker。
 */
class RankingSnapshotStore(
    private val storage: RankingSnapshotStorage,
) {

    suspend fun load(): RankingSnapshot? = RankingSnapshotCodec.decode(storage.read())

    /**
     * 本周没快照就滚动一版：本周 → [RankingSnapshot.ranks]，上周 → [RankingSnapshot.previousRanks]。
     * 同一周内再调用直接返回已有快照（不覆盖），保证 ▲▼ 整周稳定。
     */
    suspend fun captureIfNeeded(
        ranks: Map<Long, Int>,
        today: LocalDate = LocalDate.now(),
        nowMillis: Long = System.currentTimeMillis(),
    ): RankingSnapshot? {
        val week = RankingSnapshotCodec.isoWeekKey(today)
        val existing = load()
        if (existing != null && existing.week == week) return existing
        val current = RankingSnapshotCodec.rankMap(ranks)
        if (current.isEmpty()) return existing
        val rolled = RankingSnapshot(
            week = week,
            capturedAt = nowMillis,
            ranks = current,
            previousWeek = existing?.week,
            previousRanks = existing?.ranks.orEmpty(),
        )
        storage.write(RankingSnapshotCodec.encode(rolled))
        return rolled
    }

    /**
     * 当前榜单的 ▲▼ 表：与**上一周**比较；还没有上一周（首次采集）时返回空表 → 不显示箭头。
     */
    suspend fun deltas(
        currentRanks: Map<Long, Int>,
        today: LocalDate = LocalDate.now(),
    ): Map<Long, RankDelta> {
        val snapshot = load() ?: return emptyMap()
        val week = RankingSnapshotCodec.isoWeekKey(today)
        val baseline = if (snapshot.week == week) {
            snapshot.previousRanks
        } else {
            // 本周还没采集：当前名次就与上次采集那一周比较
            snapshot.ranks
        }
        if (baseline.isEmpty()) return emptyMap()
        return currentRanks.mapNotNull { (subjectId, current) ->
            RankingSnapshotCodec.delta(current, baseline[subjectId.toString()])?.let { subjectId to it }
        }.toMap()
    }
}
