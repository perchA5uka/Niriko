package com.otakup.niriko.data.local

import android.content.Context
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * ui-upgrade-plan-2026 §七 B6 / §十三·3：Room 迁移链的**动态**验证（真实 SQLite，Robolectric 起）。
 *
 * ## 为什么不直接用 MigrationTestHelper
 * `androidx.room.testing.MigrationTestHelper` 从 **assets** 里按
 * `<RoomDatabase 全限定名>/<version>.json` 读 schema。AGP 8.13.2 的本地单元测试
 * 把 `android_merged_assets` 指向**主 debug 变体**的合并 assets
 * （`app/build/intermediates/unit_test_config_directory/.../com/android/tools/test_config.properties`），
 * test source set 的 assets **根本不参与合并**（`app/build/intermediates/assets/` 下只有 debug/release）。
 * 也就是说 `sourceSets["test"].assets.srcDir("schemas")` 对 unit test 无效，
 * MigrationTestHelper 在本地单测里拿不到 schema。把 schemas 塞进 debug assets 又会把
 * 2.5MB 的 schema 打进 debug APK —— 两者都不能接受。
 *
 * 因此这里改用**等价且更强**的做法，全程走生产代码、零 schema 拷贝：
 *  1. 从 `app/schemas/...`（Gradle 单测工作目录 = 模块目录 app/）读 schema JSON，
 *     用它的 `createSql`/`setupQueries` 直接建出**任意历史版本**的真实 SQLite 库；
 *  2. 用**生产**的 `NirikoDatabase.getInstance(context)` 打开它 —— 这会走
 *     `NirikoDatabase.buildDatabase()` 里注册的、与线上完全一致的 27 个 Migration，
 *     Room 自己会在迁移结束后执行 `onValidateSchema`（不匹配即
 *     `IllegalStateException: Migration didn't properly handle...`）；
 *  3. 再独立比对一次目标版本 schema（表/列/索引/视图名 + room_master_table 的 identityHash），
 *     避免只依赖 Room 的内部校验。
 *
 * 逐边界覆盖由 [everyMigrationBoundaryProducesItsTargetSchema] 直接调用生产 Migration 对象完成
 * （对象是 `companion object` 里的 `private val MIGRATION_a_b`，用反射取回，不改生产代码）。
 *
 * ## 25.json 缺口（§十三·3）
 * 仓库里 schemas 是 v1~v24、v26~v29，**缺 25.json**；仓库只有 15 个提交，
 * `git log -S 'version = 25'` 零命中 —— 计划书说的"检出 DB 版本 25 的那次提交重新生成"
 * 在本仓库**不可能执行**。本测试因此**不伪造 25.json**，改为用 24.json 建库后
 * 让生产的 MIGRATION_24_25 + MIGRATION_25_26 连续跑完 24→25→26→...→29，
 * 并在 [missingVersion25IsAbsentAndChainStillReaches29] 里把这件事钉死。
 */
