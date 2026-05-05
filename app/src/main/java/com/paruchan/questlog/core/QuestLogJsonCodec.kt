package com.paruchan.questlog.core

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

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

    fun decodeBackup(json: String): QuestLogState {
        val root = parseRootObject(json)
        require(root.int("schemaVersion") == 1) { "Backup has an unsupported schema version" }
        require(root["quests"]?.isJsonArray == true) { "Backup is missing quests" }
        require(root["completions"]?.isJsonArray == true) { "Backup is missing completions" }
        require(root["levels"]?.isJsonArray == true) { "Backup is missing levels" }
        require(root.string("exportedAt").isNotBlank()) { "Backup is missing exportedAt" }
        return decodeState(json)
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

    private fun parseRootObject(json: String): JsonObject {
        val root: JsonElement = try {
            JsonParser.parseString(json)
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Backup is not valid JSON", error)
        }
        require(root.isJsonObject) { "Backup must be a JSON object" }
        return root.asJsonObject
    }

    private fun JsonObject.string(name: String): String = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
    }.getOrDefault("")

    private fun JsonObject.int(name: String): Int? = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asInt
    }.getOrNull()
}
