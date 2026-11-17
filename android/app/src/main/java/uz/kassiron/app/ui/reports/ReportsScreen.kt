package uz.kassiron.app.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.domain.ReportService
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KBar
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KChips
import uz.kassiron.app.ui.kit.KKpi
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.theme.K

private data class TopRow(val p: ReportService.TopProduct, val unit: String)

private data class ReportData(
    val summary: ReportService.Summary,
    val payments: ReportService.Payments,
    val top: List<TopRow>,
    val cashiers: List<ReportService.CashierRow>,
)

private val PRESETS = listOf(
    ReportService.Preset.TODAY, ReportService.Preset.YESTERDAY, ReportService.Preset.WEEK,
    ReportService.Preset.MONTH, ReportService.Preset.CUSTOM,
)

/** "Hisobotlar" (mockup 28): period, KPIs, payment types, best sellers. */
@Composable
fun ReportsScreen(nav: NavHostController) {
    if (!App.session.can("reports")) { NoAccess(); return }
    var preset by rememberSaveable { mutableStateOf(ReportService.Preset.MONTH) }
    var customFrom by rememberSaveable { mutableStateOf(ReportService.period(ReportService.Preset.TODAY).first) }
    var customTo by rememberSaveable { mutableStateOf(customFrom) }
    var pick by remember { mutableStateOf(false) }
    val (from, to) = if (preset == ReportService.Preset.CUSTOM) customFrom to customTo else ReportService.period(preset)

    val data by dbState<ReportData?>(null, from, to) {
        val db = App.db
        ReportData(
            ReportService.summary(db, from, to),
            ReportService.paymentBreakdown(db, from, to),
            ReportService.topProducts(db, from, to).map { p ->
                TopRow(p, db.queryOne("SELECT unit FROM products WHERE uuid = ?", p.productUuid)?.str("unit")?.ifBlank { null } ?: "dona")
            },
            ReportService.cashierLeaderboard(db, from, to),
        )
    }

    KScreen(topBar = { KTopBar(stringResource(R.string.rep_title), onBack = { nav.popBackStack() }) }) {
        val labels = listOf(
            stringResource(R.string.rep_today), stringResource(R.string.rep_yesterday), stringResource(R.string.rep_week),
            stringResource(R.string.rep_month),
            if (preset == ReportService.Preset.CUSTOM) rangeLabel(from, to) else stringResource(R.string.rep_custom),
        )
        KChips(labels, PRESETS.indexOf(preset), { i -> if (PRESETS[i] == ReportService.Preset.CUSTOM) pick = true else preset = PRESETS[i] })
        val d = data ?: return@KScreen
        val s = d.summary
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)) {
            item("kpi") {
                androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KKpi(Icons.Outlined.AccountBalanceWallet, stringResource(R.string.rep_revenue), K.c.lime, Modifier.weight(1f).fillMaxHeight()) { KMoney(s.revenue, size = 15.sp, currency = false) }
                        KKpi(Icons.Outlined.BarChart, stringResource(R.string.rep_profit), K.c.teal, Modifier.weight(1f).fillMaxHeight()) {
                            KMoney(s.netProfit, size = 15.sp, currency = false, color = if (s.netProfit < 0) K.c.red else K.c.tx)
                        }
                    }
                    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KKpi(Icons.Outlined.Payments, stringResource(R.string.rep_expenses), K.c.redT, Modifier.weight(1f).fillMaxHeight()) { KMoney(s.expenses, size = 15.sp, currency = false) }
                        KKpi(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.rep_refunds), K.c.violet, Modifier.weight(1f).fillMaxHeight()) { KMoney(s.refunds, size = 15.sp, currency = false) }
                    }
                }
            }
            item("pay") {
                KSection(stringResource(R.string.rep_payments))
                val rows = listOf("naqd" to K.c.primary, "karta" to K.c.blue, "qarz" to K.c.red, "qr" to K.c.violet.fg)
                    .map { (t, c) -> Triple(t, c, d.payments.net.getValue(t)) }
                    .filter { it.first != "qr" || it.third != 0.0 }
                val max = rows.maxOf { it.third }.coerceAtLeast(1.0)
                KCard(Modifier.padding(horizontal = Edge)) {
                    rows.forEach { (t, c, v) -> KBar(paymentLabel(t), (v / max).toFloat(), v, c) }
                }
            }
            item("top-title") { KSection(stringResource(R.string.rep_top)) }
            if (d.top.isEmpty()) item("top-empty") { MutedNote(stringResource(R.string.rep_no_sales)) }
            items(d.top, key = { "t" + it.p.productUuid }) { r ->
                KRow(
                    r.p.name,
                    Modifier.padding(horizontal = Edge, vertical = 4.dp),
                    subtitle = { KSub(Money.qty(r.p.qty) + " " + r.unit) },
                    trailing = { KMoney(r.p.revenue) },
                )
            }
            if (d.cashiers.size > 1) {
                item("cash-title") { KSection(stringResource(R.string.rep_cashiers)) }
                items(d.cashiers, key = { "c" + it.userUuid }) { c ->
                    KRow(
                        c.name.ifBlank { "—" },
                        Modifier.padding(horizontal = Edge, vertical = 4.dp),
                        leading = { KAvatar(c.name) },
                        subtitle = {
                            KSub(
                                stringResource(R.string.rep_sales_count, c.salesCount.toInt()) +
                                    (c.commission?.let { " · " + stringResource(R.string.rep_commission, Money.format(it)) } ?: ""),
                            )
                        },
                        trailing = { KMoney(c.revenue) },
                    )
                }
            }
        }
    }
    if (pick) DateRangeDialog(customFrom, customTo, onDismiss = { pick = false }) { f, t ->
        customFrom = f; customTo = t; preset = ReportService.Preset.CUSTOM; pick = false
    }
}
