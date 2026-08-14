package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilteringTest {

    private val apps = listOf(
        AppEntry("com.android.calculator2", "Calculator"),
        AppEntry("com.android.calendar", "Calendar"),
        AppEntry("com.android.camera", "Camera"),
        AppEntry("com.android.chrome", "Chrome"),
    )

    @Test
    fun `blank query returns everything, unchanged order`() {
        assertEquals(apps, apps.filteredBy(""))
    }

    @Test
    fun `matches are case-insensitive`() {
        assertEquals(listOf(apps[3]), apps.filteredBy("CHROME"))
    }

    @Test
    fun `matches anywhere in the label, not just the prefix`() {
        assertEquals(listOf(apps[0]), apps.filteredBy("cul"))
    }

    @Test
    fun `no matches returns an empty list`() {
        assertEquals(emptyList<AppEntry>(), apps.filteredBy("zzz"))
    }

    @Test
    fun `multiple matches preserve the original sorted order`() {
        assertEquals(listOf(apps[0], apps[1], apps[2]), apps.filteredBy("ca"))
    }

    @Test
    fun `query is trimmed before matching, so a trailing space mid-type doesn't break the match`() {
        assertEquals(listOf(apps[3]), apps.filteredBy("chrome "))
    }
}
