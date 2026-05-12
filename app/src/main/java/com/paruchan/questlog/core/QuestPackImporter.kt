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
    fun mergeQuestPack(
        state: QuestLogState,
        json: String,
        closePrevious: Boolean = true,
    ): ImportResult {
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
        val existingImplicitIds = implicitQuestIdsByCurrentStableId(state.quests)
        val incomingQuestIds = mutableSetOf<String>()
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
            val existingId = existingQuestIdFor(parsed, existingById, existingImplicitIds)
            val mergedQuestId = existingId ?: quest.id
            incomingQuestIds += mergedQuestId
            val existing = existingId?.let { existingById[it] }
            if (existing == null) {
                existingById[quest.id] = quest
                order += quest.id
                imported++
            } else {
                existingById[existing.id] = quest.copy(
                    id = existing.id,
                    createdAt = existing.createdAt.ifBlank { quest.createdAt },
                )
                updated++
            }
        }

        val merged = order.mapNotNull { existingById[it] }
        val closeResult = if (closePrevious && errors.isEmpty()) {
            closeQuestsOutside(state.copy(quests = merged), incomingQuestIds)
        } else {
            ClosePreviousResult(state.copy(quests = merged), closed = 0)
        }
        return ImportResult(
            state = closeResult.state,
            imported = imported,
            updated = updated,
            closed = closeResult.closed,
            skipped = skipped,
            errors = errors,
            incomingQuestIds = incomingQuestIds,
        )
    }

    fun closeQuestsOutside(state: QuestLogState, questIdsToKeepOpen: Set<String>): ClosePreviousResult {
        if (questIdsToKeepOpen.isEmpty()) return ClosePreviousResult(state, closed = 0)

        var closed = 0
        val quests = state.quests.map { quest ->
            if (quest.id !in questIdsToKeepOpen && !quest.archived) {
                closed++
                quest.copy(archived = true)
            } else {
                quest
            }
        }
        return ClosePreviousResult(state.copy(quests = quests), closed = closed)
    }

    fun questIdsInQuestPack(json: String, state: QuestLogState? = null): Set<String> {
        val root = try {
            JsonParser.parseString(json)
        } catch (error: Exception) {
            return emptySet()
        }
        val quests = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject && root.asJsonObject["quests"]?.isJsonArray == true -> root.asJsonObject["quests"].asJsonArray
            else -> return emptySet()
        }
        val existingById = state?.quests?.associateBy { it.id }.orEmpty()
        val existingImplicitIds = state?.let { implicitQuestIdsByCurrentStableId(it.quests) }.orEmpty()
        return quests.mapIndexedNotNull { index, element ->
            val parsed = parseQuest(index, element)
            val quest = parsed.quest ?: return@mapIndexedNotNull null
            existingQuestIdFor(parsed, existingById, existingImplicitIds) ?: quest.id
        }.toSet()
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
        val cadenceText = obj.string("cadence")
            .ifBlank { obj.string("frequency") }
            .ifBlank { obj.string("schedule") }
        val legacyCounter = obj.boolean("counter") == true || cadenceText.isCounterAlias()
        val cadence = when {
            obj.boolean("daily") == true -> QuestCadence.Daily
            else -> QuestCadence.from(cadenceText, repeatable = repeatable)
        }
        val timer = obj.obj("timer")
        val goalType = when {
            legacyCounter -> QuestGoalType.Counter
            else -> QuestGoalType.from(
                obj.string("goalType")
                    .ifBlank { obj.string("goal_type") }
                    .ifBlank { obj.obj("goal")?.string("type").orEmpty() },
            )
        }
        val goal = obj.obj("goal")
        val explicitGoalTarget = (
            obj.int("goalTarget")
                ?: obj.int("target")
                ?: goal?.int("target")
                ?: goal?.int("goalTarget")
            )
        val timerMinutes = (
            obj.int("timerMinutes")
                ?: obj.int("minutes")
                ?: timer?.int("minutes")
                ?: timer?.int("timerMinutes")
            )?.coerceIn(1, 24 * 60)
        val goalTarget = when (goalType) {
            QuestGoalType.Timer -> (explicitGoalTarget ?: timerMinutes ?: 1).coerceAtLeast(1)
            else -> (explicitGoalTarget ?: 1).coerceAtLeast(1)
        }
        val goalUnit = obj.string("goalUnit")
            .ifBlank { obj.string("unit") }
            .ifBlank { goal?.string("unit").orEmpty() }
            .trim()
            .ifBlank { defaultGoalUnit(goalType) }
        val icon = obj.string("icon").trim().ifBlank { "star" }
        val explicitId = obj.string("id").trim()
        val implicitId = if (explicitId.isBlank()) {
            stableQuestId(
                title = title,
                cadence = cadence,
                goalType = goalType,
                goalTarget = goalTarget,
                goalUnit = goalUnit,
                timerMinutes = if (goalType == QuestGoalType.Timer) null else timerMinutes,
                icon = icon,
            )
        } else {
            null
        }
        val id = explicitId.ifBlank { implicitId.orEmpty() }

        return ParsedQuest(
            quest = Quest(
                id = id,
                title = title,
                flavourText = flavourText,
                xp = xp,
                category = category,
                icon = icon,
                repeatable = cadence == QuestCadence.Repeatable,
                cadence = cadence.wireName,
                goalType = goalType.wireName,
                goalTarget = goalTarget,
                goalUnit = goalUnit,
                timerMinutes = if (goalType == QuestGoalType.Timer) null else timerMinutes,
                createdAt = obj.string("createdAt").trim().ifBlank { Instant.now(clock).toString() },
                archived = obj.boolean("archived") ?: false,
            ),
            implicitId = implicitId,
        )
    }

    private fun stableQuestId(
        title: String,
        cadence: QuestCadence,
        goalType: QuestGoalType,
        goalTarget: Int,
        goalUnit: String,
        timerMinutes: Int?,
        icon: String,
    ): String {
        return stableQuestIdFromParts(
            title,
            cadence.wireName,
            goalType.wireName,
            goalTarget.toString(),
            goalUnit,
            timerMinutes?.toString().orEmpty(),
            icon,
        )
    }

    private fun legacyCategoryQuestId(
        title: String,
        category: String,
        cadence: QuestCadence,
        goalType: QuestGoalType,
        goalTarget: Int,
        goalUnit: String,
        timerMinutes: Int?,
        icon: String,
    ): String {
        return stableQuestIdFromParts(
            title,
            category,
            cadence.wireName,
            goalType.wireName,
            goalTarget.toString(),
            goalUnit,
            timerMinutes?.toString().orEmpty(),
            icon,
        )
    }

    private fun legacyGoalQuestId(
        quest: Quest,
    ): String {
        return stableQuestIdFromParts(
            quest.title,
            quest.category,
            quest.cadence,
            quest.goalTarget.coerceAtLeast(1).toString(),
            quest.goalUnit,
            quest.timerMinutes?.toString().orEmpty(),
            quest.icon,
        )
    }

    private fun legacyOriginalQuestId(quest: Quest): String {
        return stableQuestIdFromParts(
            quest.title,
            quest.category,
            quest.xp.toString(),
            quest.flavourText,
            quest.repeatable.toString(),
        )
    }

    private fun stableQuestIdFromParts(vararg parts: String): String {
        val normalized = parts
            .joinToString("|") { it.trim().lowercase().replace(Regex("\\s+"), " ") }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)
        return "quest_$digest"
    }

    private fun implicitQuestIdsByCurrentStableId(quests: List<Quest>): Map<String, String> {
        return quests
            .filter { it.id in implicitIdsFor(it) }
            .groupBy { currentStableIdFor(it) }
            .mapNotNull { (stableId, matchingQuests) ->
                matchingQuests.singleOrNull()?.let { stableId to it.id }
            }
            .toMap()
    }

    private fun existingQuestIdFor(
        parsed: ParsedQuest,
        existingById: Map<String, Quest>,
        existingImplicitIds: Map<String, String>,
    ): String? {
        val quest = parsed.quest ?: return null
        return when {
            existingById.containsKey(quest.id) -> quest.id
            parsed.implicitId != null -> existingImplicitIds[parsed.implicitId]
            else -> null
        }
    }

    private fun currentStableIdFor(quest: Quest): String {
        val cadence = QuestCadence.from(quest)
        val goalType = QuestGoalType.from(quest)
        return stableQuestId(
            title = quest.title,
            cadence = cadence,
            goalType = goalType,
            goalTarget = quest.goalTarget.coerceAtLeast(1),
            goalUnit = quest.goalUnit,
            timerMinutes = if (goalType == QuestGoalType.Timer) null else quest.timerMinutes,
            icon = quest.icon,
        )
    }

    private fun implicitIdsFor(quest: Quest): Set<String> {
        val cadence = QuestCadence.from(quest)
        val goalType = QuestGoalType.from(quest)
        return setOf(
            currentStableIdFor(quest),
            legacyCategoryQuestId(
                title = quest.title,
                category = quest.category,
                cadence = cadence,
                goalType = goalType,
                goalTarget = quest.goalTarget.coerceAtLeast(1),
                goalUnit = quest.goalUnit,
                timerMinutes = if (goalType == QuestGoalType.Timer) null else quest.timerMinutes,
                icon = quest.icon,
            ),
            legacyGoalQuestId(quest),
            legacyOriginalQuestId(quest),
        )
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

    private fun String.isCounterAlias(): Boolean =
        trim().lowercase() in setOf("counter", "count", "per", "per-unit", "per_unit")

    private fun defaultGoalUnit(goalType: QuestGoalType): String =
        when (goalType) {
            QuestGoalType.Counter -> "unit"
            QuestGoalType.Timer -> "minute"
            QuestGoalType.Completion -> "completion"
        }
}

data class ImportResult(
    val state: QuestLogState,
    val imported: Int,
    val updated: Int,
    val skipped: Int,
    val closed: Int = 0,
    val errors: List<String> = emptyList(),
    val incomingQuestIds: Set<String> = emptySet(),
) {
    val summary: String
        get() {
            val closedText = if (closed > 0) ", closed $closed" else ""
            return "Imported $imported, updated $updated$closedText, skipped $skipped"
        }
}

data class ClosePreviousResult(
    val state: QuestLogState,
    val closed: Int,
)

private data class ParsedQuest(
    val quest: Quest? = null,
    val error: String? = null,
    val implicitId: String? = null,
)
