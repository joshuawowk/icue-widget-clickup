package net.jwowk.clickupwidget.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.jwowk.clickupwidget.data.model.TaskField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/**
 * Fixed instants used below (UTC): 1792051200000 = Thu 2026-10-15 08:00, 1792108800000 = Fri 2026-10-16 00:00.
 */
class TaskFieldsTest {
  private val utc = ZoneId.of("UTC")

  private fun fields(json: String): List<TaskField> =
    taskFields(Json.parseToJsonElement(json).jsonObject, Locale.US, utc)

  /** One custom field of [type] with [value] (raw JSON) and optional [typeConfig] (raw JSON). */
  private fun single(type: String, value: String?, typeConfig: String? = null): TaskField? {
    val v = value?.let { ""","value":$it""" }.orEmpty()
    val tc = typeConfig?.let { ""","type_config":$it""" }.orEmpty()
    return fields("""{"custom_fields":[{"name":"F","type":"$type"$v$tc}]}""").singleOrNull()
  }

  @Test
  fun emptyTask_hasNoFields() {
    assertTrue(fields("{}").isEmpty())
    assertTrue(fields("""{"assignees":[],"start_date":null,"time_estimate":null,"points":null,"custom_fields":[]}""").isEmpty())
  }

  @Test
  fun standardFields_inOrder() {
    val out = fields(
      """
      {"assignees":[{"username":"Josh"},{"username":"Becky"},{"username":""},{"id":3}],
       "start_date":"1792051200000","time_estimate":5400000,"points":3,
       "date_created":"1792108800000","creator":{"username":"Ariana"},
       "custom_fields":[{"name":"Incident No.","type":"short_text","value":"INC1"}]}
      """.trimIndent(),
    )
    assertEquals(
      listOf(
        TaskField("Assignees", "Josh, Becky"),
        TaskField("Start date", "Thu, Oct 15, 2026"),
        TaskField("Time estimate", "1h 30m"),
        TaskField("Sprint points", "3"),
        TaskField("Created", "Fri, Oct 16, 2026 by Ariana"),
        TaskField("Incident No.", "INC1"),
      ),
      out,
    )
  }

  @Test
  fun standardFields_followSpecOrderRegardlessOfJsonOrder() {
    val out = fields("""{"date_created":1792108800000,"points":1,"time_estimate":60000,"start_date":1792051200000,"assignees":[{"username":"A"}]}""")
    assertEquals(listOf("Assignees", "Start date", "Time estimate", "Sprint points", "Created"), out.map { it.name })
  }

  @Test
  fun created_withoutCreatorHasNoBy() {
    assertEquals(listOf(TaskField("Created", "Fri, Oct 16, 2026")), fields("""{"date_created":1792108800000}"""))
  }

  @Test
  fun startDate_numberOrString_zeroSkipped() {
    assertEquals(listOf(TaskField("Start date", "Thu, Oct 15, 2026")), fields("""{"start_date":1792051200000}"""))
    assertTrue(fields("""{"start_date":"0"}""").isEmpty())
  }

  @Test
  fun dateUsesTheGivenZone() {
    val task = Json.parseToJsonElement("""{"start_date":"1792108800000"}""").jsonObject
    assertEquals("Thu, Oct 15, 2026", taskFields(task, Locale.US, ZoneId.of("America/Los_Angeles")).single().value)
    assertEquals("Fri, Oct 16, 2026", taskFields(task, Locale.US, utc).single().value)
  }

  @Test
  fun dateIsLocalized() {
    val task = Json.parseToJsonElement("""{"start_date":"1792051200000"}""").jsonObject
    val de = taskFields(task, Locale.GERMANY, utc).single().value
    assertTrue(de, de.contains("Do") && de.contains("2026") && !de.contains("Thu"))
  }

  @Test
  fun timeEstimate_formats() {
    fun est(ms: Long) = fields("""{"time_estimate":"$ms"}""").singleOrNull()?.value
    assertEquals("45m", est(2_700_000))
    assertEquals("2h", est(7_200_000))
    assertEquals("1h 30m", est(5_400_000))
    assertEquals("26h 5m", est(26 * 3_600_000L + 5 * 60_000L))
    assertEquals("1m", est(20_000))
    assertNull(est(0))
    assertTrue(fields("""{"time_estimate":null}""").isEmpty())
  }

