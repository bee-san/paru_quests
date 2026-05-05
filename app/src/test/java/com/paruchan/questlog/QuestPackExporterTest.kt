package com.paruchan.questlog

import com.google.gson.JsonParser
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestPackExporter
import com.paruchan.questlog.core.QuestPackImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class QuestPackExporterTest {
    private val clock = Clock.fixed(Instant.parse("2026-05-05T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `exports shareable quest pack object`() {
        val exporter = QuestPackExporter(clock = clock)

        val json = exporter.encodePack(
            name = "Weekend Paruchan Pack",
            quests = listOf(
                Quest(
                    title = "Tidy desk",
                    flavourText = "Give the tiny blob some room.",
                    xp = 80,
                    category = "Home",
                    cadence = "daily",
                    goalTarget = 3,
                    goalUnit = "surface",
                    timerMinutes = 20,
                )
            ),
        )

        val root = JsonParser.parseString(json).asJsonObject
        val quest = root["quests"].asJsonArray.single().asJsonObject

        assertEquals("paruchan.quest-pack", root["kind"].asString)
        assertEquals("Weekend Paruchan Pack", root["name"].asString)
        assertEquals("2026-05-05T12:00:00Z", root["exportedAt"].asString)
        assertEquals("Tidy desk", quest["title"].asString)
        assertEquals("daily", quest["cadence"].asString)
        assertEquals(3, quest["goalTarget"].asInt)
        assertEquals(20, quest["timerMinutes"].asInt)
    }

    @Test
    fun `exported quest packs can be imported`() {
        val exporter = QuestPackExporter(clock = clock)
        val importer = QuestPackImporter(clock = clock)
        val json = exporter.encodePack(
            name = "Tiny Pack",
            quests = listOf(Quest(title = "Spot a paruchan", xp = 25, icon = "paruchan")),
        )

        val imported = importer.mergeQuestPack(com.paruchan.questlog.core.QuestLogState(), json)

        assertEquals(1, imported.imported)
        assertEquals("Spot a paruchan", imported.state.quests.single().title)
        assertTrue(imported.errors.isEmpty())
    }
}
