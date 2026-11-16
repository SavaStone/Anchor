package app.opora.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportTextTest {

    private val base = ReportData(
        appVersion = "1.0.0-beta",
        versionCode = 2,
        languageTag = "ru",
        fontScale = 1.0f,
        theme = ReportTheme.SYSTEM,
        device = "Google sdk_gphone64_arm64",
        androidRelease = "15",
        androidSdk = 35,
        freeBytes = (4.2 * 1024 * 1024 * 1024).toLong()
    )

    @Test
    fun `russian report has every required field`() {
        val text = ReportText.format(
            base.copy(
                lastError = "Не удалось синхронизировать копию.",
                crashFileName = "crash-20261114-201500.txt"
            )
        )
        assertEquals(
            """
            Опора: сообщение о проблеме

            Версия: 1.0.0-beta (2)
            Язык приложения: ru
            Масштаб шрифта: 1,0
            Тема: системная
            Устройство: Google sdk_gphone64_arm64
            Android: 15 (API 35)
            Свободное место: 4,2 ГБ
            Последняя ошибка: Не удалось синхронизировать копию.
            Файл падения: crash-20261114-201500.txt

            Что случилось:
            """.trimIndent(),
            text
        )
    }

    @Test
    fun `english report uses anchor labels and dot decimals`() {
        val text = ReportText.format(
            base.copy(
                languageTag = "en",
                theme = ReportTheme.DARK,
                fontScale = 1.3f,
                lastError = "Auto-backup stopped: no password set."
            )
        )
        assertTrue(text.startsWith("Anchor: problem report"))
        assertTrue(text.contains("Version: 1.0.0-beta (2)"))
        assertTrue(text.contains("App language: en"))
        assertTrue(text.contains("Font scale: 1.3"))
        assertTrue(text.contains("Theme: dark"))
        assertTrue(text.contains("Free space: 4.2 GB"))
        assertTrue(text.contains("Last error: Auto-backup stopped: no password set."))
        assertTrue(text.endsWith("What happened:"))
        assertFalse(text.contains("Файл падения"))
    }

    @Test
    fun `missing error and crash lines are omitted`() {
        val text = ReportText.format(base)
        assertFalse(text.contains("Последняя ошибка"))
        assertFalse(text.contains("Файл падения"))
        assertTrue(text.contains("Версия: 1.0.0-beta (2)"))
    }

    @Test
    fun `small free space is shown in megabytes`() {
        val text = ReportText.format(base.copy(languageTag = "en", freeBytes = 512L * 1024 * 1024))
        assertTrue(text.contains("Free space: 512 MB"))
    }

    @Test
    fun `negative free space is clamped to zero`() {
        val text = ReportText.format(base.copy(languageTag = "en", freeBytes = -42L))
        assertTrue(text.contains("Free space: 0.0 MB"))
    }

    @Test
    fun `recipient notice asks to review before sending`() {
        val text = ReportText.format(base.copy(recipient = "savastone32@proton.me"))
        assertTrue(text.contains("savastone32@proton.me"))
        assertTrue(text.contains("проверьте", ignoreCase = true))
        assertTrue(text.contains("личные данные"))
        assertTrue(text.contains("Версия: 1.0.0-beta (2)"))
    }

    @Test
    fun `last error in report is scrubbed and flattened`() {
        val text = ReportText.format(
            base.copy(
                languageTag = "en",
                lastError = "sync\nfailed https://user:pass@example.org/x token=abc"
            )
        )
        assertTrue(text.contains("Last error: sync failed [redacted-url] token=[redacted]"))
        assertFalse(text.contains("user:pass"))
        assertFalse(text.contains("token=abc"))
    }

    @Test
    fun `language tag with region is treated as russian`() {
        val text = ReportText.format(base.copy(languageTag = "ru-RU"))
        assertTrue(text.startsWith("Опора: сообщение о проблеме"))
        assertTrue(text.contains("Язык приложения: ru-RU"))
    }
}
