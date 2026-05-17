package com.paruchan.questlog.core

data class Quest(
    var id: String = "",
    var title: String = "",
    var flavourText: String = "",
    var xp: Int = 0,
    var category: String = "General",
    var icon: String = "star",
    var repeatable: Boolean = false,
    var cadence: String = QuestCadence.Once.wireName,
    var goalType: String = QuestGoalType.Completion.wireName,
    var goalTarget: Int = 1,
    var goalUnit: String = "completion",
    var timerMinutes: Int? = null,
    var createdAt: String = "",
    var archived: Boolean = false,
)

data class Completion(
    var id: String = "",
    var questId: String = "",
    var completedAt: String = "",
    var xpAwarded: Int = 0,
    var progressAmount: Int = 1,
    var note: String? = null,
)

data class Level(
    var level: Int = 1,
    var xpRequired: Int = 0,
    var title: String = "",
    var unlocks: List<String> = emptyList(),
)

data class JournalEntry(
    var id: String = "",
    var localDate: String = "",
    var happyText: String = "",
    var gratefulText: String = "",
    var favoriteMemoryText: String = "",
    var createdAt: String = "",
    var updatedAt: String = "",
    var xpAwarded: Int = 0,
)

data class QuestLogState(
    var schemaVersion: Int = 2,
    var quests: List<Quest> = emptyList(),
    var completions: List<Completion> = emptyList(),
    var levels: List<Level> = DefaultLevels.paruchan(),
    var journalEntries: List<JournalEntry> = emptyList(),
    var exportedAt: String? = null,
)

data class LevelProgress(
    val current: Level,
    val next: Level?,
    val totalXp: Int,
) {
    val xpIntoLevel: Int = totalXp - current.xpRequired
    val xpToNext: Int? = next?.let { it.xpRequired - totalXp }
    val fraction: Float = next?.let { target ->
        val span = target.xpRequired - current.xpRequired
        if (span <= 0) 1f else (xpIntoLevel.toFloat() / span.toFloat()).coerceIn(0f, 1f)
    } ?: 1f
}

data class QuestProgress(
    val questId: String,
    val cadence: QuestCadence,
    val goalType: QuestGoalType,
    val target: Int,
    val unit: String,
    val progressInCycle: Int,
    val completedCycles: Int,
    val isComplete: Boolean,
) {
    val remaining: Int = (target - progressInCycle).coerceAtLeast(0)
    val fraction: Float = if (target <= 0) 1f else (progressInCycle.toFloat() / target.toFloat()).coerceIn(0f, 1f)
    val hasGoal: Boolean = target > 1 || goalType != QuestGoalType.Completion
}

enum class QuestCadence(val wireName: String, val label: String) {
    Once("once", "One-off"),
    Daily("daily", "Daily"),
    Repeatable("repeatable", "Repeatable");

    companion object {
        fun from(quest: Quest): QuestCadence = from(quest.cadence, quest.repeatable)

        fun from(value: String?, repeatable: Boolean = false): QuestCadence {
            return when (value.orEmpty().trim().lowercase()) {
                "daily", "day", "dailies" -> Daily
                "repeatable", "repeat", "repeating", "weekly", "monthly" -> Repeatable
                else -> if (repeatable) Repeatable else Once
            }
        }
    }
}

enum class QuestGoalType(val wireName: String, val label: String) {
    Completion("completion", "Completion"),
    Counter("counter", "Counter"),
    Timer("timer", "Timer");

    companion object {
        fun from(quest: Quest): QuestGoalType = from(quest.goalType, legacyCadence = quest.cadence)

        fun from(value: String?, legacyCadence: String? = null): QuestGoalType {
            val normalized = value.orEmpty().trim().lowercase()
            return when {
                normalized in setOf("counter", "count", "counted") -> Counter
                normalized in setOf("timer", "timed", "time", "minutes", "minute") -> Timer
                legacyCadence.orEmpty().trim().lowercase() in setOf("counter", "count", "per", "per-unit", "per_unit") -> Counter
                else -> Completion
            }
        }
    }
}
