package com.meyouww.core.chat

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * The person's OK, on Google's own screen, for meyouww to keep files in a hidden app folder
 * of their Google Drive (the "appDataFolder"). That folder is all this scope reaches: meyouww
 * cannot see or touch anything else in their Drive. The access token is used on the phone
 * and never stored or sent to meyouww's server.
 */
object DriveAccess {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    sealed interface Result {
        data class Token(val token: String) : Result

        /** Google has to ask the person first: launch this from a screen. */
        data class NeedsConsent(val intent: PendingIntent) : Result

        /** No Google Play services, no such account on the phone, or offline. */
        data object Unavailable : Result
    }

    /** [email]: the Google account meyouww is signed in with, so the backup goes to that Drive. */
    suspend fun authorize(context: Context, email: String?): Result = suspendCancellableCoroutine { cont ->
        try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(SCOPE)))
                .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
                .build()
            Identity.getAuthorizationClient(context).authorize(request)
                .addOnSuccessListener { result ->
                    if (!cont.isActive) return@addOnSuccessListener
                    val pending = result.pendingIntent
                    val token = result.accessToken
                    cont.resume(
                        when {
                            result.hasResolution() && pending != null -> Result.NeedsConsent(pending)
                            !token.isNullOrBlank() -> Result.Token(token)
                            else -> Result.Unavailable
                        },
                    )
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(Result.Unavailable) }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(Result.Unavailable)
        }
    }

    /** The token after Google's consent screen, or null if the person said no. */
    fun tokenFrom(context: Context, data: Intent?): String? =
        runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).accessToken }.getOrNull()
}

/** Google Drive said no ([code] 401 or 403: the permission is gone; anything else: try later). */
class DriveException(val code: Int, message: String) : IOException(message) {
    val denied: Boolean get() = code == 401 || code == 403
}

/**
 * The few Google Drive calls the backup needs, all inside the hidden app folder:
 * list, upload, download and delete. Plain HTTPS with the person's access token.
 */
internal class DriveClient(private val token: String) {
    data class Entry(val id: String, val name: String, val size: Long, val modifiedTime: String?)

    @Serializable
    private data class Listing(val nextPageToken: String? = null, val files: List<Item> = emptyList())

    @Serializable
    private data class Item(val id: String, val name: String = "", val size: String? = null, val modifiedTime: String? = null)

    fun list(): List<Entry> {
        val out = mutableListOf<Entry>()
        var page: String? = null
        do {
            val url = "$FILES?spaces=appDataFolder&pageSize=1000&fields=" + enc("nextPageToken,files(id,name,size,modifiedTime)") +
                (page?.let { "&pageToken=" + enc(it) } ?: "")
            val listing = ChatJson.decodeFromString(Listing.serializer(), text(open(url, "GET")))
            listing.files.mapTo(out) { Entry(it.id, it.name, it.size?.toLongOrNull() ?: 0L, it.modifiedTime) }
            page = listing.nextPageToken
        } while (page != null)
        return out
    }

    /** Uploads [file] as a new Drive file called [name] (a resumable upload, sent in one go). */
    fun upload(name: String, file: File) {
        val start = open("$UPLOAD?uploadType=resumable", "POST")
        start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        start.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
        start.setRequestProperty("X-Upload-Content-Length", file.length().toString())
        start.doOutput = true
        val meta = """{"name":${quote(name)},"parents":["appDataFolder"]}""".toByteArray(Charsets.UTF_8)
        start.setFixedLengthStreamingMode(meta.size)
        val session = try {
            start.outputStream.use { it.write(meta) }
            check(start)
            start.getHeaderField("Location") ?: throw DriveException(start.responseCode, "no upload session")
        } finally {
            start.disconnect()
        }
        val put = (URL(session).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = TIMEOUT_MS
            readTimeout = UPLOAD_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(file.length())
        }
        try {
            put.outputStream.use { out -> file.inputStream().use { it.copyTo(out, 64 * 1024) } }
            check(put)
        } finally {
            put.disconnect()
        }
    }

    fun download(id: String, to: File) {
        val connection = open("$FILES/${enc(id)}?alt=media", "GET")
        try {
            check(connection)
            to.outputStream().use { out -> connection.inputStream.use { it.copyTo(out, 64 * 1024) } }
        } finally {
            connection.disconnect()
        }
    }

    fun delete(id: String) {
        val connection = open("$FILES/${enc(id)}", "DELETE")
        try {
            // 404: already gone, which is what was wanted.
            if (connection.responseCode != 404) check(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun text(connection: HttpURLConnection): String = try {
        check(connection)
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }

    private fun check(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code !in 200..299) throw DriveException(code, "Google Drive answered $code")
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun quote(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val FILES = "https://www.googleapis.com/drive/v3/files"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        const val TIMEOUT_MS = 20_000
        const val UPLOAD_TIMEOUT_MS = 120_000
    }
}
