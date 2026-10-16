package net.jwowk.clickupwidget.data.model

import kotlinx.serialization.Serializable

/** iCUE "Show" setting. */
@Serializable
enum class DueFilter(val label: String) {
  ALL("All open tasks"),
  TODAY("Due today + overdue"),
  WEEK("Due this week"),
}

/** iCUE "Priority (default)" setting — seeds which priority chips start selected. */
@Serializable
enum class PriorityDefault(val label: String) {
  ALL("All priorities"),
  URGENT("Urgent only"),
  HIGH("High and up"),
  NORMAL("Normal and up"),
}

/**
 * Widget color theme.
 *  - SYSTEM: Material You dynamic colors (Android 12+; wallpaper-derived, follows light/dark).
 *    On Android 8–11 falls back to the baseline Material 3 palette, still following light/dark.
 *  - CLICKUP_DARK / CLICKUP_LIGHT: the iCUE widget look (#15161c / lavender accent) and a light twin.
 *  - CLICKUP_AUTO: CLICKUP_LIGHT by day, CLICKUP_DARK at night (follows system dark mode).
 *  - CUSTOM: the user's [WidgetConfig.textColor] / [WidgetConfig.accentColor] / [WidgetConfig.backgroundColor].
 */
@Serializable
enum class ThemeMode(val label: String) {
  SYSTEM("System (Material You)"),
  CLICKUP_DARK("ClickUp dark"),
  CLICKUP_LIGHT("ClickUp light"),
  CLICKUP_AUTO("ClickUp (follow dark mode)"),
  CUSTOM("Custom colors"),
}

/** Row spacing. */
@Serializable
enum class Density(val label: String, val rowPaddingDp: Int) {
  COMPACT("Compact", 4),
  COMFORTABLE("Comfortable", 8),
  SPACIOUS("Spacious", 12),
}

/**
 * Per-widget settings, keyed by appWidgetId. Mirrors the iCUE widget's settings, plus Android-only
 * style options. Defaults match the iCUE defaults.
 */
@Serializable
data class WidgetConfig(
  // ---- Source ----
  /** Workspace (team) id; null = the first workspace on the account (iCUE behaviour). */
  val workspaceId: String? = null,
  /** Blank = "My Tasks" mode (all open tasks assigned to me); otherwise a single ClickUp list. */
  val listId: String = "",
  /** Display name of [listId], for the config screen only. */
  val listName: String = "",
  val title: String = "My Tasks",
  val dueFilter: DueFilter = DueFilter.ALL,
  val priorityDefault: PriorityDefault = PriorityDefault.ALL,
  /** Comma-separated status names pre-selected in the filter. Blank or no match = all. */
  val statusDefault: String = "In-Progress",
  /** 3..50 */
  val maxTasks: Int = 12,

  // ---- Style ----
  val theme: ThemeMode = ThemeMode.SYSTEM,
  /** ARGB, used when [theme] == CUSTOM. */
  val textColor: Long = 0xFFFFFFFF,
  val accentColor: Long = 0xFF9F8FFF,
  val backgroundColor: Long = 0xFF15161C,
  /** Background opacity 0..100 (0 = fully transparent, home screen shows through). All themes. */
  val backgroundOpacity: Int = 100,
  /** Text size scale in percent, 80..140. */
  val textScale: Int = 100,
  val density: Density = Density.COMFORTABLE,
  val showSubtitle: Boolean = true,
  val showCount: Boolean = true,
  val showListName: Boolean = true,
  val showDueChip: Boolean = true,
  val showPriorityStar: Boolean = true,
  /** Priority flag + name in each row's meta line. */
  val showPriority: Boolean = true,
  /** Tag chips in each row's meta line. */
  val showTags: Boolean = true,
  /** Optional processed (downscaled, pre-blurred) background image in app-private storage. */
  val backgroundImagePath: String? = null,
  /** 0..25 blur radius that was applied to [backgroundImagePath] (informational / for re-processing). */
  val backgroundBlur: Int = 0,
) {
  val isListMode: Boolean get() = listId.isNotBlank()

  companion object {
    const val MIN_TASKS = 3
    const val MAX_TASKS = 50
  }
}

/** App-wide settings (not per widget). */
@Serializable
data class AppSettings(
  /** Background refresh period. WorkManager's floor is 15 min; offered: 15, 30, 60, 120. */
  val refreshMinutes: Int = 30,
  /** Seconds a quick-complete waits (with Undo) before it is sent to ClickUp. 3..10 */
  val undoSeconds: Int = 4,
  /** Use built-in sample tasks instead of ClickUp (no token needed). */
  val demoMode: Boolean = false,
  /** List last used by "New task" in My Tasks mode (prefilled next time). */
  val lastNewTaskListId: String = "",
  val lastNewTaskListName: String = "",
) {
  companion object {
    val REFRESH_CHOICES = listOf(15, 30, 60, 120)
  }
}
