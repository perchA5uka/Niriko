package com.otakup.niriko.data.calculator

/**
 * 用 TMDb 名称补 Bangumi 缺失的分集标题（**纯函数，可单测**）。
 *
 * 背景（B13）：Bangumi 的部分词条会出现「某一集标题没取到」——列表和状态编辑页于是显示空标题。
 * TMDb 的 season 详情里每集都带 `name`，但对齐结果此前把它丢掉了
 * （[EpisodeAlignment.TmdbEp] 只保留评分/票数/剧照）。
 *
 * 规则只有一条：**只补空缺，绝不覆盖**。
 * 已有标题（哪怕与 TMDb 不一致）属于 Bangumi 的原始数据，改写它等于篡改来源。
 */
object EpisodeTitleBackfill {

    data class Fill(val epId: Long, val title: String)

    /**
     * @param matched 对齐结果（epId ↔ TMDb 单集，已带 name）
     * @param currentTitles 库里现有标题：epId → name（null 或空白视为缺失）
     */
    fun plan(
        matched: List<EpisodeAlignment.Aligned>,
        currentTitles: Map<Long, String?>,
    ): List<Fill> {
        val planned = LinkedHashMap<Long, String>()
        matched.forEach { aligned ->
            val title = aligned.tmdb.name?.trim().orEmpty()
            if (title.isEmpty()) return@forEach
            val existing = currentTitles[aligned.epId]?.trim().orEmpty()
            if (existing.isNotEmpty()) return@forEach
            planned.putIfAbsent(aligned.epId, title)
        }
        return planned.map { (epId, title) -> Fill(epId, title) }
    }
}
