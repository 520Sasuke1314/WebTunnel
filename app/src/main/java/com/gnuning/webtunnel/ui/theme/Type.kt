package com.gnuning.webtunnel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val base = Typography()

/** 只做少量调整：标题更紧凑有力，正文保持系统默认可读性。 */
val AppTypography = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

/** 地址、日志等技术信息用等宽字体 */
val MonoFamily: FontFamily = FontFamily.Monospace
