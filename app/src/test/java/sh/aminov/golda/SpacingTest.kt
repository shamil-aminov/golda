package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import sh.aminov.golda.domain.Settings
import sh.aminov.golda.ui.Spacing

class SpacingTest {
    @Test
    fun aNewInstallIsNormal() = assertEquals(Spacing.NORMAL, Spacing.of(Settings().spacing))

    @Test
    fun noneOrAnUnknownNameIsNormal() {
        assertEquals(Spacing.NORMAL, Spacing.of(null))
        assertEquals(Spacing.NORMAL, Spacing.of("HUGE"))
    }

    @Test
    fun everyNameComesBack() = Spacing.entries.forEach { assertEquals(it, Spacing.of(it.name)) }

    @Test
    fun eachStepHasMoreAirThanTheOneBefore() = Spacing.entries.zipWithNext().forEach { (a, b) ->
        assertTrue("$a → $b", b.factor > a.factor)
    }
}
