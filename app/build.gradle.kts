import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.gnuning.webtunnel"

    // compileSdk 决定「用哪套 SDK 编译」；targetSdk 才是「目标 Android 14」的运行行为。
    // 注意：Compose BOM 2026.08 / lifecycle 2.11 要求 compileSdk >= 37；
    // 若你的 SDK 只装到 34/35/36，请把下方依赖版本一起降到与 SDK 匹配的版本。
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.gnuning.webtunnel"
        minSdk = 26            // Android 8.0
        targetSdk = 34         // Android 14
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }

    lint {
        abortOnError = false
    }
}

// Kotlin 2.x + AGP 9：kotlinOptions 已移除，统一用 compilerOptions
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // ——— Compose（Material 3）———
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ——— AndroidX 基础 ———
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // ——— 本地 HTTP 服务（NanoHTTPD）———
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // ——— 反向代理「转发 URL」模式（OkHttp）———
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ——— Pinggy SSH 隧道（JSch 的社区维护版，支持现代算法）———
    implementation("com.github.mwiede:jsch:2.28.8")

    // ——— 公网地址二维码（ZXing 纯 Java 核心库）———
    implementation("com.google.zxing:core:3.5.3")
}
