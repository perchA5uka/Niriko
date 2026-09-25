# Gradle 模块草案：shared KMP 模块

> 这是**未接入**的草案，不会影响当前 Android 构建。
> 真正启用前需要联网下载依赖，并在 macOS 上验证 iOS target。

## 1. 目标

新增 `shared` KMP 模块，包含：

- `androidTarget()`：Android 运行
- `iosArm64()` + `iosSimulatorArm64()`：iOS 真机/模拟器
- `commonMain`：数据层 + ViewModel + Compose UI
- `androidMain` / `iosMain`：平台 actual

## 2. `shared/build.gradle.kts` 草案

```kotlin
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.ktx)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.androidx.datastore.preferences)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.room.runtime)
            implementation(libs.coil.compose)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.otakup.niriko.shared"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
    }
}
```

## 3. `gradle/libs.versions.toml` 需要新增的条目（草案）

```toml
[versions]
ktor = "2.3.12"               # 需确认最新稳定版
coilKmp = "3.1.0"             # 需确认
composeMultiplatform = "1.8.0" # 需确认与 Kotlin 2.3.10 匹配

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coilKmp" }
coil-network-ktor = { module = "io.coil-kt.coil3:coil-network-ktor3", version.ref = "coilKmp" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }
```

> 版本号必须与当前 Kotlin 2.3.10 / AGP 8.13.2 兼容，Spike 阶段确定。

## 4. 接入步骤（未来在 Mac/联网环境执行）

1. 把 `shared` 加入 `settings.gradle.kts`
2. 验证 `./gradlew :shared:compileKotlinAndroid`
3. 验证 `./gradlew :shared:compileKotlinIosSimulatorArm64`
4. 创建 `iosApp` Xcode 工程接入 shared framework
5. 逐步迁移代码

## 5. 当前不接入的原因

- 本机无外网，无法下载 Ktor / CMP Gradle Plugin；
- 当前是 Windows，无法编译 iOS target；
- 避免在未验证的情况下破坏现有 Android 构建。
