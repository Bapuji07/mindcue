package com.secondmemory.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.secondmemory.android.data.ThemeMode
import com.secondmemory.android.ui.MindCueApp
import com.secondmemory.android.ui.theme.MindCueTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Draw behind the system bars (enforced from Android 15); Scaffold applies the insets.
        enableEdgeToEdge()
        if (savedInstanceState == null) openRequestedPage(intent)
        setContent {
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme choice, not only the phone's.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme }
                )
                onDispose {}
            }
            MindCueTheme(darkTheme) { MindCueApp(vm) }
        }
    }

    /** A notification tapped while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequestedPage(intent)
    }

    private fun openRequestedPage(intent: Intent?) {
        intent?.getStringExtra(EXTRA_PAGE)?.let(vm::requestPage)
    }

    companion object {
        /** Extra naming the tab to open, e.g. from a reminder notification. */
        const val EXTRA_PAGE = "page"
        const val PAGE_TASKS = "TASKS"

        // The scrims Android uses behind 3-button navigation (same as enableEdgeToEdge's defaults).
        private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
