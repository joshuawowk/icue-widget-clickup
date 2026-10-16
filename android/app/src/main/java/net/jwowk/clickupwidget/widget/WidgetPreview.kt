package net.jwowk.clickupwidget.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot

// STUB — owned by the widget agent. Keep the signature exactly as declared.

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
): RemoteViews = TODO()
