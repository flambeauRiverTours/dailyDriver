package com.example.dailydriver.notifications

/**
 * One line per row in the `:notif` browse view, matching AppListView's single-`Text`-per-row
 * shape. Title is preferred over the body text — it's the more scannable summary a notification
 * shade would lead with — falling back to the body, then a placeholder, so a notification with
 * neither still reads as *something*, not a blank row.
 */
fun NotificationRecord.displayLine(): String = "$appLabel: ${title ?: text ?: "(no content)"}"
