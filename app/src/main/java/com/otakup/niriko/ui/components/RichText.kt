package com.otakup.niriko.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.otakup.niriko.util.RichTextParser

/**
 * 简介富文本渲染（P4 · R7）。
 *
 * 把 [com.otakup.niriko.util.RichTextParser] 解析出的段落（换行 / 加粗 / 斜体 / 链接）画成
 * 一个 Text：段落之间空一行，链接可点 —— 站内链接（Bangumi 作品页）优先交给 [onSubjectLink]，
 * 其余走 [onLink] 或系统浏览器。
 *
 * 纯文本输入同样适用（解析器会把纯文本当段落处理）。
 */
@Composable
fun RichText(
    text: String?,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onSubjectLink: ((Long) -> Unit)? = null,
    onLink: ((String) -> Unit)? = null,
) {
    val paragraphs = remember(text) { RichTextParser.parse(text) }
    if (paragraphs.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    val annotated = remember(paragraphs, linkColor, onSubjectLink, onLink, uriHandler) {
        buildAnnotatedString {
            paragraphs.forEachIndexed { index, paragraph ->
                if (index > 0) append("\n\n")
                for (span in paragraph.spans) {
                    val spanStyle = SpanStyle(
                        fontWeight = if (span.bold) FontWeight.SemiBold else null,
                        fontStyle = if (span.italic) FontStyle.Italic else null,
                    )
                    val url = span.linkUrl
                    if (url == null) {
                        withStyle(spanStyle) { append(span.text) }
                    } else {
                        withLink(
                            LinkAnnotation.Url(
                                url = url,
                                styles = TextLinkStyles(
                                    style = spanStyle.copy(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    ),
                                ),
                                linkInteractionListener = { clicked ->
                                    val target = (clicked as? LinkAnnotation.Url)?.url
                                    if (target != null) {
                                        val subjectId = RichTextParser.bgmSubjectIdFromUrl(target)
                                        when {
                                            subjectId != null && onSubjectLink != null -> onSubjectLink(subjectId)
                                            onLink != null -> onLink(target)
                                            else -> runCatching { uriHandler.openUri(target) }
                                        }
                                    }
                                },
                            ),
                        ) {
                            append(span.text)
                        }
                    }
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}
