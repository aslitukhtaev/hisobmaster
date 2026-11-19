package uz.kassiron.app.ui.till

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.domain.SaleException
import uz.kassiron.app.domain.SaleService
import uz.kassiron.app.domain.Wholesale
import uz.kassiron.app.print.BluetoothPrinter
import uz.kassiron.app.print.PrintException
import uz.kassiron.app.print.PrinterSettings
import uz.kassiron.app.print.ReceiptData
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAlert
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KChips
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KOption
import uz.kassiron.app.ui.kit.KSegmented
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSearch
import uz.kassiron.app.ui.kit.KStepper
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.kit.kShadow
import uz.kassiron.app.ui.switchTab
import uz.kassiron.app.ui.theme.K
import androidx.compose.material.icons.outlined.ErrorOutline
import uz.kassiron.app.ui.kit.KCardTone

internal fun haptic(view: View, confirm: Boolean = false) {
    view.performHapticFeedback(if (confirm) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.KEYBOARD_TAP)
}

/** A refused sale in plain words. */
internal fun saleErrorText(context: Context, e: SaleException): String {
    val s = e.subject ?: ""
    return when (e.code) {
        "empty_cart" -> context.getString(R.string.till_err_empty_cart)
        "invalid_product" -> context.getString(R.string.till_err_invalid_product)
        "invalid_qty" -> context.getString(R.string.till_err_invalid_qty)
        "qty_must_be_whole" -> context.getString(R.string.till_err_qty_whole, s)
        "variant_required" -> context.getString(R.string.till_err_variant_required, s)
        "invalid_discount" -> context.getString(R.string.till_err_invalid_discount)
        "discount_too_large" -> context.getString(R.string.till_err_discount_too_large)
        "customer_required_for_debt" -> context.getString(R.string.till_err_customer_required)
        "insufficient_stock" -> context.getString(R.string.till_err_insufficient_stock, s)
        "wholesale_not_allowed" -> context.getString(R.string.till_err_wholesale_not_allowed)
        else -> context.getString(R.string.till_err_unknown)
    }
}

/** Prints a sale's receipt; errors as a snackbar with a way to the printer settings. */
internal fun printReceipt(
    context: Context,
    scope: CoroutineScope,
    snackbar: SnackbarHostState,
    nav: NavHostController,
    saleUuid: String,
) {
    scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        launch { snackbar.showSnackbar(context.getString(R.string.print_printing), duration = SnackbarDuration.Indefinite) }
        try {
            BluetoothPrinter.printSale(context, saleUuid)
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(context.getString(R.string.print_done), duration = SnackbarDuration.Short)
        } catch (e: Exception) {
            snackbar.currentSnackbarData?.dismiss()
            val code = (e as? PrintException)?.code
            val toSettings = code == "not_selected" || code == "no_permission" || code == "connect_failed" || code == "not_found"
            val r = snackbar.showSnackbar(
                PrinterSettings.message(context, e),
                actionLabel = if (toSettings) context.getString(R.string.print_settings) else null,
                duration = SnackbarDuration.Long,
            )
            if (r == SnackbarResult.ActionPerformed) nav.navigate(Routes.PRINTER)
        }
    }
}

/** Which sheet is open on the till (also lets the screenshot tests open one). */
internal enum class TillSheet { PAY, CUSTOMER }

/** One line of the till's list: a cart line (selected, with a stepper) or a product to add. */
private sealed interface TillEntry {
    val key: String
    data class Line(val item: CartItem) : TillEntry { override val key get() = "l:" + item.key }
    data class Product(val product: TillProduct) : TillEntry { override val key get() = "p:" + product.uuid }
}

@Composable
fun TillScreen(nav: NavHostController) {
    TillContent(nav, viewModel())
}

