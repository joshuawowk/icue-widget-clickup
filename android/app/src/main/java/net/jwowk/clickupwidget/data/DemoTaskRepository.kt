package net.jwowk.clickupwidget.data

import net.jwowk.clickupwidget.data.model.AccountInfo
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.TaskDetail
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace

// STUB — owned by the data-layer agent.

/**
 * In-memory sample workspace for demo mode, the widget picker preview and screenshots.
 * Due dates are relative to "now" so chips always show Overdue/Today/Tomorrow/dates.
 */
class DemoTaskRepository : TaskRepository {
  override suspend fun verifyToken(token: String): AccountInfo = TODO()
  override suspend fun workspaces(): List<Workspace> = TODO()
  override suspend fun lists(workspaceId: String): List<ListRef> = TODO()
  override suspend fun fetchTasks(config: WidgetConfig): TaskFetch = TODO()
  override suspend fun listStatuses(listId: String): List<TaskStatus> = TODO()
  override suspend fun taskDetail(task: TaskItem): TaskDetail = TODO()
  override suspend fun completeTask(taskId: String, listId: String): Unit = TODO()
  override suspend fun setStatus(taskId: String, status: String): Unit = TODO()

  companion object {
    /** Sample tasks for previews (widget picker / config screen) without touching repository state. */
    fun sampleTasks(nowMs: Long): List<TaskItem> = TODO()
    const val SAMPLE_WORKSPACE_NAME = "Demo workspace"
  }
}
