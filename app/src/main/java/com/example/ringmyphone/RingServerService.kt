package com.example.ringmyphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * سرویس پس‌زمینه‌ای که همیشه در حال اجراست (Foreground Service):
 * ۱) این گوشی را روی شبکه با NSD قابل‌پیدا‌شدن می‌کند (با اسم دلخواه و شناسه یکتا)
 * ۲) یک ServerSocket باز نگه می‌دارد و منتظر پیام از بقیه گوشی‌ها می‌ماند
 * ۳) وقتی پیام برسد، صفحه زنگ را حتی روی صفحه قفل و با صفحه خاموش نشان می‌دهد
 */
class RingServerService : Service() {

    private val TAG = "RingServerService"

    private lateinit var discoveryManager: DiscoveryManager
    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(Constants.SERVICE_NOTIFICATION_ID, buildServiceNotification())

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("ringmyphone_multicast_lock").apply {
            setReferenceCounted(true)
            acquire()
        }

        discoveryManager = DiscoveryManager(applicationContext)
        startServerSocket()
    }

    private fun startServerSocket() {
        running = true
        serverThread = Thread {
            try {
                val server = ServerSocket(0)
                serverSocket = server

                val deviceId = PrefsManager.getOrCreateDeviceId(applicationContext)
                val defaultName = "${Build.MANUFACTURER}-${Build.MODEL}"
                val displayName = PrefsManager.getDeviceName(applicationContext, defaultName)
                discoveryManager.registerService(displayName, deviceId, server.localPort)

                Log.d(TAG, "سرور روی پورت ${server.localPort} در حال گوش دادن است")

                while (running) {
                    val client: Socket = server.accept()
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (running) Log.e(TAG, "خطا در سرور سوکت: ${e.message}")
            }
        }
        serverThread?.start()
    }

    private fun handleClient(client: Socket) {
        Thread {
            try {
                client.use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val line = reader.readLine()?.trim() ?: return@use
                    Log.d(TAG, "پیام دریافت شد: $line از ${socket.inetAddress?.hostAddress}")

                    // فرمت: TYPE|SENDER_NAME|MESSAGE
                    val parts = line.split("|", limit = 3)
                    val type = parts.getOrNull(0) ?: return@use
                    val senderName = parts.getOrNull(1) ?: "دستگاه ناشناس"
                    val message = parts.getOrNull(2) ?: ""

                    when (type) {
                        Constants.TYPE_RING -> triggerIncomingRing(senderName, message, isFindMode = false)
                        Constants.TYPE_FIND -> triggerIncomingRing(senderName, message, isFindMode = true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطا در پردازش پیام دریافتی: ${e.message}")
            }
        }.start()
    }

    /**
     * وقتی پیام زنگ دریافت شد، این متد اجرا می‌شود.
     * اول یک WakeLock کوتاه می‌گیریم تا CPU و صفحه بیدار شود (این تضمین می‌کند
     * حتی قبل از رسیدن نوتیفیکیشن به سیستم، دستگاه در حال "بیدار شدن" است)،
     * سپس نوتیفیکیشن با fullScreenIntent را می‌فرستیم که روش رسمی اندروید برای
     * نمایش صفحه‌ی تماس ورودی است.
     */
    private fun triggerIncomingRing(senderName: String, message: String, isFindMode: Boolean) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "RingMyPhone::TriggerWakeLock"
        )
        wakeLock.acquire(10_000L)

        val ringIntent = Intent(this, RingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(Constants.EXTRA_SENDER_NAME, senderName)
            putExtra(Constants.EXTRA_MESSAGE, message)
            putExtra(Constants.EXTRA_IS_FIND_MODE, isFindMode)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isFindMode) "درخواست پیدا کردن گوشی" else "زنگ ورودی"
        val contentText = if (message.isNotBlank()) "$senderName: $message" else "از طرف $senderName"

        // نوتیفیکیشن خودش بی‌صدا/کم‌اهمیت است چون خودِ RingActivity صدا و لرزش را پخش می‌کند.
        // نقش اصلی این نوتیفیکیشن فقط باز کردن تضمینی صفحه‌ی زنگ (fullScreenIntent) است.
        val notification = NotificationCompat.Builder(this, Constants.CHANNEL_ID_CALL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(Constants.RING_NOTIFICATION_ID, notification)

        try {
            startActivity(ringIntent)
        } catch (e: Exception) {
            Log.e(TAG, "باز کردن مستقیم اکتیویتی ناموفق بود، به fullScreenIntent متکی می‌شویم: ${e.message}")
        }

        if (wakeLock.isHeld) wakeLock.release()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val serviceChannel = NotificationChannel(
            Constants.CHANNEL_ID_SERVICE,
            "سرویس در حال اجرا",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نشان می‌دهد که اپ در حال گوش دادن برای درخواست‌های زنگ است"
        }

        // این کانال باید بی‌صدا باشد چون صدای واقعی زنگ را خودِ RingActivity پخش می‌کند؛
        // اگر این کانال هم صدا داشته باشد، دو صدای همزمان/متناقض پخش می‌شود.
        val callChannel = NotificationChannel(
            Constants.CHANNEL_ID_CALL,
            "زنگ ورودی",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "نمایش تضمینی صفحه‌ی زنگ هنگام دریافت درخواست از دستگاه دیگر"
            setSound(null, null)
            enableVibration(false)
        }

        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(callChannel)
    }

    private fun buildServiceNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.CHANNEL_ID_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("RingMyPhone فعال است")
            .setContentText("در حال گوش دادن برای درخواست زنگ از گوشی‌های دیگر")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "خطا هنگام بستن سوکت: ${e.message}")
        }
        discoveryManager.unregisterService()
        multicastLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }
}
