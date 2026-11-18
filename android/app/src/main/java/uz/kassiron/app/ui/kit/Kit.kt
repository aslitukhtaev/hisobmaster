package uz.kassiron.app.ui.kit

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kassiron.app.core.Money
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Manrope
import uz.kassiron.app.ui.theme.Onest
import uz.kassiron.app.ui.theme.Tone

/*
 * The KassirON design kit — every building block of android/design/mockup.html.
 * Screens use only these (plus layout primitives), so every screen looks the same.
 * Sizes follow the mockup: screen edge 14dp, row cards 16dp radius, cards 18dp,
 * buttons 52dp (46 small, 40 compact), inputs 50dp, chips 32dp, icon buttons 40dp.
 */

val Edge = 14.dp

// ------------------------------------------------------------------------------------------------
// Screen frame
// ------------------------------------------------------------------------------------------------

/**
 * A screen: page background, optional top bar, scrollable or fixed content,
 * an optional bottom bar (cart, save button) and a snackbar host above it.
 */
@Composable
fun KScreen(
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    fab: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(K.c.bg)) {
        Column(Modifier.fillMaxSize()) {
            topBar?.invoke()
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            bottomBar?.invoke()
        }
        if (fab != null) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) { fab() }
        }
        if (snackbar != null) {
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp)) { data ->
                Snackbar(
                    data,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = if (K.c.dark) Color(0xFF242B1E) else Color(0xFF1B2015),
                    contentColor = Color(0xFFF4F6F1),
                    actionColor = K.c.primary,
                )
            }
        }
    }
}

/** Top bar: back button, title (Onest 800 20), actions on the right. 52dp tall. */
@Composable
fun KTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = Edge, end = Edge, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) KIconButton(Icons.AutoMirrored.Outlined.ArrowBack, onBack)
        Text(title, style = K.t.screenTitle, color = K.c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

/** 40dp square icon button (card background, hairline) — or lime when [primary]. */
@Composable
fun KIconButton(icon: ImageVector, onClick: () -> Unit, primary: Boolean = false, size: Dp = 40.dp, contentDescription: String? = null, tint: Color? = null) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp))
            .background(if (primary) K.c.primary else K.c.s1)
            .border(1.dp, if (primary) K.c.primary else K.c.bd, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint ?: if (primary) K.c.onPrimary else K.c.mu, modifier = Modifier.size(20.dp))
    }
}

// ------------------------------------------------------------------------------------------------
// Text helpers
// ------------------------------------------------------------------------------------------------

/** "19 000 so'm": the number in Onest 800, "so'm" small and muted. */
@Composable
fun KMoney(
    amount: Double,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = K.c.tx,
    currency: Boolean = true,
    signed: Boolean = false,
) {
    val sign = if (signed && amount > 0) "+" else ""
    val text = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = size, color = color)) {
            append(sign + Money.format(amount, withCurrency = false))
        }
        if (currency) {
            withStyle(SpanStyle(fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.68f).coerceAtLeast(11f).sp, color = K.c.mu)) {
                append(" so‘m")
            }
        }
    }
    Text(text, modifier = modifier, maxLines = 1, softWrap = false)
}

