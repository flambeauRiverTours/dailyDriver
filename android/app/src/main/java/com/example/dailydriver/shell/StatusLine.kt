package com.example.dailydriver.shell

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/** Half a blink cycle — 500ms on, 500ms off, so the colon pulses once a second like a real clock. */
private const val BLINK_INTERVAL_MS = 500L

/**
 * The status row at the top of the shell (T146): time at the left edge, date at the right, with
 * the time's colon blinking on a one-second cycle.
 *
 * **State is local, deliberately breaking android/CLAUDE.md's state-hoisting default.** Hoisting
 * the tick to [ShellViewModel] would recompose all of [ShellScreen] — transcript, app list, input
 * line — twice a second for a clock. Keeping it here scopes recomposition to this Row, which is
 * the whole point on a surface that is always on screen.
 *
 * **The tick stops when the Activity isn't resumed.** This is the home screen: it gets paused on
 * every app switch but rarely destroyed, so a bare `LaunchedEffect` would keep waking twice a
 * second for a clock nobody can see, for as long as the process lives. [repeatOnLifecycle]
 * cancels the loop below RESUMED and restarts it on return — which also means coming back to the
 * launcher paints a current time immediately rather than a stale one.
 *
 * [hasUnreadNotifications] is a lambda, not a value, so T148's dot refreshes on the tick this
 * composable already runs. Passing a plain `Boolean` would freeze it until something *else*
 * recomposed [ShellScreen] — a keystroke, say — since the repository behind it is a non-reactive
 * getter. Reading it is cheap: the service maintains it as a field (see
 * `DailyDriverNotificationListenerService.hasUnread`), so this is a field read per blink, not a
 * binder call.
 */
@Composable
fun StatusLine(
    hasUnreadNotifications: () -> Boolean,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Read fresh on every tick rather than captured once, so flipping the system 12/24h setting
    // shows up within a second of coming back to the launcher.
    fun render(colonVisible: Boolean) = statusLineText(
        instant = Instant.now(),
        zone = ZoneId.systemDefault(),
        is24Hour = DateFormat.is24HourFormat(context),
        colonVisible = colonVisible,
    )

    var text by remember { mutableStateOf(render(colonVisible = true)) }
    var unread by remember { mutableStateOf(hasUnreadNotifications()) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var colonVisible = true
            while (true) {
                text = render(colonVisible)
                unread = hasUnreadNotifications()
                colonVisible = !colonVisible
                delay(BLINK_INTERVAL_MS)
            }
        }
    }

    val dim = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Grouped so SpaceBetween still pushes exactly two things apart — dot and time travel
        // together at the left edge, date stays pinned right.
        Row {
            // Undimmed, unlike the time and date: the dot is the one thing here worth noticing.
            Text(text = unreadIndicator(unread), style = textStyle)
            Text(text = text.time, style = dim)
        }
        Text(text = text.date, style = dim)
    }
}
