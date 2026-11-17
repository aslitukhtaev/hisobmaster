package uz.kassiron.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.PhonelinkErase
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.BuildConfig
import uz.kassiron.app.R
import uz.kassiron.app.core.Keys
import uz.kassiron.app.sync.SyncState
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.auth.InitialsAvatar
import uz.kassiron.app.ui.auth.findActivity
import uz.kassiron.app.ui.auth.syncErrorText
import uz.kassiron.app.ui.common.AppTopBar
import uz.kassiron.app.ui.common.ConfirmDialog
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.resetTo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================
// "Ko'proq": everything that isn't in the bottom bar.
// ======================================================================

private data class MenuItem(val route: String, val label: Int, val icon: ImageVector, val tint: Color, val allowed: Boolean)

@Composable
fun MoreScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val sync by App.sync.state.collectAsState()
    val shopName by dbState("") { App.kv.shop().optString("name") }
    val u = user ?: return

    // Same rules as the website's menu and routes (routes.php: purchases,
    // suppliers, categories and quick entry need 'products'; old debts
    // 'customers'; attendance is for everyone).
    val items = listOf(
        MenuItem(Routes.HOME, R.string.settings_menu_home, Icons.Filled.Home, Color(0xFF3A7300), true),
        MenuItem(Routes.REPORTS, R.string.settings_menu_reports, Icons.Filled.Assessment, Color(0xFF1D4ED8), u.can("reports")),
        MenuItem(Routes.EXPENSES, R.string.settings_menu_expenses, Icons.Filled.AccountBalanceWallet, Color(0xFFBE123C), u.can("expenses")),
        MenuItem(Routes.PURCHASES, R.string.settings_menu_purchases, Icons.Filled.MoveToInbox, Color(0xFF0F766E), u.can("products")),
        MenuItem(Routes.SUPPLIERS, R.string.settings_menu_suppliers, Icons.Filled.LocalShipping, Color(0xFFB45309), u.can("products")),
        MenuItem(Routes.CATEGORIES, R.string.settings_menu_categories, Icons.Filled.Category, Color(0xFF7C3AED), u.can("products")),
        MenuItem(Routes.QUICK_PRODUCTS, R.string.settings_menu_quick_products, Icons.Filled.AddBox, Color(0xFF0369A1), u.can("products")),
        MenuItem(Routes.QUICK_DEBTS, R.string.settings_menu_quick_debts, Icons.Filled.EditNote, Color(0xFFDC2626), u.can("customers")),
        MenuItem(Routes.ATTENDANCE, R.string.settings_menu_attendance, Icons.Filled.AccessTime, Color(0xFF4D7C0F), true),
        MenuItem(Routes.PRINTER, R.string.settings_menu_printer, Icons.Filled.Print, Color(0xFF475569), u.can("sales")),
        MenuItem(Routes.SETTINGS, R.string.settings_menu_settings, Icons.Filled.Settings, Color(0xFF475569), true),
    ).filter { it.allowed }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.settings_more_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(
                onClick = { nav.navigate(Routes.SETTINGS) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialsAvatar(u.fullName, 52.dp)
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(
                                    stringResource(if (u.isOwner) R.string.auth_role_owner else R.string.auth_role_employee),
                                    shopName.takeIf { it.isNotBlank() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SyncStatusLine(sync)
                }
            }
        }
        items(items, key = { it.route }) { item ->
            MenuTile(item) { nav.navigate(item.route) }
        }
    }
}

