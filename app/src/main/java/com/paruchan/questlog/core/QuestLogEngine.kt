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

    fun progressFor(state: QuestLogState, quest: Quest): QuestProgress {
        val cadence = QuestCadence.from(quest)
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
        val progressInCycle = when (cadence) {
            QuestCadence.Repeatable -> totalProgress % target
            QuestCadence.Daily,
            QuestCadence.Once -> totalProgress.coerceAtMost(target)
        }
        val isComplete = when (cadence) {
            QuestCadence.Repeatable -> false
            QuestCadence.Daily,
            QuestCadence.Once -> totalProgress >= target
        }

        return QuestProgress(
            questId = quest.id,
            cadence = cadence,
            target = target,
            unit = quest.goalUnit.orEmpty().trim().ifBlank { "completion" },
            progressInCycle = progressInCycle,
            completedCycles = completedCycles,
            isComplete = isComplete,
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
        note: String? = null,
    ): CompletionResult {
        val quest = state.quests.firstOrNull { it.id == questId }
            ?: return CompletionResult(state, null, "Quest not found")

        if (!canComplete(state, quest)) {
            return CompletionResult(state, null, "Quest is already completed")
        }

        val progress = progressFor(state, quest)
        val willCompleteCycle = progress.progressInCycle + 1 >= progress.target
        val completion = Completion(
            id = idFactory(),
            questId = quest.id,
            completedAt = Instant.now(clock).toString(),
            xpAwarded = if (willCompleteCycle) quest.xp.coerceAtLeast(0) else 0,
            progressAmount = 1,
            note = note?.trim()?.ifBlank { null },
        )
        val nextProgress = progress.progressInCycle + 1

        return CompletionResult(
            state = state.copy(completions = state.completions + completion),
            completion = completion,
            message = if (willCompleteCycle) {
                "${quest.title} completed for ${completion.xpAwarded} XP"
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
}

data class CompletionResult(
    val state: QuestLogState,
    val completion: Completion?,
    val message: String,
)
