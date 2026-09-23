package com.flambeauriver.dailydriver

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * The Android Studio scaffold test, kept because it earned its place.
 *
 * It caught T031 adding `applicationIdSuffix = ".debug"` to the debug build type, which nothing
 * else noticed: the unit suite, lint and the compiler were all green, because the applicationId is
 * only real on a device. That is the same shape as every other bug this suite has found.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        // **Prefix, not equality, and the suffix is the point.** The debug variant installs as
        // `…dailydriver.debug` so it can coexist with the signed release build on the author's
        // phone, which is the daily driver and holds notes and the T014 log that sync nowhere
        // (T031). Asserting equality here would either fail forever or push someone into removing
        // the suffix, which is the one thing that must not happen.
        assertTrue(
            "unexpected package: ${appContext.packageName}",
            appContext.packageName.startsWith("com.flambeauriver.dailydriver"),
        )
    }
}