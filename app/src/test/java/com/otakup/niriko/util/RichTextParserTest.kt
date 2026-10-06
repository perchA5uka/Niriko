package com.otakup.niriko.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 简介富文本解析单测（P4 · R7）：HTML / BBCode 到段落与行内样式的纯函数行为。 */
class RichTextParserTest {

    private fun plain(raw: String?): String = RichTextParser.toPlainText(raw)

    private fun spans(raw: String): List<RichTextSpan> = RichTextParser.parse(raw).flatMap { it.spans }

    @Test
    fun plainTextStaysAsIs() {
        assertEquals("轻音少女们的日常。", plain("轻音少女们的日常。"))
        assertEquals(1, RichTextParser.parse("轻音少女们的日常。").size)
    }

    @Test
    fun blankInputYieldsNothing() {
        assertEquals(emptyList<RichTextParagraph>(), RichTextParser.parse(null))
        assertEquals(emptyList<RichTextParagraph>(), RichTextParser.parse(""))
        assertEquals(emptyList<RichTextParagraph>(), RichTextParser.parse("   "))
        assertEquals(emptyList<RichTextParagraph>(), RichTextParser.parse("<p></p>"))
        assertEquals("", plain(null))
    }

    @Test
    fun newlineSplitsParagraphs() {
        assertEquals("第一段\n\n第二段", plain("第一段\n第二段"))
        assertEquals(2, RichTextParser.parse("第一段\n第二段").size)
    }

    @Test
    fun repeatedNewlinesCollapse() {
        assertEquals("甲\n\n乙", plain("甲\n\n\n\n乙"))
        assertEquals("甲\n\n乙", plain("甲\r\n\r\n乙"))
    }

    @Test
    fun brIsHardBreakInsideParagraph() {
        assertEquals(1, RichTextParser.parse("上<br>下").size)
        assertEquals("上\n下", plain("上<br>下"))
        assertEquals("上\n下", plain("上<br/>下"))
        assertEquals("上\n下", plain("上<BR />下"))
    }

    @Test
    fun paragraphTagsSplitBlocks() {
        val result = RichTextParser.parse("<p>甲</p><p>乙</p>")
        assertEquals(2, result.size)
        assertEquals("甲", result[0].spans.joinToString("") { it.text })
        assertEquals("乙", result[1].spans.joinToString("") { it.text })
    }

    @Test
    fun boldAndItalicAreCaptured() {
        val result = spans("普通<b>粗</b><i>斜</i>收尾")
        assertEquals(listOf("普通", "粗", "斜", "收尾"), result.map { it.text })
        assertEquals(listOf(false, true, false, false), result.map { it.bold })
        assertEquals(listOf(false, false, true, false), result.map { it.italic })
    }

    @Test
    fun strongAndEmAreAliases() {
        val result = spans("<strong>粗</strong><em>斜</em>")
        assertTrue(result[0].bold)
        assertTrue(result[1].italic)
    }

    @Test
    fun anchorBecomesLinkSpan() {
        val result = spans("见 <a href=\"https://bgm.tv/subject/123\">作品页</a>。")
        val link = result.first { it.linkUrl != null }
        assertEquals("https://bgm.tv/subject/123", link.linkUrl)
        assertEquals("作品页", link.text)
    }

    @Test
    fun anchorWithoutHrefKeepsText() {
        val result = spans("<a>文字</a>")
        assertEquals("文字", result.joinToString("") { it.text })
        assertNull(result.first().linkUrl)
    }

    @Test
    fun bareUrlIsAutoLinked() {
        val link = spans("官网 https://example.com/a?b=1 结束").first { it.linkUrl != null }
        assertEquals("https://example.com/a?b=1", link.linkUrl)
        assertEquals("https://example.com/a?b=1", link.text)
    }

    @Test
    fun bareUrlTrailingPunctuationIsExcluded() {
        val link = spans("见 https://example.com/x。").first { it.linkUrl != null }
        assertEquals("https://example.com/x", link.linkUrl)
    }

    @Test
    fun explicitLinkIsNotAutoLinkedAgain() {
        val result = spans("<a href=\"https://example.com/x\">https://example.com/x</a>")
        assertEquals(1, result.size)
        assertEquals("https://example.com/x", result[0].linkUrl)
    }

    @Test
    fun unknownTagsKeepInnerText() {
        assertEquals("保留文字", plain("<unknown attr=\"1\">保留文字</unknown>"))
        assertEquals("保留文字", plain("<font color=red>保留文字</font>"))
        assertEquals("加粗", plain("<b>加粗</span>"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("A & B", plain("A &amp; B"))
        assertEquals("引号“x”", plain("引号&#8220;x&#8221;"))
        assertEquals("é", plain("&#233;"))
        assertEquals("😀", plain("&#x1F600;"))
        assertEquals("&unknown;", plain("&unknown;"))
    }

    @Test
    fun listItemsBecomeBullets() {
        val result = RichTextParser.parse("<ul><li>甲</li><li>乙</li></ul>")
        assertEquals(2, result.size)
        assertEquals("• 甲", result[0].spans.joinToString("") { it.text })
        assertEquals("• 乙", result[1].spans.joinToString("") { it.text })
    }

    @Test
    fun scriptAndStyleContentIsDropped() {
        assertEquals("正文", plain("<script>alert(1)</script>正文"))
        assertEquals("正文", plain("<style>p{color:red}</style>正文"))
    }

    @Test
    fun whitespaceIsCollapsed() {
        assertEquals("甲 乙", plain("甲    乙"))
        assertEquals("甲\n\n乙", plain("甲 \n 乙"))
    }

    @Test
    fun loneLessThanIsKeptAsText() {
        assertEquals("3 < 5 成立", plain("3 < 5 成立"))
    }

    @Test
    fun markupIsStrippedForPlainTextConsumers() {
        assertEquals("甲\n\n乙 与 丙", plain("<p>甲</p><p><b>乙</b> 与 <i>丙</i></p>"))
    }

    @Test
    fun bbcodeIsSupported() {
        assertEquals("作品页", plain("[url=https://bgm.tv/subject/123]作品页[/url]"))
        val link = spans("[url=https://bgm.tv/subject/123]作品页[/url]").first { it.linkUrl != null }
        assertEquals("https://bgm.tv/subject/123", link.linkUrl)
        assertTrue(spans("[b]粗[/b]")[0].bold)
        assertTrue(spans("[i]斜[/i]")[0].italic)
        assertEquals("图", plain("[img]x.png[/img]图"))
    }

    @Test
    fun bgmSubjectIdIsRecognized() {
        assertEquals(123L, RichTextParser.bgmSubjectIdFromUrl("https://bgm.tv/subject/123"))
        assertEquals(456L, RichTextParser.bgmSubjectIdFromUrl("http://bangumi.tv/subject/456/"))
        assertEquals(789L, RichTextParser.bgmSubjectIdFromUrl("https://bgm.tv/subject/789?from=summary"))
        assertNull(RichTextParser.bgmSubjectIdFromUrl("https://example.com/subject/9"))
        assertNull(RichTextParser.bgmSubjectIdFromUrl("https://bgm.tv/person/7"))
        assertNull(RichTextParser.bgmSubjectIdFromUrl(null))
    }

    @Test
    fun overlyLongPseudoTagIsKeptAsText() {
        val raw = "<" + "a".repeat(400) + ">正文"
        assertEquals(raw, plain(raw))
    }
}