  @Test
  fun sprintPoints_zeroKeptNullSkippedDecimalsNormalised() {
    assertEquals("0", fields("""{"points":0}""").single().value)
    assertEquals("2.5", fields("""{"points":2.50}""").single().value)
    assertEquals("8", fields("""{"points":8.0}""").single().value)
    assertTrue(fields("""{"points":null}""").isEmpty())
  }

  @Test
  fun textTypes_trimmedAndBlankSkipped() {
    for (t in listOf("text", "short_text", "email", "phone", "url")) {
      assertEquals(t, TaskField("F", "x@y.z"), single(t, "\"  x@y.z \""))
      assertNull(t, single(t, "\"   \""))
      assertNull(t, single(t, "\"\""))
      assertNull(t, single(t, "null"))
      assertNull(t, single(t, null))
    }
  }

  @Test
  fun realExamples() {
    val out = fields(
      """
      {"custom_fields":[
        {"type":"drop_down","name":"Requester","value":17,"type_config":{"options":[
          {"id":"cad9","name":"Ariana Carlone","color":"#9b59b6","orderindex":0},
          {"id":"beef","name":"Becky Puleo","color":"#800000","orderindex":17}]}},
        {"type":"date","name":"Done Date","value":"1792051200000"},
        {"type":"short_text","name":"Incident No.","value":"INC2383904"},
        {"type":"number","name":"Epicor Task No","value":"9","type_config":{"number_format":"en-US"}},
        {"type":"text","name":"Incident Description","value":"Good day,\n\nPlease help."},
        {"type":"automatic_progress","name":"Progress","value":{"percent_complete":0}}
      ]}
      """.trimIndent(),
    )
    assertEquals(
      listOf(
        TaskField("Requester", "Becky Puleo", "#800000"),
        TaskField("Done Date", "Thu, Oct 15, 2026"),
        TaskField("Incident No.", "INC2383904"),
        TaskField("Epicor Task No", "9"),
        TaskField("Incident Description", "Good day,\n\nPlease help."),
      ),
      out,
    )
  }

  @Test
  fun number_andCurrency() {
    assertEquals(TaskField("F", "12.5"), single("number", "12.5"))
    assertEquals(TaskField("F", "7"), single("number", "7"))
    assertEquals(TaskField("F", "0"), single("number", "0"))
    assertNull(single("number", "\"\""))
    assertEquals(TaskField("F", "USD 12.5"), single("currency", "12.5", """{"currency_type":"USD"}"""))
    assertEquals(TaskField("F", "EUR 100"), single("currency", "\"100\"", """{"currency_type":"EUR"}"""))
    assertEquals(TaskField("F", "3"), single("currency", "3"))
  }

  @Test
  fun dropDown_byOrderindexByIdAndMissing() {
    val cfg = """{"options":[{"id":"a1","name":"Alpha","color":"#111111","orderindex":0},{"id":"b2","name":"Beta","color":null,"orderindex":1}]}"""
    assertEquals(TaskField("F", "Alpha", "#111111"), single("drop_down", "0", cfg))
    assertEquals(TaskField("F", "Beta", null), single("drop_down", "1", cfg))
    assertEquals(TaskField("F", "Beta", null), single("drop_down", "\"b2\"", cfg))
    assertEquals(TaskField("F", "Alpha", "#111111"), single("drop_down", "\"a1\"", cfg))
    assertEquals(TaskField("F", "Beta", null), single("drop_down", "\"1\"", cfg))
    assertNull(single("drop_down", "5", cfg))
    assertNull(single("drop_down", "\"zzz\"", cfg))
    assertNull(single("drop_down", "null", cfg))
    assertNull(single("drop_down", "1", null))
  }

  @Test
  fun labels_joinLabelOrName() {
    val cfg = """{"options":[{"id":"x","label":"Red"},{"id":"y","name":"Blue"},{"id":"z","label":"Green"}]}"""
    assertEquals(TaskField("F", "Red, Blue"), single("labels", """["x","y","gone"]""", cfg))
    assertNull(single("labels", "[]", cfg))
    assertNull(single("labels", """["gone"]""", cfg))
  }

  @Test
  fun date_formatsOrSkips() {
    assertEquals(TaskField("F", "Fri, Oct 16, 2026"), single("date", "\"1792108800000\""))
    assertEquals(TaskField("F", "Fri, Oct 16, 2026"), single("date", "1792108800000"))
    assertNull(single("date", "null"))
    assertNull(single("date", "\"\""))
  }

