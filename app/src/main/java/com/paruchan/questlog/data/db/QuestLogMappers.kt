package com.paruchan.questlog.data.db

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.JournalEntry
import com.paruchan.questlog.core.Level
import com.paruchan.questlog.core.Quest

private val gson = Gson()
private val stringListType = object : TypeToken<List<String>>() {}.type

fun Quest.toEntity(position: Int): QuestEntity =
    QuestEntity(
        id = id,
        title = title,
        flavourText = flavourText,
        xp = xp,
        category = category,
        icon = icon,
        repeatable = repeatable,
        cadence = cadence,
        goalType = goalType,
        goalTarget = goalTarget,
        goalUnit = goalUnit,
        timerMinutes = timerMinutes,
        createdAt = createdAt,
        archived = archived,
        position = position,
    )

fun QuestEntity.toDomain(): Quest =
    Quest(
        id = id,
        title = title,
        flavourText = flavourText,
        xp = xp,
        category = category,
        icon = icon,
        repeatable = repeatable,
        cadence = cadence,
        goalType = goalType,
        goalTarget = goalTarget,
        goalUnit = goalUnit,
        timerMinutes = timerMinutes,
        createdAt = createdAt,
        archived = archived,
    )

fun Completion.toEntity(position: Int): CompletionEntity =
    CompletionEntity(
        id = id,
        questId = questId,
        completedAt = completedAt,
        xpAwarded = xpAwarded,
        progressAmount = progressAmount,
        note = note,
        position = position,
    )

fun CompletionEntity.toDomain(): Completion =
    Completion(
        id = id,
        questId = questId,
        completedAt = completedAt,
        xpAwarded = xpAwarded,
        progressAmount = progressAmount,
        note = note,
    )

fun Level.toEntity(): LevelEntity =
    LevelEntity(
        level = level,
        xpRequired = xpRequired,
        title = title,
        unlocksJson = gson.toJson(unlocks),
    )

fun LevelEntity.toDomain(): Level =
    Level(
        level = level,
        xpRequired = xpRequired,
        title = title,
        unlocks = runCatching { gson.fromJson<List<String>>(unlocksJson, stringListType) }.getOrDefault(emptyList()),
    )

fun JournalEntry.toEntity(): JournalEntryEntity =
    JournalEntryEntity(
        id = id,
        localDate = localDate,
        happyText = happyText,
        gratefulText = gratefulText,
        favoriteMemoryText = favoriteMemoryText,
        createdAt = createdAt,
        updatedAt = updatedAt,
        xpAwarded = xpAwarded,
    )

fun JournalEntryEntity.toDomain(): JournalEntry =
    JournalEntry(
        id = id,
        localDate = localDate,
        happyText = happyText,
        gratefulText = gratefulText,
        favoriteMemoryText = favoriteMemoryText,
        createdAt = createdAt,
        updatedAt = updatedAt,
        xpAwarded = xpAwarded,
    )
