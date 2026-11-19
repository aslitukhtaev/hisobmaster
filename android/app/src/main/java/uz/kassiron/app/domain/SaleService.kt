package uz.kassiron.app.domain

import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Units
import uz.kassiron.app.core.User
import kotlin.math.max
import kotlin.math.min

/** One cart line as the till sends it: prices are always re-read from the database. */
data class CartLine(val productUuid: String, val variantUuid: String?, val qty: Double)

/**
 * Optom (wholesale) selling: one product, one stock, a second price. Exists
 * only when the shop turned it on on the website (settings.wholesale_enabled = "1").
 */
object Wholesale {
    const val RETAIL = "retail"
    const val WHOLESALE = "wholesale"

    fun enabled(): Boolean = App.kv.shopSetting("wholesale_enabled") == "1"

    /** Selling optom needs the "wholesale" permission (the owner always has it). */
    const val PERMISSION = "wholesale"

    /**
     * What a line costs (Sale.php does the same): optom = the variant's optom
     * price, else the product's, else the ordinary price.
     */
    fun unitPrice(retail: Double, variantWholesale: Double?, productWholesale: Double?, wholesale: Boolean): Double =
        if (wholesale) variantWholesale ?: productWholesale ?: retail else retail
}

/**
 * Why a sale was refused. [code] is one of: empty_cart, invalid_product,
 * invalid_qty, qty_must_be_whole, variant_required, invalid_discount,
 * discount_too_large, customer_required_for_debt, insufficient_stock,
 * not_signed_in. [subject] names the product when the code is about one
 * (the server's "insufficient_stock:<name>").
 */
class SaleException(val code: String, val subject: String? = null) :
    Exception(if (subject != null) "$code:$subject" else code)

/** How a sale's total is paid (Sale::create's split, before anything is written). */
data class PaymentSplit(
    val total: Double,
    val naqd: Double,          // cash that counts towards the total
    val karta: Double,
    val qarz: Double,          // what's left: becomes the customer's debt
    val cashReceived: Double?, // what the customer handed over, when more than naqd (else null)
    val paidAmount: Double,    // naqd + karta
    val paymentType: String,   // naqd | karta | qarz | aralash
) {
    val change: Double get() = cashReceived?.let { Money.round2(max(0.0, it - naqd)) } ?: 0.0
}

/** Pure money rules of Sale::create — no database, unit-tested. */
object SaleMath {
    const val QTY_MAX = 1_000_000_000.0

    /**
     * PHP's round($v, 2): half away from zero on the decimal value as written
     * (round(1.005, 2) = 1.01), where Math.round(v * 100) would give 1.00.
     */
    fun round2(v: Double): Double =
        if (!v.isFinite()) v else java.math.BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    fun lineSubtotal(unitPrice: Double, qty: Double): Double = round2(unitPrice * qty)

    /** Validates the discount against the subtotal; returns the rounded discount. */
    fun checkDiscount(discount: Double, subtotal: Double): Double {
        if (discount < 0 || !discount.isFinite()) throw SaleException("invalid_discount")
        if (discount > 0 && discount >= subtotal - 0.005) throw SaleException("discount_too_large")
        return round2(discount)
    }

    fun total(subtotal: Double, discount: Double): Double = round2(subtotal - discount)

    /**
     * Karta is exact and applied first (capped at the total); cash covers what
     * is left, the rest of what was handed over is change (cash_received);
     * qarz is whatever remains.
     */
    fun split(total: Double, naqdTendered: Double, karta: Double): PaymentSplit {
        val cashTendered = max(0.0, naqdTendered)
        val kartaAmount = max(0.0, min(karta, total))
        val naqdAmount = max(0.0, min(cashTendered, round2(total - kartaAmount)))
        val qarzAmount = max(0.0, round2(total - naqdAmount - kartaAmount))
        val cashReceived = if (cashTendered > naqdAmount + 0.005) round2(cashTendered) else null
        return PaymentSplit(
            total = total,
            naqd = naqdAmount,
            karta = kartaAmount,
            qarz = qarzAmount,
            cashReceived = cashReceived,
            paidAmount = round2(naqdAmount + kartaAmount),
            paymentType = paymentTypeLabel(naqdAmount, kartaAmount, qarzAmount),
        )
    }

