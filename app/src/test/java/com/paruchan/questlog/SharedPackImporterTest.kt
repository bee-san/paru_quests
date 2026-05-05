package com.paruchan.questlog

import com.paruchan.questlog.core.Completion
import com.paruchan.questlog.core.EncryptedQuestPackCodec
import com.paruchan.questlog.core.EncryptedSharedPackAsset
import com.paruchan.questlog.core.Quest
import com.paruchan.questlog.core.QuestLogState
import com.paruchan.questlog.core.SharedPackImporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SharedPackImporterTest {
    @Test
    fun `decrypts encrypted quest pack with correct password`() {
        val plaintext = """{"quests":[{"id":"shared","title":"Shared quest","xp":10}]}"""
        val encrypted = encryptedPack(plaintext = plaintext)

        val decrypted = EncryptedQuestPackCodec.decrypt(encrypted, PASSWORD)

        assertEquals("shared-pack", decrypted.packId)
        assertEquals("1", decrypted.packVersion)
        assertEquals(plaintext, decrypted.questPackJson)
    }

    @Test
    fun `wrong password does not decrypt encrypted quest pack`() {
        val encrypted = encryptedPack(plaintext = """{"quests":[{"title":"Secret","xp":10}]}""")

        try {
            EncryptedQuestPackCodec.decrypt(encrypted, "wrong-password")
            fail("Expected wrong password to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("could not decrypt"))
        }
    }

    @Test
    fun `shared pack import is skipped after marker is stored`() {
        val asset = EncryptedSharedPackAsset(
            name = "shared.encrypted.json",
            json = encryptedPack("""{"quests":[{"id":"shared","title":"Shared quest","xp":10}]}"""),
        )
        val importer = SharedPackImporter()

        val first = importer.mergeEncryptedPacks(QuestLogState(), listOf(asset), PASSWORD, emptySet())
        val second = importer.mergeEncryptedPacks(first.state, listOf(asset), PASSWORD, first.newMarkers)

        assertEquals(1, first.imported)
        assertEquals(1, first.newMarkers.size)
        assertEquals(0, second.imported)
        assertEquals(0, second.updated)
        assertEquals(1, second.unchangedPacks)
        assertEquals("Shared packs already up to date", second.summary)
    }

    @Test
    fun `changed shared pack updates quest and preserves completions`() {
        val firstAsset = EncryptedSharedPackAsset(
            name = "shared.encrypted.json",
            json = encryptedPack("""{"quests":[{"id":"shared","title":"Shared quest","xp":10}]}"""),
        )
        val secondAsset = EncryptedSharedPackAsset(
            name = "shared.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"shared","title":"Shared quest updated","xp":25}]}""",
                packVersion = "2",
            ),
        )
        val importer = SharedPackImporter()
        val first = importer.mergeEncryptedPacks(QuestLogState(), listOf(firstAsset), PASSWORD, emptySet())
        val stateWithCompletion = first.state.copy(
            completions = listOf(
                Completion(
                    id = "completion-1",
                    questId = "shared",
                    completedAt = "2026-05-05T12:00:00Z",
                    xpAwarded = 10,
                )
            )
        )

        val second = importer.mergeEncryptedPacks(
            state = stateWithCompletion,
            assets = listOf(secondAsset),
            password = PASSWORD,
            importedMarkers = first.newMarkers,
        )

        assertEquals(0, second.imported)
        assertEquals(1, second.updated)
        assertEquals("Shared quest updated", second.state.quests.single().title)
        assertEquals(25, second.state.quests.single().xp)
        assertEquals(stateWithCompletion.completions, second.state.completions)
    }

    @Test
    fun `shared pack import closes quests outside the applied pack set`() {
        val oldState = QuestLogState(
            quests = listOf(Quest(id = "old", title = "Old quest", xp = 10)),
        )
        val asset = EncryptedSharedPackAsset(
            name = "shared.encrypted.json",
            json = encryptedPack("""{"quests":[{"id":"new","title":"New quest","xp":25}]}"""),
        )

        val result = SharedPackImporter().mergeEncryptedPacks(
            state = oldState,
            assets = listOf(asset),
            password = PASSWORD,
            importedMarkers = emptySet(),
        )

        assertEquals(1, result.imported)
        assertEquals(1, result.closed)
        assertTrue(result.state.quests.first { it.id == "old" }.archived)
        assertFalse(result.state.quests.first { it.id == "new" }.archived)
    }

    @Test
    fun `shared pack import keeps every quest from bundled packs open`() {
        val firstAsset = EncryptedSharedPackAsset(
            name = "01-first.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"first","title":"First quest","xp":10}]}""",
                packId = "first-pack",
            ),
        )
        val secondAsset = EncryptedSharedPackAsset(
            name = "02-second.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"second","title":"Second quest","xp":20}]}""",
                packId = "second-pack",
            ),
        )

        val result = SharedPackImporter().mergeEncryptedPacks(
            state = QuestLogState(),
            assets = listOf(firstAsset, secondAsset),
            password = PASSWORD,
            importedMarkers = emptySet(),
        )

        assertEquals(2, result.imported)
        assertEquals(0, result.closed)
        assertFalse(result.state.quests.first { it.id == "first" }.archived)
        assertFalse(result.state.quests.first { it.id == "second" }.archived)
    }

    @Test
    fun `shared pack import keeps unchanged bundled packs open when another pack updates`() {
        val firstAsset = EncryptedSharedPackAsset(
            name = "01-first.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"first","title":"First quest","xp":10}]}""",
                packId = "first-pack",
            ),
        )
        val secondAsset = EncryptedSharedPackAsset(
            name = "02-second.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"second","title":"Second quest","xp":20}]}""",
                packId = "second-pack",
            ),
        )
        val updatedSecondAsset = EncryptedSharedPackAsset(
            name = "02-second.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"second","title":"Second quest updated","xp":25}]}""",
                packId = "second-pack",
                packVersion = "2",
            ),
        )
        val importer = SharedPackImporter()
        val firstImport = importer.mergeEncryptedPacks(
            state = QuestLogState(),
            assets = listOf(firstAsset, secondAsset),
            password = PASSWORD,
            importedMarkers = emptySet(),
        )

        val secondImport = importer.mergeEncryptedPacks(
            state = firstImport.state,
            assets = listOf(firstAsset, updatedSecondAsset),
            password = PASSWORD,
            importedMarkers = firstImport.newMarkers,
        )

        assertEquals(1, secondImport.unchangedPacks)
        assertEquals(1, secondImport.updated)
        assertEquals(0, secondImport.closed)
        assertFalse(secondImport.state.quests.first { it.id == "first" }.archived)
        assertFalse(secondImport.state.quests.first { it.id == "second" }.archived)
        assertEquals("Second quest updated", secondImport.state.quests.first { it.id == "second" }.title)
    }

    @Test
    fun `bad shared pack does not block later valid packs`() {
        val badAsset = EncryptedSharedPackAsset(
            name = "01-bad.encrypted.json",
            json = encryptedPack(
                plaintext = """{"quests":[{"id":"bad","title":"Bad quest","xp":10}]}""",
                password = "other-password",
            ),
        )
        val goodAsset = EncryptedSharedPackAsset(
            name = "02-good.encrypted.json",
            json = encryptedPack("""{"quests":[{"id":"good","title":"Good quest","xp":25}]}"""),
        )

        val result = SharedPackImporter().mergeEncryptedPacks(
            state = QuestLogState(),
            assets = listOf(badAsset, goodAsset),
            password = PASSWORD,
            importedMarkers = emptySet(),
        )

        assertEquals(1, result.imported)
        assertEquals(1, result.skipped)
        assertEquals(1, result.errors.size)
        assertEquals("Good quest", result.state.quests.single().title)
    }

    private fun encryptedPack(
        plaintext: String,
        password: String = PASSWORD,
        packId: String = "shared-pack",
        packVersion: String = "1",
    ): String {
        val salt = ByteArray(16) { index -> index.toByte() }
        val iv = ByteArray(12) { index -> (index + 16).toByte() }
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256))
            .encoded
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return """
            {
              "kind": "paruchan.encrypted-quest-pack",
              "schemaVersion": 1,
              "packId": "$packId",
              "packVersion": "$packVersion",
              "kdf": "PBKDF2WithHmacSHA256",
              "iterations": $ITERATIONS,
              "cipher": "AES-256-GCM",
              "salt": "${Base64.getEncoder().encodeToString(salt)}",
              "iv": "${Base64.getEncoder().encodeToString(iv)}",
              "ciphertext": "${Base64.getEncoder().encodeToString(ciphertext)}"
            }
        """.trimIndent()
    }

    private companion object {
        const val PASSWORD = "test-password"
        const val ITERATIONS = 100_000
    }
}
