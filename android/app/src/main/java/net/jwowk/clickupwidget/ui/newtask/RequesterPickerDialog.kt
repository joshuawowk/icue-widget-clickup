package net.jwowk.clickupwidget.ui.newtask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.TaskLogic
import net.jwowk.clickupwidget.data.model.RequesterOption

/**
 * Searchable options of the list's Requester drop-down. A typed name that isn't an option is offered as
 * "Add “name”" ([RequesterChoice.New]); Done on the keyboard picks the only match, or adds the name when nothing matches.
 */
@Composable
internal fun RequesterPickerDialog(
  options: List<RequesterOption>,
  selected: RequesterChoice?,
  onPick: (RequesterChoice) -> Unit,
  onDismiss: () -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val shown = NewTaskLogic.filterRequesters(options, query)
  val newName = NewTaskLogic.newRequesterName(options, query)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.newtask_requester)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          singleLine = true,
          label = { Text(stringResource(R.string.newtask_requester_search)) },
          modifier = Modifier.fillMaxWidth(),
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = {
            val only = shown.singleOrNull()
            when {
              only != null -> onPick(RequesterChoice.Existing(only))
              shown.isEmpty() && newName != null -> onPick(RequesterChoice.New(newName))
            }
          }),
        )
        when {
          shown.isNotEmpty() -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(shown, key = { it.id }) { option ->
              val isSelected = (selected as? RequesterChoice.Existing)?.option?.id == option.id
              Row(
                Modifier.fillMaxWidth().clickable { onPick(RequesterChoice.Existing(option)) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                OptionDot(option.colorHex)
                Text(
                  option.name,
                  modifier = Modifier.weight(1f),
                  style = MaterialTheme.typography.bodyLarge,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                if (isSelected) {
                  Icon(painterResource(R.drawable.ic_ui_check), null, tint = MaterialTheme.colorScheme.primary)
                }
              }
              HorizontalDivider()
            }
          }
          newName == null -> Text(
            stringResource(R.string.newtask_requester_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (newName != null) {
          Row(
            Modifier.fillMaxWidth().clickable { onPick(RequesterChoice.New(newName)) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(painterResource(R.drawable.ic_ui_add), null, tint = MaterialTheme.colorScheme.primary)
            Column {
              Text(
                stringResource(R.string.newtask_requester_add, newName),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                stringResource(R.string.newtask_requester_add_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
  )
}

/** The option's drop-down color as a dot (outline color when it has none). */
@Composable
internal fun OptionDot(colorHex: String?) {
  val color = colorHex?.let { Color(TaskLogic.parseColor(it)) } ?: MaterialTheme.colorScheme.outline
  Box(Modifier.size(10.dp).clip(CircleShape).background(color))
}
