@file:OptIn(ExperimentalMaterial3Api::class)

package net.jwowk.clickupwidget.ui.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.ui.common.TokenEntryCard

/** Per-widget settings: sticky live preview on top, Source and Style forms below, Save in the top and bottom bars. */
@Composable
internal fun ConfigScreen(vm: ConfigViewModel, saving: Boolean, onSave: () -> Unit, onCancel: () -> Unit) {
  var previewVisible by rememberSaveable { mutableStateOf(true) }
  var previewSize by rememberSaveable { mutableStateOf(PreviewSize.LARGE) }
  val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.ui_config_title)) },
        navigationIcon = {
          IconButton(onClick = onCancel, enabled = !saving) {
            Icon(painterResource(R.drawable.ic_ui_close), contentDescription = stringResource(R.string.ui_cancel))
          }
        },
        actions = { TextButton(onClick = onSave, enabled = !saving) { Text(stringResource(R.string.ui_save)) } },
      )
    },
    bottomBar = {
      Surface(tonalElevation = 3.dp) {
        Button(
          onClick = onSave,
          enabled = !saving,
          modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(stringResource(R.string.ui_save)) }
      }
    },
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
  ) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
      // Pinned above the scrolling form. Hidden while the keyboard is up to leave room for the field being edited.
      if (!imeVisible) {
        Row(
          Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(stringResource(R.string.ui_preview), style = MaterialTheme.typography.titleSmall)
          IconButton(onClick = { previewVisible = !previewVisible }) {
            Icon(
              painterResource(if (previewVisible) R.drawable.ic_ui_expand_less else R.drawable.ic_ui_expand_more),
              contentDescription = stringResource(if (previewVisible) R.string.ui_hide_preview else R.string.ui_show_preview),
            )
          }
        }
      }
      AnimatedVisibility(previewVisible && !imeVisible) {
        WidgetPreviewPane(
          config = vm.effectiveConfig(),
          workspaceName = vm.selectedWorkspace()?.name ?: stringResource(R.string.ui_preview_workspace),
          size = previewSize,
          onSizeChange = { previewSize = it },
          modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
      }

      Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        if (vm.needsSetup) {
          TokenEntryCard(onConnected = vm::accountChanged) {
            TextButton(onClick = vm::useSampleTasks) { Text(stringResource(R.string.ui_use_sample_tasks)) }
          }
        }
        SourceSection(vm)
        StyleSection(vm)
        Text(
          stringResource(R.string.ui_config_launcher_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
      }
    }
  }
}
