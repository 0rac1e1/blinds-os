package com.blinds.os

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Custom icons live as small PNG files in the app's private storage, one per app. */
object AppOverrides {
    fun iconFile(ctx: Context, appId: String): File {
        val dir = File(ctx.filesDir, "appicons")
        return File(dir, appId.replace(Regex("[^A-Za-z0-9]"), "_") + ".png")
    }

    fun hasIcon(ctx: Context, appId: String): Boolean = iconFile(ctx, appId).exists()

    /** Crops the picked picture to a square and saves it at icon size. */
    fun saveIcon(ctx: Context, appId: String, uri: Uri): Boolean = try {
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        if (src == null) false else {
            val side = minOf(src.width, src.height)
            val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(square, 256, 256, true)
            val f = iconFile(ctx, appId)
            f.parentFile?.mkdirs()
            f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Exception) {
        false
    }

    fun clearIcon(ctx: Context, appId: String) {
        try { iconFile(ctx, appId).delete() } catch (_: Exception) {}
    }
}

/** Customize > Apps: give any app its own name and its own icon picture. */
@Composable
fun AppOverridesSection(model: AppModel) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val id = picked
        if (uri != null && id != null) {
            if (AppOverrides.saveIcon(ctx, id, uri)) {
                s.iconVer += 1
            } else {
                Toast.makeText(ctx, "Couldn't use that picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val current = model.apps.firstOrNull { it.id == picked }
    if (current != null) {
        Group(current.label) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app = current, size = 56.dp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(color = ink, fontSize = 18.sp),
                    cursorBrush = SolidColor(Color(s.accent)),
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ink.copy(alpha = 0.08f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
            ActionRow("Save name") {
                s.setAppName(current.id, name.trim())
                model.refresh()
            }
            ActionRow("Choose icon picture…") { launcher.launch("image/*") }
            if (AppOverrides.hasIcon(ctx, current.id)) {
                ActionRow("Go back to the normal icon") {
                    AppOverrides.clearIcon(ctx, current.id)
                    s.iconVer += 1
                }
            }
            if (s.appNames.containsKey(current.id)) {
                ActionRow("Go back to the normal name") {
                    s.setAppName(current.id, "")
                    model.refresh()
                    name = ""
                }
            }
            ActionRow("Back to the list") { picked = null }
        }
        return
    }

    Group("Pick an app") {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = ink, fontSize = 16.sp),
            cursorBrush = SolidColor(Color(s.accent)),
            decorationBox = { inner ->
                if (query.isEmpty()) Txt("Search apps…", color = ink.copy(alpha = 0.45f), size = 16.sp)
                inner()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ink.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        val q = query.trim().lowercase()
        val changed = model.apps.filter { s.appNames.containsKey(it.id) || AppOverrides.hasIcon(ctx, it.id) }
        val list = (if (q.isEmpty()) changed + model.apps.filter { it !in changed }.take(30) else model.apps.filter { it.label.lowercase().contains(q) }.take(40))
        list.forEach { app ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .bouncy(onClick = {
                        picked = app.id
                        name = app.label
                    }, pressedScale = 0.98f)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(app = app, size = 36.dp)
                Txt(app.label, size = 16.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        if (q.isEmpty()) {
            Txt("Showing apps you changed, then the first few. Search to find any other app.", size = 12.sp, color = ink.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
