package net.jwowk.clickupwidget.ui.detail

import android.app.Application
import android.appwidget.AppWidgetManager
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskField
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.widget.WidgetUpdater
import java.util.concurrent.atomic.AtomicLong

/**
 * A snackbar request. [undoId] non-null means it carries an Undo action for that pending completion.
 * State-driven (not an event) so it survives rotation; [deadlineMs] is absolute ([SystemClock.elapsedRealtime])
 * so a rotation shows only the time that is left.
 */
internal data class SnackMessage(
  val key: Long,
  val text: String,
  val undoId: String? = null,
  val deadlineMs: Long = 0,
  /** How long it shows; a queued message gets a fresh [deadlineMs] when it is promoted. */
  val durationMs: Long = 0,
)

/** Process-lifetime scope for requests that must finish (and refresh the widget) after the sheet is gone. */
internal object DetailWork {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

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
  /** Populated standard + custom fields (read-only), in display order. */
  val fields: List<TaskField> = emptyList(),
  /** Direct subtasks in [TaskLogic.sortSubtasks] order. */
  val subtasks: List<TaskItem> = emptyList(),
  /** null while loading. */
  val statuses: List<TaskStatus>? = null,
  val statusesFailed: Boolean = false,
  /** Name of the status being saved; non-null disables every pill. */
  val savingStatus: String? = null,
  /** Priority being saved; non-null disables every priority pill. */
  val savingPriority: Priority? = null,
  /** A priority change was saved this session: a stale detail response must not overwrite it. */
  val priorityTouched: Boolean = false,
  /** Task ids (subtasks or the task itself) in the "completing" undo window or in flight. */
  val pending: Set<String> = emptySet(),
  /** Subtasks confirmed completed but not yet reflected by a reload. */
  val doneIds: Set<String> = emptySet(),
  val snack: SnackMessage? = null,
  /** Non-undo messages waiting behind an active undo snackbar. */
  val snackQueue: List<SnackMessage> = emptyList(),
  /** The task was completed from the sheet: the activity should close. */
  val closeRequested: Boolean = false,
) {
  val statusBusy: Boolean get() = savingStatus != null

  val priorityBusy: Boolean get() = savingPriority != null

  /** Done for display: committed or still in the undo window (struck through, circle checked). */
  fun isSubtaskDone(sub: TaskItem): Boolean = isSubtaskCommittedDone(sub) || sub.id in pending

  /** Done for real (not merely pending). */
  fun isSubtaskCommittedDone(sub: TaskItem): Boolean = sub.isDone || sub.id in doneIds

  val subtasksDone: Int get() = subtasks.count(::isSubtaskCommittedDone)

  /** Show [msg], or queue it when it would replace a still-active undo snackbar. */
  fun withSnack(msg: SnackMessage): DetailUiState =
    if (snack?.undoId != null && msg.undoId == null) copy(snackQueue = snackQueue + msg) else copy(snack = msg)

  /** Drop the current snackbar and promote the next queued one. */
  fun nextSnack(): DetailUiState = copy(
    snack = snackQueue.firstOrNull()?.let { it.copy(deadlineMs = SystemClock.elapsedRealtime() + it.durationMs) },
    snackQueue = snackQueue.drop(1),
  )

  /** Drop the undo snackbar for [id] (its op left the pending set). */
  fun clearUndoSnack(id: String): DetailUiState = if (snack?.undoId == id) nextSnack() else this
}

/**
 * State and actions of the task-detail sheet. Quick-completes wait [net.jwowk.clickupwidget.data.model.AppSettings.undoSeconds]
 * (Undo cancels) and then call the repository; they live here so a rotation doesn't double-fire.
 */
internal class TaskDetailViewModel(app: Application) : AndroidViewModel(app) {
  private class PendingOp(val id: String, val listId: String?, val isParent: Boolean, val job: Job)

  private val _state = MutableStateFlow<DetailUiState?>(null)
  val state: StateFlow<DetailUiState?> = _state.asStateFlow()

  @Volatile private var session = 0
  private val snackKey = AtomicLong()
  private var loadJob: Job? = null
  private var statusJob: Job? = null
  private val ops = HashMap<String, PendingOp>()

  /** A change was made that the home-screen widget hasn't been refreshed for yet. */
  @Volatile private var dirty = false

