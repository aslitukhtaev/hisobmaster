package uz.kassiron.app.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import uz.kassiron.app.domain.Customers
import uz.kassiron.app.domain.Debts
import uz.kassiron.app.domain.ExpenseService
import uz.kassiron.app.domain.ReportService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.AmountField
import uz.kassiron.app.ui.common.BigButton
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.SearchField
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.reports.BoCard
import uz.kassiron.app.ui.reports.BoScaffold
import uz.kassiron.app.ui.reports.NoAccess
import uz.kassiron.app.ui.reports.PaymentBadge
import uz.kassiron.app.ui.reports.Pill
import uz.kassiron.app.ui.reports.SectionTitle
import uz.kassiron.app.ui.reports.SingleDateDialog
import uz.kassiron.app.ui.reports.receiptLabel
import uz.kassiron.app.ui.theme.Brand

private const val BALANCE_SQL = "SELECT customer_id, SUM(CASE WHEN type = 'qarz' THEN amount ELSE -amount END) AS balance FROM debt_transactions GROUP BY customer_id"

private data class CustomerItem(
    val uuid: String,
    val name: String,
    val phone: String,
    val balance: Double,
    val creditLimit: Double?,
    val dueDate: String?,
)

private fun CustomerItem.limitExceeded() = creditLimit != null && balance > creditLimit + 0.001
private fun overdue(due: String?, balance: Double) = balance > 0 && !due.isNullOrBlank() && due < Time.today()

// ---------------------------------------------------------------- list

@Composable
fun CustomersScreen(nav: NavHostController) {
    if (!App.session.can("customers")) { NoAccess(); return }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyDebtors by rememberSaveable { mutableStateOf(true) }

    val list by dbState<List<CustomerItem>?>(null, query, onlyDebtors) {
        val q = query.trim()
        val digits = q.filter(Char::isDigit)
        val where = StringBuilder(" WHERE 1 = 1")
        val args = mutableListOf<Any?>()
        if (q.isEmpty()) {
            if (onlyDebtors) where.append(" AND ROUND(COALESCE(l.balance, 0), 2) > 0")
        } else {
            // Searching looks through every customer, debtor or not.
            where.append(" AND (c.full_name LIKE ?")
            args += "%$q%"
            if (digits.length >= 2) { where.append(" OR c.phone LIKE ?"); args += "%$digits%" }
            where.append(")")
        }
        App.db.query(
            """SELECT c.uuid, c.full_name, c.phone, c.credit_limit, c.debt_due_date, ROUND(COALESCE(l.balance, 0), 2) AS balance
               FROM customers c LEFT JOIN ($BALANCE_SQL) l ON l.customer_id = c.uuid
               $where ORDER BY balance DESC, c.full_name COLLATE NOCASE LIMIT 1000""",
            *args.toTypedArray(),
        ).map {
            CustomerItem(it.str("uuid"), it.str("full_name"), it.str("phone"), it.dbl("balance"), it.dblOrNull("credit_limit"), it.strOrNull("debt_due_date"))
        }
    }
    val totals by dbState(0.0 to 0L) { ReportService.totalDebt(App.db) to ReportService.debtorsCount(App.db) }

    BoScaffold(
        title = stringResource(R.string.customers_title),
        actions = {
            IconButton(onClick = { nav.navigate(Routes.QUICK_DEBTS) }) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.customers_quick_debts))
            }
        },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.customerEdit("new")) },
                icon = { Icon(Icons.Filled.PersonAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.customers_new)) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Brand.Danger.copy(alpha = 0.10f)),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.customers_total_debt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(totals.first), style = MaterialTheme.typography.headlineMedium, color = Brand.Danger)
                    }
                    Text(stringResource(R.string.customers_debtors_count, totals.second.toInt()), style = MaterialTheme.typography.labelLarge)
                }
            }
            SearchField(query, { query = it }, stringResource(R.string.customers_search), Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = onlyDebtors, onClick = { onlyDebtors = true }, label = { Text(stringResource(R.string.customers_filter_debtors)) })
                FilterChip(selected = !onlyDebtors, onClick = { onlyDebtors = false }, label = { Text(stringResource(R.string.customers_filter_all)) })
            }
            val items = list
            when {
                items == null -> Unit
                items.isEmpty() -> EmptyState(
                    Icons.Filled.People,
                    stringResource(if (onlyDebtors && query.isBlank()) R.string.customers_empty_debtors else R.string.customers_empty),
                )
                else -> LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.uuid }) { c -> CustomerRow(c) { nav.navigate(Routes.customer(c.uuid)) } }
                }
            }
        }
    }
}

