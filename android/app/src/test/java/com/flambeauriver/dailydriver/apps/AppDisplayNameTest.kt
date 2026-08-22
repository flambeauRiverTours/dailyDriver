package com.flambeauriver.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Test

/** T164 — publisher suffixes for apps that share a label. */
class AppDisplayNameTest {

    private fun app(pkg: String, label: String) = AppEntry(packageName = pkg, label = label)

    @Test
    fun `a unique label is left alone`() {
        val names = displayNames(listOf(app("com.spotify.music", "Spotify")))

        assertEquals("Spotify", names["com.spotify.music"])
    }

    @Test
    fun `duplicates are suffixed with their publisher`() {
        val names = displayNames(
            listOf(
                app("com.google.android.apps.messaging", "Messages"),
                app("com.samsung.android.messaging", "Messages"),
            ),
        )

        assertEquals("Messages (Google)", names["com.google.android.apps.messaging"])
        assertEquals("Messages (Samsung)", names["com.samsung.android.messaging"])
    }

    @Test
    fun `only the duplicated label is suffixed, not the whole list`() {
        // The point of suffixing at all: it must not turn into noise on names that are fine.
        val names = displayNames(
            listOf(
                app("com.google.android.apps.messaging", "Messages"),
                app("com.samsung.android.messaging", "Messages"),
                app("com.spotify.music", "Spotify"),
            ),
        )

        assertEquals("Spotify", names["com.spotify.music"])
    }

    @Test
    fun `duplicate detection ignores case and surrounding space`() {
        val names = displayNames(
            listOf(
                app("com.google.android.apps.messaging", "Messages"),
                app("com.samsung.android.messaging", " messages "),
            ),
        )

        assertEquals("Messages (Google)", names["com.google.android.apps.messaging"])
        // The label is displayed as the app reports it; only the ambiguity test is normalized.
        assertEquals(" messages  (Samsung)", names["com.samsung.android.messaging"])
    }

    @Test
    fun `three apps sharing a label all get suffixed`() {
        val names = displayNames(
            listOf(
                app("com.google.android.gm", "Email"),
                app("com.samsung.android.email.provider", "Email"),
                app("org.mozilla.thunderbird", "Email"),
            ),
        )

        assertEquals(setOf("Email (Google)", "Email (Samsung)", "Email (Mozilla)"), names.values.toSet())
    }

    // --- publisher derivation ----------------------------------------------------------------------

    @Test
    fun `the vendor segment is the second one`() {
        assertEquals("Google", publisherOf("com.google.android.apps.messaging"))
        assertEquals("Samsung", publisherOf("com.samsung.android.messaging"))
        assertEquals("Mozilla", publisherOf("org.mozilla.firefox"))
    }

    @Test
    fun `a two-segment package falls back to the whole package`() {
        // com.whatsapp has no org segment between the TLD and the app. Better a long unambiguous
        // row than a confident wrong one.
        assertEquals("com.whatsapp", publisherOf("com.whatsapp"))
    }

    @Test
    fun `a single-segment or empty package falls back too`() {
        assertEquals("android", publisherOf("android"))
        assertEquals("", publisherOf(""))
    }

    @Test
    fun `stray dots do not shift the vendor segment`() {
        assertEquals("Google", publisherOf("com..google.android.apps.messaging"))
    }

    @Test
    fun `matching still sees the real label, not the display name`() {
        // Load-bearing: display names are returned as a separate map rather than folded into
        // AppEntry, because a fuzzy match against "Messages (Google)" scores worse for someone
        // typing "mess" than a match against "Messages" — disambiguation would then actively harm
        // the thing it exists to clarify.
        val apps = listOf(
            app("com.google.android.apps.messaging", "Messages"),
            app("com.samsung.android.messaging", "Messages"),
        )
        displayNames(apps)

        assertEquals(listOf("Messages", "Messages"), apps.map { it.label })
    }
}
