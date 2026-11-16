package app.opora.ui.screens

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MedsListStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tabScrollPositionSurvivesStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var state: LazyListState? = null

        restoration.setContent {
            OporaTheme {
                val listState = rememberMedsListState(tab = 0)
                state = listState
                LazyColumn(state = listState, modifier = Modifier.testTag("list")) {
                    items((0 until 100).toList()) { index -> Text("item $index") }
                }
            }
        }

        compose.onNodeWithTag("list").performScrollToIndex(50)
        assertEquals(50, state?.firstVisibleItemIndex)

        restoration.emulateSavedInstanceStateRestore()

        assertEquals(50, state?.firstVisibleItemIndex)
    }
}
