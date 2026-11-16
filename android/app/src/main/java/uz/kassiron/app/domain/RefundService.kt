package uz.kassiron.app.domain

import uz.kassiron.app.App
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Units
import kotlin.math.abs
import kotlin.math.max

/** A refund the rules refuse; [code] is one of the website's error keys (invalid_refund_qty, ...). */
class RefundException(val code: String) : Exception(code)

/**
 * Returns (server: app/Models/Refund.php). Reproduces Refund::create exactly:
 * line amounts net of the sale's discount ratio, refund_payments split in
 * the same proportion the sale was paid (allocateByPayment), stock restored
 * through counter deltas, and the qarz share taken off the customer's ledger.
 */
object RefundService {

    val PAYMENT_TYPES = listOf("naqd", "karta", "qr", "qarz")

    data class Line(
        val saleItemUuid: String,
        val productUuid: String,
        val variantUuid: String?,
        val name: String,
        val variantLabel: String?,
        val qty: Double,
        val unitPrice: Double,
        val subtotal: Double,
        val refundedQty: Double,
        val remainingQty: Double,
        val unit: String?,
    )

    /** Every line of the sale with what is already refunded and what may still come back (Refund::refundableForSale). */
    fun refundable(db: Db, saleUuid: String): List<Line> = db.query(
        """SELECT si.uuid, si.product_id, si.variant_id, si.product_name, si.variant_label, si.qty, si.unit_price, si.subtotal,
                  COALESCE((SELECT SUM(ri.qty) FROM refund_items ri WHERE ri.sale_item_id = si.uuid), 0) AS refunded_qty,
                  (SELECT p.unit FROM products p WHERE p.uuid = si.product_id) AS unit
           FROM sale_items si WHERE si.sale_id = ? ORDER BY si.rowid""",
        saleUuid,
    ).map { r ->
        Line(
            saleItemUuid = r.str("uuid"),
            productUuid = r.str("product_id"),
            variantUuid = r.strOrNull("variant_id")?.takeIf { it.isNotBlank() },
            name = r.str("product_name"),
            variantLabel = r.strOrNull("variant_label")?.takeIf { it.isNotBlank() },
            qty = r.dbl("qty"),
            unitPrice = r.dbl("unit_price"),
            subtotal = r.dbl("subtotal"),
            refundedQty = r.dbl("refunded_qty"),
            remainingQty = max(0.0, Money.round2(r.dbl("qty") - r.dbl("refunded_qty"))),
            unit = r.strOrNull("unit"),
        )
    }

    /** How much of a sale went through each payment method (Sale::paymentSplit), legacy sales included. */
    fun paymentSplit(db: Db, sale: Row): Map<String, Double> {
        val split = linkedMapOf("naqd" to 0.0, "karta" to 0.0, "qr" to 0.0, "qarz" to 0.0)
        val rows = db.query("SELECT payment_type, amount FROM sale_payments WHERE sale_id = ?", sale.str("uuid"))
        if (rows.isNotEmpty()) {
            for (r in rows) {
                val t = r.str("payment_type")
                if (t in split) split[t] = split.getValue(t) + r.dbl("amount")
            }
            return split
        }
        val total = sale.dbl("total")
        val paid = minOf(sale.dbl("paid_amount"), total)
        split["qarz"] = max(0.0, Money.round2(total - paid))
        val type = sale.str("payment_type")
        split[if (type == "karta" || type == "qr") type else "naqd"] = paid
        return split
    }

    /**
     * Splits [amount] across the methods in the sale's proportion; the first
     * used method (naqd, karta, qr, qarz order) takes the rounding remainder.
     */
    fun allocateByPayment(amount: Double, naqd: Double, karta: Double, qarz: Double, qr: Double = 0.0): Map<String, Double> {
        val result = linkedMapOf("naqd" to 0.0, "karta" to 0.0, "qr" to 0.0, "qarz" to 0.0)
        val split = linkedMapOf("naqd" to naqd, "karta" to karta, "qr" to qr, "qarz" to qarz)
        val total = naqd + karta + qr + qarz
        if (amount <= 0 || total <= 0) {
            result["naqd"] = max(0.0, amount)
            return result
        }
        val remainderKey = split.entries.first { it.value > 0 }.key
        var assigned = 0.0
        for ((type, value) in split) {
            if (type == remainderKey || value <= 0) continue
            val share = Money.round2(amount * value / total)
            result[type] = share
            assigned += share
        }
        result[remainderKey] = Money.round2(amount - assigned)
        return result
    }

