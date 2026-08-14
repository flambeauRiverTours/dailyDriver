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
}
