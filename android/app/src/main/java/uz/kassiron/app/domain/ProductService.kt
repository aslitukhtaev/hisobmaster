package uz.kassiron.app.domain

import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Units
import kotlin.math.abs
import uz.kassiron.app.core.SearchText

/**
 * A rule broken while saving products, categories, suppliers or purchases.
 * [code] is mapped to a translated message by the UI (ui/products/ProductsCommon.kt);
 * [arg] fills its placeholder (a unit, a barcode, a product name).
 */
class ProductException(val code: String, val arg: String? = null) : Exception(code) {
    companion object {
        const val FILL_REQUIRED = "fill_required"
        const val NAME_FORMULA = "name_formula"
        const val NAME_TOO_LONG = "name_too_long"
        const val NEGATIVE = "negative"
        const val TOO_LARGE = "too_large"
        const val QTY_WHOLE = "qty_whole"               // arg = unit
        const val BARCODE_TOO_LONG = "barcode_too_long"
        const val BARCODE_TAKEN = "barcode_taken"       // arg = barcode
        const val BARCODE_DUPLICATE = "barcode_duplicate"
        const val PACK_SIZE = "pack_size"
        const val MXIK = "mxik"
        const val VAT = "vat"
        const val VARIANT_LABEL = "variant_label"
        const val PRICE_REQUIRED = "price_required"
        const val UNIT_TOO_LONG = "unit_too_long"
        const val NOT_FOUND = "not_found"
        const val PHONE_INVALID = "phone_invalid"
        const val NO_ITEMS = "no_items"
        const val INVALID_PRODUCT = "invalid_product"
        const val INVALID_QTY = "invalid_qty"
        const val VARIANT_REQUIRED = "variant_required" // arg = product name
        const val QTY_WHOLE_PRODUCT = "qty_whole_product" // arg = product name
        const val NAME_TAKEN = "name_taken"
    }
}

/** A product row of the list, with what its variants add (Product::allByShop + EFFECTIVE_STOCK). */
data class ProductListItem(
    val uuid: String,
    val name: String,
    val unit: String,
    val sellPrice: Double,
    val costPrice: Double,
    val ownStock: Double,
    val barcode: String?,
    val active: Boolean,
    val lowStockThreshold: Double?,
    val categoryId: String?,
    val categoryName: String?,
    val variantCount: Int,
    val variantStock: Double,
    /** Lower-cased name + barcode + variants' labels and barcodes, for instant local search. */
    val searchText: String,
    val wholesalePrice: Double? = null,
) {
    /** A product with active variants sells only through them: its stock is theirs. */
    val stock: Double get() = if (variantCount > 0) variantStock else ownStock

    fun threshold(shopDefault: Double?): Double? = lowStockThreshold ?: shopDefault
    fun isOut(): Boolean = stock <= 0.000001
    fun isLow(shopDefault: Double?): Boolean = threshold(shopDefault)?.let { stock <= it + 0.000001 } ?: false
}

/** Fields of the product form exactly as typed (strings); [ProductService.save] validates them like ProductController. */
data class ProductForm(
    val name: String = "",
    val barcode: String = "",
    val unit: String = "dona",
    val sellPrice: String = "",
    val costPrice: String = "",
    val initialStock: String = "",
    val categoryId: String? = null,
    val lowStockThreshold: String = "",
    val packSize: String = "",
    val mxik: String = "",
    val packageCode: String = "",
    val vatRate: String = "",
    val active: Boolean = true,
    /** The optom price as typed; null = the field isn't shown (wholesale off): the saved value stays. */
    val wholesalePrice: String? = null,
)

data class VariantForm(
    val label: String = "",
    val barcode: String = "",
    val sellPrice: String = "",
    val costPrice: String = "",
    val initialStock: String = "",
    /** First variant only: move the product's own stock onto it (the website's take_parent_stock). */
    val takeParentStock: Boolean = false,
    /** As in [ProductForm]: null = not shown, keep the saved one; blank = the product's optom price. */
    val wholesalePrice: String? = null,
)

/** One row of the quick entry table (QuickEntryController::productsStore). */
data class QuickRow(
    val name: String = "",
    val barcode: String = "",
    val sellPrice: String = "",
    val costPrice: String = "",
    val stock: String = "",
    val unit: String = "dona",
    /** Optional optom price (the column shows only when the shop sells wholesale). */
    val wholesalePrice: String = "",
) {
    val isBlank: Boolean get() = name.isBlank() && barcode.isBlank() && sellPrice.isBlank() && costPrice.isBlank() && stock.isBlank() && wholesalePrice.isBlank()
}

