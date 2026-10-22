package dev.glacia.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Generates a random TDLib database key once and wraps it with the Android Keystore.
 * Backup is disabled in AndroidManifest to avoid restoring ciphertext without the key.
 * Do not silently regenerate this key when decryption fails: that would orphan the DB.
 */
internal class DatabaseKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences("glacia_tdlib_key", Context.MODE_PRIVATE)
    private val alias = "glacia_tdlib_aes_v1"

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val saved = ks.getKey(alias, null) as? SecretKey
        if (saved != null) return saved
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    fun readOrCreate(): ByteArray = synchronized(this) {
        val key = wrappingKey()
        val saved = prefs.getString("wrapped", null)
        val nonce = prefs.getString("nonce", null)
        if (saved != null || nonce != null) {
            check(saved != null && nonce != null) { "TDLib key storage is incomplete. Do not reset automatically." }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, key,
                GCMParameterSpec(128, Base64.decode(nonce, Base64.NO_WRAP))
            )
            return@synchronized cipher.doFinal(Base64.decode(saved, Base64.NO_WRAP))
        }
        val value = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val wrapped = cipher.doFinal(value)
        check(
            prefs.edit()
                .putString("wrapped", Base64.encodeToString(wrapped, Base64.NO_WRAP))
                .putString("nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        ) { "Unable to persist TDLib database key" }
        value
    }
}
