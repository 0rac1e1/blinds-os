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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.key
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
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.SideEffect
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
    var showSettings: Boolean
        get() = CustomizeState.show
        set(v) { CustomizeState.show = v }
    var showSearch by mutableStateOf(false)
    var showNotifs by mutableStateOf(false)
    var popId by mutableStateOf<String?>(null)
    var popStamp by mutableIntStateOf(0)

    /** Makes [id] spring in (after it was added or dropped). */
    fun pop(id: String) {
        popId = id
        popStamp += 1
    }
    var menuApp by mutableStateOf<AppEntry?>(null)
    var openCategory by mutableStateOf<String?>(null)
    var selectedId by mutableStateOf<String?>(null)
    var homeSignal by mutableIntStateOf(0)

    // drag and drop of icons while editing
    var showWidgetPicker by mutableStateOf(false)
    var mergeTarget by mutableStateOf<String?>(null)
    var openFolderId by mutableStateOf<String?>(null)
    var editWidgetId by mutableStateOf<String?>(null)
    var showCity by mutableStateOf(false)
    var addWeatherAfterCity = false
    var firstIdOnPage: String? = null
    var dropPreview by mutableStateOf<Rect?>(null)
    var grabCol = 0
    var grabRow = 0
    var pendingPin by mutableStateOf<String?>(null)
    var pendingPage = 0
    var currentPage = 0
    var showWidgetList by mutableStateOf(false)
    var pendingVariant: WidgetVariant? = null
    var relayout by mutableIntStateOf(0)
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
        dropPreview = null
        showWidgetList = false
        showWidgetPicker = false
        openFolderId = null
        mergeTarget = null
        editWidgetId = null
        showCity = false
        editing = false
        showSettings = false
        showSearch = false
        showNotifs = false
        AboutState.show = false
        menuApp = null
        openCategory = null
        selectedId = null
    }
}

class CellInfo(val token: Any, val rect: Rect)

internal val LocalJiggle = compositionLocalOf<State<Float>?> { null }

class Placed(val id: String, val col: Int, val row: Int, val w: Int, val h: Int)

private class PageBuf(val rows: Int, val cols: Int) {
    val occ = Array(rows) { BooleanArray(cols) }
    val items = mutableListOf<Placed>()

    fun free(c: Int, r: Int, w: Int, h: Int): Boolean {
        for (rr in r until r + h) for (cc in c until c + w) if (occ[rr][cc]) return false
        return true
    }

    fun put(id: String, c: Int, r: Int, w: Int, h: Int) {
        for (rr in r until r + h) for (cc in c until c + w) occ[rr][cc] = true
        items += Placed(id, c, r, w, h)
    }

    /** First free spot, reading left to right, top to bottom. */
    fun firstFree(w: Int, h: Int): Pair<Int, Int>? {
        for (r in 0..rows - h) for (c in 0..cols - w) if (free(c, r, w, h)) return c to r
        return null
    }
}

/**
 * Lays items out on pages. Pinned items go exactly where they were put (biggest first, so a widget that
 * grows pushes smaller things aside); everything else flows into the first free spot.
 */
fun layoutPages(
    ids: List<String>,
    spanOf: (String) -> Pair<Int, Int>,
    pins: Map<String, Pin>,
    cols: Int,
    rows: Int,
): List<List<Placed>> {
    val bufs = java.util.TreeMap<Int, PageBuf>()
    val done = HashSet<String>()
    fun clampSpan(id: String): Pair<Int, Int> {
        val (w, h) = spanOf(id)
        return w.coerceIn(1, cols) to h.coerceIn(1, rows)
    }
    val pinned = ids.filter { it in pins }.sortedByDescending { val (w, h) = clampSpan(it); w * h }
    for (id in pinned) {
        val pin = pins.getValue(id)
        val (w, h) = clampSpan(id)
        val c = pin.col.coerceIn(0, cols - w)
        val r = pin.row.coerceIn(0, rows - h)
        val b = bufs.getOrPut(pin.page.coerceAtLeast(0)) { PageBuf(rows, cols) }
        if (b.free(c, r, w, h)) {
            b.put(id, c, r, w, h)
            done += id
        }
    }
    for (id in ids) {
        if (id in done) continue
        val (w, h) = clampSpan(id)
        var spot: Pair<PageBuf, Pair<Int, Int>>? = null
        for (b in bufs.values) {
            val f = b.firstFree(w, h)
            if (f != null) {
                spot = b to f
                break
            }
        }
        if (spot == null) {
            val b = PageBuf(rows, cols)
            bufs[(if (bufs.isEmpty()) 0 else bufs.lastKey() + 1)] = b
            spot = b to (0 to 0)
        }
        spot.first.put(id, spot.second.first, spot.second.second, w, h)
    }
    val pages = bufs.values.filter { it.items.isNotEmpty() }.map { it.items as List<Placed> }
    return if (pages.isEmpty()) listOf(emptyList()) else pages
}