@Composable
fun KText(text: String, style: TextStyle, color: Color = K.c.tx, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null) {
    Text(text, style = style, color = color, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

/** A section header inside a screen ("So‘nggi sotuvlar" ... "Hammasi"). */
@Composable
fun KSection(title: String, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = Edge + 2.dp, end = Edge + 2.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = K.t.section, color = K.c.tx, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, style = K.t.label, color = K.c.ink, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(4.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Buttons
// ------------------------------------------------------------------------------------------------

enum class KButtonKind { Primary, Alt, Danger }

/**
 * The main button. Primary: lime with a soft glow (one per screen). Alt: neutral.
 * Danger: red outline on a red tint (refund, disconnect).
 */
@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: KButtonKind = KButtonKind.Primary,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    height: Dp = 52.dp,
    enabled: Boolean = true,
    fontSize: TextUnit = 16.sp,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(if (height >= 50.dp) 15.dp else 12.dp)
    val (bg, fg, border) = when (kind) {
        KButtonKind.Primary -> Triple(K.c.primary, K.c.onPrimary, null)
        KButtonKind.Alt -> Triple(K.c.s2, K.c.tx, K.c.bd)
        KButtonKind.Danger -> Triple(K.c.redBg, K.c.red, K.c.red)
    }
    val glow = kind == KButtonKind.Primary && enabled
    Row(
        modifier
            .height(height)
            .then(if (glow) Modifier.shadow(14.dp, shape, ambientColor = K.c.glow, spotColor = K.c.primary) else Modifier)
            .clip(shape)
            .background(if (enabled) bg else K.c.s2)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) fg else K.c.mu
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = K.t.button.copy(fontSize = fontSize), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailingIcon != null) {
            Spacer(Modifier.width(8.dp))
            Icon(trailingIcon, null, tint = color, modifier = Modifier.size(20.dp))
        }
    }
}

/** A floating action button: 54dp lime square with a plus (or any icon). */
@Composable
fun KFab(icon: ImageVector, onClick: () -> Unit, contentDescription: String? = null) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        Modifier.size(54.dp).shadow(16.dp, shape, ambientColor = K.c.glow, spotColor = K.c.primary)
            .clip(shape).background(K.c.primary).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = K.c.onPrimary, modifier = Modifier.size(26.dp)) }
}

// ------------------------------------------------------------------------------------------------
// Inputs
// ------------------------------------------------------------------------------------------------

/**
 * A text field: label above, 50dp box (56dp and Onest 22 when [big]), lime ring
 * when focused, red border and message on [error]. [trailing] e.g. a scan button.
 */
@Composable
fun KField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    big: Boolean = false,
    error: String? = null,
    hint: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
    leading: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(14.dp)
    val borderColor by animateColorAsState(
        when {
            error != null -> K.c.red
            focused -> K.c.primary
            else -> K.c.bd
        }, label = "border",
    )
    Column(modifier) {
        if (label != null) {
            Text(label, style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
        }
        val textStyle = if (big) TextStyle(fontFamily = Onest, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = K.c.tx)
            else K.t.body.copy(fontSize = 15.sp, color = K.c.tx)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            cursorBrush = SolidColor(K.c.ink),
            interactionSource = interaction,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth()
                        .then(if (focused && error == null) Modifier.border(4.dp, K.c.glow, RoundedCornerShape(17.dp)).padding(3.dp) else Modifier)
                        .heightIn(min = if (big) 56.dp else 50.dp)
                        .clip(shape).background(K.c.s1)
                        .border(if (focused || error != null) 1.5.dp else 1.dp, borderColor, shape)
                        .padding(start = 14.dp, end = if (trailing != null) 6.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leading != null) {
                        Icon(leading, null, tint = K.c.mu, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
                        if (value.isEmpty()) Text(placeholder, style = textStyle.copy(color = K.c.mu, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Medium), maxLines = 1)
                        inner()
                    }
                    trailing?.invoke()
                }
            },
        )
        val message = error ?: hint
        if (message != null) {
            Text(message, style = K.t.small, color = if (error != null) K.c.red else K.c.mu, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

/** Money / quantity input: digits, spaces, "," and "." only; decimal keyboard. */
@Composable
fun KAmountField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, placeholder: String = "0", big: Boolean = false, error: String? = null, onDone: (() -> Unit)? = null) {
    KField(
        value = value,
        onChange = { v -> onChange(v.filter { it.isDigit() || it == ' ' || it == ',' || it == '.' }) },
        modifier = modifier, label = label, placeholder = placeholder, big = big, error = error,
        keyboard = KeyboardType.Decimal, onDone = onDone,
    )
}

/**
 * The search box (48dp): magnifier, text, clear button, and an optional scan
 * button (lime in the till, grey elsewhere). [highlight] = the lime ring the till keeps.
 */
@Composable
fun KSearch(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onScan: (() -> Unit)? = null,
    scanLime: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interaction.collectIsFocusedAsState()
    val ring = highlight || focused
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = K.t.body.copy(fontSize = 15.sp, color = K.c.tx),
        cursorBrush = SolidColor(K.c.ink),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
        modifier = modifier.fillMaxWidth().padding(horizontal = Edge),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth()
                    .then(if (ring) Modifier.border(4.dp, K.c.glow, RoundedCornerShape(17.dp)).padding(3.dp) else Modifier)
                    .height(48.dp).clip(shape).background(K.c.s1)
                    .border(if (ring) 1.5.dp else 1.dp, if (ring) K.c.primary else K.c.bd, shape)
                    .padding(start = 14.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = K.c.mu, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = K.t.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = K.c.mu, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(Modifier.size(38.dp).clip(CircleShape).clickable { onChange("") }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
                    }
                }
                if (onScan != null) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(if (scanLime) K.c.primary else K.c.s2).clickable(onClick = onScan),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.QrCodeScanner, null, tint = if (scanLime) K.c.onPrimary else K.c.mu, modifier = Modifier.size(20.dp)) }
                }
            }
        },
    )
}

