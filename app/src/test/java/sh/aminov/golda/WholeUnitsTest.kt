package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Test
import sh.aminov.golda.domain.Fmt

class WholeUnitsTest {
    @Test
    fun roundsHalfUpAndNeverShowsMinusZero() {
        assertEquals("20", Fmt.whole(2_043, "GEL"))
        assertEquals("21", Fmt.whole(2_050, "GEL"))
        assertEquals("0", Fmt.whole(-30, "GEL"))
        assertEquals("−1", Fmt.whole(-50, "GEL").replace('-', '−'))
        assertEquals("199 226", Fmt.whole(19_922_590, "RUB").replace(' ', ' ').replace(' ', ' '))
    }
}
