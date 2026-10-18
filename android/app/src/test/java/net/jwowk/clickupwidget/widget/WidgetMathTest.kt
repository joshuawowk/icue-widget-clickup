package net.jwowk.clickupwidget.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMathTest {
  @Test fun quantizeOpacityRoundsToNearestTen() {
    assertEquals(0, WidgetMath.quantizeOpacity(0))
    assertEquals(0, WidgetMath.quantizeOpacity(4))
    assertEquals(10, WidgetMath.quantizeOpacity(5))
    assertEquals(70, WidgetMath.quantizeOpacity(66))
    assertEquals(100, WidgetMath.quantizeOpacity(96))
    assertEquals(100, WidgetMath.quantizeOpacity(140))
    assertEquals(0, WidgetMath.quantizeOpacity(-3))
  }

  @Test fun headerLayoutDropsPartsAsWidthShrinks() {
    val wide = WidgetMath.headerLayout(320f, 1f, showCount = true)
    assertTrue(wide.showTasksWord && wide.showCount && wide.showRefresh)
    assertEquals(32, wide.buttonDp)
    val mid = WidgetMath.headerLayout(250f, 1f, showCount = true)
    assertTrue(!mid.showTasksWord && mid.showCount && mid.showRefresh)
    val small = WidgetMath.headerLayout(170f, 1f, showCount = true, compact = true)
    assertTrue(!small.showCount && small.showRefresh)
    assertEquals(28, small.buttonDp)
    val tiny = WidgetMath.headerLayout(120f, 1f, showCount = true)
    assertTrue(!tiny.showCount && !tiny.showRefresh)
  }

  @Test fun headerLayoutHonoursShowCountOffAndScale() {
    val off = WidgetMath.headerLayout(400f, 1f, showCount = false)
    assertTrue(!off.showCount && !off.showTasksWord && off.showRefresh)
    assertTrue(!WidgetMath.headerLayout(290f, 1.4f, showCount = true).showTasksWord)
  }

  @Test fun wrapRowsFillsGreedily() {
    val rows = WidgetMath.wrapRows(listOf(50f, 50f, 50f, 120f, 10f), availableDp = 120f)
    assertEquals(listOf(listOf(0, 1), listOf(2), listOf(3), listOf(4)), rows)
  }

  @Test fun wrapRowsOversizedChipGetsOwnRow() {
    val rows = WidgetMath.wrapRows(listOf(300f, 20f), availableDp = 100f)
    assertEquals(listOf(listOf(0), listOf(1)), rows)
  }

  @Test fun wrapRowsCapsChipsPerRow() {
    val rows = WidgetMath.wrapRows(List(5) { 1f }, availableDp = 1000f, maxPerRow = 2)
    assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), rows)
  }

  @Test fun wrapRowsEmpty() {
    assertTrue(WidgetMath.wrapRows(emptyList(), 100f).isEmpty())
  }

  @Test fun chipWidthGrowsWithLabelAndFont() {
    assertEquals(WidgetMath.CHIP_CHROME_DP, WidgetMath.chipWidthDp("", 11f), 0.001f)
    assertTrue(WidgetMath.chipWidthDp("In Progress", 11f) > WidgetMath.chipWidthDp("Open", 11f))
    assertTrue(WidgetMath.chipWidthDp("Open", 14f) > WidgetMath.chipWidthDp("Open", 11f))
  }

  @Test fun tagLabelEllipsizesLongNames() {
    assertEquals("meeting", WidgetMath.tagLabel("meeting"))
    assertEquals(WidgetMath.MAX_TAG_CHARS, WidgetMath.tagLabel("a-very-long-tag-name-indeed").length)
  }

  @Test fun fitTagChipsShowsAllWhenRoomy() {
    assertEquals(0, WidgetMath.fitTagChips(emptyList(), 11f, 300f))
    assertEquals(2, WidgetMath.fitTagChips(listOf("meeting", "bug"), 11f, 300f))
  }

  @Test fun fitTagChipsCapsAtThree() {
    assertEquals(3, WidgetMath.fitTagChips(listOf("a", "b", "c", "d", "e"), 11f, 1000f))
  }

  @Test fun fitTagChipsDropsChipsThatDoNotFitAndReservesPlusN() {
    val labels = listOf("meeting", "bug", "urgent")
    val w = labels.map { WidgetMath.tagChipWidthDp(it, 11f) }
    // Exactly the first two chips, but then "+1" would not fit: only one chip is shown.
    assertEquals(1, WidgetMath.fitTagChips(labels, 11f, w[0] + w[1]))
    // Two chips plus the "+1" counter fit.
    assertEquals(2, WidgetMath.fitTagChips(labels, 11f, w[0] + w[1] + WidgetMath.textWidthDp(2, 11f) + 0.01f))
    // All three fit with no counter needed.
    assertEquals(3, WidgetMath.fitTagChips(labels, 11f, w.sum() + 0.01f))
  }

  @Test fun fitTagChipsReturnsZeroWhenNothingFits() {
    assertEquals(0, WidgetMath.fitTagChips(listOf("meeting"), 11f, 20f))
  }

  @Test fun fitTextKeepsWholeTextOrEllipsizesToTheRoom() {
    // ESTIMATE at 12sp: 7.2dp per char. "Engineering" = 79.2dp.
    assertEquals("Engineering", WidgetMath.fitText("Engineering", 79.3f, 12f))
    assertEquals("Enginee…", WidgetMath.fitText("Engineering", 60f, 12f)) // 8 chars = 57.6dp
    assertEquals("Engin…", WidgetMath.fitText("Engineering", 10f, 12f)) // floor: 6 chars
    assertEquals("Ops", WidgetMath.fitText("Ops", 1f, 12f)) // shorter than the floor: kept
  }

  @Test fun breakpoints() {
    assertEquals(WidgetMath.Breakpoints(narrow = true, short = true), WidgetMath.breakpoints(150f, 100f))
    assertEquals(WidgetMath.Breakpoints(narrow = false, short = false), WidgetMath.breakpoints(250f, 200f))
  }

  @Test fun textScaleClampsAndShrinksWhenNarrow() {
    assertEquals(1f, WidgetMath.textScale(100, narrow = false), 0.001f)
    assertEquals(1.4f, WidgetMath.textScale(200, narrow = false), 0.001f)
    assertEquals(0.8f, WidgetMath.textScale(10, narrow = false), 0.001f)
    assertTrue(WidgetMath.textScale(100, narrow = true) < 1f)
  }

  @Test fun stableIdIsDeterministicAndNonNegative() {
    val a = WidgetMath.stableId("86abc123")
    assertEquals(a, WidgetMath.stableId("86abc123"))
    assertNotEquals(a, WidgetMath.stableId("86abc124"))
    listOf("", "x", "task-1", "__filter").forEach { assertTrue(WidgetMath.stableId(it) >= 0) }
  }

  @Test fun ellipsize() {
    assertEquals("short", WidgetMath.ellipsize("short", 10))
    assertEquals("abcd…", WidgetMath.ellipsize("abcdefghij", 5))
    assertFalse(WidgetMath.ellipsize("abcdefghij", 5).length > 5)
  }

  @Test fun sampleSizeKeepsLongEdgeUnderLimit() {
    assertEquals(1, WidgetMath.sampleSize(720, 400, 720))
    assertEquals(2, WidgetMath.sampleSize(1440, 900, 720))
    assertEquals(8, WidgetMath.sampleSize(3000, 4000, 720))
  }

  // ---- rowLayout: line 2 = list name, requester, tags (left) + priority (right) ----

  // With ESTIMATE, at metaSp 12 one char is 7.2dp; at chipSp 11 a tag chip is 6.6dp per char + 16dp chrome.
  private fun layout(
    textW: Float,
    list: String? = "Ops team", // 57.6dp
    req: String? = "Dana Reyes", // 72dp
    prio: Float? = 45f,
    tags: List<String> = emptyList(),
    measure: WidgetMath.TextMeasure = WidgetMath.ESTIMATE,
  ) = WidgetMath.rowLayout(textW, list, req, prio, tags, chipSp = 11f, metaSp = 12f, measure = measure)

  @Test fun rowLayoutWideRowKeepsEverythingOnLine2() {
    val l = layout(400f, tags = listOf("bug", "customer"))
    assertEquals(WidgetMath.Slot.META_LINE, l.tags)
    assertEquals(2, l.tagChips)
    assertEquals("Ops team", l.listName)
    assertEquals("Dana Reyes", l.requester)
  }

  @Test fun rowLayoutMoreThanThreeTagsShowPlusNOnLine2() {
    val l = layout(400f, tags = listOf("a", "b", "c", "d", "e"))
    assertEquals(WidgetMath.Slot.META_LINE, l.tags)
    assertEquals(3, l.tagChips)
  }

  @Test fun rowLayoutTagsDropBelowRatherThanSqueezeTheTexts() {
    // Left of priority: 220 - 45 - 8 = 167dp; texts keep 56 + 56 + 2 gaps = 128 -> 39dp, too little for "finance".
    val l = layout(220f, list = "Engineering team", req = "Ariana Carlone", tags = listOf("finance", "quarter-end", "ops"))
    assertEquals(WidgetMath.Slot.BELOW, l.tags)
    assertEquals(3, l.tagChips) // all three fit a full line below
    // 167 - 8 = 159dp for two texts both wider than half: 79.5dp each -> 11 chars.
    assertEquals("Engineerin…", l.listName)
    assertEquals("Ariana Car…", l.requester)
  }

  @Test fun rowLayoutRequesterIsTrimmedBeforeTagsLeaveLine2() {
    // 260 - 53 = 207dp; reserve 56 + 56 + 16 = 128 leaves 79dp: "bug" (35.8dp) stays on line 2. Texts get
    // 207 - 35.8 - 16 = 155.2dp: the short list name stays whole, the requester gets 155.2 - 57.6 = 97.6dp.
    val l = layout(260f, req = "Ariana Carlone Smith", tags = listOf("bug"))
    assertEquals(WidgetMath.Slot.META_LINE, l.tags)
    assertEquals("Ops team", l.listName)
    assertEquals("Ariana Carlo…", l.requester)
  }

  @Test fun rowLayoutPriorityKeepsItsRoom() {
    // No spaces near the cut (ellipsize trims a trailing space, which would shorten the result).
    val list = "Infrastructure-platform"
    val req = "Bartholomew-Fitzgerald"
    assertEquals(13, layout(200f, list, req, prio = null).listName!!.length) // (200 - 8) / 2 = 96dp
    val with = layout(200f, list, req, prio = 60f) // (200 - 68 - 8) / 2 = 62dp
    assertEquals(8, with.listName!!.length)
    assertEquals(8, with.requester!!.length)
  }

  @Test fun rowLayoutRequesterAloneGetsTheWholeLine() {
    val l = layout(150f, list = null, req = "Ariana Carlone Smith-Jones", prio = null)
    assertEquals(20, l.requester!!.length) // 150 / 7.2
  }

  @Test fun rowLayoutRequesterChromeComesOutOfItsRoom() {
    val l = WidgetMath.rowLayout(
      150f, null, "Ariana Carlone Smith-Jones", null, emptyList(), chipSp = 11f, metaSp = 12f, requesterChromeDp = 14.4f,
    )
    assertEquals(18, l.requester!!.length) // (150 - 14.4) / 7.2
  }

  @Test fun rowLayoutUsesTheGivenMeasure() {
    // At 300dp the estimate leaves 247 - 128 = 119dp for tags needing 186.6dp, so they drop below. A font half as
    // wide needs 117.3dp of the 124.6dp it leaves: tags stay on line 2 and both texts fit whole.
    val narrowFont = WidgetMath.TextMeasure { text, sp, _ -> text.length * sp * 0.3f }
    val tags = listOf("finance", "quarter-end", "ops")
    assertEquals(WidgetMath.Slot.BELOW, layout(300f, list = "Engineering team", req = "Ariana Carlone", tags = tags).tags)
    val l = layout(300f, list = "Engineering team", req = "Ariana Carlone", tags = tags, measure = narrowFont)
    assertEquals(WidgetMath.Slot.META_LINE, l.tags)
    assertEquals("Engineering team", l.listName)
    assertEquals("Ariana Carlone", l.requester)
  }

  @Test fun rowLayoutAbsentPartsAreNone() {
    val l = layout(300f, list = null, req = null, prio = null)
    assertEquals(WidgetMath.Slot.NONE, l.tags)
    assertEquals(0, l.tagChips)
    assertEquals(null, l.listName)
    assertEquals(null, l.requester)
  }

  @Test fun shareRoomSplitsFairly() {
    assertEquals(60f to 70f, WidgetMath.shareRoom(100f, 30f, 40f)) // both fit; spare goes to both
    assertEquals(50f to 80f, WidgetMath.shareRoom(100f, 20f, 200f)) // short one whole, long one the rest
    assertEquals(80f to 50f, WidgetMath.shareRoom(100f, 200f, 20f))
    assertEquals(50f to 50f, WidgetMath.shareRoom(100f, 200f, 300f))
  }
}
