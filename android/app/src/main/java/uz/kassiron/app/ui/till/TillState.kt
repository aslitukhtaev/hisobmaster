package uz.kassiron.app.ui.till

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import uz.kassiron.app.App
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.CartLine
import uz.kassiron.app.domain.PaymentSplit
import uz.kassiron.app.domain.SaleMath
import kotlin.math.ceil
import kotlin.math.max

data class TillProduct(
    val uuid: String,
    val name: String,
    val unit: String,
    val price: Double,
    val stock: Double,
    val barcode: String?,
    val lowThreshold: Double?,
    val variantCount: Int,
) {
    val isLow: Boolean get() = stock <= 0 || (lowThreshold != null && stock <= lowThreshold)
}

data class TillVariant(val uuid: String, val label: String, val price: Double, val stock: Double, val barcode: String?)

data class TillCustomer(val uuid: String, val name: String, val phone: String?, val balance: Double)

data class CartItem(
    val productUuid: String,
    val variantUuid: String?,
    val name: String,
    val variantLabel: String?,
    val unit: String,
    val price: Double,
    val stock: Double,
    val qty: Double,
) {
    val key: String get() = productUuid + "/" + (variantUuid ?: "")
    val subtotal: Double get() = SaleMath.lineSubtotal(price, qty)
    val fractional: Boolean get() = Units.allowsFraction(unit)
}

data class LastSale(val uuid: String, val receiptNo: String, val total: Double, val change: Double, val qarz: Double)

enum class PayMode { NAQD, KARTA, ARALASH, QARZ }

sealed interface BarcodeHit {
    data class Product(val product: TillProduct) : BarcodeHit
    data class Variant(val product: TillProduct, val variant: TillVariant) : BarcodeHit
}

/** Local reads for the till. Call off the main thread. */
object TillData {
    private const val PRODUCT_COLS = """p.uuid, p.name, p.unit, p.sell_price, p.stock_qty, p.barcode, p.low_stock_threshold,
        (SELECT COUNT(*) FROM product_variants v WHERE v.product_id = p.uuid AND v.status = 'active') AS variant_count"""

    private fun product(r: Row) = TillProduct(
        uuid = r.str("uuid"),
        name = r.str("name"),
        unit = r.str("unit").ifBlank { "dona" },
        price = r.dbl("sell_price"),
        stock = r.dbl("stock_qty"),
        barcode = r.strOrNull("barcode"),
        lowThreshold = r.dblOrNull("low_stock_threshold"),
        variantCount = r.long("variant_count").toInt(),
    )

    /** Name (LIKE) or barcode (exact, product or variant) search; best sellers first when [query] is empty. */
    fun search(query: String): List<TillProduct> {
        val q = query.trim()
        if (q.isEmpty()) {
            return App.db.query(
                """SELECT $PRODUCT_COLS FROM products p WHERE p.status = 'active'
                   ORDER BY (SELECT COUNT(*) FROM sale_items si WHERE si.product_id = p.uuid) DESC, p.name COLLATE NOCASE
                   LIMIT 80""",
            ).map(::product)
        }
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return App.db.query(
            """SELECT $PRODUCT_COLS FROM products p
               WHERE p.status = 'active' AND (
                   p.name LIKE ? ESCAPE '\' OR p.barcode = ?
                   OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.uuid AND v.status = 'active'
                              AND (v.barcode = ? OR v.variant_label LIKE ? ESCAPE '\'))
               )
               ORDER BY (p.barcode = ?) DESC, (p.name LIKE ? ESCAPE '\') DESC, p.name COLLATE NOCASE
               LIMIT 80""",
            like, q, q, like, q, q.replace("%", "\\%").replace("_", "\\_") + "%",
        ).map(::product)
    }

    /** The exact product / variant with this barcode (active only). */
    fun byBarcode(code: String): BarcodeHit? {
        val c = code.trim()
        if (c.isEmpty()) return null
        App.db.queryOne(
            """SELECT v.uuid AS v_uuid, v.variant_label, v.sell_price AS v_price, v.stock_qty AS v_stock, v.barcode AS v_barcode, $PRODUCT_COLS
               FROM product_variants v JOIN products p ON p.uuid = v.product_id
               WHERE v.barcode = ? AND v.status = 'active' AND p.status = 'active' LIMIT 1""",
            c,
        )?.let { r ->
            val p = product(r)
            return BarcodeHit.Variant(
                p,
                TillVariant(r.str("v_uuid"), r.str("variant_label"), r.dblOrNull("v_price") ?: p.price, r.dbl("v_stock"), r.strOrNull("v_barcode")),
            )
        }
        return App.db.queryOne("SELECT $PRODUCT_COLS FROM products p WHERE p.barcode = ? AND p.status = 'active' LIMIT 1", c)
            ?.let { BarcodeHit.Product(product(it)) }
    }

    fun variants(product: TillProduct): List<TillVariant> = App.db.query(
        "SELECT * FROM product_variants WHERE product_id = ? AND status = 'active' ORDER BY variant_label COLLATE NOCASE",
        product.uuid,
    ).map { TillVariant(it.str("uuid"), it.str("variant_label"), it.dblOrNull("sell_price") ?: product.price, it.dbl("stock_qty"), it.strOrNull("barcode")) }

    private const val BALANCE =
        "(SELECT COALESCE(SUM(CASE WHEN d.type = 'qarz' THEN d.amount ELSE -d.amount END), 0) FROM debt_transactions d WHERE d.customer_id = c.uuid)"

