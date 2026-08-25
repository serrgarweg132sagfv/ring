package com.example.ringmyphone

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * ویجت روی صفحه‌ی اصلی گوشی: با یک لمس، مستقیم (بدون باز کردن اپ) به یک
 * گوشی از‌پیش‌انتخاب‌شده زنگ می‌زند.
 */
class RingWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_RING_FROM_WIDGET = "com.example.ringmyphone.ACTION_RING_FROM_WIDGET"
        const val EXTRA_WIDGET_ID = "extra_widget_id"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val name = PrefsManager.getWidgetTargetName(context, appWidgetId) ?: "تنظیم نشده (لمس کن)"
            val views = RemoteViews(context.packageName, R.layout.widget_ring)
            views.setTextViewText(R.id.tvWidgetDeviceName, name)

            val intent = Intent(context, RingWidgetProvider::class.java).apply {
                action = ACTION_RING_FROM_WIDGET
                putExtra(EXTRA_WIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_RING_FROM_WIDGET) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val serviceIntent = Intent(context, WidgetRingService::class.java).apply {
                putExtra(WidgetRingService.EXTRA_WIDGET_ID, appWidgetId)
            }
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            PrefsManager.removeWidgetTarget(context, id)
        }
    }
}
