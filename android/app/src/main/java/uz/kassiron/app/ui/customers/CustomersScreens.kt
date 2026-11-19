package uz.kassiron.app.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAlert
import uz.kassiron.app.ui.kit.KAmountField
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KChips
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KFab
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSearch
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KToneIcon
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.kit.KValueChip
import uz.kassiron.app.ui.reports.EndColumn
import uz.kassiron.app.ui.reports.MutedNote
import uz.kassiron.app.ui.reports.NoAccess
import uz.kassiron.app.ui.reports.PickerField
import uz.kassiron.app.ui.reports.SingleDateDialog
import uz.kassiron.app.ui.reports.formatPhone
import uz.kassiron.app.ui.reports.receiptLabel
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Onest
import uz.kassiron.app.ui.theme.Tone
import uz.kassiron.app.core.Ids

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

// ---------------------------------------------------------------- list (mockup 22)

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

    KScreen(
        topBar = {
            KTopBar(stringResource(R.string.cust_title)) {
                KIconButton(Icons.AutoMirrored.Outlined.PlaylistAdd, { nav.navigate(Routes.QUICK_DEBTS) }, contentDescription = stringResource(R.string.cust_quick_title))
            }
        },
        fab = { KFab(Icons.Outlined.Add, { nav.navigate(Routes.customerEdit("new")) }, stringResource(R.string.cust_new)) },
    ) {
        KCard(Modifier.padding(horizontal = Edge), tone = KCardTone.Red, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.cust_total, totals.second.toInt()), style = K.t.small, color = K.c.mu)
            KMoney(totals.first, size = 24.sp, color = K.c.red)
        }
        Spacer(Modifier.height(10.dp))
        KSearch(query, { query = it }, stringResource(R.string.cust_search))
        KChips(
            listOf(stringResource(R.string.cust_filter_debtors), stringResource(R.string.cust_filter_all)),
            if (onlyDebtors) 0 else 1, { onlyDebtors = it == 0 }, Modifier.padding(top = 10.dp),
        )
        val items = list
        when {
            items == null -> Unit
            items.isEmpty() -> KEmpty(
                Icons.Outlined.People,
                stringResource(if (query.isNotBlank()) R.string.cust_empty_search else if (onlyDebtors) R.string.cust_empty_debtors else R.string.cust_empty),
                stringResource(if (query.isNotBlank()) R.string.cust_empty_search_hint else if (onlyDebtors) R.string.cust_empty_debtors_hint else R.string.cust_empty_hint),
            )
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = Edge, end = Edge, top = 10.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.uuid }) { c -> CustomerRow(c) { nav.navigate(Routes.customer(c.uuid)) } }
            }
        }
    }
}

