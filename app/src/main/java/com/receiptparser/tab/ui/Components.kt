package com.receiptparser.tab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.receiptparser.tab.R
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.receiptparser.tab.domain.Money
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun Page(
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
        )
        bottomBar?.let { footer ->
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) { footer() }
                }
            }
        }
    }
}

@Composable fun PrimaryAction(label: String, onClick: () -> Unit, enabled: Boolean = true, busy: Boolean = false) {
    Button(onClick = onClick, enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(label)
    }
}

@Composable fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun SectionHeading(text: String) = Text(
    text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
    modifier = Modifier.semantics { heading() },
)

@Composable fun SectionLabel(icon: ImageVector, title: String, subtitle: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(icon, null, Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionHeading(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun ReceiptDivider() {
    val color = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(color, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
    }
}

@Composable fun AmountRow(label: String, amount: Long, strong: Boolean = false) {
    val weight = if (strong) FontWeight.SemiBold else FontWeight.Normal
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontWeight = weight)
            Text(Money.format(amount), fontFamily = FontFamily.Monospace, fontWeight = weight)
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text(label, Modifier.weight(1f), fontWeight = weight)
            Text(Money.format(amount), fontFamily = FontFamily.Monospace, fontWeight = weight)
        }
    }
}

@Composable fun BrandHeader(trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Image(painterResource(R.drawable.ic_tungaon), null, Modifier.padding(10.dp).size(26.dp))
        }
        Text("Tungaon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (LocalDensity.current.fontScale <= 1.3f) trailing?.invoke()
    }
}

@Composable fun BalanceCard(label: String, amount: Long, note: String? = null, content: @Composable ColumnScope.() -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primary,
        contentColor = colors.onPrimary, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.background(Brush.linearGradient(listOf(colors.primary, colors.onPrimaryContainer)))) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = 0.045f), radius = size.width * 0.55f, center = Offset(size.width, 0f))
                drawCircle(Color.White.copy(alpha = 0.04f), radius = size.width * 0.45f, center = Offset(0f, size.height))
            }
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                Text(Money.format(amount), style = MaterialTheme.typography.displaySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                content()
            }
        }
    }
}

/** Decorative guide; the real camera and cropping controls are provided by ML Kit. */
@Composable fun ReceiptViewfinder() {
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(224.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val left = w * 0.25f
            val right = w * 0.75f
            val top = h * 0.12f
            val bottom = h * 0.88f
            drawCircle(Brush.radialGradient(listOf(colors.secondaryContainer.copy(alpha = 0.22f), Color.Transparent), center = center, radius = h * 0.55f), h * 0.55f)
            val paper = Path().apply {
                moveTo(left, top); lineTo(right, top); lineTo(right, bottom)
                for (i in 0..12) lineTo(right - (right - left) * i / 12f, bottom - if (i % 2 == 0) 0f else 6.dp.toPx())
                close()
            }
            drawPath(paper, colors.surface)
            drawPath(paper, colors.outlineVariant, style = Stroke(1.dp.toPx()))
            for (i in 0..3) {
                val y = top + 68.dp.toPx() + i * 14.dp.toPx()
                drawLine(colors.outlineVariant, Offset(left + 18.dp.toPx(), y), Offset(right - (if (i % 2 == 0) 18 else 38).dp.toPx(), y), 3.dp.toPx(), StrokeCap.Round)
            }
            val x1 = left - 22.dp.toPx(); val x2 = right + 22.dp.toPx()
            val y1 = top - 8.dp.toPx(); val y2 = bottom + 8.dp.toPx()
            val corner = 14.dp.toPx()
            for ((x, signX) in listOf(x1 to 1, x2 to -1)) for ((y, signY) in listOf(y1 to 1, y2 to -1)) {
                val bracket = Path().apply { moveTo(x, y + corner * signY); lineTo(x, y); lineTo(x + corner * signX, y) }
                drawPath(bracket, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            drawLine(colors.secondaryContainer.copy(alpha = 0.15f), Offset(x1, h * 0.68f), Offset(x2, h * 0.68f), 16.dp.toPx())
            drawLine(colors.secondary, Offset(x1, h * 0.68f), Offset(x2, h * 0.68f), 2.dp.toPx(), StrokeCap.Round)
        }
        Image(painterResource(R.drawable.ic_tungaon), null, Modifier.align(Alignment.TopCenter).padding(top = 42.dp).size(34.dp))
    }
}

@Composable fun ReceiptStep(step: Int, back: () -> Unit, backEnabled: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = back, enabled = backEnabled, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Back")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Check receipt", "Split bill").forEachIndexed { index, label ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(thickness = 3.dp, color = if (index + 1 <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    Text("${index + 1}. $label", style = MaterialTheme.typography.labelMedium,
                        color = if (index + 1 == step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { if (index + 1 == step) stateDescription = "Current step" })
                }
            }
        }
    }
}

@Composable fun MoneyField(label: String, amount: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier, onValidity: (Boolean) -> Unit = {}) {
    var text by rememberSaveable(label) { mutableStateOf(Money.input(amount)) }
    val parsed = Money.parse(text)
    LaunchedEffect(parsed) { onValidity(parsed != null) }
    OutlinedTextField(value = text, onValueChange = { value ->
        text = value
        val money = Money.parse(value)
        onValidity(money != null)
        money?.let(onChange)
    }, label = { Text(label) }, modifier = modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = parsed == null,
        supportingText = if (parsed == null) ({ Text("Enter 0–10,000,000 with at most 2 decimals.") }) else null)
}
@Composable fun ErrorNotice(message: String?, onDismiss: () -> Unit = {}) {
    if (message != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
@Composable fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Outlined.ReceiptLong) = Panel {
    Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}
@Composable fun Avatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(if (name.hashCode() and 1 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(name.trim().split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercase() }.joinToString(""), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}
@Composable fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } })
fun dateText(millis: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
