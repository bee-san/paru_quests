package com.paruchan.questlog

import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestPackImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class QuestPackImporterTest {
    private val importer = QuestPackImporter(
        clock = Clock.fixed(Instant.parse("2026-05-05T12:00:00Z"), ZoneOffset.UTC),
    )

    @Test
    fun `imports quest packs from a quests array`() {
        val result = importer.mergeQuestPack(
            QuestLogState(),
            """
            {
              "quests": [
                {
                  "title": "Post the letter",
                  "flavourText": "Paperwork goblet secured",
                  "xp": 50,
                  "category": "Admin",
                  "icon": "mail",
                  "repeatable": false
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1, result.imported)
        assertEquals(0, result.updated)
        assertEquals(1, result.state.quests.size)
        assertEquals("Post the letter", result.state.quests.single().title)
        assertEquals("Admin", result.state.quests.single().category)
    }

    @Test
    fun `reimport without ids derives the same stable id`() {
        val pack = """
            [
              {
                "title": "Water plants",
                "flavourText": "Leaf patrol",
                "xp": 20,
                "category": "Home",
                "repeatable": true
              }
            ]
        """.trimIndent()

        val first = importer.mergeQuestPack(QuestLogState(), pack)
        val second = importer.mergeQuestPack(first.state, pack)

        assertEquals(1, first.imported)
        assertEquals(0, second.imported)
        assertEquals(1, second.updated)
        assertEquals(1, second.state.quests.size)
        assertTrue(second.state.quests.single().id.startsWith("quest_"))
    }

    @Test
    fun `explicit ids update existing quests`() {
        val first = importer.mergeQuestPack(
            QuestLogState(),
            """[{"id":"visa","title":"Prep docs","xp":100,"category":"Admin"}]""",
        )
        val second = importer.mergeQuestPack(
            first.state,
            """[{"id":"visa","title":"Prep docs properly","xp":150,"category":"Admin"}]""",
        )

        assertEquals(0, second.imported)
        assertEquals(1, second.updated)
        assertEquals("Prep docs properly", second.state.quests.single().title)
        assertEquals(150, second.state.quests.single().xp)
    }

    @Test
    fun `imports daily goals and timers`() {
        val result = importer.mergeQuestPack(
            QuestLogState(),
            """
            [
              {
                "title": "Twenty minute tidy",
                "xp": 80,
                "category": "Home",
                "daily": true,
                "goal": {"target": 4, "unit": "room"},
                "timer": {"minutes": 20}
              }
            ]
            """.trimIndent(),
        )

        val quest = result.state.quests.single()
        assertEquals("daily", quest.cadence)
        assertEquals(false, quest.repeatable)
        assertEquals(4, quest.goalTarget)
        assertEquals("room", quest.goalUnit)
        assertEquals(20, quest.timerMinutes)
    }

    @Test
    fun `imports counter quests with xp per unit`() {
        val result = importer.mergeQuestPack(
            QuestLogState(),
            """
            [
              {
                "title": "Run a mile",
                "flavourText": "Every mile counts.",
                "xpPerUnit": 100,
                "category": "Fitness",
                "counter": true,
                "unit": "mile"
              },
              {
                "title": "Spot a dog",
                "xp": 10,
                "category": "Outside",
                "cadence": "counter",
                "goalUnit": "dog"
              }
            ]
            """.trimIndent(),
        )

        val run = result.state.quests.first { it.title == "Run a mile" }
        val dog = result.state.quests.first { it.title == "Spot a dog" }

        assertEquals(2, result.imported)
        assertEquals("counter", run.cadence)
        assertEquals(100, run.xp)
        assertEquals("mile", run.goalUnit)
        assertEquals(false, run.repeatable)
        assertEquals("counter", dog.cadence)
        assertEquals(10, dog.xp)
        assertEquals("dog", dog.goalUnit)
    }

    @Test
    fun `invalid quests are skipped with validation errors`() {
        val result = importer.mergeQuestPack(
            QuestLogState(),
            """[{"title":"","xp":10},{"title":"Bad XP","xp":-1}]""",
        )

        assertEquals(0, result.state.quests.size)
        assertEquals(2, result.skipped)
        assertEquals(2, result.errors.size)
    }
}
