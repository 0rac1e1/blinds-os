package com.blinds.os

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private fun ago(t: Long): String {
    val m = ((System.currentTimeMillis() - t) / 60000L).coerceAtLeast(0)
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 1440 -> "${m / 60}h"
        else -> "${m / 1440}d"
    }
}

/** A liquid-glass panel of the notifications that are on the phone right now, grouped by app. */
@Composable
fun NotificationCenter(model: AppModel, ui: UiState) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val anim = LocalAnim.current
    val p = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { p.animateTo(1f, anim.move()) }
    LaunchedEffect(closing) {
        if (closing) {
            p.animateTo(0f, anim.fade(180))
            ui.showNotifs = false
        }
    }
    val close = { if (!closing) closing = true }
    BackHandler { close() }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val groups = NotifCenter.items.groupBy { it.pkg }
    val byPkg = model.apps.associateBy { it.pkg }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.42f * p.value.coerceIn(0f, 1f))) }
            .bouncy(onClick = { close() }, pressedScale = 1f),
        contentAlignment = Alignment.TopCenter,
    ) {
        GlassSurface(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .graphicsLayer {
                    val v = p.value
                    translationY = -(1f - v) * 260.dp.toPx()
                    alpha = v.coerceIn(0f, 1f)
                }
                .swallowTaps(),
            shape = RoundedCornerShape(34.dp),
            strength = 1.3f,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Txt("Notifications", size = 22.sp, weight = FontWeight.SemiBold)
                    if (NotifCenter.items.isNotEmpty()) GlassButton("Clear all", onClick = { NotifCenter.dismissAll() })
                }
                Column(
                    Modifier
                        .padding(top = 10.dp)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (!Badges.enabled) {
                        Txt("Turn on notification access to see your notifications here.", color = ink.copy(alpha = 0.7f), size = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
                        GlassButton("Turn on access", onClick = { openNotificationAccess(ctx) }, accent = true)
                    } else if (groups.isEmpty()) {
                        Txt("Nothing new.", color = ink.copy(alpha = 0.6f), size = 15.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
                    } else {
                        groups.forEach { (pkg, list) ->
                            val app = byPkg[pkg]
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (app != null) AppIcon(app = app, size = 22.dp)
                                Txt(
                                    (app?.label ?: pkg) + if (list.size > 1) "  ·  ${list.size}" else "",
                                    size = 12.sp,
                                    weight = FontWeight.SemiBold,
                                    color = ink.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            list.forEach { n ->
                                NoteRow(n, onOpen = {
                                    try { n.open?.send() } catch (_: Exception) {}
                                    if (n.open == null) NotifCenter.dismiss(n.key)
                                    ui.showNotifs = false
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(n: NoteItem, onOpen: () -> Unit) {
    val ink = LocalInk.current
    val anim = LocalAnim.current
    val scope = rememberCoroutineScope()
    val x = remember(n.key) { Animatable(0f) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .graphicsLayer {
                translationX = x.value
                alpha = (1f - kotlin.math.abs(x.value) / 700f).coerceIn(0f, 1f)
            }
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (kotlin.math.abs(x.value) > 220f) {
                                x.animateTo(if (x.value > 0) 900f else -900f, anim.fade(160))
                                NotifCenter.dismiss(n.key)
                            } else {
                                x.animateTo(0f, anim.move())
                            }
                        }
                    },
                    onDragCancel = { scope.launch { x.animateTo(0f, anim.move()) } },
                    onHorizontalDrag = { change, dy ->
                        change.consume()
                        scope.launch { x.snapTo(x.value + dy) }
                    },
                )
            }
            .bouncy(onClick = onOpen, pressedScale = 0.97f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(ink.copy(alpha = 0.08f)) }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                if (n.title.isNotBlank()) Txt(n.title, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
                if (n.text.isNotBlank()) Txt(n.text, size = 13.sp, color = ink.copy(alpha = 0.75f), maxLines = 2)
            }
            Txt(ago(n.time), size = 11.sp, color = ink.copy(alpha = 0.5f), modifier = Modifier.padding(start = 8.dp))
        }
    }
}
