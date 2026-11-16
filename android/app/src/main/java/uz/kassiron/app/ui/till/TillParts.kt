package uz.kassiron.app.ui.till

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.Customers
import uz.kassiron.app.ui.common.AmountField
import uz.kassiron.app.ui.common.SearchField
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.theme.Brand

// ---------------------------------------------------------------------------------------------
// Cart

@Composable
internal fun CartPanel(
    vm: TillViewModel,
    onQty: (CartItem) -> Unit,
    onNoStock: (CartItem) -> Unit,
    onClear: () -> Unit,
    onPay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.till_cart) + if (vm.cart.isNotEmpty()) " (${vm.cart.size})" else "",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (vm.cart.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Filled.DeleteSweep, null)
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.till_clear))
                }
            }
        }
        if (vm.cart.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.ShoppingCart, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.till_cart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(vm.cart, key = { it.key }) { item ->
                    CartRow(
                        item,
                        onPlus = { if (!vm.setQty(item.key, item.qty + 1)) onNoStock(item) },
                        onMinus = { vm.setQty(item.key, if (item.qty > 1) item.qty - 1 else 0.0) },
                        onQty = { onQty(item) },
                        onRemove = { vm.remove(item.key) },
                    )
                }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.till_total), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Money.format(vm.subtotal), style = MaterialTheme.typography.headlineMedium)
        }
        Button(
            onClick = onPay,
            enabled = vm.cart.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).height(60.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text(stringResource(R.string.till_pay), style = MaterialTheme.typography.titleMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CartRow(item: CartItem, onPlus: () -> Unit, onMinus: () -> Unit, onQty: () -> Unit, onRemove: () -> Unit) {
    val state = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        if (v != SwipeToDismissBoxValue.Settled) onRemove()
        v != SwipeToDismissBoxValue.Settled
    })
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().padding(horizontal = 20.dp),
                contentAlignment = if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.name + (item.variantLabel?.let { " — $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        Money.format(item.price) + " · " + Money.format(item.subtotal),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Remove, null) }
                Surface(
                    onClick = onQty,
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp).widthIn(min = 52.dp).height(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                        Text(Money.qty(item.qty), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                }
                FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(44.dp)) { Icon(Icons.Filled.Add, null) }
                IconButton(onClick = onRemove, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Filled.Close, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun QtyDialog(item: CartItem, onSet: (Double) -> Boolean, onDismiss: () -> Unit) {
    val start = Money.qty(item.qty)
    var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val wholeMsg = stringResource(R.string.till_qty_whole)
    val stockMsg = stringResource(R.string.till_qty_over_stock, Money.qty(item.stock) + " " + item.unit)
    val invalidMsg = stringResource(R.string.till_err_invalid_qty)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun submit() {
        val qty = Money.parse(text.text)
        when {
            qty == null || qty < 0 -> error = invalidMsg
            !Units.fits(qty, item.unit) -> error = wholeMsg
            !onSet(qty) -> error = stockMsg
            else -> onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name + (item.variantLabel?.let { " — $it" } ?: ""), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.copy(text = v.text.filter { it.isDigit() || ((it == ',' || it == '.') && item.fractional) }); error = null },
                    label = { Text(stringResource(R.string.till_qty) + " (" + item.unit + ")") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = if (item.fractional) KeyboardType.Decimal else KeyboardType.Number),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    stringResource(R.string.till_stock, Money.qty(item.stock) + " " + item.unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = { submit() }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
internal fun VariantDialog(product: TillProduct, onPick: (TillVariant) -> Unit, onDismiss: () -> Unit) {
    val variants by dbState<List<TillVariant>?>(null, product.uuid) { TillData.variants(product) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.till_pick_variant, product.name), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(variants.orEmpty(), key = { it.uuid }) { v ->
                    Surface(
                        onClick = { onPick(v) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp).heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(v.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.till_stock, Money.qty(v.stock) + " " + product.unit),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (v.stock <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(Money.format(v.price), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

// ---------------------------------------------------------------------------------------------
// Payment

@Composable
internal fun PaymentContent(vm: TillViewModel, canDiscount: Boolean, selling: Boolean, error: String?, onSell: () -> Unit) {
    var pickCustomer by remember { mutableStateOf(false) }
    val subtotal = vm.subtotal
    val discount = vm.discount(canDiscount)
    val discountBad = discount < 0 || (discount > 0 && discount >= subtotal - 0.005)
    val split = vm.split(canDiscount)
    val total = split.total

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
            .padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.till_to_pay), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(Money.format(total), style = MaterialTheme.typography.headlineMedium.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize * 1.2f))
        }
        if (discount > 0 && !discountBad) {
            Text(
                stringResource(R.string.till_subtotal_discount, Money.format(subtotal), Money.format(discount)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton(Icons.Filled.Payments, stringResource(R.string.till_pay_naqd), vm.mode == PayMode.NAQD, Modifier.weight(1f)) {
                vm.mode = PayMode.NAQD; vm.cashText = ""
            }
            ModeButton(Icons.Filled.CreditCard, stringResource(R.string.till_pay_karta), vm.mode == PayMode.KARTA, Modifier.weight(1f)) {
                vm.mode = PayMode.KARTA
            }
            ModeButton(Icons.Filled.CallSplit, stringResource(R.string.till_pay_split), vm.mode == PayMode.ARALASH, Modifier.weight(1f)) {
                vm.mode = PayMode.ARALASH; vm.cashText = ""; vm.kartaText = ""
            }
            ModeButton(Icons.Filled.MenuBook, stringResource(R.string.till_pay_qarz), vm.mode == PayMode.QARZ, Modifier.weight(1f)) {
                vm.mode = PayMode.QARZ; vm.cashText = ""
            }
        }

        when (vm.mode) {
            PayMode.NAQD -> {
                AmountField(vm.cashText, { vm.cashText = it }, stringResource(R.string.till_cash_given_hint, Money.format(total, false)))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickCash(total).forEachIndexed { i, v ->
                        SuggestionChip(
                            onClick = { vm.cashText = if (i == 0) "" else Money.format(v, false) },
                            label = {
                                Text(
                                    if (i == 0) stringResource(R.string.till_exact) else Money.format(v, false),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            },
                        )
                    }
                }
            }
            PayMode.KARTA -> Text(
                stringResource(R.string.till_card_exact, Money.format(total)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PayMode.ARALASH -> {
                AmountField(vm.kartaText, { vm.kartaText = it }, stringResource(R.string.till_pay_karta))
                val karta = Money.parse(vm.kartaText) ?: 0.0
                AmountField(
                    vm.cashText, { vm.cashText = it },
                    stringResource(R.string.till_pay_naqd) + " (" + Money.format(maxOf(0.0, Money.round2(total - karta)), false) + ")",
                )
            }
            PayMode.QARZ -> AmountField(vm.cashText, { vm.cashText = it }, stringResource(R.string.till_paid_now_optional))
        }

        if (canDiscount) {
            AmountField(vm.discountText, { vm.discountText = it }, stringResource(R.string.till_discount_sum))
            if (discountBad) {
                Text(stringResource(R.string.till_err_discount_too_large), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        CustomerField(vm.customer, required = split.qarz > 0, onPick = { pickCustomer = true }, onClear = { vm.customer = null })

        if (split.change > 0) {
            SummaryLine(stringResource(R.string.till_change), Money.format(split.change), Brand.Success, big = true)
        }
        if (split.qarz > 0) {
            SummaryLine(stringResource(R.string.till_debt), Money.format(split.qarz), MaterialTheme.colorScheme.error, big = split.change <= 0)
            vm.customer?.let { c ->
                Text(
                    stringResource(R.string.till_debt_after, Money.format(Money.round2(c.balance + split.qarz))),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (error != null) {
            Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp)) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(12.dp))
            }
        }

        Button(
            onClick = onSell,
            enabled = !selling && !discountBad && !(split.qarz > 0 && vm.customer == null),
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            if (selling) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text(stringResource(R.string.till_sell) + " · " + Money.format(total), style = MaterialTheme.typography.titleLarge)
            }
        }
    }

    if (pickCustomer) {
        CustomerPicker(onPick = { vm.customer = it; pickCustomer = false }, onDismiss = { pickCustomer = false })
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(68.dp),
        shape = RoundedCornerShape(14.dp),
        colors = colors,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String, color: Color, big: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        Text(value, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, color = color)
    }
}

@Composable
private fun CustomerField(customer: TillCustomer?, required: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    val borderColor = if (required && customer == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
    Surface(
        onClick = onPick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (required && customer == null) 2.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(10.dp))
            if (customer == null) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.till_choose_customer), style = MaterialTheme.typography.bodyLarge)
                    if (required) Text(stringResource(R.string.till_customer_required), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(customer.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOfNotNull(customer.phone, if (customer.balance > 0) stringResource(R.string.till_current_debt, Money.format(customer.balance)) else null)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (customer.balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClear) { Icon(Icons.Filled.Clear, null) }
            }
        }
    }
}

@Composable
private fun CustomerPicker(onPick: (TillCustomer) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val customers by dbState<List<TillCustomer>?>(null, query) { TillData.customers(query) }
    val nameRequired = stringResource(R.string.till_customer_name_required)
    val phoneInvalid = stringResource(R.string.till_phone_invalid)

    fun save() {
        val n = name.trim()
        val p = phone.trim()
        if (n.isEmpty()) { error = nameRequired; return }
        if (p.isNotEmpty() && Customers.normalizePhone(p) == null) { error = phoneInvalid; return }
        scope.launch {
            val c = io {
                val uuid = App.db.write { Customers.findOrCreate(this, n, p.ifEmpty { null }) }
                TillData.customer(uuid)
            }
            App.sync.requestSync()
            if (c != null) onPick(c)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 560.dp).fillMaxSize(0.88f).imePadding(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (creating) R.string.till_new_customer else R.string.till_choose_customer),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
                }
                if (creating) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = null },
                        label = { Text(stringResource(R.string.till_customer_name)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it.filter { c -> c.isDigit() || c in "+ -()" }; error = null },
                        label = { Text(stringResource(R.string.till_customer_phone)) },
                        placeholder = { Text("+998 90 123 45 67") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { creating = false }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        Button(onClick = { save() }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.common_save)) }
                    }
                } else {
                    SearchField(query, { query = it }, stringResource(R.string.till_customer_search))
                    OutlinedButton(
                        onClick = {
                            val digits = query.filter(Char::isDigit)
                            if (digits.length >= 2 && query.none(Char::isLetter)) { phone = query.trim(); name = "" } else { name = query.trim(); phone = "" }
                            error = null
                            creating = true
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Filled.PersonAdd, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.till_new_customer))
                    }
                    val list = customers
                    if (list != null && list.isEmpty()) {
                        Text(
                            stringResource(R.string.till_no_customers),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(list.orEmpty(), key = { it.uuid }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(c) }.heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    c.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                                if (c.balance > 0) {
                                    Text(Money.format(c.balance), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
