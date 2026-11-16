package app.opora.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import app.opora.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Пишет стектрейс необработанного исключения в `filesDir/crashes` и хранит
 * не больше [CrashLogPolicy.MAX_FILES] файлов. Прежний обработчик вызывается
 * следом, поэтому системное поведение падения не меняется.
 *
 * Ничего не отправляет: файл прикладывается к отчёту только когда человек
 * сам нажмёт «Сообщить о проблеме» и выберет получателя.
 */
object CrashLogger {
    private const val DIR = "crashes"
    private val lock = Any()

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    /** Возвращает записанный файл или null, если запись не удалась. */
    fun record(context: Context, thread: Thread, error: Throwable): File? = synchronized(lock) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val file = File(dir, CrashLogPolicy.fileName(LocalDateTime.now()))
        val written = runCatching {
            file.bufferedWriter().use { writer ->
                writer.write("Time: ${LocalDateTime.now()}\n")
                writer.write("Thread: ${thread.name}\n")
                writer.write("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                writer.write("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                writer.write(
                    "Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n"
                )
                val trace = StringWriter()
                error.printStackTrace(PrintWriter(trace))
                writer.write(trace.toString())
            }
        }.isSuccess
        if (!written) {
            runCatching { file.delete() }
            null
        } else {
            rotate(dir)
            file.takeIf { it.isFile && it.canRead() && it.length() > 0L }
        }
    }

    /** Последний непустой читаемый файл падения, если он есть. */
    fun latest(context: Context): File? = synchronized(lock) {
        val dir = File(context.filesDir, DIR)
        dir.listFiles()
            ?.filter {
                it.isFile &&
                    it.canRead() &&
                    it.length() > 0L &&
                    CrashLogPolicy.isCrashName(it.name)
            }
            ?.maxByOrNull { it.name }
    }

    private fun rotate(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val stale = CrashLogPolicy.staleNames(files.map { it.name }).toSet()
        if (stale.isEmpty()) return
        files.forEach { file ->
            if (file.name in stale) runCatching { file.delete() }
        }
    }
}
