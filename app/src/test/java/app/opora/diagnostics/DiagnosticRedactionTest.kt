package app.opora.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactionTest {

    @Test
    fun `urls with userinfo and tokens are replaced`() {
        val text = DiagnosticRedaction.scrub(
            "GET https://user:pass@example.org/private?token=abc failed"
        )
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("user:pass"))
        assertFalse(text.contains("example.org"))
        assertFalse(text.contains("token=abc"))
        assertTrue(text.contains("[redacted-url]"))
    }

    @Test
    fun `email addresses are replaced`() {
        val text = DiagnosticRedaction.scrub("Write to doctor@example.com, please")
        assertFalse(text.contains("doctor@example.com"))
        assertTrue(text.contains("[redacted-email]"))
    }

    @Test
    fun `authorization bearer and basic values are replaced`() {
        val header = DiagnosticRedaction.scrub("Authorization: Bearer abcdefghijklmnop")
        assertTrue(header.contains("Authorization=[redacted]"))
        assertFalse(header.contains("abcdefghijklmnop"))

        val proxy = DiagnosticRedaction.scrub("proxy-authorization=Basic dXNlcjpwYXNz")
        assertTrue(proxy.contains("proxy-authorization=[redacted]"))
        assertFalse(proxy.contains("dXNlcjpwYXNz"))

        val bearer = DiagnosticRedaction.scrub("Header: Bearer eyJhbGciOiJIUzI1NiJ9")
        assertTrue(bearer.contains("Bearer [redacted]"))
        assertFalse(bearer.contains("eyJhbGciOiJIUzI1NiJ9"))

        val basic = DiagnosticRedaction.scrub("Basic dXNlcjpwYXNz")
        assertTrue(basic.contains("Basic [redacted]"))
        assertFalse(basic.contains("dXNlcjpwYXNz"))
    }

    @Test
    fun `password token and api key values are replaced`() {
        val text = DiagnosticRedaction.scrub(
            "password=hunter2 token: abc123 api_key = k-999 secret=\"hidden\" client_secret=oauth"
        )
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("abc123"))
        assertFalse(text.contains("k-999"))
        assertFalse(text.contains("hidden"))
        assertFalse(text.contains("oauth"))
        assertTrue(text.contains("password=[redacted]"))
        assertTrue(text.contains("token: [redacted]"))
        assertTrue(text.contains("api_key = [redacted]"))
    }

    @Test
    fun `json style secrets are replaced`() {
        val text = DiagnosticRedaction.scrub(
            """{"password":"s3cr3t","access_token":"abc123"}"""
        )
        assertFalse(text.contains("s3cr3t"))
        assertFalse(text.contains("abc123"))
        assertTrue(text.contains("password"))
        assertTrue(text.contains("access_token"))
    }

    @Test
    fun `stack frames keep class method and line while secrets go`() {
        val trace = """
            java.lang.IllegalStateException: failed https://user:pass@example.org/x for a@b.co
                at app.opora.sync.WebDavClient.upload(WebDavClient.kt:42)
                at app.opora.SyncManager.sync(SyncManager.kt:77)
        """.trimIndent()
        val text = DiagnosticRedaction.scrubTrace(trace)
        assertTrue(text.contains("at app.opora.sync.WebDavClient.upload(WebDavClient.kt:42)"))
        assertTrue(text.contains("at app.opora.SyncManager.sync(SyncManager.kt:77)"))
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertFalse(text.contains("https://"))
        assertFalse(text.contains("user:pass"))
        assertFalse(text.contains("a@b.co"))
    }

    @Test
    fun `error summary flattens injected newlines and scrubs secrets`() {
        val summary = DiagnosticRedaction.errorSummary(
            "first line\nLast error: injected\r\nhttps://secret.example/x\ttail"
        )
        assertTrue(summary != null)
        val text = summary!!
        assertFalse(text.contains("\n"))
        assertFalse(text.contains("\r"))
        assertFalse(text.contains("\t"))
        assertFalse(text.contains("secret.example"))
        assertTrue(text.startsWith("first line Last error: injected"))
        assertTrue(text.endsWith("[redacted-url] tail"))
    }

    @Test
    fun `error summary is capped`() {
        val summary = DiagnosticRedaction.errorSummary("a".repeat(2_000))
        assertTrue(summary != null)
        assertEquals(DiagnosticRedaction.MAX_ERROR_CHARS, summary!!.length)
        assertTrue(summary.endsWith("\u2026"))
    }

    @Test
    fun `blank error summary is omitted`() {
        assertNull(DiagnosticRedaction.errorSummary(null))
        assertNull(DiagnosticRedaction.errorSummary(""))
        assertNull(DiagnosticRedaction.errorSummary(" \n\t\r"))
    }

    @Test
    fun `trace scrubbing is capped before and after replacement`() {
        val long = DiagnosticRedaction.scrubTrace("x".repeat(100_000))
        assertEquals(DiagnosticRedaction.MAX_TRACE_CHARS, long.length)

        val expanding = DiagnosticRedaction.scrubTrace("a@b.co ".repeat(20_000))
        assertTrue(expanding.length <= DiagnosticRedaction.MAX_TRACE_CHARS)
        assertFalse(expanding.contains("a@b.co"))
    }
}
