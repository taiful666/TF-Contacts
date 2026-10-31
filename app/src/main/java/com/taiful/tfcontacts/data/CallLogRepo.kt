package com.taiful.tfcontacts.data

import android.content.Context
import android.provider.CallLog

data class CallEntry(
    val id: Long,
    val number: String,
    val cachedName: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val accountId: String?,
    val count: Int = 1,
    val ids: List<Long> = listOf(id),
)

object CallLogRepo {
    fun load(context: Context, limit: Int = 600): List<CallEntry> {
        val list = mutableListOf<CallEntry>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID
                ),
                null, null, "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                while (c.moveToNext() && list.size < limit) {
                    val e = CallEntry(
                        c.getLong(0), c.getString(1) ?: "", c.getString(2), c.getInt(3),
                        c.getLong(4), c.getLong(5), c.getString(6)
                    )
                    val last = list.lastOrNull()
                    // Group back-to-back calls with the same number and kind
                    if (last != null && last.number == e.number && last.type == e.type) {
                        list[list.size - 1] = last.copy(count = last.count + 1, ids = last.ids + e.id)
                    } else list += e
                }
            }
        } catch (_: SecurityException) {
        }
        return list
    }

    fun delete(context: Context, ids: List<Long>) {
        try {
            ids.chunked(200).forEach { chunk ->
                context.contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN (${chunk.joinToString(",")})", null
                )
            }
        } catch (_: Exception) {
        }
    }
}
