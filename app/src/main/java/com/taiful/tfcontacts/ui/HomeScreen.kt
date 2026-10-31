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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
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
    isDefault: Boolean = true,
    onMakeDefault: () -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavigationBarItem(tab == 0, { onTab(0) }, { Icon(Icons.Filled.History, null) }, label = { Text("History") })
                NavigationBarItem(tab == 1, { onTab(1) }, { Icon(Icons.Filled.Dialpad, null) }, label = { Text("Keypad") })
                NavigationBarItem(tab == 2, { onTab(2) }, { Icon(Icons.Filled.Contacts, null) }, label = { Text("Contacts") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
            if (!isDefault) {
                Surface(
                    onClick = onMakeDefault,
                    color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Call, null, tint = Color(0xFFD97706))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Not your default phone app yet", fontWeight = FontWeight.SemiBold)
                            Text("Calls still use the old dialer. Tap to make TF Contacts the default.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Fix", color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(if (!isDefault) WindowInsets.statusBars else WindowInsets(0))) {
            when (tab) {
                0 -> RecentsTab(vm, onCall, onOpen, onNew)
                1 -> DialerTab(vm, dialText, onDialText, onCall, onOpen, onNew, onContacts = { onTab(2) }, onTools = onTools)
                else -> ContactsTab(vm, onOpen, onNew, onCall, onDuplicates, onTools, dupCount)
            }
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
                    title = { Text("${selected.size} selected") },
                    navigationIcon = { IconButton({ selected = emptySet() }) { Icon(Icons.Filled.Close, "Cancel") } },
                    actions = {
                        IconButton({ selected = filtered.map { it.id }.toSet() }) { Icon(Icons.Filled.SelectAll, "Select all") }
                        if (selected.size >= 2) IconButton({ confirmMerge = true }) { Icon(Icons.Filled.CallMerge, "Merge") }
                        IconButton({ showMove = true }) { Icon(Icons.Filled.DriveFileMove, "Move to SIM / phone") }
                        IconButton({ exportLauncher.launch("TF-Contacts-${selected.size}.vcf") }) { Icon(Icons.Filled.Share, "Export") }
                        IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text("TF Contacts", fontWeight = FontWeight.Bold)
                            Text("${all.size} contacts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    actions = {
                        IconButton(onDuplicates) {
                            BadgedBox(badge = { if (dupCount > 0) Badge { Text("$dupCount") } }) {
                                Icon(Icons.Filled.CallMerge, "Duplicates")
                            }
                        }
                        IconButton(onTools) { Icon(Icons.Filled.Settings, "Settings & tools") }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!selecting) FloatingActionButton(onClick = { onNew(null) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Filled.PersonAdd, "New contact")
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Search name, number, email, notes…") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val labels = listOf(
                    "All" to all.size,
                    "Phone" to all.count { SourceKind.PHONE in it.kinds },
                    "SIM" to all.count { SourceKind.SIM in it.kinds },
                    "Google" to all.count { SourceKind.GOOGLE in it.kinds },
                    "Favourites" to all.count { it.starred },
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
                            Text("$dupCount duplicate group(s) found", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("Tap to review and merge in one click", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }

            if (!loaded) {
                Text("Loading contacts…", Modifier.padding(24.dp))
            } else if (filtered.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (query.isBlank()) "No contacts" else "Nothing found for \"$query\"", textAlign = TextAlign.Center)
                }
            } else {
                val showFav = query.isBlank() && filter == 0
                val favs = if (showFav) filtered.filter { it.starred } else emptyList()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 90.dp)) {
                    if (favs.isNotEmpty()) {
                        item(key = "h_fav") { SectionHeader("★ Favourites") }
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
            title = { Text("Delete ${selected.size} contact(s)?") },
            text = { Text("A backup is made automatically before deleting.") },
            confirmButton = { TextButton({ confirmDelete = false; vm.delete(selList); selected = emptySet() }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (confirmMerge) {
        AlertDialog(
            onDismissRequest = { confirmMerge = false },
            title = { Text("Merge ${selected.size} contacts into one?") },
            text = { Text("All numbers, emails and notes go into one contact. A backup is made first.") },
            confirmButton = { TextButton({ confirmMerge = false; vm.merge(listOf(selList)); selected = emptySet() }) { Text("Merge") } },
            dismissButton = { TextButton({ confirmMerge = false }) { Text("Cancel") } }
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
            IconButton({ onCall(number) }) { Icon(Icons.Filled.Call, "Call", tint = TFGreen) }
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
            return "Note: …" + c.note.substring(maxOf(0, i - 15), minOf(c.note.length, i + q.length + 25)).replace('\n', ' ') + "…"
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
            title = { Text("Call history", fontWeight = FontWeight.Bold) },
            actions = { if (calls.isNotEmpty()) TextButton({ clearAll = true }) { Text("Clear all") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(!onlyMissed, { onlyMissed = false }, { Text("All") })
                FilterChip(onlyMissed, { onlyMissed = true }, { Text("Missed") })
            }
            if (shown.isEmpty()) {
                Text("No calls", Modifier.padding(32.dp).fillMaxWidth(), textAlign = TextAlign.Center)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { e ->
                    val c = byKey[Normalize.phoneKey(e.number)]
                    val name = c?.name ?: e.cachedName?.takeIf { it.isNotBlank() } ?: e.number.ifBlank { "Private number" }
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
                            Icon(if (c != null) Icons.Filled.Info else Icons.Filled.PersonAdd, if (c != null) "Details" else "Save contact", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    toDelete?.let { e ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this call from history?") },
            text = { Text(e.number) },
            confirmButton = { TextButton({ vm.deleteCalls(e.ids); toDelete = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } }
        )
    }
    if (clearAll) {
        AlertDialog(
            onDismissRequest = { clearAll = false },
            title = { Text("Delete the whole call history?") },
            confirmButton = { TextButton({ vm.deleteCalls(calls.flatMap { it.ids }); clearAll = false }) { Text("Delete all", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ clearAll = false }) { Text("Cancel") } }
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
    onOpen: (Long) -> Unit, onNew: (String?) -> Unit, onContacts: () -> Unit, onTools: () -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val contacts by vm.contacts.collectAsState()
    val calls by vm.calls.collectAsState()
    var search by rememberSaveable { mutableStateOf("") }
    val toneOn = remember { Settings.System.getInt(ctx.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1 }
    val tone = remember { try { ToneGenerator(AudioManager.STREAM_DTMF, 70) } catch (_: Exception) { null } }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    val byKey = remember(contacts) {
        val m = HashMap<String, Contact>()
        contacts.forEach { c -> c.phones.forEach { p -> if (p.key.isNotEmpty()) m.putIfAbsent(p.key, c) } }
        m
    }
    val searchResults = remember(search, contacts) { if (search.isBlank()) emptyList() else contacts.filter { matches(it, search) }.take(100) }
    val suggestions = remember(text, contacts) {
        val d = Normalize.digits(text)
        if (d.isEmpty()) emptyList()
        else contacts.filter { c -> c.phones.any { Normalize.digits(it.number).contains(d) } || c.t9.contains(d) }.take(40)
    }
    val recent = remember(calls) { calls.filter { it.number.isNotBlank() }.distinctBy { Normalize.phoneKey(it.number) }.take(30) }

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
        // Small search bar + Contacts button on the right
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f).height(46.dp)) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (search.isEmpty()) Text("Search contacts", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
                        BasicTextField(
                            search, { search = it }, singleLine = true,
                            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (search.isNotEmpty()) {
                        Icon(Icons.Filled.Close, "Clear", Modifier.size(20.dp).clip(CircleShape).clickable { search = ""; focus.clearFocus() })
                    }
                }
            }
            IconButton(onContacts) { Icon(Icons.Filled.Contacts, "Contacts", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onTools) { Icon(Icons.Filled.Settings, "Settings & tools") }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when {
                search.isNotBlank() -> {
                    if (searchResults.isEmpty()) item { Text("Nothing found for \"$search\"", Modifier.padding(24.dp)) }
                    items(searchResults, key = { "s" + it.id }) { c ->
                        val num = c.phones.firstOrNull()
                        PickRow(c.name, c.thumbUri, num?.number ?: c.emails.firstOrNull() ?: "", onClick = { onOpen(c.id) }, onCall = num?.let { { onCall(it.number) } })
                    }
                }
                text.isNotEmpty() -> {
                    items(suggestions, key = { "t" + it.id }) { c ->
                        val num = c.phones.firstOrNull { Normalize.digits(it.number).contains(Normalize.digits(text)) } ?: c.phones.firstOrNull()
                        PickRow(c.name, c.thumbUri, num?.number ?: "", onClick = { num?.let { onCall(it.number) } }, onInfo = { onOpen(c.id) })
                    }
                    if (suggestions.isEmpty()) {
                        item {
                            TextButton({ onNew(text) }, Modifier.padding(16.dp)) {
                                Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Save as new contact")
                            }
                        }
                    }
                }
                else -> {
                    if (recent.isNotEmpty()) item { SectionHeader("Recent") }
                    items(recent, key = { "r" + it.id }) { e ->
                        val c = byKey[Normalize.phoneKey(e.number)]
                        PickRow(
                            c?.name ?: e.cachedName?.takeIf { it.isNotBlank() } ?: e.number, c?.thumbUri,
                            (if (c != null) e.number + " • " else "") + relativeTime(e.date),
                            onClick = { onCall(e.number) },
                            onInfo = { if (c != null) onOpen(c.id) else onNew(e.number) }
                        )
                    }
                }
            }
        }

        if (search.isBlank()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Number display
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(54.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip48().clickable(enabled = text.isNotEmpty()) { onNew(text) }, contentAlignment = Alignment.Center) {
                            if (text.isNotEmpty()) Icon(Icons.Filled.PersonAdd, "Save contact", tint = MaterialTheme.colorScheme.primary)
                        }
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
                            if (text.isNotEmpty()) Icon(Icons.AutoMirrored.Filled.Backspace, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    padKeys.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { (k, letters) ->
                                Box(
                                    Modifier.padding(vertical = 4.dp).size(width = 92.dp, height = 58.dp)
                                        .clip(RoundedCornerShape(29.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .combinedClickable(onClick = { press(k) }, onLongClick = { if (k == "0") press("+") }),
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
                    // Call button in the middle, easy to reach with the thumb
                    Box(
                        Modifier.size(width = 150.dp, height = 62.dp)
                            .clip(RoundedCornerShape(31.dp))
                            .background(TFGreen)
                            .clickable {
                                if (text.isNotEmpty()) onCall(text)
                                else calls.firstOrNull { it.type == CallLog.Calls.OUTGOING_TYPE }?.let { onText(it.number) }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Call, "Call", tint = Color.White, modifier = Modifier.size(30.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickRow(name: String, photo: String?, sub: String, onClick: () -> Unit, onCall: (() -> Unit)? = null, onInfo: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name, photo, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onCall != null) IconButton(onCall) { Icon(Icons.Filled.Call, "Call", tint = TFGreen) }
        if (onInfo != null) IconButton(onInfo) { Icon(Icons.Filled.Info, "Details", tint = MaterialTheme.colorScheme.primary) }
    }
}

private fun Modifier.clip48() = this.clip(CircleShape)
