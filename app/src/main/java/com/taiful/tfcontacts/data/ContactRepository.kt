package com.taiful.tfcontacts.data

import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts

/** Reads every contact stored on the phone (phone memory, SIM cards and accounts). */
object ContactRepository {

    private class Builder(val id: Long) {
        var lookup = ""
        var name = ""
        var starred = false
        var photo: String? = null
        var thumb: String? = null
        val phones = mutableListOf<PhoneNum>()
        val emails = mutableListOf<String>()
        var org = ""
        var title = ""
        val addresses = mutableListOf<String>()
        val websites = mutableListOf<String>()
        val nicknames = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val raws = mutableListOf<RawInfo>()
    }

    fun loadAll(context: Context): List<Contact> {
        val cr = context.contentResolver
        val map = LinkedHashMap<Long, Builder>()

        cr.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.STARRED, Contacts.PHOTO_URI, Contacts.PHOTO_THUMBNAIL_URI),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val b = Builder(c.getLong(0))
                b.lookup = c.getString(1) ?: ""
                b.name = c.getString(2) ?: ""
                b.starred = c.getInt(3) == 1
                b.photo = c.getString(4)
                b.thumb = c.getString(5)
                map[b.id] = b
            }
        }

        cr.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.CONTACT_ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME),
            "${RawContacts.DELETED}=0", null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val b = map[c.getLong(1)] ?: continue
                b.raws += RawInfo(c.getLong(0), c.getString(2), c.getString(3))
            }
        }

        val mimes = listOf(
            Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE,
            StructuredPostal.CONTENT_ITEM_TYPE, Website.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE
        )
        cr.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4),
            "${Data.MIMETYPE} IN (${mimes.joinToString(",") { "?" }})", mimes.toTypedArray(), null
        )?.use { c ->
            while (c.moveToNext()) {
                val b = map[c.getLong(0)] ?: continue
                val d1 = c.getString(2) ?: continue
                if (d1.isBlank()) continue
                when (c.getString(1)) {
                    Phone.CONTENT_ITEM_TYPE -> {
                        val p = PhoneNum(d1, if (c.isNull(3)) Phone.TYPE_MOBILE else c.getInt(3), c.getString(4))
                        if (b.phones.none { it.key == p.key && it.key.isNotEmpty() }) b.phones += p
                    }
                    Email.CONTENT_ITEM_TYPE -> if (b.emails.none { it.equals(d1, true) }) b.emails += d1
                    Note.CONTENT_ITEM_TYPE -> if (d1 !in b.notes) b.notes += d1
                    Organization.CONTENT_ITEM_TYPE -> {
                        if (b.org.isBlank()) b.org = d1
                        if (b.title.isBlank()) b.title = c.getString(5) ?: ""
                    }
                    StructuredPostal.CONTENT_ITEM_TYPE -> if (d1 !in b.addresses) b.addresses += d1
                    Website.CONTENT_ITEM_TYPE -> if (d1 !in b.websites) b.websites += d1
                    Nickname.CONTENT_ITEM_TYPE -> if (d1 !in b.nicknames) b.nicknames += d1
                }
            }
        }

        return map.values
            .filter { it.raws.isNotEmpty() }
            .map {
                Contact(
                    id = it.id, lookupKey = it.lookup,
                    name = it.name.ifBlank { it.phones.firstOrNull()?.number ?: it.emails.firstOrNull() ?: "(নাম নেই)" },
                    starred = it.starred, photoUri = it.photo, thumbUri = it.thumb,
                    phones = it.phones, emails = it.emails, org = it.org, title = it.title,
                    addresses = it.addresses, websites = it.websites, nicknames = it.nicknames,
                    note = it.notes.joinToString("\n\n"), raws = it.raws
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    /** Finds contacts that are really the same person: same phone number, or exactly the same name. */
    fun findDuplicates(all: List<Contact>): List<DupGroup> {
        val parent = HashMap<Long, Long>()
        fun find(x: Long): Long {
            var r = x
            while (parent[r] != null && parent[r] != r) r = parent[r]!!
            parent[x] = r
            return r
        }
        fun union(a: Long, b: Long) {
            val ra = find(a); val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }
        all.forEach { parent[it.id] = it.id }

        val byPhone = HashMap<String, Long>()
        for (c in all) for (p in c.phones) {
            val k = p.key
            if (k.length < 6) continue
            val other = byPhone[k]
            if (other == null) byPhone[k] = c.id else union(c.id, other)
        }
        val numberRoots = all.groupBy { find(it.id) }.filter { it.value.size > 1 }

        val result = mutableListOf<DupGroup>()
        val inNumberGroup = HashSet<Long>()
        numberRoots.values.forEach { g ->
            result += DupGroup(g, byNumber = true)
            g.forEach { inNumberGroup += it.id }
        }

        // Same name (but no shared number) — shown separately, not ticked by default
        all.filter { it.id !in inNumberGroup && it.name.isNotBlank() && it.name != "(নাম নেই)" }
            .groupBy { Normalize.name(it.name) }
            .filter { it.value.size > 1 }
            .forEach { result += DupGroup(it.value, byNumber = false) }

        return result.sortedBy { it.members.first().name.lowercase() }
    }
}
