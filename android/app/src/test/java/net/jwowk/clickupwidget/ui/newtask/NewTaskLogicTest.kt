package net.jwowk.clickupwidget.ui.newtask

import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewTaskLogicTest {
  private val zone = ZoneId.of("America/New_York")

  /** 2026-10-16 23:30 local — late evening, where UTC is already the next day. */
  private val now = ZonedDateTime.of(2026, 10, 16, 23, 30, 0, 0, zone).toInstant().toEpochMilli()

  private fun noon(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

  @Test
  fun noneHasNoDueDate() {
    assertNull(NewTaskLogic.dueMs(DueChoice.NONE, 123, now, zone))
  }

  @Test
  fun todayAndTomorrowAreLocalNoon() {
    assertEquals(noon(2026, 10, 16), NewTaskLogic.dueMs(DueChoice.TODAY, null, now, zone))
    assertEquals(noon(2026, 10, 17), NewTaskLogic.dueMs(DueChoice.TOMORROW, null, now, zone))
  }

  @Test
  fun tomorrowCrossesMonthEnd() {
    val endOfMonth = ZonedDateTime.of(2026, 10, 31, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    assertEquals(noon(2026, 11, 1), NewTaskLogic.dueMs(DueChoice.TOMORROW, null, endOfMonth, zone))
  }

  @Test
  fun pickedDayIsLocalNoonOfThatDay() {
    val day = LocalDate.of(2026, 12, 25).toEpochDay()
    assertEquals(noon(2026, 12, 25), NewTaskLogic.dueMs(DueChoice.PICK, day, now, zone))
  }

  @Test
  fun pickWithoutADateIsNone() {
    assertNull(NewTaskLogic.dueMs(DueChoice.PICK, null, now, zone))
  }

  @Test
  fun pickerUtcMidnightRoundTrips() {
    val utcMidnight = ZonedDateTime.of(2026, 10, 21, 0, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
    val day = NewTaskLogic.epochDayOfPicker(utcMidnight)
    assertEquals(LocalDate.of(2026, 10, 21).toEpochDay(), day)
    assertEquals(utcMidnight, NewTaskLogic.pickerMsOfEpochDay(day))
  }

  @Test
  fun createNeedsNameListAndIdle() {
    assertTrue(NewTaskLogic.canCreate("Write docs", "901", busy = false))
    assertFalse(NewTaskLogic.canCreate("   ", "901", busy = false))
    assertFalse(NewTaskLogic.canCreate("", "901", busy = false))
    assertFalse(NewTaskLogic.canCreate("Write docs", null, busy = false))
    assertFalse(NewTaskLogic.canCreate("Write docs", " ", busy = false))
    assertFalse(NewTaskLogic.canCreate("Write docs", "901", busy = true))
  }

  @Test
  fun unassignedNoteOnlyInMyTasksWhenNotAssigned() {
    assertTrue(NewTaskLogic.showUnassignedNote(assignToMe = false, listMode = false))
    assertFalse(NewTaskLogic.showUnassignedNote(assignToMe = false, listMode = true))
    assertFalse(NewTaskLogic.showUnassignedNote(assignToMe = true, listMode = false))
  }

  @Test
  fun initialListFollowsMode() {
    val listMode = WidgetConfig(listId = "42", listName = "Sprint")
    val settings = AppSettings(lastNewTaskListId = "7", lastNewTaskListName = "Inbox")
    assertEquals("42", NewTaskLogic.initialList(listMode, settings)?.id)
    assertEquals("Sprint", NewTaskLogic.initialList(listMode, settings)?.name)
    // Unnamed list falls back to its id.
    assertEquals("42", NewTaskLogic.initialList(WidgetConfig(listId = "42"), settings)?.name)

    assertEquals("7", NewTaskLogic.initialList(WidgetConfig(), settings)?.id)
    assertEquals("Inbox", NewTaskLogic.initialList(WidgetConfig(), settings)?.name)
    assertNull(NewTaskLogic.initialList(WidgetConfig(), AppSettings()))
  }

  @Test
  fun errorsMapToInlineKinds() {
    assertEquals(NewTaskError.INVALID_TOKEN, NewTaskLogic.errorFor(ClickUpApiException(401)))
    assertEquals(NewTaskError.INVALID_TOKEN, NewTaskLogic.errorFor(NoTokenException()))
    assertEquals(NewTaskError.SERVER, NewTaskLogic.errorFor(ClickUpApiException(500)))
    assertEquals(NewTaskError.NETWORK, NewTaskLogic.errorFor(IOException("offline")))
    assertEquals(NewTaskError.UNKNOWN, NewTaskLogic.errorFor(IllegalStateException()))
  }
}
