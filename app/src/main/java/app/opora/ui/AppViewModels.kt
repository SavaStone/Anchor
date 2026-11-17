package app.opora.ui

import android.os.SystemClock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.opora.AppContainer
import app.opora.data.OporaRepository
import app.opora.data.db.AppointmentEntity
import app.opora.data.db.DoseLogEntity
import app.opora.data.db.JournalEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MedicationPauseEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.ScheduleSnapshotEntity
import app.opora.data.db.PositiveEntity
import app.opora.data.db.journalDate
import app.opora.data.prefs.AppSettings
import app.opora.data.prefs.SettingsRepository
import app.opora.domain.DEFAULT_HIDDEN_HOME_BLOCKS
import app.opora.domain.DEFAULT_HOME_ORDER
import app.opora.domain.DoseStreak
import app.opora.domain.DoseStreakCache
import app.opora.domain.EMOTIONS
import app.opora.domain.MoodStats
import app.opora.domain.SYMPTOMS
import app.opora.domain.TAG_TYPE_EMOTION
import app.opora.domain.TAG_TYPE_SYMPTOM
import app.opora.domain.TagDef
import app.opora.domain.csvWith
import app.opora.domain.journalMatchesQuery
import app.opora.domain.csvWithout
import app.opora.domain.moveHomeBlockInOrder
import app.opora.domain.doseStreak
import app.opora.domain.parseKeys
import app.opora.ui.text.mergeTagsForDisplay
import app.opora.ui.text.sortedTagKeysForDisplay
import app.opora.ui.text.tagLabelsForDisplay
import app.opora.work.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

fun <T : ViewModel> vmFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
    }

data class TodayUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val today: LocalDate = LocalDate.now(),
    val mood: MoodEntity? = null,
    val doses: List<DoseLogEntity> = emptyList(),
    val meds: List<MedicationEntity> = emptyList(),
    val nextAppointment: AppointmentEntity? = null,
    val doseStreak: DoseStreak = DoseStreak(),
    val settings: AppSettings = AppSettings()
)

private data class TodayExtras(
    val doseHistory: List<DoseLogEntity>,
    val forgiven: Set<Long>,
    val settings: AppSettings,
    val snapshots: List<ScheduleSnapshotEntity>,
    val pauses: List<MedicationPauseEntity>
)

/** Двойной тап по «Записать» PRN не должен создавать две дозы за один жест. */
private class PrnTapGuard(private val windowMs: Long = 800) {
    private val last = mutableMapOf<String, Long>()

    fun allow(medId: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val previous = last[medId]
        if (previous != null && now - previous < windowMs) return false
        last[medId] = now
        return true
    }
}

class TodayViewModel(private val repo: OporaRepository) : ViewModel() {
    private val prnTaps = PrnTapGuard()
    private val streakCache = DoseStreakCache()
    /** Дата, к которой привязаны приёмы и отметка настроения. Меняется после полуночи. */
    private val todayDate = MutableStateFlow(LocalDate.now())
    // Серия приёма зависит от текущего времени: вечерний приём становится
    // обязательным в свой час, поэтому пересчитываем карточку раз в минуту.
    private val clock = MutableStateFlow(LocalDateTime.now())