@Composable
fun HomeScreen(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val view = LocalView.current

    val hidden = s.hiddenIds.toSet()
    val byId = remember(model.apps) { model.apps.associateBy { it.id } }
    val byIdNow by rememberUpdatedState(byId)
    val dockIds = s.dockIds.filter { (it in byId || it.startsWith("f:")) && it !in hidden }.take(s.dockCount)
    val specs = s.widgets.associateBy { it.id }
    val folders = s.folders.associateBy { it.id }
    val hostedMap = s.hosted.associateBy { it.id }

    /** Where everything sits right now, computed from the latest settings. */
    fun computePages(exclude: String? = null): List<List<Placed>> {
        val hiddenN = s.hiddenIds.toSet()
        val byN = byIdNow
        val specsN = s.widgets.associateBy { it.id }
        val foldN = s.folders.associateBy { it.id }
        val hostN = s.hosted.associateBy { it.id }
        val dockN = s.dockIds.toSet()
        val colsN = s.cols
        val ids = s.homeIds.filter {
            it != exclude && (it in byN || it in specsN || it in foldN || it in hostN) && it !in hiddenN && it !in dockN
        }
        val span: (String) -> Pair<Int, Int> = { id ->
            specsN[id]?.span(colsN) ?: hostN[id]?.let { it.cw.coerceAtMost(colsN) to it.ch } ?: (1 to 1)
        }
        return layoutPages(ids, span, s.pins, colsN, s.rows)
    }

    val placedPages = computePages()
    // while dragging, one extra empty page appears at the end so an icon can be dropped on a new page
    val displayPages = if (ui.dragId != null) placedPages + listOf(emptyList()) else placedPages
    val pages = displayPages.map { pg -> pg.map { it.id } }
    val libraryIndex = if (s.appLibrary) pages.size else -1
    val pageCount = pages.size + (if (s.appLibrary) 1 else 0)
    val pager = rememberPagerState(pageCount = { pageCount })
    val density = LocalDensity.current
    val padPx = with(density) { 14.dp.toPx() }
    SideEffect { ui.currentPage = pager.currentPage }

    // a widget was resized or removed from Customize: re-seat everything on the grid
    LaunchedEffect(ui.relayout) {
        if (ui.relayout > 0) s.commitPlacements(computePages())
    }

    // ---- drag and drop ----
    val pagesNow by rememberUpdatedState(pages)
    val boxOrigin = remember { floatArrayOf(0f, 0f) }
    val boxSize = remember { floatArrayOf(0f, 0f) }

    fun spanNow(id: String): Pair<Int, Int> {
        val colsN = s.cols
        val (w, h) = s.widget(id)?.span(colsN) ?: s.hostedWidget(id)?.let { it.cw.coerceAtMost(colsN) to it.ch } ?: (1 to 1)
        return w.coerceIn(1, colsN) to h.coerceIn(1, s.rows)
    }

    class Hover(var id: String? = null, var since: Long = 0L)

    fun isLoose(id: String) = id.startsWith("w:") || id.startsWith("f:") || id.startsWith("a:")

    /** Widgets can't live in the dock; apps and folders can. */
    fun canDock(id: String) = !(id.startsWith("w:") || id.startsWith("a:"))

    /** The grid cell (page, column, row) the dragged item's top-left corner would land on. */
    fun targetCell(p: Offset, id: String): Triple<Int, Int, Int>? {
        val gw = boxSize[0] - 2 * padPx
        val gh = boxSize[1]
        if (gw <= 0f || gh <= 0f || p.y < boxOrigin[1] || p.y > boxOrigin[1] + gh) return null
        val cw = gw / s.cols
        val chh = gh / s.rows
        val (w, h) = spanNow(id)
        val col = (((p.x - boxOrigin[0] - padPx) / cw).toInt() - ui.grabCol).coerceIn(0, s.cols - w)
        val row = (((p.y - boxOrigin[1]) / chh).toInt() - ui.grabRow).coerceIn(0, s.rows - h)
        return Triple(pager.currentPage, col, row)
    }

    fun previewRect(cell: Triple<Int, Int, Int>, id: String): Rect {
        val gw = boxSize[0] - 2 * padPx
        val cw = gw / s.cols
        val chh = boxSize[1] / s.rows
        val (w, h) = spanNow(id)
        val left = boxOrigin[0] + padPx + cell.second * cw
        val top = boxOrigin[1] + cell.third * chh
        return Rect(left, top, left + w * cw, top + h * chh)
    }

    /** Called many times a second while dragging: shows where it would land, or marks a folder merge. */
    fun step(p: Offset, id: String, hv: Hover) {
        val now = System.currentTimeMillis()
        val hit = ui.cellAt(p)
        val dr = ui.dockRect
        val overDock = dr != null && dr.contains(p)
        val canMerge = !overDock && !isLoose(id) && hit != null && hit != id && hit !in s.dockIds &&
            !hit.startsWith("w:") && !hit.startsWith("a:")
        if (canMerge) {
            val rect = hit?.let { ui.cells[it]?.rect }
            if (hit != hv.id) {
                hv.id = hit
                hv.since = now
            }
            val mid = rect?.let {
                Rect(
                    it.center.x - it.width * 0.25f, it.center.y - it.height * 0.25f,
                    it.center.x + it.width * 0.25f, it.center.y + it.height * 0.25f,
                )
            }
            if (mid != null && mid.contains(p)) {
                ui.mergeTarget = if (now - hv.since >= 450L) hit else null
                ui.dropPreview = null
                return
            }
        } else {
            hv.id = null
        }
        ui.mergeTarget = null
        if (overDock) {
            ui.dropPreview = null
            return
        }
        val cell = targetCell(p, id)
        ui.dropPreview = if (cell != null && cell.first < pagesNow.size) previewRect(cell, id) else null
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

    // a newly added widget goes to the first free spot of the page you were looking at
    LaunchedEffect(ui.pendingPin) {
        val id = ui.pendingPin ?: return@LaunchedEffect
        ui.pendingPin = null
        val others = computePages(exclude = id)
        val page = ui.pendingPage.coerceIn(0, others.size - 1)
        val (w, h) = spanNow(id)
        val buf = PageBuf(s.rows, s.cols)
        others.getOrNull(page)?.forEach { buf.put(it.id, it.col, it.row, it.w, it.h) }
        val spot = buf.firstFree(w, h)
        if (spot != null) {
            s.pinTo(id, page, spot.first, spot.second)
            s.commitPlacements(computePages())
        }
    }

    // the launcher never closes on Back; it steps back out of edit mode / to the first page instead
    BackHandler(enabled = true) {
        if (ui.openFolderId != null) {
            ui.openFolderId = null
        } else if (ui.editing) {
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
                // edit-mode buttons sit above the grid so they never cover the top row
                val an = LocalAnim.current
                AnimatedVisibility(
                    visible = ui.editing,
                    enter = fadeIn(an.fade()) + expandVertically(an.move()),
                    exit = fadeOut(an.fade(160)) + shrinkVertically(an.fade(200)),
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
                                val gr = ui.cells[grabbed]?.rect
                                val cellW = (boxSize[0] - 2 * padPx) / s.cols
                                val cellH = boxSize[1] / s.rows
                                ui.grabCol = if (gr != null && cellW > 0f) ((start.x - gr.left) / cellW).toInt().coerceAtLeast(0) else 0
                                ui.grabRow = if (gr != null && cellH > 0f) ((start.y - gr.top) / cellH).toInt().coerceAtLeast(0) else 0
                                val hv = Hover()
                                var p = start
                                val edge = 36.dp.toPx()
                                try {
                                    while (true) {
                                        val ev = withTimeoutOrNull(100L) { awaitPointerEvent(PointerEventPass.Initial) }
                                        if (ev != null) {
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                            ch.consume()
                                            if (!ch.pressed) break
                                            p = Offset(ch.position.x + boxOrigin[0], ch.position.y + boxOrigin[1])
                                            ui.dragX = p.x
                                            ui.dragY = p.y
                                            ui.edgeDir = when {
                                                ch.position.x < edge -> -1
                                                ch.position.x > size.width - edge -> 1
                                                else -> 0
                                            }
                                        }
                                        step(p, grabbed, hv)
                                    }
                                } finally {
                                    val mt = ui.mergeTarget
                                    ui.mergeTarget = null
                                    ui.dropPreview = null
                                    val dr = ui.dockRect
                                    ui.pop(grabbed)
                                    if (mt != null) {
                                        s.mergeIntoFolder(grabbed, mt)
                                        s.commitPlacements(computePages())
                                    } else if (dr != null && dr.contains(p)) {
                                        if (canDock(grabbed)) {
                                            val hit = ui.cellAt(p)
                                            val inDock = hit != null && hit in s.dockIds && hit != grabbed
                                            if (inDock || grabbed in s.dockIds || s.dockIds.size < s.dockCount) {
                                                s.dropOnto(grabbed, if (inDock) hit else null, true)
                                            }
                                        }
                                    } else {
                                        val cell = targetCell(p, grabbed)
                                        if (cell != null && cell.first < pagesNow.size) {
                                            s.placeAt(grabbed, cell.first, cell.second, cell.third)
                                            s.commitPlacements(computePages())
                                        }
                                    }
                                    ui.dragId = null
                                    ui.edgeDir = 0
                                }
                            }
                        }
                        .pointerInput(s.swipeSearch, s.notifCenter, s.appLibrary, libraryIndex, pageCount) {
                            var total = 0f
                            var startX = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f; startX = it.x },
                                onDragEnd = {
                                    val onLibrary = libraryIndex >= 0 && pager.currentPage == libraryIndex
                                    if (!onLibrary) {
                                        if (total > 140f && s.notifCenter && startX > size.width / 2f) {
                                            ui.showNotifs = true
                                        } else if (total > 140f && s.swipeSearch) {
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
                                onDoubleTap = { pos ->
                                    if (!ui.editing) {
                                        val third = (pos.y / size.height.toFloat() * 3f).toInt().coerceIn(0, 2)
                                        val action = when (third) { 0 -> s.gDtTop; 1 -> s.gDtMid; else -> s.gDtBottom }
                                        runGesture(action, ctx, view, model, ui) {
                                            if (libraryIndex >= 0) scope.launch { pager.animateScrollToPage(libraryIndex) }
                                        }
                                    }
                                },
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
                        val slideOn = LocalAnim.current.enabled
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val signed = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                    // pages slide a little slower than your finger, which gives the swipe depth
                                    if (slideOn) translationX = signed.coerceIn(-1f, 1f) * size.width * 0.22f
                                    val o = signed.absoluteValue.coerceIn(0f, 1f)
                                    alpha = 1f - 0.55f * o
                                    scaleX = 1f - 0.08f * o
                                    scaleY = 1f - 0.08f * o
                                }
                        ) {
                            if (page == libraryIndex) {
                                LibraryPage(model = model, ui = ui)
                            } else {
                                HomePage(
                                    items = displayPages.getOrElse(page) { emptyList() },
                                    specs = specs,
                                    folders = folders,
                                    hosted = hostedMap,
                                    onLayoutChanged = { s.commitPlacements(computePages()) },
                                    byId = byId,
                                    model = model,
                                    ui = ui,
                                )
                            }
                        }
                    }

                }

                PageDots(count = pageCount, current = pager.currentPage)
                Dock(dockIds = dockIds, byId = byId, model = model, ui = ui)
            }

            ui.dropPreview?.let { r ->
                Box(
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                        .padding(3.dp)
                        .border(2.dp, Color(s.accent).copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                )
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

            FolderSheet(model = model, ui = ui)
            WidgetPicker(ui)
            AppWidgetPicker(ui)
            CityPicker(ui)
            if (ui.showSearch) Spotlight(model = model, ui = ui)
            if (ui.showNotifs) NotificationCenter(model = model, ui = ui)
            ui.menuApp?.let { app -> AppMenu(app = app, model = model, ui = ui) }
            ui.openCategory?.let { cat -> CategorySheet(name = cat, model = model, ui = ui) }
            SettingsSheet(model = model, ui = ui)
            BlindsAboutApp(model = model)
            WhatsNew(ui)
        }
    }
}

