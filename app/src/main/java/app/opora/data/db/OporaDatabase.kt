package app.opora.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MoodEntity::class,
        MedicationEntity::class,
        DoseLogEntity::class,
        JournalEntity::class,
        CustomTagEntity::class,
        PositiveEntity::class,
        AppointmentEntity::class,
        SafetyPlanEntity::class,
        ForgivenDoseDayEntity::class,
        ScheduleSnapshotEntity::class,
        MedicationPauseEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class OporaDatabase : RoomDatabase() {
    abstract fun moodDao(): MoodDao
    abstract fun medicationDao(): MedicationDao
    abstract fun doseDao(): DoseDao
    abstract fun journalDao(): JournalDao
    abstract fun customTagDao(): CustomTagDao
    abstract fun positiveDao(): PositiveDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun safetyPlanDao(): SafetyPlanDao
    abstract fun forgivenDoseDayDao(): ForgivenDoseDayDao
    abstract fun scheduleSnapshotDao(): ScheduleSnapshotDao
    abstract fun medicationPauseDao(): MedicationPauseDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remove duplicate dose logs created by concurrent ensureTodayLogs calls.
                db.execSQL(
                    """
                    DELETE FROM dose_logs WHERE rowid NOT IN (
                        SELECT MIN(rowid) FROM dose_logs
                        GROUP BY medicationId, scheduledDate, scheduledTime, isPrn
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "`index_dose_logs_medicationId_scheduledDate_scheduledTime_isPrn` " +
                        "ON `dose_logs` (`medicationId`, `scheduledDate`, `scheduledTime`, `isPrn`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Unique (medication, date, time, isPrn) запрещал две PRN-дозы
                // в одну минуту. Идемпотентность плановых доз уже обеспечивает
                // детерминированный id, поэтому индекс больше не нужен.
                db.execSQL(
                    "DROP INDEX IF EXISTS " +
                        "`index_dose_logs_medicationId_scheduledDate_scheduledTime_isPrn`"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `custom_tags` (" +
                        "`key` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "PRIMARY KEY(`key`))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Несколько отметок настроения за день: дата больше не уникальна.
                db.execSQL("DROP INDEX IF EXISTS `index_mood_entries_date`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_mood_entries_date` " +
                        "ON `mood_entries` (`date`)"
                )
                // Дата записи дневника; null у старых записей — берётся из createdAt.
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `entryDate` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `positive_entries` (" +
                        "`id` TEXT NOT NULL, " +
                        "`date` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_positive_entries_date` " +
                        "ON `positive_entries` (`date`)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Запас стал дробным: 0,5 таблетки должно списывать 0,5, а не 1.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medications_new` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `dosage` TEXT NOT NULL, " +
                        "`form` TEXT NOT NULL, `color` INTEGER NOT NULL, `purpose` TEXT NOT NULL, " +
                        "`prescriber` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                        "`scheduleType` TEXT NOT NULL, `timesCsv` TEXT NOT NULL, " +
                        "`weekdaysCsv` TEXT NOT NULL, `intervalDays` INTEGER NOT NULL, " +
                        "`startDate` INTEGER NOT NULL, `endDate` INTEGER, " +
                        "`inventoryCount` REAL, `refillThreshold` REAL, " +
                        "`active` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "INSERT INTO `medications_new` (" +
                        "`id`, `name`, `dosage`, `form`, `color`, `purpose`, `prescriber`, " +
                        "`notes`, `scheduleType`, `timesCsv`, `weekdaysCsv`, `intervalDays`, " +
                        "`startDate`, `endDate`, `inventoryCount`, `refillThreshold`, " +
                        "`active`, `createdAt`, `updatedAt`) " +
                        "SELECT `id`, `name`, `dosage`, `form`, `color`, `purpose`, `prescriber`, " +
                        "`notes`, `scheduleType`, `timesCsv`, `weekdaysCsv`, `intervalDays`, " +
                        "`startDate`, `endDate`, `inventoryCount`, `refillThreshold`, " +
                        "`active`, `createdAt`, `updatedAt` FROM `medications`"
                )
                db.execSQL("DROP TABLE `medications`")
                db.execSQL("ALTER TABLE `medications_new` RENAME TO `medications`")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `forgiven_dose_days` (" +
                        "`date` INTEGER NOT NULL, PRIMARY KEY(`date`))"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Связь записи дневника с отметкой настроения: настроение,
                // отмеченное в дневнике, теперь попадает в раздел «Настроение».
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `moodEntryId` INTEGER")

                // Записи, созданные до этого исправления, получают отметку сразу,
                // без повторного открытия и сохранения редактора.
                data class JournalMoodSeed(
                    val journalId: String,
                    val mood: Int,
                    val date: Long,
                    val createdAt: Long
                )
                val seeds = mutableListOf<JournalMoodSeed>()
                db.query(
                    "SELECT `id`, `mood`, `entryDate`, `createdAt` FROM `journal_entries` " +
                        "WHERE `mood` IS NOT NULL AND `moodEntryId` IS NULL"
                ).use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val moodIndex = cursor.getColumnIndexOrThrow("mood")
                    val dateIndex = cursor.getColumnIndexOrThrow("entryDate")
                    val createdIndex = cursor.getColumnIndexOrThrow("createdAt")
                    while (cursor.moveToNext()) {
                        val createdAt = cursor.getLong(createdIndex)
                        val date = if (cursor.isNull(dateIndex)) {
                            java.time.Instant.ofEpochMilli(createdAt)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toLocalDate().toEpochDay()
                        } else {
                            cursor.getLong(dateIndex)
                        }
                        seeds += JournalMoodSeed(
                            journalId = cursor.getString(idIndex),
                            mood = cursor.getInt(moodIndex),
                            date = date,
                            createdAt = createdAt
                        )
                    }
                }
                seeds.forEach { seed ->
                    db.execSQL(
                        "INSERT INTO `mood_entries` (`date`, `mood`, `anxiety`, `energy`, " +
                            "`sleepMinutes`, `emotions`, `symptoms`, `note`, `createdAt`, `updatedAt`) " +
                            "VALUES (?, ?, NULL, NULL, NULL, '', '', '', ?, ?)",
                        arrayOf<Any?>(
                            seed.date,
                            seed.mood.coerceIn(1, 5),
                            seed.createdAt,
                            seed.createdAt
                        )
                    )
                    val moodId = db.query("SELECT last_insert_rowid()").use { inserted ->
                        inserted.moveToFirst()
                        inserted.getLong(0)
                    }
                    db.execSQL(
                        "UPDATE `journal_entries` SET `moodEntryId` = ? WHERE `id` = ?",
                        arrayOf<Any?>(moodId, seed.journalId)
                    )
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Сколько единиц реально списал приём: отмена не должна
                // создавать запас, если он уже был пуст или меньше дозы.
                db.execSQL("ALTER TABLE `dose_logs` ADD COLUMN `stockDeducted` REAL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Прекращение приёма: дата и причина хранятся рядом с active,
                // чтобы история приёмов и отчёты не зависели от удаления записи.
                db.execSQL("ALTER TABLE `medications` ADD COLUMN `discontinuedAt` INTEGER")
                db.execSQL(
                    "ALTER TABLE `medications` ADD COLUMN `discontinuedReason` TEXT NOT NULL DEFAULT ''"
                )
            }
        }


        internal val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `schedule_snapshots` (" +
                        "`date` INTEGER NOT NULL, `medicationId` TEXT NOT NULL, " +
                        "`scheduleType` TEXT NOT NULL, `timesCsv` TEXT NOT NULL, " +
                        "`weekdaysCsv` TEXT NOT NULL, `intervalDays` INTEGER NOT NULL, " +
                        "`active` INTEGER NOT NULL, `assumed` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`date`, `medicationId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_schedule_snapshots_medicationId` " +
                        "ON `schedule_snapshots` (`medicationId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_pauses` (" +
                        "`id` TEXT NOT NULL, `medicationId` TEXT NOT NULL, " +
                        "`startAt` INTEGER NOT NULL, `endAt` INTEGER, " +
                        "`assumed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_medication_pauses_medicationId` " +
                        "ON `medication_pauses` (`medicationId`)"
                )

                // Единственное, что известно о прошлом, — сами дозы и текущая
                // карточка лекарства. Снимок помечаем assumed=1, чтобы не путать
                // его с точной записью, сделанной при генерации.
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR REPLACE INTO `schedule_snapshots` (" +
                        "`date`, `medicationId`, `scheduleType`, `timesCsv`, " +
                        "`weekdaysCsv`, `intervalDays`, `active`, `assumed`, " +
                        "`createdAt`, `updatedAt`) " +
                        "SELECT d.`scheduledDate`, d.`medicationId`, " +
                        "COALESCE(m.`scheduleType`, 'daily'), " +
                        "GROUP_CONCAT(DISTINCT d.`scheduledTime`), " +
                        "COALESCE(m.`weekdaysCsv`, ''), COALESCE(m.`intervalDays`, 1), " +
                        "COALESCE(m.`active`, 1), 1, ?, ? " +
                        "FROM `dose_logs` d LEFT JOIN `medications` m ON m.`id` = d.`medicationId` " +
                        "WHERE d.`isPrn` = 0 " +
                        "GROUP BY d.`scheduledDate`, d.`medicationId`",
                    arrayOf<Any?>(now, now)
                )

                // Для уже стоящих на паузе лекарств точную дату начала взять
                // неоткуда; updatedAt — лучшая доступная оценка. Помечаем assumed.
                db.execSQL(
                    "INSERT OR REPLACE INTO `medication_pauses` (" +
                        "`id`, `medicationId`, `startAt`, `endAt`, `assumed`, " +
                        "`createdAt`, `updatedAt`) " +
                        "SELECT 'pause-backfill-' || `id`, `id`, " +
                        "CASE WHEN `updatedAt` > 0 THEN `updatedAt` ELSE `createdAt` END, " +
                        "NULL, 1, ?, ? " +
                        "FROM `medications` " +
                        "WHERE `active` = 0 AND `discontinuedAt` IS NULL",
                    arrayOf<Any?>(now, now)
                )
            }
        }


        internal val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `journal_entries` ADD COLUMN `searchText` TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_journal_entries_searchText` " +
                        "ON `journal_entries` (`searchText`)"
                )
                // Нормализуем старые записи теми же правилами, что и новые.
                db.query("SELECT `id`, `title`, `body` FROM `journal_entries`").use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow("id")
                    val titleIndex = cursor.getColumnIndexOrThrow("title")
                    val bodyIndex = cursor.getColumnIndexOrThrow("body")
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(idIndex)
                        val title = cursor.getString(titleIndex).trim()
                        val body = cursor.getString(bodyIndex).trimEnd()
                        val normalized = app.opora.domain.normalizeForSearch(title + "\n" + body)
                        db.execSQL(
                            "UPDATE `journal_entries` SET `searchText` = ? WHERE `id` = ?",
                            arrayOf<Any?>(normalized, id)
                        )
                    }
                }
            }
        }

        fun build(context: Context): OporaDatabase =
            Room.databaseBuilder(context, OporaDatabase::class.java, "opora.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11
                )
                .build()
    }
}
