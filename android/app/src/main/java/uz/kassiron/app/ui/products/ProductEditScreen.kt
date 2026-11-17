package uz.kassiron.app.ui.products

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.domain.ProductForm
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.VariantForm
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAlert
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KGroup
import uz.kassiron.app.ui.kit.KGroupRow
import uz.kassiron.app.ui.kit.KList
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KSwitch
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.kit.KValueChip
import uz.kassiron.app.ui.theme.K

// Add / edit a product (mockup 16).

private data class EditData(val product: DbRow?, val variants: List<DbRow>, val categories: List<DbRow>)

/** Which barcode field the camera fills. */
private enum class ScanTarget { PRODUCT, VARIANT }

private data class VariantDraft(val uuid: String?, val form: VariantForm, val active: Boolean = true)

/** Inline messages under the fields. */
internal data class FieldErrors(val name: String? = null, val barcode: String? = null, val price: String? = null, val unit: String? = null) {
    val any: Boolean get() = name != null || barcode != null || price != null || unit != null
}

/** The units offered as chips; anything else is "Boshqa". */
private val UNIT_CHIPS = listOf("dona", "kg", "litr", "metr")

/**
 * Create ("new", optionally with a [barcode] scanned at the till) or edit a
 * product. Saving goes back to where it was opened from.
 */
@Composable
fun ProductEditScreen(nav: NavHostController, uuid: String, barcode: String?) = ProductEdit(nav, uuid, barcode)

