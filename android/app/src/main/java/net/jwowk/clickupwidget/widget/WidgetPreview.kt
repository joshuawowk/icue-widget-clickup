package net.jwowk.clickupwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot

/**
 * Renders the real widget UI for an (unsaved) [config] and [snapshot] at [size] into RemoteViews,
 * for the config screen's live preview (`remoteViews.apply(context, parent)` in an AndroidView).
 * Click actions in the result are inert/irrelevant.
 */
suspend fun renderWidgetPreview(
  context: Context,
  config: WidgetConfig,
  snapshot: WidgetSnapshot,
  size: DpSize,
): RemoteViews =
  PreviewWidget(WidgetState(config = config, snapshot = snapshot))
    .compose(context = context, size = size, state = emptyPreferences())

/** Renders fixed content only — never reads or writes any placed widget's state. */
private class PreviewWidget(private val state: WidgetState) : GlanceAppWidget() {
  override val sizeMode: SizeMode = SizeMode.Single

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    provideContent { GlanceTheme { WidgetRoot(state, WidgetEnv(AppWidgetManager.INVALID_APPWIDGET_ID, false)) } }
  }
}
