package com.flambeauriver.dailydriver.shell

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The one bottom-anchored list every browse view in the shell is built from (T176).
 *
 * **Item 0 is drawn at the bottom, and the list rests there.** The input line — and your thumb —
 * sits at the bottom of the screen, so whichever row matters most should be the one physically
 * closest: the alphabetically first app, the newest note, the most urgent task. Later rows recede
 * upward, and a short result set clusters near the prompt instead of leaving dead space there.
 *
 * **⚠️ This exists because the same mistake was made four times.** Each view used to hand-roll it
 * as `list.reversed()` plus `verticalArrangement = Arrangement.Bottom` — which works only while
 * the content fits on screen, because an arrangement distributes *slack* and an overflowing list
 * has none. Every one of them then opened at the wrong end: `:apps` on Z (T170), `:notes` on the
 * oldest notes and `:tasks` on the least urgent ones (T175). All three survived for months because
 * a short list still looked right, which is exactly the kind of bug a shared component prevents
 * and four copies guarantee.
 *
 * `reverseLayout` rather than an initial `scrollToItem`: it makes the correct resting position the
 * natural one instead of correcting a wrong one a frame later.
 *
 * **Callers pass their rows in natural order — do not reverse before calling.** Reversing the data
 * *and* the layout cancels out, which is how three of the four got here.
 *
 * [state] is exposed for `AppListView`'s fast-scroll rail, which scrolls the list programmatically.
 * Indices are into the list as passed, not into anything reversed.
 */
@Composable
fun <T> BottomAnchoredList(
    rows: List<T>,
    key: (T) -> Any,
    testTag: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    row: @Composable (T) -> Unit,
) {
    LazyColumn(
        state = state,
        // fillMaxHeight is load-bearing rather than decoration, and stays so under reverseLayout:
        // inside a Row, fillMaxWidth alone leaves the column sized to its content, so a short list
        // would sit at the top of a box that hugs it rather than at the bottom of the screen.
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .testTag(testTag),
        reverseLayout = true,
    ) {
        items(rows, key = key) { item -> row(item) }
    }
}
