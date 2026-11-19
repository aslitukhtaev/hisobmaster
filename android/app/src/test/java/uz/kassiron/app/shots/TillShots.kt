package uz.kassiron.app.shots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.core.Keys
import uz.kassiron.app.print.PairedPrinter
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.ui.common.ScannerOverlay
import uz.kassiron.app.ui.common.ScannerPermission
import uz.kassiron.app.ui.settings.PrinterContent
import uz.kassiron.app.ui.till.LastSale
import uz.kassiron.app.ui.till.PayMode
import uz.kassiron.app.ui.till.ReceiptContent
import uz.kassiron.app.ui.till.TillContent
import uz.kassiron.app.ui.till.TillData
import uz.kassiron.app.ui.till.TillSheet
import uz.kassiron.app.ui.till.TillViewModel
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import java.io.File

/**
 * The till, payment, customer picker, receipt, printer and scanner (mockups 6–12, 30–32).
 * Captures the whole screen (all windows), so bottom sheets show up too.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class TillShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var savedSettings: String? = null

    @Before fun setUp() {
        // Shots.seed() remembers "already seeded" for the whole JVM, but Robolectric gives
        // every test a fresh database: seed again when the shop is not there.
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
        // A shop-wide low-stock level, so "Choy" shows the amber "3 dona qoldi" tag.
        savedSettings = App.kv.get(Keys.SHOP_SETTINGS)
        App.kv.set(Keys.SHOP_SETTINGS, "{\"low_stock_threshold_default\":\"5\"}")
    }

    @After fun tearDown() {
        App.kv.set(Keys.SHOP_SETTINGS, savedSettings ?: "{}")
        ThemeMode.set("system")
    }

    private fun shot(name: String, animated: Boolean = false, content: @Composable (NavHostController) -> Unit) {
        ThemeMode.set("light")
        if (animated) rule.mainClock.autoAdvance = false
        val show = mutableStateOf(true)
        rule.setContent { if (show.value) KassirTheme { content(rememberNavController()) } }
        for (mode in listOf("light", "dark")) {
            ThemeMode.set(mode)
            settle(animated)
            captureScreenRoboImage(File(Shots.outDir, "${name}_$mode.png").path, RoborazziOptions())
        }
        // Leave nothing running (local reads) into the next test, whose database is new.
        show.value = false
        settle(animated)
    }

    /** Lets local reads (Dispatchers.IO) finish and sheets slide in. */
    private fun settle(animated: Boolean) {
        repeat(3) {
            if (animated) rule.mainClock.advanceTimeBy(500) else rule.waitForIdle()
            Thread.sleep(250)
        }
        if (animated) rule.mainClock.advanceTimeBy(16) else rule.waitForIdle()
    }

    private fun cartVm(): TillViewModel {
        val vm = TillViewModel()
        val all = TillData.search("")
        val non = all.first { it.name == "Non" }
        val sut = all.first { it.name == "Sut 1L" }
        vm.add(sut, null)
        vm.add(non, null)
        vm.add(non, null)
        return vm
    }

    @Test fun till_cart() {
        val vm = cartVm()
        shot("till_cart") { nav -> TillContent(nav, vm) }
    }

    @Test fun till_category() {
        val vm = TillViewModel()
        vm.category = App.db.queryOne("SELECT uuid FROM categories WHERE name = 'Ichimliklar'")!!.str("uuid")
        shot("till_category") { nav -> TillContent(nav, vm) }
    }

    @Test fun till_empty() = run {
        val vm = TillViewModel()
        shot("till_empty") { nav -> TillContent(nav, vm) }
    }

    @Test fun till_sold() = run {
        val vm = TillViewModel().apply { lastSale = LastSale(Shots.lastSale, "K1-000003", 19000.0, 1000.0, 0.0) }
        shot("till_sold") { nav -> TillContent(nav, vm) }
    }

    @Test fun till_offline() = run {
        val vm = TillViewModel()
        shot("till_offline") { nav -> TillContent(nav, vm, offlinePending = 3) }
    }

    @Test fun till_pay_cash() = run {
        val vm = cartVm().apply { cashText = "20 000" }
        shot("till_pay_cash") { nav -> TillContent(nav, vm, startSheet = TillSheet.PAY) }
    }

    @Test fun till_pay_debt() = run {
        val vm = cartVm().apply { mode = PayMode.QARZ }
        shot("till_pay_debt") { nav -> TillContent(nav, vm, startSheet = TillSheet.PAY) }
    }

    /** Russian: the longest labels (tiles, buttons) must still fit. */
    @Config(qualifiers = "+ru")
    @Test fun till_pay_cash_ru() = run {
        val vm = cartVm().apply { cashText = "20 000" }
        shot("till_pay_cash_ru") { nav -> TillContent(nav, vm, startSheet = TillSheet.PAY) }
    }

    @Config(qualifiers = "+ru")
    @Test fun till_sold_ru() = run {
        val vm = cartVm().apply { lastSale = LastSale(Shots.lastSale, "K1-000003", 19000.0, 1000.0, 0.0) }
        shot("till_sold_ru") { nav -> TillContent(nav, vm) }
    }

    @Test fun till_customer() = run {
        val vm = cartVm()
        shot("till_customer") { nav -> TillContent(nav, vm, startSheet = TillSheet.CUSTOMER, customerQuery = "Vali") }
    }

    @Test fun receipt() {
        val first = App.db.queryOne("SELECT uuid FROM sales ORDER BY created_at, rowid LIMIT 1")!!.str("uuid")
        val data = Receipts.load(App.db, first)
        shot("receipt") { nav -> ReceiptContent(nav, true, data) }
    }

    @Test fun printer() = shot("printer") { _ ->
        PrinterContent(
            printers = listOf(PairedPrinter("XP-58IIH", "66:22:A1:0B:4F:90"), PairedPrinter("MPT-II", "00:11:22:33:44:55")),
            address = "66:22:A1:0B:4F:90", width = 58, cp866 = false, problem = null, problemCode = null, testing = false,
            snackbar = null, onBack = {}, onSelect = {}, onWidth = {}, onCharset = {}, onFix = {}, onOpenBluetooth = {}, onTest = {},
        )
    }

    @Test fun printer_none() = shot("printer_none") { _ ->
        PrinterContent(
            printers = emptyList(), address = null, width = 80, cp866 = true, problem = null, problemCode = null, testing = false,
            snackbar = null, onBack = {}, onSelect = {}, onWidth = {}, onCharset = {}, onFix = {}, onOpenBluetooth = {}, onTest = {},
        )
    }

    @Test fun scanner() = shot("scanner", animated = true) { _ ->
        // The camera picture is dark; the overlay is drawn over it.
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D09))) {
            ScannerOverlay(hint = rule.activity.getString(uz.kassiron.app.R.string.scan_hint), torch = false, hasTorch = true, onClose = {}, onTorch = {})
        }
    }

    @Test fun scanner_permission() = shot("scanner_permission") { _ -> ScannerPermission(asked = true, onAllow = {}, onClose = {}) }
}
