package com.blinds.os

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How one thing about the phone compares with what Blinds OS wants. */
enum class Grade { GOOD, OK, LOW }

class DeviceCheckRow(val label: String, val value: String, val need: String, val grade: Grade, val note: String)

class DeviceReport(val rows: List<DeviceCheckRow>, val verdict: String, val verdictGrade: Grade, val tips: List<String>)

/** Minimum and recommended specs (shown as text) and the phone's own numbers. */
fun checkDevice(ctx: Context): DeviceReport {
    val rows = ArrayList<DeviceCheckRow>()
    val tips = ArrayList<String>()

    val api = Build.VERSION.SDK_INT
    val androidGrade = when {
        api >= 31 -> Grade.GOOD
        api >= 26 -> Grade.OK
        else -> Grade.LOW
    }
    rows.add(
        DeviceCheckRow(
            "Android", "${Build.VERSION.RELEASE} (API $api)", "8.0 or newer, 12+ recommended", androidGrade,
            when (androidGrade) {
                Grade.GOOD -> "Full liquid glass blur works."
                Grade.OK -> "Works, but the glass blur is not available before Android 12, so panels look flatter."
                Grade.LOW -> "Too old to install Blinds OS."
            },
        ),
    )
    if (androidGrade == Grade.OK) tips.add("On Android 11 and older, glass looks simpler. Raise Frost in Customize > Liquid glass to compensate.")

    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo()
    am.getMemoryInfo(mi)
    val gb = mi.totalMem / 1_073_741_824.0
    // phones report a little less than the number on the box, so the limits sit a bit under 3 and 4
    val ramGrade = when {
        gb >= 3.6 -> Grade.GOOD
        gb >= 2.6 -> Grade.OK
        else -> Grade.LOW
    }
    rows.add(
        DeviceCheckRow(
            "Memory", "%.1f GB".format(gb), "3 GB minimum, 4 GB or more recommended", ramGrade,
            when (ramGrade) {
                Grade.GOOD -> "Plenty for blur, widgets and the live wallpaper."
                Grade.OK -> "Fine for normal use. Heavy live wallpapers may stutter."
                Grade.LOW -> "Below the minimum. Expect reloads and lag."
            },
        ),
    )
    if (ramGrade != Grade.GOOD) tips.add("Set the live effect to None and Animations to Snappy or Off to save memory and power.")

    val cores = Runtime.getRuntime().availableProcessors()
    val cpuGrade = when {
        cores >= 8 -> Grade.GOOD
        cores >= 6 -> Grade.OK
        else -> Grade.LOW
    }
    rows.add(
        DeviceCheckRow(
            "Processor cores", cores.toString(), "6 minimum, 8 recommended", cpuGrade,
            when (cpuGrade) {
                Grade.GOOD -> "Good headroom for glass and animations."
                Grade.OK -> "Fine, though many glass panels at once can feel heavier."
                Grade.LOW -> "Few cores. Turn glass quality down."
            },
        ),
    )
    if (cpuGrade == Grade.LOW) tips.add("In Customize > App icons set Glass quality to Light, and lower Blur in Liquid glass.")

    @Suppress("DEPRECATION")
    val hz = try {
        (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
    } catch (e: Exception) {
        60f
    }
    rows.add(
        DeviceCheckRow(
            "Screen refresh", "${hz.toInt()} Hz", "60 Hz minimum", if (hz >= 59f) Grade.GOOD else Grade.OK,
            if (hz >= 119f) "Smooth. The wallpaper animation can use 60 fps." else "Good. Wallpaper animation runs at up to 60 fps.",
        ),
    )

    val lowRam = am.isLowRamDevice
    rows.add(
        DeviceCheckRow(
            "Low-memory mode", if (lowRam) "On" else "Off", "Off", if (lowRam) Grade.LOW else Grade.GOOD,
            if (lowRam) "Android marks this as a low-memory phone, so Blinds OS may run slowly." else "This phone is not a low-memory model.",
        ),
    )

    val lows = rows.count { it.grade == Grade.LOW }
    val oks = rows.count { it.grade == Grade.OK }
    val (verdict, vg) = when {
        androidGrade == Grade.LOW -> "Not supported on this phone" to Grade.LOW
        lows >= 2 -> "Will likely be slow on this phone" to Grade.LOW
        lows == 1 || oks >= 2 -> "Runs, with some limits" to Grade.OK
        else -> "This phone runs Blinds OS smoothly" to Grade.GOOD
    }
    if (tips.isEmpty()) tips.add("Nothing to change. Everything can stay on its highest settings.")
    return DeviceReport(rows, verdict, vg, tips)
}

/** A tappable header that opens and closes the content under it. */
@Composable
fun DropSection(title: String, subtitle: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val ink = LocalInk.current
    GlassSurface(Modifier.fillMaxWidth(), RoundedCornerShape(26.dp), strength = 1f) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .bouncy(onClick = onToggle, pressedScale = 0.98f)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Txt(title, size = 19.sp, weight = FontWeight.Bold)
                    Txt(subtitle, size = 12.sp, color = ink.copy(alpha = 0.6f))
                }
                Txt(if (open) "⌃" else "⌄", size = 22.sp, color = ink.copy(alpha = 0.6f))
            }
            AnimatedVisibility(
                visible = open,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 16.dp)) { content() }
            }
        }
    }
}

private fun gradeColor(g: Grade) = when (g) {
    Grade.GOOD -> Color(0xFF34C759)
    Grade.OK -> Color(0xFFFFB020)
    Grade.LOW -> Color(0xFFFF453A)
}

/** The body of the "Supported devices" drop-down. */
@Composable
fun SupportedDevicesBody() {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val report = remember { checkDevice(ctx) }

    Txt(
        "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}",
        size = 13.sp,
        color = ink.copy(alpha = 0.6f),
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
        Txt("●", size = 18.sp, color = gradeColor(report.verdictGrade))
        Spacer(Modifier.width(8.dp))
        Txt(report.verdict, size = 17.sp, weight = FontWeight.Bold)
    }

    Txt("Minimum: Android 8.0, 3 GB memory, 6 cores. Recommended: Android 12 or newer, 4 GB or more, 8 cores.", size = 13.sp, color = ink.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 10.dp))

    report.rows.forEach { r ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Txt("●", size = 13.sp, color = gradeColor(r.grade))
                    Spacer(Modifier.width(8.dp))
                    Txt(r.label, size = 15.sp, weight = FontWeight.SemiBold)
                }
                Txt(r.value, size = 15.sp, weight = FontWeight.Medium)
            }
            Txt("Needs: ${r.need}", size = 12.sp, color = ink.copy(alpha = 0.55f), modifier = Modifier.padding(start = 21.dp))
            Txt(r.note, size = 12.sp, color = ink.copy(alpha = 0.75f), modifier = Modifier.padding(start = 21.dp))
        }
    }

    Txt("Tips for this phone", size = 14.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    report.tips.forEach { Txt("•  $it", size = 13.sp, color = ink.copy(alpha = 0.85f), modifier = Modifier.padding(vertical = 2.dp)) }
    Txt(
        "This is an estimate from the phone's memory, cores and Android version. Real speed also depends on what else is running.",
        size = 11.sp,
        color = ink.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 10.dp),
    )
}
