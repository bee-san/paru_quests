package com.paruchan.questlog

import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.data.QuestLogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

    private fun assertIllegalArgument(block: () -> Unit): IllegalArgumentException {
        return try {
            block()
            fail("Expected IllegalArgumentException")
            error("unreachable")
        } catch (error: IllegalArgumentException) {
            error
        }
    }
}
