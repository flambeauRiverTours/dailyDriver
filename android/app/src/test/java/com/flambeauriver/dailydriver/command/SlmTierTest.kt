package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.slm.InferenceEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T2 as a stage of the cascade (T036).
 *
 * **The engine here is canned, not a model.** What is under test is the wiring: that T2 runs only
 * where it is allowed to, that its answer is dispatched as if typed, and that every way it can
 * fail lands on T3 rather than anywhere else. Whether a real model answers well is T083's
 * question and is not decidable in a unit test.
 */
class SlmTierTest {

    /** Returns [reply] verbatim, and records whether it was ever asked. */
    private class CannedEngine(
        private val reply: String?,
        override val state: InferenceEngine.State = InferenceEngine.State.Loaded,
    ) : InferenceEngine {
        var calls = 0
            private set

        override suspend fun load() = Unit
        override suspend fun infer(prompt: String): String? {
            calls++
            return reply
        }

        override fun close() = Unit
    }

    private fun env(engine: InferenceEngine, apps: List<AppEntry> = emptyList()) =
        FakeCommandEnvironment(apps = apps).apply { inferenceEngine = engine }

    @Test
    fun `a resolved intent is dispatched as if it had been typed`() {
        val engine = CannedEngine("""{"intent": "web_search", "slots": {"query": "ben nevis"}, "confidence": 0.9}""")

        val outcome = runBlocking { dispatchCommand("how tall is ben nevis", env(engine)) }

        // The transcript is `:google`'s own, which is the point: the slot travelled intact from
        // the model, through the synthesised line, into the command that ran. Asserting only
        // that *something* happened would pass even if the query had been dropped.
        assertEquals("searched: ben nevis", outcome.transcript)
        assertEquals(1, engine.calls)
    }

    @Test
    fun `an app intent re-enters the cascade and launches through T1`() {
        val engine = CannedEngine("""{"intent": "open_app", "slots": {"app": "Gmail"}, "confidence": 0.95}""")
        val environment = env(engine, apps = listOf(AppEntry(label = "Gmail", packageName = "com.google.gm")))

        val outcome = runBlocking { dispatchCommand("get my electronic mail up", environment) }

        assertEquals(CommandOutcome.Silent, outcome)
        assertEquals(listOf("com.google.gm"), environment.launchedApps)
    }

    @Test
    fun `single word input never reaches the model`() {
        // §4.2: prose is multi-word, and T1 already had its shot at a one-word app name. Asking
        // the model here would put a second of latency on a plain typo.
        val engine = CannedEngine("""{"intent": "help", "slots": {}, "confidence": 0.99}""")

        runBlocking { dispatchCommand("wibble", env(engine)) }

        assertEquals(0, engine.calls)
    }

    @Test
    fun `an exact command never reaches the model`() {
        // The root hard constraint: T2 is never on the fast path. This is structural rather than
        // checked — dispatch returns before `runSlm` exists — but it is the constraint most worth
        // having a test say out loud.
        val engine = CannedEngine("""{"intent": "help", "slots": {}, "confidence": 0.99}""")

        runBlocking { dispatchCommand(":tasks", env(engine)) }

        assertEquals(0, engine.calls)
    }

    @Test
    fun `an absent model falls through to T3 unchanged`() {
        // Production today. The tier being wired in must not change what a device with no model
        // does, which is what makes T036 safe to ship before T087.
        val engine = CannedEngine(null, state = InferenceEngine.State.Absent)

        val outcome = runBlocking { dispatchCommand("call mom tonight", env(engine)) }

        assertEquals(0, engine.calls)
        assertTrue(outcome.transcript.orEmpty().contains("help"))
    }

    @Test
    fun `unparseable output falls through to T3`() {
        val engine = CannedEngine("I think you want to call your mother.")

        val outcome = runBlocking { dispatchCommand("call mom tonight", env(engine)) }

        assertEquals(1, engine.calls)
        assertTrue(outcome.transcript.orEmpty().contains("help"))
    }

    @Test
    fun `an abstention falls through to T3 rather than guessing`() {
        // §4.3 makes `unknown` a desirable output. It must reach the same place a failure does.
        val engine = CannedEngine("""{"intent": "unknown", "slots": {}, "confidence": 0.99}""")

        val outcome = runBlocking { dispatchCommand("mmph grumble", env(engine)) }

        assertTrue(outcome.transcript.orEmpty().contains("help"))
    }

    @Test
    fun `low confidence falls through to T3`() {
        val engine = CannedEngine("""{"intent": "list_tasks", "slots": {}, "confidence": 0.1}""")

        val outcome = runBlocking { dispatchCommand("what have I got on", env(engine)) }

        assertTrue(outcome.transcript.orEmpty().contains("help"))
    }

    @Test
    fun `the model is asked at most once per submission`() {
        // The recursion guard. `open_app` synthesises a bare app name, and when no such app
        // exists that line misses too — without `allowSlm = false` it would ask the model about
        // the model's own output, and keep going.
        val engine = CannedEngine("""{"intent": "open_app", "slots": {"app": "nothing installed"}, "confidence": 0.9}""")

        val outcome = runBlocking { dispatchCommand("open the thing", env(engine)) }

        assertEquals(1, engine.calls)
        assertFalse(outcome.transcript.isNullOrBlank())
    }
}
