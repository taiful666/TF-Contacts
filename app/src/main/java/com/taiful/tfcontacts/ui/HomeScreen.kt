@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.taiful.tfcontacts.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.CallLog
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.MainViewModel
import com.taiful.tfcontacts.data.CallEntry
import com.taiful.tfcontacts.data.Contact
import com.taiful.tfcontacts.data.Normalize
import com.taiful.tfcontacts.data.SourceKind

@Composable
fun HomeScreen(
    vm: MainViewModel,
    tab: Int,
    onTab: (Int) -> Unit,
    dialText: String,
    onDialText: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onNew: (String?) -> Unit,
    onCall: (String) -> Unit,
    onDuplicates: () -> Unit,
    onTools: () -> Unit,
    dupCount: Int,
) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(tab == 0, { onTab(0) }, { Icon(Icons.Filled.Contacts, null) }, label = { Text("পরিচিতি") })
                NavigationBarItem(tab == 1, { onTab(1) }, { Icon(Icons.Filled.History, null) }, label = { Text("সাম্প্রতিক") })
                NavigationBarItem(tab == 2, { onTab(2) }, { Icon(Icons.Filled.Dialpad, null) }, label = { Text("ডায়াল প্যাড") })
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> ContactsTab(vm, onOpen, onNew, onCall, onDuplicates, onTools, dupCount)
                1 -> RecentsTab(vm, onCall, onOpen, onNew)
                else -> DialerTab(vm, dialText, onDialText, onCall, onOpen, onNew)
            }
        }
    }
}

// ---------------------------------------------------------------- Contacts

fun matches(c: Contact, q: String): Boolean {
    if (q.isBlank()) return true
    val ql = q.trim().lowercase()
    if (c.searchText.contains(ql)) return true
    val qd = Normalize.digits(ql)
    val onlyNumberish = ql.all { it.isDigit() || it in "+-() " || it in '০'..'৯' }
    return onlyNumberish && qd.isNotEmpty() && c.searchText.contains(qd)
}

