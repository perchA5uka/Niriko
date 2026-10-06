package com.otakup.niriko.viewmodel

import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.SubjectRelationInfo

/**
 * 作品详情「扩展数据快照」（详情页十余路并行结果的合并快照）。
 *
 * ## 为什么单独成文件
 *
 * 这个快照的**写入**（[toExtendedSnapshot]）与**恢复**（[withExtendedSnapshot]）必须字段一一对应。
 * 漏一个字段的后果不是「少显示一点」，而是「二次进页面时那一块直接消失」——
 * 因为缓存命中路径会提前 `return`，产生该字段的那一路外部源根本不会再跑。
 * 把两份映射放进同一个文件、再用 `SubjectDetailSnapshotTest` 的反射用例锁住完备性，
 * 就是为了不再出现「写了但没恢复」这种只在这种缓存结构里才会发生的缺陷。
 */
internal data class ExtendedSnapshot(
    val characters: List<CharacterInfo>,
    val staff: List<StaffInfo>,
    val episodes: List<EpisodeInfo>,
    val ratingDistribution: Map<Int, Int>,
    val relations: List<SubjectRelationInfo>,
    val infoBox: List<InfoBoxEntry>,
    val steam: com.otakup.niriko.data.local.entity.SteamGameEntity?,
    val achievements: com.otakup.niriko.data.remote.steam.SteamAchievements?,
    val chartRank: com.otakup.niriko.data.remote.steam.SteamChartEntry?,
    val vndbBinding: com.otakup.niriko.data.local.entity.VndbBindingEntity?,
    val vndbDetail: com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto?,
    val vndbCandidates: List<com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto>,
    val anilistBinding: com.otakup.niriko.data.local.entity.AniListBindingEntity?,
    val anilistDetail: com.otakup.niriko.data.remote.game.GameItemDetail?,
    val anilistRichDetail: com.otakup.niriko.data.model.AniListRichDetail?,
    val anilistCandidates: List<com.otakup.niriko.data.match.MatchCandidate>,
    val anitabiCity: String?,
    val anitabiPoints: List<com.otakup.niriko.data.remote.anitabi.AnitabiLitePoint>,
    val anitabiPointsLength: Int,
    val anitabiImagesLength: Int,
    val guessYouLike: List<SubjectEntity>,
    val externalRatings: List<com.otakup.niriko.data.remote.rating.ExternalRating>,
    val steamMetacritic: com.otakup.niriko.data.remote.rating.ExternalRating?,
    val manualAwards: List<com.otakup.niriko.data.local.entity.ManualAwardEntity>,
    val episodeRatings: Map<Long, Map<String, com.otakup.niriko.data.local.entity.EpisodeRatingEntity>>,
    val episodeRatingLoad: com.otakup.niriko.data.repository.EpisodeRatingRepository.TmdbEpisodeLoad?,
    val imdbEpisodeLoad: com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEpisodeLoad?,
    val imdbEpisodeEntryEnabled: Boolean,
    val imdbEntry: com.otakup.niriko.data.repository.EpisodeRatingRepository.ImdbEntryStatus?,
    val myEpisodeRatings: Map<Long, Float>,
    val tmdbSupported: Boolean,
    val tmdbBinding: com.otakup.niriko.data.local.entity.SubjectExternalIdEntity?,
    val tmdbMovieBinding: com.otakup.niriko.data.local.entity.SubjectExternalIdEntity?,
    val tmdbDetail: com.otakup.niriko.data.remote.tmdb.dto.TmdbTvDetailDto?,
    val tmdbBackdrops: List<com.otakup.niriko.data.remote.tmdb.dto.TmdbImageDto>,
    val tmdbCandidates: List<com.otakup.niriko.data.remote.rating.RatingCandidate>,
    val tmdbPastedCandidate: com.otakup.niriko.data.remote.rating.RatingCandidate?,
    val episodeStills: Map<Long, String>,
    val doubanEnabled: Boolean,
    val doubanBoundId: String?,
    val doubanThumbs: List<String>,
    val doubanImageReferer: String?,
    val doubanCandidates: List<com.otakup.niriko.data.remote.douban.DoubanClient.DoubanSearchItem>,
    val vndbRelations: List<com.otakup.niriko.data.repository.VndbRepository.RelationWithTitle>,
    val vndbCandidateReasons: Map<String, List<String>>,
    val vndbRawMatchCount: Int,
    val vndbMatchReasons: List<String>,
)

/**
 * 写入：UI 状态 → 快照。
 *
 * 只抓「外部源产出的结果」。以下字段**刻意不进快照**（见 `SubjectDetailSnapshotTest` 的分类断言）：
 * - 加载/瞬时态：isLoading / isRefreshing / isExtendedLoading / isUpdating / 各类 *Loading；
 * - 一次性反馈与错误：error / snackbarMessage / *ManualMessage；
 * - 已在 loadCollectionState 单独加载的收藏与个人记录字段；
 * - 派生值：ratingDispute / localPercentile（恢复路径上由 loadRatingInsights 用
 *   snapshot.ratingDistribution 重算，进快照反而会存一份可能过期的副本）；
 * - 按需加载且与「打开选择器」这个交互绑定的 coverCandidates。
 */
