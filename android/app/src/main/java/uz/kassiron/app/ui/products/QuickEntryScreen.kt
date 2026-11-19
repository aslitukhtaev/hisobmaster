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
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.QuickRow
import uz.kassiron.app.domain.Wholesale
import uz.kassiron.app.ui.common.dbState
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

// Quick entry of many products (mockup 17, the website's /products/quick).

internal class QuickRowState(val id: Long) {
    var name by mutableStateOf("")
    var price by mutableStateOf("")
    var optom by mutableStateOf("")
    var qty by mutableStateOf("")
    var barcode by mutableStateOf("")
    /** A message from the last save (server rules), cleared when the row changes. */
    var serverError by mutableStateOf<String?>(null)
    val focus = FocusRequester()

    val isBlank: Boolean get() = name.isBlank() && price.isBlank() && optom.isBlank() && qty.isBlank() && barcode.isBlank()
    val optomOk: Boolean get() = optom.isBlank() || Money.parse(optom)?.let { it >= 0 } == true
    val priceOk: Boolean get() = Money.parse(price)?.let { it >= 0 } == true
    val complete: Boolean get() = name.isNotBlank() && priceOk && optomOk && (qty.isBlank() || Money.parse(qty) != null)

    fun touch() { serverError = null }
}

/** Which cell of a row is wrong (shown red). */
private enum class BadCell { NAME, PRICE, OPTOM, QTY }

@Composable
fun QuickProductsScreen(nav: NavHostController) = QuickEntry(nav)

