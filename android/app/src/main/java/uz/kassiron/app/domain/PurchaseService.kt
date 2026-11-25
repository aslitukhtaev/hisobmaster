package uz.kassiron.app.domain

import uz.kassiron.app.App
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Units

/** One line of a new purchase: a product, or one of its variants. */
data class PurchaseLine(
    val productUuid: String,
    val variantUuid: String?,
    val qty: Double,
    val unitCost: Double,
)

/**
 * Purchases ("kirim") — the server's Purchase.php. A purchase adds stock to
 * each line's product (or variant) and, when asked, sets its cost price to
 * the line's unit cost.
 */
object PurchaseService {

    /**
     * Records a purchase exactly like Purchase::create(): validates every
     * line (product exists, qty > 0 and fits the unit, a product with active
     * variants must name one), inserts the purchase and its items, adds
     * stock through Outbox deltas and optionally updates cost prices — all
     * in one transaction. Returns the new purchase's uuid.
     * @throws ProductException
     */
    fun create(supplierUuid: String?, lines: List<PurchaseLine>, note: String?, updateCostPrice: Boolean, draftUuid: String? = null): String {
        if (lines.isEmpty()) throw ProductException(ProductException.NO_ITEMS)
        val user = App.session.current()
        val uuid = App.db.write {
            // The same purchase form sent twice: it is already in, stock was added once.
            if (draftUuid != null && scalar("SELECT 1 FROM purchases WHERE uuid = ?", draftUuid) != null) return@write draftUuid
            data class Resolved(val line: PurchaseLine, val variantLabel: String?, val qty: Double, val unitCost: Double, val subtotal: Double)

            val resolved = ArrayList<Resolved>(lines.size)
            var total = 0.0
            for (line in lines) {
                val qty = Math.round(line.qty * 1000) / 1000.0
                val unitCost = Money.round2(line.unitCost)
                val product = ProductService.find(this, line.productUuid) ?: throw ProductException(ProductException.INVALID_PRODUCT)
                if (qty <= 0 || qty > ProductService.QTY_MAX || unitCost < 0 || unitCost > ProductService.MONEY_MAX) {
                    throw ProductException(ProductException.INVALID_QTY)
                }
                if (!Units.fits(qty, product.str("unit"))) {
                    throw ProductException(ProductException.QTY_WHOLE_PRODUCT, product.str("name"))
                }
                var label: String? = null
                if (line.variantUuid != null) {
                    val v = queryOne("SELECT * FROM product_variants WHERE uuid = ?", line.variantUuid)
                    if (v == null || v.str("product_id") != line.productUuid || v.str("status") != "active") {
                        throw ProductException(ProductException.INVALID_PRODUCT)
                    }
                    label = v.str("variant_label")
                } else if (scalarLong(
                        "SELECT COUNT(*) FROM product_variants WHERE product_id = ? AND status = 'active'", line.productUuid,
                    ) > 0
                ) {
                    throw ProductException(ProductException.VARIANT_REQUIRED, product.str("name"))
                }
                val subtotal = Money.round2(qty * unitCost)
                total += subtotal
                resolved += Resolved(line, label, qty, unitCost, subtotal)
            }
            total = Money.round2(total)
            if (total > ProductService.MONEY_MAX) throw ProductException(ProductException.TOO_LARGE)

            val supplier = supplierUuid?.takeIf { queryOne("SELECT uuid FROM suppliers WHERE uuid = ?", it) != null }
            val purchaseUuid = draftUuid ?: Ids.new()
            insert(
                "purchases",
                mapOf(
                    "uuid" to purchaseUuid,
                    "supplier_id" to supplier,
                    "total_amount" to total,
                    "note" to note?.trim()?.ifEmpty { null },
                    "created_by" to user.uuid,
                    "created_at" to Time.now(),
                ),
            )
            Outbox.upsert(this, "purchases", purchaseUuid)

            for (r in resolved) {
                val itemUuid = Ids.new()
                insert(
                    "purchase_items",
                    mapOf(
                        "uuid" to itemUuid,
                        "purchase_id" to purchaseUuid,
                        "product_id" to r.line.productUuid,
                        "qty" to r.qty,
                        "unit_cost" to r.unitCost,
                        "subtotal" to r.subtotal,
                        "variant_id" to r.line.variantUuid,
                        "variant_label" to r.variantLabel,
                    ),
                )
                Outbox.upsert(this, "purchase_items", itemUuid)

                if (r.line.variantUuid != null) {
                    ProductService.addStock(this, "product_variants", r.line.variantUuid, r.qty)
                    if (updateCostPrice) {
                        update("product_variants", r.line.variantUuid, mapOf("cost_price" to r.unitCost))
                        Outbox.upsert(this, "product_variants", r.line.variantUuid, listOf("cost_price"))
                    }
                } else {
                    ProductService.addStock(this, "products", r.line.productUuid, r.qty)
                    if (updateCostPrice) {
                        update("products", r.line.productUuid, mapOf("cost_price" to r.unitCost, "updated_at" to Time.now()))
                        Outbox.upsert(this, "products", r.line.productUuid, listOf("cost_price", "updated_at"))
                    }
                }
            }
            purchaseUuid
        }
        App.sync.requestSync()
        return uuid
    }

    /** Recent purchases with supplier and author names (Purchase::recentByShop), newest first. */
    fun recent(db: Db, limit: Int = 300): List<Row> = db.query(
        """SELECT p.*, s.name AS supplier_name, u.full_name AS created_by_name,
                  (SELECT COUNT(*) FROM purchase_items pi WHERE pi.purchase_id = p.uuid) AS item_count
           FROM purchases p
           LEFT JOIN suppliers s ON s.uuid = p.supplier_id
           LEFT JOIN users u ON u.uuid = p.created_by
           ORDER BY p.created_at DESC
           LIMIT $limit"""
    )

    fun find(db: Db, uuid: String): Row? = db.queryOne(
        """SELECT p.*, s.name AS supplier_name, u.full_name AS created_by_name
           FROM purchases p
           LEFT JOIN suppliers s ON s.uuid = p.supplier_id
           LEFT JOIN users u ON u.uuid = p.created_by
           WHERE p.uuid = ?""",
        uuid,
    )

    /** A purchase's lines with product name and unit (a product missing locally still shows its line). */
    fun items(db: Db, purchaseUuid: String): List<Row> = db.query(
        """SELECT pi.*, p.name AS product_name, p.unit
           FROM purchase_items pi
           LEFT JOIN products p ON p.uuid = pi.product_id
           WHERE pi.purchase_id = ?
           ORDER BY pi.rowid""",
        purchaseUuid,
    )
}
