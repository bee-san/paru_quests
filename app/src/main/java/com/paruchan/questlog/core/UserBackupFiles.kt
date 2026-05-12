package com.paruchan.questlog.core

import java.time.LocalDate

object UserBackupFiles {
    const val LatestBackupName = "paruchan-quest-log-latest.json"
    const val DefaultRetentionCount = 10

    private val datedBackupPattern = Regex("paruchan-quest-log-\\d{4}-\\d{2}-\\d{2}\\.json")

    fun datedBackupName(date: LocalDate): String = "paruchan-quest-log-$date.json"

    fun isDatedBackupName(name: String): Boolean = datedBackupPattern.matches(name)

    fun backupNamesToPrune(
        existingNames: Iterable<String>,
        retentionCount: Int = DefaultRetentionCount,
    ): List<String> {
        return existingNames
            .filter(::isDatedBackupName)
            .sortedDescending()
            .drop(retentionCount.coerceAtLeast(1))
    }
}
