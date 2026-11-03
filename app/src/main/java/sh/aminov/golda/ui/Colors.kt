package sh.aminov.golda.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Light graphite neutrals from Tailwind's Zinc scale, one gold and one red. Every Material role
 * below is one of these, so whatever role a component asks for, it lands on the same few colours:
 *
 * - [Tones.page]: the light grey background and every sheet.
 * - [Tones.card]: every tile, list, field and the toolbar, the hero tiles included.
 * - [Tones.soft]: tonal buttons (Сомневаюсь, Сходится), unpicked toggles.
 * - [Tones.line]: tracks, dividers, the quietest chart marks.
 * - [Tones.muted] / [Tones.text]: secondary and primary text and icons.
 * - [Tones.graphite]: what is picked (a tab, a toggle, a category): a light slate, with dark ink.
 * - [Tones.gold]: the one action to take (the mic, Записать, Сохранить, Готово). Golda's gold.
 * - red: bad news only (overspending, debts, erasing).
 */
class Tones(
    /** A dark set: Material's dark scheme, light status bar icons. */
    val dark: Boolean,
    val page: Color,
    val card: Color,
    val soft: Color,
    val line: Color,
    val muted: Color,
    val text: Color,
    val graphite: Color,
    val onGraphite: Color,
    val gold: Color,
    val onGold: Color,
    val error: Color,
    val onError: Color,
    val errorSoft: Color,
    val onErrorSoft: Color,
    /** Lightness and chroma (OKLCH) of the colours that inform: categories and the pace bar. */
    val infoL: Float = if (dark) 0.68f else 0.80f,
    val infoC: Float = 0.09f,
    /** The theme's own hue (OKLCH degrees): the leitmotif the biggest colours take. */
    val seedHue: Int = hueOf(gold),
    /**
     * Colours told apart by lightness as well as hue, with no green-yellow or blue-violet pairs:
     * for eyes that mix those up.
     */
    val clearColours: Boolean = false,
)

internal val GoldaLight = Tones(
    dark = false,
    page = Color(0xFFF4F4F5),
    card = Color(0xFFFFFFFF),
    soft = Color(0xFFE4E4E7),
    line = Color(0xFFD4D4D8),
    muted = Color(0xFF61616A),
    text = Color(0xFF27272A),
    graphite = Color(0xFFCBD5DE),
    onGraphite = Color(0xFF18222B),
    gold = Color(0xFFD9A93E),
    onGold = Color(0xFF2B1D00),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorSoft = Color(0xFFFEE2E2),
    onErrorSoft = Color(0xFF991B1B),
)

internal val GoldaDark = Tones(
    dark = true,
    page = Color(0xFF1F1F23),
    card = Color(0xFF2A2A2F),
    soft = Color(0xFF3F3F46),
    line = Color(0xFF52525B),
    muted = Color(0xFFACACB4),
    text = Color(0xFFF4F4F5),
    graphite = Color(0xFF4A5561),
    onGraphite = Color(0xFFEEF2F6),
    gold = Color(0xFFE2B655),
    onGold = Color(0xFF2B1D00),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorSoft = Color(0xFF4C1D1D),
    onErrorSoft = Color(0xFFFECACA),
)

/** The Material scheme for a set of [Tones]: every role lands on one of its few colours. */
internal fun colorSchemeOf(t: Tones) = if (t.dark) darkScheme(t) else lightScheme(t)

private fun lightScheme(t: Tones) = with(t) {
    lightColorScheme(
        primary = gold, onPrimary = onGold, primaryContainer = gold, onPrimaryContainer = onGold, inversePrimary = gold,
        secondary = graphite, onSecondary = onGraphite, secondaryContainer = soft, onSecondaryContainer = text,
        tertiary = graphite, onTertiary = onGraphite, tertiaryContainer = soft, onTertiaryContainer = text,
        background = page, onBackground = text, surface = page, onSurface = text,
        surfaceVariant = soft, onSurfaceVariant = muted, surfaceTint = Color.Transparent,
        inverseSurface = text, inverseOnSurface = page,
        error = error, onError = onError, errorContainer = errorSoft, onErrorContainer = onErrorSoft,
        outline = line, outlineVariant = line, scrim = Color.Black,
        surfaceBright = card, surfaceDim = page,
        surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = card, surfaceContainerHighest = soft,
        primaryFixed = card, primaryFixedDim = graphite, onPrimaryFixed = text, onPrimaryFixedVariant = muted,
        secondaryFixed = card, secondaryFixedDim = graphite, onSecondaryFixed = text, onSecondaryFixedVariant = muted,
        tertiaryFixed = card, tertiaryFixedDim = graphite, onTertiaryFixed = text, onTertiaryFixedVariant = muted,
    )
}

private fun darkScheme(t: Tones) = with(t) {
    darkColorScheme(
        primary = gold, onPrimary = onGold, primaryContainer = gold, onPrimaryContainer = onGold, inversePrimary = gold,
        secondary = graphite, onSecondary = onGraphite, secondaryContainer = soft, onSecondaryContainer = text,
        tertiary = graphite, onTertiary = onGraphite, tertiaryContainer = soft, onTertiaryContainer = text,
        background = page, onBackground = text, surface = page, onSurface = text,
        surfaceVariant = soft, onSurfaceVariant = muted, surfaceTint = Color.Transparent,
        inverseSurface = text, inverseOnSurface = page,
        error = error, onError = onError, errorContainer = errorSoft, onErrorContainer = onErrorSoft,
        outline = line, outlineVariant = line, scrim = Color.Black,
        surfaceBright = card, surfaceDim = page,
        surfaceContainerLowest = card, surfaceContainerLow = card, surfaceContainer = card,
        surfaceContainerHigh = card, surfaceContainerHighest = soft,
        primaryFixed = card, primaryFixedDim = graphite, onPrimaryFixed = text, onPrimaryFixedVariant = muted,
        secondaryFixed = card, secondaryFixedDim = graphite, onSecondaryFixed = text, onSecondaryFixedVariant = muted,
        tertiaryFixed = card, tertiaryFixedDim = graphite, onTertiaryFixed = text, onTertiaryFixedVariant = muted,
    )
}
