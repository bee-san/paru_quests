package com.paruchan.questlog.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.time.Clock
import java.time.Instant

class QuestPackExporter(
    private val clock: Clock = Clock.systemUTC(),
    private val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create(),
) {
    fun encodePack(name: String, quests: List<Quest>): String {
        val cleaned = quests.map { quest ->
            val cadence = QuestCadence.from(quest)
            quest.copy(
                id = quest.id.trim(),
                title = quest.title.trim(),
                flavourText = quest.flavourText.trim(),
                xp = quest.xp.coerceAtLeast(0),
                category = quest.category.trim().ifBlank { "General" },
                icon = quest.icon.trim().ifBlank { "star" },
                repeatable = cadence == QuestCadence.Repeatable,
                cadence = cadence.wireName,
                goalTarget = quest.goalTarget.coerceAtLeast(1),
                goalUnit = quest.goalUnit.trim().ifBlank { "completion" },
                timerMinutes = quest.timerMinutes?.coerceIn(1, 24 * 60),
                archived = false,
            )
        }.filter { it.title.isNotBlank() }

        require(cleaned.isNotEmpty()) { "Add at least one quest before exporting" }

        return gson.toJson(
            QuestPackDocument(
                name = name.trim().ifBlank { "Paruchan Quest Pack" },
                exportedAt = Instant.now(clock).toString(),
                quests = cleaned,
            )
        )
    }
}

private data class QuestPackDocument(
    val kind: String = "paruchan.quest-pack",
    val schemaVersion: Int = 1,
    val name: String,
    val exportedAt: String,
    val quests: List<Quest>,
)
