package com.taiful.tfcontacts.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/** The system talks to this service for every call once TF Contacts is the default phone app. */
class TFInCallService : InCallService() {

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        if (CallManager.service === this) CallManager.service = null
        CallNotifier.cancelAll(this)
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.service = this
        CallManager.add(call)
        if (call.st() != Call.STATE_RINGING) {
            // Outgoing call: open the call screen straight away
            try {
                startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
        // Incoming calls are shown through the full-screen notification (see CallNotifier)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
        // Save the finished call into the app's unlimited history (after the phone writes its log)
        val app = applicationContext
        android.os.Handler(mainLooper).postDelayed({
            Thread { com.taiful.tfcontacts.data.CallLogRepo.sync(app) }.start()
        }, 4000)
    }

    @Deprecated("Deprecated in Java")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        @Suppress("DEPRECATION")
        super.onCallAudioStateChanged(audioState)
        CallManager.audio.value = audioState
    }
}