    val state: StateFlow<TodayUiState> = todayDate.flatMapLatest { today ->
        // combine поддерживает до пяти разнотипных потоков — историю приёмов,
        // прощённые дни и настройки объединяем заранее.
        val extras = combine(
            repo.doseHistory(400, today),
            repo.forgivenDoseDays(),
            repo.settings.settings,
            repo.scheduleSnapshots(),
            repo.medicationPauses()
        ) { doseHistory, forgiven, settings, snapshots, pauses ->
            TodayExtras(doseHistory, forgiven, settings, snapshots, pauses)
        }
        // Часы меняются раз в минуту, но Room-потоки не пересоздаются:
        // clock живёт внутри inner combine, а не в flatMapLatest.
        val timedExtras = combine(extras, clock) { extra, now -> extra to now }
        combine(
            repo.moodEntries(),
            repo.todayDoses(today),
            // Все лекарства, включая прекращённые: серия приёма считает их
            // до даты прекращения, а история доз берёт из них форму.
            repo.medications(),
            repo.appointments(),
            timedExtras
        ) { moods, doses, meds, appointments, timed ->
            val (extra, now) = timed
            val todayEpoch = today.toEpochDay()
            val moodsToday = moods.filter { it.date == todayEpoch }.sortedBy { it.createdAt }
            val cache = streakCache.statesFor(
                meds, extra.doseHistory, extra.forgiven, extra.snapshots,
                extra.pauses, today
            )
            TodayUiState(
                loading = false,
                userName = extra.settings.userName,
                today = today,
                mood = moodsToday.lastOrNull(),
                doses = doses.sortedBy { it.scheduledTime },
                meds = meds,
                nextAppointment = appointments
                    .filter { !it.done && it.startsAt >= System.currentTimeMillis() - 30 * 60_000L }
                    .minByOrNull { it.startsAt },
                doseStreak = doseStreak(
                    meds = meds,
                    logs = extra.doseHistory,
                    forgivenDays = extra.forgiven,
                    today = today,
                    now = now,
                    snapshots = extra.snapshots,
                    pauses = extra.pauses,
                    dayCache = cache
                ),
                settings = extra.settings
            )
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val next = LocalDateTime.now().withSecond(0).withNano(0)
                if (next != clock.value) clock.value = next
            }
        }
    }

    fun refresh() {
        val today = LocalDate.now()
        if (todayDate.value != today) todayDate.value = today
        clock.value = LocalDateTime.now()
        viewModelScope.launch { runCatching { repo.ensureTodayLogs(today) } }
    }

    fun take(logId: String) = viewModelScope.launch { repo.setDoseStatus(logId, "taken") }
    fun skip(logId: String) = viewModelScope.launch { repo.setDoseStatus(logId, "skipped") }
    fun undo(logId: String) = viewModelScope.launch { repo.setDoseStatus(logId, "pending") }
    fun setDoseTime(logId: String, time: LocalTime) =
        viewModelScope.launch { repo.updateDoseTime(logId, time) }
    fun deleteDose(logId: String) = viewModelScope.launch { repo.deletePrnDose(logId) }
    fun snooze(logId: String) = viewModelScope.launch { repo.snoozeDose(logId) }
    fun prn(medId: String) {
        if (!prnTaps.allow(medId)) return
        viewModelScope.launch { repo.logPrn(medId) }
    }

    fun pause(medId: String) = viewModelScope.launch { repo.pauseMedication(medId) }
    fun resume(medId: String) = viewModelScope.launch { repo.resumeMedication(medId) }
    fun discontinue(medId: String) = viewModelScope.launch { repo.discontinueMedication(medId) }
    fun deleteMedication(medId: String) = viewModelScope.launch { repo.deleteMedication(medId) }
}

data class MoodUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val monthEntries: List<MoodEntity> = emptyList(),
    val selectedEntries: List<MoodEntity> = emptyList(),
    val allEntries: List<MoodEntity> = emptyList(),
    val hasEntries: Boolean = false,
    val stats30: MoodStats = MoodStats(),
    val insights: MoodInsights = MoodInsights(),
    val emotionTags: List<TagDef> = EMOTIONS,
    val symptomTags: List<TagDef> = SYMPTOMS
)

enum class InsightNote { NEED_MORE, BETTER, LOWER, STABLE }

data class MoodInsights(
    val weekAverage: Float? = null,
    val previousAverage: Float? = null,
    val deltaPercent: Int? = null,
    val topEmotionKey: String? = null,
    val bestWeekday: java.time.DayOfWeek? = null,
    val note: InsightNote? = null
)

