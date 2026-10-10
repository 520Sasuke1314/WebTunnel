package com.gnuning.webtunnel.util

import android.webkit.MimeTypeMap
import java.util.Locale

/** 简易 MIME 表（覆盖网页常用类型，未知类型交给系统 MimeTypeMap）。 */
object MimeTypes {

    const val HTML = "text/html; charset=utf-8"

    private val table = mapOf(
        "html" to HTML,
        "htm" to HTML,
        "xhtml" to "application/xhtml+xml; charset=utf-8",
        "css" to "text/css; charset=utf-8",
        "js" to "application/javascript; charset=utf-8",
        "mjs" to "application/javascript; charset=utf-8",
        "json" to "application/json; charset=utf-8",
        "map" to "application/json; charset=utf-8",
        "txt" to "text/plain; charset=utf-8",
        "md" to "text/plain; charset=utf-8",
        "csv" to "text/csv; charset=utf-8",
        "xml" to "application/xml; charset=utf-8",
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "bmp" to "image/bmp",
        "ico" to "image/x-icon",
        "avif" to "image/avif",
        "woff" to "font/woff",
        "woff2" to "font/woff2",
        "ttf" to "font/ttf",
        "otf" to "font/otf",
        "eot" to "application/vnd.ms-fontobject",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav",
        "ogg" to "audio/ogg",
        "m4a" to "audio/mp4",
        "mp4" to "video/mp4",
        "webm" to "video/webm",
        "mov" to "video/quicktime",
        "pdf" to "application/pdf",
        "zip" to "application/zip",
        "wasm" to "application/wasm",
        "apk" to "application/vnd.android.package-archive",
    )

    fun of(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US)
        if (ext.isEmpty()) return "application/octet-stream"
        table[ext]?.let { return it }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
