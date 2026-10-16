package net.jwowk.clickupwidget.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.jwowk.clickupwidget.data.model.TaskField
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

// Task-field formatting shared (by spec) with the iCUE widget: populated standard fields first, then
// custom_fields in API order. Only populated fields are returned. Android-free so it is unit-testable on the JVM.

/**
 * Display-ready, read-only fields of one ClickUp task JSON object (`GET /task/{id}`), in display order:
 * Assignees, Start date, Time estimate, Sprint points, Created, then the custom fields.
 */
fun taskFields(
  task: JsonObject,
  locale: Locale = Locale.getDefault(),
  zone: ZoneId = ZoneId.systemDefault(),
): List<TaskField> = buildList {
  fun add(name: String, value: String?, color: String? = null) {
    val v = value?.takeIf { it.isNotBlank() } ?: return
    add(TaskField(name, v, color?.takeIf { it.isNotBlank() }))
  }

  add("Assignees", task.array("assignees").mapNotNull { it.asObject()?.str("username")?.trim()?.takeIf(String::isNotEmpty) }
    .joinToString(", "))
  task.long("start_date")?.takeIf { it > 0 }?.let { add("Start date", formatFieldDate(it, locale, zone)) }
  task.long("time_estimate")?.takeIf { it > 0 }?.let { add("Time estimate", formatEstimate(it)) }
  task["points"].asString()?.let { add("Sprint points", numberText(task["points"])) }
  task.long("date_created")?.takeIf { it > 0 }?.let { created ->
    val by = task["creator"].asObject()?.str("username")?.trim()?.takeIf { it.isNotEmpty() }
    add("Created", formatFieldDate(created, locale, zone) + (by?.let { " by $it" }.orEmpty()))
  }

  task.array("custom_fields").forEach { f ->
    val field = f.asObject() ?: return@forEach
    val name = field.str("name")?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
    customField(field, locale, zone)?.let { (value, color) -> add(name, value, color) }
  }
}

/** Weekday + month + day + year in [locale], e.g. "Thu, Oct 16, 2026" for en-US. */
internal fun formatFieldDate(ms: Long, locale: Locale, zone: ZoneId): String {
  val formatter = try {
    // Locale's FULL date pattern with the weekday and month abbreviated ("EEEE, MMMM d, y" -> "EEE, MMM d, y").
    val full = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.FULL, null, IsoChronology.INSTANCE, locale)
    DateTimeFormatter.ofPattern(full.replace(Regex("E{4,}"), "EEE").replace(Regex("M{4,}"), "MMM"), locale)
  } catch (_: RuntimeException) {
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale)
  }
  return formatter.format(Instant.ofEpochMilli(ms).atZone(zone))
}

/** 5_400_000 -> "1h 30m", 2_700_000 -> "45m", 7_200_000 -> "2h". Sub-minute estimates round up to "1m". */
internal fun formatEstimate(ms: Long): String {
  val minutes = maxOf(1L, (ms + 30_000) / 60_000)
  val h = minutes / 60
  val m = minutes % 60
  return when {
    h == 0L -> "${m}m"
    m == 0L -> "${h}h"
    else -> "${h}h ${m}m"
  }
}

/** Numbers print like JS `String(n)` (12.50 -> "12.5", 3.0 -> "3"); strings are kept as they are. */
private fun numberText(e: JsonElement?): String? {
  val p = e as? JsonPrimitive ?: return null
  if (p is JsonNull) return null
  if (p.isString) return p.content.trim().takeIf { it.isNotEmpty() }
  return try {
    BigDecimal(p.content).stripTrailingZeros().toPlainString()
  } catch (_: NumberFormatException) {
    p.content
  }
}

private fun JsonElement?.asBool(): Boolean? = when (asString()?.trim()?.lowercase()) {
  "true" -> true
  "false" -> false
  else -> null
}

private fun options(typeConfig: JsonObject?): List<JsonObject> = typeConfig?.array("options")?.mapNotNull { it.asObject() }.orEmpty()

private fun JsonObject.optionColor(): String? = str("color")?.takeIf { it.isNotBlank() }

/** (value, color) for one custom field, or null when it is not populated / not displayable. */
private fun customField(
  field: JsonObject,
  locale: Locale,
  zone: ZoneId,
): Pair<String, String?>? {
  val type = field.str("type")?.lowercase().orEmpty()
  val value = field["value"]
  if (value == null || value is JsonNull) return null
  val config = field["type_config"].asObject()
  fun plain(s: String?) = s?.takeIf { it.isNotBlank() }?.let { it to null }

  return when (type) {
    "text", "short_text", "email", "phone", "url" -> plain(value.asString()?.trim())
    "number" -> plain(numberText(value))
    "currency" -> numberText(value)?.let { n ->
      val cur = config?.str("currency_type")?.trim().orEmpty()
      (if (cur.isEmpty()) n else "$cur $n") to null
    }
    "drop_down" -> {
      val opts = options(config)
      val prim = value as? JsonPrimitive ?: return null
      // A number is the option's orderindex; a string is an option id (or a numeric orderindex as a string).
      val option = (if (prim.isString) opts.firstOrNull { it.str("id") == prim.content } else null)
        ?: value.asLong()?.let { idx -> opts.firstOrNull { it.long("orderindex") == idx } }
      option?.str("name")?.takeIf { it.isNotBlank() }?.let { it to option.optionColor() }
    }
    "labels" -> {
      val opts = options(config)
      val ids = (value as? JsonArray)?.mapNotNull { it.asString() }.orEmpty()
      plain(ids.mapNotNull { id ->
        opts.firstOrNull { it.str("id") == id }?.let { o -> o.str("label") ?: o.str("name") }?.takeIf { it.isNotBlank() }
      }.joinToString(", "))
    }
    "date" -> value.asLong()?.takeIf { it > 0 }?.let { formatFieldDate(it, locale, zone) to null }
    "checkbox" -> if (value.asBool() == true) "Yes" to null else null
    "users" -> plain((value as? JsonArray)?.mapNotNull { it.asObject()?.str("username")?.trim()?.takeIf(String::isNotEmpty) }
      ?.joinToString(", "))
    "tasks" -> plain((value as? JsonArray)?.mapNotNull { it.asObject()?.str("name")?.trim()?.takeIf(String::isNotEmpty) }
      ?.joinToString(", "))
    "emoji" -> {
      val count = (config?.long("count")?.toInt() ?: DEFAULT_RATING_COUNT).coerceIn(1, 20)
      val n = value.asLong()?.toInt()?.coerceIn(0, count) ?: 0
      if (n <= 0) null else ("★".repeat(n) + "☆".repeat(count - n)) to null
    }
    "automatic_progress", "manual_progress" -> {
      val o = value.asObject()
      val pct = (o?.get("percent_complete") ?: o?.get("percent_completed")).asString()?.toDoubleOrNull()
      pct?.takeIf { it > 0 }?.let { "${Math.round(it)}%" to null }
    }
    "location" -> plain(value.asObject()?.str("formatted_address")?.trim())
    "formula" -> plain(numberText(value))
    "attachment" -> (value as? JsonArray)?.size?.takeIf { it > 0 }?.let { (if (it == 1) "1 file" else "$it files") to null }
    else -> when (value) {
      is JsonPrimitive -> plain(numberText(value))
      else -> null
    }
  }
}

private const val DEFAULT_RATING_COUNT = 5
