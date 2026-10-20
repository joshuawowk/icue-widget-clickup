package net.jwowk.clickupwidget.ui.newtask

import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.RequesterField
import net.jwowk.clickupwidget.data.model.RequesterOption
import net.jwowk.clickupwidget.data.model.TaskStatus
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
    val settings = AppSettings(lastNewTaskListId = "7", lastNewTaskListName = "Inbox", lastNewTaskScope = "ws1")
    assertEquals("42", NewTaskLogic.initialList(listMode, settings, "ws1")?.id)
    assertEquals("Sprint", NewTaskLogic.initialList(listMode, settings, "ws1")?.name)
    // Unnamed list falls back to its id.
    assertEquals("42", NewTaskLogic.initialList(WidgetConfig(listId = "42"), settings, "ws1")?.name)

    assertEquals("7", NewTaskLogic.initialList(WidgetConfig(), settings, "ws1")?.id)
    assertEquals("Inbox", NewTaskLogic.initialList(WidgetConfig(), settings, "ws1")?.name)
    assertNull(NewTaskLogic.initialList(WidgetConfig(), AppSettings(), "ws1"))
  }

  @Test
  fun rememberedListIgnoredOutsideItsScope() {
    val settings = AppSettings(lastNewTaskListId = "7", lastNewTaskListName = "Inbox", lastNewTaskScope = "ws1")
    assertNull(NewTaskLogic.initialList(WidgetConfig(), settings, "ws2"))
    assertNull(NewTaskLogic.initialList(WidgetConfig(), settings, "demo"))
    assertNull(NewTaskLogic.initialList(WidgetConfig(), settings, null))
    // Legacy value without a scope never matches.
    assertNull(NewTaskLogic.initialList(WidgetConfig(), settings.copy(lastNewTaskScope = ""), "ws1"))
    assertEquals("7", NewTaskLogic.initialList(WidgetConfig(), settings.copy(lastNewTaskScope = "demo"), "demo")?.id)
    // List mode is unaffected by scope.
    assertEquals("42", NewTaskLogic.initialList(WidgetConfig(listId = "42"), settings, "ws2")?.id)
  }

  @Test
  fun errorsMapToInlineKinds() {
    assertEquals(NewTaskError.INVALID_TOKEN, NewTaskLogic.errorFor(ClickUpApiException(401)))
    assertEquals(NewTaskError.INVALID_TOKEN, NewTaskLogic.errorFor(NoTokenException()))
    assertEquals(NewTaskError.LIST_NOT_FOUND, NewTaskLogic.errorFor(ClickUpApiException(404)))
    assertEquals(NewTaskError.LIST_NOT_FOUND, NewTaskLogic.errorFor(ClickUpApiException(403)))
    assertEquals(NewTaskError.SERVER, NewTaskLogic.errorFor(ClickUpApiException(500)))
    assertEquals(NewTaskError.NETWORK, NewTaskLogic.errorFor(IOException("offline")))
    assertEquals(NewTaskError.UNKNOWN, NewTaskLogic.errorFor(IllegalStateException()))
  }

  private val inbox = TaskStatus("INBOX", "#87909e", "open", 0)
  private val backlog = TaskStatus("backlog", "#87909e", "unstarted", 1)
  private val inProgress = TaskStatus("in-progress", "#4194f6", "custom", 2)
  private val done = TaskStatus("done", "#6bc950", "closed", 3)
  private val listStatuses = listOf(inbox, backlog, inProgress, done)

  @Test fun visibleStatuses_onlyWhenTheFilterNarrows() {
    val present = listOf(inbox, inProgress)
    assertEquals(setOf("inprogress"), NewTaskLogic.visibleStatuses(setOf("In-Progress"), present))
    assertEquals(emptySet<String>(), NewTaskLogic.visibleStatuses(setOf("INBOX", "in-progress"), present))
    assertEquals(emptySet<String>(), NewTaskLogic.visibleStatuses(emptySet(), present))
  }

  @Test fun defaultStatus_picksAnOpenStatusTheWidgetShows() {
    assertEquals("in-progress", NewTaskLogic.defaultStatus(listStatuses, setOf("inprogress")))
    // Nothing narrowed -> the list's own default (first status).
    assertNull(NewTaskLogic.defaultStatus(listStatuses, emptySet()))
    // Never preselect a done/closed status even if the filter shows it.
    assertNull(NewTaskLogic.defaultStatus(listStatuses, setOf("done")))
    // Filter shows a status this list doesn't have -> list default.
    assertNull(NewTaskLogic.defaultStatus(listStatuses, setOf("review")))
  }

  private val ariana = RequesterOption("o1", "Ariana Carlone", "#9b59b6")
  private val becky = RequesterOption("o2", "Becky Puleo", "#800000")
  private val other = RequesterOption("o3", "Other", "#b5bcc2")
  private val field = RequesterField("req", listOf(ariana, becky, other))

  @Test fun requesterSearch_filtersAndOffersOnlyUnknownNames() {
    assertEquals(listOf(ariana, becky, other), NewTaskLogic.filterRequesters(field.options, "  "))
    assertEquals(listOf(ariana, other), NewTaskLogic.filterRequesters(field.options, "R"))
    assertEquals(listOf(becky), NewTaskLogic.filterRequesters(field.options, " becky  puleo "))
    assertNull(NewTaskLogic.newRequesterName(field.options, "   "))
    assertNull(NewTaskLogic.newRequesterName(field.options, "becky puleo"))
    assertEquals("Becky", NewTaskLogic.newRequesterName(field.options, " Becky "))
    assertEquals("Jane Doe", NewTaskLogic.newRequesterName(field.options, "  Jane \t Doe "))
  }

  @Test fun requesterFields_existingOptionOrOtherForANewName() {
    assertEquals(mapOf("req" to "o2"), NewTaskLogic.requesterFields(field, RequesterChoice.Existing(becky)))
    assertEquals(mapOf("req" to "o3"), NewTaskLogic.requesterFields(field, RequesterChoice.New("Jane Doe")))
    val noOther = RequesterField("req", listOf(ariana, becky))
    assertNull(NewTaskLogic.otherOption(noOther))
    assertEquals(emptyMap<String, String>(), NewTaskLogic.requesterFields(noOther, RequesterChoice.New("Jane Doe")))
    assertEquals(emptyMap<String, String>(), NewTaskLogic.requesterFields(field, null))
    assertEquals(emptyMap<String, String>(), NewTaskLogic.requesterFields(null, RequesterChoice.Existing(becky)))
    assertEquals("o3", NewTaskLogic.otherOption(RequesterField("r", listOf(ariana, other.copy(name = " OTHER "))))?.id)
  }

  @Test fun descriptionWith_putsANewRequesterFirst() {
    assertEquals("Requester: Jane Doe", NewTaskLogic.descriptionWith("  ", RequesterChoice.New("Jane Doe")))
    assertEquals("Requester: Jane Doe\n\nPrinter jams", NewTaskLogic.descriptionWith(" Printer jams\n", RequesterChoice.New("Jane Doe")))
    assertEquals("Printer jams", NewTaskLogic.descriptionWith(" Printer jams ", RequesterChoice.Existing(becky)))
    assertEquals("", NewTaskLogic.descriptionWith("", null))
  }

  @Test fun carryRequester_keepsWhatTheNewFieldStillHas() {
    val renamed = becky.copy(name = "Rebecca Puleo")
    assertEquals(
      RequesterChoice.Existing(renamed),
      NewTaskLogic.carryRequester(RequesterChoice.Existing(becky), RequesterField("req", listOf(renamed))),
    )
    assertNull(NewTaskLogic.carryRequester(RequesterChoice.Existing(becky), RequesterField("req", listOf(ariana))))
    assertEquals(RequesterChoice.New("Jane Doe"), NewTaskLogic.carryRequester(RequesterChoice.New("Jane Doe"), field))
    // Added in ClickUp meanwhile -> becomes the real option.
    val jane = RequesterOption("o9", "Jane Doe")
    assertEquals(
      RequesterChoice.Existing(jane),
      NewTaskLogic.carryRequester(RequesterChoice.New("jane doe"), RequesterField("req", listOf(jane))),
    )
    assertNull(NewTaskLogic.carryRequester(RequesterChoice.New("Jane Doe"), null))
    assertNull(NewTaskLogic.carryRequester(null, field))
  }

  @Test fun hiddenByFilter_usesTheListDefaultWhenNoneChosen() {
    assertTrue(NewTaskLogic.hiddenByFilter(null, listStatuses, setOf("inprogress")))
    assertFalse(NewTaskLogic.hiddenByFilter("in-progress", listStatuses, setOf("inprogress")))
    assertFalse(NewTaskLogic.hiddenByFilter(null, listStatuses, emptySet()))
    assertTrue(NewTaskLogic.hiddenByFilter("backlog", listStatuses, setOf("inprogress")))
  }
}
