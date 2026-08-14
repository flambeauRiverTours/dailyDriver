package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
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
}
