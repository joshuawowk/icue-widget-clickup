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
}
