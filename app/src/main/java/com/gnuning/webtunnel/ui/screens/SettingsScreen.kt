package com.gnuning.webtunnel.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnuning.webtunnel.core.AppInfo
import com.gnuning.webtunnel.core.AppSettings
import com.gnuning.webtunnel.core.ThemeMode
import com.gnuning.webtunnel.ui.AppViewModel
import com.gnuning.webtunnel.ui.components.AppTextField
import com.gnuning.webtunnel.ui.components.ChipRow
import com.gnuning.webtunnel.ui.components.InfoBanner
import com.gnuning.webtunnel.ui.components.SectionDivider
import com.gnuning.webtunnel.ui.components.SectionHeader
import com.gnuning.webtunnel.ui.components.SettingRow
import com.gnuning.webtunnel.ui.components.SoftCard
import com.gnuning.webtunnel.ui.components.SwitchRow
import com.gnuning.webtunnel.ui.components.snack
import com.gnuning.webtunnel.ui.theme.InfoBlue
import com.gnuning.webtunnel.ui.theme.MonoFamily
import com.gnuning.webtunnel.ui.theme.SuccessGreen
import com.gnuning.webtunnel.ui.theme.WarningAmber

/**
 * 设置页：本地服务参数、Pinggy 隧道参数、外观与行为、关于（含作者与 QQ 群，点击可复制）。
 */
@Composable
fun SettingsScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("设置", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "参数会立即保存，下次启动自动生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ServerSettingsCard(settings, vm)
        Spacer(Modifier.height(14.dp))
        PinggySettingsCard(settings, vm)
        Spacer(Modifier.height(14.dp))
        AppearanceCard(settings, vm)
        Spacer(Modifier.height(14.dp))
        BehaviourCard(settings, vm)
        Spacer(Modifier.height(14.dp))
        AboutCard(vm, scope, snackbar)
        Spacer(Modifier.height(26.dp))
    }
}

// ---------------------------------------------------------------- 本地服务
@Composable
private fun ServerSettingsCard(settings: AppSettings, vm: AppViewModel) {
    var portText by rememberSaveable { mutableStateOf(settings.port.toString()) }
    LaunchedEffect(settings.port) {
        if (portText.toIntOrNull() != settings.port) portText = settings.port.toString()
    }

    SoftCard {
        SectionHeader(Icons.Rounded.Speed, "本地服务", "NanoHTTPD 把内容发布到本机端口，再交给隧道")
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
        Spacer(Modifier.height(6.dp))
        SwitchRow(
            title = "记录每个 HTTP 请求",
            subtitle = "关闭后日志更清爽，仅保留启动/停止等关键信息",
            checked = settings.logEveryRequest,
            onCheckedChange = { checked -> vm.updateSettings { it.copy(logEveryRequest = checked) } },
        )
    }
}

// ---------------------------------------------------------------- Pinggy
@Composable
private fun PinggySettingsCard(settings: AppSettings, vm: AppViewModel) {
    SoftCard {
        SectionHeader(Icons.Rounded.Router, "Pinggy 隧道", "SSH 反向隧道参数（对应命令行的 -p / -R / token）")
        Spacer(Modifier.height(14.dp))

        AppTextField(
            value = settings.sshHost,
            onValueChange = { text -> vm.updateSettings { it.copy(sshHost = text.trim()) } },
            label = "SSH 服务器",
            placeholder = "a.pinggy.io",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(12.dp))

        SettingRow(title = "SSH 端口", subtitle = "Pinggy 支持 443 与 22，默认 443") {
            ChipRow(
                options = listOf("443", "22"),
                selectedIndex = if (settings.sshPort == 22) 1 else 0,
                onSelect = { index -> vm.updateSettings { it.copy(sshPort = if (index == 1) 22 else 443) } },
            )
        }
        Spacer(Modifier.height(12.dp))

        AppTextField(
            value = settings.token,
            onValueChange = { text -> vm.updateSettings { it.copy(token = text.trim()) } },
            label = "访问令牌（可选）",
            placeholder = "在 dashboard.pinggy.io 获取",
            keyboardType = KeyboardType.Ascii,
        )
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            Icons.Rounded.Key,
            "填了令牌后，SSH 用户名就是令牌本身（等价于 TOKEN@a.pinggy.io），可解锁 Pro 套餐的更长会话与固定域名。免费使用请留空。",
            InfoBlue,
        )
        Spacer(Modifier.height(12.dp))

        AppTextField(
            value = settings.subdomain,
            onValueChange = { text -> vm.updateSettings { it.copy(subdomain = text.trim()) } },
            label = "自定义子域名（可选）",
            placeholder = "例如 mypage（需 Pinggy 付费套餐）",
            keyboardType = KeyboardType.Ascii,
        )
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            Icons.Rounded.Warning,
            "自定义子域名/固定域名属于 Pinggy 付费能力：填了之后应用会以 `-R 子域名:0:localhost:端口` 的形式请求；若套餐不支持会自动回退成随机域名。",
            WarningAmber,
        )
    }
}

