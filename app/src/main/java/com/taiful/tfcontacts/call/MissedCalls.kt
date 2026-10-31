package com.taiful.tfcontacts.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import com.taiful.tfcontacts.MainActivity
import com.taiful.tfcontacts.R

/**
 * As the default phone app, Android asks TF Contacts to show the "missed call" notification.
 * Tapping it opens TF Contacts → History (instead of asking which app to use).
 */
object MissedCalls {
    private const val CH = "tf_missed_calls"
    private const val ID = 4410
    const val ACTION_CALL_BACK = "com.taiful.tfcontacts.CALL_BACK"

    fun show(c: Context, count: Int, number: String?) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (count <= 0) {
            nm.cancel(ID); return
        }
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(NotificationChannel(CH, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val name = number?.let { CallHelper.lookup(c, it).name } ?: number
        val title = if (count == 1) "Missed call" else "$count missed calls"
        val text = when {
            name.isNullOrBlank() -> "Tap to see your call history"
            count == 1 -> name
            else -> "Latest: $name"
        }
        val open = PendingIntent.getActivity(
            c, 20, Intent(c, MainActivity::class.java).putExtra("openHistory", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val icon = android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_call_small)
        val b = Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_call_small)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setNumber(count)
        if (!number.isNullOrBlank()) {
            val back = PendingIntent.getBroadcast(
                c, 21, Intent(c, MissedCallReceiver::class.java).setAction(ACTION_CALL_BACK).putExtra("number", number),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val sms = PendingIntent.getActivity(
                c, 22, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            b.addAction(Notification.Action.Builder(icon, "Call back", back).build())
            b.addAction(Notification.Action.Builder(icon, "Message", sms).build())
        }
        nm.notify(ID, b.build())
    }

    /** Called when History is opened: the missed calls count as seen. */
    fun clear(c: Context) {
        try {
            c.getSystemService(NotificationManager::class.java).cancel(ID)
        } catch (_: Exception) {
        }
        try {
            c.getSystemService(TelecomManager::class.java).cancelMissedCallsNotification()
        } catch (_: Exception) {
        }
        try {
            val v = ContentValues().apply {
                put(CallLog.Calls.NEW, 0)
                put(CallLog.Calls.IS_READ, 1)
            }
            c.contentResolver.update(CallLog.Calls.CONTENT_URI, v, "${CallLog.Calls.TYPE}=${CallLog.Calls.MISSED_TYPE} AND ${CallLog.Calls.NEW}=1", null)
        } catch (_: Exception) {
        }
    }
}

class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION -> MissedCalls.show(
                c,
                i.getIntExtra(TelecomManager.EXTRA_NOTIFICATION_COUNT, 0),
                i.getStringExtra(TelecomManager.EXTRA_NOTIFICATION_PHONE_NUMBER)
            )
            MissedCalls.ACTION_CALL_BACK -> {
                val n = i.getStringExtra("number") ?: return
                c.getSystemService(NotificationManager::class.java).cancel(4410)
                CallHelper.place(c, n)
            }
        }
    }
}
