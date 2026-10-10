package com.receiptparser.tab.ui

import androidx.compose.foundation.background
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

@Composable fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun SectionHeading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
@Composable fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun AmountRow(label: String, amount: Long, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(Money.format(amount), fontFamily = FontFamily.Monospace, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
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
    if (message != null) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}
@Composable fun EmptyState(title: String, body: String) = Panel { Text(title, style = MaterialTheme.typography.titleLarge); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun Avatar(name: String, modifier: Modifier = Modifier) {
    Box(modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.trim().split(" ").filter { it.isNotBlank() }.take(2).map { it.first().uppercase() }.joinToString(""), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
    }
}
@Composable fun ConfirmDialog(title: String, body: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(body) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(action) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Keep it") } })
fun dateText(millis: Long): String = DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
