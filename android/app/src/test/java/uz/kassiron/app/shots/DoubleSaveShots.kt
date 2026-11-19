package uz.kassiron.app.shots

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uz.kassiron.app.App
import uz.kassiron.app.core.Ids
import uz.kassiron.app.domain.ExpenseService
import uz.kassiron.app.domain.ProductForm
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.PurchaseLine
import uz.kassiron.app.domain.PurchaseService
import uz.kassiron.app.ui.customers.CustomerActions

/**
 * A form saved twice (a second tap on "Saqlash" while the screen fades out)
 * must leave one row, not two: every new-record form passes the id it picked
 * when it opened, and the services treat a second save with it as the same row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class DoubleSaveShots {
    @Before fun setUp() {
        if (App.db.query("SELECT uuid FROM products LIMIT 1").isEmpty()) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
    }

    // The services ask for a sync 3 s later: let it run on this database, not the next test's.
    @After fun tearDown() = Thread.sleep(3_500)

    private fun count(sql: String, vararg args: Any?) = App.db.scalarLong(sql, *args)

    @Test fun productSavedTwiceIsOneProduct() {
        val draft = Ids.new()
        val form = ProductForm(name = "Pepsi 1L", sellPrice = "10000", costPrice = "7000", initialStock = "12")
        val first = ProductService.save(form, null, canPrices = true, draftUuid = draft)
        val second = ProductService.save(form, null, canPrices = true, draftUuid = draft)
        assertEquals(draft, first)
        assertEquals(first, second)
        assertEquals(1L, count("SELECT COUNT(*) FROM products WHERE name = ?", "Pepsi 1L"))
        // The opening stock is added once, not twice.
        assertEquals(12.0, App.db.queryOne("SELECT stock_qty FROM products WHERE uuid = ?", draft)!!.dbl("stock_qty"), 0.0001)
    }

    @Test fun productsFromDifferentFormsStayTwo() {
        val form = ProductForm(name = "Pepsi 0.5L", sellPrice = "6000")
        ProductService.save(form, null, canPrices = true, draftUuid = Ids.new())
        ProductService.save(form, null, canPrices = true, draftUuid = Ids.new())
        assertEquals(2L, count("SELECT COUNT(*) FROM products WHERE name = ?", "Pepsi 0.5L"))
    }

    @Test fun purchaseSavedTwiceAddsStockOnce() {
        val non = Shots.products["Non"]!!
        val before = App.db.queryOne("SELECT stock_qty FROM products WHERE uuid = ?", non)!!.dbl("stock_qty")
        val draft = Ids.new()
        val lines = listOf(PurchaseLine(non, null, 10.0, 2500.0))
        val first = PurchaseService.create(null, lines, null, false, draft)
        val second = PurchaseService.create(null, lines, null, false, draft)
        assertEquals(first, second)
        assertEquals(1L, count("SELECT COUNT(*) FROM purchases WHERE uuid = ?", draft))
        assertEquals(before + 10.0, App.db.queryOne("SELECT stock_qty FROM products WHERE uuid = ?", non)!!.dbl("stock_qty"), 0.0001)
    }

    @Test fun customerSavedTwiceIsOneCustomer() {
        val draft = Ids.new()
        val a = CustomerActions.save(null, "Olim aka", "+998935556677", "", "", null, draft)
        val b = CustomerActions.save(null, "Olim aka", "+998935556677", "", "", null, draft)
        assert(a is CustomerActions.SaveResult.Ok) { "first save: $a" }
        assert(b is CustomerActions.SaveResult.Ok) { "second save: $b" }
        assertEquals(1L, count("SELECT COUNT(*) FROM customers WHERE full_name = ?", "Olim aka"))
    }

    @Test fun supplierSavedTwiceIsOneSupplier() {
        val draft = Ids.new()
        ProductService.saveSupplier(null, "Coca-Cola Uz", "", "", "", draft)
        ProductService.saveSupplier(null, "Coca-Cola Uz", "", "", "", draft)
        assertEquals(1L, count("SELECT COUNT(*) FROM suppliers WHERE name = ?", "Coca-Cola Uz"))
    }

    @Test fun expenseSavedTwiceIsOneExpense() {
        val draft = Ids.new()
        val user = App.session.current().uuid
        ExpenseService.save(draft, 50000.0, "Kommunal", "Svet", "", user)
        ExpenseService.save(draft, 50000.0, "Kommunal", "Svet", "", user)
        assertEquals(1L, count("SELECT COUNT(*) FROM expenses WHERE description = ?", "Svet"))
    }
}
