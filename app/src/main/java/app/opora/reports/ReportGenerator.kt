package app.opora.reports

import android.content.Context
import androidx.annotation.StringRes
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import app.opora.AppLanguage
import app.opora.R
import app.opora.data.OporaRepository
import app.opora.data.db.JournalEntity
import app.opora.data.db.journalDate
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MoodEntity
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.EMOTIONS
import app.opora.domain.MedSchedule
import app.opora.domain.MoodStats
import app.opora.domain.SYMPTOMS
import app.opora.domain.formatAmount
import app.opora.domain.formatDecimal
import app.opora.domain.dateWithYear
import app.opora.domain.dayMonth
import app.opora.domain.dayMonthShort
import app.opora.ui.text.displayLabel
import app.opora.ui.text.formatSleep
import app.opora.ui.text.scheduleSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * PDF для врача: спокойная сводка без процентов и оценок.
 * Задача отчёта — дать факты и место для вопросов, а не оценивать человека.
 */
class ReportGenerator(
    private val context: Context,
    private val repo: OporaRepository,
    private val settings: SettingsRepository
) {
    private val pageWidth = 1240
    private val pageHeight = 1754
    private val margin = 72f
    private val contentWidth = pageWidth - margin * 2

    private val primary = Color.rgb(47, 102, 83)
    private val primarySoft = Color.rgb(180, 241, 213)
    private val ink = Color.rgb(26, 30, 27)
    private val muted = Color.rgb(92, 102, 96)
    private val track = Color.rgb(224, 232, 226)
    private val card = Color.rgb(244, 249, 245)
    private val cardBorder = Color.rgb(220, 232, 223)
    private val white = Color.WHITE
    private val fire = Color.rgb(226, 132, 53)
    private val moodColors = intArrayOf(
        Color.rgb(178, 57, 54),
        Color.rgb(196, 145, 33),
        Color.rgb(45, 92, 148),
        Color.rgb(104, 152, 62),
        Color.rgb(42, 120, 78)
    )

    private val whiteTitlePaint = textPaint(36f, white, Typeface.BOLD)
    private val brandPaint = textPaint(22f, primary, Typeface.BOLD).apply { letterSpacing = 0.12f }
    private val whiteBrandPaint = textPaint(18f, primarySoft, Typeface.BOLD).apply { letterSpacing = 0.16f }
    private val sectionPaint = textPaint(27f, ink, Typeface.BOLD)
    private val subPaint = textPaint(19f, muted, Typeface.NORMAL)
    private val bodyPaint = textPaint(21f, ink, Typeface.NORMAL)
    private val bodyBoldPaint = textPaint(22f, ink, Typeface.BOLD)
    private val valuePaint = textPaint(36f, ink, Typeface.BOLD)
    private val smallPaint = textPaint(16f, muted, Typeface.NORMAL)
    private val tinyPaint = textPaint(14f, muted, Typeface.NORMAL)
    private val whiteSmallPaint = textPaint(18f, Color.rgb(226, 244, 234), Typeface.NORMAL)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = track
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 255, 255, 255)
    }

    private lateinit var document: PdfDocument
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNumber = 0
    private lateinit var generatedAt: LocalDate
    private lateinit var locale: Locale
    private var userName = ""

    suspend fun generate(days: Int, includeJournal: Boolean, includeSymptoms: Boolean): File =
        withContext(Dispatchers.IO) {
            val now = LocalDateTime.now()
            generatedAt = now.toLocalDate()
            locale = AppLanguage.locale(context)
            userName = settings.current().userName
            val from = generatedAt.minusDays(days.toLong() - 1)
            val moods = repo.moodRange(from, generatedAt).first().sortedBy { it.date }
            val meds = repo.medications().first()
            val journal = repo.journalEntries().first().filter {
                it.journalDate() >= from
            }
            val stats = repo.moodStats(moods, generatedAt)

            val dir = File(context.filesDir, "reports").apply { mkdirs() }
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(now)
            var suffix = 0
            var output = File(dir, "opora-report-$stamp.pdf")
            while (output.exists()) {
                suffix++
                output = File(dir, "opora-report-$stamp-$suffix.pdf")
            }
            var written = false
            page = null
            pageNumber = 0
            try {
                document = PdfDocument()
                newPage()

                drawCover(days, from, generatedAt)
                drawSummary(stats)
                drawMoodChart(moods, from, generatedAt)
                drawDistribution(stats)
                drawMedications(meds)
                if (includeSymptoms) drawTags(stats)
                if (includeJournal) drawJournal(journal)
                drawNotesForDoctor()

                page?.let { document.finishPage(it) }
                page = null
                output.outputStream().use { document.writeTo(it) }
                written = true
                output
            } finally {
                page?.let { runCatching { document.finishPage(it) } }
                page = null
                canvas = null
                if (::document.isInitialized) {
                    runCatching { document.close() }
                }
                if (!written) output.delete()
            }
        }

    private fun newPage() {
        page?.let { document.finishPage(it) }
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val newPage = document.startPage(info)
        page = newPage
        canvas = newPage.canvas
        y = margin

        val c = newPage.canvas
        c.drawText(context.getString(R.string.app_name).uppercase(locale), margin, y + 18f, brandPaint)
        val pageText = text(R.string.pdf_page, pageNumber)
        c.drawText(pageText, pageWidth - margin - smallPaint.measureText(pageText), y + 18f, smallPaint)
        y += 34f
        c.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 30f
        c.drawText(
            text(R.string.pdf_generated, generatedAt.dateWithYear(locale)),
            margin, pageHeight - 44f, tinyPaint
        )
        c.drawText(
            text(R.string.pdf_not_medical),
            margin, pageHeight - 66f, tinyPaint
        )
    }

    private fun drawCover(days: Int, from: LocalDate, to: LocalDate) {
        val c = canvas ?: return
        val top = y
        val height = 196f
        val rect = RectF(margin, top, pageWidth - margin, top + height)
        c.drawRoundRect(rect, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary })
        // Мягкие круги вместо тяжёлой иллюстрации: печать не съедает чернила.
        c.drawCircle(pageWidth - margin - 92f, top + 42f, 118f, glowPaint)
        c.drawCircle(pageWidth - margin - 176f, top + 168f, 74f, glowPaint)

        c.drawText(text(R.string.pdf_cover_kicker), margin + 34f, top + 46f, whiteBrandPaint)
        c.drawText(text(R.string.pdf_cover_title), margin + 34f, top + 98f, whiteTitlePaint)
        val dayCount = context.resources.getQuantityString(R.plurals.days, days, days)
        val period = text(
            R.string.pdf_period,
            from.dayMonthShort(locale),
            to.dateWithYear(locale),
            dayCount
        )
        c.drawText(period, margin + 34f, top + 132f, whiteSmallPaint)
        if (userName.isNotBlank()) {
            c.drawText(userName, margin + 34f, top + 162f, whiteSmallPaint)
        }
        y = top + height + 28f
    }

    private fun drawSummary(stats: MoodStats) {
        ensureSpace(330f)
        section(text(R.string.pdf_section_highlights))
        val gap = 16f
        val w = (contentWidth - gap * 2) / 3f
        val h = 120f

        val topItems = listOf(
            Triple(text(R.string.pdf_metric_days), stats.count.toString(), moodColors[4]),
            Triple(text(R.string.pdf_metric_avg_mood), stats.avgMood?.let { fmt1(it) } ?: dash(), moodColors[2]),
            Triple(text(R.string.pdf_metric_streak), stats.streak.toString(), fire)
        )
        drawMetricRow(topItems, y, w, h, gap)
        y += h + 14f

        val second = listOf(
            Triple(text(R.string.pdf_metric_anxiety), stats.avgAnxiety?.let { fmt1(it) } ?: dash(), moodColors[1]),
            Triple(text(R.string.pdf_metric_energy), stats.avgEnergy?.let { fmt1(it) } ?: dash(), moodColors[3]),
            Triple(text(R.string.pdf_metric_sleep), formatSleep(context, stats.avgSleepMinutes), primary)
        )
        drawMetricRow(second, y, w, 104f, gap)
        y += 104f + 24f
    }

    private fun drawMetricRow(
        items: List<Triple<String, String, Int>>,
        top: Float,
        w: Float,
        h: Float,
        gap: Float
    ) {
        val c = canvas ?: return
        items.forEachIndexed { index, (label, value, accent) ->
            val x = margin + index * (w + gap)
            val rect = RectF(x, top, x + w, top + h)
            c.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
            c.drawRoundRect(
                RectF(x, top, x + 7f, top + h),
                10f, 10f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            )
            c.drawText(value, x + 22f, top + 58f, valuePaint)
            c.drawText(label, x + 22f, top + h - 24f, tinyPaint)
        }
    }

    private fun drawMoodChart(moods: List<MoodEntity>, from: LocalDate, to: LocalDate) {
        ensureSpace(420f)
        section(text(R.string.pdf_section_mood_chart))
        val chartTop = y + 16f
        val chartHeight = 264f
        val rect = RectF(margin, chartTop, margin + contentWidth, chartTop + chartHeight)
        canvas?.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })

        val left = margin + 48f
        val right = margin + contentWidth - 26f
        val top = chartTop + 26f
        val bottom = chartTop + chartHeight - 42f
        val days = max(1, (to.toEpochDay() - from.toEpochDay()).toInt())
        val gridPaint = Paint(linePaint).apply {
            pathEffect = DashPathEffect(floatArrayOf(4f, 7f), 0f)
            strokeWidth = 1.2f
        }
        for (value in 1..5) {
            val yy = bottom - (value - 1) / 4f * (bottom - top)
            canvas?.drawLine(left, yy, right, yy, gridPaint)
            canvas?.drawText(value.toString(), margin + 20f, yy + 6f, tinyPaint)
        }
        if (moods.size >= 2) {
            val points = moods.map { mood ->
                val x = left + (mood.date - from.toEpochDay()).toFloat() / days * (right - left)
                val yy = bottom - (mood.mood - 1) / 4f * (bottom - top)
                x to yy
            }
            val area = Path().apply {
                moveTo(points.first().first, bottom)
                points.forEach { (x, yy) -> lineTo(x, yy) }
                lineTo(points.last().first, bottom)
                close()
            }
            canvas?.drawPath(
                area,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, top, 0f, bottom,
                        Color.argb(70, 47, 102, 83),
                        Color.argb(0, 47, 102, 83),
                        Shader.TileMode.CLAMP
                    )
                }
            )
            val line = Path().apply {
                points.forEachIndexed { index, (x, yy) ->
                    if (index == 0) moveTo(x, yy) else lineTo(x, yy)
                }
            }
            canvas?.drawPath(
                line,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = primary
                    style = Paint.Style.STROKE
                    strokeWidth = 5f
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
            )
            moods.zip(points).forEach { (mood, point) ->
                canvas?.drawCircle(
                    point.first, point.second, 6f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = moodColors[(mood.mood - 1).coerceIn(0, 4)]
                    }
                )
                canvas?.drawCircle(
                    point.first, point.second, 9f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = moodColors[(mood.mood - 1).coerceIn(0, 4)]
                        style = Paint.Style.STROKE
                        strokeWidth = 2f
                        alpha = 90
                    }
                )
            }
        } else {
            canvas?.drawText(text(R.string.pdf_chart_need_two), left, chartTop + chartHeight / 2, subPaint)
        }
        canvas?.drawText(from.dayMonthShort(locale), left, chartTop + chartHeight - 14f, tinyPaint)
        val endText = to.dayMonthShort(locale)
        canvas?.drawText(
            endText,
            right - tinyPaint.measureText(endText),
            chartTop + chartHeight - 14f,
            tinyPaint
        )
        y = chartTop + chartHeight + 28f
    }

    private fun drawDistribution(stats: MoodStats) {
        ensureSpace(280f)
        section(text(R.string.pdf_section_distribution))
        val total = max(1, stats.count)
        val barLeft = margin + 150f
        val barWidth = contentWidth - 250f
        var top = y + 10f
        (1..5).forEach { level ->
            val count = stats.distribution[level] ?: 0
            val color = moodColors[level - 1]
            canvas?.drawText(levelLabel(level), margin, top + 21f, bodyPaint)
            val rect = RectF(barLeft, top + 3f, barLeft + barWidth, top + 27f)
            canvas?.drawRoundRect(rect, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = track })
            if (count > 0) {
                val fill = RectF(barLeft, top + 3f, barLeft + barWidth * count / total, top + 27f)
                canvas?.drawRoundRect(fill, 10f, 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
            }
            val percent = if (stats.count > 0) count * 100 / stats.count else 0
            canvas?.drawText("$count · $percent%", barLeft + barWidth + 18f, top + 23f, tinyPaint)
            top += 44f
        }
        y = top + 18f
    }

    private fun drawMedications(meds: List<MedicationEntity>) {
        ensureSpace(150f)
        section(text(R.string.pdf_section_meds))
        val active = meds.filter { it.active }
        if (active.isEmpty()) {
            paragraph(text(R.string.pdf_no_active_meds))
            return
        }
        active.forEach { med ->
            ensureSpace(116f)
            val top = y
            val height = 96f
            val rect = RectF(margin, top, margin + contentWidth, top + height)
            canvas?.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
            canvas?.drawCircle(
                margin + 28f, top + 34f, 8f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = med.color.toInt() }
            )
            val medicationLine = ellipsize(
                listOf(med.name, displayDosage(med.dosage))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                bodyBoldPaint,
                contentWidth - 190f
            )
            canvas?.drawText(medicationLine, margin + 50f, top + 40f, bodyBoldPaint)
            val schedule = scheduleSummary(context, med)
            canvas?.drawText(schedule, margin + 50f, top + 70f, smallPaint)
            med.inventoryCount?.let { stock ->
                val stockText = text(R.string.pdf_stock, formatAmount(stock, locale))
                canvas?.drawText(
                    stockText,
                    margin + contentWidth - 22f - tinyPaint.measureText(stockText),
                    top + 40f,
                    tinyPaint
                )
            }
            y = top + height + 12f
        }
        y += 12f
    }

    private fun drawTags(stats: MoodStats) {
        ensureSpace(190f)
        section(text(R.string.pdf_section_tags))
        val emotions = stats.topEmotions.take(6)
        val symptoms = stats.topSymptoms.take(6)
        if (emotions.isEmpty() && symptoms.isEmpty()) {
            paragraph(text(R.string.pdf_no_tags))
            return
        }
        if (emotions.isNotEmpty()) {
            drawChipRow(text(R.string.pdf_emotions), emotions.map { EMOTIONS.displayLabel(context, it.first) to it.second })
        }
        if (symptoms.isNotEmpty()) {
            drawChipRow(text(R.string.pdf_symptoms), symptoms.map { SYMPTOMS.displayLabel(context, it.first) to it.second })
        }
        y += 10f
    }

    private fun drawChipRow(title: String, chips: List<Pair<String, Int>>) {
        val c = canvas ?: return
        ensureSpace(70f)
        c.drawText(title, margin, y + 18f, smallPaint)
        y += 30f
        val paint = TextPaint(bodyPaint).apply { textSize = 17f }
        var x = margin
        val chipHeight = 40f
        chips.forEach { (label, count) ->
            val text = ellipsize("$label  $count", paint, contentWidth - 40f)
            val width = paint.measureText(text) + 30f
            if (x + width > margin + contentWidth) {
                x = margin
                y += chipHeight + 8f
                ensureSpace(chipHeight + 20f)
            }
            val rect = RectF(x, y, x + width, y + chipHeight)
            c.drawRoundRect(rect, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
            c.drawText(text, x + 15f, y + 26f, paint.apply { color = ink })
            x += width + 8f
        }
        y += chipHeight + 16f
    }

    private fun drawJournal(entries: List<JournalEntity>) {
        ensureSpace(100f)
        section(text(R.string.pdf_section_journal))
        if (entries.isEmpty()) {
            paragraph(text(R.string.pdf_no_entries))
            return
        }
        paragraph(text(
            R.string.pdf_entries_count,
            context.resources.getQuantityString(
                R.plurals.entries,
                entries.size,
                entries.size
            )
        ))
        entries.sortedByDescending { it.journalDate() }.forEach { entry ->
            val date = entry.journalDate().dayMonth(locale)
            val snippetPaint = TextPaint(smallPaint)
            val snippet = StaticLayout.Builder.obtain(
                entry.body.ifBlank { "—" }.replace('\n', ' '),
                0,
                entry.body.ifBlank { "—" }.replace('\n', ' ').length,
                snippetPaint,
                (contentWidth - 40f).toInt()
            )
                .setLineSpacing(0f, 1.15f)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val height = 56f + snippet.height + 18f
            ensureSpace(height)
            val top = y
            val rect = RectF(margin, top, margin + contentWidth, top + height)
            canvas?.drawRoundRect(rect, 18f, 18f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = card })
            canvas?.drawText(date, margin + 20f, top + 30f, tinyPaint)
            canvas?.drawText(
                ellipsize(
                    entry.title.ifBlank { text(R.string.pdf_untitled) },
                    bodyBoldPaint,
                    contentWidth - 180f
                ),
                margin + 130f,
                top + 30f,
                bodyBoldPaint
            )
            canvas?.save()
            canvas?.translate(margin + 20f, top + 44f)
            snippet.draw(canvas)
            canvas?.restore()
            y = top + height + 10f
        }
        y += 10f
    }

    private fun drawNotesForDoctor() {
        // Заголовок и хотя бы несколько линий должны быть на одной странице.
        ensureSpace(190f)
        section(text(R.string.pdf_section_notes))
        paragraph(text(R.string.pdf_notes_hint))
        val ruled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder
            strokeWidth = 1.2f
        }
        var lineY = y + 14f
        // Заполняем линиями остаток страницы: отдельный лист заметок не
        // должен выглядеть как случайно оставленное пустое место.
        while (lineY <= pageHeight - 110f) {
            canvas?.drawLine(margin, lineY, pageWidth - margin, lineY, ruled)
            lineY += 34f
        }
        y = lineY
    }

    private fun section(text: String) {
        ensureSpace(72f)
        y += 8f
        canvas?.drawText(text, margin, y + 24f, sectionPaint)
        canvas?.drawRoundRect(
            RectF(margin, y + 36f, margin + 44f, y + 41f),
            4f, 4f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = primary }
        )
        y += 56f
    }

    private fun paragraph(text: String) {
        val paint = TextPaint(bodyPaint).apply {
            color = ink
            textSize = 21f
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth.toInt())
            .setLineSpacing(0f, 1.2f)
            .build()
        ensureSpace(layout.height + 10f)
        val c = canvas ?: return
        c.save()
        c.translate(margin, y)
        layout.draw(c)
        c.restore()
        y += layout.height + 10f
    }

    private fun ensureSpace(height: Float) {
        if (y + height > pageHeight - 90f) newPage()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String =
        TextUtils.ellipsize(text, TextPaint(paint), maxWidth, TextUtils.TruncateAt.END).toString()

    private fun displayDosage(dosage: String): String =
        dosage.trim().replace(Regex("(\\d)\\.(\\d)"), "$1,$2")

    private fun fmt1(value: Double): String = formatDecimal(value, locale)

    private fun dash(): String = context.getString(R.string.common_dash)

    private fun text(@StringRes id: Int, vararg args: Any): String =
        context.getString(id, *args)

    private fun levelLabel(level: Int): String = context.getString(
        when (level) {
            1 -> R.string.mood_1
            2 -> R.string.mood_2
            3 -> R.string.mood_3
            4 -> R.string.mood_4
            else -> R.string.mood_5
        }
    )

    private fun textPaint(size: Float, color: Int, style: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", style)
        }
}
