package com.otakup.niriko.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.EpisodeDao
import com.otakup.niriko.data.local.dao.ExternalRatingDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.entity.EpisodeEntity
import com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity
import com.otakup.niriko.data.local.entity.EpisodeRatingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单集二级页（阶段 B）的状态。
 *
 * 入口：收藏编辑面板的「选集」长按、剧集列表行、走势曲线气泡。
 * 承载：集名、剧照、简介、我的评价、我的评分、该集的 TMDb/IMDb 权威分。
 */
data class EpisodeDetailUiState(
    val episode: EpisodeEntity? = null,
    val subjectTitle: String? = null,
    /** 该集的权威评分（sourceId 到实体）。 */
    val ratings: Map<String, EpisodeRatingEntity> = emptyMap(),
    val myScore: Float? = null,
    val myComment: String = "",
    val rewatch: Boolean = false,
    /** 同作品的剧集序列（用于上一集 / 下一集，不压栈）。 */
    val siblings: List<EpisodeEntity> = emptyList(),
    val isLoading: Boolean = true,
    val isInCollection: Boolean = false,
    val snackbarMessage: String? = null,
) {
    private val currentIndex: Int
        get() = siblings.indexOfFirst { it.epId == episode?.epId }

    val prevEpId: Long?
        get() = currentIndex.takeIf { it > 0 }?.let { siblings[it - 1].epId }

    val nextEpId: Long?
        get() = currentIndex.takeIf { it >= 0 && it < siblings.lastIndex }
            ?.let { siblings[it + 1].epId }
}

/**
 * 单集详情 ViewModel。
 *
 * 写入约定：episode_my_ratings.score 是 NOT NULL，而「只写评价、还没打分」是常态，
 * 因此未打分时用 [UNRATED_SCORE]（-1）占位，读回时用 [effectiveScore] 还原成 null。
 */
