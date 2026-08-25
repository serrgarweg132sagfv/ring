package com.example.ringmyphone

object Constants {
    // نوع سرویس روی شبکه محلی (NSD) - همه گوشی‌ها با این اسم همدیگر را پیدا می‌کنند
    const val SERVICE_TYPE = "_ringmyphone._tcp."

    // ویژگی (attribute) داخل رکورد NSD که شناسه‌ی یکتای گوشی را حمل می‌کند
    const val NSD_ATTR_ID = "id"

    // نوع پیام‌هایی که بین گوشی‌ها رد و بدل می‌شود
    const val TYPE_RING = "RING"
    const val TYPE_FIND = "FIND"

    // شناسه‌های کانال‌های نوتیفیکیشن
    const val CHANNEL_ID_SERVICE = "ring_server_channel"
    const val CHANNEL_ID_CALL = "ring_incoming_channel"
    const val CHANNEL_ID_WIDGET = "ring_widget_channel"

    const val SERVICE_NOTIFICATION_ID = 1
    const val RING_NOTIFICATION_ID = 2
    const val WIDGET_NOTIFICATION_ID = 3

    // اکسترای اینتنت برای اکتیویتی زنگ
    const val EXTRA_MESSAGE = "extra_message"
    const val EXTRA_IS_FIND_MODE = "extra_is_find_mode"
    const val EXTRA_SENDER_NAME = "extra_sender_name"
}
