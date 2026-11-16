package uz.kassiron.app.ui.expenses

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.ExpenseException
import uz.kassiron.app.domain.ExpenseService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.AmountField
import uz.kassiron.app.ui.common.BigButton
import uz.kassiron.app.ui.common.ConfirmDialog
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.reports.BoCard
import uz.kassiron.app.ui.reports.BoScaffold
import uz.kassiron.app.ui.reports.DateRangeButton
import uz.kassiron.app.ui.reports.NoAccess
import uz.kassiron.app.ui.reports.SingleDateDialog
import uz.kassiron.app.ui.theme.Brand

private data class ExpenseItem(val uuid: String, val amount: Double, val category: String?, val description: String?, val date: String)
private data class ExpensesData(val items: List<ExpenseItem>, val total: Double, val byCategory: List<Pair<String?, Double>>)

@Composable
fun ExpensesScreen(nav: NavHostController) {
    if (!App.session.can("expenses")) { NoAccess(); return }
    val today = Time.today()
    var from by rememberSaveable { mutableStateOf(today.substring(0, 8) + "01") }
    var to by rememberSaveable { mutableStateOf(today) }
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
            ).map { it.strOrNull("category_name") to it.dbl("total") },
        )
    }

    BoScaffold(
        title = stringResource(R.string.expenses_title),
        onBack = if (nav.previousBackStackEntry != null) ({ nav.popBackStack(); Unit }) else null,
        fab = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.expenseEdit("new")) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.expenses_new)) },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item("range") { DateRangeButton(from, to, { f, t -> from = f; to = t }, Modifier.fillMaxWidth()) }
            val d = data
            if (d != null) {
                item("total") {
                    BoCard {
                        Text(stringResource(R.string.expenses_total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(d.total), style = MaterialTheme.typography.headlineMedium, color = Brand.Danger)
                        if (d.byCategory.size > 1 || d.byCategory.any { it.first != null }) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                d.byCategory.forEach { (name, total) ->
                                    Column {
                                        Text(name ?: stringResource(R.string.expenses_no_category), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(Money.format(total), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                if (d.items.isEmpty()) item("empty") { EmptyState(Icons.Filled.Wallet, stringResource(R.string.expenses_empty), Modifier.height(240.dp)) }
                items(d.items, key = { it.uuid }) { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { nav.navigate(Routes.expenseEdit(e.uuid)) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.category ?: stringResource(R.string.expenses_no_category), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(Time.showDate(e.date), e.description).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(Money.format(e.amount), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

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
    var description by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(Time.today()) }
    var pickDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
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

    BoScaffold(
        title = stringResource(if (isNew) R.string.expenses_new else R.string.expenses_edit),
        onBack = { nav.popBackStack() },
        actions = {
            if (!isNew && existing.second != null) IconButton(onClick = { askDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete))
            }
        },
    ) { pad ->
        if (!isNew && existing.first && existing.second == null) {
            EmptyState(Icons.Filled.SearchOff, stringResource(R.string.expenses_not_found), Modifier.padding(pad))
            return@BoScaffold
        }
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AmountField(amount, { amount = it; error = null }, stringResource(R.string.expenses_amount) + " *")
            OutlinedTextField(
                category, { category = it }, label = { Text(stringResource(R.string.expenses_category)) },
                supportingText = { Text(stringResource(R.string.expenses_category_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
            )
            if (categories.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c ->
                        FilterChip(
                            selected = c.equals(category.trim(), ignoreCase = true),
                            onClick = { category = if (c.equals(category.trim(), ignoreCase = true)) "" else c },
                            label = { Text(c) },
                        )
                    }
                }
            }
            OutlinedTextField(
                description, { description = it }, label = { Text(stringResource(R.string.expenses_description)) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), minLines = 2,
            )
            OutlinedButton(onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.CalendarMonth, null); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.expenses_date) + ": " + Time.showDate(date))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            BigButton(stringResource(R.string.common_save), enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    val err = io<Int?> {
                        try {
                            ExpenseService.save(if (isNew) null else uuid, Money.parse(amount), category, description, date, App.session.current().uuid)
                            App.sync.requestSync()
                            null
                        } catch (e: ExpenseException) {
                            when (e.code) {
                                "amount_too_large" -> R.string.expenses_err_too_large
                                "expense_date_invalid" -> R.string.expenses_err_date
                                "expense_date_future" -> R.string.expenses_err_future
                                else -> R.string.expenses_err_positive
                            }
                        }
                    }
                    saving = false
                    if (err == null) {
                        Toast.makeText(context, R.string.expenses_saved, Toast.LENGTH_SHORT).show()
                        nav.popBackStack()
                    } else error = context.getString(err)
                }
            })
        }
    }
    if (pickDate) SingleDateDialog(date, allowFuture = false, onDismiss = { pickDate = false }) { date = it; pickDate = false }
    if (askDelete) ConfirmDialog(
        title = stringResource(R.string.expenses_delete_confirm),
        text = null,
        confirm = stringResource(R.string.common_delete),
        dismiss = stringResource(R.string.common_cancel),
        onDismiss = { askDelete = false },
        onConfirm = {
            askDelete = false
            scope.launch {
                io { ExpenseService.delete(uuid); App.sync.requestSync() }
                Toast.makeText(context, R.string.expenses_deleted, Toast.LENGTH_SHORT).show()
                nav.popBackStack()
            }
        },
    )
}
