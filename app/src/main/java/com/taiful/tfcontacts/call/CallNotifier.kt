package com.taiful.tfcontacts.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.telecom.Call
import com.taiful.tfcontacts.R

/** Incoming-call alert (with Answer / Decline) and the "call in progress" notice. */
object CallNotifier {
    private const val CH_INCOMING = "tf_incoming_calls"
    private const val CH_ONGOING = "tf_ongoing_calls"
    private const val ID = 4401

    private val names = HashMap<String, String>()

    private fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH_INCOMING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null) // the phone's own ringtone is played by the system
                    enableVibration(false)
                }
            )
        }
        if (nm.getNotificationChannel(CH_ONGOING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null) }
            )
        }
    }

    private fun nameFor(context: Context, number: String?): String {
        if (number.isNullOrBlank()) return "Unknown number"
        return names.getOrPut(number) { CallHelper.lookup(context, number).name ?: number }
    }

    private fun pi(context: Context, req: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, req, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun bc(context: Context, req: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context, req, Intent(context, CallActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun icon(context: Context) = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_call_small)

    fun update(context: Context) {
        try {
            channels(context)
            val nm = context.getSystemService(NotificationManager::class.java)
            val ringing = CallManager.ringing()
            val screen = Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (ringing != null) {
                val who = nameFor(context, ringing.number())
                val n = Notification.Builder(context, CH_INCOMING)
                    .setSmallIcon(R.drawable.ic_call_small)
                    .setContentTitle(who)
                    .setContentText("Incoming call…")
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setFullScreenIntent(pi(context, 1, screen), true)
                    .setContentIntent(pi(context, 2, screen))
                    .addAction(Notification.Action.Builder(icon(context), "Decline", bc(context, 3, CallActionReceiver.DECLINE)).build())
                    .addAction(
                        Notification.Action.Builder(
                            icon(context), "Answer",
                            pi(context, 4, Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(InCallActivity.EXTRA_ANSWER, true))
                        ).build()
                    )
                    .build()
                nm.notify(ID, n)
                return
            }
            val call = CallManager.primary()
            if (call != null && call.st() != Call.STATE_DISCONNECTED) {
                val who = nameFor(context, call.number())
                val text = when (call.st()) {
                    Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
                    Call.STATE_HOLDING -> "On hold"
                    else -> "Ongoing call"
                }
                val b = Notification.Builder(context, CH_ONGOING)
                    .setSmallIcon(R.drawable.ic_call_small)
                    .setContentTitle(who)
                    .setContentText(text)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setOngoing(true)
                    .setContentIntent(pi(context, 2, screen))
                    .addAction(Notification.Action.Builder(icon(context), "Hang up", bc(context, 5, CallActionReceiver.HANGUP)).build())
                if (call.st() == Call.STATE_ACTIVE && call.details.connectTimeMillis > 0) {
                    b.setUsesChronometer(true).setWhen(call.details.connectTimeMillis).setShowWhen(true)
                }
                nm.notify(ID, b.build())
            } else {
                nm.cancel(ID)
                if (CallManager.calls.value.isEmpty()) names.clear()
            }
        } catch (_: Exception) {
        }
    }

    fun cancelAll(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java).cancel(ID)
        } catch (_: Exception) {
        }
    }
}
