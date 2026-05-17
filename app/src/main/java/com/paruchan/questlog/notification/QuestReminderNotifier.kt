package com.paruchan.questlog.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paruchan.questlog.MainActivity
import com.paruchan.questlog.R
import com.paruchan.questlog.core.DailyQuestMessages
import com.paruchan.questlog.core.QuestLogEngine
import com.paruchan.questlog.data.QuestLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

object QuestReminderNotifier {
    @SuppressLint("MissingPermission")
    fun showIfAny(context: Context): Boolean {
        val appContext = context.applicationContext
        val settings = QuestNotificationPreferences(appContext).load()
        if (!settings.enabled || !canPostNotifications(appContext)) return false

        val state = runBlocking(Dispatchers.IO) { QuestLogRepository.create(appContext).load() }
        val quests = QuestLogEngine().reminderQuests(state)
        if (quests.isEmpty()) return false

        ensureChannel(appContext)
        val message = DailyQuestMessages.forDate(LocalDate.now())
        val questSummary = quests.take(3).joinToString(separator = " / ") { it.title }
            .let { preview ->
                if (quests.size > 3) "$preview / +${quests.size - 3} more" else preview
            }
        val body = "$message\n$questSummary"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message)
            .setContentText(questSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppPendingIntent(appContext))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        return true
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Quest reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val CHANNEL_ID = "quest_reminders"
    private const val NOTIFICATION_ID = 7448
    private const val OPEN_APP_REQUEST_CODE = 7449
}
