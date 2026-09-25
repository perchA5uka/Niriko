package com.otakup.niriko.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

/**
 * 拼音搜索工具（阶段 D）。
 * 使用 pinyin4j（Maven Central）把中文标题转成小写全拼音，供搜索命中「拼音 / 部分罗马音 / 原文」。
 */
object PinyinSearch {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    /** 中文 → 全拼音小写（无分隔）；非中文字符原样小写。 */
    fun pinyinOf(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val arr = runCatching { PinyinHelper.toHanyuPinyinStringArray(ch, format) }.getOrNull()
            if (arr != null && arr.isNotEmpty()) sb.append(arr[0]) else sb.append(ch.lowercaseChar())
        }
        return sb.toString()
    }

    /** 作品搜索键：拼音(标题/CN) + 原文(标题/CN) 小写合并。 */
    fun searchKey(title: String?, titleCN: String?): String =
        (pinyinOf(title ?: "") + " " + pinyinOf(titleCN ?: "") + " " + (title ?: "") + " " + (titleCN ?: "")).lowercase()

    /** 关键词是否命中标题的拼音 / 原文 / 中文名。 */
    fun matches(keyword: String, title: String?, titleCN: String?): Boolean {
        val k = keyword.trim().lowercase()
        if (k.isEmpty()) return false
        if (title != null && title.lowercase().contains(k)) return true
        if (titleCN != null && titleCN.lowercase().contains(k)) return true
        val key = searchKey(title, titleCN)
        return key.contains(k) || (pinyinOf(k).isNotBlank() && key.contains(pinyinOf(k)))
    }

    /** 为条目计算持久化的 pinyinKey（数据库字段，便于 SQL LIKE 命中）。 */
    fun pinyinKey(title: String?, titleCN: String?): String = searchKey(title, titleCN)
}
