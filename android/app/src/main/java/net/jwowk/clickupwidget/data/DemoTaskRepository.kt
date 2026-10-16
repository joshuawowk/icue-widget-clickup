package net.jwowk.clickupwidget.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.jwowk.clickupwidget.data.model.AccountInfo
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskDetail
import net.jwowk.clickupwidget.data.model.TaskFetch
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace
import java.time.Instant
import java.time.ZoneId

/**
 * In-memory sample workspace for demo mode, the widget picker preview and screenshots.
 * Due dates are relative to "now" so chips always show Overdue/Today/Tomorrow/dates.
 */
class DemoTaskRepository(nowMs: Long) : TaskRepository {
  constructor() : this(System.currentTimeMillis())

  private val mutex = Mutex()
  private val tasks: MutableList<TaskItem> = sampleTasks(nowMs).toMutableList()
  private val subtasks: MutableMap<String, MutableList<TaskItem>> =
    sampleSubtasks(tasks).mapValues { it.value.toMutableList() }.toMutableMap()

  override suspend fun verifyToken(token: String): AccountInfo =
    AccountInfo(0L, "Demo User", "demo@example.com", WORKSPACES)

  override suspend fun workspaces(): List<Workspace> = WORKSPACES

  override suspend fun lists(workspaceId: String): List<ListRef> = LISTS

  override suspend fun fetchTasks(config: WidgetConfig): TaskFetch = mutex.withLock {
    val open = tasks.filter { !it.isDone && (!config.isListMode || it.listId == config.listId) }
    TaskFetch(open, SAMPLE_WORKSPACE_NAME, if (config.isListMode) STATUSES else emptyList())
  }

  override suspend fun listStatuses(listId: String): List<TaskStatus> = STATUSES

  override suspend fun taskDetail(task: TaskItem): TaskDetail = mutex.withLock {
    val current = tasks.firstOrNull { it.id == task.id } ?: subtasks.values.flatten().firstOrNull { it.id == task.id } ?: task
    TaskDetail(current, DESCRIPTIONS[task.id].orEmpty(), subtasks[task.id].orEmpty().toList())
  }

  override suspend fun completeTask(taskId: String, listId: String) {
    val closed = TaskLogic.findClosedStatus(STATUSES) ?: error("No closed status on list")
    setStatus(taskId, closed.name)
  }

  override suspend fun setStatus(taskId: String, status: String) {
    val target = STATUSES.firstOrNull { it.name == status }
      ?: STATUSES.firstOrNull { TaskLogic.normStatus(it.name) == TaskLogic.normStatus(status) }
      ?: TaskStatus(status)
    mutex.withLock {
      fun update(t: TaskItem) = t.copy(status = target.name, statusColorHex = target.colorHex, statusType = target.type)
      val i = tasks.indexOfFirst { it.id == taskId }
      if (i >= 0) tasks[i] = update(tasks[i])
      subtasks.values.forEach { list ->
        val j = list.indexOfFirst { it.id == taskId }
        if (j >= 0) list[j] = update(list[j])
      }
    }
  }
  // TODO(data agent): implement
  override suspend fun setPriority(taskId: String, priority: net.jwowk.clickupwidget.data.model.Priority): Unit = TODO()
  override suspend fun createTask(task: net.jwowk.clickupwidget.data.model.NewTask): net.jwowk.clickupwidget.data.model.TaskItem = TODO()

