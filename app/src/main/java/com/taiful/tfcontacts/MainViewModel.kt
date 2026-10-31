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
                "সমস্যা হয়েছে: ${e.message ?: e.javaClass.simpleName}"
            }
            paused = false
            busy.value = null
            message.value = result ?: done
            reload()
        }
    }

    fun save(existing: Contact?, d: EditData, onSaved: (Long?) -> Unit) {
        viewModelScope.launch {
            busy.value = "সেভ হচ্ছে…"
            val id = try {
                withContext(Dispatchers.IO) { ContactWriter.save(ctx, existing, d) }
            } catch (e: Exception) {
                message.value = "সেভ করা যায়নি: ${e.message}"
                null
            }
            busy.value = null
            reload()
            if (id != null) message.value = "সেভ হয়েছে"
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

    fun delete(list: List<Contact>) = job("মোছা হচ্ছে…") {
        Backups.create(ctx, list, "before-delete")
        list.forEach { ContactWriter.delete(ctx, it) }
        "${list.size}টি কন্টাক্ট মোছা হয়েছে (ব্যাকআপ রাখা আছে)"
    }

    fun merge(groups: List<List<Contact>>) = job("মার্জ হচ্ছে… একটু অপেক্ষা করুন") {
        Backups.create(ctx, contacts.value, "before-merge")
        var n = 0
        groups.forEachIndexed { i, g ->
            busy.value = "মার্জ হচ্ছে… ${i + 1}/${groups.size}"
            ContactWriter.merge(ctx, g); n++
        }
        "${n}টি গ্রুপ মার্জ হয়েছে। আগের অবস্থার ব্যাকআপ রাখা আছে।"
    }

    fun copyToPhone(list: List<Contact>, remove: Boolean) = job(if (remove) "ফোনে সরানো হচ্ছে…" else "ফোনে কপি হচ্ছে…") {
        if (remove) Backups.create(ctx, list, "before-move")
        var ok = 0
        list.forEachIndexed { i, c ->
            busy.value = "ফোনে নেওয়া হচ্ছে… ${i + 1}/${list.size}"
            if (ContactWriter.copyToPhone(ctx, c, remove)) ok++
        }
        "${ok}/${list.size}টি কন্টাক্ট ফোনে ${if (remove) "সরানো" else "কপি করা"} হয়েছে"
    }

    fun copyToSim(list: List<Contact>, sim: SimHelper.Sim?, remove: Boolean) = job("সিমে নেওয়া হচ্ছে…") {
        if (remove) Backups.create(ctx, list, "before-move")
        var ok = 0
        list.forEachIndexed { i, c ->
            busy.value = "সিমে নেওয়া হচ্ছে… ${i + 1}/${list.size}"
            if (ContactWriter.copyToSim(ctx, c, sim, remove)) ok++
        }
        if (ok == 0 && list.isNotEmpty()) "সিমে লেখা যায়নি। সিম ভর্তি হতে পারে, বা এই ফোন অ্যাপকে সিমে লিখতে দিচ্ছে না।"
        else "${ok}/${list.size}টি কন্টাক্ট সিমে ${if (remove) "সরানো" else "কপি করা"} হয়েছে"
    }

    fun export(list: List<Contact>, uri: Uri) = job("এক্সপোর্ট হচ্ছে…") {
        ctx.contentResolver.openOutputStream(uri)?.use { Vcf.write(ctx, list, it) }
            ?: return@job "ফাইল লেখা যায়নি"
        "${list.size}টি কন্টাক্ট এক্সপোর্ট হয়েছে"
    }

    fun backupNow() = job("ব্যাকআপ নেওয়া হচ্ছে…") {
        Backups.create(ctx, contacts.value, "manual")
        "${contacts.value.size}টি কন্টাক্টের ব্যাকআপ নেওয়া হয়েছে"
    }

    fun copyFileTo(file: java.io.File, uri: Uri) = job("সেভ হচ্ছে…") {
        ctx.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        "ফাইল সেভ হয়েছে"
    }

    fun importFrom(uri: Uri) = job("ইমপোর্ট হচ্ছে…") {
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@job "ফাইল খোলা যায়নি"
        importText(text)
    }

    fun restore(file: java.io.File) = job("ফিরিয়ে আনা হচ্ছে…") { importText(file.readText()) }

    private fun importText(text: String): String {
        val entries = Vcf.parse(text)
        if (entries.isEmpty()) return "ফাইলে কোনো কন্টাক্ট পাওয়া যায়নি"
        val existing = ContactRepository.loadAll(ctx)
        val (added, skipped) = ContactWriter.importEntries(ctx, entries, existing)
        return "${added}টি নতুন কন্টাক্ট যোগ হয়েছে" + if (skipped > 0) ", ${skipped}টি আগে থেকেই ছিল" else ""
    }

    fun deleteCalls(ids: List<Long>) = job("মোছা হচ্ছে…") { CallLogRepo.delete(ctx, ids); null }

    override fun onCleared() {
        try {
            ctx.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        super.onCleared()
    }
}
