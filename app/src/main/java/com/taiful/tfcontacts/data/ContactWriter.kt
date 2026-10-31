package com.taiful.tfcontacts.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

/** All changes to contacts go through here. Everything stays on the phone. */
object ContactWriter {

    private const val AUTH = ContactsContract.AUTHORITY

    private val editedMimes = listOf(
        StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE,
        Organization.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE,
        Note.CONTENT_ITEM_TYPE
    )

    fun Contact.toEdit(): EditData = EditData(
        name = name, phones = phones, emails = emails, org = org, title = title,
        addresses = addresses, websites = websites, note = note
    )

    // ---------- helpers ----------

    private fun dataOps(rawId: Long?, d: EditData): List<ContentProviderOperation> {
        val ops = mutableListOf<ContentProviderOperation>()
        fun base(): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            return if (rawId == null) b.withValueBackReference(Data.RAW_CONTACT_ID, 0) else b.withValue(Data.RAW_CONTACT_ID, rawId)
        }
        if (d.name.isNotBlank()) {
            ops += base().withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, d.name.trim()).build()
        }
        d.phones.filter { it.number.isNotBlank() }.forEach {
            ops += base().withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, it.number.trim())
                .withValue(Phone.TYPE, it.type)
                .withValue(Phone.LABEL, it.label).build()
        }
        d.emails.filter { it.isNotBlank() }.forEach {
            ops += base().withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, it.trim()).withValue(Email.TYPE, Email.TYPE_HOME).build()
        }
        if (d.org.isNotBlank() || d.title.isNotBlank()) {
            ops += base().withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, d.org.trim())
                .withValue(Organization.TITLE, d.title.trim()).build()
        }
        d.addresses.filter { it.isNotBlank() }.forEach {
            ops += base().withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                .withValue(StructuredPostal.FORMATTED_ADDRESS, it.trim())
                .withValue(StructuredPostal.TYPE, StructuredPostal.TYPE_HOME).build()
        }
        d.websites.filter { it.isNotBlank() }.forEach {
            ops += base().withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                .withValue(Website.URL, it.trim()).withValue(Website.TYPE, Website.TYPE_OTHER).build()
        }
        if (d.note.isNotBlank()) {
            ops += base().withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note).build()
        }
        if (d.photoChanged && d.photo != null) {
            ops += base().withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                .withValue(Data.IS_SUPER_PRIMARY, 1)
                .withValue(Photo.PHOTO, PhotoUtil.shrink(d.photo)).build()
        }
        return ops
    }

    private fun contactIdOfRaw(context: Context, rawId: Long): Long? =
        context.contentResolver.query(
            ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
            arrayOf(RawContacts.CONTACT_ID), null, null, null
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    /** Name and numbers stored in one raw contact (used to find the entry on the SIM). */
    private fun rawNameAndNumbers(context: Context, rawId: Long): Pair<String, List<String>> {
        var name = ""
        val nums = mutableListOf<String>()
        context.contentResolver.query(
            Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1),
            "${Data.RAW_CONTACT_ID}=?", arrayOf(rawId.toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> name = c.getString(1) ?: name
                    Phone.CONTENT_ITEM_TYPE -> c.getString(1)?.let { nums += it }
                }
            }
        }
        return name to nums
    }

    fun photoBytes(context: Context, contactId: Long): ByteArray? = try {
        val uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId)
        Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun writableRaws(c: Contact): List<RawInfo> = c.raws.filter { it.kind != SourceKind.SIM }
        .sortedBy { when (it.kind) { SourceKind.PHONE -> 0; SourceKind.GOOGLE -> 1; else -> 2 } }

    // ---------- public actions ----------

    /** Creates a new contact in the phone's own memory. Returns the new contact id. */
    fun insertNew(context: Context, d: EditData): Long? {
        val ops = arrayListOf<ContentProviderOperation>()
        ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
            .withValue(RawContacts.ACCOUNT_TYPE, null)
            .withValue(RawContacts.ACCOUNT_NAME, null)
            .build()
        ops += dataOps(null, d)
        val res = context.contentResolver.applyBatch(AUTH, ops)
        val rawId = res.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return null
        return contactIdOfRaw(context, rawId)
    }

    /** Saves the edit screen. Returns the contact id to show afterwards. */
    fun save(context: Context, existing: Contact?, d: EditData): Long? {
        if (existing == null) return insertNew(context, d)
        val writable = writableRaws(existing)
        if (writable.isEmpty()) {
            // SIM-only contact: SIM cards cannot hold photos or extra fields, so it moves to the phone.
            val keepPhoto = if (!d.photoChanged) photoBytes(context, existing.id) else d.photo
            val id = insertNew(context, d.copy(photoChanged = keepPhoto != null, photo = keepPhoto))
            delete(context, existing)
            return id
        }
        val target = writable.first()
        val ops = arrayListOf<ContentProviderOperation>()
        val mimes = if (d.photoChanged) editedMimes + Photo.CONTENT_ITEM_TYPE else editedMimes
        val placeholders = mimes.joinToString(",") { "?" }
        for (r in writable) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection(
                    "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN ($placeholders)",
                    arrayOf(r.rawId.toString()) + mimes
                ).build()
        }
        ops += dataOps(target.rawId, d)
        context.contentResolver.applyBatch(AUTH, ops)
        return contactIdOfRaw(context, target.rawId) ?: existing.id
    }

    fun setStarred(context: Context, contactId: Long, starred: Boolean) {
        val v = android.content.ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }
        context.contentResolver.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId), v, null, null)
    }

    fun delete(context: Context, c: Contact) {
        for (r in c.raws) {
            if (r.kind == SourceKind.SIM) {
                val (name, nums) = rawNameAndNumbers(context, r.rawId)
                nums.forEach { SimHelper.delete(context, name, it) }
            }
        }
        for (r in c.raws) {
            try {
                context.contentResolver.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, r.rawId), null, null)
            } catch (_: Exception) {
            }
        }
        try {
            context.contentResolver.delete(ContentUris.withAppendedId(Contacts.CONTENT_URI, c.id), null, null)
        } catch (_: Exception) {
        }
    }

    private fun looksLikeNumber(s: String) = s.isBlank() || s == "(No name)" || s.all { it.isDigit() || it in "+-() " }

    /** What the merged contact will contain (also shown as a preview). */
    fun mergedData(group: List<Contact>): EditData {
        val sorted = group.sortedByDescending { it.richness }
        val keep = sorted.first()
        val name = if (!looksLikeNumber(keep.name)) keep.name
        else sorted.firstOrNull { !looksLikeNumber(it.name) }?.name ?: keep.name
        val phones = mutableListOf<PhoneNum>()
        sorted.flatMap { it.phones }.forEach { p -> if (phones.none { it.key == p.key }) phones += p }
        val emails = mutableListOf<String>()
        sorted.flatMap { it.emails }.forEach { e -> if (emails.none { it.equals(e, true) }) emails += e }
        return EditData(
            name = name,
            phones = phones,
            emails = emails,
            org = sorted.firstOrNull { it.org.isNotBlank() }?.org ?: "",
            title = sorted.firstOrNull { it.title.isNotBlank() }?.title ?: "",
            addresses = sorted.flatMap { it.addresses }.distinct(),
            websites = sorted.flatMap { it.websites }.distinct(),
            note = sorted.map { it.note.trim() }.filter { it.isNotBlank() }.distinct().joinToString("\n\n"),
        )
    }

    /** Joins several contacts into one. The richest copy is kept; the others are removed. */
    fun merge(context: Context, group: List<Contact>) {
        if (group.size < 2) return
        val sorted = group.sortedByDescending { it.richness }
        val keep = sorted.first()
        var data = mergedData(group)
        if (keep.photoUri == null) {
            val other = sorted.firstOrNull { it.photoUri != null }
            val bytes = other?.let { photoBytes(context, it.id) }
            if (bytes != null) data = data.copy(photoChanged = true, photo = bytes)
        }
        save(context, keep, data)
        sorted.drop(1).forEach { delete(context, it) }
    }

    /** Copies (or moves) a contact into the phone's own memory. */
    fun copyToPhone(context: Context, c: Contact, removeOriginal: Boolean): Boolean {
        if (c.raws.all { it.kind == SourceKind.PHONE }) return true
        val bytes = photoBytes(context, c.id)
        val d = c.toEdit().copy(photoChanged = bytes != null, photo = bytes)
        val id = insertNew(context, d) ?: return false
        if (removeOriginal) delete(context, c)
        return id > 0
    }

    /** Copies (or moves) a contact onto a SIM card: only the name and the first number fit. */
    fun copyToSim(context: Context, c: Contact, sim: SimHelper.Sim?, removeOriginal: Boolean): Boolean {
        val number = c.phones.firstOrNull()?.number ?: return false
        val ok = SimHelper.insert(context, sim, c.name, number)
        if (ok && removeOriginal) delete(context, c)
        return ok
    }

    /** Adds contacts from a .vcf file. Skips ones that already exist. Returns (added, skipped). */
    fun importEntries(context: Context, entries: List<EditData>, existing: List<Contact>): Pair<Int, Int> {
        val known = HashMap<String, MutableSet<String>>()
        existing.forEach { c -> known.getOrPut(Normalize.name(c.name)) { mutableSetOf() }.addAll(c.phones.map { it.key }) }
        var added = 0
        var skipped = 0
        for (e in entries) {
            val n = Normalize.name(e.name.ifBlank { e.phones.firstOrNull()?.number ?: "" })
            val keys = e.phones.map { it.key }.toSet()
            val have = known[n]
            if (have != null && (keys.isEmpty() || keys.any { it in have })) {
                skipped++
                continue
            }
            try {
                insertNew(context, e)
                known.getOrPut(n) { mutableSetOf() }.addAll(keys)
                added++
            } catch (_: Exception) {
                skipped++
            }
        }
        return added to skipped
    }
}
