package app.opora.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import app.opora.R
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Rule
import org.junit.Test

class AppointmentEditorImeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun doneActionOnNotesRunsTheKeyboardHandlerWithoutSaving() {
        var imeDone = false
        compose.setContent {
            OporaTheme {
                AppointmentEditorDialog(
                    initial = null,
                    editingId = null,
                    editingCreatedAt = null,
                    onDismiss = {},
                    onSave = {},
                    onAddToCalendar = {},
                    onImeDone = { imeDone = true }
                )
            }
        }
        val notes = compose.onNodeWithText(TestStrings.get(R.string.appointments_notes))
        notes.performClick()
        // The notes field is the last input; its IME action is Done, not NewLine,
        // and the handler clears focus (which hides the keyboard) without saving.
        notes.performImeAction()
        compose.waitForIdle()
        org.junit.Assert.assertTrue("Done must reach the dialog keyboard handler", imeDone)
        notes.assertExists()
    }

    @Test
    fun addToCalendarUsesFilledFormValues() {
        var event: app.opora.domain.CalendarEvent? = null
        compose.setContent {
            OporaTheme {
                AppointmentEditorDialog(
                    initial = null,
                    editingId = null,
                    editingCreatedAt = null,
                    onDismiss = {},
                    onSave = {},
                    onAddToCalendar = { event = it }
                )
            }
        }
        val name = compose.onNodeWithText(TestStrings.get(R.string.appointments_field_title))
        name.performTextInput("Psychiatrist")
        compose.waitForIdle()
        val calendar = compose.onNodeWithText(TestStrings.get(R.string.appointments_add_to_calendar))
        calendar.assertIsEnabled().performScrollTo().performClick()
        compose.waitForIdle()
        assert(event != null)
        assert(event!!.title == "Psychiatrist")
        assert(event!!.endMillis > event!!.beginMillis)
    }

    @Test
    fun savePassesTypedTitleToTheScreen() {
        var saved: app.opora.data.db.AppointmentEntity? = null
        compose.setContent {
            OporaTheme {
                AppointmentEditorDialog(
                    initial = null,
                    editingId = null,
                    editingCreatedAt = null,
                    onDismiss = {},
                    onSave = { saved = it },
                    onAddToCalendar = {}
                )
            }
        }
        compose.onNodeWithText(TestStrings.get(R.string.appointments_field_title)).performTextInput("Psychiatrist")
        compose.onNodeWithText(TestStrings.get(R.string.appointments_save)).performClick()
        compose.waitForIdle()
        assert(saved?.title == "Psychiatrist") { "Save must keep the typed title" }
    }
}
