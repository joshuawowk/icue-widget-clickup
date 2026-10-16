package net.jwowk.clickupwidget.ui.detail

import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskField
import net.jwowk.clickupwidget.data.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class DetailFormatTest {
  private val zone = ZoneId.of("UTC")
  private val locale = Locale.US

  private fun ms(y: Int, m: Int, d: Int, hour: Long = 12): Long =
    LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

  private val now = ms(2025, 6, 3)

  @Test fun fullDateFormatsWeekdayMonthDay() {
    assertEquals("Tue, Jun 3", DetailFormat.fullDate(ms(2025, 6, 3), zone, locale))
  }

  @Test fun overdueAndTodayChipsAppendFullDate() {
    val overdueMs = ms(2025, 6, 1)
    val overdue = TaskLogic.dueInfo(overdueMs, now, zone, locale)!!
    assertEquals("Overdue • Sun, Jun 1", DetailFormat.dueChipLabel(overdue, overdueMs, zone, locale))

    val today = TaskLogic.dueInfo(now, now, zone, locale)!!
    assertEquals("Today • Tue, Jun 3", DetailFormat.dueChipLabel(today, now, zone, locale))
  }

  @Test fun tomorrowAndLaterChipsKeepShortLabel() {
    val tomorrowMs = ms(2025, 6, 4)
    val tomorrow = TaskLogic.dueInfo(tomorrowMs, now, zone, locale)!!
    assertEquals("Tomorrow", DetailFormat.dueChipLabel(tomorrow, tomorrowMs, zone, locale))

    val laterMs = ms(2025, 7, 20)
    val later = TaskLogic.dueInfo(laterMs, now, zone, locale)!!
    assertEquals("Jul 20", DetailFormat.dueChipLabel(later, laterMs, zone, locale))
  }

  @Test fun overdueWithoutDueMsFallsBackToLabel() {
    val overdue = TaskLogic.dueInfo(ms(2025, 6, 1), now, zone, locale)!!
    assertEquals("Overdue", DetailFormat.dueChipLabel(overdue, null, zone, locale))
  }

  @Test fun shortDescriptionIsNotCollapsible() {
    val d = DetailFormat.description("  hello  ", expanded = false)
    assertEquals("hello", d.text)
    assertFalse(d.collapsible)
  }

  @Test fun descriptionAtLimitIsNotCollapsible() {
    val text = "a".repeat(DetailFormat.DESCRIPTION_LIMIT)
    val d = DetailFormat.description(text, expanded = false)
    assertEquals(text, d.text)
    assertFalse(d.collapsible)
  }

  @Test fun longDescriptionCollapsesAndExpands() {
    val text = "a".repeat(DetailFormat.DESCRIPTION_LIMIT + 50)
    val collapsed = DetailFormat.description(text, expanded = false)
    assertTrue(collapsed.collapsible)
    assertEquals("a".repeat(DetailFormat.DESCRIPTION_LIMIT) + "…", collapsed.text)
    val expanded = DetailFormat.description(text, expanded = true)
    assertTrue(expanded.collapsible)
    assertEquals(text, expanded.text)
  }

  @Test fun ellipsizeOnlyCutsLongNames() {
    assertEquals("short", DetailFormat.ellipsize("short", 40))
    assertEquals("abcde…", DetailFormat.ellipsize("abcdefgh", 5))
    assertEquals("abcde", DetailFormat.ellipsize("abcde", 5))
  }

  @Test fun taskUrlPrefersTaskUrlThenFallsBack() {
    val withUrl = TaskItem(id = "abc", name = "n", url = "https://app.clickup.com/t/xyz")
    assertEquals("https://app.clickup.com/t/xyz", DetailFormat.taskUrl(withUrl))
    assertEquals("https://app.clickup.com/t/abc", DetailFormat.taskUrl(TaskItem(id = "abc", name = "n")))
    assertEquals("https://app.clickup.com/t/abc", DetailFormat.taskUrl(TaskItem(id = "abc", name = "n", url = " ")))
  }

  @Test fun statusMatchIsCaseInsensitive() {
    assertTrue(DetailFormat.isCurrentStatus("In Progress", "in progress"))
    assertFalse(DetailFormat.isCurrentStatus("Open", "Closed"))
  }

  @Test fun priorityColorUsesClickUpColorOrDefault() {
    val custom = TaskItem(id = "1", name = "n", priority = Priority.URGENT, priorityColorHex = "#00ff00")
    assertEquals(0xFF00FF00, DetailFormat.priorityColor(custom))
    val none = TaskItem(id = "1", name = "n", priority = Priority.HIGH)
    assertEquals(Priority.HIGH.chipColor, DetailFormat.priorityColor(none))
  }

  @Test fun fieldsSectionHiddenWhenEmptyOrBlank() {
    assertFalse(DetailFormat.showFields(emptyList()))
    assertFalse(DetailFormat.showFields(listOf(TaskField("A", "  "))))
    assertTrue(DetailFormat.showFields(listOf(TaskField("A", "x"))))
  }

  @Test fun tagsSectionAndColors() {
    val t = TaskItem(id = "1", name = "n", tags = listOf("meeting", "plain", "bad"), tagColors = mapOf("meeting" to "#0f5096", "bad" to "zz"))
    assertTrue(DetailFormat.showTags(t))
    assertFalse(DetailFormat.showTags(t.copy(tags = emptyList())))
    assertEquals(0xFF0F5096, DetailFormat.tagColor(t, "meeting"))
    assertNull(DetailFormat.tagColor(t, "plain"))
    assertNull(DetailFormat.tagColor(t, "bad"))
  }

  @Test fun priorityChoicesInOrder() {
    assertEquals(
      listOf(Priority.URGENT, Priority.HIGH, Priority.NORMAL, Priority.LOW, Priority.NONE),
      DetailFormat.priorityChoices,
    )
    assertTrue(DetailFormat.isCurrentPriority(Priority.LOW, Priority.LOW))
    assertFalse(DetailFormat.isCurrentPriority(Priority.LOW, Priority.NONE))
  }

  @Test fun mergeFreshTaskKeepsLocalState() {
    val cur = TaskItem(
      id = "1", name = "n", listId = "L", listName = "List", url = "u",
      priority = Priority.HIGH, status = "doing", statusType = "custom",
    )
    val fresh = TaskItem(id = "1", name = "n", priority = Priority.LOW, status = "todo", statusType = "open", tags = listOf("a"))
    val plain = DetailFormat.mergeFreshTask(cur, fresh, keepPriority = false, keepStatus = false)
    assertEquals("L", plain.listId)
    assertEquals("List", plain.listName)
    assertEquals("u", plain.url)
    assertEquals(Priority.LOW, plain.priority)
    assertEquals("todo", plain.status)
    assertEquals(listOf("a"), plain.tags)
    val kept = DetailFormat.mergeFreshTask(cur, fresh, keepPriority = true, keepStatus = true)
    assertEquals(Priority.HIGH, kept.priority)
    assertEquals("doing", kept.status)
    assertEquals("custom", kept.statusType)
    assertEquals(listOf("a"), kept.tags)
  }
}
