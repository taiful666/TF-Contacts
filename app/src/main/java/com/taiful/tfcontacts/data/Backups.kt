package com.taiful.tfcontacts.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backup copies kept privately inside the app on this phone (never uploaded anywhere). */
object Backups {

    data class Item(val file: File, val count: Int) {
        val title: String
            get() = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(file.lastModified()))
        val reason: String
            get() = when {
                file.name.startsWith("before-merge") -> "Before merge"
                file.name.startsWith("before-move") -> "Before move"
                file.name.startsWith("before-delete") -> "Before delete"
                else -> "Manual"
            }
    }

    private fun dir(context: Context) = File(context.filesDir, "backups").apply { mkdirs() }

    fun create(context: Context, contacts: List<Contact>, prefix: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val f = File(dir(context), "$prefix-$stamp-${contacts.size}.vcf")
        f.outputStream().use { Vcf.write(context, contacts, it, withPhotos = true) }
        // Keep the latest 15 backups only
        list(context).drop(15).forEach { it.file.delete() }
        return f
    }

    fun list(context: Context): List<Item> =
        dir(context).listFiles { f -> f.name.endsWith(".vcf") }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { f -> Item(f, f.name.removeSuffix(".vcf").substringAfterLast('-').toIntOrNull() ?: 0) }
}
