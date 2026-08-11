package com.otakup.niriko.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.otakup.niriko.data.model.WatchStatus
import com.otakup.niriko.data.model.WorkType
import java.time.LocalDate

/**
 * 二次元作品记录实体。
 * 对应表 [TABLE_NAME]。
 */
@Entity(tableName = WorkItem.TABLE_NAME)
data class WorkItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** 作品名称（非空） */
    val title: String,
    /** 作品类型：动画 / 漫画 / 轻小说 / 其他 */
    val type: WorkType,
    /** 观看状态：想看 / 在看 / 看过 / 搁置 / 抛弃 */
    val status: WatchStatus,
    /** 总集数 / 总话数 */
    val totalEpisodes: Int? = null,
    /** 已看数量 */
    val watchedEpisodes: Int? = null,
    /** 评分 0–10 */
    val rating: Float? = null,
    /** 开始观看日期 */
    val startDate: LocalDate? = null,
    /** 看完日期 */
    val finishDate: LocalDate? = null,
    /** 标签列表 */
    val tags: List<String> = emptyList(),
    /** 本地封面图片路径 */
    val coverPath: String? = null,
    /** 备注 */
    val remark: String? = null,
    /** 创建时间戳（毫秒） */
    val createTime: Long = System.currentTimeMillis(),
    /** 更新时间戳（毫秒） */
    val updateTime: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TABLE_NAME = "work_items"
    }
}
