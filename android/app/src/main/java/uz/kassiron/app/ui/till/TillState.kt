package uz.kassiron.app.ui.till

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.core.SearchText
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Row
import uz.kassiron.app.core.Units
import uz.kassiron.app.domain.CartLine
import uz.kassiron.app.domain.PaymentSplit
import uz.kassiron.app.domain.SaleMath
import uz.kassiron.app.domain.Wholesale
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
    /** Optom price, null = none (the retail price is used then too). */
    val wholesale: Double? = null,
) {
    val isLow: Boolean get() = stock <= 0 || (lowThreshold != null && stock <= lowThreshold)
    fun priceFor(optom: Boolean): Double = Wholesale.unitPrice(price, null, wholesale, optom)
}

/** [wholesale]: the variant's own optom price (the product's is used when null). */
data class TillVariant(val uuid: String, val label: String, val price: Double, val stock: Double, val barcode: String?, val wholesale: Double? = null) {
    fun priceFor(product: TillProduct, optom: Boolean): Double = Wholesale.unitPrice(price, wholesale, product.wholesale, optom)
}

data class TillCustomer(val uuid: String, val name: String, val phone: String?, val balance: Double)

data class CartItem(
    val productUuid: String,
    val variantUuid: String?,
    val name: String,
    val variantLabel: String?,
    val unit: String,
    /** What the line costs now: [retail] or, in an optom sale, [optom] when there is one. */
    val price: Double,
    val stock: Double,
    val qty: Double,
    val retail: Double = price,
    val optom: Double? = null,
    /** Gone from the shop, switched off, or less in stock than in the cart (re-read after each sync). */
    val unavailable: Boolean = false,
) {
    fun priced(wholesale: Boolean): CartItem = copy(price = if (wholesale) optom ?: retail else retail)
    val key: String get() = productUuid + "/" + (variantUuid ?: "")
    val subtotal: Double get() = SaleMath.lineSubtotal(price, qty)
    val fractional: Boolean get() = Units.allowsFraction(unit)
}

data class LastSale(val uuid: String, val receiptNo: String, val total: Double, val change: Double, val qarz: Double)

/**
 * The three payment tiles. Karta and Qarz take an amount: when it is less than
 * the total, the rest is paid in cash (naqd + karta / naqd + qarz = "aralash").
 */
enum class PayMode { NAQD, KARTA, QARZ }

sealed interface BarcodeHit {
    data class Product(val product: TillProduct) : BarcodeHit
    data class Variant(val product: TillProduct, val variant: TillVariant) : BarcodeHit
}

/** Local reads for the till. Call off the main thread. */
object TillData {
    private const val PRODUCT_COLS = """p.uuid, p.name, p.unit, p.sell_price, p.stock_qty, p.barcode, p.low_stock_threshold, p.wholesale_price,
        (SELECT COUNT(*) FROM product_variants v WHERE v.product_id = p.uuid AND v.status = 'active') AS variant_count"""

    /** The shop-wide low stock level (settings.low_stock_threshold_default), used when a product has none — as Product::lowStock does. */
    private fun shopLowDefault(): Double? = App.kv.shopSetting("low_stock_threshold_default")?.let(Money::parse)

    private fun product(r: Row, lowDefault: Double? = null) = TillProduct(
        uuid = r.str("uuid"),
        name = r.str("name"),
        unit = r.str("unit").ifBlank { "dona" },
        price = r.dbl("sell_price"),
        stock = r.dbl("stock_qty"),
        barcode = r.strOrNull("barcode"),
        lowThreshold = r.dblOrNull("low_stock_threshold") ?: lowDefault,
        variantCount = r.long("variant_count").toInt(),
        wholesale = r.dblOrNull("wholesale_price"),
    )

    /** Product categories that have active products (the till's chips), by name. */
    fun categories(): List<Pair<String, String>> = App.db.query(
        """SELECT c.uuid, c.name FROM categories c
           WHERE c.type = 'product' AND EXISTS (SELECT 1 FROM products p WHERE p.category_id = c.uuid AND p.status = 'active')
           ORDER BY c.name COLLATE NOCASE""",
    ).map { it.str("uuid") to it.str("name") }

