package uz.kassiron.app.print

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Keys
import java.io.IOException
import java.util.UUID

/** Why printing failed: [code] maps to a plain message with [PrinterSettings.message]. */
class PrintException(val code: String, cause: Throwable? = null) : Exception(code, cause)

data class PairedPrinter(val name: String, val address: String)

/** The chosen printer and its settings (kv). */
object PrinterSettings {
    /** "translit" (ASCII, any printer) or "cp866" (Cyrillic code page 17). */
    const val CHARSET_KEY = "printer_charset"

    var address: String?
        get() = App.kv.get(Keys.PRINTER_ADDRESS)?.takeIf { it.isNotBlank() }
        set(v) = App.kv.set(Keys.PRINTER_ADDRESS, v)

    /** 58 or 80 (mm): this device's choice, else the shop's receipt_printer_width, else 58. */
    var widthMm: Int
        get() = App.kv.get(Keys.PRINTER_WIDTH)?.toIntOrNull()
            ?: App.kv.shop().optInt("receipt_printer_width", 58).takeIf { it == 58 || it == 80 } ?: 58
        set(v) = App.kv.set(Keys.PRINTER_WIDTH, v.toString())

    var cp866: Boolean
        get() = App.kv.get(CHARSET_KEY) == "cp866"
        set(v) = App.kv.set(CHARSET_KEY, if (v) "cp866" else "translit")

    /** Characters per line. */
    val columns: Int get() = if (widthMm == 80) 48 else 32

    fun message(context: Context, e: Throwable): String = context.getString(
        when ((e as? PrintException)?.code) {
            "no_bluetooth" -> R.string.print_err_no_bluetooth
            "bt_off" -> R.string.print_err_bt_off
            "no_permission" -> R.string.print_err_permission
            "not_selected" -> R.string.print_err_not_selected
            "connect_failed" -> R.string.print_err_connect
            "write_failed" -> R.string.print_err_write
            "not_found" -> R.string.print_err_not_found
            else -> R.string.print_err_unknown
        }
    )
}

/** Classic Bluetooth (SPP) ESC/POS printing. Every call that touches the printer runs on Dispatchers.IO. */
object BluetoothPrinter {
    private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val lock = Mutex()

    /** The runtime permission needed to talk to paired devices (API 31+); null below. */
    val permission: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    fun hasPermission(context: Context): Boolean =
        permission == null || ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isAvailable(context: Context) = adapter(context) != null

    @SuppressLint("MissingPermission")
    fun isEnabled(context: Context): Boolean = runCatching { adapter(context)?.isEnabled == true }.getOrDefault(false)

    /** Paired devices, printers first (by device class / name). */
    @SuppressLint("MissingPermission")
    fun paired(context: Context): List<PairedPrinter> {
        val adapter = adapter(context) ?: throw PrintException("no_bluetooth")
        if (!hasPermission(context)) throw PrintException("no_permission")
        if (!adapter.isEnabled) throw PrintException("bt_off")
        val devices = try { adapter.bondedDevices.orEmpty() } catch (e: SecurityException) { throw PrintException("no_permission", e) }
        return devices.map { d ->
            val name = runCatching { d.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: d.address
            val isPrinter = runCatching { d.bluetoothClass?.majorDeviceClass == 0x0600 }.getOrDefault(false) ||
                Regex("(?i)print|pos|xp-|mtp|rpp|pt-|58|80").containsMatchIn(name)
            Triple(isPrinter, name, d.address)
        }.sortedWith(compareBy({ !it.first }, { it.second.lowercase() })).map { PairedPrinter(it.second, it.third) }
    }

    /** Sends raw bytes to the printer at [address]. */
    @SuppressLint("MissingPermission")
    suspend fun send(context: Context, address: String, data: ByteArray) = withContext(Dispatchers.IO) {
        lock.withLock {
            val adapter = adapter(context) ?: throw PrintException("no_bluetooth")
            if (!hasPermission(context)) throw PrintException("no_permission")
            if (!adapter.isEnabled) throw PrintException("bt_off")
            val device = try { adapter.getRemoteDevice(address) } catch (e: IllegalArgumentException) { throw PrintException("not_found", e) }
            runCatching { adapter.cancelDiscovery() }
            var socket: BluetoothSocket? = null
            try {
                socket = connect { device.createRfcommSocketToServiceRecord(SPP) }
                    // Some cheap printers only accept an insecure connection.
                    ?: connect { device.createInsecureRfcommSocketToServiceRecord(SPP) }
                    ?: throw PrintException("connect_failed")
                try {
                    val out = socket!!.outputStream
                    // In chunks: small printer buffers overflow on one big write.
                    var i = 0
                    while (i < data.size) {
                        val n = minOf(512, data.size - i)
                        out.write(data, i, n)
                        out.flush()
                        i += n
                        Thread.sleep(15)
                    }
                    // Give the printer time to take everything before the link drops.
                    Thread.sleep(400)
                } catch (e: IOException) {
                    throw PrintException("write_failed", e)
                }
            } catch (e: SecurityException) {
                throw PrintException("no_permission", e)
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    /** A connected socket, or null when this way of connecting failed. */
    private inline fun connect(create: () -> BluetoothSocket): BluetoothSocket? {
        var s: BluetoothSocket? = null
        return try {
            s = create()
            s.connect()
            s
        } catch (e: IOException) {
            runCatching { s?.close() }
            null
        }
    }

    /** Prints a sale's receipt on the chosen printer. */
    suspend fun printSale(context: Context, saleUuid: String) {
        val (address, bytes) = withContext(Dispatchers.IO) {
            val address = PrinterSettings.address ?: throw PrintException("not_selected")
            val data = Receipts.load(App.db, saleUuid) ?: throw PrintException("unknown")
            address to receiptBytes(context, data)
        }
        send(context, address, bytes)
    }

    fun receiptBytes(context: Context, data: ReceiptData): ByteArray {
        val cp866 = PrinterSettings.cp866
        val clean: (String) -> String = if (cp866) Text::normalizeQuotes else Text::toAscii
        val lines = Receipts.lines(context, data, PrinterSettings.columns, clean)
        return EscPos(cp866).receipt(lines, if (PrinterSettings.widthMm == 80) 576 else 384).build()
    }

    /** A short test page: shop name, width ruler, Cyrillic sample. */
    suspend fun testPrint(context: Context, address: String) {
        val bytes = withContext(Dispatchers.IO) {
            val cp866 = PrinterSettings.cp866
            val cols = PrinterSettings.columns
            val shop = App.kv.shop().optString("name", "")
            val clean: (String) -> String = if (cp866) Text::normalizeQuotes else Text::toAscii
            EscPos(cp866)
                .align(true).big(true).bold(true).line(clean(shop.ifBlank { "KassirON" }))
                .big(false).bold(false).line(clean(context.getString(R.string.print_test_title)))
                .line("${PrinterSettings.widthMm} mm / $cols")
                .align(false)
                .line("1234567890".repeat(cols / 10 + 1).take(cols))
                .line(Receipts.twoCols(clean(context.getString(R.string.till_receipt_total)), "15 000 so‘m", cols))
                .line(clean("O‘zbekcha: g‘alla, o‘rik"))
                .line(clean("Русский: Привет"))
                .feed(3).cut()
                .build()
        }
        send(context, address, bytes)
    }
}
