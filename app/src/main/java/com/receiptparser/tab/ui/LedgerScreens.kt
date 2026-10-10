@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.receiptparser.tab.ui

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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import java.time.*
import java.io.File

@Composable fun HomeScreen(vm: LedgerViewModel, snackbar: SnackbarHostState, onFriend: (String) -> Unit, onEdit: (String) -> Unit, onShare: (String) -> Unit, nudge: () -> Unit, paid: (String, Long) -> Unit, onCapture: () -> Unit) {
    val s = vm.snapshot
    var archive by rememberSaveable { mutableStateOf(false) }
    var settled by rememberSaveable { mutableStateOf(false) }
    Page {
        Heading("Your tab", "A little less chasing. A little more catching up.")
        ErrorNotice(vm.error) { vm.error = null }
        if (vm.loading) { CircularProgressIndicator(); return@Page }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!archive, { archive = false }, label = { Text("Balances") })
            FilterChip(archive, { archive = true }, label = { Text("Receipts (${s.receipts.size})") })
        }
        if (!archive) {
            val open = s.friends.filter { s.balance(it.id) != 0L }.sortedByDescending { s.balance(it.id) }
            Panel {
                Text("Friends owe you", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(s.friends.sumOf { s.balance(it.id).coerceAtLeast(0) }), style = MaterialTheme.typography.displaySmall)
                val credits = s.friends.sumOf { (-s.balance(it.id)).coerceAtLeast(0) }
                if (credits > 0) Text("You also owe ${Money.format(credits)} in credits.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = nudge, enabled = open.any { s.balance(it.id) > 0 }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Send, null); Spacer(Modifier.width(8.dp)); Text("Nudge everyone") }
            }
            if (open.isEmpty()) EmptyState("Nothing outstanding", "Scan a receipt to start a tab, or enjoy being all square.")
            open.forEach { friend ->
                val balance = s.balance(friend.id)
                Card(onClick = { onFriend(friend.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(friend.name)
                            Column(Modifier.weight(1f)) { Text(friend.name, style = MaterialTheme.typography.titleMedium); Text(if (balance < 0) "You owe them" else s.oldestOutstanding(friend.id)?.let { "Open since ${dateText(it)}" } ?: "Open balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(Money.format(kotlin.math.abs(balance)), style = MaterialTheme.typography.titleMedium)
                        }
                        if (balance > 0) TextButton(onClick = { paid(friend.id, balance) }, enabled = !vm.busy, modifier = Modifier.align(Alignment.End)) { Text("Paid in full") }
                    }
                }
            }
            val zero = s.friends.filter { s.balance(it.id) == 0L }
            if (zero.isNotEmpty()) TextButton(onClick = { settled = !settled }) { Text("Settled (${zero.size})"); Icon(if (settled) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
            if (settled) zero.forEach { friend -> ListItem(headlineContent = { Text(friend.name) }, supportingContent = { Text("All square") }, trailingContent = { TextButton(onClick = { onFriend(friend.id) }) { Text("History") } }) }
        } else {
            if (s.receipts.isEmpty()) EmptyState("Your receipts will live here", "Saved splits stay editable, with the original receipt close at hand.")
            s.receipts.forEach { receipt -> Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    receipt.imagePath?.let { AsyncImage(File(it), "Receipt from ${receipt.venue}", Modifier.size(56.dp)) }
                    Column(Modifier.weight(1f)) { Text(receipt.venue, style = MaterialTheme.typography.titleMedium); Text(dateText(receipt.takenAt), style = MaterialTheme.typography.bodySmall) }
                    Text(Money.format(receipt.total))
                }
                if (!receipt.reconciled) Text("Check total · saved with a difference", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row { TextButton(onClick = { onEdit(receipt.id) }) { Text("View / edit") }; TextButton(onClick = { onShare(receipt.id) }) { Text("Share breakdown") } }
            } }
        }
        OutlinedButton(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("New receipt") }
    }
}

private data class HistoryRow(val id: String, val at: Long, val title: String, val amount: Long, val payment: Boolean, val note: String = "")
@Composable fun FriendScreen(id: String, vm: LedgerViewModel, back: () -> Unit, nudge: () -> Unit, onPayment: (String, Long, Long, String, () -> Unit) -> Unit, onEdit: (String) -> Unit) {
    val s = vm.snapshot
    val friend = s.friends.find { it.id == id }
    var payment by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var rename by remember { mutableStateOf(false) }
    var renameText by remember(friend?.name) { mutableStateOf(friend?.name ?: "") }
    if (friend == null) { Page { TextButton(onClick = back) { Text("Back") }; Text(if (vm.loading) "Loading friend…" else "Friend not found.") }; return }
    val balance = s.balance(id)
    val history = (s.receipts.filter { r -> s.participants.any { it.receiptId == r.id && it.friendId == id } }.map { HistoryRow(it.id, it.takenAt, it.venue, s.receiptAmount(it.id, id), false) } + s.payments.filter { it.friendId == id }.map { HistoryRow(it.id, it.paidAt, "Payment", -it.amount, true, it.note) }).sortedWith(compareBy<HistoryRow> { it.at }.thenBy { it.payment }.thenBy { it.id })
    var running = 0L
    val runningBalances = history.associate { running += it.amount; it.id to running }
    Page {
        TextButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, null); Text("Ledger") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(friend.name); Heading(friend.name, if (friend.archived) "Archived friend · history retained" else "Your running tab together") }
        ErrorNotice(vm.error) { vm.error = null }
        Panel {
            Text(if (balance < 0) "You owe ${friend.name}" else if (balance == 0L) "All square" else "${friend.name} owes you", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Money.format(kotlin.math.abs(balance)), style = MaterialTheme.typography.displaySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { payment = true }, enabled = !vm.busy) { Text("Record payment") }
                OutlinedButton(onClick = nudge, enabled = balance > 0) { Text("Nudge") }
            }
        }
        SectionHeading("History")
        if (history.isEmpty()) EmptyState("No shared bills yet", "Add ${friend.name} to your next receipt to start their history.")
        history.reversed().forEach { event -> Panel {
            AmountRow(event.title, event.amount, strong = true)
            Text(dateText(event.at) + if (event.note.isNotEmpty()) " · ${event.note}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountRow("Balance after", runningBalances.getValue(event.id))
            if (!event.payment) {
                TextButton(onClick = { expanded = if (event.id in expanded) expanded - event.id else expanded + event.id }) { Text(if (event.id in expanded) "Hide breakdown" else "Show breakdown") }
                if (event.id in expanded) {
                    val items = s.items.filter { it.receiptId == event.id }
                    items.forEach { item -> s.shares.find { it.lineItemId == item.id && it.friendId == id }?.let { AmountRow(item.name, it.amount) } }
                    s.participants.find { it.receiptId == event.id && it.friendId == id }?.let { p ->
                        AmountRow("Fees", p.feeShare); if (p.discountShare != 0L) AmountRow("Discount", -p.discountShare); if (p.rounding != 0L) AmountRow("Peso rounding", p.rounding)
                    }
                    TextButton(onClick = { onEdit(event.id) }) { Text("Edit receipt") }
                }
            }
        } }
        Row { TextButton(onClick = { rename = true }) { Text("Rename") }; TextButton(onClick = { vm.action { vm.repository.updateFriend(friend.copy(archived = !friend.archived)) } }, enabled = !vm.busy) { Text(if (friend.archived) "Restore friend" else "Archive friend") } }
    }
    if (payment) PaymentDialog(friend.name, balance, vm.busy, vm.error, { payment = false }) { amount, date, note -> onPayment(id, amount, date, note) { payment = false } }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Rename friend") }, text = { Column { OutlinedTextField(renameText, { renameText = it.take(60) }, label = { Text("Name") }); vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton(onClick = { vm.action { vm.repository.updateFriend(friend.copy(name = renameText)); rename = false } }, enabled = renameText.isNotBlank() && !vm.busy) { Text("Save name") } }, dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } })
}