@Composable
private fun CustomerRow(c: CustomerItem, onClick: () -> Unit) {
    KRow(
        c.name,
        leading = { KAvatar(c.name) },
        subtitle = {
            if (c.phone.isNotBlank()) KSub(formatPhone(c.phone))
            val limit = c.limitExceeded()
            val late = overdue(c.dueDate, c.balance)
            if (limit || late) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (limit) KTag(stringResource(R.string.cust_limit_over), KTagKind.Red)
                if (late) KTag(stringResource(R.string.cust_overdue), KTagKind.Low)
            }
        },
        trailing = {
            when {
                c.balance > 0 -> KMoney(c.balance, color = K.c.red)
                c.balance < 0 -> EndColumn { KMoney(-c.balance, color = K.c.grn); KTag(stringResource(R.string.cust_overpaid), KTagKind.Green) }
                else -> KTag(stringResource(R.string.cust_no_debt))
            }
        },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------- detail (mockup 23)

private data class LedgerEntry(
    val uuid: String,
    val type: String,
    val amount: Double,
    val running: Double,
    val createdAt: String,
    val saleUuid: String?,
    val receipt: String?,
)

private data class CustomerDetail(val customer: Row, val balance: Double, val ledger: List<LedgerEntry>)

private fun loadDetail(uuid: String): CustomerDetail? {
    val db = App.db
    val c = db.queryOne("SELECT * FROM customers WHERE uuid = ?", uuid) ?: return null
    // DebtTransaction::historyByCustomer: running balance in time order, newest first.
    val rows = db.query(
        """SELECT d.uuid, d.type, d.amount, d.created_at, d.sale_id, s.receipt_no
           FROM debt_transactions d LEFT JOIN sales s ON s.uuid = d.sale_id
           WHERE d.customer_id = ? ORDER BY d.created_at ASC, d.rowid ASC""",
        uuid,
    )
    var running = 0.0
    val ledger = rows.map { r ->
        val amount = r.dbl("amount")
        running = Money.round2(running + if (r.str("type") == Debts.QARZ) amount else -amount)
        val sale = r.strOrNull("sale_id")?.takeIf { it.isNotBlank() }
        LedgerEntry(r.str("uuid"), r.str("type"), amount, running, r.str("created_at"), sale, sale?.let { receiptLabel(r.strOrNull("receipt_no"), it) })
    }.reversed()
    return CustomerDetail(c, Debts.balance(db, uuid), ledger)
}

@Composable
fun CustomerDetailScreen(nav: NavHostController, uuid: String) {
    if (!App.session.can("customers")) { NoAccess(); return }
    val loaded by dbState<Pair<Boolean, CustomerDetail?>>(false to null, uuid) { true to loadDetail(uuid) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<String?>(null) } // "pay" | "debt"
    var busy by remember { mutableStateOf(false) }

    val detail = loaded.second
    KScreen(
        topBar = {
            KTopBar(detail?.customer?.str("full_name") ?: stringResource(R.string.cust_title), onBack = { nav.popBackStack() }) {
                if (detail != null) KIconButton(Icons.Outlined.Edit, { nav.navigate(Routes.customerEdit(uuid)) }, contentDescription = stringResource(R.string.cust_edit))
            }
        },
        snackbar = snackbar,
    ) {
        if (!loaded.first) return@KScreen
        if (detail == null) {
            KEmpty(Icons.Outlined.PersonOff, stringResource(R.string.cust_not_found), modifier = Modifier.padding(top = 40.dp))
            return@KScreen
        }
        val c = detail.customer
        val phone = c.str("phone")
        val balance = detail.balance
        val creditLimit = c.dblOrNull("credit_limit")
        val due = c.strOrNull("debt_due_date")?.takeIf { it.isNotBlank() }
        val shopName = App.kv.shop().optString("name").ifBlank { stringResource(R.string.app_name) }
        val reminder = if (due != null) stringResource(R.string.cust_sms_due, shopName, c.str("full_name"), Money.format(balance), Time.showDate(due))
        else stringResource(R.string.cust_sms, shopName, c.str("full_name"), Money.format(balance))

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp)) {
            item("head") {
                Column(Modifier.fillMaxWidth().padding(horizontal = Edge), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(if (balance < 0) R.string.cust_overpaid else R.string.cust_debt_label),
                        style = K.t.small, color = K.c.mu,
                    )
                    KMoney(kotlin.math.abs(balance), size = 32.sp, color = if (balance > 0) K.c.red else if (balance < 0) K.c.grn else K.c.mu)
                    val info = listOfNotNull(
                        due?.let { stringResource(R.string.cust_due_short, Time.showDate(it)) },
                        creditLimit?.let { stringResource(R.string.cust_limit_short, Money.format(it)) },
                    )
                    if (info.isNotEmpty()) Text(
                        info.joinToString(" · "), style = K.t.small, textAlign = TextAlign.Center,
                        color = if (overdue(due, balance) || (creditLimit != null && balance > creditLimit + 0.001)) K.c.red else K.c.mu,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            item("actions") {
                Column(Modifier.padding(start = Edge, end = Edge, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (phone.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KButton(
                            stringResource(R.string.cust_call), { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } },
                            Modifier.weight(1f), kind = KButtonKind.Alt, icon = Icons.Outlined.Call, height = 46.dp, fontSize = 14.sp,
                        )
                        KButton(
                            stringResource(R.string.cust_sms_btn), {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone"))
                                    if (balance > 0) intent.putExtra("sms_body", reminder)
                                    context.startActivity(intent)
                                }
                            },
                            Modifier.weight(1f), kind = KButtonKind.Alt, icon = Icons.Outlined.Sms, height = 46.dp, fontSize = 14.sp,
                        )
                    }
                    KButton(stringResource(R.string.cust_receive), { sheet = "pay" }, Modifier.fillMaxWidth(), height = 50.dp, enabled = balance > 0)
                }
            }
            item("hist") { KSection(stringResource(R.string.cust_history), stringResource(R.string.cust_add_debt), { sheet = "debt" }) }
            if (detail.ledger.isEmpty()) item("hist-empty") { MutedNote(stringResource(R.string.cust_history_empty)) }
            items(detail.ledger, key = { "l" + it.uuid }) { e ->
                LedgerRow(e, Modifier.padding(horizontal = Edge, vertical = 4.dp)) { s -> nav.navigate(Routes.sale(s)) }
            }
        }

        val mode = sheet
        if (mode != null) KSheet(onDismiss = { sheet = null }) {
            AmountSheetContent(
                debt = mode == "debt", balance = balance, creditLimit = creditLimit, busy = busy,
                onClose = { sheet = null },
            ) { amount ->
                busy = true
                scope.launch {
                    val user = App.session.current().uuid
                    val err = io { if (mode == "pay") CustomerActions.recordPayment(uuid, amount, user) else CustomerActions.addDebt(uuid, amount, user) }
                    busy = false
                    if (err == null) {
                        sheet = null
                        snackbar.showSnackbar(context.getString(if (mode == "pay") R.string.cust_paid_done else R.string.cust_debt_done))
                    } else snackbar.showSnackbar(context.getString(err))
                }
            }
        }
    }
}

