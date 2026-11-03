package sh.aminov.golda.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** How loud a generated theme is. */
enum class ThemeStyle { CALM, VIVID, EXPRESSIVE, MONO }

/** Light, dark, or the system's choice. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * A generated theme: one hue and three choices. Stored as "custom:<hue>:<style>:<mode>:<contrast>"
 * in the palette setting, next to the presets' keys.
 */
data class ThemeSpec(val hue: Int, val style: ThemeStyle = ThemeStyle.CALM, val mode: ThemeMode = ThemeMode.SYSTEM, val contrast: Int = 1) {
    val key: String get() = "custom:$hue:${style.name}:${mode.name}:$contrast"

    companion object {
        fun parse(key: String?): ThemeSpec? {
            val parts = key?.split(':') ?: return null
            if (parts.size != 5 || parts[0] != "custom") return null
            return runCatching {
                ThemeSpec(parts[1].toInt().mod(360), ThemeStyle.valueOf(parts[2]), ThemeMode.valueOf(parts[3]), parts[4].toInt().coerceIn(0, 2))
            }.getOrNull()
        }

        /** A pleasant theme by chance: any hue, a style that is never flat. */
        fun random(random: Random = Random.Default) = ThemeSpec(
            hue = random.nextInt(360),
            style = listOf(ThemeStyle.CALM, ThemeStyle.VIVID, ThemeStyle.EXPRESSIVE).random(random),
        )

        /** The theme of a colour (the wallpaper's): its hue, calm. */
        fun of(color: Color) = ThemeSpec(hue = hueOf(color))
    }
}

/** The tones for a palette setting: a preset by key, or a generated theme. */
fun tonesFor(key: String?, systemDark: Boolean): Tones =
    ThemeSpec.parse(key)?.let { ThemeGen.tones(it, systemDark) } ?: Palette.of(key).tones(systemDark)

/**
 * Builds a full set of [Tones] from a [ThemeSpec] in OKLCH, where lightness and chroma mean the
 * same at every hue: the page, cards and lines a whisper of the hue, the picked state a pale tint,
 * the action the hue itself. Expressive turns the action a third of the way round the wheel.
 */
