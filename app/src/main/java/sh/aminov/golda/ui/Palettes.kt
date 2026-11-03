package sh.aminov.golda.ui

import androidx.compose.ui.graphics.Color
import sh.aminov.golda.domain.tr

/**
 * The colour themes to pick from in Settings, each light and dark: the app's one mode setting (or the
 * phone's) decides which half is drawn. The calm ones first, then the bold ones: bold in colour, never
 * in readability, every half keeps its inks as legible as the calm ones (see ThemeGenTest). The
 * hand-drawn halves are the first themes'; their other halves and the new themes come from
 * [ThemeGen.recipe] in the same hues.
 */
enum class Palette(val key: String, private val ru: String, private val en: String, val bold: Boolean, light: () -> Tones, dark: () -> Tones) {
    GOLDA("golda", "Golda", "Golda", false, { GoldaLight }, { GoldaDark }),
    CLEAR("clear", "Ясная", "Clear", false, ::clear, ::clearNight),
    GRAPHITE("graphite", "Графит", "Graphite", false, { recipe(false, 90.0, 0.0, 250.0, 0.0, 85.0, 0.14) }, ::graphite),
    BEACH("beach", "Пляж", "Beach", false, ::beach, { recipe(true, 70.0, 0.02, 185.0, 0.05, 185.0, 0.11) }),
    SAKURA("sakura", "Сакура", "Sakura", false, ::sakura, { recipe(true, 355.0, 0.025, 355.0, 0.06, 355.0, 0.12) }),
    MINT("mint", "Мята", "Mint", false, ::mint, { recipe(true, 160.0, 0.02, 155.0, 0.05, 155.0, 0.13) }),
    LAVENDER("lavender", "Лаванда", "Lavender", false, ::lavender, { recipe(true, 290.0, 0.03, 290.0, 0.06, 290.0, 0.12) }),
    MIDNIGHT("midnight", "Полночь", "Midnight", false, { recipe(false, 250.0, 0.015, 255.0, 0.05, 255.0, 0.12) }, ::midnight),
    ESPRESSO("espresso", "Эспрессо", "Espresso", false, { recipe(false, 60.0, 0.02, 55.0, 0.05, 80.0, 0.13) }, ::espresso),
    FOREST("forest", "Лес", "Forest", false, { recipe(false, 150.0, 0.018, 150.0, 0.05, 150.0, 0.12) }, ::forest),
    OCEAN("ocean", "Океан", "Ocean", false, { recipe(false, 225.0, 0.02, 210.0, 0.05, 200.0, 0.12) }, { recipe(true, 225.0, 0.025, 210.0, 0.05, 200.0, 0.12) }),
    DESERT("desert", "Пустыня", "Desert", false, { recipe(false, 70.0, 0.025, 50.0, 0.05, 40.0, 0.13) }, { recipe(true, 60.0, 0.025, 50.0, 0.05, 45.0, 0.13) }),

    // The bold ones: tinted pages and a second colour against the first.
    SUNSET("sunset", "Закат", "Sunset", true, { recipe(false, 45.0, 0.045, 20.0, 0.08, 350.0, 0.17, 0.12) }, { recipe(true, 15.0, 0.045, 20.0, 0.08, 350.0, 0.17, 0.12) }),
    LAVA("lava", "Лава", "Lava", true, { recipe(false, 30.0, 0.04, 25.0, 0.09, 50.0, 0.19, 0.12) }, { recipe(true, 30.0, 0.05, 25.0, 0.09, 50.0, 0.19, 0.12) }),
    TROPIC("tropic", "Тропики", "Tropics", true, { recipe(false, 175.0, 0.04, 175.0, 0.08, 55.0, 0.18, 0.12) }, { recipe(true, 180.0, 0.045, 175.0, 0.08, 55.0, 0.18, 0.12) }),
    AURORA("aurora", "Аврора", "Aurora", true, { recipe(false, 190.0, 0.035, 300.0, 0.08, 150.0, 0.18, 0.12) }, { recipe(true, 200.0, 0.045, 300.0, 0.08, 150.0, 0.18, 0.12) }),
    CANDY("candy", "Леденец", "Candy", true, { recipe(false, 345.0, 0.045, 220.0, 0.08, 220.0, 0.15, 0.12) }, { recipe(true, 340.0, 0.045, 220.0, 0.08, 220.0, 0.15, 0.12) }),
    CYBER("cyber", "Кибер", "Cyber", true, { recipe(false, 330.0, 0.05, 200.0, 0.09, 195.0, 0.16, 0.13) }, { recipe(true, 320.0, 0.06, 200.0, 0.09, 195.0, 0.16, 0.13) }),
    NEON("neon", "Неон", "Neon", true, { recipe(false, 300.0, 0.03, 300.0, 0.09, 135.0, 0.22, 0.13) }, { recipe(true, 300.0, 0.04, 300.0, 0.09, 135.0, 0.22, 0.13) }),
    ACID("acid", "Кислота", "Acid", true, { recipe(false, 125.0, 0.05, 125.0, 0.1, 345.0, 0.2, 0.13) }, { recipe(true, 125.0, 0.05, 125.0, 0.1, 345.0, 0.2, 0.13) }),
    INDIGO("indigo", "Индиго", "Indigo", true, { recipe(false, 295.0, 0.06, 295.0, 0.1, 95.0, 0.17, 0.13) }, { recipe(true, 295.0, 0.07, 295.0, 0.1, 95.0, 0.17, 0.13) }),
    TERMINAL("terminal", "Терминал", "Terminal", true, { recipe(false, 95.0, 0.04, 145.0, 0.08, 145.0, 0.2, 0.12) }, { recipe(true, 145.0, 0.03, 145.0, 0.08, 145.0, 0.2, 0.12) }),
    ;

