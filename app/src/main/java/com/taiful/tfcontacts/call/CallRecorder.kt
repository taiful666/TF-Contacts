package com.taiful.tfcontacts.call

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.provider.Settings
import android.telecom.Call
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Silent call recorder: no beep, no announcement. Recordings stay in the app's private folder on this phone.
 * Android 10+ only lets an app record during a call if the app has an Accessibility service switched on.
 */
object CallRecorder {
    val recording = MutableStateFlow(false)
    var startedAt = 0L
        private set
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var stoppedByUser = false

    fun dir(c: Context) = File(c.filesDir, "recordings").apply { mkdirs() }

    private fun prefs(c: Context) = c.getSharedPreferences("tf", Context.MODE_PRIVATE)
    fun autoOn(c: Context) = prefs(c).getBoolean("autoRecord", false)
    fun setAuto(c: Context, on: Boolean) = prefs(c).edit().putBoolean("autoRecord", on).apply()

    fun hasMicPermission(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Is the "TF Contacts call recorder" accessibility switch on? */
    fun accessibilityOn(c: Context): Boolean {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.split(':').any { it.startsWith(c.packageName + "/") && it.endsWith("RecorderAccessibilityService") }
    }

    private fun safe(s: String) = s.replace(Regex("[^\\p{L}\\p{N} +._-]"), "_").trim().take(40).ifEmpty { "Unknown" }

    fun start(c: Context, number: String?): Boolean {
        if (rec != null) return true
        if (!hasMicPermission(c)) return false
        val name = CallHelper.lookup(c, number).name
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val f = File(dir(c), "${safe(name ?: number ?: "Unknown")}__${safe(number ?: "")}__$stamp.m4a")
        for (source in listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC)) {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(c) else @Suppress("DEPRECATION") MediaRecorder()
            try {
                r.setAudioSource(source)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioChannels(1)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(96000)
                r.setOutputFile(f.absolutePath)
                r.prepare()
                r.start()
                rec = r
                file = f
                startedAt = System.currentTimeMillis()
                recording.value = true
                return true
            } catch (_: Exception) {
                try { r.release() } catch (_: Exception) {}
                f.delete()
            }
        }
        return false
    }

    fun stop() {
        val r = rec ?: return
        try {
            r.stop()
        } catch (_: Exception) {
            file?.delete() // too short / failed: don't keep an empty file
        }
        try { r.release() } catch (_: Exception) {}
        rec = null
        file = null
        recording.value = false
    }

    /** Record button on the call screen. */
    fun toggle(c: Context, number: String?): Boolean {
        return if (rec != null) {
            stoppedByUser = true
            stop(); true
        } else {
            stoppedByUser = false
            start(c, number)
        }
    }

    /** Called whenever calls change: auto-record and stop when the call ends. */
    fun onCallsChanged(c: Context) {
        val calls = CallManager.calls.value
        if (calls.isEmpty() || calls.all { it.st() == Call.STATE_DISCONNECTED }) {
            stop()
            stoppedByUser = false
            return
        }
        val active = calls.firstOrNull { it.st() == Call.STATE_ACTIVE }
        if (active != null && rec == null && autoOn(c) && !stoppedByUser) start(c, active.number())
    }

    data class Item(val file: File, val name: String, val number: String, val time: Long)

    fun list(c: Context): List<Item> = dir(c).listFiles { f -> f.name.endsWith(".m4a") }.orEmpty()
        .sortedByDescending { it.lastModified() }
        .map { f ->
            val parts = f.name.removeSuffix(".m4a").split("__")
            Item(f, parts.getOrNull(0) ?: f.name, parts.getOrNull(1) ?: "", f.lastModified())
        }
}

/** Only needed so Android allows call recording. It ignores all screen events. */
class RecorderAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
