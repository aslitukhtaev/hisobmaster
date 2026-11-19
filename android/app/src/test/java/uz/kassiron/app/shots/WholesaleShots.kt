package uz.kassiron.app.shots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.CartLine
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.QuickRow
import uz.kassiron.app.domain.SaleException
import uz.kassiron.app.domain.SaleService
import uz.kassiron.app.domain.Wholesale
import uz.kassiron.app.print.Receipts
import uz.kassiron.app.ui.products.ProductEdit
import uz.kassiron.app.ui.products.QuickEntry
import uz.kassiron.app.ui.till.ReceiptContent
import uz.kassiron.app.ui.till.TillContent
import uz.kassiron.app.ui.till.TillData
import uz.kassiron.app.ui.till.TillSheet
import uz.kassiron.app.ui.till.TillViewModel
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import java.io.File

/**
 * Optom (wholesale) selling: the price rule, sales.price_type, and the screens
 * (till toggle, payment sheet, product form, receipt). Off unless the shop's
 * settings say wholesale_enabled = "1".
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class WholesaleShots {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var savedSettings: String? = null

    @Before fun setUp() {
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
        savedSettings = App.kv.get(Keys.SHOP_SETTINGS)
        enable(true)
        // Non and Sut have an optom price; Choy doesn't (it stays at retail in an optom sale).
        App.db.write {
            update("products", Shots.products["Non"]!!, mapOf("wholesale_price" to 3000.0))
            update("products", Shots.products["Sut 1L"]!!, mapOf("wholesale_price" to 10500.0))
            exec("DELETE FROM sync_outbox")
        }
    }

    @After fun tearDown() {
        App.kv.set(Keys.SHOP_SETTINGS, savedSettings ?: "{}")
        ThemeMode.set("system")
    }

    private fun enable(on: Boolean) =
        App.kv.set(Keys.SHOP_SETTINGS, if (on) "{\"wholesale_enabled\":\"1\",\"low_stock_threshold_default\":\"5\"}" else "{\"low_stock_threshold_default\":\"5\"}")

    private fun sell(lines: List<CartLine>, priceType: String) = App.db.write {
        SaleService.createIn(this, Shots.owner, lines, 1_000_000.0, 0.0, 0.0, null, priceType)
    }

    private fun unitPrice(saleUuid: String, productUuid: String) =
        App.db.queryOne("SELECT unit_price FROM sale_items WHERE sale_id = ? AND product_id = ?", saleUuid, productUuid)!!.dbl("unit_price")

    // ------------------------------------------------------------------ the rule

    @Test fun wholesaleSaleUsesOptomPricesAndRecordsIt() {
        val non = Shots.products["Non"]!!
        val choy = Shots.products["Choy Ahmad 100 g"]!!
        val sale = sell(listOf(CartLine(non, null, 2.0), CartLine(choy, null, 1.0)), Wholesale.WHOLESALE)
        assertEquals(3000.0, unitPrice(sale.uuid, non), 0.001)
        assertEquals(11000.0, unitPrice(sale.uuid, choy), 0.001) // no optom price: retail
        assertEquals(17000.0, sale.total, 0.001)
        assertEquals("wholesale", App.db.queryOne("SELECT price_type FROM sales WHERE uuid = ?", sale.uuid)!!.str("price_type"))
    }

    @Test fun retailSaleIsUnchanged() {
        val non = Shots.products["Non"]!!
        val sale = sell(listOf(CartLine(non, null, 2.0)), Wholesale.RETAIL)
        assertEquals(3500.0, unitPrice(sale.uuid, non), 0.001)
        assertEquals("retail", App.db.queryOne("SELECT price_type FROM sales WHERE uuid = ?", sale.uuid)!!.str("price_type"))
    }

    @Test fun variantOptomFallsBackToProduct() {
        // A cap with two sizes: 56 has its own optom price, 58 uses the product's.
        val cap = Ids.new()
        val v56 = Ids.new()
        val v58 = Ids.new()
        App.db.write {
            insert("products", mapOf("uuid" to cap, "name" to "Kepka", "unit" to "dona", "cost_price" to 20000.0, "sell_price" to 50000.0,
                "wholesale_price" to 40000.0, "stock_qty" to 0.0, "status" to "active", "created_at" to Time.now(), "updated_at" to Time.now()))
            insert("product_variants", mapOf("uuid" to v56, "product_id" to cap, "variant_label" to "56", "stock_qty" to 10.0,
                "wholesale_price" to 38000.0, "status" to "active", "created_at" to Time.now()))
            insert("product_variants", mapOf("uuid" to v58, "product_id" to cap, "variant_label" to "58", "stock_qty" to 10.0,
                "sell_price" to 52000.0, "status" to "active", "created_at" to Time.now()))
        }
        val optom = sell(listOf(CartLine(cap, v56, 1.0), CartLine(cap, v58, 1.0)), Wholesale.WHOLESALE)
        val prices = App.db.query("SELECT variant_id, unit_price FROM sale_items WHERE sale_id = ?", optom.uuid).associate { it.str("variant_id") to it.dbl("unit_price") }
        assertEquals(38000.0, prices[v56]!!, 0.001)
        assertEquals(40000.0, prices[v58]!!, 0.001)
        val retail = sell(listOf(CartLine(cap, v58, 1.0)), Wholesale.RETAIL)
        assertEquals(52000.0, App.db.queryOne("SELECT unit_price FROM sale_items WHERE sale_id = ?", retail.uuid)!!.dbl("unit_price"), 0.001)
    }

    @Test fun cartRepricesAndResets() {
        val vm = TillViewModel()
        val all = TillData.search("")
        vm.add(all.first { it.name == "Non" }, null)
        vm.add(all.first { it.name == "Choy Ahmad 100 g" }, null)
        assertEquals(14500.0, vm.subtotal, 0.001)
        vm.setWholesaleMode(true)
        assertEquals(14000.0, vm.subtotal, 0.001)
        vm.add(all.first { it.name == "Non" }, null) // added in optom mode: optom price
        assertEquals(17000.0, vm.subtotal, 0.001)
        vm.clear()
        assertEquals(false, vm.wholesale)
    }

    // ------------------------------------------------------------------ screens

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
    }

    private fun settle() {
        repeat(3) { rule.waitForIdle(); Thread.sleep(250) }
        rule.waitForIdle()
    }

    private fun optomCart(): TillViewModel {
        val vm = TillViewModel()
        val all = TillData.search("")
        vm.add(all.first { it.name == "Sut 1L" }, null)
        vm.add(all.first { it.name == "Choy Ahmad 100 g" }, null)
        vm.add(all.first { it.name == "Non" }, null)
        vm.add(all.first { it.name == "Non" }, null)
        vm.setWholesaleMode(true)
        return vm
    }

    @Test fun till_optom() { val vm = optomCart(); shot("wholesale_till") { nav -> TillContent(nav, vm) } }

    @Test fun till_optom_pay() { val vm = optomCart(); shot("wholesale_pay") { nav -> TillContent(nav, vm, startSheet = TillSheet.PAY) } }

    @Test fun till_retail_toggle() = run {
        val vm = TillViewModel()
        vm.add(TillData.search("").first { it.name == "Non" }, null)
        shot("wholesale_till_retail") { nav -> TillContent(nav, vm) }
    }

    @Test fun tillWithoutSettingShowsNoToggle() {
        enable(false)
        val vm = TillViewModel()
        vm.add(TillData.search("").first { it.name == "Non" }, null)
        rule.setContent { KassirTheme { TillContent(rememberNavController(), vm) } }
        settle()
        assertEquals(0, rule.onAllNodesWithText("Optom").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Chakana").fetchSemanticsNodes().size)
    }

    @Test fun product_edit() = shot("wholesale_product_edit") { nav -> ProductEdit(nav, Shots.products["Non"]!!, null) }

    @Test fun receipt() {
        val sale = sell(listOf(CartLine(Shots.products["Non"]!!, null, 2.0), CartLine(Shots.products["Sut 1L"]!!, null, 1.0)), Wholesale.WHOLESALE)
        App.db.write { exec("DELETE FROM sync_outbox") }
        val data = Receipts.load(App.db, sale.uuid)
        shot("wholesale_receipt") { nav -> ReceiptContent(nav, true, data) }
    }

    // ------------------------------------------------------------------ quick entry

    @Test fun quickCreateTakesCategoryAndOptomPrice() {
        val drinks = App.db.queryOne("SELECT uuid FROM categories WHERE name = 'Ichimliklar'")!!.str("uuid")
        val res = ProductService.quickCreate(
            listOf(
                0 to QuickRow(name = "Fanta 1L", sellPrice = "11000", wholesalePrice = "9500", stock = "12"),
                1 to QuickRow(name = "Sprite 1L", sellPrice = "11000", stock = "6"),
            ),
            canPrices = true, categoryId = drinks,
        )
        assertEquals(listOf(0, 1), res.created)
        val fanta = App.db.queryOne("SELECT * FROM products WHERE name = 'Fanta 1L'")!!
        assertEquals(drinks, fanta.str("category_id"))
        assertEquals(9500.0, fanta.dbl("wholesale_price"), 0.001)
        val sprite = App.db.queryOne("SELECT * FROM products WHERE name = 'Sprite 1L'")!!
        assertEquals(drinks, sprite.str("category_id"))
        assertEquals(null, sprite.dblOrNull("wholesale_price"))
    }

    @Test fun quick_entry() {
        val drinks = App.db.queryOne("SELECT uuid FROM categories WHERE name = 'Ichimliklar'")!!.str("uuid")
        shot("wholesale_quick") { nav ->
            QuickEntry(
                nav,
                prefill = listOf(Triple("Kepka qora", "50000", "20"), Triple("Kepka oq", "50000", "15"), Triple("Panama", "65000", "8")),
                prefillOptom = listOf("40000", "40000", ""),
                initialCategory = drinks,
            )
        }
    }

    // ------------------------------------------------------------------ permission

    @Test fun wholesaleNeedsPermission() {
        val non = Shots.products["Non"]!!
        // Sardor (cashier) may sell, but not optom: refused, never sold at retail instead.
        val refused = runCatching {
            App.db.write { SaleService.createIn(this, Shots.cashier, listOf(CartLine(non, null, 1.0)), 100_000.0, 0.0, 0.0, null, Wholesale.WHOLESALE) }
        }.exceptionOrNull()
        assertEquals("wholesale_not_allowed", (refused as? SaleException)?.code)
        assertEquals(0L, App.db.scalarLong("SELECT COUNT(*) FROM sales WHERE cashier_id = ?", Shots.cashier))
        // Given the permission, the same sale goes through at the optom price.
        App.db.write { update("users", Shots.cashier, mapOf("permissions_json" to "[\"sales\",\"customers\",\"wholesale\"]")) }
        val ok = App.db.write { SaleService.createIn(this, Shots.cashier, listOf(CartLine(non, null, 1.0)), 100_000.0, 0.0, 0.0, null, Wholesale.WHOLESALE) }
        assertEquals(3000.0, ok.total, 0.001)
    }

    @Test fun cashierWithoutPermissionSeesNoToggle() {
        App.session.signIn(Shots.cashier)
        try {
            val vm = TillViewModel()
            vm.add(TillData.search("").first { it.name == "Non" }, null)
            rule.setContent { KassirTheme { TillContent(rememberNavController(), vm) } }
            settle()
            assertEquals(0, rule.onAllNodesWithText("Optom").fetchSemanticsNodes().size)
        } finally {
            App.session.signIn(Shots.owner)
        }
    }
}
