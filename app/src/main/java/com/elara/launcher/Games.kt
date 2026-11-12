package com.elara.launcher

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The Games folder: one entry in the drawer, which can sit on the home screen or dock like an app and
 * opens [GamesSheet]. It is a stand-in app ([entry]), so every place that lists or places apps takes it.
 * Its members are the apps Android calls games, less those the user took out, plus those they added.
 */
object Games {
    const val Package = "app.elara.games"
    const val Class = "games"
    val entry = AppEntry("Games", Package, Class, 0)
    val Key: String = entry.key

    @Suppress("DEPRECATION")
    fun isGame(info: ApplicationInfo) =
        info.category == ApplicationInfo.CATEGORY_GAME || info.flags and ApplicationInfo.FLAG_IS_GAME != 0

    fun members(detected: Set<String>, added: Set<String>, removed: Set<String>): Set<String> = (detected - removed) + added
}

/** The games, tap to play, long-press to take one out; "Add game" lists the other apps to put in. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun GamesSheet(
    games: List<AppEntry>,
    others: List<AppEntry>,
    repository: AppRepository,
    onAdd: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var adding by remember { mutableStateOf(false) }
    ElaraSheet(stringResource(if (adding) R.string.games_add_title else R.string.games_title), Icons.Widgets, onDismiss = onDismiss, action = {
        FilledTonalIconButton(onClick = { adding = !adding }, Modifier.size(48.dp)) {
            Icon(if (adding) Icons.Done else Icons.Add, contentDescription = stringResource(if (adding) R.string.games_done else R.string.games_add))
        }
    }) {
        val sizePx = with(LocalDensity.current) { 56.dp.roundToPx() }
        if (adding) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(others, key = { it.key }) { app ->
                    val icon by produceState<ImageBitmap?>(repository.cachedIcon(app, sizePx), app.key, repository.iconEpoch) { value = repository.icon(app, sizePx) }
                    ListItem(
                        headlineContent = { Text(app.label) },
                        leadingContent = { Box(Modifier.size(40.dp)) { icon?.let { Image(it, null, Modifier.size(40.dp)) } } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp)).clickable { onAdd(app) },
                    )
                }
            }
        } else if (games.isEmpty()) {
            Text(
                stringResource(R.string.games_none),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
            )
        } else {
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().heightIn(max = 440.dp), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(games, key = { it.key }) { app ->
                    val icon by produceState<ImageBitmap?>(repository.cachedIcon(app, sizePx), app.key, repository.iconEpoch) { value = repository.icon(app, sizePx) }
                    var menu by remember { mutableStateOf(false) }
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).combinedClickable(
                            onClick = { onDismiss(); repository.launch(app, null) },
                            onLongClick = { menu = true },
                        ).padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(56.dp)) { icon?.let { Image(it, null, Modifier.size(56.dp)) } }
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                        AppMenu(menu, app, repository, listOf(MenuAction(Icons.Delete, stringResource(R.string.games_remove), danger = true) { onRemove(app) }), onDismiss = { menu = false }, icon = icon)
                    }
                }
            }
        }
    }
}