/**
 * The 6-box code input of "Kod bilan ulash": a hidden text field shown as boxes.
 */
@Composable
fun KCodeInput(value: String, onChange: (String) -> Unit, length: Int = 6, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    BasicTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(length)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth(),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                repeat(length) { i ->
                    val active = i == value.length
                    val shape = RoundedCornerShape(12.dp)
                    Box(
                        Modifier.size(width = 46.dp, height = 58.dp)
                            .then(if (active) Modifier.border(4.dp, K.c.glow, RoundedCornerShape(15.dp)).padding(3.dp) else Modifier)
                            .clip(shape).background(K.c.s1)
                            .border(if (active) 1.5.dp else 1.dp, if (active) K.c.primary else K.c.bd, shape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(value.getOrNull(i)?.toString() ?: "", style = K.t.hero, color = K.c.tx)
                    }
                }
            }
        },
    )
}

// ------------------------------------------------------------------------------------------------
// Selection
// ------------------------------------------------------------------------------------------------

/** Filter chips row (horizontally scrollable): the selected one is lime. */
@Composable
fun KChips(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Edge, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEachIndexed { i, label -> KChip(label, i == selected, { onSelect(i) }) }
    }
}

@Composable
fun KChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier.height(34.dp).clip(shape)
            .background(if (selected) K.c.primary else K.c.s1)
            .border(1.dp, if (selected) K.c.primary else K.c.bd, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = K.t.chip, color = if (selected) K.c.onPrimary else K.c.mu, maxLines = 1) }
}

/** A quick-value chip (payment amounts): flat box, lime border when selected. */
@Composable
fun KValueChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.height(42.dp).clip(shape).background(K.c.s2)
            .border(1.dp, if (selected) K.c.primary else K.c.bd, shape)
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = K.t.chip.copy(fontSize = 13.sp), color = if (selected) K.c.ink else K.c.tx, maxLines = 1) }
}

data class KOption(val label: String, val icon: ImageVector? = null)

/** Segmented control (login / code, UZ / RU, 58 / 80 mm). */
@Composable
fun KSegmented(options: List<KOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 40.dp) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(K.c.s1).border(1.dp, K.c.bd, shape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Row(
                Modifier.weight(1f).height(height).clip(RoundedCornerShape(10.dp))
                    .background(if (on) K.c.primary else Color.Transparent)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (o.icon != null) { Icon(o.icon, null, tint = if (on) K.c.onPrimary else K.c.mu, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
                Text(o.label, style = K.t.label.copy(fontSize = 13.5.sp), color = if (on) K.c.onPrimary else K.c.mu, maxLines = 1)
            }
        }
    }
}

/** The payment-method tiles (Naqd / Karta / Qarz): 50dp, icon + label, lime with glow when chosen. */
@Composable
fun KChoiceTiles(options: List<KOption>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).height(50.dp)
                    .then(if (on) Modifier.shadow(10.dp, shape, ambientColor = K.c.glow, spotColor = K.c.primary) else Modifier)
                    .clip(shape).background(if (on) K.c.primary else K.c.s2)
                    .border(1.dp, if (on) K.c.primary else K.c.bd, shape)
                    .clickable { onSelect(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (o.icon != null) { Icon(o.icon, null, tint = if (on) K.c.onPrimary else K.c.mu, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)) }
                Text(o.label, style = K.t.label.copy(fontSize = 14.sp), color = if (on) K.c.onPrimary else K.c.mu, maxLines = 1)
            }
        }
    }
}

