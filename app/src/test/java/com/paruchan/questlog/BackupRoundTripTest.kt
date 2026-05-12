package com.paruchan.questlog

import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogJsonCodec
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.UserBackupFiles
import com.paruchan.questlog.data.QuestLogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupRoundTripTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `backup export and restore round trip preserves state`() {
        val source = QuestLogRepository(temp.newFile("source.json"))
        source.save(
            QuestLogState(
                quests = listOf(Quest(id = "q1", title = "Make tea", xp = 15)),
                completions = listOf(
                    com.paruchan.questlog.core.Completion(
                        id = "c1",
                        questId = "q1",
                        completedAt = "2026-05-05T12:00:00Z",
                        xpAwarded = 15,
                    ),
                ),
            ),
        )

        val backup = source.exportBackup()
        val restored = QuestLogRepository(temp.newFile("restored.json")).restoreBackup(backup)

        assertEquals("Make tea", restored.quests.single().title)
        assertEquals(15, restored.completions.single().xpAwarded)
        assertNotNull(restored.exportedAt)
    }

    @Test
    fun `backup encoding for an existing state adds exported timestamp`() {
        val repository = QuestLogRepository(
            stateFile = temp.newFile("encode-source.json"),
            clock = fixedClock(day = 5),
        )

        val backup = repository.encodeBackup(
            QuestLogState(quests = listOf(Quest(id = "q1", title = "Keep safe", xp = 25))),
        )
        val restored = QuestLogJsonCodec().decodeBackup(backup)

        assertEquals("Keep safe", restored.quests.single().title)
        assertEquals("2026-05-05T12:00:00Z", restored.exportedAt)
    }

    @Test
    fun `backup restore rejects empty object without replacing current state`() {
        val repository = QuestLogRepository(temp.newFile("state.json"))
        repository.save(QuestLogState(quests = listOf(Quest(id = "q1", title = "Keep me", xp = 15))))

        val error = assertIllegalArgument {
            repository.restoreBackup("{}")
        }

        assertEquals("Backup has an unsupported schema version", error.message)
        assertEquals("Keep me", repository.load().quests.single().title)
    }

    @Test
    fun `backup restore rejects quest pack JSON`() {
        val repository = QuestLogRepository(temp.newFile("quest-pack-state.json"))
        repository.save(QuestLogState(quests = listOf(Quest(id = "q1", title = "Keep me", xp = 15))))

        val error = assertIllegalArgument {
            repository.restoreBackup("""{"kind":"paruchan.quest-pack","quests":[{"title":"Wrong file","xp":1}]}""")
        }

        assertEquals("Backup has an unsupported schema version", error.message)
        assertEquals("Keep me", repository.load().quests.single().title)
    }

    @Test
    fun `local db backup is dated and created once per day`() {
        val stateFile = File(temp.root, "questlog.json")
        val backupDirectory = File(temp.root, "questlog-backups")
        val repository = QuestLogRepository(
            stateFile = stateFile,
            clock = fixedClock(day = 5),
            backupDirectory = backupDirectory,
        )

        repository.save(QuestLogState(quests = listOf(Quest(id = "q1", title = "First save", xp = 15))))
        repository.save(QuestLogState(quests = listOf(Quest(id = "q2", title = "Second save", xp = 20))))

        val backups = backupDirectory.backupNames()
        val backedUp = QuestLogJsonCodec().decodeState(File(backupDirectory, backups.single()).readText())

        assertEquals(listOf("questlog-2026-05-05.json"), backups)
        assertEquals("First save", backedUp.quests.single().title)
    }

    @Test
    fun `local db backups keep newest ten copies`() {
        val stateFile = File(temp.root, "questlog.json")
        val backupDirectory = File(temp.root, "questlog-backups")

        for (day in 1..12) {
            QuestLogRepository(
                stateFile = stateFile,
                clock = fixedClock(day),
                backupDirectory = backupDirectory,
            ).save(QuestLogState(quests = listOf(Quest(id = "q$day", title = "Day $day", xp = day))))
        }

        val backups = backupDirectory.backupNames()

        assertEquals(10, backups.size)
        assertFalse(backups.contains("questlog-2026-05-01.json"))
        assertFalse(backups.contains("questlog-2026-05-02.json"))
        assertTrue(backups.contains("questlog-2026-05-12.json"))
    }

    @Test
    fun `user backup files keep latest plus newest ten dated backups`() {
        val names = (1..12).map {
            "paruchan-quest-log-2026-05-${it.toString().padStart(2, '0')}.json"
        } + listOf(
            UserBackupFiles.LatestBackupName,
            "holiday-photo.json",
        )

        val prune = UserBackupFiles.backupNamesToPrune(names)

        assertEquals(
            listOf(
                "paruchan-quest-log-2026-05-02.json",
                "paruchan-quest-log-2026-05-01.json",
            ),
            prune,
        )
        assertFalse(UserBackupFiles.isDatedBackupName(UserBackupFiles.LatestBackupName))
    }

    private fun assertIllegalArgument(block: () -> Unit): IllegalArgumentException {
        return try {
            block()
            fail("Expected IllegalArgumentException")
            error("unreachable")
        } catch (error: IllegalArgumentException) {
            error
        }
    }

    private fun fixedClock(day: Int): Clock =
        Clock.fixed(Instant.parse("2026-05-${day.toString().padStart(2, '0')}T12:00:00Z"), ZoneOffset.UTC)

    private fun File.backupNames(): List<String> =
        listFiles()
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
}
