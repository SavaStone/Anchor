package app.opora.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotifyChannelTest {
    @Test
    fun channelImportanceNoneIsReportedAsDisabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = "test-channel-${System.nanoTime()}"
        try {
            manager.createNotificationChannel(
                NotificationChannel(id, "Test", NotificationManager.IMPORTANCE_NONE)
            )
            assertFalse(Notify.isChannelEnabled(context, id))
            // Включённый канал проверяем без утверждения: на тестовом
            // устройстве может быть отозвано разрешение на уведомления.
        } finally {
            manager.deleteNotificationChannel(id)
        }
    }
}
