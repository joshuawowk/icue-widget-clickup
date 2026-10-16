package net.jwowk.clickupwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.data.DemoTaskRepository
import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.ThemeMode
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot

/** The ClickUp tasks home-screen widget. All data comes from its Glance Preferences state. */
class TasksWidget : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val appWidgetId = WidgetStore.appWidgetId(context, id)
    var seed = WidgetStore.read(context, id).config
    if (seed == null) {
      val fromStore = runCatching { Graph.configStore.widgetConfig(appWidgetId) }.getOrDefault(WidgetConfig())
      seed = WidgetStore.update(context, id) { if (it.config == null) it.copy(config = fromStore) else it }.config
    }
    val fallback = seed ?: WidgetConfig()
    val env = WidgetEnv(appWidgetId, interactive = true)
    provideContent {
      val state = WidgetState.from(currentState<Preferences>())
      GlanceTheme { WidgetRoot(if (state.config == null) state.copy(config = fallback) else state, env) }
    }
  }

  override suspend fun providePreview(context: Context, widgetCategory: Int) {
    val state = sampleState(System.currentTimeMillis())
    provideContent { GlanceTheme { WidgetRoot(state, WidgetEnv(AppWidgetManager.INVALID_APPWIDGET_ID, false)) } }
  }

  internal companion object {
    /** Sample content (SYSTEM theme) for the generated picker preview. */
    fun sampleState(nowMs: Long) = WidgetState(
      config = WidgetConfig(theme = ThemeMode.SYSTEM),
      snapshot = WidgetSnapshot(
        loadState = LoadState.READY,
        tasks = DemoTaskRepository.sampleTasks(nowMs),
        workspaceName = DemoTaskRepository.SAMPLE_WORKSPACE_NAME,
        fetchedAtMs = nowMs,
      ),
    )
  }
}
