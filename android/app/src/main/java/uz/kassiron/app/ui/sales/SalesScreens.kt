package uz.kassiron.app.ui.sales

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.RefundException
import uz.kassiron.app.domain.RefundService
import uz.kassiron.app.domain.ReportService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KChips
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KKeyValue
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KStepper
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.reports.BoRow
import uz.kassiron.app.ui.reports.DateRangeDialog
import uz.kassiron.app.ui.reports.EndColumn
import uz.kassiron.app.ui.reports.NoAccess
import uz.kassiron.app.ui.reports.PayTag
import uz.kassiron.app.ui.reports.paymentColor
import uz.kassiron.app.ui.reports.paymentLabel
import uz.kassiron.app.ui.reports.rangeLabel
import uz.kassiron.app.ui.reports.receiptLabel
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Onest
import uz.kassiron.app.ui.till.printReceipt

// ---------------------------------------------------------------- list (mockup 19)

private data class SaleItem(
    val uuid: String,
    val receipt: String,
    val createdAt: String,
    val total: Double,
    val paymentType: String,
    val status: String,
    val itemCount: Long,
    val refunded: Double,
    val customer: String?,
    val wholesale: Boolean = false,
)

private data class SalesData(
    val sales: List<SaleItem>,
    val count: Long,
    val gross: Double,
    val refunds: Double,
    val payments: ReportService.Payments,
)

private val FILTERS = listOf<String?>(null, "naqd", "karta", "qarz", "aralash")

private fun loadSales(from: String, to: String, filter: String?): SalesData {
    val db = App.db
    val (start, end) = Time.utcRange(from, to)
    val args = mutableListOf<Any?>(start, end)
    val where = StringBuilder("s.created_at >= ? AND s.created_at < ?")
    when (filter) {
        null -> Unit
        "aralash" -> where.append(" AND s.payment_type = 'aralash'")
        else -> {
            where.append(
                """ AND (EXISTS (SELECT 1 FROM sale_payments sp WHERE sp.sale_id = s.uuid AND sp.payment_type = ? AND sp.amount > 0)
                     OR (NOT EXISTS (SELECT 1 FROM sale_payments sp2 WHERE sp2.sale_id = s.uuid) AND s.payment_type = ?))"""
            )
            args += filter; args += filter
        }
    }
    val sales = db.query(
        """SELECT s.uuid, s.receipt_no, s.created_at, s.total, s.payment_type, s.status, s.price_type, c.full_name AS customer_name,
                  (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.uuid) AS item_count,
                  (SELECT COALESCE(SUM(r.total_amount), 0) FROM refunds r WHERE r.sale_id = s.uuid) AS refunded
           FROM sales s LEFT JOIN customers c ON c.uuid = s.customer_id
           WHERE $where ORDER BY s.created_at DESC LIMIT 3000""",
        *args.toTypedArray(),
    ).map {
        SaleItem(
            it.str("uuid"), receiptLabel(it.strOrNull("receipt_no"), it.str("uuid")), it.str("created_at"), it.dbl("total"),
            it.str("payment_type"), it.str("status"), it.long("item_count"), it.dbl("refunded"),
            it.strOrNull("customer_name")?.takeIf { n -> n.isNotBlank() },
            it.strOrNull("price_type") == "wholesale",
        )
    }
    // Totals are for the whole period, whatever the payment filter (like the website).
    val (cnt, gross) = db.queryOne(
        "SELECT COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS gross FROM sales WHERE status = 'completed' AND created_at >= ? AND created_at < ?", start, end,
    )?.let { it.long("cnt") to it.dbl("gross") } ?: (0L to 0.0)
    val refunds = db.scalarDouble("SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE created_at >= ? AND created_at < ?", start, end)
    return SalesData(sales, cnt, Money.round2(gross), Money.round2(refunds), ReportService.paymentBreakdown(db, from, to))
}

/** The period chips: Bugun, Kecha, 7 kun, Oy (+ the picked range). */
private fun presetRange(i: Int): Pair<String, String> {
    val today = Time.today()
    return when (i) {
        0 -> today to today
        1 -> Time.addDays(today, -1).let { it to it }
        2 -> Time.addDays(today, -6) to today
        else -> today.substring(0, 8) + "01" to today
    }
}

