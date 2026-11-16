package app.opora.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedEditorStockTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bothBlankExplainsWhatToFix() {
        assertEquals(
            context.getString(R.string.stock_error_empty),
            stockValidationError(context, enabled = true, countText = "", thresholdText = "")
        )
    }

    @Test
    fun thresholdWithoutStockAsksForStock() {
        assertEquals(
            context.getString(R.string.stock_error_threshold_first),
            stockValidationError(context, enabled = true, countText = "", thresholdText = "7")
        )
    }

    @Test
    fun thresholdIsOptional() {
        assertNull(stockValidationError(context, enabled = true, countText = "10", thresholdText = ""))
    }

    @Test
    fun thresholdAboveStockIsRejected() {
        assertEquals(
            context.getString(R.string.stock_error_threshold_above),
            stockValidationError(context, enabled = true, countText = "10", thresholdText = "20")
        )
    }

    @Test
    fun disabledStockIgnoresEmptyFields() {
        assertNull(stockValidationError(context, enabled = false, countText = "", thresholdText = ""))
    }
}
