package app.opora.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import app.opora.R
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Rule
import org.junit.Test

class LoadingStateTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun loadingStateIsAnnouncedAsProgress() {
        compose.setContent { OporaTheme { LoadingState() } }

        compose.onNodeWithContentDescription(TestStrings.get(R.string.common_loading))
            .assertExists()
            .assert(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
            )
    }
}
