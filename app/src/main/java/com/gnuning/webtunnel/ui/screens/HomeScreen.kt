package com.gnuning.webtunnel.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnuning.webtunnel.core.AppInfo
import com.gnuning.webtunnel.core.AppSettings
import com.gnuning.webtunnel.core.ServerMode
import com.gnuning.webtunnel.core.TunnelState
import com.gnuning.webtunnel.core.TunnelStatus
import com.gnuning.webtunnel.ui.AppViewModel
import com.gnuning.webtunnel.ui.components.AppTextField
import com.gnuning.webtunnel.ui.components.ChipRow
import com.gnuning.webtunnel.ui.components.InfoBanner
import com.gnuning.webtunnel.ui.components.PrimaryActionButton
import com.gnuning.webtunnel.ui.components.QrCodeView
import com.gnuning.webtunnel.ui.components.SectionHeader
import com.gnuning.webtunnel.ui.components.SettingRow
import com.gnuning.webtunnel.ui.components.SoftCard
import com.gnuning.webtunnel.ui.components.StatusDot
import com.gnuning.webtunnel.ui.components.UrlActionButton
import com.gnuning.webtunnel.ui.components.snack
import com.gnuning.webtunnel.ui.components.statusColors
import com.gnuning.webtunnel.ui.theme.ErrorRed
import com.gnuning.webtunnel.ui.theme.InfoBlue
import com.gnuning.webtunnel.ui.theme.MonoFamily
import com.gnuning.webtunnel.ui.theme.SuccessGreen
import com.gnuning.webtunnel.ui.theme.WarningAmber
import com.gnuning.webtunnel.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/**
 * 主页：状态卡 → 内容来源 → 快速参数 → 开始/停止。
 * 设计目标是「三步之内」：选内容 → 点按钮 → 复制公网地址。
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tunnel by vm.tunnel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ——— 选择文件 / 文件夹（SAF，不需要存储权限） ———
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.setFileUri(uri)
            scope.snack(snackbar, "已选择文件，点击「启动隧道」即可发布")
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            vm.setFolderUri(uri)
            scope.snack(snackbar, "已选择文件夹（目录内的 CSS/JS/图片都会被发布）")
        }
    }

    // ——— 通知权限（Android 13+ 前台服务通知需要） ———
    var pendingStart by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.snack(snackbar, "未授予通知权限：隧道仍可运行，但看不到状态通知")
        if (pendingStart) {
            pendingStart = false
            vm.startTunnel()?.let { message -> scope.snack(snackbar, message) }
        }
    }
    fun requestStart() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingStart = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.startTunnel()?.let { message -> scope.snack(snackbar, message) }
        }
    }

    // ——— 运行时长（每秒刷新） ———
    val now by produceState(initialValue = 0L, key1 = tunnel.startedAt) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    var showQr by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        HomeHeader(onOpenSettings = onOpenSettings)
        Spacer(Modifier.height(16.dp))

        StatusCard(
            tunnel = tunnel,
            now = now,
            vm = vm,
            scope = scope,
            snackbar = snackbar,
            onToggleQr = { showQr = !showQr },
        )

        val url = tunnel.publicUrl
        AnimatedVisibility(visible = showQr && url != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(14.dp))
                if (url != null) QrCard(url)
            }
        }

        Spacer(Modifier.height(14.dp))
        SourceCard(
            settings = settings,
            vm = vm,
            scope = scope,
            snackbar = snackbar,
            onPickFile = { filePicker.launch(arrayOf("text/html", "text/plain", "application/xhtml+xml", "*/*")) },
            onPickFolder = { folderPicker.launch(null) },
        )

        Spacer(Modifier.height(14.dp))
        QuickParamsCard(settings = settings, vm = vm, onOpenSettings = onOpenSettings)

        Spacer(Modifier.height(18.dp))
        PrimaryActionButton(
            text = when (tunnel.status) {
                TunnelStatus.STARTING, TunnelStatus.CONNECTING -> "取消启动"
                TunnelStatus.CONNECTED -> "停止隧道"
                TunnelStatus.STOPPING -> "正在停止…"
                else -> "启动隧道"
            },
            icon = if (tunnel.status.active) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            containerColor = if (tunnel.status.active) ErrorRed else MaterialTheme.colorScheme.primary,
            enabled = tunnel.status != TunnelStatus.STOPPING,
            loading = tunnel.status == TunnelStatus.STOPPING,
            onClick = { if (tunnel.status.active) vm.stopTunnel() else requestStart() },
        )

        Spacer(Modifier.height(14.dp))
        FooterCard()
        Spacer(Modifier.height(26.dp))
    }
}

