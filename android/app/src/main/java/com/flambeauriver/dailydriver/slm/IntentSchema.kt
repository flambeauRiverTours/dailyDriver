package com.flambeauriver.dailydriver.slm

import org.json.JSONException
import org.json.JSONObject

/**
 * Layer 5 of the structured-output defence (T035, §4.3 and §4.4): validate, or drop to T3.
 *
 * **This layer is what makes the SLM shippable**, and it is the last one. Everything upstream is an
 * attempt to make it fire rarely; when it does fire, the launcher answers deterministically instead
 * of acting on a guess (§4.2: "the SLM is allowed to fail; the launcher is not").
 */

/** The fixed label space from §4.3, settled once the command set stopped moving. */
enum class Intent {
    OPEN_APP, LIST_APPS, OPEN_SETTINGS,
    CREATE_NOTE, OPEN_NOTE, CREATE_CHECKLIST, SEARCH_NOTES, CHECK_ITEM,
    ADD_TASK, LIST_TASKS, LIST_COMPLETED_TASKS,
    DEFINE_ALIAS, LIST_ALIASES, LIST_NOTIFICATIONS,
    HELP,

    /**
     * A first-class, *desirable* output (§4.3), not an error.
     *
     * Training the model to abstain is cheaper and safer than training it to guess, and this routes
     * cleanly to T3. It is also what an unrecognised label degrades to, so a model that invents an
     * intent abstains rather than being trusted.
     */
    UNKNOWN;

    /** The wire spelling: `open_app`. Derived, so a label cannot be added without its string. */
    val wire: String get() = name.lowercase()

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /** Unrecognised labels become [UNKNOWN] rather than null — an invented intent is an abstention. */
        fun fromWire(value: String?): Intent = byWire[value?.trim()?.lowercase()] ?: UNKNOWN
    }
}

/** A validated model output. Only ever produced by [parseIntent]. */
data class IntentResult(
    val intent: Intent,
    val slots: Map<String, String>,
    val confidence: Double,
)

/**
 * The confidence below which an otherwise-valid result is refused.
 *
 * Provisional, and deliberately so: §8 measures abstention quality as false-positive rate on
 * out-of-scope input, and this threshold is the dial that trades against it. It cannot be chosen
 * honestly before there are numbers, so it is named here rather than scattered as a literal, and
 * T083's benchmark is what sets it.
 */
const val DEFAULT_CONFIDENCE_THRESHOLD = 0.6

/**
 * Parses raw model output into a validated result, or returns null to mean "drop to T3".
 *
 * Runs the full defence: extract the first balanced object (T033), repair it (T034), then validate
 * here. Null is returned for every failure mode — no object, unparseable, missing fields, wrong
 * types, unknown intent, or confidence below [threshold] — because the caller does the same thing
 * in every case, and distinguishing them would invite handling them differently.
 *
 * **Confidence is required, never defaulted.** A model that omits it has not expressed one, and
 * substituting a value would manufacture the number the abstention decision rests on.
 */
fun parseIntent(raw: String?, threshold: Double = DEFAULT_CONFIDENCE_THRESHOLD): IntentResult? {
    if (raw.isNullOrBlank()) return null

    val candidate = firstJsonObject(repairJson(raw))
        // Repair first, then extract: fences and smart quotes can otherwise hide the object, and a
        // markdown fence contains no braces to confuse the balance count.
        ?: return null

    val json = try {
        JSONObject(candidate)
    } catch (_: JSONException) {
        return null
    }

    val intent = Intent.fromWire(json.optString("intent", null))
    if (intent == Intent.UNKNOWN) return null

    // `has` rather than `optDouble` with a default: a missing confidence must fail, not pass as 0.0
    // and coincidentally trip the threshold for the wrong reason.
    if (!json.has("confidence")) return null
    val confidence = json.optDouble("confidence", Double.NaN)
    if (confidence.isNaN() || confidence < threshold) return null

    return IntentResult(intent = intent, slots = json.slotsOf(), confidence = confidence)
}

/**
 * Reads `slots` as a flat string map.
 *
 * Values are coerced to strings because every slot the app consumes is textual (§4.3: a title, a
 * query, a tag list), and a model that writes `"title": 42` has still expressed something usable.
 * A missing or non-object `slots` is an empty map rather than a failure: `list_apps` legitimately
 * has no slots, and failing on its absence would refuse a correct answer.
 */
private fun JSONObject.slotsOf(): Map<String, String> {
    val slots = optJSONObject("slots") ?: return emptyMap()
    return slots.keys().asSequence().mapNotNull { key ->
        slots.opt(key)?.takeIf { it != JSONObject.NULL }?.let { key to it.toString() }
    }.toMap()
}
