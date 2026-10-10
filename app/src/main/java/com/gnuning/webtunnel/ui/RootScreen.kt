package com.gnuning.webtunnel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnuning.webtunnel.ui.components.BottomBar
import com.gnuning.webtunnel.ui.screens.HomeScreen
import com.gnuning.webtunnel.ui.screens.LogsScreen
import com.gnuning.webtunnel.ui.screens.SettingsScreen

/**
 * 根界面：一个 Scaffold + 悬浮底部导航 + 三个页面（主页 / 日志 / 设置）。
 * 页面切换用 AnimatedContent 做轻微的淡入上滑，交互更顺滑。
 */
@Composable
fun RootScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // 「屏幕常亮」设置：直接作用在当前窗口的 View 上
    val view = LocalView.current
    SideEffect { view.keepScreenOn = settings.keepScreenOn }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { BottomBar(current = tab, onSelect = { tab = it }) },
    ) { innerPadding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically { it / 16 }) togetherWith fadeOut(tween(140))
            },
            label = "screen",
        ) { index ->
            when (index) {
                0 -> HomeScreen(
                    vm = vm,
                    snackbar = snackbar,
                    onOpenSettings = { tab = 2 },
                    modifier = Modifier.padding(innerPadding),
                )
                1 -> LogsScreen(vm = vm, snackbar = snackbar, modifier = Modifier.padding(innerPadding))
                else -> SettingsScreen(vm = vm, snackbar = snackbar, modifier = Modifier.padding(innerPadding))
            }
        }
    }
}
