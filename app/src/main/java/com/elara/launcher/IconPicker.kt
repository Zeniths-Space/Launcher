package com.elara.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the icon picker for an app; null where icons can't be changed. Set by the launcher, so every app menu gets Change icon. */
internal val LocalChangeIcon = staticCompositionLocalOf<((AppEntry) -> Unit)?> { null }

/**
 * Picks one icon for [app] out of any installed icon pack, for apps the pack in use has no icon
 * for (or a wrong one). [current] is the icon picked before ("pack package/drawable name"), if any;
 * [onPick] gets the new one, or null to go back to the app's usual icon. Picking closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IconPickerSheet(app: AppEntry, repository: AppRepository, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { repository.iconPacks() } }
    var chosen by remember { mutableStateOf(current?.substringBefore('/')) }
    val pack = packs?.let { list -> list.firstOrNull { it.packageName == chosen } ?: list.firstOrNull() }
    val names by produceState<List<String>?>(null, pack?.packageName) {
        value = null
        value = pack?.let { repository.packIconNames(it.packageName) }
    }
    var query by remember { mutableStateOf("") }
    val found = remember(names, query) { names?.let { IconPacks.search(it, query) } }
    val sizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    ElaraSheet(stringResource(R.string.icon_pick_title, app.label), Icons.Palette, onDismiss = onDismiss) {
        if (packs?.isEmpty() == true) {
            Text(
                stringResource(R.string.icon_pick_none),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
            )
            return@ElaraSheet
        }
        // Which pack to look in, when there is more than one.
        if ((packs?.size ?: 0) > 1) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            packs.orEmpty().forEach { p ->
                val on = p.packageName == pack?.packageName
                Text(
                    p.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) cs.primary else cs.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) cs.primary.copy(alpha = 0.22f) else cs.surfaceContainerHighest)
                        .clickable { chosen = p.packageName }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cs.surfaceContainerHighest)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp).size(22.dp))
                    Box {
                        if (query.isEmpty()) Text(stringResource(R.string.icon_pick_search), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                        inner()
                    }
                }
            },
        )
        if (current != null) TextButton(onClick = { onPick(null); onDismiss() }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(stringResource(R.string.icon_pick_default))
        }
        if (found?.isEmpty() == true) Text(
            stringResource(R.string.icon_pick_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
        // One height whatever is found, so the sheet doesn't jump while typing.
        LazyVerticalGrid(
            GridCells.Adaptive(64.dp),
            modifier = Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 12.dp).navigationBarsPadding(),
        ) {
            items(found.orEmpty(), key = { it }) { name ->
                val packName = pack?.packageName ?: return@items
                val icon by produceState<ImageBitmap?>(null, packName, name) { value = repository.packIcon(packName, name, sizePx) }
                Box(
                    Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .then(if (current == "$packName/$name") Modifier.background(cs.primary.copy(alpha = 0.22f)) else Modifier)
                        .clickable(onClickLabel = name.replace('_', ' ')) { onPick("$packName/$name"); onDismiss() }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(48.dp)) { icon?.let { Image(it, contentDescription = name.replace('_', ' '), modifier = Modifier.size(48.dp)) } }
                }
            }
        }
    }
}