// ======================================================================
// 顶部标题
// ======================================================================
@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(AppInfo.APP_TITLE, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Text(
                AppInfo.APP_SUBTITLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.size(42.dp).clickable { onOpenSettings() },
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ======================================================================
// 状态卡
// ======================================================================
@Composable
private fun StatusCard(
    tunnel: TunnelState,
    now: Long,
    vm: AppViewModel,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onToggleQr: () -> Unit,
) {
    val colors = statusColors(tunnel.status)
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color = colors.accent, pulsing = tunnel.status.busy, size = 11.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(tunnel.status.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    statusHint(tunnel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tunnel.status == TunnelStatus.CONNECTED) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(50)) {
                    Text(
                        "已运行 ${Formatters.duration(now - tunnel.startedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }

        // 已连接：公网地址 + 统计 + 本机地址
        AnimatedVisibility(
            visible = tunnel.status == TunnelStatus.CONNECTED && tunnel.publicUrl != null,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                val url = tunnel.publicUrl
                if (url != null) PublicUrlBlock(url, tunnel, vm, scope, snackbar, onToggleQr)
                Spacer(Modifier.height(14.dp))
                StatsRow(tunnel)
                Spacer(Modifier.height(12.dp))
                LocalUrlRow(tunnel, vm, scope, snackbar)
            }
        }

        // 启动失败
        AnimatedVisibility(visible = tunnel.status == TunnelStatus.ERROR, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(14.dp))
                InfoBanner(
                    icon = Icons.Rounded.ErrorOutline,
                    text = tunnel.error ?: "启动失败，请到「日志」页查看详细原因。",
                    accent = ErrorRed,
                )
            }
        }

        // 隧道被动断开（例如免费版 60 分钟到期）
        AnimatedVisibility(
            visible = tunnel.status == TunnelStatus.IDLE && !tunnel.error.isNullOrBlank(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                InfoBanner(icon = Icons.Rounded.Info, text = tunnel.error ?: "", accent = WarningAmber)
            }
        }
    }
}

private fun statusHint(tunnel: TunnelState): String = when (tunnel.status) {
    TunnelStatus.IDLE -> "选择内容后点击下方按钮即可发布"
    TunnelStatus.STARTING -> "正在启动本地 HTTP 服务…"
    TunnelStatus.CONNECTING -> "正在与 Pinggy 建立 SSH 反向隧道…"
    TunnelStatus.CONNECTED -> "外部设备可直接打开下方公网地址"
    TunnelStatus.STOPPING -> "正在释放端口与 SSH 连接…"
    TunnelStatus.ERROR -> "请查看下方提示，或到「日志」页定位问题"
}

