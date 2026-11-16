package app.opora.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.opora.R
import app.opora.domain.SYMPTOMS
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Выбранный симптом вне первых восьми не должен исчезать из карточки:
 * иначе отметка сохраняется незаметно для человека.
 */
@RunWith(AndroidJUnit4::class)
class SymptomsCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun selectedHiddenSymptomIsShown() {
        // «тремор» — за пределами первых восьми по алфавиту.
        assertTrue(SYMPTOMS.indexOfFirst { it.key == "tremor" } > 7)
        compose.setContent {
            OporaTheme {
                SymptomsCard(
                    tags = SYMPTOMS,
                    selected = setOf("tremor"),
                    onToggle = {},
                    onAddCustom = {}
                )
            }
        }
        compose.onNodeWithText(TestStrings.get(R.string.tag_tremor)).assertExists()
    }
}
