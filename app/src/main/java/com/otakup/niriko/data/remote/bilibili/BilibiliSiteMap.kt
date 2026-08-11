package com.otakup.niriko.data.remote.bilibili

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * 离线站点映射:bgmId → B站 season_id。
 *
 * 数据来源:[bangumi-data](https://github.com/bangumi-data/bangumi-data) 的精简快照,
 * 打包为 assets/data/bilibili_site_map.json,格式:
 * ```
 * { "<bgmId>": { "b": <大陆 season_id>, "h": <港澳台 season_id> } }
 * ```
 * 只保留有 B 站(含港澳台)片源的条目(共 3356 条)。
 * 进程内懒加载一次后常驻内存;读取失败/无映射时安全降级为 null。
 */
object BilibiliSiteMap {

    private const val ASSET_PATH = "data/bilibili_site_map.json"

    private val json = Json { ignoreUnknownKeys = true }

    /** 站内 season_id 类型。 */
    enum class Region(val key: String) {
        /** 大陆站 (sites.b) */
        MAINLAND("b"),
        /** 港澳台 (sites.bhmt) */
        HK_MO_TW("h"),
    }

    /** @Volatile 防止多线程重复初始化 */
    @Volatile
    private var map: Map<Long, Map<String, Int>>? = null

    /** 反向索引:season_id → bgmId(与 [map] 同生命周期)。 */
    @Volatile
    private var reverse: Map<Int, Long>? = null

    /** 上次加载时间 ms(用于非常低频的重载,避免测试/开发过期)。 */
    @Volatile
    private var loadedAt: Long = 0L

    /**
     * 查询指定 bgmId 的 B 站 season_id。
     * @param bgmId Bangumi 条目 ID
     * @param region 大陆或港澳台
     * @return 有映射返回 season_id,否则 null
     */
    fun seasonId(
        context: Context,
        bgmId: Long,
        region: Region = Region.MAINLAND,
    ): Int? {
        val site = lookup(context, bgmId) ?: return null
        val value = site[region.key] ?: return null
        return value.takeIf { it > 0 }
    }

    /**
     * 反向查询:给定 B 站 season_id → bgmId。
     * 大陆(b)优先,其余键(如港澳台 h)兜底；同一 season_id 命中多个 bgmId 时取第一个。
     * 用于 bilibili 追番列表(season_id)→ Bangumi 条目的导入匹配。
     */
    fun bgmIdBySeasonId(
        context: Context,
        seasonId: Int,
    ): Long? = reverseLookup(context)[seasonId]

    /** 懒构建一次反向索引（生命周期与正向映射同一 30 秒窗口）。 */
    private fun reverseLookup(context: Context): Map<Int, Long> {
        val now = System.currentTimeMillis()
        val cached = reverse
        if (cached != null && now - loadedAt < 30_000L) return cached
        return synchronized(this) {
            val cached2 = reverse
            if (cached2 != null && now - loadedAt < 30_000L) return@synchronized cached2
            val built = buildReverse(locate(context))
            reverse = built
            built
        }
    }

    /**
     * 由正向映射构建反向索引（纯函数，便于单元测试）。
     * 大陆 season_id 优先占位，港澳台兜底；重复 season_id 保留首个 bgmId。
     */
    internal fun buildReverse(forward: Map<Long, Map<String, Int>>): Map<Int, Long> {
        val result = HashMap<Int, Long>()
        for ((bgmId, sites) in forward) {
            val ordered = listOfNotNull(
                sites[Region.MAINLAND.key]?.takeIf { it > 0 },
                sites.entries.firstOrNull { it.key != Region.MAINLAND.key && it.value > 0 }?.value,
            )
            for (seasonId in ordered) {
                if (seasonId !in result) result[seasonId] = bgmId
            }
        }
        return result
    }

    /** 单条目查询(内部用)。 */
    private fun lookup(
        context: Context,
        bgmId: Long,
    ): Map<String, Int>? = locate(context)[bgmId]

    /** 加载整个映射。 */
    private fun locate(context: Context): Map<Long, Map<String, Int>> {
        val now = System.currentTimeMillis()
        val cached = map
        // 30 秒内直接返回缓存,避免每次会话都重读 assets
        if (cached != null && now - loadedAt < 30_000L) return cached
        return synchronized(this) {
            val cached2 = map
            if (cached2 != null && now - loadedAt < 30_000L) return@synchronized cached2
            val parsed = load(context)
            map = parsed
            loadedAt = System.currentTimeMillis()
            parsed
        }
    }

    /** 从 assets 读取并解析。任何失败 → 空 Map(不崩溃)。 */
    private fun load(context: Context): Map<Long, Map<String, Int>> {
        return try {
            val jsonText = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            parse(jsonText)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 解析 JSON 文本(纯函数,便于单元测试)。 */
    internal fun parse(jsonText: String): Map<Long, Map<String, Int>> {
        if (jsonText.isBlank()) return emptyMap()
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val result = HashMap<Long, Map<String, Int>>(root.size * 2)
            for ((key, value) in root) {
                val bgmId = key.toLongOrNull() ?: continue
                val siteObj = value.jsonObject
                val siteMap = HashMap<String, Int>(2)
                for ((rk, v) in siteObj) {
                    val num = v.jsonPrimitive.longOrNull ?: continue
                    if (num > 0L) siteMap[rk] = num.toInt()
                }
                if (siteMap.isNotEmpty()) result[bgmId] = siteMap
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}