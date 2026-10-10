@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.receiptparser.tab.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.receiptparser.tab.BuildConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    Page {
        TextButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null); Text("Back") }
        Heading(if (d.editing) "Edit the receipt" else "Check the receipt", "A quick check before everyone gets their share.")
        ErrorNotice(vm.error) { vm.error = null }
        d.imagePath?.let { path ->
            Card(onClick = { imageOpen = true }, modifier = Modifier.fillMaxWidth()) {
                AsyncImage(File(path), "Original receipt. Tap to enlarge.", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
            }
        }
        OutlinedTextField(d.venue, { text -> vm.change { it.copy(venue = text.take(120)) } }, label = { Text("Venue") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        SectionHeading("Items")
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
                    }, label = { Text("Qty") }, modifier = Modifier.width(80.dp), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = qty.toIntOrNull() !in 1..999)
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
            SectionHeading("Fees & total")
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
            Text(if (d.reconciled) "Matches the receipt ✓" else "${Money.format(kotlin.math.abs(d.total - d.computedTotal))} ${if (d.total > d.computedTotal) "short of" else "over"} the printed total. You can continue; the difference stays with your share.", color = if (d.reconciled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
        }
        val valid = d.items.isNotEmpty() && d.items.all { it.name.isNotBlank() } && invalid.values.none { it } && d.discount <= d.subtotal
        if (d.discount > d.subtotal) Text("Discount cannot exceed the item subtotal.", color = MaterialTheme.colorScheme.error)
        Button(onClick = next, enabled = valid && !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Assign items") }
        if (BuildConfig.DEBUG && d.ocrLines.isNotEmpty()) TextButton(onClick = { export.launch("receipt-${d.id}.json") }) { Text("Developer: export OCR fixture") }
        if (d.editing) TextButton(onClick = onDelete, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Delete receipt", color = MaterialTheme.colorScheme.error) }
    }
    if (imageOpen) Dialog(onDismissRequest = { imageOpen = false }) { Surface(shape = MaterialTheme.shapes.large) { Column {
        AsyncImage(d.imagePath?.let(::File), "Original receipt enlarged", Modifier.fillMaxWidth().weight(1f, false), contentScale = ContentScale.Fit)
        TextButton(onClick = { imageOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Close receipt") }
    } } }
}

@Composable fun AssignScreen(vm: ReceiptViewModel, ledger: LedgerViewModel, back: () -> Unit, editItems: () -> Unit, done: () -> Unit) {
    val d = vm.draft ?: return
    var selected by remember(d.id) { mutableStateOf(setOf<String>()) }
    var adding by remember { mutableStateOf(false) }
    var feeOptions by remember { mutableStateOf(false) }
    val friends = ledger.snapshot.friends.filter { !it.archived || d.participants.any { p -> p.id == it.id } }
    val names = ledger.snapshot.friends.associate { it.id to it.name } + (SELF to "You")
    val ids = listOf(SELF) + d.participants.map { it.id }
    val result = remember(d) { runCatching { SplitEngine.split(d) } }
    fun setMine(itemId: String) { vm.change { it.copy(items = it.items.map { item -> if (item.id == itemId) item.copy(owners = if (item.owners == setOf(SELF)) emptySet() else setOf(SELF)) else item }) } }
    Page {
        TextButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null); Text("Back") }
        Heading("Who had what?", "${d.venue.ifBlank { "Dinner" }} · ${Money.format(d.total)}")
        ErrorNotice(vm.error) { vm.error = null }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("At the table")
            TextButton(onClick = { adding = true }) { Icon(Icons.Outlined.Add, null); Text("Add friend") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InputChip(true, onClick = {}, label = { Text("You · paying") }, avatar = { Icon(Icons.Outlined.Person, null) })
            friends.forEach { friend ->
                val p = d.participants.find { it.id == friend.id }
                FilterChip(p != null, onClick = {
                    selected = emptySet()
                    vm.change { draft -> if (p == null) draft.copy(participants = draft.participants + Participant(friend.id))
                    else draft.copy(participants = draft.participants.filterNot { it.id == friend.id }, items = draft.items.map { it.copy(owners = it.owners - friend.id) }) }
                }, label = { Text(friend.name + if (p?.paysFees == false) " · no fees" else "") })
            }
        }
        if (friends.isEmpty()) Text("Add the friends at this table to start splitting.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (selected.isNotEmpty()) Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { SectionHeading("Assign ${selected.size} selected"); TextButton(onClick = { selected = emptySet() }) { Text("Clear selection") } }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ids.forEach { id ->
                    val allTagged = d.items.filter { it.id in selected }.all { id in it.owners }
                    FilterChip(allTagged, onClick = { vm.change { it.copy(items = it.items.map { row -> if (row.id in selected) row.copy(owners = if (allTagged) row.owners - id else row.owners + id) else row }) } }, label = { Text(names[id] ?: "Friend") })
                }
                AssistChip(onClick = { vm.change { it.copy(items = it.items.map { row -> if (row.id in selected) row.copy(owners = emptySet()) else row }) }; selected = emptySet() }, label = { Text("Share with everyone") })
            }
        }
        SectionHeading("Tap items, then pick people")
        d.items.forEach { item ->
            Card(colors = CardDefaults.cardColors(containerColor = if (item.id in selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = { selected = if (item.id in selected) selected - item.id else selected + item.id }, onLongClick = { setMine(item.id) }, onLongClickLabel = "Toggle mine only")) {
                Column(Modifier.padding(16.dp)) {
                    AmountRow("${if (item.qty > 1) "${item.qty}× " else ""}${item.name}", item.lineTotal, strong = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.owners.isEmpty()) "Everyone at the table" else item.owners.joinToString(" · ") { names[it] ?: "Friend" }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { setMine(item.id) }) { Text(if (item.owners == setOf(SELF)) "Share again" else "Mine only") }
                    }
                }
            }
        }
        TextButton(onClick = editItems) { Icon(Icons.Outlined.Edit, null); Text("Edit items & totals") }
        Panel {
            AmountRow("Service charge", d.serviceCharge)
            if (d.vatMode == VatMode.ADDITIVE) AmountRow("Added VAT", d.vat)
            Text("Split fees", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(d.feeMode == FeeMode.EQUAL, { vm.change { it.copy(feeMode = FeeMode.EQUAL) } }, label = { Text("Equally") })
                FilterChip(d.feeMode == FeeMode.PROPORTIONAL, { vm.change { it.copy(feeMode = FeeMode.PROPORTIONAL) } }, label = { Text("By item amount") })
            }
            if (d.participants.isNotEmpty()) TextButton(onClick = { feeOptions = !feeOptions }) { Text("Choose who pays fees") }
            if (feeOptions) d.participants.forEach { p -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(p.paysFees, { checked -> vm.change { it.copy(participants = it.participants.map { person -> if (person.id == p.id) person.copy(paysFees = checked) else person }) } })
                Text("${names[p.id]} pays fees")
            } }
        }
        result.getOrNull()?.let { split -> Panel {
            SectionHeading("The split")
            split.people.forEach { AmountRow(names[it.personId] ?: "Friend", it.total, strong = true) }
            HorizontalDivider()
            AmountRow("Receipt total", split.people.sumOf { it.total })
            Text("Friends’ totals round to the nearest peso. You cover the rounding difference.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!d.reconciled) Text("Items don’t match the printed total. The ${Money.format(d.total - d.computedTotal)} difference stays in your share.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (split.people.first().total < 0) Text("Your share is a credit: rounding or the printed total is lower than the assigned amounts. Check the receipt before saving.", color = MaterialTheme.colorScheme.error)
        } }
        result.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.save(done) }, enabled = result.isSuccess && !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text(if (vm.busy) "Saving…" else if (d.editing) "Save changes" else "Save split") }
    }
    if (adding) NewFriendDialog(ledger.busy, ledger.error, { adding = false }) { name -> ledger.action { val id = ledger.repository.addFriend(name); vm.change { it.copy(participants = it.participants + Participant(id)) }; adding = false } }
}

@Composable fun NewFriendDialog(busy: Boolean, error: String?, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Add a friend") }, text = { Column {
        OutlinedTextField(name, { name = it.take(60) }, label = { Text("Friend’s name") }, singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { save(name) }, enabled = name.isNotBlank() && !busy) { Text(if (busy) "Adding…" else "Add friend") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
}
