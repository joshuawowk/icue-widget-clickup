@file:OptIn(ExperimentalMaterial3Api::class)

package net.jwowk.clickupwidget.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A titled card that groups related settings. */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (title != null) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
      }
      content()
    }
  }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, supporting: String? = null) {
  Row(
    modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      if (supporting != null) {
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

@Composable
fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    RadioButton(selected = selected, onClick = null)
    Text(label, style = MaterialTheme.typography.bodyLarge)
  }
}

/** Labelled integer slider; [step] > 1 snaps the value. [onChangeFinished] fires when the thumb is released. */
@Composable
fun IntSliderRow(
  label: String,
  value: Int,
  range: IntRange,
  onValueChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
  step: Int = 1,
  valueText: @Composable (Int) -> String = { it.toString() },
  enabled: Boolean = true,
  onChangeFinished: (() -> Unit)? = null,
) {
  val steps = ((range.last - range.first) / step - 1).coerceAtLeast(0)
  Column(modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.bodyLarge)
      Text(valueText(value), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(
      value = value.toFloat(),
      onValueChange = { onValueChange(it.roundToInt().coerceIn(range.first, range.last)) },
      valueRange = range.first.toFloat()..range.last.toFloat(),
      steps = steps,
      enabled = enabled,
      onValueChangeFinished = onChangeFinished,
    )
  }
}

/** Single-choice segmented control over [options]. */
@Composable
fun <T> SegmentedChoice(
  options: List<T>,
  selected: T,
  optionLabel: @Composable (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
    options.forEachIndexed { index, option ->
      SegmentedButton(
        selected = option == selected,
        onClick = { onSelect(option) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
      ) {
        Text(optionLabel(option), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
      }
    }
  }
}

/** Read-only dropdown field. */
@Composable
fun <T> DropdownField(
  label: String,
  selected: T,
  options: List<T>,
  optionLabel: (T) -> String,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier.fillMaxWidth()) {
    OutlinedTextField(
      value = optionLabel(selected),
      onValueChange = {},
      readOnly = true,
      singleLine = true,
      label = { Text(label) },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
      modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      options.forEach { option ->
        DropdownMenuItem(
          text = { Text(optionLabel(option)) },
          onClick = {
            onSelect(option)
            expanded = false
          },
        )
      }
    }
  }
}

/** Tappable summary row: title, optional supporting text, trailing slot. */
@Composable
fun ClickableRow(
  title: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  supporting: String? = null,
  trailing: @Composable () -> Unit = {},
) {
  Row(
    modifier.fillMaxWidth().clickable(onClick = onClick),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      if (supporting != null) {
        Text(
          supporting,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    trailing()
  }
}
