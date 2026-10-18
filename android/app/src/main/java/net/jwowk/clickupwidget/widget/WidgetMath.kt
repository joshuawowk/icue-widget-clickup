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

  /** Where a task row's tag chips are drawn. */
  enum class Slot {
    /** Left-aligned on the list-name line (line 2), after the list name and requester. */
    META_LINE,
    /** Left-aligned on an extra line underneath (didn't fit on line 2). */
    BELOW,
    /** Not shown (absent or turned off). */
    NONE,
  }

  /** Placement of a task row's tag chips, plus how many list-name and requester chars fit on line 2. */
  data class RowLayout(
    val tags: Slot,
    /** Tag chips to draw (the rest collapse into "+N"). */
    val tagChips: Int,
    /** Max characters of the list name on line 2. */
    val listNameChars: Int,
    /** Max characters of the requester on line 2. */
    val requesterChars: Int,
  )

  /** Gap between the parts of a row line, in dp. */
  const val PART_GAP_DP = 8f
  /** The list name and the requester each keep at least this much of line 2 (or their own width if shorter), in dp. */
  const val META_TEXT_MIN_DP = 56f

  /**
   * Lays out line 2 of a task row (the due chip always sits right of the name on line 1, so it needs no math):
   * list name, requester and tag chips left-aligned, priority right-aligned. Priority keeps its room; the tags stay
   * on line 2 while the list name and requester still get [META_TEXT_MIN_DP] each, otherwise they drop to a line
   * below. The two texts then share what is left ([shareRoom]). Widths are estimates in dp: [textWidthDp] is the
   * text area between the check circle and the star; null widths = part absent. [requesterChromeDp] is the
   * requester's color dot + gap, on top of [requesterWidthDp] (its text).
   */
  fun rowLayout(
    textWidthDp: Float,
    listNameWidthDp: Float?,
    requesterWidthDp: Float?,
    priorityWidthDp: Float?,
    tagLabels: List<String>,
    chipSp: Float,
    metaSp: Float,
    requesterChromeDp: Float = 0f,
  ): RowLayout {
    val left = textWidthDp - (priorityWidthDp?.let { it + PART_GAP_DP } ?: 0f)
    val requesterFull = requesterWidthDp?.let { it + requesterChromeDp }
    val texts = listOfNotNull(listNameWidthDp, requesterFull)
    fun gaps(parts: Int) = maxOf(0, parts - 1) * PART_GAP_DP

    val wanted = minOf(tagLabels.size, MAX_TAG_CHIPS)
    val textReserve = texts.sumOf { minOf(it, META_TEXT_MIN_DP).toDouble() }.toFloat()
    val tags: Slot
    val tagChips: Int
    when {
      tagLabels.isEmpty() -> { tags = Slot.NONE; tagChips = 0 }
      fitTagChips(tagLabels, chipSp, left - textReserve - gaps(texts.size + 1)) == wanted -> {
        tags = Slot.META_LINE
        tagChips = wanted
      }
      else -> { tags = Slot.BELOW; tagChips = fitTagChips(tagLabels, chipSp, textWidthDp) }
    }

    val tagsOnLine = tags == Slot.META_LINE
    val textRoom = left - (if (tagsOnLine) tagGroupWidthDp(tagLabels, tagChips, chipSp) else 0f) -
      gaps(texts.size + (if (tagsOnLine) 1 else 0))
    val (listRoom, requesterRoom) = shareRoom(textRoom, listNameWidthDp ?: 0f, requesterFull ?: 0f)
    return RowLayout(tags, tagChips, textChars(listRoom, metaSp), textChars(requesterRoom - requesterChromeDp, metaSp))
  }

  /**
   * Splits [room] between two texts of natural widths [a] and [b] (0 = absent): both stay whole when they fit;
   * otherwise one that needs at most half stays whole and the other gets the rest; otherwise half each. Spare
   * room is handed to both, so rounding never cuts a text that fits.
   */
  fun shareRoom(room: Float, a: Float, b: Float): Pair<Float, Float> {
    val half = room / 2
    return when {
      a + b <= room -> (room - b) to (room - a)
      a <= half -> half to (room - a)
      b <= half -> (room - b) to half
      else -> half to half
    }
  }

  /** Width of [shown] tag chips of [labels] plus the "+N" counter for the rest. */
  fun tagGroupWidthDp(labels: List<String>, shown: Int, fontSp: Float): Float {
    val chips = labels.take(shown).sumOf { tagChipWidthDp(it, fontSp).toDouble() }.toFloat()
    val plus = if (shown < labels.size) textWidthDp("+${labels.size - shown}".length, fontSp) else 0f
    return chips + plus
  }

  /** Max characters of plain meta text that fit [availableDp] at [fontSp], clamped to [minChars]..[maxChars]. */
  fun textChars(availableDp: Float, fontSp: Float, minChars: Int = 6, maxChars: Int = 24): Int =
    (availableDp / (fontSp * GLYPH_EM)).toInt().coerceIn(minChars, maxChars)

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
