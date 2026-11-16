package uz.kassiron.app.domain

import uz.kassiron.app.App
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Time
import java.util.Calendar
import kotlin.math.max

/**
 * Reports and the dashboard's figures (server: app/Models/Report.php,
 * Sale::todaysSummary, DebtTransaction::totalDebtByShop, Product::lowStock).
 * Periods are local dates "yyyy-MM-dd" (inclusive); created_at columns are
 * compared through Time.utcRange.
 */
object ReportService {

    const val SIGNED = "CASE WHEN type = 'qarz' THEN amount ELSE -amount END"

    enum class Preset { TODAY, YESTERDAY, WEEK, MONTH, CUSTOM }

    /** [from, to] of a preset (Report::resolvePeriod; the week starts on Monday). */
    fun period(preset: Preset): Pair<String, String> {
        val today = Time.today()
        return when (preset) {
            Preset.TODAY, Preset.CUSTOM -> today to today
            Preset.YESTERDAY -> Time.addDays(today, -1).let { it to it }
            Preset.WEEK -> {
                val c = Calendar.getInstance()
                val dow = c.get(Calendar.DAY_OF_WEEK) // 1 = Sunday
                val sinceMonday = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
                Time.addDays(today, -sinceMonday) to today
            }
            Preset.MONTH -> today.substring(0, 8) + "01" to today
        }
    }

    data class Summary(
        val salesCount: Long,
        val grossRevenue: Double,
        val revenue: Double,
        val refunds: Double,
        val discounts: Double,
        val cogs: Double,
        val expenses: Double,
        val netProfit: Double,
    ) {
        val grossProfit: Double get() = Money.round2(revenue - cogs)
    }

    fun expensesTotal(db: Db, from: String, to: String): Double =
        db.scalarDouble("SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE expense_date BETWEEN ? AND ?", from, to)

    /** Report::summary (+ discounts given in the period). */
    fun summary(db: Db, from: String, to: String): Summary {
        val (start, end) = Time.utcRange(from, to)
        val sales = db.queryOne(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue, COALESCE(SUM(discount), 0) AS discounts FROM sales WHERE status = 'completed' AND created_at >= ? AND created_at < ?",
            start, end,
        )
        val cogs = db.scalarDouble(
            "SELECT COALESCE(SUM(si.qty * si.cost_price_snapshot), 0) FROM sale_items si INNER JOIN sales s ON s.uuid = si.sale_id WHERE s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?",
            start, end,
        )
        val refundAmount = db.scalarDouble("SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE created_at >= ? AND created_at < ?", start, end)
        val refundCogs = db.scalarDouble(
            """SELECT COALESCE(SUM(ri.qty * si.cost_price_snapshot), 0) FROM refund_items ri
               INNER JOIN refunds r ON r.uuid = ri.refund_id INNER JOIN sale_items si ON si.uuid = ri.sale_item_id
               WHERE r.created_at >= ? AND r.created_at < ?""",
            start, end,
        )
        val expenses = expensesTotal(db, from, to)
        val gross = sales?.dbl("revenue") ?: 0.0
        val revenue = Money.round2(gross - refundAmount)
        val netCogs = Money.round2(cogs - refundCogs)
        return Summary(
            salesCount = sales?.long("cnt") ?: 0,
            grossRevenue = Money.round2(gross),
            revenue = revenue,
            refunds = Money.round2(refundAmount),
            discounts = Money.round2(sales?.dbl("discounts") ?: 0.0),
            cogs = netCogs,
            expenses = Money.round2(expenses),
            netProfit = Money.round2(revenue - netCogs - expenses),
        )
    }

    private fun emptyPayments() = linkedMapOf("naqd" to 0.0, "karta" to 0.0, "qr" to 0.0, "qarz" to 0.0)

    /** Refunds in a UTC window summed by the method they went back through (Refund::totalsByPayment). */
    fun refundsByPayment(db: Db, startUtc: String, endUtc: String): Map<String, Double> {
        val result = emptyPayments()
        db.query(
            "SELECT rp.payment_type, COALESCE(SUM(rp.amount), 0) AS total FROM refund_payments rp INNER JOIN refunds r ON r.uuid = rp.refund_id WHERE r.created_at >= ? AND r.created_at < ? GROUP BY rp.payment_type",
            startUtc, endUtc,
        ).forEach { r -> r.str("payment_type").let { if (it in result) result[it] = result.getValue(it) + r.dbl("total") } }
        return result
    }

