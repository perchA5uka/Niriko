package com.otakup.niriko.data.local


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.otakup.niriko.data.local.dao.CollectionDao
import com.otakup.niriko.data.local.dao.BilibiliSyncItemDao
import com.otakup.niriko.data.local.dao.PersonCollectionDao
import com.otakup.niriko.data.local.dao.SearchHistoryDao
import com.otakup.niriko.data.local.dao.SteamDao
import com.otakup.niriko.data.local.dao.SteamLibraryItemDao
import com.otakup.niriko.data.local.dao.SubjectDao
import com.otakup.niriko.data.local.WorkDao
import com.otakup.niriko.data.local.entity.BilibiliSyncItemEntity
import com.otakup.niriko.data.local.entity.CollectionEntity
import com.otakup.niriko.data.local.entity.PersonCollectionEntity
import com.otakup.niriko.data.local.entity.SearchHistoryEntity
import com.otakup.niriko.data.local.entity.SteamBindingEntity
import com.otakup.niriko.data.local.entity.SteamGameEntity
import com.otakup.niriko.data.local.entity.SteamLibraryItemEntity
import com.otakup.niriko.data.local.entity.SubjectEntity

/**
 * Niriko 本地数据库。
 * 后续版本变更时需手动添加 Migration 对象；
 * 无匹配 Migration 时 Room 抛出异常（不会静默删除数据）。
 */
@Database(
    entities = [WorkItem::class, SubjectEntity::class, CollectionEntity::class, SearchHistoryEntity::class, PersonCollectionEntity::class, BilibiliSyncItemEntity::class, SteamGameEntity::class, SteamBindingEntity::class, SteamLibraryItemEntity::class],
    version = 18,
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
                    MIGRATION_16_17, MIGRATION_17_18,
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
                // 注意：不能带 DEFAULT NULL——Room 实体列无默认值，
                // ADD COLUMN ... DEFAULT NULL 会使 default value 校验失败。
                database.execSQL(
                    "ALTER TABLE `subjects` ADD COLUMN `sourceKey` TEXT"
                )
                // 唯一索引：SQLite 唯一索引允许多个 NULL，故 Bangumi 条目（sourceKey=null）不冲突
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_subjects_sourceKey` ON `subjects` (`sourceKey`)"
                )
            }
        }
    }
}
