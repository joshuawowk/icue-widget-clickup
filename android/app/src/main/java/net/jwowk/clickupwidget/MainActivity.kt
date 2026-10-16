@file:OptIn(ExperimentalMaterial3Api::class)

package net.jwowk.clickupwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import net.jwowk.clickupwidget.data.model.AppSettings
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.data.model.Workspace
import net.jwowk.clickupwidget.theme.ClickUpTasksTheme
import net.jwowk.clickupwidget.ui.Nav
import net.jwowk.clickupwidget.ui.common.ClickableRow
import net.jwowk.clickupwidget.ui.common.IntSliderRow
import net.jwowk.clickupwidget.ui.common.SectionCard
import net.jwowk.clickupwidget.ui.common.SegmentedChoice
import net.jwowk.clickupwidget.ui.common.SwitchRow
import net.jwowk.clickupwidget.ui.common.TokenEntryCard
import net.jwowk.clickupwidget.ui.common.disconnectAccount
import net.jwowk.clickupwidget.widget.TasksWidgetReceiver
import net.jwowk.clickupwidget.widget.WidgetUpdater

/** Launcher screen: account, demo mode, placed widgets, global settings. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { ClickUpTasksTheme { MainScreen() } }
  }
}

/** "15 min", "1 h", "2 h". */
internal fun formatRefreshMinutes(minutes: Int): String =
  if (minutes % 60 == 0) "${minutes / 60} h" else "$minutes min"

@Composable
private fun MainScreen() {
  val context = LocalContext.current
  // Bumped on resume and after any action that changes stored state; all reads below key off it.
  var tick by remember { mutableIntStateOf(0) }
  LifecycleResumeEffect(Unit) {
    tick++
    onPauseOrDispose {}
  }

  val hasToken = remember(tick) { Graph.tokenStore.hasToken() }
  val accountLabel = remember(tick) { Graph.tokenStore.accountLabel() }
  val settings = remember(tick) { Graph.configStore.appSettings() }
  val widgets = remember(tick) {
    WidgetUpdater.placedWidgetIds(context).map { it to Graph.configStore.widgetConfig(it) }
  }

  Scaffold(
    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
  ) { padding ->
    Column(
      Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      AccountSection(hasToken, accountLabel, settings.demoMode, onChanged = { tick++ })
      DemoSection(settings) { updated ->
        Graph.configStore.saveAppSettings(updated)
        WidgetUpdater.refreshNow(context)
        tick++
      }
      WidgetsSection(widgets)
      SettingsSection(settings, onSave = { Graph.configStore.saveAppSettings(it); tick++ })
      AboutSection()
      Spacer(Modifier.size(8.dp))
    }
  }
}

