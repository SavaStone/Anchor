package app.opora.data.backup

import app.opora.R
import app.opora.data.UserMessageException
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class WebDavClient(
    private val folderUrl: String,
    private val user: String,
    private val password: String,
    client: OkHttpClient? = null
) {
    private val client = client ?: OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val auth = Credentials.basic(user, password)

    private fun execute(request: Request): Response = try {
        client.newCall(request).execute()
    } catch (e: Exception) {
        val messageRes = when (e) {
            is java.net.UnknownHostException -> R.string.errors_server_not_found
            is java.net.ConnectException -> R.string.errors_server_unreachable
            is java.net.SocketTimeoutException -> R.string.errors_server_timeout
            is java.io.InterruptedIOException -> R.string.errors_connection_interrupted
            is javax.net.ssl.SSLException -> R.string.errors_secure_connection
            is IllegalArgumentException -> R.string.errors_bad_folder_url
            is java.net.SocketException -> R.string.errors_connection_interrupted
            is IOException -> R.string.errors_network
            else -> R.string.errors_connect
        }
        throw UserMessageException(messageRes, e)
    }

    fun folder(): String {
        val value = folderUrl.trim().trimEnd('/')
        if (!value.startsWith("https://", ignoreCase = true)) {
            throw UserMessageException(R.string.errors_bad_folder_url)
        }
        return value
    }

    fun fileUrl(name: String): String = folder() + "/" + name

    fun test(): Result<Unit> = runCatching {
        val request = Request.Builder()
            .url(folder())
            .method(
                "PROPFIND",
                """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
                    .toRequestBody("application/xml; charset=utf-8".toMediaType())
            )
            .header("Authorization", auth)
            .header("Depth", "0")
            .build()
        execute(request).use { response ->
            when (response.code) {
                207, 200 -> Unit
                401, 403 -> throw UserMessageException(R.string.errors_auth_rejected, response.code)
                404 -> ensureFolder()
                else -> throw UserMessageException(R.string.errors_server_code, response.code)
            }
        }
    }

    private fun ensureFolder() {
        val request = Request.Builder()
            .url(folder() + "/")
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .header("Authorization", auth)
            .build()
        execute(request).use { response ->
            if (response.code !in listOf(200, 201, 204, 301, 302, 405)) {
                if (response.code == 401 || response.code == 403) {
                    throw UserMessageException(R.string.errors_folder_access, response.code)
                }
                throw UserMessageException(R.string.errors_folder_create, response.code)
            }
        }
    }

    fun upload(name: String, bytes: ByteArray): Result<Unit> = runCatching {
        ensureFolder()
        val request = Request.Builder()
            .url(fileUrl(name))
            .put(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .header("Authorization", auth)
            .build()
        execute(request).use { response ->
            if (!response.isSuccessful) {
                throw UserMessageException(R.string.errors_upload, response.code)
            }
        }
    }

    fun download(name: String): Result<ByteArray?> = runCatching {
        val request = Request.Builder()
            .url(fileUrl(name))
            .get()
            .header("Authorization", auth)
            .build()
        execute(request).use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful ->
                    throw UserMessageException(R.string.errors_download, response.code)
                else -> response.body?.bytes()
            }
        }
    }
}
