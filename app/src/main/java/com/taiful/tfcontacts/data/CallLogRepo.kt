package com.taiful.tfcontacts.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
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

/**
 * Unlimited call history.
 * Android itself keeps only about the last 500 calls, so every call is also copied into
 * the app's own private database on this phone. Old calls stay there until you delete them.
 */
object CallLogRepo {

    private class Db(context: Context) : SQLiteOpenHelper(context, "call_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE calls (_id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, name TEXT, " +
                    "type INTEGER NOT NULL, date INTEGER NOT NULL, duration INTEGER NOT NULL, account TEXT, " +
                    "UNIQUE(date, number, type))"
            )
            db.execSQL("CREATE INDEX calls_date ON calls(date DESC)")
            db.execSQL("CREATE TABLE removed (date INTEGER NOT NULL, number TEXT NOT NULL, type INTEGER NOT NULL, UNIQUE(date, number, type))")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    @Volatile private var db: Db? = null
    private fun db(context: Context): SQLiteDatabase {
        val d = db ?: synchronized(this) { db ?: Db(context.applicationContext).also { db = it } }
        return d.writableDatabase
    }

    /** Copies every call from the phone's call log into the app's own history. */
    @Synchronized
    fun sync(context: Context) {
        val w = db(context)
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID
                ),
                null, null, null
            )?.use { c ->
                w.beginTransaction()
                try {
                    while (c.moveToNext()) {
                        val number = c.getString(0) ?: ""
                        val type = c.getInt(2)
                        val date = c.getLong(3)
                        // Calls the user deleted on purpose are not brought back
                        val gone = w.rawQuery(
                            "SELECT 1 FROM removed WHERE date=? AND number=? AND type=?",
                            arrayOf(date.toString(), number, type.toString())
                        ).use { it.moveToFirst() }
                        if (gone) continue
                        val v = ContentValues().apply {
                            put("number", number)
                            put("name", c.getString(1))
                            put("type", type)
                            put("date", date)
                            put("duration", c.getLong(4))
                            put("account", c.getString(5))
                        }
                        w.insertWithOnConflict("calls", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                    }
                    w.setTransactionSuccessful()
                } finally {
                    w.endTransaction()
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    /** Whole history, newest first; back-to-back calls with the same number and kind are grouped. */
    fun load(context: Context): List<CallEntry> {
        sync(context)
        val list = mutableListOf<CallEntry>()
        try {
            db(context).rawQuery("SELECT _id, number, name, type, date, duration, account FROM calls ORDER BY date DESC", null).use { c ->
                while (c.moveToNext()) {
                    val e = CallEntry(c.getLong(0), c.getString(1) ?: "", c.getString(2), c.getInt(3), c.getLong(4), c.getLong(5), c.getString(6))
                    val last = list.lastOrNull()
                    if (last != null && last.number == e.number && last.type == e.type) {
                        list[list.size - 1] = last.copy(count = last.count + 1, ids = last.ids + e.id)
                    } else list += e
                }
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun totalCount(context: Context): Int = try {
        db(context).rawQuery("SELECT COUNT(*) FROM calls", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    } catch (_: Exception) {
        0
    }

    /** Deletes calls from the app's history and from the phone's call log. */
    @Synchronized
    fun delete(context: Context, ids: List<Long>) {
        val w = db(context)
        ids.chunked(300).forEach { chunk ->
            val inList = chunk.joinToString(",")
            val rows = mutableListOf<Triple<Long, String, Int>>()
            w.rawQuery("SELECT date, number, type FROM calls WHERE _id IN ($inList)", null).use { c ->
                while (c.moveToNext()) rows += Triple(c.getLong(0), c.getString(1) ?: "", c.getInt(2))
            }
            w.beginTransaction()
            try {
                rows.forEach { (date, number, type) ->
                    w.insertWithOnConflict("removed", null, ContentValues().apply {
                        put("date", date); put("number", number); put("type", type)
                    }, SQLiteDatabase.CONFLICT_IGNORE)
                }
                w.execSQL("DELETE FROM calls WHERE _id IN ($inList)")
                w.setTransactionSuccessful()
            } finally {
                w.endTransaction()
            }
            rows.forEach { (date, number, type) ->
                try {
                    context.contentResolver.delete(
                        CallLog.Calls.CONTENT_URI,
                        "${CallLog.Calls.DATE}=? AND ${CallLog.Calls.NUMBER}=? AND ${CallLog.Calls.TYPE}=?",
                        arrayOf(date.toString(), number, type.toString())
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    /** Whole history as JSON (used for the Google Drive backup). */
    fun exportJson(context: Context): String {
        val arr = org.json.JSONArray()
        db(context).rawQuery("SELECT number, name, type, date, duration, account FROM calls ORDER BY date DESC", null).use { c ->
            while (c.moveToNext()) {
                arr.put(org.json.JSONObject().apply {
                    put("n", c.getString(0) ?: ""); put("nm", c.getString(1) ?: ""); put("t", c.getInt(2))
                    put("d", c.getLong(3)); put("du", c.getLong(4)); put("a", c.getString(5) ?: "")
                })
            }
        }
        return arr.toString()
    }

    /** Adds calls from a backup. Calls already in the history are skipped. Returns how many were added. */
    @Synchronized
    fun importJson(context: Context, text: String): Int {
        val arr = try { org.json.JSONArray(text) } catch (_: Exception) { return 0 }
        val w = db(context)
        var added = 0
        w.beginTransaction()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val v = ContentValues().apply {
                    put("number", o.optString("n")); put("name", o.optString("nm").ifEmpty { null })
                    put("type", o.optInt("t")); put("date", o.optLong("d")); put("duration", o.optLong("du"))
                    put("account", o.optString("a").ifEmpty { null })
                }
                if (w.insertWithOnConflict("calls", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1L) added++
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
        return added
    }

    /** Writes the whole history as a CSV file (opens in Excel). */
    fun exportCsv(context: Context, out: java.io.OutputStream) {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val w = out.bufferedWriter(Charsets.UTF_8)
        w.write("﻿Date,Name,Number,Type,Duration (sec)\r\n")
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        db(context).rawQuery("SELECT number, name, type, date, duration FROM calls ORDER BY date DESC", null).use { c ->
            while (c.moveToNext()) {
                val type = when (c.getInt(2)) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Rejected"
                    CallLog.Calls.BLOCKED_TYPE -> "Blocked"
                    else -> "Other"
                }
                w.write(listOf(fmt.format(java.util.Date(c.getLong(3))), q(c.getString(1) ?: ""), q(c.getString(0) ?: ""), type, c.getLong(4).toString()).joinToString(","))
                w.write("\r\n")
            }
        }
        w.flush()
    }
}
