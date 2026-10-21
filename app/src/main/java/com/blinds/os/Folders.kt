package com.blinds.os

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A folder on the home screen: a glass tile showing the first few apps inside. */
@Composable
fun FolderCell(folder: FolderSpec, byId: Map<String, AppEntry>, ui: UiState) {
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
            GlassSurface(Modifier.size(size), shape = shape, strength = 0.9f) {
                val mini = size * 0.36f
                Column(
                    Modifier.fillMaxSize().padding(size * 0.1f),
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
        if (s.showLabels) {
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
    val haptic = LocalHapticFeedback.current
    val folder = s.folder(id)
    if (folder == null) {
        ui.openFolderId = null
        return
    }
    val byId = model.apps.associateBy { it.id }
    val apps = folder.members.mapNotNull { byId[it] }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0x77000000)) }
            .bouncy(onClick = { ui.openFolderId = null }),
        contentAlignment = Alignment.TopCenter,
    ) {
        GlassSurface(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 90.dp).swallowTaps(),
            shape = RoundedCornerShape(34.dp),
            strength = 1.3f,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicTextField(
                    value = folder.name,
                    onValueChange = { s.renameFolder(folder.id, it) },
                    singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
                    cursorBrush = SolidColor(Color(s.accent)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                )
                Txt("Tap to open. Press and hold an app to take it out.", color = ink.copy(alpha = 0.6f), size = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
                apps.chunked(4).forEach { rowApps ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowApps.forEach { app ->
                            Column(
                                Modifier
                                    .weight(1f)
                                    .bouncy(
                                        onClick = {
                                            ui.openFolderId = null
                                            launchApp(ctx, view, app, null)
                                        },
                                        onLongClick = {
                                            if (s.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            s.takeOutOfFolder(folder.id, app.id)
                                        },
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(app = app, size = s.iconSize.dp)
                                Txt(app.label, color = ink, size = 11.sp, maxLines = 1, align = TextAlign.Center, shadow = true, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                        repeat(4 - rowApps.size) { Box(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    GlassButton("Remove folder", onClick = {
                        s.dissolveFolder(folder.id)
                        ui.openFolderId = null
                    })
                    GlassButton("Done", onClick = { ui.openFolderId = null }, accent = true)
                }
            }
        }
    }
}
