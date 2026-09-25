@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.subject

import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
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
import com.otakup.niriko.ui.animation.AnimDurationNormal
import com.otakup.niriko.ui.animation.RevealOnScroll
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.share.ShareBitmapHost
import com.otakup.niriko.ui.share.ShareCardData
import com.otakup.niriko.ui.share.SharePreviewSheet
import com.otakup.niriko.ui.common.rememberImageLuminance
import com.otakup.niriko.ui.components.CoverImage
import com.otakup.niriko.ui.components.LocalDetailCoverFallback
import com.otakup.niriko.ui.components.GlassRatingSlider
import com.otakup.niriko.ui.components.GlassSectionCard
import com.otakup.niriko.ui.components.GlassToggle
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
import com.otakup.niriko.util.TitleResolver
import com.otakup.niriko.util.PortalLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.otakup.niriko.ui.components.ErrorContent
import com.otakup.niriko.ui.components.LoadingContent
import com.otakup.niriko.ui.components.WindowBlurBehindEffect
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
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // Snackbar 消息处理（保存反馈 + 自动返回）
    // 注意：showSnackbar 是挂起等待 Snackbar 消失的，不能阻塞 onBack()，
    // 否则保存后要等 3-4s 才返回（用户感知为“保存延迟”）。
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage ?: return@LaunchedEffect
        if (msg == "记录已保存") {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
            onBack()
        } else {
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // 外层 MainActivity Scaffold 已处理状态栏 inset，内层禁用避免双重 padding（顶栏上方出现空白）
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
                onSearchMoreTmdb = viewModel::searchMoreTmdb,
                onPasteTmdbId = viewModel::pasteTmdbIdOrUrl,
                onClearTmdbManualMessage = viewModel::clearTmdbManualMessage,
                onRequestCoverCandidates = viewModel::loadCoverCandidates,
                onOpenEpisodeDetail = onOpenEpisodeDetail,
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
    val onSearchMoreTmdb: (String) -> Unit = {},
    /** 第 4 轮 C：粘贴 TMDb ID / 链接（手动入口）。 */
    val onPasteTmdbId: (String) -> Unit = {},
    val onClearTmdbManualMessage: () -> Unit = {},
    /** 跳到「权威数据源」设置（IMDb 不可用时的一键修复入口）。 */
    val onOpenRatingSettings: () -> Unit = {},
    val onRequestCoverCandidates: () -> Unit = {},
    /** 打开单集二级页（阶段 B）。 */
    val onOpenEpisodeDetail: (Long) -> Unit = {},
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
    val context = LocalContext.current
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
    LaunchedEffect(Unit) { bgEntered = true }
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
        // 整页模糊背景墙（有封面且非加载/错误态时）：封面放大铺底（scale 1.2 超出边缘避免空隙）
        // + 降采样小图 + 强模糊 + 弱化蒙层，内容在其上滑动
        val hasBackground = subject?.coverUrl != null && !state.isLoading && state.error == null
        // 阶段 P：玻璃/特效强度（默认 FULL）。非 FULL 时跳过 layerBackdrop 捕获，玻璃卡退回 tint（减轻详情页开销）
        val glassEffect = LocalGlassEffect.current
        // 毛玻璃 backdrop：捕获背景墙图层，供各 GlassSectionCard drawBackdrop 真折射
        val backdropSurface = MaterialTheme.colorScheme.surface
        val glassBackdrop = if (hasBackground) {
            rememberLayerBackdrop {
                drawRect(backdropSurface)
                drawContent()
            }
        } else null
        if (hasBackground) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(subject.coverUrl)
                    .size(360, 480)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.2f)
                    .then(if (glassEffect == GlassEffectLevel.FULL) Modifier.layerBackdrop(glassBackdrop!!) else Modifier)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 有背景墙时透明（让封面透出）；否则不透明 surface（Loading/Error/无封面）
                .background(if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 背景墙延伸到状态栏后，顶栏自行避让状态栏高度（返回键/标题不被遮挡）
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                Text("作品详情", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                // 分享入口：作品详情分享 / 我的收藏分享（仅已收藏时可用）
                var shareMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { shareMenuExpanded = true }) {
                        Icon(Icons.Filled.Share, contentDescription = "分享")
                    }
                    DropdownMenu(
                        expanded = shareMenuExpanded,
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
                }
                // 传送门：作品跳转到其他平台 / App（P0）
                var portalMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { portalMenuExpanded = true }) {
                        Icon(Icons.AutoMirrored.Outlined.Launch, contentDescription = "传送门")
                    }
                    DropdownMenu(
                        expanded = portalMenuExpanded,
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
                state.isLoading -> LoadingContent()
                state.error != null -> ErrorContent(
                    message = state.error ?: "",
                    onRetry = onRetry,
                )
                subject != null -> Column {
                    if (state.isRefreshing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    SubjectDetailBody(
                        subject = subject, state = state,
                        glassBackdrop = glassBackdrop,
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
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SubjectDetailBody(
    subject: SubjectEntity,
    state: SubjectDetailUiState,
    glassBackdrop: Backdrop?,
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
    val blurredCoverContext = LocalContext.current
    var blurredCover by remember(subject.coverUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(subject.coverUrl) {
        blurredCover = loadBlurredCover(blurredCoverContext, subject.coverUrl)
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

    // 主体入场动画（Apple：进入 fade + scale，200-350ms）
    var bodyEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bodyEntered = true }

    // 详情页所有 GlassSectionCard 共享同一张模糊封面：backdrop 不可用时一律走 BlurredGlassSurface。
    CompositionLocalProvider(LocalDetailCoverFallback provides blurredCover) {
    AnimatedVisibility(
        visible = bodyEntered,
        enter = fadeIn(animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault)) +
            scaleIn(
                initialScale = 0.97f,
                animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
            ),
    ) {
    val detailListState = rememberLazyListState()
    val isListScrolling = detailListState.isScrollInProgress
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = detailListState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // 全宽大封面：按图片自身宽高比展示（不强制 3:4），加载完成前/异常用 3:4 兜底
        item(key = "cover") {
        // 阶段 E：用户封面覆盖优先（更换封面）
        var overrideCover by remember(subject.subjectId) { mutableStateOf<String?>(null) }
        LaunchedEffect(subject.subjectId) {
            overrideCover = blurredCoverContext.nirikoApp.coverOverrideStore.overrideFor(subject.subjectId)
        }
        val coverEffective = overrideCover ?: subject.coverUrl
        if (coverEffective != null) {
            // 显式超采样解码：hero 大封面放宽到 1080px 再裁切，避免默认按绘制层解码导致的发糊；原图不究其内存
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverEffective)
                    // 1080px 宽固定上限（宽 × 高按原比例），覆盖绝大多数屏且避免大图 OOM
                    .size(coil.size.Size(1080, (1080f * 4f / 3f).toInt()))
                    .build(),
            )
            val ratio = if (painter.state is AsyncImagePainter.State.Success) {
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
                        sharedContentState = rememberSharedContentState("cover_${subject.subjectId}"),
                        animatedVisibilityScope = avScope,
                    )
                }
            } else Modifier
            Box(
                modifier = Modifier
                    .then(sharedModifier)
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(MaterialTheme.shapes.medium)
                    .shadow(10.dp, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("暂无封面", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
        // 标题（统一 TitleResolver）— Apple 大标题体系：26sp 主标题 + 15sp 原名 + 小字元信息
        item(key = "title") {
        Spacer(Modifier.height(16.dp))
        val titleInfo = com.otakup.niriko.util.TitleResolver.resolve(subject.titleCN, subject.title)
        Text(titleInfo.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        titleInfo.secondary?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(16.dp))

        // 元信息（按类型展示不同字段）
        SubjectMetaSection(subject = subject)
        Spacer(Modifier.height(16.dp))

        // 简介
        Text("简介", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subject.summary ?: "暂无简介", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 20, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(24.dp))
        }

        // === infobox（艺术家/发行商/发售日期等，音乐类型尤其丰富） ===
        if (state.infoBox.isNotEmpty()) {
            item(key = "infobox") {
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

        // === Steam 补充信息（仅游戏类型且已绑定 Steam 时显示） ===
        if (subject.type == SubjectType.GAME && state.steam != null) {
            item(key = "steam") {
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

        // === AniList 补充信息（不限制类型：动画/漫画/游戏等 bangumi 词条均可） ===
        // 只要存在绑定/详情/候选就渲染；bangumi 词条即使候选为空也保留「搜索更多」卡片，不再整块消失。
        val anilistHasData = state.anilistBinding != null || state.anilistDetail != null ||
            state.anilistRichDetail != null || state.anilistCandidates.isNotEmpty()
        if (anilistHasData || subject.sourceKey == null) {
            when {
                state.anilistDetail != null || state.anilistRichDetail != null -> {
                    item(key = "anilist") {
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
                    item(key = "anilist_pending") {
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
                    item(key = "anilist_candidates") {
                        // 第 4 轮 D：改用统一绑定区块（原 AniListCandidateSection 的三段式 UI
                        // 与 VNDB/TMDb 各写一套，行为不一致且缺「粘贴 id」通路）。
                        // GameItem → MatchCandidate 的映射只用到展示字段，绑定仍走 bindAnilist(id)。
                        var anilistPaste by remember { mutableStateOf("") }
                        ProviderBindingSection(                            title = "AniList 条目",
                            unboundHint = "AniList 的评分**无需绑定**就会显示在评分区（自动匹配高置信度时）。" +
                                "绑定后才额外提供英文名/角色/标签等深度数据。",
                            bindingSummary = null,
                            candidates = state.anilistCandidates.map { item ->
                                com.otakup.niriko.data.match.MatchCandidate(
                                    provider = "anilist",
                                    externalId = item.sourceGameId,
                                    title = item.title,
                                    subtitle = item.aliases
                                        ?: item.ratingScore?.let { "%.1f 分".format(it) },
                                    imageUrl = item.coverUrl,
                                )
                            },
                            matchReasons = emptyMap(),
                            onBind = { candidate ->
                                candidate.externalId.toLongOrNull()?.let(onBindAnilist)
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
            item(key = "anilist_source") {
                AniListSourceInfoSection(
                    subject = subject,
                    backdrop = glassBackdrop,
                    isScrolling = isListScrolling,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // === VNDB 补充信息（仅游戏类型：已绑定显示详情，未绑定显示候选/搜索，不再整块消失） ===
        if (subject.type == SubjectType.GAME) {
            when {
                state.vndbDetail != null -> {
                    item(key = "vndb") {
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
                    item(key = "vndb_pending") {
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
                    item(key = "vndb_candidates") {
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

        // === 关联条目（前后传/版本/系列） ===
        item(key = "relations") {
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

        // === 统计卡网格（AniShelf 借鉴：评分/集数/人数）===
        item(key = "statGrid") {
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

        // === 圣地巡礼（Anitabi 取景地标，阶段 K）===
        if (state.anitabiPoints.isNotEmpty()) {
            item(key = "anitabi") {
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

        // === 猜你喜欢（阶段 F：本地 tag 共现）===
        if (state.guessYouLike.isNotEmpty()) {
            item(key = "guess") {
                GuessYouLikeSection(
                    subjects = state.guessYouLike,
                    onSubjectClick = onRelationClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // === 评分（第 3 轮回退：横滑卡太细碎，恢复「一张评分卡 + 下方纵向内容」） ===
        item(key = "rating") {
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

        // === 权威评分（多源；含 Fami通 / Billboard / Oricon 手动录入） ===
        item(key = "external_rating") {
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

        // === 扩展信息：角色 / 制作人员 / 社区标签 ===
        item(key = "extended") {
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
                    subject.tags.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        } // item "extended"


        // === 剧照 / 截图（TMDb 背景图 + 每集剧照；Steam 截图仍在 Steam 区块内） ===
        if (thumbItems.isNotEmpty() || state.doubanEnabled) {
            item(key = "thumbs") {
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

        // === 剧集 / 章节 + 每集评分走势（对齐 Bangumi-master 的 Ep 区块） ===
        if (subject.type == SubjectType.ANIME || subject.type == SubjectType.REAL || subject.type == SubjectType.BOOK) {
            item(key = "episodes_rating") {
                EpisodeRatingSection(
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


        // === TMDb 绑定（保守匹配：只产候选，用户确认才写库） ===
        if (state.tmdbSupported) {
            item(key = "tmdb_binding") {
                TmdbBindingSection(
                    binding = state.tmdbBinding,
                    movieBinding = state.tmdbMovieBinding,
                    candidates = state.tmdbCandidates,
                    pastedCandidate = state.tmdbPastedCandidate,
                    detail = state.tmdbDetail,
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

        // === 音乐类型：曲目列表（按碟片分组，可勾选标记已听） ===
        if (subject.type == SubjectType.MUSIC && state.episodes.isNotEmpty()) {
            item(key = "tracks") {
                Spacer(Modifier.height(8.dp))
                TrackListSection(
                    episodes = state.episodes,
                    watchedTrackIds = watchedTrackIdsLocal,
                    onToggleTrack = { id ->
                        watchedTrackIdsLocal = if (id in watchedTrackIdsLocal) watchedTrackIdsLocal.minus(id) else watchedTrackIdsLocal.plus(id)
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // === 收藏管理（Apple 风格：单主按钮，编辑控件收进 BottomSheet） ===
        item(key = "collectionBtn") {
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
    } // AnimatedVisibility 主体入场
    } // CompositionLocalProvider(LocalDetailCoverFallback)

    // ========== 收藏管理 BottomSheet（编辑控件全部收纳于此） ==========
    if (showCollectionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showCollectionSheet = false },
            sheetState = sheetState,
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
                isSaveEnabled = isSaveEnabled,
                isUpdating = state.isUpdating,
                onUpdateStatus = onUpdateStatus,
                onSaveRecord = onSaveRecord,
                onRemoveFromCollection = onRemoveFromCollection,
                onShowStartDatePicker = { showStartDatePicker = true },
                onShowFinishDatePicker = { showFinishDatePicker = true },
                onShowRemoveConfirm = { showRemoveConfirm = true },
                onOpenEpisodeDetail = ratingCallbacks.onOpenEpisodeDetail,
            )
        }
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
    isSaveEnabled: Boolean,
    isUpdating: Boolean,
    onUpdateStatus: (WatchStatus) -> Unit,
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>, Int?, Boolean) -> Unit,
    onRemoveFromCollection: () -> Unit,
    onShowStartDatePicker: () -> Unit,
    onShowFinishDatePicker: () -> Unit,
    onShowRemoveConfirm: () -> Unit,
    onOpenEpisodeDetail: (Long) -> Unit = {},
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
            onOpenEpisodeDetail = onOpenEpisodeDetail,
            onProgressChange = onWatchedEpisodesChange,
        )
        Spacer(Modifier.height(12.dp))

        // 卷进度（书籍/漫画，阶段 B）
        if (subject.type == SubjectType.MANGA || subject.type == SubjectType.BOOK) {
            OutlinedTextField(value = watchedVolumesText, onValueChange = onWatchedVolumesChange,
                modifier = Modifier.fillMaxWidth(), label = { Text("卷进度") }, singleLine = true,
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
        Spacer(Modifier.height(8.dp))

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
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("猜你喜欢", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            subjects.forEach { s ->
                Column(modifier = Modifier.width(120.dp).clickable { onSubjectClick(s.subjectId) }) {
                    CoverImage(
                        coverUrl = s.coverUrl,
                        contentDescription = s.displayTitle,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        aspectRatio = 3f / 4f,
                        sharedElementKey = "cover_${s.subjectId}",
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        subjectId = s.subjectId,
                    )
                    Text(s.displayTitle, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                val total = subject.volumes ?: subject.totalEpisodes
                // 修复 BUG-5：原来上限 52，53 集以上的长篇（海贼/柯南/长篇国产）直接退化成
            // 纯数字输入，没有选集入口。提高到 300 并保留原有降级分支。
            if (total != null && total in 1..300) {
                    Text("卷数", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    val currentProgress = progressText.toIntOrNull() ?: 0
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
                                onClick = { onProgressChange(v.toString()) },
                            )
                        }
                    }
                } else {
                    OutlinedTextField(value = progressText, onValueChange = { onProgressChange(it.filter { c -> c.isDigit() }) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("阅读进度（话/卷）") }, singleLine = true,
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
                    subject.tags.take(8).forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        )
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
                        FilterChip(
                            selected = strength >= 2,
                            onClick = {},
                            label = {
                                Text(
                                    tag.name + if (strength > 0) " " + "★".repeat(strength) else "",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
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
                Text(
                    desc.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()
                        .let { if (expanded) it.take(1200) else it.take(160) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) 20 else 3,
                    overflow = TextOverflow.Ellipsis,
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

/** 音乐类型曲目列表：按碟片（disc）分组，显示曲序/曲名/时长/OP-ED 标记，可逐首点击标记已听。 */
@Composable
private fun TrackListSection(
    episodes: List<EpisodeInfo>,
    watchedTrackIds: Set<Long> = emptySet(),
    onToggleTrack: (Long) -> Unit = {},
) {
    // 按 disc 分组（disc=0 视为单碟），保持 sort 顺序
    val grouped = episodes
        .filter { it.type == 0 || it.type == 2 || it.type == 3 } // 本篇/OP/ED 曲目
        .groupBy { it.disc.takeIf { d -> d > 0 } ?: 1 }
        .toSortedMap(compareBy { it })
    val totalCount = grouped.values.sumOf { it.size }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "曲目（已听 ${watchedTrackIds.size.coerceAtMost(totalCount)} / $totalCount 首）",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("点击曲目标记/取消已听", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        grouped.forEach { (disc, tracks) ->
            val sorted = tracks.sortedBy { it.sort }
            if (grouped.size > 1) {
                Text("Disc $disc", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }
            sorted.forEach { track ->
                TrackRow(
                    track = track,
                    isWatched = track.id in watchedTrackIds,
                    onClick = { onToggleTrack(track.id) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 单曲行：曲序 + 曲名 + 时长 + 类型标记 + 已听勾选。 */
@Composable
private fun TrackRow(
    track: EpisodeInfo,
    isWatched: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            track.sort.toInt().toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            // name_cn 常返回空字符串（非 null），需 isNotBlank 回退 name
            val title = track.nameCn?.takeIf { it.isNotBlank() } ?: track.name
            Text(
                if (title.isBlank()) "曲目 ${track.sort.toInt()}" else title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isWatched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                track.typeLabel().takeIf { it != null },
                track.durationSeconds.takeIf { it > 0 }?.let { formatDuration(it) }
                    ?: track.duration?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 已听勾选标记
        Icon(
            imageVector = if (isWatched) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (isWatched) "已听" else "未听",
            tint = if (isWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 曲目类型标记（0=本篇 无标记，2=OP，3=ED，1=SP）。 */
private fun EpisodeInfo.typeLabel(): String? = when (type) {
    2 -> "OP"
    3 -> "ED"
    1 -> "SP"
    else -> null
}

/** 秒数 → "mm:ss"。 */
private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

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
        summary = subject.summary,
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
            summary = subject.summary,
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
        if (!subject.summary.isNullOrBlank()) {
            append("\n\n${subject.summary}")
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
