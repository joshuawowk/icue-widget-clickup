package net.jwowk.clickupwidget.data.model

import kotlinx.serialization.Serializable

/**
 * ClickUp priority vocabulary plus a NONE bucket for unprioritized tasks.
 * Order, labels and chip colors match the iCUE widget (PRIO_ORDER / PRIO_LABEL / PRIO_COLOR).
 */
@Serializable
enum class Priority(val key: String, val label: String, val rank: Int, val chipColor: Long) {
  URGENT("urgent", "Urgent", 0, 0xFFF50000),
  HIGH("high", "High", 1, 0xFFFFCC00),
  NORMAL("normal", "Normal", 2, 0xFF6FDDFF),
  LOW("low", "Low", 3, 0xFFB5BCC9),
  NONE("none", "None", 4, 0xFF6B7280);

  /** Urgent/high tasks get the filled accent star in the list. */
  val starred: Boolean get() = this == URGENT || this == HIGH

  companion object {
    fun fromKey(key: String?): Priority = entries.firstOrNull { it.key == key?.lowercase() } ?: NONE
  }
}

/**
 * A status as configured on a ClickUp list (or as seen on a task).
 * [type] is ClickUp's status type: "open", "custom", "done" or "closed".
 */
@Serializable
data class TaskStatus(
  val name: String,
  val colorHex: String = "#888888",
  val type: String = "",
  val orderIndex: Int = 0,
)

/**
 * A task already "shaped" from the API (the iCUE `shapeTask`). This is what the widget caches
 * and renders, so keep it small and serializable. Due-date classification is NOT stored here —
 * it depends on "now" and is computed at render time by [net.jwowk.clickupwidget.data.TaskLogic.dueInfo].
 */
@Serializable
data class TaskItem(
  val id: String,
  val name: String,
  /** List the task lives in. Subtasks come back with list=null — callers inherit the parent's. */
  val listId: String? = null,
  val listName: String = "",
  /** Due date, epoch millis, or null. */
  val dueMs: Long? = null,
  val priority: Priority = Priority.NONE,
  /** ClickUp's own color for the task's priority, e.g. "#f50000"; null if unprioritized. */
  val priorityColorHex: String? = null,
  val status: String = "",
  val statusColorHex: String = "#888888",
  val statusType: String = "",
  val tags: List<String> = emptyList(),
  /** Web URL of the task (https://app.clickup.com/t/<id>). */
  val url: String? = null,
  val parentId: String? = null,
) {
  /** True when the task's status type is closed/done. */
  val isDone: Boolean get() = statusType == "closed" || statusType == "done"
}

/** Full detail for the detail screen. */
@Serializable
data class TaskDetail(
  val task: TaskItem,
  /** Plain-text description (text_content), possibly empty. */
  val description: String,
  /** Direct children only, each with listId inherited from the parent when the API omits it. */
  val subtasks: List<TaskItem>,
)

@Serializable
data class Workspace(val id: String, val name: String)

/** A ClickUp list for the list picker. [path] is "Space / Folder" (or just "Space" if folderless). */
@Serializable
data class ListRef(val id: String, val name: String, val path: String)

/** Result of verifying an API token. */
data class AccountInfo(
  val userId: Long,
  val username: String,
  val email: String,
  val workspaces: List<Workspace>,
)

/** Raw result of a task fetch for one widget. Unfiltered and unsorted — TaskLogic does that. */
data class TaskFetch(
  val tasks: List<TaskItem>,
  /** Workspace (team) name shown as the widget subtitle. */
  val workspaceName: String,
  /** In list mode: the list's statuses in configured order (drives chip order). Empty in My Tasks mode. */
  val listStatuses: List<TaskStatus>,
)

/** Thrown by the repository when no API token is configured (widget shows the setup state). */
class NoTokenException : Exception("No ClickUp API token configured")

/** Non-2xx from ClickUp. 401 means the token is invalid. */
class ClickUpApiException(val code: Int, message: String = "HTTP $code") : Exception(message)
