package com.otakup.niriko.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDb v3 DTO。
 *
 * 约定：**title / overview / name 一律可空**。
 * AniShelf 的实战教训——TMDb 在部分语言下会返回 null（例如 tv/35610 的 zh-TW），
 * 若把 DTO 字段声明为非空，一条稀疏翻译就会让整条记录解析失败。
 */

@Serializable
data class TmdbConfigurationDto(
    val images: TmdbImagesConfigDto? = null,
)

@Serializable
data class TmdbImagesConfigDto(
    @SerialName("secure_base_url") val secureBaseUrl: String? = null,
    @SerialName("poster_sizes") val posterSizes: List<String> = emptyList(),
    @SerialName("backdrop_sizes") val backdropSizes: List<String> = emptyList(),
    @SerialName("still_sizes") val stillSizes: List<String> = emptyList(),
    @SerialName("profile_sizes") val profileSizes: List<String> = emptyList(),
)

/** /search/tv 与 /search/movie 通用分页壳。 */
@Serializable
data class TmdbPagedDto<T>(
    val page: Int = 1,
    val results: List<T> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 0,
    @SerialName("total_results") val totalResults: Int = 0,
)

@Serializable
data class TmdbTvSummaryDto(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("genre_ids") val genreIds: List<Int> = emptyList(),
    val popularity: Double? = null,
)

@Serializable
data class TmdbMovieSummaryDto(
    val id: Int = 0,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val releaseDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val popularity: Double? = null,
)

@Serializable
data class TmdbTvDetailDto(
    val id: Int = 0,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    val homepage: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("last_air_date") val lastAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    val networks: List<TmdbNamedDto> = emptyList(),
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),
    @SerialName("production_companies") val productionCompanies: List<TmdbNamedDto> = emptyList(),
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null,
)

@Serializable
data class TmdbGenreDto(val id: Int = 0, val name: String? = null)

@Serializable
data class TmdbNamedDto(val id: Int = 0, val name: String? = null, @SerialName("logo_path") val logoPath: String? = null)

@Serializable
data class TmdbSeasonSummaryDto(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_count") val episodeCount: Int? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
)

@Serializable
data class TmdbSeasonDetailDto(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val episodes: List<TmdbEpisodeDto> = emptyList(),
)

/**
 * 季详情里的单集。
 *
 * **这是整个每集评分功能的核心**：TMDb 一次 season 调用就返回整季 episodes[]，
 * 每集自带 [voteAverage] / [voteCount] / [stillPath]，无需逐集请求。
 */
@Serializable
data class TmdbEpisodeDto(
    val id: Int = 0,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("episode_number") val episodeNumber: Int = 0,
    @SerialName("season_number") val seasonNumber: Int = 0,
    @SerialName("episode_type") val episodeType: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    val runtime: Int? = null,
    @SerialName("still_path") val stillPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
)

@Serializable
data class TmdbExternalIdsDto(
    val id: Int = 0,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Long? = null,
    @SerialName("wikidata_id") val wikidataId: String? = null,
    @SerialName("twitter_id") val twitterId: String? = null,
    @SerialName("instagram_id") val instagramId: String? = null,
)

@Serializable
data class TmdbMovieDetailDto(
    val id: Int = 0,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    val homepage: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("original_language") val originalLanguage: String? = null,
    val genres: List<TmdbGenreDto> = emptyList(),
    @SerialName("production_companies") val productionCompanies: List<TmdbNamedDto> = emptyList(),
    @SerialName("external_ids") val externalIds: TmdbExternalIdsDto? = null,
)

/** /tv/{id}/images 响应。 */
@Serializable
data class TmdbImagesDto(
    val id: Int = 0,
    val backdrops: List<TmdbImageDto> = emptyList(),
    val posters: List<TmdbImageDto> = emptyList(),
    val logos: List<TmdbImageDto> = emptyList(),
)

@Serializable
data class TmdbImageDto(
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("aspect_ratio") val aspectRatio: Double? = null,
    val width: Int = 0,
    val height: Int = 0,
    @SerialName("iso_639_1") val languageCode: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
)

/** /find/{external_id} 响应（用于从 IMDb id 反查 TMDb）。 */
@Serializable
data class TmdbFindResultDto(
    @SerialName("tv_results") val tvResults: List<TmdbTvSummaryDto> = emptyList(),
    @SerialName("movie_results") val movieResults: List<TmdbMovieSummaryDto> = emptyList(),
)
