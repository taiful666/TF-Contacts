package com.taiful.tfcontacts.call

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps track of the phone calls the system hands to this app (when it is the default phone app). */
object CallManager {
    val calls = MutableStateFlow<List<Call>>(emptyList())
    val version = MutableStateFlow(0)
    val audio = MutableStateFlow<CallAudioState?>(null)
    var service: TFInCallService? = null

    /** Shown briefly after the last call ends. */
    val lastEnded = MutableStateFlow<Long>(0)

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = changed()
        override fun onDetailsChanged(call: Call, details: Call.Details) = changed()
    }

    private fun changed() {
        version.value = version.value + 1
        service?.let { CallNotifier.update(it) }
    }

    fun add(call: Call) {
        call.registerCallback(callback)
        calls.value = calls.value + call
        changed()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.value = calls.value - call
        if (calls.value.isEmpty()) lastEnded.value = System.currentTimeMillis()
        changed()
    }

    fun ringing(): Call? = calls.value.firstOrNull { it.st() == Call.STATE_RINGING }

    /** The call that should be on screen. */
    fun primary(): Call? {
        val list = calls.value
        return list.firstOrNull { it.st() == Call.STATE_RINGING }
            ?: list.firstOrNull { it.st() == Call.STATE_SELECT_PHONE_ACCOUNT }
            ?: list.firstOrNull { it.st() in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_PULLING_CALL) && it.parent == null }
            ?: list.firstOrNull { it.st() == Call.STATE_HOLDING && it.parent == null }
            ?: list.firstOrNull { it.parent == null }
            ?: list.firstOrNull()
    }

    fun answer(call: Call?) = call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    fun reject(call: Call?) = call?.reject(false, null)
    fun hangup(call: Call?) {
        if (call == null) return
        if (call.st() == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    fun setMuted(m: Boolean) = service?.setMuted(m)

    @Suppress("DEPRECATION")
    fun setRoute(route: Int) = service?.setAudioRoute(route)

    fun toggleSpeaker() {
        val a = audio.value
        val on = a != null && a.route == CallAudioState.ROUTE_SPEAKER
        setRoute(if (on) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }
}
