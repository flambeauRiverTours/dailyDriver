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
}