class MoodViewModel(private val repo: OporaRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())
    private val todayDate = MutableStateFlow(LocalDate.now())

    val state: StateFlow<MoodUiState> = combine(
        repo.moodEntries(), repo.customTags(), month, selected, todayDate
    ) { all, customTags, selectedMonth, selectedDate, today ->
        val emotionTags = mergeTagsForDisplay(
            EMOTIONS,
            customTags.filter { it.type == TAG_TYPE_EMOTION }.map { TagDef(it.key, it.label) },
            repo.appContext
        )
        val symptomTags = mergeTagsForDisplay(
            SYMPTOMS,
            customTags.filter { it.type == TAG_TYPE_SYMPTOM }.map { TagDef(it.key, it.label) },
            repo.appContext
        )
        val rangeStart = today.minusDays(29)
        val last30 = all.filter { it.date >= rangeStart.toEpochDay() && it.date <= today.toEpochDay() }
        val stats = repo.moodStats(last30, today)
        val week = all.filter { it.date > today.minusDays(7).toEpochDay() }
        val prev = all.filter {
            it.date in today.minusDays(14).toEpochDay()..today.minusDays(8).toEpochDay()
        }
        val weekAvg = week.map { it.mood }.average().takeIf { !it.isNaN() }?.toFloat()
        val prevAvg = prev.map { it.mood }.average().takeIf { !it.isNaN() }?.toFloat()
        val delta = if (weekAvg != null && prevAvg != null && prevAvg > 0) {
            ((weekAvg - prevAvg) / prevAvg * 100).toInt()
        } else null
        val weekdayAvgs = last30.groupBy { LocalDate.ofEpochDay(it.date).dayOfWeek }
            .mapValues { it.value.map { m -> m.mood }.average() }
        val bestDay = weekdayAvgs.maxByOrNull { it.value }?.key
        MoodUiState(
            loading = false,
            today = today,
            month = selectedMonth,
            selectedDate = selectedDate,
            monthEntries = all.filter {
                val d = LocalDate.ofEpochDay(it.date)
                YearMonth.from(d) == selectedMonth
            },
            selectedEntries = all.filter { it.date == selectedDate.toEpochDay() }
                .sortedBy { it.createdAt },
            allEntries = last30,
            hasEntries = all.isNotEmpty(),
            stats30 = stats,
            insights = MoodInsights(
                weekAverage = weekAvg,
                previousAverage = prevAvg,
                deltaPercent = delta,
                topEmotionKey = stats.topEmotions.firstOrNull()?.first,
                bestWeekday = bestDay,
                note = when {
                    stats.count == 0 -> null
                    stats.count < 3 -> InsightNote.NEED_MORE
                    delta != null && delta >= 5 -> InsightNote.BETTER
                    delta != null && delta <= -5 -> InsightNote.LOWER
                    else -> InsightNote.STABLE
                }
            ),
            emotionTags = emotionTags,
            symptomTags = symptomTags
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoodUiState())

    fun refresh() {
        todayDate.value = LocalDate.now()
    }

    fun changeMonth(delta: Int) {
        val target = month.value.plusMonths(delta.toLong())
        val current = selected.value
        month.value = target
        selected.value = when {
            YearMonth.from(current) == target -> current
            target == YearMonth.now() -> LocalDate.now()
            // Сохраняем привычное число месяца, насколько оно существует в новом месяце.
            else -> target.atDay(minOf(current.dayOfMonth, target.lengthOfMonth()))
        }
    }

    fun select(date: LocalDate) {
        selected.value = date
    }

    suspend fun save(
        date: LocalDate,
        mood: Int,
        anxiety: Int?,
        energy: Int?,
        sleepMinutes: Int?,
        emotions: Collection<String>,
        symptoms: Collection<String>,
        note: String,
        entryId: Long? = null,
        occurredAt: Long? = null
    ): Long {
        val id = repo.saveMood(
            date, mood, anxiety, energy, sleepMinutes, emotions, symptoms, note,
            entryId, occurredAt
        )
        selected.value = date
        month.value = YearMonth.from(date)
        todayDate.value = LocalDate.now()
        return id
    }

    /** Удаляет в scope вызывающего, чтобы диалог успел показать ошибку до закрытия. */
    suspend fun deleteNow(id: Long) = repo.deleteMood(id)

    suspend fun entry(id: Long): MoodEntity? = repo.moodById(id)

    suspend fun addCustomTag(type: String, label: String): String? = repo.addCustomTag(type, label)

    fun positives(date: LocalDate): Flow<List<PositiveEntity>> = repo.positives(date)
    fun addPositive(date: LocalDate, text: String, kind: String) =
        viewModelScope.launch { repo.addPositive(date, text, kind) }

    fun setPositiveDone(id: String, done: Boolean) =
        viewModelScope.launch { repo.setPositiveDone(id, done) }

    fun deletePositive(id: String) = viewModelScope.launch { repo.deletePositive(id) }
}

