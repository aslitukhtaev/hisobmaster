package uz.kassiron.app.ui.reports

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.Attendance
import uz.kassiron.app.domain.ReportService
import uz.kassiron.app.sync.SyncState
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAlert
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KHero
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KKpi
import uz.kassiron.app.ui.kit.KList
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.switchTab
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Onest
import java.util.Calendar

private data class RecentSale(val uuid: String, val receipt: String, val createdAt: String, val total: Double, val items: Long)

private data class DashData(
    val today: ReportService.DayTotals?,
    val debt: Double?,
    val products: Long?,
    val lowStock: List<ReportService.LowStock>?,
    val recent: List<RecentSale>,
    val openShift: Attendance.Shift?,
)

/** "Bosh": the day at a glance (mockup 13). */
@Composable
fun DashboardScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val u = user ?: return
    val sync by App.sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var askClockOut by remember { mutableStateOf(false) }

    val canSell = u.can("sales")
    val data by dbState<DashData?>(null, u.uuid) {
        val db = App.db
        DashData(
            today = if (canSell) ReportService.today(db) else null,
            debt = if (u.can("customers")) ReportService.totalDebt(db) else null,
            products = if (u.can("products")) ReportService.activeProducts(db) else null,
            lowStock = if (u.can("products")) ReportService.lowStock(db) else null,
            recent = if (!canSell) emptyList() else db.query(
                """SELECT s.uuid, s.receipt_no, s.created_at, s.total,
                          (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.uuid) AS item_count
                   FROM sales s ORDER BY s.created_at DESC LIMIT 3""",
            ).map { RecentSale(it.str("uuid"), receiptLabel(it.strOrNull("receipt_no"), it.str("uuid")), it.str("created_at"), it.dbl("total"), it.long("item_count")) },
            openShift = Attendance.openShift(db, u.uuid),
        )
    }
    val shopName by dbState("") { App.kv.shop().optString("name") }

    fun clock(out: Boolean) {
        scope.launch {
            val ok = io { if (out) Attendance.clockOut(u.uuid) else Attendance.clockIn(u.uuid) }
            if (ok) App.sync.requestSync()
            snackbar.showSnackbar(context.getString(if (!ok) R.string.att_failed else if (out) R.string.att_out_done else R.string.att_in_done))
        }
    }

    KScreen(snackbar = snackbar) {
        val d = data
        androidx.compose.foundation.layout.Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            // Header: logo · shop · menu
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Edge, end = Edge, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    Image(
                        painterResource(if (K.c.dark) R.drawable.logo_light else R.drawable.logo_dark),
                        contentDescription = "KassirON",
                        modifier = Modifier.height(22.dp).aspectRatio(640f / 148f),
                    )
                }
                if (shopName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = 150.dp)) {
                        Box(Modifier.size(8.dp).shadow(4.dp, CircleShape, ambientColor = K.c.glow, spotColor = K.c.primary).clip(CircleShape).background(K.c.primary))
                        Spacer(Modifier.size(6.dp))
                        Text(shopName, style = K.t.label, color = K.c.mu, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                KIconButton(Icons.Outlined.GridView, { nav.navigate(Routes.MORE) }, contentDescription = stringResource(R.string.dash_more))
            }

            SyncAlert(sync) { App.sync.requestSync() }

            // Hero
            KHero(Modifier.padding(top = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Time.showDate(Time.today()),
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp),
                        color = K.c.primary, modifier = Modifier.weight(1f),
                    )
                    if (d != null) ShiftPill(d.openShift) { if (d.openShift != null) askClockOut = true else clock(false) }
                }
                Text(
                    greeting(u.fullName),
                    style = TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.4).sp),
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                val t = d?.today
                if (t != null) {
                    Text(
                        if (t.count > 0) stringResource(R.string.dash_hero_today, t.count.toInt(), Money.format(t.revenue)) else stringResource(R.string.dash_hero_none),
                        style = K.t.body.copy(fontSize = 13.sp), color = Color.White.copy(alpha = 0.7f),
                    )
                }
                if (canSell) {
                    Spacer(Modifier.height(12.dp))
                    HeroButton(stringResource(R.string.dash_open_till)) { nav.switchTab(Routes.TILL) }
                }
            }

            if (d != null) {
                // KPI 2×2, filtered by permission
                val kpis = buildList<@Composable (Modifier) -> Unit> {
                    d.today?.let { t ->
                        add { m -> KKpi(Icons.Outlined.AccountBalanceWallet, stringResource(R.string.dash_kpi_revenue), K.c.lime, m, { nav.switchTab(Routes.SALES) }) { KMoney(t.revenue, size = 15.sp) } }
                        add { m -> KKpi(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.dash_kpi_sales), K.c.blueT, m, { nav.switchTab(Routes.SALES) }) { KpiCount(t.count) } }
                    }
                    d.debt?.let { debt -> add { m -> KKpi(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.dash_kpi_debt), K.c.redT, m, { nav.switchTab(Routes.CUSTOMERS) }) { KMoney(debt, size = 15.sp) } } }
                    d.products?.let { n -> add { m -> KKpi(Icons.Outlined.Inventory2, stringResource(R.string.dash_kpi_products), K.c.violet, m, { nav.switchTab(Routes.PRODUCTS) }) { KpiCount(n) } } }
                }
                if (kpis.isNotEmpty()) {
                    androidx.compose.foundation.layout.Column(
                        Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        kpis.chunked(2).forEach { pair ->
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { it(Modifier.weight(1f).fillMaxHeight()) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                val low = d.lowStock
                if (!low.isNullOrEmpty()) {
                    val first = low.first()
                    val text = when {
                        low.size > 1 -> stringResource(R.string.dash_low_many, first.name, low.size - 1)
                        first.stock <= 0 -> stringResource(R.string.dash_low_out, first.name)
                        else -> stringResource(R.string.dash_low_one, first.name, Money.qty(first.stock) + " " + first.unit)
                    }
                    KAlert(text, Icons.Outlined.WarningAmber, Modifier.padding(top = 10.dp), onClick = { nav.switchTab(Routes.PRODUCTS) })
                }

                if (d.recent.isNotEmpty()) {
                    KSection(stringResource(R.string.dash_recent), stringResource(R.string.dash_all), { nav.switchTab(Routes.SALES) })
                    KList {
                        d.recent.forEach { s ->
                            KRow(
                                stringResource(R.string.bo_receipt_no, s.receipt),
                                subtitle = { KSub(Time.local(s.createdAt, "HH:mm") + " · " + stringResource(R.string.bo_items, s.items.toInt())) },
                                trailing = { KMoney(s.total) },
                                onClick = { nav.navigate(Routes.sale(s.uuid)) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (askClockOut) KConfirm(
        title = stringResource(R.string.att_out_confirm),
        text = null,
        confirm = stringResource(R.string.att_out),
        dismiss = stringResource(R.string.bo_cancel),
        onConfirm = { askClockOut = false; clock(true) },
        onDismiss = { askClockOut = false },
    )
}

@Composable
private fun greeting(fullName: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val first = fullName.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
    val res = when {
        hour < 5 -> R.string.dash_greet_evening
        hour < 11 -> R.string.dash_greet_morning
        hour < 18 -> R.string.dash_greet_day
        else -> R.string.dash_greet_evening
    }
    return stringResource(res, first)
}

@Composable
private fun KpiCount(n: Long) {
    Text(stringResource(R.string.bo_count, n.toInt()), style = K.t.amount.copy(fontSize = 15.sp), color = K.c.tx, maxLines = 1)
}

/** The hero's lime button: icon + text on the left, arrow on the right. */
@Composable
private fun HeroButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(15.dp)
    Row(
        Modifier.fillMaxWidth().height(48.dp)
            .shadow(14.dp, shape, ambientColor = K.c.glow, spotColor = K.c.primary)
            .clip(shape).background(K.c.primary).clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.PointOfSale, null, tint = K.c.onPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(text, style = K.t.button.copy(fontSize = 15.sp), color = K.c.onPrimary, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = K.c.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Clock in / out as a small pill in the hero: "Ishni boshlash" or "Ishda · 09:12". */
@Composable
private fun ShiftPill(open: Attendance.Shift?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.height(30.dp).clip(shape).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape).clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (open != null) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(K.c.primary))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.att_pill_on, Time.local(open.clockIn, "HH:mm")), style = K.t.chip, color = Color.White, maxLines = 1)
        } else {
            Icon(Icons.Outlined.AccessTime, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(stringResource(R.string.att_in), style = K.t.chip, color = Color.White, maxLines = 1)
        }
    }
}

/** Shown only when something is wrong: no internet / sync failed, with unsent sales. */
@Composable
private fun SyncAlert(state: SyncState, onClick: () -> Unit) {
    if (state.running || state.error == null) return
    val offline = state.error == "network"
    val n = state.pendingSales.toInt()
    val text = when {
        offline && n > 0 -> stringResource(R.string.dash_sync_offline_pending, n)
        offline -> stringResource(R.string.dash_sync_offline)
        n > 0 -> stringResource(R.string.dash_sync_failed_pending, n)
        else -> stringResource(R.string.dash_sync_failed)
    }
    KAlert(text, Icons.Outlined.CloudOff, Modifier.padding(bottom = 10.dp), tone = KCardTone.Amber, onClick = onClick)
}
