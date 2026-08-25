package com.example.ringmyphone

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ringmyphone.databinding.ActivityWidgetConfigBinding
import com.google.android.material.button.MaterialButton

class WidgetConfigActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var binding: ActivityWidgetConfigBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        populateFavoritesList()
    }

    private fun populateFavoritesList() {
        val favorites = PrefsManager.getFavorites(applicationContext)

        if (favorites.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = "هنوز هیچ دستگاهی را ⭐ Pin نکرده‌ای.\nاول از صفحه‌ی اصلی اپ، یک دستگاه را با دکمه‌ی ☆ کنار اسمش، به علاقه‌مندی‌ها اضافه کن، بعد دوباره ویجت را اضافه کن."
                textSize = 15f
                setPadding(8, 8, 8, 8)
            }
            binding.containerFavorites.addView(emptyText)
            return
        }

        for ((deviceId, deviceName) in favorites) {
            val button = MaterialButton(this).apply {
                text = deviceName
                cornerRadius = 16
                isAllCaps = false
                setIconResource(R.drawable.ic_bell)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                setBackgroundColor(getColor(R.color.surface_card))
                setTextColor(getColor(R.color.text_primary))
                setIconTintResource(R.color.brand_primary)
                setOnClickListener {
                    PrefsManager.setWidgetTarget(applicationContext, appWidgetId, deviceId, deviceName)

                    val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
                    RingWidgetProvider.updateWidget(applicationContext, appWidgetManager, appWidgetId)

                    val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    setResult(RESULT_OK, resultValue)
                    Toast.makeText(applicationContext, "ویجت برای «$deviceName» تنظیم شد", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }
            binding.containerFavorites.addView(button, params)
        }
    }
}
