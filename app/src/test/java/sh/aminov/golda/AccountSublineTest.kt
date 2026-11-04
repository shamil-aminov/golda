package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import sh.aminov.golda.domain.I18n
import sh.aminov.golda.ui.accountSubline

@RunWith(RobolectricTestRunner::class)
class AccountSublineTest {
    private val data by lazy { I18n.russian = true; ScreenshotTest().demo() }
    private fun line(name: String) = accountSubline(data, data.states.values.first { it.account.name == name })

    @Test
    fun everyAccountHasASecondLine() = data.states.values.forEach { assertTrue(it.account.name, accountSubline(data, it).isNotEmpty()) }

    @Test
    fun aDebtCarriesItsRate() {
        assertEquals("29,9 %", line("Кредитка"))
        assertEquals("19,9 %", line("Кредит"))
    }

    @Test
    fun anAccountWithNothingElseToSayShowsItsType() = assertEquals("Карта", line("Карта ₽"))

    @Test
    fun aForeignAccountKeepsItsEquivalent() = assertTrue(line("Мультивалютная USD").startsWith("Карта · ≈ "))
}
