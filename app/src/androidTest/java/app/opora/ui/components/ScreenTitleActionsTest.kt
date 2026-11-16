package app.opora.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.opora.R
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Слот `actions` в ScreenTitle — ряд, а не наложение: статус сохранения и
 * кнопка удаления не должны пересекаться ни при каком масштабе шрифта.
 */
@RunWith(AndroidJUnit4::class)
class ScreenTitleActionsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(scale: Float, themeMode: String) {
        compose.setContent {
            OporaTheme(themeMode = themeMode) {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale)) {
                    ScreenTitle(
                        title = TestStrings.get(R.string.mood_editor_title_new),
                        onBack = {},
                        actions = {
                            Text(
                                text = TestStrings.get(R.string.mood_saved),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .testTag("saveStatus")
                            )
                            IconButton(
                                onClick = {},
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = TestStrings.get(R.string.mood_delete_a11y)
                                )
                            }
                        }
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertNoOverlap(scale: Float, themeMode: String) {
        show(scale, themeMode)
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val status = compose.onNodeWithTag("saveStatus").getUnclippedBoundsInRoot()
        val delete = compose.onNodeWithContentDescription(
            TestStrings.get(R.string.mood_delete_a11y)
        )
            .getUnclippedBoundsInRoot()
        val overlap = status.left < delete.right && delete.left < status.right &&
            status.top < delete.bottom && delete.top < status.bottom
        assertFalse(
            "scale=$scale $themeMode: статус $status пересекает кнопку $delete",
            overlap
        )
        assertTrue(
            "scale=$scale $themeMode: кнопка удаления меньше 48dp: $delete",
            delete.right - delete.left >= 48.dp && delete.bottom - delete.top >= 48.dp
        )
        assertTrue(
            "scale=$scale $themeMode: статус уехал за экран: $status",
            status.left >= 0.dp && status.right <= root.right
        )
    }

    @Test
    fun statusAndDeleteDoNotOverlapAt10() = assertNoOverlap(1.0f, "light")

    @Test
    fun statusAndDeleteDoNotOverlapAt13() = assertNoOverlap(1.3f, "light")

    @Test
    fun statusAndDeleteDoNotOverlapAt20() = assertNoOverlap(2.0f, "light")

    @Test
    fun statusAndDeleteDoNotOverlapAt20Dark() = assertNoOverlap(2.0f, "dark")
}
