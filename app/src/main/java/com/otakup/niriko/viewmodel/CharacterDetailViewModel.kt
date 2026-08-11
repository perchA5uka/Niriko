package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.model.CharacterInfo
import com.otakup.niriko.data.remote.CharacterDetailInfo
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

/** 角色详情 UI 快照。 */
data class CharacterDetailUiState(
    val detail: CharacterDetailInfo? = null,
    val subjects: List<PersonSubjectInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 角色详情 ViewModel。
 * 加载角色信息 + 出演作品（并行）。
 */
class CharacterDetailViewModel(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val characterId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterDetailUiState())
    val uiState: StateFlow<CharacterDetailUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val (detail, subjects) = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val detailDeferred = async {
                            // getCharacterDetail 失败自动重试 1 次（共 2 次），提高稳定性
                            var result: com.otakup.niriko.data.remote.CharacterDetailInfo? = null
                            repeat(2) {
                                result = try { remoteDataSource.getCharacterDetail(characterId) } catch (_: Exception) { null }
                                if (result != null) return@repeat
                                kotlinx.coroutines.delay(300)
                            }
                            result
                        }
                        val subjectsDeferred = async {
                            try { remoteDataSource.getCharacterSubjects(characterId) } catch (_: Exception) { emptyList() }
                        }
                        detailDeferred.await() to subjectsDeferred.await()
                    }
                }
                _uiState.update {
                    it.copy(detail = detail, subjects = subjects, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "加载失败，请重试") }
            }
        }
    }
}

class CharacterDetailViewModelFactory(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val characterId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CharacterDetailViewModel::class.java)) {
            return CharacterDetailViewModel(remoteDataSource, characterId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