@Composable
private fun CustomerRow(c: CustomerItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.phone.isNotBlank()) Text(c.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.limitExceeded()) Pill(stringResource(R.string.customers_limit_exceeded), Brand.Danger)
                    if (overdue(c.dueDate, c.balance)) Pill(stringResource(R.string.customers_overdue), Brand.Warning)
                }
            }
            Spacer(Modifier.width(8.dp))
            BalanceText(c.balance, MaterialTheme.typography.titleMedium.fontSize.value)
        }
    }
}

@Composable
private fun BalanceText(balance: Double, size: Float) {
    val color = when {
        balance > 0 -> Brand.Danger
        balance < 0 -> Brand.Success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(Money.format(balance), color = color, fontWeight = FontWeight.ExtraBold, fontSize = size.sp)
        if (balance < 0) Text(stringResource(R.string.customers_overpaid), color = Brand.Success, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------------------------------------------------------- detail

private data class LedgerEntry(
    val uuid: String,
    val type: String,
    val amount: Double,
    val running: Double,
    val createdAt: String,
    val saleUuid: String?,
    val receipt: String?,
    val by: String,
)

private data class CustomerDetail(
    val customer: Row,
    val balance: Double,
    val ledger: List<LedgerEntry>,
    val purchases: List<Row>,
)

private fun loadDetail(uuid: String): CustomerDetail? {
    val db = App.db
    val c = db.queryOne("SELECT * FROM customers WHERE uuid = ?", uuid) ?: return null
    // DebtTransaction::historyByCustomer: running balance in time order, newest first.
    val rows = db.query(
        """SELECT d.uuid, d.type, d.amount, d.created_at, d.sale_id, s.receipt_no, COALESCE(u.full_name, '') AS by_name
           FROM debt_transactions d LEFT JOIN sales s ON s.uuid = d.sale_id LEFT JOIN users u ON u.uuid = d.created_by
           WHERE d.customer_id = ? ORDER BY d.created_at ASC, d.rowid ASC""",
        uuid,
    )
    var running = 0.0
    val ledger = rows.map { r ->
        val amount = r.dbl("amount")
        running = Money.round2(running + if (r.str("type") == Debts.QARZ) amount else -amount)
        val sale = r.strOrNull("sale_id")?.takeIf { it.isNotBlank() }
        LedgerEntry(r.str("uuid"), r.str("type"), amount, running, r.str("created_at"), sale, sale?.let { receiptLabel(r.strOrNull("receipt_no"), it) }, r.str("by_name"))
    }.reversed()
    val purchases = db.query(
        """SELECT s.uuid, s.receipt_no, s.created_at, s.total, s.payment_type,
                  (SELECT COUNT(*) FROM sale_items si WHERE si.sale_id = s.uuid) AS item_count
           FROM sales s WHERE s.customer_id = ? ORDER BY s.created_at DESC LIMIT 200""",
        uuid,
    )
    return CustomerDetail(c, Debts.balance(db, uuid), ledger, purchases)
}

@Composable
fun CustomerDetailScreen(nav: NavHostController, uuid: String) {
    if (!App.session.can("customers")) { NoAccess(); return }
    val loaded by dbState<Pair<Boolean, CustomerDetail?>>(false to null, uuid) { true to loadDetail(uuid) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<String?>(null) } // "pay" | "debt"

    val detail = loaded.second
    BoScaffold(
        title = detail?.customer?.str("full_name") ?: stringResource(R.string.customers_title),
        onBack = { nav.popBackStack() },
        snackbar = snackbar,
        actions = {
            if (detail != null) IconButton(onClick = { nav.navigate(Routes.customerEdit(uuid)) }) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.customers_edit))
            }
        },
    ) { pad ->
        if (!loaded.first) return@BoScaffold
        if (detail == null) {
            EmptyState(Icons.Filled.PersonOff, stringResource(R.string.customers_not_found), Modifier.padding(pad))
            return@BoScaffold
        }
        val c = detail.customer
        val phone = c.str("phone")
        val creditLimit = c.dblOrNull("credit_limit")
        val due = c.strOrNull("debt_due_date")?.takeIf { it.isNotBlank() }
        val shopName = App.kv.shop().optString("name").ifBlank { stringResource(R.string.app_name) }
        val reminder = if (due != null) stringResource(R.string.customers_reminder_due, shopName, c.str("full_name"), Money.format(detail.balance), Time.showDate(due))
        else stringResource(R.string.customers_reminder_no_due, shopName, c.str("full_name"), Money.format(detail.balance))

        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("head") {
                BoCard {
                    Text(stringResource(R.string.customers_balance), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BalanceText(detail.balance, 34f)
                    if (phone.isNotBlank()) Text(phone, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp))
                    val note = c.str("note")
                    if (note.isNotBlank()) Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    Spacer(Modifier.height(8.dp))
                    InfoLine(stringResource(R.string.customers_credit_limit), creditLimit?.let { Money.format(it) } ?: stringResource(R.string.customers_not_set),
                        if (creditLimit != null && detail.balance > creditLimit + 0.001) Brand.Danger else Color.Unspecified)
                    InfoLine(stringResource(R.string.customers_due_date), due?.let { Time.showDate(it) } ?: stringResource(R.string.customers_not_set),
                        if (overdue(due, detail.balance)) Brand.Warning else Color.Unspecified)
                    if (phone.isNotBlank()) {
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.customers_call)) }
                            OutlinedButton(
                                colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                                        if (detail.balance > 0) intent.putExtra("sms_body", reminder)
                                        context.startActivity(intent)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                            ) { Icon(Icons.Filled.Sms, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.customers_sms)) }
                        }
                    }
                }
            }
            item("actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigButton(
                        stringResource(R.string.customers_receive_payment), { dialog = "pay" },
                        Modifier.weight(1f), enabled = detail.balance > 0, icon = Icons.Filled.Payments,
                    )
                    FilledTonalButton(onClick = { dialog = "debt" }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.AddCard, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.customers_add_debt))
                    }
                }
            }
            item("hist") { SectionTitle(stringResource(R.string.customers_history)) }
            if (detail.ledger.isEmpty()) item("hist-empty") {
                Text(stringResource(R.string.customers_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(detail.ledger, key = { "l" + it.uuid }) { e -> LedgerRow(e) { s -> nav.navigate(Routes.sale(s)) } }
            if (detail.purchases.isNotEmpty()) {
                item("purch") { SectionTitle(stringResource(R.string.customers_purchases)) }
                items(detail.purchases, key = { "p" + it.str("uuid") }) { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.sale(s.str("uuid"))) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("№ " + receiptLabel(s.strOrNull("receipt_no"), s.str("uuid")), fontWeight = FontWeight.Bold)
                                Text(
                                    Time.local(s.str("created_at")) + " · " + stringResource(R.string.customers_items, s.long("item_count").toInt()),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(s.dbl("total")), fontWeight = FontWeight.Bold)
                                PaymentBadge(s.str("payment_type"))
                            }
                        }
                    }
                }
            }
        }

        when (dialog) {
            "pay" -> AmountDialog(
                title = stringResource(R.string.customers_receive_payment),
                fill = detail.balance,
                warning = { null },
                onDismiss = { dialog = null },
            ) { amount ->
                scope.launch {
                    val err = io { CustomerActions.recordPayment(uuid, amount, App.session.current().uuid) }
                    if (err == null) { dialog = null; snackbar.showSnackbar(context.getString(R.string.customers_payment_recorded)) }
                    else snackbar.showSnackbar(context.getString(err))
                }
            }
            "debt" -> {
                val limitWarning = stringResource(R.string.customers_limit_warning)
                AmountDialog(
                    title = stringResource(R.string.customers_add_debt),
                    fill = null,
                    warning = { a ->
                        if (a != null && creditLimit != null && detail.balance + a > creditLimit + 0.001)
                            String.format(limitWarning, Money.format(detail.balance + a), Money.format(creditLimit)) else null
                    },
                    onDismiss = { dialog = null },
                ) { amount ->
                    scope.launch {
                        val err = io { CustomerActions.addDebt(uuid, amount, App.session.current().uuid) }
                        if (err == null) { dialog = null; snackbar.showSnackbar(context.getString(R.string.customers_debt_added)) }
                        else snackbar.showSnackbar(context.getString(err))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LedgerRow(e: LedgerEntry, openSale: (String) -> Unit) {
    val (label, color, sign) = when (e.type) {
        Debts.QARZ -> Triple(stringResource(R.string.customers_type_qarz), Brand.Danger, "+")
        Debts.TOLOV -> Triple(stringResource(R.string.customers_type_tolov), Brand.Success, "−")
        else -> Triple(stringResource(R.string.customers_type_refund), Brand.Warning, "−")
    }
    Card(
        modifier = Modifier.fillMaxWidth().then(if (e.saleUuid != null) Modifier.clickable { openSale(e.saleUuid) } else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(label, color)
                    if (e.receipt != null) Text(stringResource(R.string.customers_sale_link, e.receipt), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    Time.local(e.createdAt) + if (e.by.isNotBlank()) " · ${e.by}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(sign + Money.format(e.amount), color = color, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.customers_balance_after, Money.format(e.running)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AmountDialog(title: String, fill: Double?, warning: (Double?) -> String?, onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    val amount = Money.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField(text, { text = it }, stringResource(R.string.customers_amount))
                if (fill != null && fill > 0) {
                    TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = { text = Money.format(fill, withCurrency = false) }) {
                        Text(stringResource(R.string.customers_pay_all) + ": " + Money.format(fill))
                    }
                }
                warning(amount)?.let { Text(it, color = Brand.Warning, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), enabled = amount != null && amount > 0, onClick = { onConfirm(amount) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ---------------------------------------------------------------- edit

@Composable
fun CustomerEditScreen(nav: NavHostController, uuid: String) {
    if (!App.session.can("customers")) { NoAccess(); return }
    val isNew = uuid == "new" || uuid.isBlank()
    val existing by dbState<Pair<Boolean, Row?>>(false to null, uuid) {
        true to (if (isNew) null else App.db.queryOne("SELECT * FROM customers WHERE uuid = ?", uuid))
    }
    var initialized by rememberSaveable { mutableStateOf(isNew) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf<String?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(existing.first) {
        if (initialized || !existing.first) return@LaunchedEffect
        existing.second?.let { c ->
            name = c.str("full_name"); phone = c.str("phone"); note = c.str("note")
            limit = c.dblOrNull("credit_limit")?.let { Money.format(it, withCurrency = false) } ?: ""
            due = c.strOrNull("debt_due_date")?.takeIf { it.isNotBlank() }
        }
        initialized = true
    }

    BoScaffold(
        title = stringResource(if (isNew) R.string.customers_new else R.string.customers_edit_title),
        onBack = { nav.popBackStack() },
    ) { pad ->
        if (!isNew && existing.first && existing.second == null) {
            EmptyState(Icons.Filled.PersonOff, stringResource(R.string.customers_not_found), Modifier.padding(pad))
            return@BoScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.customers_full_name) + " *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(
                phone, { phone = it }, label = { Text(stringResource(R.string.customers_phone)) }, singleLine = true,
                placeholder = { Text("+998 90 123 45 67") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.customers_note)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2)
            AmountField(limit, { limit = it }, stringResource(R.string.customers_credit_limit))
            Text(stringResource(R.string.customers_credit_limit_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { pickDate = true }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.customers_due_date) + ": " + (due?.let { Time.showDate(it) } ?: stringResource(R.string.customers_not_set)))
                }
                if (due != null) IconButton(onClick = { due = null }) { Icon(Icons.Filled.Close, stringResource(R.string.customers_clear)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton(stringResource(R.string.common_save), enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    val r = io { CustomerActions.save(if (isNew) null else uuid, name, phone, note, limit, due) }
                    saving = false
                    when (r) {
                        is CustomerActions.SaveResult.Ok -> {
                            nav.popBackStack()
                            if (isNew) nav.navigate(Routes.customer(r.uuid))
                        }
                        is CustomerActions.SaveResult.Error -> error = if (r.arg != null) context.getString(r.res, r.arg) else context.getString(r.res)
                    }
                }
            })
        }
    }
    if (pickDate) SingleDateDialog(due, allowFuture = true, onDismiss = { pickDate = false }) { due = it; pickDate = false }
}

// ---------------------------------------------------------------- quick debts

private class QuickRow(val id: Long) {
    var name by mutableStateOf("")
    var phone by mutableStateOf("")
    var amount by mutableStateOf("")
    var error by mutableStateOf<Int?>(null)
    val blank get() = name.isBlank() && phone.isBlank() && amount.isBlank()
}

private fun looksLikeFormula(v: String): Boolean =
    v.isNotEmpty() && v.toDoubleOrNull() == null && v.first() in "=+-@\t\r"

@Composable
fun QuickDebtsScreen(nav: NavHostController) {
    if (!App.session.can("customers")) { NoAccess(); return }
    var nextId by remember { mutableStateOf(3L) }
    val rows = remember { mutableStateListOf(QuickRow(0), QuickRow(1), QuickRow(2)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }

    fun save() {
        // QuickEntryController::debtsStore validation, row by row.
        data class Valid(val row: QuickRow, val name: String, val phone: String?, val amount: Double)
        val valid = mutableListOf<Valid>()
        for (r in rows) {
            r.error = null
            if (r.blank) continue
            val name = r.name.trim()
            val phone = CustomerActions.normalize(r.phone)
            val amount = Money.parse(r.amount)
            r.error = when {
                name.isEmpty() -> R.string.customers_name_required
                name.length > 200 || looksLikeFormula(name) -> R.string.customers_quick_name_bad
                phone == null -> R.string.customers_phone_invalid
                r.amount.isBlank() -> R.string.customers_quick_amount_required
                amount == null || amount <= 0 -> R.string.customers_payment_invalid
                amount > ExpenseService.MONEY_MAX -> R.string.customers_amount_too_large
                else -> null
            }
            if (r.error == null) valid += Valid(r, name, phone!!.ifEmpty { null }, Money.round2(amount!!))
        }
        if (valid.isEmpty()) {
            scope.launch { snackbar.showSnackbar(context.getString(if (rows.any { it.error != null }) R.string.customers_quick_errors else R.string.customers_quick_nothing)) }
            return
        }
        saving = true
        scope.launch {
            val user = App.session.current().uuid
            val saved = io {
                val ok = mutableListOf<Valid>()
                App.db.write {
                    for (v in valid) {
                        try {
                            val customer = if (v.phone != null) Customers.findOrCreate(this, v.name, v.phone) else Customers.create(this, v.name, null)
                            Debts.record(this, customer, null, Debts.QARZ, v.amount, user)
                            ok += v
                        } catch (e: Exception) {
                            v.row.error = R.string.customers_quick_failed
                        }
                    }
                }
                if (ok.isNotEmpty()) App.sync.requestSync()
                ok
            }
            saving = false
            rows.removeAll(saved.map { it.row }.toSet())
            if (rows.isEmpty()) rows.add(QuickRow(nextId++))
            if (saved.isNotEmpty()) {
                snackbar.showSnackbar(context.getString(R.string.customers_quick_saved, saved.size, Money.format(saved.sumOf { it.amount })))
            } else snackbar.showSnackbar(context.getString(R.string.customers_quick_errors))
        }
    }

    BoScaffold(title = stringResource(R.string.customers_quick_debts), onBack = { nav.popBackStack() }, snackbar = snackbar) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("hint") { Text(stringResource(R.string.customers_quick_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            itemsIndexed(rows, key = { _, r -> r.id }) { index, r ->
                BoCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { rows.remove(r); if (rows.isEmpty()) rows.add(QuickRow(nextId++)) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_delete))
                        }
                    }
                    OutlinedTextField(r.name, { r.name = it; r.error = null }, label = { Text(stringResource(R.string.customers_full_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            r.phone, { r.phone = it; r.error = null }, label = { Text(stringResource(R.string.customers_phone)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1.2f), shape = RoundedCornerShape(14.dp),
                        )
                        AmountField(r.amount, { r.amount = it; r.error = null }, stringResource(R.string.customers_amount), Modifier.weight(1f))
                    }
                    r.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
            item("add") {
                OutlinedButton(colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { rows.add(QuickRow(nextId++)) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.customers_quick_add_row))
                }
            }
            item("total") {
                val total = rows.sumOf { Money.parse(it.amount)?.takeIf { a -> a > 0 } ?: 0.0 }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.customers_total_debt), modifier = Modifier.weight(1f))
                    Text(Money.format(total), fontWeight = FontWeight.ExtraBold)
                }
                BigButton(stringResource(R.string.common_save), onClick = { save() }, enabled = !saving)
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}
