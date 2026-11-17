package uz.kassiron.app.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MobileOff
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.sync.SyncException
import uz.kassiron.app.sync.SyncManager
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.KAvatar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KButtonKind
import uz.kassiron.app.ui.kit.KCard
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KCodeInput
import uz.kassiron.app.ui.kit.KConfirm
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KField
import uz.kassiron.app.ui.kit.KIconButton
import uz.kassiron.app.ui.kit.KKeyValue
import uz.kassiron.app.ui.kit.KOption
import uz.kassiron.app.ui.kit.KProgress
import uz.kassiron.app.ui.kit.KSegmented
import uz.kassiron.app.ui.kit.KTag
import uz.kassiron.app.ui.kit.KTagKind
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.resetTo
import uz.kassiron.app.ui.theme.K

/** Support contacts shown when the subscription ended. */
private const val SUPPORT_TELEGRAM = "https://t.me/aslitukhtaev"
private const val SUPPORT_PHONE = "+998997026720"

/** Side padding of the sign-in screens (a little wider than the usual 14dp, as in the mockup). */
private val AuthEdge = 18.dp

// ======================================================================
// Activation (mockups 1–2): the owner's login + password, or a 6-digit code.
// ======================================================================

@Composable
fun ActivationScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Activated earlier but the first download didn't finish.
    var interrupted by remember { mutableStateOf(false) }
    val downloaded by App.sync.downloaded.collectAsState()

    LaunchedEffect(Unit) {
        interrupted = io { App.isActivated && !App.sync.downloadComplete }
    }

    fun run(block: suspend () -> Unit) {
        error = null
        busy = true
        scope.launch {
            try {
                block()
                nav.resetTo(Routes.LOGIN)
            } catch (e: SyncException) {
                error = e.code
                interrupted = io { App.isActivated && !App.sync.downloadComplete }
            } finally {
                busy = false
            }
        }
    }

    fun submit() {
        if (busy) return
        val problem = when {
            tab == 0 && login.isBlank() -> "fill_login"
            tab == 0 && password.isEmpty() -> "fill_password"
            tab == 1 && code.length != 6 -> "code_short"
            else -> null
        }
        if (problem != null) { error = problem; return }
        // The phone names itself ("Samsung SM-A515F"); the owner can rename it on the website.
        val name = SyncManager.defaultDeviceName()
        run {
            if (tab == 0) App.sync.activate(login, password, name)
            else App.sync.activateWithCode(code, name)
        }
    }

    when {
        busy -> DownloadScreen(downloaded)
        interrupted -> InterruptedContent(
            error = error,
            onResume = { run { App.sync.resumeDownload() } },
            onSkip = { nav.resetTo(Routes.LOGIN) },
        )
        else -> ActivationContent(
            tab = tab, onTab = { tab = it; error = null },
            login = login, onLogin = { login = it.trim(); if (error == "fill_login") error = null },
            password = password, onPassword = { password = it; if (error == "fill_password" || error == "login_failed") error = null },
            code = code, onCode = { code = it; if (error == "code_short" || error == "code_invalid") error = null },
            error = error,
            onSubmit = { submit() },
        )
    }
}

