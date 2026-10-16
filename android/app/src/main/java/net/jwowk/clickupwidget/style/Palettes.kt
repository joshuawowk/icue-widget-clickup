package net.jwowk.clickupwidget.style

import net.jwowk.clickupwidget.data.model.ThemeMode
import net.jwowk.clickupwidget.data.model.WidgetConfig

/**
 * Raw ARGB colors for the non-dynamic themes, shared by the Glance widget and the Compose screens
 * (detail sheet, config preview) so both look the same. Derived tones are computed by consumers:
 * muted text = [text] at ~55–60% alpha, dividers = [text] at ~10%, chip fill = color at ~15–18%.
 * SYSTEM theme does not use these — it uses Material You dynamic colors.
 */
data class RawPalette(
  val background: Long,
  val text: Long,
  val accent: Long,
  /** Due chip: overdue. */
  val overdue: Long,
  /** Due chip: today. */
  val today: Long,
  /** Due chip: tomorrow / within a week. */
  val soon: Long,
  /** "All clear" check icon. */
  val ok: Long,
  /** Error text/icon. */
  val error: Long,
  val isDark: Boolean,
)

object Palettes {
  /** The iCUE widget defaults (ClickUpTasks.css). */
  val ClickUpDark = RawPalette(
    background = 0xFF15161C, text = 0xFFFFFFFF, accent = 0xFF9F8FFF,
    overdue = 0xFFF87171, today = 0xFFFB7185, soon = 0xFFFBBF24, ok = 0xFF4ADE80, error = 0xFFF87171,
    isDark = true,
  )

  /** Light twin of [ClickUpDark] with contrast-adjusted chips. */
  val ClickUpLight = RawPalette(
    background = 0xFFF6F6FA, text = 0xFF1B1C22, accent = 0xFF6B5BE6,
    overdue = 0xFFDC2626, today = 0xFFE11D48, soon = 0xFFB45309, ok = 0xFF16A34A, error = 0xFFDC2626,
    isDark = false,
  )

  /** Palette from the user's custom colors; due-chip colors pick the dark/light set by background luminance. */
  fun custom(config: WidgetConfig): RawPalette {
    val dark = isDark(config.backgroundColor)
    val base = if (dark) ClickUpDark else ClickUpLight
    return base.copy(
      background = config.backgroundColor,
      text = config.textColor,
      accent = config.accentColor,
      isDark = dark,
    )
  }

  /**
   * Fixed palette for a non-SYSTEM theme. For CLICKUP_AUTO returns the [nightMode] variant.
   * Returns null for SYSTEM (caller uses dynamic colors).
   */
  fun forConfig(config: WidgetConfig, nightMode: Boolean): RawPalette? = when (config.theme) {
    ThemeMode.SYSTEM -> null
    ThemeMode.CLICKUP_DARK -> ClickUpDark
    ThemeMode.CLICKUP_LIGHT -> ClickUpLight
    ThemeMode.CLICKUP_AUTO -> if (nightMode) ClickUpDark else ClickUpLight
    ThemeMode.CUSTOM -> custom(config)
  }

  /** Relative luminance < 0.5 → dark. */
  fun isDark(argb: Long): Boolean {
    fun ch(shift: Int): Double {
      val c = ((argb shr shift) and 0xFF) / 255.0
      return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val l = 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    return l < 0.5
  }

  /** [argb] with its alpha replaced by [alpha] (0..1). */
  fun withAlpha(argb: Long, alpha: Float): Long {
    val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toLong()
    return (a shl 24) or (argb and 0x00FFFFFF)
  }
}
