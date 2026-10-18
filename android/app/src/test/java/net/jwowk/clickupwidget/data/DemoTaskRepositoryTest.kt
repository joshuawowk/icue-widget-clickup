package net.jwowk.clickupwidget.data

import kotlinx.coroutines.test.runTest
import net.jwowk.clickupwidget.data.model.DueFilter
import net.jwowk.clickupwidget.data.model.NewTask
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTaskRepositoryTest {
  private val now = 1_760_000_000_000L

  @Test
  fun sampleTasks_coverTheDueBucketsAndPriorities() {
    val tasks = DemoTaskRepository.sampleTasks(now)
    assertEquals(8, tasks.size)
    val classes = tasks.map { TaskLogic.dueInfo(it.dueMs, now)?.cls }
    assertEquals(1, classes.count { it == DueClass.OVERDUE })
    assertEquals(2, classes.count { it == DueClass.TODAY })
    assertEquals(2, classes.count { it == DueClass.SOON })
    assertEquals(1, classes.count { it == DueClass.LATER })
    assertEquals(2, classes.count { it == null })
    assertEquals(Priority.URGENT, tasks.first { TaskLogic.dueInfo(it.dueMs, now)?.cls == DueClass.OVERDUE }.priority)
    assertEquals(setOf("Engineering", "Operations", "Marketing"), tasks.map { it.listName }.toSet())
  }

  @Test
  fun fetchTasks_defaultFilterShowsSomeInProgress() = runTest {
    val repo = DemoTaskRepository(now)
    val fetch = repo.fetchTasks(WidgetConfig())
    assertEquals(DemoTaskRepository.SAMPLE_WORKSPACE_NAME, fetch.workspaceName)
    assertTrue(fetch.listStatuses.isEmpty())
    val view = TaskLogic.buildView(fetch.tasks, WidgetConfig(dueFilter = DueFilter.ALL), null, null, fetch.listStatuses, now)
    assertTrue(view.total in 2..6)
  }

  @Test
  fun fetchTasks_listModeFiltersByListAndReturnsStatuses() = runTest {
    val repo = DemoTaskRepository(now)
    val list = repo.lists("demo-workspace").first { it.name == "Marketing" }
    val fetch = repo.fetchTasks(WidgetConfig(listId = list.id))
    assertTrue(fetch.tasks.isNotEmpty())
    assertTrue(fetch.tasks.all { it.listId == list.id })
    assertEquals(listOf("to do", "in progress", "review", "complete"), fetch.listStatuses.map { it.name })
  }

  @Test
  fun completeTask_removesFromOpenTasks() = runTest {
    val repo = DemoTaskRepository(now)
    val before = repo.fetchTasks(WidgetConfig()).tasks
    val target = before.first()
    repo.completeTask(target.id, target.listId!!)
    val after = repo.fetchTasks(WidgetConfig()).tasks
    assertEquals(before.size - 1, after.size)
    assertFalse(after.any { it.id == target.id })
  }

  @Test
  fun setStatus_updatesTaskAndSubtasks() = runTest {
    val repo = DemoTaskRepository(now)
    repo.setStatus("demo-3", "review")
    val moved = repo.fetchTasks(WidgetConfig()).tasks.first { it.id == "demo-3" }
    assertEquals("review", moved.status)
    assertEquals("custom", moved.statusType)

    val detail = repo.taskDetail(moved.copy(id = "demo-1"))
    assertEquals(3, detail.subtasks.size)
    val open = detail.subtasks.first { !it.isDone }
    repo.completeTask(open.id, open.listId!!)
    val again = repo.taskDetail(detail.task)
    assertEquals(2, again.subtasks.count { it.isDone })
    assertNotNull(again.subtasks.firstOrNull { it.id == open.id && it.isDone })
  }

  @Test
  fun verifyToken_returnsDemoAccount() = runTest {
    val info = DemoTaskRepository(now).verifyToken("pk_anything")
    assertEquals(1, info.workspaces.size)
    assertEquals(DemoTaskRepository.SAMPLE_WORKSPACE_NAME, info.workspaces.first().name)
  }

  @Test
  fun setPriority_updatesTaskAndColor() = runTest {
    val repo = DemoTaskRepository(now)
    repo.setPriority("demo-3", Priority.URGENT)
    val urgent = repo.fetchTasks(WidgetConfig()).tasks.first { it.id == "demo-3" }
    assertEquals(Priority.URGENT, urgent.priority)
    assertEquals("#f50000", urgent.priorityColorHex)
    repo.setPriority("demo-3", Priority.NONE)
    val none = repo.fetchTasks(WidgetConfig()).tasks.first { it.id == "demo-3" }
    assertEquals(Priority.NONE, none.priority)
    assertEquals(null, none.priorityColorHex)
    repo.setPriority("demo-1-s2", Priority.LOW)
    assertEquals(Priority.LOW, repo.taskDetail(urgent.copy(id = "demo-1")).subtasks.first { it.id == "demo-1-s2" }.priority)
  }

  @Test
  fun createTask_appearsInMyTasksWhenAssignedToMe() = runTest {
    val repo = DemoTaskRepository(now)
    val before = repo.fetchTasks(WidgetConfig()).tasks.size
    val created = repo.createTask(NewTask(listId = "demo-list-marketing", name = " Book venue ", priority = Priority.HIGH, dueMs = now))
    assertEquals("Book venue", created.name)
    assertEquals("Marketing", created.listName)
    assertEquals(Priority.HIGH, created.priority)
    assertEquals("#ffcc00", created.priorityColorHex)
    assertEquals(before + 1, repo.fetchTasks(WidgetConfig()).tasks.size)
    assertTrue(repo.fetchTasks(WidgetConfig(listId = "demo-list-marketing")).tasks.any { it.id == created.id })
    val second = repo.createTask(NewTask(listId = "demo-list-marketing", name = "Another"))
    assertTrue(second.id != created.id)
  }

  @Test
  fun createTask_notAssignedToMeOnlyShowsInListMode() = runTest {
    val repo = DemoTaskRepository(now)
    val created = repo.createTask(NewTask(listId = "demo-list-operations", name = "Someone else's", assignToMe = false))
    assertFalse(repo.fetchTasks(WidgetConfig()).tasks.any { it.id == created.id })
    assertTrue(repo.fetchTasks(WidgetConfig(listId = "demo-list-operations")).tasks.any { it.id == created.id })
  }

  @Test
  fun createTask_blankNameRejected() = runTest {
    try {
      DemoTaskRepository(now).createTask(NewTask(listId = "demo-list-operations", name = " "))
      org.junit.Assert.fail("expected exception")
    } catch (_: IllegalArgumentException) {
    }
  }

  @Test
  fun demoTasks_haveTagColorsAndPopulatedFields() = runTest {
    val repo = DemoTaskRepository(now)
    val tasks = DemoTaskRepository.sampleTasks(now)
    val tagged = tasks.filter { it.tags.isNotEmpty() }
    assertTrue(tagged.size >= 3)
    assertTrue(tagged.all { t -> t.tags.all { it in t.tagColors } })
    val detail = repo.taskDetail(tasks.first { it.id == "demo-1" })
    assertTrue(detail.fields.any { it.name == "Requester" && it.colorHex != null })
    assertTrue(detail.fields.any { it.name == "Incident No." })
    assertTrue(repo.taskDetail(tasks.first { it.id == "demo-8" }).fields.isEmpty())
    assertTrue(tasks.count { t -> repo.taskDetail(t).fields.isNotEmpty() } in 2..4)
  }

  @Test
  fun demoRequesters_matchTheDetailSheet() = runTest {
    val repo = DemoTaskRepository(now)
    val withRequester = DemoTaskRepository.sampleTasks(now).filter { it.requester != null }
    assertTrue(withRequester.size >= 2)
    withRequester.forEach { t ->
      val field = repo.taskDetail(t).fields.single { it.name == "Requester" }
      assertEquals(field.value, t.requester)
      assertEquals(field.colorHex, t.requesterColorHex)
    }
  }
}
