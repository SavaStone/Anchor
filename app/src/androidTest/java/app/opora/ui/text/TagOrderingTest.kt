package app.opora.ui.text

import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.domain.TagDef
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TagOrderingTest {
    private fun localized(locale: Locale): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    private val tags = listOf(
        TagDef("joy", "радость", R.string.tag_joy),
        TagDef("apathy", "апатия", R.string.tag_apathy),
        TagDef("anxiety", "тревога", R.string.tag_anxiety),
        TagDef("custom", "zzz")
    )

    @Test
    fun orderFollowsVisibleLanguage() {
        assertEquals(
            listOf("apathy", "joy", "anxiety", "custom"),
            tags.sortedByDisplayLabel(localized(Locale.forLanguageTag("ru"))).map { it.key }
        )
        assertEquals(
            listOf("anxiety", "apathy", "joy", "custom"),
            tags.sortedByDisplayLabel(localized(Locale.ENGLISH)).map { it.key }
        )
    }

    @Test
    fun mergedLabelsAndFilterKeysUseTheSameOrder() {
        val ru = localized(Locale.forLanguageTag("ru"))
        val en = localized(Locale.ENGLISH)
        val mergedRu = mergeTagsForDisplay(tags.take(3), tags.drop(3), ru)
        val mergedEn = mergeTagsForDisplay(tags.take(3), tags.drop(3), en)
        assertEquals("apathy", mergedRu.first().key)
        assertEquals("anxiety", mergedEn.first().key)

        val labels = tagLabelsForDisplay(en, tags)
        assertEquals("anxiety", sortedTagKeysForDisplay(tags.map { it.key }, labels, en).first())
        assertEquals("apathy", labels["apathy"])
        assertEquals("anxiety", labels["anxiety"])
    }
}
