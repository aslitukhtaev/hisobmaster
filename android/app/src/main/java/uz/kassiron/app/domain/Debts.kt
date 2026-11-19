package uz.kassiron.app.domain

import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Time

/**
 * The debt ledger (server: app/Models/DebtTransaction.php). Shared by the
 * till (a sale on credit) and the customers screens (payments, old debts).
 * Call inside db.write { }.
 */
object Debts {
    const val QARZ = "qarz"     // debt added
    const val TOLOV = "tolov"   // debt paid back
    const val REFUND = "refund" // a refunded credit sale takes its debt off

    /** A customer's balance: what they owe now (sum of qarz minus payments). */
    fun balance(db: Db, customerUuid: String): Double = Money.round2(
        db.scalarDouble(
            "SELECT COALESCE(SUM(CASE WHEN type = 'qarz' THEN amount ELSE -amount END), 0) FROM debt_transactions WHERE customer_id = ?",
            customerUuid,
        )
    )

    /** Records a ledger entry; returns its uuid. [type] is [QARZ], [TOLOV] or [REFUND]. */
    fun record(db: Db, customerUuid: String, saleUuid: String?, type: String, amount: Double, userUuid: String): String {
        require(type == QARZ || type == TOLOV || type == REFUND)
        require(amount > 0)
        val current = balance(db, customerUuid)
        val uuid = Ids.new()
        db.insert(
            "debt_transactions",
            mapOf(
                "uuid" to uuid,
                "customer_id" to customerUuid,
                "sale_id" to saleUuid,
                "type" to type,
                "amount" to Money.round2(amount),
                "balance_after" to Money.round2(if (type == QARZ) current + amount else current - amount),
                "created_by" to userUuid,
                "created_at" to Time.now(),
            ),
        )
        Outbox.upsert(db, "debt_transactions", uuid)
        return uuid
    }
}

object Customers {
    /** "+998 90 123-45-67", "901234567" -> "+998901234567"; null when it isn't a phone number. Mirrors normalize_phone(). */
    fun normalizePhone(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val compact = trimmed.replace(Regex("""[\s\-.()]"""), "")
        if (!Regex("""^\+?\d+$""").matches(compact)) return null
        val digits = compact.trimStart('+')
        return when {
            digits.length == 9 -> "+998$digits"
            digits.length == 12 && digits.startsWith("998") -> "+$digits"
            compact.startsWith("+") && digits.length in 10..15 -> "+$digits"
            else -> null
        }
    }

    /** Creates a customer; returns its uuid. Call inside db.write { }. */
    fun create(db: Db, fullName: String, phone: String?, note: String? = null, uuid: String = Ids.new()): String {
        db.insert(
            "customers",
            mapOf(
                "uuid" to uuid,
                "full_name" to fullName.trim(),
                "phone" to normalizePhone(phone),
                "note" to note,
                "created_at" to Time.now(),
            ),
        )
        Outbox.upsert(db, "customers", uuid)
        return uuid
    }

    /** The customer with this phone (last 9 digits match), or a new one. Call inside db.write { }. */
    fun findOrCreate(db: Db, fullName: String, phone: String?): String {
        val normalized = normalizePhone(phone)
        if (normalized != null) {
            val last9 = normalized.takeLast(9)
            db.query("SELECT uuid, phone FROM customers WHERE phone IS NOT NULL").firstOrNull {
                it.str("phone").filter(Char::isDigit).takeLast(9) == last9
            }?.let { return it.str("uuid") }
        }
        return create(db, fullName.ifBlank { normalized ?: "?" }, normalized)
    }
}
