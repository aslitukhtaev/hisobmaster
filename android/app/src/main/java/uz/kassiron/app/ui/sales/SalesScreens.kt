package uz.kassiron.app.ui.sales

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.RefundException
import uz.kassiron.app.domain.RefundService
import uz.kassiron.app.domain.ReportService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.BigButton
import uz.kassiron.app.ui.common.ConfirmDialog
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.reports.AmountLine
import uz.kassiron.app.ui.reports.BoCard
import uz.kassiron.app.ui.reports.BoScaffold
import uz.kassiron.app.ui.reports.DateRangeButton
import uz.kassiron.app.ui.reports.NoAccess
import uz.kassiron.app.ui.reports.PaymentBadge
import uz.kassiron.app.ui.reports.Pill
import uz.kassiron.app.ui.reports.SectionTitle
import uz.kassiron.app.ui.reports.paymentColor
import uz.kassiron.app.ui.reports.paymentLabel
import uz.kassiron.app.ui.reports.receiptLabel
import uz.kassiron.app.ui.theme.Brand

// ---------------------------------------------------------------- list

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
    val cashier: String,
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
        """SELECT s.uuid, s.receipt_no, s.created_at, s.total, s.payment_type, s.status,
                  c.full_name AS customer_name, COALESCE(u.full_name, '') AS cashier_name,
                  (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.uuid) AS item_count,
                  (SELECT COALESCE(SUM(r.total_amount), 0) FROM refunds r WHERE r.sale_id = s.uuid) AS refunded
           FROM sales s LEFT JOIN customers c ON c.uuid = s.customer_id LEFT JOIN users u ON u.uuid = s.cashier_id
           WHERE $where ORDER BY s.created_at DESC LIMIT 3000""",
        *args.toTypedArray(),
    ).map {
        SaleItem(
            it.str("uuid"), receiptLabel(it.strOrNull("receipt_no"), it.str("uuid")), it.str("created_at"), it.dbl("total"),
            it.str("payment_type"), it.str("status"), it.long("item_count"), it.dbl("refunded"),
            it.strOrNull("customer_name")?.takeIf { n -> n.isNotBlank() }, it.str("cashier_name"),
        )
    }
    val completed = sales.filter { it.status == "completed" }
    val refunds = db.scalarDouble("SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE created_at >= ? AND created_at < ?", start, end)
    return SalesData(sales, completed.size.toLong(), Money.round2(completed.sumOf { it.total }), Money.round2(refunds), ReportService.paymentBreakdown(db, from, to))
}

@Composable
fun SalesScreen(nav: NavHostController) {
    if (!App.session.can("sales")) { NoAccess(); return }
    val today = Time.today()
    var from by rememberSaveable { mutableStateOf(today) }
    var to by rememberSaveable { mutableStateOf(today) }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val data by dbState<SalesData?>(null, from, to, filter) { loadSales(from, to, filter) }
    val multiDay = from != to

    BoScaffold(title = stringResource(R.string.sales_title)) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("range") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateRangeButton(from, to, { f, t -> from = f; to = t }, Modifier.weight(1f))
                    FilterChip(selected = from == today && to == today, onClick = { from = today; to = today }, label = { Text(stringResource(R.string.reports_today)) })
                    val y = Time.addDays(today, -1)
                    FilterChip(selected = from == y && to == y, onClick = { from = y; to = y }, label = { Text(stringResource(R.string.reports_yesterday)) })
                }
            }
            val d = data
            if (d != null) {
                item("totals") { SalesTotals(d) }
            }
            item("filters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FILTERS.forEach { f ->
                        FilterChip(
                            selected = filter == f, onClick = { filter = f },
                            label = { Text(if (f == null) stringResource(R.string.sales_filter_all) else paymentLabel(f)) },
                        )
                    }
                }
            }
            if (d != null && d.sales.isEmpty()) item("empty") {
                EmptyState(Icons.Filled.ReceiptLong, stringResource(R.string.sales_empty), Modifier.height(240.dp))
            }
            if (d != null) items(d.sales, key = { it.uuid }) { s -> SaleRow(s, multiDay) { nav.navigate(Routes.sale(s.uuid)) } }
        }
    }
}

