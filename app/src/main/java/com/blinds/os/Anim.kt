package com.blinds.os

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The animation system. One style for the whole launcher, switchable in
 * Customize > Animations, and switched off when Android's "remove animations"
 * is on or (optionally) when battery saver is on.
 */
class AnimSpec(val style: String) {
    val enabled: Boolean get() = style != "off"

    private val damping: Float = when (style) {
        "bouncy" -> 0.52f
        "snappy" -> 0.9f
        else -> 0.78f
    }
    private val stiffness: Float = when (style) {
        "snappy" -> 900f
        "bouncy" -> 320f
        else -> 420f
    }

    /** The main motion used for things appearing, moving and settling. */
    fun <T> move(): FiniteAnimationSpec<T> =
        if (enabled) spring(dampingRatio = damping, stiffness = stiffness) else tween(0)

    /** A quick, non-bouncy tween for fades and small changes. */
    fun <T> fade(ms: Int = 220): FiniteAnimationSpec<T> =
        if (enabled) tween(if (style == "snappy") ms / 2 else ms) else tween(0)

    /** Delay between neighbours in a staggered entrance. */
    val stagger: Int get() = when (style) { "off" -> 0; "snappy" -> 14; else -> 30 }
}

val LocalAnim = compositionLocalOf { AnimSpec("smooth") }

/** Works out the style that is really in effect right now. */
@Composable
fun rememberAnimSpec(style: String, saverOff: Boolean): AnimSpec {
    val ctx = LocalContext.current
    return remember(style, saverOff) {
        val systemOff = try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) { false }
        val saver = saverOff && try {
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        } catch (_: Exception) { false }
        AnimSpec(if (systemOff || saver) "off" else style)
    }
}
