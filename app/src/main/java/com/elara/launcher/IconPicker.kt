package com.elara.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens the icon picker for an app; null where icons can't be changed. Set by the launcher, so every app menu gets Change icon. */
internal val LocalChangeIcon = staticCompositionLocalOf<((AppEntry) -> Unit)?> { null }

/**
 * Picks one icon for [app], for apps the pack in use has no icon for (or a wrong one). It opens on
 * a list of where to take it from: each installed icon pack (then that pack's icons, with a search),
 * the gallery, or back to the app's usual icon. [current] is the icon picked before, if any;
 * [onPick] gets the new one ("pack package/drawable name", or a [PickedIcons] file), or null for
 * the usual icon. Picking closes the sheet.
 */
@Composable
internal fun IconPickerSheet(app: AppEntry, repository: AppRepository, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { repository.iconPacks() } }
    // The pack whose icons are shown; null for the first page, the list of places to take an icon from.
    var pack by remember { mutableStateOf<IconPackInfo?>(null) }
    val names by produceState<List<String>?>(null, pack?.packageName) {
        value = null
        value = pack?.let { repository.packIconNames(it.packageName) }
    }
    var query by remember { mutableStateOf("") }
    val found = remember(names, query) { names?.let { IconPacks.search(it, query) } }
    val sizePx = with(LocalDensity.current) { 48.dp.roundToPx() }
    // Android's photo picker: it hands over the one picture chosen, so no storage permission.
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            PickedIcons.save(context, uri)?.let(onPick)
            onDismiss()
        }
    }
    ElaraSheet(stringResource(R.string.icon_pick_title, app.label), Icons.Palette, onDismiss = onDismiss) {
        val shown = pack
        if (shown == null) {
            SheetGroup(
                buildList {
                    packs.orEmpty().forEach { p -> add(SheetRow(Icons.Widgets, p.label) { query = ""; pack = p }) }
                    add(SheetRow(Icons.Wallpaper, stringResource(R.string.icon_pick_gallery)) {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    if (current != null) add(SheetRow(Icons.History, stringResource(R.string.icon_pick_default)) { onPick(null); onDismiss() })
                },
            )
            return@ElaraSheet
        }
        // Back goes to the list of packs before it closes the sheet.
        BackHandler { pack = null }
        Row(Modifier.padding(start = 8.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { pack = null }) { Icon(Icons.Previous, contentDescription = stringResource(R.string.icon_pick_back)) }
            Text(shown.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/**
 * Pictures from the gallery used as app icons: each is cut square, shrunk and kept as a PNG in the
 * app's private files. In settings one reads "file:<name>".
 */
internal object PickedIcons {
    private const val Prefix = "file:"
    /** The picture's side on the icon; around it, a margin an adaptive icon's mask cuts off. */
    private const val Side = 288

    fun isFile(icon: String) = icon.startsWith(Prefix)

    private fun file(context: Context, icon: String) = File(File(context.filesDir, "app_icons"), icon.removePrefix(Prefix).substringAfterLast('/'))

    /** Copies the picture at [uri] in as an icon; null when it can't be read. */
    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val picture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSampleSize((minOf(info.size.width, info.size.height) / Side).coerceAtLeast(1))
                }
            } else {
                // shortcut: decoded whole and unrotated before Android 9; sample and read EXIF if old phones matter.
                context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            }
            // The middle square of the picture.
            val cut = minOf(picture.width, picture.height)
            val from = Rect((picture.width - cut) / 2, (picture.height - cut) / 2, (picture.width + cut) / 2, (picture.height + cut) / 2)
            // An adaptive icon shows the middle two thirds of its layer: the picture goes there, over
            // a stretched copy that fills the margin, so no shape shows an empty edge.
            val whole = Side * 3 / 2
            val layer = Bitmap.createBitmap(whole, whole, Bitmap.Config.ARGB_8888)
            Canvas(layer).apply {
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                drawBitmap(picture, from, Rect(0, 0, whole, whole), paint)
                drawBitmap(picture, from, Rect(Side / 4, Side / 4, Side / 4 + Side, Side / 4 + Side), paint)
            }
            // A new name each time, so a changed picture is never taken for the old one.
            val name = "${System.currentTimeMillis()}.png"
            val dir = File(context.filesDir, "app_icons").apply { mkdirs() }
            File(dir, name).outputStream().use { layer.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Prefix + name
        }.getOrNull()
    }

    /** The saved picture as an adaptive icon, so it takes the icon shape like any app's; null if the file is gone. */
    fun load(context: Context, icon: String): Drawable? =
        BitmapFactory.decodeFile(file(context, icon).path)?.let { AdaptiveIconDrawable(BitmapDrawable(context.resources, it), null) }

    /** Removes the picture behind [icon] once no app uses it; other kinds of icon are left alone. */
    // shortcut: an uninstalled app's picture stays until its icon is changed; sweep the folder if it grows.
    fun delete(context: Context, icon: String?) {
        if (icon != null && isFile(icon)) file(context, icon).delete()
    }
}
