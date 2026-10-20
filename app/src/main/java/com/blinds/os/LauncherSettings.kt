package com.blinds.os

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** One persisted, Compose-observable setting. Reading it inside a composable subscribes to changes. */
internal class Pref<T : Any>(
    private val prefs: SharedPreferences,
    private val key: String,
    private val default: T,
) : ReadWriteProperty<Any?, T> {

    private var state by mutableStateOf(load())

    @Suppress("UNCHECKED_CAST")
    private fun load(): T = when (default) {
        is Boolean -> prefs.getBoolean(key, default)
        is Int -> prefs.getInt(key, default)
        is Float -> prefs.getFloat(key, default)
        is String -> prefs.getString(key, default) ?: default
        else -> default
    } as T

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = state

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state = value
        val e = prefs.edit()
        when (value) {
            is Boolean -> e.putBoolean(key, value)
            is Int -> e.putInt(key, value)
            is Float -> e.putFloat(key, value)
            is String -> e.putString(key, value)
        }
        e.apply()
    }

    fun reset() {
        state = default
        prefs.edit().remove(key).apply()
    }
}

/**
 * Every customization option in Blinds OS lives here so the settings sheet,
 * the glass engine and the home screen all read from one place.
 */
class LauncherSettings(private val prefs: SharedPreferences) {

    private val resettable = mutableListOf<Pref<*>>()

    private fun <T : Any> pref(key: String, default: T, resets: Boolean = true): Pref<T> =
        Pref(prefs, key, default).also { if (resets) resettable += it }

    // ---------- Liquid glass ----------
    var glassBlur by pref("glassBlur", 24f)                 // blur radius in dp
    var glassTint by pref("glassTint", 0.14f)               // how milky the glass is
    var glassTintArgb by pref("glassTintArgb", 0xFFFFFFFF.toInt())
    var glassEdge by pref("glassEdge", 0.8f)                // brightness of the rim light + bloom
    var glassDepth by pref("glassDepth", 0.6f)              // drop shadow strength
    var glassLight by pref("glassLight", 225f)              // degrees, 225 = light from the top-left
    var motionLight by pref("motionLight", true)            // light follows how you tilt the phone

    // ---------- Icons ----------
    var iconShape by pref("iconShape", "squircle")          // squircle | circle | rounded | square
    var iconSize by pref("iconSize", 60f)
    var iconGlass by pref("iconGlass", true)                // glossy shine on icons
    var themedIcons by pref("themedIcons", false)           // Android 13+ monochrome icons in accent colour
    var showLabels by pref("showLabels", true)

    // ---------- Layout ----------
    var cols by pref("cols", 4)
    var rows by pref("rows", 6)
    var dockCount by pref("dockCount", 4)
    var cornerRadius by pref("cornerRadius", 28f)
    var clockWidget by pref("clockWidget", true)
    var clockStyle by pref("clockStyle", "card")            // card | bare
    var appLibrary by pref("appLibrary", true)

    // ---------- Wallpaper ----------
    var wallpaper by pref("wallpaper", "aurora")
    var useCustomWallpaper by pref("useCustomWallpaper", false)
    var wallpaperVersion by pref("wallpaperVersion", 0)
    var animatedWallpaper by pref("animatedWallpaper", true)
    var parallax by pref("parallax", 0.6f)

    // ---------- Theme ----------
    var theme by pref("theme", "system")                    // system | dark | light
    var accent by pref("accent", 0xFF0A84FF.toInt())

    // ---------- Behaviour ----------
    var haptics by pref("haptics", true)
    var swipeSearch by pref("swipeSearch", true)

    // ---------- Layout data (not touched by "reset appearance") ----------
    var seeded by pref("seeded", false, resets = false)
    var dockChecked by pref("dockChecked", false, resets = false)
    var aboutPlaced by pref("aboutPlaced", false, resets = false)
    private var homeRaw by pref("home", "", resets = false)
    private var dockRaw by pref("dock", "", resets = false)
    private var hiddenRaw by pref("hidden", "", resets = false)

    val homeIds: List<String> get() = homeRaw.unpack()
    val dockIds: List<String> get() = dockRaw.unpack()
    val hiddenIds: List<String> get() = hiddenRaw.unpack()

    fun setHome(list: List<String>) { homeRaw = list.pack() }
    fun setDock(list: List<String>) { dockRaw = list.pack() }

    fun resetAppearance() {
        resettable.forEach { it.reset() }
    }

    fun resetLayout() {
        homeRaw = ""
        dockRaw = ""
        hiddenRaw = ""
        seeded = false
        aboutPlaced = false
    }

    // ---------- Arranging apps ----------

    fun appendNew(ids: List<String>) {
        val have = homeIds.toSet() + dockIds.toSet()
        val add = ids.filter { it !in have }
        if (add.isNotEmpty()) setHome(homeIds + add)
    }

    fun addToHome(id: String) {
        removeFromDock(id, toHome = false)
        if (id !in homeIds) setHome(homeIds + id)
    }

    fun removeFromHome(id: String) {
        setHome(homeIds - id)
        setDock(dockIds - id)
    }

    fun addToDock(id: String) {
        val dock = dockIds.toMutableList()
        val home = homeIds.toMutableList()
        home.remove(id)
        dock.remove(id)
        dock.add(id)
        while (dock.size > dockCount) home.add(dock.removeAt(0))
        setDock(dock)
        setHome(home)
    }

    fun removeFromDock(id: String, toHome: Boolean = true) {
        setDock(dockIds - id)
        if (toHome && id !in homeIds) setHome(homeIds + id)
    }

    /** If the dock got smaller, push the extra apps back onto the home screen instead of losing them. */
    fun normalizeDock() {
        val d = dockIds
        if (d.size > dockCount) {
            setHome(homeIds + d.drop(dockCount).filter { it !in homeIds })
            setDock(d.take(dockCount))
        }
    }

    fun hide(id: String) {
        removeFromHome(id)
        if (id !in hiddenIds) hiddenRaw = (hiddenIds + id).pack()
    }

    fun unhideAll() {
        val back = hiddenIds
        hiddenRaw = ""
        setHome(homeIds + back.filter { it !in homeIds && it !in dockIds })
    }

    /** Swaps two apps' positions, whether they are on the home pages or in the dock. */
    fun swap(a: String, b: String) {
        val home = homeIds.toMutableList()
        val dock = dockIds.toMutableList()
        val la = listOf(home, dock).firstOrNull { a in it } ?: return
        val lb = listOf(home, dock).firstOrNull { b in it } ?: return
        val ia = la.indexOf(a)
        val ib = lb.indexOf(b)
        la[ia] = b
        lb[ib] = a
        setHome(home)
        setDock(dock)
    }

    private fun String.unpack(): List<String> = if (isEmpty()) emptyList() else split('\n')
    private fun List<String>.pack(): String = joinToString("\n")
}
