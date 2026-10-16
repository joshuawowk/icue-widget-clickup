package net.jwowk.clickupwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.TaskListView
import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot
import net.jwowk.clickupwidget.data.model.WidgetStateKeys
import net.jwowk.clickupwidget.data.model.WidgetUiState

/** Keys in each widget's Glance Preferences state. */
internal object WidgetKeys {
  val SNAPSHOT = stringPreferencesKey(WidgetStateKeys.SNAPSHOT)
  val UI = stringPreferencesKey(WidgetStateKeys.UI)
  /** JSON of the widget's [WidgetConfig], mirrored from ConfigStore so config changes recompose. */
  val CONFIG = stringPreferencesKey("config")
  /** Task ids whose completion is being sent to ClickUp right now (Undo no longer possible). */
  val COMMITTING = stringSetPreferencesKey("committing")
}

private val stateJson = Json { ignoreUnknownKeys = true }

private fun <T> decode(serializer: KSerializer<T>, raw: String?): T? =
  raw?.let { runCatching { stateJson.decodeFromString(serializer, it) }.getOrNull() }

/** Decoded view of one widget's Glance state. [config] is null until mirrored/seeded. */
internal data class WidgetState(
  val config: WidgetConfig?,
  val snapshot: WidgetSnapshot = WidgetSnapshot(),
  val ui: WidgetUiState = WidgetUiState(),
  val committing: Set<String> = emptySet(),
) {
  val configOrDefault: WidgetConfig get() = config ?: WidgetConfig()

  /** The list/filter view for this state at [nowMs]. */
  fun view(nowMs: Long): TaskListView = TaskLogic.buildView(
    snapshot.tasks, configOrDefault, ui.selectedStatuses, ui.selectedPriorities, snapshot.listStatuses, nowMs,
  )

  /** iCUE renderTasks: when tasks exist but the filters hide them all, open the filter bar (and keep it open). */
  fun openBarIfFilteredEmpty(nowMs: Long): WidgetState {
    if (ui.filterBarOpen || snapshot.loadState != LoadState.READY) return this
    val v = view(nowMs)
    return if (v.total == 0 && v.scopedTotal > 0) copy(ui = ui.copy(filterBarOpen = true)) else this
  }

  fun withToast(message: String, nowMs: Long, durationMs: Long = TOAST_MS): WidgetState =
    copy(ui = ui.copy(toast = message, toastUntilMs = nowMs + durationMs))

  companion object {
    const val TOAST_MS = 4_000L

    fun from(prefs: Preferences): WidgetState = WidgetState(
      config = decode(WidgetConfig.serializer(), prefs[WidgetKeys.CONFIG]),
      snapshot = decode(WidgetSnapshot.serializer(), prefs[WidgetKeys.SNAPSHOT]) ?: WidgetSnapshot(),
      ui = decode(WidgetUiState.serializer(), prefs[WidgetKeys.UI]) ?: WidgetUiState(),
      committing = prefs[WidgetKeys.COMMITTING] ?: emptySet(),
    )
  }
}

internal fun WidgetState.writeTo(prefs: MutablePreferences) {
  if (config != null) prefs[WidgetKeys.CONFIG] = stateJson.encodeToString(WidgetConfig.serializer(), config)
  prefs[WidgetKeys.SNAPSHOT] = stateJson.encodeToString(WidgetSnapshot.serializer(), snapshot)
  prefs[WidgetKeys.UI] = stateJson.encodeToString(WidgetUiState.serializer(), ui)
  prefs[WidgetKeys.COMMITTING] = committing
}

/** Atomic read-modify-write of widget state (DataStore serializes updates) plus re-render helpers. */
internal object WidgetStore {
  suspend fun read(context: Context, glanceId: GlanceId): WidgetState =
    WidgetState.from(getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId))

  /** Applies [transform] atomically and returns the new state. */
  suspend fun update(context: Context, glanceId: GlanceId, transform: (WidgetState) -> WidgetState): WidgetState {
    var result: WidgetState? = null
    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
      val next = transform(WidgetState.from(prefs))
      result = next
      prefs.toMutablePreferences().also { next.writeTo(it) }.toPreferences()
    }
    return result!!
  }

  /** [update] then recompose the widget. */
  suspend fun updateAndRender(context: Context, glanceId: GlanceId, transform: (WidgetState) -> WidgetState): WidgetState =
    update(context, glanceId, transform).also { TasksWidget().update(context, glanceId) }

  fun glanceIdOrNull(context: Context, appWidgetId: Int): GlanceId? =
    runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()

  fun appWidgetId(context: Context, glanceId: GlanceId): Int =
    GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
}
