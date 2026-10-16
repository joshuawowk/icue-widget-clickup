package net.jwowk.clickupwidget.data.model

import kotlinx.serialization.Serializable

/** What the widget body shows. Mirrors the iCUE states. */
@Serializable
enum class LoadState {
  /** Nothing fetched yet. */
  LOADING,
  /** No API token (and not in demo mode). */
  SETUP,
  /** Fetch failed and there is no cached data to fall back to. */
  ERROR,
  /** [WidgetSnapshot.tasks] is valid (possibly stale, see [WidgetSnapshot.offline]). */
  READY,
}

/**
 * Last fetched data for one widget. Written ONLY by the refresh path (RefreshWorker and the
 * complete worker's optimistic removal); read by the widget UI. Stored as JSON in the widget's
 * Glance preferences state under [WidgetStateKeys.SNAPSHOT].
 */
@Serializable
data class WidgetSnapshot(
  val loadState: LoadState = LoadState.LOADING,
  val tasks: List<TaskItem> = emptyList(),
  /** List-mode status order for the chips; empty in My Tasks mode. */
  val listStatuses: List<TaskStatus> = emptyList(),
  val workspaceName: String = "",
  val fetchedAtMs: Long = 0,
  /** True when the last refresh failed but cached [tasks] are still shown ("• offline"). */
  val offline: Boolean = false,
  /** With [LoadState.ERROR]: ClickUp rejected the token (401); tapping the widget opens the app to fix it. */
  val authError: Boolean = false,
  /** Human-readable error for the ERROR state, e.g. "Invalid API token — check settings". */
  val errorMessage: String? = null,
)

/**
 * Interactive, per-widget UI state (filter chips, filter bar, pending quick-completes, toast).
 * Written by widget action callbacks; stored as JSON under [WidgetStateKeys.UI].
 */
@Serializable
data class WidgetUiState(
  val filterBarOpen: Boolean = false,
  /** Selected status names. null = not yet resolved from WidgetConfig.statusDefault. */
  val selectedStatuses: Set<String>? = null,
  /** Selected Priority.key values. null = not yet seeded from WidgetConfig.priorityDefault. */
  val selectedPriorities: Set<String>? = null,
  /** Task ids tapped "complete" whose commit is still pending (Undo available). */
  val pendingCompletions: Set<String> = emptySet(),
  /** Transient message (e.g. "Could not complete task — try again"); hidden after [toastUntilMs]. */
  val toast: String? = null,
  val toastUntilMs: Long = 0,
  /** A manual refresh is in flight (header shows a spinner/"Refreshing…"). */
  val refreshing: Boolean = false,
)

/** Keys inside each widget's Glance `Preferences` state. */
object WidgetStateKeys {
  /** String: JSON of [WidgetSnapshot]. */
  const val SNAPSHOT = "snapshot"
  /** String: JSON of [WidgetUiState]. */
  const val UI = "ui"
}
