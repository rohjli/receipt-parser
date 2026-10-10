@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.receiptparser.tab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.receiptparser.tab.domain.*

@Composable fun AssignScreen(vm: ReceiptViewModel, ledger: LedgerViewModel, back: () -> Unit, editItems: () -> Unit, done: () -> Unit) {
    val draft = vm.draft ?: return
    var currentId by rememberSaveable(draft.id) { mutableStateOf(draft.items.firstOrNull()?.id) }
    var reviewing by rememberSaveable(draft.id) { mutableStateOf(false) }
    var tableOpen by rememberSaveable(draft.id) { mutableStateOf(false) }
    var adding by rememberSaveable(draft.id) { mutableStateOf(false) }
    var tableNotice by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    val friends = ledger.snapshot.friends.filter { !it.archived || draft.participants.any { p -> p.id == it.id } }
    val names = ledger.snapshot.friends.associate { it.id to it.name } + (SELF to "You")
    val ids = listOf(SELF) + draft.participants.map { it.id }
    val index = draft.items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val item = draft.items.getOrNull(index)
    // An empty checkbox selection is temporary UI state, never the stored "everyone" sentinel.
    var selected by rememberSaveable(draft.id, item?.id, ids, item?.owners) {
        mutableStateOf(item?.owners?.ifEmpty { ids.toSet() }?.toList() ?: emptyList())
    }
    val result = remember(draft) { runCatching { SplitEngine.split(draft) } }
    val canContinue = item != null && selected.isNotEmpty() && !vm.busy
    val canLeaveItem = !vm.busy && (reviewing || item == null || selected.isNotEmpty())
    fun choose(people: List<String>, everyone: Boolean = false) {
        selected = people
        if (people.isNotEmpty() && item != null) vm.change { receipt ->
            receipt.copy(items = receipt.items.map { row ->
                if (row.id == item.id) row.copy(owners = if (everyone) emptySet() else people.toSet()) else row
            })
        }
    }
    fun goBack() {
        if (!canLeaveItem) return
        when {
            reviewing -> reviewing = false
            index > 0 -> currentId = draft.items[index - 1].id
            else -> back()
        }
    }
    BackHandler(enabled = !vm.busy && !tableOpen && !adding && (reviewing || index > 0 || selected.isEmpty())) { goBack() }
    key(reviewing, item?.id) {
        Page(bottomBar = {
            if (reviewing) {
                Text("${ids.size} ${if (ids.size == 1) "person" else "people"} · ${Money.format(draft.total)}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                PrimaryAction(if (vm.busy) "Saving…" else if (draft.editing) "Save changes" else "Save split", { vm.save(done) }, enabled = result.isSuccess, busy = vm.busy)
            } else if (item != null) {
                if (selected.isEmpty()) Text("Choose at least one person, or tap Everyone.", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (index > 0) TextButton(onClick = { goBack() }, enabled = canLeaveItem, modifier = Modifier.weight(1f)) { Text("Previous item") }
                    Box(Modifier.weight(1f)) {
                        PrimaryAction(if (index == draft.items.lastIndex) "Review split" else "Next item", {
                            if (index == draft.items.lastIndex) reviewing = true else currentId = draft.items[index + 1].id
                        }, enabled = canContinue)
                    }
                }
            } else PrimaryAction("Edit items & totals", editItems, enabled = !vm.busy)
        }) {
            ReceiptStep(2, ::goBack, backEnabled = canLeaveItem)
            Heading(if (reviewing) "Review the split" else "Who had this?", "${draft.venue.ifBlank { "Dinner" }} · ${Money.format(draft.total)}")
            ErrorNotice(vm.error) { vm.error = null }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("People on this receipt", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.CenterVertically).semantics { heading() })
                    TextButton(onClick = { tableOpen = true }, enabled = canLeaveItem,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Edit people on this receipt" }) { Text("Edit people") }
                }
                ReceiptPeopleBubbles(ids.map { names[it] ?: "Friend" }, canLeaveItem) { tableOpen = true }
            }
            if (ids.size == 1) Text("Add someone to split this receipt.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            tableNotice?.let { notice -> Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (reviewing) {
                AssignmentTotals(draft, result, names, vm)
                SectionHeading("All items")
                draft.items.forEach { row ->
                    Panel {
                        AmountRow("${if (row.qty > 1) "${row.qty}× " else ""}${row.name}", row.lineTotal, strong = true)
                        Text(ownershipLabel(row, names), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { currentId = row.id; reviewing = false }, enabled = !vm.busy,
                            modifier = Modifier.semantics { contentDescription = "Edit ${row.name}" }) { Text("Edit people") }
                    }
                }
                TextButton(onClick = editItems, enabled = !vm.busy) { Icon(Icons.Outlined.Edit, null); Text("Edit items & totals") }
            } else if (item != null) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Item ${index + 1} of ${draft.items.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
                    TextButton(onClick = { reviewing = true }, enabled = canContinue) { Text("View all items") }
                }
                LinearProgressIndicator(progress = { (index + 1).toFloat() / draft.items.size }, modifier = Modifier.fillMaxWidth())
                Panel {
                    SectionHeading(item.name)
                    Text(Money.format(item.lineTotal), style = MaterialTheme.typography.headlineLarge)
                    if (item.qty > 1) Text("${item.qty} items · total for the whole line", style = MaterialTheme.typography.bodySmall)
                    Text(if (item.owners.isEmpty()) "Everyone at the table is selected by default." else "Choose one person, or a few to share.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(item.owners.isEmpty() && selected.isNotEmpty(), { choose(ids, everyone = true) }, enabled = !vm.busy, label = { Text("Everyone") })
                    FilterChip(item.owners == setOf(SELF) && selected == listOf(SELF), { choose(listOf(SELF)) }, enabled = !vm.busy, label = { Text("Just me") })
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ids.forEach { id ->
                        val checked = id in selected
                        val share = if (selected.isNotEmpty()) result.getOrNull()?.items?.firstOrNull { it.itemId == item.id && it.personId == id }?.amount else null
                        AssignmentPersonRow(names[id] ?: "Friend", checked, !vm.busy, share) {
                            choose(if (checked) selected - id else selected + id)
                        }
                    }
                }
                if (selected.isNotEmpty()) Text("Item amounts before fees, discounts and final rounding. Review the split for everyone’s total.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                result.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else EmptyState("No items yet", "Go back to the receipt and add an item before splitting.")
        }
    }
    if (tableOpen) ModalBottomSheet(onDismissRequest = { tableOpen = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Heading("People on this receipt", "Choose everyone on this receipt. You’ll choose who had each item separately.")
            Text("You · paying", style = MaterialTheme.typography.titleMedium)
            friends.forEach { friend ->
                val participant = draft.participants.find { it.id == friend.id }
                AssignmentPersonRow(friend.name, participant != null, !vm.busy) {
                    val reset = draft.items.filter { it.owners == setOf(friend.id) }
                    tableNotice = if (participant != null && reset.isNotEmpty()) "${reset.joinToString { it.name }} now shared by everyone because ${friend.name} was the only owner." else null
                    vm.change { receipt ->
                        if (participant == null) receipt.copy(participants = receipt.participants + Participant(friend.id))
                        else receipt.copy(participants = receipt.participants.filterNot { it.id == friend.id }, items = receipt.items.map { it.copy(owners = it.owners - friend.id) })
                    }
                }
            }
            Text("Removing someone removes them from their items. If they were the only owner, those items return to everyone.", style = MaterialTheme.typography.bodySmall)
            tableNotice?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            OutlinedButton(onClick = { adding = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Text("Add friend") }
            PrimaryAction("Done", { tableOpen = false }, enabled = !vm.busy)
        }
    }
    if (adding) NewFriendDialog(ledger.busy, ledger.error, { adding = false }) { name ->
        ledger.action {
            val id = ledger.repository.addFriend(name)
            vm.change { it.copy(participants = it.participants + Participant(id)) }
            adding = false
        }
    }
}

@Composable private fun ReceiptPeopleBubbles(names: List<String>, enabled: Boolean, onEdit: () -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = 8.dp
        val spacingPx = with(density) { spacing.roundToPx() }
        val minimumWidthPx = with(density) { (72.dp * fontScale.coerceAtLeast(1f)).roundToPx() }
        val columns = ((constraints.maxWidth + spacingPx) / (minimumWidthPx + spacingPx)).coerceAtLeast(1)
        // Floor in pixels so rounding each cell cannot push the last bubble onto another row.
        val bubbleWidth = with(density) { ((constraints.maxWidth - spacingPx * (columns - 1)) / columns).toDp() }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            names.forEachIndexed { index, name ->
                Column(Modifier.width(bubbleWidth).semantics(mergeDescendants = true) {},
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Avatar(name, Modifier.clearAndSetSemantics {})
                    Text(name, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    if (index == 0) Text("Paying", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onEdit, enabled = enabled, shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(0.dp), modifier = Modifier.width(bubbleWidth).heightIn(min = 48.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, contentColor = LocalContentColor.current,
                        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f))) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null) }
                    }
                    Text("Add people", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable private fun AssignmentPersonRow(name: String, checked: Boolean, enabled: Boolean, amount: Long? = null, onToggle: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                amount?.let { Text(Money.format(it), style = MaterialTheme.typography.bodySmall) }
            }
            Checkbox(checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

private fun ownershipLabel(item: ReceiptItem, names: Map<String, String>): String =
    if (item.owners.isEmpty()) "Everyone at the table" else item.owners.joinToString(" · ") { names[it] ?: "Friend" }

@Composable private fun AssignmentTotals(draft: ReceiptDraft, result: Result<SplitResult>, names: Map<String, String>, vm: ReceiptViewModel) {
    var feeOptions by rememberSaveable(draft.id) { mutableStateOf(false) }
    result.getOrNull()?.let { split -> Panel {
        SectionLabel(Icons.Outlined.PeopleOutline, "The split", "Everyone’s share, ready to save")
        split.people.forEach { AmountRow(names[it.personId] ?: "Friend", it.total, strong = true) }
        ReceiptDivider()
        AmountRow("Receipt total", split.people.sumOf { it.total })
        Text("Friends’ totals round to the nearest peso. You cover the rounding difference.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!draft.reconciled) Text("Items don’t match the printed total. The ${Money.format(draft.total - draft.computedTotal)} difference stays in your share.", color = MaterialTheme.colorScheme.error)
        if (split.people.first().total < 0) Text("Your share is a credit: rounding or the printed total is lower than the assigned amounts. Check the receipt before saving.", color = MaterialTheme.colorScheme.error)
    } }
    Panel {
        AmountRow("Service charge", draft.serviceCharge)
        if (draft.vatMode == VatMode.ADDITIVE) AmountRow("Added VAT", draft.vat)
        Text("Split fees", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(draft.feeMode == FeeMode.EQUAL, { vm.change { it.copy(feeMode = FeeMode.EQUAL) } }, enabled = !vm.busy, label = { Text("Equally") })
            FilterChip(draft.feeMode == FeeMode.PROPORTIONAL, { vm.change { it.copy(feeMode = FeeMode.PROPORTIONAL) } }, enabled = !vm.busy, label = { Text("By item amount") })
        }
        if (draft.participants.isNotEmpty()) TextButton(onClick = { feeOptions = !feeOptions }, enabled = !vm.busy) { Text("Choose who pays fees") }
        if (feeOptions) draft.participants.forEach { participant ->
            AssignmentPersonRow("${names[participant.id]} pays fees", participant.paysFees, !vm.busy) {
                vm.change { it.copy(participants = it.participants.map { person -> if (person.id == participant.id) person.copy(paysFees = !person.paysFees) else person }) }
            }
        }
    }
    result.exceptionOrNull()?.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
