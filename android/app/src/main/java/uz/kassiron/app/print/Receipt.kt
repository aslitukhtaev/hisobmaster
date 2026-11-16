package uz.kassiron.app.print

import android.content.Context
import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Time
import kotlin.math.max

/** Everything a receipt shows (app/views/sales/_receipt.php). */
data class ReceiptData(
    val saleUuid: String,
    val shopName: String,
    val shopAddress: String,
    val shopPhone: String,
    val receiptNo: String,
    val createdAt: String,      // local "dd.MM.yyyy HH:mm"
    val cashier: String,
    val items: List<ReceiptItem>,
    val discount: Double,
    val total: Double,
    val paymentType: String,
    val payments: List<Pair<String, Double>>, // naqd/karta/qarz -> amount
    val cashReceived: Double,
    val change: Double,
    val paidAmount: Double,
    val debt: Double,
    val customerName: String?,
    val refunded: Double,
)

data class ReceiptItem(val name: String, val qty: Double, val unitPrice: Double, val subtotal: Double)

object Receipts {
    /** Reads a sale for its receipt; null when it doesn't exist (call off the main thread). */
    fun load(db: Db, saleUuid: String): ReceiptData? {
        val sale = db.queryOne(
            """SELECT s.*, u.full_name AS cashier_name, c.full_name AS customer_name
               FROM sales s LEFT JOIN users u ON u.uuid = s.cashier_id LEFT JOIN customers c ON c.uuid = s.customer_id
               WHERE s.uuid = ?""",
            saleUuid,
        ) ?: return null
        val items = db.query("SELECT * FROM sale_items WHERE sale_id = ? ORDER BY rowid", saleUuid).map {
            val label = it.strOrNull("variant_label")?.takeIf(String::isNotBlank)
            ReceiptItem(
                name = it.str("product_name") + (label?.let { l -> " — $l" } ?: ""),
                qty = it.dbl("qty"),
                unitPrice = it.dbl("unit_price"),
                subtotal = it.dbl("subtotal"),
            )
        }
        val payments = db.query(
            """SELECT payment_type, amount FROM sale_payments WHERE sale_id = ?
               ORDER BY CASE payment_type WHEN 'naqd' THEN 1 WHEN 'karta' THEN 2 WHEN 'qr' THEN 3 WHEN 'qarz' THEN 4 ELSE 5 END""",
            saleUuid,
        ).map { it.str("payment_type") to it.dbl("amount") }
        val cashReceived = sale.dblOrNull("cash_received") ?: 0.0
        val naqdPaid = payments.filter { it.first == "naqd" }.sumOf { it.second }
        val shop = App.kv.shop()
        val refunded = db.scalarDouble("SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE sale_id = ?", saleUuid)
        return ReceiptData(
            saleUuid = saleUuid,
            shopName = shop.optString("name", ""),
            shopAddress = shop.optString("address", "").takeIf { it != "null" } ?: "",
            shopPhone = shop.optString("phone", "").takeIf { it != "null" } ?: "",
            receiptNo = sale.strOrNull("receipt_no")?.takeIf(String::isNotBlank) ?: saleUuid.take(8),
            createdAt = Time.local(sale.str("created_at")),
            cashier = sale.str("cashier_name"),
            items = items,
            discount = sale.dbl("discount"),
            total = sale.dbl("total"),
            paymentType = sale.str("payment_type"),
            payments = payments,
            cashReceived = cashReceived,
            change = if (cashReceived > 0) Money.round2(max(0.0, cashReceived - naqdPaid)) else 0.0,
            paidAmount = sale.dbl("paid_amount"),
            debt = Money.round2(sale.dbl("total") - sale.dbl("paid_amount")),
            customerName = sale.strOrNull("customer_name")?.takeIf(String::isNotBlank),
            refunded = Money.round2(refunded),
        )
    }

    fun paymentLabel(context: Context, type: String): String = when (type) {
        "naqd" -> context.getString(R.string.till_pay_naqd)
        "karta" -> context.getString(R.string.till_pay_karta)
        "qarz" -> context.getString(R.string.till_pay_qarz)
        "qr" -> "QR"
        "aralash" -> context.getString(R.string.till_pay_mixed)
        else -> type
    }

