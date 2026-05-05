package com.paruchan.questlog.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuestReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> QuestNotificationScheduler.scheduleNext(context)
            QuestNotificationScheduler.ACTION_SHOW_REMINDER -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        runCatching { QuestReminderNotifier.showIfAny(context) }
                        QuestNotificationScheduler.scheduleNext(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
