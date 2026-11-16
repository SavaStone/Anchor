package app.opora.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.opora.R
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Rule
import org.junit.Test

class SectionHeaderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sectionHeaderIsReportedAsHeading() {
        compose.setContent { OporaTheme { SectionHeader(TestStrings.get(R.string.mood_emotions_title)) } }

        compose.onNodeWithText(TestStrings.get(R.string.mood_emotions_title))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
