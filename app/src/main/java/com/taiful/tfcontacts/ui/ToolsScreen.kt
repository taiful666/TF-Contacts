@file:OptIn(ExperimentalMaterial3Api::class)

package com.taiful.tfcontacts.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

    val simContacts = all.filter { SourceKind.SIM in it.kinds }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("সেটিংস ও টুলস") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "ফিরে যান") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            // Summary
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("মোট ${all.size}টি কন্টাক্ট", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ফোনে ${all.count { SourceKind.PHONE in it.kinds }} • সিমে ${simContacts.size} • Google-এ ${all.count { SourceKind.GOOGLE in it.kinds }}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Header("ফোন অ্যাপ")
            Tool(
                if (isDefault) Icons.Filled.Verified else Icons.Filled.PhoneAndroid,
                if (isDefault) "TF Contacts এখন ডিফল্ট ফোন অ্যাপ ✓" else "ডিফল্ট ফোন অ্যাপ বানান",
                if (isDefault) "সব কল এই অ্যাপের ডায়াল প্যাড ও কল স্ক্রিন দিয়ে হবে। আগের অ্যাপে ফিরতে: ফোনের Settings → Apps → Default apps → Phone app"
                else "চাপ দিয়ে 'TF Contacts' বেছে নিন। তাহলে এর ডায়াল প্যাড ও কল স্ক্রিন সবসময় ব্যবহার হবে।"
            ) { if (!isDefault) onMakeDefault() }

            Header("পরিষ্কার করা")
            Tool(Icons.Filled.CallMerge, "ডুপ্লিকেট খুঁজুন ও মার্জ করুন", if (dups.isEmpty()) "এখন কোনো ডুপ্লিকেট নেই" else "${dups.size}টি ডুপ্লিকেট গ্রুপ পাওয়া গেছে", onDuplicates)

            Header("সিম ও ফোন")
            Tool(Icons.Filled.SimCard, "সিমের সব কন্টাক্ট ফোনে নিন", "সিমে ${simContacts.size}টি কন্টাক্ট আছে। ফোনে নিলে ছবি, একাধিক নম্বর, নোট রাখা যাবে।") {
                if (simContacts.isNotEmpty()) simAll = true else vm.message.value = "সিমে কোনো কন্টাক্ট পাওয়া যায়নি"
            }
            Text(
                "নির্দিষ্ট কন্টাক্ট সিমে বা ফোনে নিতে: কন্টাক্টের তালিকায় নামের ওপর একটু চেপে ধরে রাখুন, কয়েকটা বাছাই করুন, তারপর ওপরের 'সরান' আইকনে চাপ দিন।",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)
            )

            Header("ব্যাকআপ ও এক্সপোর্ট")
            Tool(Icons.Filled.Upload, "সব কন্টাক্ট এক্সপোর্ট করুন (.vcf)", "একটা ফাইল তৈরি হবে, যেকোনো ফোনে খুলে কন্টাক্ট ফিরিয়ে আনা যায়।") {
                exportAll.launch("TF-Contacts-backup-${all.size}.vcf")
            }
            Tool(Icons.Filled.Download, "ফাইল থেকে কন্টাক্ট আনুন (.vcf)", "আগের ব্যাকআপ ফাইল বেছে নিন। যেগুলো আগে থেকেই আছে সেগুলো আবার যোগ হবে না।") {
                importer.launch(arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*"))
            }
            Tool(Icons.Filled.Backup, "এখনই ব্যাকআপ নিন", "অ্যাপের ভেতরে (এই ফোনেই) একটা কপি রাখা হবে।") { vm.backupNow() }

            if (backups.isNotEmpty()) {
                Text("রাখা ব্যাকআপ (মার্জ/মোছার আগে নিজে থেকেই নেওয়া হয়):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                backups.forEach { b ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("${b.reason} • ${b.count}টি কন্টাক্ট", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ restoreItem = b }) { Icon(Icons.Filled.Restore, "ফিরিয়ে আনুন", tint = MaterialTheme.colorScheme.primary) }
                            IconButton({ pendingCopy = b.file; saveBackup.launch(b.file.name) }) { Icon(Icons.Filled.Download, "ফাইল হিসেবে সেভ", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }

            Header("নিরাপত্তা")
            Tool(
                Icons.Filled.CloudOff, "সম্পূর্ণ অফলাইন",
                "এই অ্যাপের ইন্টারনেট ব্যবহারের অনুমতিই নেই। কন্টাক্ট কোথাও পাঠানো হয় না। (ফোনের সেটিংসে Google অ্যাকাউন্ট সিঙ্ক চালু থাকলে Google-এর কন্টাক্টগুলো ফোন নিজে সিঙ্ক করে — সেটা এই অ্যাপের কাজ নয়।)"
            ) {}
            Spacer(Modifier.height(40.dp))
        }
    }

    if (simAll) {
        AlertDialog(
            onDismissRequest = { simAll = false },
            title = { Text("সিমের ${simContacts.size}টি কন্টাক্ট ফোনে নেবেন?") },
            text = { Text("\"সরান\" দিলে সিম থেকে মুছে ফোনে চলে যাবে। \"কপি\" দিলে দুই জায়গাতেই থাকবে (তখন ডুপ্লিকেট দেখাবে)।") },
            confirmButton = { TextButton({ simAll = false; vm.copyToPhone(simContacts, true) }) { Text("সরান") } },
            dismissButton = { TextButton({ simAll = false; vm.copyToPhone(simContacts, false) }) { Text("কপি") } }
        )
    }
    restoreItem?.let { b ->
        AlertDialog(
            onDismissRequest = { restoreItem = null },
            title = { Text("এই ব্যাকআপ থেকে ফিরিয়ে আনবেন?") },
            text = { Text("${b.title} — ${b.count}টি কন্টাক্ট। যেগুলো এখন নেই শুধু সেগুলোই যোগ হবে।") },
            confirmButton = { TextButton({ restoreItem = null; vm.restore(b.file) }) { Text("ফিরিয়ে আনুন") } },
            dismissButton = { TextButton({ restoreItem = null }) { Text("বাতিল") } }
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
