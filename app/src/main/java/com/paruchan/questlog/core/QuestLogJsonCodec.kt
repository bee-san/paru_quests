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
        val root = parseRootElement(json)
        require(!root.isJsonNull) { "Backup is empty" }

        val parsed = try {
            gson.fromJson(root, QuestLogState::class.java)
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Backup is not valid JSON", error)
        }

        return normalize(parsed)
    }

    fun decodeBackup(json: String): QuestLogState {
        val root = parseRootObject(json)
        val schemaVersion = root.int("schemaVersion")
        require(schemaVersion == 1 || schemaVersion == 2) { "Backup has an unsupported schema version" }
        require(root["quests"]?.isJsonArray == true) { "Backup is missing quests" }
        require(root["completions"]?.isJsonArray == true) { "Backup is missing completions" }
        require(root["levels"]?.isJsonArray == true) { "Backup is missing levels" }
        if (schemaVersion == 2) {
            require(root["journalEntries"]?.isJsonArray == true) { "Backup is missing journalEntries" }
        }
        require(root.string("exportedAt").isNotBlank()) { "Backup is missing exportedAt" }
        return decodeState(json)
    }

    fun normalize(state: QuestLogState): QuestLogState {
        val importedLevels = state.levels.orEmpty()
        val levels = if (importedLevels.isEmpty() || DefaultLevels.isLegacyDefault(importedLevels)) {
            DefaultLevels.paruchan()
        } else {
            importedLevels
                .filter { it.level > 0 && it.xpRequired >= 0 }
                .sortedWith(compareBy<Level> { it.xpRequired }.thenBy { it.level })
                .ifEmpty { DefaultLevels.paruchan() }
        }

        val quests = state.quests.orEmpty()
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .map { quest ->
                val goalType = QuestGoalType.from(quest)
                val cadence = QuestCadence.from(quest)
                val target = normalizedGoalTarget(quest, goalType)
                quest.copy(
                    title = quest.title.trim(),
                    flavourText = quest.flavourText.trim(),
                    category = quest.category.trim().ifBlank { "General" },
                    icon = quest.icon.trim().ifBlank { "star" },
                    xp = quest.xp.coerceAtLeast(0),
                    repeatable = cadence == QuestCadence.Repeatable,
                    cadence = cadence.wireName,
                    goalType = goalType.wireName,
                    goalTarget = target,
                    goalUnit = normalizedGoalUnit(quest.goalUnit, goalType),
                    timerMinutes = if (goalType == QuestGoalType.Timer) null else quest.timerMinutes?.coerceIn(1, 24 * 60),
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

        val journalEntries = state.journalEntries.orEmpty()
            .filter { it.localDate.isNotBlank() }
            .map { entry ->
                val id = entry.id.trim().ifBlank { "journal-${entry.localDate.trim()}" }
                entry.copy(
                    id = id,
                    localDate = entry.localDate.trim(),
                    happyText = entry.happyText.trim(),
                    gratefulText = entry.gratefulText.trim(),
                    favoriteMemoryText = entry.favoriteMemoryText.trim(),
                    createdAt = entry.createdAt.trim(),
                    updatedAt = entry.updatedAt.trim(),
                    xpAwarded = if (entry.xpAwarded >= 10) 10 else 0,
                )
            }
            .distinctBy { it.localDate }

        return state.copy(
            schemaVersion = 2,
            quests = quests,
            completions = completions,
            levels = levels,
            journalEntries = journalEntries,
        )
    }

    private fun parseRootElement(json: String): JsonElement {
        return try {
            JsonParser.parseString(json)
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Backup is not valid JSON", error)
        }
    }

    private fun parseRootObject(json: String): JsonObject {
        val root = parseRootElement(json)
        require(root.isJsonObject) { "Backup must be a JSON object" }
        return root.asJsonObject
    }

    private fun JsonObject.string(name: String): String = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
    }.getOrDefault("")

    private fun JsonObject.int(name: String): Int? = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asInt
    }.getOrNull()

    private fun normalizedGoalTarget(quest: Quest, goalType: QuestGoalType): Int {
        val target = quest.goalTarget.coerceAtLeast(1)
        return if (goalType == QuestGoalType.Timer && target == 1) {
            quest.timerMinutes?.coerceAtLeast(1) ?: target
        } else {
            target
        }
    }

    private fun normalizedGoalUnit(unit: String?, goalType: QuestGoalType): String {
        val cleaned = unit.orEmpty().trim()
        return when (goalType) {
            QuestGoalType.Counter -> cleaned.ifBlank { "unit" }
            QuestGoalType.Timer -> "minute"
            QuestGoalType.Completion -> cleaned.ifBlank { "completion" }
        }
    }
}
