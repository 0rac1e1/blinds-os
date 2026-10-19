@file:OptIn(ExperimentalFoundationApi::class)

package com.blinds.os

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/** Which overlay is open and what is being edited. Lives for as long as the launcher does. */
class UiState {
    var editing by mutableStateOf(false)
    var showSettings by mutableStateOf(false)
    var showSearch by mutableStateOf(false)
    var menuApp by mutableStateOf<AppEntry?>(null)
    var openCategory by mutableStateOf<String?>(null)
    var selectedId by mutableStateOf<String?>(null)
    var homeSignal by mutableIntStateOf(0)

    fun closeAll() {
        editing = false
        showSettings = false
        showSearch = false
        menuApp = null
        openCategory = null
        selectedId = null
    }
}

private val LocalJiggle = compositionLocalOf<State<Float>?> { null }

private fun paginate(ids: List<String>, first: Int, rest: Int): List<List<String>> {
    if (ids.isEmpty()) return listOf(emptyList())
    val out = mutableListOf<List<String>>()
    out += ids.take(first)
    var i = first
    while (i < ids.size) {
        out += ids.subList(i, minOf(i + rest, ids.size))
        i += rest
    }
    return out
}

@Composable
fun HomeScreen(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val hidden = s.hiddenIds.toSet()
    val byId = remember(model.apps) { model.apps.associateBy { it.id } }
    val dockIds = s.dockIds.filter { it in byId && it !in hidden }.take(s.dockCount)
    val homeIds = s.homeIds.filter { it in byId && it !in hidden && it !in dockIds }

    val firstRows = (s.rows - (if (s.clockWidget) 2 else 0)).coerceAtLeast(1)
    val cap0 = s.cols * firstRows
    val cap = s.cols * s.rows
    val pages = remember(homeIds, cap0, cap) { paginate(homeIds, cap0, cap) }
    val libraryIndex = if (s.appLibrary) pages.size else -1
    val pageCount = pages.size + (if (s.appLibrary) 1 else 0)
    val pager = rememberPagerState(pageCount = { pageCount })

    // the launcher never closes on Back; it steps back out of edit mode / to the first page instead
    BackHandler(enabled = true) {
        if (ui.editing) {
            ui.editing = false
            ui.selectedId = null
        } else if (pager.currentPage != 0) {
            scope.launch { pager.animateScrollToPage(0) }
        }
    }

    // pressing the Home button while already home
    LaunchedEffect(ui.homeSignal) {
        if (ui.homeSignal > 0) {
            ui.closeAll()
            pager.animateScrollToPage(0)
        }
    }

    // icons only wiggle while editing; the animation does not exist the rest of the time
    val jiggle: State<Float>? = if (ui.editing) {
        rememberInfiniteTransition(label = "jiggle").animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(tween(130, easing = LinearEasing), RepeatMode.Reverse),
            label = "jiggleValue",
        )
    } else null

    CompositionLocalProvider(LocalJiggle provides jiggle) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(s.swipeSearch, s.appLibrary, libraryIndex, pageCount) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = {
                                    val onLibrary = libraryIndex >= 0 && pager.currentPage == libraryIndex
                                    if (!onLibrary) {
                                        if (total > 140f && s.swipeSearch) {
                                            ui.showSearch = true
                                        } else if (total < -140f && libraryIndex >= 0) {
                                            scope.launch { pager.animateScrollToPage(libraryIndex) }
                                        }
                                    }
                                },
                                onDragCancel = {},
                                onVerticalDrag = { _, dy -> total += dy },
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    ui.editing = true
                                },
                                onTap = {
                                    if (ui.editing) {
                                        ui.editing = false
                                        ui.selectedId = null
                                    }
                                },
                            )
                        }
                ) {
                    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val o = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
                                        .absoluteValue.coerceIn(0f, 1f)
                                    alpha = 1f - 0.55f * o
                                    scaleX = 1f - 0.08f * o
                                    scaleY = 1f - 0.08f * o
                                }
                        ) {
                            if (page == libraryIndex) {
                                LibraryPage(model = model, ui = ui)
                            } else {
                                HomePage(
                                    ids = pages.getOrElse(page) { emptyList() },
                                    first = page == 0,
                                    byId = byId,
                                    model = model,
                                    ui = ui,
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = ui.editing,
                        enter = fadeIn() + slideInVertically { -it / 2 },
                        exit = fadeOut(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            GlassButton("Customize", onClick = {
                                ui.editing = false
                                ui.selectedId = null
                                ui.showSettings = true
                            })
                            GlassButton("Done", onClick = {
                                ui.editing = false
                                ui.selectedId = null
                            }, accent = true)
                        }
                    }
                }

                PageDots(count = pageCount, current = pager.currentPage)
                Dock(dockIds = dockIds, byId = byId, model = model, ui = ui)
            }

            if (ui.showSearch) Spotlight(model = model, ui = ui)
            ui.menuApp?.let { app -> AppMenu(app = app, model = model, ui = ui) }
            ui.openCategory?.let { cat -> CategorySheet(name = cat, model = model, ui = ui) }
            SettingsSheet(model = model, ui = ui)
        }
    }
}

