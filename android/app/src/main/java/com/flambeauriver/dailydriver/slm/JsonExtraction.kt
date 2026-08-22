package com.flambeauriver.dailydriver.slm

/**
 * Layer 3 of the structured-output defence (T033, §4.4).
 *
 * **MediaPipe has no grammar-constrained decoding.** Unlike llama.cpp's GBNF there is no way to
 * force the model to emit JSON, so schema adherence is probabilistic and defended in layers: a
 * tight prompt, low temperature, *this*, a repair pass (T034), then validation (T035). Each layer
 * exists because the one before it was measured as insufficient, so none of them may be skipped to
 * simplify.
 *
 * This layer's only job is to find the JSON object in whatever the model said. Small models
 * routinely wrap output in prose ("Sure! Here's the JSON:"), in markdown fences, or emit a second
 * object after the first.
 */

/**
 * Returns the first balanced `{...}` span in [raw], or null if there isn't one.
 *
 * **Balanced, not regex.** A regex for `\{.*\}` is greedy across two objects and a non-greedy one
 * stops at the first `}`, which is wrong the moment the payload has a nested object — and `slots`
 * is always a nested object (§4.3). Counting depth is the only thing that gets both right.
 *
 * **Braces inside strings do not count.** A note titled `"{weird}"` is legitimate content, and a
 * naive depth count would close the object early and truncate valid JSON into invalid JSON. String
 * state and backslash escapes are tracked for exactly that reason.
 *
 * Returns the span unparsed. Whether it is *valid* JSON is the next layer's problem: this one
 * cannot both find the candidate and judge it without collapsing two layers into one.
 */
fun firstJsonObject(raw: String): String? {
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false

    raw.forEachIndexed { index, c ->
        when {
            escaped -> escaped = false
            c == '\\' && inString -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '{' -> {
                if (depth == 0) start = index
                depth++
            }
            c == '}' -> {
                if (depth > 0) {
                    depth--
                    if (depth == 0 && start >= 0) return raw.substring(start, index + 1)
                }
                // A closing brace with no opener is stray prose, not a malformed object. Ignored
                // rather than treated as an error, since the object may still be further along.
            }
        }
    }

    // Unbalanced: the model was cut off mid-object, which `maxTokens` makes a real outcome rather
    // than a hypothetical. Null routes to T3, which is the correct answer for a truncated reply.
    return null
}
