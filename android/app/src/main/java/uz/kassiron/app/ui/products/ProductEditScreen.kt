package uz.kassiron.app.ui.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.domain.ProductForm
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.VariantForm
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.AppTopBar
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.BigButton
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io

private data class EditData(val product: DbRow?, val variants: List<DbRow>, val categories: List<DbRow>)

/** Which barcode field the camera fills. */
private enum class ScanTarget { PRODUCT, VARIANT }

private data class VariantDraft(val uuid: String?, val form: VariantForm)

/**
 * Create ("new", optionally with a [barcode] scanned at the till) or edit a
 * product, with its variants. Saving goes back to where it was opened from.
 */
@Composable
fun ProductEditScreen(nav: NavHostController, uuid: String, barcode: String?) {
    val isNew = uuid == "new" || uuid.isBlank()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val user by App.session.user.collectAsState()
    val canPrices = user?.can("prices") == true
    val canProducts = user?.can("products") == true

    val data by dbState<EditData?>(null, uuid) {
        EditData(
            product = if (isNew) null else ProductService.find(App.db, uuid),
            variants = if (isNew) emptyList() else ProductService.variants(App.db, uuid),
            categories = ProductService.categories(App.db),
        )
    }

    var form by remember { mutableStateOf<ProductForm?>(if (isNew) ProductForm(barcode = barcode?.trim().orEmpty()) else null) }
    LaunchedEffect(data) {
        val p = data?.product
        if (form == null && p != null) {
            form = ProductForm(
                name = p.str("name"),
                barcode = p.str("barcode"),
                unit = p.str("unit").ifBlank { "dona" },
                sellPrice = editNumber(p.dbl("sell_price")),
                costPrice = editNumber(p.dbl("cost_price")),
                categoryId = p.strOrNull("category_id"),
                lowStockThreshold = editNumber(p.dblOrNull("low_stock_threshold")),
                packSize = p.strOrNull("pack_size")?.substringBefore('.') ?: "",
                mxik = p.str("mxik"),
                packageCode = p.str("package_code"),
                vatRate = editNumber(p.dblOrNull("vat_rate")),
                active = p.str("status") == "active",
            )
        }
    }

    var saving by remember { mutableStateOf(false) }
    var scanTarget by remember { mutableStateOf<ScanTarget?>(null) }
    var variantDraft by remember { mutableStateOf<VariantDraft?>(null) }
    var newCategory by remember { mutableStateOf<String?>(null) }
    var extraOpen by rememberSaveable { mutableStateOf(false) }

    // Live "this barcode is already X" (the save checks again in its transaction).
    val f = form
    val barcodeOwner by produceState<String?>(null, f?.barcode) {
        val code = f?.barcode?.trim().orEmpty()
        if (code.isEmpty()) { value = null; return@produceState }
        delay(150)
        value = io { ProductService.barcodeOwner(App.db, code, if (isNew) null else uuid) }
    }

    fun save() {
        val current = form ?: return
        if (saving) return
        saving = true
        scope.launch {
            val result = runCatching { io { ProductService.save(current, if (isNew) null else uuid, canPrices) } }
            saving = false
            result.onSuccess { nav.popBackStack() }
                .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
        }
    }

    ProductsScaffold(
        snackbar = snackbar,
        topBar = {
            AppTopBar(
                stringResource(if (isNew) R.string.products_new_title else R.string.products_edit_title),
                onBack = { nav.popBackStack() },
            ) {
                if (canProducts && form != null) {
                    IconButton(onClick = { save() }, enabled = !saving) { Icon(Icons.Filled.Check, stringResource(R.string.common_save)) }
                }
            }
        },
    ) { padding ->
        val d = data
        when {
            !isNew && d != null && d.product == null ->
                EmptyState(Icons.Filled.Inventory2, stringResource(R.string.products_err_not_found), Modifier.padding(padding))
            f == null || d == null -> Unit
            else -> Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!canProducts) {
                    Text(stringResource(R.string.products_no_permission), color = MaterialTheme.colorScheme.error)
                }
                FormField(f.name, { form = f.copy(name = it) }, stringResource(R.string.products_name) + " *")
                FormField(
                    f.barcode, { form = f.copy(barcode = it.trim()) }, stringResource(R.string.products_barcode),
                    isError = barcodeOwner != null,
                    supporting = barcodeOwner?.let { stringResource(R.string.products_barcode_owner, it) },
                    trailing = { ScanButton { scanTarget = ScanTarget.PRODUCT } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UnitField(f.unit, { form = f.copy(unit = it) }, Modifier.weight(1f))
                    CategoryPicker(
                        categories = d.categories,
                        selected = f.categoryId,
                        onSelect = { form = f.copy(categoryId = it) },
                        onNew = { newCategory = "" },
                        modifier = Modifier.weight(1.3f),
                    )
                }

                // Prices: editing needs "prices" (a new product needs a sell price to exist at all).
                val sellEditable = canPrices || isNew
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        f.sellPrice, { form = f.copy(sellPrice = numberInput(it)) }, stringResource(R.string.products_sell_price) + " *",
                        modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal, readOnly = !sellEditable, enabled = sellEditable,
                    )
                    if (canPrices) {
                        FormField(
                            f.costPrice, { form = f.copy(costPrice = numberInput(it)) }, stringResource(R.string.products_cost_price),
                            modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                        )
                    }
                }
                val sell = Money.parse(f.sellPrice)
                val cost = Money.parse(f.costPrice)
                if (canPrices && sell != null && cost != null && sell < cost) {
                    Text(stringResource(R.string.products_below_cost), color = StockColors.Low, style = MaterialTheme.typography.bodyMedium)
                }

                if (isNew) {
                    FormField(
                        f.initialStock, { form = f.copy(initialStock = numberInput(it)) },
                        stringResource(R.string.products_initial_stock, f.unit.ifBlank { "dona" }),
                        keyboardType = KeyboardType.Decimal,
                        supporting = stringResource(R.string.products_initial_stock_hint),
                    )
                } else {
                    StockCard(d.product!!, d.variants) { nav.navigate(Routes.PURCHASE_NEW) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(
                        f.lowStockThreshold, { form = f.copy(lowStockThreshold = numberInput(it)) }, stringResource(R.string.products_low_threshold),
                        modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                    )
                    FormField(
                        f.packSize, { form = f.copy(packSize = it.filter(Char::isDigit)) }, stringResource(R.string.products_pack_size),
                        modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                    )
                }
                val shopDefault = ProductDefaults.rememberShopDefault()
                Text(
                    if (shopDefault != null) stringResource(R.string.products_low_threshold_hint_default, Money.qty(shopDefault))
                    else stringResource(R.string.products_low_threshold_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // MXIK / package code / VAT — optional, folded away.
                Row(
                    Modifier.fillMaxWidth().clickable { extraOpen = !extraOpen }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.products_fiscal_section), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(if (extraOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                }
                AnimatedVisibility(extraOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(
                            f.mxik, { form = f.copy(mxik = it.filter(Char::isDigit).take(20)) }, stringResource(R.string.products_mxik),
                            keyboardType = KeyboardType.Number, supporting = stringResource(R.string.products_mxik_hint),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormField(
                                f.packageCode, { form = f.copy(packageCode = it.filter(Char::isDigit).take(12)) }, stringResource(R.string.products_package_code),
                                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number,
                            )
                            FormField(
                                f.vatRate, { form = f.copy(vatRate = numberInput(it)) }, stringResource(R.string.products_vat),
                                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Decimal,
                            )
                        }
                    }
                }

                if (!isNew) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.products_active), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(if (f.active) R.string.products_active_hint else R.string.products_inactive_hint),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = f.active, onCheckedChange = { form = f.copy(active = it) })
                    }
                }

                if (canProducts) {
                    BigButton(stringResource(R.string.common_save), onClick = { save() }, enabled = !saving)
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                VariantsSection(
                    isNew = isNew,
                    product = d.product,
                    variants = d.variants,
                    canPrices = canPrices,
                    canEdit = canProducts,
                    onAdd = {
                        val first = d.variants.none { it.str("status") == "active" }
                        val parentStock = d.product?.dbl("stock_qty") ?: 0.0
                        variantDraft = VariantDraft(
                            null,
                            VariantForm(
                                initialStock = if (first && parentStock > 0) editNumber(parentStock) else "",
                                takeParentStock = first && parentStock > 0,
                            ),
                        )
                    },
                    onEdit = { v ->
                        variantDraft = VariantDraft(
                            v.str("uuid"),
                            VariantForm(
                                label = v.str("variant_label"),
                                barcode = v.str("barcode"),
                                sellPrice = editNumber(v.dblOrNull("sell_price")),
                                costPrice = editNumber(v.dblOrNull("cost_price")),
                            ),
                        )
                    },
                    onToggle = { v, active ->
                        scope.launch { runCatching { io { ProductService.setVariantStatus(v.str("uuid"), active) } } }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    variantDraft?.let { draft ->
        val product = data?.product
        if (product != null) {
            val first = data?.variants?.none { it.str("status") == "active" } == true
            VariantDialog(
                draft = draft,
                unit = product.str("unit"),
                parentStock = if (first && draft.uuid == null) product.dbl("stock_qty") else 0.0,
                productUuid = uuid,
                canPrices = canPrices,
                onChange = { variantDraft = it },
                onScan = { scanTarget = ScanTarget.VARIANT },
                onDismiss = { variantDraft = null },
                onSave = {
                    scope.launch {
                        runCatching { io { ProductService.saveVariant(uuid, draft.uuid, draft.form, canPrices) } }
                            .onSuccess {
                                variantDraft = null
                                snackbar.showSnackbar(ctx.getString(if (draft.uuid == null) R.string.products_variant_created else R.string.products_variant_updated))
                            }
                            .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                    }
                },
            )
        }
    }

    newCategory?.let { name ->
        NameDialog(
            title = stringResource(R.string.categories_add),
            label = stringResource(R.string.categories_name),
            value = name,
            onChange = { newCategory = it },
            onDismiss = { newCategory = null },
            onSave = {
                scope.launch {
                    runCatching { io { ProductService.findOrCreateCategory(name) } }
                        .onSuccess { id -> newCategory = null; form = form?.copy(categoryId = id) }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
        )
    }

    when (scanTarget) {
        ScanTarget.PRODUCT -> BarcodeScannerDialog(
            onResult = { code -> scanTarget = null; form = form?.copy(barcode = code.trim()) },
            onDismiss = { scanTarget = null },
        )
        ScanTarget.VARIANT -> BarcodeScannerDialog(
            onResult = { code ->
                scanTarget = null
                variantDraft = variantDraft?.let { it.copy(form = it.form.copy(barcode = code.trim())) }
            },
            onDismiss = { scanTarget = null },
        )
        null -> Unit
    }
}

object ProductDefaults {
    /** The shop-wide low stock threshold, read off the main thread. */
    @Composable
    fun rememberShopDefault(): Double? {
        val v by dbState<Double?>(null) { ProductService.shopLowStockDefault() }
        return v
    }
}

@Composable
private fun CategoryPicker(
    categories: List<DbRow>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val name = categories.firstOrNull { it.str("uuid") == selected }?.str("name").orEmpty()
    Box(modifier) {
        FormField(
            value = name,
            onChange = {},
            label = stringResource(R.string.products_category),
            readOnly = true,
            trailing = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        // The read-only field swallows taps; a transparent layer opens the menu.
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.products_category_new)) },
                leadingIcon = { Icon(Icons.Filled.Add, null) },
                onClick = { open = false; onNew() },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.products_category_none)) }, onClick = { open = false; onSelect(null) })
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.str("name")) }, onClick = { open = false; onSelect(c.str("uuid")) })
            }
        }
    }
}

