package com.receiptparser.tab

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.receiptparser.tab.data.*
import com.receiptparser.tab.domain.*
import com.receiptparser.tab.ml.ReceiptCapture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CaptureAndShareTest {
    @Test fun bundledOcrReadsSyntheticImageAndStoresPrivateJpeg(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(1080, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 52f; typeface = Typeface.MONOSPACE }
        listOf("TAB TEST CAFE", "Chicken       100.00", "Rice           50.00", "TOTAL         150.00").forEachIndexed { i, text -> canvas.drawText(text, 60f, 100f + i * 130, paint) }
        val file = File(context.cacheDir, "synthetic-ocr-test.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        bitmap.recycle()
        try {
            val receipt = ReceiptCapture(context).read(Uri.fromFile(file))
            assertEquals(15000L, receipt.total)
            assertEquals(2, receipt.items.size)
            assertTrue(receipt.reconciled)
            assertTrue(receipt.rawText.contains("Chicken"))
            assertTrue(receipt.ocrLines.isNotEmpty())
            val saved = File(receipt.imagePath!!)
            assertTrue(saved.path.startsWith(context.filesDir.path))
            assertTrue(saved.length() > 0)
            saved.delete()
        } finally { file.delete() }
    }
    @Test fun shareCardAndNudgeReflectSavedAllocation(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, TabDatabase::class.java).build()
        try {
            val repo = TabRepository(db)
            val id = repo.addFriend("Carlo")
            val draft = ReceiptDraft(venue = "Test lunch", items = listOf(ReceiptItem(name = "Chicken", lineTotal = 30000)), total = 33000, serviceCharge = 3000, participants = listOf(Participant(id)))
            repo.save(draft)
            val snapshot = repo.snapshot()
            val message = ShareService.nudge(snapshot, "09123456789", id)
            assertTrue(message.contains("Carlo ₱165.00"))
            assertTrue(message.contains("GCash 09123456789"))
            val image = ShareService.breakdown(context, snapshot, draft.id, false)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(image.path, bounds)
            assertEquals(1080, bounds.outWidth)
            assertTrue(bounds.outHeight > 420)
            image.delete()
        } finally { db.close() }
    }
}
