package uz.kassiron.app.ui.products

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.domain.ProductListItem
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.QuickRow
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.AppTopBar
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.SearchField
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io

// ====================================================================
// Products list
// ====================================================================

private enum class StockFilter { ALL, LOW, OUT, INACTIVE }

private data class ProductsData(
    val products: List<ProductListItem>,
    val categories: List<DbRow>,
    val stockValue: Double,
    val shopDefault: Double?,
)

@Composable
fun ProductsScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val canPrices = user?.can("prices") == true
    val data by dbState<ProductsData?>(null, canPrices) {
        ProductsData(
            products = ProductService.list(App.db),
            categories = ProductService.categories(App.db),
            stockValue = if (canPrices) ProductService.stockValue(App.db) else 0.0,
            shopDefault = ProductService.shopLowStockDefault(),
        )
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(StockFilter.ALL) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }

    val all = data?.products.orEmpty()
    val shopDefault = data?.shopDefault
    val lowCount = remember(all, shopDefault) { all.count { it.active && it.isLow(shopDefault) } }
    val outCount = remember(all) { all.count { it.active && it.isOut() } }
    val inactiveCount = remember(all) { all.count { !it.active } }

    val shown = remember(all, query, filter, categoryId, shopDefault) {
        val q = query.trim().lowercase()
        all.filter { p ->
            (categoryId == null || p.categoryId == categoryId) &&
                (q.isEmpty() || p.searchText.contains(q)) &&
                when (filter) {
                    StockFilter.ALL -> true
                    StockFilter.LOW -> p.active && p.isLow(shopDefault)
                    StockFilter.OUT -> p.active && p.isOut()
                    StockFilter.INACTIVE -> !p.active
                }
        }.let { list ->
            // An exact barcode match first (a scan).
            if (q.isNotEmpty()) list.sortedByDescending { it.barcode?.lowercase() == q } else list
        }
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = {
            AppTopBar(stringResource(R.string.products_title)) {
                IconButton(onClick = { nav.navigate(Routes.QUICK_PRODUCTS) }) {
                    Icon(Icons.Filled.Bolt, contentDescription = stringResource(R.string.products_quick_title))
                }
                IconButton(onClick = { nav.navigate(Routes.PURCHASES) }) {
                    Icon(Icons.Filled.MoveToInbox, contentDescription = stringResource(R.string.purchases_title))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.purchases_new)) },
                            leadingIcon = { Icon(Icons.Filled.MoveToInbox, null) },
                            onClick = { menuOpen = false; nav.navigate(Routes.PURCHASE_NEW) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.products_quick_title)) },
                            leadingIcon = { Icon(Icons.Filled.Bolt, null) },
                            onClick = { menuOpen = false; nav.navigate(Routes.QUICK_PRODUCTS) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.categories_title)) },
                            leadingIcon = { Icon(Icons.Filled.Category, null) },
                            onClick = { menuOpen = false; nav.navigate(Routes.CATEGORIES) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.suppliers_title)) },
                            leadingIcon = { Icon(Icons.Filled.LocalShipping, null) },
                            onClick = { menuOpen = false; nav.navigate(Routes.SUPPLIERS) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate(Routes.productEdit("new")) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.products_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                value = query,
                onChange = { query = it },
                placeholder = stringResource(R.string.products_search),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                trailing = { ScanButton { scanning = true } },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = filter == StockFilter.ALL,
                        onClick = { filter = StockFilter.ALL },
                        label = { Text(stringResource(R.string.products_filter_all)) },
                    )
                }
                item {
                    FilterChip(
                        selected = filter == StockFilter.LOW,
                        onClick = { filter = StockFilter.LOW },
                        label = { Text(stringResource(R.string.products_filter_low) + if (lowCount > 0) " ($lowCount)" else "") },
                    )
                }
                item {
                    FilterChip(
                        selected = filter == StockFilter.OUT,
                        onClick = { filter = StockFilter.OUT },
                        label = { Text(stringResource(R.string.products_filter_out) + if (outCount > 0) " ($outCount)" else "") },
                    )
                }
                item {
                    FilterChip(
                        selected = filter == StockFilter.INACTIVE,
                        onClick = { filter = StockFilter.INACTIVE },
                        label = { Text(stringResource(R.string.products_filter_inactive) + if (inactiveCount > 0) " ($inactiveCount)" else "") },
                    )
                }
                val cats = data?.categories.orEmpty()
                if (cats.isNotEmpty()) {
                    item { CategoryFilterChip(cats, categoryId) { categoryId = it } }
                }
            }

            if (data != null && all.isNotEmpty()) {
                SummaryBar(
                    count = shown.size,
                    total = all.size,
                    stockValue = data!!.stockValue,
                    showValue = canPrices,
                )
            }

            when {
                data == null -> Unit // first read is instant; draw nothing for a frame
                all.isEmpty() -> EmptyProducts(nav)
                shown.isEmpty() -> NoResults(query, onAddWithBarcode = { code -> nav.navigate(Routes.productEdit("new", code)) })
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shown, key = { it.uuid }) { p ->
                        ProductRow(p, shopDefault) { nav.navigate(Routes.productEdit(p.uuid)) }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    if (scanning) {
        BarcodeScannerDialog(
            onResult = { code -> scanning = false; query = code.trim() },
            onDismiss = { scanning = false },
        )
    }
}

