package com.paruchan.questlog.core

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class QuestLogEngine(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    fun totalXp(state: QuestLogState): Int = state.completions.sumOf { it.xpAwarded }

    fun levelProgress(state: QuestLogState): LevelProgress {
        val levels = normalizedLevels(state.levels)
        val totalXp = totalXp(state)
        val current = levels.lastOrNull { totalXp >= it.xpRequired } ?: levels.first()
        val next = levels.firstOrNull { it.xpRequired > totalXp }
        return LevelProgress(current = current, next = next, totalXp = totalXp)
    }

    fun completedQuestIds(state: QuestLogState): Set<String> =
        state.quests
            .filter { progressFor(state, it).isComplete }
            .mapTo(mutableSetOf()) { it.id }

    fun reminderQuests(state: QuestLogState): List<Quest> =
        state.quests
            .filter { canComplete(state, it) }
            .sortedWith(
                compareByDescending<Quest> { QuestCadence.from(it) == QuestCadence.Daily }
                    .thenByDescending { QuestGoalType.from(it) == QuestGoalType.Timer }
                    .thenByDescending { it.timerMinutes != null }
                    .thenByDescending { it.goalTarget > 1 }
                    .thenBy { QuestGoalType.from(it) == QuestGoalType.Counter }
                    .thenBy { it.title.lowercase() },
            )

    fun progressFor(state: QuestLogState, quest: Quest): QuestProgress {
        val cadence = QuestCadence.from(quest)
        val goalType = QuestGoalType.from(quest)
        val target = quest.goalTarget.coerceAtLeast(1)
        val completions = state.completions
            .filter { it.questId == quest.id }
            .let { all ->
                if (cadence == QuestCadence.Daily) {
                    val today = LocalDate.now(clock)
                    all.filter { completion -> completion.completedDate() == today }
                } else {
                    all
                }
            }
        val totalProgress = completions.sumOf { it.progressAmount.coerceAtLeast(1) }
        val completedCycles = totalProgress / target
        val progressInCycle = when {
            cadence == QuestCadence.Repeatable -> totalProgress % target
            else -> totalProgress.coerceAtMost(target)
        }

        return QuestProgress(
            questId = quest.id,
            cadence = cadence,
            goalType = goalType,
            target = target,
            unit = normalizedGoalUnit(quest.goalUnit, goalType),
            progressInCycle = progressInCycle,
            completedCycles = completedCycles,
            isComplete = cadence != QuestCadence.Repeatable && totalProgress >= target,
        )
    }

    fun canComplete(state: QuestLogState, quest: Quest): Boolean {
        if (quest.archived) return false
        if (QuestCadence.from(quest) == QuestCadence.Repeatable) return true
        return !progressFor(state, quest).isComplete
    }

    fun completeQuest(
        state: QuestLogState,
        questId: String,
        progressAmount: Int = 1,
        note: String? = null,
    ): CompletionResult {
        val quest = state.quests.firstOrNull { it.id == questId }
            ?: return CompletionResult(state, null, "Quest not found")

        if (!canComplete(state, quest)) {
            return CompletionResult(state, null, "Quest is already completed")
        }

        val progress = progressFor(state, quest)
        val goalType = QuestGoalType.from(quest)
        val safeAmount = progressAmount.coerceAtLeast(1)
        val recordedAmount = when (goalType) {
            QuestGoalType.Counter -> 1
            QuestGoalType.Timer -> safeAmount
            QuestGoalType.Completion -> 1
        }
        val willCompleteCycle = progress.progressInCycle + recordedAmount >= progress.target
        val xpAwarded = if (willCompleteCycle) quest.xp.coerceAtLeast(0) else 0
        val completion = Completion(
            id = idFactory(),
            questId = quest.id,
            completedAt = Instant.now(clock).toString(),
            xpAwarded = xpAwarded,
            progressAmount = recordedAmount,
            note = note?.trim()?.ifBlank { null },
        )
        val nextProgress = progress.progressInCycle + recordedAmount

        return CompletionResult(
            state = state.copy(completions = state.completions + completion),
            completion = completion,
            message = if (willCompleteCycle) {
                "${quest.title} completed for ${completion.xpAwarded} XP"
            } else if (goalType == QuestGoalType.Timer) {
                val unit = pluralizeUnit(progress.unit, recordedAmount)
                "${quest.title} logged $recordedAmount $unit. $nextProgress/${progress.target} ${pluralizeUnit(progress.unit, progress.target)}"
            } else {
                "${quest.title} progress $nextProgress/${progress.target} ${progress.unit}"
            },
        )
    }

    private fun normalizedLevels(levels: List<Level>): List<Level> =
        levels.ifEmpty { DefaultLevels.paruchan() }
            .sortedWith(compareBy<Level> { it.xpRequired }.thenBy { it.level })

    private fun Completion.completedDate(): LocalDate? =
        runCatching { Instant.parse(completedAt).atZone(clock.zone).toLocalDate() }.getOrNull()

    private fun pluralizeUnit(unit: String, amount: Int): String =
        if (amount == 1 || unit.endsWith("s", ignoreCase = true)) unit else "${unit}s"

    private fun normalizedGoalUnit(unit: String?, goalType: QuestGoalType): String {
        val cleaned = unit.orEmpty().trim()
        return when (goalType) {
            QuestGoalType.Counter -> cleaned.ifBlank { "unit" }
            QuestGoalType.Timer -> "minute"
            QuestGoalType.Completion -> cleaned.ifBlank { "completion" }
        }
    }
}

data class CompletionResult(
    val state: QuestLogState,
    val completion: Completion?,
    val message: String,
)
