package com.blinds.os

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.io.File

/** Talks to Android's own wallpaper: copy it in, or push the Blinds wallpaper out to the lock screen. */
object SystemWallpaper {

    /** Draws the current Blinds wallpaper (a still snapshot, no live effect) at screen size. */
    fun render(ctx: Context, s: LauncherSettings, dark: Boolean): Bitmap {
        val dm = ctx.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(360)
        val h = dm.heightPixels.coerceAtLeast(640)
        val size = Size(w.toFloat(), h.toFloat())
        val img = ImageBitmap(w, h)
        val bd = Backdrop()
        bd.size = size
        bd.custom = if (s.useCustomWallpaper) loadWallpaper(ctx) else null
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), size) {
            drawWallpaper(bd, s, dark)
        }
        return img.asAndroidBitmap()
    }

    /** Sets the lock screen wallpaper. Needs only the normal SET_WALLPAPER permission. */
    fun setLock(ctx: Context, bmp: Bitmap): Boolean = try {
        WallpaperManager.getInstance(ctx).setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Copies Android's current wallpaper (home or lock) into Blinds OS as the custom photo.
     * Returns false when Android won't hand it over (live or Samsung dynamic wallpapers, or no access).
     */
    fun importCurrent(ctx: Context, which: Int): Boolean = try {
        val pfd = WallpaperManager.getInstance(ctx).getWallpaperFile(which)
        val bmp = pfd?.use { BitmapFactory.decodeFileDescriptor(it.fileDescriptor) }
        if (bmp == null) {
            false
        } else {
            File(ctx.filesDir, "wallpaper.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Throwable) {
        false
    }
}
