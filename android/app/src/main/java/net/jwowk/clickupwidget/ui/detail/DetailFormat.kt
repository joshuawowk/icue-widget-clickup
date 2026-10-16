package net.jwowk.clickupwidget.ui.detail

import net.jwowk.clickupwidget.data.DueClass
import net.jwowk.clickupwidget.data.DueInfo
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskField
import net.jwowk.clickupwidget.data.model.TaskItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Pure, Android-free text helpers for the detail sheet (ports of the iCUE detail-view formatting). */
internal object DetailFormat {
  const val DESCRIPTION_LIMIT = 600
  const val TASK_NAME_SNACK_MAX = 40
  const val SUBTASK_NAME_SNACK_MAX = 36

  /** iCUE fmtFullDue: "Tue, Jun 3". */
  fun fullDate(ms: Long, zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern("EEE, MMM d", locale))

  /** Due chip text: overdue/today get the full date appended ("Overdue • Tue, Jun 3"); others keep the short label. */
  fun dueChipLabel(
    due: DueInfo,
    dueMs: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
  ): String =
    if (dueMs != null && (due.cls == DueClass.OVERDUE || due.cls == DueClass.TODAY)) {
      "${due.label} • ${fullDate(dueMs, zone, locale)}"
    } else {
      due.label
    }

  /** Description as shown: [text] and whether a Show more/less toggle is needed. */
  data class Description(val text: String, val collapsible: Boolean)

  fun description(raw: String, expanded: Boolean, limit: Int = DESCRIPTION_LIMIT): Description {
    val text = raw.trim()
    if (text.length <= limit) return Description(text, false)
    return if (expanded) Description(text, true) else Description(text.take(limit).trimEnd() + "…", true)
  }

  /** Name shortened for a snackbar ("…" appended when cut). */
  fun ellipsize(name: String, max: Int): String = if (name.length > max) name.take(max) + "…" else name

  fun taskUrl(task: TaskItem): String = task.url?.takeIf { it.isNotBlank() } ?: "https://app.clickup.com/t/${task.id}"

  fun isCurrentStatus(candidate: String, current: String): Boolean = candidate.equals(current, ignoreCase = true)

  /** The priority badge color: ClickUp's own color for the task, else the Priority default. */
  fun priorityColor(task: TaskItem): Long = TaskLogic.parseColor(task.priorityColorHex, task.priority.chipColor)

  /** Max lines a field value shows before its "Show more" toggle. */
  const val FIELD_MAX_LINES = 4

  /** Priority pills in display order: Urgent, High, Normal, Low, None. */
  val priorityChoices: List<Priority> = listOf(Priority.URGENT, Priority.HIGH, Priority.NORMAL, Priority.LOW, Priority.NONE)

  /** The FIELDS section is shown only when there is at least one populated field. */
  fun showFields(fields: List<TaskField>): Boolean = fields.any { it.name.isNotBlank() && it.value.isNotBlank() }

  /** The TAGS section is shown only when the task has tags. */
  fun showTags(task: TaskItem): Boolean = task.tags.isNotEmpty()

  /** ClickUp's background color for [tag] as ARGB, or null when it has none (chip falls back to muted). */
  fun tagColor(task: TaskItem, tag: String): Long? {
    val hex = task.tagColors[tag]?.takeIf { it.isNotBlank() } ?: return null
    val c = TaskLogic.parseColor(hex, -1L)
    return c.takeIf { it != -1L }
  }

  fun isCurrentPriority(candidate: Priority, current: Priority): Boolean = candidate == current

  /**
   * The task to display once the detail request returns. [fresh] wins (tags, tag colors, priority...) except
   * that the list is kept when the response omits it, the priority is kept while a local change is saved
   * or has been saved ([keepPriority]), and the status is kept while a status change is saving ([keepStatus]).
   */
  fun mergeFreshTask(current: TaskItem, fresh: TaskItem, keepPriority: Boolean, keepStatus: Boolean): TaskItem {
    var t = fresh.copy(
      listId = fresh.listId ?: current.listId,
      listName = fresh.listName.ifBlank { current.listName },
      url = fresh.url ?: current.url,
    )
    if (keepPriority) t = t.copy(priority = current.priority, priorityColorHex = current.priorityColorHex)
    if (keepStatus) t = t.copy(status = current.status, statusColorHex = current.statusColorHex, statusType = current.statusType)
    return t
  }
}
