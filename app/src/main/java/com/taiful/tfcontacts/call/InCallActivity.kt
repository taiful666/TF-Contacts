package com.taiful.tfcontacts.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.ui.Avatar
import com.taiful.tfcontacts.ui.TFGreen
import com.taiful.tfcontacts.ui.TFRed
import com.taiful.tfcontacts.ui.TFTheme
import com.taiful.tfcontacts.ui.durationText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The screen shown during a phone call. */
class InCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ANSWER = "answer"
    }

    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        com.taiful.tfcontacts.ui.ThemePrefs.load(this)
        handleAnswer(intent)
        setContent { TFTheme(forceDark = true) { CallScreen(onFinish = { finishAndRemoveTask() }, onProximity = ::setProximity) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAnswer(intent)
    }

    private fun handleAnswer(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallManager.answer(CallManager.ringing())
            i.removeExtra(EXTRA_ANSWER)
        }
    }

    private fun setProximity(on: Boolean) {
        try {
            if (on) {
                if (proximity == null) {
                    val pm = getSystemService(PowerManager::class.java)
                    if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                        proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "tfcontacts:proximity")
                    }
                }
                if (proximity?.isHeld == false) proximity?.acquire(3 * 60 * 60 * 1000L)
            } else {
                if (proximity?.isHeld == true) proximity?.release()
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) && CallManager.ringing() != null) {
            try {
                CallHelper.telecom(this).silenceRinger()
            } catch (_: Exception) {
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun CallScreen(onFinish: () -> Unit, onProximity: (Boolean) -> Unit) {
    val calls by CallManager.calls.collectAsState()
    val version by CallManager.version.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val theme by com.taiful.tfcontacts.ui.ThemePrefs.theme.collectAsState()
    val call = remember(calls, version) { CallManager.primary() }
    var ended by remember { mutableStateOf(false) }

    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            ended = true
            delay(1200)
            onFinish()
        } else ended = false
    }

    val state = call?.st() ?: Call.STATE_DISCONNECTED
    val speakerOn = audio?.route == CallAudioState.ROUTE_SPEAKER
    val btOn = audio?.route == CallAudioState.ROUTE_BLUETOOTH
    LaunchedEffect(state, speakerOn, btOn) {
        onProximity(state in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING) && !speakerOn && !btOn)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(
                androidx.compose.ui.graphics.lerp(theme.seed, Color.Black, 0.15f),
                androidx.compose.ui.graphics.lerp(theme.seed, Color.Black, 0.55f),
                androidx.compose.ui.graphics.lerp(theme.seed, Color.Black, 0.82f)
            )))
            .safeDrawingPadding()
    ) {
        if (call == null || ended) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CallEnd, null, tint = TFRed, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Call ended", color = Color.White, fontSize = 22.sp)
            }
        } else if (state == Call.STATE_SELECT_PHONE_ACCOUNT) {
            SimChooser(call)
        } else {
            ActiveCall(call, state, calls, audio)
        }
    }
}

