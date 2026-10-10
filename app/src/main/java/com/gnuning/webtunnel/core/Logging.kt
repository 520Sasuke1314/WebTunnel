package com.gnuning.webtunnel.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** 日志级别。 */
enum class LogLevel(val title: String, val tag: String) {
    DEBUG("调试", "D"),
    INFO("信息", "I"),
    SUCCESS("成功", "S"),
    WARN("警告", "W"),
    ERROR("错误", "E");
}

data class LogEntry(
    val id: Long,
    val time: Long,
    val level: LogLevel,
    val message: String,
)

private val LOG_TIME_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
}

fun formatLogTime(time: Long): String = LOG_TIME_FORMAT.get().format(Date(time))

/**
 * 全局运行日志：内存环形缓冲 + StateFlow 暴露给 UI，同时转发到 logcat。
 * 使用说明：任何模块（HTTP 服务、SSH 隧道、前台服务）都往这里写，
 * 「日志」页会自动实时刷新。
 */
class LogRepository(private val maxEntries: Int = 600) {

    private val sequence = AtomicLong(0)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(level: LogLevel, message: String) {
        val entry = LogEntry(sequence.incrementAndGet(), System.currentTimeMillis(), level, message)
        _entries.update { old ->
            if (old.size >= maxEntries) old.subList(old.size - maxEntries + 1, old.size) + entry else old + entry
        }
        Log.println(priorityOf(level), "WebTunnel", message)
    }

    fun d(message: String) = log(LogLevel.DEBUG, message)
    fun i(message: String) = log(LogLevel.INFO, message)
    fun s(message: String) = log(LogLevel.SUCCESS, message)
    fun w(message: String) = log(LogLevel.WARN, message)
    fun e(message: String) = log(LogLevel.ERROR, message)

    fun clear() {
        _entries.value = emptyList()
    }

    /** 导出为纯文本，用于「复制全部日志」。 */
    fun snapshotText(): String = _entries.value.joinToString(separator = "\n") {
        "${formatLogTime(it.time)} [${it.level.tag}] ${it.message}"
    }

    private fun priorityOf(level: LogLevel): Int = when (level) {
        LogLevel.DEBUG -> Log.DEBUG
        LogLevel.INFO -> Log.INFO
        LogLevel.SUCCESS -> Log.INFO
        LogLevel.WARN -> Log.WARN
        LogLevel.ERROR -> Log.ERROR
    }
}
