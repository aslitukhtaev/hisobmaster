package uz.kassiron.app.ui.till

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.domain.SaleException
import uz.kassiron.app.domain.SaleService
import uz.kassiron.app.print.BluetoothPrinter
import uz.kassiron.app.print.PrintException
import uz.kassiron.app.print.PrinterSettings
import uz.kassiron.app.print.ReceiptData
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.switchTab
import uz.kassiron.app.ui.theme.Brand

/** A top bar for screens inside AppNav's Scaffold (which already pads for the status bar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TillTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back)) }
        },
        actions = { actions() },
        windowInsets = WindowInsets(0, 0, 0, 0),
    )
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TillScreen(nav: NavHostController) {
    val vm: TillViewModel = viewModel()
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val user by App.session.user.collectAsState()
    val sync by App.sync.state.collectAsState()
    val canDiscount = user?.can("discount") == true
    val canProducts = user?.can("products") == true

    var showScanner by remember { mutableStateOf(false) }
    var showCart by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf(false) }
    var variantFor by remember { mutableStateOf<TillProduct?>(null) }
    var qtyFor by remember { mutableStateOf<CartItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var selling by remember { mutableStateOf(false) }
    var payError by remember { mutableStateOf<String?>(null) }

    val q = vm.query
    val results by dbState<List<TillProduct>?>(null, q) { TillData.search(q) }

    fun say(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun add(product: TillProduct, variant: TillVariant?) {
        if (product.variantCount > 0 && variant == null) {
            showScanner = false
            variantFor = product
            return
        }
        when (val r = vm.add(product, variant)) {
            is TillViewModel.AddResult.Added -> haptic(view)
            is TillViewModel.AddResult.NoStock -> {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                say(context.getString(R.string.till_no_stock, r.name, Money.qty(r.stock) + " " + r.unit))
            }
        }
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

    // Enter in the search field: a scanner's code (keyboard wedge) or a single match.
    fun submitSearch() {
        val text = vm.query.trim()
        if (text.isEmpty()) return
        scope.launch {
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
        }
    }

    fun sell() {
        if (selling) return
        val split = vm.split(canDiscount)
        val (naqd, karta) = vm.tendered(split.total)
        val lines = vm.lines()
        val discount = vm.discount(canDiscount)
        val customer = vm.customer?.uuid
        selling = true
        payError = null
        scope.launch {
            try {
                val sale = io { SaleService.create(lines, naqd, karta, discount, customer) }
                vm.lastSale = LastSale(sale.uuid, sale.receiptNo, sale.total, sale.change, sale.qarz)
                vm.clear()
                showPay = false
                showCart = false
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
    LaunchedEffect(hardwareKeyboard) { if (hardwareKeyboard) runCatching { focus.requestFocus() } }

    Scaffold(
        topBar = {
            TillTopBar(stringResource(R.string.till_title)) {
                if (sync.pendingSales > 0) {
                    AssistChip(
                        onClick = {},
                        label = { Text(sync.pendingSales.toString()) },
                        leadingIcon = { Icon(Icons.Filled.CloudUpload, null, Modifier.size(18.dp)) },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                IconButton(onClick = { nav.navigate(Routes.PRINTER) }) { Icon(Icons.Filled.Print, stringResource(R.string.print_settings)) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            val searchArea: @Composable () -> Unit = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        modifier = Modifier.weight(1f).focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                                if (e.type == KeyEventType.KeyDown) submitSearch()
                                true
                            } else false
                        },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.till_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (vm.query.isNotEmpty()) IconButton(onClick = { vm.query = "" }) { Icon(Icons.Filled.Clear, null) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                        shape = RoundedCornerShape(14.dp),
                    )
                    FilledIconButton(
                        onClick = { showScanner = true },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Icon(Icons.Filled.QrCodeScanner, stringResource(R.string.till_scan), Modifier.size(28.dp)) }
                }
            }
            val lastSaleBar: @Composable () -> Unit = {
                vm.lastSale?.let { last ->
                    LastSaleBar(
                        last,
                        onPrint = { printReceipt(context, scope, snackbar, nav, last.uuid) },
                        onReceipt = { nav.navigate(Routes.receipt(last.uuid)) },
                        onClose = { vm.lastSale = null },
                    )
                }
            }
            val resultsList: @Composable (Modifier) -> Unit = { mod ->
                ProductResults(
                    results = results,
                    query = q,
                    cart = vm.cart,
                    canProducts = canProducts,
                    onAdd = { add(it, null) },
                    onNewProduct = {
                        val code = q.trim().takeIf { t -> t.isNotEmpty() && t.all(Char::isLetterOrDigit) && t.any(Char::isDigit) }
                        nav.navigate(Routes.productEdit("new", code))
                    },
                    modifier = mod,
                )
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.weight(1.25f).fillMaxHeight()) {
                        searchArea()
                        lastSaleBar()
                        resultsList(Modifier.weight(1f))
                    }
                    VerticalDivider()
                    CartPanel(
                        vm = vm,
                        onQty = { qtyFor = it },
                        onNoStock = { say(context.getString(R.string.till_no_stock, it.name, Money.qty(it.stock) + " " + it.unit)) },
                        onClear = { confirmClear = true },
                        onPay = { payError = null; showPay = true },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    searchArea()
                    lastSaleBar()
                    resultsList(Modifier.weight(1f))
                    if (vm.cart.isNotEmpty()) {
                        CartBar(vm, onOpen = { showCart = true }, onPay = { payError = null; showPay = true })
                    }
                }
            }
        }
    }

    if (showScanner) {
        BarcodeScannerDialog(onResult = { handleCode(it) }, onDismiss = { showScanner = false }, continuous = true)
    }

    LaunchedEffect(vm.cart.isEmpty()) { if (vm.cart.isEmpty()) showCart = false }
    if (showCart && vm.cart.isNotEmpty()) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showCart = false }, sheetState = state) {
            CartPanel(
                vm = vm,
                onQty = { qtyFor = it },
                onNoStock = { say(context.getString(R.string.till_no_stock, it.name, Money.qty(it.stock) + " " + it.unit)) },
                onClear = { confirmClear = true },
                onPay = { payError = null; showCart = false; showPay = true },
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            )
        }
    }

    if (showPay && vm.cart.isNotEmpty()) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { if (!selling) showPay = false }, sheetState = state) {
            PaymentContent(
                vm = vm,
                canDiscount = canDiscount,
                selling = selling,
                error = payError,
                onSell = { sell() },
            )
        }
    }

    variantFor?.let { product ->
        VariantDialog(product, onPick = { v -> variantFor = null; add(product, v) }, onDismiss = { variantFor = null })
    }

    qtyFor?.let { item ->
        QtyDialog(item, onSet = { qty -> vm.setQty(item.key, qty) }, onDismiss = { qtyFor = null })
    }

    if (confirmClear) {
        uz.kassiron.app.ui.common.ConfirmDialog(
            title = stringResource(R.string.till_clear_title),
            text = null,
            confirm = stringResource(R.string.till_clear),
            dismiss = stringResource(R.string.common_cancel),
            onConfirm = { vm.clear(); confirmClear = false; showCart = false },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ProductResults(
    results: List<TillProduct>?,
    query: String,
    cart: List<CartItem>,
    canProducts: Boolean,
    onAdd: (TillProduct) -> Unit,
    onNewProduct: () -> Unit,
    modifier: Modifier,
) {
    if (results == null) {
        Box(modifier.fillMaxSize())
        return
    }
    if (results.isEmpty()) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                if (query.isBlank()) Icons.Filled.Inventory2 else Icons.Filled.SearchOff,
                stringResource(if (query.isBlank()) R.string.till_no_products else R.string.till_not_found),
                Modifier.weight(1f),
            )
            if (canProducts) {
                OutlinedButton(onClick = onNewProduct, modifier = Modifier.padding(bottom = 24.dp).height(52.dp)) {
                    Icon(Icons.Filled.AddShoppingCart, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.till_add_product))
                }
            }
        }
        return
    }
    val inCart = remember(cart.toList()) { cart.groupBy { it.productUuid }.mapValues { e -> e.value.sumOf { it.qty } } }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(results, key = { it.uuid }) { p -> ProductRow(p, inCart[p.uuid] ?: 0.0, onClick = { onAdd(p) }) }
    }
}

@Composable
private fun ProductRow(p: TillProduct, inCart: Double, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (inCart > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                val stockColor = when {
                    p.stock <= 0 -> MaterialTheme.colorScheme.error
                    p.isLow -> Brand.Warning
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    buildString {
                        append(stringResource(R.string.till_stock, Money.qty(p.stock) + " " + p.unit))
                        if (p.variantCount > 0) append(" · ").append(stringResource(R.string.till_variants_count, p.variantCount))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (p.variantCount > 0) MaterialTheme.colorScheme.onSurfaceVariant else stockColor,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(p.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                if (inCart > 0) {
                    Text(
                        "× " + Money.qty(inCart),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastSaleBar(last: LastSale, onPrint: () -> Unit, onReceipt: () -> Unit, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.till_sold, last.receiptNo, Money.format(last.total)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    last.change > 0 -> Text(
                        stringResource(R.string.till_change) + ": " + Money.format(last.change),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    last.qarz > 0 -> Text(
                        stringResource(R.string.till_debt) + ": " + Money.format(last.qarz),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            IconButton(onClick = onPrint, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Print, stringResource(R.string.print_print)) }
            IconButton(onClick = onReceipt, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.ReceiptLong, stringResource(R.string.till_receipt)) }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Clear, stringResource(R.string.scan_close)) }
        }
    }
}

@Composable
private fun CartBar(vm: TillViewModel, onOpen: () -> Unit, onPay: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onOpen, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.till_cart_count, vm.cart.size, Money.qty(vm.count)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(Money.format(vm.subtotal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.size(10.dp))
            Button(onClick = onPay, modifier = Modifier.height(60.dp).widthIn(min = 128.dp), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.till_pay), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Receipt

@Composable
fun ReceiptScreen(nav: NavHostController, uuid: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val loaded by dbState<Pair<Boolean, ReceiptData?>>(false to null, uuid) { true to Receipts.load(App.db, uuid) }
    val data = loaded.second

    fun newSale() {
        if (!nav.popBackStack(Routes.TILL, inclusive = false)) nav.switchTab(Routes.TILL)
    }

    Scaffold(
        topBar = { TillTopBar(stringResource(R.string.till_receipt), onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (data != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { printReceipt(context, scope, snackbar, nav, uuid) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Icon(Icons.Filled.Print, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.print_print), maxLines = 1) }
                        OutlinedButton(
                            onClick = { shareReceipt(context, data) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.size(6.dp)); Text(stringResource(R.string.till_share), maxLines = 1) }
                        Button(
                            onClick = { newSale() },
                            modifier = Modifier.weight(1.2f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(stringResource(R.string.till_new_sale), maxLines = 1) }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !loaded.first -> {}
                data == null -> EmptyState(Icons.Filled.ReceiptLong, stringResource(R.string.till_receipt_missing))
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 460.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) { ReceiptView(data) }
                }
            }
        }
    }
}

private fun shareReceipt(context: Context, data: ReceiptData) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "№" + data.receiptNo)
        putExtra(Intent.EXTRA_TEXT, Receipts.text(context, data))
    }
    runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.till_share))) }
}

@Composable
private fun ReceiptView(r: ReceiptData) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.padding(18.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (r.shopName.isNotBlank()) Text(r.shopName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (r.shopAddress.isNotBlank()) Text(r.shopAddress, color = muted, textAlign = TextAlign.Center)
            if (r.shopPhone.isNotBlank()) Text(r.shopPhone, color = muted)
            Text("№${r.receiptNo} · ${r.createdAt}", color = muted)
            if (r.cashier.isNotBlank()) Text(stringResource(R.string.print_cashier) + ": " + r.cashier, color = muted)
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        r.items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                    Text(Money.qty(item.qty) + " × " + Money.format(item.unitPrice), color = muted, style = MaterialTheme.typography.bodyMedium)
                }
                Text(Money.format(item.subtotal), fontWeight = FontWeight.Bold)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 10.dp))
        @Composable
        fun line(label: String, value: String, strong: Boolean = false, color: androidx.compose.ui.graphics.Color? = null) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(
                    label, Modifier.weight(1f),
                    style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    color = color ?: MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    value,
                    style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (strong) FontWeight.ExtraBold else FontWeight.Medium,
                    color = color ?: MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (r.discount > 0) line(stringResource(R.string.till_discount), "-" + Money.format(r.discount))
        line(stringResource(R.string.till_total), Money.format(r.total), strong = true)
        if (r.payments.size > 1) {
            r.payments.forEach { (type, amount) -> line(Receipts.paymentLabel(context, type), Money.format(amount)) }
        } else {
            line(stringResource(R.string.print_payment_type), Receipts.paymentLabel(context, r.paymentType))
        }
        if (r.cashReceived > 0) {
            line(stringResource(R.string.till_cash_received), Money.format(r.cashReceived))
            line(stringResource(R.string.till_change), Money.format(r.change), color = Brand.Success)
        }
        if (r.debt > 0) {
            if (r.payments.size <= 1) {
                line(stringResource(R.string.print_paid_now), Money.format(r.paidAmount))
                line(stringResource(R.string.print_debt_amount), Money.format(r.debt), color = MaterialTheme.colorScheme.error)
            }
            r.customerName?.let { line(stringResource(R.string.till_customer), it) }
        }
        if (r.refunded > 0) line(stringResource(R.string.print_refunded), "-" + Money.format(r.refunded), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.print_not_fiscal),
            color = muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(R.string.print_thanks),
            color = muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
    }
}