@Composable
private fun HomePage(
    ids: List<String>,
    first: Boolean,
    byId: Map<String, AppEntry>,
    model: AppModel,
    ui: UiState,
) {
    val s = LocalSettings.current
    val showClock = first && s.clockWidget
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        if (showClock) {
            ClockWidget(Modifier.weight(2f).fillMaxWidth().padding(vertical = 6.dp))
        }
        val rowCount = if (showClock) (s.rows - 2).coerceAtLeast(1) else s.rows
        for (r in 0 until rowCount) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (c in 0 until s.cols) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        val app = ids.getOrNull(r * s.cols + c)?.let { byId[it] }
                        if (app != null) AppCell(app = app, model = model, ui = ui, labels = s.showLabels)
                    }
                }
            }
        }
    }
}

fun launchApp(ctx: Context, view: View, app: AppEntry, bounds: IntRect?) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try {
        val opts = if (bounds != null) {
            ActivityOptions.makeScaleUpAnimation(view, bounds.left, bounds.top, bounds.width, bounds.height).toBundle()
        } else null
        ctx.startActivity(intent, opts)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun AppCell(
    app: AppEntry,
    model: AppModel,
    ui: UiState,
    labels: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val jiggle = LocalJiggle.current
    var bounds by remember { mutableStateOf<IntRect?>(null) }

    val editing = ui.editing
    val selected = ui.selectedId == app.id
    val flip = if ((app.id.hashCode() and 1) == 0) 1f else -1f

    Column(
        modifier
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                bounds = IntRect(
                    p.x.roundToInt(),
                    p.y.roundToInt(),
                    (p.x + c.size.width).roundToInt(),
                    (p.y + c.size.height).roundToInt(),
                )
            }
            .graphicsLayer {
                rotationZ = if (editing) (jiggle?.value ?: 0f) * flip else 0f
                val sc = if (selected) 1.14f else 1f
                scaleX = sc
                scaleY = sc
            }
            .bouncy(
                onClick = {
                    if (editing) {
                        val sel = ui.selectedId
                        when {
                            sel == null -> ui.selectedId = app.id
                            sel == app.id -> ui.selectedId = null
                            else -> {
                                s.swap(sel, app.id)
                                ui.selectedId = null
                            }
                        }
                    } else {
                        launchApp(ctx, view, app, bounds)
                    }
                },
                onLongClick = {
                    if (!editing) {
                        if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        ui.menuApp = app
                    }
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AppIcon(app = app, size = s.iconSize.dp)
            if (editing) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(24.dp)
                        .bouncy(onClick = { s.removeFromHome(app.id) })
                        .clip(CircleShape)
                        .drawBehind { drawRect(Color(0xE6303036)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("−", color = Color.White, size = 17.sp, weight = FontWeight.Bold)
                }
            }
        }
        if (labels) {
            Txt(
                text = app.label,
                color = ink,
                size = 11.sp,
                maxLines = 1,
                align = TextAlign.Center,
                shadow = true,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    if (count <= 1) return
    val ink = LocalInk.current
    Box(Modifier.fillMaxWidth().padding(bottom = 10.dp), contentAlignment = Alignment.Center) {
        GlassSurface(shape = RoundedCornerShape(50), strength = 0.7f) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(count) { i ->
                    val on = i == current
                    Dot(color = if (on) ink else ink.copy(alpha = 0.35f), size = if (on) 8.dp else 6.dp)
                }
            }
        }
    }
}

@Composable
private fun Dock(dockIds: List<String>, byId: Map<String, AppEntry>, model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
        shape = RoundedCornerShape((s.cornerRadius + 6f).dp),
        strength = 1.1f,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val apps = dockIds.mapNotNull { byId[it] }
            apps.forEach { app -> AppCell(app = app, model = model, ui = ui, labels = false) }
            if (apps.isEmpty()) {
                Txt("Long-press an app and choose Add to Dock", color = ink.copy(alpha = 0.6f), size = 13.sp)
            }
        }
    }
}

@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val minute = now / 60_000L
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val timeText = remember(minute, is24) {
        SimpleDateFormat(if (is24) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    val dateText = remember(minute) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)) }
    val battery = remember(minute) {
        try {
            (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            -1
        }
    }

    val body: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Txt(dateText.uppercase(), color = ink.copy(alpha = 0.75f), size = 13.sp, weight = FontWeight.SemiBold, spacing = 1.2.sp, shadow = s.clockStyle == "bare")
            Txt(timeText, color = ink, size = 66.sp, weight = FontWeight.Light, shadow = s.clockStyle == "bare")
            if (battery >= 0) {
                Txt("Battery $battery%", color = ink.copy(alpha = 0.65f), size = 13.sp, shadow = s.clockStyle == "bare")
            }
        }
    }

    if (s.clockStyle == "card") {
        GlassSurface(modifier.fillMaxSize(), strength = 1f) { body() }
    } else {
        Box(modifier.fillMaxSize()) { body() }
    }
}
