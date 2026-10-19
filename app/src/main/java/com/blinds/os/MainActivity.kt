package com.blinds.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {

    private lateinit var settings: LauncherSettings
    private lateinit var model: AppModel
    private val ui = UiState()
    private val resumed = mutableStateOf(false)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_ADDED && !replacing) {
                model.refresh(addedPkg = pkg)
            } else {
                model.refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settings = LauncherSettings(getSharedPreferences("blinds_os", MODE_PRIVATE))
        model = AppModel(applicationContext, settings)
        model.refresh()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            val dark = when (settings.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val ink = if (dark) Color.White else Color(0xFF0F1115)
            val backdrop = remember { Backdrop() }
            val view = LocalView.current

            SideEffect {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }

            CompositionLocalProvider(
                LocalSettings provides settings,
                LocalDark provides dark,
                LocalInk provides ink,
                LocalBackdrop provides backdrop,
            ) {
                BackdropHost(
                    backdrop = backdrop,
                    resumed = resumed.value,
                    freeze = ui.showSettings,
                    dark = dark,
                ) {
                    HomeScreen(model = model, ui = ui)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed.value = true
    }

    override fun onPause() {
        resumed.value = false
        super.onPause()
    }

    /** The Home button was pressed while Blinds OS is already on screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            ui.homeSignal += 1
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // already gone
        }
        super.onDestroy()
    }
}
