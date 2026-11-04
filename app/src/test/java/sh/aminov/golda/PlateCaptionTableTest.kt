package sh.aminov.golda

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Test
import sh.aminov.golda.ui.Palette
import sh.aminov.golda.ui.contrast
import sh.aminov.golda.ui.oklch
import java.io.File
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.hypot
import kotlin.math.pow

/**
 * TEMPORARY (task `docs/tasks/home-type-spacing.md`): every palette's plate, its second ink (onPlateMuted)
 * and their contrast, with a same-hue tone at 4.5:1 where it falls short or reads grey on a coloured plate,
 * written to `docs/tasks/home-type-spacing/plate-captions.md`. Delete once the inks are settled.
 */
class PlateCaptionTableTest {
    private fun lch(c: Color): Triple<Double, Double, Double> {
        val argb = c.toArgb()
        fun dec(x: Double) = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
        val (r, g, b) = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF).map { dec(it / 255.0) }
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val ll = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        return Triple(ll, hypot(a, bb), Math.toDegrees(atan2(bb, a)).let { ((it % 360) + 360) % 360 })
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    /** A tone of the plate's own hue, from the plate's lightness towards its first ink, at 4.5:1 and a little more. */
    private fun suggest(plate: Color, onPlate: Color): Color {
        val (pl, pc, ph) = lch(plate)
        val (ol, _, _) = lch(onPlate)
        val chroma = (pc * 0.55).coerceIn(0.03, 0.14)
        var best = onPlate
        for (i in 0..200) {
            val l = pl + (ol - pl) * i / 200.0
            val c = oklch(l.coerceIn(0.0, 1.0), chroma, ph)
            if (contrast(c, plate) >= 4.6) { best = c; break }
        }
        return best
    }

    @Test
    fun writeTheTable() {
        val lines = mutableListOf(
            "| Палитра | Половина | Плашка | Второй цвет сейчас | Контраст | Оттенок плашки / цвета | Предложение | Контраст |",
            "|---|---|---|---|---|---|---|---|",
        )
        Palette.entries.forEach { p ->
            listOf(false, true).forEach { dark ->
                val t = p.tones(dark)
                val k = contrast(t.onPlateMuted, t.plate)
                val (_, pc, ph) = lch(t.plate)
                val (_, mc, mh) = lch(t.onPlateMuted)
                // Grey on a coloured plate: the plate has colour, the second ink hardly any.
                val grey = pc > 0.06 && mc < 0.025
                val bad = k < 4.5 || grey
                val s = if (bad) suggest(t.plate, t.onPlate) else null
                lines += "| ${p.key}${if (p.bold) " (bold)" else ""} | ${if (dark) "тёмная" else "светлая"} | ${hex(t.plate)} | ${hex(t.onPlateMuted)} | " +
                    "%.1f%s | %.0f° / %.0f°%s | ${s?.let { hex(it) } ?: "—"} | ${s?.let { "%.1f".format(contrast(it, t.plate)) } ?: "—"} |"
                        .format(k, if (k < 4.5) " ⚠" else "", ph, mh, if (grey) " серый" else "")
            }
        }
        File("../docs/tasks/home-type-spacing/plate-captions.md").writeText(
            "# Второй цвет плашки по палитрам\n\nПодпись на плашке — 14, база, второй цвет (`onPlateMuted` → `onPrimaryFixedVariant`). " +
                "Нужно ≥ 4.5:1 к плашке и тот же оттенок. ⚠ — ниже 4.5; «серый» — плашка цветная, а второй цвет почти без цвета. " +
                "Предложение — тон оттенка плашки на пути от неё к первому цвету, первый с контрастом ≥ 4.6.\n\n" + lines.joinToString("\n") + "\n",
        )
    }
}
