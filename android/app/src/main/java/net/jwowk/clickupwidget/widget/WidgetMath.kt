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

  /** Tag chip chrome in dp: horizontal padding 2×6 + trailing gap 4. */
  const val TAG_CHROME_DP = 16f
  /** Most tag chips shown on a row's second meta line; the rest collapse into "+N". */
  const val MAX_TAG_CHIPS = 3
  /** Tag names longer than this are ellipsized before measuring/drawing. */
  const val MAX_TAG_CHARS = 14

  /** Responsive flags derived from the widget's current size. */
  data class Breakpoints(val narrow: Boolean, val short: Boolean)

  fun breakpoints(widthDp: Float, heightDp: Float): Breakpoints =
    Breakpoints(narrow = widthDp < NARROW_DP, short = heightDp < SHORT_DP)

  /** Multiplier for the base text sizes: config.textScale (80..140 %), ~8 % smaller when narrow. */
  fun textScale(textScalePercent: Int, narrow: Boolean): Float =
    textScalePercent.coerceIn(80, 140) / 100f * (if (narrow) 0.92f else 1f)

  /** Background opacity 0..100 rounded to the nearest 10 (matches the shipped `w_bg_NN` colors). */
  fun quantizeOpacity(opacity: Int): Int = (opacity.coerceIn(0, 100) + 5) / 10 * 10

  /** Which header parts fit; the title (with chevron and filter dot) always keeps its room. */
  data class HeaderLayout(val showTasksWord: Boolean, val showCount: Boolean, val showRefresh: Boolean, val buttonDp: Int)

  /**
   * Decides the header parts from [contentWidthDp] (thresholds grow with [textScale] above 1): the "tasks" word
   * needs >= 290dp, the count number >= 200dp, the Refresh button >= 160dp; "+" is always kept.
   * Buttons shrink to 28dp when [compact] (short or narrow).
   */
  fun headerLayout(contentWidthDp: Float, textScale: Float, showCount: Boolean, compact: Boolean = false): HeaderLayout {
    val k = maxOf(1f, textScale)
    val count = showCount && contentWidthDp >= 200f * k
    return HeaderLayout(
      showTasksWord = count && contentWidthDp >= 290f * k,
      showCount = count,
      showRefresh = contentWidthDp >= 160f * k,
      buttonDp = if (compact) 28 else 32,
    )
  }

  /** Estimated rendered width of a filter chip in dp. */
  fun chipWidthDp(label: String, fontSp: Float): Float = label.length * fontSp * GLYPH_EM + CHIP_CHROME_DP

  /** Display label for a tag chip. */
  fun tagLabel(name: String): String = ellipsize(name, MAX_TAG_CHARS)

  /** Estimated rendered width of a tag chip in dp. */
  fun tagChipWidthDp(label: String, fontSp: Float): Float = label.length * fontSp * GLYPH_EM + TAG_CHROME_DP

  /** Estimated rendered width of plain meta text (no chrome) in dp. */
  fun textWidthDp(chars: Int, fontSp: Float): Float = chars * fontSp * GLYPH_EM

  /**
   * How many of the tag chips for [labels] (display labels, in order) fit one line of [availableDp]
   * together with the trailing "+N" counter for the ones left out. Returns the count to show (0..[maxChips]).
   */
  fun fitTagChips(labels: List<String>, fontSp: Float, availableDp: Float, maxChips: Int = MAX_TAG_CHIPS): Int {
    for (k in minOf(labels.size, maxChips) downTo 1) {
      val chips = labels.take(k).sumOf { tagChipWidthDp(it, fontSp).toDouble() }.toFloat()
      val plus = if (k < labels.size) textWidthDp("+${labels.size - k}".length, fontSp) else 0f
      if (chips + plus <= availableDp) return k
    }
    return 0
  }

  /** Where an optional part of a task row is drawn. */
  enum class Slot {
    /** Right-aligned beside the task name (line 1). */
    NAME_LINE,
    /** Right-aligned on the list-name line (line 2). */
    META_LINE,
    /** Left-aligned on an extra line underneath (didn't fit on the right). */
    BELOW,
    /** Not shown (absent or turned off). */
    NONE,
  }

  /** Placement of a task row's due chip, priority flag and tag chips, plus how many list-name chars fit. */
  data class RowLayout(
    val due: Slot,
    val priority: Slot,
    val tags: Slot,
    /** Tag chips to draw (the rest collapse into "+N"). */
    val tagChips: Int,
    /** Max characters of the list name on line 2. */
    val listNameChars: Int,
    /** Tags that dropped [Slot.BELOW] fit on the same extra line as the other dropped parts. */
    val tagsInlineBelow: Boolean = false,
  )

  /** Gap between right-aligned parts, in dp. */
  const val PART_GAP_DP = 8f
  /** The due chip may take at most this share of the row beside the name (the name wraps to a 2nd line). */
  const val DUE_BESIDE_NAME_SHARE = 0.45f
  /** The list name keeps at least this much of line 2 (or its own width if shorter), in dp. */
  const val LIST_NAME_MIN_DP = 72f

  /**
   * Uses the empty space to the right of the task text: the due chip goes beside the name when it is
   * narrow enough; priority and tags go right-aligned on the list-name line while they fit (in that
   * order — once one doesn't fit, it and the rest drop to a line below). Widths are estimates in dp:
   * [textWidthDp] is the text area between the check circle and the star; null widths = part absent.
   */
  fun rowLayout(
    textWidthDp: Float,
    listNameWidthDp: Float?,
    dueWidthDp: Float?,
    priorityWidthDp: Float?,
    tagLabels: List<String>,
    chipSp: Float,
    metaSp: Float,
  ): RowLayout {
    val dueBeside = dueWidthDp != null && dueWidthDp <= textWidthDp * DUE_BESIDE_NAME_SHARE
    val listReserve = listNameWidthDp?.let { minOf(it, LIST_NAME_MIN_DP) } ?: 0f
    var budget = textWidthDp - listReserve - (if (listNameWidthDp != null) PART_GAP_DP else 0f)
    var overflow = false

    var belowUsed = 0f
    fun place(width: Float?): Slot {
      if (width == null) return Slot.NONE
      if (!overflow && width <= budget) {
        budget -= width + PART_GAP_DP
        return Slot.META_LINE
      }
      overflow = true
      belowUsed += width + PART_GAP_DP
      return Slot.BELOW
    }

    val due = if (dueBeside) Slot.NAME_LINE else place(dueWidthDp)
    val priority = place(priorityWidthDp)
    val wanted = minOf(tagLabels.size, MAX_TAG_CHIPS)
    val tags: Slot
    val tagChips: Int
    when {
      tagLabels.isEmpty() -> { tags = Slot.NONE; tagChips = 0 }
      !overflow && fitTagChips(tagLabels, chipSp, budget) == wanted -> {
        tags = Slot.META_LINE
        tagChips = wanted
        budget -= tagGroupWidthDp(tagLabels, wanted, chipSp) + PART_GAP_DP
      }
      else -> { tags = Slot.BELOW; tagChips = fitTagChips(tagLabels, chipSp, textWidthDp) }
    }
    val inline = tags == Slot.BELOW && belowUsed > 0f &&
      belowUsed + tagGroupWidthDp(tagLabels, tagChips, chipSp) <= textWidthDp
    // Whatever the right-aligned parts left over (plus the reserve) is the list name's.
    val listRoom = budget + listReserve + PART_GAP_DP
    return RowLayout(due, priority, tags, tagChips, listNameChars(listRoom, 0f, metaSp), inline)
  }

  /** Width of [shown] tag chips of [labels] plus the "+N" counter for the rest. */
  fun tagGroupWidthDp(labels: List<String>, shown: Int, fontSp: Float): Float {
    val chips = labels.take(shown).sumOf { tagChipWidthDp(it, fontSp).toDouble() }.toFloat()
    val plus = if (shown < labels.size) textWidthDp("+${labels.size - shown}".length, fontSp) else 0f
    return chips + plus
  }

  /** Max characters of the list name so the line-1 siblings (due chip, priority) still fit [availableDp]. */
  fun listNameChars(availableDp: Float, reservedDp: Float, fontSp: Float, minChars: Int = 6, maxChars: Int = 24): Int =
    ((availableDp - reservedDp) / (fontSp * GLYPH_EM)).toInt().coerceIn(minChars, maxChars)

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
