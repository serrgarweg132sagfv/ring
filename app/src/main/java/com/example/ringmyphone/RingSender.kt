package com.example.ringmyphone

import android.util.Log
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * وظیفه‌ی این کلاس فرستادن پیام "زنگ بزن" یا "پیدام کن" به یک دستگاه دیگر است.
 * این کار باید حتماً در یک ترد پس‌زمینه (نه ترد اصلی UI) انجام شود.
 *
 * فرمت پیام ارسالی (یک خط متنی): TYPE|SENDER_NAME|MESSAGE
 * مثال: RING|گوشی من|شام حاضره
 */
object RingSender {

    private const val TAG = "RingSender"
    private const val TIMEOUT_MS = 3000

    fun sendRing(device: Device, senderName: String, message: String, isFindMode: Boolean): Boolean {
        val type = if (isFindMode) Constants.TYPE_FIND else Constants.TYPE_RING
        // کاراکتر | را از متن پیام و اسم حذف می‌کنیم تا پروتکل به‌هم نریزد
        val safeName = senderName.replace("|", " ")
        val safeMessage = message.replace("|", " ")
        val payload = "$type|$safeName|$safeMessage"

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(device.host, device.port), TIMEOUT_MS)
                val out: OutputStream = socket.getOutputStream()
                out.write((payload + "\n").toByteArray(Charsets.UTF_8))
                out.flush()
            }
            Log.d(TAG, "پیام با موفقیت به ${device.name} (${device.host}:${device.port}) فرستاده شد")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ارسال پیام به ${device.name} ناموفق بود: ${e.message}")
            false
        }
    }
}
