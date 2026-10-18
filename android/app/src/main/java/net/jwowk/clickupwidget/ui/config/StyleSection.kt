package net.jwowk.clickupwidget.ui.config

import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.model.Density
import net.jwowk.clickupwidget.data.model.ThemeMode
import net.jwowk.clickupwidget.ui.common.ColorPickerRow
import net.jwowk.clickupwidget.ui.common.IntSliderRow
import net.jwowk.clickupwidget.ui.common.RadioRow
import net.jwowk.clickupwidget.ui.common.SectionCard
import net.jwowk.clickupwidget.ui.common.SegmentedChoice
import net.jwowk.clickupwidget.ui.common.SwitchRow

private val TEXT_PRESETS = listOf(
  0xFFFFFFFF, 0xFFE5E7EB, 0xFF94A3B8, 0xFF1B1C22, 0xFF000000,
  0xFFFBBF24, 0xFF4ADE80, 0xFF38BDF8, 0xFFFD71AF, 0xFF9F8FFF,
)
private val ACCENT_PRESETS = listOf(
  0xFF9F8FFF, 0xFFFD71AF, 0xFFFB7185, 0xFFF87171, 0xFFFBBF24,
  0xFF4ADE80, 0xFF2DD4BF, 0xFF38BDF8, 0xFF6B5BE6, 0xFFFFFFFF,
)
private val BACKGROUND_PRESETS = listOf(
  0xFF15161C, 0xFF000000, 0xFF0F172A, 0xFF1E1B2E, 0xFF2A2D3A,
  0xFF1F2937, 0xFFFFFFFF, 0xFFF6F6FA, 0xFFFEF3C7, 0xFFE0F2FE,
)

@Composable
internal fun StyleSection(vm: ConfigViewModel) {
  val config = vm.config

  SectionCard(stringResource(R.string.ui_section_style)) {
    Text(stringResource(R.string.ui_theme), style = MaterialTheme.typography.bodyLarge)
    Column {
      ThemeMode.entries.forEach { mode ->
        RadioRow(mode.label, selected = config.theme == mode, onClick = { vm.update { it.copy(theme = mode) } })
      }
    }
    if (config.theme == ThemeMode.SYSTEM) {
      Text(
        stringResource(R.string.ui_theme_system_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Text(
          stringResource(R.string.ui_theme_system_legacy),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (config.theme == ThemeMode.CUSTOM) {
      ColorPickerRow(
        label = stringResource(R.string.ui_color_text),
        color = config.textColor,
        presets = TEXT_PRESETS,
        onColorChange = { c -> vm.update { it.copy(textColor = c) } },
      )
      ColorPickerRow(
        label = stringResource(R.string.ui_color_accent),
        color = config.accentColor,
        presets = ACCENT_PRESETS,
        onColorChange = { c -> vm.update { it.copy(accentColor = c) } },
      )
      ColorPickerRow(
        label = stringResource(R.string.ui_color_background),
        color = config.backgroundColor,
        presets = BACKGROUND_PRESETS,
        onColorChange = { c -> vm.update { it.copy(backgroundColor = c) } },
      )
    }

    IntSliderRow(
      label = stringResource(R.string.ui_bg_opacity),
      value = config.backgroundOpacity,
      range = 0..100,
      onValueChange = { v -> vm.update { it.copy(backgroundOpacity = v) } },
      valueText = { stringResource(R.string.ui_percent, it) },
    )
    IntSliderRow(
      label = stringResource(R.string.ui_text_size),
      value = config.textScale,
      range = 80..140,
      step = 5,
      onValueChange = { v -> vm.update { it.copy(textScale = v) } },
      valueText = { stringResource(R.string.ui_percent, it) },
    )

    Text(stringResource(R.string.ui_density), style = MaterialTheme.typography.bodyLarge)
    SegmentedChoice(
      options = Density.entries,
      selected = config.density,
      optionLabel = { it.label },
      onSelect = { v -> vm.update { it.copy(density = v) } },
    )

    SwitchRow(stringResource(R.string.ui_show_subtitle), config.showSubtitle, { v -> vm.update { it.copy(showSubtitle = v) } })
    SwitchRow(stringResource(R.string.ui_show_count), config.showCount, { v -> vm.update { it.copy(showCount = v) } })
    SwitchRow(stringResource(R.string.ui_show_list_name), config.showListName, { v -> vm.update { it.copy(showListName = v) } })
    SwitchRow(stringResource(R.string.ui_show_requester), config.showRequester, { v -> vm.update { it.copy(showRequester = v) } })
    SwitchRow(stringResource(R.string.ui_show_due_chip), config.showDueChip, { v -> vm.update { it.copy(showDueChip = v) } })
    SwitchRow(stringResource(R.string.ui_show_priority_star), config.showPriorityStar, { v -> vm.update { it.copy(showPriorityStar = v) } })
    SwitchRow(stringResource(R.string.ui_show_priority), config.showPriority, { v -> vm.update { it.copy(showPriority = v) } })
    SwitchRow(stringResource(R.string.ui_show_tags), config.showTags, { v -> vm.update { it.copy(showTags = v) } })

    BackgroundImageControls(vm)

    TextButton(onClick = vm::resetStyle) { Text(stringResource(R.string.ui_reset_style)) }
  }
}

@Composable
private fun BackgroundImageControls(vm: ConfigViewModel) {
  val path = vm.config.backgroundImagePath
  val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    if (uri != null) vm.pickImage(uri)
  }

  Text(stringResource(R.string.ui_bg_image), style = MaterialTheme.typography.bodyLarge)
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    if (path != null) {
      val thumb by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
          BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
        }
      }
      thumb?.let {
        Image(
          it,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
        )
      }
    }
    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
      Icon(painterResource(R.drawable.ic_ui_image), contentDescription = null, Modifier.size(18.dp))
      Text(
        stringResource(if (path == null) R.string.ui_pick_image else R.string.ui_replace_image),
        Modifier.padding(start = 8.dp),
      )
    }
    if (path != null) {
      TextButton(onClick = vm::removeImage) {
        Icon(painterResource(R.drawable.ic_ui_delete), contentDescription = null, Modifier.size(18.dp))
        Text(stringResource(R.string.ui_remove_image), Modifier.padding(start = 8.dp))
      }
    }
    if (vm.imageBusy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
  }
  if (vm.imageFailed) {
    Text(stringResource(R.string.ui_image_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
  }
  if (path != null) {
    IntSliderRow(
      label = stringResource(R.string.ui_bg_blur),
      value = vm.blurRadius,
      range = 0..ImageBlur.MAX_RADIUS,
      onValueChange = vm::setBlur,
      enabled = vm.canReblur,
      onChangeFinished = vm::applyBlur,
    )
    if (!vm.canReblur) {
      Text(
        stringResource(R.string.ui_bg_blur_repick),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
