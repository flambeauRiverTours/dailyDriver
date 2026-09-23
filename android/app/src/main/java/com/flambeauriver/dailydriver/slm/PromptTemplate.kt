package com.flambeauriver.dailydriver.slm

/**
 * Layer 1 of the structured-output defence (T032, §4.4): a tight prompt with the schema inline.
 *
 * **The label list is derived from [Intent.entries], never written out.** `StructuredOutputTest`
 * carries a hand-maintained copy of these wire names that was caught being wrong on 2026-09-21,
 * and this would have been the third place to keep in step. Deriving it means a new label reaches
 * the prompt for free, and the test that checks this is a property test over the enum rather than
 * a list someone has to remember to extend.
 *
 * **No chat markers here, deliberately.** `<start_of_turn>` and friends are a property of the
 * model, not of the task, so they are configured on the session in [MediaPipeEngine] through
 * MediaPipe's `PromptTemplates`. Keeping them out means swapping to a different model, or to
 * LiteRT-LM at T091, does not rewrite the prompt.
 *
 * **Everything here is paid on every command.** Prefill runs over the whole prompt before a single
 * token comes back, so length is latency in the most direct way possible. [MAX_CHARS] is a guard
 * against the few-shot block growing one well-meaning example at a time.
 */
object PromptTemplate {

    /** One teaching example. Held as data so the tests can run it through [parseIntent]. */
    data class Example(val command: String, val intent: Intent, val json: String)

    /** Separates one exchange from the next, and the only thing a command could try to forge. */
    const val COMMAND_MARKER = "Command: "

    /**
     * Generous enough for the examples below, tight enough to notice drift. Raise it with a
     * latency measurement in hand, not because a test went red.
     */
    const val MAX_CHARS = 2_000

    /**
     * Five examples, chosen from what a month of real T014 logs actually contains rather than from
     * what is easy to write.
     *
     * `open_app` and `web_search` dominate real usage, and `web_search` carries nearly all of the
     * free-form language T2 exists for. `add_task` is here because a date shortcut in a slot is
     * the most structurally awkward thing the model has to produce. And **`unknown` is here on
     * purpose**: a block of confident hits teaches that guessing always pays, which is precisely
     * what §4.3's abstention and T035's threshold exist to prevent.
     */
    val examples: List<Example> = listOf(
        Example(
            command = "open gmail",
            intent = Intent.OPEN_APP,
            json = """{"intent": "open_app", "slots": {"app": "gmail"}, "confidence": 0.98}""",
        ),
        Example(
            command = "how tall is ben nevis",
            intent = Intent.WEB_SEARCH,
            json = """{"intent": "web_search", "slots": {"query": "how tall is ben nevis"}, "confidence": 0.93}""",
        ),
        Example(
            command = "remind me to call the dentist tomorrow",
            intent = Intent.ADD_TASK,
            json = """{"intent": "add_task", "slots": {"text": "call the dentist", "due": "tomorrow"}, "confidence": 0.9}""",
        ),
        Example(
            command = "what did I write about the boiler",
            intent = Intent.SEARCH_NOTES,
            json = """{"intent": "search_notes", "slots": {"query": "boiler"}, "confidence": 0.88}""",
        ),
        Example(
            command = "asdkjh qwe",
            intent = Intent.UNKNOWN,
            json = """{"intent": "unknown", "slots": {}, "confidence": 0.95}""",
        ),
    )

    /**
     * The prompt for one typed command.
     *
     * **The command is sanitised to a single line.** It arrives straight from the prompt line and
     * can contain anything; a newline plus a forged `Command:` would otherwise let typed input
     * append its own exchange and steer the answer. Collapsing whitespace removes that without
     * needing to guess at what an attack looks like, and costs nothing, because a command is a
     * line of text by construction.
     */
    fun forCommand(command: String): String {
        val labels = Intent.entries.joinToString(", ") { it.wire }
        return buildString {
            append("You label a phone command with one intent and its slots.\n")
            append("Allowed intents: ").append(labels).append(".\n")
            append("Reply with one JSON object and nothing else, with keys ")
            append("\"intent\", \"slots\" and \"confidence\" (0 to 1).\n")
            append("Use \"unknown\" when the command does not fit any other intent.\n\n")
            examples.forEach { example ->
                append(COMMAND_MARKER).append(example.command).append('\n')
                append(example.json).append("\n\n")
            }
            append(COMMAND_MARKER).append(singleLine(command)).append('\n')
        }
    }

    private fun singleLine(command: String): String =
        command.replace(Regex("\\s+"), " ").trim()
}
