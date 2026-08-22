package com.flambeauriver.dailydriver.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Launches an installed app by package name. No separable pure logic here — this is glue over
 * two Android APIs (resolve a launch intent, start it), not something to unit test.
 *
 * Not yet wired into the T0/T1 pipeline — that's T140/T141's job. This is the standalone
 * mechanism, same shape as AppIndexRepository at T003.
 */
class AppLauncher(private val context: Context) {

    /**
     * Returns true if a launch intent existed and startActivity was called. Returns false rather
     * than throwing on failure — a launcher crashing because one tapped app was uninstalled or
     * disabled since the index was last refreshed would take the whole home screen down with it.
     */
    fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        // Launcher apps always start the target in its own task, regardless of what kind of
        // context this is called from.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