@Composable
private fun SimChooser(call: Call) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val accounts = remember { CallHelper.simAccounts(ctx) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(call.number() ?: "", color = Color.White, fontSize = 26.sp)
        Spacer(Modifier.height(8.dp))
        Text("Call with which SIM?", color = Color(0xFFB7C0DA), fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        accounts.forEach { h ->
            Surface(
                onClick = { call.phoneAccountSelected(h, false) },
                shape = RoundedCornerShape(16.dp), color = Color(0xFF1A2547),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SimCard, null, tint = Color(0xFF7FA6FF))
                    Spacer(Modifier.width(14.dp))
                    Text(CallHelper.accountLabel(ctx, h), color = Color.White, fontSize = 18.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { call.disconnect() }) { Text("Cancel", color = TFRed, fontSize = 16.sp) }
    }
}

@Composable
private fun ActiveCall(call: Call, state: Int, calls: List<Call>, audio: CallAudioState?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val number = call.number()
    var name by remember(number) { mutableStateOf<String?>(null) }
    var photo by remember(number) { mutableStateOf<String?>(null) }
    LaunchedEffect(number) {
        val info = withContext(Dispatchers.IO) { CallHelper.lookup(ctx, number) }
        name = info.name; photo = info.photo
    }
    var showKeypad by remember { mutableStateOf(false) }
    var dtmf by remember { mutableStateOf("") }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }

    val isConference = call.details.hasProperty(Call.Details.PROPERTY_CONFERENCE)
    val title = when {
        isConference -> "Conference call"
        name != null -> name!!
        number.isNullOrBlank() -> "Unknown number"
        else -> number ?: ""
    }
    val status = when (state) {
        Call.STATE_RINGING -> "Incoming call…"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_ACTIVE -> {
            val t = call.details.connectTimeMillis
            if (t > 0) durationText(((now - t) / 1000).coerceAtLeast(0)).ifEmpty { "0:00" } else "Connected"
        }
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Ending call…"
        else -> ""
    }
    val sim = remember(call.details.accountHandle) {
        call.details.accountHandle?.let { if (CallHelper.simAccounts(ctx).size > 1) CallHelper.accountLabel(ctx, it) else null }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        if (sim != null) {
            Text(sim, color = Color(0xFF7FA6FF), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        Text(status, color = Color(0xFFB7C0DA), fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        if (name != null && !number.isNullOrBlank()) {
            Text(number ?: "", color = Color(0xFFB7C0DA), fontSize = 16.sp)
        }

        // Other call waiting / on hold
        val other = calls.firstOrNull { it != call && it.parent == null && it.st() != Call.STATE_DISCONNECTED }
        if (other != null && state != Call.STATE_RINGING) {
            Spacer(Modifier.height(10.dp))
            Surface(
                onClick = {
                    if (other.st() == Call.STATE_HOLDING) {
                        call.hold(); other.unhold()
                    }
                },
                shape = RoundedCornerShape(20.dp), color = Color(0xFF1A2547)
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwapCalls, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${other.number() ?: "Other call"} — on hold (tap to swap)", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!showKeypad) {
            Box(contentAlignment = Alignment.Center) {
                if (state == Call.STATE_RINGING) Pulse()
                Avatar(title, photo, 150.dp, maxPx = 600)
            }
        } else {
            Text(dtmf, color = Color.White, fontSize = 28.sp, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
            keys.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { k ->
                        Box(
                            Modifier.padding(6.dp).size(70.dp).background(Color(0x22FFFFFF), CircleShape)
                                .clickable {
                                    dtmf += k
                                    call.playDtmfTone(k[0]); call.stopDtmfTone()
                                },
                            contentAlignment = Alignment.Center
                        ) { Text(k, color = Color.White, fontSize = 28.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (state == Call.STATE_RINGING) {
            Row(Modifier.fillMaxWidth().padding(bottom = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                BigRound(Icons.Filled.CallEnd, TFRed, "Decline") { CallManager.reject(call) }
                BigRound(Icons.Filled.Call, TFGreen, "Answer") { CallManager.answer(call) }
            }
        } else {
            val muted = audio?.isMuted == true
            val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
            val btAvailable = audio != null && (audio.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH) != 0
            val bt = audio?.route == CallAudioState.ROUTE_BLUETOOTH
            val holding = state == Call.STATE_HOLDING
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SmallToggle(if (muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", muted) { CallManager.setMuted(!muted) }
                SmallToggle(Icons.Filled.Dialpad, "Keypad", showKeypad) { showKeypad = !showKeypad }
                SmallToggle(Icons.AutoMirrored.Filled.VolumeUp, "Speaker", speaker) { CallManager.toggleSpeaker() }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SmallToggle(if (holding) Icons.Filled.PlayArrow else Icons.Filled.Pause, if (holding) "Resume" else "Hold", holding) {
                    if (holding) call.unhold() else call.hold()
                }
                if (btAvailable) {
                    SmallToggle(Icons.Filled.Bluetooth, "Bluetooth", bt) {
                        CallManager.setRoute(if (bt) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_BLUETOOTH)
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Box(Modifier.padding(bottom = 40.dp)) {
                BigRound(Icons.Filled.CallEnd, TFRed, "Call ended") { CallManager.hangup(call) }
            }
        }
    }
}

@Composable
private fun Pulse() {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(1f, 1.35f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "s")
    val a by t.animateFloat(0.45f, 0f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "a")
    Box(
        Modifier.size(150.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }
            .background(TFGreen, CircleShape)
    )
}

@Composable
private fun BigRound(icon: ImageVector, color: Color, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).background(color, CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(34.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun SmallToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(88.dp)) {
        Box(
            Modifier.size(62.dp)
                .background(if (on) Color.White else Color(0x22FFFFFF), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = if (on) Color(0xFF0B1430) else Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color(0xFFDDE3F2), fontSize = 12.sp, maxLines = 1)
    }
}
