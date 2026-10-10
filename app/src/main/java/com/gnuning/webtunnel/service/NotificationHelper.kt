package com.gnuning.webtunnel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gnuning.webtunnel.MainActivity
import com.gnuning.webtunnel.R
import com.gnuning.webtunnel.core.AppInfo
import com.gnuning.webtunnel.core.TunnelState
import com.gnuning.webtunnel.core.TunnelStatus

/** 通知渠道 + 常驻通知（隧道状态）。 */
object NotificationHelper {

    const val CHANNEL_ID = "tunnel_status"
    const val NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "隧道状态", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示本地服务与 Pinggy 隧道的运行状态"
            setShowBadge(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun buildStatus(context: Context, state: TunnelState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            pendingIntentFlags(),
        )
        val stopIntent = PendingIntent.getService(
            context,
            2,
            Intent(context, TunnelService::class.java).setAction(TunnelService.ACTION_STOP),
            pendingIntentFlags(),
        )

        val title = when (state.status) {
            TunnelStatus.IDLE -> "网页隧道已停止"
            TunnelStatus.STARTING -> "正在启动本地服务…"
            TunnelStatus.CONNECTING -> "正在建立 Pinggy 隧道…"
            TunnelStatus.CONNECTED -> "已连接 · ${state.publicUrl ?: ""}"
            TunnelStatus.STOPPING -> "正在停止…"
            TunnelStatus.ERROR -> "启动失败 · ${state.error ?: ""}"
        }
        val text = when {
            state.status == TunnelStatus.CONNECTED ->
                "本地 ${state.localUrl ?: "-"} · 点击返回应用查看/复制公网地址"
            state.status == TunnelStatus.ERROR -> "点击返回应用查看日志"
            else -> "${AppInfo.APP_TITLE} · 作者 ${AppInfo.AUTHOR}"
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$text"))
            .setContentIntent(contentIntent)
            .addAction(0, "停止隧道", stopIntent)
            .setOngoing(state.status.active)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
