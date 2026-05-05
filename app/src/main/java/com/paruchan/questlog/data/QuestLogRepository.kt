package com.paruchan.questlog.data

import com.paruchan.questlog.core.ImportResult
import com.paruchan.questlog.core.QuestLogJsonCodec
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestPackImporter
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
    private val stateFile: File,
    private val codec: QuestLogJsonCodec = QuestLogJsonCodec(),
    private val importer: QuestPackImporter = QuestPackImporter(),
    private val clock: Clock = Clock.systemDefaultZone(),
    private val backupDirectory: File = defaultBackupDirectory(stateFile),
    private val backupRetentionCount: Int = DEFAULT_BACKUP_RETENTION_COUNT,
) {
    @Synchronized
    fun load(): QuestLogState {
        if (!stateFile.exists()) return QuestLogState()
        createDailyBackupIfNeeded()
        return codec.decodeState(stateFile.readText())
    }

    @Synchronized
    fun save(state: QuestLogState) {
        val shouldBackupAfterSave = !dailyBackupFile().exists()
        createDailyBackupIfNeeded()
        val parent = stateFile.absoluteFile.parentFile ?: error("State file must have a parent directory")
        parent.mkdirs()
        val temp = File(parent, "${stateFile.name}.tmp")
        temp.writeText(codec.encode(state))
        try {
            try {
                Files.move(temp.toPath(), stateFile.absoluteFile.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            } catch (error: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), stateFile.absoluteFile.toPath(), REPLACE_EXISTING)
            }
        } finally {
            temp.delete()
        }
        if (shouldBackupAfterSave && !dailyBackupFile().exists()) {
            createDailyBackupIfNeeded()
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
        return restored
    }

    @Synchronized
    fun exportBackup(): String {
        val state = load().copy(exportedAt = Instant.now(clock).toString())
        return codec.encode(state)
    }

    @Synchronized
    fun createDailyBackupIfNeeded(): File? {
        if (!stateFile.exists() || stateFile.length() == 0L) {
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
            Files.copy(stateFile.absoluteFile.toPath(), temp.toPath(), REPLACE_EXISTING)
            try {
                Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE)
            } catch (error: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath())
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
        val name = stateFile.nameWithoutExtension.ifBlank { stateFile.name }
        val extension = stateFile.extension.ifBlank { "json" }
        return File(backupDirectory, "$name-$date.$extension")
    }

    private fun pruneBackups() {
        backupFiles()
            .drop(backupRetentionCount.coerceAtLeast(1))
            .forEach { it.delete() }
    }

    private fun backupFiles(): List<File> {
        if (!backupDirectory.exists()) return emptyList()
        val name = stateFile.nameWithoutExtension.ifBlank { stateFile.name }
        val extension = stateFile.extension.ifBlank { "json" }
        val pattern = Regex("${Regex.escape(name)}-\\d{4}-\\d{2}-\\d{2}\\.${Regex.escape(extension)}")
        return backupDirectory.listFiles { file ->
            file.isFile && pattern.matches(file.name)
        }
            ?.sortedByDescending { it.name }
            .orEmpty()
    }
}

private const val DEFAULT_BACKUP_RETENTION_COUNT = 10

private fun defaultBackupDirectory(stateFile: File): File {
    val parent = stateFile.absoluteFile.parentFile ?: error("State file must have a parent directory")
    return File(parent, "questlog-backups")
}
