package com.paruchan.questlog.data

import com.paruchan.questlog.core.ImportResult
import com.paruchan.questlog.core.QuestLogJsonCodec
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestPackImporter
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Clock
import java.time.Instant

class QuestLogRepository(
    private val stateFile: File,
    private val codec: QuestLogJsonCodec = QuestLogJsonCodec(),
    private val importer: QuestPackImporter = QuestPackImporter(),
    private val clock: Clock = Clock.systemUTC(),
) {
    @Synchronized
    fun load(): QuestLogState {
        if (!stateFile.exists()) return QuestLogState()
        return codec.decodeState(stateFile.readText())
    }

    @Synchronized
    fun save(state: QuestLogState) {
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
}
