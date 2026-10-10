package com.receiptparser.tab.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.*
import com.receiptparser.tab.domain.ReceiptDraft
import com.receiptparser.tab.domain.ReceiptParser
import com.receiptparser.tab.ml.ReceiptCapture
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel class CaptureViewModel @Inject constructor(private val capture: ReceiptCapture) : ViewModel() {
    var busy by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    fun read(uri: Uri, complete: (ReceiptDraft) -> Unit) {
        if (busy) return
        busy = true; error = null
        viewModelScope.launch { try { complete(capture.read(uri)) } catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Couldn’t read this receipt. Try another image." } finally { busy = false } }
    }
}

@Composable fun CaptureScreen(receipt: ReceiptViewModel, capture: CaptureViewModel, incoming: Uri?, consumeIncoming: () -> Unit, openDraft: (Boolean) -> Unit) {
    val context = LocalContext.current
    var launching by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val scanResult = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        launching = false
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
            if (uri != null) capture.read(uri) { receipt.start(it, openDraft) }
            else capture.error = "The scan did not return a page. Try again or choose a photo."
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { capture.read(it) { result -> receipt.start(result, openDraft) } } }
    LaunchedEffect(incoming, receipt.ready) {
        if (incoming != null && receipt.ready) {
            if (receipt.draft != null) capture.error = "A receipt is already in progress. Finish or discard it, then share the photo again."
            else capture.read(incoming) { receipt.start(it, openDraft) }
            consumeIncoming()
        }
    }
    val busy = capture.busy || receipt.busy || launching || !receipt.ready
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("tab.", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            SuggestionChip(onClick = {}, label = { Text("ON YOUR DEVICE") }, icon = { Icon(Icons.Outlined.Lock, contentDescription = null, Modifier.size(14.dp)) })
        }
        Heading("Good food.\nEasy splits.", "Snap the bill. Pick your people. All settled.")
        ErrorNotice(capture.error) { capture.error = null }
        ErrorNotice(receipt.error) { receipt.error = null }
        if (receipt.draft != null) Panel {
            SectionHeading("Your receipt is waiting")
            Text(receipt.draft?.venue?.ifBlank { "Untitled receipt" } ?: "Receipt")
            Button(onClick = { openDraft(receipt.draft?.reconciled == true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Resume receipt") }
            TextButton(onClick = { discard = true }, enabled = !busy) { Text("Discard draft") }
        } else {
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("One receipt. Everyone’s share.", style = MaterialTheme.typography.titleMedium)
                    Text("Keep the bill flat and well lit.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        launching = true; capture.error = null
                        val options = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(1).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
                        GmsDocumentScanning.getClient(options).getStartScanIntent(context as Activity)
                            .addOnSuccessListener { scanResult.launch(IntentSenderRequest.Builder(it).build()) }
                            .addOnFailureListener { launching = false; capture.error = "Scanner unavailable. Google Play services may need its first download. Choose a photo or enter items manually." }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Outlined.DocumentScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan receipt") }
                }
            }
            OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Choose a photo") }
            TextButton(onClick = { receipt.start(then = openDraft) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Enter manually") }
        }
        if (busy) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(24.dp)); Text(if (capture.busy) "Reading your receipt…" else "Getting ready…") }
        HorizontalDivider()
        Text("Your receipts stay on this device. No account needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (receipt.draft == null) TextButton(enabled = !busy, onClick = { receipt.start(ReceiptParser.parseText("Sunday lunch\nChicken inasal 285.00\nGarlic rice 65.00\nIced tea 80.00\nService charge 43.00\nTOTAL 473.00"), openDraft) }) { Text("Try a sample receipt") }
    }
    if (discard) ConfirmDialog("Discard this draft?", "Your unsaved items and assignments will be removed. Saved receipts stay in your ledger.", "Discard draft", { discard = false }) { receipt.discard { discard = false } }
}
