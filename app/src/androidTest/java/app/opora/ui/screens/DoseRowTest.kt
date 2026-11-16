package app.opora.ui.screens

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.ui.TestStrings
import app.opora.ui.theme.OporaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DoseRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val activeMed = MedicationEntity(
        id = "med-1", name = "Атаракс", dosage = "25 мг", startDate = 0L
    )

    private fun dose(
        isPrn: Boolean = false,
        scheduled: String = "09:00",
        takenHour: Int = 10,
        takenMinute: Int = 20
    ): DoseLogEntity {
        val date = LocalDate.of(2026, 11, 8)
        val takenAt = date.atTime(takenHour, takenMinute)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return DoseLogEntity(
            medicationId = "med-1",
            medicationName = "Атаракс",
            dosage = "25 мг",
            scheduledDate = date.toEpochDay(),
            scheduledTime = scheduled,
            scheduledAt = takenAt,
            status = "taken",
            takenAt = takenAt,
            isPrn = isPrn
        )
    }

    @Test
    fun scheduledDoseKeepsSlotAndShowsActualTimeInSubtitle() {
        compose.setContent {
            OporaTheme {
                DoseRow(
                    dose = dose(),
                    medication = activeMed,
                    onTake = {},
                    onSkip = {},
                    onUndo = {},
                    onEditTime = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithText("09:00").assertExists()
        compose.onNodeWithText(
            TestStrings.get(R.string.dose_line_taken_at, "25 мг", "10:20")
        ).assertExists()
    }

    @Test
    fun tappingTakenCheckReturnsDoseToPending() {
        var undone = false
        compose.setContent {
            OporaTheme {
                DoseRow(
                    dose = dose(),
                    medication = activeMed,
                    onTake = {},
                    onSkip = {},
                    onUndo = { undone = true },
                    onEditTime = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithContentDescription(TestStrings.get(app.opora.R.string.dose_taken_a11y, "Атаракс"))
            .performClick()
        compose.runOnIdle { assertTrue(undone) }
    }

    @Test
    fun prnCheckIsNotAnUndoButton() {
        compose.setContent {
            OporaTheme {
                DoseRow(
                    dose = dose(isPrn = true, scheduled = "10:20"),
                    medication = activeMed,
                    onTake = {},
                    onSkip = {},
                    onUndo = {},
                    onEditTime = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithContentDescription(TestStrings.get(app.opora.R.string.dose_taken_prn_a11y, "Атаракс"))
            .assertHasNoClickAction()
    }

    @Test
    fun prnDoseMenuAllowsEditAndDeleteButNotSkip() {
        compose.setContent {
            OporaTheme {
                DoseRow(
                    dose = dose(isPrn = true, scheduled = "10:20"),
                    medication = activeMed,
                    onTake = {},
                    onSkip = {},
                    onUndo = {},
                    onEditTime = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithContentDescription(
            TestStrings.get(R.string.a11y_dose_overflow, "Атаракс", "10:20")
        ).performClick()
        compose.onNodeWithText(TestStrings.get(R.string.actions_edit_time)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.actions_delete_dose)).assertExists()
        compose.onNodeWithText(TestStrings.get(R.string.dose_skip_action)).assertDoesNotExist()
        compose.onNodeWithText(TestStrings.get(R.string.actions_undo)).assertDoesNotExist()
    }
    @Test
    fun inactiveMedicationStatusIsNotAnUndoButton() {
        var undone = false
        compose.setContent {
            OporaTheme {
                DoseRow(
                    dose = dose(),
                    medication = activeMed.copy(active = false),
                    onTake = {},
                    onSkip = {},
                    onUndo = { undone = true },
                    onEditTime = {},
                    onDelete = {}
                )
            }
        }

        compose.onNodeWithContentDescription(
            TestStrings.get(app.opora.R.string.dose_taken_prn_a11y, "Атаракс")
        ).assertHasNoClickAction()
        compose.runOnIdle { assertTrue(!undone) }
    }

}
