package sh.aminov.golda

import org.junit.Assert.assertEquals
import org.junit.Test

/** Every screen once, whatever the order: the pager and the toolbar index into this list. */
class TabOrderTest {
    @Test
    fun everyTabStandsOnceInTheOrder() {
        assertEquals(Tab.entries.toSet(), TabOrder.toSet())
        assertEquals(Tab.entries.size, TabOrder.size)
    }
}
