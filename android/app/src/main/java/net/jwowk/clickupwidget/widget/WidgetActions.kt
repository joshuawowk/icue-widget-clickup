package net.jwowk.clickupwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.work.WidgetWork

/** Action parameter keys for the widget callbacks. */
internal object WidgetParams {
  val STATUS = ActionParameters.Key<String>("status")
  val PRIORITY = ActionParameters.Key<String>("priority")
  val TASK_ID = ActionParameters.Key<String>("taskId")
  val LIST_ID = ActionParameters.Key<String>("listId")
}

/** Header tap: show/hide the filter bar. */
class ToggleFilterBarAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    WidgetStore.updateAndRender(context, glanceId) { it.copy(ui = it.ui.copy(filterBarOpen = !it.ui.filterBarOpen)) }
  }
}

/** Status chip tap. Resolves a null selection from the config default first. */
class ToggleStatusAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val name = parameters[WidgetParams.STATUS] ?: return
    val now = System.currentTimeMillis()
    WidgetStore.updateAndRender(context, glanceId) { s ->
      val current = s.ui.selectedStatuses ?: s.view(now).selectedStatuses
      s.copy(ui = s.ui.copy(selectedStatuses = TaskLogic.toggle(current, name))).openBarIfFilteredEmpty(now)
    }
  }
}

/** Priority chip tap. Seeds a null selection from the config default first. */
class TogglePriorityAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val key = parameters[WidgetParams.PRIORITY] ?: return
    val now = System.currentTimeMillis()
    WidgetStore.updateAndRender(context, glanceId) { s ->
      val current = s.ui.selectedPriorities ?: s.view(now).selectedPriorities
      s.copy(ui = s.ui.copy(selectedPriorities = TaskLogic.toggle(current, key))).openBarIfFilteredEmpty(now)
    }
  }
}

/** Check-circle tap: start a quick-complete (with Undo), or undo it when it is still pending. */
class CompleteTaskAction : ActionCallback {
  private enum class Outcome { STARTED, UNDONE, BUSY, NO_LIST }

  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val taskId = parameters[WidgetParams.TASK_ID] ?: return
    val listId = parameters[WidgetParams.LIST_ID]
    val appWidgetId = WidgetStore.appWidgetId(context, glanceId)
    val now = System.currentTimeMillis()
    var outcome = Outcome.STARTED
    WidgetStore.update(context, glanceId) { s ->
      when {
        taskId in s.committing -> {
          outcome = Outcome.BUSY
          s.withToast(context.getString(R.string.w_already_completing), now)
        }
        taskId in s.ui.pendingCompletions -> {
          outcome = Outcome.UNDONE
          s.copy(ui = s.ui.copy(pendingCompletions = s.ui.pendingCompletions - taskId))
        }
        listId.isNullOrEmpty() -> {
          outcome = Outcome.NO_LIST
          s.withToast(context.getString(R.string.w_list_unknown), now)
        }
        else -> s.copy(ui = s.ui.copy(pendingCompletions = s.ui.pendingCompletions + taskId))
      }
    }
    when (outcome) {
      Outcome.STARTED -> WidgetWork.complete(
        context, appWidgetId, taskId, listId!!, Graph.configStore.appSettings().undoSeconds.coerceIn(3, 10),
      )
      Outcome.UNDONE -> WidgetWork.cancelComplete(context, appWidgetId, taskId)
      Outcome.BUSY, Outcome.NO_LIST -> WidgetWork.clearToastLater(context, appWidgetId, WidgetState.TOAST_MS + 200)
    }
    TasksWidget().update(context, glanceId)
  }
}

/** Toast "Undo": cancel every pending quick-complete. */
class UndoAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    val appWidgetId = WidgetStore.appWidgetId(context, glanceId)
    var undone = emptySet<String>()
    WidgetStore.update(context, glanceId) { s ->
      undone = s.ui.pendingCompletions
      s.copy(ui = s.ui.copy(pendingCompletions = emptySet()))
    }
    undone.forEach { WidgetWork.cancelComplete(context, appWidgetId, it) }
    TasksWidget().update(context, glanceId)
  }
}

/** Refresh button / error-state tap. */
class RefreshAction : ActionCallback {
  override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
    var already = false
    WidgetStore.update(context, glanceId) { s ->
      already = s.ui.refreshing
      if (already) s else s.copy(ui = s.ui.copy(refreshing = true))
    }
    if (already) return
    TasksWidget().update(context, glanceId)
    WidgetWork.refresh(context, WidgetStore.appWidgetId(context, glanceId), manual = true)
  }
}
