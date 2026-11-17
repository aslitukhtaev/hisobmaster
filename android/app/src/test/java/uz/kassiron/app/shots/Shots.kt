package uz.kassiron.app.shots

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Time
import uz.kassiron.app.domain.CartLine
import uz.kassiron.app.domain.Debts
import uz.kassiron.app.domain.SaleService
import uz.kassiron.app.ui.theme.KassirTheme
import uz.kassiron.app.ui.theme.ThemeMode
import java.io.File

/**
 * Screenshot tests of the real screens on the JVM (Robolectric + Roborazzi), so a
 * screen can be compared with android/design/mockup.html without an emulator.
 *
 *   ./gradlew recordRoborazziDebug --tests '*TillShots*'
 *   → app/build/outputs/roborazzi/<name>_light.png and <name>_dark.png
 *
 * Every test class: @RunWith(RobolectricTestRunner::class),
 * @Config(sdk = [34], qualifiers = Shots.PHONE), call Shots.seed() first.
 */
object Shots {
    /** A typical Android phone: 360×780dp at 3x. */
    const val PHONE = "w360dp-h780dp-xxhdpi"

    val outDir: File get() = File("build/outputs/roborazzi").apply { mkdirs() }

    /** Captures [content] (a whole screen) in the light and in the dark theme. */
    fun capture(name: String, content: @Composable (NavHostController) -> Unit) {
        for (mode in listOf("light", "dark")) {
            ThemeMode.set(mode)
            captureRoboImage(File(outDir, "${name}_$mode.png").path, RoborazziOptions()) {
                KassirTheme {
                    val nav = rememberNavController()
                    content(nav)
                }
            }
        }
        ThemeMode.set("system")
    }

    // --------------------------------------------------------------------------------------
    // A small, realistic shop: "Baraka market", owner Ali Valiyev, a cashier, products,
    // customers with debts, today's sales, expenses. Idempotent per test process.
    // --------------------------------------------------------------------------------------
    lateinit var owner: String
    lateinit var cashier: String
    val products = LinkedHashMap<String, String>() // name -> uuid
    val customers = LinkedHashMap<String, String>()
    var lastSale: String = ""
    private var seeded = false

    fun seed(signIn: Boolean = true) {
        if (seeded) { if (signIn) App.session.signIn(owner); return }
        seeded = true
        val db = App.db
        val kv = App.kv
        // bcrypt of "secret12" (PHP password_hash) — the login screen checks it offline.
        val hash = "\$2y\$10\$3tNwtVBHacmFBPh.rmlNM.KrJsVZNXIlL52u3RgMS2CES6vRxiasy"
        owner = Ids.new(); cashier = Ids.new()
        db.write {
            insert("users", mapOf("uuid" to owner, "role" to "owner", "full_name" to "Ali Valiyev", "login" to "ali", "password_hash" to hash, "lang" to "uz", "status" to "active", "created_at" to Time.now(), "password_set" to 1))
            insert("users", mapOf("uuid" to cashier, "role" to "employee", "full_name" to "Sardor Karimov", "login" to "sardor", "password_hash" to hash, "lang" to "uz", "permissions_json" to "[\"sales\",\"customers\"]", "status" to "active", "created_at" to Time.now(), "password_set" to 1))
            val cat = Ids.new()
            insert("categories", mapOf("uuid" to cat, "name" to "Oziq-ovqat", "type" to "product", "created_at" to Time.now()))
            listOf(
                Triple("Non", "dona", doubleArrayOf(2500.0, 3500.0, 50.0)),
                Triple("Sut 1L", "litr", doubleArrayOf(9000.0, 12000.0, 30.0)),
                Triple("Shakar", "kg", doubleArrayOf(11000.0, 14000.0, 100.0)),
                Triple("Coca-Cola 1.5L", "dona", doubleArrayOf(9000.0, 12500.0, 24.0)),
                Triple("Choy Ahmad 100 g", "dona", doubleArrayOf(8000.0, 11000.0, 3.0)),
                Triple("Un 1 kg", "dona", doubleArrayOf(7000.0, 9000.0, 40.0)),
                Triple("Tuxum (10 ta)", "dona", doubleArrayOf(14000.0, 17000.0, 0.0)),
            ).forEachIndexed { i, (name, unit, p) ->
                val u = Ids.new(); products[name] = u
                insert("products", mapOf("uuid" to u, "category_id" to cat, "name" to name, "unit" to unit, "cost_price" to p[0], "sell_price" to p[1], "stock_qty" to p[2], "barcode" to "478000${i + 1}", "status" to "active", "created_at" to Time.now(), "updated_at" to Time.now()))
            }
            listOf("Vali aka" to "+998909998877", "Nodira Saidova" to "+998912345678", "Karim aka" to "+998901234567").forEach { (n, ph) ->
                val u = Ids.new(); customers[n] = u
                insert("customers", mapOf("uuid" to u, "full_name" to n, "phone" to ph, "created_at" to Time.now()))
            }
            Debts.record(this, customers["Vali aka"]!!, null, Debts.QARZ, 100000.0, owner)
            Debts.record(this, customers["Nodira Saidova"]!!, null, Debts.QARZ, 50000.0, owner)
            val exCat = Ids.new()
            insert("categories", mapOf("uuid" to exCat, "name" to "Ijara", "type" to "expense", "created_at" to Time.now()))
            insert("expenses", mapOf("uuid" to Ids.new(), "category_id" to exCat, "amount" to 300000.0, "description" to "Do‘kon ijarasi", "expense_date" to Time.today(), "created_by" to owner, "created_at" to Time.now()))
            exec("DELETE FROM sync_outbox")
        }
        kv.set(Keys.DEVICE_CODE, "K1")
        kv.set(Keys.DEVICE_UUID, Ids.new())
        kv.set(Keys.DEVICE_SECRET, "x".repeat(64))
        kv.set(Keys.LAST_SYNC_AT, System.currentTimeMillis().toString())
        kv.set(Keys.SHOP, JSONObject(mapOf("name" to "Baraka market", "owner_full_name" to "Ali Valiyev", "phone" to "+998901112233", "address" to "Toshkent, Chilonzor", "currency" to "so‘m", "receipt_printer_width" to 58, "offline_days" to 7)).toString())
        kv.set(Keys.SHOP_SETTINGS, "{}")
        App.session.signIn(owner)
        // Two sales today.
        SaleService.create(listOf(CartLine(products["Non"]!!, null, 2.0), CartLine(products["Sut 1L"]!!, null, 1.0)), 20000.0, 0.0, 0.0, null)
        lastSale = SaleService.create(listOf(CartLine(products["Choy Ahmad 100 g"]!!, null, 1.0)), 11000.0, 0.0, 0.0, null).uuid
        App.db.write { exec("DELETE FROM sync_outbox") }
        if (!signIn) App.session.signOut()
    }

    @Suppress("unused")
    private fun touch() = Outbox
}
