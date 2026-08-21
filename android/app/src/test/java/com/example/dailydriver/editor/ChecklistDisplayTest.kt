package com.example.dailydriver.editor

import com.example.dailydriver.notes.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChecklistDisplayTest {

    // milk and bread unchecked, eggs and jam checked — deliberately interleaved, because a list
    // that happens to be already partitioned would pass even a completely broken mapping.
    private val items = listOf(
        ChecklistItem("milk"),
        ChecklistItem("eggs", checked = true),
        ChecklistItem("bread"),
        ChecklistItem("jam", checked = true),
    )

    // --- what's shown ------------------------------------------------------------------------------

    @Test
    fun `showing everything is the list itself`() {
        assertEquals(items, items.visible(hideChecked = false))
    }

    @Test
    fun `hiding checked leaves only the unchecked, in order`() {
        assertEquals(listOf("milk", "bread"), items.visible(hideChecked = true).map { it.text })
    }

    @Test
    fun `hiding checked on an all-checked list shows nothing`() {
        val allDone = listOf(ChecklistItem("a", true), ChecklistItem("b", true))

        assertEquals(emptyList<ChecklistItem>(), allDone.visible(hideChecked = true))
    }

    @Test
    fun `hiding checked never reorders what remains`() {
        assertEquals(
            items.filterNot { it.checked },
            items.visible(hideChecked = true),
        )
    }

    // --- translating a tap back to a real row ------------------------------------------------------

    @Test
    fun `with nothing hidden the index passes straight through`() {
        items.indices.forEach { index ->
            assertEquals(index, items.actualIndex(index, hideChecked = false))
        }
    }

    @Test
    fun `with checked hidden the displayed index skips over them`() {
        // This is the whole point of the file: displayed row 1 is "bread", which is stored row 2.
        // Without the translation, ticking the second visible row would tick "eggs" — an item
        // that isn't even on screen.
        assertEquals(0, items.actualIndex(0, hideChecked = true))
        assertEquals(2, items.actualIndex(1, hideChecked = true))
    }

    @Test
    fun `an index past the end of the visible list resolves to nothing`() {
        // Two visible rows, so 2 is off the end even though the stored list has four items.
        assertNull(items.actualIndex(2, hideChecked = true))
        assertNull(items.actualIndex(3, hideChecked = true))
    }

    @Test
    fun `an out-of-range index resolves to nothing rather than throwing`() {
        // A tap can arrive a frame after the list changed under it; doing nothing is the safe read.
        assertNull(items.actualIndex(9, hideChecked = false))
        assertNull(items.actualIndex(-1, hideChecked = false))
        assertNull(items.actualIndex(-1, hideChecked = true))
        assertNull(emptyList<ChecklistItem>().actualIndex(0, hideChecked = false))
        assertNull(emptyList<ChecklistItem>().actualIndex(0, hideChecked = true))
    }

    @Test
    fun `every visible row resolves back to the item it is showing`() {
        // The property the two functions have to satisfy together, asserted rather than trusted.
        listOf(true, false).forEach { hideChecked ->
            val shown = items.visible(hideChecked)
            shown.forEachIndexed { displayed, item ->
                val actual = items.actualIndex(displayed, hideChecked)
                assertEquals("hideChecked=$hideChecked row $displayed", item, items[actual!!])
            }
        }
    }

    // --- focus after conversion (T024 tweak) -------------------------------------------------------

    @Test
    fun `an empty conversion focuses the row the caller is about to add`() {
        assertEquals(0, rowToFocusAfterConversion(0))
    }

    @Test
    fun `a conversion with content focuses the last existing row`() {
        // Not a new blank row appended to real content — the prose already became rows, and you
        // carry on from the end of the last one.
        assertEquals(0, rowToFocusAfterConversion(1))
        assertEquals(2, rowToFocusAfterConversion(3))
    }

    @Test
    fun `a negative count cannot produce a negative row`() {
        assertEquals(0, rowToFocusAfterConversion(-1))
    }
}
