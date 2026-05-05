package com.paruchan.questlog.core

import java.time.LocalDate

object DailyQuestMessages {
    val messages = listOf(
        "Paru is proud of you!!",
        "I love my paruchan!!!",
        "Babsy believes in you!!",
        "Tiny steps count, paruchan!!",
        "Paru says you are doing so well!!",
        "Babsy is cheering for you!!",
    )

    fun forDate(date: LocalDate = LocalDate.now()): String {
        val index = Math.floorMod(date.toEpochDay(), messages.size.toLong()).toInt()
        return messages[index]
    }
}
