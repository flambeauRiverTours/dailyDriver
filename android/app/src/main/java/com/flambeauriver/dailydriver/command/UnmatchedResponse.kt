package com.flambeauriver.dailydriver.command

/** Every dead end points back here rather than leaving the user with nowhere to go. */
private const val HELP_HINT = "type :help or ? for commands"

/**
 * What the shell says when nothing matched (T010's T3 fallback — "the safety net that makes the
 * SLM shippable").
 *
 * **T3 is help, not a second app search.** The design doc defines T3 as "treat as app-name
 * search, else show help", but T1 (T008) already ran by the time anything reaches here, and its
 * ladder bottoms out at `SUBSEQUENCE` — matching any label containing the query's characters in
 * order. That is a *looser* app-name search than T3 would perform, so T3's search half can never
 * find something T1 missed. Implemented as help only, rather than as a second search that can't
 * fire.
 *
 * **Both tiers point at [helpText], neither dead-ends.** Since T036 the T2 branch is what is left
 * *after* the SLM has had its turn and declined — no model, a failed inference, output that would
 * not validate, an abstention, or an intent with no command line all arrive here. The wording
 * changed with it: it used to say "not built yet", which stopped being true when the tier was
 * wired in, and a message must not name a cause it cannot distinguish between.
 *
 * The tier stays distinguishable in the wording because "call mom tonight" isn't an app-name
 * typo, and saying "no match" about it would misdescribe what happened.
 *
 * Deliberately a one-line pointer, not [helpText]'s full output: on a daily driver a typo is the
 * common case, and dumping the whole command list every time you fat-finger an app name wears
 * out fast.
 */
fun unmatchedResponse(input: String, tier: EscalationTier): String = when (tier) {
    EscalationTier.T3 -> "no match for '${input.trim()}' — $HELP_HINT"
    EscalationTier.T2 -> "couldn't turn that into a command — $HELP_HINT"
}
