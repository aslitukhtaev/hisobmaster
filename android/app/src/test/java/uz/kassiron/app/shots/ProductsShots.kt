package uz.kassiron.app.shots

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.domain.ProductForm
import uz.kassiron.app.domain.ProductService
import uz.kassiron.app.domain.PurchaseLine
import uz.kassiron.app.domain.PurchaseService
import uz.kassiron.app.ui.products.CategoriesScreen
import uz.kassiron.app.ui.products.FieldErrors
import uz.kassiron.app.ui.products.ProductEdit
import uz.kassiron.app.ui.products.ProductsList
import uz.kassiron.app.ui.products.PurchaseDetailScreen
import uz.kassiron.app.ui.products.PurchaseNew
import uz.kassiron.app.ui.products.PurchasesScreen
import uz.kassiron.app.ui.products.QuickEntry
import uz.kassiron.app.ui.products.StockFilter
import uz.kassiron.app.ui.products.SuppliersScreen

/** Every screen of the products area (mockup 15–18), light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = Shots.PHONE)
class ProductsShots {
    private var supplier: String? = null
    private var purchase: String? = null

    @Before fun setUp() {
        // Robolectric gives every test a fresh app (and database), but Shots remembers it
        // seeded the first one: seed again when this test's database is empty.
        if (runCatching { App.db.queryOne("SELECT uuid FROM users LIMIT 1") }.getOrNull() == null) {
            Shots::class.java.getDeclaredField("seeded").apply { isAccessible = true }.setBoolean(Shots, false)
        }
        Shots.seed()
        // The shop's "kam qoldiq" threshold, so Choy (3 left → 2 after a sale) shows as low like the mockup.
        App.kv.set(uz.kassiron.app.core.Keys.SHOP_SETTINGS, """{"low_stock_threshold_default":"5"}""")
        val db = App.db
        supplier = db.queryOne("SELECT uuid FROM suppliers WHERE name = 'Bozor ulgurji'")?.str("uuid")
            ?: ProductService.saveSupplier(null, "Bozor ulgurji", "+998901234567", "Toshkent, Chorsu", "")
        if (db.queryOne("SELECT uuid FROM suppliers WHERE name = 'Coca-Cola diler'") == null) {
            ProductService.saveSupplier(null, "Coca-Cola diler", "+998935556677", "", "Har dushanba keladi")
        }
        if (db.queryOne("SELECT uuid FROM categories WHERE name = 'Ichimliklar'") == null) ProductService.findOrCreateCategory("Ichimliklar")
        purchase = db.queryOne("SELECT uuid FROM purchases ORDER BY created_at DESC")?.str("uuid")
            ?: PurchaseService.create(
                supplier,
                listOf(
                    PurchaseLine(Shots.products["Non"]!!, null, 100.0, 2500.0),
                    PurchaseLine(Shots.products["Un 1 kg"]!!, null, 40.0, 7000.0),
                ),
                "Faktura № 125", false,
            )
    }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private fun category() = App.db.queryOne("SELECT uuid FROM categories WHERE name = 'Oziq-ovqat'")?.str("uuid")

    @Test fun list() = Shots.capture("products_list") { nav -> ProductsList(nav) }
    @Test fun listLow() = Shots.capture("products_list_low") { nav -> ProductsList(nav, StockFilter.LOW) }
    @Test fun listOut() = Shots.capture("products_list_out") { nav -> ProductsList(nav, StockFilter.OUT) }
    @Test fun listInactive() = Shots.capture("products_list_inactive") { nav -> ProductsList(nav, StockFilter.INACTIVE) }

    @Test fun editNew() = Shots.capture("products_edit_new") { nav ->
        ProductEdit(
            nav, "new", null,
            preset = ProductForm(name = "Un 2 kg", barcode = "4780010", unit = "dona", sellPrice = "17000", costPrice = "14000", initialStock = "40", categoryId = category()),
        )
    }
    @Test fun editNewEmpty() = Shots.capture("products_edit_new_empty") { nav -> ProductEdit(nav, "new", null, preset = ProductForm()) }
    @Test fun editError() = Shots.capture("products_edit_error") { nav ->
        ProductEdit(
            nav, "new", null,
            preset = ProductForm(barcode = "4780001", unit = ""),
            presetErrors = FieldErrors(name = str(R.string.products_err_name_required), price = str(R.string.products_err_price_required), unit = str(R.string.products_err_unit_required)),
        )
    }
    @Test fun editExisting() = Shots.capture("products_edit_existing") { nav -> ProductEdit(nav, Shots.products["Choy Ahmad 100 g"]!!, null) }
    @Test fun editExpanded() = Shots.capture("products_edit_expanded") { nav -> ProductEdit(nav, Shots.products["Shakar"]!!, null, expanded = true) }

    @Test fun quick() = Shots.capture("products_quick") { nav ->
        QuickEntry(nav, prefill = listOf(Triple("Un 1 kg", "9000", "40"), Triple("Yog‘ 1L", "22000", "15"), Triple("Tuz", "", "30")), showErrors = true)
    }
    @Test fun quickEmpty() = Shots.capture("products_quick_empty") { nav -> QuickEntry(nav) }

    @Test fun purchaseNew() = Shots.capture("products_purchase_new") { nav ->
        PurchaseNew(nav, prefill = listOf("Non" to 100.0, "Un 1 kg" to 40.0), prefillSupplier = supplier)
    }
    @Test fun purchaseNewEmpty() = Shots.capture("products_purchase_new_empty") { nav -> PurchaseNew(nav) }
    @Test fun purchases() = Shots.capture("products_purchases") { nav -> PurchasesScreen(nav) }
    @Test fun purchaseDetail() = Shots.capture("products_purchase_detail") { nav -> PurchaseDetailScreen(nav, purchase!!) }
    @Test fun categories() = Shots.capture("products_categories") { nav -> CategoriesScreen(nav) }
    @Test fun suppliers() = Shots.capture("products_suppliers") { nav -> SuppliersScreen(nav) }
}