data class QuickResult(val created: List<Int>, val errors: Map<Int, ProductException>)

/**
 * Products, variants, categories and suppliers — the server's Product.php,
 * ProductVariant.php, Category.php, Supplier.php and the validation of
 * ProductController / QuickEntryController / SupplierController.
 *
 * Writers run their own `App.db.write { }` (call them from `io { }`), record
 * every row in the Outbox and ask for a sync. Stock is never written as a
 * value: a new row is inserted with 0 and the starting stock added as a delta.
 */
object ProductService {
    const val MONEY_MAX = 1_000_000_000_000.0
    const val QTY_MAX = 1_000_000_000.0
    private const val EPS = 0.000001

    // ---------- reading ----------

    /** The shop-wide low stock threshold (Settings::lowStockThresholdDefault), null when not set. */
    fun shopLowStockDefault(): Double? = App.kv.shopSetting("low_stock_threshold_default")?.let(Money::parse)

    /** Every product with its variants' count/stock and category, sorted by name. */
    fun list(db: Db): List<ProductListItem> {
        val variantText = HashMap<String, StringBuilder>()
        for (v in db.query("SELECT product_id, variant_label, barcode FROM product_variants")) {
            variantText.getOrPut(v.str("product_id")) { StringBuilder() }
                .append(' ').append(v.str("variant_label")).append(' ').append(v.str("barcode"))
        }
        return db.query(
            """SELECT p.uuid, p.name, p.unit, p.sell_price, p.cost_price, p.stock_qty, p.barcode, p.status,
                      p.low_stock_threshold, p.category_id, c.name AS category_name, p.wholesale_price,
                      COALESCE(v.cnt, 0) AS variant_count, COALESCE(v.stock, 0) AS variant_stock
               FROM products p
               LEFT JOIN categories c ON c.uuid = p.category_id
               LEFT JOIN (SELECT product_id, COUNT(*) AS cnt, SUM(stock_qty) AS stock
                          FROM product_variants WHERE status = 'active' GROUP BY product_id) v ON v.product_id = p.uuid
               ORDER BY p.name COLLATE NOCASE"""
        ).map { r ->
            val uuid = r.str("uuid")
            ProductListItem(
                uuid = uuid,
                name = r.str("name"),
                unit = r.str("unit").ifBlank { "dona" },
                sellPrice = r.dbl("sell_price"),
                costPrice = r.dbl("cost_price"),
                ownStock = r.dbl("stock_qty"),
                barcode = r.strOrNull("barcode"),
                active = r.str("status") == "active",
                lowStockThreshold = r.dblOrNull("low_stock_threshold"),
                categoryId = r.strOrNull("category_id"),
                categoryName = r.strOrNull("category_name"),
                variantCount = r.long("variant_count").toInt(),
                variantStock = r.dbl("variant_stock"),
                searchText = SearchText.key(r.str("name") + " " + r.str("barcode") + (variantText[uuid] ?: "")),
                wholesalePrice = r.dblOrNull("wholesale_price"),
            )
        }
    }

    /** Stock at cost: products' own stock plus every variant's at its cost or its product's (Product::counts). */
    fun stockValue(db: Db): Double = db.scalarDouble(
        """SELECT COALESCE((SELECT SUM(cost_price * stock_qty) FROM products), 0)
                + COALESCE((SELECT SUM(COALESCE(pv.cost_price, p.cost_price) * pv.stock_qty)
                            FROM product_variants pv JOIN products p ON p.uuid = pv.product_id), 0)"""
    )

    fun find(db: Db, uuid: String): Row? = db.queryOne("SELECT * FROM products WHERE uuid = ?", uuid)

    fun variants(db: Db, productUuid: String): List<Row> =
        db.query("SELECT * FROM product_variants WHERE product_id = ? ORDER BY variant_label COLLATE NOCASE", productUuid)

    fun categories(db: Db, type: String = "product"): List<Row> =
        db.query("SELECT * FROM categories WHERE type = ? ORDER BY name COLLATE NOCASE", type)

    fun suppliers(db: Db): List<Row> = db.query("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE")

