package uz.kassiron.app.ui.products

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.PurchaseLine
import uz.kassiron.app.domain.PurchaseService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.AppTopBar
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.SearchField
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io

// ====================================================================
// Purchases list
// ====================================================================

private sealed interface PurchaseListEntry {
    val key: String
    data class Day(val date: String, val total: Double, val count: Int) : PurchaseListEntry { override val key get() = "d$date" }
    data class Item(val row: DbRow) : PurchaseListEntry { override val key get() = row.str("uuid") }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PurchasesScreen(nav: NavHostController) {
    val purchases by dbState<List<DbRow>?>(null) { PurchaseService.recent(App.db) }
    val snackbar = remember { SnackbarHostState() }

    // Grouped by the local day, with each day's total.
    val entries = remember(purchases) {
        val out = ArrayList<PurchaseListEntry>()
        purchases.orEmpty()
            .groupBy { Time.local(it.str("created_at"), "yyyy-MM-dd") }
            .forEach { (day, rows) ->
                out += PurchaseListEntry.Day(day, Money.round2(rows.sumOf { it.dbl("total_amount") }), rows.size)
                rows.forEach { out += PurchaseListEntry.Item(it) }
            }
        out
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = {
            AppTopBar(stringResource(R.string.purchases_title), onBack = { nav.popBackStack() }) {
                IconButton(onClick = { nav.navigate(Routes.SUPPLIERS) }) {
                    Icon(Icons.Filled.LocalShipping, contentDescription = stringResource(R.string.suppliers_title))
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.PURCHASE_NEW) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.purchases_new)) },
            )
        },
    ) { padding ->
        val list = purchases
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Icons.Filled.MoveToInbox, stringResource(R.string.purchases_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                entries.forEach { e ->
                    when (e) {
                        is PurchaseListEntry.Day -> stickyHeader(key = e.key) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(Time.showDate(e.date), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.purchases_day_total, e.count, Money.format(e.total)),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        }
                        is PurchaseListEntry.Item -> item(key = e.key) {
                            PurchaseRow(e.row) { nav.navigate(Routes.purchase(e.row.str("uuid"))) }
                            HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseRow(row: DbRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.strOrNull("supplier_name")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.purchases_no_supplier),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildList {
                add(Time.local(row.str("created_at"), "HH:mm"))
                add(stringResource(R.string.purchases_items_count, row.long("item_count").toInt()))
                row.strOrNull("created_by_name")?.takeIf { it.isNotBlank() }?.let(::add)
            }
            Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(Money.format(row.dbl("total_amount")), style = MaterialTheme.typography.titleMedium)
    }
}

// ====================================================================
// New purchase
// ====================================================================

/** Something a purchase line can be: a product without active variants, or one variant. */
private data class PurchaseTarget(
    val productUuid: String,
    val variantUuid: String?,
    val name: String,
    val variantLabel: String?,
    val unit: String,
    val cost: Double,
    val packSize: Long?,
    val barcode: String?,
    val active: Boolean,
    val searchText: String,
) {
    val key: String get() = variantUuid ?: productUuid
    val title: String get() = if (variantLabel != null) "$name — $variantLabel" else name
}

private class LineState(val target: PurchaseTarget, qty: String, cost: String) {
    var qty by mutableStateOf(qty)
    var cost by mutableStateOf(cost)
    val qtyValue: Double? get() = Money.parse(qty)
    val costValue: Double? get() = if (cost.isBlank()) 0.0 else Money.parse(cost)
    val subtotal: Double get() = Money.round2((qtyValue ?: 0.0) * (costValue ?: 0.0))
}

private fun loadTargets(): List<PurchaseTarget> {
    val db = App.db
    val variantsByProduct = db.query(
        "SELECT uuid, product_id, variant_label, barcode, cost_price FROM product_variants WHERE status = 'active' ORDER BY variant_label COLLATE NOCASE"
    ).groupBy { it.str("product_id") }
    val out = ArrayList<PurchaseTarget>()
    for (p in db.query("SELECT uuid, name, unit, cost_price, pack_size, barcode, status FROM products ORDER BY status = 'active' DESC, name COLLATE NOCASE")) {
        val uuid = p.str("uuid")
        val unit = p.str("unit").ifBlank { "dona" }
        val pack = p.long("pack_size").takeIf { it > 0 }
        val active = p.str("status") == "active"
        val variants = variantsByProduct[uuid]
        if (variants.isNullOrEmpty()) {
            out += PurchaseTarget(
                uuid, null, p.str("name"), null, unit, p.dbl("cost_price"), pack, p.strOrNull("barcode"), active,
                (p.str("name") + " " + p.str("barcode")).lowercase(),
            )
        } else {
            // Stock of a product with active variants lives on them: it is bought per variant.
            for (v in variants) {
                out += PurchaseTarget(
                    uuid, v.str("uuid"), p.str("name"), v.str("variant_label"), unit,
                    v.dblOrNull("cost_price") ?: p.dbl("cost_price"), pack, v.strOrNull("barcode"), active,
                    (p.str("name") + " " + v.str("variant_label") + " " + v.str("barcode") + " " + p.str("barcode")).lowercase(),
                )
            }
        }
    }
    return out
}

@Composable
fun PurchaseNewScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val targets by dbState(emptyList<PurchaseTarget>()) { loadTargets() }
    val suppliers by dbState(emptyList<DbRow>()) { ProductService.suppliers(App.db) }

    var query by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<LineState>() }
    var supplierId by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var updateCost by rememberSaveable { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var newSupplier by remember { mutableStateOf<String?>(null) }

    val results = remember(targets, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else targets.filter { it.searchText.contains(q) }
            .sortedByDescending { it.barcode?.lowercase() == q }
            .take(30)
    }
    val total = Money.round2(lines.sumOf { it.subtotal })

    fun add(t: PurchaseTarget, qty: Double = 1.0) {
        val existing = lines.firstOrNull { it.target.key == t.key }
        if (existing != null) {
            existing.qty = Money.qty((existing.qtyValue ?: 0.0) + qty)
            lines.remove(existing)
            lines.add(0, existing)
        } else {
            lines.add(0, LineState(t, Money.qty(qty), editNumber(t.cost)))
        }
    }

    fun onCode(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        val hit = targets.firstOrNull { it.barcode == code }
            // A product's own barcode when it is bought per variant: let the user pick the variant.
            ?: run {
                val owners = targets.filter { it.variantUuid != null && it.searchText.endsWith(" " + code.lowercase()) }
                if (owners.isNotEmpty()) { query = owners.first().name; return }
                null
            }
        if (hit != null) {
            add(hit)
            query = ""
        } else {
            scope.launch {
                val r = snackbar.showSnackbar(
                    ctx.getString(R.string.purchases_code_unknown, code),
                    actionLabel = ctx.getString(R.string.products_add),
                    duration = SnackbarDuration.Long,
                )
                if (r == SnackbarResult.ActionPerformed) nav.navigate(Routes.productEdit("new", code))
            }
        }
    }

    fun save() {
        if (saving) return
        if (lines.isEmpty()) {
            scope.launch { snackbar.showSnackbar(ctx.productError(ProductException(ProductException.NO_ITEMS))) }
            return
        }
        val bad = lines.firstOrNull { l ->
            val q = l.qtyValue
            val c = l.costValue
            q == null || q <= 0 || c == null || c < 0
        }
        if (bad != null) {
            scope.launch { snackbar.showSnackbar(ctx.getString(R.string.purchases_err_line, bad.target.title)) }
            return
        }
        val input = lines.reversed().map { PurchaseLine(it.target.productUuid, it.target.variantUuid, it.qtyValue!!, it.costValue!!) }
        saving = true
        scope.launch {
            val r = runCatching { io { PurchaseService.create(supplierId, input, note, updateCost) } }
            saving = false
            r.onSuccess { id ->
                nav.navigate(Routes.purchase(id)) { popUpTo(Routes.PURCHASE_NEW) { inclusive = true } }
            }.onFailure { snackbar.showSnackbar(ctx.productError(it)) }
        }
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = { AppTopBar(stringResource(R.string.purchases_new), onBack = { nav.popBackStack() }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.purchases_total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(total), style = MaterialTheme.typography.headlineMedium)
                    }
                    androidx.compose.material3.Button(
                        onClick = { save() },
                        enabled = !saving && lines.isNotEmpty(),
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(stringResource(R.string.purchases_save)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "search") {
                // Enter (a hardware scanner ends each code with it): an exact barcode, or the only result, is added.
                androidx.compose.material3.OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.purchases_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = null) }
                        else ScanButton { scanning = true }
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                        val q = query.trim()
                        when {
                            q.isEmpty() -> Unit
                            targets.any { it.barcode == q } -> onCode(q)
                            results.size == 1 -> { add(results.first()); query = "" }
                            q.all { it.isDigit() } && q.length >= 4 -> onCode(q)
                        }
                    }),
                    shape = RoundedCornerShape(14.dp),
                )
            }
            if (results.isNotEmpty()) {
                items(results, key = { "r" + it.key }) { t ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { add(t); query = "" }
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(t.barcode?.takeIf { it.isNotBlank() }, t.unit, if (!t.active) ctx.getString(R.string.products_inactive) else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(t.cost), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "results-end") { HorizontalDivider() }
            } else if (query.isNotBlank()) {
                item(key = "no-results") {
                    Text(stringResource(R.string.products_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                }
            }

            if (lines.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringResource(if (targets.isEmpty()) R.string.purchases_no_products else R.string.purchases_empty_lines),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(lines, key = { "l" + it.target.key }) { line ->
                LineCard(line, onRemove = { lines.remove(line) })
            }

            item(key = "details") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    SupplierPicker(suppliers, supplierId, onSelect = { supplierId = it }, onNew = { newSupplier = "" })
                    FormField(note, { note = it }, stringResource(R.string.purchases_note), singleLine = false, imeAction = ImeAction.Default)
                    Row(
                        Modifier.fillMaxWidth().clickable { updateCost = !updateCost }.heightIn(min = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = updateCost, onCheckedChange = { updateCost = it })
                        Text(stringResource(R.string.purchases_update_cost))
                    }
                }
            }
        }
    }

    if (scanning) {
        BarcodeScannerDialog(
            continuous = true,
            onResult = { onCode(it) },
            onDismiss = { scanning = false },
        )
    }

    newSupplier?.let { name ->
        NameDialog(
            title = stringResource(R.string.suppliers_add),
            label = stringResource(R.string.suppliers_name),
            value = name,
            onChange = { newSupplier = it },
            onDismiss = { newSupplier = null },
            onSave = {
                scope.launch {
                    runCatching { io { ProductService.saveSupplier(null, name, "", "", "") } }
                        .onSuccess { id -> newSupplier = null; supplierId = id }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
        )
    }
}

@Composable
private fun LineCard(line: LineState, onRemove: () -> Unit) {
    val t = line.target
    val qtyBad = line.qty.isNotBlank() && (line.qtyValue == null || line.qtyValue!! <= 0 || !Units.fits(line.qtyValue!!, t.unit))
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.purchases_remove_line)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FormField(
                    line.qty, { line.qty = numberInput(it) }, stringResource(R.string.purchases_qty, t.unit),
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal, isError = qtyBad,
                )
                FormField(
                    line.cost, { line.cost = numberInput(it) }, stringResource(R.string.purchases_unit_cost),
                    modifier = Modifier.weight(1.2f), keyboardType = KeyboardType.Decimal,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.packSize != null) {
                    TextButton(
                        onClick = { line.qty = Money.qty((line.qtyValue ?: 0.0) + t.packSize) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.purchases_add_pack, Money.qty(t.packSize.toDouble()), t.unit)) }
                }
                Spacer(Modifier.weight(1f))
                Text(Money.format(line.subtotal), style = MaterialTheme.typography.titleMedium)
            }
            if (qtyBad && line.qtyValue != null && line.qtyValue!! > 0) {
                Text(stringResource(R.string.products_err_qty_whole, t.unit), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SupplierPicker(suppliers: List<DbRow>, selected: String?, onSelect: (String?) -> Unit, onNew: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = suppliers.firstOrNull { it.str("uuid") == selected }?.str("name") ?: stringResource(R.string.purchases_no_supplier)
    Box {
        FormField(value = name, onChange = {}, label = stringResource(R.string.purchases_supplier), readOnly = true, trailing = { Icon(Icons.Filled.ArrowDropDown, null) })
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.suppliers_add)) },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                onClick = { open = false; onNew() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.purchases_no_supplier)) }, onClick = { open = false; onSelect(null) })
            suppliers.forEach { s ->
                DropdownMenuItem(text = { Text(s.str("name")) }, onClick = { open = false; onSelect(s.str("uuid")) })
            }
        }
    }
}

