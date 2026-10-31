@file:OptIn(ExperimentalMaterial3Api::class)

package com.taiful.tfcontacts.ui

import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.taiful.tfcontacts.cloud.DriveBackup
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.taiful.tfcontacts.MainViewModel
import com.taiful.tfcontacts.call.CallHelper
import com.taiful.tfcontacts.data.Backups
import com.taiful.tfcontacts.data.CallLogRepo
import com.taiful.tfcontacts.data.SourceKind

@Composable
fun ToolsScreen(vm: MainViewModel, onBack: () -> Unit, onDuplicates: () -> Unit, onMakeDefault: () -> Unit) {
    val ctx = LocalContext.current
    val all by vm.contacts.collectAsState()
    val dups by vm.duplicates.collectAsState()
    val busy by vm.busy.collectAsState()
    var isDefault by remember { mutableStateOf(CallHelper.isDefaultDialer(ctx)) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        isDefault = CallHelper.isDefaultDialer(ctx)
        refresh++
        onPauseOrDispose { }
    }
    val backups = remember(refresh, busy) { Backups.list(ctx) }
    var pendingCopy by remember { mutableStateOf<java.io.File?>(null) }
    var restoreItem by remember { mutableStateOf<Backups.Item?>(null) }
    var simAll by remember { mutableStateOf(false) }

    val exportAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) vm.export(all, uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        val f = pendingCopy
        if (uri != null && f != null) vm.copyFileTo(f, uri)
        pendingCopy = null
    }

    val exportCalls = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportCalls(uri)
    }

    // ---- Google Drive backup (optional)
    val driveTick by vm.driveTick.collectAsState()
    val email = remember(driveTick, refresh, busy) { DriveBackup.email(ctx) }
    val lastDrive = remember(driveTick, refresh, busy) { DriveBackup.lastBackup(ctx) }
    val driveErr = remember(driveTick, refresh, busy) { DriveBackup.lastError(ctx) }
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    fun setupMessage(e: Exception): String {
        val code = (e as? ApiException)?.statusCode
        return if (code == 10) "Google sign-in is not switched on for this app yet (error 10). Please send this message to Claude."
        else "Could not connect to Google: ${e.message ?: "unknown error"}. Check the internet and try again."
    }
    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            try {
                val r = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(res.data)
                val t = r.accessToken
                if (t != null) vm.driveConnected(t) else vm.message.value = "Google did not give access. Please try again."
            } catch (e: Exception) {
                vm.message.value = setupMessage(e)
            }
        }
    }
    fun connectDrive() {
        scope.launch {
            try {
                val r = Identity.getAuthorizationClient(ctx as Activity).authorize(DriveBackup.request(null)).await()
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                else r.accessToken?.let { vm.driveConnected(it) }
            } catch (e: Exception) {
                vm.message.value = setupMessage(e)
            }
        }
    }

    val simContacts = all.filter { SourceKind.SIM in it.kinds }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings & tools") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            // Summary
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("${all.size} contacts", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Phone ${all.count { SourceKind.PHONE in it.kinds }} • SIM ${simContacts.size} • Google ${all.count { SourceKind.GOOGLE in it.kinds }}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Header("Appearance")
            AppearanceCard()

            Header("Phone app")
            Tool(
                if (isDefault) Icons.Filled.Verified else Icons.Filled.PhoneAndroid,
                if (isDefault) "TF Contacts is your default phone app ✓" else "Make default phone app",
                if (isDefault) "All calls use this app's dial pad and call screen. To switch back: phone Settings → Apps → Default apps → Phone app"
                else "Tap and choose 'TF Contacts' so its dial pad and call screen are always used."
            ) { if (!isDefault) onMakeDefault() }

            Header("Clean up")
            Tool(Icons.Filled.CallMerge, "Find & merge duplicates", if (dups.isEmpty()) "No duplicates right now" else "${dups.size} duplicate group(s) found", onDuplicates)

            Header("SIM & phone")
            Tool(Icons.Filled.SimCard, "Move all SIM contacts to phone", "${simContacts.size} contacts on SIM. On the phone they can have photos, several numbers and notes.") {
                if (simContacts.isNotEmpty()) simAll = true else vm.message.value = "No SIM contacts found"
            }
            Text(
                "To move specific contacts to SIM or phone: in the contact list, press and hold a name, select the ones you want, then tap the Move icon at the top.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)
            )

            Header("Backup & export")
            Tool(Icons.Filled.Upload, "Export all contacts (.vcf)", "Creates a file you can open on any phone to get the contacts back.") {
                exportAll.launch("TF-Contacts-backup-${all.size}.vcf")
            }
            Tool(Icons.Filled.Download, "Import contacts from file (.vcf)", "Pick a backup file. Contacts that already exist are not added again.") {
                importer.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*"))
            }
            Tool(Icons.Filled.Backup, "Back up now", "Keeps a copy inside the app, on this phone.") { vm.backupNow() }

            if (backups.isNotEmpty()) {
                Text("Saved backups (made automatically before merge/delete):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                backups.forEach { b ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("${b.reason} • ${b.count} contacts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ restoreItem = b }) { Icon(Icons.Filled.Restore, "Restore", tint = MaterialTheme.colorScheme.primary) }
                            IconButton({ pendingCopy = b.file; saveBackup.launch(b.file.name) }) { Icon(Icons.Filled.Download, "Save as file", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }

            Header("Google Drive backup (optional)")
            if (email == null) {
                Tool(
                    Icons.Filled.CloudUpload, "Add Gmail for backup",
                    "Not required. If you add a Gmail, your contacts and call history are also backed up every day to your own Google Drive. On a new phone, add the same Gmail and everything comes back automatically."
                ) { connectDrive() }
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(email, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Text(
                                    if (lastDrive > 0) "Last backup: " + relativeTime(lastDrive) else "No backup yet",
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (driveErr != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(driveErr, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                            TextButton({ connectDrive() }) { Text("Connect again") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Backs up automatically once a day when there is internet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton({ vm.driveBackupNow() }) { Text("Back up now") }
                            TextButton({ vm.driveRestore() }) { Text("Restore") }
                            TextButton({ confirmRemove = true }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }

            Header("Call history")
            val callCount = remember(refresh, busy) { CallLogRepo.totalCount(ctx) }
            Tool(
                Icons.Filled.History, "Unlimited call history",
                "$callCount calls saved. Android keeps only about the last 500 calls, but TF Contacts keeps every call on this phone for as long as you want."
            ) {}
            Tool(Icons.Filled.TableChart, "Export call history (.csv)", "Opens in Excel or Google Sheets.") {
                exportCalls.launch("TF-Call-History.csv")
            }

            Header("Privacy")
            Tool(
                Icons.Filled.CloudOff, "Fully offline",
                "The app uses the internet only to check for updates and, if you added a Gmail, to back up to YOUR own Google Drive (a private app folder only TF Contacts can see). Without a Gmail, contacts and call history never leave your phone."
            ) {}
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove Gmail?") },
            text = { Text("Backups will stay only on this phone. The backup already on Google Drive is not deleted.") },
            confirmButton = { TextButton({ confirmRemove = false; vm.driveRemove() }) { Text("Remove", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmRemove = false }) { Text("Cancel") } }
        )
    }

    if (simAll) {
        AlertDialog(
            onDismissRequest = { simAll = false },
            title = { Text("Move ${simContacts.size} SIM contacts to phone?") },
            text = { Text("Move: removed from SIM and saved on the phone. Copy: kept in both places (they will show as duplicates).") },
            confirmButton = { TextButton({ simAll = false; vm.copyToPhone(simContacts, true) }) { Text("Move") } },
            dismissButton = { TextButton({ simAll = false; vm.copyToPhone(simContacts, false) }) { Text("Copy") } }
        )
    }
    restoreItem?.let { b ->
        AlertDialog(
            onDismissRequest = { restoreItem = null },
            title = { Text("Restore from this backup?") },
            text = { Text("${b.title} — ${b.count} contacts. Only contacts that are missing now will be added.") },
            confirmButton = { TextButton({ restoreItem = null; vm.restore(b.file) }) { Text("Restore") } },
            dismissButton = { TextButton({ restoreItem = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 4.dp))
}

@Composable
private fun Tool(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun AppearanceCard() {
    val ctx = LocalContext.current
    val theme by ThemePrefs.theme.collectAsState()
    val mode by ThemePrefs.mode.collectAsState()
    val full by ThemePrefs.fullScreenIncoming.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Theme colour", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(10.dp))
            AppTheme.entries.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { t ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable { ThemePrefs.setTheme(ctx, t) }.padding(vertical = 6.dp)
                        ) {
                            Box(
                                Modifier.size(42.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(t.accent, t.seed)))
                                    .border(if (t == theme) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                                contentAlignment = Alignment.Center
                            ) { if (t == theme) Icon(Icons.Filled.Check, null, tint = Color.White) }
                            Spacer(Modifier.height(4.dp))
                            Text(t.label, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Mode", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { ThemePrefs.setMode(ctx, m) }, label = { Text(if (m == ThemeMode.AUTO) "Auto (follow phone)" else m.label) })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("When a call comes in", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Row(Modifier.fillMaxWidth().clickable { ThemePrefs.setFullScreenIncoming(ctx, false) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(!full, { ThemePrefs.setFullScreenIncoming(ctx, false) })
                Column { Text("Small banner at the top"); Text("Answer or decline from the banner", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.fillMaxWidth().clickable { ThemePrefs.setFullScreenIncoming(ctx, true) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(full, { ThemePrefs.setFullScreenIncoming(ctx, true) })
                Column { Text("Full screen"); Text("The whole call screen opens, even while using the phone", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("When the phone is locked, the full call screen always shows.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
