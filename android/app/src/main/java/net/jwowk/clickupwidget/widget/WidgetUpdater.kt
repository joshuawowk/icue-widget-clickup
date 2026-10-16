package net.jwowk.clickupwidget.widget

import android.content.Context

// STUB — owned by the widget agent. Keep the public API exactly as declared.

/** Entry points the app screens use to drive the widget(s). */
object WidgetUpdater {
  /** Called from Application.onCreate: (re)schedule periodic refresh; publish generated previews (API 35+). */
  fun onAppStart(context: Context): Unit = TODO()

  /** Fetch now (one widget, or all when null) via expedited one-time work; shows "refreshing". */
  fun refreshNow(context: Context, appWidgetId: Int? = null): Unit = TODO()

  /** Re-render one widget from cached state + current config, no network (after a style change). */
  suspend fun rerender(context: Context, appWidgetId: Int): Unit = TODO()

  /** Reset a widget's filter chips/bar to its config defaults (iCUE resets filters on settings change). */
  suspend fun resetFilters(context: Context, appWidgetId: Int): Unit = TODO()

  /** (Re)schedule the periodic refresh from AppSettings.refreshMinutes; cancel when no widgets are placed. */
  fun schedulePeriodic(context: Context): Unit = TODO()

  /** App widget ids of this provider currently on any home screen. */
  fun placedWidgetIds(context: Context): IntArray = TODO()
}
