package com.gnuning.webtunnel

import android.app.Application
import com.gnuning.webtunnel.core.AppContainer
import com.gnuning.webtunnel.core.AppInfo

/**
 * 应用入口。
 *
 * 这里只做一件事：创建 [AppContainer]（一个手写的极简依赖容器）。
 * 之所以不用 Hilt / KSP：小项目引入注解处理器会让编译变慢、版本耦合变多，
 * 二开时只要把新依赖挂到 [AppContainer] 上，然后从 `WebTunnelApp.instance.container` 取即可。
 */
class WebTunnelApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
        container.logs.i("网页隧道 v${AppInfo.VERSION_NAME} 已启动 · 作者 ${AppInfo.AUTHOR} · QQ 群 ${AppInfo.QQ_GROUP}")
    }

    companion object {
        /** 全局单例，UI / 前台服务 / 工具类统一从这里取容器。 */
        lateinit var instance: WebTunnelApp
            private set
    }
}
