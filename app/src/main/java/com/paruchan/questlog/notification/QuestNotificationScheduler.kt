package com.paruchan.questlog.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object QuestNotificationScheduler {
    const val ACTION_SHOW_REMINDER = "com.paruchan.questlog.action.SHOW_QUEST_REMINDER"

    fun scheduleNext(context: Context) {
        scheduleNext(context, QuestNotificationPreferences(context).load())
    }

    fun scheduleNext(context: Context, settings: QuestNotificationSettings) {
        val normalized = settings.normalized()
        if (!normalized.enabled) {
            cancel(context)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = nextTriggerAtMillis(normalized)
        val pendingIntent = reminderPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(reminderPendingIntent(context))
    }

    private fun nextTriggerAtMillis(settings: QuestNotificationSettings): Long {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.hour)
            set(Calendar.MINUTE, settings.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return next.timeInMillis
    }

    private fun reminderPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, QuestReminderReceiver::class.java)
            .setAction(ACTION_SHOW_REMINDER)
        return PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val REMINDER_REQUEST_CODE = 7447
}
