package com.gnuning.webtunnel.core

/** 内容来源模式：本地文件 / 文件夹 / 直接写 HTML / 反向代理（转发） */
enum class ServerMode(val title: String, val subtitle: String) {
    HTML("HTML 代码", "直接编写或粘贴网页源码"),
    FILE("本地文件", "选择手机里的单个 HTML 文件"),
    FOLDER("本地文件夹", "整个目录一起公开（含 CSS/JS/图片）"),
    URL("转发 URL", "反向代理到已运行的本地或远程服务");

    companion object {
        fun of(name: String?): ServerMode = entries.firstOrNull { it.name == name } ?: HTML
    }
}

/** 主题模式（默认浅色：整体以白色为主）。 */
enum class ThemeMode(val title: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun of(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

/** 隧道运行状态。 */
enum class TunnelStatus(val title: String) {
    IDLE("未连接"),
    STARTING("启动本地服务"),
    CONNECTING("建立隧道"),
    CONNECTED("已连接"),
    STOPPING("正在停止"),
    ERROR("启动失败");

    /** 正在忙（用于转圈动画） */
    val busy: Boolean get() = this == STARTING || this == CONNECTING || this == STOPPING

    /** 需要保持前台服务存活 */
    val active: Boolean get() = this == STARTING || this == CONNECTING || this == CONNECTED
}

/** 运行时状态（UI 观察的唯一数据源）。 */
data class TunnelState(
    val status: TunnelStatus = TunnelStatus.IDLE,
    val localUrl: String? = null,
    val publicUrls: List<String> = emptyList(),
    val startedAt: Long = 0L,
    val error: String? = null,
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val totalConnections: Long = 0L,
    val activeConnections: Long = 0L,
    val remoteForward: String? = null,
) {
    /** 主地址（Pinggy 免费版会同时给出两个域名，第一个作为主地址） */
    val publicUrl: String? get() = publicUrls.firstOrNull()
}

/** 启动隧道需要的参数（从设置里换算而来）。 */
data class TunnelConfig(
    val localPort: Int,
    val sshHost: String,
    val sshPort: Int,
    val token: String,
    val subdomain: String,
    val logEveryRequest: Boolean,
    val connectTimeoutMs: Long = 45_000L,
)

fun AppSettings.toTunnelConfig(): TunnelConfig = TunnelConfig(
    localPort = port,
    sshHost = sshHost.trim().ifBlank { "a.pinggy.io" },
    sshPort = sshPort,
    token = token.trim(),
    subdomain = subdomain.trim(),
    logEveryRequest = logEveryRequest,
)
