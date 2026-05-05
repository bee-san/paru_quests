package com.paruchan.questlog

import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.data.QuestLogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
