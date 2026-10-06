package com.otakup.niriko.data.local


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.DetailCacheDao
import com.otakup.niriko.data.local.dao.AniListDao
import com.otakup.niriko.data.local.dao.BilibiliSyncItemDao
import com.otakup.niriko.data.local.dao.EpisodeDao
import com.otakup.niriko.data.local.dao.PersonCollectionDao
import com.otakup.niriko.data.local.dao.SearchHistoryDao
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.SteamLibraryItemDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.dao.AnitabiDao
import com.otakup.niriko.data.local.dao.VndbDao
import com.otakup.niriko.data.local.dao.ExternalIdDao
import com.otakup.niriko.data.local.dao.ExternalRatingDao
import com.otakup.niriko.data.local.dao.ManualAwardDao
import com.otakup.niriko.data.local.WorkDao
import com.otakup.niriko.data.local.entity.AniListBindingEntity
import com.otakup.niriko.data.local.entity.AnitabiPointEntity
import com.otakup.niriko.data.local.entity.BilibiliSyncItemEntity
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.EpisodeEntity
import com.otakup.niriko.data.local.entity.PersonCollectionEntity
import com.otakup.niriko.data.local.entity.SearchHistoryEntity
import com.otakup.niriko.data.local.entity.SteamBindingEntity
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SteamLibraryItemEntity
import com.otakup.niriko.data.local.entity.SubjectEntity
import com.otakup.niriko.data.local.entity.VndbBindingEntity
import com.otakup.niriko.data.local.entity.SubjectExternalIdEntity
import com.otakup.niriko.data.local.entity.SubjectExternalRatingEntity
import com.otakup.niriko.data.local.entity.EpisodeRatingEntity
import com.otakup.niriko.data.local.entity.EpisodeMyRatingEntity
import com.otakup.niriko.data.local.entity.ManualAwardEntity
import com.otakup.niriko.data.local.entity.SubjectDetailCacheEntity
import com.otakup.niriko.data.local.entity.SubjectRelationCacheEntity
import com.otakup.niriko.data.local.entity.EpisodeExternalCacheEntity
import com.otakup.niriko.data.local.entity.LibraryFolderEntity
import com.otakup.niriko.data.local.entity.LibraryFolderSubjectEntity
import com.otakup.niriko.data.local.dao.LibraryFolderDao

/**
 * Niriko 本地数据库。
 * 后续版本变更时需手动添加 Migration 对象；
 * 无匹配 Migration 时 Room 抛出异常（不会静默删除数据）。
 */
