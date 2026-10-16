package net.jwowk.clickupwidget.ui.detail

import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailUiStateTest {
  private fun sub(id: String, type: String = "") = TaskItem(id = id, name = id, statusType = type)

  private fun state(vararg subs: TaskItem) =
    DetailUiState(appWidgetId = 1, task = sub("p"), config = WidgetConfig(), nowMs = 0, subtasks = subs.toList())

  private fun msg(key: Long, undo: String? = null) = SnackMessage(key, "m$key", undo, 0)

  @Test fun progressCountsOnlyCommittedDone() {
    val s = state(sub("a", "closed"), sub("b"), sub("c"), sub("d")).copy(pending = setOf("b"), doneIds = setOf("c"))
    assertEquals(2, s.subtasksDone)
    assertEquals(true, s.isSubtaskDone(s.subtasks[1]))
    assertEquals(false, s.isSubtaskCommittedDone(s.subtasks[1]))
  }

  @Test fun nonUndoSnackIsQueuedBehindUndo() {
    val s = state().withSnack(msg(1, "x")).withSnack(msg(2))
    assertEquals(1L, s.snack?.key)
    assertEquals(listOf(2L), s.snackQueue.map { it.key })
    val n = s.nextSnack()
    assertEquals(2L, n.snack?.key)
    assertEquals(emptyList<SnackMessage>(), n.snackQueue)
  }

  @Test fun undoSnackReplacesAndClearsById() {
    val s = state().withSnack(msg(1, "x")).withSnack(msg(2, "y"))
    assertEquals(2L, s.snack?.key)
    assertEquals(2L, s.clearUndoSnack("x").snack?.key)
    assertNull(s.clearUndoSnack("y").snack)
  }
}
