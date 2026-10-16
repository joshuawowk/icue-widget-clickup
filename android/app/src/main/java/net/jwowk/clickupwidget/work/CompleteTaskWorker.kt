package net.jwowk.clickupwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.widget.WidgetState
import net.jwowk.clickupwidget.widget.WidgetStore
import kotlin.coroutines.cancellation.CancellationException

/**
 * Commits a quick-complete once its Undo window has passed. The task moves pending → committing in
 * one atomic state update; if it is no longer pending (undone), nothing is sent.
 */
class CompleteTaskWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val ctx = applicationContext
    val appWidgetId = inputData.getInt(WidgetWork.KEY_APP_WIDGET_ID, -1)
    val taskId = inputData.getString(WidgetWork.KEY_TASK_ID) ?: return Result.success()
    val listId = inputData.getString(WidgetWork.KEY_LIST_ID) ?: return Result.success()
    val glanceId = WidgetStore.glanceIdOrNull(ctx, appWidgetId) ?: return Result.success()

    var claimed = false
    WidgetStore.updateAndRender(ctx, glanceId) { s ->
      if (taskId !in s.ui.pendingCompletions) return@updateAndRender s
      claimed = true
      s.copy(ui = s.ui.copy(pendingCompletions = s.ui.pendingCompletions - taskId), committing = s.committing + taskId)
    }
    if (!claimed) return Result.success()

    try {
      Graph.repository().completeTask(taskId, listId)
    } catch (e: CancellationException) {
      withContext(NonCancellable) {
        WidgetStore.updateAndRender(ctx, glanceId) { it.copy(committing = it.committing - taskId) }
      }
      throw e
    } catch (_: Exception) {
      val now = System.currentTimeMillis()
      WidgetStore.updateAndRender(ctx, glanceId) {
        it.copy(committing = it.committing - taskId).withToast(ctx.getString(R.string.w_complete_failed), now)
      }
      WidgetWork.clearToastLater(ctx, appWidgetId, WidgetState.TOAST_MS + 200)
      return Result.success()
    }

    WidgetStore.updateAndRender(ctx, glanceId) { s ->
      s.copy(
        committing = s.committing - taskId,
        snapshot = s.snapshot.copy(tasks = s.snapshot.tasks.filterNot { it.id == taskId }),
      )
    }
    WidgetWork.refresh(ctx, appWidgetId, manual = false, delayMs = 2_000)
    return Result.success()
  }
}
