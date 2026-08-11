@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.otakup.niriko.ui.subject

import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.EpisodeInfo
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.remote.steam.SteamAchievements
import com.otakup.niriko.data.remote.steam.SteamChartEntry
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.remote.InfoBoxEntry
import com.otakup.niriko.data.remote.game.PlaytimeConverter
import com.otakup.niriko.ui.animation.AnimDurationNormal
import com.otakup.niriko.ui.animation.AnimEasingDefault
import com.otakup.niriko.ui.share.ShareBitmapHost
import com.otakup.niriko.ui.share.ShareCardData
import com.otakup.niriko.ui.components.GlassSectionCard
import com.otakup.niriko.ui.components.liquidglass.loadBlurredCover
import com.otakup.niriko.ui.theme.LocalDarkTheme
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import com.otakup.niriko.ui.share.ShareFlow
import com.otakup.niriko.util.TitleResolver
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
    WatchStatus.PLAN_TO_WATCH, WatchStatus.WATCHING, WatchStatus.COMPLETED, WatchStatus.ON_HOLD,
)
private val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

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
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // Snackbar 消息处理（保存反馈 + 自动返回）
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            if (it == "记录已保存") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            }
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
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun SubjectDetailContent(
    state: SubjectDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onAddToCollection: () -> Unit,
    onUpdateStatus: (WatchStatus) -> Unit,
    onRemoveFromCollection: () -> Unit,
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>) -> Unit,
    onTagDeleted: (String) -> Unit = {},
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    onRelationClick: (Long) -> Unit = {},
    onViewAllStaffClick: () -> Unit = {},
    onRematchPlaceholder: () -> Unit = {},
    onUnbindSteam: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
) {
    val subject = state.subject
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    // 分享卡离屏渲染宿主：分享点击时把数据写入，由屏幕外节点录制为 Bitmap
    val shareBitmapHost = remember { ShareBitmapHost() }
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
                    .layerBackdrop(glassBackdrop!!)
                    .then(if (blurEffect != null) Modifier else Modifier.blur(30.dp))
                    .graphicsLayer {
                        if (blurEffect != null) renderEffect = blurEffect
                        alpha = bgAlpha
                    },
                contentScale = ContentScale.Crop,
            )
            // 弱化蒙层：浅色白/深色黑，保证前景文字可读性
            // （浅色 0.16 白让背景变亮、深色文字对比充足；深色保持黑 0.25 压暗）
            val overlayColor = if (LocalDarkTheme.current) {
                Color.Black.copy(alpha = 0.25f)
            } else {
                Color.White.copy(alpha = 0.16f)
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
                                subject?.let { shareScope.launch { shareSubject(shareBitmapHost, context, it, state) } }
                            },
                        )
                        if (state.isInCollection) {
                            DropdownMenuItem(
                                text = { Text("分享我的收藏") },
                                onClick = {
                                    shareMenuExpanded = false
                                    subject?.let { shareScope.launch { shareCollection(shareBitmapHost, context, it, state) } }
                                },
                            )
                        }
                    }
                }
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
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>) -> Unit,
    onTagDeleted: (String) -> Unit = {},
    onCharacterClick: (Long) -> Unit = {},
    onPersonClick: (Long) -> Unit = {},
    onRelationClick: (Long) -> Unit = {},
    onViewAllStaffClick: () -> Unit = {},
    onRematchPlaceholder: () -> Unit = {},
    onUnbindSteam: () -> Unit = {},
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

    // 主体入场动画（Apple：进入 fade + scale，200-350ms）
    var bodyEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { bodyEntered = true }

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
        if (subject.coverUrl != null) {
            // 显式超采样解码：hero 大封面放宽到 1080px 再裁切，避免默认按绘制层解码导致的发糊；原图不究其内存
            val painter = rememberAsyncImagePainter(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(subject.coverUrl)
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

        // === VNDB 补充信息（仅游戏类型且已绑定 VNDB 时显示） ===
        if (subject.type == SubjectType.GAME && state.vndbDetail != null) {
            item(key = "vndb") {
                VndbInfoSection(
                    detail = state.vndbDetail,
                    backdrop = glassBackdrop,
                    isScrolling = isListScrolling,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        // === 关联条目（前后传/版本/系列） ===
        item(key = "relations") {
            RevealOnScroll {
                RelationsSection(
                    relations = state.relations,
                    onRelationClick = onRelationClick,
                    blurredCover = blurredCover,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        // === 评分对比 ===
        item(key = "rating") {
            RatingComparisonSection(
                subject = subject,
                myRating = state.myRating,
                glassBackdrop = glassBackdrop,
                isScrolling = isListScrolling,
            )
            Spacer(Modifier.height(8.dp))
            // 评分分布柱状图
            RatingDistributionChart(distribution = state.ratingDistribution)
            Spacer(Modifier.height(24.dp))
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
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
            Spacer(Modifier.height(20.dp))
            // 社区标签（Phase 3 替换为正式组件）
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
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
        } // item "extended"

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
                Button(
                    onClick = { showCollectionSheet = true },
                    enabled = !state.isUpdating,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${state.currentStatus.label} · ${if (myRatingValue > 0) "%.1f 分".format(myRatingValue) else "未评分"}")
                }
            } else {
                Button(onClick = onAddToCollection, enabled = !state.isUpdating, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.isUpdating) "添加中…" else "加入收藏")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    } // AnimatedVisibility 主体入场

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

    // ========== 取消收藏确认 ==========
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("取消收藏") },
            text = {
                Text("确定要取消收藏「${subject.titleCN ?: subject.title}」吗？")
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

/** 滚动到视口才播放入场（Apple：空间感知，浏览到才动画）。
 * 关键：内容始终在组合树（graphicsLayer 控制 alpha/scale），检测节点 onGloballyPositioned 才会回调。
 * 不能用 AnimatedVisibility 包裹检测节点 —— visible=false 时不组合子内容，检测永不触发（死锁）。
 * LazyColumn 下离屏 item 销毁、滚回重组 → 每次进入视口都重新播放入场。
 */
@Composable
private fun RevealOnScroll(
    content: @Composable () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    val viewportHeightPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx() * 0.9f
    }
    val revealAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        label = "revealAlpha",
    )
    val revealScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.97f,
        animationSpec = tween(AnimDurationNormal, easing = AnimEasingDefault),
        label = "revealScale",
    )
    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                // 窗口坐标 y < 视口 90% 即认为已进入可视浏览区（滚动必然触发位置回调）
                if (!entered && coords.positionInWindow().y < viewportHeightPx) {
                    entered = true
                }
            }
            .graphicsLayer {
                alpha = revealAlpha
                scaleX = revealScale
                scaleY = revealScale
            },
    ) { content() }
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
    onSaveRecord: (Int?, Float?, List<String>, String?, LocalDate?, LocalDate?, Set<Long>) -> Unit,
    onRemoveFromCollection: () -> Unit,
    onShowStartDatePicker: () -> Unit,
    onShowFinishDatePicker: () -> Unit,
    onShowRemoveConfirm: () -> Unit,
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
        // 状态
        Text("当前状态：${state.currentStatus.label}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ManageableStatuses.forEach { status ->
                FilterChip(selected = state.currentStatus == status, onClick = { onUpdateStatus(status) }, label = { Text(status.label) }, enabled = !isUpdating, colors = FilterChipDefaults.filterChipColors())
            }
        }
        Spacer(Modifier.height(20.dp))

        // ========== 我的记录 ==========
        Text("我的记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        // 观看进度（按类型区分 UI）
        ProgressInputSection(
            subject = subject,
            episodes = state.episodes,
            progressText = watchedEpisodesText,
            onProgressChange = onWatchedEpisodesChange,
        )
        Spacer(Modifier.height(12.dp))

        // 评分 Slider
        Text("我的评分：%.1f / 10".format(myRatingValue), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = myRatingValue,
            onValueChange = onMyRatingChange,
            valueRange = 0f..10f,
            steps = 19, // 0.5 步进 → 0, 0.5, 1.0 ... 9.5, 10.0 = 20 个点, steps=19
            modifier = Modifier.fillMaxWidth(),
        )
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

        // 开始日期 (DatePicker)
        Text(
            text = "开始日期：${startDateValue?.format(DateFormat) ?: "未设置"}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowStartDatePicker() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))

        // 完成日期 (DatePicker)
        Text(
            text = "完成日期：${finishDateValue?.format(DateFormat) ?: "未设置"}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onShowFinishDatePicker() }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            color = MaterialTheme.colorScheme.primary,
        )
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
            )
        }, enabled = isSaveEnabled, modifier = Modifier.fillMaxWidth()) {
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
    subject.airDate?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/**
 * 观看进度输入组件，按 SubjectType 展示不同 UI。
 * ANIME / BOOK / REAL：totalEpisodes ≤ 52 时用选集网格，否则用数字输入。
 * 当有剧集标题数据时，显示增强列表（剧集标题 + 勾选标记）。
 * GAME：游戏时长（小时）。
 * MUSIC：不渲染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressInputSection(
    subject: SubjectEntity,
    episodes: List<EpisodeInfo>,
    progressText: String,
    onProgressChange: (String) -> Unit,
) {
    when (subject.type) {
        SubjectType.ANIME, SubjectType.REAL -> {
            val total = subject.totalEpisodes
            if (total != null && total in 1..52) {
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
                            FilterChip(
                                selected = isWatched,
                                onClick = { onProgressChange(ep.toString()) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
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
                            FilterChip(
                                selected = isWatched,
                                onClick = { onProgressChange(ep.toString()) },
                                label = { Text("$ep", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
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
                if (total != null && total in 1..52) {
                    Text("卷数", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 4.dp))
                    val currentProgress = progressText.toIntOrNull() ?: 0
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (v in 1..total) {
                            val isRead = v <= currentProgress
                            FilterChip(
                                selected = isRead,
                                onClick = { onProgressChange(v.toString()) },
                                label = { Text("$v", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
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
            onAddToCollection = {}, onUpdateStatus = {}, onRemoveFromCollection = {}, onSaveRecord = { _, _, _, _, _, _, _ -> },
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

/** VNDB 信息区块（视觉小说补充：评分/开发商/时长/平台/语言/标签/封面）。 */
@Composable
private fun VndbInfoSection(
    detail: com.otakup.niriko.data.remote.vndb.dto.VndbVisualNovelDto,
    backdrop: Backdrop?,
    isScrolling: Boolean = false,
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
            }
            Spacer(Modifier.height(10.dp))

            // 评分 + 时长
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                detail.rating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "%.1f".format(rating / 10f),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                detail.votecount?.let { "$it 票" } ?: "评分",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                detail.lengthMinutes?.let { minutes ->
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "时长 ${formatVndbLength(minutes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 开发商 / 平台 / 语言
            if (detail.developers.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SteamMetaRow("开发商", detail.developers.joinToString(" / ") { it.name })
            }
            if (detail.platforms.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow("平台", detail.platforms.joinToString(" / "))
            }
            if (detail.olang != null) {
                Spacer(Modifier.height(4.dp))
                SteamMetaRow("原语", detail.olang)
            }

            // 发售日
            detail.released?.takeIf { it != "TBA" && it != "unknown" }?.let { released ->
                Spacer(Modifier.height(4.dp))
                SteamMetaRow("发售日", released)
            }

            // 标签
            if (detail.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    detail.tags.take(8).forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }

            // 简介（截断）
            detail.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(10.dp))
                Text(
                    desc.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(300),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** VNDB 时长（分钟）→ 可读文本（≥60 分钟显示 xh ym）。 */
private fun formatVndbLength(minutes: Int): String = when {
    minutes >= 60 -> "${minutes / 60}h ${minutes % 60}m"
    minutes > 0 -> "${minutes}m"
    else -> "-"
}

@Composable
private fun InfoBoxSection(entries: List<InfoBoxEntry>) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                Text(
                    entry.value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

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
    val bitmap = host.render(
        context,
        ShareCardData(
            cover = cover,
            typeLabel = subject.type.label,
            primaryTitle = title.primary,
            secondaryTitle = title.secondary,
            communityScore = subject.ratingScore,
            ratingTotal = subject.ratingTotal?.let { "$it 人" },
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

    val bitmap = host.render(
        context,
        ShareCardData(
            cover = cover,
            typeLabel = subject.type.label,
            primaryTitle = title.primary,
            secondaryTitle = title.secondary,
            score = state.myRating,
            communityScore = subject.ratingScore,
            ratingTotal = subject.ratingTotal?.let { "$it 人" },
            metaText = metaText,
            statusLabel = state.currentStatus.label,
            progressText = progressText,
            progressRatio = progressRatio,
            tags = state.personalTags,
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
