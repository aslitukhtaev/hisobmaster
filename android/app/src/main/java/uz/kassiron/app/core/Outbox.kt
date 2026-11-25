package uz.kassiron.app.core

/**
 * Records a local change for the next push — the device-side equivalent of
 * the server's sync_changes triggers. Call it inside the same `db.write { }`
 * as the change itself, right after writing the row:
 *
 *   db.write {
 *       insert("sales", row)
 *       Outbox.upsert(this, "sales", uuid)
 *       exec("UPDATE products SET stock_qty = stock_qty - ? WHERE uuid = ?", qty, productUuid)
 *       Outbox.delta(this, "products", productUuid, "stock_qty", -qty)
 *   }
 *
 * Rules (the server enforces them, see SyncSchema):
 * - upsert: only for Schema tables with pushable = true (never "users").
 *   The row is read at push time, so edit the row first. For an edit, pass
 *   the columns it changed ([changed]): only those are sent, so this device's
 *   possibly stale copy of the others (an optom price just set on the website)
 *   never overwrites the server's. No columns = a new row, sent whole.
 * - delta: only for counter columns (stock_qty) — never write stock as a
 *   value through upsert; the push leaves counters out of upsert rows.
 * - delete: only for deletable tables (expenses).
 */
object Outbox {
    fun upsert(db: Db, table: String, uuid: String, cols: Collection<String>? = null) {
        val t = Schema.table(table)
        require(t.pushable) { "$table is not pushable" }
        if (cols != null && cols.isEmpty()) return // the edit changed nothing
        db.exec(
            "INSERT INTO sync_outbox (tbl, op, uuid, changed_at, cols) VALUES (?, 'upsert', ?, ?, ?)",
            table, uuid, Time.nowMillis(), cols?.joinToString(",", prefix = ","),
        )
    }

    /** The columns of [values] that differ from [existing]: what an edit really changed. */
    fun changed(existing: Row, values: Map<String, Any?>): List<String> =
        values.filter { (column, value) -> !same(existing[column], value) }.keys.toList()

    private fun same(a: Any?, b: Any?): Boolean = when {
        a == null || b == null -> a == null && b == null
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        a is Number || b is Number -> a.toString().toDoubleOrNull() == b.toString().toDoubleOrNull()
        else -> a.toString() == b.toString()
    }

    fun delta(db: Db, table: String, uuid: String, column: String, delta: Double) {
        require(column in Schema.table(table).counters) { "$table.$column is not a counter" }
        if (delta == 0.0) return
        db.exec(
            "INSERT INTO sync_outbox (tbl, op, uuid, col, delta, changed_at) VALUES (?, 'delta', ?, ?, ?, ?)",
            table, uuid, column, delta, Time.nowMillis(),
        )
    }

    fun delete(db: Db, table: String, uuid: String) {
        require(Schema.table(table).deletable) { "$table rows are never deleted" }
        db.exec("INSERT INTO sync_outbox (tbl, op, uuid, changed_at) VALUES (?, 'delete', ?, ?)", table, uuid, Time.nowMillis())
    }

    /** Sales not yet on the server. */
    fun pendingSales(db: Db): Long =
        db.scalarLong("SELECT COUNT(DISTINCT uuid) FROM sync_outbox WHERE tbl = 'sales'")

    fun pendingTotal(db: Db): Long = db.scalarLong("SELECT COUNT(*) FROM sync_outbox")
}
