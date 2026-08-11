package com.otakup.niriko.data.model

import com.otakup.niriko.data.local.entity.SubjectEntity

/**
 * 搜索建议项。
 * HistoryKeyword — 历史关键字匹配，点击填入搜索框
 * LocalSubject — 本地缓存作品标题匹配，点击直接导航
 * RemoteSuggestion — 远程 API 返回的作品，点击直接导航
 */
sealed class SuggestionItem {
    data class HistoryKeyword(val keyword: String) : SuggestionItem()
    data class LocalSubject(val subject: SubjectEntity) : SuggestionItem()
    data class RemoteSuggestion(val subject: SubjectEntity) : SuggestionItem()
}