@Composable
private fun LedgerRow(e: LedgerEntry, modifier: Modifier, openSale: (String) -> Unit) {
    val up = e.type == Debts.QARZ
    val title = when {
        up && e.receipt != null -> stringResource(R.string.cust_led_sale, e.receipt)
        up -> stringResource(R.string.cust_led_old)
        e.type == Debts.TOLOV -> stringResource(R.string.cust_led_paid)
        else -> stringResource(R.string.cust_led_refund)
    }
    val date = Time.local(e.createdAt, if (Time.local(e.createdAt, "yyyy") == Time.today().take(4)) "dd.MM" else "dd.MM.yyyy")
    KRow(
        title, modifier,
        leading = {
            if (up) KToneIcon(Icons.Outlined.Add, Tone(K.c.red, K.c.redBg), 34.dp)
            else KToneIcon(Icons.Outlined.Remove, Tone(K.c.grn, K.c.grnBg), 34.dp)
        },
        subtitle = { KSub(stringResource(R.string.cust_led_sub, date, Money.format(e.running, withCurrency = false))) },
        trailing = {
            Text(
                (if (up) "+" else "−") + Money.format(e.amount, withCurrency = false),
                style = K.t.amount.copy(fontSize = 15.sp), color = if (up) K.c.red else K.c.grn, maxLines = 1,
            )
        },
        onClick = e.saleUuid?.let { s -> { openSale(s) } },
    )
}

/**
 * The payment sheet (mockup 24) and the "add old debt" sheet: big amount, quick
 * chips, what is left, one button. Internal so the screenshot test can show it.
 */
