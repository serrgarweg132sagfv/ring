package com.example.ringmyphone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ringmyphone.databinding.ActivityMainBinding
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var discoveryManager: DiscoveryManager
    private lateinit var adapter: DeviceAdapter
    private val executor = Executors.newCachedThreadPool()
    private var myDeviceId: String = ""

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        startEverything()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        myDeviceId = PrefsManager.getOrCreateDeviceId(applicationContext)
        adapter = DeviceAdapter(this)

        binding.rvDevices.layoutManager = LinearLayoutManager(this)
        binding.rvDevices.adapter = adapter

        setupNameEditor()
        setupPresetMessageButtons()

        binding.btnRingSelected.setOnClickListener { sendToSelected(isFindMode = false) }
        binding.btnFindSelected.setOnClickListener { sendToSelected(isFindMode = true) }

        requestNeededPermissions()
        askIgnoreBatteryOptimization()
    }

    private fun setupNameEditor() {
        val defaultName = "${Build.MANUFACTURER}-${Build.MODEL}"
        val currentName = PrefsManager.getDeviceName(applicationContext, defaultName)
        binding.etMyDeviceName.setText(currentName)

        binding.btnSaveName.setOnClickListener {
            val newName = binding.etMyDeviceName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, "اسم نمی‌تواند خالی باشد", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsManager.setDeviceName(applicationContext, newName)
            Toast.makeText(this, "اسم ذخیره شد؛ برای اعمال کامل، اپ را ببند و دوباره باز کن", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupPresetMessageButtons() {
        binding.btnPresetDinner.setOnClickListener {
            binding.etMessage.setText("شام حاضره")
        }
        binding.btnPresetComeDown.setOnClickListener {
            binding.etMessage.setText("بیا پایین")
        }
    }

    private fun requestNeededPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        } else {
            startEverything()
        }
    }

    private fun askIgnoreBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "لطفاً بهینه‌سازی باتری را برای این اپ از تنظیمات غیرفعال کنید", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startEverything() {
        val serviceIntent = Intent(this, RingServerService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        discoveryManager = DiscoveryManager(applicationContext)
        discoveryManager.onDeviceFound = { device ->
            // گوشی خودمان را در لیست نشان نمی‌دهیم
            if (device.id != myDeviceId) {
                runOnUiThread {
                    adapter.addOrUpdate(device)
                    binding.tvStatus.text = "تعداد ${adapter.itemCount} دستگاه پیدا شد"
                }
            }
        }
        discoveryManager.onDeviceLost = { serviceName ->
            runOnUiThread { adapter.removeByServiceName(serviceName) }
        }
        discoveryManager.startDiscovery()
    }

    private fun sendToSelected(isFindMode: Boolean) {
        val devices = adapter.getSelectedDevices()
        if (devices.isEmpty()) {
            Toast.makeText(this, "اول حداقل یک دستگاه را از لیست انتخاب کن", Toast.LENGTH_SHORT).show()
            return
        }

        val myName = binding.etMyDeviceName.text.toString().ifBlank { "دستگاه ناشناس" }
        val message = binding.etMessage.text.toString()

        val actionText = if (isFindMode) "درخواست پیدا کردن" else "زنگ"
        Toast.makeText(this, "در حال ارسال $actionText به ${devices.size} دستگاه...", Toast.LENGTH_SHORT).show()

        devices.forEach { device ->
            executor.execute {
                val success = RingSender.sendRing(device, myName, message, isFindMode)
                runOnUiThread {
                    if (!success) {
                        Toast.makeText(this, "ارسال به ${device.name} ناموفق بود", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::discoveryManager.isInitialized) {
            discoveryManager.stopDiscovery()
        }
        executor.shutdown()
        super.onDestroy()
    }
}
