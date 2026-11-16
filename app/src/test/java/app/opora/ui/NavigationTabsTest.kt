package app.opora.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTabsTest {
    @Test
    fun hiddenTabStaysVisibleWhileOpenedByDeepLink() {
        val hidden = setOf("mood")
        assertFalse(tabVisible("mood", hidden, "today"))
        assertTrue(tabVisible("mood", hidden, "mood"))
    }

    @Test
    fun todayAndMoreAreAlwaysVisible() {
        val hidden = setOf("today", "more")
        assertTrue(tabVisible("today", hidden, "meds"))
        assertTrue(tabVisible("more", hidden, "meds"))
    }

    @Test
    fun visibleTabNeedsNoException() {
        assertTrue(tabVisible("meds", setOf("mood"), "today"))
    }
}
