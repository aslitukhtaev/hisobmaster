package uz.kassiron.app.ui.customers

import uz.kassiron.app.App
import uz.kassiron.app.R
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.domain.Customers
import uz.kassiron.app.domain.Debts
import uz.kassiron.app.domain.ExpenseService
import uz.kassiron.app.core.Ids

/**
 * Customer writes (server: CustomerController.php, QuickEntryController::debtsStore).
 * Every function runs one db.write and returns an error string resource or null.
 */
object CustomerActions {

    /** CustomerController::recordPayment: 0 < amount <= current balance. */
    fun recordPayment(customerUuid: String, amount: Double?, userUuid: String): Int? {
        val err = App.db.write<Int?> {
            if (amount == null || amount <= 0) return@write R.string.cust_err_amount
            if (amount > ExpenseService.MONEY_MAX) return@write R.string.cust_err_too_large
            val balance = Debts.balance(this, customerUuid)
            if (Money.round2(amount) > balance) return@write R.string.cust_err_exceeds
            Debts.record(this, customerUuid, null, Debts.TOLOV, Money.round2(amount), userUuid)
            null
        }
        if (err == null) App.sync.requestSync()
        return err
    }

    /** An old debt for this customer (a 'qarz' entry without a sale, like quick-debts). */
    fun addDebt(customerUuid: String, amount: Double?, userUuid: String): Int? {
        if (amount == null || amount <= 0) return R.string.cust_err_amount
        if (amount > ExpenseService.MONEY_MAX) return R.string.cust_err_too_large
        App.db.write { Debts.record(this, customerUuid, null, Debts.QARZ, Money.round2(amount), userUuid) }
        App.sync.requestSync()
        return null
    }

    /** "" for an empty phone, null for an invalid one, else the normalized number (helpers.php normalize_phone). */
    fun normalize(raw: String): String? = if (raw.isBlank()) "" else Customers.normalizePhone(raw)

    /** Another customer that already has this phone (last 9 digits), as the server's UNIQUE(shop_id, phone). */
    fun phoneOwner(db: Db, phone: String, exceptUuid: String?): String? {
        val last9 = phone.filter(Char::isDigit).takeLast(9)
        if (last9.isEmpty()) return null
        return db.query("SELECT uuid, full_name, phone FROM customers WHERE phone LIKE ?", "%$last9")
            .firstOrNull { it.str("uuid") != exceptUuid && it.str("phone").filter(Char::isDigit).takeLast(9) == last9 }
            ?.str("full_name")
    }

    sealed class SaveResult {
        data class Ok(val uuid: String) : SaveResult()
        data class Error(val res: Int, val arg: String? = null) : SaveResult()
    }

    /**
     * CustomerController::store / update. [uuid] null = new customer, whose id
     * is [draftUuid] when given: the same form saved twice stays one customer.
     */
    fun save(uuid: String?, fullName: String, phoneRaw: String, note: String, creditLimitRaw: String, dueDate: String?, draftUuid: String? = null): SaveResult {
        val name = fullName.trim()
        if (name.isEmpty()) return SaveResult.Error(R.string.cust_err_name)
        val phone = normalize(phoneRaw) ?: return SaveResult.Error(R.string.cust_err_phone)
        val creditLimit: Double? = if (creditLimitRaw.isBlank()) null else {
            val v = Money.parse(creditLimitRaw)
            if (v == null || v < 0 || v > ExpenseService.MONEY_MAX) return SaveResult.Error(R.string.cust_err_limit)
            Money.round2(v)
        }
        val result = App.db.write<SaveResult> {
            if (phone.isNotEmpty()) phoneOwner(this, phone, uuid ?: draftUuid)?.let { return@write SaveResult.Error(R.string.cust_err_phone_taken, it) }
            val id = when {
                uuid != null -> uuid
                draftUuid != null && scalar("SELECT 1 FROM customers WHERE uuid = ?", draftUuid) != null -> draftUuid
                else -> Customers.create(this, name, phone.ifEmpty { null }, note.trim().ifEmpty { null }, draftUuid ?: Ids.new())
            }
            update(
                "customers", id,
                mapOf(
                    "full_name" to name,
                    "phone" to phone.ifEmpty { null },
                    "note" to note.trim().ifEmpty { null },
                    "credit_limit" to creditLimit,
                    "debt_due_date" to dueDate?.ifBlank { null },
                ),
            )
            Outbox.upsert(this, "customers", id)
            SaveResult.Ok(id)
        }
        if (result is SaveResult.Ok) App.sync.requestSync()
        return result
    }
}
