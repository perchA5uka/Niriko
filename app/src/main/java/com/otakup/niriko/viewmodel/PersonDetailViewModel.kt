package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.remote.PersonDetailInfo
import com.otakup.niriko.data.remote.PersonSubjectInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 人物详情 UI 快照。 */
data class PersonDetailUiState(
    val detail: PersonDetailInfo? = null,
    /** 参与作品（按 staff 参与身份分组）。 */
    val subjects: List<PersonSubjectInfo> = emptyList(),
    /** 声优时：演绎的角色列表。 */
    val characters: List<CharacterInfo> = emptyList(),
    /** 职位统计（由参与作品的 staff 身份本地聚合，零额外请求）。 */
    val jobStats: List<com.otakup.niriko.data.remote.PersonJobStat> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    /** 是否已收藏（本地标记）。 */
    val isCollected: Boolean = false,
    val isCollecting: Boolean = false,
    /** Snackbar 提示消息。 */
    val snackbarMessage: String? = null,
)

/**
 * 人物（声优/导演等）详情 ViewModel。
 * 加载人物信息 + 参与作品 + 演绎角色（并行），支持收藏。
 */
class PersonDetailViewModel(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val personCollectionDao: com.otakup.niriko.data.local.dao.PersonCollectionDao,
    private val personId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    init {
        // 读本地收藏状态
        viewModelScope.launch {
            val exists = withContext(Dispatchers.IO) { personCollectionDao.exists(personId) }
            _uiState.update { it.copy(isCollected = exists) }
        }
        load()
    }

    fun retry() = load()

    /** 收藏/取消收藏人物（纯本地，不依赖 token/服务端）。 */
    fun collect() {
        if (_uiState.value.isCollecting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCollecting = true, snackbarMessage = null) }
            val currentlyCollected = _uiState.value.isCollected
            withContext(Dispatchers.IO) {
                if (currentlyCollected) {
                    personCollectionDao.deleteByPersonId(personId)
                } else {
                    val detail = _uiState.value.detail
                    personCollectionDao.insert(
                        com.otakup.niriko.data.local.entity.PersonCollectionEntity(
                            personId = personId,
                            name = detail?.name ?: "",
                            nameCn = detail?.nameCn,
                            imageUrl = detail?.imageUrl,
                            career = detail?.career ?: emptyList(),
                        )
                    )
                }
            }
            _uiState.update {
                it.copy(
                    isCollecting = false,
                    isCollected = !currentlyCollected,
                    snackbarMessage = if (currentlyCollected) "已取消收藏" else "已收藏",
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (detail, subjects, characters) = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val detailDeferred = async {
                            try { remoteDataSource.getPersonDetail(personId) } catch (_: Exception) { null }
                        }
                        val subjectsDeferred = async {
                            try { remoteDataSource.getPersonSubjects(personId) } catch (_: Exception) { emptyList() }
                        }
                        val charactersDeferred = async {
                            try { remoteDataSource.getPersonCharacters(personId) } catch (_: Exception) { emptyList() }
                        }
                        Triple(detailDeferred.await(), subjectsDeferred.await(), charactersDeferred.await())
                    }
                }
                val jobs = com.otakup.niriko.data.calculator.PersonJobAnalyzer
                    .analyze(subjects.map { it.staff })
                _uiState.update {
                    it.copy(
                        detail = detail,
                        subjects = subjects,
                        characters = characters,
                        jobStats = jobs,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "加载失败，请重试") }
            }
        }
    }
}

class PersonDetailViewModelFactory(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val personCollectionDao: com.otakup.niriko.data.local.dao.PersonCollectionDao,
    private val personId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersonDetailViewModel::class.java)) {
            return PersonDetailViewModel(remoteDataSource, personCollectionDao, personId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
