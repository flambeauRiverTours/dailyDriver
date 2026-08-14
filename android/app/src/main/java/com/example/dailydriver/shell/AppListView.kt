package com.example.dailydriver.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.dailydriver.apps.AppEntry
import com.example.dailydriver.apps.fastScrollLetters
import com.example.dailydriver.apps.indexOfFirstLabelStartingWith
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
 */
@Composable
fun AppListView(
    apps: List<AppEntry>,
    onAppClick: (AppEntry) -> Unit,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // apps arrives A→Z (AppIndex's canonical order); reversed here purely for this view's
    // bottom-up presentation — the underlying index, filtering, and everything else stays A→Z.
    val displayApps = apps.reversed()

    Row(modifier = modifier) {
        if (displayApps.size > FAST_SCROLL_THRESHOLD) {
            val letters = displayApps.fastScrollLetters()
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
                                val index = displayApps.indexOfFirstLabelStartingWith(letter) ?: return@clickable
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ShellTestTags.APP_LIST),
            verticalArrangement = Arrangement.Bottom,
        ) {
            items(displayApps, key = { it.packageName }) { app ->
                Text(
                    text = app.label,
                    style = textStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAppClick(app) }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}
