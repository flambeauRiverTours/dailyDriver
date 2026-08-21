package com.example.dailydriver.editor

import com.example.dailydriver.notes.ChecklistItem

/**
 * Mapping between the checklist as *stored* and the checklist as *displayed* (T023/T024).
 *
 * Deliberately **not** in `notes/ChecklistParsing.kt`: that file is a cross-language contract
 * pinned by `fixtures/checklist-parsing.json` and reimplemented in C#, and hide-checked is a
 * display state this app happens to have. Mixing them would imply the desktop client owes an
 * implementation of this, which it doesn't.
 *
 * Extracted from `NoteEditorViewModel` rather than left as private helpers there because this is
 * where the interesting bug lives. With checked items hidden, the third row you can *see* is not
 * the third row that *exists* — so a tap that isn't translated deletes or ticks the wrong item,
 * and does it silently, on the rows you can't see. That's worth testing directly, and testing the
 * ViewModel would mean a coroutines test dispatcher for logic that needs none.
 */

/** The items actually rendered: everything, or only the unticked ones. */
fun List<ChecklistItem>.visible(hideChecked: Boolean): List<ChecklistItem> =
    if (hideChecked) filterNot { it.checked } else this

/**
 * Resolves an index into [visible] back to an index into the full list.
 *
 * Null for an index that doesn't correspond to a visible row — callers treat that as "no such
 * row" and do nothing, which is the safe reading of a tap that arrives a frame after the list
 * changed under it.
 */
fun List<ChecklistItem>.actualIndex(displayedIndex: Int, hideChecked: Boolean): Int? {
    if (!hideChecked) return displayedIndex.takeIf { it in indices }
    return indices.filter { !this[it].checked }.getOrNull(displayedIndex)
}

/**
 * The row the cursor belongs in once a checklist surface appears (T024 tweak).
 *
 * Converting a note to a checklist should leave you typing, not aiming. With no items at all the
 * caller adds one first, so the answer is row 0; with items already parsed out of the prose, the
 * end of the last one is where you would carry on writing.
 *
 * Returned as an index rather than a boolean so the caller does no arithmetic of its own — the
 * off-by-one here is the whole content of the function.
 */
fun rowToFocusAfterConversion(visibleCount: Int): Int =
    if (visibleCount <= 0) 0 else visibleCount - 1