    /** Sale::derivePaymentTypeLabel: the single method used, 'aralash' for several, 'naqd' for none. */
    fun paymentTypeLabel(naqd: Double, karta: Double, qarz: Double): String {
        val active = listOf("naqd" to naqd, "karta" to karta, "qarz" to qarz).filter { it.second > 0.0 }.map { it.first }
        return when (active.size) {
            0 -> "naqd"
            1 -> active[0]
            else -> "aralash"
        }
    }

    /** "K3" + 145 -> "K3-000145" (Desktop::nextReceiptNo). */
    fun receiptNo(deviceCode: String?, seq: Long): String =
        (deviceCode?.takeIf { it.isNotBlank() } ?: "K") + "-" + seq.toString().padStart(6, '0')
}

/** A sale that was just written. */
data class CreatedSale(val uuid: String, val receiptNo: String, val total: Double, val change: Double, val qarz: Double)

/**
 * Selling (server: app/Models/Sale.php::create). Everything happens in one
 * App.db.write: a refused sale leaves no trace. Call off the main thread.
 */
object SaleService {

    private data class Resolved(
        val productUuid: String,
        val productName: String,
        val variantUuid: String?,
        val variantLabel: String?,
        val qty: Double,
        val unitPrice: Double,
        val cost: Double,
        val subtotal: Double,
    )

    fun create(
        cart: List<CartLine>,
        naqdTendered: Double,
        karta: Double,
        discount: Double,
        customerUuid: String?,
        priceType: String = Wholesale.RETAIL,
    ): CreatedSale {
        if (cart.isEmpty()) throw SaleException("empty_cart")
        val cashier = App.session.user.value ?: throw SaleException("not_signed_in")
        val result = App.db.write { createIn(this, cashier.uuid, cart, naqdTendered, karta, discount, customerUuid, priceType) }
        App.sync.requestSync()
        return result
    }

