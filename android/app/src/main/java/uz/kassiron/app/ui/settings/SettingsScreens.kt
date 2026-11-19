package uz.kassiron.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.BuildConfig
import uz.kassiron.app.R
import uz.kassiron.app.core.Keys
import uz.kassiron.app.print.BluetoothPrinter
import uz.kassiron.app.print.PrinterSettings
import uz.kassiron.app.sync.SyncState
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.auth.findActivity
import uz.kassiron.app.ui.auth.syncErrorText
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KDivider
import uz.kassiron.app.ui.kit.KGroup
import uz.kassiron.app.ui.kit.KGroupRow
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KOption
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSegmented
import uz.kassiron.app.ui.kit.KTile
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.resetTo
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.ThemeMode
import uz.kassiron.app.ui.theme.Tone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ======================================================================
// "Ko‘proq" (mockup 14): everything that isn't in the bottom bar.
// ======================================================================

private data class MenuItem(val route: String, val label: Int, val icon: ImageVector, val tone: Tone)

@Composable
fun MoreScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val shopName by dbState("") { App.kv.shop().optString("name") }
    val u = user ?: return
    val c = K.c

    // Same rules as the website's menu and routes (routes.php: purchases,
    // suppliers, categories and quick entry need 'products'; old debts
    // 'customers'; attendance is for everyone).
    val items = listOfNotNull(
        MenuItem(Routes.EXPENSES, R.string.settings_menu_expenses, Icons.Outlined.AccountBalanceWallet, c.redT).takeIf { u.can("expenses") },
        MenuItem(Routes.REPORTS, R.string.settings_menu_reports, Icons.Outlined.BarChart, c.blueT).takeIf { u.can("reports") },
        MenuItem(Routes.PURCHASES, R.string.settings_menu_purchases, Icons.Outlined.MoveToInbox, c.lime).takeIf { u.can("products") },
        MenuItem(Routes.SUPPLIERS, R.string.settings_menu_suppliers, Icons.Outlined.LocalShipping, c.amber).takeIf { u.can("products") },
        MenuItem(Routes.CATEGORIES, R.string.settings_menu_categories, Icons.Outlined.GridView, c.violet).takeIf { u.can("products") },
        MenuItem(Routes.QUICK_PRODUCTS, R.string.settings_menu_quick_products, Icons.AutoMirrored.Outlined.PlaylistAdd, c.teal).takeIf { u.can("products") },
        MenuItem(Routes.QUICK_DEBTS, R.string.settings_menu_quick_debts, Icons.AutoMirrored.Outlined.MenuBook, c.redT).takeIf { u.can("customers") },
        MenuItem(Routes.ATTENDANCE, R.string.settings_menu_attendance, Icons.Outlined.Schedule, c.lime),
        MenuItem(Routes.PRINTER, R.string.settings_menu_printer, Icons.Outlined.Print, c.blueT).takeIf { u.can("sales") },
        MenuItem(Routes.SETTINGS, R.string.settings_menu_settings, Icons.Outlined.Tune, c.violet),
    )

    KScreen(topBar = { KTopBar(stringResource(R.string.settings_more_title), onBack = { nav.popBackStack() }) }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            KCard(Modifier.padding(horizontal = Edge), onClick = { nav.navigate(Routes.SETTINGS) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KAvatar(u.fullName, 46.dp)
                    Column(Modifier.weight(1f)) {
                        Text(u.fullName, style = K.t.title, color = K.c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                stringResource(if (u.isOwner) R.string.auth_role_owner else R.string.auth_role_employee),
                                shopName.takeIf { it.isNotBlank() },
                            ).joinToString(" · "),
                            style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
                }
            }
            Column(Modifier.padding(start = Edge, end = Edge, top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            KTile(stringResource(item.label), item.icon, item.tone, { nav.navigate(item.route) }, Modifier.weight(1f).fillMaxHeight())
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ======================================================================
// Settings (mockup 29): sync, language, theme, printer, user, shop, disconnect.
// ======================================================================

private data class DeviceInfo(val shopName: String, val deviceCode: String, val lang: String, val theme: String)

@Composable
fun SettingsScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val sync by App.sync.state.collectAsState()
    val user by App.session.user.collectAsState()
    val info by dbState<DeviceInfo?>(null) {
        DeviceInfo(
            App.kv.shop().optString("name"),
            App.kv.get(Keys.DEVICE_CODE).orEmpty(),
            App.kv.get(Keys.LANG, "uz"),
            App.kv.get(THEME_KEY, "system"),
        )
    }
    var printerName by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val doneText = stringResource(R.string.settings_sync_done)
    val canPrint = user?.can("sales") == true

    LaunchedEffect(canPrint) {
        if (!canPrint) return@LaunchedEffect
        printerName = io {
            val address = PrinterSettings.address ?: return@io null
            runCatching { BluetoothPrinter.paired(context).firstOrNull { it.address == address }?.name }.getOrNull().orEmpty()
        }
    }

    KScreen(
        topBar = { KTopBar(stringResource(R.string.settings_title), onBack = { nav.popBackStack() }) },
        snackbar = snackbar,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SyncCard(sync, busy = syncing) {
                syncing = true
                scope.launch {
                    val ok = App.sync.syncNow()
                    syncing = false
                    if (ok) snackbar.showSnackbar(doneText)
                }
            }

            Spacer(Modifier.height(10.dp))
            KGroup {
                val i = info
                KGroupRow(stringResource(R.string.settings_language), icon = Icons.Outlined.Language, first = true, trailing = {
                    KSegmented(
                        listOf(KOption(stringResource(R.string.settings_lang_uz)), KOption(stringResource(R.string.settings_lang_ru))),
                        selected = if (i?.lang == "ru") 1 else 0,
                        onSelect = { n ->
                            val code = if (n == 1) "ru" else "uz"
                            if (i != null && i.lang != code) {
                                scope.launch {
                                    io { App.kv.set(Keys.LANG, code) }
                                    context.findActivity()?.recreate()
                                }
                            }
                        },
                        modifier = Modifier.width(120.dp),
                        height = 30.dp,
                    )
                })
                ThemeRow(i?.theme ?: "system") { mode ->
                    ThemeMode.set(mode)
                    scope.launch { io { App.kv.set(THEME_KEY, mode) } }
                }
                if (canPrint) {
                    KGroupRow(
                        stringResource(R.string.settings_printer), icon = Icons.Outlined.Print,
                        // "": chosen, but its name can't be read (no Bluetooth permission).
                        value = when (val p = printerName) {
                            null -> stringResource(R.string.settings_printer_none)
                            "" -> stringResource(R.string.settings_printer_chosen)
                            else -> p
                        },
                        onClick = { nav.navigate(Routes.PRINTER) },
                    )
                }
                KGroupRow(stringResource(R.string.settings_switch_user), icon = Icons.Outlined.PersonOutline, onClick = {
                    scope.launch {
                        io { App.session.signOut() }
                        nav.resetTo(Routes.LOGIN)
                    }
                })
            }

            Spacer(Modifier.height(10.dp))
            KGroup {
                KGroupRow(stringResource(R.string.settings_shop), icon = Icons.Outlined.Storefront, value = info?.shopName.orEmpty(), first = true)
                KGroupRow(stringResource(R.string.settings_device), icon = Icons.Outlined.PhoneAndroid, value = info?.deviceCode.orEmpty())
                KGroupRow(stringResource(R.string.settings_version), icon = Icons.Outlined.Info, value = BuildConfig.VERSION_NAME)
            }

            // Disconnect: the owner's decision (it wipes the shop off this phone).
            if (user?.isOwner == true) {
                KButton(
                    stringResource(R.string.settings_disconnect), { confirmDisconnect = true },
                    Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 14.dp),
                    kind = KButtonKind.Danger, height = 48.dp, fontSize = 14.5.sp,
                )
            }
        }
    }

    if (confirmDisconnect) {
        KConfirm(
            title = stringResource(R.string.settings_disconnect_confirm_title),
            text = when {
                sync.pendingSales > 0 -> stringResource(R.string.settings_disconnect_pending, sync.pendingSales.toInt())
                sync.pendingTotal > 0 -> stringResource(R.string.settings_disconnect_pending_other)
                else -> stringResource(R.string.settings_disconnect_confirm_text)
            },
            confirm = stringResource(R.string.settings_disconnect_yes),
            dismiss = stringResource(R.string.common_cancel),
            danger = true,
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

/** Kv key of the theme choice: system | light | dark (read at startup by KassirApp). */
private const val THEME_KEY = "theme"
private val THEMES = listOf("system", "light", "dark")

/** "Mavzu": title row, then the three choices across the full width (they don't fit beside the title). */
@Composable
private fun ThemeRow(mode: String, onSelect: (String) -> Unit) {
    KDivider()
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Contrast, null, tint = K.c.mu, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.settings_theme), style = K.t.title.copy(fontSize = 14.5.sp), color = K.c.tx)
        }
        Spacer(Modifier.height(10.dp))
        KSegmented(
            listOf(
                KOption(stringResource(R.string.settings_theme_system)),
                KOption(stringResource(R.string.settings_theme_light)),
                KOption(stringResource(R.string.settings_theme_dark)),
            ),
            selected = THEMES.indexOf(mode).coerceAtLeast(0),
            onSelect = { onSelect(THEMES[it]) },
            modifier = Modifier.fillMaxWidth(),
            height = 34.dp,
        )
    }
}

