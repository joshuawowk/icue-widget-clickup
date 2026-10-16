package net.jwowk.clickupwidget.ui.detail

import android.app.Application
import android.appwidget.AppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.widget.WidgetUpdater

/**
 * A snackbar request. [undoId] non-null means it carries an Undo action for that pending completion.
 * State-driven (not an event) so it survives rotation.
 */
internal data class SnackMessage(
  val key: Long,
  val text: String,
  val undoId: String? = null,
  val durationMs: Long = 4000,
)

internal data class DetailUiState(
  val appWidgetId: Int,
  val task: TaskItem,
  val config: WidgetConfig,
  /** "Now" for due classification, fixed when the sheet was opened. */
  val nowMs: Long,
  val detailLoading: Boolean = true,
  val detailLoaded: Boolean = false,
  val detailFailed: Boolean = false,
  val description: String = "",
  /** Direct subtasks in [TaskLogic.sortSubtasks] order. */
  val subtasks: List<TaskItem> = emptyList(),
  /** null while loading. */
  val statuses: List<TaskStatus>? = null,
  val statusesFailed: Boolean = false,
  /** Name of the status being saved; non-null disables every pill. */
  val savingStatus: String? = null,
  /** Task ids (subtasks or the task itself) in the "completing" undo window or in flight. */
  val pending: Set<String> = emptySet(),
  /** Subtasks confirmed completed but not yet reflected by a reload. */
  val doneIds: Set<String> = emptySet(),
  val snack: SnackMessage? = null,
  /** The task was completed from the sheet: the activity should close. */
  val closeRequested: Boolean = false,
) {
  val statusBusy: Boolean get() = savingStatus != null

  fun isSubtaskDone(sub: TaskItem): Boolean = sub.isDone || sub.id in doneIds || sub.id in pending

  val subtasksDone: Int get() = subtasks.count(::isSubtaskDone)
}

/**
 * State and actions of the task-detail sheet. Quick-completes wait [net.jwowk.clickupwidget.data.model.AppSettings.undoSeconds]
 * (Undo cancels) and then call the repository; they live here so a rotation doesn't double-fire.
 */
internal class TaskDetailViewModel(app: Application) : AndroidViewModel(app) {
  private class PendingOp(val id: String, val listId: String?, val isParent: Boolean, val job: Job)

  private val _state = MutableStateFlow<DetailUiState?>(null)
  val state: StateFlow<DetailUiState?> = _state.asStateFlow()

  private var session = 0
  private var snackKey = 0L
  private var loadJob: Job? = null
  private var statusJob: Job? = null
  private val ops = HashMap<String, PendingOp>()

  /** A change was made that the home-screen widget hasn't been refreshed for yet. */
  private var dirty = false

  private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

  private fun update(s: Int, f: (DetailUiState) -> DetailUiState) {
    _state.update { cur -> if (cur != null && s == session) f(cur) else cur }
  }

  /**
   * Show [task] from widget [appWidgetId]. A repeat call for the task already shown is ignored
   * unless [force] (new intent), so rotation doesn't reload.
   */
  fun open(appWidgetId: Int, task: TaskItem, force: Boolean = false) {
    val cur = _state.value
    if (!force && cur != null && cur.appWidgetId == appWidgetId && cur.task.id == task.id) return
    if (cur != null) flushAndRefresh()
    session++
    val s = session
    loadJob?.cancel()
    statusJob?.cancel()
    _state.value = DetailUiState(
      appWidgetId = appWidgetId,
      task = task,
      config = Graph.configStore.widgetConfig(appWidgetId),
      nowMs = System.currentTimeMillis(),
    )
    loadDetail(s)
    loadStatuses(s, task)
  }

