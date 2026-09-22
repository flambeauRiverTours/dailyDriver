package com.flambeauriver.dailydriver.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Queries PackageManager for launchable apps and keeps [AppIndex] current. Requires the
 * `<queries>` declaration in AndroidManifest.xml (ACTION_MAIN + CATEGORY_LAUNCHER) to see other
 * apps' packages at all on API 30+ — there is no automatic visibility exemption for
 * CATEGORY_HOME/launcher apps, despite an earlier version of this comment claiming otherwise.
 *
 * **The index is re-read on more than package changes (2026-09-22).** It used to be built once and
 * updated only on install/uninstall, which meant a single bad read was permanent: on the
 * daily-driven device the launcher spent a day listing nothing but system apps, and force-stopping
 * it was the only cure. Package changes are still the signal that matters, but they are no longer
 * the only one — see [refreshIfStale] and the unlock receiver below.
 */
class AppIndexRepository(private val context: Context) {

    private val index = AppIndex()
    private val gate = RefreshGate()
    private val packageManager: PackageManager get() = context.packageManager

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // A package really did change, so the interval does not apply — the list is known
            // stale rather than merely old.
            gate.invalidate()
            refresh()
        }
    }

    /**
     * Separate from [packageChangeReceiver] because the filters cannot be merged.
     *
     * `ACTION_USER_UNLOCKED` carries no data URI, and the package filter sets
     * `addDataScheme("package")`, which applies to the whole filter rather than to one action.
     * Adding the action there would compile, register, and simply never fire — the silent-failure
     * shape this codebase has been bitten by repeatedly.
     */
    private val userUnlockedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            gate.invalidate()
            refresh()
        }
    }

    val apps: List<AppEntry> get() = index.apps

    fun refresh() {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = packageManager.queryIntentActivities(launcherIntent, 0)
        index.replaceAll(resolved.map { it.toAppEntry(packageManager) })
    }

    /**
     * Re-reads the index if it has not been read recently, off the main thread.
     *
     * Called when the launcher regains window focus, which is the general "you are back at the
     * home screen" signal (T165). [RefreshGate] is what stops that from meaning a PackageManager
     * query on every HOME press; the dispatcher is what stops the one that does happen from
     * landing on the frame the user is waiting for, since this loads a label per installed app and
     * there are several hundred of them.
     */
    suspend fun refreshIfStale() {
        if (!gate.shouldRefresh(SystemClock.elapsedRealtime())) return
        withContext(Dispatchers.Default) { refresh() }
    }

    /** Registers the package-change and unlock listeners, and performs the initial index build. */
    fun register() {
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        // Explicit flag required: targetSdk 33 throws SecurityException without it. NOT_EXPORTED
        // because nothing outside this app needs to be able to trigger this receiver directly —
        // both of these are protected system broadcasts, which the system still delivers.
        context.registerReceiver(packageChangeReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED)

        // Being the HOME app means being started before the user has unlocked, and a PackageManager
        // query made while the device is still locked does not see credential-encrypted apps.
        // Unlocking fires no package broadcast, so without this the launcher would hold that
        // first-read answer until something else happened to change a package.
        val unlockFilter = IntentFilter(Intent.ACTION_USER_UNLOCKED)
        context.registerReceiver(userUnlockedReceiver, unlockFilter, Context.RECEIVER_NOT_EXPORTED)

        gate.shouldRefresh(SystemClock.elapsedRealtime())
        refresh()
    }

    fun unregister() {
        context.unregisterReceiver(packageChangeReceiver)
        context.unregisterReceiver(userUnlockedReceiver)
    }
}

private fun ResolveInfo.toAppEntry(packageManager: PackageManager) = AppEntry(
    packageName = activityInfo.packageName,
    label = loadLabel(packageManager).toString(),
)