@Composable
fun SalesScreen(nav: NavHostController) {
    if (!App.session.can("sales")) { NoAccess(); return }
    val today = Time.today()
    var from by rememberSaveable { mutableStateOf(today) }
    var to by rememberSaveable { mutableStateOf(today) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var pick by remember { mutableStateOf(false) }
    val data by dbState<SalesData?>(null, from, to, filter) { loadSales(from, to, filter) }
    val multiDay = from != to
    val preset = (0..3).firstOrNull { presetRange(it) == (from to to) }

    KScreen(
        topBar = {
            KTopBar(stringResource(R.string.sales_title)) {
                KIconButton(Icons.Outlined.CalendarMonth, { pick = true }, contentDescription = stringResource(R.string.bo_pick_range))
            }
        },
    ) {
        val periodLabels = buildList {
            add(stringResource(R.string.sales_today)); add(stringResource(R.string.sales_yesterday))
            add(stringResource(R.string.sales_7days)); add(stringResource(R.string.sales_month))
            if (preset == null) add(rangeLabel(from, to))
        }
        KChips(periodLabels, preset ?: 4, { i -> if (i == 4) pick = true else presetRange(i).let { (f, t) -> from = f; to = t } })
        val d = data
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)) {
            if (d != null) item("totals") { SalesTotals(d) }
            item("filters") {
                val labels = FILTERS.map { f -> if (f == null) stringResource(R.string.sales_filter_all) else paymentLabel(f) }
                KChips(labels, FILTERS.indexOf(filter), { filter = FILTERS[it] }, Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
            if (d != null && d.sales.isEmpty()) item("empty") {
                KEmpty(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    stringResource(R.string.sales_empty),
                    stringResource(if (filter == null) R.string.sales_empty_hint else R.string.sales_empty_filter),
                )
            }
            if (d != null) items(d.sales, key = { it.uuid }) { s ->
                SaleRow(s, multiDay, Modifier.padding(start = Edge, end = Edge, top = 8.dp)) { nav.navigate(Routes.sale(s.uuid)) }
            }
        }
    }
    if (pick) DateRangeDialog(from, to, onDismiss = { pick = false }) { f, t -> from = f; to = t; pick = false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SalesTotals(d: SalesData) {
    KCard(Modifier.padding(horizontal = Edge)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sales_revenue), style = K.t.small, color = K.c.mu)
                KMoney(d.gross - d.refunds, size = 24.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.sales_count), style = K.t.small, color = K.c.mu)
                Text(d.count.toString(), style = K.t.hero, color = K.c.tx)
            }
        }
        FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("naqd", "karta", "qarz", "qr").forEach { t ->
                val v = d.payments.net.getValue(t)
                if (t != "qr" || v != 0.0) PayFigure(paymentLabel(t), v, paymentColor(t))
            }
            if (d.refunds > 0) PayFigure(stringResource(R.string.sales_refunded_sum), d.refunds, K.c.amb)
        }
    }
}

@Composable
private fun PayFigure(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Text("$label ${Money.format(amount, withCurrency = false)}", style = K.t.label, color = color, maxLines = 1)
}

@Composable
private fun SaleRow(s: SaleItem, multiDay: Boolean, modifier: Modifier, onClick: () -> Unit) {
    BoRow(
        modifier, onClick = onClick,
        trailing = {
            EndColumn {
                KMoney(s.total)
                PayTag(s.paymentType)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.bo_receipt_no, s.receipt), style = K.t.title, color = K.c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            Text(Time.local(s.createdAt, if (multiDay) "dd.MM HH:mm" else "HH:mm"), style = K.t.small, color = K.c.mu, maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
        }
        KSub(listOfNotNull(stringResource(R.string.bo_items, s.itemCount.toInt()), s.customer).joinToString(" · "))
        val refundTag = s.refunded > 0
        val statusTag = !refundTag && s.status != "completed"
        if (s.wholesale || refundTag || statusTag) {
            Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (s.wholesale) KTag(stringResource(R.string.till_wholesale), KTagKind.Neutral)
                if (refundTag) KTag(stringResource(if (s.refunded >= s.total - 0.01) R.string.sales_refunded else R.string.sales_partly_refunded), KTagKind.Low)
                if (statusTag) KTag(s.status)
            }
        }
    }
}

// ---------------------------------------------------------------- detail (mockup 20)

private data class RefundInfo(
    val uuid: String,
    val createdAt: String,
    val total: Double,
    val reason: String?,
    val items: List<Row>,
)

private data class SaleDetail(
    val sale: Row,
    val lines: List<RefundService.Line>,
    val payments: List<Pair<String, Double>>,
    val refunds: List<RefundInfo>,
)

