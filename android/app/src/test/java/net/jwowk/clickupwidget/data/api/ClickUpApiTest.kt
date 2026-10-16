package net.jwowk.clickupwidget.data.api

import kotlinx.coroutines.test.runTest
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.NewTask
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskField
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.WidgetConfig
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ClickUpApiTest {
  private lateinit var server: MockWebServer
  private var token: String? = "pk_test"
  private val routes = mutableMapOf<String, MockResponse>()
  private val seen = mutableListOf<RecordedRequest>()

  private fun api() = ClickUpApi(server.url("/api/v2").toString().trimEnd('/'), { token }, OkHttpClient())

  /** Route key is "METHOD /path" (no query). Unrouted requests get a 404. */
  private fun route(key: String, body: String, code: Int = 200) {
    routes[key] = MockResponse().setResponseCode(code).setBody(body)
  }

  @Before
  fun setUp() {
    server = MockWebServer()
    server.dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        seen += request
        val key = "${request.method} ${request.requestUrl!!.encodedPath.removePrefix("/api/v2")}"
        val page = request.requestUrl!!.queryParameter("page")
        return page?.let { routes["$key#page=$it"] } ?: routes[key] ?: MockResponse().setResponseCode(404)
      }
    }
    server.start()
    route("GET /user", """{"user":{"id":183,"username":"josh","email":"j@example.com"}}""")
    route("GET /team", """{"teams":[{"id":"9001","name":"Acme"},{"id":"9002","name":"Other"}]}""")
  }

  @After
  fun tearDown() = server.shutdown()

  private fun requests(path: String) = seen.filter { it.requestUrl!!.encodedPath.endsWith(path) }

  @Test
  fun taskParsing_handlesNullsAndNumericFields() = runTest {
    route(
      "GET /team/9001/task",
      """
      {"tasks":[
        {"id":"abc","name":"Full","status":{"status":"in progress","color":"#4194f6","type":"custom"},
         "due_date":"1760000000000","priority":{"priority":"urgent","color":"#f50000"},
         "list":{"id":"55","name":"Eng"},"tags":[{"name":"bug"},{"name":"x"}],"url":"https://app.clickup.com/t/abc","parent":null},
        {"id":123,"name":"Sparse","status":{"status":"to do"},"due_date":null,"priority":null,"list":null,"tags":[]},
        {"id":"num","name":"Numeric due","due_date":1760000000000,"unknown":{"a":1},"parent":456}
      ]}
      """.trimIndent(),
    )
    val tasks = api().fetchTasks(WidgetConfig()).tasks
    assertEquals(3, tasks.size)
    val full = tasks[0]
    assertEquals(1_760_000_000_000L, full.dueMs)
    assertEquals(Priority.URGENT, full.priority)
    assertEquals("#f50000", full.priorityColorHex)
    assertEquals("55", full.listId)
    assertEquals("Eng", full.listName)
    assertEquals(listOf("bug", "x"), full.tags)
    assertEquals("in progress", full.status)
    assertEquals("custom", full.statusType)
    assertEquals("https://app.clickup.com/t/abc", full.url)
    assertNull(full.parentId)

    val sparse = tasks[1]
    assertEquals("123", sparse.id)
    assertNull(sparse.dueMs)
    assertEquals(Priority.NONE, sparse.priority)
    assertNull(sparse.priorityColorHex)
    assertNull(sparse.listId)
    assertEquals("", sparse.listName)
    assertEquals("#888888", sparse.statusColorHex)

    assertEquals(1_760_000_000_000L, tasks[2].dueMs)
    assertEquals("456", tasks[2].parentId)
    assertEquals("", tasks[2].status)
  }

  @Test
  fun myTasks_usesIdentityAndQueryParams() = runTest {
    route("GET /team/9001/task", """{"tasks":[]}""")
    val fetch = api().fetchTasks(WidgetConfig())
    assertEquals("Acme", fetch.workspaceName)
    assertTrue(fetch.listStatuses.isEmpty())
    val req = requests("/team/9001/task").single()
    assertEquals("pk_test", req.getHeader("Authorization"))
    val url = req.requestUrl!!
    assertEquals("183", url.queryParameter("assignees[]"))
    assertEquals("false", url.queryParameter("include_closed"))
    assertEquals("false", url.queryParameter("subtasks"))
    assertEquals("due_date", url.queryParameter("order_by"))
  }

  @Test
  fun myTasks_honoursConfiguredWorkspace() = runTest {
    route("GET /team/9002/task", """{"tasks":[]}""")
    val fetch = api().fetchTasks(WidgetConfig(workspaceId = "9002"))
    assertEquals("Other", fetch.workspaceName)
    assertEquals(1, requests("/team/9002/task").size)
  }

  @Test
  fun listMode_returnsStatusesInListOrderAndCachesThem() = runTest {
    route("GET /list/77/task", """{"tasks":[{"id":"t1","name":"A","status":{"status":"review","type":"custom"}}]}""")
    route(
      "GET /list/77",
      """
      {"id":"77","name":"Eng","statuses":[
        {"status":"complete","color":"#6bc950","type":"closed","orderindex":3},
        {"status":"to do","color":"#87909e","type":"open","orderindex":"0"},
        {"status":"review","color":"#a875ff","type":"custom","orderindex":2}
      ]}
      """.trimIndent(),
    )
    val api = api()
    val fetch = api.fetchTasks(WidgetConfig(listId = "77"))
    assertEquals(listOf("to do", "review", "complete"), fetch.listStatuses.map { it.name })
    assertEquals(listOf(0, 2, 3), fetch.listStatuses.map { it.orderIndex })
    assertEquals("Acme", fetch.workspaceName)
    assertEquals("false", requests("/list/77/task").single().requestUrl!!.queryParameter("include_closed"))

    api.listStatuses("77")
    api.fetchTasks(WidgetConfig(listId = "77"))
    assertEquals(1, requests("/list/77").size)
  }

  @Test
  fun unauthorized_mapsTo401WithMessage() = runTest {
    route("GET /user", """{"err":"Token invalid"}""", code = 401)
    try {
      api().verifyToken("bad")
      fail("expected exception")
    } catch (e: ClickUpApiException) {
      assertEquals(401, e.code)
      assertEquals("Invalid API token", e.message)
    }
  }

  @Test
  fun otherErrors_mapToApiExceptionWithCode() = runTest {
    route("GET /team/9001/task", "oops", code = 503)
    try {
      api().fetchTasks(WidgetConfig())
      fail("expected exception")
    } catch (e: ClickUpApiException) {
      assertEquals(503, e.code)
    }
  }

  @Test
  fun noToken_throwsWithoutNetwork() = runTest {
    token = null
    try {
      api().fetchTasks(WidgetConfig())
      fail("expected exception")
    } catch (_: NoTokenException) {
    }
    assertTrue(seen.isEmpty())
  }

  @Test
  fun verifyToken_usesGivenTokenAndDoesNotPoisonCache() = runTest {
    route("GET /team/9001/task", """{"tasks":[]}""")
    val api = api()
    val info = api.verifyToken("pk_other")
    assertEquals(183L, info.userId)
    assertEquals("josh", info.username)
    assertEquals(listOf("Acme", "Other"), info.workspaces.map { it.name })
    assertEquals(setOf("pk_other"), seen.map { it.getHeader("Authorization") }.toSet())

    seen.clear()
    api.fetchTasks(WidgetConfig())
    assertEquals(setOf("pk_test"), seen.map { it.getHeader("Authorization") }.toSet())
    assertEquals(1, requests("/user").size)
  }

  @Test
  fun tokenChange_dropsIdentityCache() = runTest {
    route("GET /team/9001/task", """{"tasks":[]}""")
    val api = api()
    api.fetchTasks(WidgetConfig())
    api.fetchTasks(WidgetConfig())
    assertEquals(1, requests("/user").size)
    token = "pk_second"
    api.fetchTasks(WidgetConfig())
    assertEquals(2, requests("/user").size)
  }

  @Test
  fun lists_combineFoldersAndFolderlessLists() = runTest {
    route("GET /team/9001/space", """{"spaces":[{"id":"s1","name":"Dev"}]}""")
    route("GET /space/s1/folder", """{"folders":[{"id":"f1","name":"Sprint","lists":[{"id":"l1","name":"Backlog"}]}]}""")
    route("GET /space/s1/list", """{"lists":[{"id":"l2","name":"Inbox"}]}""")
    val lists = api().lists("9001")
    assertEquals(listOf("l1", "l2"), lists.map { it.id })
    assertEquals(listOf("Dev / Sprint", "Dev"), lists.map { it.path })
    assertEquals("false", requests("/team/9001/space").single().requestUrl!!.queryParameter("archived"))
  }

  private val parent = TaskItem(id = "p1", name = "Parent", listId = "77", listName = "Eng")

  @Test
  fun taskDetail_primaryProvidesSubtasksAndInheritsList() = runTest {
    route(
      "GET /task/p1",
      """
      {"id":"p1","name":"Parent","text_content":"  Hello\nworld  ","description":"ignored",
       "status":{"status":"to do","type":"open"},"list":{"id":"77","name":"Eng"},
       "subtasks":[{"id":"s1","name":"Sub","parent":"p1","list":null,"status":{"status":"complete","type":"closed"}},
                   {"id":"s2","name":"Not direct","parent":"other"}]}
      """.trimIndent(),
    )
    val detail = api().taskDetail(parent)
    assertEquals("Hello\nworld", detail.description)
    assertEquals(listOf("s1"), detail.subtasks.map { it.id })
    assertEquals("77", detail.subtasks.single().listId)
    assertEquals("Eng", detail.subtasks.single().listName)
    assertTrue(detail.subtasks.single().isDone)
    assertEquals("true", requests("/task/p1").single().requestUrl!!.queryParameter("include_subtasks"))
    assertTrue(requests("/team/9001/task").isEmpty())
  }

  @Test
  fun taskDetail_fallsBackToParentQueryWhenPrimaryHasNoSubtasks() = runTest {
    route("GET /task/p1", """{"id":"p1","name":"Parent","description":"Only description","status":{"status":"to do"}}""")
    route(
      "GET /team/9001/task",
      """
      {"tasks":[{"id":"c1","name":"Child","parent":"p1","status":{"status":"to do","type":"open"}},
                {"id":"g1","name":"Grandchild","parent":"c1"}]}
      """.trimIndent(),
    )
    val detail = api().taskDetail(parent)
    assertEquals("Only description", detail.description)
    assertEquals(listOf("c1"), detail.subtasks.map { it.id })
    assertEquals("77", detail.subtasks.single().listId)
    val url = requests("/team/9001/task").single().requestUrl!!
    assertEquals("p1", url.queryParameter("parent"))
    assertEquals("true", url.queryParameter("subtasks"))
    assertEquals("true", url.queryParameter("include_closed"))
  }

  @Test
  fun taskDetail_longDescriptionIsCapped() = runTest {
    route("GET /task/p1", """{"id":"p1","text_content":"${"x".repeat(5000)}"}""")
    route("GET /team/9001/task", """{"tasks":[]}""")
    assertEquals(4000, api().taskDetail(parent).description.length)
  }

  @Test
  fun taskDetail_primaryFailureStillReturnsFallbackSubtasks() = runTest {
    route("GET /task/p1", "boom", code = 500)
    route("GET /team/9001/task", """{"tasks":[{"id":"c1","name":"Child","parent":"p1"}]}""")
    val detail = api().taskDetail(parent)
    assertEquals("", detail.description)
    assertEquals(listOf("c1"), detail.subtasks.map { it.id })
  }

  @Test
  fun setStatus_putsJsonBody() = runTest {
    route("PUT /task/t1", """{"id":"t1"}""")
    api().setStatus("t1", "in progress")
    val req = requests("/task/t1").single()
    assertEquals("PUT", req.method)
    assertEquals("pk_test", req.getHeader("Authorization"))
    assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
    assertEquals("""{"status":"in progress"}""", req.body.readUtf8())
  }

  @Test
  fun completeTask_picksClosedStatus() = runTest {
    route(
      "GET /list/77",
      """{"id":"77","statuses":[{"status":"to do","type":"open","orderindex":0},{"status":"shipped","type":"closed","orderindex":1}]}""",
    )
    route("PUT /task/t1", "{}")
    api().completeTask("t1", "77")
    assertEquals("""{"status":"shipped"}""", requests("/task/t1").single().body.readUtf8())
  }

  @Test
  fun completeTask_withoutClosedStatusThrows() = runTest {
    route("GET /list/77", """{"id":"77","statuses":[{"status":"to do","type":"open","orderindex":0}]}""")
    try {
      api().completeTask("t1", "77")
      fail("expected exception")
    } catch (e: IllegalStateException) {
      assertEquals("No closed status on list", e.message)
    }
    assertTrue(requests("/task/t1").isEmpty())
  }

  @Test
  fun listMode401_withValidToken_isNoAccess403() = runTest {
    route("GET /list/77/task", """{"err":"Team not authorized","ECODE":"OAUTH_027"}""", code = 401)
    try {
      api().fetchTasks(WidgetConfig(listId = "77"))
      fail("expected ClickUpApiException")
    } catch (e: ClickUpApiException) {
      assertEquals(403, e.code)
    }
  }

  @Test
  fun listMode401_withRejectedToken_staysInvalidToken401() = runTest {
    route("GET /list/77/task", """{"err":"Token invalid","ECODE":"OAUTH_025"}""", code = 401)
    route("GET /user", """{"err":"Token invalid","ECODE":"OAUTH_025"}""", code = 401)
    try {
      api().fetchTasks(WidgetConfig(listId = "77"))
      fail("expected ClickUpApiException")
    } catch (e: ClickUpApiException) {
      assertEquals(401, e.code)
    }
  }

  @Test
  fun myTasks_readsEveryPage() = runTest {
    fun page(ids: IntRange, last: Boolean) =
      """{"tasks":[${ids.joinToString(",") { """{"id":"t$it","name":"Task $it","list":{"id":"1","name":"L"}}""" }}],"last_page":$last}"""
    route("GET /team/9001/task#page=0", page(1..100, last = false))
    route("GET /team/9001/task#page=1", page(101..103, last = true))
    route("GET /team/9001/task#page=2", page(900..901, last = true)) // must never be requested
    val tasks = api().fetchTasks(WidgetConfig()).tasks
    assertEquals(103, tasks.size)
    assertEquals(listOf("0", "1"), requests("/team/9001/task").map { it.requestUrl!!.queryParameter("page") })
  }

  @Test
  fun tagColors_parsedLeniently() = runTest {
    route(
      "GET /team/9001/task",
      """
      {"tasks":[{"id":"a","name":"A","tags":[
        {"name":"meeting","tag_fg":"#cecece","tag_bg":"#0f5096"},
        {"name":"nocolor"},
        {"name":"blank","tag_bg":""},
        {"name":"nullbg","tag_bg":null},
        {"tag_bg":"#123456"}]},
        {"id":"b","name":"B","tags":null}]}
      """.trimIndent(),
    )
    val (a, b) = api().fetchTasks(WidgetConfig()).tasks
    assertEquals(listOf("meeting", "nocolor", "blank", "nullbg"), a.tags)
    assertEquals(mapOf("meeting" to "#0f5096"), a.tagColors)
    assertTrue(b.tags.isEmpty() && b.tagColors.isEmpty())
  }

  @Test
  fun taskDetail_includesFormattedFields() = runTest {
    route(
      "GET /task/p1",
      """
      {"id":"p1","name":"Parent","assignees":[{"username":"Josh"}],"time_estimate":5400000,
       "custom_fields":[
         {"type":"drop_down","name":"Requester","value":1,"type_config":{"options":[
           {"id":"o0","name":"Ann","color":"#111111","orderindex":0},{"id":"o1","name":"Bob","color":"#222222","orderindex":1}]}},
         {"type":"short_text","name":"Incident No.","value":"INC1"},
         {"type":"short_text","name":"Empty","value":null}]}
      """.trimIndent(),
    )
    route("GET /team/9001/task", """{"tasks":[]}""")
    val fields = api().taskDetail(parent).fields
    assertEquals(
      listOf(
        TaskField("Assignees", "Josh"),
        TaskField("Time estimate", "1h 30m"),
        TaskField("Requester", "Bob", "#222222"),
        TaskField("Incident No.", "INC1"),
      ),
      fields,
    )
  }

  @Test
  fun taskDetail_fieldsEmptyWhenOnlyFallbackRan() = runTest {
    route("GET /task/p1", "boom", code = 500)
    route("GET /team/9001/task", """{"tasks":[{"id":"c1","name":"Child","parent":"p1"}]}""")
    assertTrue(api().taskDetail(parent).fields.isEmpty())
  }

  @Test
  fun setPriority_putsApiValueOrNull() = runTest {
    route("PUT /task/t1", "{}")
    val api = api()
    api.setPriority("t1", Priority.URGENT)
    api.setPriority("t1", Priority.LOW)
    api.setPriority("t1", Priority.NONE)
    val reqs = requests("/task/t1")
    assertEquals(listOf("PUT", "PUT", "PUT"), reqs.map { it.method })
    assertEquals("pk_test", reqs[0].getHeader("Authorization"))
    assertTrue(reqs[0].getHeader("Content-Type")!!.startsWith("application/json"))
    assertEquals(
      listOf("""{"priority":1}""", """{"priority":4}""", """{"priority":null}"""),
      reqs.map { it.body.readUtf8() },
    )
  }

  @Test
  fun setPriority_mapsErrors() = runTest {
    route("PUT /task/t1", "{}", code = 403)
    try {
      api().setPriority("t1", Priority.HIGH)
      fail("expected exception")
    } catch (e: ClickUpApiException) {
      assertEquals(403, e.code)
    }
    token = null
    try {
      api().setPriority("t1", Priority.HIGH)
      fail("expected exception")
    } catch (_: NoTokenException) {
    }
  }

  @Test
  fun createTask_sendsFullBodyAndShapesResponse() = runTest {
    route(
      "POST /list/77/task",
      """
      {"id":"new1","name":"Write docs","status":{"status":"to do","color":"#87909e","type":"open"},
       "priority":{"priority":"high","color":"#ffcc00"},"due_date":"1792108800000",
       "list":{"id":"77","name":"Eng"},"tags":[],"url":"https://app.clickup.com/t/new1","team_id":"9001"}
      """.trimIndent(),
    )
    val created = api().createTask(
      NewTask(listId = "77", name = "  Write docs ", description = "Details", dueMs = 1_792_108_800_000L, priority = Priority.HIGH),
    )
    val req = requests("/list/77/task").single()
    assertEquals("POST", req.method)
    assertEquals("pk_test", req.getHeader("Authorization"))
    assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
    assertEquals(
      """{"name":"Write docs","description":"Details","priority":2,"due_date":1792108800000,"due_date_time":false,"assignees":[183]}""",
      req.body.readUtf8(),
    )
    assertEquals("new1", created.id)
    assertEquals("Write docs", created.name)
    assertEquals("77", created.listId)
    assertEquals("Eng", created.listName)
    assertEquals(Priority.HIGH, created.priority)
    assertEquals("to do", created.status)
    assertEquals(1_792_108_800_000L, created.dueMs)
    assertEquals("9001", created.teamId)
  }

  @Test
  fun createTask_minimalBodyOmitsOptionalKeys() = runTest {
    route("POST /list/77/task", """{"id":"new2","name":"Bare"}""")
    val created = api().createTask(NewTask(listId = "77", name = "Bare", assignToMe = false))
    assertEquals("""{"name":"Bare"}""", requests("/list/77/task").single().body.readUtf8())
    assertTrue(requests("/user").isEmpty())
    assertEquals("new2", created.id)
    assertNull(created.listId)
    assertEquals(Priority.NONE, created.priority)
  }

  @Test
  fun createTask_blankNameFailsBeforeAnyRequest() = runTest {
    try {
      api().createTask(NewTask(listId = "77", name = "   "))
      fail("expected exception")
    } catch (_: IllegalArgumentException) {
    }
    assertTrue(seen.isEmpty())
  }

  @Test
  fun createTask_errorsAndBadResponses() = runTest {
    route("POST /list/77/task", "{}", code = 400)
    try {
      api().createTask(NewTask(listId = "77", name = "X", assignToMe = false))
      fail("expected exception")
    } catch (e: ClickUpApiException) {
      assertEquals(400, e.code)
    }
    route("POST /list/77/task", """{"err":"no id"}""")
    try {
      api().createTask(NewTask(listId = "77", name = "X", assignToMe = false))
      fail("expected exception")
    } catch (_: java.io.IOException) {
    }
  }
}
