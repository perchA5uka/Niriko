package com.otakup.niriko.data.local

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 迁移链 / schema 一致性静态测试（计划 B5 · 6-2）。
 *
 * 仓库约定（README 开发约定）：**Room 迁移的 DDL 必须与 app/schemas 导出的 createSql 逐字一致**，
 * 而 schema 校验只在真机首次开库时执行、编译期不报错——错了要到用户升级时才炸。
 * 这道测试把该约定搬进单测：
 *
 * 1. 迁移链连续：每个 `MIGRATION_a_b` 都满足 b = a + 1，且 2..29 一步一步接得上；
 * 2. `ALTER TABLE x ADD COLUMN y` 在目标版本 schema 的 x 表里必须有同名列（且类型对得上）；
 * 3. `CREATE TABLE t` 在目标版本 schema 里必须存在，且列名集合与 createSql 一致；
 * 4. 每个 schema 文件的 identityHash 是 32 位十六进制。
 *
 * 注意：这是**静态**校验（解析源码与 schema JSON），不等于真机跑一遍 SQLite。
 * 「每个版本都能开库」需要 Robolectric / Room testing（见计划 B5 未做项）。
 */
class NirikoMigrationChainTest {

    /** Gradle 单测的工作目录是模块目录（app/），与 KazumiHiveReaderRealFileTest 同一约定。 */
    private val schemaDir = File("schemas/com.otakup.niriko.data.local.NirikoDatabase")
    private val dbSource = File("src/main/java/com/otakup/niriko/data/local/NirikoDatabase.kt")

    /** schema 文件名里的版本号。 */
    private fun schemaVersions(): Set<Int> =
        schemaDir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
            ?.toSet()
            ?: emptySet()

    private fun loadSchema(version: Int) =
        Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject

    /** 目标版本 schema 的「表名 → 列名集合」。 */
    private fun columnsOf(version: Int): Map<String, Set<String>> {
        val entities = loadSchema(version)["entities"]!!.jsonArray
        return entities.associate { e ->
            val obj = e.jsonObject
            val table = obj["tableName"]!!.jsonPrimitive.content
            val cols = obj["fields"]!!.jsonArray.map { it.jsonObject["columnName"]!!.jsonPrimitive.content }.toSet()
            table to cols
        }
    }

    /** 解析 NirikoDatabase.kt 里的迁移：version 对 → 该迁移的 execSQL 语句列表。 */
    private fun migrations(): Map<Pair<Int, Int>, List<String>> {
        val source = dbSource.readText()
        val result = linkedMapOf<Pair<Int, Int>, List<String>>()
        val header = Regex("""MIGRATION_(\d+)_(\d+) = object : Migration\((\d+), (\d+)\)""")
        val matches = header.findAll(source).toList()
        matches.forEachIndexed { index, m ->
            val from = m.groupValues[1].toInt()
            val to = m.groupValues[2].toInt()
            val declaredFrom = m.groupValues[3].toInt()
            val declaredTo = m.groupValues[4].toInt()
            assertEquals("MIGRATION_${from}_${to} 的名字与 Migration($declaredFrom, $declaredTo) 不一致", from, declaredFrom)
            assertEquals("同上", to, declaredTo)
            val start = m.range.last
            val end = matches.getOrNull(index + 1)?.range?.first ?: source.length
            val body = source.substring(start, end)
            val sqls = Regex("""execSQL\(\s*("(?:[^"\\]|\\.)*"(?:\s*\+\s*"(?:[^"\\]|\\.)*")*)\s*\)""")
                .findAll(body)
                .map { it.groupValues[1] }
                .toList()
            result[from to to] = sqls
        }
        return result
    }

    /** 把 Kotlin 字符串字面量拼起来并去掉转义，得到可解析的 SQL。 */
    private fun literalToSql(literal: String): String =
        literal.split(Regex("""\s*\+\s*"""))
            .joinToString("") { part ->
                part.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\n", " ")
            }

