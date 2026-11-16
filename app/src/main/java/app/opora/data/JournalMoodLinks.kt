package app.opora.data

import app.opora.data.db.JournalDao
import app.opora.data.db.MoodDao
import app.opora.data.db.MoodEntity
import app.opora.data.db.journalDate

/**
 * Создаёт отметки настроения для записей дневника, у которых ещё нет связи
 * `moodEntryId` — так приходят копии, созданные до исправления. Отметка
 * получает дату записи и исходные timestamps, чтобы не менять историю.
 */
internal suspend fun linkJournalMoods(journalDao: JournalDao, moodDao: MoodDao) {
    val now = System.currentTimeMillis()
    journalDao.getAll().forEach { entry ->
        val mood = entry.mood ?: return@forEach
        // Ссылка может быть битой: копия без этой отметки или слияние
        // поколений. Тогда создаём новую отметку вместо висячего id.
        if (entry.moodEntryId?.let { moodDao.byId(it) } != null) return@forEach
        val moodId = moodDao.insert(
            MoodEntity(
                date = entry.journalDate().toEpochDay(),
                mood = mood.coerceIn(1, 5),
                createdAt = entry.createdAt,
                updatedAt = now
            )
        )
        journalDao.upsert(entry.copy(moodEntryId = moodId))
    }
}
