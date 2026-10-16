package net.jwowk.clickupwidget.data

import android.content.Context

// STUB — owned by the data-layer agent. Keep the public API exactly as declared.

/**
 * Stores the ClickUp personal API token (one per app, shared by all widgets) encrypted at rest
 * with an Android Keystore AES/GCM key, in app-private SharedPreferences. Never backed up
 * (manifest allowBackup=false). Also remembers a display label for the connected account.
 */
class TokenStore(context: Context) {
  fun token(): String? = TODO()
  fun hasToken(): Boolean = TODO()
  fun save(token: String): Unit = TODO()
  fun clear(): Unit = TODO()
  /** e.g. "Josh · Getinge" — shown in the app; null if unknown. */
  fun accountLabel(): String? = TODO()
  fun saveAccountLabel(label: String?): Unit = TODO()
}
