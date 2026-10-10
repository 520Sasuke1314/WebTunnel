package com.gnuning.webtunnel.server

import android.content.Context
import android.net.Uri
import com.gnuning.webtunnel.core.ContentSource
import com.gnuning.webtunnel.core.LogRepository
import com.gnuning.webtunnel.core.SafUtil
import com.gnuning.webtunnel.util.MimeTypes
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * 本地 HTTP 服务（NanoHTTPD）。
 *
 * 职责：把 [ContentSource] 里的内容通过 `http://127.0.0.1:<port>` 暴露出来，
 * 之后由 Pinggy 隧道把这个端口映射到公网。
 *
 * 四种内容来源的处理方式：
 *  - [ContentSource.InlineHtml]：`/` 直接返回这段 HTML；
 *  - [ContentSource.SingleFile]：`/` 返回该文件（相对资源无法解析，页面会给出提示）；
 *  - [ContentSource.Directory]：按路径在目录树里查找文件，目录优先返回 index.html，否则输出目录索引；
 *  - [ContentSource.Proxy]：交给 [ReverseProxy] 反向代理。
 */
class LocalWebServer(
    private val context: Context,
    private val log: LogRepository,
    private val logEveryRequest: Boolean,
) {

    private var httpd: NanoHTTPD? = null
    private var source: ContentSource? = null
    private var proxy: ReverseProxy? = null

    /** 实际监听的端口（自动顺延后可能与用户设置不同） */
    var port: Int = 0
        private set

    val isRunning: Boolean get() = httpd != null
    val sourceLabel: String get() = source?.label ?: ""

    /**
     * 启动服务。[preferredPort] 被占用时自动 +1 顺延（最多 [maxAttempts] 次），返回真实端口。
     */
    fun start(preferredPort: Int, contentSource: ContentSource, maxAttempts: Int = 20): Int {
        stop()
        source = contentSource
        proxy = if (contentSource is ContentSource.Proxy) ReverseProxy(contentSource.target, log) else null

        val from = preferredPort.coerceIn(1024, 65535)
        var lastError: Exception? = null
        for (offset in 0 until maxAttempts) {
            val candidate = from + offset
            if (candidate > 65535) break
            try {
                val server = WebServer(candidate)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
                httpd = server
                port = candidate
                if (candidate != preferredPort) log.w("端口 $preferredPort 被占用，已自动改用 $candidate")
                return candidate
            } catch (e: Exception) {
                lastError = e
                log.d("端口 $candidate 不可用：${e.message}")
            }
        }
        throw IOException("无法启动本地服务（从 $from 起尝试了 $maxAttempts 个端口）", lastError)
    }

    fun stop() {
        runCatching { httpd?.stop() }
        httpd = null
        source = null
        proxy = null
    }

    /** 释放线程池等资源（停止隧道时调用） */
    fun shutdown() {
        stop()
        runCatching { proxy?.shutdown() }
    }

    // ------------------------------------------------------------------
    // 真正的 HTTP 处理
    // ------------------------------------------------------------------
    private inner class WebServer(bindPort: Int) : NanoHTTPD(bindPort) {

        override fun serve(session: IHTTPSession): Response {
            val method: Method = session.method ?: Method.GET
            val path = decodePath(session.uri ?: "/")
            val current = source

            // 预检请求直接放行（方便本地页面调用其他接口）
            if (method == Method.OPTIONS) {
                return withCors(newFixedLengthResponse(Response.Status.NO_CONTENT, MimeTypes.HTML, ""))
            }

            val response: Response = try {
                when (current) {
                    null -> page(Response.Status.SERVICE_UNAVAILABLE, "服务未启动", "请先在应用里启动隧道。")
                    is ContentSource.InlineHtml -> serveInline(current, path)
                    is ContentSource.SingleFile -> serveSingleFile(current, path)
                    is ContentSource.Directory -> serveDirectory(current, path)
                    is ContentSource.Proxy -> proxy?.handle(session, path)
                        ?: page(Response.Status.INTERNAL_ERROR, "500 代理未就绪", "反向代理初始化失败。")
                }
            } catch (t: Throwable) {
                log.e("处理请求失败 ${method.name} $path：${t.message}")
                page(Response.Status.INTERNAL_ERROR, "500 服务异常", escape(t.message ?: "未知错误"))
            }

            withCors(response)
            if (logEveryRequest && method != Method.OPTIONS) {
                val remote = session.remoteIpAddress ?: "unknown"
                log.i("${method.name} $path → HTTP ${response.status.requestStatus} · $remote")
            }
            return response
        }

        // ---------------- HTML 代码 ----------------
        private fun serveInline(src: ContentSource.InlineHtml, path: String): Response = when {
            path == "/" || path == "/index.html" || path == "/index.htm" -> htmlPage(src.html)
            else -> notFoundPage("当前是「HTML 代码」模式，只提供 `/` 一个页面。", path)
        }

        // ---------------- 单个文件 ----------------
        private fun serveSingleFile(src: ContentSource.SingleFile, path: String): Response {
            val uri = Uri.parse(src.uri)
            val name = src.name.ifBlank { "index.html" }
            val hitRoot = path == "/" || path.equals("/index.html", true) ||
                path.equals("/index.htm", true) || path.equals("/$name", true)
            if (!hitRoot) {
                return notFoundPage(
                    "当前是「本地文件」模式，只提供 <b>${escape(name)}</b> 这一个文件；" +
                        "它旁边的 CSS / JS / 图片不会自动加载，需要完整站点请改用「本地文件夹」模式。",
                    path,
                )
            }
            val input = SafUtil.open(context, uri)
                ?: return page(
                    Response.Status.NOT_FOUND, "404 文件不可读",
                    "无法读取 <b>${escape(name)}</b>，文件可能已被移动、删除或授权已失效，请重新选择。",
                )
            return fileResponse(name, input, SafUtil.sizeOf(context, uri))
        }

        // ---------------- 文件夹 ----------------
        private fun serveDirectory(src: ContentSource.Directory, path: String): Response {
            val treeUri = Uri.parse(src.treeUri)
            val rootId = SafUtil.treeRootId(treeUri)
                ?: return page(Response.Status.INTERNAL_ERROR, "500 目录异常", "无法解析所选目录。")

            val segments = path.trim('/').split('/').filter { it.isNotEmpty() && it != "." }
            if (segments.any { it == ".." }) {
                return page(Response.Status.FORBIDDEN, "403 非法路径", "路径中不允许出现 `..`。")
            }

            var docId = rootId
            var entry: SafUtil.Entry? = null
            for (segment in segments) {
                val child = SafUtil.findChild(context, treeUri, docId, segment)
                    ?: return notFoundPage("目录里找不到 <b>${escape(segment)}</b>。", path)
                docId = child.docId
                entry = child
            }

            // 目录（含根目录）：优先 index.html，其次输出目录索引
            if (entry == null || entry.isDir) {
                val index = listOf("index.html", "index.htm")
                    .firstNotNullOfOrNull { SafUtil.findChild(context, treeUri, docId, it) }
                if (index != null) {
                    val input = SafUtil.openDocument(context, treeUri, index.docId)
                        ?: return page(Response.Status.INTERNAL_ERROR, "500 文件不可读", escape(index.name))
                    return fileResponse(index.name, input, index.size)
                }
                return htmlPage(directoryIndexPage(treeUri, docId, path))
            }

            val input = SafUtil.openDocument(context, treeUri, entry.docId)
                ?: return page(Response.Status.INTERNAL_ERROR, "500 文件不可读", escape(entry.name))
            return fileResponse(entry.name, input, entry.size)
        }

        // ---------------- 公共响应构造 ----------------
        private fun fileResponse(name: String, input: InputStream, size: Long): Response {
            val mime = MimeTypes.of(name)
            val response = if (size > 0) {
                newFixedLengthResponse(Response.Status.OK, mime, input, size)
            } else {
                newChunkedResponse(Response.Status.OK, mime, input)
            }
            // 本地开发常用：HTML 不缓存，避免改完页面刷不出来
            if (mime.startsWith("text/html")) response.addHeader("Cache-Control", "no-store")
            return response
        }

        private fun htmlPage(html: String): Response =
            newFixedLengthResponse(Response.Status.OK, MimeTypes.HTML, html)

        private fun withCors(response: Response): Response {
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Headers", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS,HEAD")
            return response
        }

        private fun notFoundPage(tip: String, path: String): Response = page(
            Response.Status.NOT_FOUND, "404 页面不存在", "$tip<br><br>请求路径：<code>${escape(path)}</code>",
        )

        private fun page(status: Response.IStatus, title: String, body: String): Response =
            newFixedLengthResponse(status, MimeTypes.HTML, wrapPage(title, body))

        private fun wrapPage(title: String, body: String): String = """
            <!DOCTYPE html>
            <html lang="zh-CN"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>$title</title><style>$PAGE_CSS</style></head>
            <body><div class="wrap">
              <h1>$title</h1>
              <p class="hint">$body</p>
              <p class="foot">由「网页隧道 WebTunnel」提供 · 作者 Gnuning · QQ 群 1101774655</p>
            </div></body></html>
        """.trimIndent()

        /** 目录索引页（没有 index.html 时使用） */
        private fun directoryIndexPage(treeUri: Uri, docId: String, path: String): String {
            val entries = SafUtil.children(context, treeUri, docId)
                .sortedWith(compareByDescending<SafUtil.Entry> { it.isDir }.thenBy { it.name.lowercase(Locale.US) })
            val base = if (path == "/" || path.isEmpty()) "/" else path.trimEnd('/') + "/"
            val parent = if (base == "/") null else base.trimEnd('/').substringBeforeLast('/', "")

            val rows = StringBuilder()
            if (parent != null) {
                rows.append("<li><a href=\"").append(if (parent.isEmpty()) "/" else "$parent/")
                    .append("\"><span class=\"ico\">↩︎</span><span class=\"nm\">返回上级</span></a></li>")
            }
            entries.forEach { entry ->
                val href = base + urlEncode(entry.name) + if (entry.isDir) "/" else ""
                rows.append("<li><a href=\"").append(href).append("\">")
                    .append("<span class=\"ico\">").append(if (entry.isDir) "📁" else "📄").append("</span>")
                    .append("<span class=\"nm\">").append(escape(entry.name)).append("</span>")
                    .append("<span class=\"size\">").append(if (entry.isDir) "" else humanSize(entry.size))
                    .append("</span></a></li>")
            }
            if (entries.isEmpty()) rows.append("<li class=\"empty\">这个目录是空的</li>")

            return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>目录 ${escape(base)}</title><style>$PAGE_CSS</style></head>
                <body><div class="wrap">
                  <h1>目录索引</h1>
                  <p class="hint">${escape(base)} · 共 ${entries.size} 项（放入 index.html 即可作为首页）</p>
                  <ul>$rows</ul>
                  <p class="foot">由「网页隧道 WebTunnel」提供 · 作者 Gnuning · QQ 群 1101774655</p>
                </div></body></html>
            """.trimIndent()
        }
    }

    private companion object {

        /** 统一的内置页面样式（与 App 一致的「白色 + 大圆角 + 柔和阴影」风格） */
        const val PAGE_CSS = """
            :root{color-scheme:light}
            *{box-sizing:border-box}
            body{margin:0;padding:22px;background:#f5f7fc;color:#141b2d;
              font:15px/1.65 -apple-system,BlinkMacSystemFont,"PingFang SC","HarmonyOS Sans SC","Microsoft YaHei",sans-serif}
            .wrap{max-width:720px;margin:0 auto;background:#fff;border-radius:22px;padding:22px 24px;
              box-shadow:0 12px 32px rgba(24,52,120,.09);border:1px solid #e7eaf3}
            h1{font-size:19px;margin:0 0 6px;letter-spacing:.2px}
            .hint{color:#5c6478;margin:0 0 16px}
            code{background:#f1f3f9;border-radius:6px;padding:1px 6px;font-size:13px}
            ul{list-style:none;margin:0;padding:0}
            li{border-bottom:1px solid #eef1f7}
            li:last-child{border-bottom:0}
            a{display:flex;align-items:center;gap:10px;padding:11px 6px;text-decoration:none;color:#141b2d;border-radius:12px}
            a:hover{background:#f4f7ff}
            .ico{width:22px;text-align:center}
            .nm{flex:1;word-break:break-all}
            .size{color:#98a0b3;font-size:12px}
            .empty{color:#98a0b3;padding:14px 6px}
            .foot{margin:18px 0 0;color:#98a0b3;font-size:12px}
        """

        fun decodePath(raw: String): String = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)

        fun urlEncode(text: String): String =
            runCatching { URLEncoder.encode(text, "UTF-8").replace("+", "%20") }.getOrDefault(text)

        fun escape(text: String): String = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun humanSize(size: Long): String = when {
            size <= 0 -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> String.format(Locale.US, "%.1f MB", size / 1024.0 / 1024.0)
        }
    }
}
