package app.opora.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.opora.R
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImeAwareBoxTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun bottomContentStaysAboveImeInset() {
        compose.setContent {
            OporaTheme {
                val inset = with(LocalDensity.current) { 120.dp.roundToPx() }
                ImeAwareBox(imeInsets = WindowInsets(0, 0, 0, inset)) {
                    Box(Modifier.fillMaxSize()) {
                        Text(
                            TestStrings.get(R.string.med_editor_save),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val save = compose.onNodeWithText(TestStrings.get(R.string.med_editor_save))
            .getUnclippedBoundsInRoot()

        assertTrue(
            "save bottom ${save.bottom} must be above the 120dp IME inset in $root",
            save.bottom <= root.bottom - 120.dp
        )
    }
}
