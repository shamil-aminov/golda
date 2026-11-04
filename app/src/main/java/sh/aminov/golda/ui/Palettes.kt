package sh.aminov.golda.ui

import androidx.compose.ui.graphics.Color
import sh.aminov.golda.domain.tr

/**
 * The colour themes to pick from in Settings, each light and dark: the app's one mode setting (or the
 * phone's) decides which half is drawn. The calm ones first, then the bold ones: bold in colour, never
 * in readability, every half keeps its inks as legible as the calm ones (see ThemeGenTest). The
 * calm hand-drawn halves are the first themes'; their other halves come from [ThemeGen.recipe] in the
 * same hues. The bold ones are drawn by hand, both halves ([boldSet]).
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

    // The bold ones, after Bungie's Marathon: a white or near-black page and quiet lists. Two colours carry
    // each: the first on the mic (the action), the second on the plate (the hero tiles), as far apart as the
    // theme allows; the pace bar stands out on the plate, the picked state stays black or white.
    // The game's own colours: the lime mic, a magenta plate (deep enough to carry white words), cyan for the bar.
    MARATHON("marathon", "Марафон", "Marathon", true,
        { boldSet(false, 0xFFA8006A, 0xFFFFFFFF, 0xFFFFC0E4, 0xFF01FFFF, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFC2FE0C, 0xFF0A0A0A, swatches = marathonRest) },
        {
            boldSet(
                true, 0xFFA8006A, 0xFFFFFFFF, 0xFFFFC0E4, 0xFF01FFFF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFC2FE0C, 0xFF0A0A0A,
                page = 0xFF07090F, card = 0xFF29324F, soft = 0xFF3A4566, line = 0xFF4A5578, swatches = marathonRest,
            )
        }),
    ULTRAMARINE("ultramarine", "Ультрамарин", "Ultramarine", true,
        { boldSet(false, 0xFF2233FF, 0xFFFFFFFF, 0xFFD6DBFF, 0xFFFF9040, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFFF4A10, 0xFF0A0A0A) },
        { boldSet(true, 0xFF1A2BD0, 0xFFFFFFFF, 0xFFD6DBFF, 0xFFFF9040, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFF5A1F, 0xFF0A0A0A) }),
    SIGNAL("signal", "Сигнал", "Signal", true,
        { boldSet(false, 0xFFFF6E2E, 0xFF0A0A0A, 0xFF3A1000, 0xFF1426B8, 0xFF0A0A0A, 0xFFFFFFFF, 0xFF2233FF, 0xFFFFFFFF) },
        { boldSet(true, 0xFFA81800, 0xFFFFFFFF, 0xFFFFD0C0, 0xFFDFFF1A, 0xFFFFFFFF, 0xFF0A0A0A, 0xFF2233FF, 0xFFFFFFFF) }),
    PIXEL("pixel", "Пиксель", "Pixel", true,
        { boldSet(false, 0xFFFF6ACB, 0xFF0A0A0A, 0xFF5A0040, 0xFF1426B8, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFDFFF1A, 0xFF0A0A0A) },
        { boldSet(true, 0xFF8A0068, 0xFFFFFFFF, 0xFFFFC8EE, 0xFFDFFF1A, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF0A0A0A) }),
    HOLOGRAM("hologram", "Голограмма", "Hologram", true,
        { boldSet(false, 0xFF01FFFF, 0xFF0A0A0A, 0xFF00434D, 0xFFEA027E, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFEA027E, 0xFF0A0A0A) },
        { boldSet(true, 0xFF005A66, 0xFFFFFFFF, 0xFFA8EEF7, 0xFFFF6EC0, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFF2EA6, 0xFF0A0A0A) }),
    TERMINAL("terminal", "Терминал", "Terminal", true,
        { boldSet(false, 0xFF7CFF4F, 0xFF0A0A0A, 0xFF164A00, 0xFFC8102E, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFFF4A10, 0xFF0A0A0A) },
        { boldSet(true, 0xFF0F4A14, 0xFFFFFFFF, 0xFF9BE88F, 0xFF39FF14, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFF4FD8, 0xFF0A0A0A) }),
    VIOLET("violet", "Фиолет", "Violet", true,
        { boldSet(false, 0xFF5B14FF, 0xFFFFFFFF, 0xFFDCD0FF, 0xFF01FFFF, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFFFE500, 0xFF0A0A0A) },
        { boldSet(true, 0xFF3A1290, 0xFFFFFFFF, 0xFFD8C6FF, 0xFF01FFFF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFFE500, 0xFF0A0A0A) }),
    PLATE("plate", "Плашка", "Plate", true,
        { boldSet(false, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFBDBDBD, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFFF4A10, 0xFF0A0A0A) },
        { boldSet(true, 0xFFF2F2F2, 0xFF0A0A0A, 0xFF4A4A4A, 0xFF3601FB, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFF5500, 0xFF0A0A0A) }),
    ;

    val light: Tones by lazy(light)
    val dark: Tones by lazy(dark)

    val label: String get() = tr(ru, en)

    /** The half for the app's mode. */
    fun tones(dark: Boolean): Tones = if (dark) this.dark else light

    companion object {
        /** The bold themes before 0.20, each on the new one nearest in colour, so a pick never falls back to Golda. */
        private val retired = mapOf(
            "clear-night" to "clear",
            "sunset" to "signal", "lava" to "signal", "tropic" to "hologram", "aurora" to "violet", "candy" to "pixel",
            "cyber" to "pixel", "neon" to "violet", "acid" to "marathon", "indigo" to "ultramarine",
        )

        /** By its key; "clear-night", once a theme of its own, is Ясная's dark half now, and a retired bold one its heir. */
        fun of(key: String?): Palette = (retired[key] ?: key).let { k -> entries.firstOrNull { it.key == k } ?: GOLDA }
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

/**
 * A bold half: the page white or near black and the lists a step off it, every ink neutral; the colour
 * on the [plate] (the hero tiles) with its own inks, the [pace] bar against it, and the picked state
 * and the action.
 */
private fun boldSet(
    dark: Boolean, plate: Long, onPlate: Long, onPlateMuted: Long, pace: Long,
    picked: Long, onPicked: Long, action: Long, onAction: Long,
    page: Long = if (dark) 0xFF121212 else 0xFFFFFFFF, card: Long = if (dark) 0xFF1E1E1E else 0xFFF3F3F3,
    soft: Long = if (dark) 0xFF2C2C2C else 0xFFE6E6E6, line: Long = if (dark) 0xFF3A3A3A else 0xFFD4D4D4,
    swatches: List<Color>? = null,
) = Tones(
    dark = dark,
    page = Color(page), card = Color(card), soft = Color(soft), line = Color(line),
    muted = Color(if (dark) 0xFFB0B0B0 else 0xFF555555), text = Color(if (dark) 0xFFFFFFFF else 0xFF0A0A0A),
    graphite = Color(picked), onGraphite = Color(onPicked), gold = Color(action), onGold = Color(onAction),
    error = Color(if (dark) 0xFFF87171 else 0xFFDC2626), onError = Color(if (dark) 0xFF450A0A else 0xFFFFFFFF),
    errorSoft = Color(if (dark) 0xFF4C1D1D else 0xFFFEE2E2), onErrorSoft = Color(if (dark) 0xFFFECACA else 0xFF991B1B),
    infoL = 0.70f, infoC = 0.17f,
    plate = Color(plate), onPlate = Color(onPlate), onPlateMuted = Color(onPlateMuted), pace = Color(pace), swatches = swatches,
)

/** Marathon's other colours for the categories, quieter than the mic and the plate: ultramarine, orange, cyan, in turn. */
private val marathonRest = listOf(0xFF3601FB, 0xFFFF5500, 0xFF01FFFF).map { Color(it) }
