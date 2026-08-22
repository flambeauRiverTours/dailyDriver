package com.flambeauriver.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flambeauriver.dailydriver.notifications.NotificationRecord
import com.flambeauriver.dailydriver.notifications.displayLine

/**
 * The `:notif` browse view (T147) — replaces the transcript while notification access is granted
 * and the reserved command is active. Same bottom-anchored, monospace, tap-to-act shape as
 * [AppListView], with two deliberate omissions from the 2026-08-19 design check-in: no fast-scroll
 * rail (notifications aren't alphabetically browsable the way apps are) and no live filter as you
 * type (counts are usually small, and there's no single obvious field to filter by).
 */
@Composable
fun NotifListView(
    notifications: List<NotificationRecord>,
    onNotificationClick: (NotificationRecord) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag(ShellTestTags.NOTIF_LIST),
        verticalArrangement = Arrangement.Bottom,
    ) {
        items(notifications, key = { it.key }) { notification ->
            Text(
                text = notification.displayLine(),
                style = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNotificationClick(notification) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