@Composable
private fun HomePage(
    items: List<Placed>,
    specs: Map<String, WidgetSpec>,
    folders: Map<String, FolderSpec>,
    hosted: Map<String, HostedWidget>,
    onLayoutChanged: () -> Unit,
    byId: Map<String, AppEntry>,
    model: AppModel,
    ui: UiState,
) {
    val s = LocalSettings.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        val cellW = maxWidth / s.cols
        val cellH = maxHeight / s.rows
        val an = LocalAnim.current
        for (it in items) key(it.id) {
            val spec = specs[it.id]
            val app = byId[it.id]
            val folder = folders[it.id]
            val host = hosted[it.id]
            val popTok = if (ui.popId == it.id) ui.popStamp else 0
            val pop = remember(popTok) { Animatable(if (popTok != 0 && an.enabled) 0.8f else 1f) }
            LaunchedEffect(popTok) {
                if (popTok != 0) {
                    pop.animateTo(1f, an.move())
                    if (ui.popId == it.id) ui.popId = null
                }
            }
            // items glide to their new cell instead of jumping
            val ax by animateDpAsState(cellW * it.col, an.move(), label = "cellX")
            val ay by animateDpAsState(cellH * it.row, an.move(), label = "cellY")
            val aw by animateDpAsState(cellW * it.w, an.move(), label = "cellW")
            val ah by animateDpAsState(cellH * it.h, an.move(), label = "cellH")
            Box(
                Modifier
                    .offset(x = ax, y = ay)
                    .size(aw.coerceAtLeast(0.dp), ah.coerceAtLeast(0.dp))
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (spec != null) {
                    WidgetItem(spec = spec, ui = ui, cellW = cellW, cellH = cellH, onLayoutChanged = onLayoutChanged, modifier = Modifier.fillMaxSize())
                } else if (host != null) {
                    HostedWidgetItem(host = host, ui = ui, cellW = cellW, cellH = cellH, onLayoutChanged = onLayoutChanged, modifier = Modifier.fillMaxSize())
                } else if (folder != null) {
                    FolderCell(folder = folder, byId = byId, ui = ui)
                } else if (app != null) {
                    AppCell(app = app, model = model, ui = ui, labels = s.showLabels, movable = true)
                }
            }
        }
    }
}

