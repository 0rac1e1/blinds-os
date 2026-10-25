package com.blinds.os

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown once after an update: the newest patch notes in a glass card. */
@Composable
fun WhatsNew(ui: UiState) {
    val s = LocalSettings.current
    val anim = LocalAnim.current
    var gone by remember { mutableStateOf(false) }
    val p = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val note = PATCH_NOTES.firstOrNull { !it.planned } ?: return
    if (gone || s.lastSeenVersion == BlindsVersion.NAME) return
    if (CustomizeState.show || AboutState.show) return

    LaunchedEffect(Unit) { p.animateTo(1f, anim.move()) }
    LaunchedEffect(closing) {
        if (closing) {
            p.animateTo(0f, anim.fade(160))
            s.lastSeenVersion = BlindsVersion.NAME
            gone = true
        }
    }
    val close = { if (!closing) closing = true }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * p.value.coerceIn(0f, 1f))) }
            .bouncy(onClick = { close() }, pressedScale = 1f),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .graphicsLayer {
                    val v = p.value
                    val sc = 0.9f + 0.1f * v
                    scaleX = sc
                    scaleY = sc
                    alpha = v.coerceIn(0f, 1f)
                }
                .swallowTaps(),
            shape = RoundedCornerShape(32.dp),
            strength = 1.4f,
        ) {
            Column(Modifier.padding(22.dp)) {
                Txt("What's new in ${note.version}", size = 22.sp, weight = FontWeight.Bold)
                Txt(note.title, size = 14.sp, color = LocalInk.current.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    note.items.forEach { line ->
                        Row(Modifier.padding(vertical = 4.dp)) {
                            Txt("•", size = 14.sp, modifier = Modifier.padding(end = 8.dp))
                            Txt(line, size = 14.sp)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                    GlassButton("Got it", onClick = { close() }, accent = true)
                }
            }
        }
    }
}
