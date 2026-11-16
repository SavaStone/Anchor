package app.opora.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class AutoBackupPolicyTest {

    @Test
    fun `first backup uses timestamped opora name`() {
        val first = AutoBackupPolicy.fileName(LocalDateTime.of(2026, 11, 14, 15, 40))
        assertEquals("opora-auto-20261114-1540.opora", first)
        val second = AutoBackupPolicy.fileName(LocalDateTime.of(2026, 12, 1, 9, 5))
        assertEquals("opora-auto-20261201-0905.opora", second)
    }

    @Test
    fun `savedAt reads the timestamp from the name in the given zone`() {
        val stamp = LocalDateTime.of(2026, 11, 14, 15, 40)
        val name = AutoBackupPolicy.fileName(stamp)
        assertEquals(
            stamp.toInstant(ZoneOffset.UTC).toEpochMilli(),
            AutoBackupPolicy.savedAt(name, ZoneOffset.UTC)
        )
    }

    @Test
    fun `foreign or damaged names have no date`() {
        assertEquals(null, AutoBackupPolicy.savedAt("random.opora", ZoneOffset.UTC))
        assertEquals(null, AutoBackupPolicy.savedAt("opora-auto-2026.opora", ZoneOffset.UTC))
        assertEquals(null, AutoBackupPolicy.savedAt("opora-auto-20261340-9900.opora", ZoneOffset.UTC))
    }

    @Test
    fun `hourly throttle allows the first run and then waits an hour`() {
        val now = 1_000_000_000L
        assertTrue(AutoBackupPolicy.isDue(0L, now))
        assertTrue(AutoBackupPolicy.isDue(now - AutoBackupPolicy.MIN_INTERVAL_MS, now))
        assertFalse(AutoBackupPolicy.isDue(now - 59 * 60 * 1000L, now))
        assertFalse(AutoBackupPolicy.isDue(now + 1, now))
    }

    @Test
    fun `retention keeps the seven newest backups and deletes the rest`() {
        val names = (0 until 9).map { day ->
            AutoBackupPolicy.fileName(LocalDateTime.of(2026, 11, 1 + day, 12, 0))
        } + listOf("notes.txt", "auto-20260101.opora")
        val stale = AutoBackupPolicy.staleNames(names)
        assertEquals(2, stale.size)
        assertEquals(names[1], stale[0])
        assertEquals(names[0], stale[1])
        assertFalse("auto-20260101.opora" in stale)
    }

    @Test
    fun `seven or fewer backups are never deleted`() {
        val names = (0 until 7).map {
            AutoBackupPolicy.fileName(LocalDateTime.of(2026, 11, 1 + it, 12, 0))
        }
        assertTrue(AutoBackupPolicy.staleNames(names).isEmpty())
        assertTrue(AutoBackupPolicy.staleNames(emptyList()).isEmpty())
    }

    @Test
    fun `backup names are recognised strictly`() {
        assertTrue(AutoBackupPolicy.isBackupName("opora-auto-20261114-1540.opora"))
        assertFalse(AutoBackupPolicy.isBackupName("opora-auto-.opora"))
        assertFalse(AutoBackupPolicy.isBackupName("opora-auto-20261114-1540.opora.bak"))
        assertFalse(AutoBackupPolicy.isBackupName("auto-20261114.opora"))
    }
}
