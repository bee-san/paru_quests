package com.paruchan.questlog

import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.DailyQuestMessages
import com.paruchan.questlog.core.Level
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogJsonCodec
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
import java.time.LocalDate
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
    fun `level calculation shows progress inside smoother late levels`() {
        val engine = QuestLogEngine(clock = clock)
        val state = QuestLogState(
            completions = listOf(
                completion("a", 5000),
            ),
        )

        val progress = engine.levelProgress(state)

        assertEquals(5000, progress.totalXp)
        assertEquals(8, progress.current.level)
        assertEquals("Starry Paru", progress.current.title)
        assertEquals(300, progress.xpIntoLevel)
        assertEquals(1200, progress.xpToNext)
        assertEquals(0.2f, progress.fraction, 0.0001f)
    }

    @Test
    fun `old built in levels migrate to cute paru ladder`() {
        val normalized = QuestLogJsonCodec().normalize(
            QuestLogState(levels = legacyBuiltInLevels()),
        )

        assertEquals(12, normalized.levels.size)
        assertEquals("Babsy Paru", normalized.levels[0].title)
        assertEquals(120, normalized.levels[1].xpRequired)
        assertEquals("Visa Bard of Babsy", normalized.levels[6].title)
        assertEquals(3550, normalized.levels[6].xpRequired)
        assertEquals("Legendary Babsy Paru", normalized.levels.last().title)
    }

    @Test
    fun `custom levels survive normalization`() {
        val customLevels = listOf(
            Level(level = 1, xpRequired = 0, title = "Custom Paru", unlocks = listOf("Custom start")),
            Level(level = 2, xpRequired = 42, title = "Custom Babsy", unlocks = listOf("Custom next")),
        )

        val normalized = QuestLogJsonCodec().normalize(
            QuestLogState(levels = customLevels),
        )

        assertEquals(customLevels, normalized.levels)
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

    @Test
    fun `counter quests increment by one and complete at target`() {
        var nextId = 0
        val engine = QuestLogEngine(clock = clock, idFactory = { "counter-${++nextId}" })
        val quest = Quest(
            id = "run",
            title = "Spot paruchans",
            xp = 100,
            goalType = "counter",
            goalTarget = 3,
            goalUnit = "paruchan",
        )
        val state = QuestLogState(quests = listOf(quest))

        val first = engine.completeQuest(state, quest.id, progressAmount = 99)
        val second = engine.completeQuest(first.state, quest.id)
        val third = engine.completeQuest(second.state, quest.id)
        val fourth = engine.completeQuest(third.state, quest.id)
        val progress = engine.progressFor(third.state, quest)

        assertEquals(1, first.completion?.progressAmount)
        assertEquals(0, first.completion?.xpAwarded)
        assertEquals(0, second.completion?.xpAwarded)
        assertEquals(100, third.completion?.xpAwarded)
        assertEquals(100, engine.totalXp(third.state))
        assertEquals(3, progress.progressInCycle)
        assertFalse(engine.canComplete(third.state, quest))
        assertNull(fourth.completion)
    }

    @Test
    fun `timer quests record minutes and complete at target`() {
        var nextId = 0
        val engine = QuestLogEngine(clock = clock, idFactory = { "timer-${++nextId}" })
        val quest = Quest(
            id = "study",
            title = "Study for thirty minutes",
            xp = 75,
            goalType = "timer",
            goalTarget = 30,
            goalUnit = "minute",
        )

        val first = engine.completeQuest(QuestLogState(quests = listOf(quest)), quest.id, progressAmount = 12)
        val second = engine.completeQuest(first.state, quest.id, progressAmount = 18)
        val progress = engine.progressFor(second.state, quest)

        assertEquals(12, first.completion?.progressAmount)
        assertEquals(0, first.completion?.xpAwarded)
        assertEquals(18, second.completion?.progressAmount)
        assertEquals(75, second.completion?.xpAwarded)
        assertEquals(30, progress.progressInCycle)
        assertFalse(engine.canComplete(second.state, quest))
    }

    @Test
    fun `timer quests coerce blank or zero minutes to one minute`() {
        val engine = QuestLogEngine(clock = clock, idFactory = { "counter" })
        val quest = Quest(
            id = "timer",
            title = "Practice",
            xp = 10,
            goalType = "timer",
            goalTarget = 2,
            goalUnit = "minute",
        )

        val result = engine.completeQuest(QuestLogState(quests = listOf(quest)), quest.id, progressAmount = 0)

        assertEquals(1, result.completion?.progressAmount)
        assertEquals(0, result.completion?.xpAwarded)
        assertEquals("Practice logged 1 minute. 1/2 minutes", result.message)
    }

    @Test
    fun `reminder quests include only available unfinished quests`() {
        val engine = QuestLogEngine(clock = clock)
        val openDaily = Quest(id = "daily-open", title = "Open daily", xp = 10, cadence = "daily")
        val doneDaily = Quest(id = "daily-done", title = "Done daily", xp = 10, cadence = "daily")
        val openGoal = Quest(id = "goal-open", title = "Open goal", xp = 30, goalTarget = 3)
        val doneOnce = Quest(id = "once-done", title = "Done once", xp = 20)
        val archived = Quest(id = "archived", title = "Archived", xp = 20, archived = true)
        val counter = Quest(id = "counter", title = "Counter", xp = 5, goalType = "counter", goalTarget = 2, goalUnit = "paruchan")
        val state = QuestLogState(
            quests = listOf(doneOnce, openGoal, doneDaily, archived, counter, openDaily),
            completions = listOf(
                Completion(id = "done-once", questId = doneOnce.id, completedAt = "2026-05-05T09:00:00Z", xpAwarded = 20),
                Completion(id = "done-daily", questId = doneDaily.id, completedAt = "2026-05-05T09:00:00Z", xpAwarded = 10),
                Completion(id = "goal-progress", questId = openGoal.id, completedAt = "2026-05-05T09:00:00Z", xpAwarded = 0),
            ),
        )

        assertEquals(
            listOf("daily-open", "goal-open", "counter"),
            engine.reminderQuests(state).map { it.id },
        )
    }

    @Test
    fun `daily reminder quests reset after the completion day`() {
        val todayEngine = QuestLogEngine(clock = clock)
        val tomorrowClock = Clock.fixed(Instant.parse("2026-05-06T09:00:00Z"), ZoneOffset.UTC)
        val tomorrowEngine = QuestLogEngine(clock = tomorrowClock)
        val daily = Quest(id = "daily", title = "Daily", xp = 10, cadence = "daily")
        val state = QuestLogState(
            quests = listOf(daily),
            completions = listOf(
                Completion(id = "done-daily", questId = daily.id, completedAt = "2026-05-05T09:00:00Z", xpAwarded = 10),
            ),
        )

        assertTrue(todayEngine.reminderQuests(state).isEmpty())
        assertEquals(listOf("daily"), tomorrowEngine.reminderQuests(state).map { it.id })
    }

    @Test
    fun `daily quest messages include cute paruchan notes and rotate by date`() {
        assertTrue("Paru is proud of you!!" in DailyQuestMessages.messages)
        assertTrue("I love my paruchan!!!" in DailyQuestMessages.messages)

        val today = DailyQuestMessages.forDate(LocalDate.of(2026, 5, 5))
        val tomorrow = DailyQuestMessages.forDate(LocalDate.of(2026, 5, 6))

        assertEquals(today, DailyQuestMessages.forDate(LocalDate.of(2026, 5, 5)))
        assertTrue(today in DailyQuestMessages.messages)
        assertTrue(tomorrow in DailyQuestMessages.messages)
        assertTrue(DailyQuestMessages.messages.size > 2)
    }

    private fun completion(id: String, xp: Int) =
        Completion(
            id = id,
            questId = "quest-$id",
            completedAt = "2026-05-05T12:00:00Z",
            xpAwarded = xp,
        )

    private fun legacyBuiltInLevels(): List<Level> = listOf(
        Level(
            level = 1,
            xpRequired = 0,
            title = "Pocket Questling",
            unlocks = listOf("Quest log awakened"),
        ),
        Level(
            level = 2,
            xpRequired = 150,
            title = "Snack Squire",
            unlocks = listOf("Tiny treats count double emotionally"),
        ),
        Level(
            level = 3,
            xpRequired = 450,
            title = "Errand Enchanter",
            unlocks = listOf("One ceremonial side quest slot"),
        ),
        Level(
            level = 4,
            xpRequired = 900,
            title = "Cozy Crusader",
            unlocks = listOf("Blanket fort planning rights"),
        ),
        Level(
            level = 5,
            xpRequired = 1500,
            title = "Charm Collector",
            unlocks = listOf("Rare sticker-tier quest rewards"),
        ),
        Level(
            level = 6,
            xpRequired = 2400,
            title = "Babsy Minstrel",
            unlocks = listOf("Daily ballad of completed chores"),
        ),
        Level(
            level = 7,
            xpRequired = 3550,
            title = "Visa Bard of Babsy",
            unlocks = listOf("Paperwork victory anthem"),
        ),
        Level(
            level = 8,
            xpRequired = 5000,
            title = "Calendar Paladin",
            unlocks = listOf("Future quest chain planning"),
        ),
        Level(
            level = 9,
            xpRequired = 7000,
            title = "Treat Hoarder",
            unlocks = listOf("Legendary reward cache"),
        ),
        Level(
            level = 10,
            xpRequired = 9500,
            title = "Paruchan Legend",
            unlocks = listOf("Hall of tiny triumphs"),
        ),
    )
}
