package com.blinds.os

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

val LocalSettings = staticCompositionLocalOf<LauncherSettings> { error("LauncherSettings not provided") }
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalDark = staticCompositionLocalOf { true }
val LocalInk = staticCompositionLocalOf { Color.White }

/** The smooth "iOS app icon" curve: a superellipse, rounder than a rounded rectangle. */
class Superellipse(private val n: Double = 5.0) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val a = size.width / 2f
        val b = size.height / 2f
        val steps = 90
        for (i in 0..steps) {
            val t = i * 2.0 * Math.PI / steps
            val ct = cos(t)
            val st = sin(t)
            val x = a + a * sign(ct) * abs(ct).pow(2.0 / n)
            val y = b + b * sign(st) * abs(st).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

fun iconShapeFor(name: String): Shape = when (name) {
    "circle" -> CircleShape
    "rounded" -> RoundedCornerShape(percent = 24)
    "square" -> RoundedCornerShape(10.dp)
    else -> Superellipse()
}

val CATEGORY_ORDER = listOf(
    "Social", "Entertainment", "Games", "Productivity", "Creativity", "Travel", "Reading & News", "Utilities",
)

fun categoryName(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> "Entertainment"
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    ApplicationInfo.CATEGORY_IMAGE -> "Creativity"
    ApplicationInfo.CATEGORY_MAPS -> "Travel"
    ApplicationInfo.CATEGORY_NEWS -> "Reading & News"
    else -> "Utilities"
}
