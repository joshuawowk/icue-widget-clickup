package net.jwowk.clickupwidget.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.unit.ColorProvider
import net.jwowk.clickupwidget.R
import net.jwowk.clickupwidget.data.model.Priority
import net.jwowk.clickupwidget.data.model.ThemeMode
import net.jwowk.clickupwidget.data.model.WidgetConfig
import net.jwowk.clickupwidget.style.Palettes
import net.jwowk.clickupwidget.style.RawPalette
import androidx.glance.color.ColorProvider as DayNight

/** Resolved widget colors for one config. */
internal class WidgetColors(
  val background: ColorProvider,
  val text: ColorProvider,
  val muted: ColorProvider,
  /** Unselected chip label / dot. */
  val faded: ColorProvider,
  val accent: ColorProvider,
  val divider: ColorProvider,
  val rowDivider: ColorProvider,
  val starOff: ColorProvider,
  val overdue: ColorProvider,
  val today: ColorProvider,
  val soon: ColorProvider,
  val ok: ColorProvider,
  val error: ColorProvider,
  val toastBackground: ColorProvider,
  val toastText: ColorProvider,
  val toastAction: ColorProvider,
  /** Priority flag/label color, legible on this background (see [Palettes.priorityInk]). */
  val priority: (Priority) -> ColorProvider,
)

private val BG_RES = intArrayOf(
  R.color.w_bg_00, R.color.w_bg_10, R.color.w_bg_20, R.color.w_bg_30, R.color.w_bg_40, R.color.w_bg_50,
  R.color.w_bg_60, R.color.w_bg_70, R.color.w_bg_80, R.color.w_bg_90, R.color.w_bg_100,
)

private fun argb(v: Long) = Color(v)

/** Opaque mix of [a] toward [b] by [t]. */
private fun mix(a: Long, b: Long, t: Float): Long {
  fun ch(v: Long, s: Int) = ((v shr s) and 0xFF).toFloat()
  fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t + 0.5f).toLong().coerceIn(0, 255) shl s
  return 0xFF000000 or m(16) or m(8) or m(0)
}

/** Colors for [config]: Material You via GlanceTheme for SYSTEM, fixed (or day/night) palettes otherwise. */
@Composable
internal fun widgetColors(config: WidgetConfig): WidgetColors {
  val opacity = config.backgroundOpacity.coerceIn(0, 100) / 100f
  val light = Palettes.ClickUpLight
  val dark = Palettes.ClickUpDark
  if (config.theme == ThemeMode.SYSTEM) {
    val c = GlanceTheme.colors
    val q = WidgetMath.quantizeOpacity(config.backgroundOpacity)
    fun pair(f: (RawPalette) -> Long) = DayNight(day = argb(f(light)), night = argb(f(dark)))
    return WidgetColors(
      background = if (q == 100) c.widgetBackground else ColorProvider(BG_RES[q / 10]),
      text = c.onSurface,
      muted = c.onSurfaceVariant,
      faded = c.onSurfaceVariant,
      accent = c.primary,
      divider = c.outline,
      rowDivider = c.outline,
      starOff = c.outline,
      overdue = pair { it.overdue },
      today = pair { it.today },
      soon = pair { it.soon },
      ok = pair { it.ok },
      error = pair { it.error },
      toastBackground = c.inverseSurface,
      toastText = c.inverseOnSurface,
      toastAction = c.inversePrimary,
      priority = { p -> DayNight(day = argb(Palettes.priorityInk(p, onDark = false)), night = argb(Palettes.priorityInk(p, onDark = true))) },
    )
  }
  val day = Palettes.forConfig(config, nightMode = false)!!
  val night = Palettes.forConfig(config, nightMode = true)!!
  fun pick(f: (RawPalette) -> Long): ColorProvider = DayNight(day = argb(f(day)), night = argb(f(night)))
  return WidgetColors(
    background = pick { Palettes.withAlpha(it.background, opacity) },
    text = pick { it.text },
    muted = pick { Palettes.withAlpha(it.text, 0.58f) },
    faded = pick { Palettes.withAlpha(it.text, 0.40f) },
    accent = pick { it.accent },
    divider = pick { Palettes.withAlpha(it.text, 0.30f) },
    rowDivider = pick { Palettes.withAlpha(it.text, 0.22f) },
    starOff = pick { Palettes.withAlpha(it.accent, 0.35f) },
    overdue = pick { it.overdue },
    today = pick { it.today },
    soon = pick { it.soon },
    ok = pick { it.ok },
    error = pick { it.error },
    toastBackground = pick { mix(it.background, it.text, 0.14f) },
    toastText = pick { it.text },
    toastAction = pick { it.accent },
    priority = { p -> pick { Palettes.priorityInk(p, onDark = it.isDark) } },
  )
}
