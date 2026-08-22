package com.flambeauriver.dailydriver.notifications

/**
 * One line per row in the `:notif` browse view, matching AppListView's single-`Text`-per-row
 * shape. Title is preferred over the body text — it's the more scannable summary a notification
 * shade would lead with — falling back to the body, then a placeholder, so a notification with
 * neither still reads as *something*, not a blank row.
 *
 * **Blank counts as absent, not present.** A plain `title ?: text` only falls back on `null`, so
 * a notification carrying `EXTRA_TITLE = ""` alongside real body text rendered as `Messages: `
 * with nothing after the colon — the one case where the fallback exists and didn't fire. Real
 * notifications do ship empty extras, so this is a live case rather than a hypothetical.
 */
fun NotificationRecord.displayLine(): String {
    val content = title?.takeIf { it.isNotBlank() }
        ?: text?.takeIf { it.isNotBlank() }
        ?: "(no content)"
    return "$appLabel: $content"
}
