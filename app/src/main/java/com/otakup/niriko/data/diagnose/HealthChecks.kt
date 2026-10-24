package com.otakup.niriko.data.diagnose

/**
 * 数据体检的 8 项只读检查（C-1）。
 *
 * 每项都是输入 → [Finding] 的纯函数，编号（id）稳定：报告里出现的 id 不该随文案改动。
 * 判定阈值都是常量，改阈值 = 改行为，所以在 [HealthChecksTest] 里逐条钉住。
 */
object HealthChecks {

    /** 无进度占比超过该值才报 WARN（少量条目没填进度是正常的）。 */
    const val MISSING_PROGRESS_RATIO = 0.5f

    /** 无评分占比阈值（只统计「已看/在看」，不惩罚「想看」）。 */
    const val MISSING_RATING_RATIO = 0.8f

    /** 快照超过该天数没更新就提示。 */
    const val SNAPSHOT_STALE_DAYS = 7L

    /** 待同步超过该条数提示（同步可能卡住了）。 */
    const val PENDING_SYNC_WARN = 20

    /** 8 项检查的完整 id（顺序 = 报告顺序，测试钉住）。 */
    val IDS = listOf(
        "library.empty",
        "library.missing_progress",
        "library.missing_rating",
        "source.registered",
        "snapshot.missing",
        "storage.cache_over_limit",
        "sync.webdav_unconfigured",
        "theme.contrast",
    )

    fun run(input: HealthInput): List<Finding> = listOf(
        libraryEmpty(input),
        missingProgress(input),
        missingRating(input),
        registeredSources(input),
        snapshotFreshness(input),
        cacheOverLimit(input),
        webDavUnconfigured(input),
        contrast(input),
    )

    fun summarize(findings: List<Finding>): HealthSummary = HealthSummary(
        checked = findings.size,
        ok = findings.count { it.severity == Severity.OK },
        warnings = findings.count { it.severity == Severity.WARN },
        errors = findings.count { it.severity == Severity.ERROR },
    )

    /** ① 收藏为空：新手引导问题，不算错误。 */
    fun libraryEmpty(input: HealthInput): Finding =
        if (input.collectionCount > 0) {
            Finding("library.empty", "收藏库非空", Severity.OK, "共 ${input.collectionCount} 条收藏")
        } else {
            Finding(
                "library.empty", "收藏库是空的", Severity.INFO,
                "还没有任何收藏，去「发现」页搜一部作品开始吧。",
            )
        }

    /** ② 有收藏但进度为空。 */
    fun missingProgress(input: HealthInput): Finding {
        if (input.collectionCount == 0) {
            return Finding("library.missing_progress", "进度完整性", Severity.OK, "无收藏，跳过")
        }
        val ratio = input.collectionWithoutProgress.toFloat() / input.collectionCount
        return if (ratio > MISSING_PROGRESS_RATIO) {
            Finding(
                "library.missing_progress", "多数收藏没有进度", Severity.WARN,
                "${input.collectionWithoutProgress}/${input.collectionCount} 条收藏的进度为空。",
                fix = "打开几条在看作品，点一下集数即可补齐",
            )
        } else {
            Finding("library.missing_progress", "进度完整性", Severity.OK, "缺进度的条目在阈值内")
        }
    }

    /** ③ 已看/在看但没评分。 */
    fun missingRating(input: HealthInput): Finding {
        if (input.collectionCount == 0) {
            return Finding("library.missing_rating", "评分完整性", Severity.OK, "无收藏，跳过")
        }
        val ratio = input.collectionWithoutRating.toFloat() / input.collectionCount
        return if (ratio > MISSING_RATING_RATIO) {
            Finding(
                "library.missing_rating", "几乎都没打分", Severity.INFO,
                "${input.collectionWithoutRating}/${input.collectionCount} 条没有个人评分，统计页的均分参考价值会下降。",
                fix = "在收藏编辑弹层里补评分（可批量从分集评价生成）",
            )
        } else {
            Finding("library.missing_rating", "评分完整性", Severity.OK, "评分覆盖率正常")
        }
    }

    /** ④ 已注册数据源数量为 0（链是空的，所有刷新都会失败）。 */
    fun registeredSources(input: HealthInput): Finding =
        if (input.registeredSourceCount <= 0) {
            Finding(
                "source.registered", "没有任何可用数据源", Severity.ERROR,
                "数据源链是空的，搜索、详情与刷新都会失败。",
                fix = "重启应用；若仍为空，检查数据源配置",
            )
        } else {
            Finding("source.registered", "数据源", Severity.OK, "已注册 ${input.registeredSourceCount} 个")
        }

    /** ⑤ 快照新鲜度（C-3 的入口提示）。 */
    fun snapshotFreshness(input: HealthInput): Finding {
        val age = input.lastSnapshotAgeHours
        return when {
            input.snapshotCount == 0 || age == null -> Finding(
                "snapshot.missing", "还没有本地快照", Severity.WARN,
                "同步/导入/批量操作前会自动创建快照；目前一个都没有。",
                fix = "在本页点「立即创建快照」",
            )
            age > SNAPSHOT_STALE_DAYS * 24 -> Finding(
                "snapshot.missing", "最近快照过旧", Severity.WARN,
                "最近一次快照在 ${age / 24} 天前（阈值 ${SNAPSHOT_STALE_DAYS} 天）。",
                fix = "立即创建一次快照",
            )
            else -> Finding("snapshot.missing", "快照新鲜", Severity.OK, "最近 ${age} 小时内有快照")
        }
    }

    /** ⑥ 缓存超过上限。 */
    fun cacheOverLimit(input: HealthInput): Finding =
        if (input.cacheLimitBytes > 0 && input.cacheBytes > input.cacheLimitBytes) {
            Finding(
                "storage.cache_over_limit", "图片缓存超出上限", Severity.WARN,
                "缓存 ${formatBytes(input.cacheBytes)}，上限 ${formatBytes(input.cacheLimitBytes)}。",
                fix = "在本页清理缓存（不影响收藏与备份）",
            )
        } else {
            Finding("storage.cache_over_limit", "缓存占用", Severity.OK, "在限额内")
        }

    /** ⑦ 开了自动同步但没配 WebDAV（或反之）——这里只看「配了没」。 */
    fun webDavUnconfigured(input: HealthInput): Finding =
        if (input.webDavConfigured) {
            Finding("sync.webdav_unconfigured", "WebDAV", Severity.OK, "已配置")
        } else {
            Finding(
                "sync.webdav_unconfigured", "未配置 WebDAV 同步", Severity.INFO,
                "同步与备份页可以填 WebDAV 地址；不配也能用本地备份。",
            )
        }

    /** ⑧ 深色对比度（10.15 的机械判据汇总）。 */
    fun contrast(input: HealthInput): Finding =
        if (input.contrastFailures > 0) {
            Finding(
                "theme.contrast", "深色对比度未达标", Severity.WARN,
                "${input.contrastFailures} 组文字/背景对比度低于 WCAG AA。",
                fix = "见「深色对比度」审计（小件已按 4.5:1 机械校准）",
            )
        } else {
            Finding("theme.contrast", "文字对比度", Severity.OK, "深色/浅色均达标")
        }
}