// ---------------------------------------------------------------- 外观
@Composable
private fun AppearanceCard(settings: AppSettings, vm: AppViewModel) {
    SoftCard {
        SectionHeader(Icons.Rounded.Palette, "外观与显示", "整体以白色为主的浅色设计")
        Spacer(Modifier.height(14.dp))
        SettingRow(title = "主题模式", subtitle = settings.themeMode.title) {
            ChipRow(
                options = ThemeMode.entries.map { it.title },
                selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
                onSelect = { index -> vm.updateSettings { it.copy(themeMode = ThemeMode.entries[index]) } },
            )
        }
        SectionDivider()
        SwitchRow(
            title = "屏幕常亮",
            subtitle = "停留在本应用时保持屏幕不熄灭",
            checked = settings.keepScreenOn,
            onCheckedChange = { checked -> vm.updateSettings { it.copy(keepScreenOn = checked) } },
        )
        SwitchRow(
            title = "锁屏保持运行",
            subtitle = "申请唤醒锁，锁屏/后台时隧道更稳定（略增耗电）",
            checked = settings.keepAwake,
            onCheckedChange = { checked -> vm.updateSettings { it.copy(keepAwake = checked) } },
        )
    }
}

// ---------------------------------------------------------------- 行为
@Composable
private fun BehaviourCard(settings: AppSettings, vm: AppViewModel) {
    SoftCard {
        SectionHeader(Icons.Rounded.Shield, "行为", "启动与分享相关的小开关")
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "连接成功后自动复制公网地址",
            subtitle = "省一步操作，直接去别的 App 里粘贴",
            checked = settings.autoCopy,
            onCheckedChange = { checked -> vm.updateSettings { it.copy(autoCopy = checked) } },
        )
        SectionDivider()
        TextButton(onClick = {
            vm.updateSettings { AppSettings() }
        }) {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("恢复默认设置")
        }
    }
}

// ---------------------------------------------------------------- 关于
@Composable
private fun AboutCard(vm: AppViewModel, scope: kotlinx.coroutines.CoroutineScope, snackbar: SnackbarHostState) {
    SoftCard {
        SectionHeader(Icons.Rounded.Info, "关于", "使用说明与作者信息")
        Spacer(Modifier.height(12.dp))

        SettingRow(title = "应用", subtitle = "${AppInfo.APP_TITLE} WebTunnel") {
            Text("v${AppInfo.VERSION_NAME}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        SettingRow(title = "作者", subtitle = "二次开发请保留署名") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(AppInfo.AUTHOR, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // QQ 群：点击复制群号
        SettingRow(title = "QQ 交流群", subtitle = "点击群号复制，再到 QQ 里搜索加入") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (vm.copyQqGroup()) scope.snack(snackbar, "QQ 群号已复制：${AppInfo.QQ_GROUP}")
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Rounded.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(AppInfo.QQ_GROUP, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
            }
        }

        SectionDivider()
        InfoBanner(
            Icons.Rounded.Dns,
            "使用步骤：① 主页选择内容来源（文件 / 文件夹 / HTML 代码 / 转发 URL）；② 点击「启动隧道」；" +
                "③ 复制生成的公网地址，在任意外部设备打开；④ 支持时也可直接扫二维码。",
            SuccessGreen,
        )
        Spacer(Modifier.height(10.dp))
        InfoBanner(
            Icons.Rounded.Warning,
            "注意事项：本应用只把内容发布到公网，请勿用于发布隐私数据；Pinggy 免费隧道最长 60 分钟，地址随机；" +
                "手机需保持联网，退出应用后隧道由前台服务继续维持。",
            WarningAmber,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "模块划分：core（设置/日志/模型）· server（本地 HTTP 服务与反向代理）· tunnel（Pinggy SSH 隧道）· " +
                "service（前台服务）· ui（Compose 界面）。关键逻辑均有中文注释，便于二次开发。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
