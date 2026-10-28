package com.blinds.os

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
import org.json.JSONObject

/** Saves and restores every Blinds OS setting and the home screen layout as one small JSON file. */
object Backup {
    private const val APP = "BlindsOS"
    private const val PREFS = "blinds_os"

    /** The whole backup as text: every setting, the layout, and any custom app icon pictures. */
    private fun build(ctx: Context): String {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = JSONObject()
        for (entry in prefs.all.entries) {
            val o = JSONObject()
            when (val v = entry.value) {
                is Boolean -> { o.put("t", "b"); o.put("v", v) }
                is Int -> { o.put("t", "i"); o.put("v", v) }
                is Long -> { o.put("t", "l"); o.put("v", v) }
                is Float -> { o.put("t", "f"); o.put("v", v.toDouble()) }
                is String -> { o.put("t", "s"); o.put("v", v) }
                else -> continue
            }
            items.put(entry.key, o)
        }
        val icons = JSONObject()
        try {
            File(ctx.filesDir, "appicons").listFiles()?.forEach { f ->
                icons.put(f.name, Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
            }
        } catch (e: Exception) {
            // icons are optional
        }
        val root = JSONObject()
        root.put("app", APP)
        root.put("version", BlindsVersion.NAME)
        root.put("prefs", items)
        root.put("icons", icons)
        return root.toString(2)
    }

    fun export(ctx: Context, uri: Uri): Boolean {
        return try {
            val out = ctx.contentResolver.openOutputStream(uri) ?: return false
            out.use { it.write(build(ctx).toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---- automatic copy kept inside the app, refreshed about once a day ----

    fun autoFile(ctx: Context) = File(ctx.filesDir, "auto-backup.json")

    fun auto(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("autoBackup", true)) return
            val now = System.currentTimeMillis()
            if (now - prefs.getLong("autoBackupAt", 0L) < 20L * 3600_000L) return
            autoFile(ctx).writeText(build(ctx))
            prefs.edit().putLong("autoBackupAt", now).apply()
        } catch (e: Exception) {
            // best effort
        }
    }

    fun autoTime(ctx: Context): Long =
        if (autoFile(ctx).exists()) ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("autoBackupAt", 0L) else 0L

    fun restoreAuto(ctx: Context): Boolean = try {
        restoreText(ctx, autoFile(ctx).readText())
    } catch (e: Exception) {
        false
    }

    private fun dropHosted(v: String): String =
        v.split('\n').filterNot { it.startsWith("a:") }.joinToString("\n")

    fun restore(ctx: Context, uri: Uri): Boolean {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return false
            restoreText(ctx, input.use { String(it.readBytes(), Charsets.UTF_8) })
        } catch (e: Exception) {
            false
        }
    }

    private fun restoreText(ctx: Context, text: String): Boolean {
        return try {
            val root = JSONObject(text)
            if (root.optString("app") != APP) return false
            val items = root.getJSONObject("prefs")
            root.optJSONObject("icons")?.let { icons ->
                val dir = File(ctx.filesDir, "appicons")
                dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val names = icons.keys()
                while (names.hasNext()) {
                    val n = names.next()
                    File(dir, n.replace("/", "_")).writeBytes(Base64.decode(icons.getString(n), Base64.NO_WRAP))
                }
            }
            val editor = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            editor.clear()
            val keys = items.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                // real widgets from other apps are tied to this install, so they have to be added again
                if (k == "awidgets") continue
                val o = items.getJSONObject(k)
                if (o.getString("t") == "s" && (k == "home" || k == "dock" || k == "grid")) {
                    editor.putString(k, dropHosted(o.getString("v")))
                    continue
                }
                when (o.getString("t")) {
                    "b" -> editor.putBoolean(k, o.getBoolean("v"))
                    "i" -> editor.putInt(k, o.getInt("v"))
                    "l" -> editor.putLong(k, o.getLong("v"))
                    "f" -> editor.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> editor.putString(k, o.getString("v"))
                }
            }
            editor.commit()
        } catch (e: Exception) {
            false
        }
    }
}
