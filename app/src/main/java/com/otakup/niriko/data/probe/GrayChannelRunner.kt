package com.otakup.niriko.data.probe

/**
 * 灰色通道自检执行器。
 *
 * ## 设计要点
 *
 * 1. **顺序执行**：探针会打真实外部站点，并发会把用户 IP 的请求特征放大成「爬虫」，
 *    也让自检结果的耗时失去意义。数量很少（当前 5 个源 / 7 个端点），顺序足够快。
 * 2. **单个探针崩溃不影响全局**：每个 probe 调用都套 [runCatching]，
 *    异常转成 [ProbeState.UNREACHABLE] 的结果继续往下跑。
 * 3. **不静默丢弃**：灰色通道关闭时，未被执行的非自检探针仍然产出一条
 *    [ProbeState.SKIPPED] 结果——「为什么这项没跑」必须是可见的。
 * 4. **命名带源前缀**：结果名统一为「源标签 · 端点名」，UI 直接展示即可。
 */
object GrayChannelRunner {

    /** 灰色通道未开启时，对非自检探针给出的说明。 */
    private const val SKIP_REASON = "灰色通道未开启（仅自检不受影响）"

    /** 结果名连接符（全角间隔号，与设置页其它展示保持一致）。 */
    private const val NAME_SEPARATOR = " · "

    /**
     * 运行自检。
     *
     * @param config 探针配置（UA / Cookie / Referer / 灰色通道开关）
     * @param onlyId 只跑指定 id 的探针；null = 全部
     * @return 每个端点一条结果，顺序与 [GrayChannelRegistry.all] 中登记的探针顺序一致
     */
    suspend fun runAll(config: ProbeConfig, onlyId: String? = null): List<ProbeResult> {
        val probes = if (onlyId == null) {
            GrayChannelRegistry.all
        } else {
            GrayChannelRegistry.all.filter { it.id == onlyId }
        }

        val results = mutableListOf<ProbeResult>()
        for (probe in probes) {
            val endpoints = runCatching { probe.endpoints(config) }.getOrElse { error ->
                results.add(
                    ProbeResult(
                        endpointName = probe.label + NAME_SEPARATOR + "端点清单",
                        url = "",
                        state = ProbeState.UNREACHABLE,
                        error = "枚举端点失败：" + describe(error),
                    ),
                )
                emptyList<ProbeEndpoint>()
            }

            for (endpoint in endpoints) {
                val name = probe.label + NAME_SEPARATOR + endpoint.name
                if (!config.grayChannelEnabled && !probe.probeRegardlessOfToggle) {
                    // 未开启时不发请求，但仍产出一条结果：用户需要看到「有这项、但没跑」。
                    results.add(
                        ProbeResult(
                            endpointName = name,
                            url = endpoint.url,
                            state = ProbeState.SKIPPED,
                            error = SKIP_REASON,
                        ),
                    )
                    continue
                }

                val outcome = runCatching { probe.probe(endpoint, config) }.getOrElse { error ->
                    ProbeResult(
                        endpointName = endpoint.name,
                        url = endpoint.url,
                        state = ProbeState.UNREACHABLE,
                        error = describe(error),
                    )
                }
                results.add(outcome.copy(endpointName = name))
            }
        }
        return results
    }

    /** 异常 → 单行可读文本；message 为空时退回类名。 */
    private fun describe(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
