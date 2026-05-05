package com.paruchan.questlog

import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogEngine
import com.paruchan.questlog.core.QuestLogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class QuestLogEngineTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `level calculation reaches visa bard at 3550 xp`() {
        val engine = QuestLogEngine(clock = clock)
        val state = QuestLogState(
            completions = listOf(
                completion("a", 2000),
                completion("b", 1550),
            ),
        )

        val progress = engine.levelProgress(state)

        assertEquals(3550, progress.totalXp)
        assertEquals(7, progress.current.level)
        assertEquals("Visa Bard of Babsy", progress.current.title)
    }

    @Test
    fun `completion log totals xp and locks non repeatable quests`() {
        val engine = QuestLogEngine(clock = clock, idFactory = { "completion-1" })
        val quest = Quest(id = "quest-1", title = "Do the thing", xp = 75, repeatable = false)
        val state = QuestLogState(quests = listOf(quest))

        val first = engine.completeQuest(state, quest.id)
        val second = engine.completeQuest(first.state, quest.id)

        assertNotNull(first.completion)
        assertEquals(75, engine.totalXp(first.state))
        assertFalse(engine.canComplete(first.state, quest))
        assertNull(second.completion)
        assertEquals(1, second.state.completions.size)
    }

    @Test
    fun `repeatable quests can be completed more than once`() {
        var nextId = 0
        val engine = QuestLogEngine(clock = clock, idFactory = { "completion-${++nextId}" })
        val quest = Quest(id = "repeatable", title = "Drink water", xp = 10, repeatable = true)
        val state = QuestLogState(quests = listOf(quest))

        val first = engine.completeQuest(state, quest.id)
        val second = engine.completeQuest(first.state, quest.id)

        assertTrue(engine.canComplete(second.state, quest))
        assertEquals(2, second.state.completions.size)
        assertEquals(20, engine.totalXp(second.state))
    }

    @Test
    fun `multi step goals award xp only when target is reached`() {
        var nextId = 0
        val engine = QuestLogEngine(clock = clock, idFactory = { "goal-${++nextId}" })
        val quest = Quest(
            id = "goal",
            title = "Read three chapters",
            xp = 90,
            goalTarget = 3,
            goalUnit = "chapter",
        )
        val state = QuestLogState(quests = listOf(quest))

        val first = engine.completeQuest(state, quest.id)
        val second = engine.completeQuest(first.state, quest.id)
        val third = engine.completeQuest(second.state, quest.id)

        assertEquals(0, engine.totalXp(second.state))
        assertEquals(90, engine.totalXp(third.state))
        assertFalse(engine.canComplete(third.state, quest))
        assertEquals(3, engine.progressFor(third.state, quest).progressInCycle)
    }

    @Test
    fun `daily goals lock for today and reset on the next day`() {
        val todayEngine = QuestLogEngine(clock = clock, idFactory = { "daily" })
        val tomorrowClock = Clock.fixed(Instant.parse("2026-05-06T09:00:00Z"), ZoneOffset.UTC)
        val tomorrowEngine = QuestLogEngine(clock = tomorrowClock)
        val quest = Quest(
            id = "daily",
            title = "Morning reset",
            xp = 25,
            cadence = "daily",
        )

        val completedToday = todayEngine.completeQuest(QuestLogState(quests = listOf(quest)), quest.id)

        assertFalse(todayEngine.canComplete(completedToday.state, quest))
        assertTrue(tomorrowEngine.canComplete(completedToday.state, quest))
    }

    @Test
    fun `repeatable goals cycle after each target`() {
        var nextId = 0
        val engine = QuestLogEngine(clock = clock, idFactory = { "cycle-${++nextId}" })
        val quest = Quest(
            id = "stretch",
            title = "Stretch twice",
            xp = 40,
            repeatable = true,
            goalTarget = 2,
            goalUnit = "stretch",
        )
        val state = QuestLogState(quests = listOf(quest))

        val first = engine.completeQuest(state, quest.id)
        val second = engine.completeQuest(first.state, quest.id)
        val third = engine.completeQuest(second.state, quest.id)

        assertEquals(0, first.completion?.xpAwarded)
        assertEquals(40, second.completion?.xpAwarded)
        assertEquals(0, third.completion?.xpAwarded)
        assertEquals(1, engine.progressFor(third.state, quest).progressInCycle)
        assertTrue(engine.canComplete(third.state, quest))
    }

    private fun completion(id: String, xp: Int) =
        com.paruchan.questlog.core.Completion(
            id = id,
            questId = "quest-$id",
            completedAt = "2026-05-05T12:00:00Z",
            xpAwarded = xp,
        )
}
