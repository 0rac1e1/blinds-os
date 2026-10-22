package com.blinds.os

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon packs in the common Nova / ADW format: an app that ships an appfilter.xml mapping each app's
 * component to a drawable in the pack. Apps the pack does not cover keep their normal icon.
 */
object IconPacks {
    class Pack(val pkg: String, val label: String)

    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.anddoes.launcher.THEME",
        "com.fede.launcher.THEME_ICONPACK",
        "com.teslacoilsw.launcher.THEME",
    )

    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val found = LinkedHashMap<String, Pack>()
        for (action in ACTIONS) {
            try {
                for (ri in pm.queryIntentActivities(Intent(action), 0)) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (pkg !in found) found[pkg] = Pack(pkg, ri.loadLabel(pm).toString())
                }
            } catch (e: Exception) {
                // this action is not visible or not supported, skip it
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    private var loadedPack: String? = null
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null

    /** Reads the pack's appfilter once; later lookups are instant. */
    @Synchronized
    private fun ensure(ctx: Context, pack: String) {
        if (loadedPack == pack) return
        val items = HashMap<String, String>()
        var resources: Resources? = null
        try {
            val r = ctx.packageManager.getResourcesForApplication(pack)
            resources = r
            val id = r.getIdentifier("appfilter", "xml", pack)
            val parser: XmlPullParser? = if (id != 0) {
                r.getXml(id)
            } else {
                try {
                    XmlPullParserFactory.newInstance().newPullParser().also {
                        it.setInput(r.assets.open("appfilter.xml"), null)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (parser != null) {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (comp != null && drawable != null) {
                            val a = comp.indexOf('{')
                            val b = comp.lastIndexOf('}')
                            if (a >= 0 && b > a) {
                                val key = comp.substring(a + 1, b)
                                if (key !in items) items[key] = drawable
                            }
                        }
                    }
                    ev = parser.next()
                }
            }
        } catch (e: Exception) {
            // unreadable pack: behave as if no icons are mapped
        }
        map = items
        res = resources
        loadedPack = pack
    }

    /** The pack's drawable for this app, or null when the pack has none (or is not installed). */
    @Synchronized
    fun drawableFor(ctx: Context, pack: String, component: ComponentName): Drawable? {
        ensure(ctx, pack)
        val r = res ?: return null
        val name = map[component.flattenToString()] ?: return null
        return try {
            var id = r.getIdentifier(name, "drawable", pack)
            if (id == 0) id = r.getIdentifier(name, "mipmap", pack)
            if (id == 0) null else r.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }
}