/** The form of mockups 1–2 (stateless, so screenshots can show every state). */
@Composable
internal fun ActivationContent(
    tab: Int, onTab: (Int) -> Unit,
    login: String, onLogin: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    code: String, onCode: (String) -> Unit,
    error: String?,
    onSubmit: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    AuthColumn {
        Spacer(Modifier.height(60.dp))
        KassirLogo(50.dp, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(36.dp))
        KSegmented(
            listOf(KOption(stringResource(R.string.auth_tab_password)), KOption(stringResource(R.string.auth_tab_code))),
            selected = tab, onSelect = onTab, modifier = Modifier.fillMaxWidth(),
        )
        val errorText = error?.let { activationErrorText(it) }
        if (tab == 0) {
            Spacer(Modifier.height(12.dp))
            KField(
                value = login, onChange = onLogin,
                label = stringResource(R.string.auth_login),
                placeholder = stringResource(R.string.auth_login_placeholder),
                keyboard = KeyboardType.Ascii,
                error = errorText.takeIf { error == "fill_login" },
            )
            Spacer(Modifier.height(12.dp))
            KField(
                value = password, onChange = onPassword,
                label = stringResource(R.string.auth_password),
                password = !showPassword,
                keyboard = KeyboardType.Password,
                onDone = onSubmit,
                error = errorText.takeIf { error == "fill_password" || error == "login_failed" },
                trailing = { EyeToggle(showPassword) { showPassword = !showPassword } },
            )
            if (error != null && error !in setOf("fill_login", "fill_password", "login_failed")) {
                Spacer(Modifier.height(14.dp))
                ErrorLine(errorText!!)
            }
            Spacer(Modifier.height(22.dp))
            KButton(stringResource(R.string.auth_connect), onSubmit, Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward, height = 54.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.auth_once), style = K.t.small.copy(fontSize = 12.5.sp), color = K.c.mu,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(20.dp))
            val place = stringResource(R.string.auth_code_place)
            val hint = stringResource(R.string.auth_code_hint, place)
            Text(
                buildAnnotatedString {
                    val at = hint.indexOf(place)
                    if (at < 0) append(hint) else {
                        append(hint.substring(0, at))
                        withStyle(SpanStyle(color = K.c.tx, fontWeight = FontWeight.ExtraBold)) { append(place) }
                        append(hint.substring(at + place.length))
                    }
                },
                style = K.t.body.copy(fontSize = 13.sp, lineHeight = 20.sp), color = K.c.mu,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            KCodeInput(code, onCode, onDone = onSubmit)
            if (errorText != null) {
                Spacer(Modifier.height(14.dp))
                ErrorLine(errorText, center = true)
            }
            Spacer(Modifier.height(24.dp))
            KButton(stringResource(R.string.auth_connect), onSubmit, Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward, height = 54.dp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Activation went through but the first download stopped (no internet): continue it. */
@Composable
private fun InterruptedContent(error: String?, onResume: () -> Unit, onSkip: () -> Unit) {
    AuthColumn {
        Spacer(Modifier.height(60.dp))
        KassirLogo(50.dp, Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(36.dp))
        KCard(tone = KCardTone.Amber) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.CloudOff, null, tint = K.c.amb, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.auth_download_interrupted), style = K.t.body.copy(fontSize = 14.sp), color = K.c.tx)
            }
        }
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            ErrorLine(activationErrorText(error))
        }
        Spacer(Modifier.height(22.dp))
        KButton(stringResource(R.string.auth_resume), onResume, Modifier.fillMaxWidth(), icon = Icons.Outlined.Refresh, height = 54.dp)
        Spacer(Modifier.height(6.dp))
        InkTextButton(stringResource(R.string.auth_skip), onSkip)
    }
}

// ======================================================================
// First download (mockup 3).
// ======================================================================

/** Server tables in snapshot order (app/Core/SyncSchema.php TABLES): used to show real progress. */
private const val SNAPSHOT_TABLES = 18
private const val T_CUSTOMERS = 3
private const val T_PRODUCTS = 4      // products, product_variants
private const val T_PURCHASES = 6     // purchases, purchase_items come before sales
private const val T_SALES_END = 11    // sales, sale_items, sale_payments

@Composable
private fun DownloadScreen(downloaded: Int?) {
    var shopName by remember { mutableStateOf("") }
    var table by remember { mutableIntStateOf(0) }
    var fraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(downloaded) {
        val (name, t, done) = io {
            val snap = App.kv.get(Keys.PULL_SNAP)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val finished = snap == null && App.kv.get(Keys.PULL_CURSOR) != null
            Triple(App.kv.shop().optString("name"), snap?.optInt("t", 0) ?: 0, finished)
        }
        shopName = name
        table = if (done) SNAPSHOT_TABLES else t
        fraction = if (done) 1f else t.toFloat() / SNAPSHOT_TABLES
    }
    DownloadContent(shopName, downloaded, table, fraction)
}

