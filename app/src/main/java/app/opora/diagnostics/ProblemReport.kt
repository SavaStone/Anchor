package app.opora.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.FileProvider
import app.opora.AppLanguage
import app.opora.BuildConfig
import app.opora.R
import app.opora.data.backup.AutoBackupFailure
import app.opora.data.prefs.AppSettings
import java.io.ByteArrayOutputStream
import java.io.File

data class ProblemShare(
    val text: String,
    val subject: String,
    val crashFile: File?
)

/**
 * Отчёт о проблеме: собирает только технические данные, кладёт их в
 * ACTION_SEND с адресом [RECIPIENT] и прикладывает последний файл падения.
 * Получателя всё равно выбирает человек в системном листе, а письмо он
 * просматривает и отправляет сам; приложение ничего не отправляет по сети.
 */
object ProblemReport {
    const val RECIPIENT = "savastone32@proton.me"
    const val MIME_TYPE = "text/plain"

    private const val CRASH_DIR = "crashes"
    private const val SHARE_PREFIX = "share-"
    private const val MAX_SHARE_FILES = 3

    fun create(context: Context, settings: AppSettings): ProblemShare {
        val crash = CrashLogger.latest(context)
        val language = runCatching {
            context.resources.configuration.locales[0].language
        }.getOrDefault(AppLanguage.locale(context).language)
        val free = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }
            .getOrDefault(0L)
        val theme = when (settings.themeMode) {
            "light" -> ReportTheme.LIGHT
            "dark" -> ReportTheme.DARK
            else -> ReportTheme.SYSTEM
        }
        val data = ReportData(
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            languageTag = language,
            fontScale = context.resources.configuration.fontScale,
            theme = theme,
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            androidRelease = Build.VERSION.RELEASE,
            androidSdk = Build.VERSION.SDK_INT,
            freeBytes = free,
            lastError = DiagnosticRedaction.errorSummary(lastError(context, settings)),
            crashFileName = crash?.name,
            recipient = RECIPIENT
        )
        return ProblemShare(
            text = ReportText.format(data),
            subject = context.getString(R.string.problem_report_subject),
            crashFile = crash
        )
    }

    /**
     * Intent без запуска: его проверяет инструментальный тест. Адрес получателя
     * кладётся в EXTRA_EMAIL, но применяют его только почтовые клиенты; другие
     * совместимые приложения «Поделиться» могут это поле игнорировать.
     *
     * Файл падения прикладывается только если его удалось прочитать и
     * подготовить очищенную копию. Иначе отчёт остаётся текстовым и не получает
     * grant — ошибка FileProvider не должна ломать нажатие.
     */
    fun intent(context: Context, share: ProblemShare): Intent {
        val result = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_TEXT, share.text)
            putExtra(Intent.EXTRA_SUBJECT, share.subject)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(RECIPIENT))
        }
        val attachment = runCatching {
            share.crashFile?.let { sanitizedCrash(context, it) }
        }.getOrNull()
        if (attachment != null) {
            val uri = runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    attachment
                )
            }.getOrNull()
            val clip = uri?.let { safeUri ->
                runCatching {
                    ClipData.newUri(context.contentResolver, attachment.name, safeUri)
                }.getOrNull()
            }
            if (uri != null && clip != null) {
                result.putExtra(Intent.EXTRA_STREAM, uri)
                result.clipData = clip
                result.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        return result
    }

    /**
     * Собирает и открывает отчёт. False означает, что отчёт не собрался, нет
     * приложения для ACTION_SEND text/plain или лист «Поделиться» не открылся;
     * исключения наружу не выходят.
     */
    fun share(context: Context, settings: AppSettings): Boolean =
        runCatching { share(context, create(context, settings)) }.getOrDefault(false)

    fun share(context: Context, share: ProblemShare): Boolean =
        share(
            context = context,
            share = share,
            available = { canSend(context) },
            launch = { launchChooser(context, it) }
        )

    /** Тестовый шов: позволяет проверить отказ/ошибку запуска без внешних приложений. */
    internal fun share(
        context: Context,
        share: ProblemShare,
        available: () -> Boolean,
        launch: (Intent) -> Unit
    ): Boolean = runCatching {
        if (!available()) return@runCatching false
        val send = intent(context, share)
        val chooser = Intent.createChooser(
            send,
            context.getString(R.string.problem_report_chooser, RECIPIENT)
        )
        if (send.clipData != null) {
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(chooser)
        true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun canSend(context: Context): Boolean = runCatching {
        val probe = Intent(Intent.ACTION_SEND).apply { type = MIME_TYPE }
        val manager = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentActivities(
                probe,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            manager.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
        }
        resolved.isNotEmpty()
    }.getOrDefault(false)

    private fun launchChooser(context: Context, chooser: Intent) {
        val activity = context.findActivity()
        if (activity != null) {
            activity.startActivity(chooser)
        } else {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }

    private fun Context.findActivity(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    /**
     * Готовит очищенную копию выбранного файла падения в разрешённой для
     * FileProvider папке `filesDir/crashes`. Исходные журналы не меняются,
     * поэтому запись CrashLogger не ломается. Копий не больше
     * [MAX_SHARE_FILES]; старые удаляются при следующем экспорте.
     */
    private fun sanitizedCrash(context: Context, source: File): File? = runCatching {
        if (!source.isFile || !source.canRead() || source.length() <= 0L) {
            return@runCatching null
        }
        val scrubbed = DiagnosticRedaction.scrubTrace(readBounded(source))
        if (scrubbed.isBlank()) return@runCatching null
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val target = File(dir, SHARE_PREFIX + System.currentTimeMillis() + ".txt")
        target.writeText(scrubbed)
        pruneShares(dir)
        target.takeIf { it.isFile && it.canRead() && it.length() > 0L }
    }.getOrNull()

    private fun readBounded(file: File): String {
        val out = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var remaining = DiagnosticRedaction.MAX_TRACE_INPUT_BYTES
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private fun pruneShares(dir: File) {
        val shares = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SHARE_PREFIX) && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.name }
            ?: return
        shares.drop(MAX_SHARE_FILES).forEach { old ->
            runCatching { old.delete() }
        }
    }

    private fun lastError(context: Context, settings: AppSettings): String? {
        if (settings.lastSyncError.isNotBlank()) return settings.lastSyncError
        return AutoBackupFailure.fromCode(settings.autoBackupError)
            ?.let { context.getString(it.messageRes) }
    }
}
