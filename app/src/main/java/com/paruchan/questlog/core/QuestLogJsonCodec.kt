package com.paruchan.questlog.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException

class QuestLogJsonCodec(
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create(),
) {
    fun encode(state: QuestLogState): String = gson.toJson(normalize(state))

    fun decodeState(json: String): QuestLogState {
        val parsed = try {
            gson.fromJson(json, QuestLogState::class.java)
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Backup is not valid JSON", error)
        } ?: throw IllegalArgumentException("Backup is empty")

        return normalize(parsed)
    }

    fun normalize(state: QuestLogState): QuestLogState {
        val levels = state.levels.orEmpty()
            .ifEmpty { DefaultLevels.paruchan() }
            .filter { it.level > 0 && it.xpRequired >= 0 }
            .sortedWith(compareBy<Level> { it.xpRequired }.thenBy { it.level })
            .ifEmpty { DefaultLevels.paruchan() }

        val quests = state.quests.orEmpty()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .map { quest ->
                val cadence = QuestCadence.from(quest)
                quest.copy(
                    title = quest.title.trim(),
                    flavourText = quest.flavourText.trim(),
                    category = quest.category.trim().ifBlank { "General" },
                    icon = quest.icon.trim().ifBlank { "star" },
                    xp = quest.xp.coerceAtLeast(0),
                    repeatable = cadence == QuestCadence.Repeatable,
                    cadence = cadence.wireName,
                    goalTarget = quest.goalTarget.coerceAtLeast(1),
                    goalUnit = quest.goalUnit.orEmpty().trim().ifBlank { "completion" },
                    timerMinutes = quest.timerMinutes?.coerceIn(1, 24 * 60),
                )
            }
            .distinctBy { it.id }

        val completions = state.completions.orEmpty()
            .filter { it.id.isNotBlank() && it.questId.isNotBlank() && it.completedAt.isNotBlank() }
            .map { completion ->
                completion.copy(
                    xpAwarded = completion.xpAwarded.coerceAtLeast(0),
                    progressAmount = completion.progressAmount.coerceAtLeast(1),
                )
            }
            .distinctBy { it.id }

        return state.copy(
            schemaVersion = 1,
            quests = quests,
            completions = completions,
            levels = levels,
        )
    }
}
