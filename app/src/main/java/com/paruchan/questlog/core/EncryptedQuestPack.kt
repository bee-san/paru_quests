package com.paruchan.questlog.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class DecryptedQuestPack(
    val packId: String,
    val packVersion: String,
    val questPackJson: String,
)

object EncryptedQuestPackCodec {
    private const val KIND = "paruchan.encrypted-quest-pack"
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES-256-GCM"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    fun decrypt(json: String, password: String): DecryptedQuestPack {
        require(password.isNotEmpty()) { "Shared pack password is required" }

        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (error: Exception) {
            throw IllegalArgumentException("Encrypted quest pack is not valid JSON", error)
        }

        require(root.requiredString("kind") == KIND) { "Encrypted quest pack has an unsupported kind" }
        require(root.requiredInt("schemaVersion") == 1) { "Encrypted quest pack has an unsupported schema version" }
        require(root.requiredString("kdf") == KDF) { "Encrypted quest pack has an unsupported key derivation method" }
        require(root.requiredString("cipher") == CIPHER) { "Encrypted quest pack has an unsupported cipher" }

        val packId = root.requiredString("packId")
        val packVersion = root.requiredString("packVersion")
        val iterations = root.requiredInt("iterations").coerceAtLeast(100_000)
        val salt = root.requiredBase64("salt")
        val iv = root.requiredBase64("iv")
        val ciphertext = root.requiredBase64("ciphertext")

        val keyBytes = SecretKeyFactory.getInstance(KDF)
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, AES_KEY_BITS))
            .encoded
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))

        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (error: Exception) {
            throw IllegalArgumentException("Shared pack password could not decrypt this pack", error)
        }

        return DecryptedQuestPack(
            packId = packId,
            packVersion = packVersion,
            questPackJson = plaintext.toString(StandardCharsets.UTF_8),
        )
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = this[name]?.takeUnless { it.isJsonNull }?.asString.orEmpty().trim()
        require(value.isNotEmpty()) { "Encrypted quest pack is missing $name" }
        return value
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = runCatching { this[name]?.takeUnless { it.isJsonNull }?.asInt }.getOrNull()
        require(value != null) { "Encrypted quest pack is missing $name" }
        return value
    }

    private fun JsonObject.requiredBase64(name: String): ByteArray =
        try {
            Base64.getDecoder().decode(requiredString(name))
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Encrypted quest pack has invalid $name", error)
        }
}
