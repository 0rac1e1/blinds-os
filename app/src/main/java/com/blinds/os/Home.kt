@file:OptIn(ExperimentalFoundationApi::class)

package com.blinds.os

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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

    // drag and drop of icons while editing
    var showWidgetPicker by mutableStateOf(false)
    var editWidgetId by mutableStateOf<String?>(null)
    var showCity by mutableStateOf(false)
    var addWeatherAfterCity = false
    var firstIdOnPage: String? = null
    var dragId by mutableStateOf<String?>(null)
    var dragX by mutableStateOf(0f)
    var dragY by mutableStateOf(0f)
    var edgeDir by mutableIntStateOf(0)
    val cells = HashMap<String, CellInfo>()
    var dockRect: Rect? = null

    fun cellAt(p: Offset): String? =
        cells.entries.firstOrNull { it.value.rect.contains(p) }?.key

    fun closeAll() {
        dragId = null
        showWidgetPicker = false
        editWidgetId = null
        showCity = false
        editing = false
        showSettings = false
        showSearch = false
        AboutState.show = false
        menuApp = null
        openCategory = null
        selectedId = null
    }
}

class CellInfo(val token: Any, val rect: Rect)

private val LocalJiggle = compositionLocalOf<State<Float>?> { null }

class Placed(val id: String, val col: Int, val row: Int, val w: Int, val h: Int)

/** Flows items onto pages in order; widgets take several cells. Small items fill gaps beside widgets. */
private fun layoutPages(ids: List<String>, spanOf: (String) -> Pair<Int, Int>, cols: Int, rows: Int): List<List<Placed>> {
    val pages = mutableListOf<MutableList<Placed>>()
    var occ = Array(rows) { BooleanArray(cols) }
    var cur = mutableListOf<Placed>()
    pages += cur
    for (id in ids) {
        val (w0, h0) = spanOf(id)
        val w = w0.coerceIn(1, cols)
        val h = h0.coerceIn(1, rows)
        var placed = false
        while (!placed) {
            loop@ for (r in 0..rows - h) {
                for (c in 0..cols - w) {
                    var free = true
                    for (rr in r until r + h) for (cc in c until c + w) if (occ[rr][cc]) free = false
                    if (free) {
                        for (rr in r until r + h) for (cc in c until c + w) occ[rr][cc] = true
                        cur += Placed(id, c, r, w, h)
                        placed = true
                        break@loop
                    }
                }
            }
            if (!placed) {
                if (cur.isEmpty()) break
                occ = Array(rows) { BooleanArray(cols) }
                cur = mutableListOf()
                pages += cur
            }
        }
    }
    return pages
}

@Composable
fun HomeScreen(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val hidden = s.hiddenIds.toSet()
    val byId = remember(model.apps) { model.apps.associateBy { it.id } }
    val dockIds = s.dockIds.filter { it in byId && it !in hidden }.take(s.dockCount)
    val specs = s.widgets.associateBy { it.id }
    val homeIds = s.homeIds.filter { (it in byId || it in specs) && it !in hidden && it !in dockIds }

    val cols = s.cols
    val rowsN = s.rows
    val placedPages = remember(homeIds, specs, cols, rowsN) {
        layoutPages(homeIds, { id -> specs[id]?.span(cols) ?: (1 to 1) }, cols, rowsN)
    }
    val pages = remember(placedPages) { placedPages.map { pg -> pg.map { it.id } } }
    val libraryIndex = if (s.appLibrary) pages.size else -1
    val pageCount = pages.size + (if (s.appLibrary) 1 else 0)
    val pager = rememberPagerState(pageCount = { pageCount })

    // ---- drag and drop ----
    val pagesNow by rememberUpdatedState(pages)
    val boxOrigin = remember { floatArrayOf(0f, 0f) }
    val boxSize = remember { floatArrayOf(0f, 0f) }

    fun reorderAt(p: Offset, id: String, last: String?): String? {
        val hit = ui.cellAt(p)
        if (hit == id) return null
        if (hit != null) {
            if (hit == last) return last
            s.dropOnto(id, hit, hit in s.dockIds)
            return hit
        }
        val dr = ui.dockRect
        if (dr != null && dr.contains(p)) {
            if (id !in s.dockIds && s.dockIds.size < s.dockCount) s.dropOnto(id, null, true)
            return null
        }
        val inPager = p.y >= boxOrigin[1] && p.y <= boxOrigin[1] + boxSize[1]
        val page = pager.currentPage
        if (inPager && page < pagesNow.size) {
            val lastOnPage = pagesNow[page].lastOrNull()
            if (lastOnPage != id) s.dropOnto(id, lastOnPage, false, after = true)
        }
        return null
    }

    // holding a dragged icon at the screen edge flips to the next page
    LaunchedEffect(ui.dragId != null, ui.edgeDir) {
        if (ui.dragId != null && ui.edgeDir != 0) {
            while (true) {
                delay(600)
                val t = pager.currentPage + ui.edgeDir
                if (t in 0 until pagesNow.size) pager.animateScrollToPage(t) else break
            }
        }
    }

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
                        .onGloballyPositioned { c ->
                            val p = c.positionInWindow()
                            boxOrigin[0] = p.x
                            boxOrigin[1] = p.y
                            boxSize[0] = c.size.width.toFloat()
                            boxSize[1] = c.size.height.toFloat()
                        }
                        .pointerInput(ui.editing) {
                            if (!ui.editing) return@pointerInput
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                val start = Offset(down.position.x + boxOrigin[0], down.position.y + boxOrigin[1])
                                val grabbed = ui.cellAt(start) ?: return@awaitEachGesture
                                var cancelled = false
                                withTimeoutOrNull(250L) {
                                    while (!cancelled) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        val ch = ev.changes.firstOrNull { it.id == down.id }
                                        if (ch == null || !ch.pressed ||
                                            (ch.position - down.position).getDistance() > viewConfiguration.touchSlop
                                        ) cancelled = true
                                    }
                                }
                                if (cancelled) return@awaitEachGesture
                                if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                ui.dragId = grabbed
                                ui.dragX = start.x
                                ui.dragY = start.y
                                var lastTarget: String? = null
                                val edge = 36.dp.toPx()
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                        ch.consume()
                                        if (!ch.pressed) break
                                        val p = Offset(ch.position.x + boxOrigin[0], ch.position.y + boxOrigin[1])
                                        ui.dragX = p.x
                                        ui.dragY = p.y
                                        ui.edgeDir = when {
                                            ch.position.x < edge -> -1
                                            ch.position.x > size.width - edge -> 1
                                            else -> 0
                                        }
                                        lastTarget = reorderAt(p, grabbed, lastTarget)
                                    }
                                } finally {
                                    ui.dragId = null
                                    ui.edgeDir = 0
                                }
                            }
                        }
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
                                    items = placedPages.getOrElse(page) { emptyList() },
                                    specs = specs,
                                    byId = byId,
                                    model = model,
                                    ui = ui,
                                )
                            }
                        }
                    }

                    if (ui.editing) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            GlassButton("Customize", onClick = {
                                ui.editing = false
                                ui.selectedId = null
                                ui.showSettings = true
                            })
                            GlassButton("+ Widget", onClick = {
                                ui.firstIdOnPage = pages.getOrNull(pager.currentPage)?.firstOrNull()
                                ui.showWidgetPicker = true
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

            ui.dragId?.let { did ->
                byId[did]?.let { app ->
                    AppIcon(
                        app = app,
                        size = s.iconSize.dp,
                        modifier = Modifier
                            .offset {
                                val half = (s.iconSize.dp.roundToPx()) / 2
                                IntOffset(ui.dragX.roundToInt() - half, ui.dragY.roundToInt() - half)
                            }
                            .graphicsLayer {
                                scaleX = 1.2f
                                scaleY = 1.2f
                                alpha = 0.92f
                            },
                    )
                }
            }

            WidgetPicker(ui)
            WidgetEditor(ui)
            CityPicker(ui)
            if (ui.showSearch) Spotlight(model = model, ui = ui)
            ui.menuApp?.let { app -> AppMenu(app = app, model = model, ui = ui) }
            ui.openCategory?.let { cat -> CategorySheet(name = cat, model = model, ui = ui) }
            SettingsSheet(model = model, ui = ui)
            BlindsAboutApp(model = model)
        }
    }
}

