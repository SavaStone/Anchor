package app.opora.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R

/**
 * Expected strings come from the resources of the current app locale, never
 * from a hardcoded Russian literal. Tests then pass in any device language.
 */
object TestStrings {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun get(@StringRes id: Int, vararg args: Any): String =
        context.getString(id, *args)

    fun plural(@PluralsRes id: Int, n: Int): String =
        context.resources.getQuantityString(id, n, n)

    fun mood(level: Int): String = get(
        when (level) {
            1 -> R.string.mood_1
            2 -> R.string.mood_2
            3 -> R.string.mood_3
            4 -> R.string.mood_4
            else -> R.string.mood_5
        }
    )

    fun moodDescription(level: Int): String = get(R.string.mood_a11y, mood(level))
}