/** [preset]: the screenshot tests show a filled form / validation messages. */
@Composable
internal fun ProductEdit(
    nav: NavHostController,
    uuid: String,
    barcode: String?,
    preset: ProductForm? = null,
    presetErrors: FieldErrors = FieldErrors(),
    expanded: Boolean = false,
) {
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

    var form by remember { mutableStateOf(preset ?: if (isNew) ProductForm(barcode = barcode?.trim().orEmpty()) else null) }
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

    var errors by remember { mutableStateOf(presetErrors) }
    var saving by remember { mutableStateOf(false) }
    var scanTarget by remember { mutableStateOf<ScanTarget?>(null) }
    var variantDraft by remember { mutableStateOf<VariantDraft?>(null) }
    var categorySheet by remember { mutableStateOf(false) }
    var extraOpen by rememberSaveable { mutableStateOf(expanded) }
    var customUnit by rememberSaveable { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    // A new product: the cursor waits in "Nomi" (or in the price when the name came with a scan).
    LaunchedEffect(data != null) {
        if (isNew && data != null && preset == null) { delay(50); runCatching { nameFocus.requestFocus() } }
    }

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
        val check = FieldErrors(
            name = if (current.name.isBlank()) ctx.getString(R.string.products_err_name_required) else null,
            price = if ((isNew || canPrices) && Money.parse(current.sellPrice) == null) ctx.getString(R.string.products_err_price_required) else null,
            unit = if (current.unit.isBlank()) ctx.getString(R.string.products_err_unit_required) else null,
            barcode = barcodeOwner?.let { ctx.getString(R.string.products_barcode_owner, it) },
        )
        errors = check
        if (check.any) return
        saving = true
        scope.launch {
            val result = runCatching { io { ProductService.save(current, if (isNew) null else uuid, canPrices) } }
            saving = false
            result.onSuccess { nav.popBackStack() }.onFailure { e ->
                val msg = ctx.productError(e)
                errors = when ((e as? ProductException)?.code) {
                    ProductException.FILL_REQUIRED, ProductException.NAME_FORMULA, ProductException.NAME_TOO_LONG -> FieldErrors(name = msg)
                    ProductException.PRICE_REQUIRED, ProductException.NEGATIVE, ProductException.TOO_LARGE -> FieldErrors(price = msg)
                    ProductException.BARCODE_TAKEN, ProductException.BARCODE_TOO_LONG -> FieldErrors(barcode = msg)
                    ProductException.UNIT_TOO_LONG -> FieldErrors(unit = msg)
                    else -> { snackbar.showSnackbar(msg); FieldErrors() }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        KScreen(
            snackbar = snackbar,
            topBar = {
                KTopBar(stringResource(if (isNew) R.string.products_new_title else R.string.products_edit_title), onBack = { nav.popBackStack() })
            },
            bottomBar = if (canProducts && f != null) ({
                KBottomBar { KButton(stringResource(R.string.common_save), { save() }, Modifier.fillMaxWidth(), enabled = !saving) }
            }) else null,
        ) {
            val d = data
            when {
                !isNew && d != null && d.product == null ->
                    KEmpty(Icons.Outlined.Inventory2, stringResource(R.string.products_err_not_found))
                f == null || d == null -> Unit
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 20.dp)) {
                    if (!canProducts) {
                        KAlert(stringResource(R.string.products_no_permission), Icons.Outlined.Lock)
                        Spacer(Modifier.height(12.dp))
                    }
                    Column(Modifier.padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        KField(
                            f.name, { form = f.copy(name = it); if (errors.name != null) errors = errors.copy(name = null) },
                            Modifier.focusRequester(nameFocus),
                            label = stringResource(R.string.products_name),
                            placeholder = stringResource(R.string.products_name_ph),
                            error = errors.name,
                        )
                        KField(
                            f.barcode, { form = f.copy(barcode = it.trim()); errors = errors.copy(barcode = null) },
                            label = stringResource(R.string.products_barcode),
                            placeholder = stringResource(R.string.products_barcode_ph),
                            keyboard = KeyboardType.Number,
                            error = errors.barcode ?: barcodeOwner?.let { stringResource(R.string.products_barcode_owner, it) },
                            trailing = { ScanInside { scanTarget = ScanTarget.PRODUCT } },
                        )
                        UnitPicker(
                            unit = f.unit,
                            custom = customUnit || f.unit !in UNIT_CHIPS,
                            error = errors.unit,
                            onChip = { customUnit = false; form = f.copy(unit = it); errors = errors.copy(unit = null) },
                            onOther = { customUnit = true; if (f.unit in UNIT_CHIPS) form = f.copy(unit = "") },
                            onType = { form = f.copy(unit = it.take(20)); errors = errors.copy(unit = null) },
                        )
                        // Prices: editing needs "prices" (a new product needs a sell price to exist at all).
                        val sellEditable = canPrices || isNew
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (sellEditable) {
                                NumberField(
                                    f.sellPrice, { form = f.copy(sellPrice = numberInput(it)); errors = errors.copy(price = null) },
                                    Modifier.weight(1f), label = stringResource(R.string.products_sell_price), error = errors.price,
                                )
                            } else {
                                PickField(stringResource(R.string.products_sell_price), Money.format(Money.parse(f.sellPrice) ?: 0.0), "", {}, Modifier.weight(1f), chevron = false)
                            }
                            if (canPrices) {
                                NumberField(
                                    f.costPrice, { form = f.copy(costPrice = numberInput(it)) },
                                    Modifier.weight(1f), label = stringResource(R.string.products_cost_price),
                                )
                            }
                        }
                        val sell = Money.parse(f.sellPrice)
                        val cost = Money.parse(f.costPrice)
                        if (canPrices && sell != null && cost != null && sell < cost) {
                            Text(stringResource(R.string.products_below_cost), style = K.t.small, color = K.c.amb)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isNew) {
                                NumberField(
                                    f.initialStock, { form = f.copy(initialStock = numberInput(it)) },
                                    Modifier.weight(1f), label = stringResource(R.string.products_initial_stock),
                                )
                            } else {
                                val p = d.product!!
                                val active = d.variants.filter { it.str("status") == "active" }
                                val stock = if (active.isNotEmpty()) active.sumOf { it.dbl("stock_qty") } else p.dbl("stock_qty")
                                PickField(
                                    stringResource(R.string.products_stock), qtyUnit(stock, p.str("unit")), "",
                                    { nav.navigate(Routes.PURCHASE_NEW) }, Modifier.weight(1f),
                                )
                            }
                            val catName = d.categories.firstOrNull { it.str("uuid") == f.categoryId }?.str("name")
                            PickField(
                                stringResource(R.string.products_category), catName, stringResource(R.string.products_category_pick),
                                { categorySheet = true }, Modifier.weight(1f),
                            )
                        }
                        if (!isNew) Text(stringResource(R.string.products_stock_via_purchase), style = K.t.small, color = K.c.mu)
                    }

                    Spacer(Modifier.height(14.dp))
                    val variantCount = d.variants.size
                    KGroup {
                        KGroupRow(
                            stringResource(R.string.products_more),
                            value = if (variantCount > 0) stringResource(R.string.products_more_hint_variants, variantCount) else stringResource(R.string.products_more_hint),
                            first = true,
                            onClick = { extraOpen = !extraOpen },
                            trailing = { Icon(if (extraOpen) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, null, tint = K.c.mu, modifier = Modifier.size(20.dp)) },
                        )
                    }
                    AnimatedVisibility(extraOpen) {
                        Column {
                            ExtraFields(f, isNew) { form = it }
                            if (!isNew) {
                                Spacer(Modifier.height(14.dp))
                                KGroup {
                                    KGroupRow(
                                        stringResource(R.string.products_status),
                                        value = stringResource(if (f.active) R.string.products_active_hint else R.string.products_inactive_hint),
                                        first = true,
                                        trailing = { KSwitch(f.active) { form = f.copy(active = it) } },
                                    )
                                }
                            }
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
                                        active = v.str("status") == "active",
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (categorySheet) {
        val cats = data?.categories.orEmpty()
        PickSheet(
            title = stringResource(R.string.products_category),
            items = listOf(PickItem(null, stringResource(R.string.products_category_none))) + cats.map { PickItem(it.str("uuid"), it.str("name")) },
            selected = form?.categoryId,
            searchHint = stringResource(R.string.products_category_search),
            newTitle = { ctx.getString(R.string.products_category_new, it) },
            newSub = stringResource(R.string.products_category_new_hint),
            onPick = { id -> form = form?.copy(categoryId = id); categorySheet = false },
            onCreate = { name ->
                scope.launch {
                    runCatching { io { ProductService.findOrCreateCategory(name) } }
                        .onSuccess { id -> form = form?.copy(categoryId = id); categorySheet = false }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
            onDismiss = { categorySheet = false },
        )
    }

    variantDraft?.let { draft ->
        val product = data?.product
        if (product != null) {
            val first = data?.variants?.none { it.str("status") == "active" } == true
            VariantSheet(
                draft = draft,
                unit = product.str("unit"),
                parentStock = if (first && draft.uuid == null) product.dbl("stock_qty") else 0.0,
                canPrices = canPrices,
                onChange = { variantDraft = it },
                onScan = { scanTarget = ScanTarget.VARIANT },
                onDismiss = { variantDraft = null },
                onSave = {
                    scope.launch {
                        runCatching {
                            io {
                                val id = ProductService.saveVariant(uuid, draft.uuid, draft.form, canPrices)
                                if (draft.uuid != null) ProductService.setVariantStatus(id, draft.active)
                            }
                        }.onSuccess {
                            variantDraft = null
                            snackbar.showSnackbar(ctx.getString(if (draft.uuid == null) R.string.products_variant_created else R.string.products_variant_updated))
                        }.onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                    }
                },
            )
        }
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

/** Birlik: dona · kg · litr · metr · Boshqa (then a field for the unit's name). */
@Composable
private fun UnitPicker(unit: String, custom: Boolean, error: String?, onChip: (String) -> Unit, onOther: () -> Unit, onType: (String) -> Unit) {
    Column {
        FieldLabel(stringResource(R.string.products_unit))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UNIT_CHIPS.forEach { u -> KValueChip(u, !custom && unit == u, { onChip(u) }, Modifier.weight(1f)) }
            val otherLabel = if (custom && unit.isNotBlank()) unit else stringResource(R.string.products_unit_other)
            KValueChip(otherLabel, custom, onOther, Modifier.weight(1.25f))
        }
        if (custom) {
            KField(
                unit, onType, Modifier.padding(top = 8.dp),
                placeholder = stringResource(R.string.products_unit_ph), error = error,
            )
        } else if (error != null) {
            Text(error, style = K.t.small, color = K.c.red, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

/** MXIK, o‘ram kodi, QQS, kam qoldiq chegarasi, karobka hajmi. */
@Composable
private fun ExtraFields(f: ProductForm, isNew: Boolean, onChange: (ProductForm) -> Unit) {
    val shopDefault = ProductDefaults.rememberShopDefault()
    Column(Modifier.padding(start = Edge, end = Edge, top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KField(
            f.mxik, { onChange(f.copy(mxik = it.filter(Char::isDigit).take(20))) },
            label = stringResource(R.string.products_mxik), placeholder = stringResource(R.string.products_mxik_ph),
            keyboard = KeyboardType.Number, hint = stringResource(R.string.products_mxik_hint),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KField(
                f.packageCode, { onChange(f.copy(packageCode = it.filter(Char::isDigit).take(12))) }, Modifier.weight(1f),
                label = stringResource(R.string.products_package_code), placeholder = "1500543", keyboard = KeyboardType.Number,
            )
            NumberField(f.vatRate, { onChange(f.copy(vatRate = numberInput(it))) }, Modifier.weight(1f), label = stringResource(R.string.products_vat), placeholder = "12")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                f.lowStockThreshold, { onChange(f.copy(lowStockThreshold = numberInput(it))) }, Modifier.weight(1f),
                label = stringResource(R.string.products_low_threshold), placeholder = shopDefault?.let { Money.qty(it) } ?: "5",
            )
            KField(
                f.packSize, { onChange(f.copy(packSize = it.filter(Char::isDigit).take(6))) }, Modifier.weight(1f),
                label = stringResource(R.string.products_pack_size), placeholder = "12", keyboard = KeyboardType.Number,
            )
        }
        Text(
            if (shopDefault != null) stringResource(R.string.products_low_threshold_hint_default, Money.qty(shopDefault))
            else stringResource(R.string.products_low_threshold_hint),
            style = K.t.small, color = K.c.mu,
        )
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
) {
    KSection(
        stringResource(R.string.products_variants),
        action = if (!isNew && canEdit) stringResource(R.string.products_variant_add_short) else null,
        onAction = if (!isNew && canEdit) onAdd else null,
    )
    if (isNew || product == null) {
        Text(stringResource(R.string.products_variants_after_save), style = K.t.small, color = K.c.mu, modifier = Modifier.padding(horizontal = Edge + 2.dp))
        return
    }
    if (variants.isEmpty()) {
        Text(stringResource(R.string.products_variants_hint), style = K.t.small, color = K.c.mu, modifier = Modifier.padding(horizontal = Edge + 2.dp))
        return
    }
    val unit = product.str("unit")
    val samePrice = stringResource(R.string.products_same_as_product)
    KList {
        variants.forEach { v ->
            val active = v.str("status") == "active"
            val parts = buildList {
                add(v.dblOrNull("sell_price")?.let { Money.format(it) } ?: samePrice)
                if (canPrices) v.dblOrNull("cost_price")?.let { add(stringResource(R.string.products_cost_short, Money.format(it))) }
                v.strOrNull("barcode")?.takeIf { it.isNotBlank() }?.let(::add)
                if (!active) add(stringResource(R.string.products_inactive))
            }
            val stock = v.dbl("stock_qty")
            KRow(
                title = v.str("variant_label"),
                titleColor = if (active) K.c.tx else K.c.mu,
                subtitle = { KSub(parts.joinToString(" · ")) },
                trailing = { KTag(qtyUnit(stock, unit), if (stock <= 0) KTagKind.Red else KTagKind.Ok) },
                onClick = if (canEdit) ({ onEdit(v) }) else null,
            )
        }
    }
}

@Composable
private fun VariantSheet(
    draft: VariantDraft,
    unit: String,
    parentStock: Double,
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
    KSheet(onDismiss) {
        KSheetTitle(stringResource(if (draft.uuid == null) R.string.products_variant_add else R.string.products_variant_edit), onDismiss)
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KField(form.label, { set(form.copy(label = it)) }, label = stringResource(R.string.products_variant_label), placeholder = stringResource(R.string.products_variant_label_ph))
            KField(
                form.barcode, { set(form.copy(barcode = it.trim())) },
                label = stringResource(R.string.products_barcode), placeholder = stringResource(R.string.products_barcode_ph),
                keyboard = KeyboardType.Number,
                error = owner?.let { stringResource(R.string.products_barcode_owner, it) },
                trailing = { ScanInside(onScan) },
            )
            if (editPrices) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(form.sellPrice, { set(form.copy(sellPrice = numberInput(it))) }, Modifier.weight(1f), label = stringResource(R.string.products_sell_price), placeholder = stringResource(R.string.products_variant_price_ph))
                    if (canPrices) NumberField(form.costPrice, { set(form.copy(costPrice = numberInput(it))) }, Modifier.weight(1f), label = stringResource(R.string.products_cost_price), placeholder = stringResource(R.string.products_variant_price_ph))
                }
                Text(stringResource(R.string.products_variant_price_hint), style = K.t.small, color = K.c.mu)
            }
            if (draft.uuid == null) {
                if (parentStock > 0) {
                    SwitchLine(stringResource(R.string.products_variant_take_parent, qtyUnit(parentStock, unit)), form.takeParentStock) { set(form.copy(takeParentStock = it)) }
                }
                if (!(parentStock > 0 && form.takeParentStock)) {
                    NumberField(form.initialStock, { set(form.copy(initialStock = numberInput(it))) }, label = stringResource(R.string.products_initial_stock))
                }
            } else {
                SwitchLine(stringResource(R.string.products_variant_active), draft.active) { onChange(draft.copy(active = it)) }
            }
            KButton(stringResource(R.string.common_save), onSave, Modifier.fillMaxWidth(), enabled = form.label.isNotBlank())
        }
    }
}

@Composable
private fun SwitchLine(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(text, style = K.t.body, color = K.c.tx, modifier = Modifier.weight(1f).padding(end = 12.dp))
        KSwitch(checked, onChange)
    }
}
