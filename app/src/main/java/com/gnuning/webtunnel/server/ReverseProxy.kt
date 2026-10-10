package com.gnuning.webtunnel.server

import com.gnuning.webtunnel.core.LogRepository
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 反向代理：把隧道收到的请求转发到用户填写的目标地址（本地 app 的小服务 / 局域网 / 公网都行）。
 *
 * 已知限制（二次开发时可按需扩展）：
 *  1. 不支持 WebSocket（会返回 501）；
 *  2. `application/x-www-form-urlencoded` 的请求体会由 NanoHTTPD 解析成参数，这里会重新拼回去；
 *  3. 不会重写 HTML 里的相对链接（那是另一个量级的工程），仅重写 `Location` 响应头。
 */
class ReverseProxy(target: String, private val log: LogRepository) {

    private val base: String = target.trim().trimEnd('/')

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = 不限制，方便大文件 / 慢接口
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)                // 跳转交给浏览器，避免变成「服务端跟随」
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()

    private var warnedUpgrade = false

    fun handle(session: NanoHTTPD.IHTTPSession, path: String): NanoHTTPD.Response {
        val method: NanoHTTPD.Method = session.method ?: NanoHTTPD.Method.GET

        if (session.headers?.get("upgrade")?.contains("websocket", ignoreCase = true) == true) {
            if (!warnedUpgrade) {
                warnedUpgrade = true
                log.w("检测到 WebSocket 请求：当前「转发 URL」模式暂不支持 WebSocket。")
            }
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_IMPLEMENTED, "text/plain; charset=utf-8",
                "WebSocket is not supported by the reverse proxy mode.",
            )
        }

        val url = buildTargetUrl(path, session.queryParameterString)
        val request = try {
            buildRequest(session, method, url)
        } catch (t: Throwable) {
            log.w("构造代理请求失败：${t.message}")
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain; charset=utf-8", "Bad request: ${t.message}",
            )
        }

        val upstream = try {
            client.newCall(request).execute()
        } catch (t: Throwable) {
            log.w("转发失败 ${method.name} $url：${t.message}")
            return NanoHTTPD.newFixedLengthResponse(
                RawStatus(502, "Bad Gateway"), "text/plain; charset=utf-8",
                "无法连接到目标地址：$url\n${t.message}\n\n请确认目标服务正在运行、地址与端口填写正确。",
            )
        }

        val mime = upstream.header("Content-Type") ?: "application/octet-stream"
        val body = upstream.body
        val noBody = method == NanoHTTPD.Method.HEAD || upstream.code == 204 || upstream.code == 304 || body == null

        val response: NanoHTTPD.Response = if (noBody) {
            NanoHTTPD.newFixedLengthResponse(statusOf(upstream.code, upstream.message), mime, ByteArrayInputStream(ByteArray(0)), 0L)
        } else {
            NanoHTTPD.newChunkedResponse(statusOf(upstream.code, upstream.message), mime, body!!.byteStream())
        }

        upstream.headers.forEach { (name, value) ->
            val lower = name.lowercase(Locale.US)
            if (lower in SKIP_RESPONSE_HEADERS) return@forEach
            runCatching { response.addHeader(name, value) }
        }

        // 重写跳转地址：否则外部浏览器会被跳到手机内网地址
        upstream.header("Location")?.let { location ->
            val host = session.headers?.get("host")
            val scheme = session.headers?.get("x-forwarded-proto")?.takeIf { it.isNotBlank() } ?: "https"
            response.addHeader("Location", rewriteLocation(location, host, scheme))
        }

        return response
    }

    fun shutdown() {
        runCatching { client.dispatcher.cancelAll() }
        runCatching { client.connectionPool.evictAll() }
    }

    // ---------------- 内部实现 ----------------

    private fun buildTargetUrl(path: String, query: String?): String {
        val sb = StringBuilder(base)
        if (path.isNotEmpty() && path != "/") {
            sb.append(if (path.startsWith("/")) path else "/$path")
        }
        if (!query.isNullOrBlank()) {
            sb.append('?').append(query)
        }
        return sb.toString()
    }

    private fun buildRequest(session: NanoHTTPD.IHTTPSession, method: NanoHTTPD.Method, url: String): Request {
        val builder = Request.Builder().url(url)
        session.headers?.forEach { (key, value) ->
            val lower = key.lowercase(Locale.US)
            if (lower in SKIP_REQUEST_HEADERS) return@forEach
            runCatching { builder.header(key, value) }
        }

        val body: RequestBody? = when (method) {
            NanoHTTPD.Method.POST, NanoHTTPD.Method.PUT, NanoHTTPD.Method.PATCH, NanoHTTPD.Method.DELETE ->
                readBody(session)
            else -> null
        }
        builder.method(method.name, body)
        return builder.build()
    }

    /** NanoHTTPD 会把请求体解析到临时文件（postData）或参数表里，这里还原成字节。 */
    private fun readBody(session: NanoHTTPD.IHTTPSession): RequestBody {
        val files = HashMap<String, String>()
        runCatching { session.parseBody(files) }

        val contentTypeHeader = session.headers?.get("content-type")
        val mediaType = contentTypeHeader?.let { runCatching { it.toMediaTypeOrNull() }.getOrNull() }

        var bytes: ByteArray = files["postData"]?.let { tempPath ->
            runCatching { File(tempPath).readBytes() }.getOrDefault(ByteArray(0))
        } ?: ByteArray(0)

        // 表单参数会被 NanoHTTPD 解析掉，这里重新拼回 urlencoded 形式
        if (bytes.isEmpty()) {
            val parms = session.parms
            val isForm = contentTypeHeader?.startsWith("application/x-www-form-urlencoded") == true
            if (isForm && !parms.isNullOrEmpty()) {
                bytes = parms.entries.joinToString("&") { (k, v) ->
                    runCatching { "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }.getOrDefault("")
                }.toByteArray()
            }
        }
        return bytes.toRequestBody(mediaType, 0, bytes.size)
    }

    private fun rewriteLocation(location: String, host: String?, scheme: String): String {
        if (host.isNullOrBlank()) return location
        return when {
            location.startsWith(base) -> "$scheme://$host${location.removePrefix(base)}"
            location.startsWith("http://") || location.startsWith("https://") -> location
            location.startsWith("/") -> "$scheme://$host$location"
            else -> location
        }
    }

    private fun statusOf(code: Int, message: String?): NanoHTTPD.Response.IStatus =
        RawStatus(code, message?.takeIf { it.isNotBlank() } ?: defaultReason(code))

    /** NanoHTTPD 的状态枚举没有覆盖所有 HTTP 码（例如 502），这里自己实现 IStatus，保证状态码原样透传。 */
    private class RawStatus(private val code: Int, private val reason: String) : NanoHTTPD.Response.IStatus {
        override fun getDescription(): String = "$code $reason"
        override fun getRequestStatus(): Int = code
    }

    private companion object {
        val SKIP_REQUEST_HEADERS = setOf(
            "host", "connection", "content-length", "transfer-encoding", "accept-encoding",
            "upgrade", "keep-alive", "te", "trailer", "proxy-connection", "expect", "cookie2",
        )

        val SKIP_RESPONSE_HEADERS = setOf(
            "content-length", "transfer-encoding", "connection", "keep-alive",
            "content-encoding", "location", "trailer",
        )

        fun defaultReason(code: Int): String = when (code) {
            200 -> "OK"
            201 -> "Created"
            204 -> "No Content"
            301 -> "Moved Permanently"
            302 -> "Found"
            304 -> "Not Modified"
            307 -> "Temporary Redirect"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Status"
        }
    }
}
