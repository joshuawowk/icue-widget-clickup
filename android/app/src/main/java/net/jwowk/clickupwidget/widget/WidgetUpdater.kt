package net.jwowk.clickupwidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.work.WidgetWork

/** Entry points the app screens use to drive the widget(s). */
object WidgetUpdater {
  private const val PREFS = "widget_updater"
  private const val KEY_PREVIEW_VERSION = "previewVersion"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  /** Called from Application.onCreate: (re)schedule periodic refresh; publish generated previews (API 35+). */
  fun onAppStart(context: Context) {
    val app = context.applicationContext
    runCatching { schedulePeriodic(app) }
    if (Build.VERSION.SDK_INT >= 35) scope.launch { runCatching { publishPreviews(app) } }
  }

  /** Fetch now (one widget, or all when null) via one-time work without a network constraint; shows "Refreshing…". */
  fun refreshNow(context: Context, appWidgetId: Int? = null) {
    WidgetWork.refresh(context.applicationContext, appWidgetId, manual = true)
  }

  /** Re-render one widget from cached state + current config, no network (after a style change). */
  suspend fun rerender(context: Context, appWidgetId: Int) {
    val glanceId = WidgetStore.glanceIdOrNull(context, appWidgetId) ?: return
    val config = Graph.configStore.widgetConfig(appWidgetId)
    WidgetStore.updateAndRender(context, glanceId) { it.copy(config = config) }
  }

  /** Reset a widget's filter chips/bar to its config defaults (iCUE resets filters on settings change). */
  suspend fun resetFilters(context: Context, appWidgetId: Int) {
    val glanceId = WidgetStore.glanceIdOrNull(context, appWidgetId) ?: return
    val config = Graph.configStore.widgetConfig(appWidgetId)
    WidgetStore.updateAndRender(context, glanceId) {
      it.copy(config = config, ui = it.ui.copy(filterBarOpen = false, selectedStatuses = null, selectedPriorities = null))
    }
  }

  /** (Re)schedule the periodic refresh from AppSettings.refreshMinutes; cancel when no widgets are placed. */
  fun schedulePeriodic(context: Context) {
    if (placedWidgetIds(context).isEmpty()) {
      WidgetWork.cancelPeriodic(context)
    } else {
      WidgetWork.schedulePeriodic(context, Graph.configStore.appSettings().refreshMinutes)
    }
  }

  /** App widget ids of this provider currently on any home screen. */
  fun placedWidgetIds(context: Context): IntArray =
    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, TasksWidgetReceiver::class.java))

  /** Generated (live-rendered) picker previews, once per app version; the system rate-limits these calls. */
  private suspend fun publishPreviews(context: Context) {
    val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getLong(KEY_PREVIEW_VERSION, -1) == version) return
    val result = GlanceAppWidgetManager(context).setWidgetPreviews(TasksWidgetReceiver::class)
    if (result == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
      prefs.edit().putLong(KEY_PREVIEW_VERSION, version).apply()
    }
  }
}
