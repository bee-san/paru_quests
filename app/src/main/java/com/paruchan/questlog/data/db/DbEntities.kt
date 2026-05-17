package com.paruchan.questlog.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "quests")
data class QuestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val flavourText: String,
    val xp: Int,
    val category: String,
    val icon: String,
    val repeatable: Boolean,
    val cadence: String,
    val goalType: String,
    val goalTarget: Int,
    val goalUnit: String,
    val timerMinutes: Int?,
    val createdAt: String,
    val archived: Boolean,
    val position: Int,
)

@Entity(tableName = "completions")
data class CompletionEntity(
    @PrimaryKey val id: String,
    val questId: String,
    val completedAt: String,
    val xpAwarded: Int,
    val progressAmount: Int,
    val note: String?,
    val position: Int,
)

@Entity(tableName = "levels")
data class LevelEntity(
    @PrimaryKey val level: Int,
    val xpRequired: Int,
    val title: String,
    val unlocksJson: String,
)

@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["localDate"], unique = true)],
)
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val localDate: String,
    val happyText: String,
    val gratefulText: String,
    val favoriteMemoryText: String,
    val createdAt: String,
    val updatedAt: String,
    val xpAwarded: Int,
)

@Entity(tableName = "shared_pack_import_markers")
data class SharedPackImportMarkerEntity(
    @PrimaryKey val marker: String,
    val createdAt: String,
)

@Entity(tableName = "app_metadata")
data class AppMetadataEntity(
    @PrimaryKey val key: String,
    val value: String,
)