    data class Payments(val net: Map<String, Double>, val sold: Map<String, Double>, val refunded: Map<String, Double>)

    /** Report::paymentBreakdown: sale_payments (legacy sales by payment_type) minus refunds by method. */
    fun paymentBreakdown(db: Db, from: String, to: String): Payments {
        val (start, end) = Time.utcRange(from, to)
        val sold = emptyPayments()
        db.query(
            "SELECT sp.payment_type, COALESCE(SUM(sp.amount), 0) AS total FROM sale_payments sp INNER JOIN sales s ON s.uuid = sp.sale_id WHERE s.status = 'completed' AND s.created_at >= ? AND s.created_at < ? GROUP BY sp.payment_type",
            start, end,
        ).forEach { r -> r.str("payment_type").let { if (it in sold) sold[it] = sold.getValue(it) + r.dbl("total") } }
        db.query(
            """SELECT s.payment_type, COALESCE(SUM(s.total), 0) AS total FROM sales s
               WHERE s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
                 AND NOT EXISTS (SELECT 1 FROM sale_payments sp WHERE sp.sale_id = s.uuid)
               GROUP BY s.payment_type""",
            start, end,
        ).forEach { r -> r.str("payment_type").let { if (it in sold) sold[it] = sold.getValue(it) + r.dbl("total") } }
        val refunded = refundsByPayment(db, start, end)
        val net = emptyPayments()
        for (k in net.keys.toList()) net[k] = Money.round2(sold.getValue(k) - refunded.getValue(k))
        return Payments(net, sold.mapValues { Money.round2(it.value) }, refunded.mapValues { Money.round2(it.value) })
    }

    data class TopProduct(val productUuid: String, val name: String, val qty: Double, val revenue: Double)

    /** Report::topProducts: net of the sale discount and of refunds issued in the period. */
    fun topProducts(db: Db, from: String, to: String, limit: Int = 10): List<TopProduct> {
        val (start, end) = Time.utcRange(from, to)
        data class Acc(var name: String, var qty: Double, var revenue: Double)
        val by = LinkedHashMap<String, Acc>()
        db.query(
            """SELECT si.product_id, MAX(si.product_name) AS product_name, SUM(si.qty) AS qty,
                      SUM(CASE WHEN (s.total + s.discount) > 0 THEN si.subtotal * s.total / (s.total + s.discount) ELSE 0 END) AS revenue
               FROM sale_items si INNER JOIN sales s ON s.uuid = si.sale_id
               WHERE s.status = 'completed' AND s.created_at >= ? AND s.created_at < ?
               GROUP BY si.product_id""",
            start, end,
        ).forEach { r -> by[r.str("product_id")] = Acc(r.str("product_name"), r.dbl("qty"), r.dbl("revenue")) }
        db.query(
            """SELECT si.product_id, MAX(si.product_name) AS product_name, SUM(ri.qty) AS qty, SUM(ri.amount) AS amount
               FROM refund_items ri INNER JOIN refunds r ON r.uuid = ri.refund_id INNER JOIN sale_items si ON si.uuid = ri.sale_item_id
               WHERE r.created_at >= ? AND r.created_at < ?
               GROUP BY si.product_id""",
            start, end,
        ).forEach { r ->
            val acc = by.getOrPut(r.str("product_id")) { Acc(r.str("product_name"), 0.0, 0.0) }
            acc.qty -= r.dbl("qty")
            acc.revenue -= r.dbl("amount")
        }
        return by.filterValues { it.qty > 0.0001 }
            .map { (id, a) -> TopProduct(id, a.name, Money.round2(a.qty), Money.round2(a.revenue)) }
            .sortedWith(compareByDescending<TopProduct> { it.qty }.thenByDescending { it.revenue })
            .take(limit)
    }

    data class CashierRow(
        val userUuid: String,
        val name: String,
        val role: String,
        val salesCount: Long,
        val revenue: Double,
        val cogs: Double,
        val commissionRate: Double?,
        val commission: Double?,
    ) {
        val netContribution: Double get() = Money.round2(revenue - cogs)
    }