data class MedsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val meds: List<MedicationEntity> = emptyList(),
    val todayDoses: List<DoseLogEntity> = emptyList(),
    val history: List<DoseLogEntity> = emptyList(),
    val doseStreak: DoseStreak = DoseStreak(),
    val forgivenDays: Set<Long> = emptySet(),
    val nextDose: Pair<MedicationEntity, LocalDateTime>? = null
)

class MedsViewModel(private val repo: OporaRepository) : ViewModel() {
    private val prnTaps = PrnTapGuard()
    private val streakCache = DoseStreakCache()
    private val todayDate = MutableStateFlow(LocalDate.now())
    private val reloadKey = MutableStateFlow(0)
    private val clock = MutableStateFlow(LocalDateTime.now())

    val state: StateFlow<MedsUiState> =
        combine(todayDate, reloadKey) { day, _ -> day }
            .flatMapLatest { day ->
                val extras = combine(
                    repo.forgivenDoseDays(),
                    repo.scheduleSnapshots(),
                    repo.medicationPauses()
                ) { forgiven, snapshots, pauses -> Triple(forgiven, snapshots, pauses) }
                val timedExtras = combine(extras, clock) { extra, now -> extra to now }
                combine(
                    repo.medications(),
                    repo.todayDoses(day),
                    repo.doseHistory(30, day),
                    repo.doseHistory(400, day),
                    timedExtras
                ) { meds, doses, history, longHistory, timed ->
                    val (extra, now) = timed
                    val (forgiven, snapshots, pauses) = extra
                    val cache = streakCache.statesFor(meds, longHistory, forgiven, snapshots, pauses, day)
                    MedsUiState(
                        loading = false,
                        today = day,
                        meds = meds,
                        todayDoses = doses.sortedBy { it.scheduledTime },
                        history = history,
                        doseStreak = doseStreak(
                            meds = meds,
                            logs = longHistory,
                            forgivenDays = forgiven,
                            today = day,
                            now = now,
                            snapshots = snapshots,
                            pauses = pauses,
                            dayCache = cache
                        ),
                        forgivenDays = forgiven,
                        nextDose = repo.nextDose()
                    )
                }.catch { emit(MedsUiState(loading = false, error = true, today = day)) }
            }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedsUiState())

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val next = LocalDateTime.now().withSecond(0).withNano(0)
                if (next != clock.value) clock.value = next
            }
        }
    }

    /** Пересоздаёт приёмы на сегодня и перечитывает данные, если дата сменилась. */
    fun refresh() {
        val today = LocalDate.now()
        if (todayDate.value != today) todayDate.value = today
        clock.value = LocalDateTime.now()
        viewModelScope.launch { runCatching { repo.ensureTodayLogs(today) } }
    }

    fun retry() {
        reloadKey.value += 1
        refresh()
    }

    fun take(logId: String) = viewModelScope.launch { repo.setDoseStatus(logId, "taken") }
    fun skip(logId: String) = viewModelScope.launch { repo.setDoseStatus(logId, "skipped") }
    fun undo(logId: String) = viewModelScope.launch { repo.setDoseStatus(logId, "pending") }
    fun setDoseTime(logId: String, time: LocalTime) =
        viewModelScope.launch { repo.updateDoseTime(logId, time) }
    fun deleteDose(logId: String) = viewModelScope.launch { repo.deletePrnDose(logId) }
    fun snooze(logId: String) = viewModelScope.launch { repo.snoozeDose(logId) }
    fun prn(medId: String) {
        if (!prnTaps.allow(medId)) return
        viewModelScope.launch { repo.logPrn(medId) }
    }
    fun adjustStock(medId: String, delta: Int) =
        viewModelScope.launch { repo.adjustInventory(medId, delta.toDouble()) }

    fun discontinue(medId: String, reason: String) =
        viewModelScope.launch { repo.discontinueMedication(medId, reason) }
    fun resume(medId: String) = viewModelScope.launch { repo.resumeMedication(medId) }
    fun pause(medId: String) = viewModelScope.launch { repo.pauseMedication(medId) }
    fun deleteMedication(medId: String) = viewModelScope.launch { repo.deleteMedication(medId) }

    fun forgiveDay(date: LocalDate) = viewModelScope.launch { repo.forgiveDoseDay(date) }
    fun unforgiveDay(date: LocalDate) = viewModelScope.launch { repo.unforgiveDoseDay(date) }
}