    /**
     * Who already has [barcode] — "Name" or "Name — variant" — or null when it
     * is free (Product::barcodeTaken: products and variants, active or not).
     */
    fun barcodeOwner(db: Db, barcode: String, exceptProduct: String? = null, exceptVariant: String? = null): String? {
        val code = barcode.trim()
        if (code.isEmpty()) return null
        db.queryOne("SELECT name FROM products WHERE barcode = ? AND uuid != ? LIMIT 1", code, exceptProduct ?: "")
            ?.let { return it.str("name") }
        db.queryOne(
            """SELECT pv.variant_label, p.name FROM product_variants pv LEFT JOIN products p ON p.uuid = pv.product_id
               WHERE pv.barcode = ? AND pv.uuid != ? LIMIT 1""",
            code, exceptVariant ?: "",
        )?.let { return it.str("name") + " — " + it.str("variant_label") }
        return null
    }

    fun barcodeTaken(db: Db, barcode: String, exceptProduct: String? = null, exceptVariant: String? = null): Boolean =
        barcodeOwner(db, barcode, exceptProduct, exceptVariant) != null

    // ---------- validation helpers (helpers.php) ----------

    /** looks_like_formula(): a name starting with =, +, -, @ would run as a formula in Excel exports. */
    fun looksLikeFormula(value: String): Boolean =
        value.isNotEmpty() && value.trim().toDoubleOrNull() == null && value[0] in charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** A money amount 0..MONEY_MAX, or throws. Blank -> [blank]. */
    private fun money(raw: String, blank: Double? = null): Double? {
        if (raw.isBlank()) return blank
        val v = Money.parse(raw) ?: throw ProductException(ProductException.NEGATIVE)
        if (v < 0) throw ProductException(ProductException.NEGATIVE)
        if (v > MONEY_MAX) throw ProductException(ProductException.TOO_LARGE)
        return Money.round2(v)
    }

    private fun qty(raw: String, unit: String): Double {
        if (raw.isBlank()) return 0.0
        val v = Money.parse(raw) ?: throw ProductException(ProductException.NEGATIVE)
        if (v < 0) throw ProductException(ProductException.NEGATIVE)
        if (v > QTY_MAX) throw ProductException(ProductException.TOO_LARGE)
        if (!Units.fits(v, unit)) throw ProductException(ProductException.QTY_WHOLE, unit)
        return Math.round(v * 1000) / 1000.0
    }

    private fun checkBarcode(db: Db, barcode: String, exceptProduct: String?, exceptVariant: String?) {
        if (barcode.isEmpty()) return
        if (barcode.length > 64) throw ProductException(ProductException.BARCODE_TOO_LONG)
        if (barcodeTaken(db, barcode, exceptProduct, exceptVariant)) throw ProductException(ProductException.BARCODE_TAKEN, barcode)
    }

    private fun checkName(name: String) {
        if (name.isEmpty()) throw ProductException(ProductException.FILL_REQUIRED)
        if (looksLikeFormula(name)) throw ProductException(ProductException.NAME_FORMULA)
        if (name.length > 200) throw ProductException(ProductException.NAME_TOO_LONG)
    }

    private fun activity(db: Db, userUuid: String?, action: String, meta: Map<String, String>) {
        val uuid = Ids.new()
        db.insert(
            "activity_log",
            mapOf(
                "uuid" to uuid,
                "user_id" to userUuid,
                "action" to action,
                "meta_json" to JSONObject(meta).toString(),
                "created_at" to Time.now(),
            ),
        )
        Outbox.upsert(db, "activity_log", uuid)
    }

    /** format_qty(): "2", "1.25". */
    private fun formatQty(v: Double): String = Money.qty(v).replace(',', '.')

    // ---------- products ----------

