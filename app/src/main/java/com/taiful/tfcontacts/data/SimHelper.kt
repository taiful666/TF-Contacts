package com.taiful.tfcontacts.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.telephony.SubscriptionManager

/** Reads and writes contacts stored directly on the SIM card(s). */
object SimHelper {

    data class Sim(val subId: Int, val slot: Int, val name: String) {
        val label: String get() = "SIM ${slot + 1}" + if (name.isNotBlank()) " ($name)" else ""
    }

    @SuppressLint("MissingPermission")
    fun sims(context: Context): List<Sim> = try {
        val sm = context.getSystemService(SubscriptionManager::class.java)
        sm?.activeSubscriptionInfoList.orEmpty().map {
            Sim(it.subscriptionId, it.simSlotIndex, it.displayName?.toString() ?: "")
        }.sortedBy { it.slot }
    } catch (e: Exception) {
        emptyList()
    }

    private fun uris(context: Context, subId: Int?): List<Uri> {
        val list = mutableListOf<Uri>()
        if (subId != null) list += Uri.parse("content://icc/adn/subId/$subId")
        list += Uri.parse("content://icc/adn")
        return list
    }

    /** SIM cards hold only a short name and one number. */
    fun insert(context: Context, sim: Sim?, name: String, number: String): Boolean {
        val cleanNumber = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanNumber.isEmpty()) return false
        val names = listOf(name, name.take(14), name.take(10)).distinct()
        for (uri in uris(context, sim?.subId)) {
            for (n in names) {
                try {
                    val v = ContentValues().apply {
                        put("tag", n)
                        put("number", cleanNumber)
                    }
                    val res = context.contentResolver.insert(uri, v)
                    if (res != null) return true
                } catch (_: Exception) {
                }
            }
        }
        return false
    }

    fun delete(context: Context, name: String, number: String): Boolean {
        val n = name.replace("'", "")
        val num = number.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        val where = "tag='$n' AND number='$num'"
        val targets = sims(context).map { it.subId as Int? }.ifEmpty { listOf(null) }
        for (sub in targets) {
            for (uri in uris(context, sub)) {
                try {
                    if (context.contentResolver.delete(uri, where, null) > 0) return true
                } catch (_: Exception) {
                }
            }
        }
        return false
    }
}
