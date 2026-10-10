package com.gnuning.webtunnel

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gnuning.webtunnel.ui.AppViewModel
import com.gnuning.webtunnel.ui.RootScreen
import com.gnuning.webtunnel.ui.theme.WebTunnelTheme
import com.gnuning.webtunnel.ui.theme.isDarkTheme

/**
 * 唯一的 Activity：全部界面由 Jetpack Compose 绘制（Material 3）。
 * 这里负责三件事：边到边显示、注入 [AppViewModel]、按主题设置系统栏图标明暗。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: AppViewModel = viewModel(
                factory = viewModelFactory {
                    initializer { AppViewModel(WebTunnelApp.instance) }
                },
            )
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = isDarkTheme(settings.themeMode)

            // 浅色主题下把状态栏/导航栏图标切成深色，保证可读性
            val view = LocalView.current
            SideEffect {
                val window = (view.context as? Activity)?.window ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }

            WebTunnelTheme(themeMode = settings.themeMode) {
                RootScreen(vm = vm)
            }
        }
    }
}
