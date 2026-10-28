package com.blinds.os

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat

class SettingsHit(val label: String, val action: String)

/** Android settings pages the search can jump to. */
val SETTINGS_HITS = listOf(
    SettingsHit("Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
    SettingsHit("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingsHit("Display and brightness", Settings.ACTION_DISPLAY_SETTINGS),
    SettingsHit("Sound and volume", Settings.ACTION_SOUND_SETTINGS),
    SettingsHit("Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS),
    SettingsHit("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingsHit("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingsHit("Apps", Settings.ACTION_APPLICATION_SETTINGS),
    SettingsHit("Default apps", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
    SettingsHit("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsHit("Date and time", Settings.ACTION_DATE_SETTINGS),
    SettingsHit("Language", Settings.ACTION_LOCALE_SETTINGS),
    SettingsHit("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingsHit("Mobile data and network", Settings.ACTION_WIRELESS_SETTINGS),
    SettingsHit("NFC", Settings.ACTION_NFC_SETTINGS),
    SettingsHit("Privacy", Settings.ACTION_PRIVACY_SETTINGS),
    SettingsHit("Security", Settings.ACTION_SECURITY_SETTINGS),
    SettingsHit("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SettingsHit("Notification access", "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"),
)

class ContactHit(val name: String, val uri: Uri)

fun hasContactsPermission(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

/** Up to five contacts whose name contains [q]. Only called after the person allowed contacts search. */
fun contactHits(ctx: Context, q: String): List<ContactHit> {
    if (!hasContactsPermission(ctx)) return emptyList()
    val out = ArrayList<ContactHit>()
    try {
        ctx.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?",
            arrayOf("%$q%"),
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 5) {
                val id = c.getLong(0)
                val key = c.getString(1) ?: continue
                out.add(ContactHit(c.getString(2) ?: "", ContactsContract.Contacts.getLookupUri(id, key)))
            }
        }
    } catch (e: Exception) {
        // contacts not readable right now
    }
    return out
}
