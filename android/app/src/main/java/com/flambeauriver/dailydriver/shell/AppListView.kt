package com.flambeauriver.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.flambeauriver.dailydriver.apps.AppEntry
import com.flambeauriver.dailydriver.apps.fastScrollLetters
import com.flambeauriver.dailydriver.apps.indexOfFirstLabelStartingWith
import kotlinx.coroutines.launch

/** Below this count the fast-scroll rail is more chrome than it's worth. */
private const val FAST_SCROLL_THRESHOLD = 20

/**
 * The `:apps` browse view — replaces the transcript for as long as [ShellViewModel.appsCommand]
 * is non-null. Text-only, no icons, flat list (no section headers): see the T012 UI check-in for
 * why. The fast-scroll rail only appears once the list is long enough to need it.
 *
 * Bottom-anchored and reverse-alphabetical by design, not just to match the transcript's own
 * bottom-anchoring: the input line — and your thumb — is at the bottom of the screen, so the
 * alphabetically-*first* match should be the physically closest one to reach, with later letters
 * further away toward the top. A short/filtered result set clusters at the bottom rather than
 * leaving dead space there and making you reach further up for a two-app result. The fast-scroll
 * rail mirrors the same top-to-bottom order as the list so a tapped letter's position on the rail
 * still roughly corresponds to where that letter's apps sit in the list.
 *
 * **⚠️ That intent was documented from T012 and did not work until T170.** The view reversed the
 * list by hand and leaned on `Arrangement.Bottom` to push it down — but an arrangement only
 * distributes *slack*, so it does nothing once the content is taller than the viewport, which for a
 * real app list is always. The list therefore opened at item 0 of a reversed list: `z, y, x, w`,
 * with the whole alphabet to scroll before reaching A. A short filtered result still sat correctly
 * at the bottom, which is exactly why this survived so long.
 *
 * `reverseLayout` is the fix rather than an initial `scrollToItem`: it makes item 0 the *bottom*
 * item, and a reversed list already starts at item 0. The right resting position falls out instead
 * of being corrected into place a frame later.
 */
@Composable
fun AppListView(
    apps: List<AppEntry>,
    /**
     * Display name per package (T164), computed over the **whole** index by the caller.
     *
     * Passed in rather than derived here, because whether "Messages" is ambiguous is a property of
     * what is installed, not of what the current query narrowed to. Deriving it from [apps] would
     * make a row's name change as you typed, dropping the suffix the moment the filter left one
     * match — which is when you are about to commit to it.
     */
    names: Map<String, String>,
    onAppClick: (AppEntry) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Row(modifier = modifier) {
        if (apps.size > FAST_SCROLL_THRESHOLD) {
            // The rail reads Z→A top to bottom so it mirrors what the reversed layout draws, but
            // every index it produces is into the A→Z list the LazyColumn is actually given.
            // Reversing one and not the other sends each letter to the opposite end.
            val letters = apps.fastScrollLetters().reversed()
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                letters.forEach { letter ->
                    Text(
                        text = letter.toString(),
                        style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier
                            .clickable {
                                val index = apps.indexOfFirstLabelStartingWith(letter) ?: return@clickable
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    )
                }
            }
        }

        // The rail scrolls this programmatically, so it needs the shared list's state (T176).
        BottomAnchoredList(
            rows = apps,
            key = { it.packageName },
            testTag = ShellTestTags.APP_LIST,
            state = listState,
            modifier = Modifier.fillMaxWidth(),
        ) { app ->
            Text(
                // Suffixed with a publisher only where the label is ambiguous (T164). Computed
                // over the *whole* index rather than the filtered list: whether "Messages" is
                // ambiguous is a property of what is installed, not of what the current query
                // happens to have narrowed to.
                text = names[app.packageName] ?: app.label,
                style = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppClick(app) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
