package net.jwowk.clickupwidget.ui.common

/** Parsing/formatting of `#RRGGBB` text for the color pickers. Always opaque (opacity is a separate setting). */
object ColorHex {
  /** Parses `RRGGBB` or `#RRGGBB` (case-insensitive, surrounding whitespace ignored) into an opaque ARGB value; null if invalid. */
  fun parse(input: String): Long? {
    val hex = input.trim().removePrefix("#")
    if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return 0xFF000000L or hex.toLong(16)
  }

  /** `#RRGGBB` (alpha dropped). */
  fun format(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)
}
