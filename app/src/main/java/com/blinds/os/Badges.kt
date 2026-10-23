package com.blinds.os

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat

/** One notification as shown in the Notification Center. Lives in memory only. */
class NoteItem(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val open: android.app.PendingIntent?,
)

object NotifCenter {
    val items = androidx.compose.runtime.mutableStateListOf<NoteItem>()
    var collecting = false
    var service: BadgeService? = null

    fun dismiss(key: String) {
        try { service?.cancelNotification(key) } catch (_: Exception) {}
        items.removeAll { it.key == key }
    }

    fun dismissAll() {
        val keys = items.map { it.key }
        try { service?.cancelAllNotifications() } catch (_: Exception) {}
        items.removeAll { it.key in keys }
    }
}

/** How many notifications each app has right now. Only counts: message text is never read or stored. */
object Badges {
    val counts = mutableStateMapOf<String, Int>()
    var enabled by mutableStateOf(false)      // the user has granted notification access
    var connected by mutableStateOf(false)    // the listener service is running

    fun update(map: Map<String, Int>) {
        counts.keys.retainAll(map.keys)
        counts.putAll(map)
    }

    fun refreshAccess(ctx: Context) {
        enabled = try {
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        } catch (e: Exception) {
            false
        }
        if (enabled && !connected) {
            try {
                NotificationListenerService.requestRebind(ComponentName(ctx, BadgeService::class.java))
            } catch (e: Exception) {
                // not allowed right now
            }
        }
        if (!enabled) counts.clear()
    }
}

class BadgeService : NotificationListenerService() {

    override fun onListenerConnected() {
        Badges.connected = true
        NotifCenter.service = this
        recompute()
    }

    override fun onListenerDisconnected() {
        Badges.connected = false
        if (NotifCenter.service === this) NotifCenter.service = null
    }

    /** Re-reads the active notifications (used when the Notification Center is switched on). */
    fun refresh() = recompute()

    override fun onNotificationPosted(sbn: StatusBarNotification?) = recompute()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recompute()

    private fun recompute() {
        val list = try {
            activeNotifications
        } catch (e: Exception) {
            null
        } ?: return
        val map = HashMap<String, Int>()
        val notes = ArrayList<NoteItem>()
        for (n in list) {
            if (n.packageName == packageName) continue
            val f = n.notification.flags
            if (f and Notification.FLAG_ONGOING_EVENT != 0) continue    // music players, downloads, calls
            if (f and Notification.FLAG_GROUP_SUMMARY != 0) continue    // the "3 new messages" header of a group
            map[n.packageName] = (map[n.packageName] ?: 0) + 1
            // the text is only read when the person turned the Notification Center on, and never saved
            if (NotifCenter.collecting) {
                val ex = n.notification.extras
                notes.add(
                    NoteItem(
                        key = n.key,
                        pkg = n.packageName,
                        title = ex?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                        text = ex?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                        time = n.postTime,
                        open = n.notification.contentIntent,
                    ),
                )
            }
        }
        Badges.update(map)
        if (NotifCenter.collecting) {
            notes.sortByDescending { it.time }
            NotifCenter.items.clear()
            NotifCenter.items.addAll(notes)
        } else if (NotifCenter.items.isNotEmpty()) {
            NotifCenter.items.clear()
        }
    }
}

/** The little count bubble (or dot) in the corner of an icon. */
@Composable
fun BoxScope.BadgeBubble(count: Int) {
    val s = LocalSettings.current
    if (count <= 0 || s.badgeStyle == "off" || !Badges.enabled) return
    val color = Color(s.badgeColor)
    if (s.badgeStyle == "dot") {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 3.dp, y = (-3).dp)
                .size(13.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(color) },
        )
    } else {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 8.dp, y = (-7).dp)
                .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                .clip(CircleShape)
                .drawBehind { drawRect(color) }
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Txt(if (count > 99) "99+" else count.toString(), color = Color.White, size = 12.sp, weight = FontWeight.Bold)
        }
    }
}
