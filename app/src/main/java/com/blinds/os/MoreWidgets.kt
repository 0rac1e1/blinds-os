package com.blinds.os

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Draws the inside of any widget type. [preview] swaps live data for sample data (used by the picker). */
@Composable
fun WidgetContent(spec: WidgetSpec, ui: UiState, preview: Boolean = false) {
    when (spec.type) {
        "clock" -> ClockBody(spec)
        "weather" -> WeatherBody(spec, ui, preview)
        "battery" -> BatteryBody(spec, preview)
        "date" -> DateBody(spec)
        "calendar" -> CalendarBody(spec, preview)
        else -> Box(Modifier.fillMaxSize())
    }
}

// ---------------------------------------------------------------------------------------------
// Battery
// ---------------------------------------------------------------------------------------------

@Composable
fun BatteryBody(spec: WidgetSpec, preview: Boolean) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val bare = spec.style == "bare"
    var level by remember { mutableIntStateOf(if (preview) 78 else 0) }
    var charging by remember { mutableStateOf(preview) }

    if (!preview) {
        DisposableEffect(Unit) {
            fun read(i: Intent?) {
                if (i == null) return
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (l >= 0) level = (l * 100) / sc
                val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = read(i)
            }
            val sticky = try {
                ctx.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            } catch (e: Exception) {
                null
            }
            read(sticky)
            onDispose {
                try {
                    ctx.unregisterReceiver(receiver)
                } catch (e: Exception) {
                    // already unregistered
                }
            }
        }
    }

    val color = when {
        charging -> Color(0xFF30D158)
        level < 20 -> Color(0xFFFF453A)
        level < 50 -> Color(0xFFFFD60A)
        else -> Color(0xFF30D158)
    }
    val status = if (charging) "Charging" else "On battery"

    if (spec.style == "bar") {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.width(78.dp)) {
                Txt("$level%", color = ink, size = 30.sp, weight = FontWeight.Light, shadow = bare)
                Txt(status, color = ink.copy(alpha = 0.7f), size = 11.sp, maxLines = 1, shadow = bare)
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ink.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(level.coerceIn(2, 100) / 100f)
                        .clip(RoundedCornerShape(50))
                        .background(color),
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().padding(14.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val d = minOf(size.width, size.height) - stroke
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(ink.copy(alpha = 0.18f), -90f, 360f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * level.coerceIn(1, 100) / 100f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt("$level%", color = ink, size = 24.sp, weight = FontWeight.Medium, shadow = bare)
                Txt(if (charging) "Charging" else "Battery", color = ink.copy(alpha = 0.65f), size = 10.sp, shadow = bare)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Date
// ---------------------------------------------------------------------------------------------

@Composable
fun DateBody(spec: WidgetSpec) {
    val ink = LocalInk.current
    val accent = Color(LocalSettings.current.accent)
    val bare = spec.style == "bare"
    val day by produceState(System.currentTimeMillis() / 3_600_000L) {
        while (true) {
            value = System.currentTimeMillis() / 3_600_000L
            delay(60_000)
        }
    }
    val cal = remember(day) { Calendar.getInstance() }
    val cls = spec.cls(LocalSettings.current.cols)

    if (cls == "s") {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(
                SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time).uppercase(),
                color = accent, size = 12.sp, weight = FontWeight.Bold, spacing = 1.2.sp, maxLines = 1, shadow = bare,
            )
            Txt(cal.get(Calendar.DAY_OF_MONTH).toString(), color = ink, size = 54.sp, weight = FontWeight.Light, shadow = bare)
            Txt(SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time), color = ink.copy(alpha = 0.7f), size = 12.sp, maxLines = 1, shadow = bare)
        }
    } else {
        val first = cal.firstDayOfWeek
        val start = (cal.clone() as Calendar).apply {
            while (get(Calendar.DAY_OF_WEEK) != first) add(Calendar.DAY_OF_MONTH, -1)
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.Center) {
            Txt(
                SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time).uppercase(),
                color = ink.copy(alpha = 0.75f), size = 12.sp, weight = FontWeight.SemiBold, spacing = 1.2.sp, shadow = bare,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (i in 0 until 7) {
                    val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
                    val today = d.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR) && d.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(
                            SimpleDateFormat("EEEEE", Locale.getDefault()).format(d.time).uppercase(),
                            color = ink.copy(alpha = 0.6f), size = 11.sp, shadow = bare,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(if (today) accent else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Txt(
                                d.get(Calendar.DAY_OF_MONTH).toString(),
                                color = if (today) Color.White else ink,
                                size = 15.sp,
                                weight = if (today) FontWeight.Bold else FontWeight.Normal,
                                shadow = bare,
                            )
                        }
                    }
                }
            }
            if (cls == "l") {
                Spacer(Modifier.height(18.dp))
                Txt(
                    SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(cal.time),
                    color = ink, size = 22.sp, weight = FontWeight.Light, shadow = bare,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Calendar: next events (needs the calendar permission, asked for inside the widget)
// ---------------------------------------------------------------------------------------------

private class EventItem(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

private fun sampleEvents(): List<EventItem> {
    val now = System.currentTimeMillis()
    val h = 3_600_000L
    return listOf(
        EventItem("Team meeting", now + h, now + 2 * h, false),
        EventItem("Lunch with Sam", now + 3 * h, now + 4 * h, false),
        EventItem("Dentist", now + 26 * h, now + 27 * h, false),
        EventItem("Project review", now + 50 * h, now + 51 * h, false),
        EventItem("Weekend trip", now + 74 * h, now + 98 * h, true),
    )
}

private fun queryEvents(ctx: Context, limit: Int): List<EventItem> {
    val now = System.currentTimeMillis()
    val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
    ContentUris.appendId(b, now)
    ContentUris.appendId(b, now + 14L * 24 * 3_600_000L)
    val out = ArrayList<EventItem>()
    try {
        ctx.contentResolver.query(
            b.build(),
            arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
            ),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val end = c.getLong(2)
                if (end <= now) continue
                out.add(EventItem(c.getString(0) ?: "(No title)", c.getLong(1), end, c.getInt(3) == 1))
            }
        }
    } catch (e: Exception) {
        // calendar not readable
    }
    return out
}

private fun whenText(e: EventItem, is24: Boolean): String {
    if (e.allDay) {
        val day = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(e.begin))
        return "$day  All day"
    }
    val cal = Calendar.getInstance()
    val ev = Calendar.getInstance().apply { timeInMillis = e.begin }
    val dayDiff = ev.get(Calendar.DAY_OF_YEAR) - cal.get(Calendar.DAY_OF_YEAR) + (ev.get(Calendar.YEAR) - cal.get(Calendar.YEAR)) * 365
    val day = when {
        e.begin <= cal.timeInMillis -> "Now"
        dayDiff == 0 -> "Today"
        dayDiff == 1 -> "Tomorrow"
        else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(e.begin))
    }
    val time = SimpleDateFormat(if (is24) "H:mm" else "h:mm a", Locale.getDefault()).format(Date(e.begin))
    return "$day  $time"
}

