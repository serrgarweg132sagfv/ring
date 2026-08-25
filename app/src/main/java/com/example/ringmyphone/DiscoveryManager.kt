package com.example.ringmyphone

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * مسئول دو کار است:
 * ۱) اعلام حضور این گوشی روی شبکه (Register) با اسم دلخواه و شناسه یکتا
 * ۲) پیدا کردن بقیه گوشی‌های همین اپ روی همان وای‌فای (Discover)
 */
class DiscoveryManager(private val context: Context) {

    private val TAG = "DiscoveryManager"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    var onDeviceFound: ((Device) -> Unit)? = null
    var onDeviceLost: ((String) -> Unit)? = null

    /**
     * این گوشی را روی شبکه با اسم دلخواه، شناسه یکتا و پورت مشخص، قابل‌پیدا‌شدن می‌کند
     */
    fun registerService(displayName: String, deviceId: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = displayName
            serviceType = Constants.SERVICE_TYPE
            this.port = port
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute(Constants.NSD_ATTR_ID, deviceId)
            }
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "سرویس با موفقیت ثبت شد: ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "ثبت سرویس ناموفق بود. کد خطا: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.d(TAG, "سرویس لغو ثبت شد")
            }

            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "لغو ثبت ناموفق بود. کد خطا: $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    /**
     * شروع جستجو برای پیدا کردن بقیه گوشی‌هایی که همین اپ روی آن‌ها نصب است
     */
    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "جستجو شروع شد")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                resolveService(service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // فقط با اسم نمی‌توانیم شناسه واقعی را بفهمیم، پس اسم سرویس را برای حذف از UI می‌فرستیم
                onDeviceLost?.invoke(service.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "جستجو متوقف شد")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "شروع جستجو ناموفق بود: $errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "توقف جستجو ناموفق بود: $errorCode")
            }
        }

        nsdManager.discoverServices(Constants.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveService(service: NsdServiceInfo) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "resolve سرویس ناموفق بود: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val idBytes = info.attributes?.get(Constants.NSD_ATTR_ID)
                val id = idBytes?.toString(Charsets.UTF_8)

                if (id == null) {
                    Log.e(TAG, "دستگاه resolve شد ولی شناسه نداشت، نادیده گرفته شد")
                    return
                }

                val device = Device(
                    id = id,
                    name = info.serviceName ?: "دستگاه ناشناس",
                    host = host,
                    port = info.port
                )
                onDeviceFound?.invoke(device)
            }
        })
    }

    fun stopDiscovery() {
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "خطا هنگام توقف جستجو: ${e.message}")
        }
        discoveryListener = null
    }

    fun unregisterService() {
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (e: Exception) {
            Log.e(TAG, "خطا هنگام لغو ثبت سرویس: ${e.message}")
        }
        registrationListener = null
    }
}