    /**
     * Creates ([uuid] null) or updates a product, validated like
     * ProductController::validate(). Without [canPrices] the cost price is
     * kept (and on an existing product the sell price too). Returns the uuid.
     *
     * [draftUuid]: the id the new-product form picked when it opened. Saving
     * the same form again (a second tap while the screen closes) finds that
     * product and updates it instead of creating a twin.
     */
    fun save(form: ProductForm, uuid: String?, canPrices: Boolean, draftUuid: String? = null): String {
        val userUuid = App.session.user.value?.uuid
        val result = App.db.write {
            val existing = if (uuid != null) find(this, uuid) ?: throw ProductException(ProductException.NOT_FOUND)
                else draftUuid?.let { find(this, it) }
            val name = form.name.trim()
            val unit = form.unit.trim().ifEmpty { "dona" }
            val barcode = form.barcode.trim()

            checkName(name)
            if (unit.length > 20) throw ProductException(ProductException.UNIT_TOO_LONG)

            val sellPrice: Double = if (existing != null && !canPrices) existing.dbl("sell_price")
                else money(form.sellPrice) ?: throw ProductException(ProductException.FILL_REQUIRED)
            val costPrice: Double = if (canPrices) money(form.costPrice, 0.0)!!
                else existing?.dbl("cost_price") ?: 0.0
            val stock = if (existing == null) qty(form.initialStock, unit) else 0.0

            // An existing product whose unit changed must still hold a count that fits it.
            if (existing != null && unit != existing.str("unit") && !Units.fits(existing.dbl("stock_qty"), unit)) {
                throw ProductException(ProductException.QTY_WHOLE, unit)
            }

            checkBarcode(this, barcode, existing?.str("uuid"), null)

            val thresholdRaw = form.lowStockThreshold.trim()
            val threshold = if (thresholdRaw.isEmpty()) null else {
                val v = Money.parse(thresholdRaw) ?: throw ProductException(ProductException.NEGATIVE)
                if (v < 0 || v > QTY_MAX) throw ProductException(ProductException.NEGATIVE)
                v
            }
            val packRaw = form.packSize.trim()
            val packSize = if (packRaw.isEmpty()) null else {
                if (!packRaw.all(Char::isDigit)) throw ProductException(ProductException.PACK_SIZE)
                val v = packRaw.toLongOrNull() ?: throw ProductException(ProductException.PACK_SIZE)
                if (v < 1 || v > 100000) throw ProductException(ProductException.PACK_SIZE)
                v
            }
            val mxik = form.mxik.trim()
            val packageCode = form.packageCode.trim()
            if ((mxik.isNotEmpty() && (!mxik.all(Char::isDigit) || mxik.length > 20)) ||
                (packageCode.isNotEmpty() && (!packageCode.all(Char::isDigit) || packageCode.length > 12))
            ) throw ProductException(ProductException.MXIK)
            val vatRaw = form.vatRate.trim()
            val vat = if (vatRaw.isEmpty()) null else {
                val v = Money.parse(vatRaw) ?: throw ProductException(ProductException.VAT)
                if (v < 0 || v > 100) throw ProductException(ProductException.VAT)
                v
            }
            val categoryId = form.categoryId?.takeIf { id -> queryOne("SELECT uuid FROM categories WHERE uuid = ?", id) != null }
            // Editable like the sell price (ProductController: anyone who may edit products).
            val wholesale: Double? = if (form.wholesalePrice == null) existing?.dblOrNull("wholesale_price")
                else money(form.wholesalePrice, null)

            val values = mapOf(
                "category_id" to categoryId,
                "name" to name,
                "unit" to unit,
                "cost_price" to costPrice,
                "sell_price" to sellPrice,
                "barcode" to barcode.ifEmpty { null },
                "status" to if (form.active) "active" else "inactive",
                "low_stock_threshold" to threshold,
                "pack_size" to packSize,
                "mxik" to mxik.ifEmpty { null },
                "package_code" to packageCode.ifEmpty { null },
                "vat_rate" to vat,
                "wholesale_price" to wholesale,
                "updated_at" to Time.now(),
            )
            if (existing == null) {
                val newUuid = draftUuid ?: Ids.new()
                insert("products", values + mapOf("uuid" to newUuid, "stock_qty" to 0.0, "created_at" to Time.now()))
                Outbox.upsert(this, "products", newUuid)
                addStock(this, "products", newUuid, stock)
                activity(this, userUuid, "product_created", mapOf("name" to name, "qty" to "${formatQty(stock)} $unit"))
                newUuid
            } else {
                update("products", existing.str("uuid"), values)
                Outbox.upsert(this, "products", existing.str("uuid"))
                existing.str("uuid")
            }
        }
        App.sync.requestSync()
        return result
    }

    /** Active <-> inactive (Product::setStatus). */
    fun setStatus(uuid: String, active: Boolean) {
        App.db.write {
            update("products", uuid, mapOf("status" to if (active) "active" else "inactive", "updated_at" to Time.now()))
            Outbox.upsert(this, "products", uuid)
        }
        App.sync.requestSync()
    }

