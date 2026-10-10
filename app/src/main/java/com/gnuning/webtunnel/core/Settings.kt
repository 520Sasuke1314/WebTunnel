package com.gnuning.webtunnel.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全部可持久化设置。
 *
 * 说明：这里用 SharedPreferences 而不是 DataStore —— 字段少、读写全是同步的，
 * 配上 [SettingsRepository.state] 这个 StateFlow 后，UI / 服务 / 控制器拿到的都是同一份内存状态，
 * 既省掉一个依赖，也避免异步读写带来的时序问题。
 */
data class AppSettings(
    // —— 本地服务 ——
    val port: Int = 8080,
    val logEveryRequest: Boolean = true,

    // —— Pinggy 隧道 ——
    val sshHost: String = "a.pinggy.io",
    val sshPort: Int = 443,
    val token: String = "",
    val subdomain: String = "",

    // —— 外观 / 行为 ——
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val keepScreenOn: Boolean = false,
    val keepAwake: Boolean = true,
    val autoCopy: Boolean = true,

    // —— 内容来源 ——
    val mode: ServerMode = ServerMode.HTML,
    val fileUri: String = "",
    val fileName: String = "",
    val folderUri: String = "",
    val folderName: String = "",
    val inlineHtml: String = "",
    val targetUrl: String = "",

    // —— 记录类 ——
    val lastPublicUrl: String = "",
    val wasRunning: Boolean = false,
    val everStarted: Boolean = false,
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    /**
     * 读取-修改-写入。UI 里这样用：
     * ```
     * vm.updateSettings { it.copy(port = 8080) }
     * ```
     * 返回写入后的最新设置。
     */
    @Synchronized
    fun update(block: (AppSettings) -> AppSettings): AppSettings {
        val next = block(_state.value)
        save(next)
        _state.value = next
        return next
    }

    private fun load(): AppSettings = AppSettings(
        port = prefs.getInt(K_PORT, 8080),
        logEveryRequest = prefs.getBoolean(K_LOG_REQUEST, true),
        sshHost = prefs.getString(K_SSH_HOST, "a.pinggy.io") ?: "a.pinggy.io",
        sshPort = prefs.getInt(K_SSH_PORT, 443),
        token = prefs.getString(K_TOKEN, "") ?: "",
        subdomain = prefs.getString(K_SUBDOMAIN, "") ?: "",
        themeMode = ThemeMode.of(prefs.getString(K_THEME, ThemeMode.LIGHT.name)),
        keepScreenOn = prefs.getBoolean(K_KEEP_SCREEN, false),
        keepAwake = prefs.getBoolean(K_KEEP_AWAKE, true),
        autoCopy = prefs.getBoolean(K_AUTO_COPY, true),
        mode = ServerMode.of(prefs.getString(K_MODE, ServerMode.HTML.name)),
        fileUri = prefs.getString(K_FILE_URI, "") ?: "",
        fileName = prefs.getString(K_FILE_NAME, "") ?: "",
        folderUri = prefs.getString(K_FOLDER_URI, "") ?: "",
        folderName = prefs.getString(K_FOLDER_NAME, "") ?: "",
        inlineHtml = prefs.getString(K_INLINE_HTML, "") ?: "",
        targetUrl = prefs.getString(K_TARGET_URL, "") ?: "",
        lastPublicUrl = prefs.getString(K_LAST_URL, "") ?: "",
        wasRunning = prefs.getBoolean(K_WAS_RUNNING, false),
        everStarted = prefs.getBoolean(K_EVER_STARTED, false),
    )

    private fun save(s: AppSettings) {
        prefs.edit()
            .putInt(K_PORT, s.port)
            .putBoolean(K_LOG_REQUEST, s.logEveryRequest)
            .putString(K_SSH_HOST, s.sshHost)
            .putInt(K_SSH_PORT, s.sshPort)
            .putString(K_TOKEN, s.token)
            .putString(K_SUBDOMAIN, s.subdomain)
            .putString(K_THEME, s.themeMode.name)
            .putBoolean(K_KEEP_SCREEN, s.keepScreenOn)
            .putBoolean(K_KEEP_AWAKE, s.keepAwake)
            .putBoolean(K_AUTO_COPY, s.autoCopy)
            .putString(K_MODE, s.mode.name)
            .putString(K_FILE_URI, s.fileUri)
            .putString(K_FILE_NAME, s.fileName)
            .putString(K_FOLDER_URI, s.folderUri)
            .putString(K_FOLDER_NAME, s.folderName)
            .putString(K_INLINE_HTML, s.inlineHtml)
            .putString(K_TARGET_URL, s.targetUrl)
            .putString(K_LAST_URL, s.lastPublicUrl)
            .putBoolean(K_WAS_RUNNING, s.wasRunning)
            .putBoolean(K_EVER_STARTED, s.everStarted)
            .apply()
    }

    private companion object {
        const val PREF_NAME = "webtunnel_settings"
        const val K_PORT = "port"
        const val K_LOG_REQUEST = "log_every_request"
        const val K_SSH_HOST = "ssh_host"
        const val K_SSH_PORT = "ssh_port"
        const val K_TOKEN = "token"
        const val K_SUBDOMAIN = "subdomain"
        const val K_THEME = "theme_mode"
        const val K_KEEP_SCREEN = "keep_screen_on"
        const val K_KEEP_AWAKE = "keep_awake"
        const val K_AUTO_COPY = "auto_copy"
        const val K_MODE = "server_mode"
        const val K_FILE_URI = "file_uri"
        const val K_FILE_NAME = "file_name"
        const val K_FOLDER_URI = "folder_uri"
        const val K_FOLDER_NAME = "folder_name"
        const val K_INLINE_HTML = "inline_html"
        const val K_TARGET_URL = "target_url"
        const val K_LAST_URL = "last_public_url"
        const val K_WAS_RUNNING = "was_running"
        const val K_EVER_STARTED = "ever_started"
    }
}
