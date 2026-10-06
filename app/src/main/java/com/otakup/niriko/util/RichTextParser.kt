package com.otakup.niriko.util

/** 富文本行内片段：一段文字 + 行内样式（加粗 / 斜体 / 链接）。 */
data class RichTextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val linkUrl: String? = null,
)

/** 富文本段落：由块级元素（段落标签 / 换行 / 列表项）切分出的单位。 */
data class RichTextParagraph(val spans: List<RichTextSpan>)

/**
 * 简介富文本解析（P4 · R7，参考 halilozercan/compose-richtext 的强项：换行 / 加粗 / 链接）。
 *
 * 各数据源给的简介格式并不统一：Bangumi 是纯文本，AniList / Steam / VNDB 常带 HTML 片段。
 * 本解析器把两者归一成 [RichTextParagraph]，交给 「ui/components/RichText.kt」 渲染；
 * [toPlainText] 供只能用纯文本的地方（Canvas 分享卡、卡片副标题）使用。
 *
 * 不变量：
 * - 不丢字：不认识的标签只丢标签本身，内部文字照常保留；
 * - 纯文本换行、段落标签都算段落分隔，「br」算段落内硬换行；
 * - 没有配对的「<」、超长标签按普通文本处理，避免吞掉正文。
 */
object RichTextParser {

    /** 单个标签最多占用多少字符；更长的按普通文本处理。 */
    internal const val MAX_TAG_LENGTH = 200

    /** 解析简介，返回段落列表。 */
    fun parse(raw: String?): List<RichTextParagraph> {
        if (raw.isNullOrBlank()) return emptyList()
        val html = bbcodeToHtml(raw)
        val builder = ParagraphBuilder()
        var index = 0
        while (index < html.length) {
            val lt = html.indexOf("<", index)
            if (lt < 0) {
                builder.text(html.substring(index))
                break
            }
            if (lt > index) builder.text(html.substring(index, lt))
            val gt = html.indexOf(">", lt + 1)
            if (gt < 0 || gt - lt > MAX_TAG_LENGTH) {
                builder.text("<")
                index = lt + 1
                continue
            }
            builder.tag(html.substring(lt + 1, gt))
            index = gt + 1
        }
        builder.finish()
        return builder.paragraphs
    }

    /** 归一成纯文本（段落之间保留空行），供 Canvas 分享卡 / 卡片副标题使用。 */
    fun toPlainText(raw: String?): String =
        parse(raw).joinToString("\n\n") { paragraph -> paragraph.spans.joinToString("") { it.text } }

    /** 站内跳转：识别 Bangumi 作品链接里的作品 id（如 https://bgm.tv/subject/123）。 */
    fun bgmSubjectIdFromUrl(url: String?): Long? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return BGM_SUBJECT.find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun bbcodeToHtml(raw: String): String {
        var out = BBCODE_IMG.replace(raw, "")
        out = BBCODE_IMG_ONLY.replace(out, "")
        out = BBCODE_URL.replace(out) { match ->
            "<a href=\"" + match.groupValues[1] + "\">" + match.groupValues[2] + "</a>"
        }
        out = BBCODE_URL_BARE.replace(out) { match ->
            "<a href=\"" + match.groupValues[1] + "\">" + match.groupValues[1] + "</a>"
        }
        for ((from, to) in BBCODE_STYLE) out = out.replace(from, to, ignoreCase = true)
        return BBCODE_LEFTOVER.replace(out, "")
    }
}

/** 段落内的硬换行结尾标点（裸链接识别时排除）。 */
private const val TRAILING_PUNCTUATION = ".,;:!?、。，；：！？)]}》〉」』"

/** 裸链接（http(s)://…）。 */
private val BARE_URL = Regex("""https?://[^\s<>"'（）【】《》，。！？；：]+""", RegexOption.IGNORE_CASE)

/** Bangumi 作品链接。 */
private val BGM_SUBJECT = Regex("""^https?://(?:[a-z0-9-]+\.)*(?:bgm\.tv|bangumi\.tv)/subject/(\d+)""", RegexOption.IGNORE_CASE)