@Composable
private fun StockCard(product: DbRow, variants: List<DbRow>, onPurchase: () -> Unit) {
    val active = variants.filter { it.str("status") == "active" }
    val unit = product.str("unit")
    val stock = if (active.isNotEmpty()) active.sumOf { it.dbl("stock_qty") } else product.dbl("stock_qty")
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (active.isNotEmpty()) R.string.products_stock_variants else R.string.products_stock),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        qtyUnit(stock, unit),
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (stock <= 0) StockColors.Out else MaterialTheme.colorScheme.onSurface,
                    )
                }
                OutlinedButton(onClick = onPurchase, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.MoveToInbox, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.purchases_new_short))
                }
            }
            Text(stringResource(R.string.products_stock_via_purchase), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (active.isNotEmpty() && product.dbl("stock_qty") > 0) {
                Text(
                    stringResource(R.string.products_parent_leftover, qtyUnit(product.dbl("stock_qty"), unit)),
                    style = MaterialTheme.typography.bodySmall, color = StockColors.Low,
                )
            }
        }
    }
}

@Composable
private fun VariantsSection(
    isNew: Boolean,
    product: DbRow?,
    variants: List<DbRow>,
    canPrices: Boolean,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onEdit: (DbRow) -> Unit,
    onToggle: (DbRow, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.products_variants), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (!isNew && canEdit) {
                TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.products_variant_add))
                }
            }
        }
        Text(
            stringResource(if (isNew) R.string.products_variants_after_save else R.string.products_variants_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (product != null) {
            val unit = product.str("unit")
            variants.forEach { v ->
                val active = v.str("status") == "active"
                Card(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().alpha(if (active) 1f else 0.55f).clickable(enabled = canEdit) { onEdit(v) },
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.str("variant_label"), style = MaterialTheme.typography.titleMedium)
                            val price = v.dblOrNull("sell_price")?.let { Money.format(it) } ?: stringResource(R.string.products_same_as_product)
                            val parts = buildList {
                                add(price)
                                if (canPrices) v.dblOrNull("cost_price")?.let { add(stringResource(R.string.products_cost_short, Money.format(it))) }
                                v.strOrNull("barcode")?.takeIf { it.isNotBlank() }?.let(::add)
                            }
                            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            qtyUnit(v.dbl("stock_qty"), unit),
                            fontWeight = FontWeight.Bold,
                            color = if (v.dbl("stock_qty") <= 0) StockColors.Out else MaterialTheme.colorScheme.onSurface,
                        )
                        if (canEdit) {
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = active, onCheckedChange = { onToggle(v, it) })
                        }
                    }
                }
            }
            if (variants.isEmpty() && !isNew) {
                Text(stringResource(R.string.products_variants_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VariantDialog(
    draft: VariantDraft,
    unit: String,
    parentStock: Double,
    productUuid: String,
    canPrices: Boolean,
    onChange: (VariantDraft) -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val form = draft.form
    val owner by produceState<String?>(null, form.barcode) {
        val code = form.barcode.trim()
        if (code.isEmpty()) { value = null; return@produceState }
        delay(150)
        value = io { ProductService.barcodeOwner(App.db, code, null, draft.uuid) }
    }
    fun set(f: VariantForm) = onChange(draft.copy(form = f))
    val editPrices = canPrices || draft.uuid == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (draft.uuid == null) R.string.products_variant_add else R.string.products_variant_edit), fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FormField(
                    form.label, { set(form.copy(label = it)) }, stringResource(R.string.products_variant_label) + " *",
                    supporting = stringResource(R.string.products_variant_label_hint),
                )
                FormField(
                    form.barcode, { set(form.copy(barcode = it.trim())) }, stringResource(R.string.products_barcode),
                    isError = owner != null,
                    supporting = owner?.let { stringResource(R.string.products_barcode_owner, it) },
                    trailing = { ScanButton(onScan) },
                )
                FormField(
                    form.sellPrice, { set(form.copy(sellPrice = numberInput(it))) }, stringResource(R.string.products_sell_price),
                    keyboardType = KeyboardType.Decimal, enabled = editPrices,
                    supporting = stringResource(R.string.products_variant_price_hint),
                )
                if (canPrices) {
                    FormField(
                        form.costPrice, { set(form.copy(costPrice = numberInput(it))) }, stringResource(R.string.products_cost_price),
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                if (draft.uuid == null) {
                    if (parentStock > 0) {
                        Row(
                            Modifier.fillMaxWidth().clickable { set(form.copy(takeParentStock = !form.takeParentStock)) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = form.takeParentStock, onCheckedChange = { set(form.copy(takeParentStock = it)) })
                            Text(stringResource(R.string.products_variant_take_parent, qtyUnit(parentStock, unit)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!(parentStock > 0 && form.takeParentStock)) {
                        FormField(
                            form.initialStock, { set(form.copy(initialStock = numberInput(it))) },
                            stringResource(R.string.products_initial_stock, unit),
                            keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done, onIme = onSave,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = form.label.isNotBlank()) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
