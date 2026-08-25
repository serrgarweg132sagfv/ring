package com.example.ringmyphone

import android.app.NotificationManager
import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.example.ringmyphone.databinding.ActivityRingBinding

class RingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRingBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFindMode = false

    // برای حالت "پیدام کن": چشمک زدن چراغ‌قوه
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var torchOn = false
    private val torchRunnable = object : Runnable {
        override fun run() {
            toggleTorch()
            mainHandler.postDelayed(this, 500)
        }
    }

    // برای حالت "پیدام کن": صدا هر چند ثانیه بلندتر شود
    private var currentVolume = 0.3f
    private val volumeRampRunnable = object : Runnable {
        override fun run() {
            currentVolume = (currentVolume + 0.2f).coerceAtMost(1.0f)
            mediaPlayer?.setVolume(currentVolume, currentVolume)
            if (currentVolume < 1.0f) {
                mainHandler.postDelayed(this, 3000)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isFindMode = intent.getBooleanExtra(Constants.EXTRA_IS_FIND_MODE, false)
        val senderName = intent.getStringExtra(Constants.EXTRA_SENDER_NAME) ?: "دستگاه ناشناس"
        val message = intent.getStringExtra(Constants.EXTRA_MESSAGE) ?: ""

        setupTexts(senderName, message)
        showOverLockScreenAndWakeUp()
        acquireWakeLock()
        startRingtone()
        startVibration()

        if (isFindMode) {
            startTorchBlinking()
            currentVolume = 0.3f
            mainHandler.postDelayed(volumeRampRunnable, 3000)
        }

        binding.btnStopRing.setOnClickListener {
            stopRingingAndFinish()
        }
    }

    private fun setupTexts(senderName: String, message: String) {
        binding.ivRingIcon.setImageResource(if (isFindMode) R.drawable.ic_flashlight else R.drawable.ic_bell)
        binding.tvTitleRing.text = if (isFindMode) "درخواست پیدا کردن گوشی" else "یک دستگاه دیگر شما را صدا می‌زند"
        val sub = if (message.isNotBlank()) "$senderName: $message" else "از طرف $senderName"
        binding.tvSubtitleRing.text = sub
    }

    private fun showOverLockScreenAndWakeUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "RingMyPhone::RingWakeLock"
        )
        wakeLock?.acquire(120_000L) // حداکثر ۲ دقیقه، تا در صورت خطا هم برای همیشه روشن نماند
    }

    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@RingActivity, ringtoneUri)
                isLooping = true
                prepare()
                if (isFindMode) {
                    setVolume(currentVolume, currentVolume)
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 800, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 1))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 1)
        }
    }

    private fun startTorchBlinking() {
        try {
            cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            torchCameraId = cameraManager?.cameraIdList?.firstOrNull { id ->
                cameraManager?.getCameraCharacteristics(id)
                    ?.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (torchCameraId != null) {
                mainHandler.post(torchRunnable)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleTorch() {
        val id = torchCameraId ?: return
        try {
            torchOn = !torchOn
            cameraManager?.setTorchMode(id, torchOn)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTorch() {
        mainHandler.removeCallbacks(torchRunnable)
        val id = torchCameraId
        if (id != null && torchOn) {
            try {
                cameraManager?.setTorchMode(id, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        torchOn = false
    }

    private fun stopRingingAndFinish() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        mainHandler.removeCallbacks(volumeRampRunnable)
        stopTorch()

        wakeLock?.let { if (it.isHeld) it.release() }

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(Constants.RING_NOTIFICATION_ID)

        finish()
    }

    override fun onDestroy() {
        stopRingingAndFinish()
        super.onDestroy()
    }
}