object ThemeGen {
    fun tones(spec: ThemeSpec, systemDark: Boolean): Tones {
        val dark = when (spec.mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val h = spec.hue.toDouble()
        val (neutral, picked, action, info) = when (spec.style) {
            ThemeStyle.CALM -> listOf(0.012, 0.045, 0.11, 0.09)
            ThemeStyle.VIVID -> listOf(0.022, 0.085, 0.16, 0.13)
            ThemeStyle.EXPRESSIVE -> listOf(0.018, 0.07, 0.15, 0.12)
            ThemeStyle.MONO -> listOf(0.0, 0.0, 0.0, 0.05)
        }
        val actionHue = if (spec.style == ThemeStyle.EXPRESSIVE) h + 120 else h
        val pickedHue = if (spec.style == ThemeStyle.EXPRESSIVE) h - 30 else h
        // Contrast moves the inks and the lines, never the surfaces.
        val c = spec.contrast
        return if (!dark) {
            Tones(
                dark = false,
                page = oklch(0.965, neutral, h), card = oklch(0.995, neutral * 0.4, h), soft = oklch(0.92, neutral, h),
                line = oklch(listOf(0.89, 0.87, 0.80)[c], neutral, h),
                muted = oklch(listOf(0.48, 0.46, 0.40)[c], neutral * 1.5, h), text = oklch(listOf(0.32, 0.25, 0.15)[c], neutral * 1.5, h),
                graphite = oklch(0.86, picked, pickedHue), onGraphite = oklch(0.25, picked * 0.8, pickedHue),
                gold = if (spec.style == ThemeStyle.MONO) oklch(0.30, 0.0, h) else oklch(0.80, action, actionHue),
                onGold = if (spec.style == ThemeStyle.MONO) Color.White else oklch(0.25, action * 0.5, actionHue),
                error = Color(0xFFDC2626), onError = Color(0xFFFFFFFF), errorSoft = Color(0xFFFEE2E2), onErrorSoft = Color(0xFF991B1B),
                infoL = 0.80f, infoC = info.toFloat(), seedHue = spec.hue,
            )
        } else {
            Tones(
                dark = true,
                page = oklch(0.19, neutral, h), card = oklch(0.24, neutral, h), soft = oklch(0.30, neutral, h),
                line = oklch(listOf(0.33, 0.36, 0.45)[c], neutral, h),
                muted = oklch(listOf(0.66, 0.72, 0.82)[c], neutral * 1.5, h), text = oklch(listOf(0.88, 0.95, 0.99)[c], neutral, h),
                graphite = oklch(0.38, picked, pickedHue), onGraphite = oklch(0.94, picked * 0.5, pickedHue),
                gold = if (spec.style == ThemeStyle.MONO) oklch(0.92, 0.0, h) else oklch(0.75, action, actionHue),
                onGold = if (spec.style == ThemeStyle.MONO) Color.Black else oklch(0.20, action * 0.5, actionHue),
                error = Color(0xFFF87171), onError = Color(0xFF450A0A), errorSoft = Color(0xFF4C1D1D), onErrorSoft = Color(0xFFFECACA),
                infoL = 0.68f, infoC = info.toFloat(), seedHue = spec.hue,
            )
        }
    }
}

/** The tones the app is drawn in now, for colours worked out from them (categories, the pace bar). */
val LocalTones = staticCompositionLocalOf { GoldaLight }

/** Each category's hue on the OKLCH wheel: Еда stays green whatever the theme, only its depth changes. */
private val categoryHues = mapOf(
    "food" to 142.0, "transport" to 315.0, "household" to 185.0, "treats" to 355.0,
    "housing" to 255.0, "telecom" to 275.0, "subscriptions" to 300.0, "loans" to 25.0,
    "shopping" to 50.0, "travel" to 220.0, "health" to 160.0,
)

/** A category's colour in [tones]: its own hue at the theme's lightness and chroma; others grey. */
fun categoryColor(key: String?, tones: Tones): Color {
    val hue = categoryHues[key] ?: return oklch(tones.infoL.toDouble(), 0.012, 90.0)
    return oklch(tones.infoL.toDouble(), tones.infoC.toDouble(), hue)
}

/** A category's colour in the current theme. */
@Composable
@ReadOnlyComposable
fun categoryColor(key: String?): Color = categoryColor(key, LocalTones.current)

/**
 * Steps round the wheel from the theme's hue for the second, third... biggest thing: a near
 * complement first for contrast, then neighbours on either side, so the set stays one family.
 */
private val harmony = listOf(0.0, 150.0, 55.0, -55.0, 210.0, 25.0, -110.0, 95.0, -25.0, 180.0, 120.0, -150.0)

/**
 * The colour of the [rank]-th biggest thing in [tones]: the biggest in the theme's own hue, a touch
 * deeper, the others round it by [harmony]. A null rank (no category) is the theme's grey.
 */
fun leitmotifColor(rank: Int?, tones: Tones): Color {
    if (rank == null) return oklch(tones.infoL.toDouble(), 0.012, tones.seedHue.toDouble())
    if (tones.clearColours) return clearColour(rank, tones.dark)
    val chroma = tones.infoC.toDouble() * if (rank == 0) 1.25 else 1.0
    // Neighbours differ in lightness too, not hue alone: colours that read as one hue to some eyes
    // (blue and violet, green and yellow) still come apart.
    val ladder = listOf(-0.04, -0.10, 0.05, -0.16, 0.05)[rank % 5]
    return oklch((tones.infoL + ladder).toDouble(), chroma.coerceAtLeast(0.06), tones.seedHue + harmony[rank % harmony.size])
}

/** How the money is doing, on the home bar: the theme's own colour while it lasts, red once overspent. */
enum class Pace { LASTS, SHORT, OVER }

@Composable
@ReadOnlyComposable
fun paceColor(pace: Pace): Color {
    val t = LocalTones.current
    return if (pace == Pace.OVER) oklch(t.infoL - 0.06, (t.infoC * 1.15).coerceAtLeast(0.10), 28.0) else leitmotifColor(0, t)
}

/**
 * OKLCH to sRGB. Lightness 0..1, chroma about 0..0.37, hue in degrees. A colour outside sRGB loses
 * chroma until it fits, so the hue and the lightness stay what was asked.
 */
fun oklch(l: Double, c: Double, hueDeg: Double): Color {
    var chroma = c
    repeat(24) {
        val rgb = oklabToLinear(l, chroma * cos(Math.toRadians(hueDeg)), chroma * sin(Math.toRadians(hueDeg)))
        if (rgb.all { it in -0.0005..1.0005 } || chroma <= 0.0) {
            val (r, g, b) = rgb.map { encode(it.coerceIn(0.0, 1.0)) }
            return Color(r.toFloat(), g.toFloat(), b.toFloat())
        }
        chroma *= 0.9
    }
    val (r, g, b) = oklabToLinear(l, 0.0, 0.0).map { encode(it.coerceIn(0.0, 1.0)) }
    return Color(r.toFloat(), g.toFloat(), b.toFloat())
}

private fun oklch(l: Float, c: Double, hueDeg: Double) = oklch(l.toDouble(), c, hueDeg)

/** The OKLCH hue of a colour, in whole degrees. */
fun hueOf(color: Color): Int {
    val argb = color.toArgb()
    val (r, g, b) = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF).map { decode(it / 255.0) }
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
    val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    return Math.toDegrees(atan2(bb, a)).let { ((it % 360) + 360) % 360 }.toInt()
}