    /** stock_qty += qty as a counter (never as a value). Call inside db.write. */
    fun addStock(db: Db, table: String, uuid: String, qty: Double) {
        if (abs(qty) < EPS) return
        db.exec("UPDATE $table SET stock_qty = stock_qty + ? WHERE uuid = ?", qty, uuid)
        Outbox.delta(db, table, uuid, "stock_qty", qty)
    }

    // ---------- variants ----------

    /**
     * Adds ([variantUuid] null) or edits a variant, validated like
     * ProductController::validateVariant(). Stock is set only for a new
     * variant. Returns the variant's uuid.
     */
    fun saveVariant(productUuid: String, variantUuid: String?, form: VariantForm, canPrices: Boolean): String {
        val userUuid = App.session.user.value?.uuid
        val result = App.db.write {
            val product = find(this, productUuid) ?: throw ProductException(ProductException.NOT_FOUND)
            val existing = variantUuid?.let {
                queryOne("SELECT * FROM product_variants WHERE uuid = ? AND product_id = ?", it, productUuid)
                    ?: throw ProductException(ProductException.NOT_FOUND)
            }
            val unit = product.str("unit")
            val label = form.label.trim()
            if (label.isEmpty()) throw ProductException(ProductException.FILL_REQUIRED)
            if (looksLikeFormula(label) || label.length > 100) throw ProductException(ProductException.VARIANT_LABEL)

            val isFirst = existing == null &&
                scalarLong("SELECT COUNT(*) FROM product_variants WHERE product_id = ? AND status = 'active'", productUuid) == 0L
            val parentStock = product.dbl("stock_qty")
            val takeParent = isFirst && form.takeParentStock && parentStock > EPS
            val stock = when {
                existing != null -> 0.0
                takeParent -> parentStock
                else -> qty(form.initialStock, unit)
            }

            val sell: Double? = if (existing != null && !canPrices) existing.dblOrNull("sell_price") else money(form.sellPrice, null)
            val cost: Double? = if (canPrices) money(form.costPrice, null) else existing?.dblOrNull("cost_price")
            val barcode = form.barcode.trim()
            checkBarcode(this, barcode, null, existing?.str("uuid"))

            val wholesale: Double? = if (form.wholesalePrice == null) existing?.dblOrNull("wholesale_price")
                else money(form.wholesalePrice, null)
            val values = mapOf(
                "variant_label" to label,
                "barcode" to barcode.ifEmpty { null },
                "sell_price" to sell,
                "cost_price" to cost,
                "wholesale_price" to wholesale,
            )
            if (existing == null) {
                val newUuid = Ids.new()
                insert(
                    "product_variants",
                    values + mapOf(
                        "uuid" to newUuid, "product_id" to productUuid, "stock_qty" to 0.0,
                        "status" to "active", "created_at" to Time.now(),
                    ),
                )
                Outbox.upsert(this, "product_variants", newUuid)
                addStock(this, "product_variants", newUuid, stock)
                if (takeParent) {
                    // Once sold through variants, the product's own stock would be stranded.
                    addStock(this, "products", productUuid, -parentStock)
                    update("products", productUuid, mapOf("updated_at" to Time.now()))
                    Outbox.upsert(this, "products", productUuid)
                }
                activity(
                    this, userUuid, "product_created",
                    mapOf("name" to product.str("name") + " — " + label, "qty" to "${formatQty(stock)} $unit"),
                )
                newUuid
            } else {
                update("product_variants", existing.str("uuid"), values)
                Outbox.upsert(this, "product_variants", existing.str("uuid"))
                existing.str("uuid")
            }
        }
        App.sync.requestSync()
        return result
    }

    fun setVariantStatus(variantUuid: String, active: Boolean) {
        App.db.write {
            update("product_variants", variantUuid, mapOf("status" to if (active) "active" else "inactive"))
            Outbox.upsert(this, "product_variants", variantUuid)
        }
        App.sync.requestSync()
    }

    // ---------- quick entry ----------

