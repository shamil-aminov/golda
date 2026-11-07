package sh.aminov.golda

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.ui.BarMotion
import sh.aminov.golda.ui.effectiveBarMotion

class BarMotionTest {
    @Test fun oldSettingsDefaultToQuietAndRemainder() {
        val settings = Json.decodeFromString<Settings>("{}")
        assertEquals("QUIET", settings.barMotion)
        assertEquals(false, settings.barDifferenceEmphasis)
        assertEquals(settings, Json.decodeFromString<Settings>(Json.encodeToString(settings)))
    }

    @Test fun shimmerNeedsApi33() {
        for (api in 26..32) assertEquals(BarMotion.QUIET, effectiveBarMotion("SHIMMER", true, api, 1f))
        for (api in 33..37) assertEquals(BarMotion.SHIMMER, effectiveBarMotion("SHIMMER", true, api, 1f))
    }

    @Test fun selectedExperimentsSurviveBackupSerialization() {
        val settings = Settings(barMotion = "SHIMMER", barDifferenceEmphasis = true)
        val restored = Json.decodeFromString<Settings>(Json.encodeToString(settings))
        assertEquals("SHIMMER", restored.barMotion)
        assertEquals(true, restored.barDifferenceEmphasis)
    }

    @Test fun waveWorksOnEverySupportedApi() {
        for (api in 26..37) assertEquals(BarMotion.WAVE, effectiveBarMotion("WAVE", true, api, 1f))
    }

    @Test fun disabledAnimationsAndProductionAreQuiet() {
        for (motion in BarMotion.entries) {
            assertEquals(BarMotion.QUIET, effectiveBarMotion(motion.name, true, 36, 0f))
            assertEquals(BarMotion.QUIET, effectiveBarMotion(motion.name, false, 36, 1f))
        }
    }

    @Test fun unknownStyleIsQuietAndNonzeroScaleAllowsMotion() {
        assertEquals(BarMotion.QUIET, effectiveBarMotion("future", true, 36, 1f))
        for (scale in listOf(0.5f, 1f, 2f)) {
            assertEquals(BarMotion.WAVE, effectiveBarMotion("WAVE", true, 36, scale))
            assertEquals(BarMotion.SHIMMER, effectiveBarMotion("SHIMMER", true, 36, scale))
        }
    }
}
