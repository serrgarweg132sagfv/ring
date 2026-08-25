package com.example.ringmyphone

/**
 * نشان‌دهنده یک گوشی دیگر که روی همان وای‌فای پیدا شده است
 * id: شناسه یکتا و ثابت آن گوشی (برای تشخیص خودِ گوشی از بقیه، و برای Favorites)
 */
data class Device(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isFavorite: Boolean = false
)
