package app.opora.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FormDialogTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun saveButtonStaysAboveEmulatedIme() {
        // The test composes at density 1 for predictable pixel→dp maths.
        val screenHeight = InstrumentationRegistry.getInstrumentation().targetContext
            .resources.displayMetrics.heightPixels.dp
        val imeHeight = 280.dp
        rule.setContent {
            OporaTheme {
                CompositionLocalProvider(
                    LocalFormDialogImeVisible provides true,
                    LocalDensity provides Density(1f)
                ) {
                    FormDialog(
                        title = "New appointment",
                        onDismissRequest = {},
                        confirmLabel = "Save",
                        onConfirm = {},
                        dismissLabel = "Cancel",
                        imeInsets = androidx.compose.foundation.layout.WindowInsets(
                            0, 0, 0, imeHeight.value.toInt()
                        )
                    ) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth().formDialogField(),
                            keyboardOptions = KeyboardOptions()
                        )
                    }
                }
            }
        }
        val save = rule.onNodeWithText("Save")
        save.assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val bounds = save.getBoundsInRoot()
        assertTrue(
            "Save (${bounds.bottom}) must stay above the IME ($screenHeight - $imeHeight)",
            bounds.bottom <= screenHeight - imeHeight
        )
    }

    @Test
    fun firstBackHidesKeyboardAndSecondBackDismisses() {
        var imeVisible by mutableStateOf(true)
        var dismissed by mutableStateOf(false)
        rule.setContent {
            OporaTheme {
                CompositionLocalProvider(
                    LocalFormDialogImeVisible provides imeVisible,
                    LocalDensity provides Density(1f)
                ) {
                    FormDialog(
                        title = "New appointment",
                        onDismissRequest = { dismissed = true },
                        confirmLabel = "Save",
                        onConfirm = {},
                        dismissLabel = "Cancel",
                        imeInsets = androidx.compose.foundation.layout.WindowInsets(
                            0, 0, 0, if (imeVisible) 280 else 0
                        )
                    ) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            label = { Text("Title") },
                            modifier = Modifier.fillMaxWidth().formDialogField()
                        )
                    }
                }
            }
        }
        rule.onNodeWithText("Title").performTextInput("Психиатр")

        Espresso.pressBack()
        rule.waitForIdle()
        assertFalse("First back must only hide the keyboard", dismissed)

        imeVisible = false
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        assertTrue("Second back must dismiss the form", dismissed)
    }
}
