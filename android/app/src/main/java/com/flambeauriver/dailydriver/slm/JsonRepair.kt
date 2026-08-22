package com.flambeauriver.dailydriver.slm

/**
 * Layer 4 of the structured-output defence (T034, §4.4): fix the JSON a small model *nearly*
 * produced.
 *
 * **The point is to make layer 5 fire rarely** (T035's validation, which drops to T3 on failure).
 * §4.4 puts it plainly: making that final drop rare is the primary justification for fine-tuning
 * at all, and every repair here is one more case where the fine-tune does not have to be perfect.
 *
 * **Repairs are syntactic only.** Nothing here invents a field, guesses an intent, or corrects a
 * value. A repair pass that supplied a plausible `confidence` when the model omitted one would be
 * fabricating the exact number the abstention threshold depends on, which is worse than failing:
 * it turns "the model did not answer" into "the model answered confidently".
 */

/** Applies every repair, in the order they need to run. */
fun repairJson(raw: String): String =
    raw
        .stripMarkdownFences()
        .stripTrailingCommas()
        .quoteBareKeys()
        .normalizeQuotes()
        .trim()

/**
 * ```json … ``` and bare ``` … ``` wrappers.
 *
 * Instruction-tuned models emit these constantly, because the training data is full of humans
 * asking for code. Runs first: every later repair assumes it is looking at JSON rather than at
 * markdown.
 */
private fun String.stripMarkdownFences(): String =
    replace(Regex("""```[a-zA-Z]*\s*"""), "").replace("```", "")

/**
 * `{"a": 1,}` and `[1, 2,]`.
 *
 * Legal in JavaScript, rejected by every JSON parser, and produced constantly by models that
 * learned from JavaScript. Only commas immediately before a closing brace or bracket go, so a
 * comma inside a string is untouched.
 */
private fun String.stripTrailingCommas(): String =
    replace(Regex(""",(\s*[}\]])"""), "$1")

/**
 * `{intent: "help"}` becomes `{"intent": "help"}`.
 *
 * Requires the key to be preceded by `{` or `,` so that a colon inside a value is not mistaken for
 * a key boundary. Restricted to identifier-shaped keys, because anything else is not a key a model
 * meant to write.
 */
private fun String.quoteBareKeys(): String =
    replace(Regex("""([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)""")) { m ->
        "${m.groupValues[1]}\"${m.groupValues[2]}\"${m.groupValues[3]}"
    }

/**
 * Smart quotes to straight quotes.
 *
 * Models trained on prose emit typographic quotes, which are not JSON string delimiters. Applied
 * last, so earlier passes match the plain form.
 */
private fun String.normalizeQuotes(): String =
    replace('“', '"').replace('”', '"').replace('‘', '\'').replace('’', '\'')
