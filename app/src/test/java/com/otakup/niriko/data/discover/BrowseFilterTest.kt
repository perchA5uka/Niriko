package com.otakup.niriko.data.discover

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.bangumi.dto.SearchFilterDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchRequestDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * BrowseFilter 的纯函数单测（第 6 轮 §6.5 / §10 的单测清单）。
 *
 * 覆盖：9 个维度到官方 v0 请求参数的映射、年份单边、跨年季度、状态三态、
 * 制作别名展开去重、随机/名称的本地排序、评分/排名/NSFW/评分人数增强，
 * 以及**请求体里不再出现 series**。
 */
class BrowseFilterTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun subject(
        id: Long,
        type: SubjectType = SubjectType.ANIME,
        title: String = "作品" + id,
        titleCN: String? = null,
        airDate: String? = null,
        totalEpisodes: Int? = null,
        ratingTotal: Int? = null,
        ratingScore: Float? = null,
        rank: Int? = null,
    ) = SubjectEntity(
        subjectId = id,
        title = title,
        titleCN = titleCN,
        type = type,
        airDate = airDate,
        totalEpisodes = totalEpisodes,
        ratingScore = ratingScore,
        ratingTotal = ratingTotal,
        rank = rank,
    )

    private val upcomingSubject = subject(1, airDate = "2026-10-10")
    private val airingSubject = subject(2, airDate = "2026-08-26", totalEpisodes = 12)
    private val completedSubject = subject(3, airDate = "2025-08-21", totalEpisodes = 12)
    private val unknownSubject = subject(4, airDate = null)

    /** 结构断言：SearchFilterDto 的序列化字段名。 */
    private fun filterFieldNames(): List<String> {
        val descriptor = SearchFilterDto.serializer().descriptor
        return (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
    }

    // ==================== 维度 1：地区 ====================

    @Test
    fun `地区映射为 filter tag`() {
        assertEquals(listOf("日本"), BrowseFilter(area = BrowseArea.JAPAN).toRequest(today).filter!!.tag)
        assertEquals(listOf("中国"), BrowseFilter(area = BrowseArea.CHINA).toRequest(today).filter!!.tag)
        assertNull(BrowseFilter().toRequest(today).filter!!.tag)
        assertEquals(listOf("日本", "中国"), BrowseFilter.AREAS.map { it.label })
    }

    // ==================== 维度 2：版本 ====================

    @Test
    fun `版本映射为 filter tag`() {
        assertEquals(listOf("TV"), BrowseFilter(version = BrowseVersion.TV).toRequest(today).filter!!.tag)
        assertEquals(listOf("剧场版"), BrowseFilter(version = BrowseVersion.MOVIE).toRequest(today).filter!!.tag)
        assertEquals(listOf("OVA"), BrowseFilter(version = BrowseVersion.OVA).toRequest(today).filter!!.tag)
        assertEquals(listOf("WEB"), BrowseFilter(version = BrowseVersion.WEB).toRequest(today).filter!!.tag)
        assertEquals(listOf("TV", "剧场版", "OVA", "WEB"), BrowseFilter.VERSIONS.map { it.label })
    }

    // ==================== 维度 3：年份 ====================

    @Test
    fun `年份映射为整年窗口`() {
        assertEquals(
            listOf(">=2023-01-01", "<2024-01-01"),
            BrowseFilter(year = BrowseYear.Exact(2023)).airDateRange(today),
        )
    }

    @Test
    fun `2000以前退化为单边区间`() {
        assertEquals(listOf("<2001-01-01"), BrowseFilter(year = BrowseYear.Before2000).airDateRange(today))
        // 单边区间是官方支持的写法（规格 §6.2）
        assertEquals(listOf("<2001-01-01"), BrowseFilter(year = BrowseYear.Before2000).toRequest(today).filter!!.airDate)
        assertEquals("2000以前", BrowseYear.Before2000.label)
        // 「2000以前」= 含 2000 整年（captain 2026-09-25 已批准；与 Bangumi-master /^(2000|1\d{3})/ 一致）
        assertEquals("2001-01-01", BrowseYear.BEFORE_2001_CUTOFF)
    }

    @Test
    fun `年份选项从当前年往下到2001再加2000以前`() {
        val options = BrowseFilter.yearOptions(2026)
        assertEquals(27, options.size)
        assertEquals(BrowseYear.Exact(2026), options.first())
        assertEquals(BrowseYear.Exact(2001), options[options.size - 2])
        assertEquals(BrowseYear.Before2000, options.last())
        assertEquals("2024", BrowseYear.Exact(2024).label)
    }

    // ==================== 维度 4：季度 ====================

    @Test
    fun `季度需要年份_十月季度正确跨年`() {
        assertEquals(
            listOf(">=2023-07-01", "<2023-10-01"),
            BrowseFilter(year = BrowseYear.Exact(2023), quarter = 7).airDateRange(today),
        )
        assertEquals(
            listOf(">=2023-10-01", "<2024-01-01"),
            BrowseFilter(year = BrowseYear.Exact(2023), quarter = 10).airDateRange(today),
        )
        assertEquals(
            listOf(">=2023-01-01", "<2023-04-01"),
            BrowseFilter(year = BrowseYear.Exact(2023), quarter = 1).airDateRange(today),
        )
        assertEquals(listOf(1, 4, 7, 10), BrowseFilter.QUARTERS)
    }

    @Test
    fun `只给季度不给年份不产生air_date_2000以前忽略季度`() {
        assertNull(BrowseFilter(quarter = 7).airDateRange(today))
        assertEquals(
            listOf("<2001-01-01"),
            BrowseFilter(year = BrowseYear.Before2000, quarter = 7).airDateRange(today),
        )
    }

    // ==================== 维度 5：状态（三态 + 标注位） ====================

    @Test
    fun `状态三态判定`() {
        assertEquals(BrowseStatus.UPCOMING, BrowseFilter.localStatusOf(upcomingSubject, today))
        assertEquals(BrowseStatus.AIRING, BrowseFilter.localStatusOf(airingSubject, today))
        assertEquals(BrowseStatus.COMPLETED, BrowseFilter.localStatusOf(completedSubject, today))
        assertNull("没有开播日就无法判定", BrowseFilter.localStatusOf(unknownSubject, today))
    }

    @Test
    fun `未播放走服务端过滤并带上今天`() {
        val filter = BrowseFilter(status = BrowseStatus.UPCOMING)
        assertEquals(listOf(">=2026-09-25"), filter.airDateRange(today))
        assertFalse(filter.statusRequiresLocalFilter)
        assertEquals(BrowseFilter.STATUS_NOTE, filter.statusNote)
        assertEquals("按开播日推算", filter.statusNote)
    }

    @Test
    fun `未播放与年份条件叠加`() {
        val filter = BrowseFilter(year = BrowseYear.Exact(2026), status = BrowseStatus.UPCOMING)
        assertEquals(
            listOf(">=2026-01-01", "<2027-01-01", ">=2026-09-25"),
            filter.airDateRange(today),
        )
    }

    @Test
    fun `连载与完结在本地判定_并带按开播日推算标注`() {
        val items = listOf(upcomingSubject, airingSubject, completedSubject)
        val airingFilter = BrowseFilter(status = BrowseStatus.AIRING)
        assertTrue(airingFilter.statusRequiresLocalFilter)
        assertEquals(BrowseFilter.STATUS_NOTE, airingFilter.statusNote)
        assertEquals(listOf(2L), airingFilter.applyLocalFilters(items, today = today).map { it.subjectId })
        assertEquals(
            listOf(3L),
            BrowseFilter(status = BrowseStatus.COMPLETED).applyLocalFilters(items, today = today).map { it.subjectId },
        )
        // 未播放由服务端过滤，本地不重复过滤（避免误杀本地缺日期的条目）
        assertEquals(3, BrowseFilter(status = BrowseStatus.UPCOMING).applyLocalFilters(items, today = today).size)
    }

    @Test
    fun `没有状态条件时没有标注位`() {
        assertNull(BrowseFilter().statusNote)
        assertFalse(BrowseFilter().statusRequiresLocalFilter)
        assertEquals(listOf("连载", "完结", "未播放"), BrowseFilter.STATUSES.map { it.label })
    }

    // ==================== 维度 6：类型 ====================

    @Test
    fun `类型映射为官方 type 整数_不限类型时不产出该字段`() {
        assertEquals(listOf(1), BrowseFilter(type = SubjectType.BOOK).toRequest(today).filter!!.type)
        assertEquals(listOf(2), BrowseFilter(type = SubjectType.ANIME).toRequest(today).filter!!.type)
        assertEquals(listOf(3), BrowseFilter(type = SubjectType.MUSIC).toRequest(today).filter!!.type)
        assertEquals(listOf(4), BrowseFilter(type = SubjectType.GAME).toRequest(today).filter!!.type)
        assertEquals(listOf(6), BrowseFilter(type = SubjectType.REAL).toRequest(today).filter!!.type)
        assertNull(BrowseFilter().toRequest(today).filter!!.type)
    }

    @Test
    fun `内容标签是46词表_多值且`() {
        assertEquals(46, BrowseFilter.CONTENT_TAGS.size)
        assertEquals(46, BrowseFilter.CONTENT_TAGS.distinct().size)
        assertEquals("奇幻", BrowseFilter.CONTENT_TAGS.first())
        assertEquals("偶像", BrowseFilter.CONTENT_TAGS.last())
        assertTrue(BrowseFilter.CONTENT_TAGS.containsAll(listOf("轻小说", "泡面番", "欢乐向", "伪娘", "穿越")))
        assertEquals(
            listOf("奇幻", "战斗", "日本", "TV"),
            BrowseFilter(
                area = BrowseArea.JAPAN,
                version = BrowseVersion.TV,
                tags = listOf("奇幻", "战斗"),
            ).toRequest(today).filter!!.tag,
        )
    }

    @Test
    fun `重复标签去重`() {
        assertEquals(
            listOf("奇幻", "日本"),
            BrowseFilter(area = BrowseArea.JAPAN, tags = listOf("奇幻", "日本")).toRequest(today).filter!!.tag,
        )
    }

    // ==================== 维度 7：制作 ====================

    @Test
    fun `制作展开别名并按变体多请求`() {
        // 别名表只认它自己登记的变体（规范名 MAPPA 与 マッパ / Mappa），
        // 小写 mappa 不在表里 —— 所以 UI 的制作下拉必须用规范名，见 BrowseFilter 的 KDoc。
        val filter = BrowseFilter(studio = "MAPPA")
        val variants = filter.studioVariants()
        assertTrue(variants.contains("MAPPA"))
        assertTrue(variants.contains("マッパ"))
        assertTrue(variants.contains("Mappa"))
        assertEquals(listOf("mappa"), BrowseFilter(studio = "mappa").studioVariants())
        assertEquals(variants.size, variants.distinct().size)
        val requests = filter.toRequests(today)
        assertEquals(variants.size, requests.size)
        assertEquals(variants, requests.map { it.filter!!.tag!!.last() })
        assertEquals(BrowseFilter.STUDIO_NOTE, filter.studioNote)
        assertEquals("按标签匹配", filter.studioNote)
    }

    @Test
    fun `制作变体去重_未命中别名表时只有原词`() {
        val ig = BrowseFilter(studio = "Production I.G").studioVariants()
        assertEquals(ig.size, ig.distinct().size)
        assertTrue(ig.contains("Production I.G"))
        assertEquals(listOf("某小公司"), BrowseFilter(studio = "某小公司").studioVariants())
        assertEquals(listOf("某小公司"), BrowseFilter(studio = " 某小公司 ").studioVariants())
        assertTrue(BrowseFilter().studioVariants().isEmpty())
        assertEquals(1, BrowseFilter().toRequests(today).size)
        assertNull(BrowseFilter().studioNote)
    }

    // ==================== 维度 8：排序 ====================

    @Test
    fun `排序映射到官方 sort_随机与名称本地实现`() {
        assertEquals("rank", BrowseSort.RANK.requestSort)
        assertEquals("date", BrowseSort.DATE.requestSort)
        assertEquals("heat", BrowseSort.HEAT.requestSort)
        assertEquals("score", BrowseSort.SCORE.requestSort)
        assertTrue(BrowseSort.RANDOM.isLocalOnly)
        assertTrue(BrowseSort.NAME.isLocalOnly)
        // 本地排序仍要发一个合法 sort，否则请求会被拒
        assertEquals("rank", BrowseSort.RANDOM.requestSort)
        assertEquals("rank", BrowseSort.NAME.requestSort)
        assertEquals("heat", BrowseFilter(sort = BrowseSort.HEAT).toRequest(today).sort)
        assertEquals("date", BrowseFilter(sort = BrowseSort.DATE).toRequest(today).sort)
        assertEquals(
            listOf("排名", "上映时间", "评分人数", "评分", "随机", "名称"),
            BrowseFilter.SORTS.map { it.label },
        )
    }

    @Test
    fun `名称按拼音排序`() {
        val items = listOf(
            subject(1, titleCN = "春"),
            subject(2, titleCN = "阿"),
            subject(3, titleCN = "白"),
        )
        val sorted = BrowseFilter(sort = BrowseSort.NAME).sortLocally(items)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.subjectId })
    }

    @Test
    fun `随机排序用注入的种子保证可复现`() {
        val items = (1L..20L).map { subject(it) }
        val filter = BrowseFilter(sort = BrowseSort.RANDOM)
        val first = filter.sortLocally(items, Random(42)).map { it.subjectId }
        val second = filter.sortLocally(items, Random(42)).map { it.subjectId }
        assertEquals(first, second)
        assertEquals(items.map { it.subjectId }, first.sorted())
    }

    @Test
    fun `本地重排覆盖排名与评分`() {
        val a = subject(1, rank = 50, ratingScore = 7f)
        val b = subject(2, rank = 3, ratingScore = 8f)
        val c = subject(3, rank = null, ratingScore = 9.5f)
        val items = listOf(a, b, c)
        assertEquals(
            listOf(2L, 1L, 3L),
            BrowseFilter(sort = BrowseSort.RANK).sortLocally(items).map { it.subjectId },
        )
        assertEquals(
            listOf(3L, 2L, 1L),
            BrowseFilter(sort = BrowseSort.SCORE).sortLocally(items).map { it.subjectId },
        )
    }

    // ==================== 维度 9：收藏 ====================

    @Test
    fun `收藏维度隐藏已收藏是本地过滤_零请求`() {
        val items = listOf(subject(1), subject(2), subject(3))
        assertEquals(
            listOf(2L, 3L),
            BrowseFilter(hideCollected = true)
                .applyLocalFilters(items, collectedIds = setOf(1L), today = today)
                .map { it.subjectId },
        )
        assertEquals(3, BrowseFilter().applyLocalFilters(items, collectedIds = setOf(1L), today = today).size)
        assertEquals(listOf("隐藏"), BrowseFilter.COLLECTED_OPTIONS)
    }

    // ==================== 我们的增强：评分 / 排名 / 评分人数 / NSFW ====================

    @Test
    fun `评分区间整数不带小数_支持一位小数`() {
        assertEquals(listOf(">=8", "<=9"), BrowseFilter(minRating = 8f, maxRating = 9f).ratingRange())
        assertEquals(listOf(">=7.5"), BrowseFilter(minRating = 7.5f).ratingRange())
        assertEquals(listOf("<=9.5"), BrowseFilter(maxRating = 9.5f).ratingRange())
        assertNull(BrowseFilter().ratingRange())
    }

    @Test
    fun `排名区间下限夹到1`() {
        assertEquals(listOf(">=1", "<=100"), BrowseFilter(rankFrom = 0, rankTo = 100).rankRange())
        assertEquals(listOf(">=1"), BrowseFilter(rankFrom = 1).rankRange())
        assertNull(BrowseFilter().rankRange())
    }

    @Test
    fun `评分人数区间映射为 rating_count`() {
        assertEquals(listOf(">=100"), BrowseFilter(minRatingCount = 100).ratingCountRange())
        assertEquals(
            listOf(">=100", "<=5000"),
            BrowseFilter(minRatingCount = 100, maxRatingCount = 5000).ratingCountRange(),
        )
        assertEquals(listOf(">=0"), BrowseFilter(minRatingCount = -5).ratingCountRange())
        assertNull(BrowseFilter().ratingCountRange())
        val dto = BrowseFilter(minRatingCount = 100).toRequest(today).filter!!
        assertEquals(listOf(">=100"), dto.ratingCount)
        val json = Json.encodeToString(SearchFilterDto.serializer(), dto)
        assertTrue(json.contains("rating_count"))
        assertTrue(json.contains("100"))
    }

    @Test
    fun `nsfw 只在为真时产出`() {
        assertNull(BrowseFilter().toRequest(today).filter!!.nsfw)
        assertEquals(true, BrowseFilter(nsfw = true).toRequest(today).filter!!.nsfw)
    }

    @Test
    fun `评分与评分人数走本地判定_v0 搜索通路没有这两个参数`() {
        val eight = subject(1, ratingTotal = 500, ratingScore = 8.5f)
        val seven = subject(2, ratingTotal = 50, ratingScore = 7.0f)
        val noRating = subject(3, ratingTotal = null, ratingScore = null)

        // 默认（都不限）→ 全部放行
        assertTrue(BrowseFilter().matchesRatingLocally(eight))
        assertTrue(BrowseFilter().matchesRatingLocally(noRating))

        // 评分下限
        assertTrue(BrowseFilter(minRating = 8f).matchesRatingLocally(eight))
        assertFalse(BrowseFilter(minRating = 8f).matchesRatingLocally(seven))
        // 缺评分 = 无法证明达标 → 挡掉（与 DiscoveryFeed 的质量门槛同口径）
        assertFalse(BrowseFilter(minRating = 8f).matchesRatingLocally(noRating))

        // 评分人数区间（= 质量门槛的本地表达，官方 filter.rating_count 的等价物）
        assertTrue(BrowseFilter(minRatingCount = 100).matchesRatingLocally(eight))
        assertFalse(BrowseFilter(minRatingCount = 100).matchesRatingLocally(seven))
        assertFalse(BrowseFilter(minRatingCount = 100).matchesRatingLocally(noRating))
        assertFalse(BrowseFilter(maxRatingCount = 100).matchesRatingLocally(eight))

        // applyLocalFilters 会把增强项一起过一遍（第 6 轮 §6.3 的已知降级）
        val kept = BrowseFilter(minRatingCount = 100).applyLocalFilters(
            listOf(eight, seven, noRating),
            today = today,
        )
        assertEquals(listOf(1L), kept.map { it.subjectId })
    }

    // ==================== 官方 schema 硬约束：不再有 series ====================

    @Test
    fun `请求体不再有 series 字段`() {
        val names = filterFieldNames()
        assertFalse("官方 schema 无 series（规格 §1）", names.contains("series"))
        assertTrue(names.contains("rating_count"))
        assertTrue(names.contains("air_date"))
        assertTrue(names.contains("tag"))
        assertTrue(names.contains("nsfw"))
    }

    @Test
    fun `序列化后的请求体不含 series 键`() {
        val request = BrowseFilter(
            type = SubjectType.ANIME,
            area = BrowseArea.JAPAN,
            year = BrowseYear.Exact(2020),
            minRatingCount = 100,
        ).toRequest(today)
        val json = Json.encodeToString(SearchRequestDto.serializer(), request)
        assertFalse(json.contains("series"))
        assertTrue(json.contains("air_date"))
        assertTrue(json.contains("rating_count"))
        assertEquals("", request.keyword)
    }

    // ==================== 纯辅助：条件摘要 / 活动条件 ====================

    @Test
    fun `hasAnyCondition 不含类型与排序`() {
        assertFalse(BrowseFilter().hasAnyCondition)
        assertFalse(BrowseFilter(type = SubjectType.ANIME, sort = BrowseSort.RANK).hasAnyCondition)
        assertTrue(BrowseFilter(area = BrowseArea.JAPAN).hasAnyCondition)
        assertTrue(BrowseFilter(version = BrowseVersion.TV).hasAnyCondition)
        assertTrue(BrowseFilter(year = BrowseYear.Before2000).hasAnyCondition)
        assertTrue(BrowseFilter(quarter = 7, year = BrowseYear.Exact(2024)).hasAnyCondition)
        assertTrue(BrowseFilter(status = BrowseStatus.AIRING).hasAnyCondition)
        assertTrue(BrowseFilter(tags = listOf("奇幻")).hasAnyCondition)
        assertTrue(BrowseFilter(studio = "MAPPA").hasAnyCondition)
        assertTrue(BrowseFilter(hideCollected = true).hasAnyCondition)
        assertTrue(BrowseFilter(minRating = 8f).hasAnyCondition)
        assertTrue(BrowseFilter(minRatingCount = 100).hasAnyCondition)
        assertTrue(BrowseFilter(rankTo = 500).hasAnyCondition)
        assertTrue(BrowseFilter(nsfw = true).hasAnyCondition)
    }

    @Test
    fun `条件摘要文案`() {
        val filter = BrowseFilter(
            area = BrowseArea.JAPAN,
            version = BrowseVersion.TV,
            year = BrowseYear.Exact(2024),
            quarter = 7,
            status = BrowseStatus.AIRING,
            tags = listOf("奇幻", "战斗"),
            studio = "MAPPA",
            minRating = 8f,
            rankTo = 100,
            minRatingCount = 100,
            hideCollected = true,
            nsfw = true,
        )
        assertEquals(
            "日本 · TV · 2024 · 7月 · 连载 · 奇幻/战斗 · MAPPA · 评分 ≥8 · 评分人数 ≥100 · 排名 ≤100 · 隐藏已收藏 · R18",
            filter.summary(),
        )
        assertEquals("", BrowseFilter().summary())
    }

    @Test
    fun `纯条件浏览产生一个空关键词请求`() {
        val request = BrowseFilter().toRequest(today)
        assertEquals("", request.keyword)
        assertEquals("heat", request.sort)
        val dto = request.filter!!
        assertNull(dto.type)
        assertNull(dto.tag)
        assertNull(dto.airDate)
        assertNull(dto.rating)
        assertNull(dto.ratingCount)
        assertNull(dto.rank)
        assertNull(dto.nsfw)
    }
}
