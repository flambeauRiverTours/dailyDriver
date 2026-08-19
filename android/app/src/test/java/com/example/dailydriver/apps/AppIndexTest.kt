package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIndexTest {

    @Test
    fun `apps is empty before any refresh`() {
        assertEquals(emptyList<AppEntry>(), AppIndex().apps)
    }

    @Test
    fun `replaceAll sorts entries by label, case-insensitive`() {
        val index = AppIndex()

        index.replaceAll(
            listOf(
                AppEntry("com.z", "zebra"),
                AppEntry("com.a", "Apple"),
                AppEntry("com.m", "mango"),
            )
        )

        assertEquals(listOf("Apple", "mango", "zebra"), index.apps.map { it.label })
    }

    @Test
    fun `replaceAll wholesale replaces prior entries, not merges`() {
        val index = AppIndex()
        index.replaceAll(listOf(AppEntry("com.a", "A")))

        index.replaceAll(listOf(AppEntry("com.b", "B")))

        assertEquals(listOf("B"), index.apps.map { it.label })
    }

    @Test
    fun `replaceAll deduplicates by packageName — PackageManager can list one package's multiple launcher activities separately`() {
        // Real-world case that crashed on-device: com.google.android.googlequicksearchbox
        // exposes more than one CATEGORY_LAUNCHER activity, so the raw query returned it twice.
        // AppLauncher can only ever launch one (the package's default) activity regardless of
        // which duplicate is tapped, so showing both rows would be actively misleading, not just
        // a duplicate Compose key.
        val index = AppIndex()

        index.replaceAll(
            listOf(
                AppEntry("com.google.android.googlequicksearchbox", "Google"),
                AppEntry("com.google.android.googlequicksearchbox", "Google Assistant"),
            )
        )

        assertEquals(1, index.apps.size)
    }

    @Test
    fun `deduplication keeps the alphabetically-first label for a duplicated package, deterministically`() {
        val index = AppIndex()

        index.replaceAll(
            listOf(
                AppEntry("com.example", "Zeta Entry"),
                AppEntry("com.example", "Alpha Entry"),
            )
        )

        assertEquals("Alpha Entry", index.apps.single().label)
    }

    // --- more index behaviour -------------------------------------------------------------------

    @Test
    fun `replacing with nothing empties the index`() {
        // The uninstall-everything case, and also what a failed PackageManager query would look
        // like — it must clear rather than leave the previous list stranded.
        val index = AppIndex()
        index.replaceAll(listOf(AppEntry("com.a", "A")))

        index.replaceAll(emptyList())

        assertEquals(emptyList<AppEntry>(), index.apps)
    }

    @Test
    fun `labels differing only in case sort next to each other, not in two case-separated blocks`() {
        // Codepoint ordering would put every capitalised label ahead of every lowercase one.
        val index = AppIndex()

        index.replaceAll(
            listOf(
                AppEntry("com.b", "banana"),
                AppEntry("com.a", "Apple"),
                AppEntry("com.c", "apricot"),
            )
        )

        assertEquals(listOf("Apple", "apricot", "banana"), index.apps.map { it.label })
    }

    @Test
    fun `distinct packages sharing a label are both kept`() {
        // Deduplication is by package, not by label — two vendors really can ship "Music".
        val index = AppIndex()

        index.replaceAll(
            listOf(
                AppEntry("com.vendor.a", "Music"),
                AppEntry("com.vendor.b", "Music"),
            )
        )

        assertEquals(2, index.apps.size)
    }

    @Test
    fun `three copies of one package collapse to a single entry`() {
        val index = AppIndex()

        index.replaceAll(
            listOf(
                AppEntry("com.example", "C"),
                AppEntry("com.example", "A"),
                AppEntry("com.example", "B"),
            )
        )

        assertEquals(listOf("A"), index.apps.map { it.label })
    }

    @Test
    fun `blank labels are kept and sort first rather than being dropped`() {
        // AppIndex isn't the layer that decides a blank label is unusable — FastScrollIndex and
        // AppMatching each handle it. Silently dropping it here would hide a real installed app
        // from the list entirely.
        val index = AppIndex()

        index.replaceAll(listOf(AppEntry("com.b", "B"), AppEntry("com.blank", "")))

        assertEquals(listOf("", "B"), index.apps.map { it.label })
    }

    @Test
    fun `digits sort ahead of letters`() {
        val index = AppIndex()

        index.replaceAll(listOf(AppEntry("com.a", "Alpha"), AppEntry("com.1", "1Password")))

        assertEquals(listOf("1Password", "Alpha"), index.apps.map { it.label })
    }

    @Test
    fun `non-ASCII labels are sorted without crashing`() {
        val index = AppIndex()

        index.replaceAll(listOf(AppEntry("com.b", "日本語"), AppEntry("com.a", "Café")))

        assertEquals(2, index.apps.size)
    }

    @Test
    fun `a single entry survives intact`() {
        val index = AppIndex()

        index.replaceAll(listOf(AppEntry("com.a", "Alpha")))

        assertEquals(listOf(AppEntry("com.a", "Alpha")), index.apps)
    }

    @Test
    fun `the exposed list reflects the most recent replaceAll, not an earlier snapshot`() {
        val index = AppIndex()
        index.replaceAll(listOf(AppEntry("com.a", "A")))
        val first = index.apps

        index.replaceAll(listOf(AppEntry("com.b", "B")))

        assertEquals(listOf("A"), first.map { it.label })
        assertEquals(listOf("B"), index.apps.map { it.label })
    }
}
