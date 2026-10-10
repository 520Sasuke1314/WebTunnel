package com.gnuning.webtunnel.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 复制 / 分享 / 打开链接的轻封装。 */
object ShareUtil {

    fun copy(context: Context, text: String, label: String = "网页隧道") {
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    fun share(context: Context, text: String, subject: String = "网页隧道") {
        runCatching {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(
                Intent.createChooser(send, "分享公网地址").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