    /**
     * The receipt as monospaced lines of [width] characters (32 for 58 mm, 48
     * for 80 mm) — used for printing and for sharing as text.
     */
    fun lines(context: Context, r: ReceiptData, width: Int, clean: (String) -> String = Text::normalizeQuotes): List<ReceiptLine> {
        val out = ArrayList<ReceiptLine>()
        fun center(text: String, bold: Boolean = false, big: Boolean = false) {
            wrap(clean(text), if (big) width / 2 else width).forEach { out += ReceiptLine(it, center = true, bold = bold, big = big) }
        }
        fun pair(left: String, right: String, bold: Boolean = false) {
            out += ReceiptLine(twoCols(clean(left), clean(right), width), bold = bold)
        }
        fun divider() { out += ReceiptLine("-".repeat(width)) }

        if (r.shopName.isNotBlank()) center(r.shopName, bold = true, big = true)
        if (r.shopAddress.isNotBlank()) center(r.shopAddress)
        if (r.shopPhone.isNotBlank()) center(r.shopPhone)
        center("№${r.receiptNo}  ${r.createdAt}")
        if (r.cashier.isNotBlank()) center(context.getString(R.string.print_cashier) + ": " + r.cashier)
        divider()
        for (item in r.items) {
            wrap(clean(item.name), width).forEach { out += ReceiptLine(it) }
            pair("  " + Money.qty(item.qty) + " x " + Money.format(item.unitPrice, false), Money.format(item.subtotal, false))
        }
        divider()
        if (r.discount > 0) pair(context.getString(R.string.till_discount), "-" + Money.format(r.discount))
        pair(context.getString(R.string.till_total), Money.format(r.total), bold = true)
        if (r.payments.size > 1) {
            r.payments.forEach { (type, amount) -> pair(paymentLabel(context, type), Money.format(amount)) }
        } else {
            pair(context.getString(R.string.print_payment_type), paymentLabel(context, r.paymentType))
        }
        if (r.cashReceived > 0) {
            pair(context.getString(R.string.till_cash_received), Money.format(r.cashReceived))
            pair(context.getString(R.string.till_change), Money.format(r.change), bold = true)
        }
        if (r.debt > 0) {
            if (r.payments.size <= 1) {
                pair(context.getString(R.string.print_paid_now), Money.format(r.paidAmount))
                pair(context.getString(R.string.print_debt_amount), Money.format(r.debt))
            }
            r.customerName?.let { pair(context.getString(R.string.till_customer), it) }
        }
        if (r.refunded > 0) pair(context.getString(R.string.print_refunded), "-" + Money.format(r.refunded))
        out += ReceiptLine("")
        center(context.getString(R.string.print_not_fiscal))
        center(context.getString(R.string.print_thanks))
        return out
    }

    /** Plain text for sharing (messengers): the same receipt, 32 columns. */
    fun text(context: Context, r: ReceiptData): String = lines(context, r, 32).joinToString("\n") { it.text.trimEnd() }

    fun twoCols(left: String, right: String, width: Int): String {
        val space = width - right.length
        if (left.length + 1 <= space) return left + " ".repeat(space - left.length) + right
        // Too long: the label on its own line(s), the value right-aligned below.
        return wrap(left, width).joinToString("\n") + "\n" + right.padStart(width)
    }

    fun wrap(text: String, width: Int): List<String> {
        if (width <= 0) return listOf(text)
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            var w = word
            while (w.length > width) {
                if (line.isNotEmpty()) { out += line.toString(); line = StringBuilder() }
                out += w.take(width); w = w.drop(width)
            }
            if (line.isEmpty()) line.append(w)
            else if (line.length + 1 + w.length <= width) line.append(' ').append(w)
            else { out += line.toString(); line = StringBuilder(w) }
        }
        if (line.isNotEmpty() || out.isEmpty()) out += line.toString()
        return out
    }
}

/** One printed line; [big] = double width and height (uses half the columns). */
data class ReceiptLine(val text: String, val center: Boolean = false, val bold: Boolean = false, val big: Boolean = false)
