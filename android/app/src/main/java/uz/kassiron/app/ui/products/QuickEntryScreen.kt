package uz.kassiron.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.QuickRow
import uz.kassiron.app.ui.common.BarcodeScannerDialog
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAlert
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.theme.K

// Quick entry of many products (mockup 17, the website's /products/quick).

internal class QuickRowState(val id: Long) {
    var name by mutableStateOf("")
    var price by mutableStateOf("")
    var qty by mutableStateOf("")
    var barcode by mutableStateOf("")
    /** A message from the last save (server rules), cleared when the row changes. */
    var serverError by mutableStateOf<String?>(null)
    val focus = FocusRequester()

    val isBlank: Boolean get() = name.isBlank() && price.isBlank() && qty.isBlank() && barcode.isBlank()
    val priceOk: Boolean get() = Money.parse(price)?.let { it >= 0 } == true
    val complete: Boolean get() = name.isNotBlank() && priceOk && (qty.isBlank() || Money.parse(qty) != null)

    fun touch() { serverError = null }
}

/** Which cell of a row is wrong (shown red). */
private enum class BadCell { NAME, PRICE, QTY }

@Composable
fun QuickProductsScreen(nav: NavHostController) = QuickEntry(nav)

/** [prefill] lets the screenshot tests show a filled table. */
@Composable
internal fun QuickEntry(nav: NavHostController, prefill: List<Triple<String, String, String>> = emptyList(), showErrors: Boolean = false) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val user by App.session.user.collectAsState()
    val canPrices = user?.can("prices") == true

    var nextId by remember { mutableLongStateOf(0L) }
    val rows = remember { mutableStateListOf<QuickRowState>() }
    fun addRow(): QuickRowState = QuickRowState(nextId++).also { rows += it }
    var focusedRow by remember { mutableStateOf<Long?>(null) }
    var showAll by remember { mutableStateOf(showErrors) }
    var pendingFocus by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var scanMany by remember { mutableStateOf(false) }

    if (rows.isEmpty()) {
        prefill.forEach { (n, p, q) -> addRow().apply { name = n; price = p; qty = q } }
        repeat(if (prefill.isEmpty()) 5 else 2) { addRow() }
    }
    LaunchedEffect(Unit) { if (prefill.isEmpty()) pendingFocus = rows.first().id }

    fun ensureTrailingBlank() { if (!rows.last().isBlank) addRow() }

    /** What is wrong with a row, if it is filled at all. */
    fun badCell(r: QuickRowState): BadCell? = when {
        r.isBlank -> null
        r.name.isBlank() -> BadCell.NAME
        !r.priceOk -> BadCell.PRICE
        r.qty.isNotBlank() && Money.parse(r.qty) == null -> BadCell.QTY
        else -> null
    }
    fun showsError(r: QuickRowState) = (showAll || r.id != focusedRow) && (badCell(r) != null || r.serverError != null)

    val filled = rows.count { !it.isBlank }
    val ready = rows.count { it.complete && it.serverError == null }

    val messages = rows.mapIndexedNotNull { i, r ->
        if (!showsError(r)) return@mapIndexedNotNull null
        val who = r.name.trim().ifEmpty { ctx.getString(R.string.products_quick_row_n, i + 1) }
        val what = r.serverError ?: when (badCell(r)) {
            BadCell.NAME -> ctx.getString(R.string.products_quick_need_name)
            BadCell.PRICE -> ctx.getString(R.string.products_quick_need_price)
            BadCell.QTY -> ctx.getString(R.string.products_quick_need_qty)
            null -> null
        } ?: return@mapIndexedNotNull null
        ctx.getString(R.string.products_quick_row_error, who, what)
    }

    fun save() {
        if (saving) return
        showAll = true
        focusManager.clearFocus()
        val snapshot = rows.toList()
        val input = snapshot.mapIndexedNotNull { i, r ->
            if (!r.complete || r.serverError != null) null
            else i to QuickRow(name = r.name.trim(), barcode = r.barcode.trim(), sellPrice = r.price, stock = r.qty)
        }
        if (input.isEmpty()) return
        saving = true
        scope.launch {
            val result = runCatching { io { ProductService.quickCreate(input, canPrices) } }
            saving = false
            result.onSuccess { res ->
                res.errors.forEach { (i, e) -> snapshot.getOrNull(i)?.serverError = ctx.productError(e) }
                val created = res.created.mapNotNull { snapshot.getOrNull(it) }.toSet()
                rows.removeAll { it in created }
                if (rows.none { !it.isBlank }) { rows.clear(); repeat(5) { addRow() }; showAll = false }
                ensureTrailingBlank()
                if (res.created.isNotEmpty()) snackbar.showSnackbar(ctx.getString(R.string.products_quick_saved, res.created.size))
            }.onFailure { snackbar.showSnackbar(ctx.productError(it)) }
        }
    }

    Box(Modifier.fillMaxSize().imePadding()) {
        KScreen(
            snackbar = snackbar,
            topBar = {
                KTopBar(stringResource(R.string.products_quick_title), onBack = { nav.popBackStack() }) {
                    if (filled > 0) KTag(stringResource(R.string.products_quick_rows, filled), KTagKind.Ok)
                    KIconButton(Icons.Outlined.QrCodeScanner, { scanMany = true }, contentDescription = stringResource(R.string.products_quick_scan_many))
                }
            },
            bottomBar = {
                KBottomBar {
                    KButton(
                        if (ready > 0) stringResource(R.string.products_quick_save_n, ready) else stringResource(R.string.common_save),
                        { save() }, Modifier.fillMaxWidth(), enabled = ready > 0 && !saving,
                    )
                }
            },
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeadCell(stringResource(R.string.products_quick_col_name), 1.4f)
                    HeadCell(stringResource(R.string.products_quick_col_price), 0.8f)
                    HeadCell(stringResource(R.string.products_quick_col_qty), 0.8f)
                }
                rows.forEachIndexed { index, r ->
                    val bad = if (showsError(r)) badCell(r) else null
                    val serverBad = showsError(r) && r.serverError != null
                    val last = index == rows.lastIndex
                    fun onFocus() { focusedRow = r.id }
                    fun nextFromLast() {
                        if (last) { val n = addRow(); pendingFocus = n.id } else focusManager.moveFocus(FocusDirection.Next)
                    }
                    if (pendingFocus == r.id) {
                        LaunchedEffect(r.id) { runCatching { r.focus.requestFocus() }; pendingFocus = null }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(start = Edge, end = Edge, top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuickCell(
                            r.name, { r.name = it.take(200); r.touch(); ensureTrailingBlank() },
                            stringResource(R.string.products_quick_col_name), 1.4f,
                            error = bad == BadCell.NAME || serverBad, numeric = false,
                            onFocus = ::onFocus, onNext = { focusManager.moveFocus(FocusDirection.Next) }, requester = r.focus,
                        )
                        QuickCell(
                            r.price, { r.price = numberInput(it); r.touch(); ensureTrailingBlank() },
                            if (bad == BadCell.PRICE) stringResource(R.string.products_quick_price_missing) else "0", 0.8f,
                            error = bad == BadCell.PRICE, numeric = true, group = true,
                            onFocus = ::onFocus, onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        )
                        QuickCell(
                            r.qty, { r.qty = numberInput(it); r.touch(); ensureTrailingBlank() },
                            "0", 0.8f,
                            error = bad == BadCell.QTY, numeric = true,
                            onFocus = ::onFocus, onNext = ::nextFromLast,
                        )
                    }
                    if (r.barcode.isNotBlank()) {
                        Text(
                            stringResource(R.string.products_quick_barcode, r.barcode),
                            style = K.t.small.copy(fontSize = 11.5.sp), color = K.c.mu,
                            modifier = Modifier.padding(start = Edge + 4.dp, top = 3.dp),
                        )
                    }
                }
                if (messages.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    KAlert(messages.take(4).joinToString("\n"), Icons.Outlined.ErrorOutline, tone = KCardTone.Red)
                }
                Spacer(Modifier.height(10.dp))
                KButton(
                    stringResource(R.string.products_quick_add_rows), { repeat(10) { addRow() } },
                    Modifier.fillMaxWidth().padding(horizontal = Edge), kind = KButtonKind.Alt, icon = Icons.Outlined.Add, height = 44.dp, fontSize = 14.sp,
                )
                Text(
                    stringResource(R.string.products_quick_hint),
                    style = K.t.small, color = K.c.mu, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Edge * 2, vertical = 12.dp),
                )
            }
        }
    }

    if (scanMany) {
        BarcodeScannerDialog(
            continuous = true,
            onResult = { raw ->
                val code = raw.trim()
                if (code.isNotEmpty() && rows.none { it.barcode.trim() == code }) {
                    val target = rows.firstOrNull { it.isBlank } ?: addRow()
                    target.barcode = code
                    ensureTrailingBlank()
                }
            },
            onDismiss = { scanMany = false },
        )
    }
}

