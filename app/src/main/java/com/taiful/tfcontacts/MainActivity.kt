package com.taiful.tfcontacts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.taiful.tfcontacts.call.CallHelper
import com.taiful.tfcontacts.ui.DetailScreen
import com.taiful.tfcontacts.ui.DuplicatesScreen
import com.taiful.tfcontacts.ui.EditScreen
import com.taiful.tfcontacts.ui.HomeScreen
import com.taiful.tfcontacts.ui.TFTheme
import com.taiful.tfcontacts.ui.ToolsScreen
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface Screen {
    data object Home : Screen
    data class Detail(val id: Long) : Screen
    data class Edit(val id: Long?, val number: String? = null) : Screen
    data object Duplicates : Screen
    data object Tools : Screen
}

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    /** Number sent by another app ("dial this number"). */
    private val incomingDial = MutableStateFlow<String?>(null)
    private val openTools = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.taiful.tfcontacts.ui.ThemePrefs.load(this)
        readDialIntent(intent)
        com.taiful.tfcontacts.cloud.Background.schedule(this)
        vm.checkUpdate()
        if (intent?.getBooleanExtra("openTools", false) == true) openTools.value = true
        setContent {
            TFTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(vm, incomingDial, openTools)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readDialIntent(intent)
        if (intent.getBooleanExtra("openTools", false)) openTools.value = true
    }

    override fun onResume() {
        super.onResume()
        vm.checkUpdate() // look for a newer version every time the app is opened
        if (hasContactPerms(this)) vm.start()
    }

    private fun readDialIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_DIAL || i.action == Intent.ACTION_VIEW) {
            incomingDial.value = i.data?.schemeSpecificPart ?: ""
        }
    }
}

