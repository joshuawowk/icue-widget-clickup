package net.jwowk.clickupwidget.data

import net.jwowk.clickupwidget.data.model.DueFilter
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.PriorityDefault
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class TaskLogicTest {

  private val ny = ZoneId.of("America/New_York")
  private val us = Locale.US

  private fun ms(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0, sec: Int = 0): Long =
    LocalDateTime.of(y, m, d, h, min, sec).atZone(ny).toInstant().toEpochMilli()

  private val now = ms(2026, 6, 10, 15, 30)

  private fun due(dueMs: Long?, nowMs: Long = now) = TaskLogic.dueInfo(dueMs, nowMs, ny, us)

  private fun task(
    id: String,
    status: String = "open",
    prio: Priority = Priority.NONE,
    dueMs: Long? = null,
    type: String = "open",
    color: String = "#888888",
  ) = TaskItem(
    id = id, name = "Task $id", dueMs = dueMs, priority = prio,
    status = status, statusType = type, statusColorHex = color,
  )

  private fun st(name: String, type: String = "", order: Int = 0) = TaskStatus(name, "#888888", type, order)

  private fun view(
    tasks: List<TaskItem>,
    config: WidgetConfig = WidgetConfig(statusDefault = ""),
    statuses: Set<String>? = null,
    prios: Set<String>? = null,
    listStatuses: List<TaskStatus> = emptyList(),
    nowMs: Long = now,
  ) = TaskLogic.buildView(tasks, config, statuses, prios, listStatuses, nowMs, ny, us)

  private fun TaskListView.ids() = visible.map { it.task.id }

  // ---- normStatus ----

  @Test fun normStatus_stripsNonAlphanumericAndLowercases() {
    assertEquals("inprogress", TaskLogic.normStatus("In-Progress"))
    assertEquals("inprogress", TaskLogic.normStatus("in progress"))
    assertEquals("inprogress", TaskLogic.normStatus("INPROGRESS"))
    assertEquals("review2", TaskLogic.normStatus(" Review_2! "))
  }

  @Test fun normStatus_emptyAndSymbolsOnly() {
    assertEquals("", TaskLogic.normStatus(""))
    assertEquals("", TaskLogic.normStatus("--- ___"))
  }

  // ---- dueInfo ----

  @Test fun dueInfo_nullAndZeroAreNull() {
    assertNull(due(null))
    assertNull(due(0L))
  }

  @Test fun dueInfo_today() {
    val d = due(ms(2026, 6, 10, 0, 0, 0))!!
    assertEquals(DueClass.TODAY, d.cls)
    assertEquals("Today", d.label)
    assertEquals(0.0, d.sort, 0.0)
  }

  @Test fun dueInfo_earlierAndLaterSameDayAreToday() {
    assertEquals(DueClass.TODAY, due(ms(2026, 6, 10, 23, 59, 59))!!.cls)
    assertEquals(DueClass.TODAY, due(ms(2026, 6, 10, 0, 0, 1))!!.cls)
  }

  @Test fun dueInfo_justBeforeMidnightNowIsStillToday_nextMinuteDueIsTomorrow() {
    val nowLate = ms(2026, 6, 10, 23, 59, 59)
    assertEquals(DueClass.TODAY, due(ms(2026, 6, 10, 23, 59, 59), nowLate)!!.cls)
    val tomorrow = due(ms(2026, 6, 11, 0, 0, 0), nowLate)!!
    assertEquals(DueClass.SOON, tomorrow.cls)
    assertEquals("Tomorrow", tomorrow.label)
    assertEquals(1.0, tomorrow.sort, 0.0)
  }

  @Test fun dueInfo_dueOneSecondBeforeMidnightIsOverdueOnceNowRollsOver() {
    val dueMs = ms(2026, 6, 10, 23, 59, 59)
    val nowNext = ms(2026, 6, 11, 0, 0, 0)
    val d = due(dueMs, nowNext)!!
    assertEquals(DueClass.OVERDUE, d.cls)
    assertEquals("Overdue", d.label)
    assertEquals(-1.0 - 1 / 10000.0, d.sort, 1e-12)
  }

  @Test fun dueInfo_overdueOlderSortsSmaller() {
    val yesterday = due(ms(2026, 6, 9))!!
    val lastWeek = due(ms(2026, 6, 3))!!
    assertEquals(DueClass.OVERDUE, yesterday.cls)
    assertEquals(-1.0 - 1 / 10000.0, yesterday.sort, 1e-12)
    assertEquals(-1.0 - 7 / 10000.0, lastWeek.sort, 1e-12)
    assertTrue(lastWeek.sort < yesterday.sort)
    assertTrue(yesterday.sort < 0.0)
  }

  @Test fun dueInfo_tomorrow() {
    val d = due(ms(2026, 6, 11, 8))!!
    assertEquals(DueClass.SOON, d.cls)
    assertEquals("Tomorrow", d.label)
    assertEquals(1.0, d.sort, 0.0)
  }

  @Test fun dueInfo_thisWeekIsSoonWithDateLabel() {
    val d2 = due(ms(2026, 6, 12))!!
    assertEquals(DueClass.SOON, d2.cls)
    assertEquals("Jun 12", d2.label)
    assertEquals(2.0, d2.sort, 0.0)
    val d7 = due(ms(2026, 6, 17))!!
    assertEquals(DueClass.SOON, d7.cls)
    assertEquals("Jun 17", d7.label)
    assertEquals(7.0, d7.sort, 0.0)
  }

  @Test fun dueInfo_eightDaysOutIsLater() {
    val d = due(ms(2026, 6, 18))!!
    assertEquals(DueClass.LATER, d.cls)
    assertEquals("Jun 18", d.label)
    assertEquals(8.0, d.sort, 0.0)
  }

  @Test fun dueInfo_dstSpringForward_dayIs23Hours() {
    // 2026-03-08 is 23h long in New York; the next calendar day must still be exactly +1.
    val nowMs = ms(2026, 3, 8, 0, 30)
    assertEquals(DueClass.TODAY, due(ms(2026, 3, 8, 23, 30), nowMs)!!.cls)
    val d = due(ms(2026, 3, 9, 0, 30), nowMs)!!
    assertEquals("Tomorrow", d.label)
    assertEquals(1.0, d.sort, 0.0)
    val evening = ms(2026, 3, 8, 22, 0)
    assertEquals("Tomorrow", due(ms(2026, 3, 9, 0, 0, 1), evening)!!.label)
  }

  @Test fun dueInfo_dstSpringForward_weekBoundary() {
    val nowMs = ms(2026, 3, 7, 12)
    val d = due(ms(2026, 3, 14, 12), nowMs)!!
    assertEquals(DueClass.SOON, d.cls)
    assertEquals(7.0, d.sort, 0.0)
    assertEquals("Mar 14", d.label)
    assertEquals(DueClass.LATER, due(ms(2026, 3, 15, 12), nowMs)!!.cls)
  }

  @Test fun dueInfo_dstFallBack_dayIs25Hours() {
    // 2026-11-01 is 25h long in New York.
    val nowMs = ms(2026, 11, 1, 0, 15)
    assertEquals(DueClass.TODAY, due(ms(2026, 11, 1, 23, 45), nowMs)!!.cls)
    assertEquals("Tomorrow", due(ms(2026, 11, 2, 0, 15), nowMs)!!.label)
    val lateNow = ms(2026, 11, 1, 23, 30)
    assertEquals("Tomorrow", due(ms(2026, 11, 2, 0, 5), lateNow)!!.label)
    assertEquals(DueClass.OVERDUE, due(ms(2026, 10, 31, 23, 59), nowMs)!!.cls)
  }

  @Test fun dueInfo_dstFallBack_overdueCountsCalendarDays() {
    val nowMs = ms(2026, 11, 2, 12)
    val d = due(ms(2026, 10, 31, 12), nowMs)!!
    assertEquals(DueClass.OVERDUE, d.cls)
    assertEquals(-1.0 - 2 / 10000.0, d.sort, 1e-12)
  }

  @Test fun dueInfo_zoneChangesDayAssignment() {
    val utc = ZoneId.of("UTC")
    // 03:00 UTC on Jun 11 is still Jun 10 (23:00 EDT) in New York.
    val dueMs = LocalDateTime.of(2026, 6, 11, 3, 0).atZone(utc).toInstant().toEpochMilli()
    assertEquals("Today", TaskLogic.dueInfo(dueMs, now, ny, us)!!.label)
    val utcNow = LocalDateTime.of(2026, 6, 10, 15, 30).atZone(utc).toInstant().toEpochMilli()
    assertEquals("Tomorrow", TaskLogic.dueInfo(dueMs, utcNow, utc, us)!!.label)
  }

  @Test fun dueInfo_yearBoundary() {
    val nowMs = ms(2026, 12, 31, 20)
    val d = due(ms(2027, 1, 3), nowMs)!!
    assertEquals(DueClass.SOON, d.cls)
    assertEquals(3.0, d.sort, 0.0)
    assertEquals("Jan 3", d.label)
  }

  // ---- seedPriorities ----

  @Test fun seedPriorities_perDefault() {
    assertEquals(setOf("urgent"), TaskLogic.seedPriorities(PriorityDefault.URGENT))
    assertEquals(setOf("urgent", "high"), TaskLogic.seedPriorities(PriorityDefault.HIGH))
    assertEquals(setOf("urgent", "high", "normal"), TaskLogic.seedPriorities(PriorityDefault.NORMAL))
    assertEquals(setOf("urgent", "high", "normal", "low", "none"), TaskLogic.seedPriorities(PriorityDefault.ALL))
  }

  // ---- resolveStatusSelection ----

  @Test fun resolveStatus_blankSelectsAllPresent() {
    val present = listOf(st("Open"), st("In-Progress"))
    assertEquals(setOf("Open", "In-Progress"), TaskLogic.resolveStatusSelection(present, ""))
    assertEquals(setOf("Open", "In-Progress"), TaskLogic.resolveStatusSelection(present, "  , ,"))
  }

  @Test fun resolveStatus_matchesNormalized() {
    val present = listOf(st("Open"), st("In-Progress"), st("Review"))
    assertEquals(setOf("In-Progress"), TaskLogic.resolveStatusSelection(present, "in progress"))
    assertEquals(setOf("In-Progress"), TaskLogic.resolveStatusSelection(present, "INPROGRESS"))
  }

  @Test fun resolveStatus_multipleCommaSeparatedWithWhitespace() {
    val present = listOf(st("Open"), st("In-Progress"), st("Review"))
    assertEquals(setOf("Open", "Review"), TaskLogic.resolveStatusSelection(present, " open , review "))
  }

  @Test fun resolveStatus_noMatchFallsBackToAll() {
    val present = listOf(st("Open"), st("Review"))
    assertEquals(setOf("Open", "Review"), TaskLogic.resolveStatusSelection(present, "Nonexistent"))
  }

  @Test fun resolveStatus_partialMatchIgnoresAbsent() {
    val present = listOf(st("Open"), st("Review"))
    assertEquals(setOf("Review"), TaskLogic.resolveStatusSelection(present, "Review, Gone"))
  }

  @Test fun resolveStatus_emptyPresent() {
    assertEquals(emptySet<String>(), TaskLogic.resolveStatusSelection(emptyList(), "In-Progress"))
  }

  // ---- toggle ----

  @Test fun toggle_addsAndRemovesWithoutMutating() {
    val base = setOf("a", "b")
    assertEquals(setOf("a", "b", "c"), TaskLogic.toggle(base, "c"))
    assertEquals(setOf("b"), TaskLogic.toggle(base, "a"))
    assertEquals(setOf("a", "b"), base)
    assertEquals(emptySet<String>(), TaskLogic.toggle(setOf("a"), "a"))
  }

  // ---- orderStatuses ----

  @Test fun orderStatuses_byTypeRankThenName() {
    val present = listOf(
      st("Closed", "closed"), st("Done", "done"), st("Beta", "custom"),
      st("Alpha", "custom"), st("Open", "open"), st("Weird", "other"),
    )
    assertEquals(
      listOf("Open", "Alpha", "Beta", "Done", "Closed", "Weird"),
      TaskLogic.orderStatuses(present, emptyList()).map { it.name },
    )
  }

  @Test fun orderStatuses_listOrderUsesOrderIndex() {
    val list = listOf(st("Review", "custom", 2), st("Open", "open", 0), st("Doing", "custom", 1))
    val present = listOf(st("Review", "custom"), st("Open", "open"), st("Doing", "custom"))
    assertEquals(listOf("Open", "Doing", "Review"), TaskLogic.orderStatuses(present, list).map { it.name })
  }

  @Test fun orderStatuses_listOrderUnknownLastThenByName() {
    val list = listOf(st("Doing", "custom", 1), st("Open", "open", 0))
    val present = listOf(st("Zeta", "open"), st("Doing"), st("Mystery"), st("Open"))
    assertEquals(
      listOf("Open", "Doing", "Mystery", "Zeta"),
      TaskLogic.orderStatuses(present, list).map { it.name },
    )
  }

  @Test fun orderStatuses_listOrderOverridesTypeRank() {
    val list = listOf(st("Done", "done", 0), st("Open", "open", 1))
    val present = listOf(st("Open", "open"), st("Done", "done"))
    assertEquals(listOf("Done", "Open"), TaskLogic.orderStatuses(present, list).map { it.name })
  }

  @Test fun orderStatuses_sameOrderIndexTieBrokenByName() {
    val list = listOf(st("B", "", 1), st("A", "", 1))
    assertEquals(listOf("A", "B"), TaskLogic.orderStatuses(listOf(st("B"), st("A")), list).map { it.name })
  }

  @Test fun orderStatuses_nameTieBreakIsLocaleAware() {
    val present = listOf(st("Beta", "custom"), st("alpha", "custom"))
    assertEquals(listOf("alpha", "Beta"), TaskLogic.orderStatuses(present, emptyList()).map { it.name })
  }

  // ---- buildView: due filter ----

  private val mixedDue = listOf(
    task("none"),
    task("over", dueMs = ms(2026, 6, 1)),
    task("today", dueMs = ms(2026, 6, 10, 18)),
    task("tom", dueMs = ms(2026, 6, 11)),
    task("d7", dueMs = ms(2026, 6, 17)),
    task("d8", dueMs = ms(2026, 6, 18)),
  )

  @Test fun dueFilter_allKeepsEverythingSortedByDue() {
    val v = view(mixedDue)
    assertEquals(listOf("over", "today", "tom", "d7", "d8", "none"), v.ids())
    assertEquals(6, v.total)
    assertEquals(6, v.scopedTotal)
  }

  @Test fun dueFilter_todayKeepsOverdueAndTodayOnly() {
    val v = view(mixedDue, WidgetConfig(dueFilter = DueFilter.TODAY, statusDefault = ""))
    assertEquals(listOf("over", "today"), v.ids())
    assertEquals(2, v.scopedTotal)
  }

  @Test fun dueFilter_weekKeepsUpToSevenDaysAndDropsNoDue() {
    val v = view(mixedDue, WidgetConfig(dueFilter = DueFilter.WEEK, statusDefault = ""))
    assertEquals(listOf("over", "today", "tom", "d7"), v.ids())
    assertEquals(4, v.scopedTotal)
  }

  @Test fun dueFilter_scopesPresentStatusesAndPriorities() {
    val tasks = listOf(
      task("a", status = "Open", prio = Priority.URGENT, dueMs = ms(2026, 6, 10)),
      task("b", status = "Review", prio = Priority.LOW, dueMs = ms(2026, 7, 30), type = "custom"),
    )
    val v = view(tasks, WidgetConfig(dueFilter = DueFilter.TODAY, statusDefault = ""))
    assertEquals(listOf("Open"), v.presentStatuses.map { it.name })
    assertEquals(listOf(Priority.URGENT), v.presentPriorities)
  }

  @Test fun dueFilter_emptyScopeGivesZeroScopedTotal() {
    val v = view(listOf(task("x")), WidgetConfig(dueFilter = DueFilter.TODAY, statusDefault = ""))
    assertEquals(0, v.scopedTotal)
    assertEquals(0, v.total)
    assertTrue(v.visible.isEmpty())
  }

  @Test fun emptyInput() {
    val v = view(emptyList())
    assertEquals(0, v.total)
    assertEquals(0, v.scopedTotal)
    assertFalse(v.anyConstrained)
    assertTrue(v.presentStatuses.isEmpty())
  }

  // ---- buildView: sorting ----

  @Test fun sort_overdueOlderFirst() {
    val tasks = listOf(
      task("yest", dueMs = ms(2026, 6, 9)),
      task("week", dueMs = ms(2026, 6, 3)),
      task("month", dueMs = ms(2026, 5, 10)),
    )
    assertEquals(listOf("month", "week", "yest"), view(tasks).ids())
  }

  @Test fun sort_tiesBrokenByPriorityRank() {
    val d = ms(2026, 6, 10)
    val tasks = listOf(
      task("none", dueMs = d),
      task("low", prio = Priority.LOW, dueMs = d),
      task("urg", prio = Priority.URGENT, dueMs = d),
      task("norm", prio = Priority.NORMAL, dueMs = d),
      task("high", prio = Priority.HIGH, dueMs = d),
    )
    assertEquals(listOf("urg", "high", "norm", "low", "none"), view(tasks).ids())
  }

  @Test fun sort_sameDayDifferentTimesAreTiesSoPriorityDecides() {
    val tasks = listOf(
      task("early-low", prio = Priority.LOW, dueMs = ms(2026, 6, 10, 1)),
      task("late-urgent", prio = Priority.URGENT, dueMs = ms(2026, 6, 10, 23)),
    )
    assertEquals(listOf("late-urgent", "early-low"), view(tasks).ids())
  }

  @Test fun sort_noDueGoesLastEvenIfUrgent() {
    val tasks = listOf(
      task("nodue", prio = Priority.URGENT),
      task("far", dueMs = ms(2027, 1, 1)),
    )
    assertEquals(listOf("far", "nodue"), view(tasks).ids())
  }

  @Test fun sort_isStableForFullTies() {
    val tasks = listOf(task("a"), task("b"), task("c"), task("d"))
    assertEquals(listOf("a", "b", "c", "d"), view(tasks).ids())
  }

  // ---- buildView: maxTasks ----

  private fun many(n: Int) = (1..n).map { task("t%03d".format(it)) }

  @Test fun maxTasks_truncatesButTotalIsFull() {
    val v = view(many(20), WidgetConfig(maxTasks = 5, statusDefault = ""))
    assertEquals(5, v.visible.size)
    assertEquals(20, v.total)
    assertEquals(listOf("t001", "t002", "t003", "t004", "t005"), v.ids())
  }

  @Test fun maxTasks_clampedUpToThree() {
    assertEquals(3, view(many(10), WidgetConfig(maxTasks = 2, statusDefault = "")).visible.size)
    assertEquals(3, view(many(10), WidgetConfig(maxTasks = 0, statusDefault = "")).visible.size)
    assertEquals(3, view(many(10), WidgetConfig(maxTasks = -5, statusDefault = "")).visible.size)
  }

  @Test fun maxTasks_clampedDownToFifty() {
    val v = view(many(80), WidgetConfig(maxTasks = 99, statusDefault = ""))
    assertEquals(50, v.visible.size)
    assertEquals(80, v.total)
  }

  @Test fun maxTasks_boundsExact() {
    assertEquals(3, view(many(10), WidgetConfig(maxTasks = 3, statusDefault = "")).visible.size)
    assertEquals(50, view(many(60), WidgetConfig(maxTasks = 50, statusDefault = "")).visible.size)
  }

  @Test fun maxTasks_fewerTasksThanMax() {
    assertEquals(2, view(many(2), WidgetConfig(maxTasks = 12, statusDefault = "")).visible.size)
  }

  // ---- buildView: status / priority selection ----

  private val statusTasks = listOf(
    task("o1", status = "Open", type = "open"),
    task("p1", status = "In-Progress", type = "custom"),
    task("p2", status = "In-Progress", type = "custom"),
    task("r1", status = "Review", type = "custom"),
  )

  @Test fun nullStatusSelection_resolvedFromConfigDefault() {
    val v = view(statusTasks, WidgetConfig(statusDefault = "In-Progress"))
    assertEquals(setOf("In-Progress"), v.resolvedStatusSelection)
    assertEquals(setOf("In-Progress"), v.selectedStatuses)
    assertEquals(listOf("p1", "p2"), v.ids())
    assertTrue(v.anyConstrained)
    assertEquals(2, v.total)
    assertEquals(4, v.scopedTotal)
  }

  @Test fun nullStatusSelection_blankDefaultMeansAllAndUnconstrained() {
    val v = view(statusTasks, WidgetConfig(statusDefault = ""))
    assertEquals(setOf("Open", "In-Progress", "Review"), v.resolvedStatusSelection)
    assertEquals(4, v.total)
    assertFalse(v.anyConstrained)
  }

  @Test fun nullStatusSelection_noMatchingDefaultMeansAll() {
    val v = view(statusTasks, WidgetConfig(statusDefault = "Blocked"))
    assertEquals(4, v.total)
    assertFalse(v.anyConstrained)
  }

  @Test fun explicitStatusSelection_isNotResolvedAndIgnoresDefault() {
    val v = view(statusTasks, WidgetConfig(statusDefault = "In-Progress"), statuses = setOf("Review"))
    assertNull(v.resolvedStatusSelection)
    assertEquals(listOf("r1"), v.ids())
    assertEquals(setOf("Review"), v.selectedStatuses)
  }

  @Test fun statusSelection_allSelectedIsUnconstrained() {
    val v = view(statusTasks, statuses = setOf("Open", "In-Progress", "Review"))
    assertEquals(4, v.total)
    assertFalse(v.anyConstrained)
  }

  @Test fun statusSelection_noneSelectedIsUnconstrained() {
    val v = view(statusTasks, statuses = emptySet())
    assertEquals(4, v.total)
    assertFalse(v.anyConstrained)
    assertTrue(v.selectedStatuses.isEmpty())
  }

  @Test fun statusSelection_absentStatusesIgnored() {
    val v = view(statusTasks, statuses = setOf("Open", "Ghost"))
    assertEquals(setOf("Open"), v.selectedStatuses)
    assertEquals(listOf("o1"), v.ids())
    assertTrue(v.anyConstrained)
  }

  @Test fun statusSelection_onlyAbsentStatusesMeansUnconstrained() {
    val v = view(statusTasks, statuses = setOf("Ghost"))
    assertTrue(v.selectedStatuses.isEmpty())
    assertEquals(4, v.total)
    assertFalse(v.anyConstrained)
  }

  @Test fun statusSelection_allPresentPlusAbsentIsUnconstrained() {
    val v = view(statusTasks, statuses = setOf("Open", "In-Progress", "Review", "Ghost"))
    assertFalse(v.anyConstrained)
    assertEquals(4, v.total)
  }

  @Test fun statusSelection_isCaseSensitiveExactNameMatch() {
    val v = view(statusTasks, statuses = setOf("open"))
    assertTrue(v.selectedStatuses.isEmpty())
    assertEquals(4, v.total)
  }

  @Test fun tasksWithEmptyStatusAreNotChips() {
    val tasks = listOf(
      task("a", status = "Open"),
      task("b", status = "Review", type = "custom"),
      task("c", status = ""),
    )
    val v = view(tasks, statuses = setOf("Open"))
    assertEquals(listOf("Open", "Review"), v.presentStatuses.map { it.name })
    assertEquals(listOf("a"), v.ids())
  }

  @Test fun nullPriority_seededFromConfigDefault() {
    val tasks = listOf(
      task("u", prio = Priority.URGENT), task("h", prio = Priority.HIGH),
      task("n", prio = Priority.NORMAL), task("x"),
    )
    val v = view(tasks, WidgetConfig(priorityDefault = PriorityDefault.HIGH, statusDefault = ""))
    assertEquals(setOf("urgent", "high"), v.resolvedPrioritySelection)
    assertEquals(setOf("urgent", "high"), v.selectedPriorities)
    assertEquals(listOf("u", "h"), v.ids())
    assertTrue(v.anyConstrained)
  }

  @Test fun nullPriority_allDefaultIsUnconstrained() {
    val tasks = listOf(task("u", prio = Priority.URGENT), task("x"))
    val v = view(tasks, WidgetConfig(priorityDefault = PriorityDefault.ALL, statusDefault = ""))
    assertEquals(2, v.total)
    assertFalse(v.anyConstrained)
    assertEquals(setOf("urgent", "none"), v.selectedPriorities)
  }

  @Test fun nullPriority_defaultWithNoPresentOverlapIsUnconstrained() {
    val tasks = listOf(task("l", prio = Priority.LOW), task("x"))
    val v = view(tasks, WidgetConfig(priorityDefault = PriorityDefault.URGENT, statusDefault = ""))
    assertEquals(2, v.total)
    assertFalse(v.anyConstrained)
    assertTrue(v.selectedPriorities.isEmpty())
  }

  @Test fun explicitPrioritySelection_narrowsAndIsNotResolved() {
    val tasks = listOf(task("u", prio = Priority.URGENT), task("l", prio = Priority.LOW), task("x"))
    val v = view(tasks, prios = setOf("none"))
    assertNull(v.resolvedPrioritySelection)
    assertEquals(listOf("x"), v.ids())
  }

  @Test fun prioritySelection_allNoneOrAbsentOnlyIsUnconstrained() {
    val tasks = listOf(task("u", prio = Priority.URGENT), task("l", prio = Priority.LOW))
    assertEquals(2, view(tasks, prios = setOf("urgent", "low")).total)
    assertEquals(2, view(tasks, prios = emptySet()).total)
    assertEquals(2, view(tasks, prios = setOf("high")).total)
  }

  @Test fun bothDimensionsCombineWithAnd() {
    val tasks = listOf(
      task("a", status = "Open", prio = Priority.URGENT),
      task("b", status = "Open", prio = Priority.LOW),
      task("c", status = "Review", prio = Priority.URGENT, type = "custom"),
      task("d", status = "Review", prio = Priority.LOW, type = "custom"),
    )
    val v = view(tasks, statuses = setOf("Open"), prios = setOf("urgent"))
    assertEquals(listOf("a"), v.ids())
    assertTrue(v.anyConstrained)
  }

  @Test fun anyConstrained_trueIfOnlyOneDimensionNarrows() {
    val tasks = listOf(
      task("a", status = "Open", prio = Priority.URGENT),
      task("b", status = "Review", prio = Priority.LOW, type = "custom"),
    )
    val v = view(tasks, statuses = setOf("Open", "Review"), prios = setOf("urgent"))
    assertTrue(v.anyConstrained)
    assertEquals(listOf("a"), v.ids())
  }

  // ---- buildView: chips ----

  @Test fun presentPriorities_inPriorityOrder() {
    val tasks = listOf(
      task("a", prio = Priority.LOW), task("b"),
      task("c", prio = Priority.URGENT), task("d", prio = Priority.LOW),
    )
    assertEquals(listOf(Priority.URGENT, Priority.LOW, Priority.NONE), view(tasks).presentPriorities)
  }

  @Test fun presentStatuses_typeRankWhenNoListStatuses() {
    val tasks = listOf(
      task("1", status = "Complete", type = "done"),
      task("2", status = "Review", type = "custom"),
      task("3", status = "Open", type = "open"),
      task("4", status = "Archived", type = "closed"),
    )
    assertEquals(
      listOf("Open", "Review", "Complete", "Archived"),
      view(tasks).presentStatuses.map { it.name },
    )
  }

  @Test fun presentStatuses_listOrderWithUnknownLast() {
    val tasks = listOf(
      task("1", status = "Zzz", type = "custom"),
      task("2", status = "Review", type = "custom"),
      task("3", status = "Open", type = "open"),
    )
    val list = listOf(st("Review", "custom", 0), st("Open", "open", 1))
    assertEquals(
      listOf("Review", "Open", "Zzz"),
      view(tasks, listStatuses = list).presentStatuses.map { it.name },
    )
  }

  @Test fun presentStatuses_firstSeenColorAndTypeWin() {
    val tasks = listOf(
      task("1", status = "Open", type = "open", color = "#111111"),
      task("2", status = "Open", type = "custom", color = "#222222"),
    )
    val s = view(tasks).presentStatuses.single()
    assertEquals("#111111", s.colorHex)
    assertEquals("open", s.type)
  }

  @Test fun listStatusesNotPresentAreNotChips() {
    val list = listOf(st("Open", "open", 0), st("Review", "custom", 1))
    val v = view(listOf(task("1", status = "Open")), listStatuses = list)
    assertEquals(listOf("Open"), v.presentStatuses.map { it.name })
  }

  @Test fun statusDefaultMatchesNormalizedPresentName() {
    val tasks = listOf(task("1", status = "Open"), task("2", status = "In Progress", type = "custom"))
    val v = view(tasks, WidgetConfig(statusDefault = "in-progress"))
    assertEquals(setOf("In Progress"), v.selectedStatuses)
    assertEquals(listOf("2"), v.ids())
  }

  @Test fun rowsCarryDueInfo() {
    val v = view(listOf(task("a", dueMs = ms(2026, 6, 11)), task("b")))
    assertEquals("Tomorrow", v.visible[0].due!!.label)
    assertNull(v.visible[1].due)
  }

  // ---- findClosedStatus ----

  @Test fun findClosed_prefersClosedTypeOverDone() {
    val list = listOf(st("Done", "done"), st("Shipped", "closed"), st("Complete", "custom"))
    assertEquals("Shipped", TaskLogic.findClosedStatus(list)!!.name)
  }

  @Test fun findClosed_doneTypeBeforeNameMatch() {
    val list = listOf(st("Complete", "custom"), st("Finished", "done"))
    assertEquals("Finished", TaskLogic.findClosedStatus(list)!!.name)
  }

  @Test fun findClosed_nameFallbackCaseInsensitive() {
    assertEquals("COMPLETE", TaskLogic.findClosedStatus(listOf(st("Open", "open"), st("COMPLETE", "custom")))!!.name)
    assertEquals("done", TaskLogic.findClosedStatus(listOf(st("done", "custom")))!!.name)
    assertEquals("Closed", TaskLogic.findClosedStatus(listOf(st("Closed", "")))!!.name)
  }

  @Test fun findClosed_nameMustMatchWholeString() {
    val list = listOf(st("Not done", "custom"), st("Completed", "custom"), st("done!", "custom"))
    assertNull(TaskLogic.findClosedStatus(list))
  }

  @Test fun findClosed_firstOfSameTypeWins() {
    assertEquals("A", TaskLogic.findClosedStatus(listOf(st("A", "closed"), st("B", "closed")))!!.name)
  }

  @Test fun findClosed_nullWhenNothingMatchesOrEmpty() {
    assertNull(TaskLogic.findClosedStatus(listOf(st("Open", "open"), st("Review", "custom"))))
    assertNull(TaskLogic.findClosedStatus(emptyList()))
  }

  // ---- sortSubtasks ----

  @Test fun subtasks_openFirstThenByDueThenDoneSinks() {
    val subs = listOf(
      task("done-early", status = "Done", type = "done", dueMs = ms(2026, 6, 1)),
      task("open-nodue"),
      task("open-later", dueMs = ms(2026, 6, 20)),
      task("open-over", dueMs = ms(2026, 6, 2)),
      task("closed-nodue", status = "Closed", type = "closed"),
    )
    assertEquals(
      listOf("open-over", "open-later", "open-nodue", "done-early", "closed-nodue"),
      TaskLogic.sortSubtasks(subs, now, ny).map { it.id },
    )
  }

  @Test fun subtasks_stableOnTies() {
    val subs = listOf(
      task("a"), task("b"),
      task("c", dueMs = ms(2026, 6, 10)), task("d", dueMs = ms(2026, 6, 10, 23)),
    )
    assertEquals(listOf("c", "d", "a", "b"), TaskLogic.sortSubtasks(subs, now, ny).map { it.id })
  }

  @Test fun subtasks_priorityDoesNotAffectOrder() {
    val subs = listOf(task("low", prio = Priority.LOW), task("urg", prio = Priority.URGENT))
    assertEquals(listOf("low", "urg"), TaskLogic.sortSubtasks(subs, now, ny).map { it.id })
  }

  @Test fun subtasks_emptyList() {
    assertTrue(TaskLogic.sortSubtasks(emptyList(), now, ny).isEmpty())
  }

  // ---- parseColor ----

  @Test fun parseColor_sixDigitGetsOpaqueAlpha() {
    assertEquals(0xFFF50000L, TaskLogic.parseColor("#f50000"))
    assertEquals(0xFF6FDDFFL, TaskLogic.parseColor("#6FDDFF"))
  }

  @Test fun parseColor_threeDigitExpands() {
    assertEquals(0xFFAABBCCL, TaskLogic.parseColor("#abc"))
    assertEquals(0xFF000000L, TaskLogic.parseColor("#000"))
  }

  @Test fun parseColor_eightDigitIsArgbAsIs() {
    assertEquals(0x80112233L, TaskLogic.parseColor("#80112233"))
    assertEquals(0x00FFFFFFL, TaskLogic.parseColor("#00ffffff"))
  }

  @Test fun parseColor_hashOptionalAndTrimmed() {
    assertEquals(0xFF112233L, TaskLogic.parseColor("112233"))
    assertEquals(0xFF112233L, TaskLogic.parseColor("  #112233  "))
  }

  @Test fun parseColor_fallbackCases() {
    val fb = 0xFF888888L
    assertEquals(fb, TaskLogic.parseColor(null))
    assertEquals(fb, TaskLogic.parseColor(""))
    assertEquals(fb, TaskLogic.parseColor("   "))
    assertEquals(fb, TaskLogic.parseColor("#"))
    assertEquals(fb, TaskLogic.parseColor("#12"))
    assertEquals(fb, TaskLogic.parseColor("#12345"))
    assertEquals(fb, TaskLogic.parseColor("#1234567"))
    assertEquals(fb, TaskLogic.parseColor("#zzzzzz"))
    assertEquals(fb, TaskLogic.parseColor("#12 456"))
    assertEquals(fb, TaskLogic.parseColor("red"))
  }

  @Test fun parseColor_customFallback() {
    assertEquals(0xFF123456L, TaskLogic.parseColor("nope!", 0xFF123456))
    assertEquals(0xFF123456L, TaskLogic.parseColor(null, 0xFF123456))
  }

  @Test fun parseColor_fullAlphaWhiteIsPositiveLong() {
    val c = TaskLogic.parseColor("#ffffffff")
    assertEquals(0xFFFFFFFFL, c)
    assertTrue(c > 0)
  }
}