@Composable
private fun ContactsTab(
    vm: MainViewModel,
    onOpen: (Long) -> Unit,
    onNew: (String?) -> Unit,
    onCall: (String) -> Unit,
    onDuplicates: () -> Unit,
    onTools: () -> Unit,
    dupCount: Int,
) {
    val all by vm.contacts.collectAsState()
    val loaded by vm.loaded.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 all, 1 phone, 2 sim, 3 google, 4 favourites
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var confirmMerge by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    val filtered = remember(all, query, filter) {
        all.filter { c ->
            val f = when (filter) {
                1 -> SourceKind.PHONE in c.kinds
                2 -> SourceKind.SIM in c.kinds
                3 -> SourceKind.GOOGLE in c.kinds
                4 -> c.starred
                else -> true
            }
            f && matches(c, query)
        }
    }
    val selList = remember(selected, all) { all.filter { it.id in selected } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-vcard")) { uri ->
        if (uri != null) vm.export(selList, uri)
        selected = emptySet()
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size}টি নির্বাচিত") },
                    navigationIcon = { IconButton({ selected = emptySet() }) { Icon(Icons.Filled.Close, "বাতিল") } },
                    actions = {
                        IconButton({ selected = filtered.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "সব") }
                        if (selected.size >= 2) IconButton({ confirmMerge = true }) { Icon(Icons.Filled.CallMerge, "মার্জ") }
                        IconButton({ showMove = true }) { Icon(Icons.Filled.DriveFileMove, "সরান") }
                        IconButton({ exportLauncher.launch("TF-Contacts-${selected.size}.vcf") }) { Icon(Icons.Filled.Share, "এক্সপোর্ট") }
                        IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "মুছুন") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("TF Contacts", fontWeight = FontWeight.Bold)
                            Text("মোট ${all.size}টি কন্টাক্ট", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onDuplicates) {
                            BadgedBox(badge = { if (dupCount > 0) Badge { Text("$dupCount") } }) {
                                Icon(Icons.Filled.CallMerge, "ডুপ্লিকেট")
                            }
                        }
                        IconButton(onTools) { Icon(Icons.Filled.Settings, "সেটিংস ও টুলস") }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selecting) FloatingActionButton(onClick = { onNew(null) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.PersonAdd, "নতুন কন্টাক্ট")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("নাম, নম্বর, ইমেইল, নোট… যেকোনো কিছু লিখে খুঁজুন") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "মুছুন") } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val labels = listOf(
                    "সব" to all.size,
                    "ফোন" to all.count { SourceKind.PHONE in it.kinds },
                    "সিম" to all.count { SourceKind.SIM in it.kinds },
                    "Google" to all.count { SourceKind.GOOGLE in it.kinds },
                    "প্রিয়" to all.count { it.starred },
                )
                labels.forEachIndexed { i, (l, n) ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text("$l ($n)") })
                }
            }

            if (dupCount > 0 && query.isBlank() && !selecting) {
                Surface(
                    onClick = onDuplicates,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CallMerge, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${dupCount}টি ডুপ্লিকেট গ্রুপ পাওয়া গেছে", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("চাপ দিয়ে দেখুন ও এক ক্লিকে মার্জ করুন", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            if (!loaded) {
                Text("কন্টাক্ট লোড হচ্ছে…", Modifier.padding(24.dp))
            } else if (filtered.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (query.isBlank()) "কোনো কন্টাক্ট নেই" else "\"$query\" দিয়ে কিছু পাওয়া যায়নি", textAlign = TextAlign.Center)
                }
            } else {
                val showFav = query.isBlank() && filter == 0
                val favs = if (showFav) filtered.filter { it.starred } else emptyList()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                    if (favs.isNotEmpty()) {
                        item(key = "h_fav") { SectionHeader("★ প্রিয়") }
                        items(favs, key = { "f" + it.id }) { c ->
                            ContactRow(c, c.id in selected, selecting, query,
                                onClick = { if (selecting) selected = selected.toggle(c.id) else onOpen(c.id) },
                                onLong = { selected = selected.toggle(c.id) }, onCall = onCall)
                        }
                    }
                    var lastHeader = ""
                    var hIndex = 0
                    filtered.forEach { c ->
                        val h = c.name.trim().firstOrNull()?.let { if (it.isLetter()) it.uppercaseChar().toString() else "#" } ?: "#"
                        if (query.isBlank() && h != lastHeader) {
                            lastHeader = h
                            hIndex++
                            item(key = "h_${hIndex}_$h") { SectionHeader(h) }
                        }
                        item(key = c.id) {
                            ContactRow(c, c.id in selected, selecting, query,
                                onClick = { if (selecting) selected = selected.toggle(c.id) else onOpen(c.id) },
                                onLong = { selected = selected.toggle(c.id) }, onCall = onCall)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("${selected.size}টি কন্টাক্ট মুছবেন?") },
            text = { Text("মোছার আগে স্বয়ংক্রিয়ভাবে একটা ব্যাকআপ রাখা হবে।") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(selList); selected = emptySet() }) { Text("মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("বাতিল") } }
        )
    }
    if (confirmMerge) {
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text("${selected.size}টি কন্টাক্ট এক করবেন?") },
            text = { Text("সব নম্বর, ইমেইল, নোট একটা কন্টাক্টে চলে যাবে। আগে ব্যাকআপ রাখা হবে।") },
            confirmButton = { TextButton({ confirmMerge = false; vm.merge(listOf(selList)); selected = emptySet() }) { Text("মার্জ করুন") } },
            dismissButton = { TextButton({ confirmMerge = false }) { Text("বাতিল") } }
        )
    }
    if (showMove) MoveDialog(vm, selList) { showMove = false; selected = emptySet() }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@Composable
fun SectionHeader(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun ContactRow(
    c: Contact, isSelected: Boolean, selecting: Boolean, query: String,
    onClick: () -> Unit, onLong: () -> Unit, onCall: (String) -> Unit,
) {
    val sub = remember(c, query) { subtitle(c, query) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Avatar(c.name, c.thumbUri, 46.dp)
            if (isSelected) {
                Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), CircleShape), contentAlignment = Alignment.Center) {
                    Text("✓", color = Color.White, fontSize = 22.sp)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (c.starred) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Color(0xFFF59E0B))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sub.isNotEmpty()) {
                    Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                }
                c.raws.map { it.kind to it.label }.distinct().take(2).forEach { (k, l) ->
                    SourceBadge(k, l); Spacer(Modifier.width(4.dp))
                }
            }
        }
        val number = c.phones.firstOrNull()?.number
        if (!selecting && number != null) {
            IconButton({ onCall(number) }) { Icon(Icons.Filled.Call, "কল", tint = TFGreen) }
        }
    }
}