/**
 * The till (mockups 6, 7, 11, 32). [startSheet], [customerQuery] and
 * [offlinePending] exist for the screenshot tests.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TillContent(
    nav: NavHostController,
    vm: TillViewModel,
    startSheet: TillSheet? = null,
    customerQuery: String = "",
    offlinePending: Long? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val user by App.session.user.collectAsState()
    val sync by App.sync.state.collectAsState()
    val canDiscount = user?.can("discount") == true
    val canProducts = user?.can("products") == true
    // Optom selling exists only when the shop turned it on (website settings); re-read after each sync.
    val wholesaleSetting by dbState<Boolean?>(null) { Wholesale.enabled() }
    // The toggle needs the shop setting and the cashier's own "wholesale" permission.
    val wholesaleOn = wholesaleSetting == true && user?.can(Wholesale.PERMISSION) == true
    // Turned off on the website while an optom sale was open: back to chakana (not before the setting is read).
    LaunchedEffect(wholesaleSetting, user) { if (wholesaleSetting != null && !wholesaleOn && vm.wholesale) vm.setWholesaleMode(false) }
    val optom = wholesaleOn && vm.wholesale

    var showScanner by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf(startSheet == TillSheet.PAY) }
    var showCustomer by remember { mutableStateOf(startSheet == TillSheet.CUSTOMER) }
    var variantFor by remember { mutableStateOf<TillProduct?>(null) }
    var qtyFor by remember { mutableStateOf<CartItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var selling by remember { mutableStateOf(false) }
    var payError by remember { mutableStateOf<String?>(null) }

    val q = vm.query
    val categories by dbState(emptyList<Pair<String, String>>()) { TillData.categories() }
    // A chip only means something with two or more categories; a category that went away clears itself.
    val showChips = categories.size >= 2
    val cat = vm.category?.takeIf { c -> showChips && categories.any { it.first == c } }
    val results by dbState<List<TillProduct>?>(null, q, cat) { TillData.search(q, cat) }

    fun say(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun noStock(name: String, stock: Double, unit: String) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        say(
            if (stock <= 0) context.getString(R.string.till_no_stock_out, name)
            else context.getString(R.string.till_no_stock, name, Money.qty(stock) + " " + unit),
        )
    }

    fun add(product: TillProduct, variant: TillVariant?) {
        if (product.variantCount > 0 && variant == null) {
            showScanner = false
            variantFor = product
            return
        }
        when (val r = vm.add(product, variant)) {
            is TillViewModel.AddResult.Added -> { haptic(view); vm.lastSale = null }
            is TillViewModel.AddResult.NoStock -> noStock(r.name, r.stock, r.unit)
        }
    }

    fun plus(item: CartItem) {
        if (vm.setQty(item.key, item.qty + 1)) haptic(view)
        else noStock(item.name + (item.variantLabel?.let { " ($it)" } ?: ""), item.stock, item.unit)
    }

    fun minus(item: CartItem) {
        vm.setQty(item.key, if (item.qty > 1) item.qty - 1 else 0.0)
        haptic(view)
    }

    fun unknownCode(code: String) {
        showScanner = false
        say(
            context.getString(R.string.till_unknown_barcode, code),
            action = if (canProducts) context.getString(R.string.till_add_product) else null,
        ) { nav.navigate(Routes.productEdit("new", code)) }
    }

    fun handleCode(code: String) {
        scope.launch {
            when (val hit = io { TillData.byBarcode(code) }) {
                is BarcodeHit.Variant -> add(hit.product, hit.variant)
                is BarcodeHit.Product -> add(hit.product, null)
                null -> unknownCode(code)
            }
        }
    }

    // One Enter at a time: scanners that end a code with CR+LF, and keyboards
    // that send both the IME action and an Enter key, would otherwise add the
    // item twice before the first lookup has cleared the field.
    val submitting = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    // Enter in the search field: a scanner's code (keyboard wedge) or a single match.
    fun submitSearch() {
        val text = vm.query.trim()
        if (text.isEmpty()) return
        if (!submitting.compareAndSet(false, true)) return
        scope.launch {
            try {
                val hit = io { TillData.byBarcode(text) }
                when {
                    hit is BarcodeHit.Variant -> { vm.query = ""; add(hit.product, hit.variant) }
                    hit is BarcodeHit.Product -> { vm.query = ""; add(hit.product, null) }
                    else -> {
                        val found = io { TillData.search(text) }
                        if (found.size == 1) { vm.query = ""; add(found[0], null) }
                        else if (text.length >= 4 && text.all(Char::isLetterOrDigit) && text.any(Char::isDigit) && found.isEmpty()) {
                            vm.query = ""
                            unknownCode(text)
                        }
                    }
                }
            } finally {
                submitting.set(false)
            }
        }
    }

    fun sell() {
        if (selling) return
        val split = vm.split(canDiscount)
        if (split.qarz > 0.005 && vm.customer == null) {
            // Debt needs a customer: open the picker right away instead of only complaining.
            payError = null
            showCustomer = true
            return
        }
        val (naqd, karta) = vm.tendered(split.total)
        val lines = vm.lines()
        val discount = vm.discount(canDiscount)
        val customer = vm.customer?.uuid
        selling = true
        payError = null
        scope.launch {
            try {
                val priceType = if (optom) Wholesale.WHOLESALE else Wholesale.RETAIL
                val sale = io { SaleService.create(lines, naqd, karta, discount, customer, priceType) }
                vm.lastSale = LastSale(sale.uuid, sale.receiptNo, sale.total, sale.change, sale.qarz)
                vm.clear()
                vm.query = ""
                showPay = false
                haptic(view, confirm = true)
            } catch (e: SaleException) {
                payError = saleErrorText(context, e)
            } catch (e: Exception) {
                payError = context.getString(R.string.till_err_unknown)
            } finally {
                selling = false
            }
        }
    }

    val focus = remember { FocusRequester() }
    val hardwareKeyboard = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS
    // With a scanner or keyboard attached, the search field takes focus back
    // after a sale and whenever a sheet closes, so the next scan isn't lost.
    val sheetOpen = showPay || showCustomer || showScanner || variantFor != null || qtyFor != null || confirmClear
    LaunchedEffect(hardwareKeyboard, sheetOpen, vm.lastSale) {
        if (hardwareKeyboard && !sheetOpen) {
            kotlinx.coroutines.delay(120)
            runCatching { focus.requestFocus() }
        }
    }

    // Unsent sales are shown whenever sending is stuck: any sync error, or no
    // successful sync for a few minutes (not only "no internet").
    val stale = sync.lastSyncAt > 0 && System.currentTimeMillis() - sync.lastSyncAt > 5 * 60_000L
    val pending = offlinePending ?: if (sync.pendingSales > 0 && (sync.error != null || stale)) sync.pendingSales else 0L
    val offlineText = if (offlinePending != null || sync.error == "network") R.string.till_offline else R.string.till_unsent

    // Prices and stock of the cart lines follow the database (a price changed
    // on the website and synced while the cart is open): the pay sheet then
    // shows exactly what the sale will charge.
    val dbTick by App.db.changes.collectAsState()
    LaunchedEffect(dbTick) {
        if (vm.cart.isEmpty()) return@LaunchedEffect
        val items = vm.cart.toList()
        val fresh = io { items.map { it.key to TillData.fresh(it) } }
        vm.applyFresh(fresh)
    }
    val cartEmpty = vm.cart.isEmpty()

    KScreen(
        snackbar = snackbar,
        bottomBar = if (cartEmpty) null else {
            {
                CartBar(
                    count = vm.itemCount,
                    total = vm.subtotal,
                    onPay = { payError = null; showPay = true },
                    onClear = { confirmClear = true },
                    wholesaleOn = wholesaleOn,
                    optom = optom,
                    onOptom = { on -> vm.setWholesaleMode(on); haptic(view) },
                )
            }
        },
    ) {
        Spacer(Modifier.height(6.dp))
        if (pending > 0) {
            KAlert(stringResource(offlineText, pending), Icons.Outlined.CloudOff)
            Spacer(Modifier.height(10.dp))
        }
        if (sync.rejected > 0) {
            KAlert(stringResource(R.string.sync_rejected, sync.rejected.toInt()), Icons.Outlined.ErrorOutline, tone = KCardTone.Red)
            Spacer(Modifier.height(10.dp))
        }
        KSearch(
            value = vm.query,
            onChange = { vm.query = it },
            placeholder = stringResource(R.string.till_search_hint),
            highlight = !cartEmpty,
            onScan = { showScanner = true },
            onSubmit = { submitSearch() },
            modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { e ->
                if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                    if (e.type == KeyEventType.KeyDown) submitSearch()
                    true
                } else false
            },
        )
        if (showChips) {
            Spacer(Modifier.height(10.dp))
            val labels = listOf(stringResource(R.string.till_cat_all)) + categories.map { it.second }
            val selected = cat?.let { c -> 1 + categories.indexOfFirst { it.first == c } } ?: 0
            KChips(labels, selected, { i -> vm.category = if (i == 0 || i == selected) null else categories[i - 1].first })
        }

        val list = results
        val entries: List<TillEntry> = remember(list, vm.cart.toList(), q) {
            val found = list.orEmpty()
            // A product in the cart stays where it is (shown as its cart line): the
            // list never moves under the cashier's finger, so tapping the same
            // place twice adds the same product twice.
            val shown = found.flatMap { p ->
                val lines = vm.cart.filter { it.productUuid == p.uuid }.map { TillEntry.Line(it) }
                if (p.variantCount > 0) listOf(TillEntry.Product(p)) + lines
                else lines.ifEmpty { listOf(TillEntry.Product(p)) }
            }
            // Cart lines whose product isn't in this list (e.g. found by an earlier search) go first.
            val shownUuids = found.map { it.uuid }.toSet()
            vm.cart.filter { it.productUuid !in shownUuids }.map { TillEntry.Line(it) } + shown
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Edge, end = Edge, top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.lastSale?.let { last ->
                item(key = "last") {
                    LastSaleCard(
                        last,
                        onPrint = { printReceipt(context, scope, snackbar, nav, last.uuid) },
                        onReceipt = { nav.navigate(Routes.receipt(last.uuid)) },
                        onClose = { vm.lastSale = null },
                    )
                }
            }
            if (list != null && list.isNotEmpty() && q.isBlank()) {
                item(key = "best") {
                    Text(
                        stringResource(R.string.till_best_sellers), style = K.t.section, color = K.c.tx,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
                    )
                }
            }
            items(entries, key = { it.key }) { e ->
                when (e) {
                    is TillEntry.Line -> CartLineRow(
                        e.item,
                        optom,
                        onPlus = { plus(e.item) },
                        onMinus = { minus(e.item) },
                        onQty = { qtyFor = e.item },
                    )
                    is TillEntry.Product -> ProductRow(e.product, optom, onClick = { add(e.product, null) })
                }
            }
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    val blank = q.isBlank()
                    KEmpty(
                        icon = if (blank) Icons.Outlined.Inventory2 else Icons.Outlined.SearchOff,
                        title = stringResource(if (blank) R.string.till_no_products else R.string.till_not_found),
                        text = stringResource(if (blank) R.string.till_no_products_hint else R.string.till_not_found_hint),
                        action = if (canProducts) stringResource(R.string.till_add_product) else null,
                        onAction = {
                            val code = q.trim().takeIf { t -> t.isNotEmpty() && t.all(Char::isLetterOrDigit) && t.any(Char::isDigit) }
                            nav.navigate(Routes.productEdit("new", code))
                        },
                    )
                }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(onResult = { handleCode(it) }, onDismiss = { showScanner = false }, continuous = true)
    }

    LaunchedEffect(cartEmpty) { if (cartEmpty && startSheet == null) showPay = false }
    if (showPay && !cartEmpty) {
        PaymentSheet(
            vm = vm,
            optom = optom,
            canDiscount = canDiscount,
            selling = selling,
            error = payError,
            onSell = { sell() },
            onPickCustomer = { showCustomer = true },
            onDismiss = { showPay = false },
        )
    }
    if (showCustomer) {
        CustomerPickerSheet(
            initialQuery = customerQuery,
            onPick = { vm.customer = it; showCustomer = false; payError = null },
            onDismiss = { showCustomer = false },
        )
    }

    variantFor?.let { product ->
        VariantSheet(product, optom, onPick = { v -> variantFor = null; add(product, v) }, onDismiss = { variantFor = null })
    }

    qtyFor?.let { item ->
        QtySheet(item, onSet = { qty -> vm.setQty(item.key, qty) }, onDismiss = { qtyFor = null })
    }

    if (confirmClear) {
        KConfirm(
            title = stringResource(R.string.till_clear_title),
            text = null,
            confirm = stringResource(R.string.till_clear),
            dismiss = stringResource(R.string.common_cancel),
            onConfirm = { vm.clear(); confirmClear = false },
            onDismiss = { confirmClear = false },
            danger = true,
        )
    }
}

/** "48 dona", or an amber "2 dona qoldi" / red "Tugagan" tag, or "3 ta variant". */
@Composable
internal fun StockLine(stock: Double, unit: String, low: Boolean, variants: Int = 0) {
    when {
        variants > 0 -> KSub(stringResource(R.string.till_variants_count, variants))
        stock <= 0 -> KTag(stringResource(R.string.till_stock_out), KTagKind.Red)
        low -> KTag(stringResource(R.string.till_stock_left, Money.qty(stock) + " " + unit), KTagKind.Low)
        else -> KSub(Money.qty(stock) + " " + unit)
    }
}

