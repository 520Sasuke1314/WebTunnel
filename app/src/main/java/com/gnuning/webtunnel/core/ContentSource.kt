package com.gnuning.webtunnel.core

import android.content.Context
import android.net.Uri
import com.gnuning.webtunnel.R

/**
 * 「要发布什么」的抽象。本地 HTTP 服务按类型分别处理（见 server/LocalWebServer.kt）。
 */
sealed interface ContentSource {

    /** 用于 UI / 日志展示的短描述 */
    val label: String

    /** 直接输出一段 HTML 文本（应用内置示例也走这里） */
    data class InlineHtml(val html: String) : ContentSource {
        override val label: String get() = "HTML 代码"
    }

    /** 输出手机里的单个文件（SAF Uri 字符串） */
    data class SingleFile(val uri: String, val name: String) : ContentSource {
        override val label: String get() = "文件 · $name"
    }

    /** 输出整个目录（SAF Tree Uri 字符串），同目录的资源（CSS/JS/图片）都能被访问 */
    data class Directory(val treeUri: String, val name: String) : ContentSource {
        override val label: String get() = "文件夹 · $name"
    }

    /** 反向代理到另一个服务（例如 app 内 WebView 自带的小服务、局域网地址、公网地址） */
    data class Proxy(val target: String) : ContentSource {
        override val label: String get() = "转发 · $target"
    }
}

/**
 * 根据设置构造 [ContentSource]，并做启动前校验。
 * 校验失败时返回 [Result.failure]，其 message 可以直接弹给用户看。
 */
object ContentSourceFactory {

    fun build(context: Context, settings: AppSettings): Result<ContentSource> = when (settings.mode) {
        ServerMode.HTML -> {
            val html = settings.inlineHtml.ifBlank { readDemo(context) }
            if (html.isBlank()) {
                Result.failure(IllegalStateException("请先输入 HTML 代码，或点击「载入示例页面」"))
            } else {
                Result.success(ContentSource.InlineHtml(html))
            }
        }

        ServerMode.FILE -> {
            val raw = settings.fileUri
            if (raw.isBlank()) {
                Result.failure(IllegalStateException("请先选择一个 HTML 文件"))
            } else {
                val uri = Uri.parse(raw)
                if (!SafUtil.canRead(context, uri)) {
                    Result.failure(IllegalStateException("文件已无法读取，请重新选择"))
                } else {
                    Result.success(
                        ContentSource.SingleFile(
                            uri = raw,
                            name = settings.fileName.ifBlank { SafUtil.displayName(context, uri) ?: "index.html" },
                        )
                    )
                }
            }
        }

        ServerMode.FOLDER -> {
            val raw = settings.folderUri
            if (raw.isBlank()) {
                Result.failure(IllegalStateException("请先选择一个文件夹"))
            } else {
                val uri = Uri.parse(raw)
                if (!SafUtil.canAccessTree(context, uri)) {
                    Result.failure(IllegalStateException("文件夹已无法读取，请重新选择"))
                } else {
                    Result.success(
                        ContentSource.Directory(
                            treeUri = raw,
                            name = settings.folderName.ifBlank { "已选目录" },
                        )
                    )
                }
            }
        }

        ServerMode.URL -> {
            val target = settings.targetUrl.trim()
            when {
                target.isBlank() -> Result.failure(IllegalStateException("请先填写要转发的 URL"))
                !target.startsWith("http://") && !target.startsWith("https://") ->
                    Result.failure(IllegalStateException("URL 需要以 http:// 或 https:// 开头"))
                else -> Result.success(ContentSource.Proxy(target))
            }
        }
    }

    /** 读取内置示例页面（res/raw/demo_html.html） */
    fun readDemo(context: Context): String = runCatching {
        context.resources.openRawResource(R.raw.demo_html).bufferedReader().use { it.readText() }
    }.getOrDefault("")
}
