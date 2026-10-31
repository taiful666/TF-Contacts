package com.taiful.tfcontacts.cloud

import android.accounts.Account
import android.content.Context
import android.os.Build
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.taiful.tfcontacts.data.CallLogRepo
import com.taiful.tfcontacts.data.ContactRepository
import com.taiful.tfcontacts.data.ContactWriter
import com.taiful.tfcontacts.data.Vcf
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Optional backup to the user's own Google Drive.
 * Files go into the app's private, hidden Drive folder ("appDataFolder"): only TF Contacts can see them,
 * and they never go anywhere else. Nothing is uploaded unless the user adds a Gmail account.
 */
object DriveBackup {

    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val CONTACTS = "tf-contacts.vcf"
    private const val CALLS = "tf-call-history.json"
    private const val INFO = "tf-backup-info.json"

    class NeedsSignIn : Exception("Google account needs to be connected again")

    private fun prefs(c: Context) = c.getSharedPreferences("tf_drive", Context.MODE_PRIVATE)
    fun email(c: Context): String? = prefs(c).getString("email", null)
    fun lastBackup(c: Context): Long = prefs(c).getLong("last", 0)
    fun lastError(c: Context): String? = prefs(c).getString("error", null)
    fun setEmail(c: Context, e: String?) = prefs(c).edit().putString("email", e).remove("error").apply()

    fun request(email: String?): AuthorizationRequest {
        val b = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE)))
        if (email != null) b.setAccount(Account(email, "com.google"))
        return b.build()
    }

    /** Gets an access token without showing anything (works in the background once the user has agreed). */
    suspend fun token(c: Context): String {
        val email = email(c) ?: throw NeedsSignIn()
        val r: AuthorizationResult = Identity.getAuthorizationClient(c).authorize(request(email)).await()
        if (r.hasResolution()) throw NeedsSignIn()
        return r.accessToken ?: throw NeedsSignIn()
    }

    // ------------------------------------------------------------- Drive REST

    private fun http(method: String, url: String, token: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20000
        conn.readTimeout = 60000
        if (method == "PATCH") {
            conn.requestMethod = "POST"
            conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            conn.doOutput = true
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
        conn.disconnect()
        if (code == 401) throw NeedsSignIn()
        if (code !in 200..299) throw Exception("Google Drive error $code: ${String(bytes).take(200)}")
        return bytes
    }

    fun whoAmI(token: String): String? = try {
        JSONObject(String(http("GET", "https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)", token)))
            .optJSONObject("user")?.optString("emailAddress")
    } catch (_: Exception) {
        null
    }

    private fun findId(token: String, name: String): String? {
        val q = URLEncoder.encode("name='$name' and trashed=false", "UTF-8")
        val res = JSONObject(String(http("GET", "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name)", token)))
        val files = res.optJSONArray("files") ?: return null
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private fun upload(token: String, name: String, data: ByteArray, mime: String) {
        val id = findId(token, name)
        if (id != null) {
            http("PATCH", "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media", token, data, mime)
            return
        }
        val boundary = "tfcontacts${System.currentTimeMillis()}"
        val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
        val out = ByteArrayOutputStream()
        out.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray())
        out.write(data)
        out.write("\r\n--$boundary--\r\n".toByteArray())
        http("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", token, out.toByteArray(), "multipart/related; boundary=$boundary")
    }

    private fun download(token: String, name: String): ByteArray? {
        val id = findId(token, name) ?: return null
        return http("GET", "https://www.googleapis.com/drive/v3/files/$id?alt=media", token)
    }

    // ------------------------------------------------------------- backup / restore

    /** Uploads all contacts and the whole call history. Returns a short summary. */
    suspend fun backup(c: Context, tokenIn: String? = null): String {
        try {
            val token = tokenIn ?: token(c)
            val contacts = ContactRepository.loadAll(c)
            val vcf = ByteArrayOutputStream().also { Vcf.write(c, contacts, it, withPhotos = true) }.toByteArray()
            CallLogRepo.sync(c)
            val calls = CallLogRepo.exportJson(c).toByteArray()
            upload(token, CONTACTS, vcf, "text/x-vcard")
            upload(token, CALLS, calls, "application/json")
            val info = JSONObject()
                .put("time", System.currentTimeMillis())
                .put("contacts", contacts.size)
                .put("calls", CallLogRepo.totalCount(c))
                .put("device", Build.MANUFACTURER + " " + Build.MODEL)
            upload(token, INFO, info.toString().toByteArray(), "application/json")
            prefs(c).edit().putLong("last", System.currentTimeMillis()).remove("error").apply()
            return "Backed up ${contacts.size} contacts and ${CallLogRepo.totalCount(c)} calls to Google Drive"
        } catch (e: Exception) {
            prefs(c).edit().putString("error", if (e is NeedsSignIn) "Please connect the Google account again" else (e.message ?: "Backup failed")).apply()
            throw e
        }
    }

    data class RemoteInfo(val time: Long, val contacts: Int, val calls: Int, val device: String)

    fun remoteInfo(token: String): RemoteInfo? = try {
        download(token, INFO)?.let {
            val o = JSONObject(String(it))
            RemoteInfo(o.optLong("time"), o.optInt("contacts"), o.optInt("calls"), o.optString("device"))
        }
    } catch (_: Exception) {
        null
    }

    /** Brings contacts and call history back from Drive. Things that already exist are not added twice. */
    suspend fun restore(c: Context, tokenIn: String? = null): String {
        val token = tokenIn ?: token(c)
        var added = 0
        var skipped = 0
        download(token, CONTACTS)?.let { bytes ->
            val entries = Vcf.parse(String(bytes, Charsets.UTF_8))
            val r = ContactWriter.importEntries(c, entries, ContactRepository.loadAll(c))
            added = r.first; skipped = r.second
        }
        val calls = download(token, CALLS)?.let { CallLogRepo.importJson(c, String(it, Charsets.UTF_8)) } ?: 0
        return "From Google Drive: $added contacts added ($skipped already here), $calls calls added to history"
    }
}
