package net.jwowk.clickupwidget.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.model.DueFilter
import net.jwowk.clickupwidget.data.model.PriorityDefault
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.ui.common.DropdownField
import net.jwowk.clickupwidget.ui.common.IntSliderRow
import net.jwowk.clickupwidget.ui.common.SectionCard
import net.jwowk.clickupwidget.ui.common.SegmentedChoice

@Composable
internal fun SourceSection(vm: ConfigViewModel) {
  val config = vm.config
  var showPicker by rememberSaveable { mutableStateOf(false) }
  var manualId by rememberSaveable { mutableStateOf(false) }
  val focus = LocalFocusManager.current

  SectionCard(stringResource(R.string.ui_section_source)) {
    if (vm.workspaces.size > 1) {
      val selected = vm.selectedWorkspace() ?: vm.workspaces.first()
      DropdownField(
        label = stringResource(R.string.ui_workspace),
        selected = selected,
        options = vm.workspaces,
        optionLabel = { it.name },
        onSelect = { vm.setWorkspace(it.id) },
      )
    }

    Text(stringResource(R.string.ui_show_tasks), style = MaterialTheme.typography.bodyLarge)
    SegmentedChoice(
      options = listOf(false, true),
      selected = vm.listMode,
      optionLabel = { stringResource(if (it) R.string.ui_source_single_list else R.string.ui_source_my_tasks) },
      onSelect = vm::selectListMode,
    )

    if (vm.listMode) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          config.listName.ifBlank { config.listId }.ifBlank { stringResource(R.string.ui_no_list_selected) },
          style = MaterialTheme.typography.titleSmall,
        )
        if (config.listName.isNotBlank()) {
          Text(
            stringResource(R.string.ui_list_id_value, config.listId),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (vm.listError) {
          Text(stringResource(R.string.ui_list_required), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { showPicker = true }) { Text(stringResource(R.string.ui_choose_list)) }
        TextButton(onClick = { manualId = !manualId }) { Text(stringResource(R.string.ui_enter_list_id)) }
      }
      if (manualId) {
        OutlinedTextField(
          value = config.listId,
          onValueChange = { id -> vm.setList(id.filter { !it.isWhitespace() }, "") },
          singleLine = true,
          label = { Text(stringResource(R.string.ui_list_id)) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }

    OutlinedTextField(
      value = config.title,
      onValueChange = { v -> vm.update { it.copy(title = v) } },
      singleLine = true,
      label = { Text(stringResource(R.string.ui_title)) },
      modifier = Modifier.fillMaxWidth(),
    )
    DropdownField(
      label = stringResource(R.string.ui_show),
      selected = config.dueFilter,
      options = DueFilter.entries,
      optionLabel = { it.label },
      onSelect = { v -> vm.update { it.copy(dueFilter = v) } },
    )
    DropdownField(
      label = stringResource(R.string.ui_priority_default),
      selected = config.priorityDefault,
      options = PriorityDefault.entries,
      optionLabel = { it.label },
      onSelect = { v -> vm.update { it.copy(priorityDefault = v) } },
    )
    OutlinedTextField(
      value = config.statusDefault,
      onValueChange = { v -> vm.update { it.copy(statusDefault = v) } },
      singleLine = true,
      label = { Text(stringResource(R.string.ui_default_statuses)) },
      placeholder = { Text(stringResource(R.string.ui_default_statuses_hint)) },
      supportingText = { Text(stringResource(R.string.ui_default_statuses_help)) },
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
      modifier = Modifier.fillMaxWidth(),
    )
    IntSliderRow(
      label = stringResource(R.string.ui_max_tasks),
      value = config.maxTasks,
      range = WidgetConfig.MIN_TASKS..WidgetConfig.MAX_TASKS,
      onValueChange = { v -> vm.update { it.copy(maxTasks = v) } },
    )
  }

  if (showPicker) {
    ListPickerDialog(
      load = vm::loadLists,
      onPick = { list ->
        vm.setList(list.id, list.name)
        showPicker = false
      },
      onDismiss = { showPicker = false },
    )
  }
}
