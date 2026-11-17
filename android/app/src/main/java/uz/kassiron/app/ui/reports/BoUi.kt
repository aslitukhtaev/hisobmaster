package uz.kassiron.app.ui.reports

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.kassiron.app.R
import uz.kassiron.app.core.Time
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.kShadow
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Tone
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Small pieces shared by the back-office screens (customers, sales, expenses,
// reports) that the kit does not have. Built only from K.c / K.t.

/** The user may not open this section. */
@Composable
fun NoAccess() {
    KScreen {
        Spacer(Modifier.height(60.dp))
        KEmpty(Icons.Outlined.Lock, stringResource(R.string.bo_no_access), stringResource(R.string.bo_no_access_hint))
    }
}

// ---------------------------------------------------------------- payment types

@Composable
fun paymentLabel(type: String): String = when (type) {
    "naqd" -> stringResource(R.string.bo_pay_naqd)
    "karta" -> stringResource(R.string.bo_pay_karta)
    "qr" -> stringResource(R.string.bo_pay_qr)
    "qarz" -> stringResource(R.string.bo_pay_qarz)
    "aralash" -> stringResource(R.string.bo_pay_aralash)
    else -> type
}

fun paymentLabel(context: Context, type: String): String = when (type) {
    "naqd" -> context.getString(R.string.bo_pay_naqd)
    "karta" -> context.getString(R.string.bo_pay_karta)
    "qr" -> context.getString(R.string.bo_pay_qr)
    "qarz" -> context.getString(R.string.bo_pay_qarz)
    "aralash" -> context.getString(R.string.bo_pay_aralash)
    else -> type
}

/** Text colour of a payment type in totals lines (mockup: Naqd green, Karta blue, Qarz red). */
@Composable
fun paymentColor(type: String): Color = when (type) {
    "naqd" -> K.c.grn
    "karta" -> K.c.blue
    "qarz" -> K.c.red
    "qr" -> K.c.violet.fg
    else -> K.c.amb
}

/** The payment-type pill of a sale row. */
@Composable
fun PayTag(type: String) {
    when (type) {
        "naqd" -> KTag(paymentLabel(type), KTagKind.Green)
        "qarz" -> KTag(paymentLabel(type), KTagKind.Red)
        "aralash" -> KTag(paymentLabel(type), KTagKind.Low)
        "karta" -> ToneTag(paymentLabel(type), K.c.blueT)
        "qr" -> ToneTag(paymentLabel(type), K.c.violet)
        else -> KTag(paymentLabel(type))
    }
}

/** A pill like KTag in any tone (blue for Karta). */
@Composable
fun ToneTag(text: String, tone: Tone) {
    Text(
        text, style = K.t.chip.copy(fontSize = 11.5.sp), color = tone.fg, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(tone.bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

// ---------------------------------------------------------------- rows and fields

/**
 * A list row card with the exact look of KRow, for rows whose title is more
 * than a plain string (receipt number + time, amounts in the title line).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 58.dp)
            .kShadow(shape).clip(shape).background(K.c.s1)
            .then(if (onClick != null || onLongClick != null) Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        trailing?.invoke()
    }
}

/** Trailing column of a row: amount on top, small things (tags) under it. */
@Composable
fun EndColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/**
 * A field-looking box that opens a picker (date): label above, value, calendar
 * icon; an optional clear button when [onClear] is given.
 */
@Composable
fun PickerField(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier, placeholder: Boolean = false, onClear: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier) {
        Text(label, style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(shape).background(K.c.s1).border(1.dp, K.c.bd, shape)
                .clickable(onClick = onClick).padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value, style = K.t.body.copy(fontSize = 15.sp), color = if (placeholder) K.c.mu else K.c.tx,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (onClear != null) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.bo_clear), tint = K.c.mu, modifier = Modifier.size(18.dp))
                }
            }
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = K.c.mu, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** A plain muted line inside a card for an empty block ("Smenalar yo‘q"). */
@Composable
fun MutedNote(text: String, modifier: Modifier = Modifier) {
    Text(text, style = K.t.body.copy(fontSize = 13.5.sp), color = K.c.mu, modifier = modifier.fillMaxWidth().padding(horizontal = Edge + 2.dp, vertical = 6.dp))
}

// ---------------------------------------------------------------- formatting

/** receipt_number(): the receipt_no, or a short id for a sale without one. */
fun receiptLabel(receiptNo: String?, uuid: String): String =
    receiptNo?.trim()?.takeIf { it.isNotEmpty() } ?: uuid.take(6).uppercase()

fun rangeLabel(from: String, to: String): String =
    if (from == to) Time.showDate(from) else "${Time.showDate(from).take(5)} – ${Time.showDate(to)}"

/** "+998909998877" -> "+998 90 999 88 77"; anything else is shown as typed. */
fun formatPhone(raw: String?): String {
    val p = raw?.trim().orEmpty()
    val d = p.filter(Char::isDigit)
    return when {
        d.length == 12 && d.startsWith("998") -> "+998 ${d.substring(3, 5)} ${d.substring(5, 8)} ${d.substring(8, 10)} ${d.substring(10, 12)}"
        d.length == 9 -> "${d.substring(0, 2)} ${d.substring(2, 5)} ${d.substring(5, 7)} ${d.substring(7, 9)}"
        else -> p
    }
}

/** "dd.MM" of a local date "yyyy-MM-dd". */
fun shortDate(localDate: String): String = Time.showDate(localDate).take(5)

// ---------------------------------------------------------------- date pickers

private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** A local date "yyyy-MM-dd" as the date pickers count it (UTC midnight). */
fun dateToPickerMillis(date: String): Long? = runCatching { utcFormat().parse(date)?.time }.getOrNull()

fun pickerMillisToDate(ms: Long): String = utcFormat().format(java.util.Date(ms))

@OptIn(ExperimentalMaterial3Api::class)
private object NotFuture : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= (dateToPickerMillis(Time.today()) ?: Long.MAX_VALUE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun pickerColors() = DatePickerDefaults.colors(
    containerColor = K.c.s1,
    titleContentColor = K.c.mu,
    headlineContentColor = K.c.tx,
    weekdayContentColor = K.c.mu,
    subheadContentColor = K.c.mu,
    navigationContentColor = K.c.tx,
    yearContentColor = K.c.tx,
    currentYearContentColor = K.c.ink,
    selectedYearContentColor = K.c.onPrimary,
    selectedYearContainerColor = K.c.primary,
    dayContentColor = K.c.tx,
    disabledDayContentColor = K.c.mu.copy(alpha = 0.4f),
    selectedDayContentColor = K.c.onPrimary,
    selectedDayContainerColor = K.c.primary,
    todayContentColor = K.c.ink,
    todayDateBorderColor = K.c.primary,
    dayInSelectionRangeContentColor = K.c.tx,
    dayInSelectionRangeContainerColor = K.c.tint,
    dividerColor = K.c.bd,
)

@Composable
private fun PickerTitle(text: String) {
    Text(text, style = K.t.screenTitle.copy(fontSize = 19.sp), color = K.c.tx, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp))
}

/** Date range picker (no future days). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(from: String, to: String, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = dateToPickerMillis(from),
        initialSelectedEndDateMillis = dateToPickerMillis(to),
        selectableDates = NotFuture,
    )
    val colors = pickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        shape = RoundedCornerShape(22.dp),
        confirmButton = {
            KButton(
                stringResource(R.string.bo_pick), height = 44.dp, fontSize = 14.sp, enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@KButton
                    onPick(pickerMillisToDate(s), pickerMillisToDate(state.selectedEndDateMillis ?: s))
                },
                modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
            )
        },
        dismissButton = { KButton(stringResource(R.string.bo_cancel), onDismiss, kind = KButtonKind.Alt, height = 44.dp, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp, bottom = 6.dp)) },
    ) {
        DateRangePicker(
            state = state, modifier = Modifier.weight(1f), showModeToggle = false, colors = colors,
            title = { PickerTitle(stringResource(R.string.bo_pick_range)) },
        )
    }
}

/** Single date picker; [allowFuture] false = no days after today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDateDialog(date: String?, allowFuture: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val initial = date?.let(::dateToPickerMillis)
    val state = if (allowFuture) rememberDatePickerState(initialSelectedDateMillis = initial)
    else rememberDatePickerState(initialSelectedDateMillis = initial, selectableDates = NotFuture)
    val colors = pickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        shape = RoundedCornerShape(22.dp),
        confirmButton = {
            KButton(
                stringResource(R.string.bo_pick), height = 44.dp, fontSize = 14.sp, enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let { onPick(pickerMillisToDate(it)) } },
                modifier = Modifier.padding(end = 6.dp, bottom = 6.dp),
            )
        },
        dismissButton = { KButton(stringResource(R.string.bo_cancel), onDismiss, kind = KButtonKind.Alt, height = 44.dp, fontSize = 14.sp, modifier = Modifier.padding(end = 4.dp, bottom = 6.dp)) },
    ) {
        DatePicker(state = state, colors = colors, showModeToggle = false, title = { PickerTitle(stringResource(R.string.bo_pick_date)) })
    }
}

/** A thin spacer between blocks. */
@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
