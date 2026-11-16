package uz.kassiron.app.ui.products

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.ui.theme.Brand

/** A translated message for a [ProductException] (or any failure). */
fun Context.productError(e: Throwable): String {
    if (e !is ProductException) return getString(R.string.products_err_unexpected)
    val arg = e.arg ?: ""
    return when (e.code) {
        ProductException.FILL_REQUIRED -> getString(R.string.products_err_fill_required)
        ProductException.NAME_FORMULA -> getString(R.string.products_err_name_formula)
        ProductException.NAME_TOO_LONG -> getString(R.string.products_err_name_too_long)
        ProductException.NEGATIVE -> getString(R.string.products_err_negative)
        ProductException.TOO_LARGE -> getString(R.string.products_err_too_large)
        ProductException.QTY_WHOLE -> getString(R.string.products_err_qty_whole, arg)
        ProductException.BARCODE_TOO_LONG -> getString(R.string.products_err_barcode_too_long)
        ProductException.BARCODE_TAKEN -> getString(R.string.products_err_barcode_taken, arg)
        ProductException.BARCODE_DUPLICATE -> getString(R.string.products_err_barcode_duplicate)
        ProductException.PACK_SIZE -> getString(R.string.products_err_pack_size)
        ProductException.MXIK -> getString(R.string.products_err_mxik)
        ProductException.VAT -> getString(R.string.products_err_vat)
        ProductException.VARIANT_LABEL -> getString(R.string.products_err_variant_label)
        ProductException.PRICE_REQUIRED -> getString(R.string.products_err_price_required)
        ProductException.UNIT_TOO_LONG -> getString(R.string.products_err_unit_too_long)
        ProductException.NOT_FOUND -> getString(R.string.products_err_not_found)
        ProductException.PHONE_INVALID -> getString(R.string.suppliers_err_phone)
        ProductException.NO_ITEMS -> getString(R.string.purchases_err_no_items)
        ProductException.INVALID_PRODUCT -> getString(R.string.purchases_err_invalid_product)
        ProductException.INVALID_QTY -> getString(R.string.purchases_err_invalid_qty)
        ProductException.VARIANT_REQUIRED -> getString(R.string.purchases_err_variant_required, arg)
        ProductException.QTY_WHOLE_PRODUCT -> getString(R.string.purchases_err_qty_whole, arg)
        ProductException.NAME_TAKEN -> getString(R.string.categories_err_taken)
        else -> getString(R.string.products_err_unexpected)
    }
}

/** "12 dona", "1,5 kg". */
fun qtyUnit(qty: Double, unit: String?): String = Money.qty(qty) + (unit?.takeIf { it.isNotBlank() }?.let { " $it" } ?: "")

/** A number as an input field shows it: "15000", "1.5" -> "1,5"; blank for null. */
fun editNumber(v: Double?): String = v?.let { Money.qty(it) } ?: ""

object StockColors {
    val Out = Brand.Danger
    val Low = Color(0xFFD97706)
}

/** The products area's scaffold: our top bar, snackbars, no extra insets (AppNav pads already). */
@Composable
fun ProductsScaffold(
    topBar: @Composable () -> Unit,
    snackbar: SnackbarHostState,
    floatingActionButton: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = topBar,
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = floatingActionButton,
        bottomBar = bottomBar,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

/** A plain text input with a label, rounded like the shared fields. */
@Composable
fun FormField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    supporting: String? = null,
    isError: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onIme: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = if (onIme != null) KeyboardActions(onAny = { onIme() }) else KeyboardActions.Default,
        shape = RoundedCornerShape(14.dp),
    )
}

/** Keeps only what a number field may hold: digits, spaces, , and . */
fun numberInput(v: String): String = v.filter { it.isDigit() || it == ' ' || it == ',' || it == '.' }

@Composable
fun ScanButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.products_scan))
    }
}

/** The unit: free text plus a menu of the common units. */
@Composable
fun UnitField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = stringResource(R.string.products_unit)) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        FormField(
            value = value,
            onChange = { onChange(it.take(20)) },
            label = label,
            trailing = {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
            },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Units.COMMON.forEach { u ->
                DropdownMenuItem(text = { Text(u) }, onClick = { onChange(u); open = false })
            }
        }
    }
}

/** A label/value line for detail screens. */
@Composable
fun InfoLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
