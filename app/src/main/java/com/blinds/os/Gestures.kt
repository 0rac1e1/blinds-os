package com.blinds.os

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.View
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Things shared between the launcher screens and plain functions like launchApp. */
object Hub {
    var settings: LauncherSettings? = null
    /** Set by MainActivity: asks for the screen lock, then opens the app. */
    var lockPrompt: ((AppEntry) -> Unit)? = null
    val unlockedUntil = HashMap<String, Long>()
}

object Torch {
    var isOn by mutableStateOf(false)
        private set

    fun toggle(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            val next = !isOn
            cm.setTorchMode(id, next)
            isOn = next
        } catch (e: Exception) {
            isOn = false
        }
    }
}

val GESTURE_ACTIONS = listOf(
    "none" to "Nothing",
    "search" to "Open search",
    "notifs" to "Notification Center",
    "library" to "App Library",
    "edit" to "Edit home screen",
    "customize" to "Customize",
    "torch" to "Flashlight",
)

/** Runs whatever the person chose for a gesture. */
fun runGesture(
    action: String,
    ctx: Context,
    view: View,
    model: AppModel,
    ui: UiState,
    goLibrary: () -> Unit,
) {
    when {
        action == "search" -> ui.showSearch = true
        action == "notifs" -> ui.showNotifs = true
        action == "library" -> goLibrary()
        action == "edit" -> ui.editing = true
        action == "customize" -> ui.showSettings = true
        action == "torch" -> Torch.toggle(ctx)
        action.startsWith("app:") -> {
            val id = action.removePrefix("app:")
            model.apps.firstOrNull { it.id == id }?.let { launchApp(ctx, view, it, null) }
        }
    }
}

/** A searchable list of apps; tapping one calls [onPick]. */
@Composable
fun AppPickerList(model: AppModel, onPick: (AppEntry) -> Unit) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    var query by remember { mutableStateOf("") }
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
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ink.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
    val q = query.trim().lowercase()
    val list = if (q.isEmpty()) model.apps.take(20) else model.apps.filter { it.label.lowercase().contains(q) }.take(30)
    list.forEach { app ->
        Row(
            Modifier.fillMaxWidth().bouncy(onClick = { onPick(app) }, pressedScale = 0.98f).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app = app, size = 34.dp)
            Txt(app.label, size = 16.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** Customize > Gestures. */
@Composable
fun GesturesSection(model: AppModel) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    var picking by remember { mutableStateOf<Int?>(null) }

    fun get(i: Int) = when (i) { 0 -> s.gDtTop; 1 -> s.gDtMid; else -> s.gDtBottom }
    fun set(i: Int, v: String) { when (i) { 0 -> s.gDtTop = v; 1 -> s.gDtMid = v; else -> s.gDtBottom = v } }
    val names = listOf("Double-tap the top third", "Double-tap the middle third", "Double-tap the bottom third")

    Group("Double-tap on empty space") {
        for (i in 0..2) {
            val cur = get(i)
            val options = if (cur.startsWith("app:")) {
                val label = model.apps.firstOrNull { it.id == cur.removePrefix("app:") }?.label ?: "an app"
                GESTURE_ACTIONS + (cur to "Open $label")
            } else GESTURE_ACTIONS
            ChoiceRow(names[i], options, cur) { set(i, it) }
            ActionRow(if (picking == i) "Cancel" else "Open an app instead…") { picking = if (picking == i) null else i }
            if (picking == i) {
                AppPickerList(model) { app ->
                    set(i, "app:" + app.id)
                    picking = null
                }
            }
        }
        Txt(
            "Gestures only work on empty parts of the home screen, not on icons or widgets. The flashlight toggles on and off.",
            size = 12.sp,
            color = ink.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}
