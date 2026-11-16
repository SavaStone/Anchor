package app.opora.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationLabelTest {
    @Test
    fun iconOnlyNavigationStartsAt13() {
        assertTrue(useIconOnlyNavigation(1.3f))
        assertTrue(useIconOnlyNavigation(2.0f))
        assertFalse(useIconOnlyNavigation(1.29f))
    }
}