    val light: Tones by lazy(light)
    val dark: Tones by lazy(dark)

    val label: String get() = tr(ru, en)

    /** The half for the app's mode. */
    fun tones(dark: Boolean): Tones = if (dark) this.dark else light

    companion object {
        /** By its key; "clear-night", once a theme of its own, is Ясная's dark half now. */
        fun of(key: String?): Palette = if (key == "clear-night") CLEAR else entries.firstOrNull { it.key == key } ?: GOLDA
    }
}

private fun recipe(dark: Boolean, pageHue: Double, pageC: Double, pickedHue: Double, pickedC: Double, actionHue: Double, actionC: Double, infoC: Double = 0.10) =
    ThemeGen.recipe(dark, pageHue, pageC, pickedHue, pickedC, actionHue, actionC, infoC)

private fun lightSet(page: Long, card: Long, soft: Long, line: Long, muted: Long, text: Long, picked: Long, onPicked: Long, action: Long, onAction: Long, clear: Boolean = false) = Tones(
    dark = false, clearColours = clear,
    page = Color(page), card = Color(card), soft = Color(soft), line = Color(line), muted = Color(muted), text = Color(text),
    graphite = Color(picked), onGraphite = Color(onPicked), gold = Color(action), onGold = Color(onAction),
    error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF), errorSoft = Color(0xFFFEE2E2), onErrorSoft = Color(0xFF991B1B),
)

private fun darkSet(page: Long, card: Long, soft: Long, line: Long, muted: Long, text: Long, picked: Long, onPicked: Long, action: Long, onAction: Long, clear: Boolean = false) = Tones(
    dark = true, clearColours = clear,
    page = Color(page), card = Color(card), soft = Color(soft), line = Color(line), muted = Color(muted), text = Color(text),
    graphite = Color(picked), onGraphite = Color(onPicked), gold = Color(action), onGold = Color(onAction),
    error = Color(0xFFF87171), onError = Color(0xFF450A0A), errorSoft = Color(0xFF4C1D1D), onErrorSoft = Color(0xFFFECACA),
)

// Sand and turquoise, like a beach at noon.
private fun beach() = lightSet(0xFFF4EEE3, 0xFFFFFCF6, 0xFFE9E0D0, 0xFFDACFBB, 0xFF70614D, 0xFF3A3128, 0xFFBFE4DF, 0xFF103F3A, 0xFF5CC6BA, 0xFF06302B)

// Blossom pink on white.
private fun sakura() = lightSet(0xFFFBF1F3, 0xFFFFFFFF, 0xFFF2E0E5, 0xFFE7CAD2, 0xFF7D5B65, 0xFF3B2A30, 0xFFF5CDD9, 0xFF4A1E2C, 0xFFEC93AF, 0xFF3B0D1C)

// Fresh green and pale grey.
private fun mint() = lightSet(0xFFEEF5F0, 0xFFFFFFFF, 0xFFDDEAE1, 0xFFC7DCCE, 0xFF536A5B, 0xFF23302A, 0xFFC2E6CF, 0xFF123B23, 0xFF67C590, 0xFF08301A)

// Soft violet.
private fun lavender() = lightSet(0xFFF2F0FA, 0xFFFFFFFF, 0xFFE4E0F3, 0xFFD1CBE8, 0xFF615B7C, 0xFF2B2840, 0xFFD8CEF6, 0xFF2A1F5C, 0xFFA595EC, 0xFF1A1240)

// Deep night blue with a cool light.
private fun midnight() = darkSet(0xFF0F1724, 0xFF182235, 0xFF243149, 0xFF33425E, 0xFF8C9AB5, 0xFFE6ECF5, 0xFF2E4A75, 0xFFDCE8FF, 0xFF7FB2FF, 0xFF0A1B33)

// Coffee browns with a warm gold.
private fun espresso() = darkSet(0xFF1C1714, 0xFF26201B, 0xFF352C25, 0xFF463A31, 0xFFA8998A, 0xFFF3EBE2, 0xFF4A3B2F, 0xFFF6E6D3, 0xFFE2B655, 0xFF2B1D00)

// Pine green at dusk.
private fun forest() = darkSet(0xFF111A15, 0xFF19251E, 0xFF24352B, 0xFF2F4538, 0xFF8FA699, 0xFFE4EFE8, 0xFF2D4A3A, 0xFFD6F2E2, 0xFF7FD19B, 0xFF072615)

// Plain black and greys, the gold for the action.
private fun graphite() = darkSet(0xFF121212, 0xFF1E1E1E, 0xFF2C2C2C, 0xFF3A3A3A, 0xFFA0A0A0, 0xFFF2F2F2, 0xFF3D3D3D, 0xFFFFFFFF, 0xFFF2C14E, 0xFF2B1D00)

// For eyes that mix up green with yellow and blue with violet: neutral greys, a blue picked state,
// an orange action, and information colours that differ in lightness as well as hue.
private fun clear() = lightSet(0xFFF5F5F4, 0xFFFFFFFF, 0xFFE7E5E4, 0xFFD6D3D1, 0xFF57534E, 0xFF1C1917, 0xFFBFDBFE, 0xFF1E3A8A, 0xFFFDBA74, 0xFF431407, clear = true)

private fun clearNight() = darkSet(0xFF121212, 0xFF1E1E1E, 0xFF2C2C2C, 0xFF3A3A3A, 0xFFB0B0B0, 0xFFF5F5F5, 0xFF1E3A8A, 0xFFDBEAFE, 0xFFFDBA74, 0xFF431407, clear = true)
