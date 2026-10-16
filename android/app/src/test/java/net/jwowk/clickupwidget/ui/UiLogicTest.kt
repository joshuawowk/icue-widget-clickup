package net.jwowk.clickupwidget.ui

import java.io.IOException
import net.jwowk.clickupwidget.data.model.ClickUpApiException
import net.jwowk.clickupwidget.formatRefreshMinutes
import net.jwowk.clickupwidget.ui.common.ColorHex
import net.jwowk.clickupwidget.ui.common.ConnectError
import net.jwowk.clickupwidget.ui.common.connectErrorFor
import net.jwowk.clickupwidget.ui.config.ImageBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {
  @Test
  fun hexParsesSixDigitsWithOrWithoutHash() {
    assertEquals(0xFF9F8FFFL, ColorHex.parse("#9f8fff"))
    assertEquals(0xFF15161CL, ColorHex.parse(" 15161C "))
  }

  @Test
  fun hexRejectsBadInput() {
    assertNull(ColorHex.parse(""))
    assertNull(ColorHex.parse("#12345"))
    assertNull(ColorHex.parse("#GGGGGG"))
    assertNull(ColorHex.parse("#1234567"))
  }

  @Test
  fun hexFormatDropsAlpha() {
    assertEquals("#9F8FFF", ColorHex.format(0xFF9F8FFFL))
    assertEquals("#000000", ColorHex.format(0x80000000L))
  }

  @Test
  fun connectErrorMapping() {
    assertEquals(ConnectError.INVALID_TOKEN, connectErrorFor(ClickUpApiException(401)))
    assertEquals(ConnectError.SERVER, connectErrorFor(ClickUpApiException(500)))
    assertEquals(ConnectError.NETWORK, connectErrorFor(IOException("offline")))
    assertEquals(ConnectError.UNKNOWN, connectErrorFor(IllegalStateException()))
  }

  @Test
  fun refreshLabels() {
    assertEquals(listOf("15 min", "30 min", "1 h", "2 h"), listOf(15, 30, 60, 120).map(::formatRefreshMinutes))
  }

  @Test
  fun blurRadiusZeroIsNoOp() {
    val px = IntArray(16) { 0xFF000000.toInt() or (it * 16) }
    val copy = px.copyOf()
    ImageBlur.blur(px, 4, 4, 0)
    assertTrue(px.contentEquals(copy))
  }

  @Test
  fun blurKeepsUniformImageUniformAndOpaque() {
    val color = 0xFF336699.toInt()
    val px = IntArray(10 * 7) { color }
    ImageBlur.blur(px, 10, 7, 5)
    assertTrue(px.all { it == color })
  }

  @Test
  fun blurSpreadsAnImpulseAndKeepsRoughMass() {
    val w = 41
    val h = 41
    val px = IntArray(w * h) { 0xFF000000.toInt() }
    px[20 * w + 20] = 0xFFFFFFFF.toInt()
    ImageBlur.blur(px, w, h, 3)
    val center = px[20 * w + 20] and 0xFF
    val neighbour = px[20 * w + 22] and 0xFF
    assertTrue("center dimmed", center < 255)
    assertTrue("energy spread to neighbours", neighbour > 0)
    assertTrue("peak stays at center", center >= neighbour)
    val sum = px.sumOf { (it and 0xFF).toLong() }
    assertTrue("mass roughly preserved (was 255, got $sum)", sum in 150..300)
  }
}