/** Logo, title, shop name, progress, record count and the steps card. [records] null = still connecting. */
@Composable
internal fun DownloadContent(shopName: String, records: Int?, table: Int, fraction: Float) {
    AuthColumn(center = true) {
        Spacer(Modifier.height(110.dp))
        KassirLogo(44.dp)
        Spacer(Modifier.height(34.dp))
        Text(
            stringResource(if (records == null) R.string.auth_connecting else R.string.auth_downloading),
            style = K.t.hero, color = K.c.tx, textAlign = TextAlign.Center,
        )
        if (shopName.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(shopName, style = K.t.body.copy(fontSize = 13.5.sp), color = K.c.mu, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        KProgress(fraction.coerceAtLeast(0.03f))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.auth_records, Money.format((records ?: 0).toDouble(), withCurrency = false)),
            style = K.t.label.copy(fontSize = 13.sp), color = K.c.mu,
        )
        if (records != null) {
            Spacer(Modifier.height(26.dp))
            KCard(padding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                DownloadStep(stringResource(R.string.auth_step_products), table, T_PRODUCTS, T_PURCHASES, first = true)
                DownloadStep(stringResource(R.string.auth_step_customers), table, T_CUSTOMERS, T_PRODUCTS)
                DownloadStep(stringResource(R.string.auth_step_sales), table, T_PURCHASES, T_SALES_END)
            }
        }
    }
}

/** One line of the steps card: done (✓), loading (lime tag) or waiting. Tables [from, to). */
@Composable
private fun DownloadStep(label: String, table: Int, from: Int, to: Int, first: Boolean = false) {
    KKeyValue(label, first = first) {
        when {
            table >= to -> Icon(Icons.Outlined.Check, null, tint = K.c.ink, modifier = Modifier.size(18.dp))
            table >= from -> KTag(stringResource(R.string.auth_step_loading), KTagKind.Ok)
            else -> KTag(stringResource(R.string.auth_step_waiting), KTagKind.Neutral)
        }
    }
}

// ======================================================================
// Login (mockups 4–5): who is working — pick a user, enter the password (checked offline).
// ======================================================================

private const val MAX_FAILURES = 5
private const val LOCK_SECONDS = 30

