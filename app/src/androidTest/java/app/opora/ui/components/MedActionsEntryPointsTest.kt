package app.opora.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.ui.TestStrings
import app.opora.ui.screens.DoseRow
import app.opora.ui.screens.MedicationCard
import app.opora.ui.theme.OporaTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MedActionsEntryPointsTest {
    @get:Rule
    val compose = createComposeRule()

    private val med = MedicationEntity(
        id = "med", name = "Тест", dosage = "1 таблетка",
        startDate = LocalDate.now().toEpochDay()
    )
    private val dose = DoseLogEntity(
        id = "dose", medicationId = med.id, medicationName = med.name,
        dosage = med.dosage, scheduledDate = LocalDate.now().toEpochDay(),
        scheduledTime = "00:01", status = "pending"
    )

    private fun assertAllActions() {
        listOf(
            TestStrings.get(R.string.dose_skip_action),
            TestStrings.get(R.string.actions_pause),
            TestStrings.get(R.string.actions_discontinue),
            TestStrings.get(R.string.actions_edit_medication)
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test
    fun doseRowEntryPointShowsAllActions() {
        compose.setContent {
            OporaTheme {
                DoseRow(
                    dose = dose, onTake = {}, onSkip = {}, onUndo = {},
                    onEditTime = {}, onDelete = {},
                    onOpenMedication = {},
                    medication = med, onPause = {}, onResume = {},
                    onDiscontinue = {}, onDeleteMedication = {}
                )
            }
        }
        compose.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_dose_overflow, dose.medicationName, dose.scheduledTime)
        ).performClick()
        assertAllActions()
    }

    @Test
    fun medicationCardEntryPointShowsAllActions() {
        compose.setContent {
            OporaTheme {
                MedicationCard(
                    med = med, todayDoses = listOf(dose), onTake = {},
                    onSkip = {}, onUndo = {}, onEditTime = { _, _ -> },
                    onLogPrn = null, onPause = {}, onDiscontinue = {},
                    onDeleteMedication = {}, onClick = {}
                )
            }
        }
        compose.onNodeWithContentDescription(
            TestStrings.get(R.string.meds_more_a11y, "${med.name}, ${med.dosage}")
        ).performClick()
        assertAllActions()
    }
}
