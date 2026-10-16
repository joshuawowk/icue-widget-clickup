package net.jwowk.clickupwidget.data

import net.jwowk.clickupwidget.data.model.DueFilter
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.PriorityDefault
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus
import net.jwowk.clickupwidget.data.model.WidgetConfig
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Due-date bucket; drives the chip color (overdue red, today pink, soon amber, later muted). */
enum class DueClass { OVERDUE, TODAY, SOON, LATER }

/**
 * iCUE `dueInfo`: [label] is "Overdue" / "Today" / "Tomorrow" / localized "MMM d".
 * [sort] orders tasks: overdue < 0 (older = smaller), today 0, tomorrow 1, then days ahead.
 */
data class DueInfo(val cls: DueClass, val label: String, val sort: Double)

data class TaskRow(val task: TaskItem, val due: DueInfo?)

/** Everything the widget needs to render the list + filter bar, computed by [TaskLogic.buildView]. */
data class TaskListView(
  /** Filtered, sorted, truncated to config.maxTasks. */
  val visible: List<TaskRow>,
  /** Count after all filters, before truncation (the header number). */
  val total: Int,
  /** Count after the due filter only. 0 → "All clear" empty state. */
  val scopedTotal: Int,
  /** Statuses present in the due-filtered tasks, ordered for the chips. */
  val presentStatuses: List<TaskStatus>,
  /** Priorities present in the due-filtered tasks, in Priority order. */
  val presentPriorities: List<Priority>,
  /** Effective selected status names, intersected with present ones (chip "on" state). */
  val selectedStatuses: Set<String>,
  /** Effective selected Priority.key values, intersected with present ones. */
  val selectedPriorities: Set<String>,
  /**
   * Non-null when buildView had to resolve the status selection from config.statusDefault
   * (input selection was null). The caller should persist it into WidgetUiState.selectedStatuses.
   */
  val resolvedStatusSelection: Set<String>?,
  /** Non-null when buildView seeded priorities from config.priorityDefault (input was null). Persist it. */
  val resolvedPrioritySelection: Set<String>?,
  /** A filter dimension is narrowing results (accent dot + "• filtered"). */
  val anyConstrained: Boolean,
)

/**
 * Pure, Android-free port of the iCUE widget's task logic (index.html: dueInfo, priorityRank,
 * seedFiltersFromSettings, resolveStatusSelection, renderTasks filter/sort, findClosedStatus,
 * subtask ordering). Unit-tested on the JVM.
 */
object TaskLogic {

  private const val NO_DUE_SORT = 9999.0
  private val STATUS_TYPE_RANK = mapOf("open" to 0, "custom" to 1, "done" to 2, "closed" to 3)
  private val CLOSED_NAME = Regex("^(complete|done|closed)$", RegexOption.IGNORE_CASE)
  /** Stand-in for JS `localeCompare`: case-insensitive at primary level, lowercase-first ties. */
  private val NAME_ORDER: Comparator<String> = Collator.getInstance(Locale.ROOT).let { c -> Comparator { a, b -> c.compare(a, b) } }

