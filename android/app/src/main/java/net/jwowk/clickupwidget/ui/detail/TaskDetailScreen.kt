package net.jwowk.clickupwidget.ui.detail

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.DueInfo
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.TaskField
import net.jwowk.clickupwidget.data.model.TaskItem
import net.jwowk.clickupwidget.data.model.TaskStatus

private fun argb(hex: String?): Color = Color(TaskLogic.parseColor(hex))

/** The bottom-anchored task-detail sheet over a tap-to-dismiss scrim area. */
@Composable
internal fun TaskDetailScreen(
  state: DetailUiState,
  onDismiss: () -> Unit,
  onSetStatus: (TaskStatus) -> Unit,
  onSetPriority: (Priority) -> Unit,
  onCompleteSubtask: (TaskItem) -> Unit,
  onCompleteTask: () -> Unit,
  onUndo: (String) -> Unit,
  onSnackConsumed: (Long) -> Unit,
  onOpenInClickUp: () -> Unit,
) {
  val snackHost = remember { SnackbarHostState() }
  val undoLabel = stringResource(R.string.detail_undo)

  val snack = state.snack
  LaunchedEffect(snack?.key) {
    if (snack == null) return@LaunchedEffect
    coroutineScope {
      val timer = launch {
        delay((snack.deadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        snackHost.currentSnackbarData?.dismiss()
      }
      val result = snackHost.showSnackbar(
        message = snack.text,
        actionLabel = if (snack.undoId != null) undoLabel else null,
        duration = SnackbarDuration.Indefinite,
      )
      timer.cancel()
      if (result == SnackbarResult.ActionPerformed && snack.undoId != null) onUndo(snack.undoId)
      onSnackConsumed(snack.key)
    }
  }

  var shown by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { shown = true }

  BoxWithConstraints(
    Modifier
      .fillMaxSize()
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
  ) {
    val maxSheetHeight = maxHeight * 0.85f
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
        SheetContent(state, onDismiss, onSetStatus, onSetPriority, onCompleteSubtask, onCompleteTask, onUndo, onOpenInClickUp)
      }
    }
    SnackbarHost(
      hostState = snackHost,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(8.dp),
    )
  }
}