private fun oklabToLinear(l: Double, a: Double, b: Double): List<Double> {
    val l1 = (l + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m1 = (l - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s1 = (l - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return listOf(
        4.0767416621 * l1 - 3.3077115913 * m1 + 0.2309699292 * s1,
        -1.2684380046 * l1 + 2.6097574011 * m1 - 0.3413193965 * s1,
        -0.0041960863 * l1 - 0.7034186147 * m1 + 1.7076147010 * s1,
    )
}

private fun encode(x: Double) = if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
private fun decode(x: Double) = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)

/**
 * Colours for eyes that mix up green with yellow and blue with violet: orange, sky blue, pink,
 * deep teal, sand, navy... Neighbours in the list differ in lightness too, so even two that read as
 * one hue are told apart by how light they are.
 */
private val clearSet = listOf(
    Triple(0.72, 0.16, 55.0), Triple(0.60, 0.13, 245.0), Triple(0.82, 0.10, 355.0), Triple(0.50, 0.09, 195.0),
    Triple(0.88, 0.07, 80.0), Triple(0.42, 0.12, 265.0), Triple(0.68, 0.14, 20.0), Triple(0.78, 0.05, 220.0),
)

/** The light theme's pale ones made deeper so they hold on white; the dark theme's deep ones lighter. */
private val clearDay = mapOf(2 to Triple(0.74, 0.12, 355.0), 4 to Triple(0.80, 0.11, 80.0), 6 to Triple(0.64, 0.15, 20.0), 7 to Triple(0.70, 0.06, 220.0))
private val clearNight = mapOf(1 to Triple(0.62, 0.13, 245.0), 3 to Triple(0.56, 0.09, 195.0), 5 to Triple(0.50, 0.13, 275.0))

private fun clearColour(rank: Int, dark: Boolean): Color {
    val i = rank % clearSet.size
    val (l, c, h) = (if (dark) clearNight[i] else clearDay[i]) ?: clearSet[i]
    return oklch(l, c, h)
}
