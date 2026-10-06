@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.subject

import com.otakup.niriko.ui.animation.CompletionCelebrationHost
import com.otakup.niriko.ui.animation.CompletionCelebrationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.otakup.niriko.ui.animation.AmbientTiltDemand
import com.otakup.niriko.ui.animation.LocalAmbientTilt
import com.otakup.niriko.ui.animation.LocalAmbientTiltDemand
import com.otakup.niriko.ui.animation.rememberAmbientTilt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import com.otakup.niriko.ui.animation.subjectCoverPresentation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.os.Build
import androidx.compose.ui.platform.LocalHapticFeedback
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.Coil
import coil.request.ImageRequest
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.content.Context
import android.net.Uri
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.AniListRichDetail
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.remote.steam.SteamAchievements
import com.otakup.niriko.data.remote.steam.SteamChartEntry
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.PortalAction
import com.otakup.niriko.data.model.PortalIds
import com.otakup.niriko.data.model.PortalRegistry
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.verbFor
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.game.PlaytimeConverter
import com.otakup.niriko.data.remote.vndb.formatPlaytime
import com.otakup.niriko.data.remote.vndb.vndbLanguageName
import com.otakup.niriko.ui.adaptive.AdaptiveDetailScaffold
import com.otakup.niriko.ui.adaptive.NirikoDetailPane
import com.otakup.niriko.ui.animation.AnimDurationNormal
import com.otakup.niriko.ui.animation.CrtOverlay
import com.otakup.niriko.ui.animation.rememberEpisodeCompletionWave
import com.otakup.niriko.ui.animation.halftoneDots
import com.otakup.niriko.ui.animation.RevealOnScroll
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.data.model.cardMaterialFromRating
import com.otakup.niriko.ui.animation.pressTilt
import com.otakup.niriko.ui.share.ShareBitmapHost
import com.otakup.niriko.ui.share.ShareCardData
import com.otakup.niriko.ui.share.SharePreviewSheet
import com.otakup.niriko.ui.common.rememberImageLuminance
import com.otakup.niriko.ui.components.CoverImage
import com.otakup.niriko.ui.components.LocalDetailCoverFallback
import com.otakup.niriko.ui.components.GlassRatingSlider
import com.otakup.niriko.ui.components.GlassSectionCard
import com.otakup.niriko.ui.components.GlassToggle
import com.otakup.niriko.ui.components.ProvideCardGlassBackdrop
import com.otakup.niriko.ui.components.pageCardGlassBackdrop
import com.otakup.niriko.ui.components.rememberPageCardGlassBackdrop
import com.otakup.niriko.ui.components.RichText
import com.otakup.niriko.ui.components.topContentAlphaMask
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.foundation.layout.asPaddingValues
import com.otakup.niriko.ui.components.TagChip
import com.otakup.niriko.ui.components.searchGlassSurface
import com.otakup.niriko.ui.components.appleGlassCard
import com.otakup.niriko.nirikoApp
import com.otakup.niriko.ui.components.liquidglass.loadBlurredCover
import com.otakup.niriko.data.settings.GlassEffectLevel
import com.otakup.niriko.ui.theme.LocalDarkTheme
import com.otakup.niriko.ui.theme.LocalGlassEffect
import com.otakup.niriko.ui.theme.statusTone
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.otakup.niriko.ui.share.ShareFlow
import com.otakup.niriko.util.RichTextParser
import com.otakup.niriko.util.TopFadePolicy
import com.otakup.niriko.util.TitleResolver
import com.otakup.niriko.util.PortalLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.components.SkeletonBlock
import com.otakup.niriko.ui.components.WindowBlurBehindEffect
import com.otakup.niriko.ui.components.skeletonBaseColor
import com.otakup.niriko.ui.components.skeletonShimmer
import com.otakup.niriko.ui.theme.NirikoTheme
import com.otakup.niriko.viewmodel.SubjectDetailUiState
import com.otakup.niriko.viewmodel.SubjectDetailViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ManageableStatuses = listOf(
    WatchStatus.PLAN_TO_WATCH, WatchStatus.WATCHING, WatchStatus.COMPLETED, WatchStatus.ON_HOLD, WatchStatus.DROPPED,
)
private val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** 传送门可展示项。 */
private data class PortalUiItem(
    val label: String,
    val subtitle: String?,
    val actions: List<PortalAction>,
)

/** 组装传送门可展示项：按作品类型过滤，缺失 ID 的目标自动隐藏。 */
private fun buildPortalUiItems(subject: SubjectEntity, state: SubjectDetailUiState, context: Context): List<PortalUiItem> {
    val ids = buildPortalIds(subject, state)
    return PortalRegistry.targetsFor(subject.type)
        .mapNotNull { target ->
            val actions = target.resolve(subject, ids)
            if (actions.isEmpty()) return@mapNotNull null
            PortalUiItem(
                label = target.label,
                subtitle = portalSubtitle(actions),
                actions = actions,
            )
        }
        // 未安装的「仅拉起」目标（如 Mihon）自动隐藏；带网页兜底的目标必然可启动
        .filter { PortalLauncher.anyLaunchable(context, it.actions) }
}

/** 从详情状态提取跨平台 ID（用于精确跳转）。 */
private fun buildPortalIds(subject: SubjectEntity, state: SubjectDetailUiState): PortalIds = PortalIds(
    biliSeasonId = subject.biliSeasonId,
    steamAppId = state.steam?.appId ?: subject.sourceKey?.steamAppIdFromSourceKey(),
    vndbId = state.vndbBinding?.vndbId,
    anilistId = state.anilistBinding?.anilistId,
)

/** Steam 独立条目 sourceKey="steam:{appid}" 兜底取 appid。 */
private fun String.steamAppIdFromSourceKey(): Int? =
    if (startsWith("steam:")) removePrefix("steam:").toIntOrNull() else null

/** 副文案：精确 scheme 显示目标、搜索显示要搜的标题、网页/拉起给提示。 */
private fun portalSubtitle(actions: List<PortalAction>): String? =
    when (val a = actions.firstOrNull()) {
        is PortalAction.UriScheme -> Uri.parse(a.uri).getQueryParameter("keyword")?.let { "搜索：$it" }
        is PortalAction.WebUrl -> "打开网页"
        is PortalAction.LaunchPackage -> "打开 App"
        else -> null
    }


/** 已听曲目 id 集合的 rememberSaveable Saver。 */
private val TrackIdSetSaver = listSaver<Set<Long>, Long>(
    save = { it.toList() },
    restore = { it.toSet() },
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SubjectDetailScreen(
    viewModel: SubjectDetailViewModel,
    onBack: () -> Unit,
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    onRelationClick: (Long) -> Unit = {},
    onViewAllStaffClick: () -> Unit = {},
    /** 打开单集二级页（阶段 B）：由 NavHost 注入，跳 episode_detail/{subjectId}/{epId}。 */
    onOpenEpisodeDetail: (Long) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val ambientDemand = remember { AmbientTiltDemand() }
    val ambient = rememberAmbientTilt(active = true, visibleRarity = ambientDemand.visibleRarity)
    val snackbarHostState = remember { SnackbarHostState() }
    val completionPlayback = remember { CompletionCelebrationState() }
    val haptic = LocalHapticFeedback.current

    // Snackbar 消息处理（保存反馈 + 自动返回）
    // 注意：showSnackbar 是挂起等待 Snackbar 消失的，不能阻塞 onBack()，
    // 否则保存后要等 3-4s 才返回（用户感知为“保存延迟”）。
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.snackbarMessage, completionPlayback.isActive) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        if (msg == "记录已保存") {
            // Saving must not dispose the editor window before its celebration finishes.
            if (completionPlayback.isActive) return@LaunchedEffect
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            onBack()
        } else {
            snackbarHostState.showSnackbar(msg)
        }
    }

    CompositionLocalProvider(LocalAmbientTilt provides ambient, LocalAmbientTiltDemand provides ambientDemand) {
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // The route viewport starts at window y=0; only the header owns the top inset.
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier,
    ) { innerPadding ->
        val tagDeleteCallback: (String) -> Unit = { tag ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已删除标签 $tag")
            }
        }
        SubjectDetailContent(
            state = state,
            completionEvents = viewModel.completionEvents,
            // F14：进度写满的独立通道（剧集区块的波浪点亮）
            episodeCompletionEvents = viewModel.episodeCompletionEvents,
            completionPlayback = completionPlayback,
            onBack = onBack,
            onRetry = viewModel::retry,
            onAddToCollection = viewModel::addToCollection,
            onUpdateStatus = viewModel::updateStatus,
            onRemoveFromCollection = viewModel::removeFromCollection,
            onSaveRecord = viewModel::saveRecord,
            onTagDeleted = tagDeleteCallback,
            onCharacterClick = onCharacterClick,
            onPersonClick = onPersonClick,
            onRelationClick = onRelationClick,
            onViewAllStaffClick = onViewAllStaffClick,
            onRematchPlaceholder = {
                coroutineScope.launch { viewModel.rematchPlaceholder() }
            },
            onUnbindSteam = viewModel::unbindSteam,
            onBindVndb = viewModel::bindVndb,
            onUnbindVndb = viewModel::unbindVndb,
            onSearchMoreVndb = viewModel::searchMoreVndb,
            onRetryVndb = viewModel::refreshVndb,
            onBindAnilist = viewModel::bindAnilist,
            onUnbindAnilist = viewModel::unbindAnilist,
            onSearchMoreAnilist = viewModel::searchMoreAnilist,
            onClearAnilistManualMessage = viewModel::clearAnilistManualMessage,
            onRetryAnilist = viewModel::refreshAnilist,
            ratingCallbacks = RatingSectionCallbacks(
                onRefreshRatings = viewModel::refreshExternalRatings,
                onAddManualAward = viewModel::addManualAward,
                onRemoveManualAward = viewModel::removeManualAward,
                onLoadImdbEpisodes = viewModel::loadImdbEpisodeRatings,
                onSaveMyEpisodeRating = viewModel::saveMyEpisodeRating,
                onBindTmdb = viewModel::bindTmdb,
                onUnbindTmdb = viewModel::unbindTmdb,
                onSelectTmdbSeason = viewModel::setTmdbSeason,
                onSearchMoreTmdb = viewModel::searchMoreTmdb,
                onPasteTmdbId = viewModel::pasteTmdbIdOrUrl,
                onClearTmdbManualMessage = viewModel::clearTmdbManualMessage,
                onRequestCoverCandidates = viewModel::loadCoverCandidates,
                onOpenEpisodeDetail = onOpenEpisodeDetail,
                onLoadEpisodeReviewEntries = viewModel::loadEpisodeReviewEntries,
                onSearchDouban = viewModel::searchDoubanCandidates,
                onPasteDoubanId = viewModel::pasteDoubanId,
                onBindDouban = viewModel::bindDouban,
                onUnbindDouban = viewModel::unbindDouban,
                onDismissDoubanCandidates = viewModel::clearDoubanCandidates,
            ),
            onPortalUnavailable = { coroutineScope.launch { snackbarHostState.showSnackbar("未安装或无法打开") } },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.padding(innerPadding),
        )
    }
    }
}

/**
 * 权威评分 / 每集评分区块的回调集合。
 *
 * 用一个参数对象承载 7 个新回调，避免给详情页再摊平 7 个参数（Content / Body 两层都要传）。
 */