/** `<a href="…">` 的 href 取值（双引号 / 单引号 / 无引号）。 */
private val ATTR_HREF = Regex("""href\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)

/** HTML 实体（命名 / 十进制 / 十六进制）。 */
private val ENTITY = Regex("""&(#x?[0-9a-fA-F]+|[a-zA-Z]+);""")

/** BBCode 图片：简介里没有意义，整段丢弃。 */
private val BBCODE_IMG = Regex("""\[img(?:=[^\]]*)?\][\s\S]*?\[/img\]""", RegexOption.IGNORE_CASE)
private val BBCODE_IMG_ONLY = Regex("""\[img(?:=[^\]]*)?\]""", RegexOption.IGNORE_CASE)

/** BBCode 链接。 */
private val BBCODE_URL = Regex("""\[url=([^\]]+)\]([\s\S]*?)\[/url\]""", RegexOption.IGNORE_CASE)
private val BBCODE_URL_BARE = Regex("""\[url\]([\s\S]*?)\[/url\]""", RegexOption.IGNORE_CASE)

/** BBCode 带样式标签 → HTML（无对应样式的成对标签留给兜底清理）。 */
private val BBCODE_STYLE = listOf(
    "[b]" to "<b>", "[/b]" to "</b>",
    "[i]" to "<i>", "[/i]" to "</i>",
    "[u]" to "<u>", "[/u]" to "</u>",
    "[s]" to "<s>", "[/s]" to "</s>",
    "[del]" to "<s>", "[/del]" to "</s>",
    "[quote]" to "<p>", "[/quote]" to "</p>",
    "[mask]" to "<span>", "[/mask]" to "</span>",
    "[code]" to "<span>", "[/code]" to "</span>",
    "[list]" to "<p>", "[/list]" to "</p>",
    "[*]" to "<li>",
)

/** 兜底：清掉剩下的已知 BBCode 标签（颜色 / 字号 / 对齐等无样式对应）。 */
private val BBCODE_LEFTOVER = Regex(
    """\[/?(?:b|i|u|s|del|sup|sub|color|size|font|center|left|right|code|quote|mask|list|img)(?:=[^\]]*)?\]""",
    RegexOption.IGNORE_CASE,
)

/** 常见命名实体。 */
private val NAMED_ENTITIES = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
    "hellip" to "…",
    "mdash" to "—",
    "ndash" to "–",
    "middot" to "·",
)

/** 解码 HTML 实体；无法识别的原样保留。 */
private fun decodeEntities(raw: String): String {
    if (raw.indexOf('&') < 0) return raw
    return ENTITY.replace(raw) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                codePointToString(body.substring(2).toIntOrNull(16)) ?: match.value
            body.startsWith("#") -> codePointToString(body.substring(1).toIntOrNull()) ?: match.value
            else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
        }
    }
}

/** 码点 → 字符串（含补充平面字符）；非法码点返回 null。 */
private fun codePointToString(code: Int?): String? {
    if (code == null || code <= 0 || code > 0x10FFFF) return null
    return String(Character.toChars(code))
}

/** 逐字符扫描的段落构建器（内部实现）。 */
private class ParagraphBuilder {

    val paragraphs = mutableListOf<RichTextParagraph>()

    private val runs = mutableListOf<RichTextSpan>()
    private val text = StringBuilder()
    private val links = ArrayDeque<String?>()
    private var bold = 0
    private var italic = 0
    private var pendingSpace = false
    private var skipUntilTag: String? = null

    /** 普通文本：折叠空白，换行算段落分隔。 */
    fun text(raw: String) {
        if (skipUntilTag != null) return
        for (ch in decodeEntities(raw)) {
            when {
                ch == '\n' -> paragraphBreak()
                ch == ' ' || ch == '\t' || ch == '\r' -> if (hasParagraphContent()) pendingSpace = true
                else -> {
                    if (pendingSpace && hasParagraphContent() && text.lastOrNull() != '\n') text.append(' ')
                    pendingSpace = false
                    text.append(ch)
                }
            }
        }
    }

