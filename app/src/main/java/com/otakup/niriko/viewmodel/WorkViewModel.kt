package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.WorkItem
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.WorkFilter
import com.otakup.niriko.data.model.WorkType
import com.otakup.niriko.data.repository.WorkRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 作品业务 ViewModel：封装增删改查，向 UI 暴露 Flow / StateFlow。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkViewModel(
    private val repository: WorkRepository,
) : ViewModel() {

    /** 当前列表筛选条件。 */
    private val filterState = MutableStateFlow(WorkFilter())

    /** 根据 [filterState] 实时观察作品列表。 */
    val works: StateFlow<List<WorkItem>> = filterState
        .flatMapLatest { filter -> repository.observeByFilter(filter) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val workCount: StateFlow<Int> = repository.observeCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    /** 更新列表筛选条件。 */
    fun setFilter(filter: WorkFilter) {
        filterState.value = filter
    }

    /** 观察单条作品。 */
    fun observeWork(id: Long): Flow<WorkItem?> = repository.observeById(id)

    /**
     * 添加作品。
     * @return 新记录 id；校验失败返回 null。
     */
    /** 防重复提交：添加进行中再次调用直接返回。 */
    private var isAdding = false

    fun addWork(
        title: String,
        type: WorkType,
        status: WatchStatus = WatchStatus.PLAN_TO_WATCH,
        totalEpisodes: Int? = null,
        watchedEpisodes: Int? = null,
        rating: Float? = null,
        startDate: LocalDate? = null,
        finishDate: LocalDate? = null,
        tags: List<String> = emptyList(),
        coverPath: String? = null,
        remark: String? = null,
        onResult: ((Long?) -> Unit)? = null,
    ) {
        if (isAdding) {
            onResult?.invoke(null)
            return
        }
        isAdding = true
        viewModelScope.launch {
            try {
                val normalized = normalizeTitle(title)
                if (normalized == null) {
                    onResult?.invoke(null)
                    return@launch
                }
                if (!isValidRating(rating)) {
                    onResult?.invoke(null)
                    return@launch
                }
                val id = repository.add(
                    WorkItem(
                        title = normalized,
                        type = type,
                        status = status,
                        totalEpisodes = totalEpisodes,
                        watchedEpisodes = watchedEpisodes,
                        rating = rating,
                        startDate = startDate,
                        finishDate = finishDate,
                        tags = tags.map { it.trim() }.filter { it.isNotEmpty() },
                        coverPath = coverPath,
                        remark = remark?.takeIf { it.isNotBlank() },
                    ),
                )
                onResult?.invoke(id)
            } finally {
                isAdding = false
            }
        }
    }

    /** 更新已有作品。 */
    fun updateWork(
        work: WorkItem,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            val normalized = normalizeTitle(work.title)
            if (normalized == null || !isValidRating(work.rating)) {
                onResult?.invoke(false)
                return@launch
            }
            val ok = repository.update(
                work.copy(
                    title = normalized,
                    tags = work.tags.map { it.trim() }.filter { it.isNotEmpty() },
                    remark = work.remark?.takeIf { it.isNotBlank() },
                ),
            )
            onResult?.invoke(ok)
        }
    }

    /** 按实体删除。 */
    fun deleteWork(
        work: WorkItem,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            onResult?.invoke(repository.delete(work))
        }
    }

    /** 按 id 删除。 */
    fun deleteWorkById(
        id: Long,
        onResult: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            onResult?.invoke(repository.deleteById(id))
        }
    }

    /**
     * 按条件查询作品列表（一次性订阅用 Flow；与 [works] 独立）。
     */
    fun queryWorks(filter: WorkFilter): Flow<List<WorkItem>> =
        repository.observeByFilter(filter)

    private fun normalizeTitle(title: String): String? =
        title.trim().takeIf { it.isNotEmpty() }

    /** 评分允许 null；非空时需在 0–10。 */
    private fun isValidRating(rating: Float?): Boolean =
        rating == null || rating in 0f..10f
}

/** 通过 [WorkRepository] 创建 [WorkViewModel]。 */
class WorkViewModelFactory(
    private val repository: WorkRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkViewModel::class.java)) {
            return WorkViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
