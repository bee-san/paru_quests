package com.paruchan.questlog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class JournalEntryRepositoryTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-05-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `saving a partial diary entry awards zero xp`() {
        val repository = testQuestLogRepository(temp.root, clock = clock)

        val entry = repository.saveJournalEntry(
            happyText = "Paruchan had a soft little win.",
            gratefulText = "",
            favoriteMemoryText = "",
        )

        assertEquals(0, entry.xpAwarded)
        assertEquals(0, repository.load().journalEntries.single().xpAwarded)
    }

    @Test
    fun `completing all diary prompts awards ten xp once for that day`() {
        val repository = testQuestLogRepository(temp.root, clock = clock)

        val completed = repository.saveJournalEntry(
            happyText = "Paruchan had a soft little win.",
            gratefulText = "Paruchan is grateful for quiet.",
            favoriteMemoryText = "A cosy walk.",
        )
        val edited = repository.saveJournalEntry(
            happyText = "Paruchan had another soft win.",
            gratefulText = "Paruchan is still grateful.",
            favoriteMemoryText = "A cosy walk after dinner.",
        )

        assertEquals(10, completed.xpAwarded)
        assertEquals(10, edited.xpAwarded)
        assertEquals(10, repository.load().journalEntries.single().xpAwarded)
    }

    @Test
    fun `a new local day can earn a new diary reward`() {
        val repository = testQuestLogRepository(temp.root, clock = clock)

        repository.saveJournalEntry("Happy one", "Grateful one", "Memory one", LocalDate.of(2026, 5, 5))
        repository.saveJournalEntry("Happy two", "Grateful two", "Memory two", LocalDate.of(2026, 5, 6))

        assertEquals(20, repository.load().journalEntries.sumOf { it.xpAwarded })
    }

    @Test
    fun `daily reflection is deterministic and ignores todays entry`() {
        val repository = testQuestLogRepository(temp.root, clock = clock)

        repository.saveJournalEntry("Today happy", "Today grateful", "Today memory", LocalDate.of(2026, 5, 5))
        assertNull(repository.dailyReflectionForDate(LocalDate.of(2026, 5, 5)))

        repository.saveJournalEntry("Older happy", "Older grateful", "Older memory", LocalDate.of(2026, 5, 4))
        val first = repository.dailyReflectionForDate(LocalDate.of(2026, 5, 5))
        val second = repository.dailyReflectionForDate(LocalDate.of(2026, 5, 5))

        assertEquals(first, second)
        assertTrue(first in setOf("Older memory", "Older happy"))
    }
}
