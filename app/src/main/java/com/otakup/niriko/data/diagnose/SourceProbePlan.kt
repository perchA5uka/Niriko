package com.otakup.niriko.data.diagnose

/**
 * 数据源自检计划（2.5）：把「源 × 能力」摊成一张可执行的探测表。
 *
 * 本轮只做**计划与摘要**（纯函数）+ 能力矩阵的可见化，不做真实网络探测：
 * 探测本身已经在 `data/probe/` 里（ChannelProbe / SearchChainProbe），
 * 接进来需要改 probe 层（本轮授权之外），所以计划先行、执行器下次接。
 */
enum class ProbeStatus { UNKNOWN, OK, UNSUPPORTED }

/** 一个源的声明能力（来自 DataSourceCapabilities 的布尔位）。 */
data class SourceCapability(
    val sourceId: String,
    val label: String,
    val capabilities: Map<String, Boolean>,
)

data class ProbeCell(val sourceId: String, val capability: String, val status: ProbeStatus)

/** 计划里的能力顺序 = 展示顺序（固定，测试钉住）。 */
val PROBE_CAPABILITIES = listOf(
    "搜索", "详情", "剧集", "角色", "制作人员", "日历", "评分分布",
)

/** 一次自检的汇总（页面顶部一行 + 复制到反馈的摘要）。 */
data class ProbeSummary(
    val probed: Int,
    val failed: Int,
    val slowestMs: Long,
)

/**
 * 生成探测计划。
 * @param only 只测这些源（空 = 全部）；未知 id 会被忽略。
 */
fun planProbes(sources: List<SourceCapability>, only: Set<String> = emptySet()): List<ProbeCell> =
    sources
        .filter { only.isEmpty() || it.sourceId in only }
        .flatMap { source ->
            PROBE_CAPABILITIES.map { capability ->
                ProbeCell(
                    sourceId = source.sourceId,
                    capability = capability,
                    status = when (source.capabilities[capability]) {
                        true -> ProbeStatus.UNKNOWN   // 已声明支持，待实测
                        else -> ProbeStatus.UNSUPPORTED
                    },
                )
            }
        }

/** 每个源「不支持」的能力清单（报告里的失败摘要）。 */
fun unsupportedBySource(cells: List<ProbeCell>): Map<String, List<String>> =
    cells.filter { it.status == ProbeStatus.UNSUPPORTED }
        .groupBy { it.sourceId }
        .mapValues { (_, v) -> v.map { it.capability } }

/** 一行文字摘要（复制到反馈用）。 */
fun summarizePlan(cells: List<ProbeCell>): String {
    if (cells.isEmpty()) return "没有可自检的数据源"
    val supported = cells.count { it.status != ProbeStatus.UNSUPPORTED }
    return "${cells.size} 项能力 · 声明支持 $supported 项 · 不支持 ${cells.size - supported} 项"
}
