package com.blinds.os

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * The frame of a built-in app (Blinds OS, Customize): a solid, full-screen page that
 * slides and fades in when opened and out when closed, like a real app.
 */
@Composable
fun AppShell(visible: Boolean, content: @Composable () -> Unit) {
    val anim = LocalAnim.current
    val dark = LocalDark.current
    val accent = Color(LocalSettings.current.accent)
    val p = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(visible) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            p.animateTo(1f, anim.move())
        } else {
            p.animateTo(0f, anim.fade(180))
            shown = false
        }
    }
    if (!shown) return

    val base = if (dark) Color(0xFF0C0E14) else Color(0xFFF3F4F8)
    val bg = androidx.compose.ui.graphics.lerp(base, accent, if (dark) 0.06f else 0.04f)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val v = p.value
                alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * 48.dp.toPx()
                val sc = 0.95f + 0.05f * v
                scaleX = sc
                scaleY = sc
            }
            .background(bg)
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
    ) {
        content()
    }
}
