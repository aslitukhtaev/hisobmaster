package uz.kassiron.app.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uz.kassiron.app.R
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Time
import uz.kassiron.app.ui.common.AppTopBar
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.theme.Brand
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Small building blocks shared by the back-office screens (customers, sales,
// expenses, reports). Public so those packages can use them.

/**
 * A screen with a top bar, snackbar and optional FAB, inside AppNav's
 * scaffold (which already pads for the system bars, so they are consumed here).
 */
@Composable
fun BoScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.consumeWindowInsets(WindowInsets.systemBars),
        topBar = { AppTopBar(title, onBack, actions) },
        snackbarHost = { if (snackbar != null) SnackbarHost(snackbar) },
        floatingActionButton = fab,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}

@Composable
fun NoAccess() {
    EmptyState(Icons.Filled.Lock, stringResource(R.string.dash_no_access))
}

object Tones {
    val Blue = Color(0xFF2563EB)
    val Violet = Color(0xFF7C3AED)
    val Teal = Color(0xFF0D9488)
    val Amber = Color(0xFFD97706)
}

@Composable
fun paymentLabel(type: String): String = when (type) {
    "naqd" -> stringResource(R.string.sales_pay_naqd)
    "karta" -> stringResource(R.string.sales_pay_karta)
    "qr" -> stringResource(R.string.sales_pay_qr)
    "qarz" -> stringResource(R.string.sales_pay_qarz)
    "aralash" -> stringResource(R.string.sales_pay_aralash)
    else -> type
}

fun paymentColor(type: String): Color = when (type) {
    "naqd" -> Brand.Success
    "karta" -> Tones.Blue
    "qr" -> Tones.Violet
    "qarz" -> Brand.Danger
    else -> Brand.Warning
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun PaymentBadge(type: String) = Pill(paymentLabel(type), paymentColor(type))

/** A figure tile: icon, label and a big value. */
@Composable
fun StatCard(label: String, value: String, icon: ImageVector, tone: Color, modifier: Modifier = Modifier, sub: String? = null, onClick: (() -> Unit)? = null) {
    Card(
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.size(36.dp).background(tone.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(20.dp)) }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** "label ........ value" on one line. */
@Composable
fun AmountLine(label: String, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(value, color = color, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

// ---- dates ----

private fun utcFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

/** A local date "yyyy-MM-dd" as the date pickers count it (UTC midnight). */
fun dateToPickerMillis(date: String): Long? = runCatching { utcFormat().parse(date)?.time }.getOrNull()

fun pickerMillisToDate(ms: Long): String = utcFormat().format(java.util.Date(ms))

fun rangeLabel(from: String, to: String): String =
    if (from == to) Time.showDate(from) else "${Time.showDate(from)} – ${Time.showDate(to)}"

@OptIn(ExperimentalMaterial3Api::class)
private object NotFuture : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        utcTimeMillis <= (dateToPickerMillis(Time.today()) ?: Long.MAX_VALUE)
}

/** A button showing the range; opens a date range picker. */
@Composable
fun DateRangeButton(from: String, to: String, onChange: (String, String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { open = true }, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(rangeLabel(from, to), maxLines = 1)
    }
    if (open) DateRangeDialog(from, to, onDismiss = { open = false }) { f, t -> open = false; onChange(f, t) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(from: String, to: String, onDismiss: () -> Unit, onPick: (String, String) -> Unit) {
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = dateToPickerMillis(from),
        initialSelectedEndDateMillis = dateToPickerMillis(to),
        selectableDates = NotFuture,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                colors = uz.kassiron.app.ui.theme.Btn.text(), enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: s
                    onPick(pickerMillisToDate(s), pickerMillisToDate(e))
                },
            ) { Text(stringResource(R.string.dash_ok)) }
        },
        dismissButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.weight(1f), showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDateDialog(date: String?, allowFuture: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = if (allowFuture) rememberDatePickerState(initialSelectedDateMillis = date?.let(::dateToPickerMillis))
    else rememberDatePickerState(initialSelectedDateMillis = date?.let(::dateToPickerMillis), selectableDates = NotFuture)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onPick(pickerMillisToDate(it)) }
            }) { Text(stringResource(R.string.dash_ok)) }
        },
        dismissButton = { TextButton(colors = uz.kassiron.app.ui.theme.Btn.text(), onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    ) { DatePicker(state = state) }
}

/** Plain money text helpers. */
fun money(v: Double): String = Money.format(v)

/** receipt_number(): the receipt_no, or a short id for a sale without one. */
fun receiptLabel(receiptNo: String?, uuid: String): String =
    receiptNo?.trim()?.takeIf { it.isNotEmpty() } ?: uuid.take(6).uppercase()
