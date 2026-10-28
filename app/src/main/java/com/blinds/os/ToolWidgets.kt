package com.blinds.os

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------------------
// Music: what is playing, with play/pause/skip (needs notification access)
// ---------------------------------------------------------------------------------------------

class MusicInfo(val title: String, val artist: String, val playing: Boolean, val controller: MediaController?)

private fun readMusic(ctx: Context): MusicInfo? {
    if (!Badges.enabled) return null
    return try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val list = msm.getActiveSessions(ComponentName(ctx, BadgeService::class.java))
        val c = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull() ?: return null
        val md = c.metadata
        MusicInfo(
            md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "",
            c.playbackState?.state == PlaybackState.STATE_PLAYING,
            c,
        )
    } catch (e: Exception) {
        null
    }
}

@Composable
fun MusicBody(spec: WidgetSpec, preview: Boolean) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val info by produceState<MusicInfo?>(null, preview, Badges.enabled) {
        if (preview) {
            value = MusicInfo("Song title", "Artist name", true, null)
        } else {
            while (true) {
                value = readMusic(ctx)
                delay(1500)
            }
        }
    }
    val i = info
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.CenterStart) {
        if (i == null) {
            Txt(
                if (Badges.enabled) "Nothing is playing" else "Tap to turn on notification access and see what's playing",
                size = 14.sp,
                color = ink.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().then(if (Badges.enabled) Modifier else Modifier.bouncy(onClick = { openNotificationAccess(ctx) })),
            )
        } else {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(i.title.ifEmpty { "Playing" }, size = 17.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    Txt(i.artist, size = 13.sp, color = ink.copy(alpha = 0.7f), maxLines = 1)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicButton("‹") { i.controller?.transportControls?.skipToPrevious() }
                    MusicButton(if (i.playing) "❚❚" else "▶") {
                        val tc = i.controller?.transportControls
                        if (i.playing) tc?.pause() else tc?.play()
                    }
                    MusicButton("›") { i.controller?.transportControls?.skipToNext() }
                }
            }
        }
    }
}

@Composable
private fun MusicButton(label: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier
            .size(40.dp)
            .bouncy(onClick = onClick)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Txt(label, size = 16.sp, weight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------------------------------------
// Notes: type straight into the widget
// ---------------------------------------------------------------------------------------------

@Composable
fun NotesBody(spec: WidgetSpec, preview: Boolean) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    // the widget text is saved with line breaks swapped for ¶ because layouts are stored one line each
    val saved = spec.extra.replace('¶', '\n')
    var text by remember(spec.id) { mutableStateOf(if (preview) "Buy milk\nCall mum\nFinish the report" else saved) }
    Box(Modifier.fillMaxSize().padding(14.dp)) {
        if (preview) {
            Txt(text, size = 14.sp)
        } else {
            BasicTextField(
                value = text,
                onValueChange = {
                    text = it
                    s.widget(spec.id)?.let { w -> s.updateWidget(w.copy(extra = it.replace('\n', '¶').take(600))) }
                },
                textStyle = TextStyle(color = ink, fontSize = 14.sp),
                cursorBrush = SolidColor(Color(s.accent)),
                decorationBox = { inner ->
                    if (text.isEmpty()) Txt("Write a note…", color = ink.copy(alpha = 0.45f), size = 14.sp)
                    inner()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quick toggles: flashlight and shortcuts to the Android panels
// ---------------------------------------------------------------------------------------------

@Composable
fun TogglesBody(spec: WidgetSpec, preview: Boolean) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    fun open(action: String) {
        try { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { /* not on this phone */ }
    }
    Row(
        Modifier.fillMaxSize().padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleTile("Light", Torch.isOn && !preview, Modifier.weight(1f)) { if (!preview) Torch.toggle(ctx) }
        ToggleTile("Wi-Fi", false, Modifier.weight(1f)) {
            if (!preview) open(if (android.os.Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
        }
        ToggleTile("Bluetooth", false, Modifier.weight(1f)) { if (!preview) open(Settings.ACTION_BLUETOOTH_SETTINGS) }
        ToggleTile("Display", false, Modifier.weight(1f)) { if (!preview) open(Settings.ACTION_DISPLAY_SETTINGS) }
    }
}

@Composable
private fun ToggleTile(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    Column(
        modifier
            .fillMaxHeight()
            .bouncy(onClick = onClick, pressedScale = 0.94f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (on) Color(s.accent).copy(alpha = 0.85f) else ink.copy(alpha = 0.12f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Txt(label, size = 12.sp, weight = FontWeight.SemiBold, color = if (on) Color.White else ink, maxLines = 1, align = TextAlign.Center)
    }
}

// ---------------------------------------------------------------------------------------------
// Stack: several widgets in one spot, swipe up and down to flip through them
// ---------------------------------------------------------------------------------------------

@Composable
fun StackBody(spec: WidgetSpec, ui: UiState, preview: Boolean) {
    val ink = LocalInk.current
    val parts = remember(spec.extra) {
        spec.extra.split(',').mapNotNull { p ->
            val bits = p.split(':')
            if (bits.size == 2 && bits[0].isNotBlank()) bits[0] to bits[1] else null
        }
    }
    if (parts.isEmpty()) return
    val state = rememberPagerState { parts.size }
    Box(Modifier.fillMaxSize()) {
        VerticalPager(state = state, modifier = Modifier.fillMaxSize()) { page ->
            val (t, st) = parts[page]
            val sub = remember(spec, page) { spec.copy(id = spec.id + "#" + page, type = t, style = st, extra = "") }
            Box(Modifier.fillMaxSize()) { WidgetContent(sub, ui, preview) }
        }
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(parts.size) { i ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = if (i == state.currentPage) 0.9f else 0.3f)),
                )
            }
        }
    }
}
