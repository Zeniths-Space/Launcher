package com.elara.launcher

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

/*
 * Design language shared with the owner's Kontract app and Zenboard (M3 Expressive): Elara brand purple,
 * an optional AMOLED near-black surface ladder in dark mode, white cards on a tinted page in light mode,
 * slate ink, semibold type, springy motion.
 *
 * shortcut: plain MaterialTheme, because MaterialExpressiveTheme and MotionScheme.expressive() are
 * not public in stable material3 1.4.0; switch when material3 1.5.0 is stable.
 */

private val SlateInk = Color(0xFF0F172A)
private val SlateMuted = Color(0xFF475569)

/** Near-black dark surfaces, lowest to highest, so tonal cards lift off an AMOLED page. */
private val DarkSurfaces = listOf(
    Color(0xFF050607), Color(0xFF0A0C0E), Color(0xFF0F1215), Color(0xFF151A1E), Color(0xFF1D242A),
)

/** Light layering: tinted page, white cards; anything nested in a card is a step darker. */
private fun ColorScheme.withLightSurfaces(): ColorScheme {
    val page = surfaceContainer
    val card = surfaceContainerLowest
    return copy(
        surface = page,
        background = page,
        surfaceBright = card,
        surfaceContainerLow = page,
        surfaceContainer = card,
        surfaceContainerHigh = card,
        surfaceContainerHighest = surfaceContainerHigh,
        surfaceTint = primary,
        onSurface = SlateInk,
        onBackground = SlateInk,
        onSurfaceVariant = SlateMuted,
    )
}

/** Dark treatment: near-black ladder and light slate ink, keeping the scheme's accents. */
private fun ColorScheme.withDarkSurfaces(): ColorScheme {
    val s = DarkSurfaces
    return copy(
        surface = s[0],
        background = s[0],
        surfaceDim = s[0],
        surfaceBright = s[4],
        surfaceContainerLowest = s[0],
        surfaceContainerLow = s[1],
        surfaceContainer = s[2],
        surfaceContainerHigh = s[3],
        surfaceContainerHighest = s[4],
        outlineVariant = lerp(outline, s[0], 0.4f),
        onSurface = Color(0xFFF1F5F9),
        onBackground = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFFCBD5E1),
    )
}

/** Elara brand purple (#7C4DFF) as M3 roles; secondary, tertiary and surfaces keep the purple-grey M3 baseline. */
private val BrandLight = lightColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF23005C),
    inversePrimary = Color(0xFFCFBCFF),
    secondaryContainer = Color(0xFFE9DEF8),
    onSecondaryContainer = Color(0xFF1F182B),
    surfaceTint = Color(0xFF7C4DFF),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF33009C),
    primaryContainer = Color(0xFF5A22D8),
    onPrimaryContainer = Color(0xFFE9DDFF),
    inversePrimary = Color(0xFF7C4DFF),
    secondaryContainer = Color(0xFF4B4358),
    onSecondaryContainer = Color(0xFFE9DEF8),
    surfaceTint = Color(0xFFCFBCFF),
)

/**
 * The Elara brand purple, light or dark.
 * Dark mode keeps the scheme's own surfaces unless [pureBlack] asks for the AMOLED ladder.
 */
private fun schemeFor(dark: Boolean, pureBlack: Boolean): ColorScheme {
    return when {
        dark -> {
            val scheme = BrandDark
            if (pureBlack) scheme.withDarkSurfaces() else scheme
        }
        else -> BrandLight.withLightSurfaces()
    }
}

/** System font (the user's choice); only weights are emphasized. */
private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Every text style in [font]: all text in the app comes from these, so this reaches labels, folders and settings alike. */
private fun Typography.withFont(font: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = font),
    displayMedium = displayMedium.copy(fontFamily = font),
    displaySmall = displaySmall.copy(fontFamily = font),
    headlineLarge = headlineLarge.copy(fontFamily = font),
    headlineMedium = headlineMedium.copy(fontFamily = font),
    headlineSmall = headlineSmall.copy(fontFamily = font),
    titleLarge = titleLarge.copy(fontFamily = font),
    titleMedium = titleMedium.copy(fontFamily = font),
    titleSmall = titleSmall.copy(fontFamily = font),
    bodyLarge = bodyLarge.copy(fontFamily = font),
    bodyMedium = bodyMedium.copy(fontFamily = font),
    bodySmall = bodySmall.copy(fontFamily = font),
    labelLarge = labelLarge.copy(fontFamily = font),
    labelMedium = labelMedium.copy(fontFamily = font),
    labelSmall = labelSmall.copy(fontFamily = font),
)

internal val LocalDark = staticCompositionLocalOf { false }

/** Whether the pure black (AMOLED) surfaces are showing: dark mode with the setting on. */
internal val LocalPureBlack = staticCompositionLocalOf { false }

/** Settings cards on the pure black page: neutral grey. */
internal val PureBlackCard = Color(0xFF1A1A1A)

object ZenithColors {
    /** Selected/active pill: the secondary accent washed over a card. */
    val indicator: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.run {
            secondary.copy(alpha = if (LocalDark.current) 0.30f else 0.18f).compositeOver(surfaceContainerHigh)
        }

    /** Content on [indicator]. */
    val onIndicator: Color
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.run {
            if (LocalDark.current) secondary else onSecondaryContainer
        }
}

/** Motion tokens (same values as Kontract's Motion.kt). */
object Motion {
    private val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Colour / opacity changes. */
    fun <T> effects(duration: Int = 300, delay: Int = 0): FiniteAnimationSpec<T> = tween(duration, delay, Standard)

    /** Things arriving on screen, such as a page sliding in. */
    fun <T> spatial(duration: Int = 450): FiniteAnimationSpec<T> = tween(duration, easing = EmphasizedDecelerate)

    /** Gently springy, for small transforms such as a pressed button. */
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Shrinks while a finger is down and springs back on release, so a tap is felt before the app opens.
 * Only watches touches: put it last in the chain, inside any gesture handlers, which still get them.
 * A drag or long-press drag that takes the touch lets it go.
 */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) PressedScale else 1f, Motion.bouncy(), label = "press")
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            waitForUpOrCancellation()
            pressed = false
        }
    }.graphicsLayer {
        // Read here, per frame, so the press animates without recomposing.
        scaleX = scale
        scaleY = scale
    }
}

private const val PressedScale = 0.88f

@Composable
fun ZenithTheme(
    dark: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    /** [LauncherSettings.fontVersion]: 0 for the system font. */
    fontVersion: Long = 0,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = remember(dark, pureBlack) { schemeFor(dark, pureBlack) }
    val typography = remember(fontVersion) { CustomFont.family(context, fontVersion)?.let(AppTypography::withFont) ?: AppTypography }
    CompositionLocalProvider(LocalDark provides dark, LocalPureBlack provides (dark && pureBlack)) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            // Screens draw on plain backgrounds, not Surfaces; without this, bare Icons default to black.
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}