  companion object {
    /** Sample tasks for previews (widget picker / config screen) without touching repository state. */
    fun sampleTasks(nowMs: Long): List<TaskItem> {
      fun due(days: Long?): Long? = days?.let {
        val zone = ZoneId.systemDefault()
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(it).atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
      }
      fun task(
        n: Int, name: String, list: ListRef, days: Long?, priority: Priority, status: TaskStatus, tags: List<String> = emptyList(),
      ) = TaskItem(
        id = "demo-$n",
        name = name,
        listId = list.id,
        listName = list.name,
        dueMs = due(days),
        priority = priority,
        priorityColorHex = PRIORITY_COLORS[priority],
        status = status.name,
        statusColorHex = status.colorHex,
        statusType = status.type,
        tags = tags,
      )
      val (eng, ops, mkt) = LISTS
      return listOf(
        task(1, "Fix login redirect loop", eng, -2, Priority.URGENT, IN_PROGRESS, listOf("bug")),
        task(2, "Review pull request #482", eng, 0, Priority.HIGH, IN_PROGRESS),
        task(3, "Update deployment runbook", ops, 0, Priority.NORMAL, TO_DO, listOf("docs")),
        task(4, "Prepare Q4 launch checklist", mkt, 1, Priority.NORMAL, IN_PROGRESS),
        task(5, "Plan sprint retrospective", eng, 4, Priority.NORMAL, IN_PROGRESS),
        task(6, "Renew monitoring licenses", ops, 10, Priority.HIGH, TO_DO),
        task(7, "Investigate slow dashboard queries", eng, null, Priority.URGENT, REVIEW, listOf("performance")),
        task(8, "Draft newsletter copy", mkt, null, Priority.NONE, TO_DO),
      )
    }

    const val SAMPLE_WORKSPACE_NAME = "Demo workspace"

    private val TO_DO = TaskStatus("to do", "#87909e", "open", 0)
    private val IN_PROGRESS = TaskStatus("in progress", "#4194f6", "custom", 1)
    private val REVIEW = TaskStatus("review", "#a875ff", "custom", 2)
    private val COMPLETE = TaskStatus("complete", "#6bc950", "closed", 3)
    private val STATUSES = listOf(TO_DO, IN_PROGRESS, REVIEW, COMPLETE)

    private val WORKSPACES = listOf(Workspace("demo-workspace", SAMPLE_WORKSPACE_NAME))
    private val LISTS = listOf(
      ListRef("demo-list-engineering", "Engineering", "Demo space"),
      ListRef("demo-list-operations", "Operations", "Demo space"),
      ListRef("demo-list-marketing", "Marketing", "Demo space"),
    )

    private val PRIORITY_COLORS = mapOf(
      Priority.URGENT to "#f50000",
      Priority.HIGH to "#ffcc00",
      Priority.NORMAL to "#6fddff",
      Priority.LOW to "#d8d8d8",
    )

    private val DESCRIPTIONS = mapOf(
      "demo-1" to "Users who sign in with an expired session bounce between /login and /home. Reproduce, find the stale cookie check and add a regression test.",
      "demo-4" to "Everything that has to be ready before the Q4 launch announcement goes out.",
    )

    private fun sampleSubtasks(parents: List<TaskItem>): Map<String, List<TaskItem>> {
      fun sub(parent: TaskItem, n: Int, name: String, status: TaskStatus, days: Long? = null) = TaskItem(
        id = "${parent.id}-s$n",
        name = name,
        listId = parent.listId,
        listName = parent.listName,
        dueMs = days?.let { parent.dueMs?.plus(it * 86_400_000L) },
        status = status.name,
        statusColorHex = status.colorHex,
        statusType = status.type,
        parentId = parent.id,
      )
      val login = parents.first { it.id == "demo-1" }
      val launch = parents.first { it.id == "demo-4" }
      return mapOf(
        login.id to listOf(
          sub(login, 1, "Reproduce with an expired session", COMPLETE),
          sub(login, 2, "Fix cookie check", IN_PROGRESS),
          sub(login, 3, "Add regression test", TO_DO),
        ),
        launch.id to listOf(
          sub(launch, 1, "Finalize announcement copy", COMPLETE),
          sub(launch, 2, "Design social graphics", IN_PROGRESS, days = -1),
          sub(launch, 3, "Schedule email campaign", TO_DO),
          sub(launch, 4, "Brief the support team", COMPLETE),
        ),
      )
    }
  }
}
