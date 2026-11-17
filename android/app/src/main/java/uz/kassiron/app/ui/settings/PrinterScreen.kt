package uz.kassiron.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import uz.kassiron.app.R
import uz.kassiron.app.print.BluetoothPrinter
import uz.kassiron.app.print.PairedPrinter
import uz.kassiron.app.print.PrintException
import uz.kassiron.app.print.PrinterSettings
import uz.kassiron.app.ui.common.io
import uz.kassiron.app.ui.kit.Edge
import uz.kassiron.app.ui.kit.KAlert
import uz.kassiron.app.ui.kit.KBottomBar
import uz.kassiron.app.ui.kit.KButton
import uz.kassiron.app.ui.kit.KCardTone
import uz.kassiron.app.ui.kit.KEmpty
import uz.kassiron.app.ui.kit.KList
import uz.kassiron.app.ui.kit.KOption
import uz.kassiron.app.ui.kit.KRow
import uz.kassiron.app.ui.kit.KScreen
import uz.kassiron.app.ui.kit.KSegmented
import uz.kassiron.app.ui.kit.KSub
import uz.kassiron.app.ui.kit.KTopBar
import uz.kassiron.app.ui.theme.K

/** Receipt printer (mockup 30): a paired Bluetooth ESC/POS printer, paper width, text encoding, test print. */
@Composable
fun PrinterScreen(nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var granted by remember { mutableStateOf(BluetoothPrinter.hasPermission(context)) }
    var printers by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    var problemCode by remember { mutableStateOf<String?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var width by remember { mutableIntStateOf(58) }
    var cp866 by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        refresh++
    }

    LaunchedEffect(Unit) {
        io { Triple(PrinterSettings.address, PrinterSettings.widthMm, PrinterSettings.cp866) }.let { (a, w, c) ->
            address = a; width = w; cp866 = c
        }
        if (!granted) BluetoothPrinter.permission?.let { launcher.launch(it) }
    }
    // Bluetooth may have been switched on / a printer paired in the system settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    LaunchedEffect(refresh, granted) {
        try {
            printers = BluetoothPrinter.paired(context)
            problem = null
            problemCode = null
        } catch (e: Exception) {
            printers = emptyList()
            problemCode = (e as? PrintException)?.code
            problem = PrinterSettings.message(context, e)
        }
    }

    fun openBluetoothSettings() {
        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }

    PrinterContent(
        printers = printers,
        address = address,
        width = width,
        cp866 = cp866,
        problem = problem,
        problemCode = problemCode,
        testing = testing,
        snackbar = snackbar,
        onBack = { nav.popBackStack() },
        onSelect = { p ->
            address = p.address
            scope.launch { io { PrinterSettings.address = p.address } }
        },
        onWidth = { w ->
            width = w
            scope.launch { io { PrinterSettings.widthMm = w } }
        },
        onCharset = { c ->
            cp866 = c
            scope.launch { io { PrinterSettings.cp866 = c } }
        },
        onFix = {
            when (problemCode) {
                "no_permission" -> BluetoothPrinter.permission?.let { launcher.launch(it) }
                    ?: runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)) }
                else -> openBluetoothSettings()
            }
        },
        onOpenBluetooth = { openBluetoothSettings() },
        onTest = {
            val a = address
            if (a != null && !testing) {
                testing = true
                scope.launch {
                    try {
                        BluetoothPrinter.testPrint(context, a)
                        snackbar.showSnackbar(context.getString(R.string.print_done))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(PrinterSettings.message(context, e))
                    } finally {
                        testing = false
                    }
                }
            }
        },
    )
}

@Composable
internal fun PrinterContent(
    printers: List<PairedPrinter>,
    address: String?,
    width: Int,
    cp866: Boolean,
    problem: String?,
    problemCode: String?,
    testing: Boolean,
    snackbar: SnackbarHostState?,
    onBack: () -> Unit,
    onSelect: (PairedPrinter) -> Unit,
    onWidth: (Int) -> Unit,
    onCharset: (Boolean) -> Unit,
    onFix: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onTest: () -> Unit,
) {
    KScreen(
        snackbar = snackbar,
        topBar = { KTopBar(stringResource(R.string.print_title), onBack = onBack) },
        bottomBar = {
            KBottomBar {
                KButton(
                    if (testing) stringResource(R.string.print_printing) else stringResource(R.string.print_test),
                    onTest, Modifier.fillMaxWidth(), icon = Icons.Outlined.Print,
                    enabled = address != null && !testing,
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.print_paired), style = K.t.section, color = K.c.tx,
                modifier = Modifier.padding(start = Edge + 2.dp, end = Edge, top = 2.dp, bottom = 8.dp),
            )
            when {
                problem != null -> KAlert(
                    problem, Icons.Outlined.BluetoothDisabled,
                    tone = if (problemCode == "no_bluetooth") KCardTone.Red else KCardTone.Amber,
                    onClick = if (problemCode == "no_permission" || problemCode == "bt_off") onFix else null,
                )
                printers.isEmpty() -> KEmpty(
                    Icons.Outlined.Bluetooth,
                    stringResource(R.string.print_none_paired),
                    stringResource(R.string.print_none_paired_hint),
                    action = stringResource(R.string.print_open_bt_settings),
                    onAction = onOpenBluetooth,
                )
                else -> KList {
                    printers.forEach { p ->
                        val selected = p.address == address
                        KRow(
                            title = p.name,
                            subtitle = { KSub(p.address) },
                            leading = {
                                Box(
                                    Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(if (selected) K.c.s1 else K.c.s2),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Outlined.Bluetooth, null, tint = K.c.mu, modifier = Modifier.size(18.dp)) }
                            },
                            trailing = if (selected) ({ Icon(Icons.Outlined.Check, null, tint = K.c.ink, modifier = Modifier.size(20.dp)) }) else null,
                            selected = selected,
                            onClick = { onSelect(p) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = Edge)) {
                Text(stringResource(R.string.print_width), style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
                KSegmented(
                    listOf(KOption(stringResource(R.string.print_width_58)), KOption(stringResource(R.string.print_width_80))),
                    selected = if (width == 80) 1 else 0,
                    onSelect = { onWidth(if (it == 1) 80 else 58) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.print_charset), style = K.t.label, color = K.c.mu, modifier = Modifier.padding(bottom = 6.dp))
                KSegmented(
                    listOf(KOption(stringResource(R.string.print_charset_latin)), KOption(stringResource(R.string.print_charset_cp866))),
                    selected = if (cp866) 1 else 0,
                    onSelect = { onCharset(it == 1) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.print_charset_hint), style = K.t.small, color = K.c.mu)
            }
        }
    }
}
