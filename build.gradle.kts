// 顶层构建脚本：只声明插件版本，不应用到根工程。
//
// 工具链说明（作者 Gnuning / QQ 群 1101774655）：
//   AGP 9.x 默认启用「内置 Kotlin」与新 DSL，本工程沿用「经典 Kotlin 插件模式」，
//   因此 gradle.properties 中显式设置了 android.builtInKotlin=false + android.newDsl=false，
//   对应这里显式声明 kotlin.android / kotlin.plugin.compose。
plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
