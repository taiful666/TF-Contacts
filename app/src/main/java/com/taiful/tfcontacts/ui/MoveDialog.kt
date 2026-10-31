package com.taiful.tfcontacts.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.MainViewModel
import com.taiful.tfcontacts.data.Contact
import com.taiful.tfcontacts.data.SimHelper

/** Choose where to copy / move contacts: the phone's memory or a SIM card. */
@Composable
fun MoveDialog(vm: MainViewModel, list: List<Contact>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val sims = remember { SimHelper.sims(ctx) }
    // 0 = phone, 1.. = sims (or a single "SIM" if the list is empty)
    val targets = remember { listOf("ফোন মেমোরি (এই ফোনে)") + sims.map { it.label }.ifEmpty { listOf("SIM") } }
    var target by remember { mutableIntStateOf(0) }
    var move by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${list.size}টি কন্টাক্ট কোথায় নেবেন?") },
        text = {
            Column {
                targets.forEachIndexed { i, t ->
                    Row(
                        Modifier.fillMaxWidth().selectable(target == i, onClick = { target = i }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(target == i, onClick = { target = i })
                        Text(t, fontSize = 16.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("কীভাবে?", fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().selectable(!move, onClick = { move = false }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(!move, onClick = { move = false }); Text("কপি (আগেরটাও থাকবে)")
                }
                Row(Modifier.fillMaxWidth().selectable(move, onClick = { move = true }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(move, onClick = { move = true }); Text("সরান (আগের জায়গা থেকে মুছে যাবে)")
                }
                if (target > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "মনে রাখবেন: সিমে শুধু নাম (ছোট) ও প্রথম নম্বরটি রাখা যায়। ছবি, অন্য নম্বর, ইমেইল, নোট সিমে যায় না।" +
                            if (move) " \"সরান\" দিলে সেগুলো হারিয়ে যাবে (তবে একটা ব্যাকআপ রাখা হবে)।" else "",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.error
                    )
                }
                if (move && list.any { c -> c.raws.any { it.kind == com.taiful.tfcontacts.data.SourceKind.GOOGLE } }) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Google অ্যাকাউন্টের কন্টাক্ট সরালে সেটা Google থেকেও মুছে যাবে।",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (target == 0) vm.copyToPhone(list, move)
                else vm.copyToSim(list, sims.getOrNull(target - 1), move)
            }) { Text(if (move) "সরান" else "কপি করুন") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}
