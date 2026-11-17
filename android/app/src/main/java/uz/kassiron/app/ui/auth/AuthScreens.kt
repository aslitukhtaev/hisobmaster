package uz.kassiron.app.ui.auth

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.kassiron.app.App
import uz.kassiron.app.BuildConfig
import uz.kassiron.app.R
import uz.kassiron.app.core.Row as DbRow
import uz.kassiron.app.sync.SyncException
import uz.kassiron.app.sync.SyncManager
import uz.kassiron.app.ui.Routes
import uz.kassiron.app.ui.common.BigButton
import uz.kassiron.app.ui.common.ConfirmDialog
import uz.kassiron.app.ui.common.EmptyState
import uz.kassiron.app.ui.common.dbState
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.resetTo

// ======================================================================
// Activation: the owner's login + password, or a 6-digit code from the website.
// ======================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(SyncManager.defaultDeviceName()) }
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
        if (tab == 0 && (login.isBlank() || password.isEmpty() || deviceName.isBlank())) {
            error = "fill"; return
        }
        if (tab == 1 && code.length != 6) {
            error = "code_short"; return
        }
        run {
            if (tab == 0) App.sync.activate(login, password, deviceName)
            else App.sync.activateWithCode(code, deviceName)
        }
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Image(
                painter = painterResource(R.drawable.ic_launcher_maskable),
                contentDescription = null,
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Dns, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.auth_server, Uri.parse(BuildConfig.SERVER_URL).host ?: BuildConfig.SERVER_URL),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (busy) {
                DownloadProgress(downloaded)
            } else if (interrupted) {
                MessageCard(Icons.Filled.CloudOff, stringResource(R.string.auth_download_interrupted))
                error?.let { MessageCard(Icons.Filled.ErrorOutline, activationErrorText(it), isError = true) }
                BigButton(stringResource(R.string.auth_resume), onClick = { run { App.sync.resumeDownload() } }, icon = Icons.Filled.Refresh)
                TextButton(onClick = { nav.resetTo(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                    Text(stringResource(R.string.auth_skip))
                }
            } else {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(R.string.auth_tab_password, R.string.auth_tab_code).forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = tab == i,
                            onClick = { tab = i; error = null },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }

                Text(
                    stringResource(if (tab == 0) R.string.auth_owner_hint else R.string.auth_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val fieldShape = RoundedCornerShape(14.dp)
                if (tab == 0) {
                    OutlinedTextField(
                        value = login,
                        onValueChange = { login = it.trim() },
                        label = { Text(stringResource(R.string.auth_login)) },
                        singleLine = true,
                        shape = fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.auth_password)) },
                        singleLine = true,
                        shape = fieldShape,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = stringResource(R.string.auth_show_password),
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.auth_code)) },
                        singleLine = true,
                        shape = fieldShape,
                        textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 10.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.take(100) },
                    label = { Text(stringResource(R.string.auth_device_name)) },
                    singleLine = true,
                    shape = fieldShape,
                    modifier = Modifier.fillMaxWidth(),
                )

                error?.let { MessageCard(Icons.Filled.ErrorOutline, activationErrorText(it), isError = true) }

                BigButton(stringResource(R.string.auth_connect), onClick = { submit() })
            }
        }
    }
}