data class RatingSectionCallbacks(
    val onRefreshRatings: () -> Unit = {},
    val onAddManualAward: (String, Float?, Float, Int?, String?) -> Unit = { _, _, _, _, _ -> },
    val onRemoveManualAward: (Long) -> Unit = {},
    val onLoadImdbEpisodes: () -> Unit = {},
    val onSaveMyEpisodeRating: (Long, Float?) -> Unit = { _, _ -> },
    val onBindTmdb: (com.otakup.niriko.data.remote.rating.RatingCandidate) -> Unit = {},
    val onUnbindTmdb: () -> Unit = {},
    /** 计划 B3 · 4-14：手动指定 TMDb 季号（多季作品自动挑季挑错时的出口）。 */
    val onSelectTmdbSeason: (Int) -> Unit = {},
    val onSearchMoreTmdb: (String) -> Unit = {},
    /** 第 4 轮 C：粘贴 TMDb ID / 链接（手动入口）。 */
    val onPasteTmdbId: (String) -> Unit = {},
    val onClearTmdbManualMessage: () -> Unit = {},
    /** 跳到「权威数据源」设置（IMDb 不可用时的一键修复入口）。 */
    val onOpenRatingSettings: () -> Unit = {},
    val onRequestCoverCandidates: () -> Unit = {},
    /** 打开单集二级页（阶段 B）。 */
    val onOpenEpisodeDetail: (Long) -> Unit = {},
    /**
     * F07：加载「分集评价 → 作品感想/均分」所需的条目（集号标签 + 我的分数 + 我的短评）。
     *
     * 参数是「拿到结果后的回调」而不是返回值：数据来自 Room，而调用它的地方在组合里
     * （预览弹层的打开动作），不可能为了一个列表去做阻塞查询。
     */
    val onLoadEpisodeReviewEntries: ((List<com.otakup.niriko.util.EpisodeReviewMergePolicy.Entry>) -> Unit) -> Unit =
        { callback -> callback(emptyList()) },
    /** 豆瓣剧照（阶段 E）：搜索候选 / 绑定 / 解绑。 */
    val onSearchDouban: () -> Unit = {},
    /** 第 4 轮 F：粘贴豆瓣 id / 链接（豆瓣搜索常被反爬拦，这条通路必须有）。 */
    val onPasteDoubanId: (String) -> Unit = {},
    val onBindDouban: (com.otakup.niriko.data.remote.douban.DoubanClient.DoubanSearchItem) -> Unit = {},
    val onUnbindDouban: () -> Unit = {},
    val onDismissDoubanCandidates: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SubjectDetailContent(
    state: SubjectDetailUiState,
    completionEvents: Flow<Long>,
    /** F14：进度写满的独立通道（波浪点亮），与状态完成的全屏庆祝分开。 */
    episodeCompletionEvents: Flow<Long> = emptyFlow(),
    completionPlayback: CompletionCelebrationState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onAddToCollection: () -> Unit,
    onUpdateStatus: (WatchStatus) -> Unit,
    onRemoveFromCollection: () -> Unit,
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>, Int?, Boolean) -> Unit,
    onTagDeleted: (String) -> Unit = {},
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    onRelationClick: (Long) -> Unit = {},
    onViewAllStaffClick: () -> Unit = {},
    onRematchPlaceholder: () -> Unit = {},
    onUnbindSteam: () -> Unit = {},
    onBindVndb: (String) -> Unit = {},
    onUnbindVndb: () -> Unit = {},
    onSearchMoreVndb: (String) -> Unit = {},
    onRetryVndb: () -> Unit = {},
    onBindAnilist: (Long) -> Unit = {},
    onUnbindAnilist: () -> Unit = {},
    onSearchMoreAnilist: (String) -> Unit = {},
    /** 第 4 轮 D：清掉 AniList 手动入口的反馈。 */
    onClearAnilistManualMessage: () -> Unit = {},
    onRetryAnilist: () -> Unit = {},
    onPortalUnavailable: () -> Unit = {},
    ratingCallbacks: RatingSectionCallbacks = RatingSectionCallbacks(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val subject = state.subject
    val coverFlightActive = sharedTransitionScope?.isTransitionActive == true
    val context = LocalContext.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 48dp touch target plus the button's two 4dp margins, known before first measure.
    val headerHeight = statusTop + 56.dp
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var portalMenuExpanded by remember { mutableStateOf(false) }
    var shareAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var portalAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val shareFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val portalFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val glassEffect = LocalGlassEffect.current
    val menuBackdrop = rememberPageCardGlassBackdrop(
        enabled = (shareMenuExpanded || portalMenuExpanded) && glassEffect != GlassEffectLevel.OFF,
    )
    // 传送门可显示项（按类型过滤，缺失 ID 的目标隐藏；未安装的仅拉起目标自动隐藏）
    val portalItems = remember(subject, state) {
        subject?.let { buildPortalUiItems(it, state, context) } ?: emptyList()
    }
    val shareScope = rememberCoroutineScope()
    // 分享卡离屏渲染宿主：分享点击时把数据写入，由屏幕外节点录制为 Bitmap
    val shareBitmapHost = remember { ShareBitmapHost() }
    // 阶段 E：更换封面弹窗
    var showCoverPicker by remember { mutableStateOf(false) }
    // 分享预览 Sheet（AniShelf 借鉴）
    var sharePreviewData by remember { mutableStateOf<ShareCardData?>(null) }
    var showSharePreview by remember { mutableStateOf(false) }
    // 背景墙材质到达动画（Apple §12 Materialize：材质渐显，非瞬间出现）
    var bgEntered by remember { mutableStateOf(false) }
    LaunchedEffect(coverFlightActive) { if (!coverFlightActive) bgEntered = true }
    val bgAlpha by animateFloatAsState(
        targetValue = if (bgEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = AnimEasingDefault),
        label = "bgAlpha",
    )
    // 静态模糊 RenderEffect（一次性创建；API 31+ 生效，低版本退回 Modifier.blur）
    val canRenderEffect = Build.VERSION.SDK_INT >= 31
    val blurRadiusPx = with(LocalDensity.current) { 30.dp.toPx() }
    val blurEffect = remember(blurRadiusPx) {
        if (canRenderEffect) {
            android.graphics.RenderEffect.createBlurEffect(
                blurRadiusPx, blurRadiusPx, android.graphics.Shader.TileMode.CLAMP,
            ).asComposeRenderEffect()
        } else null
    }
    Box(modifier = modifier.fillMaxSize()) {
        // Capture page content and its background, with menus as uncaptured siblings.
        Box(Modifier.fillMaxSize().pageCardGlassBackdrop(menuBackdrop)) {
        // 整页模糊背景墙（有封面且非加载/错误态时）：封面放大铺底（scale 1.2 超出边缘避免空隙）
        // + 降采样小图 + 强模糊 + 弱化蒙层，内容在其上滑动
        val hasBackground = subject?.coverUrl != null && !state.isLoading && state.error == null
        // 阶段 P：玻璃/特效强度（默认 FULL）。非 FULL 时跳过 layerBackdrop 捕获，玻璃卡退回 tint（减轻详情页开销）
        // 毛玻璃 backdrop：捕获背景墙图层，供各 GlassSectionCard drawBackdrop 真折射
        val backdropSurface = MaterialTheme.colorScheme.surface
        // 分区卡（miuix）：封面背景墙，逐卡显式传给 GlassSectionCard。
        val glassBackdrop = if (hasBackground) {
            rememberLayerBackdrop {
                drawRect(backdropSurface)
                drawContent()
            }
        } else null
        // 卡片（kyant，R2）：把同一面背景墙声明成页内卡片的玻璃源；
        // 只在 FULL 档捕获——REDUCED/OFF 保持旧行为（静态 tint）。
        val pageCardBackdrop = rememberPageCardGlassBackdrop(
            enabled = hasBackground && glassEffect == GlassEffectLevel.FULL,
        )
        if (hasBackground) {
            Box(modifier = Modifier.fillMaxSize().pageCardGlassBackdrop(pageCardBackdrop)) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(subject.coverUrl)
                    .size(360, 480)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.2f)
                    .then(if (glassEffect == GlassEffectLevel.FULL && glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)
                    .then(if (blurEffect != null) Modifier else Modifier.blur(30.dp))
                    .graphicsLayer {
                        if (blurEffect != null) renderEffect = blurEffect
                        alpha = bgAlpha
                    },
                contentScale = ContentScale.Crop,
            )
            // 弱化蒙层：亮度自适应 scrim（P0b）——白封面自动加深，避免"白雾"。
            // 深色 scrim = lerp(0.45, 0.68, L)；浅色白 scrim = 0.50 + 0.18L。
            val coverLuma = rememberImageLuminance(subject?.coverUrl) ?: 0.5f
            val overlayColor = if (LocalDarkTheme.current) {
                Color.Black.copy(alpha = 0.45f + (0.68f - 0.45f) * coverLuma)
            } else {
                Color.White.copy(alpha = 0.5f + 0.18f * coverLuma)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayColor)
                    .graphicsLayer { alpha = bgAlpha },
            )
            }
        }
        ProvideCardGlassBackdrop(pageCardBackdrop) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 有背景墙时透明（让封面透出）；否则不透明 surface（Loading/Error/无封面）
                .background(if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.surface),
        ) {
            val floatingButtonSource = pageCardBackdrop ?: com.otakup.niriko.ui.components.LocalCardGlassBackdrop.current
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Exact first-pass height: safe top + 48dp controls + 8dp margins.
                    .height(headerHeight)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp)
                    .zIndex(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.padding(4.dp).size(48.dp).searchGlassSurface(CircleShape, source = floatingButtonSource)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("作品详情", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                // 分享入口：作品详情分享 / 我的收藏分享（仅已收藏时可用）
                Box {
                    IconButton(onClick = { portalMenuExpanded = false; shareMenuExpanded = true }, modifier = Modifier.focusRequester(shareFocus).onGloballyPositioned { shareAnchor = it.boundsInRoot() }.padding(4.dp).size(48.dp).searchGlassSurface(CircleShape, source = floatingButtonSource)) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }

                }
                // 传送门：作品跳转到其他平台 / App（P0）
                Box {
                    IconButton(onClick = { shareMenuExpanded = false; portalMenuExpanded = true }, modifier = Modifier.focusRequester(portalFocus).onGloballyPositioned { portalAnchor = it.boundsInRoot() }.padding(4.dp).size(48.dp).searchGlassSurface(CircleShape, source = floatingButtonSource)) {
                        Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = "传送门")
                    }

                }
            }
            if (showCoverPicker && subject != null) {
                CoverPickerDialog(
                    subjectId = subject.subjectId,
                    currentCoverUrl = subject.coverUrl,
                    remoteCandidates = state.coverCandidates.mapNotNull { image ->
                        com.otakup.niriko.data.remote.tmdb.TmdbImageUrl
                            .url(image.filePath, com.otakup.niriko.data.remote.tmdb.TmdbImageUrl.POSTER_MEDIUM)
                            ?.let { url ->
                                CoverCandidate(
                                    url = url,
                                    label = buildString {
                                        append(
                                            image.languageCode?.takeIf { it.isNotBlank() } ?: "无语言"
                                        )
                                        if (image.width > 0) append(" · ${image.width}×${image.height}")
                                    },
                                )
                            }
                    },
                    loadingRemote = state.coverCandidatesLoading,
                    onRequestRemote = ratingCallbacks.onRequestCoverCandidates,
                    onDismiss = { showCoverPicker = false },
                )
            }
            if (showSharePreview && sharePreviewData != null) {
                SharePreviewSheet(
                    context = context,
                    host = shareBitmapHost,
                    data = sharePreviewData!!,
                    onDismiss = { showSharePreview = false },
                )
            }
            when {
                // B4：首屏用分区骨架替代居中转圈（形状对齐真实正文，数据到达不跳版）
                state.isLoading -> SubjectDetailSkeleton(topInset = headerHeight)
                state.error != null -> ErrorContent(
                    message = state.error ?: "",
                    onRetry = onRetry,
                )
                subject != null -> Box {
                    SubjectDetailBody(
                        subject = subject, state = state,
                        completionEvents = completionEvents,
                episodeCompletionEvents = episodeCompletionEvents,
                        completionPlayback = completionPlayback,
                        glassBackdrop = glassBackdrop,
                        topInset = headerHeight,
                        onAddToCollection = onAddToCollection, onUpdateStatus = onUpdateStatus,
                    onRemoveFromCollection = onRemoveFromCollection, onSaveRecord = onSaveRecord,
                    onTagDeleted = onTagDeleted,
                    onCharacterClick = onCharacterClick,
                    onPersonClick = onPersonClick,
                    onRelationClick = onRelationClick,
                    onViewAllStaffClick = onViewAllStaffClick,
                    onRematchPlaceholder = onRematchPlaceholder,
                    onUnbindSteam = onUnbindSteam,
                    onBindVndb = onBindVndb,
                    onUnbindVndb = onUnbindVndb,
                    onSearchMoreVndb = onSearchMoreVndb,
                    onRetryVndb = onRetryVndb,
                    onBindAnilist = onBindAnilist,
                    onUnbindAnilist = onUnbindAnilist,
                    onSearchMoreAnilist = onSearchMoreAnilist,
                    onClearAnilistManualMessage = onClearAnilistManualMessage,
                    onRetryAnilist = onRetryAnilist,
                    ratingCallbacks = ratingCallbacks,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                    if (state.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
            }
        }
        }
        }
        }

                    com.otakup.niriko.ui.components.GlassAnchoredMenu(
                        expanded = shareMenuExpanded,
                        anchorInRoot = shareAnchor,
                        anchorFocusRequester = shareFocus,
                        restoreAnchorFocus = !portalMenuExpanded && !showCoverPicker && !showSharePreview,
                        title = "分享",
                        source = menuBackdrop,
                        safePadding = WindowInsets.safeDrawing.asPaddingValues(),
                        onDismissRequest = { shareMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("分享作品") },
                            onClick = {
                                shareMenuExpanded = false
                                subject?.let { shareScope.launch {
                                    val cover = loadShareCover(context, it.coverUrl)
                                    sharePreviewData = buildShareData(context, it, state, cover)
                                    showSharePreview = true
                                } }
                            },
                        )
                        if (state.isInCollection) {
                            DropdownMenuItem(
                                text = { Text("分享我的收藏") },
                                onClick = {
                                    shareMenuExpanded = false
                                    subject?.let { shareScope.launch {
                                        val cover = loadShareCover(context, it.coverUrl)
                                        sharePreviewData = buildShareData(context, it, state, cover)
                                        showSharePreview = true
                                    } }
                                },
                            )
                        }
                        if (subject != null) {
                            DropdownMenuItem(
                                text = { Text("更换封面") },
                                onClick = {
                                    shareMenuExpanded = false
                                    showCoverPicker = true
                                },
                            )
                        }
                    }

                    com.otakup.niriko.ui.components.GlassAnchoredMenu(
                        expanded = portalMenuExpanded,
                        anchorInRoot = portalAnchor,
                        anchorFocusRequester = portalFocus,
                        restoreAnchorFocus = !shareMenuExpanded,
                        title = "传送门",
                        source = menuBackdrop,
                        safePadding = WindowInsets.safeDrawing.asPaddingValues(),
                        onDismissRequest = { portalMenuExpanded = false },
                    ) {
                        if (portalItems.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("暂无可用跳转") },
                                onClick = { portalMenuExpanded = false },
                            )
                        } else {
                            portalItems.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(item.label)
                                            if (item.subtitle != null) {
                                                Text(
                                                    item.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        portalMenuExpanded = false
                                        if (!PortalLauncher.launch(context, item.actions)) {
                                            onPortalUnavailable()
                                        }
                                    },
                                )
                            }
                        }
                    }
    }
}

/**
 * 详情页首屏骨架（B4）。
 *
 * 形状逐项对齐真实正文：全宽大封面（3:4 兜底比例 + `MaterialTheme.shapes.medium` 圆角，
 * 与 `item("cover")` 一致）→ 主标题 / 原名 / 元信息 / 简介段落（同字号行高）→
 * 三块 [GlassSectionCard] 分区（评分 / 剧集 / 角色，同圆角、同 16.dp 内边距、同 24.dp 间距）。
 *
 * 分区卡传 `backdrop = null`：骨架阶段不做背景捕获，走 [com.otakup.niriko.ui.components.appleGlassCard]
 * 的纯渐变底（零 RenderEffect），避免「骨架 + 真玻璃」双重开销。
 */
