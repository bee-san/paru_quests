package com.paruchan.questlog

import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.JournalEntry
import com.paruchan.questlog.core.QuestLogJsonCodec
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.UserBackupFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `backup export and restore round trip preserves state`() {
        val source = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("source.json"))
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
        val restored = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("restored.json")).restoreBackup(backup)

        assertEquals("Make tea", restored.quests.single().title)
        assertEquals(15, restored.completions.single().xpAwarded)
        assertNotNull(QuestLogJsonCodec().decodeBackup(backup).exportedAt)
    }

    @Test
    fun `schema one backup restore defaults journal entries to empty`() {
        val repository = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("schema-one.json"))

        val restored = repository.restoreBackup(
            """
            {
              "schemaVersion": 1,
              "quests": [{"id":"q1","title":"Old quest","xp":15}],
              "completions": [],
              "levels": [],
              "exportedAt": "2026-05-05T12:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals("Old quest", restored.quests.single().title)
        assertTrue(restored.journalEntries.isEmpty())
        assertEquals(2, restored.schemaVersion)
    }

    @Test
    fun `schema two backup round trip preserves journal entries`() {
        val source = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("journal-source.json"))
        source.save(
            QuestLogState(
                quests = listOf(Quest(id = "q1", title = "Make tea", xp = 15)),
                journalEntries = listOf(
                    JournalEntry(
                        id = "journal-2026-05-05",
                        localDate = "2026-05-05",
                        happyText = "Paruchan had tea.",
                        gratefulText = "Paruchan is grateful for quiet.",
                        favoriteMemoryText = "A cosy cup.",
                        createdAt = "2026-05-05T12:00:00Z",
                        updatedAt = "2026-05-05T12:01:00Z",
                        xpAwarded = 10,
                    ),
                ),
            ),
        )

        val backup = source.exportBackup()
        val restored = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("journal-restored.json")).restoreBackup(backup)

        assertEquals(2, QuestLogJsonCodec().decodeBackup(backup).schemaVersion)
        assertEquals("A cosy cup.", restored.journalEntries.single().favoriteMemoryText)
        assertEquals(10, restored.journalEntries.single().xpAwarded)
    }

    @Test
    fun `backup encoding for an existing state adds exported timestamp`() {
        val repository = testQuestLogRepository(
            root = temp.root,
            legacyStateFile = temp.newFile("encode-source.json"),
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
        val repository = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("state.json"))
        repository.save(QuestLogState(quests = listOf(Quest(id = "q1", title = "Keep me", xp = 15))))

        val error = assertIllegalArgument {
            repository.restoreBackup("{}")
        }

        assertEquals("Backup has an unsupported schema version", error.message)
        assertEquals("Keep me", repository.load().quests.single().title)
    }

    @Test
    fun `backup restore rejects quest pack JSON`() {
        val repository = testQuestLogRepository(temp.root, legacyStateFile = temp.newFile("quest-pack-state.json"))
        repository.save(QuestLogState(quests = listOf(Quest(id = "q1", title = "Keep me", xp = 15))))

        val error = assertIllegalArgument {
            repository.restoreBackup("""{"kind":"paruchan.quest-pack","quests":[{"title":"Wrong file","xp":1}]}""")
        }

        assertEquals("Backup has an unsupported schema version", error.message)
        assertEquals("Keep me", repository.load().quests.single().title)
    }

    @Test
    fun `local db backup is dated and created once per day`() {
        val backupDirectory = File(temp.root, "questlog-backups")
        val repository = testQuestLogRepository(
            root = temp.root,
            legacyStateFile = File(temp.root, "questlog.json"),
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
        val backupDirectory = File(temp.root, "questlog-backups")

        for (day in 1..12) {
            testQuestLogRepository(
                root = File(temp.root, "day-$day").also { it.mkdirs() },
                legacyStateFile = File(temp.root, "questlog-$day.json"),
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
