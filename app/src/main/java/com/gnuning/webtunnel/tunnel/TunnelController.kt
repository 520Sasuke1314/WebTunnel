package com.gnuning.webtunnel.tunnel

import android.content.Context
import com.gnuning.webtunnel.core.AppSettings
import com.gnuning.webtunnel.core.ContentSource
import com.gnuning.webtunnel.core.ContentSourceFactory
import com.gnuning.webtunnel.core.LogRepository
import com.gnuning.webtunnel.core.SettingsRepository
import com.gnuning.webtunnel.core.TunnelConfig
import com.gnuning.webtunnel.core.TunnelState
import com.gnuning.webtunnel.core.TunnelStatus
import com.gnuning.webtunnel.core.toTunnelConfig
import com.gnuning.webtunnel.server.LocalWebServer
import com.gnuning.webtunnel.service.TunnelService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 隧道总控：把「本地 HTTP 服务」+「Pinggy SSH 隧道」+「前台服务」串起来，
 * 并把运行状态通过 [state] 暴露给 UI。
 *
 * 生命周期：
 * ```
 * start()  → STARTING（起本地服务）→ CONNECTING（连 Pinggy）→ CONNECTED（拿到公网地址）
 * stop()   → STOPPING → 释放端口/连接 → IDLE
 * 异常/超时 → ERROR（error 字段给用户看的提示）
 * ```
 */
class TunnelController(
    private val context: Context,
    private val settings: SettingsRepository,
    val logs: LogRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private var server: LocalWebServer? = null
    private var tunnel: PinggyTunnel? = null

    /** 是否还有真实资源占用（用于前台服务销毁时判断要不要清理） */
    val isEngineRunning: Boolean
        get() = server != null || tunnel != null

    /** 启动隧道（参数校验在 ViewModel 已完成） */
    fun start(config: TunnelConfig, source: ContentSource) {
        if (_state.value.status.active) {
            logs.w("隧道已在运行，忽略本次启动请求")
            return
        }
        _state.value = TunnelState(status = TunnelStatus.STARTING)
        logs.i("──── 启动隧道 ──── 来源：${source.label}，本地端口 ${config.localPort}")
        TunnelService.startForeground(context)

        scope.launch {
            try {
                // 1) 本地 HTTP 服务
                val localServer = LocalWebServer(context, logs, config.logEveryRequest)
                server = localServer
                val actualPort = localServer.start(config.localPort, source)
                _state.value = TunnelState(
                    status = TunnelStatus.CONNECTING,
                    localUrl = "http://127.0.0.1:$actualPort",
                    startedAt = System.currentTimeMillis(),
                )
                logs.s("本地 HTTP 服务已就绪：http://127.0.0.1:$actualPort（${source.label}）")

                // 2) Pinggy 隧道
                val pinggy = PinggyTunnel(logs)
                tunnel = pinggy
                val urls = pinggy.connect(config, actualPort, object : PinggyTunnel.Listener {
                    override fun onPublicUrls(urls: List<String>) {
                        _state.update { it.copy(publicUrls = urls) }
                    }

                    override fun onStats(rx: Long, tx: Long, total: Long, active: Long) {
                        _state.update {
                            it.copy(rxBytes = rx, txBytes = tx, totalConnections = total, activeConnections = active)
                        }
                    }

                    override fun onRemoteForward(info: String) {
                        _state.update { it.copy(remoteForward = info) }
                        logs.i("远程端口转发信息：$info")
                    }

                    override fun onClosed(reason: String?) {
                        // Pinggy 主动断开（例如免费版 60 分钟到期）
                        logs.w(reason ?: "隧道连接已断开")
                        teardown(notifyService = true)
                        _state.value = TunnelState(status = TunnelStatus.IDLE, error = reason)
                        settings.update { it.copy(wasRunning = false) }
                    }
                })

                // 3) 完成
                _state.update {
                    it.copy(status = TunnelStatus.CONNECTED, publicUrls = urls, startedAt = System.currentTimeMillis())
                }
                logs.s("隧道已连通，公网地址：${urls.firstOrNull() ?: "-"}")
                if (urls.size > 1) logs.i("备用地址：${urls.drop(1).joinToString("  ")}")
                settings.update {
                    it.copy(
                        lastPublicUrl = urls.firstOrNull() ?: it.lastPublicUrl,
                        wasRunning = true,
                        everStarted = true,
                    )
                }
            } catch (t: Throwable) {
                logs.e("启动失败：${t.message}")
                teardown(notifyService = true)
                settings.update { it.copy(wasRunning = false) }
                _state.value = TunnelState(status = TunnelStatus.ERROR, error = t.message ?: "未知错误")
            }
        }
    }

    /** 停止隧道（用户点击 / 通知栏按钮 / 服务被销毁时调用） */
    fun stop(byUser: Boolean = true) {
        if (!isEngineRunning && _state.value.status == TunnelStatus.IDLE) {
            TunnelService.stop(context)
            return
        }
        if (byUser) logs.i("正在停止隧道…")
        _state.update { it.copy(status = TunnelStatus.STOPPING) }
        scope.launch {
            teardown(notifyService = true)
            settings.update { it.copy(wasRunning = false) }
            _state.value = TunnelState(status = TunnelStatus.IDLE)
            if (byUser) logs.i("隧道已停止，端口与 SSH 连接均已释放")
        }
    }

    /**
     * 进程被系统回收后（前台服务 START_STICKY）尝试自动恢复上次的隧道。
     * 只有「上次确实在运行」且内容来源仍然可用时才会恢复。
     */
    fun restoreFromSettings() {
        val snapshot: AppSettings = settings.state.value
        if (!snapshot.wasRunning) return
        if (_state.value.status.active) return
        logs.i("检测到上次隧道处于运行状态，正在尝试自动恢复…")
        val source = ContentSourceFactory.build(context, snapshot).getOrElse { error ->
            logs.w("自动恢复失败：${error.message}")
            settings.update { it.copy(wasRunning = false) }
            return
        }
        start(snapshot.toTunnelConfig(), source)
    }

    /** 释放资源。幂等：重复调用安全。 */
    @Synchronized
    private fun teardown(notifyService: Boolean) {
        runCatching { tunnel?.disconnect() }
        tunnel = null
        runCatching { server?.shutdown() }
        server = null
        if (notifyService) TunnelService.stop(context)
    }
}
