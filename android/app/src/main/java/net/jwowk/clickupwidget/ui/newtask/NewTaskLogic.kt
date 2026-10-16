package net.jwowk.clickupwidget.ui.newtask

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.WidgetConfig

/** The due-date chips on the New task sheet. */
internal enum class DueChoice { NONE, TODAY, TOMORROW, PICK }

/** Why creating a task failed, for the inline error. */
internal enum class NewTaskError { INVALID_TOKEN, LIST_NOT_FOUND, NETWORK, SERVER, UNKNOWN }

/** Pure rules behind the New task sheet (free of Android types so they unit-test on the JVM). */
internal object NewTaskLogic {
  /** Due dates are stored at 12:00 local so a time-zone shift can't flip the calendar day. */
  private val DUE_TIME: LocalTime = LocalTime.NOON
  private const val DAY_MS = 86_400_000L

  /**
   * Epoch millis for [choice]: NONE is null; TODAY/TOMORROW are relative to [nowMs] in [zone];
   * PICK uses [pickedEpochDay] (null while nothing is picked yet).
   */
  fun dueMs(choice: DueChoice, pickedEpochDay: Long?, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val date = when (choice) {
      DueChoice.NONE -> return null
      DueChoice.TODAY -> today
      DueChoice.TOMORROW -> today.plusDays(1)
      DueChoice.PICK -> LocalDate.ofEpochDay(pickedEpochDay ?: return null)
    }
    return date.atTime(DUE_TIME).atZone(zone).toInstant().toEpochMilli()
  }

  /** The Material date picker reports the chosen day as UTC midnight; this is that day's epoch-day. */
  fun epochDayOfPicker(utcMidnightMs: Long): Long = Math.floorDiv(utcMidnightMs, DAY_MS)

  /** Inverse of [epochDayOfPicker], to seed the picker. */
  fun pickerMsOfEpochDay(epochDay: Long): Long =
    LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

  /** Create needs a non-blank name and a list, and nothing in flight. */
  fun canCreate(name: String, listId: String?, busy: Boolean): Boolean =
    !busy && name.isNotBlank() && !listId.isNullOrBlank()

  /** An unassigned task never matches the "assigned to me" query, so warn in My Tasks mode. */
  fun showUnassignedNote(assignToMe: Boolean, listMode: Boolean): Boolean = !assignToMe && !listMode

  /**
   * The list the sheet starts on: the widget's list in list mode, otherwise the last list used from
   * "New task" (or null if there is none yet, or it was picked in another scope). [scope] is the current
   * scope ("demo" or the workspace id; null while unknown) and must equal [AppSettings.lastNewTaskScope].
   */
  fun initialList(config: WidgetConfig, settings: AppSettings, scope: String?): ListRef? = when {
    config.isListMode -> ListRef(config.listId, config.listName.ifBlank { config.listId }, "")
    settings.lastNewTaskListId.isNotBlank() && scope != null && settings.lastNewTaskScope == scope ->
      ListRef(settings.lastNewTaskListId, settings.lastNewTaskListName.ifBlank { settings.lastNewTaskListId }, "")
    else -> null
  }

  fun errorFor(t: Throwable): NewTaskError = when {
    t is NoTokenException -> NewTaskError.INVALID_TOKEN
    t is ClickUpApiException && t.code == 401 -> NewTaskError.INVALID_TOKEN
    t is ClickUpApiException && (t.code == 404 || t.code == 403) -> NewTaskError.LIST_NOT_FOUND
    t is ClickUpApiException -> NewTaskError.SERVER
    t is IOException -> NewTaskError.NETWORK
    else -> NewTaskError.UNKNOWN
  }
}
