package com.elara.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * Shown over the home screen the first time: a welcome, making Elara the home app, a wallpaper,
 * and the two gestures there is no on-screen hint for. Back goes a step back; [onDone] when finished.
 */
@Composable
internal fun SetupScreen(isDefaultHome: Boolean, onChooseHome: () -> Unit, onWallpaper: () -> Unit, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = SetupSteps - 1
    BackHandler(enabled = step > 0) { step-- }
    Column(
        Modifier
            .fillMaxSize()
            .background(cs.surface)
            // Taps never fall through to the home screen underneath.
            .clickable(interactionSource = null, indication = null) {}
            .safeDrawingPadding()
            .padding(horizontal = 24.dp),
    ) {
        StepDots(step, Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp))
        // Material's shared-axis X, as in settings: forward slides in from the end, back from the start.
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val shift = { width: Int -> (if (targetState > initialState) width else -width) / 10 }
                (slideInHorizontally(tween(300)) { shift(it) } + fadeIn(tween(150, delayMillis = 75)))
                    .togetherWith(slideOutHorizontally(tween(300)) { -shift(it) } + fadeOut(tween(75)))
            },
            label = "setup step",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (shown) {
                    0 -> {
                        AppIcon()
                        StepText(R.string.setup_welcome_title, R.string.setup_welcome_body)
                    }
                    1 -> {
                        AccentBadge(if (isDefaultHome) Icons.Done else Icons.Home, Page.Home.accent, Page.Home.shape, size = 112)
                        StepText(R.string.setup_home_title, if (isDefaultHome) R.string.setup_home_done else R.string.setup_home_body)
                        if (!isDefaultHome) {
                            FilledTonalButton(onClick = onChooseHome, modifier = Modifier.padding(top = 24.dp)) {
                                Text(stringResource(R.string.settings_hero_set_home))
                            }
                        }
                    }
                    2 -> {
                        AccentBadge(Icons.Wallpaper, Page.Appearance.accent, Page.Appearance.shape, size = 112)
                        StepText(R.string.setup_wallpaper_title, R.string.setup_wallpaper_body)
                        FilledTonalButton(onClick = onWallpaper, modifier = Modifier.padding(top = 24.dp)) {
                            Icon(Icons.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.setup_wallpaper_pick))
                        }
                    }
                    else -> {
                        AccentBadge(Icons.Gesture, Page.Gestures.accent, Page.Gestures.shape, size = 112)
                        StepText(R.string.setup_tips_title, null)
                        Tip(Icons.Apps, R.string.setup_tip_drawer)
                        Tip(Icons.Settings, R.string.setup_tip_menu)
                        Tip(Icons.Edit, R.string.setup_tip_apps)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1 until last) {
                TextButton(onClick = { step++ }) { Text(stringResource(R.string.setup_skip)) }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = { if (step == last) onDone() else step++ }, modifier = Modifier.animateContentSize()) {
                Text(stringResource(if (step == 0) R.string.setup_start else if (step == last) R.string.setup_done else R.string.setup_next))
            }
        }
    }
}

private const val SetupSteps = 4

@Composable
private fun StepDots(step: Int, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(R.string.setup_step, step + 1, SetupSteps)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(SetupSteps) { i ->
            // The current step stretches into a pill, like the home screen's page dots.
            Box(
                Modifier
                    .animateContentSize()
                    .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                    .background(if (i == step) cs.primary else cs.outlineVariant, CircleShape),
            )
        }
    }
}

@Composable
private fun AppIcon() {
    val context = LocalContext.current
    val icon = remember { context.packageManager.getApplicationIcon(context.packageName).toBitmap(256, 256).asImageBitmap() }
    Image(icon, contentDescription = null, modifier = Modifier.size(112.dp).clearAndSetSemantics {})
}

@Composable
private fun StepText(title: Int, body: Int?) {
    val cs = MaterialTheme.colorScheme
    Spacer(Modifier.height(28.dp))
    Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = cs.onSurface, textAlign = TextAlign.Center)
    if (body != null) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Tip(icon: ImageVector, text: Int) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        AccentBadge(icon, Page.Gestures.accent, CircleShape, size = 40)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
    }
}
