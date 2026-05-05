package com.paruchan.questlog.core

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant

class QuestPackImporter(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun mergeQuestPack(state: QuestLogState, json: String): ImportResult {
        val root = try {
            JsonParser.parseString(json)
        } catch (error: Exception) {
            return ImportResult(state = state, imported = 0, updated = 0, skipped = 0, errors = listOf("Quest pack is not valid JSON"))
        }

        val quests = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject["quests"]?.isJsonArray == true -> root.asJsonObject["quests"].asJsonArray
            else -> return ImportResult(state = state, imported = 0, updated = 0, skipped = 0, errors = listOf("Quest pack must be an array or an object with a quests array"))
        }

        val existingById = state.quests.associateBy { it.id }.toMutableMap()
        val order = state.quests.mapTo(mutableListOf()) { it.id }
        val errors = mutableListOf<String>()
        var imported = 0
        var updated = 0
        var skipped = 0

        quests.forEachIndexed { index, element ->
            val parsed = parseQuest(index, element)
            if (parsed.quest == null) {
                skipped++
                errors += parsed.error ?: "Quest ${index + 1} could not be imported"
                return@forEachIndexed
            }

            val quest = parsed.quest
            val existing = existingById[quest.id]
            if (existing == null) {
                existingById[quest.id] = quest
                order += quest.id
                imported++
            } else {
                existingById[quest.id] = quest.copy(createdAt = existing.createdAt.ifBlank { quest.createdAt })
                updated++
            }
        }

        val merged = order.mapNotNull { existingById[it] }
        return ImportResult(
            state = state.copy(quests = merged),
            imported = imported,
            updated = updated,
            skipped = skipped,
            errors = errors,
        )
    }

    private fun parseQuest(index: Int, element: JsonElement): ParsedQuest {
        if (!element.isJsonObject) {
            return ParsedQuest(error = "Quest ${index + 1} is not an object")
        }

        val obj = element.asJsonObject
        val title = obj.string("title").trim()
        if (title.isBlank()) return ParsedQuest(error = "Quest ${index + 1} is missing a title")

        val xp = obj.int("xp")
            ?: obj.int("xpPerUnit")
            ?: obj.int("xp_per_unit")
        if (xp == null || xp < 0) return ParsedQuest(error = "Quest '$title' must have a non-negative xp value")

        val flavourText = (obj.string("flavourText").ifBlank { obj.string("flavorText") }).trim()
        val category = obj.string("category").trim().ifBlank { "General" }
        val repeatable = obj.boolean("repeatable") ?: false
        val cadence = when {
            obj.boolean("counter") == true -> QuestCadence.Counter
            obj.boolean("daily") == true -> QuestCadence.Daily
            else -> QuestCadence.from(
                obj.string("cadence")
                    .ifBlank { obj.string("frequency") }
                    .ifBlank { obj.string("schedule") },
                repeatable = repeatable,
            )
        }
        val goal = obj.obj("goal")
        val goalTarget = (
            obj.int("goalTarget")
                ?: obj.int("target")
                ?: goal?.int("target")
                ?: goal?.int("goalTarget")
                ?: 1
            ).coerceAtLeast(1)
        val goalUnit = obj.string("goalUnit")
            .ifBlank { obj.string("unit") }
            .ifBlank { goal?.string("unit").orEmpty() }
            .trim()
            .ifBlank { if (cadence == QuestCadence.Counter) "unit" else "completion" }
        val timer = obj.obj("timer")
        val timerMinutes = (
            obj.int("timerMinutes")
                ?: obj.int("minutes")
                ?: timer?.int("minutes")
                ?: timer?.int("timerMinutes")
            )?.coerceIn(1, 24 * 60)
        val explicitId = obj.string("id").trim()
        val id = explicitId.ifBlank {
            stableQuestId(
                title = title,
                category = category,
                xp = xp,
                flavourText = flavourText,
                repeatable = cadence == QuestCadence.Repeatable,
            )
        }

        return ParsedQuest(
            quest = Quest(
                id = id,
                title = title,
                flavourText = flavourText,
                xp = xp,
                category = category,
                icon = obj.string("icon").trim().ifBlank { "star" },
                repeatable = cadence == QuestCadence.Repeatable,
                cadence = cadence.wireName,
                goalTarget = goalTarget,
                goalUnit = goalUnit,
                timerMinutes = timerMinutes,
                createdAt = obj.string("createdAt").trim().ifBlank { Instant.now(clock).toString() },
                archived = obj.boolean("archived") ?: false,
            )
        )
    }

    private fun stableQuestId(
        title: String,
        category: String,
        xp: Int,
        flavourText: String,
        repeatable: Boolean,
    ): String {
        val normalized = listOf(title, category, xp.toString(), flavourText, repeatable.toString())
            .joinToString("|") { it.trim().lowercase().replace(Regex("\\s+"), " ") }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return "quest_$digest"
    }

    private fun JsonObject.string(name: String): String = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asString.orEmpty()
    }.getOrDefault("")

    private fun JsonObject.int(name: String): Int? = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asInt
    }.getOrNull()

    private fun JsonObject.boolean(name: String): Boolean? = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.asBoolean
    }.getOrNull()

    private fun JsonObject.obj(name: String): JsonObject? = runCatching {
        this[name]?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonObject }?.asJsonObject
    }.getOrNull()
}

data class ImportResult(
    val state: QuestLogState,
    val imported: Int,
    val updated: Int,
    val skipped: Int,
    val errors: List<String> = emptyList(),
) {
    val summary: String
        get() = "Imported $imported, updated $updated, skipped $skipped"
}

private data class ParsedQuest(
    val quest: Quest? = null,
    val error: String? = null,
)
