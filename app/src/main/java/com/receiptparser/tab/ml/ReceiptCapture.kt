package com.receiptparser.tab.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.receiptparser.tab.domain.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class ReceiptCapture @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun read(uri: Uri): ReceiptDraft = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "This image can’t be read. Choose a JPEG or PNG receipt." }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2600) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("This image couldn’t be opened. Try another photo.")
        val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: 1
        val transform = Matrix().apply { when (orientation) {
            2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
            5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
            7 -> { setRotate(270f); postScale(-1f, 1f) }; 8 -> setRotate(270f)
        } }
        val upright = if (transform.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
        // Small shared images often have single-digit text heights. Enlarge only the OCR
        // input; retain the source image and map recognition boxes back to its coordinates.
        val ocrScale = if (maxOf(upright.width, upright.height) < 1000) 2 else 1
        val ocrBitmap = if (ocrScale == 1) upright else Bitmap.createScaledBitmap(
            upright, upright.width * ocrScale, upright.height * ocrScale, true,
        )
        val directory = File(context.filesDir, "receipts").apply { mkdirs() }
        val image = File(directory, "${UUID.randomUUID()}.jpg")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(ocrBitmap, 0)).await()
            require(text.text.isNotBlank()) { "No text found. Retake the photo in better light, or enter items manually." }
            image.outputStream().use { check(upright.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line -> line.boundingBox?.let { b -> OcrLine(line.text, Box(b.left / ocrScale, b.top / ocrScale, b.right / ocrScale, b.bottom / ocrScale)) } }
            ReceiptParser.parse(lines).copy(imagePath = image.absolutePath, rawText = text.text)
        } catch (e: Exception) { image.delete(); throw e }
        finally { recognizer.close(); if (ocrBitmap !== upright) ocrBitmap.recycle(); if (upright !== bitmap) upright.recycle(); bitmap.recycle() }
    }
}
