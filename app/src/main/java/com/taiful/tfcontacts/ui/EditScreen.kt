@file:OptIn(ExperimentalMaterial3Api::class)

package com.taiful.tfcontacts.ui

import android.graphics.BitmapFactory
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.MainViewModel
import com.taiful.tfcontacts.data.ContactWriter.toEdit
import com.taiful.tfcontacts.data.EditData
import com.taiful.tfcontacts.data.PhoneNum
import com.taiful.tfcontacts.data.PhotoUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val phoneTypes = listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_MAIN, Phone.TYPE_OTHER)

@Composable
fun EditScreen(vm: MainViewModel, id: Long?, number: String?, onBack: () -> Unit, onSaved: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(id) { id?.let { vm.contact(it) } }
    val start = remember(existing) { existing?.toEdit() }

    var name by remember { mutableStateOf(start?.name ?: "") }
    val phones = remember {
        mutableStateListOf<PhoneNum>().apply {
            addAll(start?.phones ?: emptyList())
            if (isEmpty()) add(PhoneNum(number ?: ""))
        }
    }
    val emails = remember { mutableStateListOf<String>().apply { addAll(start?.emails ?: emptyList()) } }
    var org by remember { mutableStateOf(start?.org ?: "") }
    var title by remember { mutableStateOf(start?.title ?: "") }
    var address by remember { mutableStateOf(start?.addresses?.joinToString("\n\n") ?: "") }
    var website by remember { mutableStateOf(start?.websites?.joinToString("\n") ?: "") }
    var note by remember { mutableStateOf(start?.note ?: "") }
    var photoChanged by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }

    val preview = remember(photo) { photo?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val existingPhoto = rememberPhoto(if (photoChanged) null else existing?.photoUri, 500)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bytes = withContext(Dispatchers.IO) { PhotoUtil.readUri(ctx, uri) }
            if (bytes != null) {
                photo = bytes; photoChanged = true
            }
        }
    }

    fun save() {
        val d = EditData(
            name = name.trim(),
            phones = phones.filter { it.number.isNotBlank() },
            emails = emails.filter { it.isNotBlank() },
            org = org, title = title,
            addresses = address.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() },
            websites = website.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            note = note,
            photoChanged = photoChanged, photo = photo
        )
        if (d.name.isBlank() && d.phones.isEmpty() && d.emails.isEmpty()) {
            vm.message.value = "Enter a name or number"
            return
        }
        vm.save(existing, d, onSaved)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (existing == null) "New contact" else "Edit contact") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.Close, "Cancel") } },
            actions = { Button(onClick = { save() }, modifier = Modifier.padding(end = 8.dp)) { Text("Save") } }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                val img = preview ?: existingPhoto
                if (img != null) Image(img, null, Modifier.size(120.dp), contentScale = ContentScale.Crop)
                else Icon(Icons.Filled.AddAPhoto, "Photo", Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Row {
                TextButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text(if (preview != null || existingPhoto != null) "Change photo" else "Add photo (optional)")
                }
                if (preview != null || existingPhoto != null) {
                    TextButton({ photo = null; photoChanged = true }) { Text("Remove photo", color = MaterialTheme.colorScheme.error) }
                }
            }

            if (existing?.isSimOnly == true) {
                Text(
                    "This is a SIM contact. Saving moves it to phone memory, so it can have a photo, several numbers and notes.",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Field("Name", name, { name = it }, singleLine = false)

            Section("Phone numbers")
            phones.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        p.number, { phones[i] = p.copy(number = it) },
                        Modifier.weight(1f).padding(vertical = 4.dp),
                        label = { Text("Number") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    TypePicker(p) { phones[i] = it }
                    IconButton({ phones.removeAt(i) }) { Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error) }
                }
            }
            AddButton("Add another number") { phones.add(PhoneNum("")) }

            Section("Email")
            emails.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        e, { emails[i] = it }, Modifier.weight(1f).padding(vertical = 4.dp), label = { Text("Email") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    IconButton({ emails.removeAt(i) }) { Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error) }
                }
            }
            AddButton("Add email") { emails.add("") }

            Section("More details")
            Field("Company", org, { org = it })
            Field("Job title", title, { title = it })
            Field("Address (leave an empty line between addresses)", address, { address = it }, singleLine = false)
            Field("Website / links (one per line)", website, { website = it }, singleLine = false)
            Field("Notes (write as much as you like)", note, { note = it }, singleLine = false, minLines = 4)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save", fontSize = 17.sp) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp)
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true, minLines: Int = 1) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth().padding(vertical = 4.dp),
        label = { Text(label) }, singleLine = singleLine, minLines = minLines
    )
}

@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(text) }
    }
}

@Composable
private fun TypePicker(p: PhoneNum, onPick: (PhoneNum) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton({ open = true }) { Text(p.typeLabel(), fontSize = 13.sp, color = Color.Unspecified) }
        DropdownMenu(open, { open = false }) {
            phoneTypes.forEach { t ->
                DropdownMenuItem({ Text(PhoneNum("", t).typeLabel()) }, { onPick(p.copy(type = t, label = null)); open = false })
            }
        }
    }
}