@Composable
private fun SalesTotals(d: SalesData) {
    BoCard {
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.sales_net), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(d.gross - d.refunds), style = MaterialTheme.typography.headlineMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.sales_count), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.count.toString(), style = MaterialTheme.typography.headlineMedium)
            }
        }
        if (d.refunds > 0) {
            Text(
                stringResource(R.string.sales_revenue) + ": " + Money.format(d.gross) + " · " + stringResource(R.string.sales_refunds) + ": −" + Money.format(d.refunds),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RefundService.PAYMENT_TYPES.forEach { t ->
                val v = d.payments.net.getValue(t)
                if (t != "qr" || v != 0.0) {
                    Column {
                        Text(paymentLabel(t), color = paymentColor(t), style = MaterialTheme.typography.labelLarge)
                        Text(Money.format(v), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleRow(s: SaleItem, multiDay: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("№ ${s.receipt}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(Time.local(s.createdAt, if (multiDay) "dd.MM HH:mm" else "HH:mm"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    listOfNotNull(stringResource(R.string.sales_items, s.itemCount.toInt()), s.customer, s.cashier.ifBlank { null }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (s.refunded > 0) {
                    Pill(stringResource(if (s.refunded >= s.total - 0.01) R.string.sales_refunded else R.string.sales_partly_refunded), Brand.Warning)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Money.format(s.total), style = MaterialTheme.typography.titleMedium)
                PaymentBadge(s.paymentType)
            }
        }
    }
}

// ---------------------------------------------------------------- detail

private data class RefundInfo(
    val uuid: String,
    val createdAt: String,
    val total: Double,
    val reason: String?,
    val by: String,
    val items: List<Row>,
    val payments: List<Row>,
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
        """SELECT s.*, COALESCE(u.full_name, '') AS cashier_name, c.full_name AS customer_name, c.phone AS customer_phone
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
    val refunds = db.query(
        "SELECT r.*, COALESCE(u.full_name, '') AS by_name FROM refunds r LEFT JOIN users u ON u.uuid = r.refunded_by WHERE r.sale_id = ? ORDER BY r.created_at DESC",
        uuid,
    ).map { r ->
        RefundInfo(
            r.str("uuid"), r.str("created_at"), r.dbl("total_amount"), r.strOrNull("reason")?.takeIf { it.isNotBlank() }, r.str("by_name"),
            db.query(
                "SELECT ri.qty, ri.amount, COALESCE(si.product_name, '?') AS product_name, si.variant_label FROM refund_items ri LEFT JOIN sale_items si ON si.uuid = ri.sale_item_id WHERE ri.refund_id = ? ORDER BY ri.rowid",
                r.str("uuid"),
            ),
            db.query("SELECT payment_type, amount FROM refund_payments WHERE refund_id = ?", r.str("uuid")),
        )
    }
    return SaleDetail(sale, lines, payments, refunds)
}

@Composable
fun SaleDetailScreen(nav: NavHostController, uuid: String) {
    val loaded by dbState<Pair<Boolean, SaleDetail?>>(false to null, uuid) { true to loadSale(uuid) }
    val d = loaded.second
    BoScaffold(
        title = d?.let { stringResource(R.string.sales_receipt_title, receiptLabel(it.sale.strOrNull("receipt_no"), uuid)) } ?: stringResource(R.string.sales_title),
        onBack = { nav.popBackStack() },
    ) { pad ->
        if (!loaded.first) return@BoScaffold
        if (d == null) { EmptyState(Icons.Filled.SearchOff, stringResource(R.string.sales_not_found), Modifier.padding(pad)); return@BoScaffold }
        val s = d.sale
        val canRefund = App.session.can("sales") && d.lines.any { it.remainingQty > 0.0001 }
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("head") {
                BoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Money.format(s.dbl("total")), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                        PaymentBadge(s.str("payment_type"))
                    }
                    Spacer(Modifier.height(6.dp))
                    AmountLine(stringResource(R.string.sales_date), Time.local(s.str("created_at")))
                    AmountLine(stringResource(R.string.sales_cashier), s.str("cashier_name").ifBlank { "—" })
                    val customerUuid = s.strOrNull("customer_id")?.takeIf { it.isNotBlank() }
                    if (customerUuid != null) {
                        val name = s.strOrNull("customer_name") ?: "—"
                        val canOpen = App.session.can("customers")
                        Row(
                            Modifier.fillMaxWidth().then(if (canOpen) Modifier.clickable { nav.navigate(Routes.customer(customerUuid)) } else Modifier).padding(vertical = 3.dp),
                        ) {
                            Text(stringResource(R.string.sales_customer), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(name + (s.strOrNull("customer_phone")?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                                color = if (canOpen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (s.str("status") != "completed") Pill(s.str("status"), Brand.Warning)
                }
            }
            item("btns") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canRefund) {
                        OutlinedButton(onClick = { nav.navigate(Routes.refund(uuid)) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.AutoMirrored.Filled.Undo, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sales_refund_action))
                        }
                    }
                    OutlinedButton(onClick = { nav.navigate(Routes.receipt(uuid)) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Receipt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.sales_open_receipt))
                    }
                }
            }
            item("items-title") { SectionTitle(stringResource(R.string.sales_items_title)) }
            item("items") {
                BoCard {
                    d.lines.forEachIndexed { i, l ->
                        if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(l.name + (l.variantLabel?.let { " ($it)" } ?: ""), fontWeight = FontWeight.SemiBold)
                                Text("${Money.qty(l.qty)} × ${Money.format(l.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (l.refundedQty > 0) Text(stringResource(R.string.sales_refunded_qty, Money.qty(l.refundedQty)), style = MaterialTheme.typography.bodySmall, color = Brand.Warning)
                            }
                            Text(Money.format(l.subtotal), fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val discount = s.dbl("discount")
                    if (discount > 0) {
                        AmountLine(stringResource(R.string.sales_subtotal), Money.format(s.dbl("total") + discount))
                        AmountLine(stringResource(R.string.sales_discount), "−" + Money.format(discount), color = Brand.Danger)
                    }
                    AmountLine(stringResource(R.string.sales_total), Money.format(s.dbl("total")), bold = true)
                }
            }
            item("pay-title") { SectionTitle(stringResource(R.string.sales_payments)) }
            item("pay") {
                BoCard {
                    d.payments.forEach { (t, a) -> AmountLine(paymentLabel(t), Money.format(a), color = paymentColor(t)) }
                    val received = s.dblOrNull("cash_received")
                    val naqd = d.payments.filter { it.first == "naqd" }.sumOf { it.second }
                    if (received != null && received > 0) {
                        AmountLine(stringResource(R.string.sales_cash_received), Money.format(received))
                        if (received - naqd > 0.004) AmountLine(stringResource(R.string.sales_change), Money.format(received - naqd))
                    }
                }
            }
            if (d.refunds.isNotEmpty()) {
                item("ref-title") { SectionTitle(stringResource(R.string.sales_refund_history)) }
                items(d.refunds, key = { it.uuid }) { r ->
                    BoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("−" + Money.format(r.total), style = MaterialTheme.typography.titleMedium, color = Brand.Warning, modifier = Modifier.weight(1f))
                            Text(Time.local(r.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (r.by.isNotBlank()) Text(r.by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        r.items.forEach { it2 ->
                            AmountLine(
                                it2.str("product_name") + (it2.strOrNull("variant_label")?.takeIf { v -> v.isNotBlank() }?.let { v -> " ($v)" } ?: "") + " × " + Money.qty(it2.dbl("qty")),
                                Money.format(it2.dbl("amount")),
                            )
                        }
                        if (r.payments.isNotEmpty()) {
                            Text(
                                r.payments.joinToString(" · ") { p -> "${paymentLabelPlain(p.str("payment_type"))}: ${Money.format(p.dbl("amount"))}" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        r.reason?.let { Text(stringResource(R.string.sales_reason) + ": " + it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
    }
}

private fun paymentLabelPlain(type: String): String {
    val ctx = App.app
    return when (type) {
        "naqd" -> ctx.getString(R.string.sales_pay_naqd)
        "karta" -> ctx.getString(R.string.sales_pay_karta)
        "qr" -> ctx.getString(R.string.sales_pay_qr)
        "qarz" -> ctx.getString(R.string.sales_pay_qarz)
        else -> type
    }
}

// ---------------------------------------------------------------- refund

private data class RefundData(val sale: Row, val lines: List<RefundService.Line>, val split: Map<String, Double>)

@Composable
fun RefundScreen(nav: NavHostController, uuid: String) {
    if (!App.session.can("sales")) { NoAccess(); return }
    val loaded by dbState<Pair<Boolean, RefundData?>>(false to null, uuid) {
        true to App.db.queryOne("SELECT * FROM sales WHERE uuid = ?", uuid)?.let { sale ->
            RefundData(sale, RefundService.refundable(App.db, uuid), RefundService.paymentSplit(App.db, sale))
        }
    }
    val qty = remember { mutableStateMapOf<String, String>() }
    var reason by rememberSaveable { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    BoScaffold(title = stringResource(R.string.sales_refund_title), onBack = { nav.popBackStack() }, snackbar = snackbar) { pad ->
        if (!loaded.first) return@BoScaffold
        val data = loaded.second
        if (data == null) { EmptyState(Icons.Filled.SearchOff, stringResource(R.string.sales_not_found), Modifier.padding(pad)); return@BoScaffold }
        val open = data.lines.filter { it.remainingQty > 0.0001 }
        if (open.isEmpty()) { EmptyState(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.sales_refund_nothing), Modifier.padding(pad)); return@BoScaffold }
        val ratio = RefundService.discountRatio(data.sale)
        val chosen = open.mapNotNull { l -> Money.parse(qty[l.saleItemUuid] ?: "")?.takeIf { it > 0 }?.let { l to it } }
        val total = Money.round2(chosen.sumOf { (l, q) -> RefundService.lineAmount(l.unitPrice, minOf(q, l.remainingQty), ratio) })
        val allocation = RefundService.allocateByPayment(total, data.split.getValue("naqd"), data.split.getValue("karta"), data.split.getValue("qarz"), data.split.getValue("qr"))

        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("hint") {
                Text(
                    stringResource(R.string.sales_receipt_title, receiptLabel(data.sale.strOrNull("receipt_no"), uuid)) + " · " + stringResource(R.string.sales_refund_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(open, key = { it.saleItemUuid }) { l ->
                val text = qty[l.saleItemUuid] ?: ""
                val q = Money.parse(text) ?: 0.0
                val step = 1.0
                BoCard {
                    Text(l.name + (l.variantLabel?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    Text(
                        Money.format(l.unitPrice) + " · " + stringResource(R.string.sales_refund_remaining, Money.qty(l.remainingQty)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(onClick = {
                            val n = maxOf(0.0, q - step)
                            qty[l.saleItemUuid] = if (n <= 0) "" else Money.qty(n)
                        }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Remove, null) }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { v -> qty[l.saleItemUuid] = v.filter { it.isDigit() || it == ',' || it == '.' } },
                            modifier = Modifier.width(96.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                            keyboardOptions = KeyboardOptions(keyboardType = if (Units.allowsFraction(l.unit)) KeyboardType.Decimal else KeyboardType.Number),
                            isError = q > l.remainingQty + 0.0001,
                            shape = RoundedCornerShape(12.dp),
                        )
                        FilledTonalIconButton(onClick = {
                            qty[l.saleItemUuid] = Money.qty(minOf(l.remainingQty, q + step))
                        }, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Add, null) }
                        TextButton(onClick = { qty[l.saleItemUuid] = Money.qty(l.remainingQty) }) { Text(stringResource(R.string.sales_refund_all)) }
                    }
                    if (q > 0) Text(
                        "= " + Money.format(RefundService.lineAmount(l.unitPrice, minOf(q, l.remainingQty), ratio)),
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            item("reason") {
                OutlinedTextField(
                    reason, { reason = it }, label = { Text(stringResource(R.string.sales_reason)) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2,
                )
            }
            item("total") {
                BoCard {
                    AmountLine(stringResource(R.string.sales_refund_amount), Money.format(total), bold = true)
                    allocation.filterValues { it > 0 }.forEach { (t, a) -> AmountLine(paymentLabel(t), Money.format(a), color = paymentColor(t)) }
                }
            }
            item("go") {
                BigButton(stringResource(R.string.sales_refund_confirm), onClick = { confirm = true }, enabled = chosen.isNotEmpty() && !saving, icon = Icons.AutoMirrored.Filled.Undo)
                Spacer(Modifier.height(24.dp))
            }
        }

        if (confirm) {
            ConfirmDialog(
                title = stringResource(R.string.sales_refund_confirm),
                text = stringResource(R.string.sales_refund_confirm_text, Money.format(total)),
                confirm = stringResource(R.string.common_yes),
                dismiss = stringResource(R.string.common_no),
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
