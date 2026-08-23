package com.flambeauriver.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** T167 — the name the list shows has to be a name you can type. */
class AppFilteringDisplayNameTest {

    private fun app(pkg: String, label: String) = AppEntry(packageName = pkg, label = label)

    private val apps = listOf(
        app("com.google.android.apps.messaging", "Messages"),
        app("com.samsung.android.messaging", "Messages"),
        app("com.spotify.music", "Spotify"),
    )

    private val names = displayNames(apps)

    @Test
    fun `the disambiguated name matches its own app`() {
        // The reported bug: ":apps Messages (Google)" matched nothing, even though that is exactly
        // what the list had just rendered.
        val found = apps.filteredBy("Messages (Google)", names)

        assertEquals(listOf("com.google.android.apps.messaging"), found.map { it.packageName })
    }

    @Test
    fun `the other publisher is reachable the same way`() {
        val found = apps.filteredBy("Messages (Samsung)", names)

        assertEquals(listOf("com.samsung.android.messaging"), found.map { it.packageName })
    }

    @Test
    fun `a full disambiguated name narrows to one, so it can auto-launch`() {
        // The point of being able to type it: narrowing to a single result is what lets Enter or
        // the typing pause commit to it.
        val found = apps.filteredBy("Messages (Google)", names)

        assertEquals("com.google.android.apps.messaging", found.soleAutoLaunchTarget("Messages (Google)")?.packageName)
    }

    @Test
    fun `the bare label still matches both`() {
        assertEquals(2, apps.filteredBy("Messages", names).size)
    }

    @Test
    fun `a prefix still matches both, suffix or not`() {
        // The suffix must not cost anything for someone typing the short thing. "mess" is a
        // substring of "Messages" and of "Messages (Google)" alike.
        assertEquals(2, apps.filteredBy("mess", names).size)
    }

    @Test
    fun `matching is case insensitive on the display name too`() {
        assertEquals(1, apps.filteredBy("messages (google)", names).size)
    }

    @Test
    fun `an unambiguous app is unaffected`() {
        assertEquals(listOf("com.spotify.music"), apps.filteredBy("Spotify", names).map { it.packageName })
    }

    @Test
    fun `a publisher name alone finds the app it disambiguates`() {
        // Falls out of substring matching rather than being designed, but it is a reasonable thing
        // to type and worth knowing it works.
        assertEquals(1, apps.filteredBy("(Samsung)", names).size)
    }

    @Test
    fun `without a names map the behaviour is exactly as before`() {
        // The default argument exists so nothing that had no display map changes.
        assertTrue(apps.filteredBy("Messages (Google)").isEmpty())
        assertEquals(2, apps.filteredBy("Messages").size)
    }

    @Test
    fun `order is preserved`() {
        assertEquals(
            apps.map { it.packageName },
            apps.filteredBy("", names).map { it.packageName },
        )
    }
}
