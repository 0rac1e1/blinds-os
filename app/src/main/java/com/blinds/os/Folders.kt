package com.blinds.os

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** How strong the glass is behind a folder, for each look. */
internal fun folderStrength(style: String): Float = when (style) {
    "clear" -> 0.45f
    "dark" -> 0.9f
    else -> 0.9f
}

/** A colour wash laid over the folder glass for the tinted and dark looks. */
internal fun folderTint(style: String, accent: Int): Color = when (style) {
    "tinted" -> Color(accent).copy(alpha = 0.32f)
    "dark" -> Color(0x66000000)
    else -> Color.Transparent
}

/** A folder on the home screen: a glass tile showing the first few apps inside. */
@Composable
fun FolderCell(folder: FolderSpec, byId: Map<String, AppEntry>, ui: UiState, labels: Boolean = true) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val jiggle = LocalJiggle.current
    val editing = ui.editing
    val flip = if ((folder.id.hashCode() and 1) == 0) 1f else -1f
    val apps = folder.members.mapNotNull { byId[it] }
    val size = s.iconSize.dp
    val shape = RoundedCornerShape((s.iconSize * 0.26f).dp)

    Column(
        Modifier
            .dragSlot(ui, folder.id)
            .graphicsLayer {
                alpha = if (ui.dragId == folder.id) 0.35f else 1f
                rotationZ = if (editing) (jiggle?.value ?: 0f) * flip else 0f
                val sc = if (ui.mergeTarget == folder.id) 1.14f else 1f
                scaleX = sc
                scaleY = sc
            }
            .bouncy(onClick = { ui.openFolderId = folder.id }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            GlassSurface(Modifier.size(size), shape = shape, strength = folderStrength(s.folderStyle)) {
                val mini = size * 0.36f
                val tint = folderTint(s.folderStyle, s.accent)
                Column(
                    Modifier.fillMaxSize().drawBehind { drawRect(tint) }.padding(size * 0.1f),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    for (r in 0..1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            for (c in 0..1) {
                                val app = apps.getOrNull(r * 2 + c)
                                Box(Modifier.size(mini)) {
                                    if (app != null) AppIcon(app = app, size = mini)
                                }
                            }
                        }
                    }
                }
            }
            if (!editing) BadgeBubble(apps.sumOf { Badges.counts[it.pkg] ?: 0 })
            if (editing) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = (-6).dp, y = (-6).dp)
                        .size(24.dp)
                        .bouncy(onClick = { s.dissolveFolder(folder.id) })
                        .clip(CircleShape)
                        .drawBehind { drawRect(Color(0xE6303036)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt("−", color = Color.White, size = 17.sp, weight = FontWeight.Bold)
                }
            }
        }
        if (labels && s.showLabels) {
            Txt(
                text = folder.name,
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

/** The popup that opens when a folder is tapped. */
@Composable
fun FolderSheet(model: AppModel, ui: UiState) {
    val id = ui.openFolderId ?: return
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val view = LocalView.current
    val anim = LocalAnim.current
    val folder = s.folder(id)
    if (folder == null) {
        ui.openFolderId = null
        return
    }
    val byId = model.apps.associateBy { it.id }
    val apps = folder.members.mapNotNull { byId[it] }
    val cols = s.folderCols.coerceIn(3, 5)

    val p = remember(id) { Animatable(0f) }
    var closing by remember(id) { mutableStateOf(false) }
    var popup by remember(id) { mutableStateOf(Rect.Zero) }
    var draggingOut by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { p.animateTo(1f, anim.move()) }
    LaunchedEffect(closing) {
        if (closing) {
            p.animateTo(0f, anim.fade(170))
            ui.openFolderId = null
        }
    }
    val close = { if (!closing) closing = true }
    val tint = folderTint(s.folderStyle, s.accent)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.47f * p.value.coerceIn(0f, 1f))) }
            .bouncy(onClick = { close() }, pressedScale = 1f),
        contentAlignment = Alignment.TopCenter,
    ) {
        GlassSurface(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 90.dp)
                .graphicsLayer {
                    val v = p.value
                    val sc = 0.82f + 0.18f * v
                    scaleX = sc
                    scaleY = sc
                    alpha = v.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0.5f, 0f)
                }
                .onGloballyPositioned { popup = it.boundsInRoot() }
                .swallowTaps(),
            shape = RoundedCornerShape(34.dp),
            strength = folderStrength(s.folderStyle) + 0.4f,
        ) {
            Column(
                Modifier.drawBehind { drawRect(tint) }.padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicTextField(
                    value = folder.name,
                    onValueChange = { s.renameFolder(folder.id, it) },
                    singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color(s.accent)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Txt(
                    if (draggingOut) "Let go outside this box to take it out" else "Tap to open. Hold an app and drag it out to remove it.",
                    color = ink.copy(alpha = 0.6f),
                    size = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                var index = 0
                apps.chunked(cols).forEach { rowApps ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowApps.forEach { app ->
                            FolderMember(
                                app = app,
                                order = index++,
                                popup = { popup },
                                onDragState = { draggingOut = it },
                                onOpen = {
                                    ui.openFolderId = null
                                    launchApp(ctx, view, app, null)
                                },
                                onOut = { s.takeOutOfFolder(folder.id, app.id) },
                            )
                        }
                        repeat(cols - rowApps.size) { Box(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassButton("Remove folder", onClick = {
                        s.dissolveFolder(folder.id)
                        ui.openFolderId = null
                    })
                    GlassButton("Done", onClick = { close() }, accent = true)
                }
            }
        }
    }
}

/** One app inside the open folder. Appears with a little stagger; press and hold, then drag it out of the box to take it out. */
@Composable
private fun RowScope.FolderMember(
    app: AppEntry,
    order: Int,
    popup: () -> Rect,
    onDragState: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onOut: () -> Unit,
) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val anim = LocalAnim.current
    val haptic = LocalHapticFeedback.current
    val appear = remember { Animatable(if (anim.enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (anim.enabled) {
            delay((order * anim.stagger).toLong())
            appear.animateTo(1f, anim.move())
        }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var local by remember { mutableStateOf(Offset.Zero) }
    var shift by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }

    Column(
        Modifier
            .weight(1f)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .zIndex(if (dragging) 2f else 0f)
            .graphicsLayer {
                val a = appear.value
                val sc = (0.55f + 0.45f * a) * (if (dragging) 1.18f else 1f)
                scaleX = sc
                scaleY = sc
                alpha = a.coerceIn(0f, 1f)
                translationX = shift.x
                translationY = shift.y
            }
            .pointerInput(app.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o ->
                        dragging = true
                        onDragState(true)
                        local = o
                        shift = Offset.Zero
                        if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        shift += delta
                        local += delta
                    },
                    onDragEnd = {
                        val outside = !popup().contains(origin + local)
                        dragging = false
                        onDragState(false)
                        shift = Offset.Zero
                        if (outside) onOut()
                    },
                    onDragCancel = {
                        dragging = false
                        onDragState(false)
                        shift = Offset.Zero
                    },
                )
            }
            .bouncy(onClick = onOpen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app = app, size = s.iconSize.dp)
        Txt(app.label, color = ink, size = 11.sp, maxLines = 1, align = TextAlign.Center, shadow = true, modifier = Modifier.padding(top = 6.dp))
    }
}
