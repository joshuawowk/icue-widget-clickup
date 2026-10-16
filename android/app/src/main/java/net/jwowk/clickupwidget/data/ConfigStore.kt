package net.jwowk.clickupwidget.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.WidgetConfig

// STUB — owned by the data-layer agent. Keep the public API exactly as declared.

/**
 * Per-widget [WidgetConfig] (keyed by appWidgetId) and global [AppSettings], as JSON in
 * app-private SharedPreferences. Reads are synchronous and cheap (safe on any thread).
 */
class ConfigStore(context: Context) {
  /** Saved config, or defaults if this widget was never configured. */
  fun widgetConfig(appWidgetId: Int): WidgetConfig = TODO()
  fun hasWidgetConfig(appWidgetId: Int): Boolean = TODO()
  fun saveWidgetConfig(appWidgetId: Int, config: WidgetConfig): Unit = TODO()
  fun deleteWidgetConfig(appWidgetId: Int): Unit = TODO()

  fun appSettings(): AppSettings = TODO()
  fun saveAppSettings(settings: AppSettings): Unit = TODO()
  /** Emits the current settings, then again on every change. */
  fun appSettingsFlow(): Flow<AppSettings> = TODO()

  /**
   * Debug builds only: an alternate API base URL (e.g. a local mock server) set via
   * `adb shell am broadcast`/run-as prefs editing. Returns null in release builds or when unset.
   */
  fun apiBaseUrlOverride(): String? = TODO()
}
