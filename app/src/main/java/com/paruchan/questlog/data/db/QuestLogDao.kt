package com.paruchan.questlog.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuestLogDao {
    @Query("SELECT * FROM quests ORDER BY position ASC, title COLLATE NOCASE ASC")
    fun quests(): List<QuestEntity>

    @Query("SELECT * FROM completions ORDER BY position ASC, completedAt ASC")
    fun completions(): List<CompletionEntity>

    @Query("SELECT * FROM levels ORDER BY xpRequired ASC, level ASC")
    fun levels(): List<LevelEntity>

    @Query("SELECT * FROM journal_entries ORDER BY localDate ASC")
    fun journalEntries(): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE localDate = :localDate LIMIT 1")
    fun journalEntryForDate(localDate: String): JournalEntryEntity?

    @Query("SELECT * FROM journal_entries ORDER BY localDate DESC LIMIT :limit")
    fun recentJournalEntries(limit: Int): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE localDate != :localDate ORDER BY localDate ASC")
    fun reflectionJournalEntries(localDate: String): List<JournalEntryEntity>

    @Query("SELECT COUNT(*) FROM quests")
    fun questCount(): Int

    @Query("SELECT COUNT(*) FROM completions")
    fun completionCount(): Int

    @Query("SELECT COUNT(*) FROM journal_entries")
    fun journalEntryCount(): Int

    @Query("SELECT marker FROM shared_pack_import_markers ORDER BY marker ASC")
    fun importedMarkers(): List<String>

    @Query("SELECT value FROM app_metadata WHERE `key` = :key LIMIT 1")
    fun metadataValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertQuests(quests: List<QuestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCompletions(completions: List<CompletionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertLevels(levels: List<LevelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertJournalEntries(entries: List<JournalEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertJournalEntry(entry: JournalEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertImportMarkers(markers: List<SharedPackImportMarkerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMetadata(metadata: AppMetadataEntity)

    @Query("DELETE FROM quests")
    fun deleteQuests()

    @Query("DELETE FROM completions")
    fun deleteCompletions()

    @Query("DELETE FROM levels")
    fun deleteLevels()

    @Query("DELETE FROM journal_entries")
    fun deleteJournalEntries()
}
