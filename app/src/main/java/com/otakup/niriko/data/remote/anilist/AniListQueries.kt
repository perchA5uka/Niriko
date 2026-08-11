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
        "    }\n" +
        "  }\n" +
        "}"
    )

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