    /** The sale's discount as a share of its pre-discount subtotal. */
    fun discountRatio(sale: Row): Double {
        val subtotal = Money.round2(sale.dbl("total") + sale.dbl("discount"))
        return if (subtotal > 0) sale.dbl("discount") / subtotal else 0.0
    }

    /** What returning [qty] of a line gives back, net of the sale-wide discount. */
    fun lineAmount(unitPrice: Double, qty: Double, ratio: Double): Double =
        Money.round2(Money.round2(unitPrice * qty) * (1 - ratio))

    /**
     * Issues a refund ([lines]: sale_items.uuid -> qty) in one transaction and
     * returns the new refunds.uuid. Throws [RefundException].
     */
    fun create(saleUuid: String, userUuid: String, lines: Map<String, Double>, reason: String?): String {
        val wanted = lines.filterValues { it > 0 }
        if (wanted.isEmpty()) throw RefundException("refund_no_items_selected")

        return App.db.write {
            val sale = queryOne("SELECT * FROM sales WHERE uuid = ?", saleUuid) ?: throw RefundException("sale_not_found")
            val refundable = refundable(this, saleUuid).associateBy { it.saleItemUuid }
            val ratio = discountRatio(sale)

            data class Resolved(val line: Line, val qty: Double, val amount: Double)
            val resolved = mutableListOf<Resolved>()
            var totalAmount = 0.0
            for ((itemUuid, rawQty) in wanted) {
                val item = refundable[itemUuid] ?: throw RefundException("invalid_refund_item")
                val qty = Money.round2(rawQty)
                if (qty <= 0 || qty > item.remainingQty + 0.0001) throw RefundException("invalid_refund_qty")
                val isRemainder = abs(qty - item.remainingQty) < 0.0001
                if (!isRemainder && !Units.fits(qty, item.unit)) throw RefundException("invalid_refund_qty_whole")
                val net = lineAmount(item.unitPrice, qty, ratio)
                totalAmount += net
                resolved += Resolved(item, qty, net)
            }
            totalAmount = Money.round2(totalAmount)

            val refundUuid = Ids.new()
            val now = Time.now()
            insert(
                "refunds",
                mapOf(
                    "uuid" to refundUuid, "sale_id" to saleUuid, "refunded_by" to userUuid,
                    "total_amount" to totalAmount, "reason" to reason, "created_at" to now,
                ),
            )
            Outbox.upsert(this, "refunds", refundUuid)

            for (r in resolved) {
                val itemRowUuid = Ids.new()
                insert(
                    "refund_items",
                    mapOf("uuid" to itemRowUuid, "refund_id" to refundUuid, "sale_item_id" to r.line.saleItemUuid, "qty" to r.qty, "amount" to r.amount),
                )
                Outbox.upsert(this, "refund_items", itemRowUuid)

                val variant = r.line.variantUuid
                if (variant != null) {
                    if (scalar("SELECT 1 FROM product_variants WHERE uuid = ?", variant) == null) throw RefundException("product_not_found")
                    exec("UPDATE product_variants SET stock_qty = stock_qty + ? WHERE uuid = ?", r.qty, variant)
                    Outbox.delta(this, "product_variants", variant, "stock_qty", r.qty)
                } else {
                    if (scalar("SELECT 1 FROM products WHERE uuid = ?", r.line.productUuid) == null) throw RefundException("product_not_found")
                    exec("UPDATE products SET stock_qty = stock_qty + ? WHERE uuid = ?", r.qty, r.line.productUuid)
                    Outbox.delta(this, "products", r.line.productUuid, "stock_qty", r.qty)
                }
            }

            val split = paymentSplit(this, sale)
            val allocation = allocateByPayment(
                totalAmount, split.getValue("naqd"), split.getValue("karta"), split.getValue("qarz"), split.getValue("qr"),
            )
            for ((type, amount) in allocation) {
                if (amount > 0) {
                    val payUuid = Ids.new()
                    insert("refund_payments", mapOf("uuid" to payUuid, "refund_id" to refundUuid, "payment_type" to type, "amount" to amount))
                    Outbox.upsert(this, "refund_payments", payUuid)
                }
            }

            val customer = sale.strOrNull("customer_id")?.takeIf { it.isNotBlank() }
            val debtPortion = allocation.getValue("qarz")
            if (customer != null && debtPortion > 0) {
                Debts.record(this, customer, saleUuid, Debts.REFUND, debtPortion, userUuid)
            }
            refundUuid
        }
    }
}
