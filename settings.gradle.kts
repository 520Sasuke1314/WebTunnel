pluginManagement {
    repositories {
        // 说明（很重要）：
        // kotlin-compose-compiler-plugin / kotlin-annotation-processing 等 Kotlin 插件产物只发布在 Maven Central，
        // Google Maven 上没有；而部分网络环境下 dl.google.com 会长时间超时，导致依赖解析整体失败。
        // 因此这里把国内镜像放在前面，google() 兜底。国内网络环境按需保留/删除。
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        google()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        mavenCentral()
        google()
    }
}

rootProject.name = "WebTunnel"
include(":app")
