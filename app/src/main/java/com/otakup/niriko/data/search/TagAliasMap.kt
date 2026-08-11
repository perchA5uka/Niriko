package com.otakup.niriko.data.search

/**
 * 标签/公司名变体映射表。
 *
 * Bangumi 用户打标存在大量变体（如「京都动画」「京阿尼」「京都アニメーション」「Kyoto Animation」
 * 指同一公司），而 v0 的 `filter.tag` 是**大小写敏感**的精确匹配。搜索时展开所有变体分别查询、
 * 合并去重，可显著提高公司名/标签的召回率（如搜小写 `mappa` 时用别名表里的 `MAPPA` 去查）。
 */
object TagAliasMap {

    /** 规范名 → 变体列表（含规范名本身）。 */
    private val ALIASES: Map<String, List<String>> = mapOf(
        "京都动画" to listOf("京都动画", "京阿尼", "京都アニメーション", "Kyoto Animation", "京都animation"),
        "MAPPA" to listOf("MAPPA", "マッパ", "Mappa"),
        "ufotable" to listOf("ufotable", "幽浮社", "Ufotable"),
        "Shaft" to listOf("Shaft", "シャフト", "SHAFT"),
        "MADHOUSE" to listOf("MADHOUSE", "マッドハウス", "Madhouse"),
        "BONES" to listOf("BONES", "ボンズ", "骨头社"),
        "WIT STUDIO" to listOf("WIT STUDIO", "WIT", "ウィットスタジオ"),
        "A-1 Pictures" to listOf("A-1 Pictures", "A1", "エー・ワン"),
        "CloverWorks" to listOf("CloverWorks", "クローバーワークス"),
        "Studio Bind" to listOf("Studio Bind", "スタジオバインド"),
        "J.C.STAFF" to listOf("J.C.STAFF", "JC社", "ジェーシースタッフ"),
        "David Production" to listOf("David Production", "デイヴィッドプロダクション"),
        "Studio Pierrot" to listOf("Studio Pierrot", "ぴえろ", "小丑社"),
        "Production I.G" to listOf("Production I.G", "プロダクション・アイジー", "Production I.G"),
        "Sunrise" to listOf("Sunrise", "サンライズ", "日升"),
        "Trigger" to listOf("Trigger", "トリガー", "扳机社"),
        "Lerche" to listOf("Lerche", "ラルケ"),
        "P.A.WORKS" to listOf("P.A.WORKS", "ピーエーワークス"),
        "WHITE FOX" to listOf("WHITE FOX", "ホワイトフォックス"),
        "SILVER LINK." to listOf("SILVER LINK.", "シルバーリンク"),
        "Gainax" to listOf("Gainax", "ガイナックス"),
        "Khara" to listOf("Khara", "カラー"),
        "Studio Ghibli" to listOf("Studio Ghibli", "ジブリ", "吉卜力"),
        "CoMix Wave Films" to listOf("CoMix Wave Films", "コミックス・ウェーブ・フィルム"),
        "GoHands" to listOf("GoHands", "ゴーハンズ"),
        "Diomedéa" to listOf("Diomedéa", "ディオメディア"),
    )

    /** 反向：任意变体 → 规范名。 */
    private val VARIANT_TO_CANON: Map<String, String> =
        ALIASES.flatMap { (canon, variants) -> variants.map { it to canon } }.toMap()

    /**
     * 展开输入词的所有变体。
     * - 命中别名表（输入词是某公司的任一变体）→ 返回该公司全部变体 **加上输入词本身**（去重），
     *   保证搜小写 `mappa` 时也会用别名表规范名 `MAPPA` 查询
     * - 未命中 → 返回原词本身
     */
    fun expand(keyword: String): List<String> {
        val trimmed = keyword.trim()
        val canon = VARIANT_TO_CANON[trimmed]
        return if (canon != null) {
            (listOf(trimmed) + (ALIASES[canon] ?: emptyList())).distinct()
        } else {
            listOf(trimmed)
        }
    }
}
