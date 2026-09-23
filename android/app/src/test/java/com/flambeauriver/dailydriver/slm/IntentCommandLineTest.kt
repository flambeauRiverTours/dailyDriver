package com.flambeauriver.dailydriver.slm

import com.flambeauriver.dailydriver.command.reservedCommandTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T036's mapping from a validated intent back to a line the T0 dispatcher already handles.
 *
 * The important tests here are the two property tests: every label is accounted for, and every
 * token produced is one that actually exists. Between them a new §4.3 label cannot reach
 * production without someone deciding what it does, and a typo in a token cannot survive.
 */
class IntentCommandLineTest {

    private fun result(intent: Intent, slots: Map<String, String> = emptyMap()) =
        IntentResult(intent = intent, slots = slots, confidence = 0.9)

    /** The labels that deliberately have no command line, with the reason in [commandLineFor]. */
    private val deliberatelyUnmapped =
        setOf(Intent.CHECK_ITEM, Intent.CREATE_CHECKLIST, Intent.UNKNOWN)

    @Test
    fun `every intent is either mapped or deliberately unmapped`() {
        // The `when` is exhaustive so this cannot fail to compile, but it *can* fail to be
        // thought about: a new label could be waved through with `-> null` to make the build go
        // green. Listing the intended nulls here means doing that trips a test instead.
        Intent.entries.forEach { intent ->
            val line = commandLineFor(result(intent, slotsFor(intent)))
            if (intent in deliberatelyUnmapped) {
                assertNull("$intent should have no command line", line)
            } else {
                assertTrue("$intent produced no command line", !line.isNullOrBlank())
            }
        }
    }

    @Test
    fun `every produced token is a real reserved command`() {
        // Catches `:tasksDone` or `:notif ` before it reaches a device, where the symptom would be
        // a silent fall-through to the unmatched response rather than an error.
        Intent.entries.forEach { intent ->
            val line = commandLineFor(result(intent, slotsFor(intent))) ?: return@forEach
            if (!line.startsWith(":")) return@forEach
            val token = line.substringBefore(' ')
            assertTrue("$intent produced unknown token '$token'", token in reservedCommandTokens)
        }
    }

    @Test
    fun `open_app returns the bare app name so it re-enters the cascade`() {
        // Not a package name and not a token: this has to be matched and ranked like anything
        // else typed at the prompt, or it bypasses the usage ordering T183 exists for.
        assertEquals("gmail", commandLineFor(result(Intent.OPEN_APP, mapOf("app" to "gmail"))))
    }

    @Test
    fun `add_task puts the due date back as an at-token`() {
        // So T153's shortcut parser resolves it, rather than this file learning to write dates.
        val line = commandLineFor(
            result(Intent.ADD_TASK, mapOf("text" to "call the dentist", "due" to "tomorrow")),
        )

        assertEquals(":task call the dentist @tomorrow", line)
    }

    @Test
    fun `add_task without a due date is still a task`() {
        // T155: an undated `:task` is due today, so dropping the token is correct rather than a
        // reason to refuse the command.
        assertEquals(":task buy milk", commandLineFor(result(Intent.ADD_TASK, mapOf("text" to "buy milk"))))
    }

    @Test
    fun `add_task with no text is refused rather than opening an empty task`() {
        assertNull(commandLineFor(result(Intent.ADD_TASK, mapOf("due" to "friday"))))
    }

    @Test
    fun `define_alias uses the definition syntax, not a token`() {
        // `alias x = y` is parsed before dispatch reaches the candidate list, so `:alias` here
        // would list aliases instead of defining one.
        assertEquals(
            "alias gm = gmail",
            commandLineFor(result(Intent.DEFINE_ALIAS, mapOf("name" to "gm", "command" to "gmail"))),
        )
    }

    @Test
    fun `define_alias missing half its slots is refused`() {
        assertNull(commandLineFor(result(Intent.DEFINE_ALIAS, mapOf("name" to "gm"))))
    }

    @Test
    fun `a listing command needs no slots and gains no trailing space`() {
        assertEquals(":notes", commandLineFor(result(Intent.SEARCH_NOTES)))
        assertEquals(":google", commandLineFor(result(Intent.WEB_SEARCH)))
        assertEquals(":tasks", commandLineFor(result(Intent.LIST_TASKS)))
    }

    @Test
    fun `blank slots are treated as absent`() {
        // A model that emits `"app": ""` has not named an app, and launching the empty string
        // would fall through to the unmatched response looking like a pipeline bug.
        assertNull(commandLineFor(result(Intent.OPEN_APP, mapOf("app" to "   "))))
        assertEquals(":notes", commandLineFor(result(Intent.SEARCH_NOTES, mapOf("query" to ""))))
    }

    /** Plausible slots per intent, so the property tests exercise the filled-in path. */
    private fun slotsFor(intent: Intent): Map<String, String> = when (intent) {
        Intent.OPEN_APP -> mapOf("app" to "gmail")
        Intent.CREATE_NOTE, Intent.OPEN_NOTE -> mapOf("title" to "standup")
        Intent.SEARCH_NOTES -> mapOf("query" to "boiler")
        Intent.ADD_TASK -> mapOf("text" to "call the dentist", "due" to "tomorrow")
        Intent.DEFINE_ALIAS -> mapOf("name" to "gm", "command" to "gmail")
        Intent.WEB_SEARCH -> mapOf("query" to "how tall is ben nevis")
        else -> emptyMap()
    }
}