    /**
     * Name (LIKE) or barcode (exact, product or variant) search; best sellers first when [query] is empty.
     * [category]: only that category's products.
     */
    fun search(query: String, category: String? = null): List<TillProduct> {
        val q = query.trim()
        val low = shopLowDefault()
        val inCategory = if (category != null) "AND p.category_id = ?" else ""
        val catArgs = listOfNotNull(category).toTypedArray()
        if (q.isEmpty()) {
            // Best sellers of the last 30 days, counted once in one grouped pass
            // (not a count per product), so this stays quick with a big catalogue.
            return App.db.query(
                """SELECT $PRODUCT_COLS FROM products p
                   LEFT JOIN (SELECT si.product_id AS pid, COUNT(*) AS n FROM sale_items si
                              JOIN sales s ON s.uuid = si.sale_id
                              WHERE s.created_at >= ? GROUP BY si.product_id) b ON b.pid = p.uuid
                   WHERE p.status = 'active' $inCategory
                   ORDER BY COALESCE(b.n, 0) DESC, p.name COLLATE NOCASE
                   LIMIT 80""",
                Time.utcDaysAgo(30), *catArgs,
            ).map { product(it, low) }
        }
        if (SearchText.needsKotlin(q)) return searchInKotlin(q, inCategory, catArgs, low)
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return App.db.query(
            """SELECT $PRODUCT_COLS FROM products p
               WHERE p.status = 'active' $inCategory AND (
                   p.name LIKE ? ESCAPE '\' OR p.barcode = ?
                   OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.uuid AND v.status = 'active'
                              AND (v.barcode = ? OR v.variant_label LIKE ? ESCAPE '\'))
               )
               ORDER BY (p.barcode = ?) DESC, (p.name LIKE ? ESCAPE '\') DESC, p.name COLLATE NOCASE
               LIMIT 80""",
            *catArgs, like, q, q, like, q, q.replace("%", "\\%").replace("_", "\\_") + "%",
        ).map { product(it, low) }
    }

    /** Cyrillic or apostrophes in the query: compared here, since SQLite's LIKE only folds ASCII. */
    private fun searchInKotlin(q: String, inCategory: String, catArgs: Array<String>, low: Double?): List<TillProduct> {
        val key = SearchText.key(q)
        val rows = App.db.query(
            """SELECT $PRODUCT_COLS,
                      (SELECT group_concat(v.variant_label, '|') FROM product_variants v WHERE v.product_id = p.uuid AND v.status = 'active') AS v_labels
               FROM products p WHERE p.status = 'active' $inCategory""",
            *catArgs,
        )
        return rows.asSequence()
            .filter { SearchText.matches(it.str("name"), key) || SearchText.matches(it.strOrNull("v_labels"), key) }
            .sortedWith(compareByDescending<Row> { SearchText.key(it.str("name")).startsWith(key) }.thenBy { SearchText.key(it.str("name")) })
            .take(80)
            .map { product(it, low) }
            .toList()
    }

    /** What a cart line costs and has in stock now; null when the product (or variant) is gone or switched off. */
    data class Fresh(val retail: Double, val optom: Double?, val stock: Double)

    fun fresh(item: CartItem): Fresh? {
        val p = App.db.queryOne("SELECT sell_price, wholesale_price, stock_qty, status FROM products WHERE uuid = ?", item.productUuid)
            ?: return null
        if (p.str("status") != "active") return null
        if (item.variantUuid == null) return Fresh(p.dbl("sell_price"), p.dblOrNull("wholesale_price"), p.dbl("stock_qty"))
        val v = App.db.queryOne("SELECT sell_price, wholesale_price, stock_qty, status FROM product_variants WHERE uuid = ?", item.variantUuid)
            ?: return null
        if (v.str("status") != "active") return null
        return Fresh(v.dblOrNull("sell_price") ?: p.dbl("sell_price"), v.dblOrNull("wholesale_price") ?: p.dblOrNull("wholesale_price"), v.dbl("stock_qty"))
    }

    /** The exact product / variant with this barcode (active only). */
    fun byBarcode(code: String): BarcodeHit? {
        val c = code.trim()
        if (c.isEmpty()) return null
        App.db.queryOne(
            """SELECT v.uuid AS v_uuid, v.variant_label, v.sell_price AS v_price, v.stock_qty AS v_stock, v.barcode AS v_barcode,
                      v.wholesale_price AS v_wholesale, $PRODUCT_COLS
               FROM product_variants v JOIN products p ON p.uuid = v.product_id
               WHERE v.barcode = ? AND v.status = 'active' AND p.status = 'active' LIMIT 1""",
            c,
        )?.let { r ->
            val p = product(r, shopLowDefault())
            return BarcodeHit.Variant(
                p,
                TillVariant(r.str("v_uuid"), r.str("variant_label"), r.dblOrNull("v_price") ?: p.price, r.dbl("v_stock"), r.strOrNull("v_barcode"), r.dblOrNull("v_wholesale")),
            )
        }
        return App.db.queryOne("SELECT $PRODUCT_COLS FROM products p WHERE p.barcode = ? AND p.status = 'active' LIMIT 1", c)
            ?.let { BarcodeHit.Product(product(it, shopLowDefault())) }
    }