@Composable
private fun CategoryFilterChip(categories: List<DbRow>, selected: String?, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val name = categories.firstOrNull { it.str("uuid") == selected }?.str("name")
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(name ?: stringResource(R.string.products_category)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.products_category_all)) }, onClick = { onSelect(null); open = false })
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.str("name")) }, onClick = { onSelect(c.str("uuid")); open = false })
            }
        }
    }
}

@Composable
private fun SummaryBar(count: Int, total: Int, stockValue: Double, showValue: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (count == total) stringResource(R.string.products_count, total) else stringResource(R.string.products_count_of, count, total),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (showValue) {
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.products_stock_value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(stockValue), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ProductRow(p: ProductListItem, shopDefault: Double?, onClick: () -> Unit) {
    val stockColor = when {
        p.isOut() -> StockColors.Out
        p.isLow(shopDefault) -> StockColors.Low
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .alpha(if (p.active) 1f else 0.55f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = buildList {
                if (!p.active) add(stringResource(R.string.products_inactive))
                if (p.variantCount > 0) add(stringResource(R.string.products_variant_count, p.variantCount))
                p.categoryName?.takeIf { it.isNotBlank() }?.let(::add)
                p.barcode?.takeIf { it.isNotBlank() }?.let(::add)
            }
            if (sub.isNotEmpty()) {
                Text(
                    sub.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(p.sellPrice), style = MaterialTheme.typography.titleMedium)
            Text(
                qtyUnit(p.stock, p.unit),
                style = MaterialTheme.typography.bodyMedium,
                color = stockColor,
                fontWeight = if (stockColor != MaterialTheme.colorScheme.onSurfaceVariant) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun EmptyProducts(nav: NavHostController) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Inventory2, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.products_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { nav.navigate(Routes.QUICK_PRODUCTS) }, modifier = Modifier.height(48.dp)) {
            Icon(Icons.Filled.Bolt, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.products_quick_title))
        }
    }
}

@Composable
private fun NoResults(query: String, onAddWithBarcode: (String) -> Unit) {
    val code = query.trim()
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.products_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (code.length >= 4 && code.all { it.isDigit() }) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { onAddWithBarcode(code) }, modifier = Modifier.height(48.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.products_add_with_barcode, code))
            }
        }
    }
}

// ====================================================================
// Quick entry (/products/quick)
// ====================================================================

private class QuickRowState(val id: Long, initial: QuickRow = QuickRow()) {
    var row by mutableStateOf(initial)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun QuickProductsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val user by App.session.user.collectAsState()
    val canPrices = user?.can("prices") == true

    var nextId by remember { mutableStateOf(0L) }
    val rows = remember { mutableStateListOf<QuickRowState>() }
    fun addRow(initial: QuickRow = QuickRow()): QuickRowState = QuickRowState(nextId++, initial).also { rows += it }
    LaunchedEffect(Unit) { if (rows.isEmpty()) repeat(3) { addRow() } }

    // Every barcode already in the shop, for a mark while typing (the save checks again).
    val taken by dbState(emptySet<String>()) {
        App.db.query("SELECT barcode FROM products WHERE barcode IS NOT NULL AND barcode != '' UNION SELECT barcode FROM product_variants WHERE barcode IS NOT NULL AND barcode != ''")
            .map { it.str("barcode") }.toSet()
    }

    var saving by remember { mutableStateOf(false) }
    var scanRow by remember { mutableStateOf<QuickRowState?>(null) }
    var scanMany by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val filled = rows.count { !it.row.isBlank }

    fun ensureTrailingBlank() {
        if (rows.isEmpty() || !rows.last().row.isBlank) addRow()
    }

