package com.flambeauriver.dailydriver.shell

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Starting an Activity from the launcher, in the one way this app ever does it (T177).
 *
 * Replaces `AppLauncher`, `SettingsLauncher` and `WebSearchLauncher`, which were three classes
 * doing one thing: build an Intent, add [Intent.FLAG_ACTIVITY_NEW_TASK], start it, return false
 * instead of throwing.
 *
 * **Returning false rather than throwing is the whole point of the shape.** This is the home
 * screen. An app uninstalled since the index was last refreshed, or a device with no browser,
 * must produce a transcript line, not take the home screen down with it — there is nothing
 * underneath to fall back to.
 *
 * **`FLAG_ACTIVITY_NEW_TASK` on every one of them**, because a launcher always starts its target
 * in the target's own task regardless of what kind of context it was called from.
 *
 * No pure half to separate out, unlike most of this codebase: every line here is glue over an
 * Android API, and there is nothing left once the framework calls are removed.
 */
class IntentLauncher(private val context: Context) {

    /** An installed app, by package name. False if nothing is registered to launch it. */
    fun launchApp(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return start(intent)
    }

    /**
     * System settings.
     *
     * `ACTION_SETTINGS` is standard on every certified Android device, so failure is far less
     * likely than [launchApp]'s — third-party package availability is genuinely unpredictable,
     * this is not. Kept to the same shape anyway rather than special-cased.
     */
    fun openSettings(): Boolean = start(Intent(Settings.ACTION_SETTINGS))

    /**
     * A URL in the default browser.
     *
     * `ACTION_VIEW` on an `https://` URL, **not** `ACTION_WEB_SEARCH` (T172). The latter sounds
     * more correct, since it respects whatever search engine the device is set to, and frequently
     * resolves to a search *app* rather than a browser — a different destination than the one
     * `:google` asks for.
     */
    fun openUrl(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, url.toUri()))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
