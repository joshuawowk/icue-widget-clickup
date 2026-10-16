package net.jwowk.clickupwidget.widget

/** Pure sizing/layout helpers for the widget (no Android or Glance types; unit-tested on the JVM). */
internal object WidgetMath {
  /** Below this width (dp) the star and list name are hidden and text shrinks slightly. */
  const val NARROW_DP = 200f
  /** Below this height (dp) the subtitle is hidden. */
  const val SHORT_DP = 140f
  /** RemoteViews containers misbehave past ~10 children; never put more chips than this in one row. */
  const val MAX_CHIPS_PER_ROW = 8
  /** Chip chrome in dp: dot 6 + gap 5 + horizontal padding 2×8 + trailing spacer 4. */
  const val CHIP_CHROME_DP = 31f
  /** Glyph width estimate as a fraction of the font size. */
  const val GLYPH_EM = 0.6f

  /** Responsive flags derived from the widget's current size. */
  data class Breakpoints(val narrow: Boolean, val short: Boolean)

  fun breakpoints(widthDp: Float, heightDp: Float): Breakpoints =
    Breakpoints(narrow = widthDp < NARROW_DP, short = heightDp < SHORT_DP)

  /** Multiplier for the base text sizes: config.textScale (80..140 %), ~8 % smaller when narrow. */
  fun textScale(textScalePercent: Int, narrow: Boolean): Float =
    textScalePercent.coerceIn(80, 140) / 100f * (if (narrow) 0.92f else 1f)

  /** Background opacity 0..100 rounded to the nearest 10 (matches the shipped `w_bg_NN` colors). */
  fun quantizeOpacity(opacity: Int): Int = (opacity.coerceIn(0, 100) + 5) / 10 * 10

  /** Estimated rendered width of a filter chip in dp. */
  fun chipWidthDp(label: String, fontSp: Float): Float = label.length * fontSp * GLYPH_EM + CHIP_CHROME_DP

  /**
   * Greedy wrap of chips with estimated [widths] into rows no wider than [availableDp] (a chip
   * wider than a whole row gets its own row). Returns index lists in input order.
   */
  fun wrapRows(widths: List<Float>, availableDp: Float, maxPerRow: Int = MAX_CHIPS_PER_ROW): List<List<Int>> {
    val rows = mutableListOf<MutableList<Int>>()
    var used = 0f
    widths.forEachIndexed { i, w ->
      val row = rows.lastOrNull()
      if (row == null || row.size >= maxPerRow || used + w > availableDp) {
        rows += mutableListOf(i)
        used = w
      } else {
        row += i
        used += w
      }
    }
    return rows
  }

  /** Stable, non-negative 64-bit lazy-list item id for [key] (FNV-1a), clear of Glance's reserved range. */
  fun stableId(key: String): Long {
    var h = -0x340d631b7bdddcdbL
    for (c in key) {
      h = h xor c.code.toLong()
      h *= 0x100000001b3L
    }
    return h and Long.MAX_VALUE
  }

  /** [text] cut to [max] chars with an ellipsis. */
  fun ellipsize(text: String, max: Int): String =
    if (text.length <= max) text else text.take((max - 1).coerceAtLeast(1)).trimEnd() + "…"

  /** Power-of-two BitmapFactory sample size so the decoded long edge is ≤ [maxEdge]. */
  fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    val edge = maxOf(width, height)
    while (edge / sample > maxEdge) sample *= 2
    return sample
  }
}