    fun save() {
        if (saving) return
        val ids = rows.map { it.id }
        val input = rows.mapIndexedNotNull { i, r -> if (r.row.isBlank) null else i to r.row }
        if (input.isEmpty()) {
            scope.launch { snackbar.showSnackbar(ctx.getString(R.string.products_quick_nothing)) }
            return
        }
        saving = true
        scope.launch {
            val result = runCatching { io { ProductService.quickCreate(input, canPrices) } }
            saving = false
            result.onSuccess { res ->
                val createdIds = res.created.map { ids[it] }.toSet()
                res.errors.forEach { (i, e) -> rows.firstOrNull { it.id == ids[i] }?.error = ctx.productError(e) }
                rows.removeAll { it.id in createdIds }
                ensureTrailingBlank()
                val msg = buildString {
                    append(ctx.getString(R.string.products_quick_saved, res.created.size))
                    if (res.errors.isNotEmpty()) append(" · ").append(ctx.getString(R.string.products_quick_errors, res.errors.size))
                }
                snackbar.showSnackbar(msg)
            }.onFailure { e ->
                snackbar.showSnackbar(ctx.productError(e))
            }
        }
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = {
            AppTopBar(stringResource(R.string.products_quick_title), onBack = { nav.popBackStack() }) {
                IconButton(onClick = { scanMany = true }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.products_quick_scan_many))
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.products_quick_filled, filled),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    androidx.compose.material3.Button(
                        onClick = { save() },
                        enabled = !saving && filled > 0,
                        modifier = Modifier.height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Filled.Save, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.products_quick_save))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.products_quick_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            itemsIndexed(rows, key = { _, r -> r.id }) { index, state ->
                val dupInTable = state.row.barcode.isNotBlank() &&
                    rows.any { it !== state && it.row.barcode.trim() == state.row.barcode.trim() }
                val takenHere = state.row.barcode.trim().let { it.isNotEmpty() && it in taken }
                QuickRowCard(
                    number = index + 1,
                    state = state,
                    canPrices = canPrices,
                    barcodeWarning = when {
                        takenHere -> ctx.getString(R.string.products_err_barcode_taken, state.row.barcode.trim())
                        dupInTable -> ctx.getString(R.string.products_err_barcode_duplicate)
                        else -> null
                    },
                    onChange = { new ->
                        state.row = new
                        state.error = null
                        ensureTrailingBlank()
                    },
                    onScan = { scanRow = state },
                    onRemove = {
                        rows.remove(state)
                        ensureTrailingBlank()
                    },
                )
            }
            item {
                TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = { repeat(5) { addRow() } }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.products_quick_add_rows))
                }
            }
        }
    }

    scanRow?.let { target ->
        BarcodeScannerDialog(
            onResult = { code ->
                scanRow = null
                if (rows.contains(target)) {
                    target.row = target.row.copy(barcode = code.trim())
                    target.error = null
                    ensureTrailingBlank()
                }
            },
            onDismiss = { scanRow = null },
        )
    }
    if (scanMany) {
        BarcodeScannerDialog(
            continuous = true,
            onResult = { raw ->
                val code = raw.trim()
                if (code.isNotEmpty() && rows.none { it.row.barcode.trim() == code }) {
                    val target = rows.firstOrNull { it.row.barcode.isBlank() && it.row.name.isBlank() } ?: addRow()
                    target.row = target.row.copy(barcode = code)
                    ensureTrailingBlank()
                }
            },
            onDismiss = { scanMany = false },
        )
    }
}

