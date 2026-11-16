package uz.kassiron.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.Attendance
import uz.kassiron.app.domain.RefundService
import uz.kassiron.app.domain.ReportService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.switchTab
import uz.kassiron.app.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------- dashboard

private data class DashData(
    val today: ReportService.DayTotals?,
    val debt: Double?,
    val products: Long?,
    val lowStock: List<ReportService.LowStock>?,
    val openShift: Attendance.Shift?,
)

private data class QuickAction(val title: Int, val hint: Int, val icon: ImageVector, val tone: Color, val go: () -> Unit)

@Composable
fun DashboardScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val u = user ?: return
    val sync by App.sync.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val data by dbState<DashData?>(null, u.uuid) {
        val db = App.db
        DashData(
            today = if (u.can("sales")) ReportService.today(db) else null,
            debt = if (u.can("customers")) ReportService.totalDebt(db) else null,
            products = if (u.can("products")) ReportService.activeProducts(db) else null,
            lowStock = if (u.can("products")) ReportService.lowStock(db) else null,
            openShift = Attendance.openShift(db, u.uuid),
        )
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = stringResource(if (hour < 11) R.string.dash_greet_morning else if (hour < 18) R.string.dash_greet_day else R.string.dash_greet_evening)
    val firstName = u.fullName.trim().split(" ").firstOrNull().orEmpty()
    val shopName = App.kv.shop().optString("name").ifBlank { stringResource(R.string.app_name) }

    val actions = buildList {
        if (u.can("sales")) add(QuickAction(R.string.dash_qa_till, R.string.dash_qa_till_hint, Icons.Filled.PointOfSale, Brand.LimeInk) { nav.switchTab(Routes.TILL) })
        if (u.can("products")) add(QuickAction(R.string.dash_qa_product, R.string.dash_qa_product_hint, Icons.Filled.AddBox, Tones.Blue) { nav.navigate(Routes.productEdit("new")) })
        if (u.can("expenses")) add(QuickAction(R.string.dash_qa_expense, R.string.dash_qa_expense_hint, Icons.Filled.Wallet, Tones.Amber) { nav.navigate(Routes.expenseEdit("new")) })
        if (u.can("reports")) add(QuickAction(R.string.dash_qa_reports, R.string.dash_qa_reports_hint, Icons.Filled.BarChart, Tones.Violet) { nav.navigate(Routes.REPORTS) })
        if (u.can("customers")) add(QuickAction(R.string.dash_qa_debts, R.string.dash_qa_debts_hint, Icons.Filled.EditNote, Brand.Danger) { nav.navigate(Routes.QUICK_DEBTS) })
        if (u.can("expenses")) add(QuickAction(R.string.dash_qa_expenses, R.string.dash_qa_expenses_hint, Icons.Filled.ReceiptLong, Tones.Teal) { nav.navigate(Routes.EXPENSES) })
        add(QuickAction(R.string.dash_qa_attendance, R.string.dash_qa_attendance_hint, Icons.Filled.AccessTime, Tones.Teal) { nav.navigate(Routes.ATTENDANCE) })
    }

    BoScaffold(title = shopName, snackbar = snackbar) { pad ->
        val d = data
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("hello") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("$greeting, $firstName!", style = MaterialTheme.typography.headlineMedium, maxLines = 2)
                        Text(Time.showDate(Time.today()), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (d != null) {
                        val on = d.openShift != null
                        Button(
                            onClick = {
                                scope.launch {
                                    val ok = io { if (on) Attendance.clockOut(u.uuid) else Attendance.clockIn(u.uuid) }
                                    if (ok) App.sync.requestSync()
                                    snackbar.showSnackbar(context.getString(
                                        if (!ok) R.string.dash_attendance_failed else if (on) R.string.dash_clock_out_done else R.string.dash_clock_in_done,
                                    ))
                                }
                            },
                            colors = if (on) ButtonDefaults.buttonColors(containerColor = Tones.Teal, contentColor = Color.White) else ButtonDefaults.buttonColors(),
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.AccessTime, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (on) R.string.dash_clock_out else R.string.dash_clock_in))
                        }
                    }
                }
            }
            item("sync") { SyncLine(sync) { App.sync.requestSync() } }
            if (d != null) {
                item("kpi") {
                    val cards = buildList<@Composable (Modifier) -> Unit> {
                        d.today?.let { t ->
                            add { m -> StatCard(stringResource(R.string.dash_today_revenue), Money.format(t.revenue), Icons.Filled.Paid, Brand.LimeInk, m) { nav.switchTab(Routes.SALES) } }
                            add { m -> StatCard(stringResource(R.string.dash_today_sales), t.count.toString(), Icons.Filled.ReceiptLong, Tones.Blue, m) { nav.switchTab(Routes.SALES) } }
                        }
                        d.debt?.let { debt -> add { m -> StatCard(stringResource(R.string.dash_total_debt), Money.format(debt), Icons.Filled.People, Brand.Danger, m) { nav.switchTab(Routes.CUSTOMERS) } } }
                        d.products?.let { n -> add { m -> StatCard(stringResource(R.string.dash_products), n.toString(), Icons.Filled.Inventory2, Tones.Violet, m) { nav.switchTab(Routes.PRODUCTS) } } }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cards.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { it(Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
                val low = d.lowStock
                if (!low.isNullOrEmpty()) {
                    item("low") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Brand.Warning.copy(alpha = 0.12f)),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nav.switchTab(Routes.PRODUCTS) }) {
                                    Icon(Icons.Filled.Warning, null, tint = Brand.Warning)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(stringResource(R.string.dash_low_stock_title), style = MaterialTheme.typography.titleMedium)
                                        val out = low.count { it.stock <= 0 }
                                        Text(
                                            stringResource(R.string.dash_low_stock_count, low.size) + if (out > 0) " · " + stringResource(R.string.dash_out_of_stock_count, out) else "",
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                                }
                                low.take(5).forEach { p ->
                                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                    Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.productEdit(p.uuid)) }.padding(vertical = 4.dp)) {
                                        Text(p.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${Money.qty(p.stock)} ${p.unit}", fontWeight = FontWeight.Bold, color = if (p.stock <= 0) Brand.Danger else Brand.Warning)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (actions.isNotEmpty()) {
                item("qa-title") { SectionTitle(stringResource(R.string.dash_quick_actions)) }
                item("qa") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        actions.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { a -> QuickActionCard(a, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(a: QuickAction, modifier: Modifier) {
    Card(
        modifier = modifier.heightIn(min = 96.dp).clickable(onClick = a.go),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(40.dp).background(a.tone.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(a.icon, null, tint = a.tone)
            }
            Text(stringResource(a.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(a.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun SyncLine(state: uz.kassiron.app.sync.SyncState, onClick: () -> Unit) {
    val (icon, tint) = when {
        state.running -> Icons.Filled.CloudSync to Tones.Blue
        state.error != null -> Icons.Filled.CloudOff to Brand.Warning
        else -> Icons.Filled.CloudDone to Brand.Success
    }
    val parts = mutableListOf<String>()
    parts += when {
        state.running -> stringResource(R.string.dash_sync_running)
        state.lastSyncAt <= 0 -> stringResource(R.string.dash_sync_never)
        else -> stringResource(R.string.dash_sync_last, SimpleDateFormat("dd.MM HH:mm", Locale.US).format(Date(state.lastSyncAt)))
    }
    if (!state.running && state.error != null) parts += stringResource(R.string.dash_sync_offline)
    if (state.pendingSales > 0) parts += stringResource(R.string.dash_sync_pending, state.pendingSales.toInt())
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- reports

private data class ReportData(
    val summary: ReportService.Summary,
    val payments: ReportService.Payments,
    val top: List<ReportService.TopProduct>,
    val cashiers: List<ReportService.CashierRow>,
)

@Composable
fun ReportsScreen(nav: NavHostController) {
    if (!App.session.can("reports")) { NoAccess(); return }
    var preset by rememberSaveable { mutableStateOf(ReportService.Preset.TODAY) }
    var customFrom by rememberSaveable { mutableStateOf(Time.today()) }
    var customTo by rememberSaveable { mutableStateOf(Time.today()) }
    var pick by remember { mutableStateOf(false) }
    val (from, to) = if (preset == ReportService.Preset.CUSTOM) customFrom to customTo else ReportService.period(preset)

    val data by dbState<ReportData?>(null, from, to) {
        val db = App.db
        ReportData(
            ReportService.summary(db, from, to),
            ReportService.paymentBreakdown(db, from, to),
            ReportService.topProducts(db, from, to),
            ReportService.cashierLeaderboard(db, from, to),
        )
    }

    BoScaffold(title = stringResource(R.string.reports_title), onBack = { nav.popBackStack() }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("presets") {
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            ReportService.Preset.TODAY to R.string.reports_today,
                            ReportService.Preset.YESTERDAY to R.string.reports_yesterday,
                            ReportService.Preset.WEEK to R.string.reports_week,
                            ReportService.Preset.MONTH to R.string.reports_month,
                            ReportService.Preset.CUSTOM to R.string.reports_custom,
                        ).forEach { (p, label) ->
                            FilterChip(
                                selected = preset == p,
                                onClick = { if (p == ReportService.Preset.CUSTOM) pick = true else preset = p },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                    Text(rangeLabel(from, to), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                }
            }
            val d = data ?: return@LazyColumn
            val s = d.summary
            item("kpi") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(stringResource(R.string.reports_revenue), Money.format(s.revenue), Icons.Filled.Paid, Brand.LimeInk, Modifier.weight(1f),
                            sub = stringResource(R.string.reports_sales_count, s.salesCount.toInt()))
                        StatCard(stringResource(R.string.reports_net_profit), Money.format(s.netProfit), Icons.Filled.BarChart,
                            if (s.netProfit < 0) Brand.Danger else Brand.Success, Modifier.weight(1f))
                    }
                }
            }
            item("summary") {
                BoCard {
                    AmountLine(stringResource(R.string.reports_gross_revenue), Money.format(s.grossRevenue))
                    if (s.discounts > 0) AmountLine(stringResource(R.string.reports_discounts), Money.format(s.discounts))
                    AmountLine(stringResource(R.string.reports_refunds), if (s.refunds > 0) "−" + Money.format(s.refunds) else Money.format(0.0), color = if (s.refunds > 0) Brand.Warning else Color.Unspecified)
                    AmountLine(stringResource(R.string.reports_revenue), Money.format(s.revenue), bold = true)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AmountLine(stringResource(R.string.reports_cogs), "−" + Money.format(s.cogs))
                    AmountLine(stringResource(R.string.reports_gross_profit), Money.format(s.grossProfit))
                    AmountLine(stringResource(R.string.reports_expenses), "−" + Money.format(s.expenses))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AmountLine(stringResource(R.string.reports_net_profit), Money.format(s.netProfit), bold = true, color = if (s.netProfit < 0) Brand.Danger else Brand.Success)
                }
            }
            item("pay-title") { SectionTitle(stringResource(R.string.reports_payments)) }
            item("pay") {
                BoCard {
                    RefundService.PAYMENT_TYPES.forEach { t ->
                        val net = d.payments.net.getValue(t)
                        val refunded = d.payments.refunded.getValue(t)
                        if (t != "qr" || net != 0.0 || refunded != 0.0) {
                            AmountLine(paymentLabel(t), Money.format(net), color = paymentColor(t))
                            if (refunded > 0) Text(
                                stringResource(R.string.reports_payment_refunded, Money.format(d.payments.sold.getValue(t)), Money.format(refunded)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item("top-title") { SectionTitle(stringResource(R.string.reports_top_products)) }
            if (d.top.isEmpty()) item("top-empty") { Text(stringResource(R.string.reports_no_data), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else item("top") {
                BoCard {
                    d.top.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}.", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.reports_qty_sold, Money.qty(p.qty)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.format(p.revenue), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item("cash-title") { SectionTitle(stringResource(R.string.reports_cashiers)) }
            items(d.cashiers, key = { "c" + it.userUuid }) { c ->
                BoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.reports_sales_count, c.salesCount.toInt()) + " · " + stringResource(R.string.reports_contribution, Money.format(c.netContribution)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (c.commission != null) Text(
                                stringResource(R.string.reports_commission, Money.qty(c.commissionRate ?: 0.0), Money.format(c.commission)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        Text(Money.format(c.revenue), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    if (pick) DateRangeDialog(customFrom, customTo, onDismiss = { pick = false }) { f, t ->
        customFrom = f; customTo = t; preset = ReportService.Preset.CUSTOM; pick = false
    }
}

// ---------------------------------------------------------------- attendance

private data class AttendanceData(
    val open: Attendance.Shift?,
    val mine: List<Attendance.Shift>,
    val today: List<Attendance.Shift>?,
    val openCount: Long,
)

private fun duration(context: android.content.Context, minutes: Long): String =
    context.getString(R.string.dash_duration, (minutes / 60).toInt(), (minutes % 60).toInt())

@Composable
fun AttendanceScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val u = user ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val data by dbState<AttendanceData?>(null, u.uuid) {
        val db = App.db
        val today = Time.today()
        AttendanceData(
            open = Attendance.openShift(db, u.uuid),
            mine = Attendance.history(db, Time.addDays(today, -6), today, u.uuid),
            today = if (u.isOwner) Attendance.history(db, today, today) else null,
            openCount = Attendance.openCount(db),
        )
    }

    BoScaffold(title = stringResource(R.string.dash_attendance_title), onBack = { nav.popBackStack() }, snackbar = snackbar) { pad ->
        val d = data ?: return@BoScaffold
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("me") {
                BoCard {
                    val on = d.open != null
                    Text(u.fullName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (on) stringResource(R.string.dash_working_since, Time.local(d.open!!.clockIn, "HH:mm"), duration(context, Attendance.minutes(d.open.clockIn, null)))
                        else stringResource(R.string.dash_not_working),
                        color = if (on) Tones.Teal else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val ok = io { if (on) Attendance.clockOut(u.uuid) else Attendance.clockIn(u.uuid) }
                                if (ok) App.sync.requestSync()
                                snackbar.showSnackbar(context.getString(
                                    if (!ok) R.string.dash_attendance_failed else if (on) R.string.dash_clock_out_done else R.string.dash_clock_in_done,
                                ))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = if (on) ButtonDefaults.buttonColors(containerColor = Tones.Teal, contentColor = Color.White) else ButtonDefaults.buttonColors(),
                    ) {
                        Icon(Icons.Filled.AccessTime, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (on) R.string.dash_clock_out else R.string.dash_clock_in), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (d.today != null) {
                item("today-title") {
                    SectionTitle(stringResource(R.string.dash_attendance_today)) {
                        Text(stringResource(R.string.dash_working_now, d.openCount.toInt()), color = Tones.Teal, style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (d.today.isEmpty()) item("today-empty") { Text(stringResource(R.string.dash_attendance_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(d.today, key = { "t" + it.uuid }) { s -> ShiftRow(s, showName = true) }
            }
            item("mine-title") { SectionTitle(stringResource(R.string.dash_attendance_mine)) }
            if (d.mine.isEmpty()) item("mine-empty") { Text(stringResource(R.string.dash_attendance_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(d.mine, key = { "m" + it.uuid }) { s -> ShiftRow(s, showName = false) }
        }
    }
}

@Composable
private fun ShiftRow(s: Attendance.Shift, showName: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (showName) s.userName.ifBlank { "—" } else Time.local(s.clockIn, "dd.MM.yyyy"), fontWeight = FontWeight.SemiBold)
                Text(
                    Time.local(s.clockIn, "HH:mm") + " – " + (s.clockOut?.let { Time.local(it, "HH:mm") } ?: stringResource(R.string.dash_ongoing)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(duration(context, Attendance.minutes(s.clockIn, s.clockOut)), fontWeight = FontWeight.Bold, color = if (s.clockOut == null) Tones.Teal else Color.Unspecified)
        }
    }
}
