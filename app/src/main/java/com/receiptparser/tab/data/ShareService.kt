package com.receiptparser.tab.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import com.receiptparser.tab.domain.*
import com.receiptparser.tab.R
import com.receiptparser.tab.ui.dateText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareService {
    fun nudge(snapshot: LedgerSnapshot, gcash: String, friendId: String? = null): String {
        require(gcash.isNotBlank()) { "Add your GCash number in Settings first." }
        val balances = snapshot.friends.filter { (friendId == null || it.id == friendId) && snapshot.balance(it.id) > 0 }
        require(balances.isNotEmpty()) { "There’s nothing outstanding to nudge for." }
        return "Hey! Here’s our open tab:\n" + balances.joinToString(" · ") { "${it.name} ${Money.format(snapshot.balance(it.id))}" } + "\nGCash $gcash 🙏"
    }
    fun shareText(context: Context, text: String) {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share a nudge"))
    }
    fun copy(context: Context, text: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Tungaon nudge", text)) }
    suspend fun breakdown(context: Context, snapshot: LedgerSnapshot, receiptId: String, showOwn: Boolean): File = withContext(Dispatchers.Default) {
        val receipt = snapshot.receipts.first { it.id == receiptId }
        val names = snapshot.friends.associate { it.id to it.name } + (SELF to "You")
        val participants = snapshot.participants.filter { it.receiptId == receiptId && (showOwn || it.friendId != SELF) }
        val rows = mutableListOf<Pair<String, String>>()
        participants.forEach { p ->
            rows += "#${names[p.friendId] ?: "Friend"}" to Money.format(snapshot.receiptAmount(receiptId, p.friendId))
            snapshot.items.filter { it.receiptId == receiptId }.forEach { item ->
                snapshot.shares.find { it.lineItemId == item.id && it.friendId == p.friendId }?.let { share -> rows += item.name to Money.format(share.amount) }
            }
            rows += "Fees" to Money.format(p.feeShare)
            if (p.discountShare != 0L) rows += "Discount" to Money.format(-p.discountShare)
            if (p.rounding != 0L) rows += "Rounding / adjustment" to Money.format(p.rounding)
            rows += "" to ""
        }
        if (participants.isEmpty()) rows += "No friend shares on this receipt" to ""
        // Bound bitmap memory for unusually large receipts. Text sharing remains available separately.
        require(rows.size <= 250) { "This breakdown is too long for one image. Share each friend’s amount instead." }
        val height = 420 + rows.size * 54
        val bitmap = Bitmap.createBitmap(1080, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(context.getColor(R.color.brand_cream))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.brand_ink); typeface = Typeface.create("sans-serif", Typeface.NORMAL) }
        fun text(value: String, x: Float, y: Float, size: Float, bold: Boolean = false) {
            paint.textSize = size; paint.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(value, x, y, paint)
        }
        paint.color = context.getColor(R.color.brand_emerald)
        text(context.getString(R.string.app_name), 60f, 72f, 36f, true)
        paint.color = context.getColor(R.color.brand_ink)
        text(receipt.venue.take(35), 60f, 122f, 40f, true)
        text(dateText(receipt.takenAt), 60f, 165f, 28f)
        var y = 235f
        rows.forEach { (label, amount) ->
            val heading = label.startsWith('#')
            val clean = label.removePrefix("#")
            paint.textSize = if (heading) 34f else 28f
            val available = 1080f - 120f - paint.measureText(amount) - 30f
            val length = paint.breakText(clean, true, available, null)
            val clipped = if (length < clean.length) clean.take((length - 1).coerceAtLeast(0)) + "…" else clean
            text(clipped, 60f, y, if (heading) 34f else 28f, heading)
            paint.textSize = if (heading) 34f else 28f
            val x = 1020f - paint.measureText(amount)
            text(amount, x, y, if (heading) 34f else 28f, heading)
            y += 54
        }
        text("Receipt total: ${Money.format(receipt.total)}", 60f, y + 40, 34f, true)
        text(if (receipt.reconciled) "Rounded to the peso. The payer covers the difference." else "Saved with an adjustment to the payer’s share.", 60f, y + 92, 24f)
        val directory = File(context.cacheDir, "shares").apply { mkdirs() }
        val file = File(directory, "tungaon-$receiptId.png")
        try { withContext(Dispatchers.IO) { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }; file } finally { bitmap.recycle() }
    }
    fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newUri(context.contentResolver, "Tungaon breakdown", uri) }
        context.startActivity(Intent.createChooser(intent, "Share the breakdown"))
    }
}