@Database(
    entities = [WorkItem::class, SubjectEntity::class, CollectionEntity::class, SearchHistoryEntity::class, PersonCollectionEntity::class, BilibiliSyncItemEntity::class, SteamGameEntity::class, SteamBindingEntity::class, SteamLibraryItemEntity::class, VndbBindingEntity::class, AniListBindingEntity::class, AnitabiPointEntity::class, EpisodeEntity::class, SubjectExternalIdEntity::class, SubjectExternalRatingEntity::class, EpisodeRatingEntity::class, EpisodeMyRatingEntity::class, ManualAwardEntity::class, SubjectDetailCacheEntity::class, SubjectRelationCacheEntity::class, EpisodeExternalCacheEntity::class, LibraryFolderEntity::class, LibraryFolderSubjectEntity::class],
    // 第 6 轮 v28 → v29：删除评分月刊的 rating_snapshots 表（DROP TABLE）
    // B15 v29 → v30：详情页外部结果落 Room（三张新表，只加不改，见 MIGRATION_29_30）
    // B15 v30 → v31：集合缓存主键改为按位置（见 MIGRATION_30_31）
    // F09 v31 → v32：作品库自定义分区（两张新表，只加不改，见 MIGRATION_31_32）
    version = 32,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NirikoDatabase : RoomDatabase() {

    abstract fun workDao(): WorkDao
    abstract fun subjectDao(): SubjectDao
    abstract fun collectionDao(): CollectionDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun personCollectionDao(): PersonCollectionDao
    abstract fun bilibiliSyncItemDao(): BilibiliSyncItemDao
    abstract fun steamDao(): SteamDao
    abstract fun steamLibraryItemDao(): SteamLibraryItemDao
    abstract fun vndbDao(): VndbDao
    abstract fun anilistDao(): AniListDao
    abstract fun anitabiDao(): AnitabiDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun externalIdDao(): ExternalIdDao
    abstract fun externalRatingDao(): ExternalRatingDao
    abstract fun manualAwardDao(): ManualAwardDao

    /** B15：详情页外部结果缓存（subject_detail_cache / subject_relation_cache / episode_external_cache）。 */
    abstract fun detailCacheDao(): DetailCacheDao

    /** F09：作品库自定义分区（library_folder / library_folder_subject）。 */
    abstract fun libraryFolderDao(): LibraryFolderDao

    companion object {
        private const val DB_NAME = "niriko.db"

        @Volatile
        private var instance: NirikoDatabase? = null

        fun getInstance(context: Context): NirikoDatabase {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): NirikoDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                NirikoDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7, MIGRATION_7_8,
                    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20,
                    MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23,
                    MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27,
                    MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31,
                    MIGRATION_31_32,
                )
                .build()
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `subjects` (
                        `subjectId` INTEGER NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `titleCN` TEXT,
                        `type` TEXT NOT NULL,
                        `summary` TEXT,
                        `coverUrl` TEXT,
                        `totalEpisodes` INTEGER,
                        `airDate` TEXT,
                        `ratingScore` REAL,
                        `tags` TEXT NOT NULL DEFAULT '[]',
                        `lastSyncTime` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `collections` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `subjectId` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `watchedEpisodes` INTEGER,
                        `rating` REAL,
                        `startDate` INTEGER,
                        `finishDate` INTEGER,
                        `personalTags` TEXT NOT NULL DEFAULT '[]',
                        `personalImpression` TEXT,
                        `remark` TEXT,
                        `createTime` INTEGER NOT NULL,
                        `updateTime` INTEGER NOT NULL,
                        FOREIGN KEY (`subjectId`) REFERENCES `subjects`(`subjectId`) ON DELETE CASCADE
                    )"""
                )
                database.execSQL(
                    """CREATE UNIQUE INDEX IF NOT EXISTS `index_collections_subjectId` ON `collections` (`subjectId`)"""
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `platform` TEXT")
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `ratingTotal` INTEGER")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `volumes` INTEGER")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `series` INTEGER")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `airWeekday` INTEGER")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `search_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `keyword` TEXT NOT NULL,
                        `createTime` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 7→8 schema 未变（版本号推进），空迁移
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `sourceId` TEXT NOT NULL DEFAULT 'bangumi'")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `person_collections` (
                        `personId` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `nameCn` TEXT,
                        `imageUrl` TEXT,
                        `career` TEXT NOT NULL DEFAULT '[]',
                        `createTime` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `rank` INTEGER")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 音乐类型：已听曲目 id 集合（JSON 数组字符串），默认空
                database.execSQL("ALTER TABLE `collections` ADD COLUMN `watchedTrackIds` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Bilibili 社区评分补充列（均为可空，旧数据不变）
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `biliScore` REAL")
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `biliRatingTotal` INTEGER")
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `biliSeasonId` INTEGER")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 哔哩哔哩导入原始数据快照（追番 + 用户评分/短评）
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `bilibili_sync_items` (
                        `mediaId` INTEGER NOT NULL PRIMARY KEY,
                        `seasonId` INTEGER,
                        `bgmSubjectId` INTEGER,
                        `title` TEXT NOT NULL,
                        `cover` TEXT,
                        `followStatus` INTEGER NOT NULL,
                        `progress` INTEGER,
                        `totalEpisodes` INTEGER,
                        `biliScore` REAL,
                        `biliComment` TEXT,
                        `imported` INTEGER NOT NULL DEFAULT 0,
                        `importTime` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Steam 补充数据（扩展表，与 subjects 解耦，仅 ADD TABLE 零数据风险）
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `steam_games` (
                        `subjectId` INTEGER NOT NULL PRIMARY KEY,
                        `appId` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `shortDescription` TEXT,
                        `developers` TEXT NOT NULL DEFAULT '[]',
                        `publishers` TEXT NOT NULL DEFAULT '[]',
                        `priceCents` INTEGER,
                        `currency` TEXT,
                        `metacriticScore` INTEGER,
                        `currentPlayers` INTEGER,
                        `steamTags` TEXT NOT NULL DEFAULT '[]',
                        `screenshots` TEXT NOT NULL DEFAULT '[]',
                        `headerImage` TEXT,
                        `releaseDate` TEXT,
                        `lastUpdated` INTEGER NOT NULL DEFAULT 0
                    )"""
                )
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `steam_bindings` (
                        `subjectId` INTEGER NOT NULL PRIMARY KEY,
                        `steamAppId` INTEGER NOT NULL,
                        `matchMethod` TEXT NOT NULL DEFAULT 'AUTO',
                        `confidence` REAL NOT NULL DEFAULT 0,
                        `createTime` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Steam 游戏库导入快照（GetOwnedGames 原始数据，与收藏主表解耦）
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `steam_library_items` (
                        `appId` INTEGER NOT NULL PRIMARY KEY,
                        `steamId64` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `playtimeForeverMinutes` INTEGER NOT NULL DEFAULT 0,
                        `playtime2WeeksMinutes` INTEGER,
                        `bgmSubjectId` INTEGER,
                        `isPlaceholder` INTEGER NOT NULL DEFAULT 0,
                        `imported` INTEGER NOT NULL DEFAULT 0,
                        `importTime` INTEGER NOT NULL
                    )"""
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 家庭共享库标记（IFamilyGroupsService/GetSharedLibraryApps 借入游戏）
                database.execSQL(
                    "ALTER TABLE `steam_library_items` ADD COLUMN `shared` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // sourceKey：跨数据源稳定唯一键（"steam:570"、"neodb:xxx" 等），
                // 取代负数占位 hack。新列可空（Bangumi 条目沿用 subjectId 语义）。
                //
                // 幂等保护：前一次失败迁移可能已 ADD COLUMN（SQLite DDL 隐式提交，
                // 校验失败也无法回滚），此时列已存在、仅缺索引 → 先查列存在性再决定。
                // 注意不能带 DEFAULT NULL——Room 实体列无默认值，default value 校验会失败。
                val cursor = database.query("PRAGMA table_info(`subjects`)")
                val hasSourceKey = cursor.use { c ->
                    val nameIdx = c.getColumnIndexOrThrow("name")
                    var found = false
                    while (c.moveToNext()) {
                        if (c.getString(nameIdx) == "sourceKey") { found = true; break }
                    }
                    found
                }
                if (!hasSourceKey) {
                    database.execSQL("ALTER TABLE `subjects` ADD COLUMN `sourceKey` TEXT")
                }
                // 唯一索引：SQLite 唯一索引允许多个 NULL，故 Bangumi 条目（sourceKey=null）不冲突
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_subjects_sourceKey` ON `subjects` (`sourceKey`)"
                )
            }
        }

        /** v18 → v19：新增 vndb_bindings 表（bangumi subjectId ↔ VNDB id 绑定，与 steam_bindings 同构）。 */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vndb_bindings` (
                        `subjectId` INTEGER NOT NULL,
                        `vndbId` TEXT NOT NULL,
                        `matchMethod` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `createTime` INTEGER NOT NULL,
                        PRIMARY KEY(`subjectId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** v19 → v20：新增 anilist_bindings 表（bangumi subjectId ↔ AniList id 绑定，与 vndb_bindings 同构）。 */
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `anilist_bindings` (
                        `subjectId` INTEGER NOT NULL,
                        `anilistId` INTEGER NOT NULL,
                        `matchMethod` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `createTime` INTEGER NOT NULL,
                        PRIMARY KEY(`subjectId`)
                    )
                    """.trimIndent()
                )
            }
        }

        /** v20 → v21：收藏表新增卷进度与私密标记（阶段 B）；作品表新增精确放送时刻（阶段 A）。 */
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `collections` ADD COLUMN `watchedVolumes` INTEGER")
                database.execSQL("ALTER TABLE `collections` ADD COLUMN `isPrivate` INTEGER NOT NULL DEFAULT 0")
                // 阶段 A：精确放送时刻（分钟）+ 时区标识，来自 onair 静态数据
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `airTimeMinutes` INTEGER")
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `airTimeZone` TEXT")
            }
        }

        /** v21 → v22：圣地巡礼（Anitabi）取景地标快照表（阶段 K）。 */
        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `anitabi_points` (
                        `subjectId` INTEGER NOT NULL PRIMARY KEY,
                        `city` TEXT NOT NULL,
                        `pointsLength` INTEGER NOT NULL,
                        `imagesLength` INTEGER NOT NULL,
                        `litePointsJson` TEXT NOT NULL,
                        `fetchedAt` INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }

        /** v22 → v23：作品表新增拼音搜索键（阶段 D）。 */
        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `subjects` ADD COLUMN `pinyinKey` TEXT")
            }
        }

        /** v23 → v24：每集/章节表（每集播出状态与热力图）。 */
        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episodes` (" +
                        "`epId` INTEGER NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`sort` REAL NOT NULL, " +
                        "`ep` REAL NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`nameCn` TEXT, " +
                        "`airdate` TEXT, " +
                        "`duration` TEXT, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`type` INTEGER NOT NULL, " +
                        "`status` TEXT, " +
                        "`comment` INTEGER NOT NULL, " +
                        "`lastSyncTime` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epId`)" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_subjectId` ON `episodes` (`subjectId`)")
            }
        }

        /**
         * v24 → v25：权威评分基础设施。
         *
         * - episodes 表补回被映射层丢弃的 description / disc，并新增 stillUrl（TMDb 每集剧照）
         * - 新增 subject_external_ids（通用外部身份，取代不断增加的 xxx_bindings 表）
         * - 新增 subject_external_ratings（作品级权威评分，多标尺并存）
         * - 新增 episode_ratings（每集评分，按来源）
         *
         * 全部为 ADD COLUMN / CREATE TABLE，不修改既有列类型、不删除列，零数据风险。
         */
        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `episodes` ADD COLUMN `description` TEXT")
                database.execSQL("ALTER TABLE `episodes` ADD COLUMN `disc` INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE `episodes` ADD COLUMN `stillUrl` TEXT")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_external_ids` (" +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`provider` TEXT NOT NULL, " +
                        "`externalId` TEXT NOT NULL, " +
                        "`titleSnapshot` TEXT, " +
                        "`confidence` REAL NOT NULL, " +
                        "`bindMethod` TEXT NOT NULL, " +
                        "`subKey` TEXT, " +
                        "`boundAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`subjectId`, `provider`)" +
                        ")"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_external_ratings` (" +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`score` REAL, " +
                        "`nativeScore` REAL, " +
                        "`scoreMax` REAL NOT NULL, " +
                        "`voteCount` INTEGER, " +
                        "`sourceUrl` TEXT, " +
                        "`extraJson` TEXT NOT NULL, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`subjectId`, `sourceId`)" +
                        ")"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episode_ratings` (" +
                        "`epId` INTEGER NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`score` REAL, " +
                        "`scoreMax` REAL NOT NULL, " +
                        "`voteCount` INTEGER, " +
                        "`fetchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epId`, `sourceId`)" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_ratings_subjectId` ON `episode_ratings` (`subjectId`)")
            }
        }

        /**
         * v25 → v26：用户侧数据。
         *
         * - episode_my_ratings：我的每集评分（走势曲线的「我的曲线」，随备份 / WebDAV 同步）
         * - manual_awards：手动录入的权威机构成绩（Fami通 40 分制、Billboard / Oricon 榜位等——
         *   这些机构没有可接入 API，方案明确不做抓取，改由用户录入）
         */
        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episode_my_ratings` (" +
                        "`epId` INTEGER NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`score` REAL NOT NULL, " +
                        "`ratedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`epId`)" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_episode_my_ratings_subjectId` ON `episode_my_ratings` (`subjectId`)")

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `manual_awards` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`sourceId` TEXT NOT NULL, " +
                        "`score` REAL, " +
                        "`scoreMax` REAL NOT NULL, " +
                        "`rankPosition` INTEGER, " +
                        "`note` TEXT, " +
                        "`url` TEXT, " +
                        "`createTime` INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_manual_awards_subjectId` ON `manual_awards` (`subjectId`)")
            }
        }

        /**
         * v26 → v27：单集二级页所需的用户数据。
         *
         * - `comment`：本集短评（此前每集只能存一个分数，写不了任何文字）
         * - `rewatch`：二刷标记
         *
         * 两列都可空/带默认值，纯 ADD COLUMN，零数据风险。
         */
        private val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `episode_my_ratings` ADD COLUMN `comment` TEXT")
                database.execSQL(
                    "ALTER TABLE `episode_my_ratings` ADD COLUMN `rewatch` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * v27 → v28：新增 `rating_snapshots` 表（评分月刊的本地历史快照）。
         *
         * 纯 CREATE TABLE，不动任何既有表，因此零数据风险。
         *
         * ⚠️ 这段 DDL **逐字对齐** Room 生成的
         * `app/schemas/com.otakup.niriko.data.local.NirikoDatabase/28.json` 里的 `createSql`：
         * Room 的 schema 校验只在**真机首次开库**时执行，编译期不会报错，不一致就是运行时崩溃。
         * 特别地，`recordedAt` 在生成的 SQL 里是 `INTEGER NOT NULL`（**没有** DEFAULT）——
         * 迁移里也不能加 DEFAULT，否则两者的建表语句不等价。
         */
        private val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `rating_snapshots` (" +
                        "`subjectId` INTEGER NOT NULL, " +
                        "`month` TEXT NOT NULL, " +
                        "`score` REAL, " +
                        "`rank` INTEGER, " +
                        "`total` INTEGER, " +
                        "`recordedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`subjectId`, `month`)" +
                        ")"
                )
            }
        }

        /**
         * v28 → v29（第 6 轮 §5）：删除评分月刊的本地快照表。
         *
         * 这是本轮**唯一不可逆**的改动（老库升级后表就没了），但评分月刊整块被删除，
         * 且该表**不在**备份/同步范围内（第 4 轮设计），因此不影响用户数据。
         *
         * 注意：Room 的 schema 校验只在真机首次开库时执行，编译期不报错 ——
         * 必须逐字核对构建生成的 29.json（app/schemas 目录下）里确实不再有
         * `rating_snapshots`，否则老用户升级即崩。
         */
        private val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `rating_snapshots`")
            }
        }

        /**
         * B15（v29 → v30）：详情页外部结果落 Room —— **只加三张新表，不动任何既有列**。
         *
         * 为什么可以这么干净：这一版的目标是「重启后不用重新跑一遍外部源」，
         * 属于纯新增缓存，因此不需要改既有表结构，也就不存在「用户评分/评论/分区/手动绑定」
         * 被迁移影响的风险（§16 的口径：这些数据不可因此清除）。
         * 回滚方式也最简单：停止读新表即可（§7.3 回滚条款），旧数据一动没动。
         */

        private val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_detail_cache` (`subjectId` INTEGER NOT NULL, `sourceKey` TEXT NOT NULL, `schemaVersion` INTEGER NOT NULL, `fetchedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `payload` TEXT, `errorSummary` TEXT, `lastErrorAt` INTEGER, PRIMARY KEY(`subjectId`, `sourceKey`))",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subject_detail_cache_expiresAt` ON `subject_detail_cache` (`expiresAt`)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_relation_cache` (`subjectId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `itemId` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, `payload` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`subjectId`, `kind`, `itemId`))",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subject_relation_cache_expiresAt` ON `subject_relation_cache` (`expiresAt`)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `episode_external_cache` (`subjectId` INTEGER NOT NULL, `epId` INTEGER NOT NULL, `seasonNumber` INTEGER, `episodeNumber` INTEGER, `tmdbTitle` TEXT, `sourceId` TEXT NOT NULL, `alignmentState` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`subjectId`, `epId`))",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_episode_external_cache_expiresAt` ON `episode_external_cache` (`expiresAt`)",
                )
            }
        }

        /**
         * B15（v30 → v31）：`subject_relation_cache` 的主键从 (subjectId, kind, itemId) 改成
         * (subjectId, kind, sortIndex)。
         *
         * 为什么必须改：staff 列表里同一个人会因为**多个职务**重复出现 —— 真机实测
         * 「角色 46 条 / staff 524 条 / 关联 55 条」，用 itemId 做主键写入后只剩 381 行 staff，
         * 读回来会静默少一批人（而且少得不明显）。改成按位置做主键后行数与列表长度严格一致。
         *
         * 直接 DROP + CREATE 是安全的：这张表**纯粹是可再取的缓存**，不含任何用户数据
         * （用户评分/评论/分区/手动绑定都在别的表里，一律没动）。丢掉的只是「重启后不用重拉」，
         * 下次进详情页会重新写一份。
         */
        /**
         * F09（v31 → v32）：作品库自定义分区 —— **只加两张新表 + 两个索引，不动任何既有列**。
         *
         * 与 v29→v30 同一个口径：这两张表装的是**用户数据**（他自己建的分区与成员关系），
         * 但迁移本身是纯新增 —— 不动 collections / subjects / episode_ratings 的任何一列，
         * 因此不可能影响用户评分、评论、手动绑定或既有收藏（§16 的口径）。
         *
         * 回滚最简单的做法同样是**停止读新表**（§7.3 回滚条款配 §14 的回滚规则）：
         * 表留着、数据留着，界面回到没有分区的样子，用户的收藏一条没动。
         *
         * `isCollapsed` 用 INTEGER NOT NULL（Room 的 Boolean 约定），默认 0 = 展开。
         * 成员表的联合唯一键是复合主键 (folderId, subjectId) —— 重复加入是幂等的。
         */
        private val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_folder` (`id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `isCollapsed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_library_folder_sortOrder` ON `library_folder` (`sortOrder`)",
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `library_folder_subject` (`folderId` INTEGER NOT NULL, `subjectId` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`folderId`, `subjectId`))",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_library_folder_subject_subjectId` ON `library_folder_subject` (`subjectId`)",
                )
            }
        }

        private val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `subject_relation_cache`")
                // 元信息行必须一起删：否则会留下「元信息说取过、数据行却没了」的漂移状态，
                // 读侧会把三组列表都读成空、还因为「没过期」不发网络请求（真机实测踩到过）。
                database.execSQL("DELETE FROM `subject_detail_cache` WHERE `sourceKey` = 'collections_meta'")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subject_relation_cache` (`subjectId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `itemId` INTEGER NOT NULL, `payload` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`subjectId`, `kind`, `sortIndex`))",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subject_relation_cache_expiresAt` ON `subject_relation_cache` (`expiresAt`)",
                )
            }
        }
    }
}
