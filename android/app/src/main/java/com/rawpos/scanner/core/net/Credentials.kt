package com.rawpos.scanner.core.net

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** What the phone needs to reach one desktop. The token is the only secret. */
data class Credentials(
    val baseUrl: String,
    val deviceId: String,
    val pin: String,
    val token: String,
    val desktopName: String? = null,
) {
    fun endpoint(): Endpoint = Endpoint(baseUrl = baseUrl, deviceId = deviceId, pin = pin)
}

/** Encodes credentials for storage. Kept separate from the Keystore so it is testable on the JVM. */
object CredentialCodec {

    fun encode(credentials: Credentials): String = JSONObject()
        .put("base_url", credentials.baseUrl)
        .put("device_id", credentials.deviceId)
        .put("pin", credentials.pin)
        .put("token", credentials.token)
        .put("desktop_name", credentials.desktopName ?: JSONObject.NULL)
        .toString()

    fun decode(text: String): Credentials? = runCatching {
        val json = JSONObject(text)
        Credentials(
            baseUrl = json.getString("base_url"),
            deviceId = json.getString("device_id"),
            pin = json.getString("pin"),
            token = json.getString("token"),
            desktopName = json.optString("desktop_name").takeIf { it.isNotEmpty() },
        )
    }.getOrNull()
}

interface CredentialStore {
    fun load(): Credentials?

    fun save(credentials: Credentials)

    fun clear()

    fun isPaired(): Boolean = load() != null
}

/**
 * Stores the token encrypted with an Android Keystore AES-GCM key (A20: hash at rest on the desktop,
 * Keystore on the phone). The app has `allowBackup="false"`, so the ciphertext never leaves the device.
 *
 * A lost key — factory reset, key invalidation — is handled as "not paired": the cashier re-pairs
 * rather than the app silently trusting something it cannot decrypt.
 */
class KeystoreCredentialStore(context: Context) : CredentialStore {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): Credentials? {
        val stored = preferences.getString(KEY_BLOB, null) ?: return null
        return runCatching { decode(stored) }.getOrNull()
    }

    override fun save(credentials: Credentials) {
        preferences.edit { putString(KEY_BLOB, encode(credentials)) }
    }

    override fun clear() {
        preferences.edit { remove(KEY_BLOB) }
    }

    private fun encode(credentials: Credentials): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(CredentialCodec.encode(credentials).toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    private fun decode(stored: String): Credentials? {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) {
            return null
        }
        val iv = raw.copyOfRange(0, IV_BYTES)
        val ciphertext = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        return CredentialCodec.decode(String(plaintext, Charsets.UTF_8))
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance("AES", ANDROID_KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE,
                )
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS_NAME = "rawpos_scanner_credentials"
        const val KEY_BLOB = "desktop"
        const val KEY_ALIAS = "rawpos_mobile_token"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
