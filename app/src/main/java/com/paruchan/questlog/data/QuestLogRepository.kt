package com.paruchan.questlog.data

import com.paruchan.questlog.core.ImportResult
import com.paruchan.questlog.core.QuestLogJsonCodec
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestPackImporter
import java.io.File
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
        stateFile.parentFile?.mkdirs()
        val temp = File(stateFile.parentFile, "${stateFile.name}.tmp")
        temp.writeText(codec.encode(state))
        if (!temp.renameTo(stateFile)) {
            temp.copyTo(stateFile, overwrite = true)
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
        val restored = codec.decodeState(json)
        save(restored)
        return restored
    }

    @Synchronized
    fun exportBackup(): String {
        val state = load().copy(exportedAt = Instant.now(clock).toString())
        return codec.encode(state)
    }
}
