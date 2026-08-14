package com.example.dailydriver.shell

import androidx.compose.foundation.clickable
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

    Row(modifier = modifier) {
        if (apps.size > FAST_SCROLL_THRESHOLD) {
            val letters = apps.fastScrollLetters()
            Column(modifier = Modifier.fillMaxHeight().padding(end = 8.dp)) {
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ShellTestTags.APP_LIST),
        ) {
            items(apps, key = { it.packageName }) { app ->
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
