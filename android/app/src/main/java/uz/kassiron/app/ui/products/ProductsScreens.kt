package uz.kassiron.app.ui.products

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.domain.ProductListItem
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KChip
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KFab
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSearch
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.theme.K

// Products list (mockup 15).

internal enum class StockFilter { ALL, LOW, OUT, INACTIVE }

private data class ProductsData(
    val products: List<ProductListItem>,
    val categories: List<DbRow>,
    val stockValue: Double,
    val shopDefault: Double?,
)

@Composable
fun ProductsScreen(nav: NavHostController) = ProductsList(nav)

/** [initialFilter] lets the screenshot tests show each filter. */
@Composable
internal fun ProductsList(nav: NavHostController, initialFilter: StockFilter = StockFilter.ALL) {
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
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    val all = data?.products.orEmpty()
    val shopDefault = data?.shopDefault
    val lowCount = remember(all, shopDefault) { all.count { it.active && !it.isOut() && it.isLow(shopDefault) } }
    val outCount = remember(all) { all.count { it.active && it.isOut() } }
    val inactiveCount = remember(all) { all.count { !it.active } }

    val shown = remember(all, query, filter, categoryId, shopDefault) {
        val q = query.trim().lowercase()
        all.filter { p ->
            (categoryId == null || p.categoryId == categoryId) &&
                (q.isEmpty() || p.searchText.contains(q)) &&
                when (filter) {
                    StockFilter.ALL -> true
                    StockFilter.LOW -> p.active && !p.isOut() && p.isLow(shopDefault)
                    StockFilter.OUT -> p.active && p.isOut()
                    StockFilter.INACTIVE -> !p.active
                }
        }.let { list -> if (q.isNotEmpty()) list.sortedByDescending { it.barcode?.lowercase() == q } else list }
    }

    KScreen(
        topBar = {
            KTopBar(stringResource(R.string.products_title)) {
                KIconButton(Icons.AutoMirrored.Outlined.PlaylistAdd, { nav.navigate(Routes.QUICK_PRODUCTS) }, contentDescription = stringResource(R.string.products_quick_title))
                KIconButton(Icons.Outlined.MoveToInbox, { nav.navigate(Routes.PURCHASES) }, contentDescription = stringResource(R.string.purchases_title))
            }
        },
        fab = { KFab(Icons.Outlined.Add, { nav.navigate(Routes.productEdit("new")) }, stringResource(R.string.products_add)) },
    ) {
        KSearch(query, { query = it }, stringResource(R.string.products_search), onScan = { scanning = true }, scanLime = false)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = Edge, end = Edge, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KChip(stringResource(R.string.products_filter_all, all.size), filter == StockFilter.ALL, { filter = StockFilter.ALL })
            KChip(stringResource(R.string.products_filter_low, lowCount), filter == StockFilter.LOW, { filter = StockFilter.LOW })
            KChip(stringResource(R.string.products_filter_out, outCount), filter == StockFilter.OUT, { filter = StockFilter.OUT })
            KChip(
                if (inactiveCount > 0) stringResource(R.string.products_filter_inactive_n, inactiveCount) else stringResource(R.string.products_filter_inactive),
                filter == StockFilter.INACTIVE, { filter = StockFilter.INACTIVE },
            )
            // Filtering by category only makes sense with two or more of them.
            val cats = data?.categories.orEmpty()
            if (cats.size >= 2) cats.forEach { c ->
                val id = c.str("uuid")
                KChip(c.str("name"), categoryId == id, { categoryId = if (categoryId == id) null else id })
            }
        }

        when {
            data == null -> Unit
            all.isEmpty() -> KEmpty(
                Icons.Outlined.Inventory2,
                stringResource(R.string.products_empty_title),
                stringResource(R.string.products_empty_text),
                action = stringResource(R.string.products_quick_title),
                onAction = { nav.navigate(Routes.QUICK_PRODUCTS) },
            )
            shown.isEmpty() -> {
                val code = query.trim()
                val looksLikeCode = code.length >= 4 && code.all { it.isDigit() }
                KEmpty(
                    Icons.Outlined.SearchOff,
                    stringResource(R.string.products_not_found),
                    action = if (looksLikeCode) stringResource(R.string.products_add_with_barcode, code) else null,
                    onAction = if (looksLikeCode) ({ nav.navigate(Routes.productEdit("new", code)) }) else null,
                )
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                // Bottom: the FAB (54dp + 16dp margin) never covers the last row.
                contentPadding = PaddingValues(start = Edge, end = Edge, top = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canPrices && filter == StockFilter.ALL && query.isBlank() && categoryId == null) {
                    item(key = "value") { StockValueLine(data!!.stockValue) }
                }
                items(shown, key = { it.uuid }) { p ->
                    ProductRow(p, shopDefault) { nav.navigate(Routes.productEdit(p.uuid)) }
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
private fun StockValueLine(value: Double) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.products_stock_value), style = K.t.small, color = K.c.mu, modifier = Modifier.weight(1f))
        KMoney(value, size = 13.sp)
    }
}

@Composable
private fun ProductRow(p: ProductListItem, shopDefault: Double?, onClick: () -> Unit) {
    val kind = when {
        p.isOut() -> KTagKind.Red
        p.isLow(shopDefault) -> KTagKind.Low
        else -> KTagKind.Ok
    }
    val sub = buildList {
        if (!p.active) add(stringResource(R.string.products_inactive))
        if (p.variantCount > 0) add(stringResource(R.string.products_variant_count, p.variantCount))
        val code = p.barcode?.takeIf { it.isNotBlank() }
        if (code != null) add(code) else p.categoryName?.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
    KRow(
        title = p.name,
        titleColor = if (p.active) K.c.tx else K.c.mu,
        subtitle = if (sub.isNotEmpty()) ({ KSub(sub) }) else null,
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                KMoney(p.sellPrice)
                KTag(qtyUnit(p.stock, p.unit), kind)
            }
        },
        onClick = onClick,
    )
}
