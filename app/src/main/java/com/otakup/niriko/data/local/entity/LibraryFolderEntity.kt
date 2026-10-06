package com.otakup.niriko.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 作品库自定义分区（清单 F09）。
 *
 * 一部作品可以属于**多个**分区，因此分区与作品是多对多，成员关系放在
 * [LibraryFolderSubjectEntity] 里 —— 这张表只描述「分区本身是什么」。
 *
 * 分区**不是**收藏：删除分区只删关系行与分区行，绝不触碰 subjects / collections
 * （§10.1 与 §14 的回滚条款：删除分区只删除关系表记录，不触碰作品主表）。
 *
 * @param sortOrder 分区在导航条里的顺序（升序）。新建时取「当前最大 + 1」，保证新分区排最后。
 * @param isCollapsed 折叠状态持久化（§10.1 要求）—— 重启后保持用户收起的样子。
 */
@Entity(
    tableName = "library_folder",
    indices = [Index(value = ["sortOrder"])],
)
data class LibraryFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val sortOrder: Int = 0,
    val isCollapsed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * 分区成员关系（清单 F09）：分区 ⇄ 作品 的多对多。
 *
 * 复合主键 (folderId, subjectId) 就是那条「联合唯一键」（§10.1）——
 * 重复加入同一分区是幂等写入（OnConflictStrategy.REPLACE），不会出现同一部作品
 * 在同一个分区里出现两次（那会让分区内的列表出现重复卡）。
 *
 * 外键 **ON DELETE CASCADE**：删分区时关系行自动消失。注意 Room 默认不开启外键约束，
 * 但本表同时靠 DAO 的显式删除兜底（见 LibraryFolderDao.deleteFolder），
 * 两条路都通，任何一条单独生效都不会留下孤儿关系行。
 */
@Entity(
    tableName = "library_folder_subject",
    primaryKeys = ["folderId", "subjectId"],
    // subjectId 单独建索引：反查「这部作品在哪些分区」（长按菜单用）；
    // folderId 是复合主键的最左列，自带索引，因此不再重复声明。
    indices = [Index(value = ["subjectId"])],
)
data class LibraryFolderSubjectEntity(
    val folderId: Long,
    val subjectId: Long,
    /** 分区内顺序（升序）；0 = 未显式排序，按加入时间退让。 */
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
)