/** [prefill] lets the screenshot tests show a filled table. */
@Composable
internal fun QuickEntry(
    nav: NavHostController,
    prefill: List<Triple<String, String, String>> = emptyList(),
    showErrors: Boolean = false,
    prefillOptom: List<String> = emptyList(),
    initialCategory: String? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val user by App.session.user.collectAsState()
    val canPrices = user?.can("prices") == true

    // Saveable: a half-filled table survives the screen being recreated.
    var nextId by rememberSaveable { mutableLongStateOf(0L) }
    val rows = rememberSaveable(saver = QuickRowsSaver) { mutableStateListOf<QuickRowState>() }
    fun addRow(): QuickRowState = QuickRowState(nextId++).also { rows += it }
    var focusedRow by remember { mutableStateOf<Long?>(null) }
    var showAll by remember { mutableStateOf(showErrors) }
    var pendingFocus by remember { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var scanMany by remember { mutableStateOf(false) }
    var categoryId by rememberSaveable { mutableStateOf(initialCategory) }
    var categorySheet by remember { mutableStateOf(false) }
    val categories by dbState(emptyList<DbRow>()) { ProductService.categories(App.db) }
    // A second price column ("Optom narx") only where the shop sells wholesale.
    val wholesaleOn by dbState(false) { Wholesale.enabled() }
    val nameW = if (wholesaleOn) 1.25f else 1.4f
    val priceW = if (wholesaleOn) 0.85f else 0.8f
    val qtyW = if (wholesaleOn) 0.6f else 0.8f

    if (rows.isEmpty()) {
        prefill.forEachIndexed { i, (n, p, q) -> addRow().apply { name = n; price = p; qty = q; optom = prefillOptom.getOrElse(i) { "" } } }
        repeat(if (prefill.isEmpty()) 5 else 2) { addRow() }
    }
    LaunchedEffect(Unit) { if (prefill.isEmpty()) pendingFocus = rows.first().id }

    fun ensureTrailingBlank() { if (!rows.last().isBlank) addRow() }

    /** What is wrong with a row, if it is filled at all. */
    fun badCell(r: QuickRowState): BadCell? = when {
        r.isBlank -> null
        r.name.isBlank() -> BadCell.NAME
        !r.priceOk -> BadCell.PRICE
        wholesaleOn && !r.optomOk -> BadCell.OPTOM
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
            BadCell.OPTOM -> ctx.getString(R.string.products_quick_need_optom)
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
            else i to QuickRow(
                name = r.name.trim(), barcode = r.barcode.trim(), sellPrice = r.price, stock = r.qty,
                wholesalePrice = if (wholesaleOn) r.optom else "",
            )
        }
        if (input.isEmpty()) return
        saving = true
        scope.launch {
            val result = runCatching { io { ProductService.quickCreate(input, canPrices, categoryId) } }
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
                // One category for the whole batch; it stays chosen for the next one.
                PickField(
                    stringResource(R.string.products_category),
                    categories.firstOrNull { it.str("uuid") == categoryId }?.str("name") ?: stringResource(R.string.products_category_none),
                    "", { categorySheet = true },
                    Modifier.fillMaxWidth().padding(start = Edge, end = Edge, bottom = 10.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = Edge), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeadCell(stringResource(R.string.products_quick_col_name), nameW)
                    HeadCell(stringResource(if (wholesaleOn) R.string.products_retail_price else R.string.products_quick_col_price), priceW)
                    if (wholesaleOn) HeadCell(stringResource(R.string.products_wholesale_price), priceW)
                    HeadCell(stringResource(R.string.products_quick_col_qty), qtyW)
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
                            stringResource(R.string.products_quick_col_name), nameW,
                            error = bad == BadCell.NAME || serverBad, numeric = false,
                            onFocus = ::onFocus, onNext = { focusManager.moveFocus(FocusDirection.Next) }, requester = r.focus,
                        )
                        QuickCell(
                            r.price, { r.price = numberInput(it); r.touch(); ensureTrailingBlank() },
                            if (bad == BadCell.PRICE) stringResource(R.string.products_quick_price_missing) else "0", priceW,
                            error = bad == BadCell.PRICE, numeric = true, group = true,
                            onFocus = ::onFocus, onNext = { focusManager.moveFocus(FocusDirection.Next) },
                        )
                        if (wholesaleOn) {
                            QuickCell(
                                r.optom, { r.optom = numberInput(it); r.touch(); ensureTrailingBlank() },
                                "—", priceW,
                                error = bad == BadCell.OPTOM, numeric = true, group = true,
                                onFocus = ::onFocus, onNext = { focusManager.moveFocus(FocusDirection.Next) },
                            )
                        }
                        QuickCell(
                            r.qty, { r.qty = numberInput(it); r.touch(); ensureTrailingBlank() },
                            "0", qtyW,
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

    if (categorySheet) {
        PickSheet(
            title = stringResource(R.string.products_category),
            items = listOf(PickItem(null, stringResource(R.string.products_category_none))) + categories.map { PickItem(it.str("uuid"), it.str("name")) },
            selected = categoryId,
            searchHint = stringResource(R.string.products_category_search),
            newTitle = { ctx.getString(R.string.products_category_new, it) },
            newSub = stringResource(R.string.products_category_new_hint),
            onPick = { id -> categoryId = id; categorySheet = false },
            onCreate = { name ->
                scope.launch {
                    runCatching { io { ProductService.findOrCreateCategory(name) } }
                        .onSuccess { id -> categoryId = id; categorySheet = false }
                        .onFailure { snackbar.showSnackbar(ctx.productError(it)) }
                }
            },
            onDismiss = { categorySheet = false },
        )
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
    Text(text, style = K.t.small.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp), color = K.c.mu, maxLines = 2, modifier = Modifier.weight(weight).padding(horizontal = 2.dp, vertical = 4.dp))
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

/** The quick-entry rows as JSON for rememberSaveable. */
private val QuickRowsSaver = Saver<SnapshotStateList<QuickRowState>, String>(
    save = { list ->
        val arr = JSONArray()
        for (r in list) {
            arr.put(JSONObject().put("id", r.id).put("n", r.name).put("p", r.price).put("o", r.optom).put("q", r.qty).put("b", r.barcode))
        }
        arr.toString()
    },
    restore = { s ->
        val out = mutableStateListOf<QuickRowState>()
        runCatching {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += QuickRowState(o.getLong("id")).apply {
                    name = o.optString("n"); price = o.optString("p"); optom = o.optString("o"); qty = o.optString("q"); barcode = o.optString("b")
                }
            }
        }
        out
    },
)