@Composable
internal fun AmountSheetContent(debt: Boolean, balance: Double, creditLimit: Double?, busy: Boolean, onClose: () -> Unit, initial: String = "", onSubmit: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val amount = Money.parse(text)?.let { Money.round2(it) }
    KSheetTitle(stringResource(if (debt) R.string.cust_add_debt else R.string.cust_receive), onClose)
    KAmountField(text, { text = it }, label = stringResource(R.string.cust_amount), big = true)
    if (!debt && balance > 0) {
        val half = Money.round2(kotlin.math.ceil(balance / 2 / 1000) * 1000).coerceAtMost(balance)
        val fifth = Money.round2(kotlin.math.round(balance / 5 / 1000) * 1000)
        val chips = buildList {
            add(stringResource(R.string.cust_all) to balance)
            add(stringResource(R.string.cust_half) to half)
            if (fifth >= 1000 && fifth < half) add(Money.format(fifth, withCurrency = false) to fifth)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { (label, v) ->
                KValueChip(label, amount == v, { text = Money.format(v, withCurrency = false) }, Modifier.weight(1f))
            }
        }
    }
    val a = amount ?: 0.0
    val (label, value, bad) = when {
        !debt && a > balance + 0.001 -> Triple(stringResource(R.string.cust_too_much), balance, true)
        !debt -> Triple(stringResource(R.string.cust_left), Money.round2(balance - a), false)
        creditLimit != null && balance + a > creditLimit + 0.001 -> Triple(stringResource(R.string.cust_over_limit, Money.format(creditLimit)), Money.round2(balance + a), true)
        else -> Triple(stringResource(R.string.cust_new_total), Money.round2(balance + a), false)
    }
    Row(
        Modifier.padding(top = 12.dp, bottom = 10.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp))
            .background(if (bad) K.c.redBg else K.c.tint).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = K.t.title.copy(fontSize = 14.sp), color = K.c.tx, modifier = Modifier.weight(1f))
        Text(Money.format(value), style = K.t.amount.copy(fontFamily = Onest, fontSize = 19.sp), color = if (bad) K.c.red else K.c.ink, maxLines = 1)
    }
    KButton(
        stringResource(if (debt) R.string.cust_add else R.string.cust_accept), { amount?.let(onSubmit) }, Modifier.fillMaxWidth(),
        height = 54.dp, enabled = !busy && amount != null && amount > 0 && (debt || amount <= balance + 0.001),
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
    var errField by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val draftUuid = rememberSaveable { Ids.new() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(existing.first) {
        if (initialized || !existing.first) return@LaunchedEffect
        existing.second?.let { c ->
            name = c.str("full_name"); phone = formatPhone(c.str("phone")); note = c.str("note")
            limit = c.dblOrNull("credit_limit")?.let { Money.format(it, withCurrency = false) } ?: ""
            due = c.strOrNull("debt_due_date")?.takeIf { it.isNotBlank() }
        }
        initialized = true
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val r = io { CustomerActions.save(if (isNew) null else uuid, name, phone, note, limit, due, draftUuid) }
            when (r) {
                is CustomerActions.SaveResult.Ok -> {
                    // The button stays off: the screen is closing, and its fade-out still takes taps.
                    nav.popBackStack()
                    if (isNew) nav.navigate(Routes.customer(r.uuid))
                }
                is CustomerActions.SaveResult.Error -> {
                    saving = false
                    error = if (r.arg != null) context.getString(r.res, r.arg) else context.getString(r.res)
                    errField = when (r.res) {
                        R.string.cust_err_name -> "name"
                        R.string.cust_err_phone, R.string.cust_err_phone_taken -> "phone"
                        R.string.cust_err_limit -> "limit"
                        else -> null
                    }
                }
            }
        }
    }

    val missing = !isNew && existing.first && existing.second == null
    KScreen(
        topBar = { KTopBar(stringResource(if (isNew) R.string.cust_new else R.string.cust_edit_title), onBack = { nav.popBackStack() }) },
        bottomBar = if (missing) null else ({ KBottomBar { KButton(stringResource(R.string.bo_save), { save() }, Modifier.fillMaxWidth(), enabled = !saving) } }),
    ) {
        if (missing) {
            KEmpty(Icons.Outlined.PersonOff, stringResource(R.string.cust_not_found), modifier = Modifier.padding(top = 40.dp))
            return@KScreen
        }
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(start = Edge, end = Edge, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KField(
                name, { name = it; if (errField == "name") errField = null },
                label = stringResource(R.string.cust_name), placeholder = stringResource(R.string.cust_name_ph),
                error = error.takeIf { errField == "name" },
            )
            KField(
                phone, { phone = it; if (errField == "phone") errField = null },
                label = stringResource(R.string.cust_phone), placeholder = "+998 90 123 45 67", keyboard = KeyboardType.Phone,
                error = error.takeIf { errField == "phone" },
            )
            KField(note, { note = it }, label = stringResource(R.string.cust_note), placeholder = stringResource(R.string.cust_note_ph), singleLine = false)
            KAmountField(
                limit, { limit = it; if (errField == "limit") errField = null },
                label = stringResource(R.string.cust_limit), placeholder = stringResource(R.string.cust_limit_ph),
                error = error.takeIf { errField == "limit" },
            )
            PickerField(
                stringResource(R.string.cust_due),
                due?.let { Time.showDate(it) } ?: stringResource(R.string.cust_due_none),
                { pickDate = true }, placeholder = due == null, onClear = if (due != null) ({ due = null }) else null,
            )
            if (error != null && errField == null) Text(error!!, style = K.t.small.copy(fontSize = 13.sp), color = K.c.red)
        }
    }
    if (pickDate) SingleDateDialog(due, allowFuture = true, onDismiss = { pickDate = false }) { due = it; pickDate = false }
}

