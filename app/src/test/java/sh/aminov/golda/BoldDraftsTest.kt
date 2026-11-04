package sh.aminov.golda

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.AnalyticsScreen
import sh.aminov.golda.ui.GoldaTheme
import sh.aminov.golda.ui.HomeScreen
import sh.aminov.golda.ui.ThemeScreen
import sh.aminov.golda.ui.Tones
import java.util.TimeZone

/**
 * Drafts of the bold themes after Bungie's Marathon, third round: white or near-black pages, neutral
 * lists, one plate of colour per tab (the hero), and on it the pace bar, the picked state and the
 * action in the colour opposite the plate's. Temporary: the chosen ones move into Palette and this
 * file goes. See docs/tasks/bold-themes.md.
 */
internal object BoldDrafts {
    class Draft(val key: String, val light: Tones, val dark: Tones) {
        /** The dark half on pure black, as the OLED switch draws it. */
        val oled: Tones get() = dark.onPage(Color.Black)
    }

    /** One half: the page, lists and inks neutral; [c] the plate, its two inks, the pace, then picked and action with their inks. */
    private fun set(dark: Boolean, vararg c: Long) = Tones(
        dark = dark,
        page = if (dark) Color(0xFF121212) else Color.White, card = if (dark) Color(0xFF1E1E1E) else Color(0xFFF3F3F3),
        soft = if (dark) Color(0xFF2C2C2C) else Color(0xFFE6E6E6), line = if (dark) Color(0xFF3A3A3A) else Color(0xFFD4D4D4),
        muted = if (dark) Color(0xFFB0B0B0) else Color(0xFF555555), text = if (dark) Color.White else Color(0xFF0A0A0A),
        graphite = Color(c[4]), onGraphite = Color(c[5]), gold = Color(c[6]), onGold = Color(c[7]),
        error = if (dark) Color(0xFFF87171) else Color(0xFFDC2626), onError = if (dark) Color(0xFF450A0A) else Color.White,
        errorSoft = if (dark) Color(0xFF4C1D1D) else Color(0xFFFEE2E2), onErrorSoft = if (dark) Color(0xFFFECACA) else Color(0xFF991B1B),
        infoL = if (dark) 0.70f else 0.70f, infoC = 0.17f,
        plate = Color(c[0]), onPlate = Color(c[1]), onPlateMuted = Color(c[2]), pace = Color(c[3]),
    )

    // plate, on plate, on plate quiet, pace, picked, on picked, action, on action
    val all = listOf(
        // Acid lime plate with an ultramarine bar; at night the other way round.
        Draft("marathon",
            set(false, 0xFFDFFF1A, 0xFF0A0A0A, 0xFF3D4500, 0xFF2233FF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF2233FF, 0xFFFFFFFF),
            set(true, 0xFF2233FF, 0xFFFFFFFF, 0xFFD6DBFF, 0xFFDFFF1A, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF0A0A0A)),
        // Ultramarine with orange against it.
        Draft("ultramarine",
            set(false, 0xFF2233FF, 0xFFFFFFFF, 0xFFD6DBFF, 0xFFFF9040, 0xFF2233FF, 0xFFFFFFFF, 0xFFFF4A10, 0xFF0A0A0A),
            set(true, 0xFF1A2BD0, 0xFFFFFFFF, 0xFFD6DBFF, 0xFFFF9040, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF5A1F, 0xFF0A0A0A)),
        // Red-orange, the ref's 0241 plate, with deep blue by day and lime by night.
        Draft("signal",
            set(false, 0xFFFF6E2E, 0xFF0A0A0A, 0xFF3A1000, 0xFF1426B8, 0xFF2233FF, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFFF6E2E),
            set(true, 0xFFA81800, 0xFFFFFFFF, 0xFFFFD0C0, 0xFFDFFF1A, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF5A1F, 0xFF0A0A0A)),
        // Magenta with deep blue by day, lime by night.
        Draft("pixel",
            set(false, 0xFFFF6ACB, 0xFF0A0A0A, 0xFF5A0040, 0xFF1426B8, 0xFF2233FF, 0xFFFFFFFF, 0xFFDFFF1A, 0xFF0A0A0A),
            set(true, 0xFF8A0068, 0xFFFFFFFF, 0xFFFFC8EE, 0xFFDFFF1A, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFFF3DBE, 0xFF0A0A0A)),
        // Cyan with hot pink.
        Draft("hologram",
            set(false, 0xFF3DE8FF, 0xFF0A0A0A, 0xFF00434D, 0xFFE0007A, 0xFF0A0A0A, 0xFF3DE8FF, 0xFFFF2EA6, 0xFF0A0A0A),
            set(true, 0xFF005A66, 0xFFFFFFFF, 0xFFA8EEF7, 0xFFFF6EC0, 0xFFFF2EA6, 0xFF0A0A0A, 0xFF2EE6FF, 0xFF0A0A0A)),
        // Phosphor green with red by day, magenta by night.
        Draft("terminal",
            set(false, 0xFF7CFF4F, 0xFF0A0A0A, 0xFF164A00, 0xFFC8102E, 0xFF0A0A0A, 0xFF7CFF4F, 0xFFFF4A10, 0xFF0A0A0A),
            set(true, 0xFF0F4A14, 0xFFFFFFFF, 0xFF9BE88F, 0xFFFF4FD8, 0xFF39FF14, 0xFF000000, 0xFF39FF14, 0xFF000000)),
        // Electric violet with yellow.
        Draft("violet",
            set(false, 0xFF5B14FF, 0xFFFFFFFF, 0xFFDCD0FF, 0xFFFFE500, 0xFF0A0A0A, 0xFFFFE500, 0xFFFFE500, 0xFF0A0A0A),
            set(true, 0xFF3A1290, 0xFFFFFFFF, 0xFFD8C6FF, 0xFFFFE500, 0xFFFFFFFF, 0xFF2A0A6B, 0xFFFFE500, 0xFF0A0A0A)),
        // A black plate on white with lime; at night a white plate on black with red-orange.
        Draft("plate",
            set(false, 0xFF0A0A0A, 0xFFFFFFFF, 0xFFBDBDBD, 0xFFDFFF1A, 0xFF0A0A0A, 0xFFDFFF1A, 0xFFFF4A10, 0xFF0A0A0A),
            set(true, 0xFFF2F2F2, 0xFF0A0A0A, 0xFF4A4A4A, 0xFFE03000, 0xFFFFFFFF, 0xFF0A0A0A, 0xFFDFFF1A, 0xFF0A0A0A)),
    )

