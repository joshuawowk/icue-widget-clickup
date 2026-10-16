package net.jwowk.clickupwidget.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the ClickUp personal API token (one per app, shared by all widgets) encrypted at rest
 * with an Android Keystore AES/GCM key, in app-private SharedPreferences. Never backed up
 * (manifest allowBackup=false). Also remembers a display label for the connected account.
 *
 * Thread-safe; the decrypted token is cached in memory so [token] is cheap after the first call.
 */
class TokenStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val lock = Any()
  private var cached: String? = null
  private var cacheLoaded = false
  private var transientFailure = false

  /** The decrypted token, or null if none is stored (or the Keystore key was invalidated, which clears it). */
  fun token(): String? = synchronized(lock) {
    if (!cacheLoaded) {
      transientFailure = false
      cached = readToken()
      if (!transientFailure) cacheLoaded = true
    }
    cached
  }

  fun hasToken(): Boolean = token() != null

  fun save(token: String): Unit = synchronized(lock) {
    val blob = try {
      encrypt(token)
    } catch (e: Exception) {
      // A broken/invalidated key: start over with a fresh one.
      deleteKey()
      encrypt(token)
    }
    prefs.edit().putString(KEY_TOKEN, blob).apply()
    cached = token
    cacheLoaded = true
  }

  fun clear(): Unit = synchronized(lock) {
    prefs.edit().remove(KEY_TOKEN).remove(KEY_LABEL).apply()
    cached = null
    cacheLoaded = true
  }

  /** e.g. "Josh · Getinge" — shown in the app; null if unknown. */
  fun accountLabel(): String? = prefs.getString(KEY_LABEL, null)

  fun saveAccountLabel(label: String?) {
    prefs.edit().apply { if (label == null) remove(KEY_LABEL) else putString(KEY_LABEL, label) }.apply()
  }

  /** Null if none stored, or (token kept, cache not marked loaded) on a transient error; wipes only if the blob/key is provably unusable. */
  private fun readToken(): String? {
    val blob = prefs.getString(KEY_TOKEN, null) ?: return null
    return try {
      decrypt(blob)
    } catch (e: Exception) {
      if (isUnusable(e)) {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_LABEL).apply()
        deleteKey()
      } else {
        transientFailure = true
      }
      null
    }
  }

  private fun isUnusable(e: Exception): Boolean =
    e is AEADBadTagException || e is IllegalArgumentException || e is UnrecoverableKeyException ||
      e is KeyPermanentlyInvalidatedException || e is MissingKeyException

  private fun encrypt(plain: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + ct, Base64.NO_WRAP)
  }

  private fun decrypt(blob: String): String {
    val bytes = Base64.decode(blob, Base64.NO_WRAP)
    require(bytes.size > IV_BYTES) { "Token blob too short" }
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(
      Cipher.DECRYPT_MODE,
      existingKey() ?: throw MissingKeyException(),
      GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES),
    )
    return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
  }

  private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

  private fun existingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

  private fun getOrCreateKey(): SecretKey {
    existingKey()?.let { return it }
    val spec = KeyGenParameterSpec.Builder(
      KEY_ALIAS,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      .apply { init(spec) }
      .generateKey()
  }

  private fun deleteKey() {
    try {
      keyStore().deleteEntry(KEY_ALIAS)
    } catch (_: Exception) {
    }
  }

  /** The Keystore holds no key to decrypt with: the stored blob can never be read. */
  private class MissingKeyException : Exception("No Keystore key")

  private companion object {
    const val PREFS = "secure_prefs"
    const val KEY_TOKEN = "token"
    const val KEY_LABEL = "account_label"
    const val KEY_ALIAS = "clickup_token_key"
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    const val TAG_BITS = 128
  }
}
