plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.otakup.niriko"
    // compileSdk 37：miuix-blur 0.9.0 硬要求（AGP 8.13.2 支持）；运行时行为仍由 targetSdk 34 决定
    compileSdk = 37

    defaultConfig {
        applicationId = "com.otakup.niriko"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema 导出目录（后续做数据库迁移时使用）
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}
