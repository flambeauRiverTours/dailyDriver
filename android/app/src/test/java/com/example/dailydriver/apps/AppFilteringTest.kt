package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `exactly one match with a real query is an auto-launch target`() {
        assertEquals(apps[3], apps.filteredBy("chrome").soleAutoLaunchTarget("chrome"))
    }

    @Test
    fun `multiple matches are not an auto-launch target`() {
        assertNull(apps.filteredBy("ca").soleAutoLaunchTarget("ca"))
    }

    @Test
    fun `no matches is not an auto-launch target`() {
        assertNull(apps.filteredBy("zzz").soleAutoLaunchTarget("zzz"))
    }

    @Test
    fun `a blank query never auto-launches, even when only one app exists`() {
        // Typing bare ":apps" on a device with one app installed must not immediately launch it —
        // the user asked to browse, not to open something.
        val onlyOneApp = listOf(apps[0])

        assertNull(onlyOneApp.soleAutoLaunchTarget(""))
        assertNull(onlyOneApp.soleAutoLaunchTarget("   "))
    }

    @Test
    fun `a whitespace-padded real query still auto-launches`() {
        assertEquals(apps[3], apps.filteredBy("chrome ").soleAutoLaunchTarget("chrome "))
    }
}
