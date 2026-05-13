package com.paruchan.questlog

import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.Level
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.data.KEY_LEGACY_JSON_MIGRATED
import com.paruchan.questlog.data.QuestLogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class QuestLogDatabaseMigrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `json migration inserts existing quests completions and levels once`() {
        val legacyFile = File(temp.root, "questlog.json")
        legacyFile.writeText(
            """
            {
              "schemaVersion": 1,
              "quests": [{"id":"q1","title":"Legacy quest","xp":15,"archived":true}],
              "completions": [{"id":"c1","questId":"q1","completedAt":"2026-05-05T12:00:00Z","xpAwarded":15}],
              "levels": [{"level":1,"xpRequired":0,"title":"Custom start","unlocks":["Saved"]}]
            }
            """.trimIndent(),
        )
        val database = testParuchanDatabase()
        val repository = QuestLogRepository(
            database = database,
            legacyStateFile = legacyFile,
            backupDirectory = File(temp.root, "questlog-backups"),
        )

        val migrated = repository.load()
        legacyFile.writeText(
            """{"schemaVersion":1,"quests":[{"id":"q2","title":"Should not remigrate","xp":99}],"completions":[],"levels":[]}""",
        )
        val loadedAgain = repository.load()

        assertEquals("Legacy quest", migrated.quests.single().title)
        assertTrue(migrated.quests.single().archived)
        assertEquals("c1", migrated.completions.single().id)
        assertEquals("Custom start", migrated.levels.single().title)
        assertEquals("Legacy quest", loadedAgain.quests.single().title)
        assertTrue(legacyFile.exists())
        assertEquals("true", database.questLogDao().metadataValue(KEY_LEGACY_JSON_MIGRATED))
    }

    @Test
    fun `failed migration does not mark migration complete`() {
        val legacyFile = File(temp.root, "questlog.json")
        legacyFile.writeText("{bad json")
        val database = testParuchanDatabase()
        val repository = QuestLogRepository(
            database = database,
            legacyStateFile = legacyFile,
            backupDirectory = File(temp.root, "questlog-backups"),
        )

        try {
            repository.load()
        } catch (error: IllegalArgumentException) {
            assertEquals("Backup is not valid JSON", error.message)
        }

        assertNull(database.questLogDao().metadataValue(KEY_LEGACY_JSON_MIGRATED))
        assertEquals(0, database.questLogDao().questCount())
    }

    @Test
    fun `clean install marks json migration complete without legacy file`() {
        val database = testParuchanDatabase()
        val repository = QuestLogRepository(
            database = database,
            legacyStateFile = File(temp.root, "missing-questlog.json"),
            backupDirectory = File(temp.root, "questlog-backups"),
        )

        val state = repository.load()

        assertTrue(state.quests.isEmpty())
        assertTrue(state.journalEntries.isEmpty())
        assertEquals("true", database.questLogDao().metadataValue(KEY_LEGACY_JSON_MIGRATED))
    }

    @Test
    fun `restore replaces quest completion level and journal tables without replacing markers`() {
        val repository = testQuestLogRepository(temp.root)
        repository.save(
            QuestLogState(
                quests = listOf(Quest(id = "old", title = "Old", xp = 1)),
                completions = listOf(Completion(id = "old-c", questId = "old", completedAt = "2026-05-04T12:00:00Z", xpAwarded = 1)),
                levels = listOf(Level(level = 1, xpRequired = 0, title = "Old level", unlocks = emptyList())),
            ),
        )
        repository.addImportedSharedPackMarkers(setOf("pack:1:abc"))

        val restored = repository.restoreBackup(
            """
            {
              "schemaVersion": 2,
              "quests": [{"id":"new","title":"New","xp":2}],
              "completions": [],
              "levels": [{"level":1,"xpRequired":0,"title":"New level","unlocks":[]}],
              "journalEntries": [{"id":"journal-2026-05-05","localDate":"2026-05-05","happyText":"Happy","gratefulText":"Grateful","favoriteMemoryText":"Memory","createdAt":"2026-05-05T12:00:00Z","updatedAt":"2026-05-05T12:00:00Z","xpAwarded":10}],
              "exportedAt": "2026-05-05T12:00:00Z"
            }
            """.trimIndent(),
        )

        assertEquals(listOf("new"), restored.quests.map { it.id })
        assertTrue(restored.completions.isEmpty())
        assertEquals("New level", restored.levels.single().title)
        assertEquals("Memory", restored.journalEntries.single().favoriteMemoryText)
        assertFalse(repository.importedSharedPackMarkers().isEmpty())
    }
}
