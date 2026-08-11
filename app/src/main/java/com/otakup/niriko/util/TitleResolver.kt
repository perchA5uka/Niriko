package com.otakup.niriko.util

/**
 * 统一标题解析工具。
 * 所有页面使用此工具，避免散落的 titleCN ?: title 判断。
 */
object TitleResolver {

    fun resolve(titleCN: String?, title: String): DisplayTitle {
        val primary = titleCN?.takeIf { it.isNotBlank() } ?: title
        val secondary = if (title != primary) title else null
        return DisplayTitle(primary = primary, secondary = secondary)
    }
}

data class DisplayTitle(
    val primary: String,
    val secondary: String?,
)
