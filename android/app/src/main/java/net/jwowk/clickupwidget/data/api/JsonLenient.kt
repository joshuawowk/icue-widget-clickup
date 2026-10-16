package net.jwowk.clickupwidget.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Lenient accessors for ClickUp JSON, where ids/numbers may be strings or numbers and fields may be null or absent.

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

/** Primitive content as a string (numbers included); null for JsonNull, objects, arrays and absent values. */
internal fun JsonElement?.asString(): String? =
  (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun JsonElement?.asLong(): Long? {
  val s = asString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
  return s.toLongOrNull() ?: s.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

internal fun JsonObject.str(key: String): String? = this[key].asString()

internal fun JsonObject.long(key: String): Long? = this[key].asLong()

/** Elements of the array at [key]; empty when absent, null or not an array. */
internal fun JsonElement.array(key: String): List<JsonElement> =
  ((this as? JsonObject)?.get(key) as? JsonArray) ?: emptyList()