@Composable
private fun PublicUrlBlock(
    url: String,
    tunnel: TunnelState,
    vm: AppViewModel,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onToggleQr: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("公网地址", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                Text(
                    "长按可选中",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(8.dp))
            SelectionContainer {
                Text(
                    url,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFamily),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            if (tunnel.publicUrls.size > 1) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "备用地址：${tunnel.publicUrls.drop(1).joinToString("   ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                UrlActionButton(Icons.Rounded.ContentCopy, "复制") {
                    if (vm.copyPublicUrl()) scope.snack(snackbar, "公网地址已复制") else scope.snack(snackbar, "还没有可用地址")
                }
                UrlActionButton(Icons.Rounded.Share, "分享") {
                    if (!vm.sharePublicUrl()) scope.snack(snackbar, "还没有可用地址")
                }
                UrlActionButton(Icons.AutoMirrored.Rounded.OpenInNew, "打开") {
                    if (!vm.openPublicUrl()) scope.snack(snackbar, "还没有可用地址")
                }
                UrlActionButton(Icons.Rounded.QrCode2, "二维码") { onToggleQr() }
            }
        }
    }
}

@Composable
private fun StatsRow(tunnel: TunnelState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCell(Modifier.weight(1f), Icons.Rounded.Speed, "下行", Formatters.bytes(tunnel.rxBytes))
        StatCell(Modifier.weight(1f), Icons.Rounded.Bolt, "上行", Formatters.bytes(tunnel.txBytes))
        StatCell(Modifier.weight(1f), Icons.AutoMirrored.Rounded.AltRoute, "请求数", tunnel.totalConnections.toString())
        StatCell(Modifier.weight(1f), Icons.Rounded.Wifi, "在线", tunnel.activeConnections.toString())
    }
}

