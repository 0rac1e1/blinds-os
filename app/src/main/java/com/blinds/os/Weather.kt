package com.blinds.os

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class HourW(val time: String, val temp: Int, val code: Int)
class DayW(val date: String, val hi: Int, val lo: Int, val code: Int)
class WeatherData(
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hours: List<HourW>,
    val days: List<DayW>,
)

class CityResult(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Weather from Open-Meteo.com (free, no key). The last answer is cached so the widget works offline. */
object Weather {
    var data by mutableStateOf<WeatherData?>(null)
    var failed by mutableStateOf(false)
    private var busy = false

    fun reset() {
        data = null
        failed = false
    }

    suspend fun refresh(s: LauncherSettings, force: Boolean = false) {
        if (s.weatherLat.isEmpty() || s.weatherLon.isEmpty()) return
        val key = "${s.weatherLat},${s.weatherLon},${s.weatherUnit}"
        val cached = s.weatherJson.isNotEmpty() && s.weatherKey == key
        if (data == null && cached) data = parse(s.weatherJson)
        val age = System.currentTimeMillis() - (s.weatherTime.toLongOrNull() ?: 0L)
        if (cached && !force && age < 25 * 60_000L) return
        if (busy) return
        busy = true
        try {
            val json = withContext(Dispatchers.IO) { download(s.weatherLat, s.weatherLon, s.weatherUnit) }
            val parsed = json?.let { parse(it) }
            if (json != null && parsed != null) {
                s.weatherJson = json
                s.weatherKey = key
                s.weatherTime = System.currentTimeMillis().toString()
                data = parsed
                failed = false
            } else {
                failed = true
            }
        } finally {
            busy = false
        }
    }

    private fun download(lat: String, lon: String, unit: String): String? = try {
        val u = if (unit == "c") "celsius" else "fahrenheit"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day" +
            "&hourly=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&temperature_unit=$u&timezone=auto&forecast_days=6"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) {
        null
    }

    private fun parse(json: String): WeatherData? = try {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val curTime = cur.getString("time")
        val hourly = o.getJSONObject("hourly")
        val ht = hourly.getJSONArray("time")
        val hv = hourly.getJSONArray("temperature_2m")
        val hc = hourly.getJSONArray("weather_code")
        val floor = curTime.substring(0, 13) + ":00"
        val hours = mutableListOf<HourW>()
        for (i in 0 until ht.length()) {
            if (ht.getString(i) >= floor && hours.size < 6) {
                hours += HourW(ht.getString(i), hv.getDouble(i).roundToInt(), hc.getInt(i))
            }
        }
        val daily = o.getJSONObject("daily")
        val dt = daily.getJSONArray("time")
        val dc = daily.getJSONArray("weather_code")
        val dh = daily.getJSONArray("temperature_2m_max")
        val dl = daily.getJSONArray("temperature_2m_min")
        val days = (0 until minOf(dt.length(), 5)).map {
            DayW(dt.getString(it), dh.getDouble(it).roundToInt(), dl.getDouble(it).roundToInt(), dc.getInt(it))
        }
        WeatherData(
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            hours = hours,
            days = days,
        )
    } catch (e: Exception) {
        null
    }

    suspend fun searchCity(q: String): List<CityResult> = withContext(Dispatchers.IO) {
        try {
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(q, "UTF-8")}&count=6&language=en&format=json"
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) return@withContext emptyList()
            val arr = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results")
                ?: return@withContext emptyList()
            (0 until arr.length()).map {
                val r = arr.getJSONObject(it)
                CityResult(
                    name = r.getString("name"),
                    detail = listOf(r.optString("admin1"), r.optString("country")).filter { s -> s.isNotEmpty() }.joinToString(", "),
                    lat = r.getDouble("latitude"),
                    lon = r.getDouble("longitude"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

fun conditionText(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "Weather"
}

private fun DrawScope.cloud(cx: Float, cy: Float, w: Float, color: Color) {
    drawCircle(color, w * 0.22f, Offset(cx - w * 0.2f, cy))
    drawCircle(color, w * 0.3f, Offset(cx + w * 0.02f, cy - w * 0.1f))
    drawCircle(color, w * 0.2f, Offset(cx + w * 0.25f, cy + w * 0.02f))
    drawRoundRect(color, Offset(cx - w * 0.4f, cy - w * 0.02f), Size(w * 0.8f, w * 0.22f), CornerRadius(w * 0.11f))
}

private fun DrawScope.sun(c: Offset, r: Float) {
    val col = Color(0xFFFFC83D)
    drawCircle(col, r, c)
    for (i in 0 until 8) {
        val a = Math.toRadians(i * 45.0)
        val ca = Math.cos(a).toFloat()
        val sa = Math.sin(a).toFloat()
        drawLine(col, Offset(c.x + ca * r * 1.35f, c.y + sa * r * 1.35f), Offset(c.x + ca * r * 1.75f, c.y + sa * r * 1.75f), strokeWidth = r * 0.22f, cap = StrokeCap.Round)
    }
}

/** Weather icons, drawn in code. */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val cx = w / 2f
        val cy = w / 2f
        val white = Color(0xFFF4F7FB)
        val grey = Color(0xFFC3CBD8)
        val rainy = code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)
        val snowy = code in listOf(71, 73, 75, 77, 85, 86)
        val storm = code >= 95
        when {
            code == 0 || code == 1 -> {
                if (isDay) sun(Offset(cx, cy), w * 0.22f)
                else drawCircle(Color(0xFFE8EAF2), w * 0.26f, Offset(cx, cy))
            }
            code == 2 -> {
                if (isDay) sun(Offset(cx - w * 0.14f, cy - w * 0.14f), w * 0.16f)
                else drawCircle(Color(0xFFE8EAF2), w * 0.2f, Offset(cx - w * 0.14f, cy - w * 0.14f))
                cloud(cx + w * 0.06f, cy + w * 0.1f, w * 0.8f, white)
            }
            code == 3 -> cloud(cx, cy, w * 0.9f, white)
            code == 45 || code == 48 -> {
                cloud(cx, cy - w * 0.12f, w * 0.8f, grey)
                for (i in 0..2) {
                    val y = cy + w * (0.18f + i * 0.12f)
                    drawLine(grey, Offset(cx - w * 0.3f, y), Offset(cx + w * 0.3f, y), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                }
            }
            rainy || snowy || storm -> {
                cloud(cx, cy - w * 0.1f, w * 0.85f, if (snowy) white else grey)
                if (storm) {
                    val p = Path().apply {
                        moveTo(cx + w * 0.04f, cy + w * 0.1f)
                        lineTo(cx - w * 0.1f, cy + w * 0.34f)
                        lineTo(cx, cy + w * 0.34f)
                        lineTo(cx - w * 0.06f, cy + w * 0.5f)
                        lineTo(cx + w * 0.14f, cy + w * 0.26f)
                        lineTo(cx + w * 0.04f, cy + w * 0.26f)
                        close()
                    }
                    drawPath(p, Color(0xFFFFC83D))
                } else if (snowy) {
                    for (i in 0..2) drawCircle(Color.White, w * 0.045f, Offset(cx - w * 0.22f + i * w * 0.22f, cy + w * (0.28f + (i % 2) * 0.1f)))
                } else {
                    for (i in 0..2) {
                        val x = cx - w * 0.22f + i * w * 0.22f
                        drawLine(Color(0xFF5AB4FF), Offset(x, cy + w * 0.2f), Offset(x - w * 0.05f, cy + w * 0.36f), strokeWidth = w * 0.06f, cap = StrokeCap.Round)
                    }
                }
            }
            else -> cloud(cx, cy, w * 0.9f, white)
        }
    }
}

private fun hourLabel(time: String, is24: Boolean): String {
    val h = time.substring(11, 13).toIntOrNull() ?: return ""
    return if (is24) "%02d".format(h) else when {
        h == 0 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }
}

private fun dayLabel(date: String, index: Int): String {
    if (index == 0) return "Today"
    return try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        SimpleDateFormat("EEE", Locale.getDefault()).format(d!!)
    } catch (e: Exception) {
        date
    }
}

private val SAMPLE_WEATHER = WeatherData(
    temp = 72,
    code = 1,
    isDay = true,
    hours = listOf(
        HourW("2026-01-01T09:00", 68, 1), HourW("2026-01-01T10:00", 70, 1), HourW("2026-01-01T11:00", 72, 2),
        HourW("2026-01-01T12:00", 74, 2), HourW("2026-01-01T13:00", 75, 3), HourW("2026-01-01T14:00", 75, 3),
    ),
    days = listOf(
        DayW("2026-01-01", 76, 62, 1), DayW("2026-01-02", 74, 61, 2), DayW("2026-01-03", 71, 59, 61),
        DayW("2026-01-04", 70, 58, 3), DayW("2026-01-05", 73, 60, 1), DayW("2026-01-06", 75, 62, 0),
    ),
)

@Composable
fun WeatherBody(spec: WidgetSpec, ui: UiState, preview: Boolean = false) {
    val s = LocalSettings.current
    val ink = LocalInk.current
    val ctx = LocalContext.current
    val hasCity = preview || s.weatherLat.isNotEmpty()
    val bare = spec.style == "bare"

    if (!preview) {
        LaunchedEffect(s.weatherLat, s.weatherLon, s.weatherUnit) {
            while (true) {
                Weather.refresh(s)
                delay(30 * 60_000L)
            }
        }
    }

    if (!hasCity) {
        Column(
            Modifier.fillMaxSize().bouncy(onClick = {
                if (!ui.editing) {
                    ui.addWeatherAfterCity = false
                    ui.showCity = true
                }
            }).padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt("Weather", color = ink, size = 18.sp, weight = FontWeight.SemiBold, shadow = bare)
            Txt("Tap to choose a city", color = ink.copy(alpha = 0.7f), size = 13.sp, shadow = bare)
        }
        return
    }

    val d = if (preview) SAMPLE_WEATHER else Weather.data
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt(if (Weather.failed) "No connection" else "Loading…", color = ink.copy(alpha = 0.7f), size = 14.sp, shadow = bare)
        }
        return
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(ctx)
    val unitSign = "°"
    val cls = spec.cls(s.cols)
    val small = cls == "s"
    val large = cls == "l"
    val today = d.days.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = if (small) 12.dp else 20.dp, vertical = if (small) 10.dp else 14.dp)) {
        if (small) {
            Txt(if (preview) "Your city" else s.weatherName, color = ink, size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1, shadow = bare)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                WeatherIcon(d.code, d.isDay, 36.dp)
                Spacer(Modifier.width(6.dp))
                Txt("${d.temp}$unitSign", color = ink, size = 34.sp, weight = FontWeight.Light, shadow = bare)
            }
            Txt(conditionText(d.code), color = ink.copy(alpha = 0.8f), size = 12.sp, maxLines = 1, shadow = bare)
            if (today != null) Txt("H:${today.hi}$unitSign  L:${today.lo}$unitSign", color = ink.copy(alpha = 0.65f), size = 11.sp, shadow = bare)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(if (preview) "Your city" else s.weatherName, color = ink, size = 15.sp, weight = FontWeight.SemiBold, maxLines = 1, shadow = bare)
                    Txt("${d.temp}$unitSign", color = ink, size = if (large) 64.sp else 48.sp, weight = FontWeight.Light, shadow = bare)
                    Txt(
                        conditionText(d.code) + if (today != null) "  H:${today.hi}$unitSign L:${today.lo}$unitSign" else "",
                        color = ink.copy(alpha = 0.8f), size = 13.sp, maxLines = 1, shadow = bare,
                    )
                }
                WeatherIcon(d.code, d.isDay, if (large) 84.dp else 64.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                d.hours.take(if (large) 6 else 5).forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(hourLabel(h.time, is24), color = ink.copy(alpha = 0.7f), size = 11.sp, shadow = bare)
                        WeatherIcon(h.code, h.time.substring(11, 13).toIntOrNull()?.let { it in 6..18 } ?: true, 26.dp)
                        Txt("${h.temp}$unitSign", color = ink, size = 13.sp, weight = FontWeight.Medium, shadow = bare)
                    }
                }
            }
            if (large) {
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                    d.days.forEachIndexed { i, day ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Txt(dayLabel(day.date, i), color = ink, size = 14.sp, modifier = Modifier.width(64.dp), shadow = bare)
                            WeatherIcon(day.code, true, 24.dp)
                            Spacer(Modifier.weight(1f))
                            Txt("${day.lo}$unitSign", color = ink.copy(alpha = 0.6f), size = 14.sp, shadow = bare)
                            Spacer(Modifier.width(14.dp))
                            Txt("${day.hi}$unitSign", color = ink, size = 14.sp, weight = FontWeight.Medium, shadow = bare)
                        }
                    }
                }
            }
        }
    }
}

