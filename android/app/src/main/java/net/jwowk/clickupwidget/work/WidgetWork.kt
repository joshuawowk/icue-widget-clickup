package net.jwowk.clickupwidget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Work names, tags and enqueue helpers for the widget's background jobs. */
object WidgetWork {
  const val KEY_APP_WIDGET_ID = "appWidgetId"
  const val KEY_TASK_ID = "taskId"
  const val KEY_LIST_ID = "listId"
  const val KEY_MANUAL = "manual"
  /** Skip the fetch when this widget was fetched moments ago (placement bursts, periodic right after onUpdate). */
  const val KEY_DEDUPE = "dedupe"
  const val PERIODIC = "refresh-all"
  private const val NO_WIDGET = -1

  /** Every request for one widget carries this tag so it can be cancelled when the widget is removed. */
  fun widgetTag(appWidgetId: Int) = "widget-$appWidgetId"
  fun refreshName(appWidgetId: Int?) = "refresh-${appWidgetId ?: "all-now"}"
  fun completeName(appWidgetId: Int, taskId: String) = "complete-$appWidgetId-$taskId"
  fun toastName(appWidgetId: Int) = "toast-$appWidgetId"

  /**
   * One-time refresh of one widget (or all when null). Manual refreshes replace an in-flight one and
   * flag "Refreshing…"; automatic ones keep an existing request. No network constraint, so it never hangs.
   */
  fun refresh(context: Context, appWidgetId: Int?, manual: Boolean, delayMs: Long = 0, dedupe: Boolean = false) {
    val req = OneTimeWorkRequestBuilder<RefreshWorker>()
      .setInputData(
        workDataOf(KEY_APP_WIDGET_ID to (appWidgetId ?: NO_WIDGET), KEY_MANUAL to manual, KEY_DEDUPE to dedupe),
      )
      .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
      .apply { if (appWidgetId != null) addTag(widgetTag(appWidgetId)) }
      .build()
    val policy = if (manual || delayMs > 0) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
    WorkManager.getInstance(context).enqueueUniqueWork(refreshName(appWidgetId), policy, req)
  }

  /** Commit a quick-complete after the Undo window. */
  fun complete(context: Context, appWidgetId: Int, taskId: String, listId: String, delaySeconds: Int) {
    val req = OneTimeWorkRequestBuilder<CompleteTaskWorker>()
      .setInputData(workDataOf(KEY_APP_WIDGET_ID to appWidgetId, KEY_TASK_ID to taskId, KEY_LIST_ID to listId))
      .setInitialDelay(delaySeconds.toLong(), TimeUnit.SECONDS)
      .addTag(widgetTag(appWidgetId))
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(completeName(appWidgetId, taskId), ExistingWorkPolicy.KEEP, req)
  }

  fun cancelComplete(context: Context, appWidgetId: Int, taskId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(completeName(appWidgetId, taskId))
  }

  /** Re-render after [delayMs] so an expired toast disappears. */
  fun clearToastLater(context: Context, appWidgetId: Int, delayMs: Long) {
    val req = OneTimeWorkRequestBuilder<ToastClearWorker>()
      .setInputData(workDataOf(KEY_APP_WIDGET_ID to appWidgetId))
      .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
      .addTag(widgetTag(appWidgetId))
      .build()
    WorkManager.getInstance(context).enqueueUniqueWork(toastName(appWidgetId), ExistingWorkPolicy.REPLACE, req)
  }

  fun cancelForWidget(context: Context, appWidgetId: Int) {
    WorkManager.getInstance(context).cancelAllWorkByTag(widgetTag(appWidgetId))
  }

  fun schedulePeriodic(context: Context, refreshMinutes: Int) {
    val minutes = refreshMinutes.coerceAtLeast(15).toLong()
    // Initial delay: placing a widget already fetches via onUpdate, so the first period need not run at once.
    val req = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES)
      .setInitialDelay(minutes, TimeUnit.MINUTES)
      .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
      .setInputData(workDataOf(KEY_APP_WIDGET_ID to NO_WIDGET, KEY_MANUAL to false, KEY_DEDUPE to true))
      .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, req)
  }

  fun cancelPeriodic(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
  }

  internal fun widgetIdFrom(value: Int): Int? = value.takeIf { it != NO_WIDGET }
}