@Composable
private fun DownloadProgress(downloaded: Int?) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(Modifier.size(14.dp))
                Text(
                    stringResource(if (downloaded == null) R.string.auth_connecting else R.string.auth_downloading),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (downloaded != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.auth_downloaded_count, downloaded), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.auth_download_wait),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageCard(icon: ImageVector, text: String, isError: Boolean = false) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp))
            Spacer(Modifier.size(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ======================================================================
// Login: who is working — pick a user, enter the password (checked offline).
// ======================================================================

private const val MAX_FAILURES = 5
private const val LOCK_SECONDS = 30

@Composable
fun LoginScreen(nav: NavHostController) {
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
    var selectedUuid by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = users?.firstOrNull { it.str("uuid") == selectedUuid }

    BackHandler(enabled = selected != null) { selectedUuid = null }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        when {
            users == null -> Unit // reading (a few milliseconds)
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
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (shopName.isNotBlank()) {
                            Text(shopName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                        }
                        Text(stringResource(R.string.auth_who), style = MaterialTheme.typography.headlineMedium)
                        Text(
                            stringResource(R.string.auth_pick_user),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (users!!.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(Icons.Filled.PersonOff, stringResource(R.string.auth_no_users), Modifier.height(260.dp))
                            sync.error?.let { Text(syncErrorText(it), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
                            Spacer(Modifier.height(12.dp))
                            BigButton(
                                stringResource(R.string.auth_sync),
                                onClick = { scope.launch { App.sync.syncNow() } },
                                enabled = !sync.running,
                                icon = Icons.Filled.Sync,
                            )
                        }
                    }
                }
                items(users!!, key = { it.str("uuid") }) { user ->
                    UserTile(user) { selectedUuid = user.str("uuid") }
                }
            }
        }
    }
}

@Composable
private fun UserTile(user: DbRow, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InitialsAvatar(user.str("full_name"), 64.dp)
            Text(
                user.str("full_name"),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (user.str("role") == "owner") R.string.auth_role_owner else R.string.auth_role_employee),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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

    val isOwner = user.str("role") == "owner"
    val noPassword = user.long("password_set") == 0L

    LaunchedEffect(lockedUntil) {
        while (true) {
            val left = ((lockedUntil - System.currentTimeMillis()) / 1000).toInt()
            secondsLeft = left.coerceAtLeast(0)
            if (left <= 0) break
            delay(1000)
        }
    }

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

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(24.dp))
            InitialsAvatar(user.str("full_name"), 96.dp)
            Text(user.str("full_name"), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)

            if (noPassword) {
                MessageCard(
                    Icons.Filled.Lock,
                    stringResource(if (isOwner) R.string.auth_no_password_owner else R.string.auth_no_password_employee),
                )
                if (isOwner) BigButton(stringResource(R.string.auth_sign_in), onClick = onSignedIn)
            } else {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; wrong = false },
                    label = { Text(stringResource(R.string.auth_password)) },
                    singleLine = true,
                    isError = wrong,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = stringResource(R.string.auth_show_password),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    secondsLeft > 0 -> Text(stringResource(R.string.auth_too_many, secondsLeft), color = MaterialTheme.colorScheme.error)
                    wrong -> Text(stringResource(R.string.auth_wrong_password), color = MaterialTheme.colorScheme.error)
                }
                BigButton(
                    stringResource(R.string.auth_sign_in),
                    onClick = { submit() },
                    enabled = !checking && secondsLeft == 0 && password.isNotEmpty(),
                )
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)) {
                Text(stringResource(R.string.auth_other_user))
            }
        }
    }
}

// ======================================================================
// Blocked: the phone may not sell (revoked, shop blocked, offline too long).
// ======================================================================

@Composable
fun BlockedScreen(nav: NavHostController) {
    val scope = rememberCoroutineScope()
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

    val (icon, title, text) = when (reason) {
        "device_revoked" -> Triple(Icons.Filled.Block, stringResource(R.string.auth_blocked_device_revoked_title), stringResource(R.string.auth_blocked_device_revoked_text))
        "shop_blocked" -> Triple(Icons.Filled.Lock, stringResource(R.string.auth_blocked_shop_blocked_title), stringResource(R.string.auth_blocked_shop_blocked_text))
        else -> Triple(Icons.Filled.WifiOff, stringResource(R.string.auth_blocked_offline_title), stringResource(R.string.auth_blocked_offline_text, offlineDays))
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(32.dp))
            Box(
                Modifier.size(96.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.error)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (sync.pendingSales > 0) {
                MessageCard(Icons.Filled.CloudOff, stringResource(R.string.auth_blocked_pending, sync.pendingSales))
            }
            if (tried && !busy) {
                sync.error?.takeIf { it != reason }?.let { MessageCard(Icons.Filled.ErrorOutline, syncErrorText(it), isError = true) }
            }

            Spacer(Modifier.height(8.dp))
            BigButton(
                stringResource(R.string.auth_blocked_retry),
                onClick = {
                    busy = true
                    scope.launch {
                        App.sync.syncNow()
                        tried = true
                        busy = false
                    }
                },
                enabled = !busy && !sync.running,
                icon = Icons.Filled.Refresh,
            )
            if (busy || sync.running) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (reason == "device_revoked") {
                OutlinedButton(
                    onClick = { confirmDisconnect = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                ) { Text(stringResource(R.string.auth_blocked_disconnect)) }
            }
        }
    }

    if (confirmDisconnect) {
        ConfirmDialog(
            title = stringResource(R.string.settings_disconnect_confirm_title),
            text = if (sync.pendingSales > 0) stringResource(R.string.settings_disconnect_pending, sync.pendingSales)
            else stringResource(R.string.settings_disconnect_confirm_text),
            confirm = stringResource(R.string.settings_disconnect_yes),
            dismiss = stringResource(R.string.common_cancel),
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
