package com.otakup.niriko.data.remote.anilist

/**
 * AniList GraphQL 查询字符串。
 * 使用 \$ 转义避免 Kotlin 字符串模板解析。
 */
object AniListQueries {

    val SEARCH = (
        "query(\$search: String, \$type: MediaType, \$page: Int = 1, \$perPage: Int = 50) {\n" +
        "  Page(page: \$page, perPage: \$perPage) {\n" +
        "    pageInfo { total }\n" +
        "    media(search: \$search, type: \$type, sort: SEARCH_MATCH) {\n" +
        "      id\n" +
        "      title { romaji english native }\n" +
        "      type format episodes chapters volumes\n" +
        "      averageScore description\n" +
        "      coverImage { large extraLarge }\n" +
        "      startDate { year month day }\n" +
        "      genres tags { name }\n" +
        "      status source duration isAdult\n" +
        "      popularity favourites\n" +
        "    }\n" +
        "  }\n" +
        "}"
    )

    /** 稳定的基础查询：保证至少能拿到 AniList 基础详情。 */
    val DETAIL_BASIC = (
        "query(\$id: Int) {\n" +
        "  Media(id: \$id) {\n" +
        "    id\n" +
        "    title { romaji english native }\n" +
        "    type format episodes chapters volumes\n" +
        "    averageScore description\n" +
        "    coverImage { large extraLarge }\n" +
        "    startDate { year month day }\n" +
        "    genres tags { name } status source\n" +
        "    duration isAdult season seasonYear\n" +
        "  }\n" +
        "}"
    )

    /** 富信息查询：在基础字段上补充热度/排名/下一集等（失败不影响基础详情）。 */
    val DETAIL = (
        "query(\$id: Int) {\n" +
        "  Media(id: \$id) {\n" +
        "    id\n" +
        "    title { romaji english native }\n" +
        "    type format episodes chapters volumes\n" +
        "    averageScore description\n" +
        "    coverImage { large extraLarge }\n" +
        "    startDate { year month day }\n" +
        "    genres tags { name } status source\n" +
        "    duration isAdult season seasonYear\n" +
        "    popularity favourites\n" +
        "    rankings { rank type year season }\n" +
        "    nextAiringEpisode { episode airingAt }\n" +
        "    siteUrl\n" +
        "  }\n" +
        "}"
    )

    /**
     * 只取评分相关字段的轻量查询（AniList 权威评分源用）。
     *
     * 为什么单独一条：评分聚合会在详情页打开时对**每个**未绑定的动画跑一次，
     * 拿整份 DETAIL 是浪费——这里只要 averageScore / meanScore / 票源信息。
     */
    val RATING_ONLY = (
        "query(\$search: String, \$type: MediaType) {\n" +
        "  Page(page: 1, perPage: 5) {\n" +
        "    media(search: \$search, type: \$type, sort: SEARCH_MATCH) {\n" +
        "      id\n" +
        "      title { romaji english native }\n" +
        "      averageScore meanScore popularity favourites\n" +
        "      startDate { year }\n" +
        "      format episodes isAdult siteUrl\n" +
        "    }\n" +
        "  }\n" +
        "}"
    )

    val CHARACTERS = (
        "query(\$id: Int) {\n" +
        "  Media(id: \$id) {\n" +
        "    characters(perPage: 50, sort: [ROLE, ID]) {\n" +
        "      edges {\n" +
        "        role\n" +
        "        node { id name { full native } image { large } }\n" +
        "        voiceActors(sort: [ID]) { id name { full native } image { large } language }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}"
    )

    val STAFF = (
        "query(\$id: Int) {\n" +
        "  Media(id: \$id) {\n" +
        "    staff(perPage: 50, sort: [ID]) {\n" +
        "      edges {\n" +
        "        role\n" +
        "        node { id name { full native } image { large } }\n" +
        "      }\n" +
        "    }\n" +
        "  }\n" +
        "}"
    )
}
