@file:OptIn(ExperimentalFoundationApi::class)

package com.blinds.os

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun Modifier.swallowTaps(): Modifier = pointerInput(Unit) { detectTapGestures(onTap = {}) }

@Composable
fun SearchGlyph(color: Color, dp: Dp = 20.dp) {
    Canvas(Modifier.size(dp)) {
        val w = size.width
        val stroke = w * 0.11f
        drawCircle(color, radius = w * 0.32f, center = Offset(w * 0.42f, w * 0.42f), style = Stroke(stroke))
        drawLine(color, Offset(w * 0.66f, w * 0.66f), Offset(w * 0.92f, w * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun SearchBar(
    query: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    onSearch: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    GlassSurface(modifier.fillMaxWidth(), RoundedCornerShape(20.dp), strength = 1.1f) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            SearchGlyph(ink.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 17.sp),
                cursorBrush = SolidColor(Color(s.accent)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier
                    .weight(1f)
                    .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Txt(hint, color = ink.copy(alpha = 0.5f), size = 17.sp)
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun AppRow(app: AppEntry, onLaunched: () -> Unit = {}) {
    val ctx = LocalContext.current
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .bouncy(onClick = {
                launchApp(ctx, view, app, null)
                onLaunched()
            }, pressedScale = 0.97f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 42.dp)
        Spacer(Modifier.width(14.dp))
        Txt(app.label, size = 16.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// App Library (last page): apps grouped by category, iOS style
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryPage(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    var query by remember { mutableStateOf("") }
    val hidden = s.hiddenIds.toSet()
    val visible = model.apps.filter { it.id !in hidden }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Txt(
            "App Library",
            size = 30.sp,
            weight = FontWeight.Bold,
            shadow = true,
            modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
        )
        SearchBar(query = query, onChange = { query = it }, hint = "Search apps")
        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            val groups = visible
                .groupBy { categoryName(it.category) }
                .toList()
                .sortedBy { CATEGORY_ORDER.indexOf(it.first) }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(groups, key = { it.first }) { (name, list) ->
                    CategoryCard(name = name, apps = list, ui = ui)
                }
            }
        } else {
            val q = query.trim()
            val results = visible.filter { it.label.contains(q, ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { app -> AppRow(app) }
            }
        }
    }
}

@Composable
private fun CategoryCard(name: String, apps: List<AppEntry>, ui: UiState) {
    Column {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .bouncy(onClick = { ui.openCategory = name }),
            shape = RoundedCornerShape(28.dp),
            strength = 0.9f,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                for (row in 0..1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (col in 0..1) {
                            val app = apps.getOrNull(row * 2 + col)
                            Box(Modifier.size(48.dp)) {
                                if (app != null) AppIcon(app, 48.dp)
                            }
                        }
                    }
                }
            }
        }
        Txt(
            name,
            size = 13.sp,
            weight = FontWeight.Medium,
            align = TextAlign.Center,
            shadow = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
fun CategorySheet(name: String, model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    BackHandler { ui.openCategory = null }
    val hidden = s.hiddenIds.toSet()
    val list = model.apps.filter { it.id !in hidden && categoryName(it.category) == name }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .pointerInput(Unit) { detectTapGestures(onTap = { ui.openCategory = null }) },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(20.dp).heightIn(max = 540.dp).swallowTaps(),
            shape = RoundedCornerShape(34.dp),
            strength = 1.4f,
        ) {
            Column(Modifier.padding(18.dp)) {
                Txt(name, size = 20.sp, weight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 12.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(s.cols.coerceAtMost(4))) {
                    items(list, key = { it.id }) { app ->
                        Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            AppCell(app = app, model = model, ui = ui, labels = true)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Spotlight search (swipe down on the home screen)
// ---------------------------------------------------------------------------------------------

private fun webSearch(ctx: Context, q: String) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e2: Exception) {
            // nothing can open a web search
        }
    }
}

@Composable
fun Spotlight(model: AppModel, ui: UiState) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val ink = LocalInk.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler { ui.showSearch = false }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim()
    val results = remember(q, model.apps) {
        if (q.isEmpty()) {
            emptyList()
        } else {
            model.apps
                .filter { it.label.contains(q, ignoreCase = true) }
                .sortedBy { !it.label.startsWith(q, ignoreCase = true) }
                .take(12)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .pointerInput(Unit) { detectTapGestures(onTap = { ui.showSearch = false }) }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(16.dp)
        ) {
            SearchBar(
                query = query,
                onChange = { query = it },
                hint = "Search apps and the web",
                focus = focus,
                modifier = Modifier.swallowTaps(),
                onSearch = {
                    val first = results.firstOrNull()
                    if (first != null) {
                        launchApp(ctx, view, first, null)
                        ui.showSearch = false
                    } else if (q.isNotEmpty()) {
                        webSearch(ctx, q)
                        ui.showSearch = false
                    }
                },
                trailing = {
                    Txt(
                        "Cancel",
                        color = ink.copy(alpha = 0.8f),
                        size = 15.sp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .bouncy(onClick = { ui.showSearch = false }),
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            if (q.isNotEmpty()) {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth().swallowTaps(),
                    shape = RoundedCornerShape(26.dp),
                    strength = 1.3f,
                ) {
                    LazyColumn(Modifier.heightIn(max = 480.dp).padding(vertical = 6.dp)) {
                        items(results, key = { it.id }) { app ->
                            AppRow(app, onLaunched = { ui.showSearch = false })
                        }
                        item {
                            Txt(
                                "Search the web for “$q”",
                                size = 16.sp,
                                color = ink.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncy(onClick = {
                                        webSearch(ctx, q)
                                        ui.showSearch = false
                                    }, pressedScale = 0.97f)
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Long-press menu
// ---------------------------------------------------------------------------------------------

@Composable
private fun MenuRow(text: String, color: Color = LocalInk.current, onClick: () -> Unit) {
    Txt(
        text,
        color = color,
        size = 16.sp,
        modifier = Modifier
            .fillMaxWidth()
            .bouncy(onClick = onClick, pressedScale = 0.98f)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

@Composable
private fun MenuDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(LocalInk.current.copy(alpha = 0.12f)))
}

@Composable
fun AppMenu(app: AppEntry, model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val view = LocalView.current
    BackHandler { ui.menuApp = null }

    val shortcuts = remember(app.id) { model.shortcutsFor(app.pkg) }
    val inDock = app.id in s.dockIds
    val inHome = app.id in s.homeIds

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val t by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f),
        label = "menuIn",
    )

    fun close() { ui.menuApp = null }
    fun newTask(i: Intent) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { /* ignore */ }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f * t))
            .pointerInput(Unit) { detectTapGestures(onTap = { close() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.graphicsLayer {
                alpha = t.coerceIn(0f, 1f)
                scaleX = 0.84f + 0.16f * t
                scaleY = 0.84f + 0.16f * t
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app, 76.dp)
            Txt(app.label, size = 15.sp, weight = FontWeight.SemiBold, shadow = true)

            GlassSurface(
                modifier = Modifier.width(270.dp).swallowTaps(),
                shape = RoundedCornerShape(24.dp),
                strength = 1.4f,
            ) {
                Column {
                    MenuRow("Open") {
                        close()
                        launchApp(ctx, view, app, null)
                    }
                    shortcuts.forEach { sc ->
                        MenuDivider()
                        MenuRow(sc.shortLabel?.toString() ?: sc.id) {
                            close()
                            model.startShortcut(sc)
                        }
                    }
                    MenuDivider()
                    MenuRow("Edit Home Screen") {
                        close()
                        ui.editing = true
                    }
                    MenuDivider()
                    if (inDock) {
                        MenuRow("Remove from Dock") { s.removeFromDock(app.id); close() }
                    } else {
                        MenuRow("Add to Dock") { s.addToDock(app.id); close() }
                    }
                    MenuDivider()
                    if (inHome || inDock) {
                        MenuRow("Remove from Home Screen") { s.removeFromHome(app.id); close() }
                    } else {
                        MenuRow("Add to Home Screen") { s.addToHome(app.id); close() }
                    }
                    MenuDivider()
                    MenuRow("Hide App") { s.hide(app.id); close() }
                    MenuDivider()
                    MenuRow("App Info") {
                        close()
                        newTask(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")))
                    }
                    if (!app.isSystem) {
                        MenuDivider()
                        MenuRow("Uninstall", color = Color(0xFFFF453A)) {
                            close()
                            newTask(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")))
                        }
                    }
                }
            }
        }
    }
}