@Composable
private fun ProductRow(p: TillProduct, optom: Boolean, onClick: () -> Unit) {
    KRow(
        title = p.name,
        subtitle = { StockLine(p.stock, p.unit, p.isLow, p.variantCount) },
        trailing = { KMoney(p.priceFor(optom)) },
        onClick = onClick,
    )
}

@Composable
private fun CartLineRow(item: CartItem, optom: Boolean, onPlus: () -> Unit, onMinus: () -> Unit, onQty: () -> Unit) {
    KRow(
        title = item.name + (item.variantLabel?.let { " · $it" } ?: ""),
        subtitle = {
            // In an optom sale, a line with no optom price goes at the ordinary price: say so.
            val note = if (optom && item.optom == null) " · " + stringResource(R.string.till_no_wholesale_price) else ""
            if (item.unavailable) {
                KTag(
                    if (item.stock > 0) stringResource(R.string.till_line_short, Money.qty(item.stock) + " " + item.unit)
                    else stringResource(R.string.till_line_unavailable),
                    KTagKind.Red,
                )
            } else {
                KSub(Money.format(item.price) + note)
            }
        },
        trailing = { KStepper(Money.qty(item.qty), onMinus = onMinus, onPlus = onPlus, onQtyClick = onQty) },
        selected = true,
        onClick = onPlus,
        onLongClick = onQty,
    )
}

