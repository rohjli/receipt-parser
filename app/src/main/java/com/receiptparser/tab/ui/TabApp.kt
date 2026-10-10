package com.receiptparser.tab.ui

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.receiptparser.tab.data.ShareService
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object CaptureRoute
@Serializable data object HomeRoute
@Serializable data object FriendsRoute
@Serializable data object SettingsRoute
@Serializable data object FixRoute
@Serializable data object AssignRoute
@Serializable data class FriendRoute(val id: String)
private data class TabDestination(val title: String, val route: Any, val icon: ImageVector)

@Composable fun TabApp(incoming: Uri?, consumeIncoming: () -> Unit, openHome: Boolean, consumeHome: () -> Unit) {
    val nav = rememberNavController()
    val receipt: ReceiptViewModel = hiltViewModel()
    val ledger: LedgerViewModel = hiltViewModel()
    val capture: CaptureViewModel = hiltViewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val drafting = destination?.hasRoute<FixRoute>() == true || destination?.hasRoute<AssignRoute>() == true
    var discard by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var nudgeText by remember { mutableStateOf<String?>(null) }
    var needGcash by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun goHome() { nav.navigate(HomeRoute) { popUpTo<CaptureRoute> { inclusive = false }; launchSingleTop = true } }
    fun payment(id: String, amount: Long, date: Long, note: String, success: () -> Unit = {}) {
        ledger.action {
            val paymentId = ledger.repository.recordPayment(id, amount, date, note)
            success()
            scope.launch { if (snackbar.showSnackbar("Payment recorded", "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) ledger.action { ledger.repository.undoPayment(paymentId) } }
        }
    }
    fun nudge(id: String? = null) {
        if (ledger.settings.gcash.isBlank()) needGcash = true
        else try { nudgeText = ShareService.nudge(ledger.snapshot, ledger.settings.gcash, id) } catch (e: Exception) { ledger.error = e.message }
    }
    fun edit(id: String) { receipt.edit(id) { nav.navigate(FixRoute) } }
    LaunchedEffect(openHome) { if (openHome) { goHome(); consumeHome() } }
    LaunchedEffect(incoming) { if (incoming != null) nav.navigate(CaptureRoute) { launchSingleTop = true } }
    LaunchedEffect(destination) {
        if (destination?.hasRoute<HomeRoute>() == true && Build.VERSION.SDK_INT >= 33) {
            val preferences = context.getSharedPreferences("notification_permission", Context.MODE_PRIVATE)
            if (!preferences.getBoolean("asked", false)) { preferences.edit().putBoolean("asked", true).apply(); permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }
    BackHandler(enabled = drafting && !receipt.busy) { discard = true }
    Scaffold(
        bottomBar = { Column {
            SnackbarHost(snackbar)
            if (!drafting) {
                val destinations = listOf(
                    TabDestination("Ledger", HomeRoute, Icons.Outlined.Home),
                    TabDestination("Capture", CaptureRoute, Icons.Outlined.DocumentScanner),
                    TabDestination("Friends", FriendsRoute, Icons.Outlined.PeopleOutline),
                    TabDestination("Settings", SettingsRoute, Icons.Outlined.Tune),
                )
                val largeText = LocalDensity.current.fontScale > 1.3f
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        destinations.chunked(if (largeText) 2 else 4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                row.forEach { tab ->
                                    val selected = destination?.hasRoute(tab.route::class) == true || (tab.route == HomeRoute && destination?.hasRoute<FriendRoute>() == true)
                                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium)
                                        .selectable(selected, role = Role.Tab, onClick = {
                                            nav.navigate(tab.route) { popUpTo<CaptureRoute> { inclusive = false }; launchSingleTop = true }
                                        }).padding(vertical = 4.dp).heightIn(min = 56.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (tab.route == CaptureRoute) {
                                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shadowElevation = 4.dp) {
                                                Icon(tab.icon, null, Modifier.padding(horizontal = 18.dp, vertical = 12.dp).size(24.dp))
                                            }
                                        } else {
                                            Icon(tab.icon, null, Modifier.padding(top = 8.dp, bottom = 4.dp).size(24.dp),
                                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(tab.title, style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize()) {
            if (receipt.error != null && !drafting && destination?.hasRoute<CaptureRoute>() != true) ErrorNotice(receipt.error) { receipt.error = null }
            NavHost(navController = nav, startDestination = CaptureRoute, modifier = Modifier.weight(1f)) {
                composable<CaptureRoute> { CaptureScreen(receipt, capture, incoming, consumeIncoming) { reconciled -> nav.navigate(if (reconciled) AssignRoute else FixRoute) } }
                composable<FixRoute> { FixScreen(receipt, next = { nav.navigate(AssignRoute) { popUpTo<FixRoute> { inclusive = true } } }, back = { discard = true }, onDelete = { delete = true }) }
                composable<AssignRoute> { AssignScreen(receipt, ledger, back = { discard = true }, editItems = { nav.navigate(FixRoute) { popUpTo<AssignRoute> { inclusive = true } } }, done = { goHome(); scope.launch { snackbar.showSnackbar("Split saved") } }) }
                composable<HomeRoute> { HomeScreen(ledger, snackbar, onFriend = { nav.navigate(FriendRoute(it)) }, onEdit = ::edit, onShare = { id -> ledger.action { val file = ShareService.breakdown(context, ledger.snapshot, id, ledger.settings.showOwnShare); ShareService.shareImage(context, file) } }, nudge = { nudge() }, onPayment = ::payment, onCapture = { nav.navigate(CaptureRoute) { launchSingleTop = true } }) }
                composable<FriendsRoute> { FriendsScreen(ledger) { nav.navigate(FriendRoute(it)) } }
                composable<FriendRoute> { backStack -> val id = backStack.toRoute<FriendRoute>().id; FriendScreen(id, ledger, back = { nav.popBackStack() }, nudge = { nudge(id) }, onPayment = ::payment, onEdit = ::edit) }
                composable<SettingsRoute> { SettingsScreen(ledger) { scope.launch { snackbar.showSnackbar("Settings saved") } } }
            }
        }
    }
    if (discard) ConfirmDialog("Discard this draft?", "Unsaved changes will be removed. Your saved receipt and payments stay as they were.", "Discard draft", { discard = false }) { receipt.discard { discard = false; goHome() } }
    if (delete) ConfirmDialog("Delete ${receipt.draft?.venue?.ifBlank { "this receipt" }}?", "This removes its item shares from everyone’s balance. Recorded payments stay in the ledger.", "Delete receipt", { delete = false }) {
        val id = receipt.draft?.id
        if (id != null) ledger.action { ledger.repository.deleteReceipt(id); receipt.discard { delete = false; goHome() } }
    }
    if (needGcash) AlertDialog(onDismissRequest = { needGcash = false }, title = { Text("Where should they send it?") }, text = { Text("Add your GCash number in Settings, then share your nudge.") }, confirmButton = { TextButton(onClick = { needGcash = false; nav.navigate(SettingsRoute) }) { Text("Open settings") } }, dismissButton = { TextButton(onClick = { needGcash = false }) { Text("Cancel") } })
    nudgeText?.let { text -> AlertDialog(onDismissRequest = { nudgeText = null }, title = { Text("Your nudge") }, text = { Text(text, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { try { ShareService.shareText(context, text); nudgeText = null } catch (e: Exception) { ledger.error = "No sharing app available. Use Copy instead." } }) { Text("Share") } }, dismissButton = { TextButton(onClick = { ShareService.copy(context, text); nudgeText = null; scope.launch { snackbar.showSnackbar("Nudge copied") } }) { Text("Copy") } }) }
}