    /** The sale itself; [db] must be inside a write transaction. */
    fun createIn(
        db: Db,
        cashierUuid: String,
        cart: List<CartLine>,
        naqdTendered: Double,
        karta: Double,
        discount: Double,
        customerUuid: String?,
        priceType: String = Wholesale.RETAIL,
    ): CreatedSale {
        if (cart.isEmpty()) throw SaleException("empty_cart")
        val wholesale = priceType == Wholesale.WHOLESALE
        if (wholesale) {
            // Refused, never quietly sold at retail: the cashier must know.
            val seller = db.queryOne("SELECT * FROM users WHERE uuid = ?", cashierUuid)?.let(User::from)
            if (seller == null || !seller.can(Wholesale.PERMISSION)) throw SaleException("wholesale_not_allowed")
        }

        var subtotal = 0.0
        val resolved = ArrayList<Resolved>(cart.size)
        for (line in cart) {
            val product = db.queryOne("SELECT * FROM products WHERE uuid = ?", line.productUuid)
            if (product == null || product.str("status") != "active") throw SaleException("invalid_product")
            val name = product.str("name")
            val qty = line.qty
            if (!qty.isFinite() || qty <= 0 || qty > SaleMath.QTY_MAX) throw SaleException("invalid_qty")
            if (!Units.fits(qty, product.strOrNull("unit"))) throw SaleException("qty_must_be_whole", name)

            var variant: uz.kassiron.app.core.Row? = null
            if (!line.variantUuid.isNullOrEmpty()) {
                variant = db.queryOne("SELECT * FROM product_variants WHERE uuid = ?", line.variantUuid)
                if (variant == null || variant.str("product_id") != line.productUuid || variant.str("status") != "active") {
                    throw SaleException("invalid_product")
                }
            } else if (db.scalarLong(
                    "SELECT COUNT(*) FROM product_variants WHERE product_id = ? AND status = 'active'", line.productUuid,
                ) > 0
            ) {
                throw SaleException("variant_required", name)
            }

            val retail = variant?.dblOrNull("sell_price") ?: product.dbl("sell_price")
            val unitPrice = Wholesale.unitPrice(retail, variant?.dblOrNull("wholesale_price"), product.dblOrNull("wholesale_price"), wholesale)
            val cost = variant?.dblOrNull("cost_price") ?: product.dbl("cost_price")
            val lineSubtotal = SaleMath.lineSubtotal(unitPrice, qty)
            subtotal += lineSubtotal
            resolved += Resolved(
                productUuid = line.productUuid,
                productName = name,
                variantUuid = variant?.str("uuid"),
                variantLabel = variant?.strOrNull("variant_label"),
                qty = qty,
                unitPrice = unitPrice,
                cost = cost,
                subtotal = lineSubtotal,
            )
        }

        val disc = SaleMath.checkDiscount(discount, subtotal)
        val total = SaleMath.total(subtotal, disc)
        val split = SaleMath.split(total, naqdTendered, karta)

        val customer = customerUuid?.takeIf { it.isNotEmpty() }?.takeIf {
            db.scalarLong("SELECT COUNT(*) FROM customers WHERE uuid = ?", it) > 0
        }
        if (split.qarz > 0 && customer == null) throw SaleException("customer_required_for_debt")

        // Receipt number: this device's code + its own sequence (rolled back with the sale).
        val seq = App.kv.long(Keys.RECEIPT_SEQ, 0) + 1
        App.kv.set(Keys.RECEIPT_SEQ, seq.toString())
        val receiptNo = SaleMath.receiptNo(App.kv.get(Keys.DEVICE_CODE), seq)

        val saleUuid = Ids.new()
        val now = Time.now()
        db.insert(
            "sales",
            mapOf(
                "uuid" to saleUuid,
                "cashier_id" to cashierUuid,
                "customer_id" to customer,
                "total" to total,
                "discount" to disc,
                "payment_type" to split.paymentType,
                "paid_amount" to split.paidAmount,
                "status" to "completed",
                "cash_received" to split.cashReceived,
                "created_at" to now,
                "receipt_no" to receiptNo,
                "price_type" to if (wholesale) Wholesale.WHOLESALE else Wholesale.RETAIL,
            ),
        )
        Outbox.upsert(db, "sales", saleUuid)

        for ((type, amount) in listOf("naqd" to split.naqd, "karta" to split.karta, "qarz" to split.qarz)) {
            if (amount > 0) {
                val uuid = Ids.new()
                db.insert("sale_payments", mapOf("uuid" to uuid, "sale_id" to saleUuid, "payment_type" to type, "amount" to amount))
                Outbox.upsert(db, "sale_payments", uuid)
            }
        }

        for (item in resolved) {
            val itemUuid = Ids.new()
            db.insert(
                "sale_items",
                mapOf(
                    "uuid" to itemUuid,
                    "sale_id" to saleUuid,
                    "product_id" to item.productUuid,
                    "product_name" to item.productName,
                    "qty" to item.qty,
                    "unit_price" to item.unitPrice,
                    "cost_price_snapshot" to item.cost,
                    "subtotal" to item.subtotal,
                    "variant_id" to item.variantUuid,
                    "variant_label" to item.variantLabel,
                ),
            )
            Outbox.upsert(db, "sale_items", itemUuid)

            // Conditional decrement: refuses to sell stock that isn't there.
            val affected = if (item.variantUuid != null) {
                db.exec(
                    "UPDATE product_variants SET stock_qty = stock_qty - ? WHERE uuid = ? AND product_id = ? AND stock_qty >= ? - 0.000001",
                    item.qty, item.variantUuid, item.productUuid, item.qty,
                )
                db.scalarLong("SELECT changes()")
            } else {
                db.exec(
                    "UPDATE products SET stock_qty = stock_qty - ? WHERE uuid = ? AND stock_qty >= ? - 0.000001",
                    item.qty, item.productUuid, item.qty,
                )
                db.scalarLong("SELECT changes()")
            }
            if (affected != 1L) {
                val label = if (item.variantLabel != null) "${item.productName} (${item.variantLabel})" else item.productName
                throw SaleException("insufficient_stock", label)
            }
            if (item.variantUuid != null) Outbox.delta(db, "product_variants", item.variantUuid, "stock_qty", -item.qty)
            else Outbox.delta(db, "products", item.productUuid, "stock_qty", -item.qty)
        }

        if (split.qarz > 0 && customer != null) {
            Debts.record(db, customer, saleUuid, Debts.QARZ, split.qarz, cashierUuid)
        }

        // The website's ActivityLog::record(..., 'sale_created', ...).
        val logUuid = Ids.new()
        db.insert(
            "activity_log",
            mapOf(
                "uuid" to logUuid,
                "user_id" to cashierUuid,
                "action" to "sale_created",
                "meta_json" to JSONObject().put("sale_id", receiptNo).put("total", Money.format(total)).toString(),
                "created_at" to now,
            ),
        )
        Outbox.upsert(db, "activity_log", logUuid)

        return CreatedSale(saleUuid, receiptNo, total, split.change, split.qarz)
    }
}
