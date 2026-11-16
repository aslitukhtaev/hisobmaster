package uz.kassiron.app.domain

import uz.kassiron.app.App
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Ids
import uz.kassiron.app.core.Money
import uz.kassiron.app.core.Outbox
import uz.kassiron.app.core.Time

/** An expense the rules refuse; [code]: values_must_be_positive, amount_too_large, expense_date_invalid, expense_date_future. */
class ExpenseException(val code: String) : Exception(code)

/** Expenses (server: app/Models/Expense.php, ExpenseController.php). */
object ExpenseService {
    /** The server's MONEY_MAX. */
    const val MONEY_MAX = 1_000_000_000_000.0

    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")

    /** Expense categories (type 'expense'), by name. */
    fun categories(db: Db): List<String> =
        db.query("SELECT name FROM categories WHERE type = 'expense' ORDER BY name COLLATE NOCASE").map { it.str("name") }

    /** Category::findOrCreate for type 'expense' (case-insensitive name). Call inside db.write. */
    fun findOrCreateCategory(db: Db, name: String): String {
        db.queryOne("SELECT uuid FROM categories WHERE type = 'expense' AND LOWER(name) = LOWER(?)", name)?.let { return it.str("uuid") }
        val uuid = Ids.new()
        db.insert("categories", mapOf("uuid" to uuid, "name" to name, "type" to "expense", "created_at" to Time.now()))
        Outbox.upsert(db, "categories", uuid)
        return uuid
    }

    /** Validates like ExpenseController::validate; throws [ExpenseException]. */
    fun validate(amount: Double?, expenseDate: String) {
        if (amount == null || amount <= 0) throw ExpenseException("values_must_be_positive")
        if (amount > MONEY_MAX) throw ExpenseException("amount_too_large")
        if (!DATE.matches(expenseDate) || expenseDate < "2000-01-01" || runCatching { Time.addDays(expenseDate, 0) != expenseDate }.getOrDefault(true)) {
            throw ExpenseException("expense_date_invalid")
        }
        if (expenseDate > Time.today()) throw ExpenseException("expense_date_future")
    }

    /** Creates ([uuid] null) or updates an expense; returns its uuid. */
    fun save(uuid: String?, amount: Double?, category: String, description: String, expenseDate: String, userUuid: String): String {
        val date = expenseDate.trim().ifEmpty { Time.today() }
        validate(amount, date)
        val cat = category.trim()
        val desc = description.trim().takeIf { it.isNotEmpty() }
        return App.db.write {
            val categoryUuid = if (cat.isNotEmpty()) findOrCreateCategory(this, cat) else null
            val values = mapOf(
                "category_id" to categoryUuid,
                "amount" to Money.round2(amount!!),
                "description" to desc,
                "expense_date" to date,
            )
            val id = if (uuid == null || scalar("SELECT 1 FROM expenses WHERE uuid = ?", uuid) == null) {
                val newUuid = uuid ?: Ids.new()
                insert("expenses", values + mapOf("uuid" to newUuid, "created_by" to userUuid, "created_at" to Time.now()))
                newUuid
            } else {
                update("expenses", uuid, values)
                uuid
            }
            Outbox.upsert(this, "expenses", id)
            id
        }
    }

    fun delete(uuid: String) {
        App.db.write {
            if (scalar("SELECT 1 FROM expenses WHERE uuid = ?", uuid) != null) {
                delete("expenses", uuid)
                Outbox.delete(this, "expenses", uuid)
            }
        }
    }
}
