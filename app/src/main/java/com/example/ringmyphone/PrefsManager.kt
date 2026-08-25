package com.example.ringmyphone

import android.content.Context
import java.util.UUID

/**
 * مسئول ذخیره‌سازی دائمی تنظیمات روی خود گوشی (حتی بعد از بستن اپ):
 * - شناسه‌ی یکتای این گوشی (برای اینکه گوشی خودش را در لیست نبیند)
 * - اسم دلخواهی که کاربر برای گوشی خودش انتخاب کرده
 * - لیست گوشی‌های موردعلاقه (Pin شده)
 */
object PrefsManager {

    private const val PREFS_NAME = "ringmyphone_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_FAVORITES = "favorites" // ذخیره به شکل "id::name" در یک Set

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** شناسه‌ی یکتای این گوشی؛ یک‌بار ساخته می‌شود و برای همیشه ثابت می‌ماند */
    fun getOrCreateDeviceId(context: Context): String {
        val p = prefs(context)
        var id = p.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getDeviceName(context: Context, default: String): String {
        return prefs(context).getString(KEY_DEVICE_NAME, null) ?: default
    }

    fun setDeviceName(context: Context, name: String) {
        prefs(context).edit().putString(KEY_DEVICE_NAME, name).apply()
    }

    fun getFavorites(context: Context): Map<String, String> {
        val raw = prefs(context).getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        return raw.mapNotNull {
            val parts = it.split("::", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()
    }

    fun isFavorite(context: Context, id: String): Boolean {
        return getFavorites(context).containsKey(id)
    }

    fun toggleFavorite(context: Context, id: String, name: String) {
        val p = prefs(context)
        val current = (p.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()).toMutableSet()
        val existing = current.find { it.startsWith("$id::") }
        if (existing != null) {
            current.remove(existing)
        } else {
            current.add("$id::$name")
        }
        p.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    // ---- تنظیمات ویجت: هر ویجت روی صفحه اصلی به یک دستگاه خاص وصل می‌شود ----

    private fun widgetDevIdKey(appWidgetId: Int) = "widget_${appWidgetId}_devid"
    private fun widgetDevNameKey(appWidgetId: Int) = "widget_${appWidgetId}_devname"

    fun setWidgetTarget(context: Context, appWidgetId: Int, deviceId: String, deviceName: String) {
        prefs(context).edit()
            .putString(widgetDevIdKey(appWidgetId), deviceId)
            .putString(widgetDevNameKey(appWidgetId), deviceName)
            .apply()
    }

    fun getWidgetTargetId(context: Context, appWidgetId: Int): String? {
        return prefs(context).getString(widgetDevIdKey(appWidgetId), null)
    }

    fun getWidgetTargetName(context: Context, appWidgetId: Int): String? {
        return prefs(context).getString(widgetDevNameKey(appWidgetId), null)
    }

    fun removeWidgetTarget(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(widgetDevIdKey(appWidgetId))
            .remove(widgetDevNameKey(appWidgetId))
            .apply()
    }
}
