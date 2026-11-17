package uz.kassiron.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import uz.kassiron.app.ui.till.TillTopBar

/** Receipt printer: a paired Bluetooth ESC/POS printer, paper width, text encoding, test print. */
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

    Scaffold(
        topBar = {
            TillTopBar(stringResource(R.string.print_title), onBack = { nav.popBackStack() }) {
                IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, null) }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.print_choose), style = MaterialTheme.typography.titleMedium)

            if (problem != null) {
                Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(problem!!, color = MaterialTheme.colorScheme.error)
                        when (problemCode) {
                            "no_permission" -> Button(onClick = {
                                BluetoothPrinter.permission?.let { launcher.launch(it) }
                                    ?: context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
                            }) { Text(stringResource(R.string.print_allow)) }
                            "bt_off" -> Button(onClick = {
                                runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                            }) { Text(stringResource(R.string.print_open_bt_settings)) }
                        }
                    }
                }
            } else if (printers.isEmpty()) {
                Text(stringResource(R.string.print_none_paired), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(colors = uz.kassiron.app.ui.theme.Btn.outlined(), onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }) {
                    Text(stringResource(R.string.print_open_bt_settings))
                }
            }

            printers.forEach { p ->
                val selected = p.address == address
                Surface(
                    onClick = {
                        address = p.address
                        scope.launch { io { PrinterSettings.address = p.address } }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Bluetooth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(p.address, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        RadioButton(selected = selected, onClick = null)
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.print_width), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(58, 80).forEach { w ->
                    Choice(
                        text = stringResource(if (w == 58) R.string.print_width_58 else R.string.print_width_80),
                        selected = width == w,
                        modifier = Modifier.weight(1f),
                    ) {
                        width = w
                        scope.launch { io { PrinterSettings.widthMm = w } }
                    }
                }
            }

            Text(stringResource(R.string.print_charset), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(stringResource(R.string.print_charset_latin), !cp866, Modifier.weight(1f)) {
                    cp866 = false
                    scope.launch { io { PrinterSettings.cp866 = false } }
                }
                Choice(stringResource(R.string.print_charset_cp866), cp866, Modifier.weight(1f)) {
                    cp866 = true
                    scope.launch { io { PrinterSettings.cp866 = true } }
                }
            }
            Text(stringResource(R.string.print_charset_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val a = address ?: return@Button
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
                },
                enabled = address != null && !testing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                } else {
                    Icon(Icons.Filled.Print, null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.print_test))
                }
            }
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.size(6.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
