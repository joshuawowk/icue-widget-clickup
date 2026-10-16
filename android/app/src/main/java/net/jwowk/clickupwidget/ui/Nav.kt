package net.jwowk.clickupwidget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.Json
import net.jwowk.clickupwidget.MainActivity
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.ui.config.ConfigActivity
import net.jwowk.clickupwidget.ui.detail.TaskDetailActivity

/** Intent contracts between the widget and the app screens. */
object Nav {
  /** JSON of the tapped [TaskItem] so the detail screen renders instantly before fetching more. */
  const val EXTRA_TASK_JSON = "net.jwowk.clickupwidget.extra.TASK_JSON"

  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Detail screen for [task] opened from widget [appWidgetId]. The data URI makes every row's
   * PendingIntent distinct (extras alone don't count for PendingIntent equality).
   */
  fun detailIntent(context: Context, appWidgetId: Int, task: TaskItem): Intent =
    Intent(context, TaskDetailActivity::class.java)
      .setData(Uri.parse("clickupwidget://task/$appWidgetId/${Uri.encode(task.id)}"))
      .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      .putExtra(EXTRA_TASK_JSON, json.encodeToString(TaskItem.serializer(), task))
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

  fun taskFromIntent(intent: Intent): TaskItem? =
    intent.getStringExtra(EXTRA_TASK_JSON)?.let {
      runCatching { json.decodeFromString(TaskItem.serializer(), it) }.getOrNull()
    }

  /** Settings for one placed widget (also used outside the launcher's configure flow). */
  fun configIntent(context: Context, appWidgetId: Int): Intent =
    Intent(context, ConfigActivity::class.java)
      .setData(Uri.parse("clickupwidget://config/$appWidgetId"))
      .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

  fun mainIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
