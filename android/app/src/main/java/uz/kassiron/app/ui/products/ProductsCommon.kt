package uz.kassiron.app.ui.products

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.domain.ProductException
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KSheet
import uz.kassiron.app.ui.kit.KSheetTitle
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.theme.K

/** A translated message for a [ProductException] (or any failure). */
fun Context.productError(e: Throwable): String {
    if (e !is ProductException) return getString(R.string.products_err_unexpected)
    val arg = e.arg ?: ""
    return when (e.code) {
        ProductException.FILL_REQUIRED -> getString(R.string.products_err_name_required)
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

/** "+998901234567" → "+998 90 123 45 67" (anything else is shown as stored). */
fun showPhone(raw: String): String {
    val d = raw.filter(Char::isDigit)
    return if (d.length == 12 && d.startsWith("998")) "+998 ${d.substring(3, 5)} ${d.substring(5, 8)} ${d.substring(8, 10)} ${d.substring(10)}" else raw
}

/** Keeps only what a number field may hold: digits, , and . */
fun numberInput(v: String): String = v.filter { it.isDigit() || it == ',' || it == '.' }

object ProductDefaults {
    /** The shop-wide low stock threshold, read off the main thread. */
    @Composable
    fun rememberShopDefault(): Double? {
        val v by dbState<Double?>(null) { ProductService.shopLowStockDefault() }
        return v
    }
}

/** Shows "15000" as "15 000" while typing (digits only; anything else is shown as typed). */
object GroupThousands : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        if (s.length <= 3 || !s.all { it.isDigit() }) return TransformedText(text, OffsetMapping.Identity)
        val n = s.length
        val sb = StringBuilder()
        val o2t = IntArray(n + 1)
        for (i in 0 until n) {
            if (i > 0 && (n - i) % 3 == 0) sb.append(' ')
            o2t[i] = sb.length
            sb.append(s[i])
        }
        o2t[n] = sb.length
        val t = sb.toString()
        val t2o = IntArray(t.length + 1)
        var digits = 0
        for (j in 0..t.length) {
            t2o[j] = digits
            if (j < t.length && t[j] != ' ') digits++
        }
        return TransformedText(AnnotatedString(t), object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = o2t[offset.coerceIn(0, n)]
            override fun transformedToOriginal(offset: Int) = t2o[offset.coerceIn(0, t.length)]
        })
    }
}

/**
 * A money / quantity field drawn exactly like KField, but showing "15 000" while
 * the value stays "15000" (KField has no visual transformation). Digits, "," and "." only.
 */
@Composable
fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "0",
    error: String? = null,
    group: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val style = K.t.body.copy(fontSize = 15.sp, color = K.c.tx)
    Column(modifier) {
        if (label != null) FieldLabel(label)
        BasicTextField(
            value = value,
            onValueChange = { onChange(numberInput(it)) },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(K.c.ink),
            interactionSource = interaction,
            visualTransformation = if (group) GroupThousands else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (focused && error == null) Modifier.border(4.dp, K.c.glow, RoundedCornerShape(17.dp)).padding(3.dp) else Modifier)
                        .heightIn(min = 50.dp).clip(shape).background(K.c.s1)
                        .border(if (focused || error != null) 1.5.dp else 1.dp, if (error != null) K.c.red else if (focused) K.c.primary else K.c.bd, shape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        if (value.isEmpty()) Text(placeholder, style = style.copy(fontWeight = FontWeight.Medium), color = K.c.mu, maxLines = 1)
                        inner()
                    }
                }
            },
        )
        if (error != null) Text(error, style = K.t.small, color = K.c.red, modifier = Modifier.padding(top = 5.dp))
    }
}

/** The small lime scan button inside a field (mockup 16: Shtrix-kod). */
@Composable
fun ScanInside(onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(K.c.primary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.QrCodeScanner, stringResource(R.string.products_scan), tint = K.c.onPrimary, modifier = Modifier.size(18.dp))
    }
}

/** The label above a field or a group of chips, as KField draws it. */
@Composable
fun FieldLabel(text: String) {
    Text(text, style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
}

/**
 * A field that opens a picker (Kategoriya, Ta’minotchi): looks like a KField,
 * shows the chosen value (or a muted placeholder) and a chevron.
 */
@Composable
fun PickField(
    label: String,
    value: String?,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    chevron: Boolean = true,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier) {
        FieldLabel(label)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(shape).background(K.c.s1)
                .border(1.dp, K.c.bd, shape).then(if (chevron) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value ?: placeholder,
                style = K.t.body.copy(fontSize = 15.sp, fontWeight = if (value == null) FontWeight.Medium else FontWeight.SemiBold),
                color = if (value == null) K.c.mu else K.c.tx,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (chevron) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
        }
        if (hint != null) Text(hint, style = K.t.small, color = K.c.mu, modifier = Modifier.padding(top = 5.dp))
    }
}

/** One choice of a [PickSheet]; id null = "none" (Kategoriyasiz, Ta’minotchisiz). */
data class PickItem(val id: String?, val title: String, val sub: String? = null)

/**
 * A picker sheet with search and inline create (like the customer picker of
 * the till, mockup 10): typing filters the list; a name that is not there
 * yet can be created right away.
 */
@Composable
fun PickSheet(
    title: String,
    items: List<PickItem>,
    selected: String?,
    searchHint: String,
    newTitle: (String) -> String,
    newSub: String,
    onPick: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val shown = remember(items, q) { if (q.isEmpty()) items else items.filter { it.id != null && it.title.contains(q, ignoreCase = true) } }
    val exact = q.isNotEmpty() && items.any { it.id != null && it.title.equals(q, ignoreCase = true) }
    KSheet(onDismiss) {
        KSheetTitle(title, onDismiss)
        KField(query, { query = it }, placeholder = searchHint, leading = Icons.Outlined.Search)
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 440.dp).padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (q.isNotEmpty() && !exact) {
                item(key = "new") { NewRow(newTitle(q), newSub) { onCreate(q) } }
            }
            items(shown, key = { it.id ?: "none" }) { item ->
                KRow(
                    title = item.title,
                    selected = item.id == selected,
                    subtitle = item.sub?.let { { KSub(it) } },
                    trailing = if (item.id == selected) ({ Icon(Icons.Outlined.Check, null, tint = K.c.ink, modifier = Modifier.size(20.dp)) }) else null,
                    onClick = { onPick(item.id) },
                )
            }
        }
    }
}

/** The dashed lime "create «…»" row of the pickers. */
@Composable
fun NewRow(title: String, sub: String?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val line = K.c.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(shape).background(K.c.tint)
            .drawBehind {
                drawRoundRect(
                    line, cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(K.c.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, tint = K.c.onPrimary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = K.t.title, color = K.c.tx, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) KSub(sub)
        }
    }
}

/** A sheet with one name field and a Saqlash button (new / rename category). */
@Composable
fun NameSheet(
    title: String,
    label: String,
    placeholder: String,
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    KSheet(onDismiss) {
        KSheetTitle(title, onDismiss)
        KField(value, onChange, label = label, placeholder = placeholder, error = error, onDone = onSave)
        Spacer(Modifier.size(14.dp))
        KButton(stringResource(R.string.common_save), onSave, Modifier.fillMaxWidth(), enabled = value.isNotBlank())
    }
}