@Composable
fun CalendarBody(spec: WidgetSpec, preview: Boolean) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val accent = Color(LocalSettings.current.accent)
    val bare = spec.style == "bare"
    val cls = spec.cls(LocalSettings.current.cols)
    var granted by remember {
        mutableStateOf(
            preview || ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val tick by produceState(0L) {
        while (true) {
            value = System.currentTimeMillis() / 60_000L
            delay(60_000)
        }
    }
    val limit = when (cls) {
        "s" -> 1
        "w" -> 2
        else -> 5
    }
    val events by produceState(emptyList<EventItem>(), granted, tick, limit) {
        value = when {
            preview -> sampleEvents().take(limit)
            !granted -> emptyList()
            else -> withContext(Dispatchers.IO) { queryEvents(ctx, limit) }
        }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt("Calendar", color = ink, size = 16.sp, weight = FontWeight.SemiBold, shadow = bare)
            Txt("Show your next events", color = ink.copy(alpha = 0.7f), size = 12.sp, align = TextAlign.Center, shadow = bare)
            Spacer(Modifier.height(8.dp))
            Txt(
                "Allow calendar",
                color = Color.White, size = 13.sp, weight = FontWeight.SemiBold,
                modifier = Modifier
                    .bouncy(onClick = { launcher.launch(Manifest.permission.READ_CALENDAR) })
                    .clip(RoundedCornerShape(50))
                    .background(accent)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        return
    }
    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt("No upcoming events", color = ink.copy(alpha = 0.7f), size = 14.sp, shadow = bare)
        }
        return
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (cls == "l") 12.dp else 8.dp, if (cls == "l") Alignment.Top else Alignment.CenterVertically),
    ) {
        if (cls != "s") {
            Txt("UP NEXT", color = ink.copy(alpha = 0.6f), size = 11.sp, weight = FontWeight.SemiBold, spacing = 1.2.sp, shadow = bare)
        }
        events.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(if (cls == "s") 44.dp else 34.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Spacer(Modifier.width(10.dp))
                Column {
                    Txt(e.title, color = ink, size = if (cls == "s") 14.sp else 15.sp, weight = FontWeight.Medium, maxLines = if (cls == "s") 2 else 1, shadow = bare)
                    Txt(whenText(e, is24), color = ink.copy(alpha = 0.65f), size = 12.sp, maxLines = 1, shadow = bare)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Second city on the clock
// ---------------------------------------------------------------------------------------------

/** Cities offered for a clock's second time zone, as "TimeZoneId;Label". */
val WORLD_CITIES = listOf(
    "" to "None",
    "America/Los_Angeles;Los Angeles" to "Los Angeles",
    "America/Denver;Denver" to "Denver",
    "America/Chicago;Chicago" to "Chicago",
    "America/New_York;New York" to "New York",
    "America/Mexico_City;Mexico City" to "Mexico City",
    "America/Bogota;Bogotá" to "Bogotá",
    "America/Sao_Paulo;São Paulo" to "São Paulo",
    "Europe/London;London" to "London",
    "Europe/Madrid;Madrid" to "Madrid",
    "Europe/Paris;Paris" to "Paris",
    "Europe/Rome;Rome" to "Rome",
    "Africa/Cairo;Cairo" to "Cairo",
    "Europe/Moscow;Moscow" to "Moscow",
    "Asia/Dubai;Dubai" to "Dubai",
    "Asia/Kolkata;Mumbai" to "Mumbai",
    "Asia/Bangkok;Bangkok" to "Bangkok",
    "Asia/Shanghai;Shanghai" to "Shanghai",
    "Asia/Tokyo;Tokyo" to "Tokyo",
    "Australia/Sydney;Sydney" to "Sydney",
    "Pacific/Auckland;Auckland" to "Auckland",
)

/** "London  5:42 PM" for the clock's second city, or null when none is set. */
fun secondCityText(extra: String, now: Long, is24: Boolean): String? {
    val p = extra.split(';')
    if (p.size < 2 || p[0].isEmpty()) return null
    return try {
        val f = SimpleDateFormat(if (is24) "H:mm" else "h:mm a", Locale.getDefault())
        f.timeZone = TimeZone.getTimeZone(p[0])
        "${p[1]}  ${f.format(Date(now))}"
    } catch (e: Exception) {
        null
    }
}