    /** Report::cashierLeaderboard. */
    fun cashierLeaderboard(db: Db, from: String, to: String): List<CashierRow> {
        val (start, end) = Time.utcRange(from, to)
        val sales = db.query(
            "SELECT cashier_id, COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue FROM sales WHERE status = 'completed' AND created_at >= ? AND created_at < ? GROUP BY cashier_id",
            start, end,
        ).associateBy { it.str("cashier_id") }
        val cogs = db.query(
            "SELECT s.cashier_id, COALESCE(SUM(si.qty * si.cost_price_snapshot), 0) AS cogs FROM sale_items si INNER JOIN sales s ON s.uuid = si.sale_id WHERE s.status = 'completed' AND s.created_at >= ? AND s.created_at < ? GROUP BY s.cashier_id",
            start, end,
        ).associate { it.str("cashier_id") to it.dbl("cogs") }
        val refunds = db.query(
            "SELECT s.cashier_id, COALESCE(SUM(r.total_amount), 0) AS total FROM refunds r INNER JOIN sales s ON s.uuid = r.sale_id WHERE r.created_at >= ? AND r.created_at < ? GROUP BY s.cashier_id",
            start, end,
        ).associate { it.str("cashier_id") to it.dbl("total") }
        val refundCogs = db.query(
            """SELECT s.cashier_id, COALESCE(SUM(ri.qty * si.cost_price_snapshot), 0) AS cogs FROM refund_items ri
               INNER JOIN refunds r ON r.uuid = ri.refund_id INNER JOIN sale_items si ON si.uuid = ri.sale_item_id
               INNER JOIN sales s ON s.uuid = r.sale_id
               WHERE r.created_at >= ? AND r.created_at < ? GROUP BY s.cashier_id""",
            start, end,
        ).associate { it.str("cashier_id") to it.dbl("cogs") }
        return db.query("SELECT uuid, full_name, role, commission_rate FROM users WHERE role IN ('owner', 'employee') ORDER BY full_name")
            .map { u ->
                val id = u.str("uuid")
                val revenue = Money.round2((sales[id]?.dbl("revenue") ?: 0.0) - (refunds[id] ?: 0.0))
                val c = Money.round2((cogs[id] ?: 0.0) - (refundCogs[id] ?: 0.0))
                val rate = u.dblOrNull("commission_rate")
                CashierRow(id, u.str("full_name"), u.str("role"), sales[id]?.long("cnt") ?: 0, revenue, c, rate,
                    rate?.let { max(0.0, Money.round2(revenue * it / 100)) })
            }
            .sortedByDescending { it.revenue }
    }

    data class DayTotals(val count: Long, val revenue: Double)

    /** Sale::todaysSummary: today's completed sales, revenue net of today's refunds. */
    fun today(db: Db): DayTotals {
        val today = Time.today()
        val (start, end) = Time.utcRange(today, today)
        val row = db.queryOne(
            "SELECT COUNT(*) AS cnt, COALESCE(SUM(total), 0) AS revenue FROM sales WHERE created_at >= ? AND created_at < ? AND status = 'completed'",
            start, end,
        )
        val refunded = db.scalarDouble("SELECT COALESCE(SUM(total_amount), 0) FROM refunds WHERE created_at >= ? AND created_at < ?", start, end)
        return DayTotals(row?.long("cnt") ?: 0, Money.round2((row?.dbl("revenue") ?: 0.0) - refunded))
    }

    /** DebtTransaction::totalDebtByShop: per customer, so an overpayment never lowers others' debt. */
    fun totalDebt(db: Db): Double = Money.round2(
        db.scalarDouble("SELECT COALESCE(SUM(CASE WHEN b > 0 THEN b ELSE 0 END), 0) FROM (SELECT ROUND(SUM($SIGNED), 2) AS b FROM debt_transactions GROUP BY customer_id)")
    )

    fun debtorsCount(db: Db): Long =
        db.scalarLong("SELECT COUNT(*) FROM (SELECT ROUND(SUM($SIGNED), 2) AS b FROM debt_transactions GROUP BY customer_id) WHERE b > 0")

    fun activeProducts(db: Db): Long = db.scalarLong("SELECT COUNT(*) FROM products WHERE status = 'active'")

    data class LowStock(val uuid: String, val name: String, val unit: String, val stock: Double, val threshold: Double)

    private const val EFFECTIVE_STOCK = """CASE WHEN EXISTS (SELECT 1 FROM product_variants pv WHERE pv.product_id = p.uuid AND pv.status = 'active')
             THEN (SELECT COALESCE(SUM(pv.stock_qty), 0) FROM product_variants pv WHERE pv.product_id = p.uuid AND pv.status = 'active')
             ELSE p.stock_qty END"""

