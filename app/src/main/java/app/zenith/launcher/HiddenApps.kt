package app.zenith.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect

/**
 * The drawer's hidden apps, shown only after [rememberDeviceAuth]: tap one to open it, or its eye
 * button to unhide it; or a line saying how to hide one. [apps] are the hidden ones still installed.
 * It closes when the launcher leaves the screen, so seeing them again asks again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HiddenAppsSheet(apps: List<AppEntry>, repository: AppRepository, onUnhide: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    LifecycleStartEffect(Unit) { onStopOrDispose { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (LocalPureBlack.current) PureBlackCard else cs.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.settings_hidden),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        )
        if (apps.isEmpty()) {
            Text(
                stringResource(R.string.settings_hidden_none),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).navigationBarsPadding(),
            )
            return@ModalBottomSheet
        }
        val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp).padding(bottom = 16.dp)) {
            items(apps, key = { it.key }) { app ->
                val icon by produceState<ImageBitmap?>(repository.cachedIcon(app, sizePx), app.key) { value = repository.icon(app, sizePx) }
                val unhide = stringResource(R.string.settings_unhide_app, app.label)
                ListItem(
                    headlineContent = { Text(app.label) },
                    leadingContent = { Box(Modifier.size(40.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) } } },
                    trailingContent = {
                        IconButton(onClick = { onUnhide(app) }) {
                            Icon(Icons.Visibility, contentDescription = unhide, tint = cs.primary)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onDismiss()
                            repository.launch(app, null)
                        },
                )
            }
        }
    }
}
