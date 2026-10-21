package com.blinds.os

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Draws the wallpaper full-screen and drives everything that needs a shared clock:
 * the slow wallpaper animation, and the accelerometer that makes the glass light follow your hand.
 */
@Composable
fun BackdropHost(
    backdrop: Backdrop,
    resumed: Boolean,
    freeze: Boolean,
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val s = LocalSettings.current
    val ctx = LocalContext.current

    // wallpaper clock: frame rate and drift speed are settings; paused in battery saver
    LaunchedEffect(s.animatedWallpaper, resumed, freeze, s.wallpaperFps, s.pauseSaver) {
        val saver = s.pauseSaver && (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
        if (s.animatedWallpaper && resumed && !freeze && !saver) {
            val minGap = 1_000_000_000L / s.wallpaperFps.coerceAtLeast(5) - 2_000_000L
            var prev = -1L
            while (true) {
                withFrameNanos { now ->
                    if (prev < 0) prev = now
                    if (now - prev >= minGap) {
                        backdrop.timeSec += (now - prev) / 1_000_000_000f * s.wallpaperSpeed
                        prev = now
                    }
                }
            }
        }
    }

    // custom wallpaper photo
    LaunchedEffect(s.wallpaperVersion, s.useCustomWallpaper) {
        backdrop.custom = if (s.useCustomWallpaper) withContext(Dispatchers.IO) { loadWallpaper(ctx) } else null
    }

    // tilt
    val wantTilt = s.motionLight || s.parallax > 0.01f
    DisposableEffect(wantTilt, resumed) {
        if (!wantTilt || !resumed) return@DisposableEffect onDispose { }
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val nx = (-e.values[0] / 6f).coerceIn(-1f, 1f)
                val ny = (e.values[1] / 6f).coerceIn(-1f, 1f)
                val t = backdrop.tilt
                val tx = t.x + (nx - t.x) * 0.08f
                val ty = t.y + (ny - t.y) * 0.08f
                if (abs(tx - t.x) > 0.004f || abs(ty - t.y) > 0.004f) backdrop.tilt = Offset(tx, ty)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { backdrop.size = Size(it.width.toFloat(), it.height.toFloat()) }
    ) {
        Canvas(Modifier.fillMaxSize()) { drawWallpaper(backdrop, s, dark, live = true) }
        content()
    }
}
