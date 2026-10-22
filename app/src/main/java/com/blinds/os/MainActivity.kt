package com.blinds.os

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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

    private lateinit var widgetHost: AppWidgetHost
    private var pendingAwId = -1
    private var pendingInfo: AppWidgetProviderInfo? = null

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

        widgetHost = AppWidgetHost(applicationContext, APP_WIDGET_HOST_ID)
        WidgetFlow.starter = { info -> beginWidget(info) }

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
                LocalWidgetHost provides widgetHost,
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

    override fun onStart() {
        super.onStart()
        try {
            widgetHost.startListening()
        } catch (e: Exception) {
            // widget updates will resume on the next start
        }
    }

    override fun onStop() {
        try {
            widgetHost.stopListening()
        } catch (e: Exception) {
            // not listening
        }
        super.onStop()
    }

    // ---- adding a real Android widget: allocate an id, ask permission to bind it, configure it, place it ----

    private fun beginWidget(info: AppWidgetProviderInfo) {
        cancelPendingWidget()
        val id = widgetHost.allocateAppWidgetId()
        pendingAwId = id
        pendingInfo = info
        val awm = AppWidgetManager.getInstance(this)
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
            afterBind()
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                cancelPendingWidget()
            }
        }
    }

    private fun afterBind() {
        val info = pendingInfo ?: return
        if (info.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, pendingAwId, 0, REQ_CONFIG, null)
            } catch (e: Exception) {
                finishWidget()
            }
        } else {
            finishWidget()
        }
    }

    private fun finishWidget() {
        val info = pendingInfo
        val id = pendingAwId
        if (info == null || id < 0) return
        val density = resources.displayMetrics.density
        val cw = (((info.minWidth / density) + 30f) / 70f).toInt().coerceIn(1, settings.cols)
        val ch = (((info.minHeight / density) + 30f) / 70f).toInt().coerceIn(1, settings.rows)
        val hid = settings.addHosted(id, info.provider.flattenToString(), cw, ch)
        ui.pendingPage = ui.currentPage
        ui.pendingPin = hid
        pendingAwId = -1
        pendingInfo = null
    }

    private fun cancelPendingWidget() {
        if (pendingAwId >= 0) {
            try {
                widgetHost.deleteAppWidgetId(pendingAwId)
            } catch (e: Exception) {
                // already freed
            }
        }
        pendingAwId = -1
        pendingInfo = null
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_BIND -> if (resultCode == Activity.RESULT_OK) afterBind() else cancelPendingWidget()
            REQ_CONFIG -> if (resultCode == Activity.RESULT_OK) finishWidget() else cancelPendingWidget()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed.value = true
        Badges.refreshAccess(this)
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
        WidgetFlow.starter = null
        try {
            unregisterReceiver(packageReceiver)
        } catch (e: Exception) {
            // already gone
        }
        super.onDestroy()
    }

    companion object {
        private const val REQ_BIND = 7101
        private const val REQ_CONFIG = 7102
    }
}
