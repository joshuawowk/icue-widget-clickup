package net.jwowk.clickupwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.work.WidgetWork
import java.io.File

/** Provider for [TasksWidget]: kicks off refreshes and cleans up per-widget data. */
class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = TasksWidget()

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    appWidgetIds.forEach { WidgetWork.refresh(context, it, manual = false, dedupe = true) }
    runCatching { WidgetUpdater.schedulePeriodic(context) }
  }

  override fun onDeleted(context: Context, appWidgetIds: IntArray) {
    super.onDeleted(context, appWidgetIds)
    appWidgetIds.forEach { id ->
      WidgetWork.cancelForWidget(context, id)
      runCatching {
        Graph.configStore.widgetConfig(id).backgroundImagePath?.let { deleteIfPrivate(context, it) }
        Graph.configStore.deleteWidgetConfig(id)
      }
    }
  }

  override fun onDisabled(context: Context) {
    super.onDisabled(context)
    WidgetWork.cancelPeriodic(context)
  }

  private fun deleteIfPrivate(context: Context, path: String) {
    val file = File(path).canonicalFile
    val root = context.filesDir.canonicalPath + File.separator
    if (file.path.startsWith(root)) file.delete()
  }
}