/** The sync card: tint (all sent) or amber (waiting / problem), icon tile, status, last time, lime refresh. */
@Composable
private fun SyncCard(sync: SyncState, busy: Boolean, onSync: () -> Unit) {
    val running = busy || sync.running
    // Refused changes stay amber even while a sync runs: they won't go away by themselves.
    val problem = (!running && (sync.pendingSales > 0 || sync.error != null)) || sync.rejected > 0
    KCard(Modifier.padding(horizontal = Edge), tone = if (problem) KCardTone.Amber else KCardTone.Tint) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(if (problem) K.c.ambBg else K.c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (problem) Icons.Outlined.CloudOff else Icons.Outlined.Cloud, null,
                    tint = if (problem) K.c.amb else K.c.onPrimary, modifier = Modifier.size(22.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        running -> stringResource(R.string.settings_sync_running)
                        sync.pendingSales > 0 -> stringResource(R.string.settings_sync_pending, sync.pendingSales.toInt())
                        sync.rejected > 0 -> stringResource(R.string.sync_rejected_title)
                        sync.lastSyncAt > 0 -> stringResource(R.string.settings_sync_ok)
                        else -> stringResource(R.string.settings_sync_never)
                    },
                    style = K.t.title.copy(fontSize = 14.5.sp), color = K.c.tx,
                )
                val detail = when {
                    !running && sync.error != null -> syncErrorText(sync.error)
                    sync.lastSyncAt > 0 -> stringResource(R.string.settings_sync_last, formatMillis(sync.lastSyncAt))
                    else -> null
                }
                if (detail != null) Text(detail, style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.mu)
                if (sync.rejected > 0) {
                    Text(stringResource(R.string.sync_rejected, sync.rejected.toInt()), style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.red)
                }
            }
            KIconButton(
                Icons.Outlined.Refresh, { if (!running) onSync() }, primary = !running, size = 38.dp,
                contentDescription = stringResource(R.string.settings_sync_now),
            )
        }
    }
}

private fun formatMillis(millis: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).format(Date(millis))