fun launchApp(ctx: Context, view: View, app: AppEntry, bounds: IntRect?) {
    val hub = Hub.settings
    if (hub != null) {
        // locked apps ask for the screen lock first (and stay open for 30 seconds afterwards)
        if (app.id in hub.lockedIds && (Hub.unlockedUntil[app.id] ?: 0L) < System.currentTimeMillis()) {
            Hub.lockPrompt?.invoke(app)
            return
        }
        hub.noteLaunch(app.id)
    }
    // the Blinds OS app lives inside the launcher itself
    if (app.isCustomize) {
        CustomizeState.show = true
        return
    }
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
                val sc = if (selected || ui.mergeTarget == app.id) 1.14f else 1f
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
            if (!editing) BadgeBubble(Badges.counts[app.pkg] ?: 0)
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
            var shown = 0
            dockIds.forEach { did ->
                val folder = if (did.startsWith("f:")) s.folder(did) else null
                val app = byId[did]
                if (folder != null) {
                    shown++
                    FolderCell(folder = folder, byId = byId, ui = ui, labels = false)
                } else if (app != null) {
                    shown++
                    AppCell(app = app, model = model, ui = ui, labels = false, movable = true)
                }
            }
            if (shown == 0) {
                Txt("Long-press an app and choose Add to Dock", color = ink.copy(alpha = 0.6f), size = 13.sp)
            }
        }
    }
}