  private fun loadDetail(s: Int, delayMs: Long = 0) {
    val task = _state.value?.task ?: return
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      try {
        if (delayMs > 0) delay(delayMs)
        val d = Graph.repository().taskDetail(task)
        update(s) {
          it.copy(
            detailLoading = false,
            detailLoaded = true,
            detailFailed = false,
            description = d.description,
            subtasks = TaskLogic.sortSubtasks(d.subtasks, it.nowMs),
            doneIds = emptySet(),
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        update(s) { it.copy(detailLoading = false, detailFailed = !it.detailLoaded) }
      }
    }
  }

  private fun loadStatuses(s: Int, task: TaskItem) {
    val listId = task.listId
    if (listId == null) {
      update(s) { it.copy(statusesFailed = true) }
      return
    }
    statusJob = viewModelScope.launch {
      try {
        val list = Graph.repository().listStatuses(listId)
        update(s) { it.copy(statuses = list, statusesFailed = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        update(s) { it.copy(statusesFailed = true) }
      }
    }
  }

  /** Tap on a status pill. */
  fun setStatus(status: TaskStatus) {
    val s = session
    val cur = _state.value ?: return
    if (cur.statusBusy || DetailFormat.isCurrentStatus(status.name, cur.task.status)) return
    update(s) { it.copy(savingStatus = status.name) }
    viewModelScope.launch {
      try {
        withContext(NonCancellable) { Graph.repository().setStatus(cur.task.id, status.name) }
        update(s) {
          it.copy(
            task = it.task.copy(status = status.name, statusColorHex = status.colorHex, statusType = status.type),
            savingStatus = null,
          )
        }
        snack(s, str(R.string.detail_status_changed, status.name.uppercase()), durationMs = 2200)
        refreshWidget(cur.appWidgetId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        update(s) { it.copy(savingStatus = null) }
        snack(s, str(R.string.detail_status_failed))
      }
    }
  }

  /** Quick-complete a subtask (undo window first). */
  fun completeSubtask(sub: TaskItem) {
    val cur = _state.value ?: return
    startComplete(sub.id, sub.name, sub.listId ?: cur.task.listId, DetailFormat.SUBTASK_NAME_SNACK_MAX, isParent = false)
  }

  /** "Mark complete" for the task itself (undo window first). */
  fun completeTask() {
    val cur = _state.value ?: return
    startComplete(cur.task.id, cur.task.name, cur.task.listId, DetailFormat.TASK_NAME_SNACK_MAX, isParent = true)
  }

  private fun startComplete(id: String, name: String, listId: String?, nameMax: Int, isParent: Boolean) {
    val s = session
    val cur = _state.value ?: return
    if (id in cur.pending) return
    if (listId == null) {
      snack(s, str(R.string.detail_no_list), durationMs = 3000)
      return
    }
    val undoMs = Graph.configStore.appSettings().undoSeconds.coerceIn(1, 30) * 1000L
    update(s) { it.copy(pending = it.pending + id) }
    snack(s, str(R.string.detail_completing, DetailFormat.ellipsize(name, nameMax)), undoId = id, durationMs = undoMs)
    val job = viewModelScope.launch {
      delay(undoMs)
      ops.remove(id)
      runComplete(s, cur.appWidgetId, id, listId, isParent)
    }
    ops[id] = PendingOp(id, listId, isParent, job)
  }

  /** Undo button on the "Completing" snackbar. A no-op once the request is on its way. */
  fun undo(id: String) {
    val op = ops.remove(id) ?: return
    op.job.cancel()
    update(session) { it.copy(pending = it.pending - id) }
  }

  private suspend fun runComplete(s: Int, appWidgetId: Int, id: String, listId: String, isParent: Boolean) {
    try {
      withContext(NonCancellable) { Graph.repository().completeTask(id, listId) }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      update(s) { it.copy(pending = it.pending - id) }
      snack(
        s,
        str(if (isParent) R.string.detail_complete_failed else R.string.detail_subtask_complete_failed),
        durationMs = 4000,
      )
      return
    }
    if (isParent) {
      refreshWidget(appWidgetId)
      update(s) { it.copy(pending = it.pending - id, closeRequested = true) }
    } else {
      dirty = true
      update(s) { it.copy(pending = it.pending - id, doneIds = it.doneIds + id) }
      loadDetail(s, delayMs = 450)
    }
  }

  fun snackConsumed(key: Long) {
    _state.update { cur -> if (cur?.snack?.key == key) cur.copy(snack = null) else cur }
  }

  private fun snack(s: Int, text: String, undoId: String? = null, durationMs: Long = 4000) {
    update(s) { it.copy(snack = SnackMessage(++snackKey, text, undoId, durationMs)) }
  }

  private fun refreshWidget(appWidgetId: Int) {
    dirty = false
    WidgetUpdater.refreshNow(
      getApplication(),
      appWidgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID },
    )
  }

  /**
   * The sheet is closing (or switching tasks): completions still inside their undo window are sent now
   * rather than lost, and the widget is refreshed once if anything changed.
   */
  fun flushAndRefresh() {
    val cur = _state.value ?: return
    val repo = Graph.repository()
    val toSend = ops.values.toList()
    ops.clear()
    toSend.forEach { it.job.cancel() }
    val needRefresh = dirty || toSend.isNotEmpty()
    dirty = false
    if (!needRefresh) return
    val context = getApplication<Application>()
    val widgetId = cur.appWidgetId.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    appScope.launch {
      toSend.forEach { op -> op.listId?.let { runCatching { repo.completeTask(op.id, it) } } }
      WidgetUpdater.refreshNow(context, widgetId)
    }
  }

  private companion object {
    /** Outlives the ViewModel so a completion flushed on close still reaches ClickUp. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }
}
