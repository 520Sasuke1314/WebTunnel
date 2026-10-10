package com.gnuning.webtunnel.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gnuning.webtunnel.WebTunnelApp
import com.gnuning.webtunnel.core.AppInfo
import com.gnuning.webtunnel.core.AppSettings
import com.gnuning.webtunnel.core.ContentSourceFactory
import com.gnuning.webtunnel.core.LogEntry
import com.gnuning.webtunnel.core.SafUtil
import com.gnuning.webtunnel.core.ServerMode
import com.gnuning.webtunnel.core.TunnelState
import com.gnuning.webtunnel.core.TunnelStatus
import com.gnuning.webtunnel.core.toTunnelConfig
import com.gnuning.webtunnel.util.ShareUtil
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 全应用共用的 ViewModel：把容器里的状态暴露给 Compose，并集中处理「启动/停止/复制/分享」等动作。
 * 三个页面（主页 / 日志 / 设置）共用同一个实例，切换 Tab 不会丢状态。
 */
class AppViewModel(private val app: WebTunnelApp) : ViewModel() {

    private val container = app.container

    val settings: StateFlow<AppSettings> = container.settings.state
    val tunnel: StateFlow<TunnelState> = container.controller.state
    val logs: StateFlow<List<LogEntry>> = container.logs.entries

    /** 已自动复制过的地址，避免同一条地址被反复写进剪贴板 */
    private var autoCopiedUrl: String? = null

    init {
        // 连接成功后按需自动复制公网地址（可在设置里关闭）
        viewModelScope.launch {
            container.controller.state.collect { state ->
                val url = state.publicUrl
                if (state.status == TunnelStatus.CONNECTED && url != null &&
                    container.settings.state.value.autoCopy && autoCopiedUrl != url
                ) {
                    autoCopiedUrl = url
                    ShareUtil.copy(app, url, "公网地址")
                    container.logs.d("已自动复制公网地址到剪贴板")
                }
            }
        }
    }

    // ---------------- 设置 ----------------
    fun updateSettings(block: (AppSettings) -> AppSettings) {
        container.settings.update(block)
    }

    // ---------------- 内容来源 ----------------
    fun setMode(mode: ServerMode) = updateSettings { it.copy(mode = mode) }

    fun setFileUri(uri: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = SafUtil.displayName(app, uri) ?: "index.html"
        updateSettings { it.copy(fileUri = uri.toString(), fileName = name, mode = ServerMode.FILE) }
        container.logs.i("已选择文件：$name")
    }

    fun setFolderUri(uri: Uri) {
        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val name = SafUtil.displayName(app, uri) ?: "已选目录"
        updateSettings { it.copy(folderUri = uri.toString(), folderName = name, mode = ServerMode.FOLDER) }
        container.logs.i("已选择文件夹：$name（目录内文件均可被访问）")
    }

    fun loadDemoHtml() {
        val demo = ContentSourceFactory.readDemo(app)
        if (demo.isBlank()) {
            container.logs.w("示例页面读取失败（res/raw/demo_html.html 缺失）")
        } else {
            updateSettings { it.copy(inlineHtml = demo, mode = ServerMode.HTML) }
            container.logs.i("已载入内置示例页面")
        }
    }

    // ---------------- 隧道控制 ----------------
    /** 启动隧道。返回 null 表示已开始启动；否则返回给用户看的错误提示。 */
    fun startTunnel(): String? {
        val snapshot = settings.value
        when {
            snapshot.port !in 1024..65535 -> return "本地端口需要在 1024 ~ 65535 之间"
            snapshot.sshHost.isBlank() -> return "请填写 Pinggy SSH 服务器地址"
            snapshot.sshPort !in 1..65535 -> return "SSH 端口不合法"
        }
        val source = ContentSourceFactory.build(app, snapshot).getOrElse { error ->
            return error.message ?: "内容来源无效"
        }
        container.controller.start(snapshot.toTunnelConfig(), source)
        return null
    }

    fun stopTunnel() = container.controller.stop(byUser = true)

    // ---------------- 地址操作 ----------------
    fun copyPublicUrl(): Boolean {
        val url = tunnel.value.publicUrl ?: return false
        ShareUtil.copy(app, url, "公网地址")
        return true
    }

    fun copyLocalUrl(): Boolean {
        val url = tunnel.value.localUrl ?: return false
        ShareUtil.copy(app, url, "本地地址")
        return true
    }

    fun sharePublicUrl(): Boolean {
        val url = tunnel.value.publicUrl ?: return false
        ShareUtil.share(app, "我用「${AppInfo.APP_TITLE}」把手机上的网页发布到公网了：$url", "分享公网地址")
        return true
    }

    fun openPublicUrl(): Boolean {
        val url = tunnel.value.publicUrl ?: return false
        ShareUtil.open(app, url)
        return true
    }

    // ---------------- 日志 ----------------
    fun clearLogs() = container.logs.clear()

    fun copyLogs(): Boolean = runCatching {
        ShareUtil.copy(app, container.logs.snapshotText(), "网页隧道日志")
        true
    }.getOrDefault(false)

    // ---------------- 关于 ----------------
    /** 点击复制 QQ 群号 */
    fun copyQqGroup(): Boolean = runCatching {
        ShareUtil.copy(app, AppInfo.QQ_GROUP, "QQ 群号")
        true
    }.getOrDefault(false)
}
