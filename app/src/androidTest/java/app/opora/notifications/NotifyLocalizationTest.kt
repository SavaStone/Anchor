package app.opora.notifications

import app.opora.AppLanguage
import android.app.NotificationManager
import android.content.Context
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NotifyLocalizationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = base.getSystemService(NotificationManager::class.java)
    private val previousTag = AppLanguage.currentTag()

    private fun localized(locale: Locale): Context {
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }

    @After
    fun restoreChannels() {
        AppLanguage.setTag(previousTag)
        Notify.ensureChannels(base)
    }

    @Test
    fun channelNamesAndDescriptionsFollowTheAppLanguage() {
        AppLanguage.setTag(AppLanguage.RUSSIAN)
        Notify.ensureChannels(localized(Locale.forLanguageTag("ru")))
        val ru = manager.getNotificationChannel(Notify.CHANNEL_MEDS)
        assertEquals("Лекарства", ru.name.toString())
        assertEquals("Напоминания о времени приёма", ru.description.toString())

        AppLanguage.setTag(AppLanguage.ENGLISH)
        Notify.ensureChannels(localized(Locale.ENGLISH))
        val en = manager.getNotificationChannel(Notify.CHANNEL_MEDS)
        assertEquals("Medications", en.name.toString())
        assertEquals("Reminders about dose times", en.description.toString())

        AppLanguage.setTag(AppLanguage.RUSSIAN)
        Notify.ensureChannels(localized(Locale.forLanguageTag("ru")))
        assertEquals(
            "Пополнение запаса",
            manager.getNotificationChannel(Notify.CHANNEL_REFILL).name.toString()
        )
    }
}
