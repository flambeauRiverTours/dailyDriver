package com.flambeauriver.dailydriver.command

import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.command.CommandResolution.Tier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T014 — classifying what the pipeline did with a line, for the training log.
 *
 * **Rewritten at T184.** These used to test `classifyResolution`, which took the three tiers'
 * already-computed answers and applied the cascade order to them. There are no longer three
 * answers to order: [resolutionOf] reads the top of the same ranking dispatch acts on (ADR-0010),
 * so what this suite has to pin is that the log agrees with what actually ran.
 */
class CommandResolutionTest {

    private val apps = listOf(
        AppEntry("com.spotify.music", "Spotify"),
        AppEntry("com.android.chrome", "Chrome"),
    )

    private fun resolve(input: String, aliases: Map<String, String> = emptyMap()) =
        runBlocking { resolutionOf(input, FakeCommandEnvironment(apps = apps, aliases = aliases)) }

    @Test
    fun `a reserved command is a T0 hit on its token`() {
        assertEquals(CommandResolution(Tier.T0, ":apps"), resolve(":apps"))
    }

    @Test
    fun `an alias is a T0 hit on what it expands to`() {
        assertEquals(CommandResolution(Tier.T0, "spotify"), resolve("sp", mapOf("sp" to "spotify")))
    }

    @Test
    fun `a fuzzy app match is a T1 hit on the package`() {
        assertEquals(CommandResolution(Tier.T1, "com.spotify.music"), resolve("spot"))
    }

    @Test
    fun `nothing matching is T3 with an empty target`() {
        val resolution = resolve("zzzznotanything")

        assertEquals(Tier.T3, resolution.tier)
        assertEquals(CommandResolution.UNRESOLVED, resolution.target)
    }

    @Test
    fun `the log records what ran, including a typo'd command the old classifier called a miss`() {
        // ⚠️ The defect T184 fixed, and the reason this file changed shape. Once reserved tokens
        // became fuzzy-matchable, dispatch ran `:apps` for `:aps` while the classifier — which
        // matched tokens exactly — filed the line as a T3 *miss*. That is training data asserting
        // a line which worked was unresolvable, the same damage the alias-definition exclusion
        // exists to prevent.
        assertEquals(CommandResolution(Tier.T1, ":apps"), resolve(":aps"))
    }

    @Test
    fun `the token is recorded without its argument`() {
        // ":note standup" and ":note groceries" are one command with different slots. Collapsing
        // them is what makes the log a frequency table rather than a list of every note title.
        assertEquals(":note", resolve(":note standup").target)
        assertEquals(":note", resolve(":note groceries").target)
        assertEquals(":note", resolve(":note").target)
    }

    @Test
    fun `every registered command logs the command that ran, bare and with an argument`() {
        // Property test, in the registry style this module already uses: a newly registered
        // command is automatically checked for being loggable, rather than silently collecting no
        // training data. Both shapes, because the argument is what the token has to survive.
        //
        // Asserted as "names the same command" rather than "is the same string", because
        // `:tasksDone` is a casing variant of `:tasksdone` and logs the canonical spelling — see
        // the dedupe note in `buildCandidates`. What the log owes is a target that identifies the
        // command, not the exact characters typed.
        reservedCommandTokens.forEach { token ->
            val bare = resolve(token)

            assertEquals("'$token' bare", Tier.T0, bare.tier)
            assertEquals("'$token' bare", parseReservedCommand(token), parseReservedCommand(bare.target))
            assertEquals("'$token' with an argument", bare.target, resolve("$token some argument").target)
        }
    }

    @Test
    fun `a casing variant of a command logs one target, not two`() {
        // ⚠️ Found by the property above at T184. The registry holds `:tasksDone` beside
        // `:tasksdone` so both spellings enter browse mode; case-insensitive matching made them
        // two equally-good candidates for one command, which would have split its usage count
        // between two rows and shown a phantom runner-up in the preview.
        assertEquals(resolve(":tasksdone").target, resolve(":tasksDone").target)
    }

    @Test
    fun `an exact match is T0 whatever kind of thing matched`() {
        // ⚠️ A deliberate shift in what the tier means (T184). The tiers survive the redesign as
        // *pipeline stages* — exact, then fuzzy — rather than as separate lookups, so a fully
        // typed app name is T0 where it used to be T1. Existing rows keep their old meaning;
        // §8's hand-labelling step is where that is reconciled, which is the reason this log
        // records what happened rather than a label.
        assertEquals(Tier.T0, resolve("chrome").tier)
        assertEquals(Tier.T1, resolve("chr").tier)
    }
}
