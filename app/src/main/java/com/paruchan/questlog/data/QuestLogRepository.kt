package com.paruchan.questlog.data

import android.content.Context
import com.paruchan.questlog.core.ImportResult
import com.paruchan.questlog.core.JournalEntry
import com.paruchan.questlog.core.QuestLogJsonCodec
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestPackImporter
import com.paruchan.questlog.data.db.AppMetadataEntity
import com.paruchan.questlog.data.db.ParuchanDatabase
import com.paruchan.questlog.data.db.SharedPackImportMarkerEntity
import com.paruchan.questlog.data.db.toDomain
import com.paruchan.questlog.data.db.toEntity
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

class QuestLogRepository(
    private val database: ParuchanDatabase,
    private val legacyStateFile: File?,
    private val codec: QuestLogJsonCodec = QuestLogJsonCodec(),
    private val importer: QuestPackImporter = QuestPackImporter(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val backupDirectory: File,
    private val backupRetentionCount: Int = DEFAULT_BACKUP_RETENTION_COUNT,
) {
    @Volatile
    private var initialized = false

    @Synchronized
    fun load(): QuestLogState {
        ensureInitialized()
        val state = readState()
        createDailyBackupIfNeeded(state)
        return state
    }

    @Synchronized
    fun save(state: QuestLogState) {
        ensureInitialized()
        val before = readState()
        val shouldBackupAfterSave = !dailyBackupFile().exists()
        createDailyBackupIfNeeded(before)
        writeState(state)
        if (shouldBackupAfterSave && !dailyBackupFile().exists()) {
            createDailyBackupIfNeeded(readState())
        }
    }

    @Synchronized
    fun importQuestPack(json: String): ImportResult {
        val result = importer.mergeQuestPack(load(), json)
        save(result.state)
        return result
    }

    @Synchronized
    fun restoreBackup(json: String): QuestLogState {
        val restored = codec.decodeBackup(json)
        save(restored)
        return readState()
    }

    @Synchronized
    fun exportBackup(): String = encodeBackup(load())

    @Synchronized
    fun encodeBackup(state: QuestLogState): String {
        return codec.encode(state.copy(exportedAt = Instant.now(clock).toString()))
    }

    @Synchronized
    fun saveJournalEntry(
        happyText: String,
        gratefulText: String,
        favoriteMemoryText: String,
        localDate: LocalDate = LocalDate.now(clock),
    ): JournalEntry {
        val dateText = localDate.toString()
        val now = Instant.now(clock).toString()
        val current = load()
        val existing = current.journalEntries.firstOrNull { it.localDate == dateText }
        val trimmedHappy = happyText.trim()
        val trimmedGrateful = gratefulText.trim()
        val trimmedFavoriteMemory = favoriteMemoryText.trim()
        val complete = trimmedHappy.isNotBlank() && trimmedGrateful.isNotBlank() && trimmedFavoriteMemory.isNotBlank()
        val xpAwarded = when {
            existing?.xpAwarded == JOURNAL_ENTRY_XP -> JOURNAL_ENTRY_XP
            complete -> JOURNAL_ENTRY_XP
            else -> 0
        }
        val entry = JournalEntry(
            id = existing?.id?.ifBlank { "journal-$dateText" } ?: "journal-$dateText",
            localDate = dateText,
            happyText = trimmedHappy,
            gratefulText = trimmedGrateful,
            favoriteMemoryText = trimmedFavoriteMemory,
            createdAt = existing?.createdAt?.ifBlank { now } ?: now,
            updatedAt = now,
            xpAwarded = xpAwarded,
        )
        val nextEntries = current.journalEntries.filterNot { it.localDate == dateText } + entry
        save(current.copy(journalEntries = nextEntries))
        return entry
    }

    @Synchronized
    fun journalEntryForDate(localDate: LocalDate): JournalEntry {
        ensureInitialized()
        return database.questLogDao().journalEntryForDate(localDate.toString())?.toDomain()
            ?: JournalEntry(localDate = localDate.toString())
    }

    @Synchronized
    fun recentJournalEntries(limit: Int): List<JournalEntry> {
        ensureInitialized()
        return database.questLogDao()
            .recentJournalEntries(limit.coerceAtLeast(0))
            .map { it.toDomain() }
    }

    @Synchronized
    fun dailyReflectionForDate(localDate: LocalDate): String? {
        ensureInitialized()
        val candidates = database.questLogDao()
            .reflectionJournalEntries(localDate.toString())
            .flatMap { entry ->
                listOf(entry.favoriteMemoryText.trim(), entry.happyText.trim())
                    .filter { it.isNotBlank() }
            }
        if (candidates.isEmpty()) return null
        val index = Math.floorMod(localDate.toEpochDay(), candidates.size.toLong()).toInt()
        return candidates[index]
    }

    @Synchronized
    fun importedSharedPackMarkers(): Set<String> {
        ensureInitialized()
        return database.questLogDao().importedMarkers().toSet()
    }

    @Synchronized
    fun addImportedSharedPackMarkers(markers: Set<String>) {
        if (markers.isEmpty()) return
        ensureInitialized()
        val now = Instant.now(clock).toString()
        database.questLogDao().insertImportMarkers(
            markers.map { SharedPackImportMarkerEntity(marker = it, createdAt = now) },
        )
    }

    @Synchronized
    fun createDailyBackupIfNeeded(): File? {
        ensureInitialized()
        return createDailyBackupIfNeeded(readState())
    }

    private fun ensureInitialized() {
        if (initialized && database.questLogDao().metadataValue(KEY_LEGACY_JSON_MIGRATED) == "true") return

        val legacyState = legacyStateFile
            ?.takeIf { it.exists() && it.length() > 0L }
            ?.let { codec.decodeState(it.readText()) }

        database.runInTransaction {
            val dao = database.questLogDao()
            if (dao.metadataValue(KEY_LEGACY_JSON_MIGRATED) == "true") {
                return@runInTransaction
            }

            if (legacyState != null) {
                replaceStateTables(legacyState)
            } else if (!hasAnyDatabaseState()) {
                replaceStateTables(QuestLogState())
            }

            dao.upsertMetadata(AppMetadataEntity(KEY_LEGACY_JSON_MIGRATED, "true"))
        }
        initialized = true
    }

    private fun hasAnyDatabaseState(): Boolean {
        val dao = database.questLogDao()
        return dao.questCount() > 0 ||
            dao.completionCount() > 0 ||
            dao.journalEntryCount() > 0 ||
            dao.levels().isNotEmpty()
    }

    private fun readState(): QuestLogState {
        val dao = database.questLogDao()
        return codec.normalize(
            QuestLogState(
                schemaVersion = 2,
                quests = dao.quests().map { it.toDomain() },
                completions = dao.completions().map { it.toDomain() },
                levels = dao.levels().map { it.toDomain() },
                journalEntries = dao.journalEntries().map { it.toDomain() },
            ),
        )
    }

    private fun writeState(state: QuestLogState) {
        database.runInTransaction {
            replaceStateTables(state)
        }
    }

    private fun replaceStateTables(state: QuestLogState) {
        val normalized = codec.normalize(state)
        val dao = database.questLogDao()
        dao.deleteQuests()
        dao.deleteCompletions()
        dao.deleteLevels()
        dao.deleteJournalEntries()
        dao.insertQuests(normalized.quests.mapIndexed { index, quest -> quest.toEntity(index) })
        dao.insertCompletions(normalized.completions.mapIndexed { index, completion -> completion.toEntity(index) })
        dao.insertLevels(normalized.levels.map { it.toEntity() })
        dao.insertJournalEntries(normalized.journalEntries.map { it.toEntity() })
    }

    private fun createDailyBackupIfNeeded(state: QuestLogState): File? {
        if (!state.hasUserData()) {
            pruneBackups()
            return null
        }

        backupDirectory.mkdirs()
        val target = dailyBackupFile()
        if (target.exists()) {
            pruneBackups()
            return null
        }

        val temp = File(backupDirectory, "${target.name}.tmp")
        return try {
            temp.writeText(encodeBackup(state))
            try {
                Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE)
            } catch (error: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING)
            }
            target
        } catch (error: FileAlreadyExistsException) {
            null
        } finally {
            temp.delete()
            pruneBackups()
        }
    }

    private fun dailyBackupFile(): File {
        val date = LocalDate.now(clock)
        return File(backupDirectory, "questlog-$date.json")
    }

    private fun pruneBackups() {
        backupFiles()
            .drop(backupRetentionCount.coerceAtLeast(1))
            .forEach { it.delete() }
    }

    private fun backupFiles(): List<File> {
        if (!backupDirectory.exists()) return emptyList()
        val pattern = Regex("questlog-\\d{4}-\\d{2}-\\d{2}\\.json")
        return backupDirectory.listFiles { file ->
            file.isFile && pattern.matches(file.name)
        }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }

    companion object {
        fun create(
            context: Context,
            codec: QuestLogJsonCodec = QuestLogJsonCodec(),
            importer: QuestPackImporter = QuestPackImporter(),
            clock: Clock = Clock.systemDefaultZone(),
        ): QuestLogRepository {
            val appContext = context.applicationContext
            return QuestLogRepository(
                database = ParuchanDatabase.getInstance(appContext),
                legacyStateFile = File(appContext.filesDir, "questlog.json"),
                codec = codec,
                importer = importer,
                clock = clock,
                backupDirectory = File(appContext.filesDir, "questlog-backups"),
            )
        }
    }
}

private const val DEFAULT_BACKUP_RETENTION_COUNT = 10
private const val JOURNAL_ENTRY_XP = 10
const val KEY_LEGACY_JSON_MIGRATED = "legacy_json_migrated"

private fun QuestLogState.hasUserData(): Boolean =
    quests.isNotEmpty() || completions.isNotEmpty() || journalEntries.isNotEmpty()
