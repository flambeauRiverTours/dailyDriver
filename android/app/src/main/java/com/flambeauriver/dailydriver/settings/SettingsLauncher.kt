package com.flambeauriver.dailydriver.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Opens the system Settings app. ACTION_SETTINGS is a standard intent every certified Android
 * device must handle, so failure here is much less likely than AppLauncher's (arbitrary
 * third-party package availability is genuinely unpredictable); the same defensive shape is kept
 * anyway for consistency and because a launcher app crashing is worse than most alternatives.
 *
 * Wired into the T0 pipeline at T142 — `MainActivity` calls [open] when `ShellScreen` reports the
 * `:settings` reserved command was submitted.
 */
class SettingsLauncher(private val context: Context) {

    fun open(): Boolean {
        val intent = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
