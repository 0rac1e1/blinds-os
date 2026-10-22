package com.blinds.os

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.os.Process
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class AppEntry(
    val id: String,
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val category: Int,
    val isSystem: Boolean,
) {
    /** The built-in Customize app (an alias of the launcher itself). */
    val isCustomize: Boolean get() = component.className.endsWith("CustomizeAlias")

    /** The built-in Blinds OS about / patch notes app. */
    val isAbout: Boolean get() = component.className.endsWith("MainActivity") && !isCustomize
}

/** Loads the installed-app list and keeps it fresh when apps are installed or removed. */
class AppModel(private val ctx: Context, private val settings: LauncherSettings) {

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cachePrefs = ctx.getSharedPreferences("blinds_cache", Context.MODE_PRIVATE)

    init {
        // show the last known app list straight away; the real list replaces it a moment later
        apps = readCache()
        preloadIcons(apps)
    }

    private fun readCache(): List<AppEntry> = try {
        (cachePrefs.getString("apps", "") ?: "").split('\n').mapNotNull { line ->
            val p = line.split('\t')
            if (p.size < 5) return@mapNotNull null
            val cn = ComponentName.unflattenFromString(p[0]) ?: return@mapNotNull null
            AppEntry(p[0], p[1], p[2], cn, p[3].toIntOrNull() ?: ApplicationInfo.CATEGORY_UNDEFINED, p[4] == "1")
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeCache(list: List<AppEntry>) {
        val text = list.joinToString("\n") {
            listOf(it.id, it.pkg, it.label.replace('\t', ' ').replace('\n', ' '), it.category, if (it.isSystem) 1 else 0).joinToString("\t")
        }
        cachePrefs.edit().putString("apps", text).apply()
    }

    /** Renders the icons of the dock and the first home page in the background so they are ready when drawn. */
    private fun preloadIcons(list: List<AppEntry>) {
        if (list.isEmpty()) return
        val byId = list.associateBy { it.id }
        val ids = (settings.dockIds + settings.homeIds).filter { it in byId }.take(settings.cols * settings.rows + settings.dockCount)
        val px = (settings.iconSize * ctx.resources.displayMetrics.density).roundToInt()
        val style = if (settings.iconQuality == "off") "default" else settings.iconStyle
        scope.launch(Dispatchers.Default) {
            ids.forEach { id ->
                val app = byId[id] ?: return@forEach
                val key = "${app.id}|$px|${settings.themedIcons}|${settings.accent}|${settings.iconPack}"
                IconCache.load(ctx, app, px, settings.themedIcons, settings.accent, key, pack = settings.iconPack)
                if (style != "default") {
                    if (style == "glass") IconCache.load(ctx, app, px, settings.themedIcons, settings.accent, "$key|bg", LAYER_BG, settings.iconPack)
                    else IconCache.load(ctx, app, px, settings.themedIcons, settings.accent, "$key|mono", LAYER_MONO, settings.iconPack)
                    IconCache.load(ctx, app, px, settings.themedIcons, settings.accent, "$key|fg", LAYER_FG, settings.iconPack)
                }
            }
        }
    }

    fun refresh(addedPkg: String? = null) {
        scope.launch {
            val list = withContext(Dispatchers.Default) { load() }
            apps = list
            if (list.isNotEmpty()) {
                writeCache(list)
                if (addedPkg == null) preloadIcons(list)
            }
            if (!settings.seeded && list.isNotEmpty()) {
                seed(list)
            } else {
                if (addedPkg != null) {
                    settings.appendNew(list.filter { it.pkg == addedPkg }.map { it.id })
                }
                if (!settings.dockChecked && list.isNotEmpty()) repairDock(list)
            }
            if (!settings.aboutPlaced && list.isNotEmpty()) placeAbout(list)
            if (!settings.customizePlaced && list.isNotEmpty()) placeCustomize(list)
            if (list.isNotEmpty()) settings.migrateWidgets()
        }
    }

    /** Fills empty dock slots with the default phone / messages / browser / camera apps, once. */
    private fun repairDock(list: List<AppEntry>) {
        val room = settings.dockCount - settings.dockIds.size
        if (room > 0) {
            dockPicks(list).filter { it !in settings.dockIds }.take(room).forEach { settings.addToDock(it) }
        }
        settings.dockChecked = true
    }

    /** Finds the default phone, messages, browser and camera apps, with known package names as a fallback. */
    private fun dockPicks(list: List<AppEntry>): List<String> {
        val pm = ctx.packageManager
        fun resolve(intent: Intent): String? = try {
            val p = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (p == null || p == "android" || p == ctx.packageName) null else p
        } catch (e: Exception) {
            null
        }

        fun pick(resolved: String?, vararg known: String): String? {
            val candidates = listOfNotNull(resolved) + known.toList()
            val pkg = candidates.firstOrNull { c -> list.any { it.pkg == c } } ?: return null
            return list.first { it.pkg == pkg }.id
        }

        return listOfNotNull(
            pick(
                resolve(Intent(Intent.ACTION_DIAL)),
                "com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer",
            ),
            pick(
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
                "com.samsung.android.messaging", "com.google.android.apps.messaging",
            ),
            pick(
                resolve(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
                "com.sec.android.app.sbrowser", "com.android.chrome",
            ),
            pick(
                resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
                "com.sec.android.app.camera", "com.android.camera", "com.google.android.GoogleCamera",
            ),
        ).distinct()
    }

    private fun load(): List<AppEntry> {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val installed = pm.queryIntentActivities(query, 0).mapNotNull { ri ->
            val ai = ri.activityInfo ?: return@mapNotNull null
            if (ai.packageName == ctx.packageName) return@mapNotNull null
            val cn = ComponentName(ai.packageName, ai.name)
            AppEntry(
                id = cn.flattenToShortString(),
                pkg = ai.packageName,
                label = ri.loadLabel(pm).toString(),
                component = cn,
                category = ai.applicationInfo.category,
                isSystem = (ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }
        // the Blinds OS app itself (opens the in-launcher About / patch notes screen)
        val own = ComponentName(ctx, MainActivity::class.java)
        val ownEntry = AppEntry(
            id = own.flattenToShortString(),
            pkg = ctx.packageName,
            label = "Blinds OS",
            component = own,
            category = ApplicationInfo.CATEGORY_UNDEFINED,
            isSystem = true,
        )
        val cust = ComponentName(ctx.packageName, "com.blinds.os.CustomizeAlias")
        val customizeEntry = AppEntry(
            id = cust.flattenToShortString(),
            pkg = ctx.packageName,
            label = "Customize",
            component = cust,
            category = ApplicationInfo.CATEGORY_UNDEFINED,
            isSystem = true,
        )
        return (installed + ownEntry + customizeEntry).sortedBy { it.label.lowercase() }
    }

    /** One time: put the Blinds OS app first on the home screen. */
    private fun placeAbout(list: List<AppEntry>) {
        val own = list.firstOrNull { it.isAbout }
        if (own != null) {
            settings.setHome(listOf(own.id) + settings.homeIds.filter { it != own.id })
            settings.setDock(settings.dockIds.filter { it != own.id })
        }
        settings.aboutPlaced = true
    }

    /** One time: put the Customize app right after the Blinds OS app. */
    private fun placeCustomize(list: List<AppEntry>) {
        val c = list.firstOrNull { it.isCustomize }
        if (c != null) {
            val home = settings.homeIds.filter { it != c.id }.toMutableList()
            val aboutAt = list.firstOrNull { it.isAbout }?.let { home.indexOf(it.id) } ?: -1
            home.add(if (aboutAt >= 0) aboutAt + 1 else 0, c.id)
            settings.setHome(home)
            settings.setDock(settings.dockIds.filter { it != c.id })
        }
        settings.customizePlaced = true
    }

    /** First run: put the phone, messages, browser and camera in the dock and fill the home pages. */
    private fun seed(list: List<AppEntry>) {
        val dock = dockPicks(list).take(settings.dockCount)
        val home = list.map { it.id }.filter { it !in dock }.take(settings.cols * settings.rows * 2)
        settings.setDock(dock)
        settings.setHome(home)
        settings.seeded = true
        settings.dockChecked = true
    }

    /** Long-press quick actions (needs Blinds OS to be the default home app). */
    fun shortcutsFor(pkg: String): List<ShortcutInfo> = try {
        val la = ctx.getSystemService(LauncherApps::class.java)
        if (la == null || !la.hasShortcutHostPermission()) {
            emptyList()
        } else {
            val q = LauncherApps.ShortcutQuery()
                .setPackage(pkg)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            la.getShortcuts(q, Process.myUserHandle())?.filter { it.isEnabled }?.take(4) ?: emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun startShortcut(info: ShortcutInfo) {
        try {
            ctx.getSystemService(LauncherApps::class.java)?.startShortcut(info, null, null)
        } catch (e: Exception) {
            // shortcut no longer valid, ignore
        }
    }
}