// 用 Robolectric 自带的 RobolectricTestRunner，而不是 androidx.test.ext.junit.runners.AndroidJUnit4：
// 后者需要额外引入 androidx.test.ext:junit / androidx.test:core，本批次不想为它新增两个坐标
// （Robolectric 4.17 只传递带入 androidx.test:monitor:1.8.0）。二者在 JVM 上等价 —— AndroidJUnit4
// 本身就是"在非 Android 环境下委派给 RobolectricTestRunner"的薄壳。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMigrationTest {

    private companion object {
        const val DB_NAME = "niriko.db"
        const val SCHEMA_DIR = "schemas/com.otakup.niriko.data.local.NirikoDatabase"

        /** v1 的 schema 存在，但生产迁移链里没有 MIGRATION_1_2（最早是 MIGRATION_2_3），故不作为起点。 */
        const val EARLIEST_SUPPORTED_VERSION = 2

        /**
         * Room 导出 schema 时的表名占位符（entity 的 createSql 与 indices 的 createSql 里都是它）。
         * 拼写方式刻意避开 Kotlin 字符串模板语法，避免被当成模板求值。
         */
        val TABLE_PLACEHOLDER = "\$" + "{TABLE_NAME}"
    }

    private val schemaDir = File(SCHEMA_DIR)

    // ───────────────────────── 基础工具 ─────────────────────────

    private fun exportedVersions(): List<Int> =
        schemaDir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json").toInt() }
            ?.sorted()
            ?: emptyList()

    private fun schemaJson(version: Int): JSONObject {
        val f = File(schemaDir, "$version.json")
        assertTrue("缺少 schema 文件：${f.absolutePath}", f.exists())
        return JSONObject(f.readText())
    }

    /** 用 schema JSON 自带 DDL 造出该版本的完整结构（等价于 MigrationTestHelper.createDatabase 做的事）。 */
    private fun createSchema(db: SupportSQLiteDatabase, json: JSONObject) {
        val database = json.getJSONObject("database")
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            // 必须把占位符换成真实表名：否则只会造出一张字面叫占位符的表，后续
            // CREATE TABLE IF NOT EXISTS 全部变成空操作，比对时全是"表不存在"（本批次踩过）。
            db.execSQL(entity.getString("createSql").replace(TABLE_PLACEHOLDER, table))
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace(TABLE_PLACEHOLDER, table))
                }
            }
        }
        database.optJSONArray("views")?.let { views ->
            for (i in 0 until views.length()) {
                db.execSQL(views.getJSONObject(i).getString("createSql"))
            }
        }
        database.optJSONArray("setupQueries")?.let { queries ->
            for (i in 0 until queries.length()) {
                db.execSQL(queries.getString(i))
            }
        }
    }

    private fun openSchemaHelper(
        context: Context,
        name: String,
        version: Int,
        json: JSONObject,
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) = createSchema(db, json)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build(),
        )
    }

    /** companion object 里的 private val MIGRATION_a_b —— 反射取回（companion 的字段可能落在伴生类或外层类上，两处都扫）。 */
    private fun productionMigrations(): Map<String, Migration> {
        // 不用 Class.forName("...NirikoDatabase$Companion")：在 Robolectric 的沙箱类加载器下会抛
        // ClassNotFoundException（实测）。改用 Kotlin 的 Companion::class.java，等价且稳。
        val companionClass = NirikoDatabase.Companion::class.java
        val result = LinkedHashMap<String, Migration>()
        for (holder in listOf(NirikoDatabase::class.java to null, companionClass to NirikoDatabase.Companion)) {
            for (field in holder.first.declaredFields) {
                if (!field.name.startsWith("MIGRATION_")) continue
                field.isAccessible = true
                val value = field.get(holder.second)
                if (value is Migration) result[field.name] = value
            }
        }
        return result
    }

    private fun actualColumns(db: SupportSQLiteDatabase, table: String): Set<String>? {
        val names = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            if (cursor.count == 0) return null
            val idx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) names += cursor.getString(idx)
        }
        return names
    }

    private fun actualIndexNames(db: SupportSQLiteDatabase, table: String): Set<String> {
        val names = mutableSetOf<String>()
        db.query("PRAGMA index_list(`$table`)").use { cursor ->
            val idx = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                val name = cursor.getString(idx)
                // 复合主键会让 SQLite 自动建 sqlite_autoindex_* —— schema JSON 里不会列出它
                if (!name.startsWith("sqlite_autoindex_")) names += name
            }
        }
        return names
    }

    private fun actualViewNames(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'view'").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }
        return names
    }

    private fun identityHash(db: SupportSQLiteDatabase): String? {
        db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    /** 把真实库结构与某版本 schema JSON 对照，差异写进 failures（不抛异常，便于一次性收集全部问题）。 */
    private fun compareSchema(
        db: SupportSQLiteDatabase,
        expected: JSONObject,
        label: String,
        failures: MutableList<String>,
    ) {
        val database = expected.getJSONObject("database")
        val entities = database.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            val expectedColumns = mutableSetOf<String>()
            val fields = entity.getJSONArray("fields")
            for (j in 0 until fields.length()) {
                val field = fields.getJSONObject(j)
                expectedColumns += field.optString("columnName", field.optString("fieldPath"))
            }
            val actual = actualColumns(db, table)
            if (actual == null) {
                failures += "$label: 表 `$table` 不存在"
                continue
            }
            val missing = expectedColumns - actual
            val extra = actual - expectedColumns
            if (missing.isNotEmpty() || extra.isNotEmpty()) {
                failures += "$label: 表 `$table` 列不符（缺=$missing 多=$extra）"
            }
            val expectedIndices = mutableSetOf<String>()
            entity.optJSONArray("indices")?.let { indices ->
                for (j in 0 until indices.length()) expectedIndices += indices.getJSONObject(j).getString("name")
            }
            val actualIndices = actualIndexNames(db, table)
            if (expectedIndices != actualIndices) {
                failures += "$label: 表 `$table` 索引不符（期望=$expectedIndices 实际=$actualIndices）"
            }
        }
        val expectedViews = mutableSetOf<String>()
        database.optJSONArray("views")?.let { views ->
            for (i in 0 until views.length()) expectedViews += views.getJSONObject(i).getString("viewName")
        }
        val actualViews = actualViewNames(db)
        if (expectedViews != actualViews) {
            failures += "$label: 视图不符（期望=$expectedViews 实际=$actualViews）"
        }
    }

    /** 清掉 NirikoDatabase 的单例缓存（@Volatile private var instance），避免测试之间串味。 */
    private fun resetSingleton() {
        val companionClass = NirikoDatabase.Companion::class.java
        for (holder in listOf(NirikoDatabase::class.java to null, companionClass to NirikoDatabase.Companion)) {
            val field = runCatching { holder.first.getDeclaredField("instance") }.getOrNull() ?: continue
            field.isAccessible = true
            (field.get(holder.second) as? RoomDatabase)?.close()
            field.set(holder.second, null)
        }
    }

    // ───────────────────────── 用例 ─────────────────────────

    /** 每个迁移边界单独跑：从 a.json 建库 → 调用**生产** Migration 对象 → 比对 b.json。 */
    @Test
    fun everyMigrationBoundaryProducesItsTargetSchema() {
        val context: Context = RuntimeEnvironment.getApplication()
        val available = exportedVersions().toSet()
        val failures = mutableListOf<String>()
        var checked = 0
        val gapBoundaries = mutableSetOf<String>()

        val migrations = productionMigrations()
        assertTrue(
            "没能反射取到任何 MIGRATION_a_b 字段，反射策略需要更新（取到 ${migrations.size} 个）",
            migrations.size >= 27,
        )

        for ((fieldName, migration) in migrations) {
            val parts = fieldName.removePrefix("MIGRATION_").split("_")
            val from = parts[0].toInt()
            val to = parts[1].toInt()
            if (from !in available || to !in available) {
                val missing = setOf(from, to).filter { it !in available }
                // 只有 25.json 这个已登记缺口允许跳过：本仓库不可能生成它（§十三·3 禁止伪造），
                // 由循环后的 24→25→26 连续实证统一覆盖。
                if (missing == listOf(25)) {
                    gapBoundaries += fieldName
                    continue
                }
                failures += "$fieldName: 缺少 schema JSON（from=$from in=${from in available}, to=$to in=${to in available}），无法逐边界校验"
                continue
            }
            val name = "boundary_${from}_${to}.db"
            context.deleteDatabase(name)
            val helper = openSchemaHelper(context, name, from, schemaJson(from))
            try {
                val db = helper.writableDatabase
                migration.migrate(db)
                compareSchema(db, schemaJson(to), "$fieldName", failures)
                checked++
            } catch (t: Throwable) {
                failures += "$fieldName: ${t.javaClass.simpleName}: ${t.message}"
            } finally {
                helper.close()
                context.deleteDatabase(name)
            }
        }

        // ── 25.json 缺口（§十三·3：禁止伪造）──────────────────────────────
        // MIGRATION_24_25 缺目标 schema、MIGRATION_25_26 缺起始 schema，无法逐边界比对。
        // 改用等价且更强的做法：24.json 建库 → 连续跑这两条生产迁移 → 比对 26.json。
        assertTrue(
            "涉及 25.json 的边界不是预期的两条（实际=$gapBoundaries）",
            gapBoundaries == setOf("MIGRATION_24_25", "MIGRATION_25_26"),
        )
        val gapName = "boundary_24_26.db"
        context.deleteDatabase(gapName)
        val gapHelper = openSchemaHelper(context, gapName, 24, schemaJson(24))
        try {
            val gapDb = gapHelper.writableDatabase
            migrations.getValue("MIGRATION_24_25").migrate(gapDb)
            migrations.getValue("MIGRATION_25_26").migrate(gapDb)
            compareSchema(gapDb, schemaJson(26), "24→25→26", failures)
            checked++
        } catch (t: Throwable) {
            failures += "24→25→26: ${t.javaClass.simpleName}: ${t.message}"
        } finally {
            gapHelper.close()
            context.deleteDatabase(gapName)
        }

        assertTrue("逐边界迁移校验失败：" + failures.joinToString("\n"), failures.isEmpty())
        // 不写死版本号：N 条生产迁移里，涉及 25.json 的两条合并成一次 24→25→26 实证，
        // 因此期望恰好校验 N-1 次。以后每加一个版本都不必回来改这里的数字。
        assertTrue(
            "逐边界校验数量异常：" + checked + "（期望 " + (migrations.size - 1) + " = " +
                migrations.size + " 条迁移减去合并的那 1 次）",
            checked == migrations.size - 1,
        )
    }

    /** 从每一个已导出的起始版本出发，用**生产** NirikoDatabase 打开，必须一路迁到**最新导出版本**且结构正确。 */
    @Test
    fun fullChainFromEveryExportedSchemaReachesLatestVersion() {
        val context: Context = RuntimeEnvironment.getApplication()
        val target = exportedVersions().max()
        val starts = exportedVersions().filter { it >= EARLIEST_SUPPORTED_VERSION }
        val failures = mutableListOf<String>()
        var checked = 0

        for (start in starts) {
            try {
                resetSingleton()
                context.deleteDatabase(DB_NAME)
                // 必须 writableDatabase 强制打开一次：SQLiteOpenHelper 是懒的，只 close() 的话
                // onCreate 根本不跑、库文件不存在，Room 会当成全新库直接建到 29 —— 整链测试假绿。
                val base = openSchemaHelper(context, DB_NAME, start, schemaJson(start))
                base.writableDatabase
                base.close()

                val room = NirikoDatabase.getInstance(context)
                val db = room.openHelper.writableDatabase
                if (db.version != target) {
                    failures += "从 v$start 出发：迁移后 user_version=${db.version}，期望 $target"
                } else {
                    compareSchema(db, schemaJson(target), "v$start→v$target", failures)
                    val expectedHash = schemaJson(target).getJSONObject("database").getString("identityHash")
                    val actualHash = identityHash(db)
                    if (actualHash != expectedHash) {
                        failures += "从 v$start 出发：room_master_table identityHash=$actualHash，期望 $expectedHash"
                    }
                    checked++
                }
                room.close()
            } catch (t: Throwable) {
                failures += "从 v$start 出发：${t.javaClass.simpleName}: ${t.message}"
            }
        }
        resetSingleton()

        assertTrue("整链迁移校验失败：" + failures.joinToString("\n"), failures.isEmpty())
        // 同样不写死：每个已导出且受支持的起始版本都必须成功迁到最新版本，一个都不能少。
        assertTrue("整链校验数量异常：" + checked + "（期望 " + starts.size + "）", checked == starts.size)
    }

    /** §十三·3：25.json 在本仓库不可能生成，链路由 24 → 25 → 26 实测覆盖，不得伪造。 */
    @Test
    fun missingVersion25IsAbsentAndChainStillReachesLatestVersion() {
        val present = exportedVersions()
        assertTrue("25.json 出现了，说明缺口已补齐：应当把 25 纳入 ${RoomMigrationTest::class.simpleName} 的边界校验", 25 !in present)
        assertTrue("v24 schema 缺失，无法覆盖 24→25→26", 24 in present)
        assertTrue("v26 schema 缺失，无法覆盖 24→25→26", 26 in present)

        val context: Context = RuntimeEnvironment.getApplication()
        resetSingleton()
        context.deleteDatabase(DB_NAME)
        val base = openSchemaHelper(context, DB_NAME, 24, schemaJson(24))
        base.writableDatabase
        base.close()
        val room = NirikoDatabase.getInstance(context)
        val db = room.openHelper.writableDatabase
        val target = exportedVersions().max()
        assertTrue("24→" + target + " 迁移后版本应为 " + target + "，实际 " + db.version, db.version == target)
        val expectedHash = schemaJson(target).getJSONObject("database").getString("identityHash")
        assertTrue("24→" + target + " 迁移后 identityHash 不符：" + identityHash(db), identityHash(db) == expectedHash)
        room.close()
        resetSingleton()
    }
}
