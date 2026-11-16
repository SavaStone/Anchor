package app.opora.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Rule
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedActionsMenuTest {
    @get:Rule
    val compose = createComposeRule()

    private val med = MedicationEntity(
        id = "med", name = "Тест", startDate = LocalDate.now().toEpochDay()
    )
    private val pending = DoseLogEntity(
        medicationId = med.id,
        medicationName = med.name,
        scheduledDate = LocalDate.now().toEpochDay(),
        scheduledTime = "00:01",
        status = "pending"
    )
    private val taken = pending.copy(status = "taken", takenAt = System.currentTimeMillis())

    private var deletedMedication = false

    private fun show(medication: MedicationEntity?, dose: DoseLogEntity?) {
        compose.setContent {
            OporaTheme {
                MedActionsMenu(
                    medication = medication,
                    dose = dose,
                    callbacks = MedActionCallbacks(
                        onSkip = {}, onEditTime = {}, onUndo = {}, onDeleteDose = {},
                        onPause = {}, onResume = {}, onDiscontinue = {},
                        onEditMedication = {},
                        onDeleteMedication = { deletedMedication = true }
                    ),
                    contentDescription = "actions"
                )
            }
        }
        compose.onNodeWithContentDescription("actions").performClick()
    }

    @Test
    fun activeMedicationAndPendingDoseShowAllApplicableActions() {
        show(med, pending)
        listOf(
            TestStrings.get(R.string.dose_skip_action),
            TestStrings.get(R.string.actions_pause),
            TestStrings.get(R.string.actions_discontinue),
            TestStrings.get(R.string.actions_edit_medication)
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText(TestStrings.get(R.string.actions_resume)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_undo)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.med_dose_take_at, "09:00"))
            .assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_medication))
            .assertDoesNotExist()
    }

    @Test
    fun takenDoseOffersUndoAndMedicationActions() {
        show(med, taken)
        compose.onNodeWithText(TestStrings.get(R.string.actions_undo)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_time)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.med_dose_take_at, "09:00"))
            .assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.dose_skip_action)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_medication))
            .assertDoesNotExist()
    }

    @Test
    fun deleteMedicationAsksForConfirmation() {
        show(med.copy(active = false, discontinuedAt = System.currentTimeMillis()), null)
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_medication)).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_med_title)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.action_delete)).performClick()
        compose.runOnIdle { assertTrue(deletedMedication) }
    }

    @Test
    fun pausedMedicationOnlyOffersResumeAndDiscontinue() {
        show(med.copy(active = false), null)
        compose.onNodeWithText(TestStrings.get(R.string.actions_resume)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_pause)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_medication))
            .assertDoesNotExist()
    }

    @Test
    fun discontinuedMedicationOnlyOffersDelete() {
        show(med.copy(active = false, discontinuedAt = System.currentTimeMillis()), null)
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_medication)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_resume)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_discontinue)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_medication))
            .assertDoesNotExist()
    }
    @Test
    fun inactiveMedicationHidesDoseActionsInHistory() {
        show(med.copy(active = false), taken)
        compose.onNodeWithText(TestStrings.get(R.string.actions_resume)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_undo)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_time)).assertDoesNotExist()
    }

    @Test
    fun discontinuedMedicationHidesDoseActionsInHistory() {
        show(med.copy(active = false, discontinuedAt = System.currentTimeMillis()), taken)
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_medication)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_undo)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_time)).assertDoesNotExist()
    }

    @Test
    fun deletedMedicationHidesUndo() {
        show(null, taken)
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_time)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_undo)).assertDoesNotExist()
    }

}
