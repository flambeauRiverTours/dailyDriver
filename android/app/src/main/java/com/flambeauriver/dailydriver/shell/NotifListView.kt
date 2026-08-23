package com.flambeauriver.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    // ⚠️ Adopting the shared list flipped the order of a *short* list here, which is the common
    // case for notifications: the first one now sits at the bottom rather than the top of the
    // group. Deliberate — every other view in the shell puts item 0 nearest the prompt, and this
    // one's own docs already claimed "the same bottom-anchored shape as AppListView" while doing
    // something else. Unlike the other three it had no hand-reversed list to undo, so only its
    // resting position was ever wrong (T175/T176).
    BottomAnchoredList(
        rows = notifications,
        key = { it.key },
        testTag = ShellTestTags.NOTIF_LIST,
        modifier = modifier,
    ) { notification ->
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