@Composable
private fun StatCell(modifier: Modifier, icon: ImageVector, label: String, value: String) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Spacer(Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocalUrlRow(tunnel: TunnelState, vm: AppViewModel, scope: CoroutineScope, snackbar: SnackbarHostState) {
    val local = tunnel.localUrl ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.Lan,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "本机地址 $local",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "复制",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { if (vm.copyLocalUrl()) scope.snack(snackbar, "本机地址已复制") }
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun QrCard(url: String) {
    SoftCard {
        SectionHeader(Icons.Rounded.QrCode2, "扫码访问", "用另一台设备的相机 / 浏览器扫码即可打开")
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { QrCodeView(url, 176.dp) }
        Spacer(Modifier.height(10.dp))
        Text(
            url,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ======================================================================
// 内容来源
// ======================================================================
@Composable
private fun SourceCard(
    settings: AppSettings,
    vm: AppViewModel,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    onPickFile: () -> Unit,
    onPickFolder: () -> Unit,
) {
    SoftCard {
        SectionHeader(Icons.Rounded.Language, "内容来源", "选择要发布到公网的内容")
        Spacer(Modifier.height(14.dp))
        ModeSelector(settings.mode) { mode -> vm.setMode(mode) }
        Spacer(Modifier.height(16.dp))

        when (settings.mode) {
            ServerMode.FILE -> {
                OutlinedButton(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (settings.fileUri.isBlank()) "选择 HTML 文件" else "重新选择文件")
                }
                Spacer(Modifier.height(10.dp))
                if (settings.fileUri.isBlank()) {
                    InfoBanner(Icons.Rounded.Info, "还没有选择文件。选择后目录里其他资源不会一起发布，整站请用「本地文件夹」。", InfoBlue)
                } else {
                    InfoBanner(Icons.Rounded.CheckCircle, "已选择：${settings.fileName}", SuccessGreen)
                }
            }

            ServerMode.FOLDER -> {
                OutlinedButton(
                    onClick = onPickFolder,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (settings.folderUri.isBlank()) "选择文件夹" else "重新选择文件夹")
                }
                Spacer(Modifier.height(10.dp))
                if (settings.folderUri.isBlank()) {
                    InfoBanner(Icons.Rounded.Info, "推荐方式：选中网页所在目录，目录里的 CSS / JS / 图片都会被一起发布；含 index.html 时自动作为首页。", InfoBlue)
                } else {
                    InfoBanner(Icons.Rounded.CheckCircle, "已选择：${settings.folderName}", SuccessGreen)
                }
            }

            ServerMode.HTML -> {
                AppTextField(
                    value = settings.inlineHtml,
                    onValueChange = { text -> vm.updateSettings { it.copy(inlineHtml = text, mode = ServerMode.HTML) } },
                    label = "HTML 源码",
                    singleLine = false,
                    minLines = 6,
                    keyboardType = KeyboardType.Ascii,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { vm.loadDemoHtml(); scope.snack(snackbar, "已载入示例页面") }) { Text("载入示例页面") }
                    TextButton(onClick = { vm.updateSettings { it.copy(inlineHtml = "") } }) { Text("清空") }
                }
                if (settings.inlineHtml.isBlank()) {
                    InfoBanner(Icons.Rounded.Info, "内容为空时会自动使用内置示例页面，可直接启动查看效果。", InfoBlue)
                }
            }

            ServerMode.URL -> {
                AppTextField(
                    value = settings.targetUrl,
                    onValueChange = { text -> vm.updateSettings { it.copy(targetUrl = text, mode = ServerMode.URL) } },
                    label = "目标地址",
                    placeholder = "http://127.0.0.1:9000 或 https://example.com",
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(10.dp))
                InfoBanner(
                    Icons.Rounded.Link,
                    "反向代理：外部请求会转发到该地址，适合转发 app 内已运行的小服务、局域网服务或公网接口。" +
                        "注意：不支持 WebSocket，HTML 内的相对链接不会重写。",
                    InfoBlue,
                )
            }
        }
    }
}

/** 2×2 模式选择（自绘，避免 Material 实验性 API） */
@Composable
private fun ModeSelector(selected: ServerMode, onSelect: (ServerMode) -> Unit) {
    val modes = ServerMode.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.chunked(2).forEach { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowModes.forEach { mode ->
                    val active = mode == selected
                    Surface(
                        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(18.dp),
                        border = if (active) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
                        modifier = Modifier.weight(1f).clickable { onSelect(mode) },
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                            Text(mode.title, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                mode.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                                },
                            )
                        }
                    }
                }
                if (rowModes.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ======================================================================
// 快速参数 / 页脚
// ======================================================================
@Composable
private fun QuickParamsCard(settings: AppSettings, vm: AppViewModel, onOpenSettings: () -> Unit) {
    var portText by rememberSaveable { mutableStateOf(settings.port.toString()) }
    LaunchedEffect(settings.port) {
        if (portText.toIntOrNull() != settings.port) portText = settings.port.toString()
    }

    SoftCard {
        SectionHeader(
            icon = Icons.Rounded.Tune,
            title = "快速参数",
            subtitle = "端口与 SSH 服务器，更多选项在「设置」页",
            trailing = {
                Text(
                    "更多设置",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        AppTextField(
            value = portText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(5)
                portText = digits
                digits.toIntOrNull()?.let { value -> if (value in 1024..65535) vm.updateSettings { it.copy(port = value) } }
            },
            label = "本地端口",
            keyboardType = KeyboardType.Number,
        )
        Spacer(Modifier.height(12.dp))
        SettingRow(title = "SSH 端口", subtitle = "默认 443，被网络限制时可切 22") {
            ChipRow(
                options = listOf("443", "22"),
                selectedIndex = if (settings.sshPort == 22) 1 else 0,
                onSelect = { index -> vm.updateSettings { it.copy(sshPort = if (index == 1) 22 else 443) } },
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingRow(title = "SSH 服务器", subtitle = settings.sshHost) {
            Text(
                settings.sshHost,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FooterCard() {
    SoftCard(contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Pinggy 免费隧道单次最长 60 分钟，到期后重新点击「启动隧道」即可；" +
                    "公网地址随机生成，需要固定地址请使用 Pro 套餐（可在设置里填写令牌）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "${AppInfo.APP_TITLE} WebTunnel v${AppInfo.VERSION_NAME} · 作者 ${AppInfo.AUTHOR} · QQ 群 ${AppInfo.QQ_GROUP}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