    /**
     * Saves the filled rows of the quick entry table in one transaction
     * (QuickEntryController::productsStore): rows with a mistake are
     * reported by index and skipped, the rest are created. [categoryId]: the
     * one category the screen picked for the whole batch (null = none).
     */
    fun quickCreate(rows: List<Pair<Int, QuickRow>>, canPrices: Boolean, categoryId: String? = null): QuickResult {
        val userUuid = App.session.user.value?.uuid
        val created = mutableListOf<Int>()
        val errors = LinkedHashMap<Int, ProductException>()
        App.db.write {
            val category = categoryId?.takeIf { id -> queryOne("SELECT uuid FROM categories WHERE uuid = ? AND type = 'product'", id) != null }
            val seen = HashSet<String>()
            for ((index, row) in rows) {
                try {
                    val name = row.name.trim()
                    val barcode = row.barcode.trim()
                    val unit = row.unit.trim().ifEmpty { "dona" }
                    checkName(name)
                    if (row.sellPrice.isBlank()) throw ProductException(ProductException.PRICE_REQUIRED)
                    val sell = money(row.sellPrice)!!
                    val cost = if (canPrices) money(row.costPrice, 0.0)!! else 0.0
                    val wholesale = money(row.wholesalePrice, null)
                    if (unit.length > 20) throw ProductException(ProductException.UNIT_TOO_LONG)
                    val stock = qty(row.stock, unit)
                    if (barcode.length > 64) throw ProductException(ProductException.BARCODE_TOO_LONG)
                    if (barcode.isNotEmpty() && barcode in seen) throw ProductException(ProductException.BARCODE_DUPLICATE)
                    if (barcode.isNotEmpty() && barcodeTaken(this, barcode)) throw ProductException(ProductException.BARCODE_TAKEN, barcode)

                    val uuid = Ids.new()
                    val now = Time.now()
                    insert(
                        "products",
                        mapOf(
                            "uuid" to uuid, "category_id" to category, "name" to name, "unit" to unit,
                            "cost_price" to cost, "sell_price" to sell, "wholesale_price" to wholesale, "stock_qty" to 0.0,
                            "barcode" to barcode.ifEmpty { null }, "status" to "active",
                            "created_at" to now, "updated_at" to now,
                        ),
                    )
                    Outbox.upsert(this, "products", uuid)
                    addStock(this, "products", uuid, stock)
                    if (barcode.isNotEmpty()) seen += barcode
                    created += index
                } catch (e: ProductException) {
                    errors[index] = e
                }
            }
            if (created.isNotEmpty()) activity(this, userUuid, "products_bulk_created", mapOf("count" to created.size.toString()))
        }
        if (created.isNotEmpty()) App.sync.requestSync()
        return QuickResult(created, errors)
    }

    // ---------- categories ----------

    /** A category name as stored and compared: trimmed, inner whitespace collapsed (like the server). */
    fun cleanCategoryName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    /** The category of [type] whose name matches [name] (any case, Cyrillic too), other than [except]. */
    fun categoryNamed(db: Db, name: String, type: String, except: String? = null): Row? {
        val clean = cleanCategoryName(name)
        return categories(db, type).firstOrNull { it.str("uuid") != except && cleanCategoryName(it.str("name")).equals(clean, ignoreCase = true) }
    }

    /** Category::findOrCreate: the category with this name (any case), or a new one. Returns its uuid. */
    fun findOrCreateCategory(name: String, type: String = "product"): String {
        val clean = cleanCategoryName(name)
        checkName(clean)
        val result = App.db.write {
            categoryNamed(this, clean, type)?.str("uuid")
                ?: Ids.new().also { uuid ->
                    insert("categories", mapOf("uuid" to uuid, "name" to clean, "type" to type, "created_at" to Time.now()))
                    Outbox.upsert(this, "categories", uuid)
                }
        }
        App.sync.requestSync()
        return result
    }

    /** Renames a category; another one of the same type already called that → NAME_TAKEN with its uuid in [ProductException.arg]. */
    fun renameCategory(uuid: String, name: String) {
        val clean = cleanCategoryName(name)
        checkName(clean)
        App.db.write {
            val row = queryOne("SELECT * FROM categories WHERE uuid = ?", uuid) ?: throw ProductException(ProductException.NOT_FOUND)
            categoryNamed(this, clean, row.str("type"), except = uuid)?.let { throw ProductException(ProductException.NAME_TAKEN, it.str("uuid")) }
            update("categories", uuid, mapOf("name" to clean))
            Outbox.upsert(this, "categories", uuid)
            if (row.str("name") != clean) activity(this, App.session.user.value?.uuid, "category_renamed", mapOf("from" to row.str("name"), "to" to clean))
        }
        App.sync.requestSync()
    }

