@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.taiful.tfcontacts.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.MainViewModel
import com.taiful.tfcontacts.call.CallHelper

private fun copy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("TF Contacts", text))
    Toast.makeText(ctx, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
}

@Composable
fun DetailScreen(vm: MainViewModel, id: Long, onBack: () -> Unit, onEdit: () -> Unit, onCall: (String) -> Unit) {
    val ctx = LocalContext.current
    val all by vm.contacts.collectAsState()
    val c = remember(all, id) { all.firstOrNull { it.id == id } }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    val loaded by vm.loaded.collectAsState()
    LaunchedEffect(c, loaded) { if (c == null && loaded && all.isNotEmpty()) onBack() }
    if (c == null) return

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) vm.export(listOf(c), uri)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "ফিরে যান") } },
            actions = {
                IconButton({ vm.toggleStar(c) }) {
                    Icon(if (c.starred) Icons.Filled.Star else Icons.Filled.StarBorder, "প্রিয়", tint = if (c.starred) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onEdit) { Icon(Icons.Filled.Edit, "এডিট") }
                IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "আরও") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("সিম / ফোনে সরান বা কপি") }, { menu = false; showMove = true }, leadingIcon = { Icon(Icons.Filled.DriveFileMove, null) })
                    DropdownMenuItem({ Text("শেয়ার / এক্সপোর্ট (.vcf)") }, { menu = false; exportLauncher.launch("${c.name}.vcf") }, leadingIcon = { Icon(Icons.Filled.Share, null) })
                    DropdownMenuItem({ Text("মুছে ফেলুন") }, { menu = false; confirmDelete = true }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(c.name, c.photoUri ?: c.thumbUri, 132.dp, maxPx = 700)
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Text(c.name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            }
            if (c.org.isNotBlank() || c.title.isNotBlank()) {
                Text(listOf(c.title, c.org).filter { it.isNotBlank() }.joinToString(", "), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                c.raws.map { it.kind to it.label }.distinct().forEach { (k, l) -> SourceBadge(k, l) }
            }
            Spacer(Modifier.height(16.dp))

            val first = c.phones.firstOrNull()?.number
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                QuickAction(Icons.Filled.Call, "কল", TFGreen, first != null) { first?.let(onCall) }
                QuickAction(Icons.AutoMirrored.Filled.Message, "মেসেজ", TFBlue, first != null) { first?.let { CallHelper.sms(ctx, it) } }
                QuickAction(Icons.Filled.Edit, "এডিট", Color(0xFF7C3AED), true, onEdit)
            }
            Spacer(Modifier.height(18.dp))

            Card {
                if (c.phones.isEmpty()) Text("কোনো নম্বর নেই", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                c.phones.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = { onCall(p.number) }, onLongClick = { copy(ctx, p.number) })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Call, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.number, fontSize = 18.sp)
                            Text(p.typeLabel(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ CallHelper.sms(ctx, p.number) }) { Icon(Icons.AutoMirrored.Filled.Message, "মেসেজ", tint = TFBlue) }
                    }
                }
            }

            if (c.emails.isNotEmpty()) Card {
                c.emails.forEach { e ->
                    InfoRow(Icons.Filled.Email, e, "ইমেইল", onClick = {
                        try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$e"))) } catch (_: Exception) {}
                    }, onLong = { copy(ctx, e) })
                }
            }
            if (c.addresses.isNotEmpty()) Card {
                c.addresses.forEach { a -> InfoRow(Icons.Filled.LocationOn, a, "ঠিকানা", onClick = { copy(ctx, a) }, onLong = { copy(ctx, a) }) }
            }
            if (c.org.isNotBlank()) Card { InfoRow(Icons.Filled.Business, c.org, if (c.title.isNotBlank()) c.title else "প্রতিষ্ঠান", onClick = {}, onLong = { copy(ctx, c.org) }) }
            if (c.websites.isNotEmpty()) Card {
                c.websites.forEach { w -> InfoRow(Icons.Filled.Language, w, "ওয়েবসাইট", onClick = { copy(ctx, w) }, onLong = { copy(ctx, w) }) }
            }
            if (c.note.isNotBlank()) Card {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("নোট", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(c.note, fontSize = 16.sp) }
                    }
                }
            }
            Card {
                InfoRow(Icons.Filled.Storage, c.sourceLabel, "কোথায় সংরক্ষিত", onClick = {}, onLong = {})
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("\"${c.name}\" মুছবেন?") },
            text = { Text("মোছার আগে একটা ব্যাকআপ রাখা হবে।") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(listOf(c)); onBack() }) { Text("মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("বাতিল") } }
        )
    }
    if (showMove) MoveDialog(vm, listOf(c)) { showMove = false }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) { Column(Modifier.padding(vertical = 4.dp)) { content() } }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, label: String, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text, fontSize = 16.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick, enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            color = color.copy(alpha = if (enabled) 0.16f else 0.06f),
            modifier = Modifier.size(width = 84.dp, height = 56.dp)
        ) {
            Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, label, tint = if (enabled) color else color.copy(alpha = 0.4f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp)
    }
}
