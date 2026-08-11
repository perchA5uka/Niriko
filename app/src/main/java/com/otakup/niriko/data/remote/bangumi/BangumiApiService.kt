package com.otakup.niriko.data.remote.bangumi

import com.otakup.niriko.data.remote.bangumi.dto.CalendarDayDto
import com.otakup.niriko.data.remote.bangumi.dto.CharacterDetailDto
import com.otakup.niriko.data.remote.bangumi.dto.CharacterDto
import com.otakup.niriko.data.remote.bangumi.dto.EpisodeResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.PersonDto
import com.otakup.niriko.data.remote.bangumi.dto.PersonSearchResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.PersonSubjectDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchRequestDto
import com.otakup.niriko.data.remote.bangumi.dto.SearchResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.StaffDto
import com.otakup.niriko.data.remote.bangumi.dto.SubjectDetailResponseDto
import com.otakup.niriko.data.remote.bangumi.dto.SubjectRelationDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BangumiApiService {

    @POST("v0/search/subjects")
    suspend fun searchSubjects(
        @Body request: SearchRequestDto,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): SearchResponseDto

    /** 人物搜索（声优/导演/作者等）。body 只需 keyword，可选 filter.career 按职业过滤。 */
    @POST("v0/search/persons")
    suspend fun searchPersons(
        @Body request: SearchRequestDto,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): PersonSearchResponseDto

    @GET("v0/subjects/{subjectId}")
    suspend fun getSubjectDetail(
        @Path("subjectId") subjectId: Long,
    ): SubjectDetailResponseDto

    /** 获取当季放送日历，按星期分组。Bangumi 的日历 API 不带 v0 前缀。 */
    @GET("calendar")
    suspend fun getCalendar(): List<CalendarDayDto>

    /**
     * 按月浏览条目（主要用于非当季月份的数据获取）。
     * schema: Paged_Subject（与 SearchResponseDto 结构一致）。
     */
    @GET("v0/subjects")
    suspend fun getSubjectsByMonth(
        @Query("type") type: Int,
        @Query("sort") sort: String = "date",
        @Query("year") year: Int,
        @Query("month") month: Int,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): SearchResponseDto

    /**
     * 按开播日期范围查询条目（跨月延续用）。
     * filter.air_date = [">=startDate", "<endDate"]，可跨月。
     */
    @POST("v0/search/subjects")
    suspend fun searchSubjectsByDateRange(
        @Body request: SearchRequestDto,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): SearchResponseDto

    /**
     * 获取类型排名榜（GET，绕开 POST 在代理环境下 body 被吞的问题）。
     * sort=rank 按 Bangumi 排名升序。分页用 offset。
     */
    @GET("v0/subjects")
    suspend fun getRankingByType(
        @Query("type") type: Int,
        @Query("sort") sort: String = "rank",
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): SearchResponseDto

    /** 获取角色列表。 */
    @GET("v0/subjects/{subjectId}/characters")
    suspend fun getCharacters(
        @Path("subjectId") subjectId: Long,
    ): List<CharacterDto>

    /** 获取制作人员列表。 */
    @GET("v0/subjects/{subjectId}/persons")
    suspend fun getPersons(
        @Path("subjectId") subjectId: Long,
    ): List<StaffDto>

    /** 获取剧集列表。 */
    @GET("v0/episodes")
    suspend fun getEpisodes(
        @Query("subject_id") subjectId: Long,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): EpisodeResponseDto

    // ==================== 详情扩展（角色/人物/关联） ====================

    /** 获取角色详情。 */
    @GET("v0/characters/{characterId}")
    suspend fun getCharacterDetail(
        @Path("characterId") characterId: Long,
    ): CharacterDetailDto

    /** 获取角色出演作品列表。 */
    @GET("v0/characters/{characterId}/subjects")
    suspend fun getCharacterSubjects(
        @Path("characterId") characterId: Long,
    ): List<PersonSubjectDto>

    /** 获取人物（声优/导演等）详情。 */
    @GET("v0/persons/{personId}")
    suspend fun getPersonDetail(
        @Path("personId") personId: Long,
    ): PersonDto

    /** 获取人物参与的作品列表。 */
    @GET("v0/persons/{personId}/subjects")
    suspend fun getPersonSubjects(
        @Path("personId") personId: Long,
    ): List<PersonSubjectDto>

    /** 获取人物演绎的角色列表（声优最近角色）。 */
    @GET("v0/persons/{personId}/characters")
    suspend fun getPersonCharacters(
        @Path("personId") personId: Long,
    ): List<CharacterDto>

    /** 获取条目关联（前后传/版本/系列等）。 */
    @GET("v0/subjects/{subjectId}/subjects")
    suspend fun getSubjectRelations(
        @Path("subjectId") subjectId: Long,
    ): List<SubjectRelationDto>
}