@Composable
private fun QuickRowCard(
    number: Int,
    state: QuickRowState,
    canPrices: Boolean,
    barcodeWarning: String?,
    onChange: (QuickRow) -> Unit,
    onScan: () -> Unit,
    onRemove: () -> Unit,
) {
    val r = state.row
    val error = state.error
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (!r.isBlank) {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.products_quick_remove)) }
                }
            }
            FormField(r.name, { onChange(r.copy(name = it)) }, stringResource(R.string.products_name))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    r.barcode, { onChange(r.copy(barcode = it)) }, stringResource(R.string.products_barcode),
                    modifier = Modifier.weight(1.4f),
                    isError = barcodeWarning != null,
                    trailing = { ScanButton(onScan) },
                )
                UnitField(r.unit, { onChange(r.copy(unit = it)) }, Modifier.weight(1f))
            }
            if (barcodeWarning != null) {
                Text(barcodeWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(
                    r.sellPrice, { onChange(r.copy(sellPrice = numberInput(it))) }, stringResource(R.string.products_sell_price),
                    modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                )
                if (canPrices) {
                    FormField(
                        r.costPrice, { onChange(r.copy(costPrice = numberInput(it))) }, stringResource(R.string.products_cost_price),
                        modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                    )
                }
                FormField(
                    r.stock, { onChange(r.copy(stock = numberInput(it))) }, stringResource(R.string.products_stock_short),
                    modifier = Modifier.weight(0.8f), keyboardType = KeyboardType.Decimal,
                )
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ====================================================================
// Categories
// ====================================================================

@Composable
fun CategoriesScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val categories by dbState<List<DbRow>?>(null) {
        App.db.query(
            """SELECT c.uuid, c.name, (SELECT COUNT(*) FROM products p WHERE p.category_id = c.uuid) AS product_count
               FROM categories c WHERE c.type = 'product' ORDER BY c.name COLLATE NOCASE"""
        )
    }
    // null = closed; "" = new; uuid = rename.
    var editing by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = { AppTopBar(stringResource(R.string.categories_title), onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = ""; editName = "" }) { Icon(Icons.Filled.Add, stringResource(R.string.categories_add)) }
        },
    ) { padding ->
        val list = categories
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Icons.Filled.Category, stringResource(R.string.categories_empty), Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                items(list, key = { it.str("uuid") }) { c ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { editing = c.str("uuid"); editName = c.str("name") }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.str("name"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.products_count, c.long("product_count").toInt()),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    editing?.let { target ->
        NameDialog(
            title = stringResource(if (target.isEmpty()) R.string.categories_add else R.string.categories_rename),
            label = stringResource(R.string.categories_name),
            value = editName,
            onChange = { editName = it },
            onDismiss = { editing = null },
            onSave = {
                scope.launch {
                    val r = runCatching {
                        io { if (target.isEmpty()) ProductService.findOrCreateCategory(editName) else ProductService.renameCategory(target, editName) }
                    }
                    r.onSuccess { editing = null; snackbar.showSnackbar(ctx.getString(R.string.categories_saved)) }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
        )
    }
}

@Composable
fun NameDialog(title: String, label: String, value: String, onChange: (String) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            FormField(value, onChange, label, imeAction = ImeAction.Done, onIme = onSave)
        },
        confirmButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onSave, enabled = value.isNotBlank()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ====================================================================
// Suppliers
// ====================================================================

private data class SupplierDraft(val uuid: String?, val name: String = "", val phone: String = "", val address: String = "", val note: String = "")

@Composable
fun SuppliersScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val suppliers by dbState<List<DbRow>?>(null) { ProductService.suppliers(App.db) }
    var query by rememberSaveable { mutableStateOf("") }
    var draft by remember { mutableStateOf<SupplierDraft?>(null) }

    val shown = remember(suppliers, query) {
        val q = query.trim().lowercase()
        suppliers.orEmpty().filter { q.isEmpty() || it.str("name").lowercase().contains(q) || it.str("phone").contains(q) }
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = { AppTopBar(stringResource(R.string.suppliers_title), onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { draft = SupplierDraft(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.suppliers_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val list = suppliers
            if (list != null && list.size > 6) {
                SearchField(query, { query = it }, stringResource(R.string.common_search), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(Icons.Filled.LocalShipping, stringResource(R.string.suppliers_empty))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(shown, key = { it.str("uuid") }) { s ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    draft = SupplierDraft(s.str("uuid"), s.str("name"), s.str("phone"), s.str("address"), s.str("note"))
                                }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(s.str("name"), style = MaterialTheme.typography.titleMedium)
                            val sub = listOf(s.str("phone"), s.str("address")).filter { it.isNotBlank() }
                            if (sub.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (s.str("phone").isNotBlank()) {
                                        Icon(Icons.Filled.Phone, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(sub.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (s.str("note").isNotBlank()) {
                                Text(s.str("note"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    draft?.let { d ->
        SupplierDialog(
            draft = d,
            onChange = { draft = it },
            onDismiss = { draft = null },
            onSave = {
                scope.launch {
                    runCatching { io { ProductService.saveSupplier(d.uuid, d.name, d.phone, d.address, d.note) } }
                        .onSuccess { draft = null; snackbar.showSnackbar(ctx.getString(if (d.uuid == null) R.string.suppliers_created else R.string.suppliers_updated)) }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
        )
    }
}

@Composable
private fun SupplierDialog(draft: SupplierDraft, onChange: (SupplierDraft) -> Unit, onDismiss: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.uuid == null) R.string.suppliers_add else R.string.suppliers_edit), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormField(draft.name, { onChange(draft.copy(name = it)) }, stringResource(R.string.suppliers_name))
                FormField(draft.phone, { onChange(draft.copy(phone = it)) }, stringResource(R.string.suppliers_phone), keyboardType = KeyboardType.Phone)
                FormField(draft.address, { onChange(draft.copy(address = it)) }, stringResource(R.string.suppliers_address))
                FormField(draft.note, { onChange(draft.copy(note = it)) }, stringResource(R.string.suppliers_note), singleLine = false, imeAction = ImeAction.Default)
            }
        },
        confirmButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onSave, enabled = draft.name.isNotBlank()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