fun hasContactPerms(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED

private val allPerms: Array<String> = buildList {
    add(Manifest.permission.READ_CONTACTS)
    add(Manifest.permission.WRITE_CONTACTS)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.WRITE_CALL_LOG)
    add(Manifest.permission.READ_PHONE_STATE)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@Composable
private fun Root(vm: MainViewModel, incomingDial: MutableStateFlow<String?>, openTools: MutableStateFlow<Boolean>) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val prefs = remember { ctx.getSharedPreferences("tf", Context.MODE_PRIVATE) }
    var granted by remember { mutableStateOf(hasContactPerms(ctx)) }
    var askedOnce by remember { mutableStateOf(false) }

    var isDefault by remember { mutableStateOf(CallHelper.isDefaultDialer(ctx)) }
    var showDefaultHelp by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = CallHelper.isDefaultDialer(ctx)
        if (isDefault) vm.message.value = "Done! TF Contacts is now your default phone app. All calls will use it."
        else showDefaultHelp = true // the phone did not show / accept the request: explain the manual way
    }
    fun askDefaultDialer() {
        if (CallHelper.isDefaultDialer(ctx)) {
            isDefault = true; return
        }
        val i = CallHelper.defaultDialerIntent(activity)
        if (i == null) {
            showDefaultHelp = true; return
        }
        try {
            roleLauncher.launch(i)
        } catch (_: Exception) {
            showDefaultHelp = true
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasContactPerms(ctx)
        askedOnce = true
        if (granted) {
            vm.start()
            if (!prefs.getBoolean("askedDialer2", false) && !CallHelper.isDefaultDialer(ctx)) {
                prefs.edit().putBoolean("askedDialer2", true).apply()
                askDefaultDialer()
            }
        }
    }
    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(allPerms)
        else {
            vm.start()
            // Ask once after this update if the app is still not the default phone app
            if (!prefs.getBoolean("askedDialer2", false) && !CallHelper.isDefaultDialer(ctx)) {
                prefs.edit().putBoolean("askedDialer2", true).apply()
                askDefaultDialer()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = CallHelper.isDefaultDialer(ctx)
        val now = hasContactPerms(ctx)
        if (now && !granted) vm.start()
        granted = now
    }

    if (!granted) {
        PermissionScreen(askedOnce) {
            if (askedOnce) {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
            } else permLauncher.launch(allPerms)
        }
        return
    }

    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var tab by remember { mutableStateOf(1) } // 0 History, 1 Keypad, 2 Contacts
    var dialText by remember { mutableStateOf("") }
    val dialReq by incomingDial.collectAsState()
    LaunchedEffect(dialReq) {
        dialReq?.let {
            dialText = it
            tab = 1
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            incomingDial.value = null
        }
    }

    val toolsReq by openTools.collectAsState()
    LaunchedEffect(toolsReq) {
        if (toolsReq) {
            if (stack.lastOrNull() != Screen.Tools) stack.add(Screen.Tools)
            openTools.value = false
        }
    }
    val updateBuild by vm.updateBuild.collectAsState()

    // First run: once contacts are loaded, open the duplicate cleaner if anything was found
    val loaded by vm.loaded.collectAsState()
    val dups by vm.duplicates.collectAsState()
    LaunchedEffect(loaded) {
        if (loaded && !prefs.getBoolean("firstScanDone", false)) {
            prefs.edit().putBoolean("firstScanDone", true).apply()
            if (vm.duplicates.value.isNotEmpty()) stack.add(Screen.Duplicates)
        }
    }

    // Calling, with SIM choice when the phone has two SIMs and no default
    var simChoiceFor by remember { mutableStateOf<String?>(null) }
    val call: (String) -> Unit = { number ->
        if (CallHelper.needsSimChoice(ctx)) simChoiceFor = number else CallHelper.place(ctx, number)
    }

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val push: (Screen) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(
            vm = vm, tab = tab, onTab = { tab = it },
            dialText = dialText, onDialText = { dialText = it },
            onOpen = { push(Screen.Detail(it)) },
            onNew = { push(Screen.Edit(null, it)) },
            onCall = call,
            onDuplicates = { push(Screen.Duplicates) },
            onTools = { push(Screen.Tools) },
            dupCount = dups.size,
            isDefault = isDefault,
            onMakeDefault = { askDefaultDialer() },
            updateBuild = updateBuild,
            onUpdate = { com.taiful.tfcontacts.cloud.Updates.openDownload(ctx) },
        )
        is Screen.Detail -> DetailScreen(vm, s.id, onBack = pop, onEdit = { push(Screen.Edit(s.id)) }, onCall = call)
        is Screen.Edit -> EditScreen(vm, s.id, s.number, onBack = pop, onSaved = { newId ->
            pop()
            if (s.id == null && newId != null) push(Screen.Detail(newId))
            else if (s.id != null && newId != null && newId != s.id) {
                pop(); push(Screen.Detail(newId))
            }
        })
        Screen.Duplicates -> DuplicatesScreen(vm, onBack = pop)
        Screen.Tools -> ToolsScreen(vm, onBack = pop, onDuplicates = { push(Screen.Duplicates) }, onMakeDefault = { askDefaultDialer() })
    }

    // Progress box for long jobs
    val busy by vm.busy.collectAsState()
    if (busy != null) {
        Dialog(onDismissRequest = {}) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(busy ?: "", fontSize = 16.sp)
                }
            }
        }
    }

    val msg by vm.message.collectAsState()
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { vm.message.value = null },
            confirmButton = { TextButton(onClick = { vm.message.value = null }) { Text("OK") } },
            text = { Text(msg ?: "", fontSize = 16.sp) }
        )
    }

    if (showDefaultHelp) {
        AlertDialog(
            onDismissRequest = { showDefaultHelp = false },
            title = { Text("Make TF Contacts your phone app") },
            text = {
                Column {
                    Text("The phone did not switch automatically. Please set it once by hand:", fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("1. Tap \"Open settings\" below", fontSize = 15.sp)
                    Text("2. Tap \"Phone app\" (or \"Default phone app\")", fontSize = 15.sp)
                    Text("3. Choose \"TF Contacts\"", fontSize = 15.sp)
                    Text("4. Come back here", fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "If you can't find it: phone Settings → Apps → Default apps → Phone app → TF Contacts",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton({
                    showDefaultHelp = false
                    val tries = listOf(
                        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                        Intent(Settings.ACTION_SETTINGS)
                    )
                    for (t in tries) {
                        try {
                            ctx.startActivity(t); break
                        } catch (_: Exception) {
                        }
                    }
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton({ showDefaultHelp = false }) { Text("Later") } }
        )
    }

    simChoiceFor?.let { number ->
        val accounts = remember { CallHelper.simAccounts(ctx) }
        AlertDialog(
            onDismissRequest = { simChoiceFor = null },
            title = { Text("Call with which SIM?") },
            text = {
                Column {
                    Text(number, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    accounts.forEach { h ->
                        Surface(
                            onClick = { simChoiceFor = null; CallHelper.place(ctx, number, h) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.SimCard, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Text(CallHelper.accountLabel(ctx, h), fontSize = 17.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { simChoiceFor = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PermissionScreen(askedOnce: Boolean, onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Contacts, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text("TF Contacts", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "TF Contacts needs permission to show your contacts and make calls. Everything stays on your phone — the app does not use the internet.",
            textAlign = TextAlign.Center, fontSize = 16.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGrant) { Text(if (askedOnce) "Allow in Settings" else "Allow", fontSize = 16.sp) }
        if (askedOnce) {
            Spacer(Modifier.height(10.dp))
            Text("Settings → Permissions → Contacts → Allow", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
