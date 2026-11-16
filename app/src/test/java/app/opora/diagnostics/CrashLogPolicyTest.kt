package app.opora.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CrashLogPolicyTest {

    @Test
    fun `first crash name has a sortable timestamp`() {
        assertEquals(
            "crash-20261114-201500.txt",
            CrashLogPolicy.fileName(LocalDateTime.of(2026, 11, 14, 20, 15, 0))
        )
    }

    @Test
    fun `rotation keeps the ten newest files and ignores foreign names`() {
        val names = (0 until 12).map { index ->
            CrashLogPolicy.fileName(LocalDateTime.of(2026, 11, 14, 20, 15, index))
        } + listOf("notes.txt", "crash-old.log", "report.pdf")
        val stale = CrashLogPolicy.staleNames(names)
        assertEquals(2, stale.size)
        assertEquals(names[1], stale[0])
        assertEquals(names[0], stale[1])
        assertFalse("notes.txt" in stale)
    }

    @Test
    fun `ten or fewer crash files are never deleted`() {
        val names = (0 until 10).map { index ->
            CrashLogPolicy.fileName(LocalDateTime.of(2026, 11, 14, 20, 15, index))
        }
        assertTrue(CrashLogPolicy.staleNames(names).isEmpty())
        assertTrue(CrashLogPolicy.staleNames(emptyList()).isEmpty())
    }

    @Test
    fun `crash names are recognised strictly`() {
        assertTrue(CrashLogPolicy.isCrashName("crash-20261114-201500.txt"))
        assertFalse(CrashLogPolicy.isCrashName("crash-.txt"))
        assertFalse(CrashLogPolicy.isCrashName("crash-20261114-201500.txt.bak"))
        assertFalse(CrashLogPolicy.isCrashName("notes.txt"))
    }

    @Test
    fun `sanitized share copies are not crash logs`() {
        assertFalse(CrashLogPolicy.isCrashName("share-1763310000000.txt"))
        assertTrue(
            CrashLogPolicy.staleNames(
                listOf("share-1763310000000.txt", "share-1763310001000.txt")
            ).isEmpty()
        )
    }
}