/** Shows the field that matched the search, otherwise the first number. */
private fun subtitle(c: Contact, query: String): String {
    val q = query.trim().lowercase()
    if (q.isNotEmpty() && !c.name.lowercase().contains(q)) {
        val qd = Normalize.digits(q)
        c.phones.firstOrNull { it.number.lowercase().contains(q) || (qd.isNotEmpty() && Normalize.digits(it.number).contains(qd)) }?.let { return it.number }
        c.emails.firstOrNull { it.lowercase().contains(q) }?.let { return it }
        if (c.org.lowercase().contains(q)) return c.org
        c.addresses.firstOrNull { it.lowercase().contains(q) }?.let { return it }
        c.nicknames.firstOrNull { it.lowercase().contains(q) }?.let { return it }
        if (c.note.lowercase().contains(q)) {
            val i = c.note.lowercase().indexOf(q)
            return "নোট: …" + c.note.substring(maxOf(0, i - 15), minOf(c.note.length, i + q.length + 25)).replace('\n', ' ') + "…"
        }
    }
    return c.phones.firstOrNull()?.number ?: c.emails.firstOrNull() ?: c.org
}

// ---------------------------------------------------------------- Recents

@Composable
private fun RecentsTab(vm: MainViewModel, onCall: (String) -> Unit, onOpen: (Long) -> Unit, onNew: (String?) -> Unit) {
    val calls by vm.calls.collectAsState()
    val contacts by vm.contacts.collectAsState()
    val byKey = remember(contacts) {
        val m = HashMap<String, Contact>()
        contacts.forEach { c -> c.phones.forEach { p -> if (p.key.isNotEmpty()) m.putIfAbsent(p.key, c) } }
        m
    }
    var toDelete by remember { mutableStateOf<CallEntry?>(null) }
    var clearAll by remember { mutableStateOf(false) }
    var onlyMissed by rememberSaveable { mutableStateOf(false) }
    val shown = remember(calls, onlyMissed) { if (onlyMissed) calls.filter { it.type == CallLog.Calls.MISSED_TYPE } else calls }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("সাম্প্রতিক কল", fontWeight = FontWeight.Bold) },
            actions = { if (calls.isNotEmpty()) TextButton({ clearAll = true }) { Text("সব মুছুন") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!onlyMissed, { onlyMissed = false }, { Text("সব") })
                FilterChip(onlyMissed, { onlyMissed = true }, { Text("মিসড কল") })
            }
            if (shown.isEmpty()) {
                Text("কোনো কল নেই", Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { e ->
                    val c = byKey[Normalize.phoneKey(e.number)]
                    val name = c?.name ?: e.cachedName?.takeIf { it.isNotBlank() } ?: e.number.ifBlank { "লুকানো নম্বর" }
                    val (icon, color) = when (e.type) {
                        CallLog.Calls.INCOMING_TYPE -> Icons.AutoMirrored.Filled.CallReceived to TFGreen
                        CallLog.Calls.OUTGOING_TYPE -> Icons.AutoMirrored.Filled.CallMade to TFBlue
                        CallLog.Calls.MISSED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to TFRed
                        CallLog.Calls.REJECTED_TYPE -> Icons.AutoMirrored.Filled.CallMissed to Color(0xFFF59E0B)
                        CallLog.Calls.BLOCKED_TYPE -> Icons.Filled.Block to Color.Gray
                        else -> Icons.Filled.Call to Color.Gray
                    }
                    Row(
                        Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { if (e.number.isNotBlank()) onCall(e.number) }, onLongClick = { toDelete = e })
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(name, c?.thumbUri, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                name + if (e.count > 1) " (${e.count})" else "",
                                fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = if (e.type == CallLog.Calls.MISSED_TYPE) TFRed else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, Modifier.size(15.dp), tint = color)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    relativeTime(e.date) + (durationText(e.duration).let { if (it.isEmpty()) "" else " • $it" }),
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton({ if (c != null) onOpen(c.id) else onNew(e.number) }) {
                            Icon(if (c != null) Icons.Filled.Info else Icons.Filled.PersonAdd, if (c != null) "বিস্তারিত" else "সেভ করুন", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("এই কল রেকর্ড মুছবেন?") },
            text = { Text(e.number) },
            confirmButton = { TextButton({ vm.deleteCalls(e.ids); toDelete = null }) { Text("মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("বাতিল") } }
        )
    }
    if (clearAll) {
        AlertDialog(
            onDismissRequest = { clearAll = false },
            title = { Text("সব কল রেকর্ড মুছবেন?") },
            confirmButton = { TextButton({ vm.deleteCalls(calls.flatMap { it.ids }); clearAll = false }) { Text("সব মুছুন", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ clearAll = false }) { Text("বাতিল") } }
        )
    }
}

// ---------------------------------------------------------------- Dial pad

private val padKeys = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

@Composable
private fun DialerTab(
    vm: MainViewModel, text: String, onText: (String) -> Unit, onCall: (String) -> Unit,
    onOpen: (Long) -> Unit, onNew: (String?) -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val contacts by vm.contacts.collectAsState()
    val toneOn = remember { Settings.System.getInt(ctx.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1 }
    val tone = remember { try { ToneGenerator(AudioManager.STREAM_DTMF, 70) } catch (_: Exception) { null } }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    val suggestions = remember(text, contacts) {
        val d = Normalize.digits(text)
        if (d.isEmpty()) emptyList()
        else contacts.filter { c -> c.phones.any { Normalize.digits(it.number).contains(d) } || c.t9.contains(d) }.take(40)
    }

    fun press(k: String) {
        onText(text + k)
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (toneOn) {
            val t = when (k) {
                "*" -> ToneGenerator.TONE_DTMF_S
                "#" -> ToneGenerator.TONE_DTMF_P
                "+" -> ToneGenerator.TONE_DTMF_0
                else -> ToneGenerator.TONE_DTMF_0 + (k.toIntOrNull() ?: 0)
            }
            tone?.startTone(t, 120)
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Matching contacts
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (text.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Dialpad, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("নম্বর বা নামের অক্ষর টিপুন", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(suggestions, key = { it.id }) { c ->
                val num = c.phones.firstOrNull { Normalize.digits(it.number).contains(Normalize.digits(text)) } ?: c.phones.firstOrNull()
                Row(
                    Modifier.fillMaxWidth().clickable { num?.let { onCall(it.number) } }.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(c.name, c.thumbUri, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(num?.number ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ onOpen(c.id) }) { Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary) }
                }
            }
            if (text.isNotEmpty() && suggestions.isEmpty()) {
                item {
                    TextButton({ onNew(text) }, Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("নতুন কন্টাক্ট হিসেবে সেভ করুন")
                    }
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Number display
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(48.dp))
                    Text(
                        text, Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                        fontSize = when { text.length > 16 -> 22.sp; text.length > 12 -> 28.sp; else -> 34.sp },
                        fontWeight = FontWeight.Medium
                    )
                    Box(
                        Modifier.size(48.dp).clip48()
                            .combinedClickable(onClick = { if (text.isNotEmpty()) onText(text.dropLast(1)) }, onLongClick = { onText("") }),
                        contentAlignment = Alignment.Center
                    ) {
                        if (text.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.Backspace, "মুছুন", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                padKeys.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { (k, letters) ->
                            Box(
                                Modifier.padding(vertical = 4.dp).size(width = 92.dp, height = 60.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(30.dp))
                                    .combinedClickable(
                                        onClick = { press(k) },
                                        onLongClick = { if (k == "0") press("+") else if (k == "1") press("1") }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                                    if (letters.isNotEmpty()) Text(letters, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip48().clickable(enabled = text.isNotEmpty()) { onNew(text) }, contentAlignment = Alignment.Center) {
                        if (text.isNotEmpty()) Icon(Icons.Filled.PersonAdd, "সেভ", tint = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        Modifier.size(width = 120.dp, height = 62.dp)
                            .background(TFGreen, RoundedCornerShape(31.dp))
                            .clickable {
                                if (text.isNotEmpty()) onCall(text)
                                else vm.calls.value.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }?.let { onText(it.number) }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Call, "কল", tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Spacer(Modifier.size(56.dp))
                }
            }
        }
    }
}

private fun Modifier.clip48() = this.clip(CircleShape)
