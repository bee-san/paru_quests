package com.paruchan.questlog

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BundledSharedPackAssetTest {
    @Test
    fun `apk bundles exactly one current shared pack asset`() {
        val assets = assetsDir()
        val bundledFiles = assets.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(assets).invariantSeparatorsPath }
            .sorted()
            .filter { it.startsWith("shared-packs/") || it.startsWith("quest-packs/") }
            .toList()

        assertEquals(listOf("shared-packs/current.encrypted.json"), bundledFiles)
        assertFalse(File(assets, "quest-packs/thank-you-paruchan.json").exists())
    }

    @Test
    fun `current shared pack metadata is the bedroom dust bandits generation`() {
        val currentPack = File(assetsDir(), "shared-packs/current.encrypted.json")
        assertTrue(currentPack.exists())

        val root = JsonParser.parseString(currentPack.readText()).asJsonObject

        assertEquals("paruchan.encrypted-quest-pack", root["kind"].asString)
        assertEquals("bedroom-dust-bandits", root["packId"].asString)
        assertEquals("6", root["packVersion"].asString)
    }

    private fun assetsDir(): File =
        listOf(
            File("app/src/main/assets"),
            File("src/main/assets"),
        ).first { it.isDirectory }
}
