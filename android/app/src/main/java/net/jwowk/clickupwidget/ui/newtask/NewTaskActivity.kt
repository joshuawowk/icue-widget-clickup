package net.jwowk.clickupwidget.ui.newtask

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import net.jwowk.clickupwidget.ui.Nav
import net.jwowk.clickupwidget.ui.detail.DetailTheme

/**
 * "New task" bottom sheet opened from the widget header's + button (see [Nav.newTaskIntent]).
 * Themed per widget like the detail sheet; closes on outside tap, back, or after a successful create.
 */
class NewTaskActivity : ComponentActivity() {
  private val viewModel: NewTaskViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    openFrom(intent, force = false)
    setContent {
      LaunchedEffect(viewModel.created) { if (viewModel.created) finish() }
      DetailTheme(viewModel.config) {
        NewTaskScreen(viewModel, onDismiss = ::finish)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    openFrom(intent, force = true)
  }

  private fun openFrom(intent: Intent, force: Boolean) {
    viewModel.open(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID), force)
  }
}
