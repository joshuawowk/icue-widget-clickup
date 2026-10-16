package net.jwowk.clickupwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot
import net.jwowk.clickupwidget.widget.TasksWidget
import net.jwowk.clickupwidget.widget.WidgetStore
import net.jwowk.clickupwidget.widget.WidgetUpdater
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** Fetches tasks for one widget (input [WidgetWork.KEY_APP_WIDGET_ID]) or every placed widget, into its snapshot. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val ctx = applicationContext
    val manual = inputData.getBoolean(WidgetWork.KEY_MANUAL, false)
    val dedupe = inputData.getBoolean(WidgetWork.KEY_DEDUPE, false)
    val ids = WidgetWork.widgetIdFrom(inputData.getInt(WidgetWork.KEY_APP_WIDGET_ID, -1))
      ?.let { intArrayOf(it) } ?: WidgetUpdater.placedWidgetIds(ctx)
    for (id in ids) {
      try {
        refreshOne(ctx, id, manual, dedupe)
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // One broken widget must not stop the others.
      }
    }
    return Result.success()
  }

  private suspend fun refreshOne(ctx: Context, appWidgetId: Int, manual: Boolean, dedupe: Boolean) {
    val glanceId = WidgetStore.glanceIdOrNull(ctx, appWidgetId) ?: return
    val config = Graph.configStore.widgetConfig(appWidgetId)
    if (dedupe && !manual) {
      val s = WidgetStore.read(ctx, glanceId)
      val fresh = System.currentTimeMillis() - s.snapshot.fetchedAtMs < DEDUPE_MS
      if (s.snapshot.loadState == LoadState.READY && fresh && s.config?.let { sameSource(it, config) } == true) return
    }
    WidgetStore.update(ctx, glanceId) { s -> s.copy(config = config, ui = if (manual) s.ui.copy(refreshing = true) else s.ui) }
    if (manual) TasksWidget().update(ctx, glanceId)

    val fetched: kotlin.Result<TaskFetch> = try {
      kotlin.Result.success(Graph.repository().fetchTasks(config))
    } catch (e: CancellationException) {
      withContext(NonCancellable) {
        WidgetStore.updateAndRender(ctx, glanceId) { it.copy(ui = it.ui.copy(refreshing = false)) }
      }
      throw e
    } catch (e: Exception) {
      kotlin.Result.failure(e)
    }
    val stale = staleCommitting(ctx, appWidgetId, WidgetStore.read(ctx, glanceId).committing)
    val now = System.currentTimeMillis()
    WidgetStore.updateAndRender(ctx, glanceId) { s ->
      // The widget was re-pointed (list/workspace) while this fetch was in flight: its result is stale.
      if (s.config != null && !sameSource(s.config, config)) {
        return@updateAndRender s.copy(ui = s.ui.copy(refreshing = false))
      }
      val snapshot = fetched.fold(
        onSuccess = { f ->
          WidgetSnapshot(
            loadState = LoadState.READY,
            tasks = f.tasks,
            listStatuses = f.listStatuses,
            workspaceName = f.workspaceName,
            fetchedAtMs = now,
            offline = false,
          )
        },
        onFailure = { e -> failedSnapshot(ctx, s.snapshot, e) },
      )
      s.copy(snapshot = snapshot, ui = s.ui.copy(refreshing = false), committing = s.committing - stale)
        .openBarIfFilteredEmpty(now)
    }
  }

  private fun failedSnapshot(ctx: Context, prev: WidgetSnapshot, e: Throwable): WidgetSnapshot {
    if (e is NoTokenException) return WidgetSnapshot(loadState = LoadState.SETUP)
    // A rejected token won't fix itself: say so instead of showing stale tasks as "offline" forever.
    if (e is ClickUpApiException && e.code == 401) {
      return WidgetSnapshot(loadState = LoadState.ERROR, errorMessage = ctx.getString(R.string.w_err_token), authError = true)
    }
    if (prev.loadState == LoadState.READY) return prev.copy(offline = true)
    val message = when {
      e is ClickUpApiException -> ctx.getString(R.string.w_err_http, e.code)
      e is IOException -> ctx.getString(R.string.w_err_network)
      else -> e.message ?: e.javaClass.simpleName
    }
    return WidgetSnapshot(loadState = LoadState.ERROR, errorMessage = message)
  }

  private fun sameSource(a: WidgetConfig, b: WidgetConfig) = a.listId == b.listId && a.workspaceId == b.workspaceId

  /** "Committing" ids whose complete worker is no longer alive (e.g. the process died mid-commit). */
  private suspend fun staleCommitting(ctx: Context, appWidgetId: Int, committing: Set<String>): Set<String> {
    if (committing.isEmpty()) return emptySet()
    val wm = WorkManager.getInstance(ctx)
    return committing.filterTo(mutableSetOf()) { taskId ->
      wm.getWorkInfosForUniqueWorkFlow(WidgetWork.completeName(appWidgetId, taskId)).first().none { !it.state.isFinished }
    }
  }

  private companion object {
    const val DEDUPE_MS = 20_000L
  }
}