@Composable fun PaymentDialog(name: String, balance: Long, busy: Boolean, error: String?, dismiss: () -> Unit, save: (Long, Long, String) -> Unit) {
    var amount by rememberSaveable { mutableLongStateOf(balance.coerceAtLeast(0)) }
    var valid by remember { mutableStateOf(true) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pickDate by remember { mutableStateOf(false) }
    val localDate = LocalDate.parse(date)
    AlertDialog(onDismissRequest = { if (!busy) dismiss() }, title = { Text("Payment from $name") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MoneyField("Amount received", amount, { amount = it }, onValidity = { valid = it })
        TextButton(onClick = { pickDate = true }) { Text("Date: ${dateText(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())}") }
        OutlinedTextField(note, { note = it.take(250) }, label = { Text("Note (optional)") }, placeholder = { Text("GCash reference") })
        if (amount > balance.coerceAtLeast(0)) Text("The extra amount becomes a credit. Your ledger will show that you owe $name.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { save(amount, if (localDate == LocalDate.now()) System.currentTimeMillis() else localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), note) }, enabled = valid && amount > 0 && !busy) { Text("Save payment") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") } })
    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), selectableDates = object : SelectableDates { override fun isSelectableDate(utcTimeMillis: Long) = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= LocalDate.now() })
        DatePickerDialog(onDismissRequest = { pickDate = false }, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }; pickDate = false }) { Text("Choose date") } }, dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