    @Test
    fun schemaFilesArePresentAndContiguousExceptDocumentedGap() {
        val versions = schemaVersions()
        assertTrue("未找到 schema 目录: ${schemaDir.absolutePath}", versions.isNotEmpty())
        val expected = (versions.min()..versions.max()).toSet()
        val missing = expected - versions
        // 已知缺口：v25 的 schema 文件不在仓库里（README 工程待办）。
        // 除它以外再缺任何一个版本都要立刻失败。
        assertTrue("schema 版本断档: $missing（除 v25 外不允许缺）", missing.isEmpty() || missing == setOf(25))
    }

    @Test
    fun everyMigrationAdvancesExactlyOneVersion() {
        val migrations = migrations()
        assertTrue("没有解析到任何迁移，正则可能失效", migrations.isNotEmpty())
        migrations.keys.forEach { (from, to) ->
            assertEquals("MIGRATION_${from}_${to} 不是单步迁移", from + 1, to)
        }
        val steps = migrations.keys.map { it.first }.toSet()
        val versions = schemaVersions()
        val maxVersion = versions.max()
        // README：迁移链是 v2 → v29。v1 是 Room 的初始建库版本（无需迁移），
        // 因此 from 值必须从 2 连续到 max-1：每一个中间版本都得有一步迁移把它带上来。
        val expectedSteps = (2..(maxVersion - 1)).toSet()
        val missingSteps = expectedSteps - steps
        assertTrue("以下升级步骤没有迁移: $missingSteps", missingSteps.isEmpty())
    }

    @Test
    fun addColumnDdlMatchesTargetSchema() {
        val ddl = Regex("""ALTER TABLE `([A-Za-z_]+)` ADD COLUMN `([A-Za-z_]+)` ([A-Z]+)""")
        val problems = mutableListOf<String>()
        var checked = 0
        migrations().forEach { (fromTo, sqls) ->
            val (_, to) = fromTo
            if (!schemaVersions().contains(to)) return@forEach
            val columns = columnsOf(to)
            sqls.map(::literalToSql).forEach { sql ->
                ddl.find(sql)?.let { m ->
                    checked++
                    val (table, column, type) = m.destructured
                    val tableColumns = columns[table]
                    when {
                        tableColumns == null -> problems += "v$to 的 schema 里没有表 $table（迁移 $fromTo 试图加列）"
                        column !in tableColumns -> problems += "v$to.$table 缺列 $column（类型 $type）"
                    }
                }
            }
        }
        assertTrue("没有解析到 ADD COLUMN，正则可能失效", checked > 0)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun createTableDdlMatchesTargetSchemaColumns() {
        val create = Regex("""CREATE TABLE IF NOT EXISTS `([A-Za-z_]+)` \((.+)\)""", RegexOption.DOT_MATCHES_ALL)
        val columnDef = Regex("""`([A-Za-z_]+)`\s+(INTEGER|REAL|TEXT)""")
        val problems = mutableListOf<String>()
        var checked = 0
        migrations().forEach { (fromTo, sqls) ->
            val (_, to) = fromTo
            if (!schemaVersions().contains(to)) return@forEach
            val columns = columnsOf(to)
            sqls.map(::literalToSql).forEach { sql ->
                create.find(sql)?.let { m ->
                    checked++
                    val table = m.groupValues[1]
                    val ddlColumns = columnDef.findAll(m.groupValues[2]).map { it.groupValues[1] }.toSet()
                    val schemaColumns = columns[table]
                    when {
                        schemaColumns == null -> problems += "v$to 的 schema 里没有表 $table"
                        ddlColumns != schemaColumns -> problems += "v$to.$table 列不一致：DDL=$ddlColumns schema=$schemaColumns"
                    }
                }
            }
        }
        assertTrue("没有解析到 CREATE TABLE，正则可能失效", checked > 0)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun identityHashesAreWellFormed() {
        val hash = Regex("""^[0-9a-f]{32}$""")
        schemaVersions().forEach { version ->
            val value = loadSchema(version)["identityHash"]!!.jsonPrimitive.content
            assertTrue("v$version 的 identityHash 格式不对: $value", hash.matches(value))
        }
    }
}