  /** Lowercase alphanumerics only: "In-Progress" == "in progress" == "inprogress". */
  fun normStatus(s: String): String = s.lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }

  /**
   * iCUE dueInfo. Day boundaries are computed in [zone] (calendar days, not 24h spans).
   * diff<0 → OVERDUE "Overdue" sort = -1 + diff/10000; 0 → TODAY "Today" 0; 1 → SOON "Tomorrow" 1;
   * else SOON if diff ≤ 7 else LATER, label = date formatted "MMM d" in [locale], sort = diff.
   */
  fun dueInfo(
    dueMs: Long?,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
  ): DueInfo? {
    if (dueMs == null || dueMs == 0L) return null
    val dueDay = Instant.ofEpochMilli(dueMs).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val diff = ChronoUnit.DAYS.between(today, dueDay)
    return when {
      diff < 0 -> DueInfo(DueClass.OVERDUE, "Overdue", -1.0 + diff / 10000.0)
      diff == 0L -> DueInfo(DueClass.TODAY, "Today", 0.0)
      diff == 1L -> DueInfo(DueClass.SOON, "Tomorrow", 1.0)
      else -> DueInfo(
        if (diff <= 7) DueClass.SOON else DueClass.LATER,
        dueDay.format(DateTimeFormatter.ofPattern("MMM d", locale)),
        diff.toDouble(),
      )
    }
  }

  /** iCUE seedFiltersFromSettings (priority half). ALL → every Priority key incl. "none". */
  fun seedPriorities(def: PriorityDefault): Set<String> = when (def) {
    PriorityDefault.URGENT -> setOf(Priority.URGENT.key)
    PriorityDefault.HIGH -> setOf(Priority.URGENT.key, Priority.HIGH.key)
    PriorityDefault.NORMAL -> setOf(Priority.URGENT.key, Priority.HIGH.key, Priority.NORMAL.key)
    PriorityDefault.ALL -> Priority.entries.map { it.key }.toSet()
  }

  /**
   * iCUE resolveStatusSelection: parse comma-separated [statusDefault]; select present statuses whose
   * normStatus matches; blank or no match → all present names.
   */
  fun resolveStatusSelection(present: List<TaskStatus>, statusDefault: String): Set<String> {
    val all = present.map { it.name }.toSet()
    val wanted = statusDefault.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map(::normStatus).toSet()
    if (wanted.isEmpty()) return all
    val sel = present.filter { normStatus(it.name) in wanted }.map { it.name }.toSet()
    return sel.ifEmpty { all }
  }

  /**
   * Chip order. With [listStatuses] (list mode): by the list's orderIndex (unknown = 999), then name.
   * Otherwise by status type rank open 0, custom 1, done 2, closed 3, unknown 9, then name.
   */
  fun orderStatuses(present: List<TaskStatus>, listStatuses: List<TaskStatus>): List<TaskStatus> {
    if (listStatuses.isNotEmpty()) {
      val order = listStatuses.associate { it.name to it.orderIndex }
      return present.sortedWith(compareBy<TaskStatus> { order[it.name] ?: 999 }.thenBy(NAME_ORDER) { it.name })
    }
    return present.sortedWith(compareBy<TaskStatus> { STATUS_TYPE_RANK[it.type] ?: 9 }.thenBy(NAME_ORDER) { it.name })
  }

  /**
   * iCUE renderTasks minus the DOM:
   *  1. due filter (TODAY: due && sort ≤ 0; WEEK: due && sort ≤ 7)
   *  2. present statuses (first-seen color/type per name) and priorities
   *  3. resolve null selections (status from config.statusDefault, priority from config.priorityDefault)
   *  4. a dimension narrows only if its effective selection is a proper non-empty subset of present
   *  5. sort by due sort (no due = 9999) then priority rank; stable
   *  6. truncate to config.maxTasks clamped to 3..50
   */
  fun buildView(
    tasks: List<TaskItem>,
    config: WidgetConfig,
    selectedStatuses: Set<String>?,
    selectedPriorities: Set<String>?,
    listStatuses: List<TaskStatus>,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
  ): TaskListView {
    var rows = tasks.map { TaskRow(it, dueInfo(it.dueMs, nowMs, zone, locale)) }
    rows = when (config.dueFilter) {
      DueFilter.ALL -> rows
      DueFilter.TODAY -> rows.filter { it.due != null && it.due.sort <= 0 }
      DueFilter.WEEK -> rows.filter { it.due != null && it.due.sort <= 7 }
    }

    // Statuses + priorities actually present in the in-scope tasks (drive the chips).
    val statusMap = LinkedHashMap<String, TaskStatus>()
    rows.forEach { r ->
      val t = r.task
      if (t.status.isNotEmpty() && t.status !in statusMap) {
        statusMap[t.status] = TaskStatus(t.status, t.statusColorHex, t.statusType)
      }
    }
    val presentStatuses = orderStatuses(statusMap.values.toList(), listStatuses)
    val presentStatusNames = presentStatuses.map { it.name }.toSet()
    val presentPriorities = Priority.entries.filter { p -> rows.any { it.task.priority == p } }
    val presentPrioKeys = presentPriorities.map { it.key }.toSet()

    val resolvedStatus = if (selectedStatuses == null) resolveStatusSelection(presentStatuses, config.statusDefault) else null
    val resolvedPrio = if (selectedPriorities == null) seedPriorities(config.priorityDefault) else null
    val statusSel = (selectedStatuses ?: resolvedStatus!!).filter { it in presentStatusNames }.toSet()
    val prioSel = (selectedPriorities ?: resolvedPrio!!).filter { it in presentPrioKeys }.toSet()

    // A dimension only narrows when a proper, non-empty subset is selected.
    val statusConstrained = statusSel.isNotEmpty() && statusSel.size < presentStatusNames.size
    val prioConstrained = prioSel.isNotEmpty() && prioSel.size < presentPrioKeys.size

    val filtered = rows.filter { r ->
      (!statusConstrained || r.task.status in statusSel) &&
        (!prioConstrained || r.task.priority.key in prioSel)
    }.sortedWith(compareBy<TaskRow> { it.due?.sort ?: NO_DUE_SORT }.thenBy { it.task.priority.rank })

    val max = config.maxTasks.coerceIn(WidgetConfig.MIN_TASKS, WidgetConfig.MAX_TASKS)
    return TaskListView(
      visible = filtered.take(max),
      total = filtered.size,
      scopedTotal = rows.size,
      presentStatuses = presentStatuses,
      presentPriorities = presentPriorities,
      selectedStatuses = statusSel,
      selectedPriorities = prioSel,
      resolvedStatusSelection = resolvedStatus,
      resolvedPrioritySelection = resolvedPrio,
      anyConstrained = statusConstrained || prioConstrained,
    )
  }

  /** Toggle membership of [value] (iCUE toggleSelection), returning a new set. */
  fun toggle(set: Set<String>, value: String): Set<String> = if (value in set) set - value else set + value

  /** iCUE findClosedStatus: type closed → type done → name matches ^(complete|done|closed)$ (ci) → null. */
  fun findClosedStatus(statuses: List<TaskStatus>): TaskStatus? =
    statuses.firstOrNull { it.type == "closed" }
      ?: statuses.firstOrNull { it.type == "done" }
      ?: statuses.firstOrNull { CLOSED_NAME.matches(it.name) }

  /** Detail-view subtask order: open first, then by due sort (no due = 9999); stable. */
  fun sortSubtasks(subtasks: List<TaskItem>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): List<TaskItem> =
    subtasks.sortedWith(
      compareBy<TaskItem> { it.isDone }.thenBy { dueInfo(it.dueMs, nowMs, zone, Locale.ROOT)?.sort ?: NO_DUE_SORT },
    )

  /**
   * Parse a ClickUp color ("#rgb", "#rrggbb", "#aarrggbb", case-insensitive, '#' optional) to ARGB
   * with alpha forced to FF for 3/6-digit forms. Returns [fallback] when null/blank/unparseable.
   */
  fun parseColor(hex: String?, fallback: Long = 0xFF888888): Long {
    val h = hex?.trim()?.removePrefix("#") ?: return fallback
    if (h.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return fallback
    return when (h.length) {
      3 -> 0xFF000000 or h.map { "$it$it" }.joinToString("").toLong(16)
      6 -> 0xFF000000 or h.toLong(16)
      8 -> h.toLong(16)
      else -> fallback
    }
  }
}
