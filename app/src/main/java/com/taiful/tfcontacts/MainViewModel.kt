package com.taiful.tfcontacts

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.taiful.tfcontacts.data.Backups
import com.taiful.tfcontacts.data.CallEntry
import com.taiful.tfcontacts.data.CallLogRepo
import com.taiful.tfcontacts.data.Contact
import com.taiful.tfcontacts.data.ContactRepository
import com.taiful.tfcontacts.data.ContactWriter
import com.taiful.tfcontacts.data.DupGroup
import com.taiful.tfcontacts.data.EditData
import com.taiful.tfcontacts.data.SimHelper
import com.taiful.tfcontacts.data.Vcf
import com.taiful.tfcontacts.ui.PhotoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val contacts = MutableStateFlow<List<Contact>>(emptyList())
    val duplicates = MutableStateFlow<List<DupGroup>>(emptyList())
    val calls = MutableStateFlow<List<CallEntry>>(emptyList())
    val loaded = MutableStateFlow(false)
    /** Text of a long-running job (merge, export …) — shows a progress box. */
    val busy = MutableStateFlow<String?>(null)
    /** One-time messages shown at the bottom of the screen. */
    val message = MutableStateFlow<String?>(null)

    /** Newer build number available on GitHub (null = up to date / unknown). */
    val updateBuild = MutableStateFlow<Long?>(null)
    /** Changes whenever the Google Drive backup state changes, so the screen can refresh. */
    val driveTick = MutableStateFlow(0)

    private val ctx get() = getApplication<Application>()
    private var reloadJob: Job? = null
    private var observing = false
    private var paused = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!paused) scheduleReload()
        }
    }

    fun start() {
        if (!observing) {
            try {
                ctx.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            } catch (_: Exception) {
            }
            try {
                ctx.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
            } catch (_: Exception) {
            }
            observing = true
        }
        reload()
    }

    private fun scheduleReload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(600)
            reload()
        }
    }

    fun reload() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    ContactRepository.loadAll(ctx)
                } catch (_: Exception) {
                    null
                }
            }
            if (list != null) {
                PhotoCache.clear()
                contacts.value = list
                duplicates.value = withContext(Dispatchers.Default) { ContactRepository.findDuplicates(list) }
            }
            calls.value = withContext(Dispatchers.IO) { CallLogRepo.load(ctx) }
            loaded.value = true
        }
    }

    fun contact(id: Long): Contact? = contacts.value.firstOrNull { it.id == id }

    /** Runs a job off the main thread with a progress message, then reloads. */
    private fun job(text: String, done: String? = null, block: suspend () -> String?) {
        viewModelScope.launch {
            busy.value = text
            paused = true
            val result = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                "Something went wrong: ${e.message ?: e.javaClass.simpleName}"
            }
            paused = false
            busy.value = null
            message.value = result ?: done
            reload()
        }
    }

    fun save(existing: Contact?, d: EditData, onSaved: (Long?) -> Unit) {
        viewModelScope.launch {
            busy.value = "Saving…"
            val id = try {
                withContext(Dispatchers.IO) { ContactWriter.save(ctx, existing, d) }
            } catch (e: Exception) {
                message.value = "Could not save: ${e.message}"
                null
            }
            busy.value = null
            reload()
            if (id != null) message.value = "Saved"
            onSaved(id)
        }
    }

    fun toggleStar(c: Contact) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    ContactWriter.setStarred(ctx, c.id, !c.starred)
                } catch (_: Exception) {
                }
            }
            reload()
        }
    }

    fun delete(list: List<Contact>) = job("Deleting…") {
        Backups.create(ctx, list, "before-delete")
        list.forEach { ContactWriter.delete(ctx, it) }
        "${list.size} contact(s) deleted (a backup was kept)"
    }

    fun merge(groups: List<List<Contact>>) = job("Merging… please wait") {
        Backups.create(ctx, contacts.value, "before-merge")
        var n = 0
        groups.forEachIndexed { i, g ->
            busy.value = "Merging… ${i + 1}/${groups.size}"
            ContactWriter.merge(ctx, g); n++
        }
        "$n group(s) merged. A backup of the previous state was kept."
    }

    fun copyToPhone(list: List<Contact>, remove: Boolean) = job(if (remove) "Moving to phone…" else "Copying to phone…") {
        if (remove) Backups.create(ctx, list, "before-move")
        var ok = 0
        list.forEachIndexed { i, c ->
            busy.value = "Copying to phone… ${i + 1}/${list.size}"
            if (ContactWriter.copyToPhone(ctx, c, remove)) ok++
        }
        "$ok/${list.size} contact(s) ${if (remove) "moved" else "copied"} to phone"
    }

    fun copyToSim(list: List<Contact>, sim: SimHelper.Sim?, remove: Boolean) = job("Writing to SIM…") {
        if (remove) Backups.create(ctx, list, "before-move")
        var ok = 0
        list.forEachIndexed { i, c ->
            busy.value = "Writing to SIM… ${i + 1}/${list.size}"
            if (ContactWriter.copyToSim(ctx, c, sim, remove)) ok++
        }
        if (ok == 0 && list.isNotEmpty()) "Could not write to the SIM. It may be full, or this phone does not allow apps to write to the SIM."
        else "$ok/${list.size} contact(s) ${if (remove) "moved" else "copied"} to SIM"
    }

    fun export(list: List<Contact>, uri: Uri) = job("Exporting…") {
        ctx.contentResolver.openOutputStream(uri)?.use { Vcf.write(ctx, list, it) }
            ?: return@job "Could not write the file"
        "${list.size} contact(s) exported"
    }

    fun backupNow() = job("Backing up…") {
        Backups.create(ctx, contacts.value, "manual")
        "Backup of ${contacts.value.size} contacts saved"
    }

    fun copyFileTo(file: java.io.File, uri: Uri) = job("Saving…") {
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        "File saved"
    }

    fun importFrom(uri: Uri) = job("Importing…") {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@job "Could not open the file"
        importText(text)
    }

    fun restore(file: java.io.File) = job("Restoring…") { importText(file.readText()) }

    private fun importText(text: String): String {
        val entries = Vcf.parse(text)
        if (entries.isEmpty()) return "No contacts found in the file"
        val existing = ContactRepository.loadAll(ctx)
        val (added, skipped) = ContactWriter.importEntries(ctx, entries, existing)
        return "$added new contact(s) added" + if (skipped > 0) ", $skipped already existed" else ""
    }

    private var lastUpdateCheck = 0L
    fun checkUpdate() {
        if (System.currentTimeMillis() - lastUpdateCheck < 60_000) return
        lastUpdateCheck = System.currentTimeMillis()
        viewModelScope.launch {
            val latest = withContext(Dispatchers.IO) { com.taiful.tfcontacts.cloud.Updates.latestBuild() }
            if (latest != null && latest > com.taiful.tfcontacts.cloud.Updates.installedBuild(ctx)) updateBuild.value = latest
        }
    }

    private fun driveError(e: Exception): String =
        if (e is com.taiful.tfcontacts.cloud.DriveBackup.NeedsSignIn) "Please connect your Gmail again (Settings → Google Drive backup)."
        else "Google Drive: ${e.message ?: "something went wrong"}. Check the internet connection and try again."

    /** Called after the user picked a Gmail account and agreed. Restores an existing backup, then backs up. */
    fun driveConnected(token: String) = job("Connecting to Google Drive…") {
        val D = com.taiful.tfcontacts.cloud.DriveBackup
        val email = D.whoAmI(token) ?: return@job "Could not read the Google account. Please try again."
        D.setEmail(ctx, email)
        driveTick.value++
        val sb = StringBuilder("Connected: $email\n\n")
        try {
            val info = D.remoteInfo(token)
            if (info != null) {
                busy.value = "Found a backup on Drive — bringing it back…"
                sb.append(D.restore(ctx, token)).append("\n\n")
            }
            busy.value = "Backing up to Google Drive…"
            sb.append(D.backup(ctx, token))
            sb.append("\n\nFrom now on a backup is made automatically every day.")
        } catch (e: Exception) {
            sb.append(driveError(e))
        }
        driveTick.value++
        sb.toString()
    }

    fun driveBackupNow() = job("Backing up to Google Drive…") {
        try {
            com.taiful.tfcontacts.cloud.DriveBackup.backup(ctx)
        } catch (e: Exception) {
            driveError(e)
        }.also { driveTick.value++ }
    }

    fun driveRestore() = job("Restoring from Google Drive…") {
        try {
            com.taiful.tfcontacts.cloud.DriveBackup.restore(ctx)
        } catch (e: Exception) {
            driveError(e)
        }
    }

    fun driveRemove() {
        com.taiful.tfcontacts.cloud.DriveBackup.setEmail(ctx, null)
        driveTick.value++
        message.value = "Gmail removed. Backups now stay only on this phone. (Your old backup on Google Drive was not deleted.)"
    }

    fun exportCalls(uri: Uri) = job("Exporting call history…") {
        ctx.contentResolver.openOutputStream(uri)?.use { CallLogRepo.exportCsv(ctx, it) } ?: return@job "Could not write the file"
        "Call history exported"
    }

    fun deleteCalls(ids: List<Long>) = job("Deleting…") { CallLogRepo.delete(ctx, ids); null }

    override fun onCleared() {
        try {
            ctx.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        super.onCleared()
    }
}
