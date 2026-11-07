package com.elara.launcher

import android.appwidget.AppWidgetProviderInfo
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.max

/**
 * A widget's size in cells: what it asks for on Android 12+, but never less than its smallest size
 * needs, since a dense grid's cells are smaller than the ones it asked for.
 */
fun widgetSpan(info: AppWidgetProviderInfo, cell: Size, columns: Int, rows: Int): Pair<Int, Int> {
    fun cells(target: Int, minPx: Int, cellPx: Float) = maxOf(target, if (cellPx > 0f) ceil(minPx / cellPx).toInt() else 2, 1)
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val w = cells(if (modern) info.targetCellWidth else 0, info.minWidth, cell.width)
    val h = cells(if (modern) info.targetCellHeight else 0, info.minHeight, cell.height)
    return w.coerceIn(1, columns) to h.coerceIn(1, rows)
}

/** Every app's widgets, a row per app, each with its preview, name and size; tap one to add it. */
@Composable
internal fun WidgetPicker(
    host: WidgetHost,
    appLabel: (packageName: String) -> String?,
    span: (AppWidgetProviderInfo) -> Pair<Int, Int>,
    onPick: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    // By app, apps in name order; widgets as each app lists them.
    val groups by produceState<List<Pair<String, List<AppWidgetProviderInfo>>>?>(null) {
        value = withContext(Dispatchers.IO) {
            host.providers()
                .groupBy { it.provider.packageName to it.profile }
                .map { (key, infos) -> (appLabel(key.first) ?: infos.first().loadLabel(context.packageManager)) to infos }
                .sortedBy { it.first.lowercase() }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surface)
            .statusBarsPadding(),
    ) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Previous, contentDescription = stringResource(R.string.back))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.widgets_title), style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
        }
        val list = groups
        when {
            list == null -> Unit
            list.isEmpty() -> Text(
                stringResource(R.string.widgets_none),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            ) {
                items(list, key = { it.second.first().provider.flattenToString() + it.second.first().profile.hashCode() }) { (label, infos) ->
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
                    )
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(infos, key = { it.provider.flattenToString() }) { info -> WidgetCard(info, span(info)) { onPick(info) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(info: AppWidgetProviderInfo, span: Pair<Int, Int>, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val maxPx = with(LocalDensity.current) { PreviewHeight.roundToPx() * 2 }
    val dpi = LocalConfiguration.current.densityDpi
    val preview by produceState<ImageBitmap?>(null, info, dpi) {
        value = withContext(Dispatchers.IO) {
            runCatching { info.loadPreviewImage(context, dpi) ?: info.loadIcon(context, dpi) }.getOrNull()?.toPreview(maxPx)
        }
    }
    val label = androidx.compose.runtime.remember(info) { runCatching { info.loadLabel(context.packageManager) }.getOrNull().orEmpty() }
    Column(
        Modifier
            .width(CardWidth)
            .clip(RoundedCornerShape(20.dp))
            .background(if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(PreviewHeight), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            stringResource(R.string.widgets_size, span.first, span.second),
            style = MaterialTheme.typography.labelMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

// A preview at most [maxPx] on its longer side, keeping its shape.
private fun Drawable.toPreview(maxPx: Int): ImageBitmap {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = minOf(1f, maxPx.toFloat() / max(w, h))
    val bw = (w * scale).toInt().coerceAtLeast(1)
    val bh = (h * scale).toInt().coerceAtLeast(1)
    val bitmap = createBitmap(bw, bh)
    setBounds(0, 0, bw, bh)
    draw(android.graphics.Canvas(bitmap))
    return bitmap.asImageBitmap()
}

private val CardWidth = 168.dp
private val PreviewHeight = 112.dp
