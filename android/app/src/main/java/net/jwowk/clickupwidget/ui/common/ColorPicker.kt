package net.jwowk.clickupwidget.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.style.Palettes

/** A filled circle in [argb], optionally with a check mark when [selected]. */
@Composable
fun ColorSwatch(argb: Long, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null) {
  val outline = MaterialTheme.colorScheme.outline
  Box(
    modifier
      .size(36.dp)
      .clip(CircleShape)
      .background(Color(argb))
      .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else outline), CircleShape)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    if (selected) {
      Icon(
        painterResource(R.drawable.ic_ui_check),
        contentDescription = null,
        tint = if (Palettes.isDark(argb)) Color.White else Color.Black,
        modifier = Modifier.size(20.dp),
      )
    }
  }
}

/** Color chooser: current swatch, a row of [presets] and a `#RRGGBB` field. */
@Composable
fun ColorPickerRow(label: String, color: Long, presets: List<Long>, onColorChange: (Long) -> Unit, modifier: Modifier = Modifier) {
  var text by remember(color) { mutableStateOf(ColorHex.format(color)) }
  val valid = ColorHex.parse(text) != null
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, style = MaterialTheme.typography.bodyLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      presets.forEach { preset ->
        ColorSwatch(preset, selected = (preset and 0xFFFFFF) == (color and 0xFFFFFF), onClick = { onColorChange(preset) })
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      ColorSwatch(color)
      OutlinedTextField(
        value = text,
        onValueChange = { input ->
          text = input
          ColorHex.parse(input)?.let(onColorChange)
        },
        singleLine = true,
        isError = !valid,
        label = { Text(stringResource(R.string.ui_hex_color)) },
        supportingText = if (valid) null else ({ Text(stringResource(R.string.ui_hex_color_invalid)) }),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.weight(1f).padding(top = 4.dp).fillMaxWidth(),
      )
    }
  }
}