// ---------------------------------------------------------------- quick old debts (mockup 25)

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
    var nextId by remember { mutableStateOf(5L) }
    val rows = remember { mutableStateListOf(QuickRow(0), QuickRow(1), QuickRow(2), QuickRow(3), QuickRow(4)) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    val filled = rows.count { !it.blank }

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
                name.isEmpty() -> R.string.cust_err_name
                name.length > 200 || looksLikeFormula(name) -> R.string.cust_quick_err_name
                phone == null -> R.string.cust_quick_err_phone
                r.amount.isBlank() -> R.string.cust_quick_err_amount
                amount == null || amount <= 0 -> R.string.cust_err_amount
                amount > ExpenseService.MONEY_MAX -> R.string.cust_err_too_large
                else -> null
            }
            if (r.error == null) valid += Valid(r, name, phone!!.ifEmpty { null }, Money.round2(amount!!))
        }
        if (valid.isEmpty()) {
            if (rows.none { it.error != null }) scope.launch { snackbar.showSnackbar(context.getString(R.string.cust_quick_nothing)) }
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
                            v.row.error = R.string.cust_quick_failed
                        }
                    }
                }
                if (ok.isNotEmpty()) App.sync.requestSync()
                ok
            }
            saving = false
            rows.removeAll(saved.map { it.row }.toSet())
            while (rows.size < 5) rows.add(QuickRow(nextId++))
            if (saved.isNotEmpty()) snackbar.showSnackbar(context.getString(R.string.cust_quick_saved, saved.size, Money.format(saved.sumOf { it.amount })))
        }
    }

    KScreen(
        topBar = {
            KTopBar(stringResource(R.string.cust_quick_title), onBack = { nav.popBackStack() }) {
                if (filled > 0) KTag(stringResource(R.string.cust_quick_rows, filled), KTagKind.Ok)
            }
        },
        bottomBar = {
            KBottomBar {
                KButton(
                    if (filled > 0) stringResource(R.string.cust_quick_save, filled) else stringResource(R.string.bo_save),
                    { save() }, Modifier.fillMaxWidth(), enabled = filled > 0 && !saving,
                )
            }
        },
        snackbar = snackbar,
    ) {
        LazyColumn(Modifier.weight(1f).imePadding(), contentPadding = PaddingValues(top = 2.dp, bottom = 16.dp)) {
            item("head") {
                Row(Modifier.padding(start = Edge, end = Edge, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GridHead(stringResource(R.string.cust_quick_name), Modifier.weight(1f))
                    GridHead(stringResource(R.string.cust_quick_phone), Modifier.weight(1.35f))
                    GridHead(stringResource(R.string.cust_quick_sum), Modifier.weight(0.85f))
                }
            }
            items(rows, key = { it.id }) { r ->
                val bad = r.error != null
                Row(Modifier.padding(start = Edge, end = Edge, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GridCell(r.name, { r.name = it; r.error = null }, stringResource(R.string.cust_quick_name), Modifier.weight(1f), bad && r.error in NAME_ERRORS)
                    GridCell(r.phone, { r.phone = it; r.error = null }, stringResource(R.string.cust_quick_phone_ph), Modifier.weight(1.35f), bad && r.error == R.string.cust_quick_err_phone, KeyboardType.Phone)
                    GridCell(r.amount, { v -> r.amount = v.filter { it.isDigit() || it == ' ' || it == ',' || it == '.' }; r.error = null }, "0", Modifier.weight(0.85f), bad && r.error in AMOUNT_ERRORS, KeyboardType.Number, last = true)
                }
            }
            val errors = rows.filter { it.error != null }
            if (errors.isNotEmpty()) item("errors") {
                val first = errors.first()
                val who = first.name.trim().ifEmpty { stringResource(R.string.cust_quick_row_n, rows.indexOf(first) + 1) }
                KAlert(
                    stringResource(R.string.cust_quick_err_line, who, stringResource(first.error!!)) +
                        if (errors.size > 1) " " + stringResource(R.string.cust_quick_err_more, errors.size - 1) else "",
                    Icons.Outlined.ErrorOutline, Modifier.padding(top = 4.dp, bottom = 4.dp), tone = KCardTone.Red,
                )
            }
            item("add") {
                KButton(
                    stringResource(R.string.cust_quick_add10), { repeat(10) { rows.add(QuickRow(nextId++)) } },
                    Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 6.dp), kind = KButtonKind.Alt, icon = Icons.Outlined.Add, height = 44.dp, fontSize = 14.sp,
                )
            }
        }
    }
}

private val NAME_ERRORS = setOf(R.string.cust_err_name, R.string.cust_quick_err_name, R.string.cust_quick_failed)
private val AMOUNT_ERRORS = setOf(R.string.cust_quick_err_amount, R.string.cust_err_amount, R.string.cust_err_too_large)

@Composable
private fun GridHead(text: String, modifier: Modifier) {
    Text(text, style = K.t.label.copy(fontSize = 11.5.sp), color = K.c.mu, modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp), maxLines = 1)
}

/** One cell of the quick-entry grid: 44dp, 11dp radius, hairline (red when wrong). */
@Composable
private fun GridCell(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier,
    error: Boolean,
    keyboard: KeyboardType = KeyboardType.Text,
    last: Boolean = false,
) {
    val shape = RoundedCornerShape(11.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = K.t.body.copy(fontSize = 13.5.sp, color = K.c.tx),
        cursorBrush = SolidColor(K.c.ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(shape).background(K.c.s1)
                    .border(1.dp, if (error) K.c.red else K.c.bd, shape).padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = K.t.body.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Medium), color = K.c.mu, maxLines = 1)
                inner()
            }
        },
    )
}
