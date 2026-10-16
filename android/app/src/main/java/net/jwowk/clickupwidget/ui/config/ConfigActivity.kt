package net.jwowk.clickupwidget.ui.config

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import kotlinx.coroutines.launch
import net.jwowk.clickupwidget.Graph
import net.jwowk.clickupwidget.theme.ClickUpTasksTheme
import net.jwowk.clickupwidget.widget.WidgetUpdater

/**
 * Per-widget settings. Launched by the home screen with EXTRA_APPWIDGET_ID when a widget is placed
 * (result must be RESULT_OK or the launcher removes the widget) and via [net.jwowk.clickupwidget.ui.Nav.configIntent].
 */
class ConfigActivity : ComponentActivity() {
  private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
  private var saving by mutableStateOf(false)

  private val vm: ConfigViewModel by viewModels {
    viewModelFactory { initializer { ConfigViewModel(application, appWidgetId) } }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
      ?: AppWidgetManager.INVALID_APPWIDGET_ID
    setResult(RESULT_CANCELED, resultIntent())
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish()
      return
    }
    enableEdgeToEdge()
    setContent {
      ClickUpTasksTheme {
        ConfigScreen(vm, saving = saving, onSave = ::save, onCancel = ::finish)
      }
    }
  }

  private fun resultIntent() = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

  private fun save() {
    if (saving) return
    saving = true
    lifecycleScope.launch {
      try {
        val config = vm.commit() ?: return@launch
        Graph.configStore.saveWidgetConfig(appWidgetId, config)
        // Set before the (slower) widget refresh so the launcher keeps the widget even if we are cut short.
        setResult(RESULT_OK, resultIntent())
        runCatching {
          WidgetUpdater.resetFilters(this@ConfigActivity, appWidgetId)
          WidgetUpdater.rerender(this@ConfigActivity, appWidgetId)
          WidgetUpdater.refreshNow(this@ConfigActivity, appWidgetId)
          WidgetUpdater.schedulePeriodic(this@ConfigActivity)
        }.onFailure { Log.w(TAG, "Widget update after save failed", it) }
        finish()
      } finally {
        saving = false
      }
    }
  }

  private companion object {
    const val TAG = "ConfigActivity"
  }
}
