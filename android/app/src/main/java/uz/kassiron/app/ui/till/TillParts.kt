package uz.kassiron.app.ui.till

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.Customers
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAmountField
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KChoiceTiles
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KMoney
import uz.kassiron.app.ui.kit.KOption
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KSearch
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.KValueChip
import uz.kassiron.app.ui.theme.K

// ---------------------------------------------------------------------------------------------
// Small private building blocks (made from K.c / K.t; the shared kit has no equivalent)

/** Lets a full-bleed kit block (KSearch, which pads itself by [Edge]) sit flush inside a sheet that already pads by [Edge]. */
internal fun Modifier.bleed(by: Dp = Edge): Modifier = layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
}

/** A dashed horizontal line through the middle of the box. */
internal fun Modifier.drawDashes(color: Color): Modifier = drawBehind {
    drawLine(
        color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
        strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )
}

/** Change / debt line of the payment sheet (mockup .chg): tint box, label left, amount right. */
@Composable
private fun AmountBox(label: String, amount: Double, danger: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(if (danger) K.c.redBg else K.c.tint)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = K.t.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = K.c.tx, modifier = Modifier.weight(1f))
        KMoney(amount, size = 19.sp, color = if (danger) K.c.red else K.c.ink)
    }
}

/** The small two-up buttons of the payment sheet (mockup .two): outline, lime when [on]. */
@Composable
private fun SmallToggle(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onClear: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    val fg = if (on) K.c.ink else K.c.mu
    Row(
        modifier.height(44.dp).clip(shape).background(if (on) K.c.tint else Color.Transparent)
            .border(1.dp, if (on) K.c.primary else K.c.bd, shape).clickable(onClick = onClick)
            .padding(start = 10.dp, end = if (onClear != null) 2.dp else 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = K.t.label.copy(fontSize = 13.sp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (onClear != null) {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Close, stringResource(R.string.common_delete), tint = fg, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** "Mijoz (majburiy)": a picker-looking field with a red border and message when missing. */
@Composable
private fun CustomerField(customer: TillCustomer?, error: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.till_customer_required_label), style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(shape).background(K.c.s1)
                .border(if (error != null) 1.5.dp else 1.dp, if (error != null) K.c.red else K.c.bd, shape)
                .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Person, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                if (customer == null) {
                    Text(stringResource(R.string.till_choose_customer_ph), style = K.t.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = K.c.mu, maxLines = 1)
                } else {
                    Text(customer.name, style = K.t.body.copy(fontSize = 15.sp), color = K.c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (customer.balance > 0.005) KSub(stringResource(R.string.till_current_debt, Money.format(customer.balance)), K.c.red)
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
        }
        if (error != null) Text(error, style = K.t.small, color = K.c.red, modifier = Modifier.padding(top = 5.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Payment (mockups 8 and 9)

@Composable
internal fun PaymentSheet(
    vm: TillViewModel,
    optom: Boolean = false,
    canDiscount: Boolean,
    selling: Boolean,
    error: String?,
    onSell: () -> Unit,
    onPickCustomer: () -> Unit,
    onDismiss: () -> Unit,
) {
    KSheet(onDismiss = { if (!selling) onDismiss() }) {
        PaymentContent(vm, canDiscount, selling, error, onSell, onPickCustomer, optom)
    }
}

@Composable
internal fun PaymentContent(
    vm: TillViewModel,
    canDiscount: Boolean,
    selling: Boolean,
    error: String?,
    onSell: () -> Unit,
    onPickCustomer: () -> Unit,
    optom: Boolean = false,
) {
    var showDiscount by remember { mutableStateOf(vm.discountText.isNotEmpty()) }
    val subtotal = vm.subtotal
    val discount = vm.discount(canDiscount)
    val discountBad = discount < 0 || (discount > 0 && discount >= subtotal - 0.005)
    val split = vm.split(canDiscount)
    val total = split.total
    val totalText = Money.format(total, false)

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Row(Modifier.weight(1f).alignByBaseline(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.till_to_pay), style = K.t.label.copy(fontSize = 13.sp), color = K.c.mu)
                if (optom) KTag(stringResource(R.string.till_wholesale_prices), KTagKind.Low)
            }
            KMoney(total, size = 32.sp, modifier = Modifier.alignByBaseline())
        }
        if (discount > 0 && !discountBad) {
            Text(
                stringResource(R.string.till_subtotal_discount, Money.format(subtotal), Money.format(discount)),
                style = K.t.small, color = K.c.mu, modifier = Modifier.align(Alignment.End),
            )
        }
        Spacer(Modifier.height(12.dp))

        val modes = listOf(PayMode.NAQD, PayMode.KARTA, PayMode.QARZ)
        KChoiceTiles(
            options = listOf(
                KOption(stringResource(R.string.till_pay_naqd), Icons.Outlined.Payments),
                KOption(stringResource(R.string.till_pay_karta), Icons.Outlined.CreditCard),
                KOption(stringResource(R.string.till_pay_qarz), Icons.AutoMirrored.Outlined.MenuBook),
            ),
            selected = modes.indexOf(vm.mode),
            onSelect = { i ->
                vm.mode = modes[i]
                vm.cashText = ""; vm.kartaText = ""; vm.debtText = ""
            },
        )
        Spacer(Modifier.height(10.dp))

        when (vm.mode) {
            PayMode.NAQD -> {
                KAmountField(vm.cashText, { vm.cashText = it }, label = stringResource(R.string.till_cash_given), placeholder = totalText, big = true)
                Spacer(Modifier.height(10.dp))
                val given = Money.parse(vm.cashText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickCash(total).forEachIndexed { i, v ->
                        val selected = if (i == 0) given == null || kotlin.math.abs(given - v) < 0.005 else given != null && kotlin.math.abs(given - v) < 0.005
                        KValueChip(
                            label = if (i == 0) stringResource(R.string.till_exact) else Money.format(v, false),
                            selected = selected,
                            onClick = { vm.cashText = if (i == 0) "" else Money.format(v, false) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (split.qarz > 0.005) AmountBox(stringResource(R.string.till_short_debt), split.qarz, danger = true)
                else AmountBox(stringResource(R.string.till_change), split.change)
                Spacer(Modifier.height(10.dp))
            }
            PayMode.KARTA -> {
                KAmountField(vm.kartaText, { vm.kartaText = it }, label = stringResource(R.string.till_card_amount), placeholder = totalText, big = true)
                Spacer(Modifier.height(10.dp))
                if (split.naqd > 0.005) {
                    AmountBox(stringResource(R.string.till_rest_cash), split.naqd)
                    Spacer(Modifier.height(10.dp))
                }
            }
            PayMode.QARZ -> {
                KAmountField(vm.debtText, { vm.debtText = it }, label = stringResource(R.string.till_debt_amount), placeholder = totalText, big = true)
                if (split.naqd > 0.005) {
                    Spacer(Modifier.height(10.dp))
                    AmountBox(stringResource(R.string.till_rest_cash), split.naqd)
                }
                Spacer(Modifier.height(12.dp))
                CustomerField(
                    vm.customer,
                    error = if (vm.customer == null && split.qarz > 0.005) stringResource(R.string.till_customer_required) else null,
                    onClick = onPickCustomer,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val c = vm.customer
            SmallToggle(
                Icons.Outlined.Person, c?.name ?: stringResource(R.string.till_customer), on = c != null,
                onClick = onPickCustomer, modifier = Modifier.weight(1f),
                onClear = if (c != null) ({ vm.customer = null }) else null,
            )
            if (canDiscount) {
                SmallToggle(
                    Icons.Outlined.Percent, stringResource(R.string.till_discount), on = showDiscount || discount > 0,
                    onClick = { showDiscount = !showDiscount; if (!showDiscount) vm.discountText = "" },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (canDiscount && showDiscount) {
            KAmountField(
                vm.discountText, { vm.discountText = it }, label = stringResource(R.string.till_discount_sum),
                error = if (discountBad) stringResource(R.string.till_err_discount_too_large) else null,
            )
            Spacer(Modifier.height(12.dp))
        }
        if (error != null) {
            Text(error, style = K.t.label.copy(fontSize = 13.sp), color = K.c.red, modifier = Modifier.padding(bottom = 10.dp))
        }
        KButton(
            text = when {
                selling -> stringResource(R.string.till_selling)
                vm.mode == PayMode.QARZ -> stringResource(R.string.till_sell_debt)
                else -> stringResource(R.string.till_sell_total, Money.format(total))
            },
            onClick = onSell,
            modifier = Modifier.fillMaxWidth(),
            height = 54.dp,
            enabled = !selling && !discountBad,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Customer picker (mockup 10)

@Composable
internal fun CustomerPickerSheet(initialQuery: String = "", onPick: (TillCustomer) -> Unit, onDismiss: () -> Unit) {
    KSheet(onDismiss = onDismiss) { CustomerPickerContent(initialQuery, onPick, onDismiss) }
}

private fun looksLikePhone(q: String) = q.count(Char::isDigit) >= 2 && q.none(Char::isLetter)

@Composable
internal fun CustomerPickerContent(initialQuery: String, onPick: (TillCustomer) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val customers by dbState<List<TillCustomer>?>(null, query) { TillData.customers(query) }
    val nameRequired = stringResource(R.string.till_customer_name_required)
    val phoneInvalid = stringResource(R.string.till_phone_invalid)

    fun create(n: String, p: String) {
        if (n.isBlank()) { error = nameRequired; return }
        if (p.isNotBlank() && Customers.normalizePhone(p) == null) { error = phoneInvalid; return }
        scope.launch {
            val c = io {
                val uuid = App.db.write { Customers.findOrCreate(this, n.trim(), p.trim().ifEmpty { null }) }
                TillData.customer(uuid)
            }
            App.sync.requestSync()
            if (c != null) onPick(c)
        }
    }

    if (creating) {
        KSheetTitle(stringResource(R.string.till_new_customer), onClose = { creating = false; error = null })
        KField(name, { name = it; error = null }, label = stringResource(R.string.till_customer_name), placeholder = stringResource(R.string.till_customer_name_ph))
        Spacer(Modifier.height(12.dp))
        KField(
            phone, { phone = it.filter { c -> c.isDigit() || c in "+ -()" }; error = null },
            label = stringResource(R.string.till_customer_phone), placeholder = "+998 90 123 45 67", keyboard = KeyboardType.Phone,
            error = error,
        )
        Spacer(Modifier.height(16.dp))
        KButton(stringResource(R.string.common_save), { create(name, phone) }, Modifier.fillMaxWidth())
        return
    }

    KSheetTitle(stringResource(R.string.till_choose_customer), onClose)
    KSearch(query, { query = it }, stringResource(R.string.till_customer_search), highlight = true, modifier = Modifier.bleed())
    Spacer(Modifier.height(10.dp))
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        val q = query.trim()
        if (q.isEmpty()) {
            item(key = "new") {
                NewCustomerRow(stringResource(R.string.till_new_customer), null) { name = ""; phone = ""; error = null; creating = true }
            }
        }
        items(customers.orEmpty(), key = { it.uuid }) { c ->
            KRow(
                title = c.name,
                subtitle = c.phone?.let { { KSub(Receipts.phone(it)) } },
                leading = { KAvatar(c.name) },
                trailing = {
                    if (c.balance > 0.005) KTag(stringResource(R.string.till_current_debt, Money.format(c.balance, false)), KTagKind.Red)
                    else KTag(stringResource(R.string.till_no_debt), KTagKind.Neutral)
                },
                onClick = { onPick(c) },
            )
        }
        if (q.isNotEmpty()) {
            item(key = "new") {
                NewCustomerRow(
                    stringResource(R.string.till_new_customer_named, q),
                    if (looksLikePhone(q)) null else stringResource(R.string.till_new_customer_hint),
                ) {
                    if (looksLikePhone(q)) { name = ""; phone = q; error = null; creating = true } else create(q, "")
                }
            }
        }
    }
}

/** The dashed lime "Yangi mijoz: «…»" row. */
@Composable
private fun NewCustomerRow(title: String, hint: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val border = K.c.primary
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 58.dp).clip(shape).background(K.c.tint)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    border, topLeft = Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(16.dp.toPx() - w / 2),
                    style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(K.c.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, tint = K.c.onPrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = K.t.title, color = K.c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (hint != null) KSub(hint)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Quantity and variants

@Composable
internal fun QtySheet(item: CartItem, onSet: (Double) -> Boolean, onDismiss: () -> Unit) {
    // Starts empty with the current amount as the hint: the cashier just types the new number.
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val wholeMsg = stringResource(R.string.till_qty_whole)
    val stockMsg = stringResource(R.string.till_qty_over_stock, Money.qty(item.stock) + " " + item.unit)
    val invalidMsg = stringResource(R.string.till_err_invalid_qty)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        if (text.isBlank()) { onDismiss(); return }
        val qty = Money.parse(text)
        when {
            qty == null || qty < 0 -> error = invalidMsg
            !Units.fits(qty, item.unit) -> error = wholeMsg
            !onSet(qty) -> error = stockMsg
            else -> onDismiss()
        }
    }

    KSheet(onDismiss = onDismiss) {
        KSheetTitle(item.name + (item.variantLabel?.let { " · $it" } ?: ""), onDismiss)
        KAmountField(
            text, { v -> text = v.filter { it.isDigit() || ((it == ',' || it == '.') && item.fractional) }; error = null },
            modifier = Modifier.focusRequester(focus),
            label = stringResource(R.string.till_qty, item.unit), placeholder = Money.qty(item.qty), big = true, error = error, onDone = { submit() },
        )
        if (error == null) {
            Spacer(Modifier.height(6.dp))
            KSub(stringResource(R.string.till_in_stock, Money.qty(item.stock) + " " + item.unit))
        }
        Spacer(Modifier.height(16.dp))
        KButton(stringResource(R.string.common_save), { submit() }, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun VariantSheet(product: TillProduct, optom: Boolean = false, onPick: (TillVariant) -> Unit, onDismiss: () -> Unit) {
    val variants by dbState<List<TillVariant>?>(null, product.uuid) { TillData.variants(product) }
    KSheet(onDismiss = onDismiss) {
        KSheetTitle(product.name, onDismiss)
        KSub(stringResource(R.string.till_pick_variant))
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(variants.orEmpty(), key = { it.uuid }) { v ->
                KRow(
                    title = v.label,
                    subtitle = { StockLine(v.stock, product.unit, v.stock <= 0 || (product.lowThreshold != null && v.stock <= product.lowThreshold)) },
                    trailing = { KMoney(v.priceFor(product, optom)) },
                    onClick = { onPick(v) },
                )
            }
        }
    }
}