@Composable
private fun HomePage(
    items: List<Placed>,
    specs: Map<String, WidgetSpec>,
    byId: Map<String, AppEntry>,
    model: AppModel,
    ui: UiState,
) {
    val s = LocalSettings.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        val cellW = maxWidth / s.cols
        val cellH = maxHeight / s.rows
        for (it in items) {
            val spec = specs[it.id]
            val app = byId[it.id]
            Box(
                Modifier
                    .offset(x = cellW * it.col, y = cellH * it.row)
                    .size(cellW * it.w, cellH * it.h),
                contentAlignment = Alignment.Center,
            ) {
                if (spec != null) {
                    WidgetItem(spec = spec, ui = ui, modifier = Modifier.fillMaxSize())
                } else if (app != null) {
                    AppCell(app = app, model = model, ui = ui, labels = s.showLabels, movable = true)
                }
            }
        }
    }
}

fun launchApp(ctx: Context, view: View, app: AppEntry, bounds: IntRect?) {
    // the Blinds OS app lives inside the launcher itself
    if (app.pkg == ctx.packageName) {
        AboutState.show = true
        return
    }
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
    movable: Boolean = false,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val jiggle = LocalJiggle.current
    var bounds by remember { mutableStateOf<IntRect?>(null) }
    val token = remember { Any() }
    if (movable) {
        DisposableEffect(app.id) {
            onDispose { if (ui.cells[app.id]?.token === token) ui.cells.remove(app.id) }
        }
    }

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
                if (movable) {
                    ui.cells[app.id] = CellInfo(token, Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height))
                }
            }
            .graphicsLayer {
                alpha = if (ui.dragId == app.id) 0f else 1f
                rotationZ = if (editing) (jiggle?.value ?: 0f) * flip else 0f
                val sc = if (selected) 1.14f else 1f
                scaleX = sc
                scaleY = sc
            }
            .bouncy(
                onClick = {
                    // while editing, icons are moved by dragging them, not tapped
                    if (!editing) launchApp(ctx, view, app, bounds)
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
            .onGloballyPositioned { c ->
                val p = c.positionInWindow()
                ui.dockRect = Rect(p.x, p.y, p.x + c.size.width, p.y + c.size.height)
            },
        shape = RoundedCornerShape((s.cornerRadius + 6f).dp),
        strength = 1.1f,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val apps = dockIds.mapNotNull { byId[it] }
            apps.forEach { app -> AppCell(app = app, model = model, ui = ui, labels = false, movable = true) }
            if (apps.isEmpty()) {
                Txt("Long-press an app and choose Add to Dock", color = ink.copy(alpha = 0.6f), size = 13.sp)
            }
        }
    }
}
