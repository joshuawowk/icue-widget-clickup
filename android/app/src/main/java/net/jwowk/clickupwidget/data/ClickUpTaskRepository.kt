package net.jwowk.clickupwidget.data

import android.content.Context
import net.jwowk.clickupwidget.data.model.AccountInfo
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.TaskDetail
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace

// STUB — owned by the data-layer agent. Constructor signature is fixed (Graph uses it).

/** Live ClickUp v2 API implementation (OkHttp + kotlinx.serialization). */
class ClickUpTaskRepository(context: Context, private val tokenStore: TokenStore) : TaskRepository {
  override suspend fun verifyToken(token: String): AccountInfo = TODO()
  override suspend fun workspaces(): List<Workspace> = TODO()
  override suspend fun lists(workspaceId: String): List<ListRef> = TODO()
  override suspend fun fetchTasks(config: WidgetConfig): TaskFetch = TODO()
  override suspend fun listStatuses(listId: String): List<TaskStatus> = TODO()
  override suspend fun taskDetail(task: TaskItem): TaskDetail = TODO()
  override suspend fun completeTask(taskId: String, listId: String): Unit = TODO()
  override suspend fun setStatus(taskId: String, status: String): Unit = TODO()
}