/** On/off switch in the lime style. */
@Composable
fun KSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (checked) K.c.primary else K.c.bd, label = "track")
    Box(
        Modifier.size(width = 46.dp, height = 28.dp).clip(RoundedCornerShape(999.dp)).background(track).clickable { onChange(!checked) }.padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) { Box(Modifier.size(22.dp).clip(CircleShape).background(if (checked) K.c.onPrimary else Color.White)) }
}

/** − qty + : the cart / purchase quantity stepper. Tap the number to type it. */
@Composable
fun KStepper(qty: String, onMinus: () -> Unit, onPlus: () -> Unit, onQtyClick: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val shape = RoundedCornerShape(10.dp)
        Box(Modifier.size(36.dp).clip(shape).background(K.c.s1).border(1.dp, K.c.bd, shape).clickable(onClick = onMinus), contentAlignment = Alignment.Center) {
            Text("−", style = K.t.button.copy(fontSize = 20.sp), color = K.c.tx)
        }
        if (onQtyClick != null) {
            // Drawn as a field, so it is clear the number can be typed.
            Box(
                Modifier.padding(horizontal = 2.dp).height(36.dp).widthIn(min = 46.dp).clip(shape).background(K.c.s1).border(1.dp, K.c.bd, shape)
                    .clickable(onClick = onQtyClick).padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(qty, style = K.t.amount, color = K.c.tx, textAlign = TextAlign.Center, maxLines = 1)
            }
        } else {
            Text(
                qty, style = K.t.amount, color = K.c.tx, textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 34.dp).padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        Box(Modifier.size(36.dp).clip(shape).background(K.c.primary).clickable(onClick = onPlus), contentAlignment = Alignment.Center) {
            Text("+", style = K.t.button.copy(fontSize = 20.sp), color = K.c.onPrimary)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Surfaces
// ------------------------------------------------------------------------------------------------

enum class KCardTone { Plain, Tint, Red, Green, Amber }

/** A card: 18dp radius, surface + soft shadow (Plain) or a coloured tint. */
@Composable
fun KCard(
    modifier: Modifier = Modifier,
    tone: KCardTone = KCardTone.Plain,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(14.dp),
    radius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val (bg, border) = when (tone) {
        KCardTone.Plain -> K.c.s1 to null
        KCardTone.Tint -> K.c.tint to null
        KCardTone.Red -> K.c.redBg to K.c.red.copy(alpha = 0.45f)
        KCardTone.Green -> K.c.grnBg to K.c.grn.copy(alpha = 0.6f)
        KCardTone.Amber -> K.c.ambBg to K.c.amb.copy(alpha = 0.45f)
    }
    Column(
        modifier.fillMaxWidth()
            .then(if (tone == KCardTone.Plain) Modifier.kShadow(shape) else Modifier)
            .clip(shape).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** The mockup's soft card shadow (stronger in the dark theme, barely there in light). */
@Composable
fun Modifier.kShadow(shape: RoundedCornerShape): Modifier =
    shadow(if (K.c.dark) 6.dp else 3.dp, shape, ambientColor = Color.Black.copy(alpha = if (K.c.dark) 0.5f else 0.12f), spotColor = Color.Black.copy(alpha = if (K.c.dark) 0.5f else 0.14f))

/**
 * A list row card (min 58dp, 16dp radius): leading (avatar / icon), title,
 * subtitle line(s), trailing (price, tag, stepper). [selected] = lime border + tint.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    titleColor: Color = K.c.tx,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = if (selected) 64.dp else 58.dp)
            .then(if (!selected) Modifier.kShadow(shape) else Modifier)
            .clip(shape).background(if (selected) K.c.tint else K.c.s1)
            .border(1.5.dp, if (selected) K.c.primary else if (danger) K.c.red else Color.Transparent, shape)
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = K.t.title, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.invoke()
        }
        trailing?.invoke()
    }
}

/** A subtitle line under a row title: 12sp, muted. */
@Composable
fun KSub(text: String, color: Color = K.c.mu) {
    Text(text, style = K.t.small, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Vertical list spacing: a column of rows with 8dp gaps and the screen edge. */
@Composable
fun KList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Edge), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

enum class KTagKind { Ok, Low, Red, Green, Neutral }

/** A small pill: stock level, payment type, status. */
@Composable
fun KTag(text: String, kind: KTagKind = KTagKind.Neutral) {
    val (bg, fg) = when (kind) {
        KTagKind.Ok -> K.c.tint to K.c.ink
        KTagKind.Low -> K.c.ambBg to K.c.amb
        KTagKind.Red -> K.c.redBg to K.c.red
        KTagKind.Green -> K.c.grnBg to K.c.grn
        KTagKind.Neutral -> K.c.s2 to K.c.mu
    }
    Text(text, style = K.t.chip.copy(fontSize = 11.5.sp), color = fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** Initials avatar (38dp; 72dp on the sign-in screens). */
@Composable
fun KAvatar(name: String, size: Dp = 38.dp, neutral: Boolean = false) {
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        Modifier.size(size).clip(CircleShape).background(if (neutral) K.c.s2 else K.c.tint),
        contentAlignment = Alignment.Center,
    ) { Text(initials, style = K.t.button.copy(fontSize = (size.value * 0.34f).sp), color = if (neutral) K.c.mu else K.c.ink) }
}

/** A square icon on a soft coloured background (menu tiles, KPI cards, ledger). */
@Composable
fun KToneIcon(icon: ImageVector, tone: Tone, size: Dp = 36.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.31f)).background(tone.bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tone.fg, modifier = Modifier.size(size * 0.56f))
    }
}

/** Amber / red / green notice bar (low stock, offline, errors). */
@Composable
fun KAlert(text: String, icon: ImageVector, modifier: Modifier = Modifier, tone: KCardTone = KCardTone.Amber, onClick: (() -> Unit)? = null) {
    val (bg, fg) = when (tone) {
        KCardTone.Red -> K.c.redBg to K.c.red
        KCardTone.Green -> K.c.grnBg to K.c.grn
        else -> K.c.ambBg to K.c.amb
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().padding(horizontal = Edge).heightIn(min = 46.dp).clip(shape).background(bg)
            .border(1.dp, fg.copy(alpha = 0.5f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(text, style = K.t.label.copy(fontSize = 13.sp), color = K.c.tx, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = fg, modifier = Modifier.size(18.dp))
    }
}

/** A KPI card: tone icon left, label and value right, soft tone circle in the corner. */
@Composable
fun KKpi(icon: ImageVector, label: String, tone: Tone, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, value: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.kShadow(shape).clip(shape).background(K.c.s1)
            .drawBehind { drawCircle(tone.bg.copy(alpha = tone.bg.alpha * 0.7f), radius = 40.dp.toPx(), center = Offset(size.width + 2.dp.toPx(), size.height + 2.dp.toPx())) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        KToneIcon(icon, tone)
        Column(Modifier.weight(1f)) {
            Text(label, style = K.t.small.copy(fontSize = 11.5.sp), color = K.c.mu, maxLines = 1)
            value()
        }
    }
}

/**
 * The dashboard hero: dark card with a lime glow and dotted texture (the
 * website's .dash-hero), in both themes.
 */
@Composable
fun KHero(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = Edge).clip(shape).background(K.c.heroBg)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0x5CA9FA06), Color(0x00A9FA06)), center = Offset(size.width - 20.dp.toPx(), 10.dp.toPx()), radius = 190.dp.toPx()),
                    radius = 190.dp.toPx(), center = Offset(size.width - 20.dp.toPx(), 10.dp.toPx()),
                )
                val step = 18.dp.toPx()
                var x = size.width * 0.45f
                while (x < size.width) {
                    var y = step / 2
                    val a = ((x - size.width * 0.45f) / (size.width * 0.55f)).coerceIn(0f, 1f) * 0.08f
                    while (y < size.height) { drawCircle(Color.White.copy(alpha = a), 1.dp.toPx(), Offset(x, y)); y += step }
                    x += step
                }
            }
            .padding(16.dp),
        content = content,
    )
}