internal fun SubjectDetailUiState.toExtendedSnapshot(): ExtendedSnapshot = ExtendedSnapshot(
    characters = characters,
    staff = staff,
    episodes = episodes,
    ratingDistribution = ratingDistribution,
    relations = relations,
    infoBox = infoBox,
    steam = steam,
    achievements = achievements,
    chartRank = chartRank,
    vndbBinding = vndbBinding,
    vndbDetail = vndbDetail,
    vndbCandidates = vndbCandidates,
    anilistBinding = anilistBinding,
    anilistDetail = anilistDetail,
    anilistRichDetail = anilistRichDetail,
    anilistCandidates = anilistCandidates,
    anitabiCity = anitabiCity,
    anitabiPoints = anitabiPoints,
    anitabiPointsLength = anitabiPointsLength,
    anitabiImagesLength = anitabiImagesLength,
    guessYouLike = guessYouLike,
    externalRatings = externalRatings,
    steamMetacritic = steamMetacritic,
    manualAwards = manualAwards,
    episodeRatings = episodeRatings,
    episodeRatingLoad = episodeRatingLoad,
    imdbEpisodeLoad = imdbEpisodeLoad,
    imdbEpisodeEntryEnabled = imdbEpisodeEntryEnabled,
    imdbEntry = imdbEntry,
    myEpisodeRatings = myEpisodeRatings,
    tmdbSupported = tmdbSupported,
    tmdbBinding = tmdbBinding,
    tmdbMovieBinding = tmdbMovieBinding,
    tmdbDetail = tmdbDetail,
    tmdbBackdrops = tmdbBackdrops,
    tmdbCandidates = tmdbCandidates,
    tmdbPastedCandidate = tmdbPastedCandidate,
    episodeStills = episodeStills,
    doubanEnabled = doubanEnabled,
    doubanBoundId = doubanBoundId,
    doubanThumbs = doubanThumbs,
    doubanImageReferer = doubanImageReferer,
    doubanCandidates = doubanCandidates,
    vndbRelations = vndbRelations,
    vndbCandidateReasons = vndbCandidateReasons,
    vndbRawMatchCount = vndbRawMatchCount,
    vndbMatchReasons = vndbMatchReasons,
)

/**
 * 恢复：把快照写回 UI 状态（缓存命中路径）。纯函数，因此可以在 JVM 单测里直接验证往返一致。
 *
 * 不设置 isExtendedLoading —— 那属于加载态，由调用方按自己的时序决定。
 */
internal fun SubjectDetailUiState.withExtendedSnapshot(snapshot: ExtendedSnapshot): SubjectDetailUiState = copy(
    characters = snapshot.characters,
    staff = snapshot.staff,
    episodes = snapshot.episodes,
    ratingDistribution = snapshot.ratingDistribution,
    relations = snapshot.relations,
    infoBox = snapshot.infoBox,
    steam = snapshot.steam,
    achievements = snapshot.achievements,
    chartRank = snapshot.chartRank,
    vndbBinding = snapshot.vndbBinding,
    vndbDetail = snapshot.vndbDetail,
    vndbCandidates = snapshot.vndbCandidates,
    anilistBinding = snapshot.anilistBinding,
    anilistDetail = snapshot.anilistDetail,
    anilistRichDetail = snapshot.anilistRichDetail,
    anilistCandidates = snapshot.anilistCandidates,
    anitabiCity = snapshot.anitabiCity,
    anitabiPoints = snapshot.anitabiPoints,
    anitabiPointsLength = snapshot.anitabiPointsLength,
    anitabiImagesLength = snapshot.anitabiImagesLength,
    guessYouLike = snapshot.guessYouLike,
    externalRatings = snapshot.externalRatings,
    steamMetacritic = snapshot.steamMetacritic,
    manualAwards = snapshot.manualAwards,
    episodeRatings = snapshot.episodeRatings,
    episodeRatingLoad = snapshot.episodeRatingLoad,
    imdbEpisodeLoad = snapshot.imdbEpisodeLoad,
    imdbEpisodeEntryEnabled = snapshot.imdbEpisodeEntryEnabled,
    imdbEntry = snapshot.imdbEntry,
    myEpisodeRatings = snapshot.myEpisodeRatings,
    tmdbSupported = snapshot.tmdbSupported,
    tmdbBinding = snapshot.tmdbBinding,
    tmdbMovieBinding = snapshot.tmdbMovieBinding,
    tmdbDetail = snapshot.tmdbDetail,
    tmdbBackdrops = snapshot.tmdbBackdrops,
    tmdbCandidates = snapshot.tmdbCandidates,
    tmdbPastedCandidate = snapshot.tmdbPastedCandidate,
    episodeStills = snapshot.episodeStills,
    doubanEnabled = snapshot.doubanEnabled,
    doubanBoundId = snapshot.doubanBoundId,
    doubanThumbs = snapshot.doubanThumbs,
    doubanImageReferer = snapshot.doubanImageReferer,
    doubanCandidates = snapshot.doubanCandidates,
    vndbRelations = snapshot.vndbRelations,
    vndbCandidateReasons = snapshot.vndbCandidateReasons,
    vndbRawMatchCount = snapshot.vndbRawMatchCount,
    vndbMatchReasons = snapshot.vndbMatchReasons,
)
