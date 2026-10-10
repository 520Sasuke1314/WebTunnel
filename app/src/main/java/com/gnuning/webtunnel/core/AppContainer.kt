package com.gnuning.webtunnel.core

import android.content.Context
import com.gnuning.webtunnel.tunnel.TunnelController

/**
 * 手写依赖容器：Application 里创建一次，全局共享。
 * 二次开发时新增的「全局单例」都可以挂在这里。
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val logs = LogRepository()
    val settings = SettingsRepository(appContext)
    val controller = TunnelController(appContext, settings, logs)
}