  /** The ViewModel is gone: requests finishing now must refresh the widget themselves. */
  @Volatile private var cleared = false

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
            task = DetailFormat.mergeFreshTask(
              it.task,
              d.task,
              keepPriority = it.priorityBusy || it.priorityTouched,
              keepStatus = it.statusBusy,
            ),
            description = d.description,
            fields = d.fields,
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
    DetailWork.scope.launch {
      try {
        Graph.repository().setStatus(cur.task.id, status.name)
        update(s) {
          it.copy(
            task = it.task.copy(status = status.name, statusColorHex = status.colorHex, statusType = status.type),
            savingStatus = null,
          )
        }
        snack(s, str(R.string.detail_status_changed, status.name.uppercase()), durationMs = 2200)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        update(s) { it.copy(savingStatus = null) }
        snack(s, str(R.string.detail_status_failed))
      } finally {
        refreshWidget(cur.appWidgetId)
      }
    }
  }

  /** Tap on a priority pill: optimistic, reverted with an error snackbar when the request fails. */
  fun setPriority(priority: Priority) {
    val s = session
    val cur = _state.value ?: return
    if (cur.priorityBusy || DetailFormat.isCurrentPriority(priority, cur.task.priority)) return
    val before = cur.task
    update(s) {
      it.copy(task = it.task.copy(priority = priority, priorityColorHex = null), savingPriority = priority)
    }
    DetailWork.scope.launch {
      try {
        Graph.repository().setPriority(before.id, priority)
        update(s) { it.copy(savingPriority = null, priorityTouched = true) }
        val text = if (priority == Priority.NONE) str(R.string.detail_priority_cleared)
        else str(R.string.detail_priority_changed, priority.label)
        snack(s, text, durationMs = 2200)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        update(s) {
          it.copy(
            task = it.task.copy(priority = before.priority, priorityColorHex = before.priorityColorHex),
            savingPriority = null,
          )
        }
        snack(s, str(R.string.detail_priority_failed))
      } finally {
        refreshWidget(cur.appWidgetId)
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
      DetailWork.scope.launch { runComplete(s, cur.appWidgetId, id, listId, isParent) }
    }
    ops[id] = PendingOp(id, listId, isParent, job)
  }

  /** Undo button on the "Completing" snackbar. A no-op once the request is on its way. */
  fun undo(id: String) {
    val op = ops.remove(id) ?: return
    op.job.cancel()
    update(session) { it.copy(pending = it.pending - id).clearUndoSnack(id) }
  }

  /** Runs in [DetailWork.scope]: must tolerate the ViewModel (and sheet) being gone. */
  private suspend fun runComplete(s: Int, appWidgetId: Int, id: String, listId: String, isParent: Boolean) {
    try {
      Graph.repository().completeTask(id, listId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      update(s) { it.copy(pending = it.pending - id).clearUndoSnack(id) }
      snack(
        s,
        str(if (isParent) R.string.detail_complete_failed else R.string.detail_subtask_complete_failed),
        durationMs = 4000,
      )
      refreshAfterRequest(s, appWidgetId)
      return
    }
    if (isParent) {
      refreshWidget(appWidgetId)
      update(s) { it.copy(pending = it.pending - id, closeRequested = true).clearUndoSnack(id) }
    } else {
      update(s) { it.copy(pending = it.pending - id, doneIds = it.doneIds + id).clearUndoSnack(id) }
      refreshAfterRequest(s, appWidgetId)
      if (!cleared) loadDetail(s, delayMs = 450)
    }
  }

  /** A subtask request finished: refresh now if nobody is left to flush on close, else defer to the flush. */
  private fun refreshAfterRequest(s: Int, appWidgetId: Int) {
    if (cleared || s != session) {
      refreshWidget(appWidgetId)
    } else {
      dirty = true
      // onCleared may have flushed between the check and the write: don't strand the refresh.
      if (cleared) refreshWidget(appWidgetId)
    }
  }

  fun snackConsumed(key: Long) {
    _state.update { cur -> if (cur?.snack?.key == key) cur.nextSnack() else cur }
  }

  private fun snack(s: Int, text: String, undoId: String? = null, durationMs: Long = 4000) {
    val msg = SnackMessage(snackKey.incrementAndGet(), text, undoId, SystemClock.elapsedRealtime() + durationMs, durationMs)
    update(s) { it.withSnack(msg) }
  }

  private fun refreshWidget(appWidgetId: Int) {
    // Safe from any thread and after clear(): only touches the application context.
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
    DetailWork.scope.launch {
      toSend.forEach { op -> op.listId?.let { runCatching { repo.completeTask(op.id, it) } } }
      WidgetUpdater.refreshNow(context, widgetId)
    }
  }

  override fun onCleared() {
    cleared = true
    flushAndRefresh()
  }
}
