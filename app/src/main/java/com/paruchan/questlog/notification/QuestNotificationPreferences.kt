package com.paruchan.questlog.notification

import android.content.Context

class QuestNotificationPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): QuestNotificationSettings =
        QuestNotificationSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            hour = prefs.getInt(KEY_HOUR, QuestNotificationSettings.DefaultHour),
            minute = prefs.getInt(KEY_MINUTE, QuestNotificationSettings.DefaultMinute),
        ).normalized()

    fun save(settings: QuestNotificationSettings) {
        val normalized = settings.normalized()
        prefs.edit()
            .putBoolean(KEY_ENABLED, normalized.enabled)
            .putInt(KEY_HOUR, normalized.hour)
            .putInt(KEY_MINUTE, normalized.minute)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "quest_notifications"
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
