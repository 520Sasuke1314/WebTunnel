package com.gnuning.webtunnel.tunnel

import com.gnuning.webtunnel.core.LogRepository
import com.gnuning.webtunnel.core.TunnelConfig
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Collections
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TunnelException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Pinggy 隧道客户端（SSH 反向端口转发）。
 *
 * 原理与命令行完全一致：
 * ```
 * ssh -p 443 -R0:localhost:8080 a.pinggy.io        # 免费版：随机公网地址，60 分钟有效
 * ssh -p 443 -R0:localhost:8080 <token>@a.pinggy.io # Pro：用访问令牌当 SSH 用户名
 * ```
 * 这里用 JSch 实现：
 *  1. 连接 Pinggy 的 SSH 服务器（免密，随便给个空密码）；
 *  2. `setPortForwardingR(0, "localhost", port)` 请求远程动态端口（等价 `-R0:localhost:port`）；
 *  3. 打开一个空命令的 exec 通道，Pinggy 会把公网地址和实时流量统计打印到它的标准输出，
 *     逐行解析即可拿到 `https://xxx.free.pinggy.net` 这类地址，以及 `RB/SB/TC/AC` 统计。
 *
 * 实测行为（2026-10，a.pinggy.io:443）：
 * ```
 * Allocated port 3 for remote forward to localhost:8080
 * You are not authenticated.
 * Your tunnel will expire in 60 minutes. ...
 * https://xxxx-...run.pinggy-free.link
 * https://xxxx-....free.pinggy.net
 * RB: 0, SB: 0, TC: 0, AC: 0     ← 周期性推送的流量统计
 * ```
 */
class PinggyTunnel(private val log: LogRepository) {

    /** 回调（会在读取线程 / 调用线程上触发） */
    interface Listener {
        /** 拿到（或更新）公网地址列表，第一个为主地址 */
        fun onPublicUrls(urls: List<String>) {}

        /** 流量统计：rx=下行字节，tx=上行字节，total=累计连接数，active=当前连接数 */
        fun onStats(rx: Long, tx: Long, total: Long, active: Long) {}

        /** 远程端口转发信息，如 `3:localhost:8080` */
        fun onRemoteForward(info: String) {}

        /** 隧道断开（非用户主动停止） */
        fun onClosed(reason: String?) {}
    }

    private var session: Session? = null
    private var channel: ChannelExec? = null
    private var reader: Thread? = null

    private val stopped = AtomicBoolean(false)
    private val urlLatch = CountDownLatch(1)
    private val foundUrls: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    @Volatile
    var allocatedRemoteForward: String? = null
        private set

