package uz.kassiron.app.ui.reports

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.Attendance
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSection
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KToneIcon
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.theme.K
import uz.kassiron.app.ui.theme.Tone

private data class AttendanceData(
    val open: Attendance.Shift?,
    val mine: List<Attendance.Shift>,
    val today: List<Attendance.Shift>?,
    val openCount: Long,
)

private fun duration(context: Context, minutes: Long): String =
    if (minutes < 60) context.getString(R.string.att_minutes, minutes.toInt())
    else context.getString(R.string.att_duration, (minutes / 60).toInt(), (minutes % 60).toInt())

/** "Davomat": clock in / out, today's shifts (owner), my last 7 days. */
@Composable
fun AttendanceScreen(nav: NavHostController) {
    val user by App.session.user.collectAsState()
    val u = user ?: return
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var askOut by remember { mutableStateOf(false) }
    val data by dbState<AttendanceData?>(null, u.uuid) {
        val db = App.db
        val today = Time.today()
        AttendanceData(
            open = Attendance.openShift(db, u.uuid),
            mine = Attendance.history(db, Time.addDays(today, -6), today, u.uuid),
            today = if (u.isOwner) Attendance.history(db, today, today) else null,
            openCount = Attendance.openCount(db),
        )
    }

    fun clock(out: Boolean) {
        scope.launch {
            val ok = io { if (out) Attendance.clockOut(u.uuid) else Attendance.clockIn(u.uuid) }
            if (ok) App.sync.requestSync()
            snackbar.showSnackbar(context.getString(if (!ok) R.string.att_failed else if (out) R.string.att_out_done else R.string.att_in_done))
        }
    }

    KScreen(topBar = { KTopBar(stringResource(R.string.att_title), onBack = { nav.popBackStack() }) }, snackbar = snackbar) {
        val d = data ?: return@KScreen
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 2.dp, bottom = 20.dp)) {
            item("me") {
                val on = d.open != null
                KCard(Modifier.padding(horizontal = Edge)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KToneIcon(Icons.Outlined.AccessTime, if (on) Tone(K.c.onPrimary, K.c.primary) else Tone(K.c.mu, K.c.s2), 42.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(
                                stringResource(if (on) R.string.att_working else R.string.att_not_working),
                                style = K.t.title.copy(fontSize = 15.sp), color = K.c.tx,
                            )
                            Text(
                                if (on) stringResource(R.string.att_since, Time.local(d.open!!.clockIn, "HH:mm"), duration(context, Attendance.minutes(d.open.clockIn, null)))
                                else u.fullName,
                                style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.mu,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (on) KButton(stringResource(R.string.att_out), { askOut = true }, Modifier.fillMaxWidth(), kind = KButtonKind.Danger, icon = Icons.AutoMirrored.Outlined.Logout)
                    else KButton(stringResource(R.string.att_in), { clock(false) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.PlayArrow)
                }
            }
            if (d.today != null) {
                item("today-title") {
                    KSection(stringResource(R.string.att_today), stringResource(R.string.att_working_now, d.openCount.toInt()), {})
                }
                if (d.today.isEmpty()) item("today-empty") { MutedNote(stringResource(R.string.att_empty)) }
                items(d.today, key = { "t" + it.uuid }) { s -> ShiftRow(s, showName = true) }
            }
            item("mine-title") { KSection(stringResource(R.string.att_mine)) }
            if (d.mine.isEmpty()) item("mine-empty") { MutedNote(stringResource(R.string.att_empty)) }
            items(d.mine, key = { "m" + it.uuid }) { s -> ShiftRow(s, showName = false) }
        }
    }
    if (askOut) KConfirm(
        title = stringResource(R.string.att_out_confirm),
        text = null,
        confirm = stringResource(R.string.att_out),
        dismiss = stringResource(R.string.bo_cancel),
        onConfirm = { askOut = false; clock(true) },
        onDismiss = { askOut = false },
        danger = true,
    )
}

@Composable
private fun ShiftRow(s: Attendance.Shift, showName: Boolean) {
    val context = LocalContext.current
    val ongoing = s.clockOut == null
    KRow(
        if (showName) s.userName.ifBlank { "—" } else Time.local(s.clockIn, "dd.MM.yyyy"),
        Modifier.padding(horizontal = Edge, vertical = 4.dp),
        leading = if (showName) ({ KAvatar(s.userName) }) else null,
        subtitle = {
            KSub(Time.local(s.clockIn, "HH:mm") + " – " + (s.clockOut?.let { Time.local(it, "HH:mm") } ?: stringResource(R.string.att_ongoing)))
        },
        trailing = {
            Text(
                duration(context, Attendance.minutes(s.clockIn, s.clockOut)),
                style = K.t.amount.copy(fontSize = 14.sp), color = if (ongoing) K.c.ink else K.c.tx, maxLines = 1,
            )
        },
    )
}