    fun variants(product: TillProduct): List<TillVariant> = App.db.query(
        "SELECT * FROM product_variants WHERE product_id = ? AND status = 'active' ORDER BY variant_label COLLATE NOCASE",
        product.uuid,
    ).map {
        TillVariant(it.str("uuid"), it.str("variant_label"), it.dblOrNull("sell_price") ?: product.price, it.dbl("stock_qty"), it.strOrNull("barcode"), it.dblOrNull("wholesale_price"))
    }

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
            SearchText.needsKotlin(q) -> {
                val key = SearchText.key(q)
                App.db.query("SELECT c.uuid, c.full_name, c.phone, $BALANCE AS balance FROM customers c ORDER BY c.full_name COLLATE NOCASE")
                    .filter { SearchText.matches(it.str("full_name"), key) }
                    .take(60)
            }
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

/**
 * The till's state: survives going to the receipt / another tab and back,
 * and (every cart, through [saved] and the local kv) Android closing the app in
 * the background while the cashier is in a payment app, or a restart.
 *
 * Several carts can be open at once (up to [MAX_CARTS]): one customer's long
 * cart waits while a quick one is rung up. The fields below ([cart], [wholesale],
 * the payment fields, [customer]) always belong to the ACTIVE cart; the others are
 * kept in [slots] and swapped in by [switchTo]. A waiting cart reserves no stock
 * and is never synced.
 */
class TillViewModel(private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    val cart = mutableStateListOf<CartItem>()
    var query by mutableStateOf("")
    /** The chosen category chip (its uuid), null = all; kept across rotation like the query. */
    var category by mutableStateOf<String?>(null)
    var lastSale by mutableStateOf<LastSale?>(null)

    /** Optom sale: every line at its optom price. Back to chakana after each sale and when the cart is cleared. */
    var wholesale by mutableStateOf(false)
        private set

    fun setWholesaleMode(on: Boolean) {
        wholesale = on
        for (i in cart.indices) cart[i] = cart[i].priced(on)
        persist()
    }

    // Payment: every change is kept (a half-typed payment survives like the lines do).
    private var modeState by mutableStateOf(PayMode.NAQD)
    private var cashState by mutableStateOf("")
    private var kartaState by mutableStateOf("")
    private var debtState by mutableStateOf("")
    private var discountState by mutableStateOf("")
    private var customerState by mutableStateOf<TillCustomer?>(null)

    var mode: PayMode
        get() = modeState
        set(v) { modeState = v; persist() }
    var cashText: String
        get() = cashState
        set(v) { cashState = v; persist() }
    var kartaText: String
        get() = kartaState
        set(v) { kartaState = v; persist() }
    var debtText: String
        get() = debtState
        set(v) { debtState = v; persist() }
    var discountText: String
        get() = discountState
        set(v) { discountState = v; persist() }
    var customer: TillCustomer?
        get() = customerState
        set(v) { customerState = v; persist() }

    // ---------- the carts ----------

    private val slots = mutableStateListOf<CartSlot>()
    /** Index of the open cart in [slots]. */
    var active by mutableIntStateOf(0)
        private set
    private var nextId = 1L
    private var restoring = false

    /** Carts left waiting longer than a day (asked about once on opening the till). */
    var stalePrompt by mutableIntStateOf(0)
        private set

    /** The tabs of the till: every cart with what is in it (the open one live). */
    val tabs: List<CartTab>
        get() = slots.mapIndexed { i, s ->
            if (i == active) CartTab(i, s.number, s.name, customerState?.name, itemCount, subtotal, true)
            else CartTab(i, s.number, s.name, s.customer?.name, s.itemCount, s.subtotal, false)
        }

    val cartCount: Int get() = slots.size
    val canAddCart: Boolean get() = slots.size < MAX_CARTS

    /** The open cart as a slot (its id, number and name come from [slots]). */
    private fun snapshot(): CartSlot = slots[active].copy(
        lines = cart.toList(), wholesale = wholesale, mode = modeState, cash = cashState, karta = kartaState,
        debt = debtState, discount = discountState, customer = customerState,
    )

    private fun loadSlot(s: CartSlot) {
        cart.clear()
        cart.addAll(s.lines)
        wholesale = s.wholesale
        modeState = s.mode
        cashState = s.cash
        kartaState = s.karta
        debtState = s.debt
        discountState = s.discount
        customerState = s.customer
    }

    /** Opens a new empty cart next to the others and switches to it; false at the limit. */
    fun newCart(): Boolean {
        if (slots.size >= MAX_CARTS) return false
        persist()
        val number = (1..MAX_CARTS).first { n -> slots.none { it.number == n } }
        slots += CartSlot(id = nextId++, number = number)
        active = slots.lastIndex
        loadSlot(slots[active])
        write()
        return true
    }

    fun switchTo(index: Int) {
        if (index == active || index !in slots.indices) return
        persist()
        active = index
        loadSlot(slots[index])
        write()
    }

    /** Closes a cart with its lines. The last cart is not removed but emptied ("Savat 1"). */
    fun closeCart(index: Int) {
        if (index !in slots.indices) return
        persist()
        if (slots.size == 1) {
            slots[0] = CartSlot(id = nextId++, number = 1)
            active = 0
            loadSlot(slots[0])
            write()
            return
        }
        slots.removeAt(index)
        when {
            index == active -> { active = minOf(index, slots.lastIndex); loadSlot(slots[active]) }
            index < active -> active -= 1
        }
        write()
    }

    fun renameCart(index: Int, name: String) {
        if (index !in slots.indices) return
        persist()
        slots[index] = slots[index].copy(name = name.trim().take(MAX_NAME).ifBlank { null })
        write()
    }

    /** The sale of the open cart went through: only this cart closes, the waiting ones stay. */
    fun finishSale() = closeCart(active)

    private fun staleSlots(now: Long = System.currentTimeMillis()): List<Int> =
        slots.indices.filter { i -> !slots[i].isEmpty && slots[i].touched > 0 && now - slots[i].touched > STALE_CART_MILLIS }

    /** Deletes the carts left waiting for a day. */
    fun deleteStale() {
        persist()
        val stale = staleSlots()
        stalePrompt = 0
        for (i in stale.reversed()) closeCart(i)
    }

    /** Keeps them, and asks again in a day. */
    fun keepStale() {
        persist()
        val now = System.currentTimeMillis()
        staleSlots().forEach { slots[it] = slots[it].copy(touched = now) }
        stalePrompt = 0
        write()
    }

    /** Writes the carts to the local kv now (leaving the till, tests). */
    fun flush() = CartStore.flush()

    override fun onCleared() = CartStore.flush()

    /**
     * Brings the cart in line with the database after a sync or another
     * screen's edit: the price the sale will charge, the stock, and whether
     * the product can still be sold. [fresh] is [TillData.fresh] per line, in cart order.
     */
    fun applyFresh(fresh: List<Pair<String, TillData.Fresh?>>) {
        var changed = false
        for ((key, f) in fresh) {
            val i = cart.indexOfFirst { it.key == key }
            if (i < 0) continue
            val old = cart[i]
            val updated = if (f == null) old.copy(unavailable = true)
                else old.copy(retail = f.retail, optom = f.optom, stock = f.stock, unavailable = old.qty > f.stock + 0.000001).priced(wholesale)
            if (updated != old) { cart[i] = updated; changed = true }
        }
        if (changed) persist()
    }

    /** Remembers the open cart (and every other) after a change. */
    private fun persist() {
        if (restoring || slots.isEmpty()) return
        val now = snapshot()
        val old = slots[active]
        // The time only moves when something in the cart really changed.
        slots[active] = if (now.copy(touched = old.touched) == old) old else now.copy(touched = System.currentTimeMillis())
        write()
    }

    private fun write() {
        val json = CartCodec.encode(slots, active, nextId)
        saved[SAVED_CARTS] = json
        CartStore.schedule(json)
    }

    private fun restore() {
        restoring = true
        try {
            // The saved state of a process the system closed, else what was kept on the device, else the old single-cart format.
            val decoded = saved.get<String>(SAVED_CARTS)?.let(CartCodec::decode)
                ?: legacySlot()?.let { CartCodec.Decoded(listOf(it), 0, it.id + 1) }
                ?: CartStore.load()?.let(CartCodec::decode)
            if (decoded == null) {
                slots += CartSlot(id = nextId++, number = 1)
                return
            }
            slots += decoded.slots
            active = decoded.active
            nextId = maxOf(decoded.nextId, decoded.slots.maxOf { it.id } + 1)
            loadSlot(slots[active])
            stalePrompt = staleSlots().size
        } finally {
            restoring = false
        }
    }

    /** The single cart older versions kept in the saved state. */
    private fun legacySlot(): CartSlot? {
        val json = saved.get<String>(SAVED_CART) ?: return null
        val wholesale = saved.get<Boolean>(SAVED_WHOLESALE) ?: false
        val lines = runCatching { CartCodec.lines(JSONArray(json), wholesale) }.getOrNull() ?: return null
        return CartSlot(id = 1, number = 1, lines = lines, wholesale = wholesale, touched = System.currentTimeMillis())
    }

    val count: Double get() = cart.sumOf { it.qty }

    /** "3 ta mahsulot": whole units counted one by one, a weighed line (kg, litr) counts once. */
    val itemCount: Int get() = cartItemCount(cart)

    fun qtyOf(productUuid: String): Double = cart.filter { it.productUuid == productUuid }.sumOf { it.qty }
    val subtotal: Double get() = cartSubtotal(cart)

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
        val retail = variant?.price ?: product.price
        val optom = if (variant != null) variant.wholesale ?: product.wholesale else product.wholesale
        if (index >= 0) {
            val item = cart.removeAt(index)
            cart.add(0, item.copy(qty = round3(item.qty + qty), stock = stock, retail = retail, optom = optom).priced(wholesale))
        } else {
            cart.add(
                0,
                CartItem(product.uuid, variant?.uuid, product.name, variant?.label, product.unit, retail, stock, qty, retail, optom).priced(wholesale),
            )
        }
        persist()
        return AddResult.Added
    }