    /** 处理一个标签的内容（不含尖括号）。 */
    fun tag(content: String) {
        val raw = content.trim()
        if (raw.isEmpty() || raw.startsWith("!") || raw.startsWith("?")) return
        val closing = raw.startsWith("/")
        val body = if (closing) raw.substring(1).trim() else raw
        val name = body.takeWhile { it.isLetterOrDigit() }.lowercase()
        if (name.isEmpty()) return
        val skipping = skipUntilTag
        if (skipping != null) {
            if (closing && name == skipping) skipUntilTag = null
            return
        }
        when (name) {
            "br" -> hardBreak()
            "b", "strong" -> {
                flushRun()
                if (closing) bold = (bold - 1).coerceAtLeast(0) else bold++
            }
            "i", "em" -> {
                flushRun()
                if (closing) italic = (italic - 1).coerceAtLeast(0) else italic++
            }
            "a" -> if (closing) {
                flushRun()
                if (links.isNotEmpty()) links.removeLast()
            } else {
                flushRun()
                val href = ATTR_HREF.find(body)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                links.addLast(href?.let { decodeEntities(it.trim()) })
            }
            "p", "div", "li", "tr", "ul", "ol", "table", "section", "article", "blockquote",
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                paragraphBreak()
                if (!closing && name == "li") text("• ")
            }
            "script", "style", "svg", "iframe", "video", "audio", "object", "template" ->
                if (!closing) skipUntilTag = name
            else -> Unit
        }
    }

    fun finish() = flushParagraph()

    /** 段落内的硬换行。 */
    private fun hardBreak() {
        if (skipUntilTag != null) return
        pendingSpace = false
        while (text.isNotEmpty() && (text.last() == ' ' || text.last() == '\n')) {
            text.deleteCharAt(text.length - 1)
        }
        if (text.isNotEmpty()) text.append('\n')
    }

    private fun flushRun() {
        if (text.isEmpty()) return
        if (pendingSpace) {
            text.append(' ')
            pendingSpace = false
        }
        val value = text.toString()
        text.setLength(0)
        runs.add(RichTextSpan(value, bold = bold > 0, italic = italic > 0, linkUrl = links.lastOrNull()))
    }

    private fun hasParagraphContent(): Boolean = text.isNotEmpty() || runs.isNotEmpty()

    private fun paragraphBreak() {
        pendingSpace = false
        flushRun()
        flushParagraph()
    }

    private fun flushParagraph() {
        flushRun()
        if (runs.isEmpty()) return
        val merged = normalize(runs)
        runs.clear()
        if (merged.isNotEmpty()) paragraphs.add(RichTextParagraph(merged))
    }

    /** 合并同样式片段、折叠空白与空行、去掉首尾空白，最后做裸链接识别。 */
    private fun normalize(source: List<RichTextSpan>): List<RichTextSpan> {
        val merged = mutableListOf<RichTextSpan>()
        for (span in source) {
            var value = span.text
            while (value.contains("\n\n")) value = value.replace("\n\n", "\n")
            if (value.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.bold == span.bold && last.italic == span.italic && last.linkUrl == span.linkUrl) {
                merged[merged.size - 1] = last.copy(text = last.text + value)
            } else {
                merged.add(span.copy(text = value))
            }
        }
        while (merged.isNotEmpty() && isBlank(merged.first().text)) merged.removeAt(0)
        while (merged.isNotEmpty() && isBlank(merged.last().text)) merged.removeAt(merged.size - 1)
        if (merged.isNotEmpty()) {
            merged[0] = merged[0].copy(text = merged[0].text.trimStart(' ', '\n', '\t'))
            val lastIndex = merged.size - 1
            merged[lastIndex] = merged[lastIndex].copy(text = merged[lastIndex].text.trimEnd(' ', '\n', '\t'))
        }
        return autoLink(merged)
    }

    private fun isBlank(value: String): Boolean =
        value.all { it == ' ' || it == '\n' || it == '\t' }

    /** 把没有链接的文本里的裸 URL 变成链接片段。 */
    private fun autoLink(source: List<RichTextSpan>): List<RichTextSpan> {
        val out = mutableListOf<RichTextSpan>()
        for (span in source) {
            if (span.linkUrl != null) {
                out.add(span)
                continue
            }
            var cursor = 0
            for (match in BARE_URL.findAll(span.text)) {
                var url = match.value
                while (url.isNotEmpty() && TRAILING_PUNCTUATION.indexOf(url.last()) >= 0) {
                    url = url.dropLast(1)
                }
                if (url.length <= 10) continue
                val start = match.range.first
                if (start > cursor) out.add(span.copy(text = span.text.substring(cursor, start)))
                out.add(span.copy(text = url, linkUrl = url))
                cursor = start + url.length
            }
            if (cursor < span.text.length) out.add(span.copy(text = span.text.substring(cursor)))
        }
        return out.filter { it.text.isNotEmpty() }
    }
}
