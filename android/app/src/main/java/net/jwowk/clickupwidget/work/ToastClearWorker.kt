package net.jwowk.clickupwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.jwowk.clickupwidget.widget.WidgetStore

/** Clears an expired widget toast and re-renders (a real state change, so the widget recomposes). */
class ToastClearWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val ctx = applicationContext
    val glanceId = WidgetStore.glanceIdOrNull(ctx, inputData.getInt(WidgetWork.KEY_APP_WIDGET_ID, -1))
      ?: return Result.success()
    val now = System.currentTimeMillis()
    WidgetStore.updateAndRender(ctx, glanceId) { s ->
      if (s.ui.toast != null && s.ui.toastUntilMs <= now) s.copy(ui = s.ui.copy(toast = null, toastUntilMs = 0)) else s
    }
    return Result.success()
  }
}