    /** Sets a line's quantity (0 removes it); false when it exceeds the stock. */
    fun setQty(key: String, qty: Double): Boolean {
        val index = cart.indexOfFirst { it.key == key }
        if (index < 0) return true
        if (qty <= 0) { cart.removeAt(index); persist(); return true }
        if (qty > cart[index].stock + 0.000001) return false
        cart[index] = cart[index].copy(qty = round3(qty), unavailable = false)
        persist()
        return true
    }

    fun remove(key: String) { cart.removeAll { it.key == key }; persist() }

    /** Empties the open cart (the tab stays). */
    fun clear() {
        cart.clear()
        wholesale = false
        resetPayment()
        persist()
    }

    private companion object {
        const val SAVED_CART = "till_cart"
        const val SAVED_WHOLESALE = "till_wholesale"
        const val SAVED_CARTS = "till_carts"
        const val MAX_NAME = 20
    }

    fun priceType(): String = if (wholesale) Wholesale.WHOLESALE else Wholesale.RETAIL

    fun resetPayment() {
        modeState = PayMode.NAQD
        cashState = ""
        kartaState = ""
        debtState = ""
        discountState = ""
        customerState = null
    }

    fun lines(): List<CartLine> = cart.reversed().map { CartLine(it.productUuid, it.variantUuid, it.qty) }

