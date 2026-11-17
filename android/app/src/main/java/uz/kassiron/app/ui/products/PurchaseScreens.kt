package uz.kassiron.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KFab
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KGroup
import uz.kassiron.app.ui.kit.KGroupRow
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KKeyValue
import uz.kassiron.app.ui.kit.KList
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSearch
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.kit.KStepper
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KSwitch
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.theme.K

// ====================================================================
// Purchases list ("Kirimlar"), grouped by day
// ====================================================================

private sealed interface PurchaseListEntry {
    val key: String
    data class Day(val date: String, val total: Double, val count: Int) : PurchaseListEntry { override val key get() = "d$date" }
    data class Item(val row: DbRow) : PurchaseListEntry { override val key get() = row.str("uuid") }
}

@Composable
fun PurchasesScreen(nav: NavHostController) {
    val purchases by dbState<List<DbRow>?>(null) { PurchaseService.recent(App.db) }

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

    KScreen(
        topBar = {
            KTopBar(stringResource(R.string.purchases_title), onBack = { nav.popBackStack() }) {
                KIconButton(Icons.Outlined.LocalShipping, { nav.navigate(Routes.SUPPLIERS) }, contentDescription = stringResource(R.string.suppliers_title))
            }
        },
        fab = { KFab(Icons.Outlined.Add, { nav.navigate(Routes.PURCHASE_NEW) }, stringResource(R.string.purchases_new)) },
    ) {
        val list = purchases
        when {
            list == null -> Unit
            list.isEmpty() -> KEmpty(
                Icons.Outlined.MoveToInbox,
                stringResource(R.string.purchases_empty_title),
                stringResource(R.string.purchases_empty_text),
                action = stringResource(R.string.purchases_new),
                onAction = { nav.navigate(Routes.PURCHASE_NEW) },
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Edge, end = Edge, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entries.forEach { e ->
                    when (e) {
                        is PurchaseListEntry.Day -> item(key = e.key) { DayHeader(e) }
                        is PurchaseListEntry.Item -> item(key = e.key) {
                            PurchaseRow(e.row) { nav.navigate(Routes.purchase(e.row.str("uuid"))) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: PurchaseListEntry.Day) {
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Time.showDate(day.date), style = K.t.section, color = K.c.tx, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.purchases_day_count, day.count) + " · ", style = K.t.small, color = K.c.mu)
        KMoney(day.total, size = 13.sp)
    }
}

@Composable
private fun PurchaseRow(row: DbRow, onClick: () -> Unit) {
    val sub = buildList {
        add(Time.local(row.str("created_at"), "HH:mm"))
        add(stringResource(R.string.purchases_items_count, row.long("item_count").toInt()))
        row.strOrNull("created_by_name")?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
    KRow(
        title = row.strOrNull("supplier_name")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.purchases_no_supplier),
        subtitle = { KSub(sub) },
        trailing = { KMoney(row.dbl("total_amount")) },
        onClick = onClick,
    )
}

// ====================================================================
// New purchase (mockup 18)
// ====================================================================

/** Something a purchase line can be: a product without active variants, or one variant. */
internal data class PurchaseTarget(
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
fun PurchaseNewScreen(nav: NavHostController) = PurchaseNew(nav)

/** [prefill] (product name → qty) lets the screenshot tests show a purchase with items. */
@Composable
internal fun PurchaseNew(nav: NavHostController, prefill: List<Pair<String, Double>> = emptyList(), prefillSupplier: String? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val targets by dbState(emptyList<PurchaseTarget>()) { loadTargets() }
    val suppliers by dbState(emptyList<DbRow>()) { ProductService.suppliers(App.db) }

    var query by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<LineState>() }
    var supplierId by rememberSaveable { mutableStateOf(prefillSupplier) }
    var note by rememberSaveable { mutableStateOf("") }
    // The approved design has it on: most shops want the cost price of the last purchase.
    var updateCost by rememberSaveable { mutableStateOf(true) }
    var scanning by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var supplierSheet by remember { mutableStateOf(false) }
    var noteSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LineState?>(null) }
    var prefilled by remember { mutableStateOf(false) }

    if (!prefilled && prefill.isNotEmpty() && targets.isNotEmpty()) {
        prefilled = true
        prefill.reversed().forEach { (name, qty) ->
            targets.firstOrNull { it.name == name }?.let { lines.add(0, LineState(it, Money.qty(qty), editNumber(it.cost))) }
        }
    }

    val results = remember(targets, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else targets.filter { it.searchText.contains(q) }.sortedByDescending { it.barcode?.lowercase() == q }.take(30)
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
                    actionLabel = ctx.getString(R.string.purchases_code_add),
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
            q == null || q <= 0 || c == null || c < 0 || !Units.fits(q, l.target.unit)
        }
        if (bad != null) {
            editing = bad
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

    Box(Modifier.fillMaxSize().imePadding()) {
        KScreen(
            snackbar = snackbar,
            topBar = { KTopBar(stringResource(R.string.purchases_new), onBack = { nav.popBackStack() }) },
            bottomBar = {
                KBottomBar {
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.purchases_total), style = K.t.label.copy(fontSize = 13.sp), color = K.c.mu, modifier = Modifier.weight(1f))
                        KMoney(total, size = 24.sp)
                    }
                    KButton(stringResource(R.string.purchases_save), { save() }, Modifier.fillMaxWidth(), enabled = !saving && lines.isNotEmpty())
                }
            },
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp)) {
                item(key = "supplier") {
                    val name = suppliers.firstOrNull { it.str("uuid") == supplierId }?.str("name")
                    PickField(
                        stringResource(R.string.purchases_supplier), name, stringResource(R.string.purchases_no_supplier),
                        { supplierSheet = true }, Modifier.padding(horizontal = Edge),
                    )
                }
                item(key = "search") {
                    Spacer(Modifier.height(12.dp))
                    // Enter (a hardware scanner ends each code with it): an exact barcode, or the only result, is added.
                    KSearch(
                        query, { query = it }, stringResource(R.string.purchases_search),
                        onScan = { scanning = true }, scanLime = true,
                        onSubmit = {
                            val q = query.trim()
                            when {
                                q.isEmpty() -> Unit
                                targets.any { it.barcode == q } -> onCode(q)
                                results.size == 1 -> { add(results.first()); query = "" }
                                q.all { it.isDigit() } && q.length >= 4 -> onCode(q)
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (query.isNotBlank()) {
                    if (results.isEmpty()) {
                        item(key = "no-results") {
                            Text(
                                stringResource(R.string.products_not_found), style = K.t.body, color = K.c.mu, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                            )
                        }
                    }
                    items(results, key = { "r" + it.key }) { t ->
                        val sub = listOfNotNull(
                            t.barcode?.takeIf { it.isNotBlank() },
                            stringResource(R.string.purchases_cost_line, Money.format(t.cost, withCurrency = false)),
                            if (!t.active) stringResource(R.string.products_inactive) else null,
                        ).joinToString(" · ")
                        KRow(
                            title = t.title,
                            modifier = Modifier.padding(start = Edge, end = Edge, bottom = 8.dp),
                            subtitle = { KSub(sub) },
                            trailing = {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(K.c.primary), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Add, null, tint = K.c.onPrimary, modifier = Modifier.size(20.dp))
                                }
                            },
                            onClick = { add(t); query = "" },
                        )
                    }
                } else {
                    if (lines.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                stringResource(if (targets.isEmpty()) R.string.purchases_no_products else R.string.purchases_empty_lines),
                                style = K.t.body, color = K.c.mu, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
                            )
                        }
                    }
                    items(lines, key = { "l" + it.target.key }) { line ->
                        LineRow(
                            line,
                            onMinus = {
                                val q = (line.qtyValue ?: 0.0) - 1
                                if (q <= 0.000001) lines.remove(line) else line.qty = Money.qty(q)
                            },
                            onPlus = { line.qty = Money.qty((line.qtyValue ?: 0.0) + 1) },
                            onEdit = { editing = line },
                        )
                    }
                    item(key = "options") {
                        Spacer(Modifier.height(4.dp))
                        KGroup {
                            KGroupRow(
                                stringResource(R.string.purchases_update_cost), first = true,
                                trailing = { KSwitch(updateCost) { updateCost = it } },
                            )
                            KGroupRow(
                                stringResource(R.string.purchases_note),
                                value = note.ifBlank { stringResource(R.string.purchases_note_empty) }.take(24),
                                onClick = { noteSheet = true },
                            )
                        }
                    }
                }
            }
        }
    }

    if (scanning) {
        BarcodeScannerDialog(continuous = true, onResult = { onCode(it) }, onDismiss = { scanning = false })
    }

    if (supplierSheet) {
        PickSheet(
            title = stringResource(R.string.purchases_supplier),
            items = listOf(PickItem(null, stringResource(R.string.purchases_no_supplier))) +
                suppliers.map { PickItem(it.str("uuid"), it.str("name"), it.str("phone").takeIf { p -> p.isNotBlank() }?.let(::showPhone)) },
            selected = supplierId,
            searchHint = stringResource(R.string.suppliers_search),
            newTitle = { ctx.getString(R.string.suppliers_new_named, it) },
            newSub = stringResource(R.string.suppliers_new_hint),
            onPick = { supplierId = it; supplierSheet = false },
            onCreate = { name ->
                scope.launch {
                    runCatching { io { ProductService.saveSupplier(null, name, "", "", "") } }
                        .onSuccess { id -> supplierId = id; supplierSheet = false }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
            onDismiss = { supplierSheet = false },
        )
    }

    if (noteSheet) {
        var draft by remember { mutableStateOf(note) }
        KSheet({ noteSheet = false }) {
            KSheetTitle(stringResource(R.string.purchases_note), { noteSheet = false })
            KField(draft, { draft = it }, placeholder = stringResource(R.string.purchases_note_ph), singleLine = false)
            Spacer(Modifier.height(14.dp))
            KButton(stringResource(R.string.common_save), { note = draft.trim(); noteSheet = false }, Modifier.fillMaxWidth())
        }
    }

    editing?.let { line ->
        val t = line.target
        val qtyBad = line.qty.isNotBlank() && (line.qtyValue == null || line.qtyValue!! <= 0 || !Units.fits(line.qtyValue!!, t.unit))
        KSheet({ editing = null }) {
            KSheetTitle(t.title, { editing = null })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(
                    line.qty, { line.qty = numberInput(it) }, Modifier.weight(1f),
                    label = stringResource(R.string.purchases_qty, t.unit),
                    error = if (qtyBad) stringResource(R.string.purchases_qty_whole_short) else null,
                )
                NumberField(line.cost, { line.cost = numberInput(it) }, Modifier.weight(1f), label = stringResource(R.string.products_cost_price))
            }
            if (t.packSize != null) {
                Spacer(Modifier.height(10.dp))
                KButton(
                    stringResource(R.string.purchases_add_pack, Money.qty(t.packSize.toDouble()), t.unit),
                    { line.qty = Money.qty((line.qtyValue ?: 0.0) + t.packSize) },
                    Modifier.fillMaxWidth(), kind = KButtonKind.Alt, icon = Icons.Outlined.Add, height = 46.dp, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KButton(
                    stringResource(R.string.common_delete), { lines.remove(line); editing = null },
                    Modifier.weight(1f), kind = KButtonKind.Danger, icon = Icons.Outlined.Delete,
                )
                KButton(stringResource(R.string.common_save), { editing = null }, Modifier.weight(1f), enabled = !qtyBad)
            }
        }
    }
}

@Composable
private fun LineRow(line: LineState, onMinus: () -> Unit, onPlus: () -> Unit, onEdit: () -> Unit) {
    val t = line.target
    val sub = stringResource(R.string.purchases_cost_line, Money.format(line.costValue ?: 0.0, withCurrency = false)) +
        if ((line.qtyValue ?: 0.0) > 1) " · " + Money.format(line.subtotal) else ""
    KRow(
        title = t.title,
        modifier = Modifier.padding(start = Edge, end = Edge, bottom = 8.dp),
        selected = true,
        subtitle = { KSub(sub) },
        trailing = { KStepper(line.qty.ifBlank { "0" }, onMinus, onPlus, onQtyClick = onEdit) },
        onClick = onEdit,
    )
}

// ====================================================================
// Purchase detail
// ====================================================================

@Composable
fun PurchaseDetailScreen(nav: NavHostController, uuid: String) {
    val data by dbState<Pair<DbRow?, List<DbRow>>?>(null, uuid) {
        PurchaseService.find(App.db, uuid) to PurchaseService.items(App.db, uuid)
    }
    val d = data
    val p = d?.first
    KScreen(
        topBar = {
            KTopBar(
                if (p != null) stringResource(R.string.purchases_detail_title_at, Time.local(p.str("created_at"), "dd.MM HH:mm"))
                else stringResource(R.string.purchases_detail_title),
                onBack = { nav.popBackStack() },
            )
        },
    ) {
        when {
            d == null -> Unit
            p == null -> KEmpty(Icons.AutoMirrored.Outlined.ReceiptLong, stringResource(R.string.purchases_not_found))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp)) {
                item(key = "head") {
                    KCard(Modifier.padding(horizontal = Edge), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        KKeyValue(stringResource(R.string.purchases_date), first = true) { Value(Time.local(p.str("created_at"))) }
                        KKeyValue(stringResource(R.string.purchases_supplier)) {
                            Value(p.strOrNull("supplier_name")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.purchases_no_supplier))
                        }
                        p.strOrNull("created_by_name")?.takeIf { it.isNotBlank() }?.let { KKeyValue(stringResource(R.string.purchases_created_by)) { Value(it) } }
                        p.strOrNull("note")?.takeIf { it.isNotBlank() }?.let { KKeyValue(stringResource(R.string.purchases_note)) { Value(it) } }
                    }
                }
                item(key = "items-title") { KSection(stringResource(R.string.purchases_items_title)) }
                item(key = "items") {
                    KList {
                        d.second.forEach { it ->
                            val name = it.strOrNull("product_name")?.takeIf { n -> n.isNotBlank() } ?: "—"
                            val label = it.strOrNull("variant_label")?.takeIf { l -> l.isNotBlank() }
                            KRow(
                                title = if (label != null) "$name — $label" else name,
                                subtitle = { KSub(Money.format(it.dbl("unit_cost"), withCurrency = false) + " × " + qtyUnit(it.dbl("qty"), it.strOrNull("unit"))) },
                                trailing = { KMoney(it.dbl("subtotal")) },
                            )
                        }
                    }
                }
                item(key = "total") {
                    Spacer(Modifier.height(10.dp))
                    KCard(Modifier.padding(horizontal = Edge), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        KKeyValue(stringResource(R.string.purchases_total), first = true) { KMoney(p.dbl("total_amount"), size = 18.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(text, style = K.t.body.copy(fontSize = 14.sp), color = K.c.tx, maxLines = 2)
}
