package com.blinds.os

import android.content.Context
import android.net.Uri
import org.json.JSONObject

/** Saves and restores every Blinds OS setting and the home screen layout as one small JSON file. */
object Backup {
    private const val APP = "BlindsOS"
    private const val PREFS = "blinds_os"

    fun export(ctx: Context, uri: Uri): Boolean {
        return try {
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
            val root = JSONObject()
            root.put("app", APP)
            root.put("version", BlindsVersion.NAME)
            root.put("prefs", items)
            val out = ctx.contentResolver.openOutputStream(uri) ?: return false
            out.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun restore(ctx: Context, uri: Uri): Boolean {
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return false
            val text = input.use { String(it.readBytes(), Charsets.UTF_8) }
            val root = JSONObject(text)
            if (root.optString("app") != APP) return false
            val items = root.getJSONObject("prefs")
            val editor = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            editor.clear()
            val keys = items.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = items.getJSONObject(k)
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
