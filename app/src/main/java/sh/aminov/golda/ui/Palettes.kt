package sh.aminov.golda.ui

import androidx.compose.ui.graphics.Color
import sh.aminov.golda.domain.tr

/**
 * The colour themes to pick from in Settings. Golda follows the system's light or dark; every other
 * one is light or dark of its own. Each is a full set of [Tones]: page, cards, controls, what is
 * picked, the action colour and the inks, so a pick repaints the whole app.
 */
enum class Palette(val key: String, private val ru: String, private val en: String, val light: Tones?, val dark: Tones?) {
    GOLDA("golda", "Golda", "Golda", GoldaLight, GoldaDark),
    BEACH("beach", "Пляж", "Beach", beach(), null),
    SAKURA("sakura", "Сакура", "Sakura", sakura(), null),
    MINT("mint", "Мята", "Mint", mint(), null),
    LAVENDER("lavender", "Лаванда", "Lavender", lavender(), null),
    MIDNIGHT("midnight", "Полночь", "Midnight", null, midnight()),
    ESPRESSO("espresso", "Эспрессо", "Espresso", null, espresso()),
    FOREST("forest", "Лес", "Forest", null, forest()),
    GRAPHITE("graphite", "Графит", "Graphite", null, graphite()),
    ;

    val label: String get() = tr(ru, en)

    /** The tones for the system's [systemDark]: a fixed theme ignores it. */
    fun tones(systemDark: Boolean): Tones = when {
        light != null && dark != null -> if (systemDark) dark else light
        else -> light ?: dark!!
    }

    companion object {
        fun of(key: String?): Palette = entries.firstOrNull { it.key == key } ?: GOLDA
    }
}

private fun lightSet(page: Long, card: Long, soft: Long, line: Long, muted: Long, text: Long, picked: Long, onPicked: Long, action: Long, onAction: Long) = Tones(
    dark = false,
    page = Color(page), card = Color(card), soft = Color(soft), line = Color(line), muted = Color(muted), text = Color(text),
    graphite = Color(picked), onGraphite = Color(onPicked), gold = Color(action), onGold = Color(onAction),
    error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF), errorSoft = Color(0xFFFEE2E2), onErrorSoft = Color(0xFF991B1B),
)

private fun darkSet(page: Long, card: Long, soft: Long, line: Long, muted: Long, text: Long, picked: Long, onPicked: Long, action: Long, onAction: Long) = Tones(
    dark = true,
    page = Color(page), card = Color(card), soft = Color(soft), line = Color(line), muted = Color(muted), text = Color(text),
    graphite = Color(picked), onGraphite = Color(onPicked), gold = Color(action), onGold = Color(onAction),
    error = Color(0xFFF87171), onError = Color(0xFF450A0A), errorSoft = Color(0xFF4C1D1D), onErrorSoft = Color(0xFFFECACA),
)

// Sand and turquoise, like a beach at noon.
private fun beach() = lightSet(0xFFF4EEE3, 0xFFFFFCF6, 0xFFE9E0D0, 0xFFDACFBB, 0xFF8A7B66, 0xFF3A3128, 0xFFBFE4DF, 0xFF103F3A, 0xFF5CC6BA, 0xFF06302B)

// Blossom pink on white.
private fun sakura() = lightSet(0xFFFBF1F3, 0xFFFFFFFF, 0xFFF2E0E5, 0xFFE7CAD2, 0xFF8E6B75, 0xFF3B2A30, 0xFFF5CDD9, 0xFF4A1E2C, 0xFFEC93AF, 0xFF3B0D1C)

// Fresh green and pale grey.
private fun mint() = lightSet(0xFFEEF5F0, 0xFFFFFFFF, 0xFFDDEAE1, 0xFFC7DCCE, 0xFF637A6B, 0xFF23302A, 0xFFC2E6CF, 0xFF123B23, 0xFF67C590, 0xFF08301A)

// Soft violet.
private fun lavender() = lightSet(0xFFF2F0FA, 0xFFFFFFFF, 0xFFE4E0F3, 0xFFD1CBE8, 0xFF706A8C, 0xFF2B2840, 0xFFD8CEF6, 0xFF2A1F5C, 0xFFA595EC, 0xFF1A1240)

// Deep night blue with a cool light.
private fun midnight() = darkSet(0xFF0F1724, 0xFF182235, 0xFF243149, 0xFF33425E, 0xFF8C9AB5, 0xFFE6ECF5, 0xFF2E4A75, 0xFFDCE8FF, 0xFF7FB2FF, 0xFF0A1B33)

// Coffee browns with a warm gold.
private fun espresso() = darkSet(0xFF1C1714, 0xFF26201B, 0xFF352C25, 0xFF463A31, 0xFFA8998A, 0xFFF3EBE2, 0xFF4A3B2F, 0xFFF6E6D3, 0xFFE2B655, 0xFF2B1D00)

// Pine green at dusk.
private fun forest() = darkSet(0xFF111A15, 0xFF19251E, 0xFF24352B, 0xFF2F4538, 0xFF8FA699, 0xFFE4EFE8, 0xFF2D4A3A, 0xFFD6F2E2, 0xFF7FD19B, 0xFF072615)

// Plain black and greys, the gold for the action.
private fun graphite() = darkSet(0xFF121212, 0xFF1E1E1E, 0xFF2C2C2C, 0xFF3A3A3A, 0xFFA0A0A0, 0xFFF2F2F2, 0xFF3D3D3D, 0xFFFFFFFF, 0xFFF2C14E, 0xFF2B1D00)
