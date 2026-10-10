package com.gnuning.webtunnel.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gnuning.webtunnel.WebTunnelApp
import com.gnuning.webtunnel.core.TunnelState
import com.gnuning.webtunnel.core.TunnelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 前台服务：让「本地 HTTP 服务 + SSH 隧道」在切后台 / 锁屏 / 划掉最近任务后继续存活。
 *
 * Android 14 要求声明前台服务类型，这里使用 `specialUse`
 * （见 AndroidManifest 里的 android:foregroundServiceType 与 PROPERTY_SPECIAL_USE_FGS_SUBTYPE）。
 * 服务本身不持有业务逻辑，只做三件事：显示常驻通知、监听停止指令、进程被回收后尝试恢复隧道。
 */
class TunnelService : Service() {

    private val container get() = (application as WebTunnelApp).container
    private val controller get() = container.controller
    private val logs get() = container.logs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var sawActiveState = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                logs.i("收到通知栏「停止隧道」指令")
                controller.stop(byUser = true)
                return START_NOT_STICKY
            }

            else -> {
                startForegroundSafely()
                observeState()
                if (intent == null) {
                    // 进程被系统回收后由 START_STICKY 拉起
                    logs.i("前台服务被系统重启，检查是否需要恢复隧道")
                    controller.restoreFromSettings()
                    scope.launch {
                        delay(1_200)
                        if (controller.state.value.status == TunnelStatus.IDLE && !controller.isEngineRunning) {
                            logs.i("无需恢复隧道，前台服务退出")
                            stopSelfSafely()
                        }
                    }
                }
            }
        }
        return START_STICKY
    }

    /** 跟随隧道状态刷新常驻通知，并在隧道结束后自动退出服务。 */
    private fun observeState() {
        if (observeJob != null) return
        observeJob = scope.launch {
            controller.state.collect { state ->
                if (state.status.active) sawActiveState = true
                updateNotification(state)
                if (sawActiveState && state.status == TunnelStatus.IDLE && !controller.isEngineRunning) {
                    stopSelfSafely()
                }
            }
        }
    }

    private fun updateNotification(state: TunnelState) {
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(NotificationHelper.NOTIFICATION_ID, NotificationHelper.buildStatus(this, state))
        }
    }

    private fun startForegroundSafely() {
        val notification = NotificationHelper.buildStatus(this, controller.state.value)
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID, notification, serviceType)
        }.onFailure { logs.e("前台服务启动失败：${it.message}") }
        acquireWakeLock()
    }

    /** 锁屏后保持 CPU 可用（Pinggy 免费隧道 60 分钟，这里按 70 分钟自动释放）。 */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        if (!container.settings.state.value.keepAwake) {
            logs.i("已关闭「锁屏保持运行」，不加唤醒锁")
            return
        }
        runCatching {
            val manager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebTunnel:tunnel").apply {
                setReferenceCounted(false)
                acquire(70 * 60 * 1000L)
            }
            logs.d("已获取唤醒锁，锁屏后隧道继续运行")
        }
    }

    private fun stopSelfSafely() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        observeJob?.cancel()
        observeJob = null
        scope.cancel()
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        if (controller.isEngineRunning) {
            logs.w("前台服务被销毁，正在清理隧道资源")
            controller.stop(byUser = false)
        }
        super.onDestroy()
    }

    companion object {

        const val ACTION_START = "com.gnuning.webtunnel.action.START_TUNNEL"
        const val ACTION_STOP = "com.gnuning.webtunnel.action.STOP_TUNNEL"

        /** 拉起前台服务。必须在应用处于前台时调用（Android 12+ 限制后台启动前台服务）。 */
        fun startForeground(context: Context) {
            val intent = Intent(context, TunnelService::class.java).setAction(ACTION_START)
            val appLogs = (context.applicationContext as? WebTunnelApp)?.container?.logs
            try {
                ContextCompat.startForegroundService(context, intent)
                appLogs?.d("已请求启动前台服务，隧道可在后台持续运行")
            } catch (t: Throwable) {
                appLogs?.w("前台服务启动失败（${t.message}），退化为普通服务启动")
                runCatching { context.startService(intent) }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, TunnelService::class.java)) }
        }
    }
}
