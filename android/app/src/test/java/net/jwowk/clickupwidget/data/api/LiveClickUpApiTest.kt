package net.jwowk.clickupwidget.data.api

import kotlinx.coroutines.runBlocking
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.WidgetConfig
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Read-only smoke test against the real ClickUp API. Skipped unless CLICKUP_TOKEN is set:
 *   CLICKUP_TOKEN=pk_... ./gradlew testDebugUnitTest --tests '*LiveClickUpApiTest*' -i
 * Only GETs — never completes or changes anything.
 */
class LiveClickUpApiTest {
  private val token: String? = System.getenv("CLICKUP_TOKEN")

  @Test
  fun readOnlyTour() = runBlocking {
    assumeTrue("CLICKUP_TOKEN not set", !token.isNullOrBlank())
    val api = ClickUpApi("https://api.clickup.com/api/v2", { token }, OkHttpClient())

    val account = api.verifyToken(token!!)
    println("LIVE account: user=${account.userId} workspaces=${account.workspaces.size}")
    assertTrue(account.workspaces.isNotEmpty())

    val mine = api.fetchTasks(WidgetConfig())
    val t = mine.tasks
    println(
      "LIVE my tasks: ${t.size} in '${mine.workspaceName}'; due=${t.count { it.dueMs != null }} " +
        "prio=${t.count { it.priority.key != "none" }} list=${t.count { it.listName.isNotBlank() }} " +
        "url=${t.count { it.url != null }} team=${t.count { it.teamId != null }} " +
        "statuses=${t.map { "${it.status}/${it.statusType}" }.distinct()}",
    )
    assertTrue(t.all { it.id.isNotBlank() && it.name.isNotBlank() && it.listId != null })

    val view = TaskLogic.buildView(t, WidgetConfig(), null, null, emptyList(), System.currentTimeMillis())
    println(
      "LIVE default view: visible=${view.visible.size} total=${view.total} scoped=${view.scopedTotal} " +
        "chips=${view.presentStatuses.map { it.name }} prios=${view.presentPriorities.map { it.key }} " +
        "selected=${view.selectedStatuses} constrained=${view.anyConstrained}",
    )

    val lists = api.lists(account.workspaces.first().id)
    println("LIVE lists in first workspace: ${lists.size}")

    t.firstOrNull { it.listId != null }?.let { task ->
      val statuses = api.listStatuses(task.listId!!)
      println("LIVE list ${task.listId} statuses=${statuses.map { "${it.name}/${it.type}/${it.orderIndex}" }}")
      println("LIVE closed status -> ${TaskLogic.findClosedStatus(statuses)?.name}")
      val inList = api.fetchTasks(WidgetConfig(listId = task.listId!!))
      println("LIVE list mode: ${inList.tasks.size} tasks, ${inList.listStatuses.size} statuses, ws='${inList.workspaceName}'")
    }

    // Details for a few tasks; report subtask coverage (the iCUE v1.3.1 fallback path).
    t.take(8).forEach { task ->
      val d = api.taskDetail(task)
      println(
        "LIVE detail ${task.id}: desc=${d.description.length} chars, subtasks=${d.subtasks.size} " +
          "(done=${d.subtasks.count { it.isDone }}, withList=${d.subtasks.count { it.listId != null }})",
      )
    }
  }
}
