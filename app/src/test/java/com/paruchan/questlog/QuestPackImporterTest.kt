package com.paruchan.questlog

import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.QuestPackImporter
import com.paruchan.questlog.core.Quest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun `implicit ids survive mutable text and xp updates`() {
        val first = importer.mergeQuestPack(
            QuestLogState(),
            """[{"title":"Eat Thai food","flavourText":"Tasty","xp":150,"category":"Food"}]""",
        )
        val firstId = first.state.quests.single().id

        val second = importer.mergeQuestPack(
            first.state,
            """[{"title":"Eat Thai food","flavourText":"Very tasty","xp":175,"category":"Food"}]""",
        )

        assertEquals(0, second.imported)
        assertEquals(1, second.updated)
        assertEquals(1, second.state.quests.size)
        assertEquals(firstId, second.state.quests.single().id)
        assertEquals(175, second.state.quests.single().xp)
        assertEquals("Very tasty", second.state.quests.single().flavourText)
    }

    @Test
    fun `implicit ids include cadence and goal shape to avoid collisions`() {
        val result = importer.mergeQuestPack(
            QuestLogState(),
            """
            [
              {"title":"Run","xp":10,"category":"Fitness","cadence":"once"},
              {"title":"Run","xp":10,"category":"Fitness","cadence":"counter","goalUnit":"mile"},
              {"title":"Run","xp":10,"category":"Fitness","cadence":"daily","goalTarget":3,"goalUnit":"lap"}
            ]
            """.trimIndent(),
        )

        assertEquals(3, result.imported)
        assertEquals(3, result.state.quests.map { it.id }.distinct().size)
    }

    @Test
    fun `imported quests are reopened when included in the latest pack`() {
        val existing = Quest(
            id = "local",
            title = "Local quest",
            xp = 10,
            archived = true,
        )

        val result = importer.mergeQuestPack(
            QuestLogState(quests = listOf(existing)),
            """[{"id":"local","title":"Local quest updated","xp":25}]""",
        )

        assertEquals(1, result.updated)
        assertEquals("Local quest updated", result.state.quests.single().title)
        assertFalse(result.state.quests.single().archived)
    }

    @Test
    fun `import closes previous quests without awarding xp`() {
        val previousCompletion = Completion(
            id = "completion-1",
            questId = "already-done",
            completedAt = "2026-05-05T10:00:00Z",
            xpAwarded = 40,
        )
        val state = QuestLogState(
            quests = listOf(
                Quest(id = "previous", title = "Previous quest", xp = 100),
                Quest(id = "already-done", title = "Already done", xp = 40),
            ),
            completions = listOf(previousCompletion),
        )

        val result = importer.mergeQuestPack(
            state,
            """[{"id":"latest","title":"Latest quest","xp":25}]""",
        )

        val previous = result.state.quests.first { it.id == "previous" }
        val alreadyDone = result.state.quests.first { it.id == "already-done" }
        val latest = result.state.quests.first { it.id == "latest" }

        assertEquals(1, result.imported)
        assertEquals(2, result.closed)
        assertTrue(previous.archived)
        assertTrue(alreadyDone.archived)
        assertFalse(latest.archived)
        assertEquals(listOf(previousCompletion), result.state.completions)
        assertEquals(40, result.state.completions.sumOf { it.xpAwarded })
        assertEquals("Imported 1, updated 0, closed 2, skipped 0", result.summary)
    }

    @Test
    fun `import with validation errors does not close previous quests`() {
        val state = QuestLogState(
            quests = listOf(Quest(id = "previous", title = "Previous quest", xp = 100)),
        )

        val result = importer.mergeQuestPack(
            state,
            """[{"id":"latest","title":"Latest quest","xp":25},{"id":"bad","title":"","xp":10}]""",
        )

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(0, result.closed)
        assertFalse(result.state.quests.first { it.id == "previous" }.archived)
        assertFalse(result.state.quests.first { it.id == "latest" }.archived)
    }

    @Test
    fun `imports daily goals and timer helpers`() {
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
        assertEquals("completion", quest.goalType)
        assertEquals(false, quest.repeatable)
        assertEquals(4, quest.goalTarget)
        assertEquals("room", quest.goalUnit)
        assertEquals(20, quest.timerMinutes)
    }

    @Test
    fun `imports timer goals from required minutes`() {
        val result = importer.mergeQuestPack(
            QuestLogState(),
            """
            [
              {
                "title": "Practice for twenty minutes",
                "xp": 80,
                "category": "Music",
                "goalType": "timer",
                "timerMinutes": 20
              }
            ]
            """.trimIndent(),
        )

        val quest = result.state.quests.single()
        assertEquals("once", quest.cadence)
        assertEquals("timer", quest.goalType)
        assertEquals(20, quest.goalTarget)
        assertEquals("minute", quest.goalUnit)
        assertEquals(null, quest.timerMinutes)
    }

    @Test
    fun `imports legacy counter quests as finite counter goals`() {
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
        assertEquals("once", run.cadence)
        assertEquals("counter", run.goalType)
        assertEquals(100, run.xp)
        assertEquals(1, run.goalTarget)
        assertEquals("mile", run.goalUnit)
        assertEquals(false, run.repeatable)
        assertEquals("once", dog.cadence)
        assertEquals("counter", dog.goalType)
        assertEquals(10, dog.xp)
        assertEquals(1, dog.goalTarget)
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
