package net.jwowk.clickupwidget.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.json.Json
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.WidgetConfig
import java.io.File

/**
 * Per-widget [WidgetConfig] (keyed by appWidgetId) and global [AppSettings], as JSON in
 * app-private SharedPreferences. Reads are synchronous and cheap (safe on any thread).
 * A missing or corrupt entry reads as defaults.
 */
class ConfigStore(context: Context) {
  private val appContext = context.applicationContext
  private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** Saved config, or defaults if this widget was never configured. */
  fun widgetConfig(appWidgetId: Int): WidgetConfig =
    decode(prefs.getString(widgetKey(appWidgetId), null)) ?: WidgetConfig()

  fun hasWidgetConfig(appWidgetId: Int): Boolean = prefs.contains(widgetKey(appWidgetId))

  fun saveWidgetConfig(appWidgetId: Int, config: WidgetConfig) {
    prefs.edit().putString(widgetKey(appWidgetId), json.encodeToString(WidgetConfig.serializer(), config)).apply()
  }

  fun deleteWidgetConfig(appWidgetId: Int) {
    prefs.edit().remove(widgetKey(appWidgetId)).apply()
  }

  fun appSettings(): AppSettings = decode(prefs.getString(KEY_APP_SETTINGS, null)) ?: AppSettings()

  fun saveAppSettings(settings: AppSettings) {
    prefs.edit().putString(KEY_APP_SETTINGS, json.encodeToString(AppSettings.serializer(), settings)).apply()
  }

  /** Emits the current settings, then again on every change. */
  fun appSettingsFlow(): Flow<AppSettings> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key == null || key == KEY_APP_SETTINGS) trySend(appSettings())
    }
    prefs.registerOnSharedPreferenceChangeListener(listener)
    trySend(appSettings())
    awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
  }.distinctUntilChanged()

  /**
   * Debug builds only: an alternate API base URL (e.g. a local mock server), read from the first line of
   * `files/api_base_override.txt` (write it via `adb shell run-as`). Null in release builds or when unset.
   */
  fun apiBaseUrlOverride(): String? {
    if (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return null
    return try {
      val file = File(appContext.filesDir, OVERRIDE_FILE)
      if (!file.isFile) return null
      file.useLines { it.firstOrNull() }?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
      null
    }
  }

  private inline fun <reified T> decode(raw: String?): T? {
    if (raw == null) return null
    return try {
      json.decodeFromString<T>(raw)
    } catch (_: Exception) {
      null
    }
  }

  private fun widgetKey(appWidgetId: Int) = "w_$appWidgetId"

  private companion object {
    const val PREFS = "widget_config"
    const val KEY_APP_SETTINGS = "app_settings"
    const val OVERRIDE_FILE = "api_base_override.txt"

    val json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      coerceInputValues = true
      explicitNulls = false
    }
  }
}
