package com.paruchan.questlog.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SharedPackSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("shared_pack_secret", Context.MODE_PRIVATE)

    fun hasPassword(): Boolean =
        prefs.contains(KEY_PASSWORD_CIPHERTEXT) && prefs.contains(KEY_PASSWORD_IV)

    fun savePassword(password: String) {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_PASSWORD_IV, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(KEY_PASSWORD_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
            .apply()
    }

    fun loadPassword(): String? {
        val iv = prefs.getString(KEY_PASSWORD_IV, null) ?: return null
        val ciphertext = prefs.getString(KEY_PASSWORD_CIPHERTEXT, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(AES_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.getDecoder().decode(iv)),
            )
            cipher.doFinal(Base64.getDecoder().decode(ciphertext)).toString(Charsets.UTF_8)
        }.getOrElse {
            clearPassword()
            null
        }
    }

    fun clearPassword() {
        prefs.edit()
            .remove(KEY_PASSWORD_IV)
            .remove(KEY_PASSWORD_CIPHERTEXT)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "paruchan_shared_pack_password"
        const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val KEY_PASSWORD_IV = "password_iv"
        const val KEY_PASSWORD_CIPHERTEXT = "password_ciphertext"
    }
}