/** Settings-style grouped rows in one card. */
@Composable
fun KGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.fillMaxWidth().padding(horizontal = Edge).kShadow(shape).clip(shape).background(K.c.s1), content = content)
}

/** One row of a [KGroup]: icon, title, value, trailing (chevron by default when clickable). */
@Composable
fun KGroupRow(
    title: String,
    icon: ImageVector? = null,
    value: String? = null,
    first: Boolean = false,
    onClick: (() -> Unit)? = null,
    titleColor: Color = K.c.tx,
    trailing: (@Composable () -> Unit)? = null,
) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(K.c.bd))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = K.c.mu, modifier = Modifier.size(20.dp))
        Text(title, style = K.t.title.copy(fontSize = 14.5.sp), color = titleColor, modifier = Modifier.weight(1f))
        if (value != null) Text(value, style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.mu, maxLines = 1)
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
        }
    }
}

/** Key–value line (receipt details, sale details). */
@Composable
fun KKeyValue(key: String, first: Boolean = false, value: @Composable () -> Unit) {
    if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(K.c.bd))
    Row(Modifier.fillMaxWidth().heightIn(min = 38.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = K.t.body.copy(fontSize = 14.sp), color = K.c.mu, modifier = Modifier.weight(1f))
        value()
    }
}

/** A menu tile ("Ko‘proq" grid): tone icon over a label. */
@Composable
fun KTile(label: String, icon: ImageVector, tone: Tone, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.kShadow(shape).clip(shape).background(K.c.s1).clickable(onClick = onClick).padding(top = 14.dp, bottom = 12.dp, start = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KToneIcon(icon, tone, 40.dp)
        Text(label, style = K.t.label.copy(fontSize = 12.sp), color = K.c.tx, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Empty list: big muted icon, title, one line, optional action. */
@Composable
fun KEmpty(icon: ImageVector, title: String, text: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KToneIcon(icon, uz.kassiron.app.ui.theme.Tone(K.c.mu, K.c.s2), 64.dp)
        Spacer(Modifier.height(4.dp))
        Text(title, style = K.t.title.copy(fontSize = 16.sp), color = K.c.tx, textAlign = TextAlign.Center)
        if (text != null) Text(text, style = K.t.small.copy(fontSize = 13.sp), color = K.c.mu, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            KButton(action, onAction, height = 46.dp, fontSize = 14.sp)
        }
    }
}

/** A thin lime progress bar. */
@Composable
fun KProgress(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)).background(K.c.bd)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(99.dp)).background(K.c.primary))
    }
}