    /**
     * 建立隧道（阻塞，直到拿到公网地址或超时）。成功返回公网地址列表。
     */
    fun connect(config: TunnelConfig, localPort: Int, listener: Listener): List<String> {
        val username = config.token.trim().ifEmpty { DEFAULT_USERNAME }
        val sshSession = JSch().getSession(username, config.sshHost, config.sshPort)

        val props = Properties().apply {
            this["StrictHostKeyChecking"] = "no"
            // Pinggy 不需要密钥：走 password 方式并给一个空密码即可通过
            this["PreferredAuthentications"] = "password,keyboard-interactive"
        }
        sshSession.setConfig(props)
        sshSession.setPassword("")
        sshSession.setServerAliveInterval(20_000)
        sshSession.setServerAliveCountMax(3)
        session = sshSession

        log.i("正在连接 Pinggy：${username}@${config.sshHost}:${config.sshPort}")
        try {
            sshSession.connect(config.connectTimeoutMs.toInt())
        } catch (t: Throwable) {
            disconnectQuietly()
            throw TunnelException(
                "无法连接 ${config.sshHost}:${config.sshPort}（${t.message}）。" +
                    "请检查网络；若 443 不通可在设置里改用 22 端口。",
                t,
            )
        }
        log.s("SSH 已连接，正在把本地端口 $localPort 映射到公网…")

        requestRemoteForward(sshSession, config, localPort)

        sshSession.getPortForwardingR()?.firstOrNull()?.let { forward ->
            allocatedRemoteForward = forward
            listener.onRemoteForward(forward)
        }

        // Pinggy 通过这条 exec 通道输出公网地址与统计信息（必须保持打开）
        val execChannel = sshSession.openChannel("exec") as ChannelExec
        execChannel.setCommand("")
        execChannel.connect(10_000)
        channel = execChannel
        startReader(execChannel, listener)

        // 等第一个公网地址
        val ok = urlLatch.await(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
        if (!ok) {
            if (stopped.get()) throw TunnelException("已取消")
            disconnectQuietly()
            throw TunnelException(
                "隧道已建立，但 ${config.connectTimeoutMs / 1000} 秒内没有收到 Pinggy 分配的公网地址。" +
                    "可尝试：① 更换 SSH 端口（443 / 22）；② 在设置里填写访问令牌；③ 稍后重试。",
            )
        }
        return foundUrls.toList()
    }

    /** 请求远程端口转发（等价命令行 `-R0:localhost:port`）。 */
    private fun requestRemoteForward(sshSession: Session, config: TunnelConfig, localPort: Int) {
        if (config.subdomain.isNotBlank()) {
            // 付费套餐支持固定子域名：以 bind_address 形式带上（`-R <子域名>:0:localhost:port`）
            val ok = runCatching {
                sshSession.setPortForwardingR(config.subdomain, 0, "localhost", localPort)
                true
            }.getOrElse { t ->
                log.w("自定义子域名「${config.subdomain}」请求失败（${t.message}），改用随机域名。")
                false
            }
            if (ok) {
                log.i("已按自定义子域名「${config.subdomain}」请求隧道（需 Pinggy 付费套餐支持）")
                return
            }
        }
        try {
            sshSession.setPortForwardingR(0, "localhost", localPort)
        } catch (t: Throwable) {
            disconnectQuietly()
            throw TunnelException("请求远程端口转发失败：${t.message}", t)
        }
    }

    /** 持续读取 Pinggy 输出：解析公网地址、流量统计，并感知连接断开。 */
    private fun startReader(execChannel: ChannelExec, listener: Listener) {
        val thread = Thread({
            try {
                BufferedReader(InputStreamReader(execChannel.inputStream, Charsets.UTF_8)).use { reader ->
                    while (!stopped.get()) {
                        val line = reader.readLine() ?: break
                        handleLine(line, listener)
                    }
                }
            } catch (t: Throwable) {
                if (!stopped.get()) log.d("隧道输出读取结束：${t.message}")
            } finally {
                if (!stopped.get()) {
                    listener.onClosed("隧道已断开（Pinggy 免费隧道单次最长 60 分钟，到期后需重新启动）")
                }
            }
        }, "pinggy-output-reader")
        thread.isDaemon = true
        thread.start()
        reader = thread
    }

    private fun handleLine(rawLine: String, listener: Listener) {
        val line = rawLine.trim()
        if (line.isEmpty()) return

        // 1) 流量统计：RB: 123, SB: 456, TC: 7, AC: 1
        STATS_REGEX.find(line)?.let { match ->
            listener.onStats(
                rx = match.groupValues[1].toLongOrNull() ?: 0L,
                tx = match.groupValues[2].toLongOrNull() ?: 0L,
                total = match.groupValues[3].toLongOrNull() ?: 0L,
                active = match.groupValues[4].toLongOrNull() ?: 0L,
            )
            return
        }

        // 2) 公网地址
        var foundNew = false
        URL_REGEX.findAll(line).forEach { match ->
            val url = match.value.trimEnd('.', ',')
            if (!isTunnelUrl(url)) return@forEach
            if (!foundUrls.contains(url)) {
                foundUrls.add(url)
                foundNew = true
            }
        }
        if (foundNew) {
            log.s("Pinggy 已分配公网地址：${foundUrls.joinToString("  ")}")
            listener.onPublicUrls(foundUrls.toList())
            urlLatch.countDown()
            return
        }

        // 3) 其他提示信息（免费版 60 分钟提醒、过期通知等）
        when {
            line.contains("expire", ignoreCase = true) -> log.w("Pinggy：$line")
            line.contains("error", ignoreCase = true) || line.contains("fail", ignoreCase = true) -> log.w("Pinggy：$line")
            else -> log.i("Pinggy：$line")
        }
    }

    private fun isTunnelUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("dashboard.") || lower.contains("www.") || lower.contains("/docs")) return false
        return TUNNEL_HOST_REGEX.containsMatchIn(lower)
    }

    /** 用户主动停止 / 释放资源。 */
    fun disconnect() {
        stopped.set(true)
        urlLatch.countDown()
        disconnectQuietly()
        reader = null
    }

    private fun disconnectQuietly() {
        runCatching { channel?.disconnect() }
        runCatching { session?.disconnect() }
        channel = null
        session = null
    }

    companion object {

        /** 免费版会忽略用户名；这里给个占位值，Pro 版则用访问令牌作为用户名。 */
        private const val DEFAULT_USERNAME = "free"

        /** Pinggy 分配的公网地址形如 xxx.free.pinggy.net / xxx.run.pinggy-free.link / xxx.a.free.pinggy.link */
        private val TUNNEL_HOST_REGEX = Regex("""(?:pinggy-free\.link|pinggy\.net|pinggy\.link|pinggy\.online|pinggy\.io)$""")

        private val URL_REGEX = Regex("""https?://[a-zA-Z0-9][a-zA-Z0-9.\-]*\.[a-zA-Z]{2,}""")

        private val STATS_REGEX = Regex("""RB:\s*(\d+),\s*SB:\s*(\d+),\s*TC:\s*(\d+),\s*AC:\s*(\d+)""")

        init {
            // 只启用 Android/Conscrypt 原生就支持的算法，避免额外引入 BouncyCastle：
            //  - kex：优先 curve25519，若不支持 JSch 会自动剔除，回落到 ECDH/DH group14
            //  - 主机密钥：ecdsa 优先（服务器同时提供 ssh-ed25519，这里避免依赖 EdDSA）
            //  - 加密/MAC：AES-CTR / AES-GCM + hmac-sha2
            JSch.setConfig(
                "kex",
                "curve25519-sha256,curve25519-sha256@libssh.org,ecdh-sha2-nistp256,ecdh-sha2-nistp384," +
                    "ecdh-sha2-nistp521,diffie-hellman-group14-sha256,diffie-hellman-group-exchange-sha256",
            )
            JSch.setConfig(
                "server_host_key",
                "ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,rsa-sha2-512,rsa-sha2-256,ssh-rsa,ssh-ed25519",
            )
            JSch.setConfig("cipher.s2c", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com")
            JSch.setConfig("cipher.c2s", "aes128-ctr,aes192-ctr,aes256-ctr,aes128-gcm@openssh.com,aes256-gcm@openssh.com")
            JSch.setConfig("mac.s2c", "hmac-sha2-256,hmac-sha2-512,hmac-sha1")
            JSch.setConfig("mac.c2s", "hmac-sha2-256,hmac-sha2-512,hmac-sha1")
        }
    }
}