class EpisodeDetailViewModel(
    private val episodeDao: EpisodeDao,
    private val externalRatingDao: ExternalRatingDao,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao?,
    /**
     * 修复 R1 的兜底：库里没有该集时用它按 subjectId 拉一次远端并落库。
     * 可空以便单测/预览不构造它。
     */
    private val episodeRepository: com.otakup.niriko.data.repository.EpisodeRepository? = null,
    private val subjectId: Long,
    initialEpId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    /** 当前展示的集（上一集/下一集切换时改它再 load）。 */
    private var currentEpId: Long = initialEpId

    init { load() }

    fun retry() = load()

    fun switchEpisode(newEpId: Long) {
        if (newEpId == currentEpId) return
        currentEpId = newEpId
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val snapshot = withContext(Dispatchers.IO) { readSnapshot() }
            _uiState.update {
                it.copy(
                    episode = snapshot.episode,
                    subjectTitle = snapshot.title,
                    siblings = snapshot.siblings,
                    ratings = snapshot.ratings,
                    myScore = effectiveScore(snapshot.mine),
                    myComment = snapshot.mine?.comment.orEmpty(),
                    rewatch = snapshot.mine?.rewatch ?: false,
                    isInCollection = snapshot.inCollection,
                    isLoading = false,
                )
            }
        }
    }

    private suspend fun readSnapshot(): Snapshot {
        var episode = runCatching { episodeDao.getById(currentEpId) }.getOrNull()
        if (episode == null) {
            // 修复 R1：剧集此前从不落库，单集页因此永远「未找到该集」。
            // 这里做一次兜底：拉远端 → EpisodeRepository 落库 → 再读一次。
            runCatching { episodeRepository?.getEpisodes(subjectId, forceRefresh = true) }
            episode = runCatching { episodeDao.getById(currentEpId) }.getOrNull()
        }
        val subject = runCatching { subjectDao.getById(subjectId) }.getOrNull()
        val siblings = runCatching { episodeDao.getMainEpisodes(subjectId) }.getOrDefault(emptyList())
        val ratings = runCatching {
            externalRatingDao.getEpisodeRatings(subjectId)
                .filter { it.epId == currentEpId }
                .associateBy { it.sourceId }
        }.getOrDefault(emptyMap())
        val mine = runCatching { externalRatingDao.getMyEpisodeRating(currentEpId) }.getOrNull()
        val inCollection = runCatching { collectionDao?.getBySubjectId(subjectId) != null }
            .getOrDefault(false)
        return Snapshot(episode, subject?.displayTitle, siblings, ratings, mine, inCollection)
    }

    /** 保存我的评分（null = 清除）。评分与评价共用一行记录，因此要带上另一侧的值。 */
    fun saveMyScore(score: Float?) {
        _uiState.update { it.copy(myScore = score) }
        persist(score, _uiState.value.myComment, _uiState.value.rewatch)
    }

    /** 保存我的评价（UI 侧已做防抖，这里直接落库）。 */
    fun saveMyComment(comment: String) {
        _uiState.update { it.copy(myComment = comment) }
        persist(_uiState.value.myScore, comment, _uiState.value.rewatch)
    }

    fun toggleRewatch() {
        val next = !_uiState.value.rewatch
        _uiState.update { it.copy(rewatch = next) }
        persist(_uiState.value.myScore, _uiState.value.myComment, next)
    }

    private fun persist(myScore: Float?, myComment: String, rewatch: Boolean) {
        val epId = currentEpId
        viewModelScope.launch(Dispatchers.IO) {
            val hasContent = myScore != null || myComment.isNotBlank() || rewatch
            runCatching {
                if (!hasContent) {
                    externalRatingDao.deleteMyEpisodeRating(epId)
                } else {
                    externalRatingDao.upsertMyEpisodeRating(
                        EpisodeMyRatingEntity(
                            epId = epId,
                            subjectId = subjectId,
                            score = myScore ?: UNRATED_SCORE,
                            comment = myComment.takeIf { it.isNotBlank() },
                            rewatch = rewatch,
                            ratedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
        }
    }

    /** 「看到这里」：把收藏进度写到该集。未收藏时给出明确提示（而不是静默失败）。 */
    fun markWatched() {
        val episode = _uiState.value.episode ?: return
        val progress = kotlin.math.round(episode.sort).toInt().coerceAtLeast(1)
        viewModelScope.launch {
            val existing = withContext(Dispatchers.IO) {
                runCatching { collectionDao?.getBySubjectId(subjectId) }.getOrNull()
            }
            if (existing == null) {
                _uiState.update { it.copy(snackbarMessage = "尚未收藏该作品，请先在详情页加入收藏") }
                return@launch
            }
            withContext(Dispatchers.IO) {
                runCatching {
                    collectionDao?.update(
                        existing.copy(
                            watchedEpisodes = progress,
                            updateTime = System.currentTimeMillis(),
                        )
                    )
                }
            }
            _uiState.update {
                it.copy(
                    snackbarMessage = "观看进度已更新为第 " + progress + " 集",
                    isInCollection = true,
                )
            }
        }
    }

    fun consumeSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private data class Snapshot(
        val episode: EpisodeEntity?,
        val title: String?,
        val siblings: List<EpisodeEntity>,
        val ratings: Map<String, EpisodeRatingEntity>,
        val mine: EpisodeMyRatingEntity?,
        val inCollection: Boolean,
    )

    companion object {
        /** 未打分占位（实体列 NOT NULL）。>= 0 才算有效分数。 */
        const val UNRATED_SCORE = -1f

        /** 把实体里的占位分数还原成「未评分」。 */
        fun effectiveScore(entity: EpisodeMyRatingEntity?): Float? =
            entity?.score?.takeIf { it >= 0f }
    }
}

class EpisodeDetailViewModelFactory(
    private val episodeDao: EpisodeDao,
    private val externalRatingDao: ExternalRatingDao,
    private val subjectDao: SubjectDao,
    private val collectionDao: CollectionDao?,
    private val episodeRepository: com.otakup.niriko.data.repository.EpisodeRepository? = null,
    private val subjectId: Long,
    private val epId: Long,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EpisodeDetailViewModel::class.java)) {
            return EpisodeDetailViewModel(
                episodeDao, externalRatingDao, subjectDao, collectionDao, episodeRepository,
                subjectId, epId,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: " + modelClass.name)
    }
}
