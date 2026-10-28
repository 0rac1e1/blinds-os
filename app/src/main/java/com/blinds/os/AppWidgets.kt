package com.blinds.os

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The app widget host that lives as long as the launcher; set by MainActivity. */
val LocalWidgetHost = compositionLocalOf<AppWidgetHost?> { null }

/** Lets the picker ask MainActivity to run the bind / configure steps (they need an Activity). */
object WidgetFlow {
    var starter: ((AppWidgetProviderInfo) -> Unit)? = null
}

const val APP_WIDGET_HOST_ID = 291

/** A real widget from another app, drawn inside a grid cell. */
@Composable
fun HostedWidgetItem(
    host: HostedWidget,
    ui: UiState,
    cellW: Dp,
    cellH: Dp,
    onLayoutChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val awHost = LocalWidgetHost.current
    val info = remember(host.appWidgetId) {
        try {
            AppWidgetManager.getInstance(ctx).getAppWidgetInfo(host.appWidgetId)
        } catch (e: Exception) {
            null
        }
    }
    val editing = ui.editing
    Box(
        modifier
            .dragSlot(ui, host.id)
            .padding(5.dp)
            .graphicsLayer { alpha = if (ui.dragId == host.id) 0.35f else 1f },
    ) {
        if (info == null || awHost == null) {
            GlassSurface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                    Txt("Widget unavailable. Remove it and add it again.", size = 13.sp, color = LocalInk.current.copy(alpha = 0.7f))
                }
            }
        } else {
            // widgets from other apps lean on system theme attributes, so inflate them with a real Android theme
            val themed = remember(ctx) {
                android.view.ContextThemeWrapper(
                    ctx,
                    if (android.os.Build.VERSION.SDK_INT >= 29) android.R.style.Theme_DeviceDefault_DayNight
                    else android.R.style.Theme_DeviceDefault,
                )
            }
            val view = remember(host.appWidgetId) {
                // many widgets (clocks especially) pick their layout from the size they are given,
                // so hand them a real size before they draw for the first time
                try {
                    val wDp = (cellW.value * host.cw.coerceAtMost(s.cols)).toInt().coerceAtLeast(40)
                    val hDp = (cellH.value * host.ch).toInt().coerceAtLeast(40)
                    val opts = Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, wDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, wDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, hDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, hDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
                    }
                    AppWidgetManager.getInstance(ctx).updateAppWidgetOptions(host.appWidgetId, opts)
                } catch (e: Exception) {
                    // the widget will get its size from the layout pass instead
                }
                awHost.createView(themed, host.appWidgetId, info).also { v ->
                    v.setOnLongClickListener {
                        ui.editing = true
                        true
                    }
                }
            }
            AndroidView(
                factory = {
                    (view.parent as? ViewGroup)?.removeView(view)
                    view
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(s.cornerRadius.dp))
                    .onSizeChanged { sz ->
                        val wDp = with(density) { sz.width.toDp().value.toInt() }
                        val hDp = with(density) { sz.height.toDp().value.toInt() }
                        try {
                            view.updateAppWidgetSize(Bundle(), wDp, hDp, wDp, hDp)
                        } catch (e: Exception) {
                            // widget does not take size hints
                        }
                    },
            )
        }
        if (editing) {
            // swallow touches so the widget does not react while you arrange things
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(26.dp)
                    .bouncy(onClick = {
                        s.removeHosted(host.id)?.let { id ->
                            try {
                                awHost?.deleteAppWidgetId(id)
                            } catch (e: Exception) {
                                // already gone
                            }
                        }
                    })
                    .clip(CircleShape)
                    .drawBehind { drawRect(Color(0xE6303036)) },
                contentAlignment = Alignment.Center,
            ) {
                Txt("−", color = Color.White, size = 18.sp, weight = FontWeight.Bold)
            }
            ResizeHandle(
                host.cw.coerceAtMost(s.cols), host.ch, cellW, cellH, s.cols, s.rows,
                onResize = { nw, nh -> s.hostedWidget(host.id)?.let { s.updateHosted(it.copy(cw = nw, ch = nh)) } },
                onDone = onLayoutChanged,
            )
        }
    }
}

/** Lists every widget the installed apps offer; picking one starts the bind (and configure) steps. */
@Composable
fun AppWidgetPicker(ui: UiState) {
    if (!ui.showWidgetList) return
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val providers = remember {
        try {
            AppWidgetManager.getInstance(ctx).installedProviders
                .sortedBy { it.loadLabel(ctx.packageManager).lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0x88000000)) }
            .bouncy(onClick = { ui.showWidgetList = false }),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(Modifier.fillMaxWidth().padding(20.dp).swallowTaps(), strength = 1.3f) {
            Column(Modifier.padding(16.dp)) {
                Txt("Widgets from your apps", color = ink, size = 20.sp, weight = FontWeight.SemiBold)
                Txt(
                    "Pick one to add it. Android may ask you to allow Blinds OS to show it.",
                    color = ink.copy(alpha = 0.65f), size = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
                if (providers.isEmpty()) {
                    Txt("No widgets found.", color = ink.copy(alpha = 0.7f), size = 14.sp)
                }
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(providers) { info ->
                        WidgetRow(info) {
                            ui.showWidgetList = false
                            WidgetFlow.starter?.invoke(info)
                        }
                    }
                }
                Spacer(Modifier.size(8.dp))
                GlassButton("Close", onClick = { ui.showWidgetList = false })
            }
        }
    }
}

@Composable
private fun WidgetRow(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val label = remember(info) { info.loadLabel(ctx.packageManager) }
    val appName = remember(info) {
        try {
            ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString()
        } catch (e: Exception) {
            info.provider.packageName
        }
    }
    val preview by produceState<ImageBitmap?>(null, info) {
        value = withContext(Dispatchers.Default) {
            try {
                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                val iw = d.intrinsicWidth.coerceAtLeast(1)
                val ih = d.intrinsicHeight.coerceAtLeast(1)
                val w = 240
                d.toBitmap(w, (w.toFloat() * ih / iw).toInt().coerceIn(48, 240)).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    val density = LocalDensity.current
    val cellsW = (info.minWidth / density.density + 30f).div(70f).toInt().coerceAtLeast(1)
    val cellsH = (info.minHeight / density.density + 30f).div(70f).toInt().coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().bouncy(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(12.dp)).drawBehind { drawRect(ink.copy(alpha = 0.08f)) },
            contentAlignment = Alignment.Center,
        ) {
            val bmp = preview
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Txt(label, color = ink, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
            Txt("$appName · $cellsW × $cellsH", color = ink.copy(alpha = 0.6f), size = 12.sp, maxLines = 1)
        }
    }
}