@Composable
private fun AccountSection(hasToken: Boolean, label: String?, demoMode: Boolean, onChanged: () -> Unit) {
  val context = LocalContext.current
  when {
    hasToken -> {
      var workspaces by remember { mutableStateOf<List<Workspace>>(emptyList()) }
      LaunchedEffect(Unit) {
        workspaces = runCatching { Graph.repository().workspaces() }.getOrDefault(emptyList())
      }
      SectionCard(stringResource(R.string.ui_account_title)) {
        Text(label ?: stringResource(R.string.ui_account_connected), style = MaterialTheme.typography.titleMedium)
        if (workspaces.isNotEmpty()) {
          Text(
            stringResource(R.string.ui_account_workspaces, workspaces.joinToString { it.name }),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        OutlinedButton(onClick = {
          disconnectAccount(context)
          onChanged()
        }) { Text(stringResource(R.string.ui_disconnect)) }
      }
    }
    demoMode -> SectionCard(stringResource(R.string.ui_account_title)) {
      Text(
        stringResource(R.string.ui_account_demo_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    else -> TokenEntryCard(onConnected = onChanged)
  }
}

@Composable
private fun DemoSection(settings: AppSettings, onChange: (AppSettings) -> Unit) {
  SectionCard(null) {
    SwitchRow(
      label = stringResource(R.string.ui_demo_label),
      checked = settings.demoMode,
      onCheckedChange = { onChange(settings.copy(demoMode = it)) },
    )
  }
}

@Composable
private fun WidgetsSection(widgets: List<Pair<Int, WidgetConfig>>) {
  val context = LocalContext.current
  var showHelp by remember { mutableStateOf(!pinSupported(context)) }
  SectionCard(stringResource(R.string.ui_widgets_title)) {
    if (widgets.isEmpty()) {
      Text(
        stringResource(R.string.ui_widgets_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    widgets.forEach { (id, config) ->
      ClickableRow(
        title = config.title.ifBlank { stringResource(R.string.ui_widget_untitled) },
        supporting = widgetSummary(config),
        onClick = { context.startActivity(Nav.configIntent(context, id)) },
        trailing = { Icon(painterResource(R.drawable.ic_ui_chevron_right), contentDescription = null) },
      )
    }
    Button(onClick = { if (!requestPin(context)) showHelp = true }) {
      Icon(painterResource(R.drawable.ic_ui_add), contentDescription = null, Modifier.size(18.dp))
      Text(stringResource(R.string.ui_add_widget), Modifier.padding(start = 8.dp))
    }
    if (showHelp) {
      Text(
        stringResource(R.string.ui_add_widget_help),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Text(
      stringResource(R.string.ui_widget_settings_hint),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun widgetSummary(config: WidgetConfig): String {
  val source = if (config.isListMode) {
    stringResource(R.string.ui_source_list, config.listName.ifBlank { config.listId })
  } else {
    stringResource(R.string.ui_source_my_tasks)
  }
  return "$source · ${config.theme.label}"
}

private fun pinSupported(context: Context): Boolean =
  AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/** Asks the launcher to pin a widget; false when it is unsupported or the request was not accepted. */
private fun requestPin(context: Context): Boolean {
  val manager = AppWidgetManager.getInstance(context)
  if (!manager.isRequestPinAppWidgetSupported) return false
  return manager.requestPinAppWidget(ComponentName(context, TasksWidgetReceiver::class.java), null, null)
}

@Composable
private fun SettingsSection(settings: AppSettings, onSave: (AppSettings) -> Unit) {
  val context = LocalContext.current
  var undoSeconds by remember(settings.undoSeconds) { mutableIntStateOf(settings.undoSeconds) }
  SectionCard(stringResource(R.string.ui_settings_title)) {
    Text(stringResource(R.string.ui_refresh_interval), style = MaterialTheme.typography.bodyLarge)
    SegmentedChoice(
      options = AppSettings.REFRESH_CHOICES,
      selected = settings.refreshMinutes,
      optionLabel = { formatRefreshMinutes(it) },
      onSelect = {
        onSave(settings.copy(refreshMinutes = it))
        WidgetUpdater.schedulePeriodic(context)
      },
    )
    IntSliderRow(
      label = stringResource(R.string.ui_undo_delay),
      value = undoSeconds,
      range = 3..10,
      onValueChange = { undoSeconds = it },
      valueText = { stringResource(R.string.ui_seconds, it) },
      onChangeFinished = { onSave(settings.copy(undoSeconds = undoSeconds)) },
    )
    OutlinedButton(onClick = { WidgetUpdater.refreshNow(context) }) {
      Icon(painterResource(R.drawable.ic_ui_refresh), contentDescription = null, Modifier.size(18.dp))
      Text(stringResource(R.string.ui_refresh_all), Modifier.padding(start = 8.dp))
    }
  }
}

@Composable
private fun AboutSection() {
  SectionCard(stringResource(R.string.ui_about_title)) {
    Text(stringResource(R.string.ui_about_privacy), style = MaterialTheme.typography.bodyMedium)
  }
}