/** [preselect]: a users.uuid to open straight at the password step (screenshots). */
@Composable
fun LoginScreen(nav: NavHostController, preselect: String? = null) {
    val scope = rememberCoroutineScope()
    val users by dbState<List<DbRow>?>(null) {
        App.db.query(
            """SELECT uuid, full_name, role, password_hash, password_set FROM users
               WHERE status = 'active'
               ORDER BY CASE role WHEN 'owner' THEN 0 ELSE 1 END, full_name COLLATE NOCASE""",
        )
    }
    val shopName by dbState("") { App.kv.shop().optString("name") }
    val sync by App.sync.state.collectAsState()
    var selectedUuid by rememberSaveable { mutableStateOf(preselect) }
    val selected = users?.firstOrNull { it.str("uuid") == selectedUuid }

    BackHandler(enabled = selected != null) { selectedUuid = null }

    Box(Modifier.fillMaxSize().background(K.c.bg).imePadding()) {
        val list = users
        when {
            list == null -> Unit // reading (a few milliseconds)
            selected != null -> PasswordPanel(
                user = selected,
                onBack = { selectedUuid = null },
                onSignedIn = {
                    scope.launch {
                        io { App.session.signIn(selected.str("uuid")) }
                        nav.goHome()
                    }
                },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = AuthEdge, end = AuthEdge, top = 18.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        if (shopName.isNotBlank()) {
                            Text(shopName, style = K.t.label.copy(fontSize = 13.sp), color = K.c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(stringResource(R.string.auth_who), style = K.t.hero.copy(fontSize = 28.sp), color = K.c.tx, modifier = Modifier.padding(top = 2.dp))
                        Text(stringResource(R.string.auth_pick_user), style = K.t.body.copy(fontSize = 13.5.sp), color = K.c.mu, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (list.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            KEmpty(
                                Icons.Outlined.PersonOff,
                                stringResource(R.string.auth_no_users),
                                sync.error?.let { syncErrorText(it) } ?: stringResource(R.string.auth_no_users_text),
                                action = stringResource(R.string.auth_sync).takeIf { !sync.running },
                                onAction = { scope.launch { App.sync.syncNow() } },
                            )
                        }
                    }
                }
                items(list, key = { it.str("uuid") }) { user ->
                    UserTile(user) { selectedUuid = user.str("uuid") }
                }
            }
        }
    }
}

@Composable
private fun UserTile(user: DbRow, onClick: () -> Unit) {
    val owner = user.str("role") == "owner"
    KCard(onClick = onClick, padding = PaddingValues(horizontal = 8.dp, vertical = 18.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            KAvatar(user.str("full_name"), 72.dp, neutral = !owner)
            Spacer(Modifier.height(10.dp))
            Text(
                user.str("full_name"), style = K.t.title, color = K.c.tx,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text(roleLabel(user.str("role")), style = K.t.small, color = K.c.mu)
        }
    }
}

@Composable
private fun roleLabel(role: String) = stringResource(if (role == "owner") R.string.auth_role_owner else R.string.auth_role_employee)

@Composable
private fun PasswordPanel(user: DbRow, onBack: () -> Unit, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var password by remember(user.str("uuid")) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var failures by remember { mutableIntStateOf(0) }
    var lockedUntil by remember { mutableLongStateOf(0L) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    val owner = user.str("role") == "owner"
    val noPassword = user.long("password_set") == 0L

    LaunchedEffect(lockedUntil) {
        while (true) {
            val left = ((lockedUntil - System.currentTimeMillis()) / 1000).toInt()
            secondsLeft = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(1000)
        }
    }
    LaunchedEffect(Unit) { if (!noPassword) runCatching { focus.requestFocus() } }

    fun submit() {
        if (checking || secondsLeft > 0 || password.isEmpty()) return
        checking = true
        scope.launch {
            val ok = io { checkPassword(password, user.strOrNull("password_hash")) }
            checking = false
            if (ok) {
                onSignedIn()
            } else {
                wrong = true
                password = ""
                failures++
                if (failures >= MAX_FAILURES) {
                    failures = 0
                    lockedUntil = System.currentTimeMillis() + LOCK_SECONDS * 1000L
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = Edge, end = Edge, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            KIconButton(Icons.AutoMirrored.Outlined.ArrowBack, onBack, contentDescription = stringResource(R.string.auth_other_user))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Edge).widthIn(max = 480.dp).align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            KAvatar(user.str("full_name"), 72.dp, neutral = !owner)
            Spacer(Modifier.height(12.dp))
            Text(user.str("full_name"), style = K.t.hero, color = K.c.tx, textAlign = TextAlign.Center)
            Text(roleLabel(user.str("role")), style = K.t.small, color = K.c.mu)
            Spacer(Modifier.height(26.dp))

            if (noPassword) {
                KCard(tone = KCardTone.Tint) {
                    Text(
                        stringResource(if (owner) R.string.auth_no_password_owner else R.string.auth_no_password_employee),
                        style = K.t.body.copy(fontSize = 14.sp, lineHeight = 21.sp), color = K.c.tx,
                    )
                }
                if (owner) {
                    Spacer(Modifier.height(16.dp))
                    KButton(stringResource(R.string.auth_sign_in), onSignedIn, Modifier.fillMaxWidth(), height = 54.dp)
                }
            } else {
                KField(
                    value = password,
                    onChange = { password = it; wrong = false },
                    modifier = Modifier.focusRequester(focus),
                    placeholder = stringResource(R.string.auth_password),
                    password = !showPassword,
                    keyboard = KeyboardType.Password,
                    onDone = { submit() },
                    error = when {
                        secondsLeft > 0 -> stringResource(R.string.auth_too_many, secondsLeft)
                        wrong -> stringResource(R.string.auth_wrong_password)
                        else -> null
                    },
                    trailing = { EyeToggle(showPassword) { showPassword = !showPassword } },
                )
                Spacer(Modifier.height(16.dp))
                KButton(
                    stringResource(R.string.auth_sign_in), { submit() }, Modifier.fillMaxWidth(), height = 54.dp,
                    enabled = !checking && secondsLeft == 0 && password.isNotEmpty(),
                )
            }
            Spacer(Modifier.height(6.dp))
            InkTextButton(stringResource(R.string.auth_other_user), onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ======================================================================
// Blocked (mockup 33 style): the phone may not sell.
// ======================================================================

@Composable
fun BlockedScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sync by App.sync.state.collectAsState()
    val offlineDays by dbState(7) { App.kv.shop().optInt("offline_days", 7).coerceAtLeast(1) }
    var busy by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val reason = sync.blockedReason

    // Unblocked (a sync went through): back to work.
    LaunchedEffect(reason) {
        if (reason == null && io { App.isActivated }) nav.goHome()
    }
    if (reason == null) return

    fun open(uri: String, action: String = Intent.ACTION_VIEW) {
        runCatching { context.startActivity(Intent(action, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    BlockedContent(
        reason = reason,
        offlineDays = offlineDays,
        pendingSales = sync.pendingSales,
        checking = busy || sync.running,
        error = if (tried && !busy) sync.error?.takeIf { it != reason } else null,
        onRetry = {
            busy = true
            scope.launch {
                App.sync.syncNow()
                tried = true
                busy = false
            }
        },
        onPay = { open(SUPPORT_TELEGRAM) },
        onCall = { open("tel:$SUPPORT_PHONE", Intent.ACTION_DIAL) },
        onReconnect = { confirmDisconnect = true },
    )

    if (confirmDisconnect) {
        KConfirm(
            title = stringResource(R.string.settings_disconnect_confirm_title),
            text = if (sync.pendingSales > 0) stringResource(R.string.settings_disconnect_pending, sync.pendingSales)
            else stringResource(R.string.settings_disconnect_confirm_text),
            confirm = stringResource(R.string.settings_disconnect_yes),
            dismiss = stringResource(R.string.common_cancel),
            danger = true,
            onConfirm = {
                confirmDisconnect = false
                scope.launch {
                    App.sync.deactivate()
                    nav.resetTo(Routes.ACTIVATION)
                }
            },
            onDismiss = { confirmDisconnect = false },
        )
    }
}

/** Amber icon tile, big title, explanation, the main action and a second one. */
@Composable
internal fun BlockedContent(
    reason: String,
    offlineDays: Int,
    pendingSales: Long,
    checking: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onPay: () -> Unit,
    onCall: () -> Unit,
    onReconnect: () -> Unit,
) {
    val icon: ImageVector
    val title: String
    val text: String
    when (reason) {
        "device_revoked" -> {
            icon = Icons.Outlined.MobileOff
            title = stringResource(R.string.auth_blocked_device_revoked_title)
            text = stringResource(R.string.auth_blocked_device_revoked_text)
        }
        "shop_blocked" -> {
            icon = Icons.Outlined.Schedule
            title = stringResource(R.string.auth_blocked_shop_title)
            text = stringResource(R.string.auth_blocked_shop_text)
        }
        else -> {
            icon = Icons.Outlined.CloudOff
            title = stringResource(R.string.auth_blocked_offline_title)
            text = stringResource(R.string.auth_blocked_offline_text, offlineDays)
        }
    }
    val retryText = stringResource(if (checking) R.string.auth_blocked_checking else R.string.auth_blocked_retry)

    Column(
        Modifier.fillMaxSize().background(K.c.bg).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(90.dp))
        Box(Modifier.size(68.dp).clip(RoundedCornerShape(21.dp)).background(K.c.ambBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = K.c.amb, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = K.t.hero.copy(fontSize = 25.sp), color = K.c.tx, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = K.t.body.copy(fontSize = 14.sp, lineHeight = 21.7.sp), color = K.c.mu, textAlign = TextAlign.Center)
        if (pendingSales > 0) {
            Spacer(Modifier.height(16.dp))
            KCard(tone = KCardTone.Amber, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.CloudOff, null, tint = K.c.amb, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.auth_blocked_pending, pendingSales.toInt()), style = K.t.label.copy(fontSize = 13.sp), color = K.c.tx)
                }
            }
        }
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            ErrorLine(syncErrorText(error), center = true)
        }
        Spacer(Modifier.height(26.dp))
        when (reason) {
            "shop_blocked" -> {
                KButton(stringResource(R.string.auth_blocked_pay), onPay, Modifier.fillMaxWidth(), height = 54.dp)
                Spacer(Modifier.height(10.dp))
                KButton(stringResource(R.string.auth_blocked_phone), onCall, Modifier.fillMaxWidth(), kind = KButtonKind.Alt, icon = Icons.Outlined.Phone, height = 50.dp)
                Spacer(Modifier.height(6.dp))
                InkTextButton(retryText, onRetry, enabled = !checking)
            }
            "device_revoked" -> {
                KButton(stringResource(R.string.auth_blocked_reconnect), onReconnect, Modifier.fillMaxWidth(), height = 54.dp)
                Spacer(Modifier.height(10.dp))
                KButton(retryText, onRetry, Modifier.fillMaxWidth(), kind = KButtonKind.Alt, icon = Icons.Outlined.Refresh, height = 50.dp, enabled = !checking)
            }
            else -> {
                KButton(retryText, onRetry, Modifier.fillMaxWidth(), icon = Icons.Outlined.Refresh, height = 54.dp, enabled = !checking)
                Spacer(Modifier.height(10.dp))
                KButton(stringResource(R.string.auth_blocked_phone), onCall, Modifier.fillMaxWidth(), kind = KButtonKind.Alt, icon = Icons.Outlined.Phone, height = 50.dp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ======================================================================
// Small pieces shared by the sign-in screens (built from K.c / K.t).
// ======================================================================

/** A scrollable, keyboard-aware column with the sign-in side padding. */
@Composable
private fun AuthColumn(center: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(K.c.bg).imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = AuthEdge),
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            content = content,
        )
    }
}

/** The eye button inside a password field. */
@Composable
private fun EyeToggle(shown: Boolean, onToggle: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            stringResource(R.string.auth_show_password), tint = K.c.mu, modifier = Modifier.size(20.dp),
        )
    }
}

/** A plain red message with an icon, for errors that don't belong to one field. */
@Composable
private fun ErrorLine(text: String, center: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = K.c.red, modifier = Modifier.padding(top = 1.dp).size(16.dp))
        Text(text, style = K.t.label.copy(fontSize = 13.sp), color = K.c.red, textAlign = if (center) TextAlign.Center else TextAlign.Start, modifier = if (center) Modifier else Modifier.weight(1f))
    }
}

/** A text-only button in the readable lime ("ink") colour, 48dp tall. */
@Composable
private fun InkTextButton(text: String, onClick: () -> Unit, enabled: Boolean = true, minHeight: Dp = 48.dp) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = minHeight).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = K.t.label.copy(fontSize = 13.5.sp), color = if (enabled) K.c.ink else K.c.mu, textAlign = TextAlign.Center)
    }
}
