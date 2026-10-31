package com.taiful.tfcontacts.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.Writer

/** Reading and writing .vcf (vCard) files — the standard contact backup format every phone understands. */
object Vcf {

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")

    private fun Writer.line(s: String) {
        // Fold long lines at 75 characters as the vCard standard asks
        if (s.length <= 75) {
            write(s); write("\r\n"); return
        }
        var i = 0
        var first = true
        while (i < s.length) {
            val end = minOf(s.length, i + if (first) 75 else 74)
            if (!first) write(" ")
            write(s, i, end - i)
            write("\r\n")
            i = end
            first = false
        }
    }

    private fun telType(p: PhoneNum) = when (p.type) {
        Phone.TYPE_MOBILE -> "CELL"
        Phone.TYPE_HOME -> "HOME"
        Phone.TYPE_WORK -> "WORK"
        Phone.TYPE_MAIN -> "PREF"
        else -> "VOICE"
    }

    fun write(context: Context, contacts: List<Contact>, out: OutputStream, withPhotos: Boolean = true) {
        val w = out.bufferedWriter(Charsets.UTF_8)
        for (c in contacts) {
            w.line("BEGIN:VCARD")
            w.line("VERSION:3.0")
            w.line("FN:${esc(c.name)}")
            w.line("N:${esc(c.name)};;;;")
            c.phones.forEach { w.line("TEL;TYPE=${telType(it)}:${it.number}") }
            c.emails.forEach { w.line("EMAIL;TYPE=INTERNET:${esc(it)}") }
            if (c.org.isNotBlank()) w.line("ORG:${esc(c.org)}")
            if (c.title.isNotBlank()) w.line("TITLE:${esc(c.title)}")
            c.addresses.forEach { w.line("ADR;TYPE=HOME:;;${esc(it)};;;;") }
            c.websites.forEach { w.line("URL:${esc(it)}") }
            c.nicknames.forEach { w.line("NICKNAME:${esc(it)}") }
            if (c.note.isNotBlank()) w.line("NOTE:${esc(c.note)}")
            if (c.starred) w.line("X-TF-FAVORITE:1")
            if (withPhotos && c.photoUri != null) {
                val bytes = ContactWriter.photoBytes(context, c.id)
                if (bytes != null) w.line("PHOTO;ENCODING=b;TYPE=JPEG:" + Base64.encodeToString(bytes, Base64.NO_WRAP))
            }
            w.line("END:VCARD")
        }
        w.flush()
    }

    // ---------------- reading ----------------

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun decodeQP(s: String, charset: String): String {
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val hex = s.substring(i + 1, minOf(s.length, i + 3))
                val v = hex.toIntOrNull(16)
                if (v != null && hex.length == 2) {
                    out.write(v); i += 3; continue
                }
            }
            out.write(c.code and 0xFF)
            i++
        }
        return try {
            String(out.toByteArray(), charset(charset))
        } catch (_: Exception) {
            String(out.toByteArray(), Charsets.UTF_8)
        }
    }

    /** Splits text into logical lines (undoing line folding, including quoted-printable soft breaks). */
    private fun logicalLines(text: String): List<String> {
        val raw = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val result = mutableListOf<String>()
        for (l in raw) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && result.isNotEmpty()) {
                result[result.size - 1] = result.last() + l.substring(1)
            } else if (result.isNotEmpty() && result.last().endsWith("=") &&
                result.last().contains("QUOTED-PRINTABLE", ignoreCase = true)
            ) {
                result[result.size - 1] = result.last().dropLast(1) + l
            } else {
                result += l
            }
        }
        return result
    }

    fun parse(text: String): List<EditData> {
        val list = mutableListOf<EditData>()
        var fn = ""
        var n = ""
        var phones = mutableListOf<PhoneNum>()
        var emails = mutableListOf<String>()
        var org = ""
        var title = ""
        var addrs = mutableListOf<String>()
        var urls = mutableListOf<String>()
        var notes = mutableListOf<String>()
        var photo: ByteArray? = null
        var inCard = false

        for (line in logicalLines(text)) {
            if (line.isBlank()) continue
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val head = line.substring(0, colon)
            var value = line.substring(colon + 1)
            val parts = head.split(';')
            val prop = parts[0].substringAfter('.').uppercase()
            val params = parts.drop(1).map { it.uppercase() }

            if (prop == "BEGIN" && value.trim().equals("VCARD", true)) {
                inCard = true
                fn = ""; n = ""; phones = mutableListOf(); emails = mutableListOf(); org = ""; title = ""
                addrs = mutableListOf(); urls = mutableListOf(); notes = mutableListOf(); photo = null
                continue
            }
            if (prop == "END" && value.trim().equals("VCARD", true)) {
                if (inCard) {
                    val name = fn.ifBlank { n }
                    if (name.isNotBlank() || phones.isNotEmpty() || emails.isNotEmpty()) {
                        list += EditData(
                            name = name, phones = phones, emails = emails, org = org, title = title,
                            addresses = addrs, websites = urls, note = notes.joinToString("\n\n"),
                            photoChanged = photo != null, photo = photo
                        )
                    }
                }
                inCard = false
                continue
            }
            if (!inCard) continue

            val qp = params.any { it.contains("QUOTED-PRINTABLE") }
            val cs = params.firstOrNull { it.startsWith("CHARSET=") }?.substringAfter('=') ?: "UTF-8"
            if (qp) value = decodeQP(value, cs)

            when (prop) {
                "FN" -> fn = unesc(value).trim()
                "N" -> {
                    val p = value.split(';').map { unesc(it).trim() }
                    // family;given;middle;prefix;suffix -> "prefix given middle family suffix"
                    n = listOfNotNull(p.getOrNull(3), p.getOrNull(1), p.getOrNull(2), p.getOrNull(0), p.getOrNull(4))
                        .filter { it.isNotBlank() }.joinToString(" ")
                }
                "TEL" -> {
                    val t = params.joinToString(";")
                    val type = when {
                        t.contains("CELL") -> Phone.TYPE_MOBILE
                        t.contains("HOME") -> Phone.TYPE_HOME
                        t.contains("WORK") -> Phone.TYPE_WORK
                        else -> Phone.TYPE_OTHER
                    }
                    val num = value.trim().removePrefix("tel:")
                    if (num.isNotBlank() && phones.none { it.key == Normalize.phoneKey(num) }) phones += PhoneNum(num, type)
                }
                "EMAIL" -> unesc(value).trim().takeIf { it.isNotBlank() }?.let { emails += it }
                "ORG" -> org = value.split(';').map { unesc(it).trim() }.filter { it.isNotBlank() }.joinToString(", ")
                "TITLE" -> title = unesc(value).trim()
                "ADR" -> {
                    val a = value.split(';').map { unesc(it).trim() }.filter { it.isNotBlank() }.joinToString(", ")
                    if (a.isNotBlank()) addrs += a
                }
                "URL" -> unesc(value).trim().takeIf { it.isNotBlank() }?.let { urls += it }
                "NOTE" -> unesc(value).takeIf { it.isNotBlank() }?.let { notes += it }
                "PHOTO" -> {
                    val isB64 = params.any { it.contains("ENCODING=B") || it.contains("BASE64") || it == "B" } ||
                        value.startsWith("data:")
                    if (isB64) {
                        val data = value.substringAfter("base64,", value)
                        photo = try {
                            Base64.decode(data, Base64.DEFAULT)
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return list
    }
}
