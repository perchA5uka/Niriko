import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

// 发布签名（计划 B5 · 6-5）：凭据只从本地 keystore.properties 读取，该文件与 keystore 都在 .gitignore 里。
// 没有它时（CI / 其它机器）release 仍按原样产出未签名包，构建不会失败。
// 注意：这段必须在 plugins {} 之后——Gradle 要求 plugins 块是最靠前的语句。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.otakup.niriko"
    // compileSdk 37：miuix-blur 0.9.0 硬要求（AGP 8.13.2 支持）；运行时行为仍由 targetSdk 34 决定
    compileSdk = 37

    defaultConfig {
        applicationId = "com.otakup.niriko"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema 导出目录（后续做数据库迁移时使用）
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 有 keystore.properties 才挂签名；否则（CI 等环境）保持未签名，构建不失败
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ── ui-upgrade-plan-2026 §七 B6：Robolectric（JVM 上的真实 Android 运行时） ──
    // Room 迁移测试靠它起真实 SQLite；读到合并后的资源与 AndroidManifest 才不会拿到空壳运行时。
    // 注意：AGP 的单元测试**不合并 test source set 的 assets**（见 RoomMigrationTest 头部注释），
    // 所以 app/schemas 不能靠挂 assets 让 MigrationTestHelper 看到，本工程改用直读 schema JSON。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // ProcessLifecycleOwner：进前台触发一轮受控刷新（onStop 取消前台任务）
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)

    // Compose BOM + Material 3
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    // Liquid Glass 背景模糊（悬浮胶囊/卡片玻璃）— miuix（SukiSU 同款）
    // 0.9.0 需 Kotlin 2.3（本项目已升级）；0.9.2+ 需 Kotlin 2.4（KSP 无对应版本，不采用）
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.0")

    // Liquid Glass 进阶：kyant/backdrop（highlight/shadow/innerShadow + lens/vibrancy DSL）
    // Route A：接入官方库。1.0.6 匹配 Kotlin 2.3.10 + Compose 1.10.3 + AGP 8.x；
    // 2.0.1 需 AGP 9.1 / Compose 1.12，当前工程不兼容（阶段 1 先做静态卡三件套）。
    // 上游最新即 2.0.1（需 Compose 1.12），当前不可升级；升级前必须先升 Compose/AGP。
    implementation("io.github.kyant0:backdrop:1.0.6")

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

        // Coil（图片加载，后续作品封面可用）
    implementation(libs.coil.compose)

    // DataStore Preferences（设置持久化）
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // HCT 色彩空间：自定义主题色种子 → 完整 M3 配色方案
    implementation(libs.material.color.utilities)

    // Media3（动态壁纸：ExoPlayer 循环静音播放）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    // 开屏动画（SplashScreen API 兼容层）
    implementation(libs.core.splashscreen)

    // WorkManager（放送提醒每日周期任务，阶段 J）
    implementation(libs.androidx.work.runtime.ktx)

    // pinyin4j（阶段 D：拼音搜索）
    implementation(libs.pinyin4j)

    // 本地单元测试（JVM，无需设备）
    testImplementation("junit:junit:4.13.2")

    // Network (Retrofit + OkHttp + Kotlin Serialization)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // ─────────────────────────────────────────────────────────────────────
    // ui-upgrade-plan-2026 批次依赖 · B0 可解析性探针（已通过，见 docs §十四）
    // 本段只声明坐标、尚未有任何业务代码引用；B0 checklist 第 8 条
    // 要求先证明它们拉得下来，再按 B1 → B3 → B4 的强串行顺序落地实现。
    // 若某坐标在此处解析失败，先改这里，不要先写代码。
    // ─────────────────────────────────────────────────────────────────────
    // B1 图表：Vico 3（替换 ui/stats/StatsScreen.kt 与 ui/subject/EpisodeRatingChart.kt 的手绘 Canvas）
    // [B0 探针结论] 3.3.1 会把 kotlin-stdlib 顶到 2.4.10、Compose UI 顶到 1.12.0（与 Kotlin 2.3.10 冲突），
    // 3.2.0 实测零版本提升 → 本轮以 3.2.0 为准。
    implementation(libs.vico.compose.m3)
    // B3 日历：kizitonwose Calendar（StatsScreen 的月份日历改为周/月双模）
    implementation(libs.kizitonwose.calendar)
    // B4 加载态：shimmer 骨架屏微光（接 AppSettings.reduceMotion）
    implementation(libs.compose.shimmer)
    // B2 窗口尺寸类别：androidx.compose.material3.adaptive（currentWindowAdaptiveInfo）
    // 明令不使用旧的 material3-window-size-class，避免两套尺寸类别判定不一致。
    implementation(libs.androidx.compose.material3.adaptive)

    // ─────────────────────────────────────────────────────────────────────
    // ui-upgrade-plan-2026 批次依赖 · §七 B6（Room 迁移测试）
    // 只进本地单元测试（JVM）：Robolectric 提供真实 Android 运行时（含 SQLite），不进 APK，
    // 也不顶高任何已有构件的版本（已对 debug/debugUnitTest 两条类路径逐 artifact 比对核实）。
    //
    // 注：计划书 §七 想要的 Roborazzi 截图基线在 B6 探针阶段被**证伪并移除** ——
    // kyant-backdrop 的 DefaultHighlightShaderString / lens 着色器，以及本项目自己的
    // LIQUID_LENS_SHADER，一律被 Robolectric 4.17 的 native SkSL 拒绝：
    //   java.lang.IllegalArgumentException: error: 4: 'color' is not a valid layout qualifier
    //   java.lang.IllegalArgumentException: error: 50/51: cannot swizzle value of type 'shader'
    // 也就是说任何走真玻璃（drawBackdrop + RuntimeShader）的卡片都无法在 Robolectric 下光栅化，
    // 只剩「无 backdrop 的静态降级」与「BlurredGlassSurface 毛玻璃位图」两条路能渲染。
    // 详见本批次交付报告。
    // ─────────────────────────────────────────────────────────────────────
    testImplementation(libs.robolectric)
    // Room 迁移测试；同时带入 androidx.test.ext:junit（@RunWith(AndroidJUnit4) 的来源）
    testImplementation(libs.androidx.room.testing)

    // ─────────────────────────────────────────────────────────────────────
    // ui-reference-plan-round3 · P2（R5）telephoto（Apache-2.0，Coil 2 变体）：ImageViewer 的子采样缩放。
    // 探针已通过（`gradlew :app:dependencyInsight --dependency kotlin-stdlib`）：基线本身就是
    // kotlin-stdlib 2.3.21 / JBC foundation 1.11.0（由 miuix-blur 0.9.0 与 calendar 2.10.1 带来），
    // 本库不产生任何额外顶版。R4 的雷达图改为自绘 Canvas（compose-charts 已发布版本里没有雷达图），
    // 故 compose-charts 不引入。
    // ─────────────────────────────────────────────────────────────────────
    implementation(libs.telephoto.zoomable)
    implementation(libs.telephoto.zoomable.image.coil)
}

