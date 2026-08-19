package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FastScrollIndexTest {

    private val apps = listOf(
        AppEntry("com.a", "Calculator"),
        AppEntry("com.b", "Calendar"),
        AppEntry("com.c", "Camera"),
        AppEntry("com.d", "Chrome"),
        AppEntry("com.e", "drive"),
    )

    @Test
    fun `distinct first letters, uppercased, in list order`() {
        assertEquals(listOf('C', 'D'), apps.fastScrollLetters())
    }

    @Test
    fun `empty list has no letters`() {
        assertEquals(emptyList<Char>(), emptyList<AppEntry>().fastScrollLetters())
    }

    @Test
    fun `first-letter matching is case-insensitive, so lowercase labels still index under their letter`() {
        assertEquals(4, apps.indexOfFirstLabelStartingWith('D'))
    }

    @Test
    fun `index of first occurrence of a letter, not any occurrence`() {
        assertEquals(0, apps.indexOfFirstLabelStartingWith('C'))
    }

    @Test
    fun `letter with no matching app returns null`() {
        assertNull(apps.indexOfFirstLabelStartingWith('Z'))
    }

    @Test
    fun `blank labels are skipped, not crashed on — real devices can have apps with no label`() {
        val withBlank = apps + AppEntry("com.mystery", "")

        assertEquals(listOf('C', 'D'), withBlank.fastScrollLetters())
    }

    @Test
    fun `a list of only blank labels has no letters`() {
        assertEquals(emptyList<Char>(), listOf(AppEntry("com.a", "")).fastScrollLetters())
    }

    @Test
    fun `indexOfFirstLabelStartingWith also tolerates blank labels in the list`() {
        val withBlankFirst = listOf(AppEntry("com.mystery", "")) + apps

        assertEquals(1, withBlankFirst.indexOfFirstLabelStartingWith('C'))
    }

    // --- first characters that aren't letters ---------------------------------------------------

    @Test
    fun `digits and symbols get their own rail entries rather than being skipped`() {
        // "1Password" and "+Message" are real app names. Dropping them from the rail would make
        // those apps unreachable by fast-scroll while still being in the list.
        val odd = listOf(
            AppEntry("com.pw", "1Password"),
            AppEntry("com.msg", "+Message"),
            AppEntry("com.c", "Chrome"),
        )

        assertEquals(listOf('1', '+', 'C'), odd.fastScrollLetters())
    }

    @Test
    fun `a digit-led label can be jumped to`() {
        val odd = listOf(AppEntry("com.c", "Chrome"), AppEntry("com.pw", "1Password"))

        assertEquals(1, odd.indexOfFirstLabelStartingWith('1'))
    }

    @Test
    fun `non-ASCII first characters are uppercased by the same rule as ASCII`() {
        val intl = listOf(AppEntry("com.a", "épicerie"), AppEntry("com.b", "Ábaco"))

        assertEquals(listOf('É', 'Á'), intl.fastScrollLetters())
    }

    // --- more rail behaviour -----------------------------------------------------------------------

    @Test
    fun `letters keep list order rather than being sorted independently`() {
        // The rail is positioned to mirror the list, so re-sorting it here would break the
        // correspondence between where a letter sits and where its apps are.
        val reversed = listOf(AppEntry("com.z", "Zebra"), AppEntry("com.a", "Apple"))

        assertEquals(listOf('Z', 'A'), reversed.fastScrollLetters())
    }

    @Test
    fun `a letter appearing many times contributes one rail entry`() {
        assertEquals(listOf('C'), apps.take(4).fastScrollLetters())
    }

    @Test
    fun `a list where every label shares a letter yields exactly one entry`() {
        val allC = listOf(AppEntry("com.a", "Cat"), AppEntry("com.b", "Cow"))

        assertEquals(listOf('C'), allC.fastScrollLetters())
    }

    @Test
    fun `lookup accepts either case for the letter argument`() {
        assertEquals(apps.indexOfFirstLabelStartingWith('C'), apps.indexOfFirstLabelStartingWith('c'))
        assertEquals(apps.indexOfFirstLabelStartingWith('D'), apps.indexOfFirstLabelStartingWith('d'))
    }

    @Test
    fun `lookup on an empty list is null, not an out-of-range index`() {
        assertNull(emptyList<AppEntry>().indexOfFirstLabelStartingWith('C'))
    }

    @Test
    fun `every rail letter resolves to a real index — the rail can't offer a dead jump`() {
        // The property that actually matters at the UI: tapping any letter the rail shows must
        // scroll somewhere, never no-op.
        val mixed = apps + AppEntry("com.pw", "1Password") + AppEntry("com.mystery", "")

        mixed.fastScrollLetters().forEach { letter ->
            assertNotNull("rail offered '$letter' with nowhere to go", mixed.indexOfFirstLabelStartingWith(letter))
        }
    }
}