    private fun customer(r: Row) = TillCustomer(r.str("uuid"), r.str("full_name"), r.strOrNull("phone"), SaleMath.round2(r.dbl("balance")))

    /** By name, or by phone digits; debtors first when [query] is empty. */
    fun customers(query: String): List<TillCustomer> {
        val q = query.trim()
        val digits = q.filter(Char::isDigit)
        return when {
            q.isEmpty() -> App.db.query(
                "SELECT c.uuid, c.full_name, c.phone, $BALANCE AS balance FROM customers c ORDER BY balance DESC, c.created_at DESC LIMIT 60",
            )
            digits.length >= 2 && q.none(Char::isLetter) -> App.db.query(
                "SELECT c.uuid, c.full_name, c.phone, $BALANCE AS balance FROM customers c WHERE c.phone LIKE ? ORDER BY c.full_name COLLATE NOCASE LIMIT 60",
                "%$digits%",
            )
            else -> App.db.query(
                "SELECT c.uuid, c.full_name, c.phone, $BALANCE AS balance FROM customers c WHERE c.full_name LIKE ? ORDER BY c.full_name COLLATE NOCASE LIMIT 60",
                "%$q%",
            )
        }.map(::customer)
    }

    fun customer(uuid: String): TillCustomer? = App.db.queryOne(
        "SELECT c.uuid, c.full_name, c.phone, $BALANCE AS balance FROM customers c WHERE c.uuid = ?", uuid,
    )?.let(::customer)
}

/** The till's state: survives going to the receipt / another tab and back. */
class TillViewModel : ViewModel() {
    val cart = mutableStateListOf<CartItem>()
    var query by mutableStateOf("")
    var lastSale by mutableStateOf<LastSale?>(null)

    // Payment.
    var mode by mutableStateOf(PayMode.NAQD)
    var cashText by mutableStateOf("")
    var kartaText by mutableStateOf("")
    var discountText by mutableStateOf("")
    var customer by mutableStateOf<TillCustomer?>(null)

    val count: Double get() = cart.sumOf { it.qty }
    val subtotal: Double get() = SaleMath.round2(cart.sumOf { it.subtotal })

    sealed interface AddResult {
        data object Added : AddResult
        data class NoStock(val name: String, val stock: Double, val unit: String) : AddResult
    }

    /** Adds one unit (or [qty]); refuses more than the stock on hand. */
    fun add(product: TillProduct, variant: TillVariant?, qty: Double = 1.0): AddResult {
        val key = product.uuid + "/" + (variant?.uuid ?: "")
        val index = cart.indexOfFirst { it.key == key }
        val stock = variant?.stock ?: product.stock
        val current = if (index >= 0) cart[index].qty else 0.0
        val name = product.name + (variant?.let { " (${it.label})" } ?: "")
        if (current + qty > stock + 0.000001) return AddResult.NoStock(name, stock, product.unit)
        if (index >= 0) {
            val item = cart.removeAt(index)
            cart.add(0, item.copy(qty = round3(item.qty + qty), stock = stock, price = variant?.price ?: product.price))
        } else {
            cart.add(
                0,
                CartItem(product.uuid, variant?.uuid, product.name, variant?.label, product.unit, variant?.price ?: product.price, stock, qty),
            )
        }
        return AddResult.Added
    }

    /** Sets a line's quantity (0 removes it); false when it exceeds the stock. */
    fun setQty(key: String, qty: Double): Boolean {
        val index = cart.indexOfFirst { it.key == key }
        if (index < 0) return true
        if (qty <= 0) { cart.removeAt(index); return true }
        if (qty > cart[index].stock + 0.000001) return false
        cart[index] = cart[index].copy(qty = round3(qty))
        return true
    }

    fun remove(key: String) { cart.removeAll { it.key == key } }

    fun clear() {
        cart.clear()
        resetPayment()
    }

    fun resetPayment() {
        mode = PayMode.NAQD
        cashText = ""
        kartaText = ""
        discountText = ""
        customer = null
    }

    fun lines(): List<CartLine> = cart.reversed().map { CartLine(it.productUuid, it.variantUuid, it.qty) }

    fun discount(canDiscount: Boolean): Double = if (canDiscount) (Money.parse(discountText) ?: 0.0) else 0.0

    /** Cash handed over and card amount as the selected mode means them. */
    fun tendered(total: Double): Pair<Double, Double> = when (mode) {
        PayMode.NAQD -> (Money.parse(cashText) ?: total) to 0.0
        PayMode.KARTA -> 0.0 to total
        PayMode.ARALASH -> {
            val karta = Money.parse(kartaText) ?: 0.0
            (Money.parse(cashText) ?: max(0.0, SaleMath.round2(total - karta))) to karta
        }
        PayMode.QARZ -> (Money.parse(cashText) ?: 0.0) to 0.0
    }

    fun split(canDiscount: Boolean): PaymentSplit {
        val total = max(0.0, SaleMath.round2(subtotal - discount(canDiscount)))
        val (naqd, karta) = tendered(total)
        return SaleMath.split(total, naqd, karta)
    }

    private fun round3(v: Double) = Math.round(v * 1000) / 1000.0
}

/** Quick cash chips: the exact total, then the total rounded up to common notes. */
fun quickCash(total: Double): List<Double> {
    if (total <= 0) return emptyList()
    val notes = listOf(1_000.0, 5_000.0, 10_000.0, 50_000.0, 100_000.0, 200_000.0)
    val out = linkedSetOf(SaleMath.round2(total))
    for (n in notes) {
        val v = ceil(total / n - 1e-9) * n
        if (v > total + 0.005) out += v
    }
    return out.toList().take(6)
}