  @Test
  fun checkbox_onlyTrue() {
    assertEquals(TaskField("F", "Yes"), single("checkbox", "true"))
    assertEquals(TaskField("F", "Yes"), single("checkbox", "\"true\""))
    assertNull(single("checkbox", "false"))
    assertNull(single("checkbox", "null"))
  }

  @Test
  fun users_andTasks() {
    assertEquals(TaskField("F", "Ann, Bob"), single("users", """[{"username":"Ann","id":1},{"username":"Bob"}]"""))
    assertNull(single("users", "[]"))
    assertEquals(TaskField("F", "One, Two"), single("tasks", """[{"id":"1","name":"One"},{"id":"2","name":"Two"}]"""))
    assertNull(single("tasks", "[]"))
  }

  @Test
  fun emoji_rating() {
    assertEquals(TaskField("F", "★★★☆☆"), single("emoji", "3", """{"count":5}"""))
    assertEquals(TaskField("F", "★★★☆☆"), single("emoji", "\"3\"", """{"count":5}"""))
    assertEquals(TaskField("F", "★★☆☆☆"), single("emoji", "2"))
    assertEquals(TaskField("F", "★★★★"), single("emoji", "9", """{"count":4}"""))
    assertNull(single("emoji", "0", """{"count":5}"""))
    assertNull(single("emoji", "null"))
  }

  @Test
  fun progress_percent() {
    for (t in listOf("automatic_progress", "manual_progress")) {
      assertEquals(TaskField("F", "33%"), single(t, """{"percent_complete":33}"""))
      assertEquals(TaskField("F", "40%"), single(t, """{"percent_completed":40}"""))
      assertEquals(TaskField("F", "33%"), single(t, """{"percent_complete":"33"}"""))
      assertNull(single(t, """{"percent_complete":0}"""))
      assertNull(single(t, """{}"""))
    }
  }

  @Test
  fun location_formula_attachment() {
    assertEquals(TaskField("F", "1 Main St, Perth"), single("location", """{"formatted_address":"1 Main St, Perth","location":{"lat":1}}"""))
    assertNull(single("location", """{"location":{"lat":1}}"""))
    assertEquals(TaskField("F", "42"), single("formula", "42"))
    assertEquals(TaskField("F", "done"), single("formula", "\"done\""))
    assertEquals(TaskField("F", "3 files"), single("attachment", """[{"id":"a"},{"id":"b"},{"id":"c"}]"""))
    assertEquals(TaskField("F", "1 file"), single("attachment", """[{"id":"a"}]"""))
    assertNull(single("attachment", "[]"))
  }

  @Test
  fun unknownType_primitiveOnly() {
    assertEquals(TaskField("F", "hello"), single("brand_new", "\"hello\""))
    assertEquals(TaskField("F", "5"), single("brand_new", "5"))
    assertNull(single("brand_new", """{"a":1}"""))
    assertNull(single("brand_new", "[1,2]"))
    assertNull(single("brand_new", "\"\""))
  }

  @Test
  fun customFields_keepApiOrder_andSkipUnnamedOrMalformed() {
    val out = fields(
      """
      {"custom_fields":[
        {"name":"Z","type":"short_text","value":"1"},
        {"type":"short_text","value":"no name"},
        "junk",
        {"name":"A","type":"short_text","value":"2"},
        {"name":"Empty","type":"short_text","value":""}
      ]}
      """.trimIndent(),
    )
    assertEquals(listOf("Z", "A"), out.map { it.name })
  }

  @Test
  fun malformedContainersAreTolerated() {
    val o: JsonObject = Json.parseToJsonElement("""{"assignees":"x","custom_fields":{"a":1},"creator":5,"tags":3}""").jsonObject
    assertTrue(taskFields(o, Locale.US, utc).isEmpty())
  }

  @Test
  fun unifiedRules_progressLabelsEmoji() {
    assertNull(single("manual_progress", """{"percent_complete":0.4}"""))
    assertEquals(TaskField("F", "1%"), single("manual_progress", """{"percent_complete":0.6}"""))
    val cfg = """{"options":[{"id":"x","label":"","name":"Fallback"},{"id":"y","label":"Blue","name":"n"}]}"""
    assertEquals(TaskField("F", "Fallback, Blue"), single("labels", """["x","y"]""", cfg))
    assertEquals(TaskField("F", "★★★★★"), single("emoji", "5"))
    assertEquals(TaskField("F", "★★★"), single("emoji", "7", """{"count":3}"""))
  }
}