/** A horizontal bar row (report breakdowns): label, bar, amount. */
@Composable
fun KBar(label: String, fraction: Float, amount: Double, color: Color = K.c.primary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.mu, modifier = Modifier.width(88.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(99.dp)).background(color))
        }
        KMoney(amount, size = 13.sp, currency = false)
    }
}

/** The bar fixed at the bottom of a screen (save button, cart): bg + top hairline. */
@Composable
fun KBottomBar(modifier: Modifier = Modifier, background: Color = K.c.bg, content: @Composable ColumnScope.() -> Unit) {
    val line = K.c.bd
    Column(
        modifier.fillMaxWidth().background(background)
            .drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = Edge, vertical = 10.dp),
        content = content,
    )
}

/** A hairline separator. */
@Composable
fun KDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(K.c.bd))
}

// ------------------------------------------------------------------------------------------------
// Sheets and dialogs
// ------------------------------------------------------------------------------------------------

/** Bottom sheet: 24dp top corners, small grab handle, card colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KSheet(onDismiss: () -> Unit, skipPartial: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartial)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = K.c.s1,
        contentColor = K.c.tx,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(4.dp)).background(K.c.bd))
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Edge).padding(bottom = 14.dp).navigationBarsPadding(), content = content)
    }
}

/** Title row of a sheet with a close button. */
@Composable
fun KSheetTitle(title: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = K.t.screenTitle, color = K.c.tx, modifier = Modifier.weight(1f))
        KIconButton(Icons.Outlined.Close, onClose)
    }
}

/** A confirm dialog in the kit's style; [danger] makes the confirm button red. */
@Composable
fun KConfirm(title: String, text: String?, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = K.c.s1,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title, style = K.t.screenTitle.copy(fontSize = 19.sp), color = K.c.tx) },
        text = text?.let { { Text(it, style = K.t.body, color = K.c.mu) } },
        confirmButton = { KButton(confirm, onConfirm, kind = if (danger) KButtonKind.Danger else KButtonKind.Primary, height = 44.dp, fontSize = 14.sp) },
        dismissButton = { KButton(dismiss, onDismiss, kind = KButtonKind.Alt, height = 44.dp, fontSize = 14.sp) },
    )
}

/** A box helper to align content at the bottom-end of a screen area. */
@Composable
fun KOverlay(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) = Box(modifier.fillMaxSize(), content = content)