@Composable
private fun SheetContent(
  state: DetailUiState,
  onDismiss: () -> Unit,
  onSetStatus: (TaskStatus) -> Unit,
  onSetPriority: (Priority) -> Unit,
  onCompleteSubtask: (TaskItem) -> Unit,
  onCompleteTask: () -> Unit,
  onUndo: (String) -> Unit,
  onOpenInClickUp: () -> Unit,
) {
  val task = state.task
  Column(
    Modifier
      .verticalScroll(rememberScrollState())
      .navigationBarsPadding()
      .padding(horizontal = 20.dp)
      .padding(top = 8.dp, bottom = 20.dp),
  ) {
    Box(
      Modifier
        .align(Alignment.CenterHorizontally)
        .padding(bottom = 4.dp)
        .size(width = 32.dp, height = 4.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(MaterialTheme.colorScheme.outlineVariant),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        task.listName,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      IconButton(onClick = onDismiss) {
        Icon(painterResource(R.drawable.ic_detail_close), stringResource(R.string.detail_close))
      }
    }
    Text(task.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

    MetaRow(state)
    Description(state)
    if (DetailFormat.showFields(state.fields)) FieldsSection(state.fields, state.task.id)
    if (DetailFormat.showTags(task)) TagsSection(task)
    PrioritySection(state, onSetPriority)
    if (state.subtasks.isNotEmpty()) SubtasksSection(state, onCompleteSubtask, onUndo)
    StatusSection(state, onSetStatus)
    Actions(state, onCompleteTask, onUndo, onOpenInClickUp)
  }
}

@Composable
private fun MetaRow(state: DetailUiState) {
  val task = state.task
  val due = remember(task.dueMs, state.nowMs) { TaskLogic.dueInfo(task.dueMs, state.nowMs) } ?: return
  Box(Modifier.padding(top = 12.dp)) {
    DueChip(due, DetailFormat.dueChipLabel(due, task.dueMs), large = true)
  }
}

@Composable
private fun DueChip(due: DueInfo, label: String, large: Boolean = false) {
  val color = DetailColors.due.forClass(due.cls, MaterialTheme.colorScheme.onSurfaceVariant)
  Text(
    label,
    modifier = Modifier
      .clip(RoundedCornerShape(50))
      .background(color.copy(alpha = 0.16f))
      .padding(horizontal = if (large) 10.dp else 8.dp, vertical = if (large) 4.dp else 2.dp),
    color = color,
    style = if (large) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
private fun Description(state: DetailUiState) {
  var expanded by rememberSaveable(state.task.id) { mutableStateOf(false) }
  val muted = MaterialTheme.colorScheme.onSurfaceVariant
  Column(Modifier.padding(top = 20.dp)) {
    SectionLabel(stringResource(R.string.detail_description))
    Spacer(Modifier.size(6.dp))
    when {
      state.detailLoading -> Text(stringResource(R.string.detail_loading), style = MaterialTheme.typography.bodyMedium, color = muted)
      state.detailFailed -> Text(stringResource(R.string.detail_load_failed), style = MaterialTheme.typography.bodySmall, color = muted)
      else -> {
        val d = DetailFormat.description(state.description, expanded)
        if (d.text.isEmpty()) {
          Text(stringResource(R.string.detail_no_description), style = MaterialTheme.typography.bodyMedium, color = muted)
        } else {
          Text(d.text, style = MaterialTheme.typography.bodyMedium)
          if (d.collapsible) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
              Text(stringResource(if (expanded) R.string.detail_show_less else R.string.detail_show_more))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FieldsSection(fields: List<TaskField>, taskId: String) {
  Column(Modifier.padding(top = 20.dp)) {
    SectionLabel(stringResource(R.string.detail_fields))
    fields.forEachIndexed { i, f ->
      if (f.name.isNotBlank() && f.value.isNotBlank()) FieldRow(f, "$taskId:$i", Modifier.padding(top = 10.dp))
    }
  }
}

/** One field: muted name over its value (color dot when [TaskField.colorHex]); long values expand. */
@Composable
private fun FieldRow(field: TaskField, key: String, modifier: Modifier = Modifier) {
  var expanded by rememberSaveable(key) { mutableStateOf(false) }
  var overflows by remember(key) { mutableStateOf(false) }
  Column(modifier.fillMaxWidth()) {
    Text(field.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.Top) {
      if (field.colorHex != null) {
        Box(
          Modifier
            .padding(top = 6.dp, end = 8.dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(argb(field.colorHex)),
        )
      }
      Text(
        field.value,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else DetailFormat.FIELD_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
      )
    }
    if (overflows || expanded) {
      TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
        Text(stringResource(if (expanded) R.string.detail_show_less else R.string.detail_show_more))
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(task: TaskItem) {
  Column(Modifier.padding(top = 20.dp)) {
    SectionLabel(stringResource(R.string.detail_tags))
    Spacer(Modifier.size(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      task.tags.forEach { tag ->
        val color = DetailFormat.tagColor(task, tag)?.let { Color(it) }
        Text(
          tag,
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
          style = MaterialTheme.typography.labelMedium,
          color = if (color != null) onColor(color) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

private fun onColor(bg: Color): Color = if (bg.luminance() < 0.5f) Color.White else Color(0xFF111111)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrioritySection(state: DetailUiState, onSetPriority: (Priority) -> Unit) {
  Column(Modifier.padding(top = 20.dp)) {
    SectionLabel(stringResource(R.string.detail_priority_heading))
    Spacer(Modifier.size(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      DetailFormat.priorityChoices.forEach { p ->
        PriorityPill(
          priority = p,
          current = DetailFormat.isCurrentPriority(p, state.task.priority),
          saving = state.savingPriority == p,
          busy = state.priorityBusy,
          onClick = { onSetPriority(p) },
        )
      }
    }
  }
}

@Composable
private fun PriorityPill(priority: Priority, current: Boolean, saving: Boolean, busy: Boolean, onClick: () -> Unit) {
  val color = Color(priority.chipColor)
  val textColor = if (current) onColor(color) else MaterialTheme.colorScheme.onSurface
  Row(
    Modifier
      .alpha(if (busy && !saving && !current) 0.5f else if (saving) 0.7f else 1f)
      .clip(RoundedCornerShape(50))
      .background(if (current) color else color.copy(alpha = 0.14f))
      .clickable(enabled = !busy && !current, role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (current) textColor else color))
    Spacer(Modifier.width(6.dp))
    Text(
      priority.label.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.4.sp,
      color = textColor,
    )
  }
}

@Composable
private fun SubtasksSection(state: DetailUiState, onComplete: (TaskItem) -> Unit, onUndo: (String) -> Unit) {
  Row(
    Modifier
      .fillMaxWidth()
      .padding(top = 20.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    SectionLabel(stringResource(R.string.detail_subtasks), Modifier.weight(1f))
    Text(
      stringResource(R.string.detail_subtasks_progress, state.subtasksDone, state.subtasks.size),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.primary,
    )
  }
  state.subtasks.forEach { sub ->
    SubtaskRow(
      sub,
      done = state.isSubtaskDone(sub),
      committed = state.isSubtaskCommittedDone(sub),
      nowMs = state.nowMs,
    ) {
      // Tapping a still-pending (checked) circle undoes it.
      if (sub.id in state.pending) onUndo(sub.id) else onComplete(sub)
    }
  }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(),
    modifier = modifier,
    style = MaterialTheme.typography.labelMedium,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.8.sp,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtaskRow(sub: TaskItem, done: Boolean, committed: Boolean, nowMs: Long, onClick: () -> Unit) {
  val statusColor = argb(sub.statusColorHex)
  val due = remember(sub.dueMs, nowMs) { TaskLogic.dueInfo(sub.dueMs, nowMs) }
  Row(
    Modifier
      .fillMaxWidth()
      .alpha(if (done) 0.5f else 1f),
    verticalAlignment = Alignment.Top,
  ) {
    CheckCircle(
      color = statusColor,
      checked = done,
      enabled = !committed,
      description = if (committed) stringResource(R.string.detail_subtask_done_cd)
      else if (done) stringResource(R.string.detail_subtask_undo_cd, sub.name)
      else stringResource(R.string.detail_subtask_complete_cd, sub.name),
      onClick = onClick,
    )
    Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 6.dp)) {
      Text(
        sub.name,
        style = MaterialTheme.typography.bodyLarge,
        textDecoration = if (done) TextDecoration.LineThrough else null,
      )
      if (sub.status.isNotEmpty() || due != null) {
        FlowRow(
          modifier = Modifier.padding(top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (sub.status.isNotEmpty()) StatusMini(sub.status, statusColor)
          if (due != null) DueChip(due, due.label)
        }
      }
    }
  }
}

@Composable
private fun StatusMini(name: String, color: Color) {
  Row(
    Modifier
      .clip(RoundedCornerShape(50))
      .background(color.copy(alpha = 0.16f))
      .padding(horizontal = 8.dp, vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(5.dp))
    Text(name, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
  }
}

/** Circle tinted with [color]; filled with a check when [checked]. The 40dp box is the touch target. */
@Composable
private fun CheckCircle(color: Color, checked: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
  Box(
    Modifier
      .size(40.dp)
      .clip(CircleShape)
      .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
      .semantics { contentDescription = description },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      Modifier
        .size(22.dp)
        .clip(CircleShape)
        .background(if (checked) color else Color.Transparent)
        .border(2.dp, color, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (checked) {
        Icon(
          painterResource(R.drawable.ic_detail_check),
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = if (color.luminance() < 0.5f) Color.White else Color(0xFF111111),
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusSection(state: DetailUiState, onSetStatus: (TaskStatus) -> Unit) {
  Column(Modifier.padding(top = 20.dp)) {
    SectionLabel(stringResource(R.string.detail_status_heading))
    Spacer(Modifier.size(8.dp))
    val statuses = state.statuses
    when {
      statuses != null -> FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        statuses.forEach { s ->
          StatusPill(
            status = s,
            current = DetailFormat.isCurrentStatus(s.name, state.task.status),
            saving = state.savingStatus == s.name,
            busy = state.statusBusy,
            onClick = { onSetStatus(s) },
          )
        }
      }
      state.statusesFailed -> Text(
        stringResource(R.string.detail_statuses_failed),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      else -> Text(
        stringResource(R.string.detail_statuses_loading),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StatusPill(status: TaskStatus, current: Boolean, saving: Boolean, busy: Boolean, onClick: () -> Unit) {
  val color = argb(status.colorHex)
  val textColor = if (current) Color.White else MaterialTheme.colorScheme.onSurface
  Row(
    Modifier
      .alpha(if (busy && !saving && !current) 0.5f else if (saving) 0.7f else 1f)
      .clip(RoundedCornerShape(50))
      .background(if (current) color else color.copy(alpha = 0.14f))
      .clickable(enabled = !busy && !current, role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(if (current) Color.White else color))
    Spacer(Modifier.width(6.dp))
    Text(
      status.name.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      letterSpacing = 0.4.sp,
      color = textColor,
    )
  }
}

@Composable
private fun Actions(state: DetailUiState, onCompleteTask: () -> Unit, onUndo: (String) -> Unit, onOpenInClickUp: () -> Unit) {
  val completing = state.task.id in state.pending
  Row(
    Modifier
      .fillMaxWidth()
      .padding(top = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    OutlinedButton(
      onClick = onOpenInClickUp,
      modifier = Modifier.weight(1f),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
      Icon(painterResource(R.drawable.ic_detail_open_in_new), null, Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.detail_open_in_clickup), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (!state.task.isDone) {
      Button(onClick = { if (completing) onUndo(state.task.id) else onCompleteTask() }, modifier = Modifier.weight(1f)) {
        Icon(painterResource(R.drawable.ic_detail_task_alt), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.detail_mark_complete), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}
