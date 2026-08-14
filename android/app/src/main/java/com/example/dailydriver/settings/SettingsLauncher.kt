package com.example.dailydriver.settings

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
 * Not yet wired into the T0/T1 pipeline — that's T140/T142's job.
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
