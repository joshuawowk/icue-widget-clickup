package net.jwowk.clickupwidget.ui.detail

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.ui.Nav

/**
 * Task details bottom sheet opened from a widget row (see [Nav.detailIntent]). Themed per widget;
 * closes on outside tap or back, and refreshes the widget once if something changed.
 */
class TaskDetailActivity : ComponentActivity() {
  private val viewModel: TaskDetailViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (!openFrom(intent, force = false)) {
      finish()
      return
    }
    setContent {
      val state by viewModel.state.collectAsState()
      val current = state ?: return@setContent
      LaunchedEffect(current.closeRequested) { if (current.closeRequested) finish() }
      DetailTheme(current.config) {
        TaskDetailScreen(
          state = current,
          onDismiss = ::finish,
          onSetStatus = viewModel::setStatus,
          onCompleteSubtask = viewModel::completeSubtask,
          onCompleteTask = viewModel::completeTask,
          onUndo = viewModel::undo,
          onSnackConsumed = viewModel::snackConsumed,
          onOpenInClickUp = { openInClickUp(DetailFormat.taskUrl(current.task)) },
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (!openFrom(intent, force = true)) finish()
  }

  override fun onDestroy() {
    if (isFinishing) viewModel.flushAndRefresh()
    super.onDestroy()
  }

  private fun openFrom(intent: Intent, force: Boolean): Boolean {
    val task = Nav.taskFromIntent(intent) ?: return false
    val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    viewModel.open(widgetId, task, force)
    return true
  }

  private fun openInClickUp(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(this, R.string.detail_no_app, Toast.LENGTH_SHORT).show()
    }
  }
}