    fun discount(canDiscount: Boolean): Double = if (canDiscount) (Money.parse(discountText) ?: 0.0) else 0.0

    /** Cash handed over and card amount as the selected mode means them. */
    fun tendered(total: Double): Pair<Double, Double> = when (mode) {
        PayMode.NAQD -> (Money.parse(cashText) ?: total) to 0.0
        PayMode.KARTA -> {
            val karta = (Money.parse(kartaText) ?: total).coerceIn(0.0, total)
            SaleMath.round2(total - karta) to karta
        }
        PayMode.QARZ -> {
            val debt = (Money.parse(debtText) ?: total).coerceIn(0.0, total)
            SaleMath.round2(total - debt) to 0.0
        }
    }

    fun split(canDiscount: Boolean): PaymentSplit {
        val total = max(0.0, SaleMath.round2(subtotal - discount(canDiscount)))
        val (naqd, karta) = tendered(total)
        return SaleMath.split(total, naqd, karta)
    }

    private fun round3(v: Double) = Math.round(v * 1000) / 1000.0

    init {
        restore()
    }
}

/** Quick cash chips: the exact total, then the total rounded up to common notes (19 000 → 20 000, 50 000, 100 000). */
fun quickCash(total: Double): List<Double> {
    if (total <= 0) return emptyList()
    val notes = listOf(1_000.0, 5_000.0, 10_000.0, 20_000.0, 50_000.0, 100_000.0, 200_000.0)
    val out = linkedSetOf(SaleMath.round2(total))
    for (n in notes) {
        val v = ceil(total / n - 1e-9) * n
        if (v > total + 0.005) out += v
    }
    return out.toList().take(4)
}
