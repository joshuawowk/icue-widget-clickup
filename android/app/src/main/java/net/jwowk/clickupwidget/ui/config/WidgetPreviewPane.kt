package net.jwowk.clickupwidget.ui.config

import android.widget.FrameLayout
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.DemoTaskRepository
import net.jwowk.clickupwidget.data.model.LoadState
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.WidgetSnapshot
import net.jwowk.clickupwidget.ui.common.SegmentedChoice
import net.jwowk.clickupwidget.widget.renderWidgetPreview

/** Widget sizes the preview can show. A null width means "as wide as the screen allows" (capped). */
enum class PreviewSize(@param:StringRes val labelRes: Int, val widthDp: Int?, val heightDp: Int) {
  SMALL(R.string.ui_preview_small, 140, 140),
  MEDIUM(R.string.ui_preview_medium, 300, 150),
  LARGE(R.string.ui_preview_large, null, 300),
}

private const val LARGE_MAX_WIDTH_DP = 420
private const val RENDER_DEBOUNCE_MS = 150L

/**
 * Live preview of the real widget UI for the unsaved [config], with a size switch. Re-renders ~150 ms
 * after the last change by applying `RemoteViews` into a plain FrameLayout.
 */
@Composable
fun WidgetPreviewPane(
  config: WidgetConfig,
  workspaceName: String,
  size: PreviewSize,
  onSizeChange: (PreviewSize) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SegmentedChoice(PreviewSize.entries, size, { stringResource(it.labelRes) }, onSizeChange)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
      val width: Dp = (size.widthDp?.dp ?: LARGE_MAX_WIDTH_DP.dp).coerceAtMost(maxWidth)
      val height = size.heightDp.dp
      PreviewFrame(config, workspaceName, DpSize(width, height))
    }
  }
}

@Composable
private fun PreviewFrame(config: WidgetConfig, workspaceName: String, size: DpSize) {
  val context = LocalContext.current
  var frame by remember { mutableStateOf<FrameLayout?>(null) }
  var failed by remember { mutableStateOf(false) }

  LaunchedEffect(frame, config, workspaceName, size) {
    val host = frame ?: return@LaunchedEffect
    delay(RENDER_DEBOUNCE_MS)
    val ok = try {
      val snapshot = WidgetSnapshot(
        loadState = LoadState.READY,
        tasks = DemoTaskRepository.sampleTasks(System.currentTimeMillis()),
        workspaceName = workspaceName,
      )
      val views = renderWidgetPreview(context, config, snapshot, size)
      val rendered = views.apply(context, host)
      host.removeAllViews()
      host.addView(rendered)
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      false
    }
    if (!ok) host.removeAllViews()
    failed = !ok
  }

  // A neutral two-tone backdrop so the effect of background opacity is visible.
  val backdrop = Brush.linearGradient(listOf(Color(0xFF3B4B6B), Color(0xFF7A4B6B)))
  Box(
    Modifier
      .fillMaxWidth()
      .height(size.height + 24.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(backdrop),
    contentAlignment = Alignment.Center,
  ) {
    AndroidView(
      factory = { FrameLayout(it).also { view -> frame = view } },
      modifier = Modifier.size(size.width, size.height),
    )
    if (failed) {
      Text(
        stringResource(R.string.ui_preview_unavailable),
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}
