package uz.kassiron.app.ui.expenses

import android.widget.Toast
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.ExpenseException
import uz.kassiron.app.domain.ExpenseService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAmountField
import uz.kassiron.app.ui.kit.KBar
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KFab
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KToneIcon
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.kit.KValueChip
import uz.kassiron.app.ui.reports.DateRangeDialog
import uz.kassiron.app.ui.reports.NoAccess
import uz.kassiron.app.ui.reports.PickerField
import uz.kassiron.app.ui.reports.SingleDateDialog
import uz.kassiron.app.ui.reports.rangeLabel
import uz.kassiron.app.ui.reports.shortDate
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Tone
import uz.kassiron.app.core.Ids

private data class ExpenseItem(val uuid: String, val amount: Double, val category: String?, val description: String?, val date: String)
private data class ExpensesData(val items: List<ExpenseItem>, val total: Double, val byCategory: List<Pair<String?, Double>>)

// ---------------------------------------------------------------- list (mockup 26)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(nav: NavHostController) {
    if (!App.session.can("expenses")) { NoAccess(); return }
    val today = Time.today()
    var from by rememberSaveable { mutableStateOf(today.substring(0, 8) + "01") }
    var to by rememberSaveable { mutableStateOf(today) }
    var pick by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf<ExpenseItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val data by dbState<ExpensesData?>(null, from, to) {
        val db = App.db
        ExpensesData(
            items = db.query(
                """SELECT e.uuid, e.amount, e.description, e.expense_date, c.name AS category_name
                   FROM expenses e LEFT JOIN categories c ON c.uuid = e.category_id
                   WHERE e.expense_date BETWEEN ? AND ? ORDER BY e.expense_date DESC, e.created_at DESC""",
                from, to,
            ).map {
                ExpenseItem(it.str("uuid"), it.dbl("amount"), it.strOrNull("category_name")?.takeIf { n -> n.isNotBlank() },
                    it.strOrNull("description")?.takeIf { d -> d.isNotBlank() }, it.str("expense_date"))
            },
            total = Money.round2(db.scalarDouble("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE expense_date BETWEEN ? AND ?", from, to)),
            byCategory = db.query(
                """SELECT c.name AS category_name, COALESCE(SUM(e.amount), 0) AS total FROM expenses e
                   LEFT JOIN categories c ON c.uuid = e.category_id WHERE e.expense_date BETWEEN ? AND ?
                   GROUP BY e.category_id ORDER BY total DESC""",
                from, to,
            ).map { it.strOrNull("category_name")?.takeIf { n -> n.isNotBlank() } to it.dbl("total") },
        )
    }
    val months = stringArrayResource(R.array.exp_months)
    val period = if (from.endsWith("-01") && from.take(7) == to.take(7) && to >= from) months[from.substring(5, 7).toInt() - 1] else rangeLabel(from, to)

    KScreen(
        topBar = {
            KTopBar(stringResource(R.string.exp_title), onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack(); Unit }) else null) {
                KIconButton(Icons.Outlined.CalendarMonth, { pick = true }, contentDescription = stringResource(R.string.bo_pick_range))
            }
        },
        fab = { KFab(Icons.Outlined.Add, { nav.navigate(Routes.expenseEdit("new")) }, stringResource(R.string.exp_new)) },
    ) {
        val d = data ?: return@KScreen
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 2.dp, bottom = 88.dp)) {
            item("total") {
                KCard(Modifier.padding(horizontal = Edge)) {
                    Text(stringResource(R.string.exp_total, period), style = K.t.small, color = K.c.mu)
                    KMoney(d.total, size = 24.sp)
                    if (d.byCategory.isNotEmpty()) {
                        val other = stringResource(R.string.exp_other)
                        val none = stringResource(R.string.exp_no_category)
                        val bars = if (d.byCategory.size <= 4) d.byCategory.map { (it.first ?: none) to it.second }
                        else d.byCategory.take(3).map { (it.first ?: none) to it.second } + (other to d.byCategory.drop(3).sumOf { it.second })
                        val max = bars.maxOf { it.second }.coerceAtLeast(1.0)
                        Column(Modifier.padding(top = 8.dp)) {
                            bars.forEach { (name, v) -> KBar(name, (v / max).toFloat(), v) }
                        }
                    }
                }
            }
            if (d.items.isEmpty()) item("empty") {
                KEmpty(Icons.Outlined.AccountBalanceWallet, stringResource(R.string.exp_empty), stringResource(R.string.exp_empty_hint))
            }
            items(d.items, key = { it.uuid }) { e ->
                val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                    if (v == SwipeToDismissBoxValue.EndToStart) askDelete = e
                    false
                })
                SwipeToDismissBox(
                    state,
                    modifier = Modifier.padding(start = Edge, end = Edge, top = 8.dp),
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(K.c.redBg).padding(end = 20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Outlined.DeleteOutline, null, tint = K.c.red, modifier = Modifier.size(22.dp)) }
                    },
                ) {
                    KRow(
                        e.category ?: stringResource(R.string.exp_no_category),
                        leading = { KToneIcon(Icons.Outlined.AccountBalanceWallet, Tone(K.c.mu, K.c.s2), 34.dp) },
                        subtitle = { KSub(listOfNotNull(shortDate(e.date), e.description).joinToString(" · ")) },
                        trailing = { KMoney(e.amount) },
                        onClick = { nav.navigate(Routes.expenseEdit(e.uuid)) },
                        onLongClick = { askDelete = e },
                    )
                }
            }
        }
    }
    if (pick) DateRangeDialog(from, to, onDismiss = { pick = false }) { f, t -> from = f; to = t; pick = false }
    askDelete?.let { e ->
        KConfirm(
            title = stringResource(R.string.exp_delete_confirm),
            text = stringResource(R.string.exp_delete_text, (e.category ?: stringResource(R.string.exp_no_category)), Money.format(e.amount)),
            confirm = stringResource(R.string.bo_delete),
            dismiss = stringResource(R.string.bo_cancel),
            danger = true,
            onDismiss = { askDelete = null },
            onConfirm = {
                askDelete = null
                scope.launch {
                    io { ExpenseService.delete(e.uuid); App.sync.requestSync() }
                    Toast.makeText(context, R.string.exp_deleted, Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

// ---------------------------------------------------------------- add / edit (mockup 27)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(nav: NavHostController, uuid: String) {
    if (!App.session.can("expenses")) { NoAccess(); return }
    val isNew = uuid == "new" || uuid.isBlank()
    val existing by dbState<Pair<Boolean, Row?>>(false to null, uuid) {
        true to (if (isNew) null else App.db.queryOne(
            "SELECT e.*, c.name AS category_name FROM expenses e LEFT JOIN categories c ON c.uuid = e.category_id WHERE e.uuid = ?", uuid,
        ))
    }
    val categories by dbState(emptyList<String>()) { ExpenseService.categories(App.db) }
    var initialized by rememberSaveable { mutableStateOf(isNew) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf(false) }
    var description by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(Time.today()) }
    var pickDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Pair<String, String>?>(null) } // field to message
    var saving by remember { mutableStateOf(false) }
    val draftUuid = rememberSaveable { Ids.new() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(existing.first) {
        if (initialized || !existing.first) return@LaunchedEffect
        existing.second?.let { e ->
            amount = Money.format(e.dbl("amount"), withCurrency = false)
            category = e.str("category_name")
            description = e.str("description")
            date = e.str("expense_date").ifBlank { Time.today() }
        }
        initialized = true
    }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            val err = io<Pair<String, Int>?> {
                try {
                    // A new expense keeps the id picked when the form opened: saving twice stays one expense.
                    ExpenseService.save(if (isNew) draftUuid else uuid, Money.parse(amount), category, description, date, App.session.current().uuid)
                    App.sync.requestSync()
                    null
                } catch (e: ExpenseException) {
                    when (e.code) {
                        "amount_too_large" -> "amount" to R.string.exp_err_too_large
                        "expense_date_invalid" -> "date" to R.string.exp_err_date
                        "expense_date_future" -> "date" to R.string.exp_err_future
                        else -> "amount" to R.string.exp_err_amount
                    }
                }
            }
            if (err == null) {
                // The button stays off: the screen is closing, and its fade-out still takes taps.
                Toast.makeText(context, R.string.exp_saved, Toast.LENGTH_SHORT).show()
                nav.popBackStack()
            } else {
                saving = false
                error = err.first to context.getString(err.second)
            }
        }
    }

    val missing = !isNew && existing.first && existing.second == null
    KScreen(
        topBar = {
            KTopBar(stringResource(if (isNew) R.string.exp_new else R.string.exp_edit), onBack = { nav.popBackStack() }) {
                if (!isNew && existing.second != null) KIconButton(Icons.Outlined.DeleteOutline, { askDelete = true }, contentDescription = stringResource(R.string.bo_delete), tint = K.c.red)
            }
        },
        bottomBar = if (missing) null else ({ KBottomBar { KButton(stringResource(R.string.bo_save), { save() }, Modifier.fillMaxWidth(), enabled = !saving) } }),
    ) {
        if (missing) {
            KEmpty(Icons.Outlined.SearchOff, stringResource(R.string.exp_not_found), modifier = Modifier.padding(top = 40.dp))
            return@KScreen
        }
        Column(
            Modifier.weight(1f).imePadding().verticalScroll(rememberScrollState()).padding(start = Edge, end = Edge, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KAmountField(
                amount, { amount = it; if (error?.first == "amount") error = null },
                label = stringResource(R.string.exp_amount), big = true,
                error = error?.takeIf { it.first == "amount" }?.second,
            )
            Column {
                Text(stringResource(R.string.exp_category), style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c ->
                        val on = !newCategory && c.equals(category.trim(), ignoreCase = true)
                        KValueChip(c, on, {
                            newCategory = false
                            category = if (on) "" else c
                        }, Modifier.widthIn(min = 76.dp))
                    }
                    KValueChip(stringResource(R.string.exp_new_category), newCategory, {
                        newCategory = !newCategory
                        if (newCategory) category = ""
                    }, Modifier.widthIn(min = 76.dp))
                }
                if (newCategory) {
                    KField(
                        category, { category = it }, Modifier.padding(top = 8.dp),
                        placeholder = stringResource(R.string.exp_new_category_ph),
                    )
                }
            }
            KField(description, { description = it }, label = stringResource(R.string.exp_note), placeholder = stringResource(R.string.exp_note_ph), singleLine = false)
            Column {
                PickerField(stringResource(R.string.exp_date), Time.showDate(date), { pickDate = true })
                error?.takeIf { it.first == "date" }?.let { Text(it.second, style = K.t.small, color = K.c.red, modifier = Modifier.padding(top = 5.dp)) }
            }
        }
    }
    if (pickDate) SingleDateDialog(date, allowFuture = false, onDismiss = { pickDate = false }) { date = it; pickDate = false; if (error?.first == "date") error = null }
    if (askDelete) KConfirm(
        title = stringResource(R.string.exp_delete_confirm),
        text = stringResource(R.string.exp_delete_text_short),
        confirm = stringResource(R.string.bo_delete),
        dismiss = stringResource(R.string.bo_cancel),
        danger = true,
        onDismiss = { askDelete = false },
        onConfirm = {
            askDelete = false
            scope.launch {
                io { ExpenseService.delete(uuid); App.sync.requestSync() }
                Toast.makeText(context, R.string.exp_deleted, Toast.LENGTH_SHORT).show()
                nav.popBackStack()
            }
        },
    )
}
