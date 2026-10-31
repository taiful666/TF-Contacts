package com.taiful.tfcontacts.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {
    companion object {
        const val DECLINE = "com.taiful.tfcontacts.DECLINE"
        const val HANGUP = "com.taiful.tfcontacts.HANGUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DECLINE -> CallManager.reject(CallManager.ringing())
            HANGUP -> CallManager.hangup(CallManager.primary())
        }
    }
}
