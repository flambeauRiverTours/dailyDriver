package com.example.dailydriver.command

/**
 * Where input goes once T0 (reserved commands, aliases) and T1 (fuzzy app match, T008) have both
 * missed — the design doc §4.2 escalation rule. Neither [T2] nor [T3] has a real handler yet
 * (T009/T010); this classification exists so callers can at least say *which* gap they hit,
 * rather than one undifferentiated placeholder.
 */
enum class EscalationTier { T2, T3 }

/**
 * Multi-word input is almost always prose, not an app name — T1 already had its shot at matching
 * the whole string against app labels, so a miss here means the words don't add up to an app
 * name. Skip straight to [EscalationTier.T2] rather than wasting [EscalationTier.T3]'s
 * app-name-search fallback on input that was never going to satisfy it. Single-word input, having
 * exhausted T1 the same way, falls to [EscalationTier.T3] instead.
 */
fun escalationTier(input: String): EscalationTier =
    if (input.trim().any { it.isWhitespace() }) EscalationTier.T2 else EscalationTier.T3