    /** Product::lowStock with the shop's default threshold (settings.low_stock_threshold_default). */
    fun lowStock(db: Db): List<LowStock> {
        val def = App.kv.shopSetting("low_stock_threshold_default")?.trim()?.toDoubleOrNull()
        val orDefault = if (def != null) " OR (t.low_stock_threshold IS NULL AND t.effective_stock <= CAST(? AS REAL))" else ""
        val args: Array<Any?> = if (def != null) arrayOf(def) else emptyArray()
        return db.query(
            """SELECT * FROM (SELECT p.uuid, p.name, p.unit, p.low_stock_threshold, $EFFECTIVE_STOCK AS effective_stock
                              FROM products p WHERE p.status = 'active') t
               WHERE (t.low_stock_threshold IS NOT NULL AND t.effective_stock <= CAST(t.low_stock_threshold AS REAL))$orDefault
               ORDER BY t.effective_stock ASC, t.name""",
            *args,
        ).map { LowStock(it.str("uuid"), it.str("name"), it.str("unit"), it.dbl("effective_stock"), it.dblOrNull("low_stock_threshold") ?: def ?: 0.0) }
    }
}

/** Clock in / out (server: app/Models/Attendance.php). Times are UTC like created_at. */
object Attendance {
    data class Shift(val uuid: String, val userUuid: String, val userName: String, val clockIn: String, val clockOut: String?)

    fun openShift(db: Db, userUuid: String): Shift? = db.queryOne(
        "SELECT a.*, u.full_name AS user_name FROM attendance a LEFT JOIN users u ON u.uuid = a.user_id WHERE a.user_id = ? AND a.clock_out IS NULL ORDER BY a.clock_in DESC LIMIT 1",
        userUuid,
    )?.let { shift(it) }

    private fun shift(r: uz.kassiron.app.core.Row) =
        Shift(r.str("uuid"), r.str("user_id"), r.str("user_name"), r.str("clock_in"), r.strOrNull("clock_out")?.takeIf { it.isNotBlank() })

    /** Opens a shift; false when one is already open. */
    fun clockIn(userUuid: String): Boolean = App.db.write {
        if (scalar("SELECT 1 FROM attendance WHERE user_id = ? AND clock_out IS NULL LIMIT 1", userUuid) != null) return@write false
        val uuid = Ids.new()
        val now = Time.now()
        insert("attendance", mapOf("uuid" to uuid, "user_id" to userUuid, "clock_in" to now, "clock_out" to null, "created_at" to now))
        Outbox.upsert(this, "attendance", uuid)
        true
    }

    /** Closes the open shift(s); false when there was none. */
    fun clockOut(userUuid: String): Boolean = App.db.write {
        val open = query("SELECT uuid FROM attendance WHERE user_id = ? AND clock_out IS NULL", userUuid).map { it.str("uuid") }
        if (open.isEmpty()) return@write false
        val now = Time.now()
        for (uuid in open) {
            update("attendance", uuid, mapOf("clock_out" to now))
            Outbox.upsert(this, "attendance", uuid)
        }
        true
    }

    /** Shifts that started within the local days from..to, newest first (Attendance::historyByShop). */
    fun history(db: Db, from: String, to: String, userUuid: String? = null): List<Shift> {
        val (start, end) = Time.utcRange(from, to)
        val byUser = if (userUuid != null) " AND a.user_id = ?" else ""
        val args: Array<Any?> = if (userUuid != null) arrayOf(start, end, userUuid) else arrayOf(start, end)
        return db.query(
            """SELECT a.*, COALESCE(u.full_name, '') AS user_name FROM attendance a LEFT JOIN users u ON u.uuid = a.user_id
               WHERE a.clock_in >= ? AND a.clock_in < ?$byUser ORDER BY a.clock_in DESC""",
            *args,
        ).map { shift(it) }
    }

    fun openCount(db: Db): Long = db.scalarLong("SELECT COUNT(*) FROM attendance WHERE clock_out IS NULL")

    /** Minutes between two UTC times (to = now when null). */
    fun minutes(clockIn: String, clockOut: String?): Long {
        val a = Time.parseUtc(clockIn) ?: return 0
        val b = clockOut?.let { Time.parseUtc(it) } ?: java.util.Date()
        return max(0L, (b.time - a.time) / 60000)
    }
}
