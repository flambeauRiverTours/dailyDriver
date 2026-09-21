package com.flambeauriver.dailydriver.slm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T033–T035, the structured-output defence (§4.4).
 *
 * Every case here is something a small model actually does. MediaPipe has no grammar-constrained
 * decoding, so none of this is hypothetical — the layers exist because the output cannot be
 * assumed to be JSON at all.
 */
class StructuredOutputTest {

    // --- T033: finding the object ------------------------------------------------------------------

    @Test
    fun `a bare object is found`() {
        assertEquals("""{"intent":"help"}""", firstJsonObject("""{"intent":"help"}"""))
    }

    @Test
    fun `prose around the object is discarded`() {
        // The single most common small-model behaviour: answering the question conversationally.
        val raw = """Sure! Here is the JSON you asked for: {"intent":"help"} Hope that helps."""

        assertEquals("""{"intent":"help"}""", firstJsonObject(raw))
    }

    @Test
    fun `nested objects are kept whole`() {
        // `slots` is always nested (§4.3), so a non-greedy regex stopping at the first brace would
        // truncate every real payload. This is the case that rules regex out.
        val raw = """{"intent":"create_note","slots":{"title":"call the bank"}}"""

        assertEquals(raw, firstJsonObject(raw))
    }

    @Test
    fun `braces inside strings do not close the object`() {
        // A note titled "{weird}" is legitimate content. Counting braces naively truncates valid
        // JSON into invalid JSON, which the next layer then cannot repair.
        val raw = """{"intent":"create_note","slots":{"title":"a {weird} title"}}"""

        assertEquals(raw, firstJsonObject(raw))
    }

    @Test
    fun `escaped quotes do not confuse string tracking`() {
        val raw = """{"intent":"create_note","slots":{"title":"say \"hi\" to bob"}}"""

        assertEquals(raw, firstJsonObject(raw))
    }

    @Test
    fun `the first of several objects wins`() {
        val raw = """{"intent":"help"} and also {"intent":"list_apps"}"""

        assertEquals("""{"intent":"help"}""", firstJsonObject(raw))
    }

    @Test
    fun `truncated output yields nothing`() {
        // maxTokens makes this a real outcome, not a hypothetical. Null routes to T3, which is the
        // right answer for a reply that was cut off.
        assertNull(firstJsonObject("""{"intent":"create_note","slots":{"title":"unfinished"""))
    }

    @Test
    fun `output with no object at all yields nothing`() {
        assertNull(firstJsonObject("I'm not sure what you mean."))
        assertNull(firstJsonObject(""))
    }

    @Test
    fun `a stray closing brace before the object is ignored`() {
        assertEquals("""{"intent":"help"}""", firstJsonObject("""} oops {"intent":"help"}"""))
    }

    // --- T034: repairing it ------------------------------------------------------------------------

    @Test
    fun `markdown fences are stripped`() {
        val raw = "```json\n{\"intent\":\"help\"}\n```"

        assertEquals("""{"intent":"help"}""", repairJson(raw))
    }

    @Test
    fun `bare fences are stripped too`() {
        assertEquals("""{"intent":"help"}""", repairJson("```\n{\"intent\":\"help\"}\n```"))
    }

    @Test
    fun `trailing commas are removed`() {
        assertEquals("""{"a":1}""", repairJson("""{"a":1,}"""))
        assertEquals("""{"a":[1,2]}""", repairJson("""{"a":[1,2,]}"""))
    }

    @Test
    fun `bare keys are quoted`() {
        assertEquals("""{"intent":"help"}""", repairJson("""{intent:"help"}"""))
    }

    @Test
    fun `a colon inside a value is not mistaken for a key`() {
        // quoteBareKeys requires a preceding { or , precisely so this survives untouched.
        val raw = """{"title":"meeting: standup"}"""

        assertEquals(raw, repairJson(raw))
    }

    @Test
    fun `smart quotes are normalized`() {
        assertEquals("""{"intent":"help"}""", repairJson("{\u201cintent\u201d:\u201chelp\u201d}"))
    }

    @Test
    fun `already-valid json survives repair unchanged`() {
        // The repair pass must be a no-op on good input, or it becomes a source of corruption in
        // the common case.
        val raw = """{"intent":"create_note","slots":{"title":"a, b"},"confidence":0.9}"""

        assertEquals(raw, repairJson(raw))
    }

    @Test
    fun `several defects at once are all repaired`() {
        val raw = "```json\n{intent: \"help\", confidence: 0.9,}\n```"

        assertNotNull(parseIntent(raw))
    }

    // --- T035: validating it -----------------------------------------------------------------------

    @Test
    fun `a well-formed result parses`() {
        val result = parseIntent("""{"intent":"create_note","slots":{"title":"call the bank"},"confidence":0.91}""")

        assertNotNull(result)
        assertEquals(Intent.CREATE_NOTE, result!!.intent)
        assertEquals("call the bank", result.slots["title"])
        assertEquals(0.91, result.confidence, 0.0001)
    }

    @Test
    fun `every label in the schema round-trips through the wire form`() {
        // Property test: adding an Intent without a wire spelling is impossible by construction,
        // and this pins that the derivation stays correct.
        Intent.entries.forEach { intent ->
            assertEquals(intent, Intent.fromWire(intent.wire))
        }
    }