/** The cart bar (mockup 6): item count and total on the left, "To‘lash" on the right. Long-press the total to clear the cart. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartBar(
    count: Int,
    total: Double,
    onPay: () -> Unit,
    onClear: () -> Unit,
    wholesaleOn: Boolean = false,
    optom: Boolean = false,
    onOptom: (Boolean) -> Unit = {},
) {
    KBottomBar(background = K.c.s1) {
        if (wholesaleOn) {
            KSegmented(
                listOf(KOption(stringResource(R.string.till_retail)), KOption(stringResource(R.string.till_wholesale))),
                if (optom) 1 else 0,
                { onOptom(it == 1) },
                Modifier.fillMaxWidth(),
                height = 30.dp,
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = onPay, onLongClick = onClear),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.till_cart_count, count), style = K.t.small, color = K.c.mu, maxLines = 1)
                    // Never sell optom by accident: the total says so in amber.
                    if (optom) KTag(stringResource(R.string.till_wholesale), KTagKind.Low)
                }
                KMoney(total, size = 21.sp)
            }
            Spacer(Modifier.width(12.dp))
            KButton(stringResource(R.string.till_pay), onPay, trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward)
        }
    }
}

/** The result of the sale just made (mockup 11): green card, change in big type, print / receipt. */
@Composable
private fun LastSaleCard(last: LastSale, onPrint: () -> Unit, onReceipt: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(K.c.grnBg).border(1.5.dp, K.c.grn, shape)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.till_sold, last.receiptNo, Money.format(last.total)),
                    style = K.t.label.copy(fontSize = 12.sp), color = K.c.grn, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val big = K.t.hero.copy(fontSize = 22.sp)
                when {
                    last.qarz > 0.005 -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.till_debt_colon) + " ", style = big, color = K.c.red, maxLines = 1, modifier = Modifier.alignByBaseline())
                        KMoney(last.qarz, size = 22.sp, color = K.c.red, modifier = Modifier.alignByBaseline())
                    }
                    last.change > 0.005 -> Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.till_change_colon) + " ", style = big, color = K.c.tx, maxLines = 1, modifier = Modifier.alignByBaseline())
                        KMoney(last.change, size = 22.sp, modifier = Modifier.alignByBaseline())
                    }
                    else -> Text(stringResource(R.string.till_no_change), style = big, color = K.c.tx, maxLines = 1)
                }
            }
            KIconButton(Icons.Outlined.Close, onClose, size = 34.dp, contentDescription = stringResource(R.string.scan_close))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KButton(
                stringResource(R.string.print_print), onPrint, Modifier.weight(1f),
                kind = KButtonKind.Alt, icon = Icons.Outlined.Print, height = 40.dp, fontSize = 14.sp,
            )
            KButton(
                stringResource(R.string.till_receipt), onReceipt, Modifier.weight(1f),
                kind = KButtonKind.Alt, icon = Icons.Outlined.Receipt, height = 40.dp, fontSize = 14.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Receipt (mockup 12)

/** The paper of a printed receipt: white with dark ink in both themes, on purpose. */
private val PaperWhite = Color(0xFFFFFFFF)
private val PaperInk = Color(0xFF111111)
private val PaperRule = Color(0xFF999999)

@Composable
fun ReceiptScreen(nav: NavHostController, uuid: String) {
    val loaded by dbState<Pair<Boolean, ReceiptData?>>(false to null, uuid) { true to Receipts.load(App.db, uuid) }
    ReceiptContent(nav, loaded.first, loaded.second)
}

@Composable
internal fun ReceiptContent(nav: NavHostController, loaded: Boolean, data: ReceiptData?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun newSale() {
        if (!nav.popBackStack(Routes.TILL, inclusive = false)) nav.switchTab(Routes.TILL)
    }

    KScreen(
        snackbar = snackbar,
        topBar = {
            KTopBar(stringResource(R.string.till_receipt), onBack = { nav.popBackStack() }) {
                if (data != null) KIconButton(Icons.Outlined.Share, { shareReceipt(context, data) }, contentDescription = stringResource(R.string.till_share))
            }
        },
        bottomBar = if (data == null) null else {
            {
                KBottomBar {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KButton(
                            stringResource(R.string.print_print), { printReceipt(context, scope, snackbar, nav, data.saleUuid) }, Modifier.weight(1f),
                            kind = KButtonKind.Alt, icon = Icons.Outlined.Print, height = 46.dp, fontSize = 14.sp,
                        )
                        KButton(
                            stringResource(R.string.till_share), { shareReceipt(context, data) }, Modifier.weight(1f),
                            kind = KButtonKind.Alt, icon = Icons.Outlined.Share, height = 46.dp, fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    KButton(stringResource(R.string.till_new_sale), { newSale() }, Modifier.fillMaxWidth())
                }
            }
        },
    ) {
        when {
            !loaded -> {}
            data == null -> KEmpty(Icons.Outlined.Receipt, stringResource(R.string.till_receipt_missing))
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = Edge, end = Edge, top = 4.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { ReceiptPaper(data) }
        }
    }
}

private fun shareReceipt(context: Context, data: ReceiptData) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "№ " + data.receiptNo)
        putExtra(Intent.EXTRA_TEXT, Receipts.text(context, data))
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.till_share))) }
}

