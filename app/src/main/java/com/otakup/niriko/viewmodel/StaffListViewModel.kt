package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.model.StaffInfo
import com.otakup.niriko.data.remote.SubjectRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 制作人员完整列表 UI 快照。 */
data class StaffListUiState(
    val staff: List<StaffInfo> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

/**
 * 制作人员完整列表 ViewModel（"查看全部 N 人"入口）。
 */
class StaffListViewModel(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StaffListUiState())
    val uiState: StateFlow<StaffListUiState> = _uiState.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val staff = withContext(Dispatchers.IO) {
                    try { remoteDataSource.getStaff(subjectId) } catch (_: Exception) { emptyList() }
                }
                _uiState.update { it.copy(staff = staff, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "加载失败，请重试") }
            }
        }
    }
}

class StaffListViewModelFactory(
    private val remoteDataSource: SubjectRemoteDataSource,
    private val subjectId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StaffListViewModel::class.java)) {
            return StaffListViewModel(remoteDataSource, subjectId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