private fun loadSale(uuid: String): SaleDetail? {
    val db = App.db
    val sale = db.queryOne(
        """SELECT s.*, COALESCE(u.full_name, '') AS cashier_name, c.full_name AS customer_name
           FROM sales s LEFT JOIN users u ON u.uuid = s.cashier_id LEFT JOIN customers c ON c.uuid = s.customer_id
           WHERE s.uuid = ?""",
        uuid,
    ) ?: return null
    val lines = RefundService.refundable(db, uuid)
    val rows = db.query(
        """SELECT payment_type, amount FROM sale_payments WHERE sale_id = ?
           ORDER BY CASE payment_type WHEN 'naqd' THEN 1 WHEN 'karta' THEN 2 WHEN 'qr' THEN 3 WHEN 'qarz' THEN 4 ELSE 5 END""",
        uuid,
    )
    val payments = if (rows.isNotEmpty()) rows.map { it.str("payment_type") to it.dbl("amount") }
    else RefundService.paymentSplit(db, sale).filterValues { it > 0 }.toList()
    val refunds = db.query("SELECT * FROM refunds WHERE sale_id = ? ORDER BY created_at DESC", uuid).map { r ->
        RefundInfo(
            r.str("uuid"), r.str("created_at"), r.dbl("total_amount"), r.strOrNull("reason")?.takeIf { it.isNotBlank() },
            db.query(
                "SELECT ri.qty, COALESCE(si.product_name, '?') AS product_name FROM refund_items ri LEFT JOIN sale_items si ON si.uuid = ri.sale_item_id WHERE ri.refund_id = ? ORDER BY ri.rowid",
                r.str("uuid"),
            ),
        )
    }
    return SaleDetail(sale, lines, payments, refunds)
}

private fun lineName(l: RefundService.Line) = l.name + (l.variantLabel?.let { " ($it)" } ?: "")

