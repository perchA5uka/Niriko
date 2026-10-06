package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 详情页「集合类」外部结果（表 subject_relation_cache）。B15 阶段 1。
 *
 * 角色 / 制作人员 / 关联作品这类结果的特点是：**条数多、可独立刷新、UI 只按顺序整块渲染**。
 * 塞进 subject_detail_cache 的一行 JSON 会变成一个不可查询的大字段，也不利于「只刷新角色这一路」。
 * 因此按 (subjectId, kind, itemId) 一行一条存：[kind] 见 [RelationKinds]，[sortIndex] 保留源侧顺序。
 *
 * 每行都带自己的 [expiresAt]：角色与关联作品的 TTL 可以不同，互不牵连。
 */
@Entity(
    tableName = "subject_relation_cache",
    // 主键用**位置**而不是 itemId：staff 列表里同一个人会因为多个职务重复出现
    //（实测「女神异闻录4 黄金版」那类条目：524 条 staff 只有 381 个不同的 id），
    // 用 itemId 做主键会把重复项折叠掉，读回来静默少一批人。
    primaryKeys = ["subjectId", "kind", "sortIndex"],
    indices = [Index(value = ["expiresAt"])],
)
data class SubjectRelationCacheEntity(
    val subjectId: Long,
    /** characters / staff / relations。 */
    val kind: String,
    /** 源侧条目标识（角色 id / 人物 id / 关联作品 id）。 */
    val itemId: Long,
    /** 源侧顺序，读取时按其排序还原成列表。 */
    val sortIndex: Int,
    /** 行级 JSON（拆行是查询粒度，不是要求逐字段建表：这里仍是一行的完整事实）。 */
    val payload: String,
    val sourceId: String,
    val fetchedAt: Long,
    val expiresAt: Long,
) {
    /** 集合种类（与 [sortIndex] 一起决定 UI 里的整块顺序）。 */
    object RelationKinds {
        const val CHARACTERS = "characters"
        const val STAFF = "staff"
        const val RELATIONS = "relations"
    }
}
