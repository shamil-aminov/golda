package sh.aminov.golda.ui

import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import sh.aminov.golda.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.material3.Typography

/** The gold of the one action to take: the mic, Записать, Сохранить, Купить, Беру. */
@Immutable
class ActionColors(val container: Color, val content: Color)

private val LocalAction = staticCompositionLocalOf { ActionColors(Color.Unspecified, Color.Unspecified) }

/** The gold behind the one action on a screen or sheet. */
@Composable
@ReadOnlyComposable
fun actionColor(): Color = LocalAction.current.container

/** Ink on [actionColor]. */
@Composable
@ReadOnlyComposable
fun onActionColor(): Color = LocalAction.current.content

/**
 * Colors.kt puts the gold on primary. Every M3 component reaches for primary on its own (switches,
 * date-picker selection, text buttons, cursors, focus, progress, the snackbar's action), so the
 * scheme the app runs with hands primary the graphite of "what is picked" instead, and the gold is
 * only where a composable asks for it by name: [actionColor].
 */
private fun ColorScheme.goldOnlyByName(): ColorScheme = copy(
    primary = secondary,
    onPrimary = onSecondary,
    primaryContainer = secondaryContainer,
    onPrimaryContainer = onSecondaryContainer,
    // The snackbar's action sits on the inverse surface: the quiet line tone reads on it in both themes.
    inversePrimary = outline,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GoldaTheme(palette: String? = null, content: @Composable () -> Unit) {
    val tones = tonesFor(palette, isSystemInDarkTheme())
    val base = colorSchemeOf(tones)
    // The bars' icons follow the theme, not the system: dark icons on a light theme and back.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !tones.dark
                isAppearanceLightNavigationBars = !tones.dark
            }
        }
    }
    CompositionLocalProvider(LocalAction provides ActionColors(base.primary, base.onPrimary), LocalTones provides tones) {
        MaterialExpressiveTheme(
            colorScheme = base.goldOnlyByName(),
            motionScheme = MotionScheme.expressive(),
            typography = Typography().withFamily(Inter),
            content = content,
        )
    }
}

/** Ice blue: the one cool colour, for the way to Gemini's reviews. The gold stays the action. */
@Composable
@ReadOnlyComposable
fun iceColor(): Color = if (isDark()) Color(0xFF234A63) else Color(0xFFCFE7F7)

/** Ink on [iceColor]. */
@Composable
@ReadOnlyComposable
fun onIceColor(): Color = if (isDark()) Color(0xFFD7EEFB) else Color(0xFF0E3B57)

/** Tabular figures, so columns of amounts line up. */
val Tnum = TextStyle(fontFeatureSettings = "tnum")

/**
 * Inter, bundled (third_party/inter): one face with every currency the app shows, ₾ ₽ $ ฿ € ₸, and
 * Cyrillic. The system's Roboto has no ฿, which then came from a Thai fallback with other metrics and
 * shifted the line it stood in. One variable file; each weight is an instance of it.
 */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    (100..900 step 100).map { weight ->
        Font(R.font.inter, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    },
)

/** Every style of [this] in [family], the emphasized ones too. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** Material's tracking is set for Roboto; Inter reads better without it. */
private val Tight = 0.sp

private fun Typography.withFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family, letterSpacing = Tight),
    titleMedium = titleMedium.copy(fontFamily = family, letterSpacing = Tight),
    titleSmall = titleSmall.copy(fontFamily = family, letterSpacing = Tight),
    bodyLarge = bodyLarge.copy(fontFamily = family, letterSpacing = Tight),
    bodyMedium = bodyMedium.copy(fontFamily = family, letterSpacing = Tight),
    bodySmall = bodySmall.copy(fontFamily = family, letterSpacing = Tight),
    labelLarge = labelLarge.copy(fontFamily = family, letterSpacing = Tight),
    labelMedium = labelMedium.copy(fontFamily = family, letterSpacing = Tight),
    labelSmall = labelSmall.copy(fontFamily = family, letterSpacing = Tight),
    displayLargeEmphasized = displayLargeEmphasized.copy(fontFamily = family),
    displayMediumEmphasized = displayMediumEmphasized.copy(fontFamily = family),
    displaySmallEmphasized = displaySmallEmphasized.copy(fontFamily = family),
    headlineLargeEmphasized = headlineLargeEmphasized.copy(fontFamily = family),
    headlineMediumEmphasized = headlineMediumEmphasized.copy(fontFamily = family),
    headlineSmallEmphasized = headlineSmallEmphasized.copy(fontFamily = family),
    titleLargeEmphasized = titleLargeEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    titleMediumEmphasized = titleMediumEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    titleSmallEmphasized = titleSmallEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    bodyLargeEmphasized = bodyLargeEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    bodyMediumEmphasized = bodyMediumEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    bodySmallEmphasized = bodySmallEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    labelLargeEmphasized = labelLargeEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    labelMediumEmphasized = labelMediumEmphasized.copy(fontFamily = family, letterSpacing = Tight),
    labelSmallEmphasized = labelSmallEmphasized.copy(fontFamily = family, letterSpacing = Tight),
)