@Composable
fun SaleDetailScreen(nav: NavHostController, uuid: String) {
    val loaded by dbState<Pair<Boolean, SaleDetail?>>(false to null, uuid) { true to loadSale(uuid) }
    val d = loaded.second
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val canRefund = d != null && App.session.can("sales") && d.sale.str("status") == "completed" && d.lines.any { it.remainingQty > 0.0001 }

    KScreen(
        topBar = {
            KTopBar(
                d?.let { stringResource(R.string.bo_receipt_no, receiptLabel(it.sale.strOrNull("receipt_no"), uuid)) } ?: stringResource(R.string.sales_title),
                onBack = { nav.popBackStack() },
            ) {
                if (d != null) KIconButton(Icons.Outlined.Print, { printReceipt(context, scope, snackbar, nav, uuid) }, contentDescription = stringResource(R.string.sales_print))
            }
        },
        bottomBar = if (d == null) null else ({
            KBottomBar {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KButton(
                        stringResource(R.string.sales_receipt), { nav.navigate(Routes.receipt(uuid)) }, Modifier.weight(1f),
                        kind = KButtonKind.Alt, icon = Icons.AutoMirrored.Outlined.ReceiptLong, height = 46.dp, fontSize = 14.sp,
                    )
                    if (canRefund) KButton(
                        stringResource(R.string.sales_refund), { nav.navigate(Routes.refund(uuid)) }, Modifier.weight(1f),
                        kind = KButtonKind.Danger, icon = Icons.AutoMirrored.Outlined.Undo, height = 46.dp, fontSize = 14.sp,
                    )
                }
            }
        }),
        snackbar = snackbar,
    ) {
        if (!loaded.first) return@KScreen
        if (d == null) { KEmpty(Icons.Outlined.SearchOff, stringResource(R.string.sales_not_found), modifier = Modifier.padding(top = 40.dp)); return@KScreen }
        val s = d.sale
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp)) {
            item("info") {
                KCard(Modifier.padding(horizontal = Edge), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    KKeyValue(stringResource(R.string.sales_time), first = true) { Value(Time.local(s.str("created_at"))) }
                    KKeyValue(stringResource(R.string.sales_cashier)) { Value(s.str("cashier_name").ifBlank { "—" }) }
                    val customerUuid = s.strOrNull("customer_id")?.takeIf { it.isNotBlank() }
                    KKeyValue(stringResource(R.string.sales_customer)) {
                        if (customerUuid == null) Value("—")
                        else {
                            val canOpen = App.session.can("customers")
                            Text(
                                s.strOrNull("customer_name")?.takeIf { it.isNotBlank() } ?: "—",
                                style = K.t.body.copy(fontSize = 14.sp), color = if (canOpen) K.c.ink else K.c.tx, maxLines = 1,
                                modifier = if (canOpen) Modifier.clickable { nav.navigate(Routes.customer(customerUuid)) }.padding(vertical = 8.dp) else Modifier,
                            )
                        }
                    }
                    if (s.str("status") != "completed") KKeyValue(stringResource(R.string.sales_status)) { KTag(s.str("status"), KTagKind.Low) }
                    if (s.strOrNull("price_type") == "wholesale") KKeyValue(stringResource(R.string.sales_price_type)) { KTag(stringResource(R.string.till_wholesale_prices), KTagKind.Neutral) }
                }
            }
            item("items-title") { KSection(stringResource(R.string.sales_items_title)) }
            items(d.lines, key = { "i" + it.saleItemUuid }) { l ->
                KRow(
                    lineName(l),
                    Modifier.padding(horizontal = Edge, vertical = 4.dp),
                    subtitle = {
                        KSub("${Money.format(l.unitPrice, withCurrency = false)} × ${Money.qty(l.qty)}")
                        if (l.refundedQty > 0) KSub(stringResource(R.string.sales_line_refunded, Money.qty(l.refundedQty)), K.c.amb)
                    },
                    trailing = { KMoney(l.subtotal) },
                )
            }
            item("pay") {
                KCard(Modifier.padding(start = Edge, end = Edge, top = 6.dp), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    val received = s.dblOrNull("cash_received") ?: 0.0
                    val naqd = d.payments.filter { it.first == "naqd" }.sumOf { it.second }
                    var first = true
                    d.payments.forEach { (t, a) ->
                        val shown = if (t == "naqd" && received > a) received else a
                        KKeyValue(paymentLabel(t), first = first) { Amount(shown) }
                        first = false
                    }
                    if (received - naqd > 0.004 && naqd > 0) KKeyValue(stringResource(R.string.sales_change)) { Amount(received - naqd) }
                    val discount = s.dbl("discount")
                    if (discount > 0) KKeyValue(stringResource(R.string.sales_discount), first = first) { Amount(-discount, K.c.red) }
                    KKeyValue(stringResource(R.string.sales_total), first = first && discount <= 0) { KMoney(s.dbl("total")) }
                }
            }
            if (d.refunds.isNotEmpty()) {
                item("ref-title") { KSection(stringResource(R.string.sales_refunds_title)) }
                items(d.refunds, key = { "r" + it.uuid }) { r ->
                    KRow(
                        Time.local(r.createdAt),
                        Modifier.padding(horizontal = Edge, vertical = 4.dp),
                        subtitle = {
                            KSub(r.items.joinToString(", ") { it.str("product_name") + " × " + Money.qty(it.dbl("qty")) })
                            r.reason?.let { KSub(it) }
                        },
                        trailing = { Amount(-r.total, K.c.amb) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = K.t.body.copy(fontSize = 14.sp), color = K.c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Amount(v: Double, color: androidx.compose.ui.graphics.Color = K.c.tx) {
    Text(
        (if (v < 0) "−" else "") + Money.format(kotlin.math.abs(v), withCurrency = false),
        style = K.t.title.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = color, maxLines = 1,
    )
}

// ---------------------------------------------------------------- refund (mockup 21)

private data class RefundData(val sale: Row, val lines: List<RefundService.Line>, val split: Map<String, Double>)

@Composable
fun RefundScreen(nav: NavHostController, uuid: String) {
    if (!App.session.can("sales")) { NoAccess(); return }
    val loaded by dbState<Pair<Boolean, RefundData?>>(false to null, uuid) {
        true to App.db.queryOne("SELECT * FROM sales WHERE uuid = ?", uuid)?.let { sale ->
            RefundData(sale, RefundService.refundable(App.db, uuid), RefundService.paymentSplit(App.db, sale))
        }
    }
    val qty = remember { mutableStateMapOf<String, Double>() }
    var reason by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val data = loaded.second
    val open = data?.lines?.filter { it.remainingQty > 0.0001 }.orEmpty()
    val ratio = data?.let { RefundService.discountRatio(it.sale) } ?: 1.0
    val chosen = open.mapNotNull { l -> qty[l.saleItemUuid]?.takeIf { it > 0 }?.let { l to minOf(it, l.remainingQty) } }
    val total = Money.round2(chosen.sumOf { (l, q) -> RefundService.lineAmount(l.unitPrice, q, ratio) })

    KScreen(
        topBar = { KTopBar(stringResource(R.string.sales_refund), onBack = { nav.popBackStack() }) },
        bottomBar = if (open.isEmpty()) null else ({
            KBottomBar {
                KButton(
                    if (total > 0) stringResource(R.string.sales_refund_btn, Money.format(total)) else stringResource(R.string.sales_refund_pick),
                    { confirm = true }, Modifier.fillMaxWidth(), kind = KButtonKind.Danger, height = 54.dp, enabled = total > 0 && !saving,
                )
            }
        }),
        snackbar = snackbar,
    ) {
        if (!loaded.first) return@KScreen
        if (data == null) { KEmpty(Icons.Outlined.SearchOff, stringResource(R.string.sales_not_found), modifier = Modifier.padding(top = 40.dp)); return@KScreen }
        if (open.isEmpty()) { KEmpty(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.sales_refund_nothing), stringResource(R.string.sales_refund_nothing_hint), modifier = Modifier.padding(top = 40.dp)); return@KScreen }
        val allocation = RefundService.allocateByPayment(total, data.split.getValue("naqd"), data.split.getValue("karta"), data.split.getValue("qarz"), data.split.getValue("qr"))

        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp)) {
            items(open, key = { it.saleItemUuid }) { l ->
                val q = qty[l.saleItemUuid] ?: 0.0
                val price = Money.format(l.unitPrice, withCurrency = false)
                KRow(
                    lineName(l),
                    Modifier.padding(horizontal = Edge, vertical = 4.dp),
                    selected = q > 0,
                    subtitle = {
                        KSub(
                            if (l.refundedQty > 0) stringResource(R.string.sales_ref_sold_back, price, Money.qty(l.qty), Money.qty(l.refundedQty))
                            else stringResource(R.string.sales_ref_sold, price, Money.qty(l.qty)),
                        )
                    },
                    trailing = {
                        KStepper(
                            Money.qty(q),
                            onMinus = { qty[l.saleItemUuid] = maxOf(0.0, q - 1.0) },
                            onPlus = { qty[l.saleItemUuid] = minOf(l.remainingQty, q + 1.0) },
                            onQtyClick = { qty[l.saleItemUuid] = if (q >= l.remainingQty) 0.0 else l.remainingQty },
                        )
                    },
                )
            }
            item("reason") {
                KField(
                    reason, { reason = it },
                    Modifier.padding(start = Edge, end = Edge, top = 8.dp),
                    label = stringResource(R.string.sales_reason),
                    placeholder = stringResource(R.string.sales_reason_ph),
                )
            }
            item("sum") {
                KCard(Modifier.padding(start = Edge, end = Edge, top = 14.dp), tone = KCardTone.Tint, padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    val parts = allocation.filterValues { it > 0 }
                    if (parts.isEmpty()) {
                        KKeyValue(stringResource(R.string.sales_refund_sum), first = true) { RefundAmount(0.0) }
                    } else {
                        var first = true
                        parts.forEach { (t, a) ->
                            KKeyValue(refundPartLabel(t), first = first) { RefundAmount(a) }
                            first = false
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        KConfirm(
            title = stringResource(R.string.sales_refund_confirm, Money.format(total)),
            text = stringResource(R.string.sales_refund_confirm_text),
            confirm = stringResource(R.string.sales_refund),
            dismiss = stringResource(R.string.bo_cancel),
            danger = true,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                saving = true
                val lines = chosen.associate { (l, q) -> l.saleItemUuid to q }
                val r = reason.trim().ifEmpty { null }
                scope.launch {
                    val err = io<Int?> {
                        try {
                            RefundService.create(uuid, App.session.current().uuid, lines, r)
                            App.sync.requestSync()
                            null
                        } catch (e: RefundException) {
                            refundError(e.code)
                        } catch (e: Exception) {
                            R.string.sales_err_unexpected
                        }
                    }
                    saving = false
                    if (err == null) {
                        Toast.makeText(context, R.string.sales_refund_done, Toast.LENGTH_SHORT).show()
                        nav.popBackStack()
                    } else snackbar.showSnackbar(context.getString(err))
                }
            },
        )
    }
}

@Composable
private fun refundPartLabel(type: String): String = when (type) {
    "naqd" -> stringResource(R.string.sales_back_naqd)
    "karta" -> stringResource(R.string.sales_back_karta)
    "qarz" -> stringResource(R.string.sales_back_qarz)
    "qr" -> stringResource(R.string.sales_back_qr)
    else -> type
}

@Composable
private fun RefundAmount(v: Double) {
    Text(Money.format(v), style = K.t.amount.copy(fontFamily = Onest, fontSize = 18.sp), color = if (v > 0) K.c.ink else K.c.mu, maxLines = 1)
}

private fun refundError(code: String): Int = when (code) {
    "refund_no_items_selected" -> R.string.sales_err_no_items
    "invalid_refund_qty" -> R.string.sales_err_qty
    "invalid_refund_qty_whole" -> R.string.sales_err_qty_whole
    "invalid_refund_item" -> R.string.sales_err_item
    "sale_not_found" -> R.string.sales_not_found
    "product_not_found" -> R.string.sales_err_product
    else -> R.string.sales_err_unexpected
}
