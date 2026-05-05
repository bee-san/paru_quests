package com.paruchan.questlog

import com.paruchan.questlog.data.QuestLogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QuestPackImportFlowTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `imports a full paruchan quest pack and persists every quest shape`() {
        val repository = QuestLogRepository(File(temp.root, "questlog.json"))

        val firstImport = repository.importQuestPack(fullParuchanPack())
        val reloaded = QuestLogRepository(File(temp.root, "restored.json")).restoreBackup(repository.exportBackup())

        assertEquals(4, firstImport.imported)
        assertEquals(0, firstImport.updated)
        assertEquals(0, firstImport.skipped)
        assertTrue(firstImport.errors.isEmpty())
        assertEquals(4, reloaded.quests.size)

        val oneOff = reloaded.quests.first { it.id == "full-once" }
        assertEquals("Find a paruchan sticker", oneOff.title)
        assertEquals("Tiny mascot sighted.", oneOff.flavourText)
        assertEquals(120, oneOff.xp)
        assertEquals("Paruchan", oneOff.category)
        assertEquals("paruchan", oneOff.icon)
        assertFalse(oneOff.repeatable)
        assertEquals("once", oneOff.cadence)
        assertEquals(3, oneOff.goalTarget)
        assertEquals("sticker", oneOff.goalUnit)
        assertEquals(15, oneOff.timerMinutes)
        assertEquals("2026-05-05T12:00:00Z", oneOff.createdAt)
        assertFalse(oneOff.archived)

        val daily = reloaded.quests.first { it.id == "full-daily" }
        assertEquals("daily", daily.cadence)
        assertFalse(daily.repeatable)
        assertEquals(2, daily.goalTarget)
        assertEquals("cup", daily.goalUnit)
        assertEquals(5, daily.timerMinutes)

        val repeatable = reloaded.quests.first { it.id == "full-repeatable" }
        assertEquals("repeatable", repeatable.cadence)
        assertTrue(repeatable.repeatable)
        assertEquals(1, repeatable.goalTarget)
        assertEquals("completion", repeatable.goalUnit)
        assertNull(repeatable.timerMinutes)

        val counter = reloaded.quests.first { it.id == "full-counter" }
        assertEquals("counter", counter.cadence)
        assertFalse(counter.repeatable)
        assertEquals(40, counter.xp)
        assertEquals(1, counter.goalTarget)
        assertEquals("page", counter.goalUnit)
        assertEquals(30, counter.timerMinutes)
    }

    @Test
    fun `reimporting a paruchan quest pack updates by id without duplicates`() {
        val repository = QuestLogRepository(File(temp.root, "questlog.json"))

        repository.importQuestPack(fullParuchanPack())
        val secondImport = repository.importQuestPack(
            fullParuchanPack().replace(
                oldValue = """"xp": 120""",
                newValue = """"xp": 175""",
            ),
        )
        val reloaded = repository.load()

        assertEquals(0, secondImport.imported)
        assertEquals(4, secondImport.updated)
        assertEquals(0, secondImport.skipped)
        assertEquals(4, reloaded.quests.size)
        assertEquals(175, reloaded.quests.first { it.id == "full-once" }.xp)
    }

    private fun fullParuchanPack(): String = """
        {
          "kind": "paruchan.quest-pack",
          "schemaVersion": 1,
          "name": "Full Paruchan Import Pack",
          "quests": [
            {
              "id": "full-once",
              "title": "Find a paruchan sticker",
              "flavourText": "Tiny mascot sighted.",
              "xp": 120,
              "category": "Paruchan",
              "icon": "paruchan",
              "cadence": "once",
              "goalTarget": 3,
              "goalUnit": "sticker",
              "timerMinutes": 15,
              "createdAt": "2026-05-05T12:00:00Z",
              "archived": false
            },
            {
              "id": "full-daily",
              "title": "Brew paruchan tea",
              "flavourText": "A soft daily ritual.",
              "xp": 50,
              "category": "Paruchan",
              "icon": "star",
              "cadence": "daily",
              "goalTarget": 2,
              "goalUnit": "cup",
              "timerMinutes": 5
            },
            {
              "id": "full-repeatable",
              "title": "Say thank you paruchan",
              "flavourText": "Repeat the gratitude loop.",
              "xp": 25,
              "category": "Paruchan",
              "icon": "heart",
              "repeatable": true,
              "goalTarget": 1,
              "goalUnit": "completion"
            },
            {
              "id": "full-counter",
              "title": "Read a quest page",
              "flavourText": "Every page counts.",
              "xpPerUnit": 40,
              "category": "Reading",
              "icon": "book",
              "counter": true,
              "unit": "page",
              "timerMinutes": 30
            }
          ]
        }
    """.trimIndent()
}