@Composable fun FriendsScreen(vm: LedgerViewModel, detail: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    Page {
        Heading("Your people", "Keep the usual crowd close.")
        ErrorNotice(vm.error) { vm.error = null }
        Button(onClick = { vm.error = null; adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Add friend") }
        if (vm.loading) CircularProgressIndicator()
        val friends = vm.snapshot.friends.filter { it.archived == archived }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!archived, { archived = false }, label = { Text("Active") }); FilterChip(archived, { archived = true }, label = { Text("Archived") }) }
        if (friends.isEmpty()) EmptyState(if (archived) "No archived friends" else "Who’s joining?", if (archived) "Archived friends keep their full history." else "Add a name. They’ll be ready for the next split.")
        friends.forEach { friend -> Card(onClick = { detail(friend.id) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Avatar(friend.name); Text(friend.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Icon(Icons.Outlined.ChevronRight, null) }
        } }
    }
    if (adding) NewFriendDialog(vm.busy, vm.error, { adding = false }) { name -> vm.action { vm.repository.addFriend(name); adding = false } }
}

@Composable fun SettingsScreen(vm: LedgerViewModel, saved: () -> Unit) {
    var gcash by rememberSaveable(vm.settings.gcash) { mutableStateOf(vm.settings.gcash) }
    var hours by rememberSaveable(vm.settings.reminderHours) { mutableStateOf(vm.settings.reminderHours.toString()) }
    var start by rememberSaveable(vm.settings.quietStart) { mutableStateOf(vm.settings.quietStart.toString()) }
    var end by rememberSaveable(vm.settings.quietEnd) { mutableStateOf(vm.settings.quietEnd.toString()) }
    var mode by remember(vm.settings.feeMode) { mutableStateOf(vm.settings.feeMode) }
    var own by remember(vm.settings.showOwnShare) { mutableStateOf(vm.settings.showOwnShare) }
    val validNumber = gcash.isBlank() || Regex("09\\d{9}").matches(gcash)
    Page {
        Heading("Make it yours", "A few defaults for the next dinner.")
        ErrorNotice(vm.error) { vm.error = null }
        Panel {
            SectionHeading("Getting paid")
            OutlinedTextField(gcash, { gcash = it.filter(Char::isDigit).take(11) }, label = { Text("Your GCash number") }, placeholder = { Text("09xxxxxxxxx") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = !validNumber, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone), supportingText = { Text(if (validNumber) "Included only when you share a nudge." else "Use 11 digits starting with 09.") })
        }
        Panel {
            SectionHeading("Gentle reminders")
            OutlinedTextField(hours, { hours = it }, label = { Text("Remind after (hours)") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), isError = hours.toIntOrNull() !in 1..720, supportingText = { Text("1–720 hours. Android chooses the exact delivery time.") })
            Text("Quiet hours · 24-hour clock", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("From (0–23)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), isError = start.toIntOrNull() !in 0..23)
                OutlinedTextField(end, { end = it }, label = { Text("Until (0–23)") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), isError = end.toIntOrNull() !in 0..23)
            }
            Text("Same start and end turns quiet hours off.", style = MaterialTheme.typography.bodySmall)
        }
        Panel {
            SectionHeading("Splitting & sharing")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(mode == FeeMode.EQUAL, { mode = FeeMode.EQUAL }, label = { Text("Fees equally") }); FilterChip(mode == FeeMode.PROPORTIONAL, { mode = FeeMode.PROPORTIONAL }, label = { Text("Fees by item amount") }) }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(own, { own = it }); Text("Show my share on breakdown images") }
        }
        Button(onClick = { vm.action { vm.settingsStore.save(TabSettings(gcash, hours.toInt(), start.toInt(), end.toInt(), mode, own)); saved() } }, enabled = validNumber && hours.toIntOrNull() in 1..720 && start.toIntOrNull() in 0..23 && end.toIntOrNull() in 0..23 && !vm.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (vm.busy) "Saving…" else "Save settings") }
        Text("Tab 0.1 · Stored on this device\nRemoving the app removes your ledger. Export/import backup is planned.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
