package net.jwowk.clickupwidget.ui.newtask

import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.api.REQUESTER_FIELD
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.data.model.ListRef
import net.jwowk.clickupwidget.data.model.NoTokenException
import net.jwowk.clickupwidget.data.model.RequesterField
import net.jwowk.clickupwidget.data.model.RequesterOption
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig

/** The due-date chips on the New task sheet. */
internal enum class DueChoice { NONE, TODAY, TOMORROW, PICK }

/** The requester picked on the New task sheet: an option of the list's Requester drop-down, or a new name. */
internal sealed interface RequesterChoice {
  val name: String

  data class Existing(val option: RequesterOption) : RequesterChoice {
    override val name: String get() = option.name
  }

  /** Not a drop-down option (ClickUp's API can't add one): saved as the "Other" option plus a description line. */
  data class New(override val name: String) : RequesterChoice
}

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

  /**
   * The statuses this widget's Status filter currently shows: the effective chip selection when it narrows
   * the list (a proper non-empty subset of the statuses present), else empty = every status is shown.
   */
  fun visibleStatuses(selected: Set<String>, present: List<TaskStatus>): Set<String> =
    if (selected.isNotEmpty() && selected.size < present.size) selected.map(TaskLogic::normStatus).toSet() else emptySet()

  /**
   * Status preselected for a new task: the first open (not done/closed) status of the list that the widget's
   * filter shows, so the task appears in the widget right away. Null = the list's default (first) status.
   */
  fun defaultStatus(statuses: List<TaskStatus>, visible: Set<String>): String? {
    if (visible.isEmpty()) return null
    return statuses.firstOrNull { !isDoneType(it.type) && TaskLogic.normStatus(it.name) in visible }?.name
  }

  /** True when a task created in [status] (null = first of [statuses]) would be hidden by the widget's filter. */
  fun hiddenByFilter(status: String?, statuses: List<TaskStatus>, visible: Set<String>): Boolean {
    if (visible.isEmpty()) return false
    val effective = status ?: statuses.firstOrNull()?.name ?: return false
    return TaskLogic.normStatus(effective) !in visible
  }

  private fun isDoneType(type: String) = type == "done" || type == "closed"

  /** The drop-down option a new requester is filed under, when the field has one. */
  private const val OTHER_OPTION = "Other"

  /** A typed name, trimmed, with runs of whitespace collapsed. */
  fun cleanName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

  /** Options whose name contains [query]; all of them for a blank query. */
  fun filterRequesters(options: List<RequesterOption>, query: String): List<RequesterOption> {
    val q = cleanName(query)
    return if (q.isEmpty()) options else options.filter { it.name.contains(q, ignoreCase = true) }
  }

  /** [query] as a new requester to offer, or null when it is blank or already an option (any case). */
  fun newRequesterName(options: List<RequesterOption>, query: String): String? =
    cleanName(query).takeIf { q -> q.isNotEmpty() && options.none { it.name.equals(q, ignoreCase = true) } }

  /** The field's "Other" option (any case), if it has one. */
  fun otherOption(field: RequesterField?): RequesterOption? =
    field?.options?.firstOrNull { it.name.trim().equals(OTHER_OPTION, ignoreCase = true) }

  /**
   * The choice kept when the field (re)loads, e.g. for another list: an option only if this field still has it,
   * a new name always — or as the option when ClickUp has one by that name now. Nothing when there is no field.
   */
  fun carryRequester(choice: RequesterChoice?, field: RequesterField?): RequesterChoice? {
    if (field == null) return null
    return when (choice) {
      is RequesterChoice.Existing -> field.options.firstOrNull { it.id == choice.option.id }?.let(RequesterChoice::Existing)
      is RequesterChoice.New ->
        field.options.firstOrNull { it.name.equals(choice.name, ignoreCase = true) }?.let(RequesterChoice::Existing) ?: choice
      null -> null
    }
  }

  /** `custom_fields` for the create request: the chosen option; for a new name the "Other" option (if any). */
  fun requesterFields(field: RequesterField?, choice: RequesterChoice?): Map<String, String> {
    if (field == null) return emptyMap()
    val option = when (choice) {
      is RequesterChoice.Existing -> choice.option
      is RequesterChoice.New -> otherOption(field)
      null -> null
    } ?: return emptyMap()
    return mapOf(field.id to option.id)
  }

  /** The description line that records a new requester, e.g. "Requester: Jane Doe". */
  fun requesterLine(name: String): String = "$REQUESTER_FIELD: $name"

  /** The description sent: a new requester's [requesterLine] first, as the drop-down can't hold the name. */
  fun descriptionWith(description: String, choice: RequesterChoice?): String {
    val body = description.trim()
    if (choice !is RequesterChoice.New) return body
    val line = requesterLine(choice.name)
    return if (body.isEmpty()) line else "$line\n\n$body"
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