// ====================================================================
// Purchase detail
// ====================================================================

@Composable
fun PurchaseDetailScreen(nav: NavHostController, uuid: String) {
    val snackbar = remember { SnackbarHostState() }
    val data by dbState<Pair<DbRow?, List<DbRow>>?>(null, uuid) {
        PurchaseService.find(App.db, uuid) to PurchaseService.items(App.db, uuid)
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = { AppTopBar(stringResource(R.string.purchases_detail_title), onBack = { nav.popBackStack() }) },
    ) { padding ->
        val d = data
        val p = d?.first
        when {
            d == null -> Unit
            p == null -> EmptyState(Icons.Filled.ReceiptLong, stringResource(R.string.purchases_not_found), Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "head") {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.purchases_total), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Money.format(p.dbl("total_amount")), style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            InfoLine(stringResource(R.string.purchases_date), Time.local(p.str("created_at")))
                            InfoLine(
                                stringResource(R.string.purchases_supplier),
                                p.strOrNull("supplier_name")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.purchases_no_supplier),
                            )
                            p.strOrNull("created_by_name")?.takeIf { it.isNotBlank() }?.let {
                                InfoLine(stringResource(R.string.purchases_created_by), it)
                            }
                            p.strOrNull("note")?.takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(R.string.purchases_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(it)
                            }
                        }
                    }
                }
                item(key = "items-title") {
                    Text(
                        stringResource(R.string.purchases_items_count, d.second.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(d.second, key = { it.str("uuid") }) { it ->
                    val name = it.strOrNull("product_name")?.takeIf { n -> n.isNotBlank() } ?: "—"
                    val label = it.strOrNull("variant_label")?.takeIf { l -> l.isNotBlank() }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(if (label != null) "$name — $label" else name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                qtyUnit(it.dbl("qty"), it.strOrNull("unit")) + " × " + Money.format(it.dbl("unit_cost")),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(Money.format(it.dbl("subtotal")), style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