    @Test
    fun `the label space covers every reserved command`() {
        // The stale-label-space bug that prompted settling §4.3: four of ten commands had no label.
        //
        // ⚠️ This list is hand-maintained, and on 2026-09-21 it was caught being wrong in exactly
        // the way it exists to prevent: T140 registered `:google` and `:export` and neither brought
        // a label, and this test passed throughout because nobody added them here either. A test
        // that only fails when someone remembers to update it is not a guard. It is kept because a
        // wrong list still beats no list, but the durable fix is an exhaustive `when` over
        // ReservedCommand, which the compiler enforces — see the note on Intent.WEB_SEARCH.
        listOf(
            "list_apps", "search_notes", "create_note", "open_note",
            "add_task", "list_tasks", "list_completed_tasks",
            "list_aliases", "list_notifications", "open_settings",
            "web_search", "export_log", "help",
        ).forEach { wire ->
            assertTrue("no Intent for '$wire'", Intent.fromWire(wire) != Intent.UNKNOWN)
        }
    }

    @Test
    fun `web_search carries its query as a slot`() {
        // The query *is* the command for `:google` (ReservedCommand.Google), so a web_search with
        // no slot is not a usable result even though it validates. Pinned because this label was
        // added late and carries the bulk of the free-form input T2 exists to handle.
        val result = parseIntent(
            """{"intent":"web_search","slots":{"query":"ice price algorithm"},"confidence":0.88}""",
        )

        assertNotNull(result)
        assertEquals(Intent.WEB_SEARCH, result!!.intent)
        assertEquals("ice price algorithm", result.slots["query"])
    }

    @Test
    fun `export_log validates without slots`() {
        // The opposite shape: `:export` takes no argument (ReservedCommand.Export), so an empty
        // slots map is correct rather than missing data.
        val result = parseIntent("""{"intent":"export_log","slots":{},"confidence":0.95}""")

        assertNotNull(result)
        assertEquals(Intent.EXPORT_LOG, result!!.intent)
        assertTrue(result!!.slots.isEmpty())
    }

    @Test
    fun `an invented intent abstains rather than being trusted`() {
        assertNull(parseIntent("""{"intent":"launch_nukes","confidence":0.99}"""))
    }

    @Test
    fun `an explicit unknown abstains`() {
        // `unknown` is a desirable output, and it means the same thing as failing to parse: T3.
        assertNull(parseIntent("""{"intent":"unknown","confidence":0.99}"""))
    }

    @Test
    fun `a missing confidence fails rather than defaulting`() {
        // Defaulting would manufacture the exact number the abstention decision rests on, turning
        // "the model did not answer" into "the model answered confidently".
        assertNull(parseIntent("""{"intent":"help"}"""))
    }

    @Test
    fun `confidence below the threshold abstains`() {
        assertNull(parseIntent("""{"intent":"help","confidence":0.4}"""))
        assertNotNull(parseIntent("""{"intent":"help","confidence":0.7}"""))
    }

    @Test
    fun `the threshold is adjustable, because §8 has to set it from measurements`() {
        val raw = """{"intent":"help","confidence":0.5}"""

        assertNull(parseIntent(raw, threshold = 0.9))
        assertNotNull(parseIntent(raw, threshold = 0.1))
    }

    @Test
    fun `a non-numeric confidence abstains`() {
        assertNull(parseIntent("""{"intent":"help","confidence":"very"}"""))
    }

    @Test
    fun `missing slots is fine, since some intents have none`() {
        val result = parseIntent("""{"intent":"list_apps","confidence":0.95}""")

        assertNotNull(result)
        assertEquals(emptyMap<String, String>(), result!!.slots)
    }

    @Test
    fun `non-string slot values are coerced rather than refused`() {
        val result = parseIntent("""{"intent":"create_note","slots":{"title":42},"confidence":0.9}""")

        assertEquals("42", result?.slots?.get("title"))
    }

    @Test
    fun `garbage in every form abstains`() {
        listOf(
            null,
            "",
            "   ",
            "I don't know",
            "{",
            "{{{",
            """{"intent":}""",
            """{"not":"json-ish"}""",
        ).forEach { assertNull("'$it' should abstain", parseIntent(it)) }
    }

    @Test
    fun `the whole chain works on realistically messy output`() {
        // Fences, prose, a bare key, a trailing comma and smart quotes, all at once — which is what
        // an un-fine-tuned model at low temperature actually produces.
        val raw = "Sure!\n```json\n{intent: \u201ccreate_note\u201d, slots: {title: \"call the bank\"}, confidence: 0.88,}\n```\nLet me know!"

        val result = parseIntent(raw)
        assertNotNull(result)
        assertEquals(Intent.CREATE_NOTE, result!!.intent)
        assertEquals("call the bank", result.slots["title"])
    }

    // --- the engine contract -----------------------------------------------------------------------

    @Test
    fun `the absent engine declines everything`() = kotlinx.coroutines.runBlocking {
        // This ships until T036, and runs whenever the download has not happened or the LoRA gate
        // came back negative. "No model" is a real state, not a test double.
        assertEquals(InferenceEngine.State.Absent, NoInferenceEngine.state)
        assertNull(NoInferenceEngine.infer("anything"))
        assertNull(parseIntent(NoInferenceEngine.infer("anything")))
    }
}