@Composable
private fun MenuTile(item: MenuItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().height(112.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(48.dp).background(item.tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.icon, null, tint = item.tint, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(item.label),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One line: syncing / all sent / N sales waiting (+ the reason). */
@Composable
private fun SyncStatusLine(sync: SyncState) {
    val (icon, color) = when {
        sync.running -> Icons.Filled.Sync to MaterialTheme.colorScheme.secondary
        sync.error != null && sync.error != "network" -> Icons.Filled.SyncProblem to MaterialTheme.colorScheme.error
        sync.pendingSales > 0 || sync.error == "network" -> Icons.Filled.CloudOff to Color(0xFFB45309)
        else -> Icons.Filled.CloudDone to MaterialTheme.colorScheme.secondary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (sync.running) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(
            when {
                sync.running -> stringResource(R.string.settings_sync_running)
                sync.pendingSales > 0 -> stringResource(R.string.settings_sync_pending, sync.pendingSales)
                sync.lastSyncAt > 0 -> stringResource(R.string.settings_sync_last, formatMillis(sync.lastSyncAt))
                else -> stringResource(R.string.settings_sync_never)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatMillis(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(millis))

// ======================================================================
// Settings: sync, shop, language, user, disconnect.
// ======================================================================

private data class ShopInfo(val shop: JSONObject, val deviceCode: String, val lang: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val sync by App.sync.state.collectAsState()
    val user by App.session.user.collectAsState()
    val info by dbState<ShopInfo?>(null) {
        ShopInfo(App.kv.shop(), App.kv.get(Keys.DEVICE_CODE).orEmpty(), App.kv.get(Keys.LANG, "uz"))
    }
    var syncing by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val doneText = stringResource(R.string.settings_sync_done)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            AppTopBar(stringResource(R.string.settings_title), onBack = { nav.popBackStack() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Sync
                Section(stringResource(R.string.settings_sync_title), Icons.Filled.Sync) {
                    SyncStatusLine(sync)
                    if (sync.lastSyncAt > 0 && sync.pendingSales > 0) {
                        Text(
                            stringResource(R.string.settings_sync_last, formatMillis(sync.lastSyncAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!sync.running && sync.pendingSales == 0L && sync.error == null && sync.lastSyncAt > 0) {
                        Text(stringResource(R.string.settings_sync_ok), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    sync.error?.let {
                        Text(
                            syncErrorText(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (it == "network") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        onClick = {
                            syncing = true
                            scope.launch {
                                val ok = App.sync.syncNow()
                                syncing = false
                                if (ok) snackbar.showSnackbar(doneText)
                            }
                        },
                        enabled = !syncing && !sync.running,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (syncing || sync.running) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Icon(Icons.Filled.Sync, null, Modifier.size(20.dp))
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.settings_sync_now))
                    }
                }

                // Shop
                info?.let { i ->
                    Section(stringResource(R.string.settings_shop_title), Icons.Filled.Store) {
                        val s = i.shop
                        s.optString("name").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        listOf("owner_full_name", "phone", "address").forEach { key ->
                            if (!s.isNull(key)) {
                                s.optString(key).takeIf { it.isNotBlank() }?.let { InfoText(it) }
                            }
                        }
                        if (i.deviceCode.isNotBlank()) InfoText(stringResource(R.string.settings_device_code, i.deviceCode))
                        InfoText(stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME))
                    }
                }

                // Language
                info?.let { i ->
                    Section(stringResource(R.string.settings_language), Icons.Filled.Language) {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            listOf("uz" to R.string.settings_lang_uz, "ru" to R.string.settings_lang_ru).forEachIndexed { n, (code, label) ->
                                SegmentedButton(
                                    selected = i.lang == code,
                                    onClick = {
                                        if (i.lang != code) {
                                            scope.launch {
                                                io { App.kv.set(Keys.LANG, code) }
                                                context.findActivity()?.recreate()
                                            }
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(n, 2),
                                    modifier = Modifier.height(48.dp),
                                ) { Text(stringResource(label)) }
                            }
                        }
                    }
                }

                // User
                user?.let { u ->
                    Section(stringResource(R.string.settings_user_title), null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(u.fullName, 44.dp)
                            Spacer(Modifier.size(12.dp))
                            Column {
                                Text(u.fullName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(if (u.isOwner) R.string.auth_role_owner else R.string.auth_role_employee),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    io { App.session.signOut() }
                                    nav.resetTo(Routes.LOGIN)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.settings_switch_user))
                        }
                    }
                }

                // Disconnect (the owner's decision: it wipes the shop off this phone)
                if (user?.isOwner == true) {
                    Section(stringResource(R.string.settings_disconnect), Icons.Filled.PhonelinkErase) {
                        InfoText(stringResource(R.string.settings_disconnect_hint))
                        Button(
                            onClick = { confirmDisconnect = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(stringResource(R.string.settings_disconnect)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.settings_disconnect_confirm_title),
            text = if (sync.pendingSales > 0) stringResource(R.string.settings_disconnect_pending, sync.pendingSales)
            else stringResource(R.string.settings_disconnect_confirm_text),
            confirm = stringResource(R.string.settings_disconnect_yes),
            dismiss = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmDisconnect = false
                scope.launch {
                    App.sync.deactivate()
                    nav.resetTo(Routes.ACTIVATION)
                }
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

@Composable
private fun Section(title: String, icon: ImageVector?, content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(32.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.size(10.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun InfoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
