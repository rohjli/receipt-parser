package com.receiptparser.tab.domain

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ReceiptCorpusTest(private val name: String) {
    @Test fun matchesExpectedReceipt() {
        val text = javaClass.getResource("/receipts/$name.json")!!.readText()
        val fixture = Json.parseToJsonElement(text).jsonObject
        val lines = Json.decodeFromJsonElement<List<OcrLine>>(fixture.getValue("lines"))
        val e = fixture.getValue("expected").jsonObject
        val r = ReceiptParser.parse(lines)
        assertEquals(e.getValue("itemCount").jsonPrimitive.int, r.items.size)
        e.getValue("items").jsonArray.forEachIndexed { index, expected ->
            val item = expected.jsonObject
            assertEquals(item.getValue("name").jsonPrimitive.content, r.items[index].name)
            assertEquals(item.getValue("qty").jsonPrimitive.int, r.items[index].qty)
            assertEquals(item.getValue("lineTotal").jsonPrimitive.long, r.items[index].lineTotal)
        }
        assertEquals(e.getValue("subtotal").jsonPrimitive.long, r.subtotal)
        assertEquals(e.getValue("serviceCharge").jsonPrimitive.long, r.serviceCharge)
        assertEquals(e.getValue("vat").jsonPrimitive.long, r.vat)
        assertEquals(e.getValue("discount").jsonPrimitive.long, r.discount)
        assertEquals(e.getValue("vatMode").jsonPrimitive.content, r.vatMode.name)
        assertEquals(e.getValue("total").jsonPrimitive.long, r.total)
        assertEquals(e.getValue("reconciled").jsonPrimitive.boolean, r.reconciled)
    }
    companion object { @JvmStatic @Parameterized.Parameters(name = "{0}") fun fixtures(): List<String> = ReceiptCorpusTest::class.java.getResource("/receipts/index.txt")!!.readText().lines().filter { it.isNotBlank() } }
}
