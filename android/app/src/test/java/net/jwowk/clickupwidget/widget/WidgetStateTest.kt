package net.jwowk.clickupwidget.widget

import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot
import net.jwowk.clickupwidget.data.model.WidgetUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
  private val tasks = listOf(
    TaskItem("a", "A", status = "to do", priority = Priority.HIGH),
    TaskItem("b", "B", status = "doing", priority = Priority.LOW),
  )

  private fun state(statuses: Set<String>?, priorities: Set<String>? = null, open: Boolean = false) = WidgetState(
    config = WidgetConfig(statusDefault = ""),
    snapshot = WidgetSnapshot(loadState = LoadState.READY, tasks = tasks),
    ui = WidgetUiState(filterBarOpen = open, selectedStatuses = statuses, selectedPriorities = priorities),
  )

  @Test fun opensBarWhenFiltersHideEverything() {
    // "to do" only + "low" only: a proper subset on both dimensions with no task matching both.
    val s = state(setOf("to do"), setOf("low")).openBarIfFilteredEmpty(0)
    assertTrue(s.ui.filterBarOpen)
  }

  @Test fun leavesBarAloneWhenSomethingMatches() {
    assertFalse(state(setOf("to do")).openBarIfFilteredEmpty(0).ui.filterBarOpen)
  }

  @Test fun neverClosesAnOpenBar() {
    assertTrue(state(null, open = true).openBarIfFilteredEmpty(0).ui.filterBarOpen)
  }

  @Test fun ignoresNonReadySnapshots() {
    val s = state(setOf("to do"), setOf("low")).let { it.copy(snapshot = it.snapshot.copy(loadState = LoadState.ERROR)) }
    assertFalse(s.openBarIfFilteredEmpty(0).ui.filterBarOpen)
  }
}