@Composable
private fun ReceiptPaper(r: ReceiptData) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(6.dp)
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 19.sp, color = PaperInk)
    val bold = mono.copy(fontWeight = FontWeight.Bold)

    @Composable
    fun center(text: String, style: TextStyle = mono) =
        Text(text, style = style, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())

    @Composable
    fun line(left: String, right: String, style: TextStyle = mono) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(left, style = style, modifier = Modifier.weight(1f))
            Text(right, style = style, maxLines = 1, softWrap = false)
        }
    }

    @Composable
    fun rule() = DashedRule(Modifier.padding(vertical = 8.dp))

    Column(
        Modifier.widthIn(max = 460.dp).fillMaxWidth().kShadow(shape).clip(shape).background(PaperWhite)
            .padding(horizontal = 14.dp, vertical = 16.dp),
    ) {
        if (r.shopName.isNotBlank()) center(r.shopName, bold)
        if (r.shopAddress.isNotBlank()) center(r.shopAddress)
        if (r.shopPhone.isNotBlank()) center(r.shopPhone)
        rule()
        line("№ " + r.receiptNo, r.createdAt)
        if (r.cashier.isNotBlank()) Text(stringResource(R.string.print_cashier) + ": " + r.cashier, style = mono)
        if (r.wholesale) Text(stringResource(R.string.till_wholesale_prices), style = bold)
        rule()
        r.items.forEach { item -> line(item.name + " × " + Money.qty(item.qty), Money.format(item.subtotal, false)) }
        rule()
        if (r.discount > 0) line(stringResource(R.string.till_discount), "−" + Money.format(r.discount, false))
        line(stringResource(R.string.till_receipt_total), Money.format(r.total), bold)
        Receipts.paymentRows(r).forEach { (type, amount) -> line(Receipts.paymentLabel(context, type), Money.format(amount, false)) }
        if (r.change > 0) line(stringResource(R.string.till_change), Money.format(r.change, false))
        if (r.debt > 0) r.customerName?.let { line(stringResource(R.string.till_customer), it) }
        if (r.refunded > 0) line(stringResource(R.string.print_refunded), "−" + Money.format(r.refunded, false))
        rule()
        center(stringResource(R.string.print_not_fiscal), mono.copy(fontSize = 11.sp, lineHeight = 17.sp))
        center(stringResource(R.string.print_thanks), mono.copy(fontSize = 11.sp, lineHeight = 17.sp))
    }
}

/** The receipt's dashed separator. */
@Composable
private fun DashedRule(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(1.dp).drawDashes(PaperRule),
    )
}
