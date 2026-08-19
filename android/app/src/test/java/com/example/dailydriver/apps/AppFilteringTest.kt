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

    // --- more filtering ---------------------------------------------------------------------

    @Test
    fun `a whitespace-only query behaves like a blank one and returns everything`() {
        assertEquals(apps, apps.filteredBy("   "))
        assertEquals(apps, apps.filteredBy("\t"))
    }

    @Test
    fun `the query is matched literally, not as a regular expression`() {
        // "." would match every label if this were regex-based, silently making the filter
        // useless for anyone typing a package-style name.
        assertEquals(emptyList<AppEntry>(), apps.filteredBy("."))
        assertEquals(emptyList<AppEntry>(), apps.filteredBy(".*"))
    }

    @Test
    fun `matches at the very end of a label count`() {
        assertEquals(listOf(apps[1]), apps.filteredBy("dar"))
    }

    @Test
    fun `a query equal to the whole label matches exactly that app`() {
        assertEquals(listOf(apps[2]), apps.filteredBy("Camera"))
    }

    @Test
    fun `filtering an empty list gives an empty list`() {
        assertEquals(emptyList<AppEntry>(), emptyList<AppEntry>().filteredBy("chrome"))
    }

    @Test
    fun `filtering does not mutate the receiver`() {
        val before = apps.toList()

        apps.filteredBy("ca")

        assertEquals(before, apps)
    }

    @Test
    fun `non-ASCII queries match non-ASCII labels case-insensitively`() {
        val intl = listOf(AppEntry("com.a", "Café Münchén"))

        assertEquals(intl, intl.filteredBy("münchén"))
        assertEquals(intl, intl.filteredBy("CAFÉ"))
    }

    // --- more auto-launch -------------------------------------------------------------------

    @Test
    fun `a single-app list with a real query auto-launches even without filtering first`() {
        // soleAutoLaunchTarget doesn't know whether filtering happened — it only requires
        // exactly one candidate and a non-blank query.
        val one = listOf(apps[0])

        assertEquals(apps[0], one.soleAutoLaunchTarget("anything"))
    }

    @Test
    fun `an empty list never auto-launches`() {
        assertNull(emptyList<AppEntry>().soleAutoLaunchTarget("chrome"))
    }

    @Test
    fun `filtering down to one is what makes auto-launch fire`() {
        // The pairing the UI actually relies on: keep typing until exactly one row is left.
        assertNull(apps.filteredBy("ca").soleAutoLaunchTarget("ca"))
        assertEquals(apps[2], apps.filteredBy("cam").soleAutoLaunchTarget("cam"))
    }
}
