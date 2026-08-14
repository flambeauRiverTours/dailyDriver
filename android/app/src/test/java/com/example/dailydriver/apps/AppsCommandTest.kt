package com.example.dailydriver.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppsCommandTest {

    @Test
    fun `bare colon-apps has an empty query`() {
        assertEquals(AppsCommand(""), parseAppsCommand(":apps"))
    }

    @Test
    fun `colon-apps followed by a space and text captures the query`() {
        assertEquals(AppsCommand("chrome"), parseAppsCommand(":apps chrome"))
    }

    @Test
    fun `leading whitespace in the query is trimmed`() {
        assertEquals(AppsCommand("chrome"), parseAppsCommand(":apps   chrome"))
    }

    @Test
    fun `trailing whitespace in the query is preserved mid-type`() {
        // The user is still typing — don't eagerly trim a trailing space, or a query like
        // "chrome browser" would have its word-boundary space stripped while typing it.
        assertEquals(AppsCommand("chrome "), parseAppsCommand(":apps chrome "))
    }

    @Test
    fun `command token is case-sensitive, matching the rest of T0`() {
        assertNull(parseAppsCommand(":APPS"))
        assertNull(parseAppsCommand(":Apps chrome"))
    }

    @Test
    fun `not a prefix match — colon-apps must be the whole token, not part of a longer one`() {
        assertNull(parseAppsCommand(":appsfoo"))
    }

    @Test
    fun `unrelated input is not an apps command`() {
        assertNull(parseAppsCommand("apps"))
        assertNull(parseAppsCommand(""))
        assertNull(parseAppsCommand(":settings"))
        assertNull(parseAppsCommand("chrome"))
    }
}
