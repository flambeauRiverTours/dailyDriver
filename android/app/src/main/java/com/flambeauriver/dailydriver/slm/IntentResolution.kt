package com.flambeauriver.dailydriver.slm

/**
 * The T2 tier, end to end (T036): prompt, infer, validate.
 *
 * **Three layers of §4.4's defence live here in order** — [PromptTemplate.forCommand] is layer 1,
 * the engine's low temperature and Gemma turn markers are layer 2, and [parseIntent] runs layers
 * 3 to 5 (extract the first balanced span, repair, validate against the label space and the
 * confidence threshold). Keeping them in one small function means the caller cannot accidentally
 * skip one, which `android/CLAUDE.md` warns against in as many words.
 *
 * **Returns null for every failure, which is the point.** No model, still loading, inference
 * declined, unparseable output, a label outside §4.3, an explicit `unknown`, or confidence below
 * the threshold all mean the same thing to the caller: this tier has no answer, go to T3. §4.2 is
 * explicit that the SLM may fail and the launcher may not.
 */
suspend fun resolveIntent(input: String, engine: InferenceEngine): IntentResult? {
    if (input.isBlank()) return null
    if (engine.state != InferenceEngine.State.Loaded) return null
    return parseIntent(engine.infer(PromptTemplate.forCommand(input)))
}
