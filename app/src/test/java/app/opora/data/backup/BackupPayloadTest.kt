package app.opora.data.backup

import app.opora.data.db.CustomTagEntity
import app.opora.data.db.MedicationEntity
import app.opora.data.db.MoodEntity
import app.opora.data.db.PositiveEntity
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `empty payload has version 3 and no records`() {
        val payload = BackupPayload()
        assertEquals(3, payload.formatVersion)
        assertEquals(0, payload.totalRecords)
        assertNull(payload.safetyPlan)
    }

    @Test
    fun `totalRecords counts every section and safety plan`() {
        val payload = BackupPayload(
            moods = listOf(MoodEntity(date = 20_000, mood = 3)),
            medications = listOf(
                MedicationEntity(name = "Тест", startDate = 20_000),
                MedicationEntity(name = "Тест 2", startDate = 20_000)
            ),
            safetyPlan = app.opora.data.db.SafetyPlanEntity(),
            customTags = listOf(CustomTagEntity("custom_1", "антистресс", "emotion")),
            positives = listOf(PositiveEntity("p1", 20_000, "Прогулка", "event"))
        )
        assertEquals(6, payload.totalRecords)
    }

    @Test
    fun `payload roundtrips with Cyrillic content`() {
        val payload = BackupPayload(
            moods = listOf(
                MoodEntity(
                    date = 20_000,
                    mood = 4,
                    anxiety = 3,
                    note = "Спокойный день, была прогулка"
                )
            ),
            medications = listOf(
                MedicationEntity(
                    name = "Магний",
                    dosage = "1 таблетка",
                    purpose = "Сон",
                    startDate = 20_000
                )
            )
        )

        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload))

        assertEquals(payload.moods, decoded.moods)
        assertEquals(payload.medications, decoded.medications)
        assertEquals(2, decoded.totalRecords)
    }

    @Test
    fun `foreign json is not accepted as a backup`() {
        listOf("{}", """{"foo":1}""", """{"formatVersion":2}""", "[]", "\"text\"")
            .forEach { text ->
                val error = runCatching { parseBackupPayload(json, text) }.exceptionOrNull()
                assertEquals(
                    app.opora.R.string.errors_not_a_backup,
                    (error as app.opora.data.UserMessageException).messageRes
                )
            }
    }

    @Test
    fun `legacy backup without marker is accepted`() {
        val payload = parseBackupPayload(
            json,
            """{"formatVersion":2,"exportedAt":1,"appVersion":"1.0.0"}"""
        )
        assertEquals(0, payload.totalRecords)
    }

    @Test
    fun `encoded payload carries the marker and parses again`() {
        val text = json.encodeToString(BackupPayload())
        assertTrue(text.contains("\"app\":\"opora\""))
        assertEquals(0, parseBackupPayload(json, text).totalRecords)
    }

    @Test
    fun `unknown keys from a newer version are ignored`() {
        val decoded = json.decodeFromString<BackupPayload>(
            """{"formatVersion":4,"exportedAt":1,"futureField":"x","moods":[]}"""
        )
        assertEquals(4, decoded.formatVersion)
        assertEquals(0, decoded.totalRecords)
    }

    @Test
    fun `old integer stock restores into fractional inventory`() {
        val decoded = json.decodeFromString<BackupPayload>(
            """{"formatVersion":1,"medications":[{"id":"m1","name":"Тест","dosage":"0,5 капсулы","startDate":20000,"inventoryCount":19,"refillThreshold":5}]}"""
        )
        assertEquals(19.0, decoded.medications.single().inventoryCount!!, 0.0001)
        assertEquals(5.0, decoded.medications.single().refillThreshold!!, 0.0001)
    }

    @Test
    fun `fractional stock survives a new-format roundtrip`() {
        val payload = BackupPayload(
            medications = listOf(
                MedicationEntity(
                    name = "Тест",
                    dosage = "0,5 капсулы",
                    startDate = 20_000,
                    inventoryCount = 18.5,
                    refillThreshold = 5.5
                )
            )
        )
        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload))
        assertEquals(18.5, decoded.medications.single().inventoryCount!!, 0.0001)
        assertEquals(5.5, decoded.medications.single().refillThreshold!!, 0.0001)
    }

    @Test
    fun `deducted stock snapshot survives a roundtrip`() {
        val dose = app.opora.data.db.DoseLogEntity(
            id = "dose-1",
            medicationId = "med-1",
            dosage = "1 таблетка",
            scheduledDate = 20_000,
            scheduledTime = "09:00",
            status = "taken",
            takenAt = 1_000,
            stockDeducted = 0.5
        )
        val payload = BackupPayload(doses = listOf(dose))

        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload))

        assertEquals(0.5, decoded.doses.single().stockDeducted!!, 0.0001)
    }
}
