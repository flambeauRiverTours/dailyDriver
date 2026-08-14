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
}
