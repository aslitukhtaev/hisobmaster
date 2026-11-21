package uz.kassiron.app.shots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.BarcodeGen
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.ui.products.ProductEdit
import uz.kassiron.app.ui.products.ProductsList
import uz.kassiron.app.ui.till.CartCodec
import uz.kassiron.app.ui.till.CartSlot
import uz.kassiron.app.ui.till.MAX_CARTS
import uz.kassiron.app.ui.till.STALE_CART_MILLIS
import uz.kassiron.app.ui.till.TillContent
import uz.kassiron.app.ui.till.TillData
import uz.kassiron.app.ui.till.TillProduct
import uz.kassiron.app.ui.till.TillViewModel

/** Several carts on the till (parked on this device) and the shop's own barcodes. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class CartsAndBarcodesShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Like TillShots: waits for the local reads before each capture, light and dark. */
    private fun shot(name: String, content: @Composable (NavHostController) -> Unit) {
        ThemeMode.set("light")
        val show = mutableStateOf(true)
        rule.setContent { if (show.value) KassirTheme { content(rememberNavController()) } }
        for (mode in listOf("light", "dark")) {
            ThemeMode.set(mode)
            settle()
            captureScreenRoboImage(File(Shots.outDir, "${name}_$mode.png").path, RoborazziOptions())
        }
        show.value = false
        settle()
        ThemeMode.set("system")
    }

    private fun settle() = repeat(4) { rule.waitForIdle(); Thread.sleep(250) }
    @Before fun setUp() {
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
        App.kv.set(Keys.TILL_CARTS, null)
    }

    // The services ask for a sync 3 s later: let it run on this database, not the next test's.
    @After fun tearDown() = Thread.sleep(3_500)

    private fun product(name: String): TillProduct = TillData.search("").first { it.name == name }

    // ------------------------------------------------------------------ carts

    @Test fun cartsSwitchCloseAndFinishOneOfTwo() {
        val vm = TillViewModel()
        assertEquals(1, vm.cartCount)
        vm.add(product("Non"), null, 3.0)
        vm.customer = null
        assertTrue(vm.newCart())
        assertEquals(2, vm.cartCount)
        assertEquals(1, vm.active)
        assertTrue("a new cart starts empty", vm.cart.isEmpty())
        vm.add(product("Sut 1L"), null)
        vm.discountText = "1000"

        vm.switchTo(0)
        assertEquals(1, vm.cart.size)
        assertEquals("Non", vm.cart[0].name)
        assertEquals("", vm.discountText)
        assertEquals(listOf(3, 1), vm.tabs.map { it.count })

        // The quick customer (cart 2) pays first: only that cart closes.
        vm.switchTo(1)
        assertEquals("1000", vm.discountText)
        vm.finishSale()
        assertEquals(1, vm.cartCount)
        assertEquals(0, vm.active)
        assertEquals("Non", vm.cart.single().name)
        assertEquals(3.0, vm.cart.single().qty, 0.0001)

        // Finishing the last cart leaves an empty "Savat 1".
        vm.finishSale()
        assertEquals(1, vm.cartCount)
        assertTrue(vm.cart.isEmpty())
        assertEquals(1, vm.tabs.single().number)

        // Closing a waiting cart keeps the open one.
        vm.add(product("Non"), null)
        vm.newCart(); vm.add(product("Shakar"), null)
        vm.newCart(); vm.add(product("Sut 1L"), null)
        vm.closeCart(0)
        assertEquals(2, vm.cartCount)
        assertEquals("Sut 1L", vm.cart.single().name)
        vm.renameCart(vm.active, "  Sherzod  ")
        assertEquals("Sherzod", vm.tabs[vm.active].name)
    }

    @Test fun atMostFiveCarts() {
        val vm = TillViewModel()
        repeat(MAX_CARTS - 1) { assertTrue(vm.newCart()) }
        assertEquals(MAX_CARTS, vm.cartCount)
        assertFalse(vm.canAddCart)
        assertFalse(vm.newCart())
        assertEquals(MAX_CARTS, vm.cartCount)
        // Numbers stay distinct and come back once a cart closes.
        assertEquals((1..MAX_CARTS).toList(), vm.tabs.map { it.number })
        vm.closeCart(1)
        assertTrue(vm.newCart())
        assertEquals(setOf(1, 2, 3, 4, 5), vm.tabs.map { it.number }.toSet())
    }

    @Test fun cartsSurviveARestart() {
        val first = TillViewModel()
        first.add(product("Non"), null, 2.0)
        first.newCart()
        first.add(product("Sut 1L"), null)
        first.setWholesaleMode(true)
        first.cashText = "50 000"
        first.flush()

        // A cold start: no saved state, only what was kept on the device.
        val again = TillViewModel(SavedStateHandle())
        assertEquals(2, again.cartCount)
        assertEquals(1, again.active)
        assertEquals("Sut 1L", again.cart.single().name)
        assertTrue(again.wholesale)
        assertEquals("50 000", again.cashText)
        again.switchTo(0)
        assertEquals(2.0, again.cart.single().qty, 0.0001)
        assertFalse(again.wholesale)
    }

    @Test fun aWaitingCartIsRepricedWhenOpenedAgain() {
        val vm = TillViewModel()
        val non = product("Non")
        vm.add(non, null, 2.0)
        vm.newCart()
        App.db.write { exec("UPDATE products SET sell_price = 4200 WHERE uuid = ?", non.uuid); exec("DELETE FROM sync_outbox") }
        vm.switchTo(0)
        // What the till does when a cart is opened (LaunchedEffect on the active cart).
        vm.applyFresh(vm.cart.map { it.key to TillData.fresh(it) })
        assertEquals(4200.0, vm.cart.single().price, 0.001)
        assertEquals(8400.0, vm.subtotal, 0.001)
        App.db.write { exec("UPDATE products SET sell_price = 3500 WHERE uuid = ?", non.uuid); exec("DELETE FROM sync_outbox") }
    }

    @Test fun cartsLeftForADayAreAskedAboutNotDeleted() {
        val non = product("Non")
        val old = System.currentTimeMillis() - STALE_CART_MILLIS - 60_000
        val probe = TillViewModel()
        probe.add(non, null)
        val line = probe.cart.single()
        val json = CartCodec.encode(
            listOf(CartSlot(1, 1, lines = listOf(line), touched = old), CartSlot(2, 2, lines = listOf(line), touched = System.currentTimeMillis())),
            1, 3,
        )
        App.kv.set(Keys.TILL_CARTS, json)
        val vm = TillViewModel(SavedStateHandle())
        assertEquals(1, vm.stalePrompt)
        assertEquals(2, vm.cartCount)
        vm.deleteStale()
        assertEquals(0, vm.stalePrompt)
        assertEquals(1, vm.cartCount)
        assertEquals(2, vm.tabs.single().number)
    }

    @Test fun till_carts() {
        val vm = TillViewModel()
        vm.add(product("Non"), null, 4.0)
        vm.add(product("Shakar"), null, 2.0)
        vm.newCart()
        vm.add(product("Sut 1L"), null)
        vm.renameCart(1, "Sherzod")
        vm.newCart()
        vm.add(product("Coca-Cola 1.5L"), null, 2.0)
        shot("till_carts") { nav -> TillContent(nav, vm) }
    }

    // --------------------------------------------------------------- barcodes

    @Test fun ean13CheckDigitsAndDevicePrefix() {
        assertTrue(BarcodeGen.isValidEan13("4006381333931"))
        assertTrue(BarcodeGen.isValidEan13("4780001234561".let { it.substring(0, 12) + BarcodeGen.checkDigit(it.substring(0, 12)) }))
        assertFalse(BarcodeGen.isValidEan13("4006381333932"))
        assertEquals(1, BarcodeGen.checkDigit("400638133393"))
        assertEquals("03", BarcodeGen.deviceDigits("K3"))
        assertEquals("12", BarcodeGen.deviceDigits("K12"))
        assertEquals("05", BarcodeGen.deviceDigits("K105"))
        assertEquals("00", BarcodeGen.deviceDigits(null))
        val code = BarcodeGen.build("03", 1)
        assertEquals("2030000000010".substring(0, 12), code.substring(0, 12))
        assertTrue(BarcodeGen.isValidEan13(code))
    }

    @Test fun nextCodesAreUniqueAndSkipTakenOnes() {
        val taken = setOf(BarcodeGen.build("03", 7), BarcodeGen.build("03", 9), BarcodeGen.build("05", 500), "4780001")
        // One past the highest of this device, never another device's sequence.
        assertEquals(BarcodeGen.build("03", 10), BarcodeGen.next("K3", taken))
        assertEquals(BarcodeGen.build("05", 501), BarcodeGen.next("K5", taken))
        assertEquals(BarcodeGen.build("07", 1), BarcodeGen.next("K7", taken))
        val many = BarcodeGen.nextMany("K3", taken, 50)
        assertEquals(50, many.toSet().size)
        assertTrue(many.none { it in taken })
        assertTrue(many.all { BarcodeGen.isValidEan13(it) && it.startsWith("203") })
        // A taken code in the middle is skipped.
        val gap = setOf(BarcodeGen.build("03", 1), BarcodeGen.build("03", 2))
        assertEquals(BarcodeGen.build("03", 3), BarcodeGen.next("K3", gap))
    }

    @Test fun givingCodesToEveryProductWithoutOne() {
        App.db.write {
            exec("UPDATE products SET barcode = NULL WHERE name IN ('Non', 'Shakar')")
            insert("products", mapOf("uuid" to Ids.new(), "name" to "Eski tovar", "unit" to "dona", "sell_price" to 1000.0, "cost_price" to 500.0,
                "stock_qty" to 0.0, "status" to "inactive", "created_at" to Time.now(), "updated_at" to Time.now()))
            val kepka = Ids.new()
            insert("products", mapOf("uuid" to kepka, "name" to "Kepka", "unit" to "dona", "sell_price" to 50000.0, "cost_price" to 30000.0,
                "stock_qty" to 0.0, "status" to "active", "created_at" to Time.now(), "updated_at" to Time.now()))
            insert("product_variants", mapOf("uuid" to Ids.new(), "product_id" to kepka, "variant_label" to "qora", "stock_qty" to 5.0, "status" to "active", "created_at" to Time.now()))
            insert("product_variants", mapOf("uuid" to Ids.new(), "product_id" to kepka, "variant_label" to "oq", "stock_qty" to 5.0, "barcode" to "2000000000017", "status" to "active", "created_at" to Time.now()))
            exec("DELETE FROM sync_outbox")
        }
        val before = App.db.query("SELECT name, barcode FROM products WHERE barcode IS NOT NULL").associate { it.str("name") to it.str("barcode") }
        // Non, Shakar and the black cap: not the inactive product, not the cap itself (it sells through variants).
        assertEquals(3, ProductService.withoutBarcodeCount())

        assertEquals(3, ProductService.generateMissingBarcodes())

        val codes = App.db.query("SELECT barcode FROM products WHERE barcode IS NOT NULL").map { it.str("barcode") } +
            App.db.query("SELECT barcode FROM product_variants WHERE barcode IS NOT NULL").map { it.str("barcode") }
        assertEquals("every code is used once", codes.size, codes.toSet().size)
        val non = App.db.queryOne("SELECT barcode FROM products WHERE name = 'Non'")!!.str("barcode")
        assertTrue(non.startsWith("201") && BarcodeGen.isValidEan13(non)) // device K1
        before.forEach { (name, code) -> assertEquals(code, App.db.queryOne("SELECT barcode FROM products WHERE name = ?", name)!!.str("barcode")) }
        assertNull(App.db.queryOne("SELECT barcode FROM products WHERE name = 'Eski tovar'")!!.strOrNull("barcode"))
        assertNull(App.db.queryOne("SELECT barcode FROM products WHERE name = 'Kepka'")!!.strOrNull("barcode"))
        assertEquals("2000000000017", App.db.queryOne("SELECT barcode FROM product_variants WHERE variant_label = 'oq'")!!.str("barcode"))
        assertTrue(BarcodeGen.isValidEan13(App.db.queryOne("SELECT barcode FROM product_variants WHERE variant_label = 'qora'")!!.str("barcode")))
        assertEquals(3L, App.db.scalarLong("SELECT COUNT(*) FROM sync_outbox WHERE tbl IN ('products', 'product_variants')"))
        assertEquals(1L, App.db.scalarLong("SELECT COUNT(*) FROM activity_log WHERE action = 'barcodes_generated'"))
        assertEquals(0, ProductService.withoutBarcodeCount())

        // The button on the form gives the next one, never one in use.
        val fresh = ProductService.newBarcode()
        assertFalse(fresh in codes)
        assertTrue(fresh.startsWith("201"))
    }

    @Test fun products_no_barcode() {
        App.db.write { exec("UPDATE products SET barcode = NULL WHERE name IN ('Non', 'Shakar', 'Un 1 kg')"); exec("DELETE FROM sync_outbox") }
        shot("products_no_barcode") { nav -> ProductsList(nav) }
    }

    @Test fun product_new_barcode_button() {
        shot("product_new_barcode") { nav -> ProductEdit(nav, "new", null) }
    }
}
