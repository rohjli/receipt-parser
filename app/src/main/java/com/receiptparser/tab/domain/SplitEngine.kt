package com.receiptparser.tab.domain

object SplitEngine {
    fun split(receipt: ReceiptDraft): SplitResult {
        require(receipt.items.isNotEmpty()) { "Add at least one item." }
        require(receipt.items.size <= 500) { "A receipt can have up to 500 items." }
        require(receipt.items.map { it.id }.distinct().size == receipt.items.size) { "Duplicate item IDs." }
        require(receipt.participants.size <= 50) { "A group can have up to 50 friends." }
        val ids = listOf(SELF) + receipt.participants.map { it.id }
        require(ids.distinct().size == ids.size && ids.none { it.isBlank() }) { "Duplicate or invalid participant." }
        require(listOf(receipt.total, receipt.serviceCharge, receipt.vat, receipt.discount).all { it in 0..Money.MAX }) { "Amounts must be from zero to ₱10,000,000." }
        receipt.items.forEach {
            require(it.name.isNotBlank() && it.name.length <= 200) { "Give every item a name of 1–200 characters." }
            require(it.qty in 1..999 && it.lineTotal in 0..Money.MAX) { "Check the item quantities and amounts." }
            require(it.owners.all { owner -> owner in ids }) { "An item is assigned to someone outside this group." }
        }
        require(receipt.subtotal <= Money.MAX) { "Item subtotal is too large." }
        require(receipt.discount <= receipt.subtotal) { "Discount cannot exceed the item subtotal." }
        val itemShares = receipt.items.flatMap { item ->
            val owners = item.owners.ifEmpty { ids.toSet() }
            val each = item.lineTotal / owners.size
            val remainder = item.lineTotal % owners.size
            val values = ids.associateWith { if (it in owners) each else 0L }.toMutableMap()
            values[SELF] = values.getValue(SELF) + remainder
            values.filter { it.key in owners || it.value != 0L }.map { ItemShare(item.id, it.key, it.value) }
        }
        val sums = ids.associateWith { id -> itemShares.filter { it.personId == id }.sumOf { it.amount } }
        val eligible = ids.filter { id -> id == SELF || receipt.participants.first { it.id == id }.paysFees }
        val fees = receipt.serviceCharge + if (receipt.vatMode == VatMode.ADDITIVE) receipt.vat else 0
        val weights = eligible.associateWith { if (receipt.feeMode == FeeMode.EQUAL) 1L else sums.getValue(it) }
        val feeShares = allocate(fees, weights)
        val discounts = allocate(receipt.discount, sums)
        val initial = ids.map { id ->
            val base = sums.getValue(id) + (feeShares[id] ?: 0) - (discounts[id] ?: 0)
            val rounded = ((base + 50) / 100) * 100
            PersonShare(id, sums.getValue(id), feeShares[id] ?: 0, discounts[id] ?: 0, if (id == SELF) 0 else rounded - base)
        }
        val residual = receipt.total - initial.sumOf { it.total }
        return SplitResult(itemShares, initial.map { if (it.personId == SELF) it.copy(rounding = residual) else it })
    }
    private fun allocate(amount: Long, weights: Map<String, Long>): Map<String, Long> {
        if (weights.isEmpty()) return mapOf(SELF to amount)
        val effective = if (weights.values.sum() == 0L) weights.mapValues { 1L } else weights
        val total = effective.values.sum()
        val shares = effective.mapValues { (_, weight) -> amount * weight / total }.toMutableMap()
        shares[SELF] = (shares[SELF] ?: 0) + amount - shares.values.sum()
        return shares
    }
}
