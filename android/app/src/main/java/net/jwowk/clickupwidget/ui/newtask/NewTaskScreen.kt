package net.jwowk.clickupwidget.ui.newtask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.ui.common.SwitchRow
import net.jwowk.clickupwidget.ui.config.ListPickerDialog

/** The bottom-anchored New task sheet over a tap-to-dismiss scrim area (same pattern as the detail sheet). */
@Composable
internal fun NewTaskScreen(vm: NewTaskViewModel, onDismiss: () -> Unit) {
  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { shown = true }

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
      .imePadding(),
  ) {
    val maxSheetHeight = maxHeight * 0.92f
    AnimatedVisibility(
      visible = shown,
      modifier = Modifier.align(Alignment.BottomCenter),
      enter = slideInVertically { it },
    ) {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = maxSheetHeight)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
      ) {
        SheetContent(vm, onDismiss)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetContent(vm: NewTaskViewModel, onDismiss: () -> Unit) {
  var showListPicker by rememberSaveable { mutableStateOf(false) }
  var showDatePicker by rememberSaveable { mutableStateOf(false) }
  var showRequesterPicker by rememberSaveable { mutableStateOf(false) }
  val nameFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { nameFocus.requestFocus() } }

  Column(
    Modifier
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(horizontal = 20.dp)
      .padding(top = 8.dp, bottom = 20.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Box(
      Modifier
        .align(Alignment.CenterHorizontally)
        .size(width = 32.dp, height = 4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(MaterialTheme.colorScheme.outlineVariant),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        stringResource(R.string.newtask_title),
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
      )
      IconButton(onClick = onDismiss) {
        Icon(painterResource(R.drawable.ic_ui_close), stringResource(R.string.newtask_close))
      }
    }

    OutlinedTextField(
      value = vm.name,
      onValueChange = { vm.name = it },
      modifier = Modifier.fillMaxWidth().focusRequester(nameFocus),
      label = { Text(stringResource(R.string.newtask_name)) },
      singleLine = true,
      enabled = !vm.busy,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
    )

    ListRow(vm, onChange = { showListPicker = true })

    if (vm.list != null) StatusSection(vm)

    if (vm.list != null) RequesterSection(vm, onChoose = { showRequesterPicker = true })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FieldLabel(stringResource(R.string.newtask_due))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        DueChip(stringResource(R.string.newtask_due_none), vm.dueChoice == DueChoice.NONE, !vm.busy) { vm.chooseDue(DueChoice.NONE) }
        DueChip(stringResource(R.string.newtask_due_today), vm.dueChoice == DueChoice.TODAY, !vm.busy) { vm.chooseDue(DueChoice.TODAY) }
        DueChip(stringResource(R.string.newtask_due_tomorrow), vm.dueChoice == DueChoice.TOMORROW, !vm.busy) { vm.chooseDue(DueChoice.TOMORROW) }
        val picked = vm.pickedEpochDay
        DueChip(
          if (picked != null && vm.dueChoice == DueChoice.PICK) formatDay(picked) else stringResource(R.string.newtask_due_pick),
          vm.dueChoice == DueChoice.PICK,
          !vm.busy,
        ) { showDatePicker = true }
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FieldLabel(stringResource(R.string.newtask_priority))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PRIORITY_ORDER.forEach { p -> PriorityChip(p, vm.priority == p, !vm.busy) { vm.priority = p } }
      }
    }

    OutlinedTextField(
      value = vm.description,
      onValueChange = { vm.description = it },
      modifier = Modifier.fillMaxWidth(),
      label = { Text(stringResource(R.string.newtask_description)) },
      minLines = 3,
      maxLines = 6,
      enabled = !vm.busy,
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      SwitchRow(
        label = stringResource(R.string.newtask_assign_me),
        checked = vm.assignToMe,
        onCheckedChange = { if (!vm.busy) vm.assignToMe = it },
      )
      if (NewTaskLogic.showUnassignedNote(vm.assignToMe, vm.config.isListMode)) {
        Text(
          stringResource(R.string.newtask_unassigned_note),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    vm.error?.let { err ->
      Text(
        stringResource(errorRes(err)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
      )
    }

    Button(onClick = vm::create, enabled = vm.canCreate, modifier = Modifier.fillMaxWidth()) {
      if (vm.busy) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        Text(stringResource(R.string.newtask_creating), Modifier.padding(start = 8.dp))
      } else {
        Text(stringResource(R.string.newtask_create))
      }
    }
  }

  if (showListPicker) {
    ListPickerDialog(
      load = vm::loadLists,
      onPick = { picked ->
        vm.chooseList(picked)
        showListPicker = false
      },
      onDismiss = { showListPicker = false },
    )
  }
  val requesterField = vm.requesterField
  if (showRequesterPicker && requesterField != null) {
    RequesterPickerDialog(
      options = requesterField.options,
      selected = vm.requester,
      onPick = { choice ->
        vm.chooseRequester(choice)
        showRequesterPicker = false
      },
      onDismiss = { showRequesterPicker = false },
    )
  }
  if (showDatePicker) {
    DueDatePicker(
      initialEpochDay = vm.pickedEpochDay,
      onPicked = { day ->
        vm.pickDate(day)
        showDatePicker = false
      },
      onDismiss = { showDatePicker = false },
    )
  }
}

private val PRIORITY_ORDER = listOf(Priority.NONE, Priority.URGENT, Priority.HIGH, Priority.NORMAL, Priority.LOW)

private fun errorRes(e: NewTaskError): Int = when (e) {
  NewTaskError.INVALID_TOKEN -> R.string.newtask_error_token
  NewTaskError.LIST_NOT_FOUND -> R.string.newtask_error_list
  NewTaskError.NETWORK -> R.string.ui_error_network
  NewTaskError.SERVER -> R.string.ui_error_server
  NewTaskError.UNKNOWN -> R.string.ui_error_unknown
}

private fun formatDay(epochDay: Long): String =
  DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LocalDate.ofEpochDay(epochDay))

@Composable
private fun FieldLabel(text: String) {
  Text(
    text.uppercase(),
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.8.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/** The target list with a Change / Choose button (list mode shows the widget's list, changeable for this task). */
@Composable
private fun ListRow(vm: NewTaskViewModel, onChange: () -> Unit) {
  val list = vm.list
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      FieldLabel(stringResource(R.string.newtask_list))
      if (list != null) {
        Text(list.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      } else {
        Text(
          stringResource(R.string.newtask_list_none),
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    TextButton(onClick = onChange, enabled = !vm.busy) {
      Text(stringResource(if (list == null) R.string.newtask_list_choose else R.string.newtask_list_change))
    }
  }
}

/** The chosen list's statuses as chips (null selection = the list's default, its first status). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection(vm: NewTaskViewModel) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    FieldLabel(stringResource(R.string.newtask_status))
    when {
      vm.statusesLoading -> Text(
        stringResource(R.string.newtask_status_loading),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      vm.statuses.isEmpty() -> Text(
        stringResource(if (vm.statusesFailed) R.string.newtask_status_failed else R.string.newtask_status_default),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val effective = vm.status ?: vm.statuses.first().name
        vm.statuses.forEach { s ->
          val color = Color(TaskLogic.parseColor(s.colorHex))
          FilterChip(
            selected = s.name == effective,
            onClick = { vm.status = s.name },
            enabled = !vm.busy,
            label = { Text(s.name) },
            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) },
            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.25f)),
          )
        }
      }
    }
    if (vm.hiddenByFilter) {
      Text(
        stringResource(R.string.newtask_status_hidden),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * The list's Requester drop-down, shown only when the list has one (or loading it failed): the chosen person with a
 * clear button and Choose / Change. A new name explains how it is saved, since ClickUp can't add the option.
 */
@Composable
private fun RequesterSection(vm: NewTaskViewModel, onChoose: () -> Unit) {
  val field = vm.requesterField
  if (field == null && !vm.requesterFailed) return
  val choice = vm.requester
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        FieldLabel(stringResource(R.string.newtask_requester))
        when {
          field == null -> Text(
            stringResource(R.string.newtask_requester_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          choice == null -> Text(
            stringResource(R.string.newtask_requester_none),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          else -> Row(verticalAlignment = Alignment.CenterVertically) {
            if (choice is RequesterChoice.Existing) {
              OptionDot(choice.option.colorHex)
              Spacer(Modifier.width(8.dp))
            }
            Text(choice.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      if (field == null) {
        TextButton(onClick = vm::retryRequesterField, enabled = !vm.busy && !vm.requesterLoading) {
          Text(stringResource(R.string.ui_retry))
        }
      } else {
        if (choice != null) {
          IconButton(onClick = { vm.chooseRequester(null) }, enabled = !vm.busy) {
            Icon(painterResource(R.drawable.ic_ui_close), stringResource(R.string.newtask_requester_clear))
          }
        }
        TextButton(onClick = onChoose, enabled = !vm.busy && !vm.requesterLoading) {
          Text(stringResource(if (choice == null) R.string.newtask_requester_choose else R.string.newtask_requester_change))
        }
      }
    }
    if (choice is RequesterChoice.New && field != null) {
      val line = NewTaskLogic.requesterLine(choice.name)
      val other = NewTaskLogic.otherOption(field)
      Text(
        if (other != null) stringResource(R.string.newtask_requester_new_other, other.name, line)
        else stringResource(R.string.newtask_requester_new_plain, line),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun DueChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
  FilterChip(selected = selected, onClick = onClick, enabled = enabled, label = { Text(label) })
}

@Composable
private fun PriorityChip(priority: Priority, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val color = Color(priority.chipColor)
  FilterChip(
    selected = selected,
    onClick = onClick,
    enabled = enabled,
    label = { Text(priority.label) },
    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(color)) },
    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.25f)),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(initialEpochDay: Long?, onPicked: (Long) -> Unit, onDismiss: () -> Unit) {
  val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay?.let(NewTaskLogic::pickerMsOfEpochDay))
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = { state.selectedDateMillis?.let { onPicked(NewTaskLogic.epochDayOfPicker(it)) } },
        enabled = state.selectedDateMillis != null,
      ) { Text(stringResource(R.string.newtask_date_ok)) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
  ) {
    DatePicker(state)
  }
}
