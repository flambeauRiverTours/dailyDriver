package com.flambeauriver.dailydriver.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a search URL to the default browser (T172).
 *
 * **`ACTION_VIEW` on a `https://` URL, not `ACTION_WEB_SEARCH`.** The latter sounds more correct —
 * it respects whatever search engine the device is set to — and is not what `:google` means. It
 * also frequently resolves to a search *app* (the Google app's own surface) rather than a browser,
 * which is a different destination than the one that was asked for.
 *
 * Same defensive shape as [SettingsLauncher][com.flambeauriver.dailydriver.settings.SettingsLauncher]:
 * a launcher crashing is worse than almost any alternative, so a missing browser returns false and
 * the transcript says so.
 */
class WebSearchLauncher(private val context: Context) {

    fun open(url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
