package uz.kassiron.app.ui.till

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import uz.kassiron.app.App
import uz.kassiron.app.core.Keys
import uz.kassiron.app.domain.SaleMath

/** At most this many carts are open at once (the rest would only be forgotten ones). */
const val MAX_CARTS = 5

/** A cart left waiting this long (while it still has lines) is offered for deletion. */
const val STALE_CART_MILLIS = 24L * 60 * 60 * 1000

/** "3 ta mahsulot": whole units counted one by one, a weighed line (kg, litr) counts once. */
fun cartItemCount(lines: List<CartItem>): Int = lines.sumOf { if (it.fractional) 1 else Math.round(it.qty).toInt() }

fun cartSubtotal(lines: List<CartItem>): Double = SaleMath.round2(lines.sumOf { it.subtotal })

/**
 * One cart of the till with everything that belongs to its sale: lines,
 * Chakana / Optom, the customer, the discount and what was typed in the
 * payment sheet. Parked carts live on this device only: never synced, never in a report.
 */
data class CartSlot(
    val id: Long,
    /** The "Savat N" number: the lowest free one, so it stays the same while other carts close. */
    val number: Int,
    val name: String? = null,
    val lines: List<CartItem> = emptyList(),
    val wholesale: Boolean = false,
    val mode: PayMode = PayMode.NAQD,
    val cash: String = "",
    val karta: String = "",
    val debt: String = "",
    val discount: String = "",
    val customer: TillCustomer? = null,
    /** When the cart was last changed (epoch millis); 0 for a cart nothing was done in. */
    val touched: Long = 0,
) {
    val itemCount: Int get() = cartItemCount(lines)
    val subtotal: Double get() = cartSubtotal(lines)
    val isEmpty: Boolean get() = lines.isEmpty()
}

/** What a tab of the till shows. */
data class CartTab(
    val index: Int,
    val number: Int,
    val name: String?,
    val customerName: String?,
    val count: Int,
    val total: Double,
    val active: Boolean,
)

/** The carts as JSON: in the saved state (process death) and in the local kv (app restart). */
object CartCodec {
    private fun lineJson(c: CartItem) = JSONObject().put("p", c.productUuid).put("v", c.variantUuid ?: JSONObject.NULL).put("n", c.name)
        .put("l", c.variantLabel ?: JSONObject.NULL).put("u", c.unit).put("s", c.stock).put("q", c.qty)
        .put("r", c.retail).put("o", c.optom ?: JSONObject.NULL)

    fun line(o: JSONObject, wholesale: Boolean): CartItem {
        val retail = o.getDouble("r")
        return CartItem(
            productUuid = o.getString("p"),
            variantUuid = o.optString("v").takeIf { !o.isNull("v") && it.isNotEmpty() },
            name = o.getString("n"),
            variantLabel = o.optString("l").takeIf { !o.isNull("l") },
            unit = o.getString("u"),
            price = retail,
            stock = o.getDouble("s"),
            qty = o.getDouble("q"),
            retail = retail,
            optom = if (o.isNull("o")) null else o.getDouble("o"),
        ).priced(wholesale)
    }

    fun lines(arr: JSONArray, wholesale: Boolean): List<CartItem> =
        (0 until arr.length()).mapNotNull { runCatching { line(arr.getJSONObject(it), wholesale) }.getOrNull() }

    fun encode(slots: List<CartSlot>, active: Int, nextId: Long): String {
        val carts = JSONArray()
        for (s in slots) {
            carts.put(
                JSONObject().put("id", s.id).put("num", s.number).put("name", s.name ?: JSONObject.NULL).put("t", s.touched)
                    .put("w", s.wholesale).put("mode", s.mode.name).put("cash", s.cash).put("karta", s.karta).put("debt", s.debt)
                    .put("disc", s.discount)
                    .put(
                        "cust",
                        s.customer?.let { JSONObject().put("u", it.uuid).put("n", it.name).put("p", it.phone ?: JSONObject.NULL).put("b", it.balance) }
                            ?: JSONObject.NULL,
                    )
                    .put("lines", JSONArray().also { a -> s.lines.forEach { a.put(lineJson(it)) } }),
            )
        }
        return JSONObject().put("v", 1).put("active", active).put("next", nextId).put("carts", carts).toString()
    }

    data class Decoded(val slots: List<CartSlot>, val active: Int, val nextId: Long)

    fun decode(json: String): Decoded? = runCatching {
        val root = JSONObject(json)
        val arr = root.getJSONArray("carts")
        val slots = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val wholesale = o.optBoolean("w", false)
            CartSlot(
                id = o.getLong("id"),
                number = o.getInt("num"),
                name = o.optString("name").takeIf { !o.isNull("name") && it.isNotBlank() },
                lines = lines(o.getJSONArray("lines"), wholesale),
                wholesale = wholesale,
                mode = runCatching { PayMode.valueOf(o.optString("mode", "NAQD")) }.getOrDefault(PayMode.NAQD),
                cash = o.optString("cash"), karta = o.optString("karta"), debt = o.optString("debt"), discount = o.optString("disc"),
                customer = o.optJSONObject("cust")?.let {
                    TillCustomer(it.getString("u"), it.getString("n"), if (it.isNull("p")) null else it.getString("p"), it.optDouble("b", 0.0))
                },
                touched = o.optLong("t", 0),
            )
        }
        if (slots.isEmpty()) return null
        Decoded(slots, root.optInt("active", 0).coerceIn(0, slots.lastIndex), root.optLong("next", slots.maxOf { it.id } + 1))
    }.getOrNull()
}

/**
 * The carts in the local kv, written a moment after the last change (so typing in
 * the payment sheet doesn't write on every key) and always when the till is left.
 */
object CartStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var pending: String? = null

    fun load(): String? = runCatching { App.kv.get(Keys.TILL_CARTS) }.getOrNull()

    // The kv the waiting write belongs to: a write that comes late never lands in another database.
    @Volatile private var target: uz.kassiron.app.core.Kv? = null

    fun schedule(json: String) {
        pending = json
        target = runCatching { App.kv }.getOrNull()
        job?.cancel()
        job = scope.launch { delay(400); write() }
    }

    /** Writes what is waiting now. */
    fun flush() {
        job?.cancel()
        write()
    }

    @Synchronized private fun write() {
        val json = pending ?: return
        val kv = target ?: return
        pending = null
        runCatching { kv.set(Keys.TILL_CARTS, json) }
    }
}
