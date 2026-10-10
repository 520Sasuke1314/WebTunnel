package com.gnuning.webtunnel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnuning.webtunnel.core.LogEntry
import com.gnuning.webtunnel.core.LogLevel
import com.gnuning.webtunnel.core.formatLogTime
import com.gnuning.webtunnel.ui.AppViewModel
import com.gnuning.webtunnel.ui.components.ChipRow
import com.gnuning.webtunnel.ui.components.EmptyState
import com.gnuning.webtunnel.ui.components.snack
import com.gnuning.webtunnel.ui.theme.ErrorRed
import com.gnuning.webtunnel.ui.theme.IdleGray
import com.gnuning.webtunnel.ui.theme.InfoBlue
import com.gnuning.webtunnel.ui.theme.MonoFamily
import com.gnuning.webtunnel.ui.theme.SuccessGreen
import com.gnuning.webtunnel.ui.theme.WarningAmber

/**
 * 日志页：按级别过滤、自动滚动、一键复制/清空。
 * 日志来自 [com.gnuning.webtunnel.core.LogRepository]，HTTP 请求、SSH 隧道、前台服务都写在这里。
 */
@Composable
fun LogsScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var filter by rememberSaveable { mutableIntStateOf(0) }
    var autoScroll by rememberSaveable { mutableStateOf(true) }

    val filtered = remember(logs, filter) {
        when (filter) {
            1 -> logs.filter { it.level == LogLevel.INFO || it.level == LogLevel.SUCCESS }
            2 -> logs.filter { it.level == LogLevel.WARN }
            3 -> logs.filter { it.level == LogLevel.ERROR }
            else -> logs
        }
    }

    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.lastIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("运行日志", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "共 ${logs.size} 条 · 实时刷新（含 HTTP 请求与 SSH 隧道输出）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RoundIconButton(Icons.Rounded.ContentCopy, "复制") {
                    if (vm.copyLogs()) scope.snack(snackbar, "日志已复制到剪贴板") else scope.snack(snackbar, "复制失败")
                }
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Rounded.Delete, "清空") {
                    vm.clearLogs()
                    scope.snack(snackbar, "日志已清空")
                }
            }
            Spacer(Modifier.height(14.dp))
            ChipRow(
                options = listOf("全部", "信息", "警告", "错误"),
                selectedIndex = filter,
                onSelect = { filter = it },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("自动滚动到最新", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { autoScroll = it },
                    modifier = Modifier.scale(0.85f),
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        if (filtered.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Rounded.Article,
                title = "暂无日志",
                subtitle = "启动隧道后，这里会实时显示 HTTP 请求与隧道运行信息",
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(items = filtered, key = { it.id }) { entry -> LogRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val accent: Color = when (entry.level) {
        LogLevel.DEBUG -> IdleGray
        LogLevel.INFO -> InfoBlue
        LogLevel.SUCCESS -> SuccessGreen
        LogLevel.WARN -> WarningAmber
        LogLevel.ERROR -> ErrorRed
    }
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Text(
            formatLogTime(entry.time),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(entry.level.tag, style = MaterialTheme.typography.labelSmall, color = accent)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            entry.message,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
