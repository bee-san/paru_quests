package com.paruchan.questlog.notification

data class QuestNotificationSettings(
    val enabled: Boolean = false,
    val hour: Int = DefaultHour,
    val minute: Int = DefaultMinute,
) {
    val timeLabel: String = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    fun normalized(): QuestNotificationSettings =
        copy(
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
        )

    companion object {
        const val DefaultHour = 20
        const val DefaultMinute = 0
    }
}
