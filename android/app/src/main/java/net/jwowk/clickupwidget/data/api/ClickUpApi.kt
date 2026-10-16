package net.jwowk.clickupwidget.data.api

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.AccountInfo
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskDetail
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/**
 * ClickUp API v2 client. Android-free so it can be unit-tested against MockWebServer.
 * Every call runs on Dispatchers.IO. Only the first page of any task list is read (iCUE parity).
 *
 * In-memory caches (account identity, list statuses) are dropped whenever [tokenProvider]
 * returns a different token than on the previous call.
 */
class ClickUpApi(
  private val baseUrl: String,
  private val tokenProvider: () -> String?,
  private val client: OkHttpClient,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private class Identity(val userId: Long, val username: String, val email: String, val teams: List<Workspace>)

  private class CachedStatuses(val statuses: List<TaskStatus>, val loadedAt: Long)

  private val statusCache = ConcurrentHashMap<String, CachedStatuses>()
  @Volatile private var identity: Identity? = null
  @Volatile private var cachedFor: String? = null

  /** Verifies [token] (not the stored one) via /user + /team. Nothing is cached. */
  suspend fun verifyToken(token: String): AccountInfo {
    val id = loadIdentity(token)
    return AccountInfo(id.userId, id.username, id.email, id.teams)
  }

  suspend fun workspaces(): List<Workspace> = identity().teams

  /** All lists in [workspaceId]; [ListRef.path] is "Space / Folder" or "Space". */
  suspend fun lists(workspaceId: String): List<ListRef> {
    val spaces = getJson(path("team", workspaceId, "space") { addQueryParameter("archived", "false") })
      .array("spaces").mapNotNull { s ->
        val o = s.asObject() ?: return@mapNotNull null
        o.str("id")?.let { it to o.str("name").orEmpty() }
      }
    return coroutineScope {
      spaces.map { (spaceId, spaceName) ->
        async {
          val folders = getJson(path("space", spaceId, "folder") { addQueryParameter("archived", "false") })
          val loose = getJson(path("space", spaceId, "list") { addQueryParameter("archived", "false") })
          buildList {
            folders.array("folders").forEach { f ->
              val folder = f.asObject() ?: return@forEach
              val folderName = folder.str("name").orEmpty()
              folder.array("lists").forEach { l -> listRef(l, "$spaceName / $folderName")?.let(::add) }
            }
            loose.array("lists").forEach { l -> listRef(l, spaceName)?.let(::add) }
          }
        }
      }.awaitAll().flatten()
    }
  }

  /** Open tasks per [config] (list mode or My Tasks mode); unfiltered and unsorted. */
  suspend fun fetchTasks(config: WidgetConfig): TaskFetch {
    if (config.isListMode) {
      val tasks = getJson(path("list", config.listId, "task") {
        addQueryParameter("include_closed", "false")
        addQueryParameter("subtasks", "false")
      }).array("tasks").mapNotNull { shapeTask(it) }
      val statuses = bestEffort { listStatuses(config.listId) }.orEmpty()
      val teams = bestEffort { identity().teams }.orEmpty()
      return TaskFetch(tasks, workspaceName(teams, config.workspaceId).orEmpty(), statuses)
    }
    val id = identity()
    val workspaceId = config.workspaceId ?: id.teams.firstOrNull()?.id ?: throw IllegalStateException("No workspace")
    val tasks = getJson(path("team", workspaceId, "task") {
      addEncodedQueryParameter("assignees[]", id.userId.toString())
      addQueryParameter("include_closed", "false")
      addQueryParameter("subtasks", "false")
      addQueryParameter("order_by", "due_date")
    }).array("tasks").mapNotNull { shapeTask(it) }
    return TaskFetch(tasks, workspaceName(id.teams, workspaceId).orEmpty(), emptyList())
  }

  /** Statuses of a list in configured order; cached per list id for [STATUS_TTL_MS]. */
  suspend fun listStatuses(listId: String): List<TaskStatus> {
    checkToken()
    statusCache[listId]?.let { if (clock() - it.loadedAt < STATUS_TTL_MS) return it.statuses }
    val statuses = getJson(path("list", listId))
      .array("statuses").mapNotNull { s ->
        val o = s.asObject() ?: return@mapNotNull null
        val name = o.str("status") ?: return@mapNotNull null
        TaskStatus(
          name = name,
          colorHex = o.str("color") ?: "#888888",
          type = o.str("type").orEmpty(),
          orderIndex = o.long("orderindex")?.toInt() ?: 0,
        )
      }
      .sortedBy { it.orderIndex }
    statusCache[listId] = CachedStatuses(statuses, clock())
    return statuses
  }

  /**
   * Description + direct subtasks. Primary endpoint first; if it yields no subtasks, the team-level
   * `parent` query. Throws only when the primary failed AND the fallback also failed.
   */
  suspend fun taskDetail(task: TaskItem): TaskDetail {
    var description = ""
    var fresh: TaskItem? = null
    var subs: List<TaskItem> = emptyList()
    var primaryError: Exception? = null
    try {
      val full = getJson(path("task", task.id) { addQueryParameter("include_subtasks", "true") })
      val obj = full.asObject()
      if (obj != null) {
        description = (obj.str("text_content")?.takeIf { it.isNotBlank() } ?: obj.str("description").orEmpty())
          .trim().take(MAX_DESCRIPTION)
        fresh = shapeTask(obj, task)
        subs = full.array("subtasks").mapNotNull { shapeTask(it, task) }
          .filter { it.parentId == null || it.parentId == task.id }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      primaryError = e
    }
    if (subs.isEmpty()) {
      try {
        subs = fetchSubtasksViaParent(task)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        if (primaryError != null) throw primaryError
      }
    }
    return TaskDetail(fresh ?: task, description, subs)
  }

  /** Moves the task to its list's closed status. */
  suspend fun completeTask(taskId: String, listId: String) {
    val closed = TaskLogic.findClosedStatus(listStatuses(listId))
      ?: throw IllegalStateException("No closed status on list")
    setStatus(taskId, closed.name, listId)
  }

  /** On [ClickUpApiException] the cached statuses ([listId]'s, or all if unknown) are dropped, so a renamed status recovers. */
  suspend fun setStatus(taskId: String, status: String, listId: String? = null) {
    val token = checkToken()
    val body = buildJsonObject { put("status", status) }.toString().toRequestBody(JSON_MEDIA)
    try {
      execute(authed(path("task", taskId), token).put(body).build()) { }
    } catch (e: ClickUpApiException) {
      if (listId != null) statusCache.remove(listId) else statusCache.clear()
      throw e
    }
  }

  private suspend fun fetchSubtasksViaParent(parent: TaskItem): List<TaskItem> {
    val teamId = parent.teamId ?: identity().teams.firstOrNull()?.id ?: return emptyList()
    return getJson(path("team", teamId, "task") {
      addQueryParameter("parent", parent.id)
      addQueryParameter("subtasks", "true")
      addQueryParameter("include_closed", "true")
    }).array("tasks").mapNotNull { shapeTask(it, parent) }.filter { it.parentId == parent.id }
  }

  // ---- identity / token ----

  /** The stored token; drops the caches if it changed since the last call. */
  private fun checkToken(): String {
    val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: throw NoTokenException()
    if (token != cachedFor) {
      synchronized(this) {
        if (token != cachedFor) {
          statusCache.clear()
          identity = null
          cachedFor = token
        }
      }
    }
    return token
  }

  private suspend fun identity(): Identity {
    val token = checkToken()
    identity?.let { return it }
    val loaded = loadIdentity(token)
    // Keep it only if the stored token did not change while loading.
    if (token == cachedFor) identity = loaded
    return loaded
  }

  private suspend fun loadIdentity(token: String): Identity {
    val user = getJson(path("user"), token).asObject()?.get("user").asObject()
    val teams = getJson(path("team"), token).array("teams").mapNotNull { t ->
      val o = t.asObject() ?: return@mapNotNull null
      o.str("id")?.let { Workspace(it, o.str("name").orEmpty()) }
    }
    return Identity(
      userId = user?.long("id") ?: 0L,
      username = user?.str("username").orEmpty(),
      email = user?.str("email").orEmpty(),
      teams = teams,
    )
  }

  private fun workspaceName(teams: List<Workspace>, workspaceId: String?): String? =
    (if (workspaceId == null) teams.firstOrNull() else teams.firstOrNull { it.id == workspaceId })?.name

  private inline fun <T> bestEffort(block: () -> T): T? = try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }

  // ---- HTTP ----

  private fun path(vararg segments: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
    baseUrl.toHttpUrl().newBuilder().apply { segments.forEach { addPathSegment(it) } }.apply(query).build()

  private fun authed(url: HttpUrl, token: String): Request.Builder =
    Request.Builder().url(url).header("Authorization", token).header("Accept", "application/json")

  /** GET [url] with [token] (default: the stored token) and parse the body as JSON. */
  private suspend fun getJson(url: HttpUrl, token: String? = null): JsonElement {
    val t = token ?: checkToken()
    return execute(authed(url, t).get().build()) { body -> JSON.parseToJsonElement(body) }
  }

  private suspend fun <T> execute(request: Request, parse: (String) -> T): T = withContext(Dispatchers.IO) {
    client.newCall(request).execute().use { response ->
      if (response.code == 401) throw ClickUpApiException(401, "Invalid API token")
      if (!response.isSuccessful) throw ClickUpApiException(response.code)
      parse(response.body?.string().orEmpty())
    }
  }

  private fun listRef(e: JsonElement, path: String): ListRef? {
    val o = e.asObject() ?: return null
    val id = o.str("id") ?: return null
    return ListRef(id, o.str("name").orEmpty(), path)
  }

  companion object {
    private const val MAX_DESCRIPTION = 4000
    private const val STATUS_TTL_MS = 10 * 60 * 1000L
    private const val UNTITLED = "(untitled)"
    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * ClickUp task JSON → [TaskItem], leniently. Subtasks carry `list: null`; when [inherit] is given,
     * its listId/listName/teamId fill the gap. Returns null for objects without an id.
     */
    internal fun shapeTask(e: JsonElement, inherit: TaskItem? = null): TaskItem? {
      val o = e.asObject() ?: return null
      val id = o.str("id") ?: return null
      val list = o["list"].asObject()
      val status = o["status"].asObject()
      val priority = o["priority"].asObject()
      return TaskItem(
        id = id,
        name = o.str("name")?.takeIf { it.isNotBlank() } ?: UNTITLED,
        listId = list?.str("id") ?: inherit?.listId,
        listName = list?.str("name") ?: inherit?.listName.orEmpty(),
        dueMs = o.long("due_date")?.takeIf { it > 0 },
        priority = Priority.fromKey(priority?.str("priority")),
        priorityColorHex = priority?.str("color"),
        status = status?.str("status").orEmpty(),
        statusColorHex = status?.str("color")?.takeIf { it.isNotBlank() } ?: "#888888",
        statusType = status?.str("type").orEmpty(),
        tags = o.array("tags").mapNotNull { it.asObject()?.str("name") },
        url = o.str("url"),
        parentId = o.str("parent"),
        teamId = o.str("team_id") ?: inherit?.teamId,
      )
    }
  }
}