    /** How many products (or expenses, for an expense category) use the category. */
    fun categoryUseCount(db: Db, uuid: String): Int =
        (db.scalarLong("SELECT COUNT(*) FROM products WHERE category_id = ?", uuid) +
            db.scalarLong("SELECT COUNT(*) FROM expenses WHERE category_id = ?", uuid)).toInt()

    /**
     * Deletes a category. Its products and expenses stay, without a category:
     * here they are cleared locally; the server clears its own copies before
     * deleting it, so they need no outbox entries.
     */
    fun deleteCategory(uuid: String) {
        App.db.write {
            val row = queryOne("SELECT * FROM categories WHERE uuid = ?", uuid) ?: return@write
            exec("UPDATE products SET category_id = NULL WHERE category_id = ?", uuid)
            exec("UPDATE expenses SET category_id = NULL WHERE category_id = ?", uuid)
            delete("categories", uuid)
            Outbox.delete(this, "categories", uuid)
            activity(this, App.session.user.value?.uuid, "category_deleted", mapOf("name" to row.str("name")))
        }
        App.sync.requestSync()
    }

    /** Moves everything in [fromUuid] to [intoUuid] (same type) and deletes [fromUuid]. Returns how many rows moved. */
    fun mergeCategory(fromUuid: String, intoUuid: String): Int {
        if (fromUuid == intoUuid) throw ProductException(ProductException.NOT_FOUND)
        val moved = App.db.write {
            val from = queryOne("SELECT * FROM categories WHERE uuid = ?", fromUuid) ?: throw ProductException(ProductException.NOT_FOUND)
            val into = queryOne("SELECT * FROM categories WHERE uuid = ?", intoUuid) ?: throw ProductException(ProductException.NOT_FOUND)
            if (from.str("type") != into.str("type")) throw ProductException(ProductException.NOT_FOUND)
            var count = 0
            // The moved rows go to the server before the delete, so it never clears them.
            for (table in listOf("products", "expenses")) {
                val uuids = query("SELECT uuid FROM $table WHERE category_id = ?", fromUuid).map { it.str("uuid") }
                for (u in uuids) {
                    update(table, u, if (table == "products") mapOf("category_id" to intoUuid, "updated_at" to Time.now()) else mapOf("category_id" to intoUuid))
                    Outbox.upsert(this, table, u)
                }
                count += uuids.size
            }
            delete("categories", fromUuid)
            Outbox.delete(this, "categories", fromUuid)
            activity(this, App.session.user.value?.uuid, "category_merged", mapOf("from" to from.str("name"), "into" to into.str("name"), "count" to count.toString()))
            count
        }
        App.sync.requestSync()
        return moved
    }

    // ---------- suppliers ----------

    /** Creates or updates a supplier (SupplierController::validate). Returns its uuid. */
    /** [draftUuid]: see [save] — the same new-supplier form saved twice stays one supplier. */
    fun saveSupplier(uuid: String?, name: String, phone: String, address: String, note: String, draftUuid: String? = null): String {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) throw ProductException(ProductException.FILL_REQUIRED)
        if (looksLikeFormula(cleanName)) throw ProductException(ProductException.NAME_FORMULA)
        if (cleanName.length > 200) throw ProductException(ProductException.NAME_TOO_LONG)
        val phoneClean = if (phone.isBlank()) null
            else Customers.normalizePhone(phone) ?: throw ProductException(ProductException.PHONE_INVALID)
        val values = mapOf(
            "name" to cleanName,
            "phone" to phoneClean,
            "address" to address.trim().ifEmpty { null },
            "note" to note.trim().ifEmpty { null },
        )
        val result = App.db.write {
            if (uuid == null) {
                val id = draftUuid ?: Ids.new()
                if (queryOne("SELECT uuid FROM suppliers WHERE uuid = ?", id) != null) update("suppliers", id, values)
                else insert("suppliers", values + mapOf("uuid" to id, "created_at" to Time.now()))
                Outbox.upsert(this, "suppliers", id)
                id
            } else {
                queryOne("SELECT uuid FROM suppliers WHERE uuid = ?", uuid) ?: throw ProductException(ProductException.NOT_FOUND)
                update("suppliers", uuid, values)
                Outbox.upsert(this, "suppliers", uuid)
                uuid
            }
        }
        App.sync.requestSync()
        return result
    }
}
