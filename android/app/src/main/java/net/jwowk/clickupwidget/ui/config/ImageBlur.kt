package net.jwowk.clickupwidget.ui.config

import kotlin.math.max
import kotlin.math.min

/** Pure-Kotlin blur on ARGB pixel arrays (no Android classes, so it is unit-testable). */
object ImageBlur {
  const val MAX_RADIUS = 25

  /**
   * Blurs [pixels] (row-major ARGB, [width] x [height]) in place with three successive box passes in each
   * direction, which approximates a Gaussian with sigma ~ [radius] px. Edges are clamped; the result is opaque.
   * A [radius] <= 0 leaves the pixels untouched.
   */
  fun blur(pixels: IntArray, width: Int, height: Int, radius: Int) {
    require(pixels.size >= width * height) { "pixel array too small" }
    val r = radius.coerceAtMost(MAX_RADIUS)
    if (r <= 0 || width <= 0 || height <= 0) return
    val tmp = IntArray(width * height)
    repeat(3) {
      boxPass(pixels, tmp, width, height, r, horizontal = true)
      boxPass(tmp, pixels, width, height, r, horizontal = false)
    }
  }

  /** One running-sum box pass of window 2r+1 along rows (horizontal) or columns, clamped at the edges. */
  private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val stride = if (horizontal) 1 else w
    val lineStep = if (horizontal) w else 1
    val div = 2 * r + 1
    val half = div / 2
    for (line in 0 until lines) {
      val base = line * lineStep
      var sr = 0
      var sg = 0
      var sb = 0
      for (k in -r..r) {
        val p = src[base + k.coerceIn(0, len - 1) * stride]
        sr += (p shr 16) and 0xFF
        sg += (p shr 8) and 0xFF
        sb += p and 0xFF
      }
      for (i in 0 until len) {
        dst[base + i * stride] = (0xFF shl 24) or (((sr + half) / div) shl 16) or (((sg + half) / div) shl 8) or ((sb + half) / div)
        val add = src[base + min(i + r + 1, len - 1) * stride]
        val rem = src[base + max(i - r, 0) * stride]
        sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
        sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
        sb += (add and 0xFF) - (rem and 0xFF)
      }
    }
  }
}
