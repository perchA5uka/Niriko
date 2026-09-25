# 数据库与设置迁移草案：Room KMP + DataStore

> 这些是迁移草案，不是最终代码。需要 Room KMP / DataStore KMP 支持验证。

## 1. 目标

- 保留现有 Room v20 schema、实体、DAO、TypeConverter 和 Migration 链；
- Android 与 iOS 使用同一份数据库文件结构，确保 WebDAV / JSON 备份互通；
- SettingsDataStore 只保留公共逻辑，平台负责创建 `DataStore<Preferences>`。

## 2. Room KMP 草案

### 2.1 实体 / DAO

现有 `entity/**` 和 `dao/**` 可以原样放入 `commonMain`，因为只依赖 Room 注解与 Kotlin 类型。

### 2.2 Database 构建

现有 `NirikoDatabase.getInstance(context)` 依赖 Android `Context`，需要改为 expect/actual 工厂：

```kotlin
// commonMain
expect fun createNirikoDatabase(): NirikoDatabase

// androidMain
actual fun createNirikoDatabase(): NirikoDatabase =
    Room.databaseBuilder(
        appContext,
        NirikoDatabase::class.java,
        "niriko.db",
    )
        .addMigrations(*ALL_MIGRATIONS)
        .build()

// iosMain
actual fun createNirikoDatabase(): NirikoDatabase =
    Room.databaseBuilder(
        name = "niriko.db",
        factory = { SupportSQLiteOpenHelperFactory() }, // 或 Room KMP 提供的 iOS driver
    )
        .addMigrations(*ALL_MIGRATIONS)
        .build()
```

> 实际 API 以 Room KMP 文档为准，Spike 阶段必须验证 `Migration` 的数据库对象类型。

### 2.3 Migration 迁移

现有 `NirikoDatabase.kt` 中有大量 `SupportSQLiteDatabase.execSQL(...)` 手写 SQL。

- SQL 语句本身双端通用；
- 如果 Room KMP 的 Migration 参数类型不同，需要统一改写为跨平台类型；
- 建议把 Migration 抽到独立文件，例如 `NirikoMigrations.kt`，方便双端复用。

```kotlin
// commonMain 草案
object NirikoMigrations {
    val ALL: Array<Migration> = arrayOf(
        object : Migration(2, 3) {
            override fun migrate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS ...")
            }
        },
        // ...
    )
}
```

### 2.4 迁移测试

```kotlin
// 双端共同执行
@Test
fun migrateV2ToV20() {
    // 用 schema JSON 创建 v2 数据库，逐步执行 ALL migrations
    // 断言最终 schema 与 20.json 一致
}
```

## 3. SettingsDataStore 草案

现有 `SettingsDataStore` 使用 `Context.dataStore`。改造后：

```kotlin
// commonMain
class SettingsDataStore(
    private val dataStore: DataStore<Preferences>,
) {
    // 键定义、Flow、所有 setter 保持不变，只把 context.dataStore 换成 dataStore
}

// androidMain
actual fun createSettingsDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        producePath = { appContext.filesDir.resolve("niriko_settings.preferences_pb").absolutePath },
    )

// iosMain
actual fun createSettingsDataStore(): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        producePath = { platformFileDirectory() + "/niriko_settings.preferences_pb" },
    )
```

> DataStore KMP 的 API 需要验证；如果 `createWithPath` 不可用，可改用 `create { ... }`。

## 4. 需要验证的关键点

- [ ] Room KMP 是否支持当前 KSP 版本
- [ ] Room KMP 的 Migration 参数类型与 Android 是否一致
- [ ] `withTransaction` 在 iOS 上可用
- [ ] DAO 返回 `Flow` 在 iOS 上可用
- [ ] DataStore KMP 在 iOS 上可读写
- [ ] 数据库 schema 与 Android v20 完全一致
