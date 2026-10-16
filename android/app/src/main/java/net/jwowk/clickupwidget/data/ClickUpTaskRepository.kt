package net.jwowk.clickupwidget.data

import android.content.Context
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.data.api.ClickUpApi
import net.jwowk.clickupwidget.data.model.AccountInfo
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.TaskDetail
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Live ClickUp v2 API implementation: a thin shell over [ClickUpApi] (OkHttp + kotlinx.serialization). */
@Suppress("UNUSED_PARAMETER")
class ClickUpTaskRepository(context: Context, private val tokenStore: TokenStore) : TaskRepository {
  private val client = OkHttpClient.Builder()
    .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
    .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
    .writeTimeout(TIMEOUT_S, TimeUnit.SECONDS)
    .build()

  private val lock = Any()
  private var current: Pair<String, ClickUpApi>? = null

  /** One [ClickUpApi] per base URL, so its caches survive between calls but follow the debug override. */
  private fun api(): ClickUpApi {
    val base = Graph.configStore.apiBaseUrlOverride() ?: DEFAULT_BASE_URL
    synchronized(lock) {
      current?.takeIf { it.first == base }?.let { return it.second }
      return ClickUpApi(base, tokenStore::token, client).also { current = base to it }
    }
  }

  override suspend fun verifyToken(token: String): AccountInfo = api().verifyToken(token)
  override suspend fun workspaces(): List<Workspace> = api().workspaces()
  override suspend fun lists(workspaceId: String): List<ListRef> = api().lists(workspaceId)
  override suspend fun fetchTasks(config: WidgetConfig): TaskFetch = api().fetchTasks(config)
  override suspend fun listStatuses(listId: String): List<TaskStatus> = api().listStatuses(listId)
  override suspend fun taskDetail(task: TaskItem): TaskDetail = api().taskDetail(task)
  override suspend fun completeTask(taskId: String, listId: String) = api().completeTask(taskId, listId)
  override suspend fun setStatus(taskId: String, status: String) = api().setStatus(taskId, status)

  private companion object {
    const val DEFAULT_BASE_URL = "https://api.clickup.com/api/v2"
    const val TIMEOUT_S = 15L
  }
}
