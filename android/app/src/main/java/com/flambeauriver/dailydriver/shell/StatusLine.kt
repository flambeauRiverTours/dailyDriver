package com.flambeauriver.dailydriver.shell

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.LifecycleEventObserver
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
    var battery by remember { mutableStateOf(batteryText(0, charging = false)) }

    // Battery arrives by broadcast rather than being polled on the blink loop: it changes on the
    // order of minutes, and ACTION_BATTERY_CHANGED is *sticky*, so registering returns the current
    // value immediately rather than waiting for the next change. Registering with a null receiver
    // would read it once; a real receiver keeps it current while the launcher is on screen.
    //
    // Scoped to RESUMED like the clock, for the same reason: this Activity is the home screen and
    // is paused constantly but rarely destroyed, so an unscoped registration would keep a receiver
    // alive for the life of the process.
    DisposableEffect(lifecycleOwner) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level < 0 || scale <= 0) return

                // EXTRA_SCALE is not guaranteed to be 100 — it is the denominator, and reading
                // EXTRA_LEVEL as a percentage directly is a common way to be quietly wrong on
                // devices that report out of a different maximum.
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                battery = batteryText(
                    percent = (level * 100) / scale,
                    charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL,
                )
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> context.registerReceiver(receiver, filter)
                Lifecycle.Event.ON_PAUSE -> runCatching { context.unregisterReceiver(receiver) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

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
        // Time and date travel together at the left edge (T171). They are one piece of information
        // and get read as one — "when is it" — so splitting them across the full width of the
        // screen made you look twice for something you glance at. Battery is the thing that
        // genuinely stands alone, so it gets the far side to itself.
        Row {
            // Undimmed, unlike the time and date: the dot is the one thing here worth noticing.
            Text(text = unreadIndicator(unread), style = textStyle)
            Text(text = text.time, style = dim)
            // A separator rather than space alone. Once the date sat next to the time the two ran
            // together as one string, and the blinking colon makes it worse: the time alternates
            // between "4:28" and "4 28", so at every other tick the only thing dividing the fields
            // was a gap the same width as the one inside the time itself.
            Text(text = "·", style = dim, modifier = Modifier.padding(horizontal = 8.dp))
            Text(text = text.date, style = dim)
        }
        // Separated by layout, not by a space inside the string: the battery cell's own leading
        // column is the charging marker, so when charging there is nothing left to separate it
        // from whatever precedes it and it reads as "Aug+100%". Found by looking at it.
        Text(text = battery, style = dim, modifier = Modifier.padding(start = 8.dp))
    }
}
