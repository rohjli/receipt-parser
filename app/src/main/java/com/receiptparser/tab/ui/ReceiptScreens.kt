@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.receiptparser.tab.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.receiptparser.tab.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.receiptparser.tab.domain.*
import java.io.File

@Composable fun FixScreen(vm: ReceiptViewModel, next: () -> Unit, back: () -> Unit, onDelete: () -> Unit) {
    val d = vm.draft ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            try {
                val fixture = buildJsonObject {
                    put("source", "Captured receipt; expected values require manual verification")
                    put("name", d.venue)
                    put("lines", Json.encodeToJsonElement(d.ocrLines))
                    put("expected", buildJsonObject {
                        put("itemCount", d.items.size); put("subtotal", d.subtotal); put("serviceCharge", d.serviceCharge)
                        put("vat", d.vat); put("discount", d.discount); put("vatMode", d.vatMode.name)
                        put("total", d.total); put("reconciled", d.reconciled)
                        put("items", buildJsonArray { d.items.forEach { item -> add(buildJsonObject { put("name", item.name); put("qty", item.qty); put("lineTotal", item.lineTotal) }) } })
                    })
                }
                withContext(Dispatchers.IO) { requireNotNull(context.contentResolver.openOutputStream(uri)).bufferedWriter().use { it.write(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), fixture)) } }
            } catch (e: Exception) { vm.error = "Couldn’t export the fixture. Try another destination." }
        }
    }
    val invalid = remember(d.id) { mutableStateMapOf<String, Boolean>() }
    var skippedOpen by rememberSaveable(d.id) { mutableStateOf(false) }
    var imageOpen by remember { mutableStateOf(false) }
    val valid = d.items.isNotEmpty() && d.items.all { it.name.isNotBlank() } && invalid.values.none { it } && d.discount <= d.subtotal
    Page(bottomBar = {
        PrimaryAction("Assign items", next, enabled = valid && !vm.busy)
    }) {
        ReceiptStep(1, back)
        Heading(if (d.editing) "Edit the receipt" else "Check the receipt", "A quick check before everyone gets their share.")
        ErrorNotice(vm.error) { vm.error = null }
        d.imagePath?.let { path ->
            Card(onClick = { imageOpen = true }, modifier = Modifier.fillMaxWidth()) {
                AsyncImage(File(path), "Original receipt. Tap to enlarge.", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
            }
        }
        OutlinedTextField(d.venue, { text -> vm.change { it.copy(venue = text.take(120)) } }, label = { Text("Venue") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SectionLabel(Icons.Outlined.ReceiptLong, "Items", "Check names, quantities and line totals")
        d.items.forEach { item -> key(item.id) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(item.name, { name -> vm.change { it.copy(items = it.items.map { row -> if (row.id == item.id) row.copy(name = name.take(200)) else row }) } }, label = { Text("Item name") }, modifier = Modifier.weight(1f), singleLine = true, isError = item.name.isBlank())
                    IconButton(onClick = { invalid.remove(item.id); invalid.remove("qty-${item.id}"); vm.change { it.copy(items = it.items.filterNot { row -> row.id == item.id }) } }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${item.name.ifBlank { "item" }}") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    var qty by rememberSaveable(item.id) { mutableStateOf(item.qty.toString()) }
                    LaunchedEffect(qty) { invalid["qty-${item.id}"] = qty.toIntOrNull() !in 1..999 }
                    OutlinedTextField(qty, { text ->
                        qty = text
                        val number = text.toIntOrNull()?.takeIf { it in 1..999 }
                        invalid["qty-${item.id}"] = number == null
                        number?.let { value -> vm.change { it.copy(items = it.items.map { row -> if (row.id == item.id) row.copy(qty = value) else row }) } }
                    }, label = { Text("Qty") }, modifier = Modifier.width(80.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = qty.toIntOrNull() !in 1..999, supportingText = if (qty.toIntOrNull() !in 1..999) ({ Text("1–999") }) else null)
                    MoneyField("Line total", item.lineTotal, { value -> vm.change { it.copy(items = it.items.map { row -> if (row.id == item.id) row.copy(lineTotal = value) else row }) } }, Modifier.weight(1f)) { valid -> invalid[item.id] = !valid }
                }
            }
        } }
        OutlinedButton(onClick = { vm.change { it.copy(items = it.items + ReceiptItem()) } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add item") }
        if (d.skippedRows.isNotEmpty()) {
            TextButton(onClick = { skippedOpen = !skippedOpen }) { Text("Didn’t parse (${d.skippedRows.size})"); Icon(if (skippedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (skippedOpen) Panel { d.skippedRows.forEach { row -> TextButton(onClick = { vm.change { it.copy(items = it.items + ReceiptItem(name = row.take(200), rawRow = row), skippedRows = it.skippedRows - row) } }) { Text("+ $row") } } }
        }
        Panel {
            SectionLabel(Icons.Outlined.Calculate, "Fees & total", "Use the amounts printed on the receipt")
            MoneyField("Service charge", d.serviceCharge, { value -> vm.change { it.copy(serviceCharge = value) } }) { invalid["service"] = !it }
            MoneyField("VAT", d.vat, { value -> vm.change { it.copy(vat = value) } }) { invalid["vat"] = !it }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(d.vatMode == VatMode.INCLUSIVE, { vm.change { it.copy(vatMode = VatMode.INCLUSIVE) } }, label = { Text("VAT already included") })
                FilterChip(d.vatMode == VatMode.ADDITIVE, { vm.change { it.copy(vatMode = VatMode.ADDITIVE) } }, label = { Text("Add VAT") })
            }
            MoneyField("Discount", d.discount, { value -> vm.change { it.copy(discount = value) } }) { invalid["discount"] = !it }
            MoneyField("Printed total", d.total, { value -> vm.change { it.copy(total = value) } }) { invalid["total"] = !it }
        }
        Panel {
            AmountRow("Items + fees − discount", d.computedTotal, strong = true)
            Text(if (d.reconciled) "Matches the receipt ✓" else "${Money.format(kotlin.math.abs(d.total - d.computedTotal))} ${if (d.total > d.computedTotal) "short of" else "over"} the printed total. You can continue; the difference stays with your share.", color = if (d.reconciled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
        if (d.discount > d.subtotal) Text("Discount cannot exceed the item subtotal.", color = MaterialTheme.colorScheme.error)
        if (BuildConfig.DEBUG && d.ocrLines.isNotEmpty()) TextButton(onClick = { export.launch("receipt-${d.id}.json") }) { Text("Developer: export OCR fixture") }
        if (d.editing) TextButton(onClick = onDelete, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Delete receipt", color = MaterialTheme.colorScheme.error) }
    }
    if (imageOpen) Dialog(onDismissRequest = { imageOpen = false }) { Surface(shape = MaterialTheme.shapes.large) { Column {
        AsyncImage(d.imagePath?.let(::File), "Original receipt enlarged", Modifier.fillMaxWidth().weight(1f, false), contentScale = ContentScale.Fit)
        TextButton(onClick = { imageOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Close receipt") }
    } } }
}

@Composable fun NewFriendDialog(busy: Boolean, error: String?, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Add a friend") }, text = { Column {
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Friend’s name") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { save(name) }, enabled = name.isNotBlank() && !busy) { Text(if (busy) "Adding…" else "Add friend") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}