    /** The ones shown before and after a pure black page. */
    val oledKeys = listOf("marathon", "ultramarine", "pixel")
}

class BoldDraftsContrastTest {
    /** The presets' bar (ThemeGenTest) on the page and the lists, the same on the plate, and a bar that stands out on it. */
    @Test
    fun everyDraftIsReadable() {
        BoldDrafts.all.forEach { d ->
            listOf(d.light, d.dark, d.oled).forEach { t ->
                val name = "${d.key} ${if (t.dark) "dark" else "light"} ${t.page}"
                assertTrue("$name text/page", ratio(t.text, t.page) >= 7.0)
                assertTrue("$name text/card", ratio(t.text, t.card) >= 7.0)
                assertTrue("$name muted/card", ratio(t.muted, t.card) >= 4.5)
                assertTrue("$name muted/page", ratio(t.muted, t.page) >= 4.5)
                assertTrue("$name onPlate", ratio(t.onPlate, t.plate) >= 7.0)
                assertTrue("$name onPlateMuted", ratio(t.onPlateMuted, t.plate) >= 4.5)
                // WCAG's 3:1 for a mark that carries meaning: the pace bar on its plate.
                assertTrue("$name pace/plate", ratio(t.pace!!, t.plate) >= 3.0)
                assertTrue("$name onPicked", ratio(t.onGraphite, t.graphite) >= 4.5)
                assertTrue("$name onAction", ratio(t.onGold, t.gold) >= 4.5)
                // isDark() reads the surface's luminance: a light half must stay above the half.
                assertTrue("$name page luminance", (t.page.luminance() >= 0.5f) != t.dark)
            }
        }
    }

    private fun ratio(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}

/** Home and Аналитика in every draft, both halves, and the Theme page's OLED switch: recordRoborazziDebug with --tests "*BoldDraftsShots*". */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BoldDraftsShots {
    @get:Rule val compose = createComposeRule()

    @Before
    fun fixTheWorld() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        I18n.russian = true
    }

    /** One composition for every frame: compose takes setContent only once a test. */
    @Test
    fun everyDraft() {
        val data = ScreenshotTest().demo()
        var tones by mutableStateOf(BoldDrafts.all[0].light)
        var home by mutableStateOf(true)
        compose.setContent {
            GoldaTheme(tones) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    if (home) HomeScreen(data, PaddingValues(), rememberLazyListState(), {}, {}, onEdit = {})
                    else AnalyticsScreen(data, PaddingValues()) {}
                }
            }
        }
        fun capture(t: Tones, h: Boolean, name: String) {
            tones = t
            home = h
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/screenshots/bold/$name.png")
        }
        listOf(true, false).forEach { h ->
            val screen = if (h) "home" else "insights"
            BoldDrafts.all.forEach { d ->
                capture(d.light, h, "${d.key}_light_$screen")
                capture(d.dark, h, "${d.key}_dark_$screen")
            }
            BoldDrafts.all.filter { it.key in BoldDrafts.oledKeys }.forEach { d -> capture(d.oled, h, "${d.key}_oled_$screen") }
        }
    }

    /** The Theme page in Марафон's dark half, the switch off and on. */
    @Test
    fun oledSwitch() {
        val marathon = BoldDrafts.all.first { it.key == "marathon" }
        var oled by mutableStateOf(false)
        compose.setContent {
            GoldaTheme(if (oled) marathon.oled else marathon.dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    ThemeScreen("golda", "DARK", emptyList(), {}, {}, {}, {}, {}, oled = oled, onOled = { oled = it })
                }
            }
        }
        listOf(false, true).forEach { on ->
            oled = on
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("src/test/screenshots/bold/theme_oled_${if (on) "on" else "off"}.png")
        }
    }
}
