package com.otakup.niriko.ui.steam

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.NirikoDatabase
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.SteamLibraryItemDao
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.model.SubjectType
import com.otakup.niriko.data.remote.steam.SteamApiClient
import com.otakup.niriko.data.remote.steam.SteamLibraryImporter
import com.otakup.niriko.data.remote.steam.SteamLibraryMatcher
import com.otakup.niriko.data.remote.steam.SteamLibraryPreview
import com.otakup.niriko.data.remote.steam.SteamImportResult
import com.otakup.niriko.data.repository.SteamRepository
import com.otakup.niriko.data.repository.SubjectRepository
import com.otakup.niriko.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 导入工作流阶段。 */
enum class SteamSyncStatus {
    /** 待登录（未登录/未配置 key）。 */
    NEED_LOGIN,
    /** 已登录，等待操作。 */
    IDLE,
    /** 正在拉取游戏库。 */
    PULLING,
    /** 正在匹配 Bangumi 词条。 */
    MATCHING,
    /** 匹配完成，预览勾选。 */
    READY,
    /** 正在导入。 */
    IMPORTING,
    /** 拉取失败（可重试）。 */
    ERROR,
}

/** 页面临时 UI 状态。 */
data class SteamSyncUiState(
    val stage: SteamSyncStatus = SteamSyncStatus.NEED_LOGIN,
    /** 顶部状态条文案。 */
    val message: String = "请先登录 Steam 账号",
    val steamId64: String = "",
    val userName: String? = null,
    val previews: List<SteamLibraryPreview> = emptyList(),
    val isImporting: Boolean = false,
    val importResult: SteamImportResult? = null,
    /** 错误信息（stage=ERROR 时非空）。 */
    val error: String? = null,
) {
    val selectedCount: Int get() = previews.count { it.selected }
    val isReady: Boolean get() = stage == SteamSyncStatus.READY
}

/**
 * Steam 游戏库导入工作流 ViewModel。
 *
 * 流程：校验登录（steamId64 + steamApiKey）→ GetOwnedGames 拉库 → 匹配 Bangumi →
 * 预览勾选（已匹配默认勾选，占位候选单独分组标记）→ SteamLibraryImporter 导入收藏。
 */
class SteamSyncViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val steamRepository: SteamRepository,
    private val subjectRepository: SubjectRepository,
    private val database: NirikoDatabase,
    private val steamDao: SteamDao,
    private val steamLibraryItemDao: SteamLibraryItemDao,
    private val apiService: com.otakup.niriko.data.remote.steam.SteamApiService =
        SteamApiClient.apiService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SteamSyncUiState())
    val uiState: StateFlow<SteamSyncUiState> = _uiState.asStateFlow()

    /** 匹配器（注入依赖，便于替换/测试）。 */
    private val matcher: SteamLibraryMatcher by lazy {
        SteamLibraryMatcher(
            steamDao = steamDao,
            searchBangumiGame = { title ->
                try {
                    subjectRepository.search(keyword = title, type = 4, limit = 5)
                } catch (_: Exception) {
                    emptyList()
                }
            },
            inCollection = { id ->
                try {
                    database.collectionDao().getBySubjectId(id) != null
                } catch (_: Exception) {
                    false
                }
            },
            subjectTitle = { id ->
                try {
                    database.subjectDao().getById(id)?.let { it.titleCN ?: it.title }
                } catch (_: Exception) {
                    null
                }
            },
        )
    }

    private val importer: SteamLibraryImporter by lazy {
        SteamLibraryImporter(
            steamLibraryItemDao = steamLibraryItemDao,
            subjectDao = database.subjectDao(),
            collectionDao = database.collectionDao(),
            steamDao = steamDao,
        )
    }

    init {
        viewModelScope.launch {
            val settings = settingsDataStore.settings.first()
            val steamId64 = settings.steamId64
            val apiKey = settings.steamApiKey
            _uiState.update {
                it.copy(
                    steamId64 = steamId64,
                    stage = if (steamId64.isBlank() || apiKey.isBlank()) {
                        SteamSyncStatus.NEED_LOGIN
                    } else {
                        SteamSyncStatus.IDLE
                    },
                    message = if (steamId64.isBlank() || apiKey.isBlank()) {
                        "请先登录 Steam 并配置 API Key"
                    } else {
                        "已登录 $steamId64，点击拉取游戏库"
                    },
                )
            }
        }
    }

    /** 登录成功后由登录页回调（steamId64 已持久化）。 */
    fun onLoginSuccess(steamId64: String) {
        _uiState.update {
            it.copy(
                steamId64 = steamId64,
                stage = SteamSyncStatus.IDLE,
                message = "已登录 $steamId64，点击拉取游戏库",
            )
        }
    }

    /** 拉取游戏库（GetOwnedGames）→ 匹配 → 预览。 */
    fun pullLibrary() {
        val s = _uiState.value
        if (s.steamId64.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(stage = SteamSyncStatus.PULLING, message = "正在拉取 Steam 游戏库…", error = null)
            }
            try {
                val key = SteamApiClient.currentApiKey()
                if (key.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(stage = SteamSyncStatus.ERROR, error = "未配置 Steam Web API Key，请到设置页填写")
                    }
                    return@launch
                }
                val response = withContext(Dispatchers.IO) {
                    apiService.ownedGames(
                        key = key,
                        steamId = s.steamId64,
                    )
                }
                val games = response.response?.games ?: emptyList()
                if (games.isEmpty()) {
                    _uiState.update {
                        it.copy(stage = SteamSyncStatus.READY, previews = emptyList(), message = "游戏库为空或接口未返回数据")
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(stage = SteamSyncStatus.MATCHING, message = "已拉取 ${games.size} 款游戏，正在匹配 Bangumi 词条…")
                }
                val previews = matcher.toPreviews(games)
                _uiState.update {
                    it.copy(
                        stage = SteamSyncStatus.READY,
                        previews = previews,
                        message = "共 ${previews.size} 款游戏（已匹配 ${previews.count { it.isMatched }}，占位 ${previews.count { it.isPlaceholder }}）",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(stage = SteamSyncStatus.ERROR, error = "拉取失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    /** 切换勾选态。 */
    fun toggleSelection(appId: Int, selected: Boolean) {
        _uiState.update { state ->
            state.copy(
                previews = state.previews.map {
                    if (it.appId == appId) it.copy(selected = selected) else it
                },
            )
        }
    }

    /** 全选/全不选（默认只全选已匹配项）。 */
    fun selectAll() {
        _uiState.update { state ->
            state.copy(previews = state.previews.map { it.copy(selected = it.isMatched) })
        }
    }

    /** 导入勾选项。 */
    fun importSelected() {
        val s = _uiState.value
        if (s.selectedCount == 0 || s.isImporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, message = "正在导入收藏…") }
            val result = withContext(Dispatchers.IO) {
                importer.import(
                    previews = s.previews.filter { it.selected },
                    steamId64 = s.steamId64,
                )
            }
            _uiState.update {
                it.copy(
                    isImporting = false,
                    importResult = result,
                    message = "导入完成：新增 ${result.imported}，占位 ${result.placeholderCreated}，跳过已存在 ${result.skippedExisting}",
                )
            }
        }
    }

    /** 占位条目重新匹配升级（预览页操作）。 */
    fun rematchPlaceholder(appId: Int) {
        val preview = _uiState.value.previews.find { it.appId == appId } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(message = "正在重新匹配 ${preview.name}…", error = null) }
            val newSubjectId = withContext(Dispatchers.IO) {
                try {
                    val candidates = subjectRepository.search(keyword = preview.name, type = 4, limit = 5)
                    val best = candidates
                        .filter { it.subjectId > 0 }
                        .mapNotNull { candidate ->
                            val score = com.otakup.niriko.data.remote.steam.SteamTitleMatcher
                                .confidence(preview.name, candidate.titleCN ?: candidate.title)
                            if (score >= com.otakup.niriko.data.remote.steam.SteamTitleMatcher.MIN_CONFIDENCE) {
                                candidate to score
                            } else null
                        }
                        .maxByOrNull { it.second }
                        ?.first ?: return@withContext null
                    val ok = steamRepository.upgradePlaceholder(
                        appId = appId,
                        newSubjectId = best.subjectId,
                        database = database,
                        subjectDao = database.subjectDao(),
                        collectionDao = database.collectionDao(),
                        steamLibraryItemDao = steamLibraryItemDao,
                    )
                    if (ok) best.subjectId else null
                } catch (_: Exception) {
                    null
                }
            }
            if (newSubjectId != null) {
                // 刷新预览（该条目不再占位）
                _uiState.update { state ->
                    state.copy(
                        previews = state.previews.map {
                            if (it.appId == appId) {
                                it.copy(
                                    bgmSubjectId = newSubjectId,
                                    isPlaceholder = false,
                                    localSubjectTitle = it.name,
                                    selected = true,
                                )
                            } else it
                        },
                        message = "已升级为正式词条（subjectId=$newSubjectId）",
                    )
                }
            } else {
                _uiState.update { it.copy(message = "重新匹配未命中 Bangumi 词条", error = "未找到可匹配的 Bangumi 词条") }
            }
        }
    }
}

class SteamSyncViewModelFactory(
    private val settingsDataStore: SettingsDataStore,
    private val steamRepository: SteamRepository,
    private val subjectRepository: SubjectRepository,
    private val database: NirikoDatabase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SteamSyncViewModel::class.java)) {
            return SteamSyncViewModel(
                settingsDataStore = settingsDataStore,
                steamRepository = steamRepository,
                subjectRepository = subjectRepository,
                database = database,
                steamDao = database.steamDao(),
                steamLibraryItemDao = database.steamLibraryItemDao(),
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
