package com.gnuning.webtunnel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.gnuning.webtunnel.core.TunnelStatus
import com.gnuning.webtunnel.ui.theme.ErrorRed
import com.gnuning.webtunnel.ui.theme.InfoBlue
import com.gnuning.webtunnel.ui.theme.IdleGray
import com.gnuning.webtunnel.ui.theme.SoftShadow
import com.gnuning.webtunnel.ui.theme.SuccessGreen
import com.gnuning.webtunnel.ui.theme.WarningAmber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ======================================================================
// 通用容器与排版
// ======================================================================

/** 大圆角 + 柔和阴影 + 细边框的卡片（全应用统一的视觉基底） */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = shape, clip = false, ambientColor = SoftShadow, spotColor = SoftShadow)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** 卡片内的分区标题（左侧圆角小图标 + 标题/副标题） */
@Composable
fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

// ======================================================================
// 状态指示
// ======================================================================

data class StatusColors(val accent: Color, val soft: Color)

@Composable
fun statusColors(status: TunnelStatus): StatusColors {
    val accent = when (status) {
        TunnelStatus.IDLE -> IdleGray
        TunnelStatus.STARTING, TunnelStatus.CONNECTING -> WarningAmber
        TunnelStatus.CONNECTED -> SuccessGreen
        TunnelStatus.STOPPING -> InfoBlue
        TunnelStatus.ERROR -> ErrorRed
    }
    return StatusColors(accent, accent.copy(alpha = 0.13f))
}

/** 带呼吸动画的状态圆点：连接中脉冲，其余静止 */
@Composable
fun StatusDot(color: Color, pulsing: Boolean, size: Dp = 10.dp) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 2.1f else 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotScale",
    )
    Box(modifier = Modifier.size(size * 2.2f), contentAlignment = Alignment.Center) {
        if (pulsing) {
            Box(
                Modifier
                    .size(size)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f))
            )
        }
        Box(Modifier.size(size).clip(CircleShape).background(color))
    }
}

@Composable
fun StatusPill(status: TunnelStatus) {
    val colors = statusColors(status)
    Surface(color = colors.soft, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(colors.accent))
            Spacer(Modifier.width(7.dp))
            Text(status.title, style = MaterialTheme.typography.labelLarge, color = colors.accent)
        }
    }
}

/** 提示条（错误 / 警告 / 说明） */
@Composable
fun InfoBanner(
    icon: ImageVector,
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ======================================================================
// 输入控件
// ======================================================================

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailing: (@Composable (() -> Unit))? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { hint -> { Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        singleLine = singleLine,
        minLines = minLines,
        trailingIcon = trailing,
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** 设置项：标题 + 副标题 + 右侧控件 */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(title = title, subtitle = subtitle) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 胶囊选项组（单选），用于主题模式 / SSH 端口这类小选项 */
@Composable
fun ChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selectedIndex
            val background by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                label = "chipBg",
            )
            val foreground by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "chipFg",
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = foreground)
            }
        }
    }
}

// ======================================================================
// 按钮
// ======================================================================

/** 主操作按钮：大圆角、按下有轻微缩放反馈 */
@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.975f else 1f, label = "press")

    Surface(
        color = if (enabled) containerColor else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(9.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** 地址下方的图标动作（复制 / 分享 / 打开 / 二维码） */
@Composable
fun UrlActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ======================================================================
// 二维码 / 空状态 / 底部导航
// ======================================================================

/** 用 ZXing 现场生成二维码，方便外部设备扫码打开公网地址 */
@Composable
fun QrCodeView(text: String, size: Dp = 176.dp, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1))
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null) {
            Text("二维码生成失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                val modules = matrix.width
                val cell = this.size.minDimension / modules
                for (x in 0 until modules) {
                    for (y in 0 until modules) {
                        if (matrix.get(x, y)) {
                            drawRect(
                                color = Color(0xFF141B2D),
                                topLeft = Offset(x * cell, y * cell),
                                size = Size(cell + 0.7f, cell + 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 悬浮式底部导航（白色圆角胶囊 + 选中态动画） */
@Composable
fun BottomBar(current: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("主页", Icons.Rounded.Home, 0),
        Triple("日志", Icons.AutoMirrored.Rounded.Article, 1),
        Triple("设置", Icons.Rounded.Settings, 2),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { (label, icon, index) ->
                    val active = index == current
                    val background by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "tabBg",
                    )
                    val foreground by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "tabFg",
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(background)
                            .clickable { onSelect(index) }
                            .padding(vertical = 9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(icon, contentDescription = label, tint = foreground, modifier = Modifier.size(21.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = foreground)
                    }
                }
            }
        }
    }
}

/** 便捷的 Snackbar 提示（在 Composable 里 `scope.snack(host, "文案")` 即可） */
fun CoroutineScope.snack(host: SnackbarHostState, message: String) {
    launch { host.showSnackbar(message) }
}