@Composable
private fun SubjectDetailSkeleton(
    topInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = topInset + 16.dp, bottom = 16.dp),
    ) {
        // 大封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(skeletonBaseColor())
                .skeletonShimmer(),
        )
        Spacer(Modifier.height(16.dp))
        // 主标题 + 原名
        SkeletonBlock(height = 30.dp, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(0.72f))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(height = 18.dp, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(0.45f))
        Spacer(Modifier.height(16.dp))
        // 元信息（按类型字段数不同，取三行居中值）
        SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(0.9f))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(0.78f))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(16.dp))
        // 简介
        SkeletonBlock(width = 64.dp, height = 18.dp, shape = RoundedCornerShape(6.dp))
        Spacer(Modifier.height(8.dp))
        SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(0.94f))
        Spacer(Modifier.height(6.dp))
        SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(0.7f))
        Spacer(Modifier.height(24.dp))
        // 分区：评分 / 剧集 / 角色
        repeat(3) { index ->
            GlassSectionCard(
                backdrop = null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SkeletonBlock(height = 18.dp, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth(0.3f))
                Spacer(Modifier.height(12.dp))
                repeat(if (index == 2) 2 else 3) {
                    SkeletonBlock(height = 14.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth(0.9f))
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * B2b：详情页单列内容。[activePane] 为 null 时是全量单栏（手机 / 中屏，与升级前逐字相同的一份
 * item 列表），否则只渲染属于该列的 item。
 *
 * 每列各自持有 [rememberLazyListState]，所以并排两栏能独立滚动，外层不滚动（计划书 §三 B2 验收 3）。
 */
@Composable
private fun SubjectDetailItemsColumn(
    activePane: NirikoDetailPane?,
    modifier: Modifier,
    /** F13：把滚动位置回报给详情页顶部的连续渐隐（null = 不需要）。 */
    onScrollSample: ((Int, Int) -> Unit)? = null,
    topInset: Dp = 0.dp,
    itemsBody: LazyListScope.(NirikoDetailPane?, Boolean) -> Unit,
) {
    val entryState = com.otakup.niriko.navigation.LocalDetailNavigationState.current
    val paneKey = activePane?.name ?: "single"
    val indexKey = "detail_scroll_" + paneKey + "_index"
    val offsetKey = "detail_scroll_" + paneKey + "_offset"
    val listState = rememberSaveable(entryState, paneKey, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState(
            entryState?.get<Int>(indexKey) ?: 0, entryState?.get<Int>(offsetKey) ?: 0,
        )
    }
    LaunchedEffect(listState, entryState, paneKey) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (listState.layoutInfo.totalItemsCount > 0) {
                    entryState?.set(indexKey, index)
                    entryState?.set(offsetKey, offset)
                }
            }
    }
    androidx.compose.runtime.DisposableEffect(listState, entryState, paneKey) {
        onDispose {
            if (listState.layoutInfo.totalItemsCount > 0) {
                entryState?.set(indexKey, listState.firstVisibleItemIndex)
                entryState?.set(offsetKey, listState.firstVisibleItemScrollOffset)
            }
        }
    }
    val isScrolling = listState.isScrollInProgress
    if (onScrollSample != null) {
        // 只回报「首项索引 + 偏移」这一对：渐隐的判据在 TopFadePolicy 里（纯函数、有单测），
        // 这里不做任何阈值判断 —— §8.3 明确要求连续、不要离散显隐。
        val report = onScrollSample
        LaunchedEffect(listState) {
            snapshotFlow<Pair<Int, Int>> {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }.collect { sample -> report(sample.first, sample.second) }
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset + 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        itemsBody(activePane, isScrolling)
    }
}

/**
 * B2b：把一个 item 归到某列。[activePane] 为 null（单栏）时全部渲染且保持原始顺序 ——
 * 这是手机端行为零变化的关键；两栏时只渲染该列自己的 item，故跨列相对顺序不再保留。
 */
private fun LazyListScope.detailPaneItem(
    id: DetailSectionId,
    activePane: NirikoDetailPane?,
    hidden: Set<DetailSectionId>,
    content: @Composable LazyItemScope.() -> Unit,
) {
    // F06：被用户关掉的部件**完全不组合**（不是画成透明）——
    // 不组合才不会发出网络请求、不会占用测量，这才是「隐藏」应有的代价。
    // 核心部件在 DetailLayoutPolicy 里就不可隐藏，这里再兜一层。
    if (!DetailLayoutPolicy.isVisible(id, hidden)) return
    val pane = id.pane
    if (activePane == null || activePane == pane) item(key = id.key, content = content)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SubjectDetailBody(
    subject: SubjectEntity,
    state: SubjectDetailUiState,
    completionEvents: Flow<Long> = emptyFlow(),
    /** F14：进度写满的独立通道（波浪点亮）。 */
    episodeCompletionEvents: Flow<Long> = emptyFlow(),
    completionPlayback: CompletionCelebrationState = remember { CompletionCelebrationState() },
    glassBackdrop: Backdrop?,
    topInset: Dp = 0.dp,
    onAddToCollection: () -> Unit,
    onUpdateStatus: (WatchStatus) -> Unit,
    onRemoveFromCollection: () -> Unit,
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>, Int?, Boolean) -> Unit,
    onTagDeleted: (String) -> Unit = {},
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    onRelationClick: (Long) -> Unit = {},
    onViewAllStaffClick: () -> Unit = {},
    onRematchPlaceholder: () -> Unit = {},
    onUnbindSteam: () -> Unit = {},
    onBindVndb: (String) -> Unit = {},
    onUnbindVndb: () -> Unit = {},
    onSearchMoreVndb: (String) -> Unit = {},
    onRetryVndb: () -> Unit = {},
    onBindAnilist: (Long) -> Unit = {},
    onUnbindAnilist: () -> Unit = {},
    onSearchMoreAnilist: (String) -> Unit = {},
    /** 第 4 轮 D：清掉 AniList 手动入口的反馈。 */
    onClearAnilistManualMessage: () -> Unit = {},
    onRetryAnilist: () -> Unit = {},
    ratingCallbacks: RatingSectionCallbacks = RatingSectionCallbacks(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // 共享模糊封面：横向小卡毛玻璃背景的唯一模糊源（进程级缓存,只模糊一次）
    val episodeWaveProgress by rememberEpisodeCompletionWave(episodeCompletionEvents, subject.subjectId)
    val blurredCoverContext = LocalContext.current
    var blurredCover by remember(subject.coverUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(subject.coverUrl) {
        blurredCover = loadBlurredCover(blurredCoverContext, subject.coverUrl)
    }
    // F06：用户关掉的详情部件。规则在 DetailLayoutPolicy（纯函数）：未知 key 忽略、核心部件不可隐藏。
    val detailSettings by blurredCoverContext.nirikoApp.settingsDataStore.settings
        .collectAsState(initial = com.otakup.niriko.data.settings.AppSettings())
    val hiddenSections = remember(detailSettings.detailHiddenSections) {
        DetailLayoutPolicy.hiddenFromEncoded(detailSettings.detailHiddenSections)
    }
    // 本地编辑状态 — 使用 rememberSaveable 确保配置变更（如屏幕旋转）后状态不丢失
    // 游戏类型：watchedEpisodes 统一存分钟（Steam 导入分钟、详情页按小时输入）
    val isGameType = subject.type == SubjectType.GAME
    var watchedEpisodesText by rememberSaveable(state.watchedEpisodes, state.collectionId) {
        mutableStateOf(
            if (isGameType && state.watchedEpisodes != null) {
                PlaytimeConverter.minutesToHours(state.watchedEpisodes).toString() // 分钟 → 小时（UI 展示）
            } else {
                state.watchedEpisodes?.toString() ?: ""
            }
        )
    }
    // 卷进度 / 私密（阶段 B）
    var watchedVolumesText by rememberSaveable(state.watchedVolumes, state.collectionId) {
        mutableStateOf(state.watchedVolumes?.toString() ?: "")
    }
    var isPrivateLocal by rememberSaveable(state.collectionId) { mutableStateOf(state.isPrivate) }
    var myRatingValue by rememberSaveable(state.collectionId) { mutableFloatStateOf(state.myRating ?: 0f) }
    var personalTagsText by rememberSaveable(state.collectionId) { mutableStateOf(state.personalTags.joinToString(", ")) }
    var personalImpressionText by rememberSaveable(state.collectionId) { mutableStateOf(state.personalImpression ?: "") }
    var watchedTrackIdsLocal by rememberSaveable(state.collectionId, stateSaver = TrackIdSetSaver) { mutableStateOf(state.watchedTrackIds) }
    var startDateValue by rememberSaveable(state.collectionId) { mutableStateOf(state.startDate) }
    var finishDateValue by rememberSaveable(state.collectionId) { mutableStateOf(state.finishDate) }
    var showCollectionSheet by remember { mutableStateOf(false) }
    // F07：分集评价合并（预览弹层）。条目按需从 ViewModel 取，不进 uiState。
    var showEpisodeMerge by remember { mutableStateOf(false) }
    var episodeMergeEntries by remember { mutableStateOf<List<com.otakup.niriko.util.EpisodeReviewMergePolicy.Entry>>(emptyList()) }
    var episodeMergeMode by remember { mutableStateOf(com.otakup.niriko.util.EpisodeReviewMergePolicy.Mode.BOTH) }
    var episodeMergeRounding by remember {
        mutableStateOf(com.otakup.niriko.util.EpisodeReviewMergePolicy.Rounding.HALF_UP_HALF)
    }
    var episodeMergeOverwrite by remember { mutableStateOf(false) }

    // 验证
    val parsedWatched = watchedEpisodesText.toIntOrNull()
    val hasEpisodeError = subject.type != SubjectType.GAME && subject.totalEpisodes != null &&
        parsedWatched != null &&
        (parsedWatched < 0 || parsedWatched > subject.totalEpisodes)
    val isSaveEnabled = !hasEpisodeError && !state.isUpdating

    // DatePicker 状态
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showFinishDatePicker by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    // 全屏图片查看器（剧照 / 截图）
    var imageViewerRequest by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    // 剧照条目：TMDb 背景图 + 每集剧照（放在 LazyColumn 之前计算——LazyListScope 不是 @Composable 作用域）
    val thumbItems = remember(
        state.tmdbBackdrops,
        state.episodeStills,
        state.episodes,
        state.doubanThumbs,
        state.doubanImageReferer,
    ) {
        val backdropItems = state.tmdbBackdrops.mapNotNull { image ->
            com.otakup.niriko.data.remote.tmdb.TmdbImageUrl.url(
                image.filePath,
                com.otakup.niriko.data.remote.tmdb.TmdbImageUrl.BACKDROP_MEDIUM,
            )?.let { ThumbItem(it, caption = "TMDb 剧照") }
        }
        val stillItems = state.episodes.mapNotNull { ep ->
            state.episodeStills[ep.id]?.let { url ->
                ThumbItem(url, caption = "EP${formatEpisodeNumber(ep.ep)}", source = "tmdb_episode")
            }
        }
        // 阶段 E：豆瓣剧照（灰色通道，仅在开启且已绑定时才有内容）
        val doubanItems = state.doubanThumbs.map { url ->
            ThumbItem(
                url = url,
                caption = "豆瓣剧照",
                source = "douban",
                referer = state.doubanImageReferer,
            )
        }
        (backdropItems + stillItems + doubanItems).distinctBy { it.url }
    }

    // 详情页所有 GlassSectionCard 共享同一张模糊封面：backdrop 不可用时一律走 BlurredGlassSurface。
    CompositionLocalProvider(LocalDetailCoverFallback provides blurredCover) {
    // NavHost owns page motion; shared covers must be present from the first composition.
    // F06 第二步：用户自定义的部件顺序（未知 key 忽略、新部件追加到默认尾部，规则见 DetailLayoutPolicy）。
    // 必须在组合作用域里算：下面的 itemsBody 是普通 lambda（内部装 @Composable 内容 lambda），
    // 它内部不能调用 remember / 读 CompositionLocal。
    val orderedSections = remember(detailSettings.detailSectionOrder) {
        DetailLayoutPolicy.resolveOrder(detailSettings.detailSectionOrder)
    }
    // F13：详情页顶部的**连续**渐隐。滚动样本来自身持有滚动状态的那一层（两栏时谁被滚动就跟着谁）；
    // 渐隐底色取 Scaffold 的容器色（background）—— 顶部栏与内容的底都是它。
    var detailScrollSample by remember { mutableStateOf(0 to 0) }
    val detailFadeDistancePx = with(LocalDensity.current) { TopFadePolicy.FADE_DISTANCE_DP.dp.toPx() }
    val detailFadeAlpha = TopFadePolicy.alphaFor(
        scrollOffsetPx = TopFadePolicy.scrollOffsetFor(detailScrollSample.first, detailScrollSample.second),
        fadeDistancePx = detailFadeDistancePx,
    )
    // B2b：同一份 item 列表按 activePane 分流 —— activePane == null 时全量按原顺序渲染
    //（手机端 / 中屏逐字不变），否则只渲染属于该列的 item（宽屏并排两栏，两列各自持有滚动状态）。
    val itemsBody: LazyListScope.(NirikoDetailPane?, Boolean) -> Unit = { activePane, isListScrolling ->
        // F06 第二步：按**解析出的顺序**分发这些部件。
        // orderedSections 在组合作用域里算好（LazyListScope lambda 不能调 remember），
        // when 对枚举穷举 —— 以后新增部件而忘了在这里接上会直接编译失败。
        orderedSections.forEach { sectionId ->
            when (sectionId) {
                DetailSectionId.COVER -> {
            // 全宽大封面：按图片自身宽高比展示（不强制 3:4），加载完成前/异常用 3:4 兜底
            detailPaneItem(DetailSectionId.COVER, activePane, hiddenSections) {
            // 阶段 E：用户封面覆盖优先（更换封面）
            val coverSeed = remember(subject.subjectId) { com.otakup.niriko.util.SubjectNavigationSeed.coverFor(subject.subjectId) }
            var overrideCover by remember(subject.subjectId) { mutableStateOf<String?>(coverSeed?.url) }
            LaunchedEffect(subject.subjectId) {
                overrideCover = blurredCoverContext.nirikoApp.coverOverrideStore.overrideFor(subject.subjectId)
                    ?: coverSeed?.url
            }
            val coverEffective = overrideCover ?: subject.coverUrl
            val material = remember(state.myRating, state.isInCollection) {
                cardMaterialFromRating(state.myRating.takeIf { state.isInCollection })
            }
            val record = remember(subject, state) { detailCardBackModel(subject, state) }
            if (coverEffective != null) {
                // 显式超采样解码：hero 大封面放宽到 1080px 再裁切，避免默认按绘制层解码导致的发糊；原图不究其内存
                val painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(coverEffective)
                        .placeholderMemoryCacheKey(coverSeed?.takeIf { it.url == coverEffective }?.memoryCacheKey)
                        // 1080px 宽固定上限（宽 × 高按原比例），覆盖绝大多数屏且避免大图 OOM
                        .size(coil.size.Size(coil.size.Dimension.Pixels(1080), coil.size.Dimension.Undefined))
                        .crossfade(false)
                        .build(),
                )
                LaunchedEffect(painter.state, coverEffective, subject.subjectId) {
                    val loaded = painter.state as? AsyncImagePainter.State.Success
                    if (loaded != null) {
                        val drawable = loaded.result.drawable
                        com.otakup.niriko.util.SubjectNavigationSeed.rememberCover(
                            subject.subjectId, coverEffective, drawable.intrinsicWidth, drawable.intrinsicHeight,
                            loaded.result.memoryCacheKey,
                        )
                    }
                }
                val ratio = coverSeed?.takeIf { it.url == coverEffective }?.aspectRatio
                    ?: if (painter.state is AsyncImagePainter.State.Success) {
                    val s = painter.intrinsicSize
                    if (s.width.isFinite() && s.height.isFinite() && s.width > 0f && s.height > 0f)
                        s.width / s.height else 3f / 4f
                } else 3f / 4f
                // 共享元素：与列表卡片封面同 key（"cover_${subjectId}"），实现 Kazumi 同款缩放飞入
                val scope = sharedTransitionScope
                val avScope = animatedVisibilityScope
                val sharedModifier = if (scope != null && avScope != null) {
                    with(scope) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState(com.otakup.niriko.navigation.LocalDetailHeroKey.current ?: "cover_${subject.subjectId}"),
                            animatedVisibilityScope = avScope,
                            boundsTransform = com.otakup.niriko.ui.animation.NirikoMotionSpecs.subjectCoverPathBounds(androidx.compose.ui.platform.LocalDensity.current.density),
                        )
                    }
                } else Modifier
                DetailPosterCard(
                    subjectId = subject.subjectId,
                    record = record,
                    material = material,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                        .then(sharedModifier)
                        .subjectCoverPresentation(avScope),
                ) {
                    Image(
                        painter = painter,
                        contentDescription = subject.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                // 无封面：占位
                DetailPosterCard(
                    subjectId = subject.subjectId,
                    record = record,
                    material = material,
                    shape = MaterialTheme.shapes.medium,
                    hasCover = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f),
                ) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text("暂无封面", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            }
                }
                DetailSectionId.TITLE -> {
            // 标题（统一 TitleResolver）— Apple 大标题体系：26sp 主标题 + 15sp 原名 + 小字元信息
            detailPaneItem(DetailSectionId.TITLE, activePane, hiddenSections) {
            Spacer(Modifier.height(16.dp))
            val titleInfo = com.otakup.niriko.util.TitleResolver.resolve(subject.titleCN, subject.title)
            Text(
                text = titleInfo.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
            titleInfo.secondary?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(16.dp))

            // 元信息（按类型展示不同字段）
            SubjectMetaSection(subject = subject)
            Spacer(Modifier.height(16.dp))

            // 简介
            Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            RichText(
                text = subject.summary ?: "暂无简介",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 20,
            )
            Spacer(Modifier.height(24.dp))
            }
                }
                DetailSectionId.INFOBOX -> {
            // === infobox（艺术家/发行商/发售日期等，音乐类型尤其丰富） ===
            if (state.infoBox.isNotEmpty()) {
                detailPaneItem(DetailSectionId.INFOBOX, activePane, hiddenSections) {
                    GlassSectionCard(
                        backdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                        modifier = Modifier.fillMaxWidth(),
                        fallbackBitmap = blurredCover,
                    ) {
                        InfoBoxSection(entries = state.infoBox)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
                }
                DetailSectionId.STEAM -> {
            // === Steam 补充信息（仅游戏类型且已绑定 Steam 时显示） ===
            if (subject.type == SubjectType.GAME && state.steam != null) {
                detailPaneItem(DetailSectionId.STEAM, activePane, hiddenSections) {
                    SteamInfoSection(
                        steam = state.steam,
                        achievements = state.achievements,
                        chartRank = state.chartRank,
                        backdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                        isPlaceholder = subject.isSteamPlaceholder,
                        onRematchPlaceholder = onRematchPlaceholder,
                        onUnbindSteam = onUnbindSteam,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
                }
                DetailSectionId.ANILIST -> {
            // === AniList 补充信息（不限制类型：动画/漫画/游戏等 bangumi 词条均可） ===
            // 只要存在绑定/详情/候选就渲染；bangumi 词条即使候选为空也保留「搜索更多」卡片，不再整块消失。
            val anilistHasData = state.anilistBinding != null || state.anilistDetail != null ||
                state.anilistRichDetail != null || state.anilistCandidates.isNotEmpty()
            if (anilistHasData || subject.sourceKey == null) {
                when {
                    state.anilistDetail != null || state.anilistRichDetail != null -> {
                        detailPaneItem(DetailSectionId.ANILIST, activePane, hiddenSections) {
                            AniListInfoSection(
                                detail = state.anilistDetail,
                                rich = state.anilistRichDetail,
                                anilistId = state.anilistBinding?.anilistId,
                                backdrop = glassBackdrop,
                                isScrolling = isListScrolling,
                                onUnbind = onUnbindAnilist,
                                onRetry = onRetryAnilist,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    state.anilistBinding != null -> {
                        detailPaneItem(DetailSectionId.ANILIST, activePane, hiddenSections) {
                            AniListBoundPendingSection(
                                anilistId = state.anilistBinding.anilistId,
                                backdrop = glassBackdrop,
                                isScrolling = isListScrolling,
                                onRetry = onRetryAnilist,
                                onUnbind = onUnbindAnilist,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    else -> {
                        detailPaneItem(DetailSectionId.ANILIST, activePane, hiddenSections) {
                            // 第 4 轮 D：改用统一绑定区块（原 AniListCandidateSection 的三段式 UI
                            // 与 VNDB/TMDb 各写一套，行为不一致且缺「粘贴 id」通路）。
                            // GameItem → MatchCandidate 的映射只用到展示字段，绑定仍走 bindAnilist(id)。
                            var anilistPaste by remember { mutableStateOf("") }
                            ProviderBindingSection(                            title = "AniList 条目",
                                unboundHint = "AniList 的评分**无需绑定**就会显示在评分区（自动匹配高置信度时）。" +
                                    "绑定后才额外提供英文名/角色/标签等深度数据。",
                                bindingSummary = null,
                                // 候选的匹配度/理由已由 ViewModel 侧 AniListCandidateMapper 算好，这里只做展示
                                candidates = state.anilistCandidates,
                                matchReasons = state.anilistCandidates
                                    .filter { it.reasons.isNotEmpty() }
                                    .associate { it.externalId to it.reasons },
                                onBind = { candidate ->
                                    // externalId 形如 media-{id}：此前直接 toLongOrNull() 恒为 null，
                                    // 点击静默失败（用户反馈「按下右侧绑定无反应」）。
                                    val id = com.otakup.niriko.util.AniListIdParser.parse(candidate.externalId)
                                    if (id != null) {
                                        onBindAnilist(id)
                                    } else {
                                        // 解析不出来也要有反应，不能静默：把原文回灌给「粘贴 id」入口，
                                        // 由 ViewModel 给出「请输入关键词或 AniList id」这类一行反馈。
                                        onSearchMoreAnilist(candidate.externalId)
                                    }
                                },
                                onUnbind = {},
                                onSearch = onSearchMoreAnilist,
                                onPasteId = { raw ->
                                    // 直接复用搜索入口：ViewModel 会先尝试把它当 id 解析
                                    anilistPaste = raw
                                    onSearchMoreAnilist(raw)
                                },
                                manualMessage = state.anilistManualMessage,
                                onClearMessage = onClearAnilistManualMessage,
                                loading = false,
                                pasteHint = "如 21 或 anilist.co/anime/21",
                                glassBackdrop = glassBackdrop,
                                isScrolling = isListScrolling,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            } else if (subject.sourceId == "anilist") {
                // AniList 兜底来源条目（sourceKey=anilist:...）：展示该条目落库时的 AniList 侧数据
                detailPaneItem(DetailSectionId.ANILIST, activePane, hiddenSections) {
                    AniListSourceInfoSection(
                        subject = subject,
                        backdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
                }
                DetailSectionId.VNDB -> {
            // === VNDB 补充信息（仅游戏类型：已绑定显示详情，未绑定显示候选/搜索，不再整块消失） ===
            if (subject.type == SubjectType.GAME) {
                when {
                    state.vndbDetail != null -> {
                        detailPaneItem(DetailSectionId.VNDB, activePane, hiddenSections) {
                            VndbInfoSection(
                                detail = state.vndbDetail,
                                relations = state.vndbRelations,
                                backdrop = glassBackdrop,
                                isScrolling = isListScrolling,
                                onBindRelation = onBindVndb,
                                onUnbind = onUnbindVndb,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    state.vndbBinding != null -> {
                        detailPaneItem(DetailSectionId.VNDB, activePane, hiddenSections) {
                            VndbBoundPendingSection(
                                vndbId = state.vndbBinding.vndbId,
                                backdrop = glassBackdrop,
                                isScrolling = isListScrolling,
                                onRetry = onRetryVndb,
                                onUnbind = onUnbindVndb,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    else -> {
                        detailPaneItem(DetailSectionId.VNDB, activePane, hiddenSections) {
                            VndbCandidateSection(
                                candidates = state.vndbCandidates,
                                candidateReasons = state.vndbCandidateReasons,
                                manualMessage = state.vndbManualMessage,
                                backdrop = glassBackdrop,
                                isScrolling = isListScrolling,
                                onBind = onBindVndb,
                                onSearchMore = onSearchMoreVndb,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
                }
                DetailSectionId.RELATIONS -> {
            // === 关联条目（前后传/版本/系列） ===
            detailPaneItem(DetailSectionId.RELATIONS, activePane, hiddenSections) {
                RevealOnScroll {
                    RelationsSection(
                        relations = state.relations,
                        onRelationClick = onRelationClick,
                        blurredCover = blurredCover,
                        glassBackdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
                }
                DetailSectionId.STAT_GRID -> {
            // === 统计卡网格（AniShelf 借鉴：评分/集数/人数）===
            detailPaneItem(DetailSectionId.STAT_GRID, activePane, hiddenSections) {
                DetailStatGrid(
                    subject = subject,
                    glassBackdrop = glassBackdrop,
                    isScrolling = isListScrolling,
                    episodeCount = if (subject.type == SubjectType.MUSIC) {
                        state.episodes.count { it.type == 0 || it.type == 2 || it.type == 3 }
                    } else null,
                    vndbLengthMinutes = state.vndbDetail?.lengthMinutes,
                )
                Spacer(Modifier.height(16.dp))
            }
                }
                DetailSectionId.ANITABI -> {
            // === 圣地巡礼（Anitabi 取景地标，阶段 K）===
            if (state.anitabiPoints.isNotEmpty()) {
                detailPaneItem(DetailSectionId.ANITABI, activePane, hiddenSections) {
                    AnitabiSection(
                        subject = subject,
                        city = state.anitabiCity.orEmpty(),
                        points = state.anitabiPoints,
                        pointsLength = state.anitabiPointsLength,
                        imagesLength = state.anitabiImagesLength,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
                }
                DetailSectionId.GUESS -> {
            // === 猜你喜欢（阶段 F：本地 tag 共现）===
            if (state.guessYouLike.isNotEmpty()) {
                detailPaneItem(DetailSectionId.GUESS, activePane, hiddenSections) {
                    GuessYouLikeSection(
                        subjects = state.guessYouLike,
                        onSubjectClick = onRelationClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
                }
                DetailSectionId.RATING -> {
            // === 评分（第 3 轮回退：横滑卡太细碎，恢复「一张评分卡 + 下方纵向内容」） ===
            detailPaneItem(DetailSectionId.RATING, activePane, hiddenSections) {
                RatingComparisonSection(
                    subject = subject,
                    myRating = state.myRating,
                    glassBackdrop = glassBackdrop,
                    isScrolling = isListScrolling,
                    disputeLabel = state.ratingDispute,
                    localPercentile = state.localPercentile,
                )
                Spacer(Modifier.height(8.dp))
                // 评分分布（整宽柱状图，保持改造前的版式）
                RatingDistributionChart(distribution = state.ratingDistribution)
                Spacer(Modifier.height(20.dp))
            }
                }
                DetailSectionId.EXTERNAL_RATING -> {
            // === 权威评分（多源；含 Fami通 / Billboard / Oricon 手动录入） ===
            detailPaneItem(DetailSectionId.EXTERNAL_RATING, activePane, hiddenSections) {
                ExternalRatingSection(
                    ratings = state.externalRatings.filter {
                        it.sourceId != com.otakup.niriko.data.remote.rating.ExternalRating.SOURCE_BANGUMI &&
                            it.sourceId != com.otakup.niriko.data.remote.rating.ExternalRating.SOURCE_BILIBILI
                    },
                    manualAwards = state.manualAwards,
                    subjectType = subject.type,
                    showManualEntry = true,
                    onRefresh = ratingCallbacks.onRefreshRatings,
                    onAddManualAward = ratingCallbacks.onAddManualAward,
                    onRemoveManualAward = ratingCallbacks.onRemoveManualAward,
                    glassBackdrop = glassBackdrop,
                    isScrolling = isListScrolling,
                )
                Spacer(Modifier.height(20.dp))
            }
                }
                DetailSectionId.EXTENDED -> {
            // === 扩展信息：角色 / 制作人员 / 社区标签 ===
            detailPaneItem(DetailSectionId.EXTENDED, activePane, hiddenSections) {
            if (state.isExtendedLoading) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else {
                // 角色
                RevealOnScroll {
                    CharacterSection(
                        characters = state.characters,
                        onCharacterClick = onCharacterClick,
                        onPersonClick = onPersonClick,
                        blurredCover = blurredCover,
                        glassBackdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // 制作人员
                RevealOnScroll {
                    StaffSection(
                        staff = state.staff,
                        onPersonClick = onPersonClick,
                        onViewAllClick = onViewAllStaffClick,
                        blurredCover = blurredCover,
                        glassBackdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
                Spacer(Modifier.height(20.dp))
                // 社区标签（保持原样，本轮不合并背景）
                if (subject.tags.isNotEmpty()) {
                    Text("标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // F02：统一到 TagChip（与海报小件同一族材质）。
                        // 前 3 个是权重最高的标签 → 强调态；其余保持普通态。
                        subject.tags.forEachIndexed { index, tag ->
                            TagChip(label = tag, emphatic = index < 3)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            } // item "extended"
                }
                DetailSectionId.THUMBS -> {
            // === 剧照 / 截图（TMDb 背景图 + 每集剧照；Steam 截图仍在 Steam 区块内） ===
            if (thumbItems.isNotEmpty() || state.doubanEnabled) {
                detailPaneItem(DetailSectionId.THUMBS, activePane, hiddenSections) {
                    if (thumbItems.isNotEmpty()) {
                        ThumbsSection(
                            items = thumbItems,
                            onOpen = { urls, index -> imageViewerRequest = urls to index },
                            glassBackdrop = glassBackdrop,
                            isScrolling = isListScrolling,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    // 阶段 E：豆瓣剧照（灰色通道）。开启后需要先绑定豆瓣词条——
                    // 保守匹配：只搜候选，用户点选才写库（豆瓣同名作品极多，自动绑必错）。
                    if (state.doubanEnabled) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AssistChip(
                                onClick = ratingCallbacks.onSearchDouban,
                                label = {
                                    Text(
                                        if (state.doubanLoading) "正在搜索豆瓣…" else "豆瓣剧照：搜索词条",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                            )
                            if (state.doubanBoundId != null) {
                                Text(
                                    "已绑定 #" + state.doubanBoundId +
                                        "（" + state.doubanThumbs.size + " 张）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "解绑",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { ratingCallbacks.onUnbindDouban() },
                                )
                            } else {
                                Text(
                                    "未绑定（豆瓣剧照走非官方接口，可能被拦截）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        // 第 4 轮 F：手动粘贴豆瓣 id / 链接（终极兜底）。
                        // 豆瓣的 id 来源本来就不可靠（搜索常被反爬拦），因此必须留这条通路：
                        // 用户在浏览器里找到条目，把链接贴进来即可。
                        if (state.doubanBoundId == null) {
                            var doubanPasteInput by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = doubanPasteInput,
                                    onValueChange = { doubanPasteInput = it },
                                    label = {
                                        Text(
                                            "或粘贴豆瓣 ID / 链接（如 1292052）",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(6.dp))
                                TextButton(onClick = {
                                    ratingCallbacks.onPasteDoubanId(doubanPasteInput)
                                    doubanPasteInput = ""
                                }) { Text("绑定", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
                }
                DetailSectionId.EPISODES_RATING -> {
            // === 剧集 / 章节 + 每集评分走势（对齐 Bangumi-master 的 Ep 区块） ===
            if (subject.type == SubjectType.ANIME || subject.type == SubjectType.REAL || subject.type == SubjectType.BOOK) {
                detailPaneItem(DetailSectionId.EPISODES_RATING, activePane, hiddenSections) {
                    // F14：这一部作品的进度**刚写满**时，剧集区块从左到右点亮一次。
                    // 事件来自进度写入（初次加载不写库 → 不会触发），与状态完成的庆祝是两条通道。
                    EpisodeRatingSection(
                        completionWaveProgress = episodeWaveProgress,
                        episodes = state.episodes,
                        ratings = state.episodeRatings,
                        myRatings = state.myEpisodeRatings,
                        stills = state.episodeStills,
                        loadState = state.episodeRatingLoad,
                        imdbLoading = state.imdbEpisodesLoading,
                        hasTmdbBinding = state.tmdbBinding != null,
                        imdbAvailable = state.tmdbBinding != null,
                        imdbEntry = state.imdbEntry,
                        imdbLoadResult = state.imdbEpisodeLoad,
                        onOpenSettings = ratingCallbacks.onOpenRatingSettings,
                        onLoadImdb = ratingCallbacks.onLoadImdbEpisodes,
                        onMarkWatched = { sort ->
                            onSaveRecord(
                                sort.toInt(),
                                myRatingValue.takeIf { it > 0f },
                                personalTagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                personalImpressionText.takeIf { it.isNotBlank() },
                                startDateValue,
                                finishDateValue,
                                watchedTrackIdsLocal,
                                watchedVolumesText.toIntOrNull(),
                                isPrivateLocal,
                            )
                        },
                        onOpenEpisodeDetail = ratingCallbacks.onOpenEpisodeDetail,
                        onOpenImage = { urls, index, _ -> imageViewerRequest = urls to index },
                        watchedEpisodes = state.watchedEpisodes,
                        glassBackdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
                }
                DetailSectionId.TMDB_BINDING -> {
            // === TMDb 绑定（保守匹配：只产候选，用户确认才写库） ===
            if (state.tmdbSupported) {
                detailPaneItem(DetailSectionId.TMDB_BINDING, activePane, hiddenSections) {
                    TmdbBindingSection(
                        binding = state.tmdbBinding,
                        movieBinding = state.tmdbMovieBinding,
                        candidates = state.tmdbCandidates,
                        pastedCandidate = state.tmdbPastedCandidate,
                        detail = state.tmdbDetail,
                        currentSeason = state.tmdbBinding?.subKey?.toIntOrNull(),
                        onSeasonSelect = ratingCallbacks.onSelectTmdbSeason,
                        manualQuery = state.tmdbManualQuery,
                        manualLoading = state.tmdbManualLoading,
                        manualMessage = state.tmdbManualMessage,
                        onBind = ratingCallbacks.onBindTmdb,
                        onUnbind = ratingCallbacks.onUnbindTmdb,
                        onSearchMore = { keyword -> ratingCallbacks.onSearchMoreTmdb(keyword) },
                        onPasteId = ratingCallbacks.onPasteTmdbId,
                        onClearMessage = ratingCallbacks.onClearTmdbManualMessage,
                        onOpenSettings = ratingCallbacks.onOpenRatingSettings,
                        glassBackdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
                }
                DetailSectionId.TRACKS -> Unit // Editing only; retain the persisted section key.
                DetailSectionId.COLLECTION_BTN -> {
            // === 收藏管理（Apple 风格：单主按钮，编辑控件收进 BottomSheet） ===
            detailPaneItem(DetailSectionId.COLLECTION_BTN, activePane, hiddenSections) {
                if (state.isInCollection) {
                    DetailDock(
                        backdrop = glassBackdrop,
                        isScrolling = isListScrolling,
                        statusLabel = state.currentStatus.label,
                        ratingText = if (myRatingValue > 0) "%.1f 分".format(myRatingValue) else "未评分",
                        progressText = if (!isGameType && parsedWatched != null && subject.totalEpisodes != null)
                            "${parsedWatched}/${subject.totalEpisodes} 集" else null,
                        onClick = { showCollectionSheet = true },
                    )
                } else {
                    Button(onClick = onAddToCollection, enabled = !state.isUpdating, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isUpdating) "添加中…" else "加入收藏")
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
                }
            }
        }
    }

    // B2b：宽屏（EXPANDED）并排两栏、各自独立滚动；窄屏仍走 single —— 与升级前逐字相同的一份列表。
    // F18：书籍 / 漫画页面的半调网点。画在内容**之下**（drawWithCache 只画自己），
    // 因此不遮挡正文、不拦点击；其他类型一律不画（HalftonePolicy 判据）。
    val halftoneModifier = if (
        com.otakup.niriko.util.HalftonePolicy.shouldApply(subject.type, detailSettings.halftoneEnabled)
    ) {
        Modifier.halftoneDots(
            // 主题判定统一走 LocalDarkTheme（项目唯一来源），不按背景亮度猜
            isDark = androidx.compose.foundation.isSystemInDarkTheme().let {
                com.otakup.niriko.ui.theme.LocalDarkTheme.current
            },
            contentColor = MaterialTheme.colorScheme.onBackground,
        )
    } else {
        Modifier
    }
    // F08：CRT 老电视模式（默认关闭）。只对「2000 年以前的动画 + 用户自己开启」播放约 4 秒。
    // API 33+ 使用缓存的 AGSL 内容 shader；低版本或 shader 创建失败时降级为 Canvas 图元。返回时由组合销毁取消协程。
    CrtOverlay(
        airDate = subject.airDate,
        type = subject.type,
        userEnabled = detailSettings.crtModeEnabled,
    ) {
    AdaptiveDetailScaffold(
        modifier = Modifier.fillMaxSize().then(halftoneModifier).topContentAlphaMask(strength = { detailFadeAlpha }, hiddenHeight = TopFadePolicy.WINDOW_MASK_HIDDEN_DP.dp),
        single = { paneModifier ->
            SubjectDetailItemsColumn(null, paneModifier, { i, o -> detailScrollSample = i to o }, topInset, itemsBody)
        },
        overview = { paneModifier ->
            SubjectDetailItemsColumn(NirikoDetailPane.OVERVIEW, paneModifier, { i, o -> detailScrollSample = i to o }, topInset, itemsBody)
        },
        content = { paneModifier ->
            SubjectDetailItemsColumn(NirikoDetailPane.CONTENT, paneModifier, { i, o -> detailScrollSample = i to o }, topInset, itemsBody)
        },
    )
    }
    } // CompositionLocalProvider(LocalDetailCoverFallback)

    // ========== 收藏管理 BottomSheet（编辑控件全部收纳于此） ==========
    if (showCollectionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCollectionSheet = false },
            sheetState = sheetState,
        ) {
            // ModalBottomSheet has its own window; draw feedback inside that window.
            CompletionCelebrationHost(
                events = completionEvents,
                playbackState = completionPlayback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CollectionEditSheet(
                    subject = subject, state = state,
                    watchedEpisodesText = watchedEpisodesText,
                    onWatchedEpisodesChange = { watchedEpisodesText = it },
                    watchedVolumesText = watchedVolumesText,
                    onWatchedVolumesChange = { watchedVolumesText = it },
                    isPrivateLocal = isPrivateLocal,
                    onIsPrivateChange = { isPrivateLocal = it },
                    myRatingValue = myRatingValue,
                    onMyRatingChange = { myRatingValue = it },
                    personalTagsText = personalTagsText,
                    onPersonalTagsChange = { personalTagsText = it },
                    personalImpressionText = personalImpressionText,
                    onPersonalImpressionChange = { personalImpressionText = it },
                    startDateValue = startDateValue, onStartDateChange = { startDateValue = it },
                    finishDateValue = finishDateValue, onFinishDateChange = { finishDateValue = it },
                    watchedTrackIdsLocal = watchedTrackIdsLocal,
                    onToggleTrack = { id ->
                        // 与详情页曲目列表同一份状态：两处永远一致（§9.2 验收线）
                        watchedTrackIdsLocal =
                            if (id in watchedTrackIdsLocal) watchedTrackIdsLocal.minus(id)
                            else watchedTrackIdsLocal.plus(id)
                    },
                    isSaveEnabled = isSaveEnabled,
                    isUpdating = state.isUpdating,
                    onUpdateStatus = onUpdateStatus,
                    onSaveRecord = onSaveRecord,
                    onRemoveFromCollection = onRemoveFromCollection,
                    onShowStartDatePicker = { showStartDatePicker = true },
                    onShowFinishDatePicker = { showFinishDatePicker = true },
                    onShowRemoveConfirm = { showRemoveConfirm = true },
                    onOpenEpisodeDetail = ratingCallbacks.onOpenEpisodeDetail,
                    onRequestEpisodeMerge = {
                        episodeMergeOverwrite = false
                        // 打开就先取数据：预览必须是**真实数据**算出来的，不能拿现有文本猜
                        ratingCallbacks.onLoadEpisodeReviewEntries { entries ->
                            episodeMergeEntries = entries
                            showEpisodeMerge = true
                        }
                    },
                )
            }
        }
    }

    // ========== F07：分集评价合并预览 ==========
    if (showEpisodeMerge) {
        EpisodeReviewMergeDialog(
            entries = episodeMergeEntries,
            // > 0 才算「已打分」：滑块停在 0 是「还没评」，不该要求用户勾选覆盖
            currentRating = myRatingValue.takeIf { it > 0f },
            existingImpression = personalImpressionText.takeIf { it.isNotBlank() },
            onDismiss = { showEpisodeMerge = false },
            onConfirm = { result ->
                // 只写本地编辑状态：真正的持久化走下面那条唯一的「保存记录」路径。
                // 因此「取消」天然是零副作用，用户也始终有机会再改一遍。
                result.impression?.let { personalImpressionText = it }
                result.average?.let { myRatingValue = it }
                showEpisodeMerge = false
            },
        )
    }

    // ========== DatePickerDialogs ==========
    if (showStartDatePicker) {
        DatePickerDialogComponent(
            initialDate = startDateValue,
            onDateSelected = { startDateValue = it; showStartDatePicker = false },
            onDismiss = { showStartDatePicker = false },
        )
    }
    if (showFinishDatePicker) {
        DatePickerDialogComponent(
            initialDate = finishDateValue,
            onDateSelected = { finishDateValue = it; showFinishDatePicker = false },
            onDismiss = { showFinishDatePicker = false },
        )
    }

    // ========== 豆瓣词条候选（阶段 E：用户确认后才绑定） ==========
    if (state.doubanCandidates.isNotEmpty()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { ratingCallbacks.onDismissDoubanCandidates() },
            title = { Text("选择豆瓣词条") },
            text = {
                Column {
                    Text(
                        "豆瓣同名作品很多，请自行确认（选中后会记住，可随时解绑）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    state.doubanCandidates.forEach { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { ratingCallbacks.onBindDouban(candidate) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                candidate.title +
                                    (candidate.year?.let { "（" + it + "）" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "#" + candidate.id,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    }

    // ========== 全屏图片查看器（剧照 / 截图） ==========
    imageViewerRequest?.let { (urls, index) ->
        com.otakup.niriko.ui.common.ImageViewer(
            urls = urls,
            initialIndex = index,
            title = subject.displayTitle,
            // 豆瓣图床防盗链：显示与「保存到相册」都要带同一份 Referer
            referer = urls.getOrNull(index)
                ?.takeIf { it.contains("doubanio") }
                ?.let { state.doubanImageReferer },
            onDismiss = { imageViewerRequest = null },
        )
    }

    // ========== 取消收藏确认 ==========
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("取消收藏") },
            text = {
                Text("确定要取消收藏「${subject.displayTitle}」吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveConfirm = false
                    onRemoveFromCollection()
                }) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialogComponent(
    initialDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            ?.atStartOfDay(ZoneId.of("UTC"))
            ?.toInstant()
            ?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = datePickerState.selectedDateMillis
                if (millis != null) {
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.of("UTC"))
                        .toLocalDate()
                    onDateSelected(date)
                } else {
                    onDateSelected(null)
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}

/** 详情页底部收藏状态 dock：L2 玻璃胶囊（复用 GlassSectionCard 的 drawBackdrop 管线）。 */
@Composable
private fun DetailDock(
    backdrop: Backdrop?,
    isScrolling: Boolean,
    statusLabel: String,
    ratingText: String,
    progressText: String?,
    onClick: () -> Unit,
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(statusLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(ratingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            progressText?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(end = 10.dp))
            }
            Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 评分输入：10 星条 + 数字弹簧（P3 §6.5）。拖动/点击点亮，松手数字 1.15→1 弹回。 */
@Composable
private fun StarRatingInput(
    rating: Float,
    onRatingChange: (Float) -> Unit,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(rating) {
        scale.snapTo(1.15f)
        scale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }
    Column {
        Text(
            text = "我的评分：%.1f / 10".format(rating),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxWidth()) {
            (1..10).forEach { i ->
                val filled = i <= rating
                Icon(
                    imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$i 星",
                    tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .weight(1f)
                        .size(28.dp)
                        .clickable { onRatingChange(i.toFloat()) },
                )
            }
        }
    }
}

/**
 * 收藏管理编辑面板（BottomSheet 内容）。
 * 收纳全部收藏编辑控件：状态、进度、评分、个人标签、感想、日期、保存、取消收藏。
 * 本地编辑状态由 SubjectDetailBody 持有（sheet 关闭再开保留未保存修改）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CollectionEditSheet(
    subject: SubjectEntity,
    state: SubjectDetailUiState,
    watchedEpisodesText: String,
    onWatchedEpisodesChange: (String) -> Unit,
    watchedVolumesText: String,
    onWatchedVolumesChange: (String) -> Unit,
    isPrivateLocal: Boolean,
    onIsPrivateChange: (Boolean) -> Unit,
    myRatingValue: Float,
    onMyRatingChange: (Float) -> Unit,
    personalTagsText: String,
    onPersonalTagsChange: (String) -> Unit,
    personalImpressionText: String,
    onPersonalImpressionChange: (String) -> Unit,
    startDateValue: LocalDate?,
    onStartDateChange: (LocalDate?) -> Unit,
    finishDateValue: LocalDate?,
    onFinishDateChange: (LocalDate?) -> Unit,
    watchedTrackIdsLocal: Set<Long>,
    /** F04：逐首勾选曲目（只改本地状态，保存时才落库）。 */
    onToggleTrack: (Long) -> Unit = {},
    isSaveEnabled: Boolean,
    isUpdating: Boolean,
    onUpdateStatus: (WatchStatus) -> Unit,
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>, Int?, Boolean) -> Unit,
    onRemoveFromCollection: () -> Unit,
    onShowStartDatePicker: () -> Unit,
    onShowFinishDatePicker: () -> Unit,
    onShowRemoveConfirm: () -> Unit,
    onOpenEpisodeDetail: (Long) -> Unit = {},
    /** F07：请求「从分集评价生成」——打开预览弹层（真正的文本由弹层算出来再填回这里）。 */
    onRequestEpisodeMerge: () -> Unit = {},
) {
    // Android 14+：宿主 window 背后模糊（iOS 弹窗效果），关闭自动恢复
    WindowBlurBehindEffect()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
    ) {
        // 状态（一段式分段选择器，AniShelf 借鉴）
        Text("观看状态", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .appleGlassCard(shape = RoundedCornerShape(999.dp))
                .padding(4.dp),
        ) {
            ManageableStatuses.forEach { status ->
                val selected = state.currentStatus == status
                val accent = statusTone(status).accent
                val segBg by animateColorAsState(
                    targetValue = if (selected) accent else Color.Transparent,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "statusSeg",
                )
                Box(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(999.dp))
                        .background(segBg.copy(alpha = if (selected) 0.92f else 0f), RoundedCornerShape(999.dp))
                        .clickable(enabled = !isUpdating) { onUpdateStatus(status) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 阶段 B：按类型映射动词（看/读/玩/听）
                    Text(
                        status.verbFor(subject.type),
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ========== 我的记录 ==========
        Text("我的记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // 观看进度（按类型区分 UI）
        ProgressInputSection(
            subject = subject,
            episodes = state.episodes,
            progressText = watchedEpisodesText,
            onProgressChange = onWatchedEpisodesChange,
            watchedVolumesText = watchedVolumesText,
            onWatchedVolumesChange = onWatchedVolumesChange,
            onOpenEpisodeDetail = onOpenEpisodeDetail,
        )
        Spacer(Modifier.height(12.dp))

        // 卷进度（书籍/漫画，阶段 B）
        if (subject.type == SubjectType.MANGA || subject.type == SubjectType.BOOK) {
            OutlinedTextField(value = watchedVolumesText, onValueChange = onWatchedVolumesChange,
                modifier = Modifier.fillMaxWidth(), label = { Text("卷进度") }, singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
        }

        // F04：音乐曲目（只在音乐类型显示）。勾选只改本地状态 —— 取消保存就什么都不写。
        if (subject.type == SubjectType.MUSIC && state.episodes.isNotEmpty()) {
            TrackListSection(
                episodes = state.episodes,
                watchedTrackIds = watchedTrackIdsLocal,
                onToggleTrack = onToggleTrack,
            )
            Spacer(Modifier.height(12.dp))
        }

        // 评分：玻璃滑块（0.5 分辨率，P3）
        GlassRatingSlider(
            rating = myRatingValue,
            onRatingChange = onMyRatingChange,
        )
        Spacer(Modifier.height(8.dp))

        // 私密收藏（阶段 B：仅本地可见，导出/备份排除）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("私密收藏", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            GlassToggle(checked = isPrivateLocal, onCheckedChange = onIsPrivateChange)
        }
        Spacer(Modifier.height(12.dp))

        // 个人标签（用于收藏库筛选/计数，结构化分类）
        OutlinedTextField(value = personalTagsText, onValueChange = onPersonalTagsChange,
            modifier = Modifier.fillMaxWidth(), label = { Text("个人标签（用逗号分隔）") }, singleLine = true,
        )
        Text("提示：标签可在收藏库中按标签筛选，如：追番中、神作、待补", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))

        // 个人感想
        OutlinedTextField(value = personalImpressionText, onValueChange = onPersonalImpressionChange,
            modifier = Modifier.fillMaxWidth().height(120.dp), label = { Text("个人感想") },
        )
        // F07：从分集评价生成（预览 → 填入编辑区；持久化仍走下面的「保存记录」，只保留一条写路径）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onRequestEpisodeMerge) { Text("从分集评价生成…") }
            Spacer(Modifier.weight(1f))
            Text(
                text = "会整块替换上次生成的内容，手写部分不受影响",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))

        // 开始 / 完成日期（两列胶囊，AniShelf 借鉴）
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DatePill("开始日期", startDateValue?.format(DateFormat) ?: "未设置", onClick = onShowStartDatePicker, Modifier.weight(1f))
            DatePill("完成日期", finishDateValue?.format(DateFormat) ?: "未设置", onClick = onShowFinishDatePicker, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        // 保存按钮
        Button(onClick = {
            // 游戏类型：UI 按小时输入，存储统一为分钟（与 Steam 导入一致）
            val storedProgress = if (subject.type == SubjectType.GAME) {
                PlaytimeConverter.hoursToMinutes(watchedEpisodesText.toIntOrNull())
            } else {
                watchedEpisodesText.toIntOrNull()
            }
            onSaveRecord(
                storedProgress,
                myRatingValue,
                personalTagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                personalImpressionText.ifBlank { null },
                startDateValue,
                finishDateValue,
                watchedTrackIdsLocal,
                watchedVolumesText.toIntOrNull().takeIf { subject.type == SubjectType.MANGA || subject.type == SubjectType.BOOK },
                isPrivateLocal,
            )
        }, enabled = isSaveEnabled, modifier = Modifier.fillMaxWidth(0.85f).align(Alignment.CenterHorizontally)) {
            Text(if (isUpdating) "保存中…" else "保存记录")
        }
        Spacer(Modifier.height(12.dp))

        // 取消收藏
        OutlinedButton(onClick = onShowRemoveConfirm, enabled = !isUpdating,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(if (isUpdating) "处理中…" else "取消收藏") }
    }
}

/**
 * 按 SubjectType 展示不同类型的元信息。
 * 避免在 SubjectDetailBody 中用 if(type) 堆砌。
 */
@Composable
private fun SubjectMetaSection(subject: SubjectEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(subject.type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        subject.ratingScore?.let { Text("评分：%.1f".format(it), style = MaterialTheme.typography.bodyMedium) }
    }
    when (subject.type) {
        SubjectType.ANIME -> {
            val parts = mutableListOf<String>()
            subject.platform?.let { parts.add(it) }
            subject.totalEpisodes?.let { parts.add("${it}集") }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        SubjectType.BOOK, SubjectType.MANGA -> {
            val parts = mutableListOf<String>()
            subject.platform?.let { parts.add(it) }
            subject.volumes?.takeIf { it > 0 }?.let { parts.add("${it}卷") }
            subject.series?.let { s -> parts.add(if (s) "系列" else "单本") }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        SubjectType.GAME -> {
            subject.platform?.let { p -> Text(if (p == "游戏") "电子游戏" else p, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
        SubjectType.MUSIC -> {
            subject.platform?.let { p -> Text(p, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        }
        SubjectType.REAL -> {
            val parts = mutableListOf<String>()
            subject.platform?.let { parts.add(it) }
            subject.totalEpisodes?.let { parts.add("${it}集") }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        SubjectType.PERSON, SubjectType.OTHER -> {}
    }
    // 放送信息（阶段 A：精确到分钟）
    val airParts = mutableListOf<String>()
    subject.airDate?.let { airParts.add(it) }
    subject.airWeekday?.let { wd -> airParts.add(weekdayName(wd)) }
    subject.airTimeMinutes?.let { m -> airParts.add("%02d:%02d".format(m / 60, m % 60)) }
    if (airParts.isNotEmpty()) {
        Text(airParts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** 星期数字 → 中文。0=周日，1..7=周一到周日。 */
private fun weekdayName(wd: Int): String = when (wd) {
    0 -> "周日"
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}

/** 圣地巡礼（Anitabi）取景地标区块（阶段 K）。 */
@Composable
private fun AnitabiSection(
    subject: SubjectEntity,
    city: String,
    points: List<com.otakup.niriko.data.remote.anitabi.AnitabiLitePoint>,
    pointsLength: Int,
    imagesLength: Int,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("取景地标", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "巡礼地图",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://anitabi.cn/map?bangumiId=" + subject.subjectId),
                            )
                        )
                    }
                },
            )
        }
        val summary = listOfNotNull(city.takeIf { it.isNotBlank() }, "${pointsLength} 个地标、${imagesLength} 张截图")
        if (summary.isNotEmpty()) {
            Text(summary.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            points.take(14).forEachIndexed { index, p -> AnitabiPointCard(p, index + 1) }
        }
    }
}

@Composable
private fun AnitabiPointCard(point: com.otakup.niriko.data.remote.anitabi.AnitabiLitePoint, index: Int) {
    val title = point.cn ?: point.name ?: "取景点"
    val time = point.s?.let { sec -> "%02d:%02d".format(sec / 60, sec % 60) }
    Column(modifier = Modifier.width(160.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (point.image != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(point.image).crossfade(true).build(),
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(if (index == 1) "无截图" else ("#" + index), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(point.ep?.let { "EP" + it }, time, ("#" + index)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 猜你喜欢（阶段 F：本地 tag 共现，横滑卡片，带共享元素）。 */
@Composable
private fun GuessYouLikeSection(
    subjects: List<SubjectEntity>,
    onSubjectClick: (Long) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val parentId = com.otakup.niriko.navigation.LocalDetailSubjectId.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("猜你喜欢", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberDetailPixelRailState("recommendations")),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            subjects.forEachIndexed { index, s ->
                val coverKey = com.otakup.niriko.navigation.recommendationCoverKey(parentId, s.subjectId, index)
                val interactionSource = remember(s.subjectId) { MutableInteractionSource() }
                Column(
                    modifier = Modifier.width(120.dp)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                            onClick = {
                                com.otakup.niriko.navigation.SubjectCoverHandoff.prepare(s.subjectId, coverKey)
                                com.otakup.niriko.util.SubjectNavigationSeed.prepare(s)
                                onSubjectClick(s.subjectId)
                            },
                        ),
                ) {
                    CoverImage(
                        coverUrl = s.coverUrl,
                        contentDescription = s.displayTitle,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        aspectRatio = 3f / 4f,
                        sharedElementKey = coverKey,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        subjectId = s.subjectId,
                        coverContentModifier = Modifier.pressTilt(interactionSource),
                    )
                    Text(s.displayTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, minLines = com.otakup.niriko.util.RailCardPolicy.WIDE_TITLE_LINES, maxLines = com.otakup.niriko.util.RailCardPolicy.WIDE_TITLE_LINES, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * 观看进度输入组件，按 SubjectType 展示不同 UI。
 * ANIME / BOOK / REAL：totalEpisodes ≤ 52 时用选集网格，否则用数字输入。
 * 当有剧集标题数据时，显示增强列表（剧集标题 + 勾选标记）。
 * GAME：游戏时长（小时）。
 * MUSIC：不渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
/** 日期胶囊（AniShelf 借鉴）：点击弹日期选择。 */
@Composable
private fun DatePill(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 选集/卷数 chip：选中 tonal 填充 + 边框 1dp→1.5dp（P3 §6.6）。
 *
 * **修复 R3**：改造前这里用 `FilterChip` 并在外层挂 `Modifier.combinedClickable`，
 * 但 FilterChip 内部自带可点击 Surface 会**消费手势**，长按永远不触发（你反馈的
 * 「收藏面板长按单集无反应」就是这个原因）。
 * 现在改为自绘 Surface + combinedClickable，视觉与原来一致。
 */
@Composable
private fun SelectionChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    /** 长按：弹出单集快捷菜单。单击仍然只改进度，保持既有习惯。 */
    onLongClick: (() -> Unit)? = null,
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.outlineVariant
    val textColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = borderColor,
        ),
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}


@Composable
private fun ProgressInputSection(
    subject: SubjectEntity,
    episodes: List<EpisodeInfo>,
    progressText: String,
    onProgressChange: (String) -> Unit,
    /** 卷进度文本（书籍 / 漫画）。有总卷数时「卷数」选择器写的就是它。 */
    watchedVolumesText: String = "",
    onWatchedVolumesChange: (String) -> Unit = {},
    /** 长按某一集 → 打开单集二级页（阶段 B）。 */
    onOpenEpisodeDetail: (Long) -> Unit = {},
) {
    when (subject.type) {
        SubjectType.ANIME, SubjectType.REAL -> {
            val total = subject.totalEpisodes
            // 修复 BUG-5：原来上限 52，53 集以上的长篇（海贼/柯南/长篇国产）直接退化成
            // 纯数字输入，没有选集入口。提高到 300 并保留原有降级分支。
            if (total != null && total in 1..300) {
                // 当有标题数据时展示增强列表
                if (episodes.isNotEmpty()) {
                    val currentProgress = progressText.toIntOrNull() ?: 0
                    Text("选集", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (ep in 1..total) {
                            val isWatched = ep <= currentProgress
                            val epInfo = episodes.find { it.ep.toInt() == ep || it.sort.toInt() == ep }
                            val label = epInfo?.let {
                                val title = it.nameCn?.takeIf { n -> n.isNotBlank() } ?: it.name
                                if (title.isBlank() || title.length > 10) "$ep" else "$ep $title"
                            } ?: "$ep"
                            SelectionChip(
                                selected = isWatched,
                                label = label,
                                onClick = { onProgressChange(ep.toString()) },
                                onLongClick = epInfo?.let { info -> { onOpenEpisodeDetail(info.id) } },
                            )
                        }
                    }
                } else {
                    // 无标题数据时的简单网格
                    Text("选集", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    val currentProgress = progressText.toIntOrNull() ?: 0
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (ep in 1..total) {
                            val isWatched = ep <= currentProgress
                            SelectionChip(
                                selected = isWatched,
                                label = "$ep",
                                onClick = { onProgressChange(ep.toString()) },
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(value = progressText, onValueChange = { onProgressChange(it.filter { c -> c.isDigit() }) },
                    modifier = Modifier.fillMaxWidth(), label = {
                        if (subject.type == SubjectType.REAL || subject.type == SubjectType.ANIME) Text("观看进度（集）")
                        else Text("观看进度（集/卷）")
                    }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        SubjectType.GAME -> {
            OutlinedTextField(value = progressText, onValueChange = { onProgressChange(it.filter { c -> c.isDigit() }) },
                modifier = Modifier.fillMaxWidth(), label = { Text("游戏时长（小时）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        SubjectType.MUSIC -> {
            // 曲目勾选已合并到曲目列表（TrackListSection），此处只显示计数
            if (episodes.isNotEmpty()) {
                val tracks = episodes
                    .filter { it.type == 0 || it.type == 2 || it.type == 3 }
                val currentProgress = progressText.toIntOrNull() ?: 0
                Text(
                    "已听 ${currentProgress.coerceAtMost(tracks.size)} / ${tracks.size} 首（在曲目列表中点击标记）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SubjectType.BOOK, SubjectType.MANGA -> {
            if (subject.series == false) { /* 单本不展示进度 */ } else {
                // B11：这一块是「卷 / 话」二选一的入口，必须和真正存进去的字段一致。
                // 改造前的错误链条：界面把 1..总卷 的格子标成「卷数」，点击却写进
                // watchedEpisodes（话/集进度），而收藏页进度条又拿总话数当分母 ——
                // 《灌篮高手》选满 31 卷，存下来是 31 话，进度条显示 31/276 ≈ 11%。
                val totalVolumes = subject.volumes?.takeIf { it > 0 }
                val useVolume = totalVolumes != null
                val total = totalVolumes ?: subject.totalEpisodes
                val currentText = if (useVolume) watchedVolumesText else progressText
                val change: (String) -> Unit = if (useVolume) onWatchedVolumesChange else onProgressChange
                val unitLabel = if (useVolume) "卷数" else "话数"
                // 修复 BUG-5：原来上限 52，53 集以上的长篇（海贼/柯南/长篇国产）直接退化成
                // 纯数字输入，没有选集入口。提高到 300 并保留原有降级分支。
                if (total != null && total in 1..300) {
                    Text(unitLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    val currentProgress = currentText.toIntOrNull() ?: 0
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (v in 1..total) {
                            val isRead = v <= currentProgress
                            SelectionChip(
                                selected = isRead,
                                label = "$v",
                                onClick = { change(v.toString()) },
                            )
                        }
                    }
                } else {
                    OutlinedTextField(value = currentText, onValueChange = { change(it.filter { c -> c.isDigit() }) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("阅读进度（$unitLabel）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        }
        SubjectType.PERSON, SubjectType.OTHER -> {}
    }
}

@Preview(showBackground = true)
@Composable
private fun SubjectDetailPreview() {
    NirikoTheme {
        SubjectDetailBody(
            subject = SubjectEntity(subjectId = 328609, title = "ぼっち・ざ・ろっく！", titleCN = "孤独摇滚！", type = SubjectType.ANIME, coverUrl = null, totalEpisodes = 12, ratingScore = 8.4f),
            state = SubjectDetailUiState(
                isInCollection = true, currentStatus = WatchStatus.COMPLETED, isLoading = false,
                watchedEpisodes = 12, myRating = 9.5f, personalTags = listOf("芳文社", "音乐"), personalImpression = "好看！",
                startDate = LocalDate.of(2022, 10, 8), finishDate = LocalDate.of(2022, 12, 24),
            ),
            glassBackdrop = null,
            onAddToCollection = {}, onUpdateStatus = {}, onRemoveFromCollection = {}, onSaveRecord = { _, _, _, _, _, _, _, _, _ -> },
        )
    }
}

/** 条目 infobox 区块：键值对列表（艺术家/发行商/发售日期等）。 */
/** Steam 补充信息区块（GlassCard 风格，仅游戏类型已绑定 Steam 时显示）。 */
@Composable
private fun SteamInfoSection(
    steam: SteamGameEntity,
    achievements: SteamAchievements? = null,
    chartRank: SteamChartEntry? = null,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    isPlaceholder: Boolean = false,
    onRematchPlaceholder: () -> Unit = {},
    onUnbindSteam: () -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Steam 信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // 独占词条徽标（Bangumi 无词条的占位作品）
                if (isPlaceholder) {
                    Text(
                        "独占词条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    "appid ${steam.appId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 价格 + 在线人数
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 价格
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = steam.priceCents?.let { cents ->
                            val symbol = if (steam.currency == "CNY") "¥" else (steam.currency ?: "")
                            if (cents == 0) "免费"
                            else {
                                val yuan = cents / 100
                                val fen = cents % 100
                                if (fen == 0) "$symbol$yuan" else "$symbol$yuan.${fen.toString().padStart(2, '0')}"
                            }
                        } ?: "价格未知",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                // 当前在线
                steam.currentPlayers?.let { players ->
                    Text(
                        text = "当前在线 ${formatPlayerCount(players)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                // 活跃玩家排名（Top 100 活跃榜；未上榜不显示）
                chartRank?.let { rank ->
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "活跃排名 #${rank.rank}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }

            // 开发商 / 发行商
            if (steam.developers.isNotEmpty() || steam.publishers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                if (steam.developers.isNotEmpty()) {
                    SteamMetaRow("开发商", steam.developers.joinToString(" / "))
                }
                if (steam.publishers.isNotEmpty()) {
                    SteamMetaRow("发行商", steam.publishers.joinToString(" / "))
                }
            }

            // Metacritic
            steam.metacriticScore?.let { score ->
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("Metacritic", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        score.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (score >= 75) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            // Steam 类型标签
            if (steam.steamTags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    steam.steamTags.take(8).forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 成就进度（隐私未公开/未登录时 null，隐藏区块）
            if (achievements != null && achievements.total > 0) {
                Spacer(Modifier.height(12.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "成就",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${achievements.unlocked} / ${achievements.total} 已解锁",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { achievements.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                    // 最近解锁的前 5 条（含未解锁占位提示用「未解锁」）
                    Spacer(Modifier.height(6.dp))
                    achievements.items
                        .sortedByDescending { it.achieved }
                        .take(5)
                        .forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (item.achieved) "✓" else "○",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.achieved) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.achieved) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                }
            }

            // 截图横滑
            if (steam.screenshots.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(steam.screenshots) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "Steam 截图",
                            modifier = Modifier
                                .width(200.dp)
                                .aspectRatio(16f / 9f)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            // 独占占位条目：重新匹配到 Bangumi 词条（升级迁移）
            if (isPlaceholder) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRematchPlaceholder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重新匹配 Bangumi 词条（升级为正式条目）")
                }
            } else {
                // 已绑定正式词条：可解除绑定（错绑数据手动解绑后重新匹配）
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onUnbindSteam,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("解除 Steam 绑定")
                }
            }
        }
    }
}

/** Steam 元信息行（开发商/发行商等）。 */
@Composable
private fun SteamMetaRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 游玩人数格式化：≥10000 → "1.2万"，≥1000 → "1.2K"，否则原样。 */
private fun formatPlayerCount(count: Int): String = when {
    count >= 10_000 -> "%.1f万".format(count / 10_000.0)
    count >= 1_000 -> "%.1fK".format(count / 1_000.0)
    else -> count.toString()
}

/**
 * VNDB 候选区块（未绑定 GAME 条目）：按标题搜索的 Top 候选，一键手动绑定。
 * 解决"VNDB 数据不可见"——即使自动匹配未命中，用户也能看到 VNDB 有词条并主动绑定。
 */
@Composable
private fun VndbCandidateSection(
    candidates: List<com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto>,
    /** 每条候选的匹配理由（externalId → 理由列表），第 4 轮 D。 */
    candidateReasons: Map<String, List<String>> = emptyMap(),
    /** 手动搜索/粘贴 ID 的一行反馈。 */
    manualMessage: String? = null,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    onBind: (String) -> Unit = {},
    onSearchMore: (String) -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "VNDB 词条候选（未绑定）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Bangumi 未自动匹配到 VNDB 词条，可能是以下作品。每条的「为什么」来自四层匹配" +
                    "（infobox id → 多语言标题查询 → 全标题集合打分），据此判断再绑定：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            // 搜索更多：允许用户按任意关键词重搜 VNDB，或直接粘贴 vndb id / 链接
            var vndbSearchQuery by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = vndbSearchQuery,
                    onValueChange = { vndbSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("关键词，或直接粘贴 v12345 / vndb 链接") },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { onSearchMore(vndbSearchQuery.trim()) }) {
                    Text("搜索", style = MaterialTheme.typography.labelMedium)
                }
            }
            manualMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (candidates.isEmpty()) {
                Text(
                    "暂无候选。可输入日文原名 / 罗马音再搜一次——中文名在 VNDB 里往往不是主标题；" +
                        "也可以直接粘贴 vndb id。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            candidates.forEach { vn ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            vn.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = buildList {
                            vn.ctitle?.takeIf { it.isNotBlank() && it != vn.title }?.let { add(it) }
                            vn.released?.takeIf { it != "TBA" && it != "unknown" }?.let { add(it) }
                            vn.rating?.let { add("%.1f 分".format(it / 10f)) }
                            vn.lengthMinutes?.let { add(formatPlaytime(it)) }
                        }.joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        // 匹配理由（第 4 轮 D）：把「为什么认为它是这条」写出来
                        candidateReasons[vn.id]?.takeIf { it.isNotEmpty() }?.let { reasons ->
                            Text(
                                reasons.take(3).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onBind(vn.id) }) {
                        Text("绑定", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * VNDB 已绑定但详情未拉取：显示绑定状态 + 重试 + 解绑（不再静默隐藏区块）。
 */
@Composable
private fun VndbBoundPendingSection(
    vndbId: String,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    onRetry: () -> Unit = {},
    onUnbind: () -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VNDB 信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "vndb id $vndbId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onUnbind) {
                    Text("解绑", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "已绑定但数据未拉取（可能是网络不可达或 API 暂时失败）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRetry) {
                Text("重试", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * AniList 已绑定但详情/富信息均未拉取：显示绑定状态 + 重试 + 解绑。
 */
@Composable
private fun AniListBoundPendingSection(
    anilistId: Long,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    onRetry: () -> Unit = {},
    onUnbind: () -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AniList 信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "anilist id $anilistId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onUnbind) {
                    Text("解绑", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "已绑定但数据未拉取（可能是网络不可达或 API 暂时失败）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onRetry) {
                Text("重试", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * AniList 信息区块（已绑定 bangumi 词条）：只展示 Bangumi 没有/不如 AniList 的内容。
 * 主打：排名/热度/趋势/下一集倒计时/格式/来源/季·年/AniList 外链/三标题。
 * 评分/标签/简介/开播等已由主条目展示，不再重复。
 */
@Composable
private fun AniListInfoSection(
    detail: com.otakup.niriko.data.remote.game.GameItemDetail?,
    rich: AniListRichDetail?,
    anilistId: Long?,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    onUnbind: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AniList 信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    anilistId?.let { "anilist id $it" } ?: "anilist",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (anilistId != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onUnbind) {
                        Text("解绑", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            if (rich != null) {
                // 排名（多维度：All Time / 本季 / Popular / Score）
                if (rich.rankings.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rich.rankings.take(3).forEach { r ->
                            val rankText = "#${r.rank} ${r.type ?: ""} ${r.season ?: ""} ${r.year ?: ""}".trim()
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    rankText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 热度/趋势/收藏
                val stats = listOfNotNull(
                    rich.popularity?.let { "热度 $it" },
                    rich.favourites?.let { "收藏 $it" },
                    rich.trending?.let { "趋势 $it" },
                )
                if (stats.isNotEmpty()) {
                    Text(
                        text = stats.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // 下一集倒计时
                if (rich.nextAiringEpisode != null && rich.nextAiringAt != null) {
                    val remainSec = rich.nextAiringAt - System.currentTimeMillis() / 1000
                    val remainText = if (remainSec > 0) {
                        val days = remainSec / 86400
                        val hours = (remainSec % 86400) / 3600
                        if (days > 0) "$days 天 $hours 小时" else "$hours 小时"
                    } else "已播出"
                    SteamMetaRow("下一集", "ep ${rich.nextAiringEpisode} · $remainText")
                }

                // 格式/状态/来源/季
                rich.format?.let { SteamMetaRow("格式", it) }
                rich.status?.let { SteamMetaRow("状态", it) }
                rich.source?.let { SteamMetaRow("原作来源", it) }
                if (rich.seasonYear != null) {
                    SteamMetaRow("季", listOfNotNull(rich.season, rich.seasonYear.toString()).joinToString(" "))
                }

                // AniList 三标题
                listOfNotNull(
                    rich.titleRomaji?.let { "罗马字" to it },
                    rich.titleEnglish?.let { "英文" to it },
                    rich.titleNative?.let { "原名" to it },
                ).take(3).forEach { (label, value) ->
                    SteamMetaRow(label, value)
                }

                // 外链
                rich.siteUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    Spacer(Modifier.height(4.dp))
                    val context = LocalContext.current
                    Text(
                        text = "打开 AniList",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }
                        },
                    )
                }
            } else {
                // 富信息未取到但已绑定：只展示基础连接信息，避免重复 Bangumi 已有的评分/简介/标签/开播。
                if (anilistId != null) {
                    SteamMetaRow("AniList", "id $anilistId")
                }
                detail?.item?.aliases?.takeIf { it.isNotBlank() }?.let {
                    SteamMetaRow("别名", it)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "富信息（排名/热度/收藏/下一集）暂不可用，已回退到基础数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onRetry) {
                    Text("重试", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * AniList 候选区块（未绑定条目）：按标题搜索的 Top 候选，一键手动绑定。
 * 与 VNDB 候选区块同款，解决"AniList 数据不可见"——自动匹配未命中时
 * 用户也能看到 AniList 有词条并主动绑定。
 */
@Composable
private fun AniListCandidateSection(
    candidates: List<com.otakup.niriko.data.remote.game.GameItem>,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    onBind: (Long) -> Unit = {},
    onSearchMore: (String) -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "AniList 词条候选（未绑定）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Bangumi 未自动匹配到 AniList 词条，可能是以下作品，点击绑定：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
            )
            // 搜索更多：允许用户按任意关键词重搜 AniList
            var anilistSearchQuery by remember { mutableStateOf("") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = anilistSearchQuery,
                    onValueChange = { anilistSearchQuery = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("搜索更多 AniList...") },
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { onSearchMore(anilistSearchQuery.trim()) }) {
                    Text("搜索", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (candidates.isEmpty()) {
                Text(
                    "暂无候选，可能网络不可达，可输入关键词搜索更多。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            candidates.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = buildList {
                            item.aliases?.takeIf { it.isNotBlank() }?.let { add(it) }
                            item.ratingScore?.let { add("%.1f 分".format(it)) }
                        }.joinToString(" · ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        item.sourceGameId.removePrefix("media-").toLongOrNull()?.let(onBind)
                    }) {
                        Text("绑定", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

/**
 * AniList 源条目信息区块（AniList 兜底来源条目 sourceId="anilist"，即 bangumi 无词条
 * 由 AniList 兜底创建的作品）。展示该条目经 GameItemMapper 落库时的 AniList 侧数据
 * （评分/集数/格式/开播/标签），与 Steam/VNDB 区块同款 GlassSectionCard 风格。
 */
@Composable
private fun AniListSourceInfoSection(
    subject: SubjectEntity,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "AniList 信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "anilist media-${subject.subjectId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))

            // 评分（AniList averageScore 0-100 → GameItemMapper 已转 0-10）
            if (subject.ratingScore != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "%.1f".format(subject.ratingScore),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                subject.ratingTotal?.let { "AniList $it 分" } ?: "AniList 评分",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                Text(
                    "暂无评分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 集数/格式
            subject.totalEpisodes?.let {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow("集数", "$it 集")
            }
            subject.platform?.let {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow("格式", it)
            }
            subject.airDate?.let {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow("开播", it)
            }

            // 标签
            if (subject.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // F02：与社区标签同款
                    subject.tags.take(8).forEach { tag ->
                        TagChip(label = tag)
                    }
                }
            }
        }
    }
}

/**
 * VNDB 信息区块（第 4 轮 E：只留 VNDB **独有**的数据）。
 *
 * ## 差异化原则
 *
 * 改造前这个区块展示了评分 / 开发商 / 发售日 / 简介 —— 这些 Bangumi 大多也有，
 * 属于「占了版面却没给新信息」。现在只保留 VNDB 独有且对本项目有价值的：
 *
 * | 保留 | 理由 |
 * |---|---|
 * | 平均游玩时长 | VNDB 独有，且是选 VN 时最实用的决策信息 |
 * | 贝叶斯评分 + 票数 + **popularity** | 项目里唯一能做「冷门佳作 vs 热门平庸」区分的地方 |
 * | 原语 + 支持语言 | Bangumi 没有语言维度 |
 * | 平台 | Bangumi 的 platform 字段常为空 |
 * | 标签 + 权重 | VNDB 的标签体系是它最强的部分 |
 * | 截图 | 常比 Steam 全，尤其日式 PC 游戏 |
 * | **relations** | 前作/续作/同世界观——匹配不上的作品可顺关系链反查 |
 *
 * 移除：整剧评分（已有权威评分卡）、开发商/发售日/简介（Bangumi 通常已有）。
 */
@Composable
private fun VndbInfoSection(
    detail: com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto,
    relations: List<com.otakup.niriko.data.repository.VndbRepository.RelationWithTitle>,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
    onBindRelation: (String) -> Unit = {},
    onUnbind: () -> Unit = {},
) {
    GlassSectionCard(
        backdrop = backdrop,
        isScrolling = isScrolling,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "VNDB 信息",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "vndb id ${detail.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onUnbind) {
                    Text("解绑", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))

            // —— 第一行：VNDB 独有的三个数字（时长 / 贝叶斯评分+票数 / 人气） ——
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                detail.lengthMinutes?.let { minutes ->
                    VndbStatChip(
                        value = formatPlaytime(minutes),
                        label = "平均游玩时长",
                        emphasis = true,
                    )
                }
                detail.rating?.let { raw ->
                    VndbStatChip(
                        value = "%.1f".format(raw / 10f),
                        label = detail.votecount?.let { "贝叶斯分 · $it 票" } ?: "贝叶斯分",
                    )
                }
                detail.popularity?.let { pop ->
                    VndbStatChip(
                        value = "%.0f".format(pop),
                        label = "人气",
                    )
                }
                if (detail.rating == null && detail.lengthMinutes == null && detail.popularity == null) {
                    Text(
                        "VNDB 暂无评分/时长数据（冷门条目常见）。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // —— 语言与平台（Bangumi 没有的语言维度） ——
            detail.olang?.takeIf { it.isNotBlank() }?.let { lang ->
                Spacer(Modifier.height(8.dp))
                SteamMetaRow("原语", vndbLanguageName(lang))
            }
            if (detail.languages.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow(
                    "支持语言",
                    detail.languages.take(10).joinToString(" / ") { vndbLanguageName(it) },
                )
            }
            if (detail.platforms.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow(
                    "平台",
                    detail.platforms.joinToString(" / ") { vndbPlatformName(it) },
                )
            }

            // —— 标签（带权重，这是 VNDB 最强的一块） ——
            if (detail.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("标签（按权重）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "★ 越多表示越贴切",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // 按权重降序取前 12 个（VNDB 返回顺序不保证，必须自己排）
                    detail.tags.sortedByDescending { it.rating ?: 0.0 }.take(12).forEach { tag ->
                        val strength = (tag.rating ?: 0.0).toInt().coerceIn(0, 3)
                        // F02：权重强度改用「强调态」表达（原来靠 FilterChip 的 selected 实心色）
                        TagChip(
                            label = tag.name + if (strength > 0) " " + "★".repeat(strength) else "",
                            emphatic = strength >= 2,
                        )
                    }
                }
            }

            // —— 截图 ——
            if (detail.screenshots.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("截图", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    detail.screenshots.take(8).forEach { shot ->
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(shot.thumbnail ?: shot.url).crossfade(true).build(),
                            contentDescription = "VNDB 截图",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(96.dp)
                                .height(60.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                    }
                }
            }

            // —— 关联作品（前作/续作/同世界观）：点一下即可绑定跳转 ——
            if (relations.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "关联作品",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "「同世界观」这类关系常能帮你找到自动匹配不上的作品——点一下即绑定到本条目。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    relations.forEach { relation ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBindRelation(relation.vndbId) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            relation.coverUrl?.let { cover ->
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current).data(cover).crossfade(true).build(),
                                    contentDescription = relation.displayTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(width = 32.dp, height = 44.dp)
                                        .clip(MaterialTheme.shapes.extraSmall),
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    relation.displayTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    buildString {
                                        append(relation.relationLabel)
                                        if (!relation.official) append(" · 同人")
                                        relation.released?.takeIf { it != "TBA" }?.let { append(" · $it") }
                                        relation.rating?.let { append(" · ★ %.1f".format(it / 10f)) }
                                        append(" · ${relation.vndbId}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "绑定",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            // —— 简介放最后（Bangumi 通常有，但 VNDB 的常更详细，所以保留可展开） ——
            detail.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(10.dp))
                var expanded by remember { mutableStateOf(false) }
                RichText(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 20 else 3,
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起简介" else "展开简介", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** VNDB 数字胶囊（值 + 标签）。 */
@Composable
private fun VndbStatChip(value: String, label: String, emphasis: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (emphasis) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (emphasis) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** VNDB 平台代码 → 展示名（未知回退原码，不丢信息）。 */
private fun vndbPlatformName(code: String): String = when (code.lowercase()) {
    "win" -> "Windows"
    "lin" -> "Linux"
    "mac" -> "macOS"
    "web" -> "Web"
    "and" -> "Android"
    "ios" -> "iOS"
    "dvd" -> "DVD"
    "bd" -> "Blu-ray"
    "vnds" -> "NDS"
    "psp" -> "PSP"
    "ps2" -> "PS2"
    "ps3" -> "PS3"
    "ps4" -> "PS4"
    "ps5" -> "PS5"
    "psv" -> "PS Vita"
    "swi" -> "Switch"
    "nin" -> "Nintendo"
    "3ds" -> "3DS"
    "nds" -> "NDS"
    "gba" -> "GBA"
    "gb" -> "GB"
    "xbo" -> "Xbox"
    "x360" -> "Xbox 360"
    "xone" -> "Xbox One"
    "xsx" -> "Xbox Series"
    "dos" -> "DOS"
    else -> code
}

/** VNDB 时长（分钟）→ 可读文本（保留旧签名，其它调用点仍在用）。 */
private fun formatVndbLength(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    minutes > 0 -> "${minutes}m"
    else -> "-"
}

@Composable
private fun InfoBoxSection(entries: List<InfoBoxEntry>) {
    // 长/略长信息卡限高 + 内部滚动，避免把玻璃卡撑到失控高度。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("详细信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
            ) {
                Text(
                    entry.key,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                val link = remember(entry.value) { extractFirstUrl(entry.value) }
                if (link == null) {
                    Text(
                        entry.value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    // 阶段 7：infobox 里大量「官方网站 / 引用来源 / Twitter / IMDb」等值就是 URL，
                    // 此前一律渲染成不可点的纯文本。
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.value, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "打开链接",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .clickable { runCatching { uriHandler.openUri(link) } }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 从一段文本里取出第一个 http(s) 链接（infobox 里「官方网站」「引用来源」等常见）。 */
private val URL_REGEX = Regex("https?://[^\\s，。；、）)\\]]+")

private fun extractFirstUrl(text: String): String? =
    URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', '）', '。')


// ==================== 分享 ====================

/** 加载封面 Bitmap（IO 线程），失败返回 null（卡片走占位）。 */
private suspend fun loadShareCover(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            // allowHardware(false)：分享卡使用软件 Canvas 渲染，硬件位图会导致
            // IllegalArgumentException: Software rendering doesn't support hardware bitmaps
            val req = ImageRequest.Builder(context).data(url).size(720).allowHardware(false).build()
            val result = Coil.imageLoader(context).execute(req)
            (result.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }
}

/** 元信息行：「平台 · 集数/卷数 · 年份」（类型敏感，详情页 SubjectMetaSection 语义）。 */
private fun buildMetaText(subject: SubjectEntity): String? {
    val parts = mutableListOf<String>()
    when (subject.type) {
        SubjectType.ANIME, SubjectType.REAL -> {
            subject.platform?.let { parts.add(it) }
            subject.totalEpisodes?.let { parts.add("${it}集") }
        }
        SubjectType.BOOK, SubjectType.MANGA -> {
            subject.platform?.let { parts.add(it) }
            subject.volumes?.takeIf { it > 0 }?.let { parts.add("${it}卷") }
            subject.series?.let { s -> parts.add(if (s) "系列" else "单本") }
        }
        SubjectType.GAME -> {
            subject.platform?.let { p -> parts.add(if (p == "游戏") "电子游戏" else p) }
        }
        SubjectType.MUSIC -> {
            subject.platform?.let { parts.add(it) }
        }
        SubjectType.PERSON, SubjectType.OTHER -> {}
    }
    subject.airDate?.let { parts.add(it) }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
}

/** 构建分享卡数据（作品卡 / 收藏卡由 isInCollection 区分；配置交由 SharePreviewSheet）。 */
private fun buildShareData(
    context: Context,
    subject: SubjectEntity,
    state: SubjectDetailUiState,
    cover: android.graphics.Bitmap?,
): ShareCardData {
    val title = TitleResolver.resolve(subject.titleCN, subject.title)
    val metaText = buildMetaText(subject)
    val isColl = state.isInCollection
    val total = subject.totalEpisodes
    val watched = state.watchedEpisodes
    val progressText = if (isColl) {
        if (subject.type == SubjectType.GAME) {
            watched?.let { PlaytimeConverter.format(it)?.let { fmt -> "已玩 " + fmt } }
        } else {
            when {
                watched != null && total != null && total > 0 -> watched.toString() + " / " + total.toString() + " 集"
                watched != null -> "已看 " + watched.toString() + " 集"
                else -> null
            }
        }
    } else null
    val progressRatio = if (isColl && watched != null && total != null && total > 0) watched / total.toFloat() else 0f
    val dateText = if (isColl) listOfNotNull(
        state.startDate?.let { "开始：" + DateFormat.format(it) },
        state.finishDate?.let { "完成：" + DateFormat.format(it) },
    ).joinToString(" · ").takeIf { it.isNotBlank() } else null

    return ShareCardData(
        cover = cover,
        typeLabel = subject.type.label,
        primaryTitle = title.primary,
        secondaryTitle = title.secondary,
        score = if (isColl) state.myRating else null,
        communityScore = subject.ratingScore,
        ratingTotal = subject.ratingTotal?.let { it.toString() + " 人" },
        metaText = metaText,
        statusLabel = if (isColl) state.currentStatus.label else null,
        progressText = progressText,
        progressRatio = progressRatio,
        tags = if (isColl) state.personalTags else emptyList(),
        impressions = if (isColl) state.personalImpression else null,
        dateText = dateText,
        summary = RichTextParser.toPlainText(subject.summary).takeIf { it.isNotBlank() },
        communityTags = subject.tags.take(10),
    )
}

/** 作品详情分享：生成卡片并调起系统分享面板。 */
private suspend fun shareSubject(
    host: ShareBitmapHost,
    context: Context,
    subject: SubjectEntity,
    state: SubjectDetailUiState,
) {
    val title = TitleResolver.resolve(subject.titleCN, subject.title)
    val cover = loadShareCover(context, subject.coverUrl)
    val metaText = buildMetaText(subject)
    // 阶段 I：分享卡配置开关
    val s = context.nirikoApp.settingsDataStore.settings.first()
    val bitmap = host.render(
        context,
        ShareCardData(
            cover = cover,
            typeLabel = subject.type.label,
            primaryTitle = title.primary,
            secondaryTitle = title.secondary,
            communityScore = if (s.shareIncludeRating) subject.ratingScore else null,
            ratingTotal = if (s.shareIncludeRating) subject.ratingTotal?.let { "$it 人" } else null,
            metaText = metaText,
            summary = RichTextParser.toPlainText(subject.summary).takeIf { it.isNotBlank() },
            communityTags = subject.tags.take(10),
        ),
    )
    val text = buildString {
        append("《${title.primary}》")
        if (!title.secondary.isNullOrBlank()) {
            append("（${title.secondary}）")
        }
        append("\n类型：${subject.type.label}")
        subject.ratingScore?.let { append(" · 评分 $it") }
        val shareSummary = RichTextParser.toPlainText(subject.summary)
        if (shareSummary.isNotBlank()) {
            append("\n\n$shareSummary")
        }
        append("\n\n—— 来自 Niriko")
    }
    launchShare(context, bitmap, "share_${subject.subjectId}_subject.png", text)
}

/** 个人收藏分享（追加收藏信息），仅已收藏时可用。 */
private suspend fun shareCollection(
    host: ShareBitmapHost,
    context: Context,
    subject: SubjectEntity,
    state: SubjectDetailUiState,
) {
    val title = TitleResolver.resolve(subject.titleCN, subject.title)
    val cover = loadShareCover(context, subject.coverUrl)
    val total = subject.totalEpisodes
    val watched = state.watchedEpisodes
    // 游戏类型：watchedEpisodes 存分钟，展示转小时
    val progressText = if (subject.type == SubjectType.GAME) {
        watched?.let { PlaytimeConverter.format(it)?.let { fmt -> "已玩 $fmt" } }
    } else {
        when {
            watched != null && total != null && total > 0 -> "$watched / $total 集"
            watched != null -> "已看 $watched 集"
            else -> null
        }
    }
    val progressRatio = if (watched != null && total != null && total > 0) watched / total.toFloat() else 0f
    val dateText = listOfNotNull(
        state.startDate?.let { "开始：${DateFormat.format(it)}" },
        state.finishDate?.let { "完成：${DateFormat.format(it)}" },
    ).joinToString(" · ").takeIf { it.isNotBlank() }
    val metaText = buildMetaText(subject)
    // 阶段 I：分享卡配置开关
    val s = context.nirikoApp.settingsDataStore.settings.first()

    val bitmap = host.render(
        context,
        ShareCardData(
            cover = cover,
            typeLabel = subject.type.label,
            primaryTitle = title.primary,
            secondaryTitle = title.secondary,
            score = if (s.shareIncludeRating) state.myRating else null,
            communityScore = if (s.shareIncludeRating) subject.ratingScore else null,
            ratingTotal = if (s.shareIncludeRating) subject.ratingTotal?.let { "$it 人" } else null,
            metaText = metaText,
            statusLabel = state.currentStatus.label,
            progressText = if (s.shareIncludeProgress) progressText else null,
            progressRatio = if (s.shareIncludeProgress) progressRatio else 0f,
            tags = if (s.shareIncludeTags) state.personalTags else emptyList(),
            impressions = state.personalImpression,
            dateText = dateText,
        ),
    )
    val text = buildString {
        append("《${title.primary}》")
        append("\n状态：${state.currentStatus.label}")
        if (progressText != null) append(" · $progressText")
        state.myRating?.let { append(" · 我的评分 $it") }
        if (state.personalTags.isNotEmpty()) append("\n标签：${state.personalTags.joinToString(" ")}")
        if (dateText != null) append("\n$dateText")
        if (!state.personalImpression.isNullOrBlank()) append("\n\n${state.personalImpression}")
        append("\n\n——来自 Niriko")
    }
    launchShare(context, bitmap, "share_${subject.subjectId}_collection.png", text)
}

/** 保存 PNG → FileProvider URI → ACTION_SEND → 分享面板。 */
private fun launchShare(context: Context, bitmap: Bitmap, fileName: String, summary: String) {
    try {
        val uri = ShareFlow.saveAndGetUri(context, bitmap, fileName)
        val intent = ShareFlow.buildChooser(ShareFlow.buildShareIntent(uri, summary))
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("Share", "分享失败", e)
    }
}
