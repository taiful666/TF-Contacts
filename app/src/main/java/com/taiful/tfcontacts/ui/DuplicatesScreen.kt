@file:OptIn(ExperimentalMaterial3Api::class)

package com.taiful.tfcontacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.MainViewModel
import com.taiful.tfcontacts.data.ContactWriter

@Composable
fun DuplicatesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val groups by vm.duplicates.collectAsState()
    // Same-number groups are ticked by default; same-name-only groups are not (they may be different people)
    var chosen by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(groups) {
        val keys = groups.map { it.key }.toSet()
        chosen = if (chosen == null) groups.filter { it.byNumber }.map { it.key }.toSet() else chosen!!.intersect(keys)
    }
    val sel = chosen ?: emptySet()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Duplicate contacts") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (groups.isNotEmpty()) {
                        TextButton({ chosen = if (sel.size == groups.size) emptySet() else groups.map { it.key }.toSet() }) {
                            Text(if (sel.size == groups.size) "Select none" else "Select all")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (groups.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Button(
                        onClick = { confirm = true }, enabled = sel.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp).height(54.dp)
                    ) { Text("Merge ${sel.size} selected group(s)", fontSize = 17.sp) }
                }
            }
        }
    ) { pad ->
        if (groups.isEmpty()) {
            Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.CheckCircle, null, tint = TFGreen, modifier = Modifier.padding(8.dp))
                Text("No duplicates. All clean!", fontSize = 18.sp)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item {
                Text(
                    "${groups.size} group(s) found. \"Same number\" groups are already selected. " +
                        "\"Same name only\" groups may be different people, so check them first. A backup of all contacts is made before merging.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(4.dp, 0.dp, 4.dp, 10.dp)
                )
            }
            items(groups, key = { it.key }) { g ->
                val on = g.key in sel
                val merged = remember(g) { ContactWriter.mergedData(g.members) }
                Surface(
                    onClick = { chosen = if (on) sel - g.key else sel + g.key },
                    shape = RoundedCornerShape(18.dp),
                    color = if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { chosen = if (on) sel - g.key else sel + g.key })
                            Text(
                                if (g.byNumber) "Same number" else "Same name only",
                                color = if (g.byNumber) TFGreen else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("• ${g.members.size} contacts", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        g.members.forEach { c ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(c.name, c.thumbUri, 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(c.phones.joinToString(", ") { it.number }.ifBlank { c.emails.firstOrNull() ?: "" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                SourceBadge(c.raws.first().kind, c.raws.first().label)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Text("After merge: ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        Text(merged.name, fontWeight = FontWeight.SemiBold)
                        Text(merged.phones.joinToString(", ") { it.number }, fontSize = 13.sp)
                        if (merged.emails.isNotEmpty()) Text(merged.emails.joinToString(", "), fontSize = 13.sp)
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Merge ${sel.size} group(s)?") },
            text = { Text("All numbers, emails, photos and notes of each group go into one contact; the extra copies are removed. A backup of all contacts is made first (restore it from Settings → Backup).") },
            confirmButton = {
                TextButton({
                    confirm = false
                    vm.merge(groups.filter { it.key in sel }.map { it.members })
                }) { Text("Merge") }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } }
        )
    }
}
