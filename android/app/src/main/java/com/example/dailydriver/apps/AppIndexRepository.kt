package com.example.dailydriver.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo

/**
 * Queries PackageManager for launchable apps and keeps [AppIndex] current. No `<queries>`
 * manifest entry is needed to see other apps' packages — apps holding a CATEGORY_HOME activity
 * (registered in T002) are automatically exempt from Android 11+'s package-visibility filtering.
 */
class AppIndexRepository(private val context: Context) {

    private val index = AppIndex()
    private val packageManager: PackageManager get() = context.packageManager

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    val apps: List<AppEntry> get() = index.apps

    fun refresh() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(launcherIntent, 0)
        index.replaceAll(resolved.map { it.toAppEntry(packageManager) })
    }

    /** Registers the package-change listener and performs the initial index build. */
    fun register() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Explicit flag required: targetSdk 33 throws SecurityException without it. NOT_EXPORTED
        // because nothing outside this app needs to be able to trigger this receiver directly.
        context.registerReceiver(packageChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun unregister() {
        context.unregisterReceiver(packageChangeReceiver)
    }
}

private fun ResolveInfo.toAppEntry(packageManager: PackageManager) = AppEntry(
    packageName = activityInfo.packageName,
    label = loadLabel(packageManager).toString(),
)
