package com.elara.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The pages the drawer opens over itself: the Games folder, a drawer folder, or the hidden apps. */
sealed interface DrawerPage {
    data object Games : DrawerPage
    data object Hidden : DrawerPage
    data class Folder(val id: Int) : DrawerPage
}

/**
 * A page that looks like the app drawer but lists only [apps]: [title] at the top, a settings button and an add button at
 * the right. Add switches the list to [others] (tap one to put it in) until it is pressed again. Tap opens an app;
 * long-press offers [removeLabel]. Back leaves the add list, then the page. The wallpaper dim behind is the drawer's own.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppListPage(
    title: String,
    addTitle: String,
    emptyText: String,
    removeLabel: String,
    removeIcon: ImageVector,
    apps: List<AppEntry>,
    others: List<AppEntry>,
    repository: AppRepository,
    showLabels: Boolean,
    columns: Int,
    iconSize: Dp,
    rowGap: Dp,
    sidePadding: Dp,
    onSettings: () -> Unit,
    onAdd: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    BackHandler { if (adding) adding = false else onClose() }
    val list = if (adding) others else apps
    val sizePx = with(LocalDensity.current) { iconSize.roundToPx() }
    // A drag on the page must not carry the drawer under it.
    Column(modifier.fillMaxSize().safeDrawingPadding().draggable(rememberDraggableState { }, Orientation.Vertical)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (adding) addTitle else title,
                style = MaterialTheme.typography.headlineMedium.copy(shadow = WallpaperShadow),
                color = OnWallpaper,
                maxLines = 1,
                modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Settings, contentDescription = stringResource(R.string.drawer_settings), tint = OnWallpaper)
            }
            IconButton(onClick = { adding = !adding }) {
                Icon(if (adding) Icons.Done else Icons.Add, contentDescription = stringResource(if (adding) R.string.games_done else R.string.games_add), tint = OnWallpaper)
            }
        }
        if (list.isEmpty()) {
            Text(
                if (adding) stringResource(R.string.drawer_no_results) else emptyText,
                style = MaterialTheme.typography.bodyMedium.copy(shadow = WallpaperShadow),
                color = OnWallpaper.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 24.dp, end = 24.dp),
            )
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            items(list, key = { it.key }) { app ->
                val icon by produceState<ImageBitmap?>(repository.cachedIcon(app, sizePx), app.key, sizePx, repository.iconEpoch) { value = repository.icon(app, sizePx) }
                var menu by remember { mutableStateOf(false) }
                Box {
                    AppTileContent(
                        icon, app.label, showLabels, iconSize,
                        modifier = Modifier.combinedClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = { if (adding) onAdd(app) else { onClose(); repository.launch(app, null) } },
                            onLongClick = { if (!adding) menu = true },
                        ),
                    )
                    AppMenu(menu, app, repository, listOf(MenuAction(removeIcon, removeLabel, danger = true) { onRemove(app) }), onDismiss = { menu = false }, icon = icon)
                }
            }
        }
    }
}