@Composable
private fun RowScope.HeadCell(text: String, weight: Float) {
    Text(text, style = K.t.small.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold), color = K.c.mu, modifier = Modifier.weight(weight).padding(horizontal = 2.dp, vertical = 4.dp))
}

/** One table cell: 44dp, rounded 11, lime border when focused, red when wrong. Enter = next cell. */
@Composable
private fun RowScope.QuickCell(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    weight: Float,
    error: Boolean,
    numeric: Boolean,
    onFocus: () -> Unit,
    onNext: () -> Unit,
    group: Boolean = false,
    requester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(11.dp)
    val style = K.t.body.copy(fontSize = 13.5.sp, color = K.c.tx)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(K.c.ink),
        interactionSource = interaction,
        visualTransformation = if (group) GroupThousands else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            capitalization = if (numeric) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }, onDone = { onNext() }),
        modifier = Modifier.weight(weight)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusChanged { if (it.isFocused) onFocus() },
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(shape).background(K.c.s1)
                    .border(
                        if (focused || error) 1.5.dp else 1.dp,
                        when { error -> K.c.red; focused -> K.c.primary; else -> K.c.bd },
                        shape,
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(fontWeight = FontWeight.Medium), color = K.c.mu, maxLines = 1)
                inner()
            }
        },
    )
}
