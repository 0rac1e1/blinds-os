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

data class AppEntry(
    val id: String,
    val pkg: String,
    val label: String,
    val component: ComponentName,
    val category: Int,
    val isSystem: Boolean,
)

/** Loads the installed-app list and keeps it fresh when apps are installed or removed. */
class AppModel(private val ctx: Context, private val settings: LauncherSettings) {

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun refresh(addedPkg: String? = null) {
        scope.launch {
            val list = withContext(Dispatchers.Default) { load() }
            apps = list
            if (!settings.seeded && list.isNotEmpty()) {
                seed(list)
            } else if (addedPkg != null) {
                settings.appendNew(list.filter { it.pkg == addedPkg }.map { it.id })
            }
        }
    }

    private fun load(): List<AppEntry> {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(query, 0).mapNotNull { ri ->
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
        }.sortedBy { it.label.lowercase() }
    }

    /** First run: put the phone, messages, browser and camera in the dock and fill the home pages. */
    private fun seed(list: List<AppEntry>) {
        val pm = ctx.packageManager
        fun pkgFor(intent: Intent): String? = try {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (e: Exception) {
            null
        }

        val picks = listOfNotNull(
            pkgFor(Intent(Intent.ACTION_DIAL)),
            pkgFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)),
            pkgFor(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)),
            pkgFor(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        ).distinct()

        val dock = picks.mapNotNull { p -> list.firstOrNull { it.pkg == p } }.map { it.id }
        val home = list.map { it.id }.filter { it !in dock }.take(settings.cols * settings.rows * 2)
        settings.setDock(dock)
        settings.setHome(home)
        settings.seeded = true
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
