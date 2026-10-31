package com.taiful.tfcontacts.data

import android.provider.ContactsContract.CommonDataKinds.Phone as CPhone

data class PhoneNum(val number: String, val type: Int = CPhone.TYPE_MOBILE, val label: String? = null) {
    val key: String get() = Normalize.phoneKey(number)
    fun typeLabel(): String = when (type) {
        CPhone.TYPE_MOBILE -> "Mobile"
        CPhone.TYPE_HOME -> "Home"
        CPhone.TYPE_WORK -> "Work"
        CPhone.TYPE_MAIN -> "Main"
        CPhone.TYPE_CUSTOM -> label?.takeIf { it.isNotBlank() } ?: "Other"
        else -> "Other"
    }
}

enum class SourceKind { PHONE, SIM, GOOGLE, OTHER }

data class RawInfo(val rawId: Long, val accountType: String?, val accountName: String?) {
    val kind: SourceKind
        get() {
            val t = accountType?.lowercase() ?: return SourceKind.PHONE
            return when {
                t.contains("sim") || t.contains("icc") || t.contains("usim") -> SourceKind.SIM
                t == "com.google" -> SourceKind.GOOGLE
                t.contains("local") || t.contains("phone") || t.contains("device") -> SourceKind.PHONE
                else -> SourceKind.OTHER
            }
        }

    val label: String
        get() = when (kind) {
            SourceKind.PHONE -> "Phone"
            SourceKind.SIM -> accountName?.takeIf { it.isNotBlank() }?.let { if (it.contains("sim", true)) it.uppercase() else "SIM ($it)" } ?: "SIM"
            SourceKind.GOOGLE -> "Google"
            SourceKind.OTHER -> accountName ?: accountType ?: "Other"
        }
}

data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val photoUri: String?,
    val thumbUri: String?,
    val phones: List<PhoneNum>,
    val emails: List<String>,
    val org: String,
    val title: String,
    val addresses: List<String>,
    val websites: List<String>,
    val nicknames: List<String>,
    val note: String,
    val raws: List<RawInfo>,
) {
    /** Everything that can be searched, in lower case, plus the bare digits of every number. */
    val searchText: String by lazy {
        buildString {
            append(name.lowercase()).append('\n')
            phones.forEach { append(it.number.lowercase()).append('\n').append(Normalize.digits(it.number)).append('\n') }
            emails.forEach { append(it.lowercase()).append('\n') }
            append(org.lowercase()).append('\n').append(title.lowercase()).append('\n')
            addresses.forEach { append(it.lowercase()).append('\n') }
            websites.forEach { append(it.lowercase()).append('\n') }
            nicknames.forEach { append(it.lowercase()).append('\n') }
            append(note.lowercase())
        }
    }

    val t9: String by lazy { Normalize.t9(name) }

    val kinds: Set<SourceKind> get() = raws.map { it.kind }.toSet()
    val isSimOnly: Boolean get() = raws.isNotEmpty() && raws.all { it.kind == SourceKind.SIM }
    val sourceLabel: String get() = raws.map { it.label }.distinct().joinToString(" + ").ifBlank { "Phone" }

    /** Rough amount of information, used to pick which copy to keep when merging. */
    val richness: Int
        get() = phones.size * 3 + emails.size * 2 + (if (note.isNotBlank()) 2 else 0) +
            (if (org.isNotBlank()) 1 else 0) + addresses.size + websites.size +
            (if (photoUri != null) 3 else 0) + (if (isSimOnly) 0 else 5) + name.length.coerceAtMost(30) / 10
}

/** Data typed in the edit screen. */
data class EditData(
    val name: String,
    val phones: List<PhoneNum>,
    val emails: List<String>,
    val org: String,
    val title: String,
    val addresses: List<String>,
    val websites: List<String>,
    val note: String,
    val photoChanged: Boolean = false,
    val photo: ByteArray? = null,
)

data class DupGroup(val members: List<Contact>, val byNumber: Boolean) {
    val key: String get() = members.map { it.id }.sorted().joinToString(",")
}

object Normalize {
    fun digits(s: String): String = buildString {
        for (c in s) {
            when {
                c in '0'..'9' -> append(c)
                c in '০'..'৯' -> append('0' + (c - '০')) // Bangla digits
            }
        }
    }

    /** Last 10 digits, so +8801711000000 and 01711000000 are treated as the same number. */
    fun phoneKey(s: String): String {
        val d = digits(s)
        return if (d.length > 10) d.takeLast(10) else d
    }

    fun name(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    private val t9Map: Map<Char, Char> = buildMap {
        val keys = listOf("2" to "abc", "3" to "def", "4" to "ghi", "5" to "jkl", "6" to "mno", "7" to "pqrs", "8" to "tuv", "9" to "wxyz")
        for ((d, letters) in keys) letters.forEach { put(it, d[0]) }
    }

    fun t9(s: String): String = buildString {
        for (c in s.lowercase()) {
            val m = t9Map[c]
            when {
                m != null -> append(m)
                c in '0'..'9' -> append(c)
                c == ' ' -> append(' ')
            }
        }
    }
}
