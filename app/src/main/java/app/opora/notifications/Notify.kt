package app.opora.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.opora.AppLanguage
import app.opora.MainActivity
import app.opora.R
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.MedicationEntity
import app.opora.domain.DEFAULT_REFILL_THRESHOLD
import app.opora.domain.formatAmount
import app.opora.data.db.OporaDatabase
import app.opora.domain.hhmm

object Notify {
    const val CHANNEL_MEDS = "meds"
    const val CHANNEL_REFILL = "refill"
    const val CHANNEL_DAILY = "daily"
    const val CHANNEL_APPOINTMENTS = "appointments"

    const val ACTION_TAKEN = "app.opora.action.TAKEN"
    const val ACTION_SKIP = "app.opora.action.SKIP"
    const val ACTION_SNOOZE = "app.opora.action.SNOOZE"

    fun ensureChannels(baseContext: Context) {
        val context = AppLanguage.wrap(baseContext)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MEDS,
                    context.getString(R.string.channel_meds),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.channel_meds_desc)
                    enableVibration(true)
                    setShowBadge(true)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_REFILL,
                    context.getString(R.string.channel_refill),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.channel_refill_desc) }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DAILY,
                    context.getString(R.string.channel_daily),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.channel_daily_desc) }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_APPOINTMENTS,
                    context.getString(R.string.channel_appointments),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.channel_appointments_desc) }
            )
        }
    }

    /** Пользователь мог выключить конкретный канал, не трогая приложение целиком. */
    fun isChannelEnabled(context: Context, channelId: String): Boolean =
        NotificationManagerCompat.from(context)
            .getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun canNotify(context: Context, channelId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            isChannelEnabled(context, channelId)
    }

    private fun openApp(context: Context, deepLink: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (deepLink != null) {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse(deepLink)
            }
        }
        return PendingIntent.getActivity(
            context, deepLink?.hashCode() ?: 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(context: Context, action: String, log: DoseLogEntity): PendingIntent {
        val intent = Intent(context, MedActionReceiver::class.java).apply {
            this.action = action
            putExtra("logId", log.id)
            putExtra("medId", log.medicationId)
        }
        return PendingIntent.getBroadcast(
            context,
            (action + log.id).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun medication(baseContext: Context, log: DoseLogEntity, med: MedicationEntity?) {
        val context = AppLanguage.wrap(baseContext)
        if (!canNotify(context, CHANNEL_MEDS)) return
        val name = med?.name ?: log.medicationName
        val dosage = med?.dosage ?: log.dosage
        val title = context.getString(R.string.notif_med_title, name)
        val text = listOfNotNull(log.scheduledTime, dosage.takeIf { it.isNotBlank() })
            .joinToString(" · ")

        val builder = NotificationCompat.Builder(context, CHANNEL_MEDS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text\n${med?.purpose.orEmpty()}"
                )
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_MEDS)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notif_med_public_title))
                    .setContentText(context.getString(R.string.notif_med_public_text))
                    .build()
            )
            .setContentIntent(openApp(context, "opora://meds"))
            .setAutoCancel(true)
            .setTimeoutAfter(3 * 60 * 60 * 1000L)
            .addAction(0, context.getString(R.string.notif_action_taken), actionIntent(context, ACTION_TAKEN, log))
            .addAction(0, context.getString(R.string.notif_action_snooze), actionIntent(context, ACTION_SNOOZE, log))
            .addAction(0, context.getString(R.string.notif_action_skip), actionIntent(context, ACTION_SKIP, log))

        notify(context, log.id.hashCode(), builder.build())
    }

    fun refill(baseContext: Context, med: MedicationEntity) {
        val context = AppLanguage.wrap(baseContext)
        if (!canNotify(context, CHANNEL_REFILL)) return
        val count = med.inventoryCount ?: return
        val builder = NotificationCompat.Builder(context, CHANNEL_REFILL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_refill_title))
            .setContentText(context.getString(R.string.notif_refill_text, med.name, formatAmount(count, AppLanguage.locale(context))))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(
                        R.string.notif_refill_big,
                        med.name,
                        med.dosage,
                        formatAmount(count, AppLanguage.locale(context)),
                        formatAmount(med.refillThreshold ?: DEFAULT_REFILL_THRESHOLD, AppLanguage.locale(context))
                    )
                )
            )
            .setContentIntent(openApp(context, "opora://meds"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notify(context, ("refill:" + med.id).hashCode(), builder.build())
    }

    fun dailyMood(baseContext: Context) {
        val context = AppLanguage.wrap(baseContext)
        if (!canNotify(context, CHANNEL_DAILY)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_daily_title))
            .setContentText(context.getString(R.string.notif_daily_text))
            .setContentIntent(openApp(context, "opora://mood"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notify(context, 9001, builder.build())
    }

    fun appointment(context: Context, title: String, detail: String, id: String) {
        if (!canNotify(context, CHANNEL_APPOINTMENTS)) return
        val builder = NotificationCompat.Builder(context, CHANNEL_APPOINTMENTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(openApp(context, "opora://appointments"))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        notify(context, ("appt:" + id).hashCode(), builder.build())
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        // Permission is checked by canNotify() at every call site; posting is best-effort.
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    fun cancel(context: Context, logId: String) {
        NotificationManagerCompat.from(context).cancel(logId.hashCode())
    }

    /** Запас снова выше порога — старое напоминание о пополнении больше не актуально. */
    fun cancelRefill(context: Context, medicationId: String) {
        NotificationManagerCompat.from(context)
            .cancel(("refill:" + medicationId).hashCode())
    }

    /** Убирает все показанные уведомления приложения (например, при полной очистке данных). */
    fun cancelAll(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancelAll() }
    }
}