data class JournalUiState(
    val loading: Boolean = true,
    val entries: List<JournalEntity> = emptyList(),
    val tags: List<String> = emptyList(),
    val tagLabels: Map<String, String> = emptyMap(),
    /** Отметки настроения по дням: дневник показывает их как контекст, но не редактирует. */
    val moodsByDate: Map<Long, List<MoodEntity>> = emptyMap(),
    val query: String = "",
    val activeTag: String? = null
)

class JournalViewModel(private val repo: OporaRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val tag = MutableStateFlow<String?>(null)

    val state: StateFlow<JournalUiState> = combine(
        repo.journalEntries(), repo.customTags(), repo.moodEntries(), query, tag
    ) { entries, customTags, moods, q, activeTag ->
        val labels = tagLabelsForDisplay(
            repo.appContext,
            EMOTIONS,
            SYMPTOMS,
            customTags.map { TagDef(it.key, it.label) }
        )
        // Built-in labels are searchable in both languages: an English UI still
        // finds a note tagged "anxiety" by the Russian word and vice versa.
        val searchLabels = labels + (EMOTIONS + SYMPTOMS).associate {
            it.key to "${labels[it.key] ?: it.label} ${it.label}"
        }
        // Эмоции и симптомы из связанной отметки тоже участвуют в поиске.
        val moodTags = moods.associate { mood ->
            mood.id to (parseKeys(mood.emotions) + parseKeys(mood.symptoms))
        }
        // Эмоции и симптомы, отмеченные в «состоянии дня» из дневника,
        // относятся к записи по дате, даже если прямая ссылка не сохранилась.
        val moodTagsByDate = moods.groupBy { it.date }.mapValues { (_, dayMoods) ->
            dayMoods.flatMap { parseKeys(it.emotions) + parseKeys(it.symptoms) }.distinct()
        }
        val filtered = entries.filter { entry ->
            val entryTags = parseKeys(entry.tags)
            val linkedMoodTags = entry.moodEntryId?.let { moodTags[it] }
                ?: moodTagsByDate[entry.journalDate().toEpochDay()].orEmpty()
            val matchesQuery = journalMatchesQuery(
                title = entry.title,
                body = entry.body,
                tagKeys = entryTags + linkedMoodTags,
                tagLabels = searchLabels,
                query = q,
                normalizedText = entry.searchText.ifBlank {
                    app.opora.domain.normalizeForSearch(entry.title + "\n" + entry.body)
                }
            )
            val matchesTag = activeTag == null ||
                entryTags.any { it.equals(activeTag, ignoreCase = true) }
            matchesQuery && matchesTag
        }
        JournalUiState(
            loading = false,
            entries = filtered.sortedWith(
                compareByDescending<JournalEntity> { it.journalDate().toEpochDay() }
                    .thenByDescending { it.updatedAt }
            ),
            tags = sortedTagKeysForDisplay(
                entries.flatMap { parseKeys(it.tags) },
                labels,
                repo.appContext
            ),
            tagLabels = labels,
            moodsByDate = moods.groupBy { it.date }
                .mapValues { (_, dayMoods) -> dayMoods.sortedBy { it.createdAt } },
            query = q,
            activeTag = activeTag
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun search(value: String) { query.value = value }
    fun filterTag(value: String?) { tag.value = value }
    /** Удаление без ожидания результата; оставлено для прежних вызовов. */
    fun delete(id: String) = viewModelScope.launch { repo.deleteJournal(id) }

    /** Удаляет в scope вызывающего, чтобы диалог успел показать ошибку до закрытия. */
    suspend fun deleteNow(id: String) = repo.deleteJournal(id)

    suspend fun load(id: String): JournalEntity? = repo.journalEntry(id)
    suspend fun save(entry: JournalEntity): JournalEntity = repo.saveJournalText(entry)
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val workScheduler: WorkScheduler,
    private val alarms: app.opora.notifications.MedicationAlarms,
    private val repository: OporaRepository
) : ViewModel() {
    val state: StateFlow<AppSettings> =
        settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun update(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settings.block() }
    }

    fun setMoodReminder(enabled: Boolean, time: String) {
        viewModelScope.launch {
            settings.setMoodReminder(enabled, time)
            workScheduler.scheduleDailyMood(enabled, time)
        }
    }

    fun setMedReminders(enabled: Boolean) {
        viewModelScope.launch {
            settings.setMedReminders(enabled)
            if (!enabled) repository.clearMedicationReminders()
            alarms.scheduleAll()
        }
    }

    fun setTheme(mode: String) = update { setThemeMode(mode) }
    fun setDynamic(enabled: Boolean) = update { setDynamicColor(enabled) }
    fun setLeftHanded(enabled: Boolean) = update { setLeftHanded(enabled) }

    fun setHomeBlockVisible(key: String, visible: Boolean) {
        viewModelScope.launch {
            val current = settings.current()
            settings.setHiddenHomeBlocks(
                if (visible) csvWithout(current.hiddenHomeBlocks, key)
                else csvWith(current.hiddenHomeBlocks, key)
            )
        }
    }

    fun moveHomeBlock(key: String, delta: Int) {
        viewModelScope.launch {
            val current = settings.current()
            settings.setHomeBlocks(moveHomeBlockInOrder(current.homeBlocks, key, delta))
        }
    }

    fun resetHomeLayout() {
        viewModelScope.launch {
            settings.setHomeBlocks(DEFAULT_HOME_ORDER)
            settings.setHiddenHomeBlocks(DEFAULT_HIDDEN_HOME_BLOCKS)
        }
    }

    fun setTabVisible(route: String, visible: Boolean) {
        viewModelScope.launch {
            val current = settings.current()
            settings.setHiddenTabs(
                if (visible) csvWithout(current.hiddenTabs, route)
                else csvWith(current.hiddenTabs, route)
            )
        }
    }
    fun setAppLock(enabled: Boolean) = update { setAppLock(enabled) }
    fun setSecureScreen(enabled: Boolean) = update { setSecureScreen(enabled) }
    fun setUserName(name: String) = update { setUserName(name) }
    fun setRefillReminders(enabled: Boolean) = update { setRefillReminders(enabled) }
    fun setAutoSync(enabled: Boolean) = update { setAutoSync(enabled) }
    fun setWebDav(url: String, user: String, password: String?) =
        update { setWebDav(url, user, password) }
}

class AppointmentsViewModel(private val repo: OporaRepository) : ViewModel() {
    val appointments: StateFlow<List<AppointmentEntity>> = repo.appointments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun save(entry: AppointmentEntity): String = repo.saveAppointment(entry)
    fun delete(id: String) = viewModelScope.launch { repo.deleteAppointment(id) }
}
