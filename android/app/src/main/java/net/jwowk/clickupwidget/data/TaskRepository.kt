package net.jwowk.clickupwidget.data

import net.jwowk.clickupwidget.data.model.AccountInfo
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.TaskDetail
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace

/**
 * Everything the app and widget need from ClickUp. Obtain via [net.jwowk.clickupwidget.Graph.repository].
 *
 * All calls are main-safe (implementations switch to Dispatchers.IO). Failures throw:
 *  - [net.jwowk.clickupwidget.data.model.NoTokenException] — no token stored (and not demo mode)
 *  - [net.jwowk.clickupwidget.data.model.ClickUpApiException] — non-2xx (401 = bad token)
 *  - java.io.IOException — network failure
 */
interface TaskRepository {
  /** Verifies [token] against /user + /team without storing it. */
  suspend fun verifyToken(token: String): AccountInfo

  /** Workspaces (teams) for the stored token. */
  suspend fun workspaces(): List<Workspace>

  /** Every list in [workspaceId] (spaces → folders → lists, plus folderless lists), for the list picker. */
  suspend fun lists(workspaceId: String): List<ListRef>

  /**
   * Open tasks for one widget per its [config]:
   *  - list mode (`config.listId` set): GET /list/{id}/task?include_closed=false&subtasks=false,
   *    plus that list's statuses (for chip order)
   *  - My Tasks mode: GET /team/{teamId}/task?assignees[]={me}&include_closed=false&subtasks=false&order_by=due_date
   * Returned unfiltered and unsorted.
   */
  suspend fun fetchTasks(config: WidgetConfig): TaskFetch

  /** Statuses configured on a list, in list order. Cached in memory per list id. */
  suspend fun listStatuses(listId: String): List<TaskStatus>

  /**
   * Description + direct subtasks. Primary: GET /task/{id}?include_subtasks=true; if that yields
   * no subtasks, fall back to GET /team/{teamId}/task?parent={id}&subtasks=true&include_closed=true
   * filtered to direct children (iCUE v1.3.1 behaviour). Subtasks inherit the parent's listId/listName.
   */
  suspend fun taskDetail(task: TaskItem): TaskDetail

  /** Moves the task to its list's closed status (closed → done → name "complete|done|closed"). */
  suspend fun completeTask(taskId: String, listId: String)

  /** PUT /task/{id} {"status": status}. */
  suspend fun setStatus(taskId: String, status: String)
}