/** Choose the weather city and units. */
@Composable
fun CityPicker(ui: UiState) {
    if (!ui.showCity) return
    val s = LocalSettings.current
    val ink = LocalInk.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CityResult>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searched = false
        } else {
            delay(450)
            results = Weather.searchCity(query.trim())
            searched = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color(0x88000000)) }
            .bouncy(onClick = { ui.showCity = false; ui.addWeatherAfterCity = false }),
        contentAlignment = Alignment.TopCenter,
    ) {
        GlassSurface(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 70.dp).swallowTaps(),
            strength = 1.2f,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("Weather city", color = ink, size = 20.sp, weight = FontWeight.SemiBold)
                if (s.weatherName.isNotEmpty()) Txt("Now: ${s.weatherName}", color = ink.copy(alpha = 0.7f), size = 13.sp)
                SearchBar(query = query, onChange = { query = it }, hint = "Search for a city")
                ChoiceRow("Units", listOf("f" to "Fahrenheit °F", "c" to "Celsius °C"), s.weatherUnit) {
                    s.weatherUnit = it
                    Weather.reset()
                }
                Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    results.forEach { r ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .bouncy(onClick = {
                                    s.weatherName = r.name
                                    s.weatherLat = r.lat.toString()
                                    s.weatherLon = r.lon.toString()
                                    s.weatherKey = ""
                                    Weather.reset()
                                    if (ui.addWeatherAfterCity) {
                                        placeWidgetVariant(
                                            s, ui,
                                            ui.pendingVariant ?: WidgetVariant("Wide", "weather", "card", FULL_WIDTH, 2),
                                        )
                                        ui.pendingVariant = null
                                        ui.addWeatherAfterCity = false
                                    }
                                    ui.showCity = false
                                })
                                .padding(vertical = 9.dp),
                        ) {
                            Txt(r.name, color = ink, size = 16.sp, weight = FontWeight.Medium)
                            if (r.detail.isNotEmpty()) Txt(r.detail, color = ink.copy(alpha = 0.6f), size = 13.sp)
                        }
                    }
                    if (searched && results.isEmpty()) {
                        Txt("No cities found (or no connection)", color = ink.copy(alpha = 0.6f), size = 13.sp)
                    }
                }
                Txt("Weather data by Open-Meteo.com", color = ink.copy(alpha = 0.5f), size = 11.sp)
                GlassButton("Close", onClick = { ui.showCity = false; ui.addWeatherAfterCity = false })
            }
        }
    }
}
