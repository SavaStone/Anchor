package app.opora.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.opora.R
import app.opora.data.UserMessageException
import app.opora.data.db.MedicationEntity
import app.opora.data.db.OporaDatabase
import app.opora.data.prefs.SettingsRepository
import app.opora.security.BackupCrypto
import app.opora.work.WorkScheduler
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class WebDavTransportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: OporaDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var backup: BackupManager
    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, OporaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        backup = BackupManager(context, db, settings)
        val held = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val certs = HandshakeCertificates.Builder()
            .heldCertificate(held)
            .addTrustedCertificate(held.certificate)
            .build()
        client = OkHttpClient.Builder()
            .sslSocketFactory(certs.sslSocketFactory(), certs.trustManager)
            .build()
        server = MockWebServer().apply {
            protocols = listOf(Protocol.HTTP_1_1)
            useHttps(certs.sslSocketFactory(), false)
            start()
        }
    }

    @After
    fun tearDown() = runBlocking {
        server.shutdown()
        settings.clearWebDav()
        db.close()
    }

    private fun sync() = SyncManager(context, db, settings, backup) { url, user, password ->
        WebDavClient(url, user, password, client)
    }

    @Test
    fun uploadUsesBackupPasswordAndDownloadRestoresPayload() = runBlocking {
        settings.setWebDav(
            server.url("/opora").toString(), "user", "webdav-pass", "backup-pass"
        )
        db.medicationDao().upsert(
            MedicationEntity(name = "Через WebDAV", startDate = LocalDate.now().toEpochDay())
        )
        server.enqueue(MockResponse().setResponseCode(201)) // MKCOL
        server.enqueue(MockResponse().setResponseCode(201)) // PUT

        val upload = sync().upload()
        assertTrue(upload.isSuccess)
        server.takeRequest() // MKCOL
        val put = server.takeRequest()
        val body = put.body.readByteArray()
        assertEquals("backup password must decrypt the uploaded file",
            "Через WebDAV", kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<BackupPayload>(BackupCrypto.decrypt(body, "backup-pass".toCharArray()))
                .medications.single().name)
        assertFalse(runCatching { BackupCrypto.decrypt(body, "webdav-pass".toCharArray()) }.isSuccess)

        server.enqueue(MockResponse().setResponseCode(200).setBody(
            okio.Buffer().write(body)
        ))
        val download = sync().download()
        assertTrue(download.isSuccess)
        assertEquals("Через WebDAV", download.getOrThrow().medications.single().name)
    }

    @Test
    fun legacyWebDavEncryptedFileStillDownloads() = runBlocking {
        settings.setWebDav(
            server.url("/opora").toString(), "user", "webdav-pass", "backup-pass"
        )
        val legacy = BackupCrypto.encrypt(
            """{"app":"opora","formatVersion":3,"exportedAt":1,"appVersion":"1.0.0",""" +
                """"medications":[{"id":"legacy","name":"Старый архив","startDate":20000}]}""",
            "webdav-pass".toCharArray()
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(legacy)))
        val download = sync().download()
        assertTrue(download.isSuccess)
        assertEquals("Старый архив", download.getOrThrow().medications.single().name)
    }

    @Test
    fun unauthorizedServerReturnsFriendlyError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        val result = sync().test(server.url("/opora").toString(), "user", "bad")
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as UserMessageException
        assertEquals(R.string.errors_auth_rejected, error.messageRes)
        assertEquals(401, error.args.single())
    }
}
