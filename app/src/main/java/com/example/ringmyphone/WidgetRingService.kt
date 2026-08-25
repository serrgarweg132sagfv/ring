package com.example.ringmyphone

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * وقتی روی ویجت صفحه اصلی لمس می‌شود، این سرویس کوتاه اجرا می‌شود:
 * یک جستجوی سریع (چند ثانیه‌ای) روی شبکه انجام می‌دهد تا دستگاه هدف را پیدا کند،
 * پیام زنگ را می‌فرستد، و بعد خودش را متوقف می‌کند.
 */
class WidgetRingService : Service() {

    companion object {
        const val EXTRA_WIDGET_ID = "extra_widget_id"
        private const val DISCOVERY_TIMEOUT_MS = 8000L
    }

    private lateinit var discoveryManager: DiscoveryManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var handled = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannelIfNeeded()
        val appWidgetId = intent?.getIntExtra(EXTRA_WIDGET_ID, -1) ?: -1
        val targetId = PrefsManager.getWidgetTargetId(applicationContext, appWidgetId)
        val targetName = PrefsManager.getWidgetTargetName(applicationContext, appWidgetId) ?: "دستگاه"

        startForeground(Constants.WIDGET_NOTIFICATION_ID, buildNotification("در حال پیدا کردن $targetName ..."))

        if (targetId == null) {
            showToast("این ویجت هنوز تنظیم نشده. آن را از صفحه اصلی حذف و دوباره اضافه کن.")
            stopSelf()
            return START_NOT_STICKY
        }

        val myName = PrefsManager.getDeviceName(applicationContext, "${Build.MANUFACTURER}-${Build.MODEL}")

        discoveryManager = DiscoveryManager(applicationContext)
        discoveryManager.onDeviceFound = { device ->
            if (!handled && device.id == targetId) {
                handled = true
                Thread {
                    RingSender.sendRing(device, myName, "", isFindMode = false)
                }.start()
                showToast("زنگ به $targetName فرستاده شد")
                discoveryManager.stopDiscovery()
                stopSelf()
            }
        }
        discoveryManager.startDiscovery()

        mainHandler.postDelayed({
            if (!handled) {
                discoveryManager.stopDiscovery()
                showToast("$targetName الان روی شبکه پیدا نشد")
                stopSelf()
            }
        }, DISCOVERY_TIMEOUT_MS)

        return START_NOT_STICKY
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createChannelIfNeeded() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            Constants.CHANNEL_ID_WIDGET,
            "زنگ از ویجت",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "نشان می‌دهد که در حال ارسال زنگ از طریق ویجت هستیم" }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String) =
        NotificationCompat.Builder(this, Constants.CHANNEL_ID_WIDGET)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("RingMyPhone")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::discoveryManager.isInitialized) {
            discoveryManager.stopDiscovery()
        }
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
