package com.flambeauriver.dailydriver.editor

/**
 * A destructive action that can be put back (T138).
 *
 * [label] says what happened, in the past tense, because by the time it is on screen the action
 * already has. [restore] is a closure over whatever state the action replaced, which is why undo
 * needs no history stack: there is only ever one offer, and it carries everything it needs.
 *
 * Only the most recent action is offered. A stack would be more powerful and would also mean
 * reasoning about undoing a delete after three unrelated edits, which is a much larger promise
 * than "that wasn't what I meant".
 */
data class UndoableAction(val label: String, val restore: () -> Unit)
