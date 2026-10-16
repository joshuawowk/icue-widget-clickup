package net.jwowk.clickupwidget.ui.detail

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import net.jwowk.clickupwidget.data.DueClass
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.style.Palettes
import net.jwowk.clickupwidget.style.RawPalette

/** Due-chip colors for the current detail theme (Overdue red / Today pink / Soon amber / later muted). */
@Immutable
internal data class DueColors(val overdue: Color, val today: Color, val soon: Color) {
  fun forClass(cls: DueClass, muted: Color): Color = when (cls) {
    DueClass.OVERDUE -> overdue
    DueClass.TODAY -> today
    DueClass.SOON -> soon
    DueClass.LATER -> muted
  }
}

private val LocalDueColors = compositionLocalOf { DueColors(Color.Red, Color.Red, Color.Red) }

internal object DetailColors {
  val due: DueColors
    @Composable @ReadOnlyComposable get() = LocalDueColors.current
}

private fun dueColorsOf(p: RawPalette) = DueColors(Color(p.overdue), Color(p.today), Color(p.soon))

private fun onColor(argb: Long): Color = if (Palettes.isDark(argb)) Color.White else Color(0xFF111111)

/** M3 color scheme from a fixed widget palette: opaque background/surface, palette text, accent as primary. */
internal fun paletteColorScheme(p: RawPalette): ColorScheme {
  val bg = Color(p.background or 0xFF000000)
  val text = Color(p.text)
  val accent = Color(p.accent)
  val variant = lerp(bg, text, 0.08f)
  val base = if (p.isDark) darkColorScheme() else lightColorScheme()
  return base.copy(
    primary = accent,
    onPrimary = onColor(p.accent),
    primaryContainer = lerp(bg, accent, 0.25f),
    onPrimaryContainer = text,
    secondary = accent,
    onSecondary = onColor(p.accent),
    background = bg,
    onBackground = text,
    surface = bg,
    onSurface = text,
    surfaceVariant = variant,
    onSurfaceVariant = text.copy(alpha = 0.6f),
    surfaceTint = accent,
    inverseSurface = text,
    inverseOnSurface = bg,
    inversePrimary = accent,
    outline = text.copy(alpha = 0.35f),
    outlineVariant = text.copy(alpha = 0.12f),
    error = Color(p.error),
    surfaceBright = variant,
    surfaceDim = bg,
    surfaceContainerLowest = bg,
    surfaceContainerLow = bg,
    surfaceContainer = bg,
    surfaceContainerHigh = bg,
    surfaceContainerHighest = variant,
  )
}

/**
 * Theme for one widget's detail sheet. SYSTEM uses dynamic colors on Android 12+ (baseline M3 below)
 * following the system light/dark setting; other themes use the widget's fixed palette.
 */
@Composable
internal fun DetailTheme(config: WidgetConfig, content: @Composable () -> Unit) {
  val dark = isSystemInDarkTheme()
  val context = LocalContext.current
  val palette = remember(config, dark) { Palettes.forConfig(config, nightMode = dark) }
  val scheme = remember(config, dark, palette) {
    when {
      palette != null -> paletteColorScheme(palette)
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      dark -> darkColorScheme()
      else -> lightColorScheme()
    }
  }
  val due = remember(palette, dark) {
    dueColorsOf(palette ?: if (dark) Palettes.ClickUpDark else Palettes.ClickUpLight)
  }
  CompositionLocalProvider(LocalDueColors provides due) {
    MaterialTheme(colorScheme = scheme, content = content)
  }
}
