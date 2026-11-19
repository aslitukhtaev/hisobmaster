package uz.kassiron.app.core

/**
 * The local copy of the shop's data — the server's synced tables
 * (app/Core/SyncSchema.php on the server) with one difference: rows are
 * identified by their `uuid` only (the primary key here), and every foreign
 * key column holds the uuid of the row it points to, exactly as the sync wire
 * format carries it. There are no integer ids and no shop_id on this device.
 *
 * Columns are the server's data columns (everything but id, shop_id and the
 * server's local bookkeeping). A column the server adds later is simply not
 * stored here until this list learns it: pull ignores unknown columns and a
 * push only sends the columns it has, which the server leaves the others of.
 */
object Schema {

    data class Column(val name: String, val type: String, val notNull: Boolean = false, val default: String? = null)

    data class Table(
        val name: String,
        val columns: List<Column>,
        /** column -> referenced table (values are uuids). */
        val foreignKeys: Map<String, String> = emptyMap(),
        /** Synced as differences ("-2"), never as values. */
        val counters: List<String> = emptyList(),
        val pushable: Boolean = true,
        val deletable: Boolean = false,
        val indexes: List<String> = emptyList(),
    ) {
        val columnNames: List<String> get() = columns.map { it.name }
    }

    private fun t(name: String, notNull: Boolean = false, default: String? = null) = Column(name, "TEXT", notNull, default)
    private fun r(name: String, notNull: Boolean = false, default: String? = null) = Column(name, "REAL", notNull, default)
    private fun i(name: String, notNull: Boolean = false, default: String? = null) = Column(name, "INTEGER", notNull, default)

    /** In dependency order: a row's parents always come before it (same as the server). */
    val TABLES: List<Table> = listOf(
        Table(
            "users",
            listOf(
                t("role", true), t("full_name", true), t("phone"), t("login", true), t("password_hash", true),
                t("lang", true, "'uz'"), t("permissions_json"), t("status", true, "'active'"), r("commission_rate"),
                t("created_at", true), i("password_set", true, "1"),
            ),
            pushable = false,
        ),
        // Deletable: removing or merging a category (the server first takes it off its products and expenses).
        Table("categories", listOf(t("name", true), t("type", true, "'product'"), t("created_at", true)), deletable = true),
        Table("suppliers", listOf(t("name", true), t("phone"), t("address"), t("note"), t("created_at", true))),
        Table(
            "customers",
            listOf(t("full_name", true), t("phone"), t("note"), r("credit_limit"), t("debt_due_date"), t("created_at", true)),
            indexes = listOf("phone", "full_name COLLATE NOCASE"),
        ),
        Table(
            "products",
            listOf(
                t("category_id"), t("name", true), t("unit", true, "'dona'"), r("cost_price", true, "0"),
                r("sell_price", true, "0"), r("stock_qty", true, "0"), t("barcode"), t("status", true, "'active'"),
                r("low_stock_threshold"), i("pack_size"), t("mxik"), t("package_code"), r("vat_rate"),
                t("created_at", true), t("updated_at", true),
                // The optom (wholesale) price; null = none. Used only where the shop turned wholesale on.
                r("wholesale_price"),
            ),
            foreignKeys = mapOf("category_id" to "categories"),
            counters = listOf("stock_qty"),
            indexes = listOf("barcode", "name COLLATE NOCASE", "status"),
        ),
        Table(
            "product_variants",
            listOf(
                t("product_id", true), t("variant_label", true), r("stock_qty", true, "0"), t("barcode"),
                r("sell_price"), r("cost_price"), t("status", true, "'active'"), t("created_at", true),
                r("wholesale_price"),
            ),
            foreignKeys = mapOf("product_id" to "products"),
            counters = listOf("stock_qty"),
            indexes = listOf("product_id", "barcode"),
        ),
        Table(
            "purchases",
            listOf(t("supplier_id"), r("total_amount", true, "0"), t("note"), t("created_by", true), t("created_at", true)),
            foreignKeys = mapOf("supplier_id" to "suppliers", "created_by" to "users"),
            indexes = listOf("created_at"),
        ),
        Table(
            "purchase_items",
            listOf(
                t("purchase_id", true), t("product_id", true), r("qty", true), r("unit_cost", true), r("subtotal", true),
                t("variant_id"), t("variant_label"),
            ),
            foreignKeys = mapOf("purchase_id" to "purchases", "product_id" to "products", "variant_id" to "product_variants"),
            indexes = listOf("purchase_id"),
        ),
        Table(
            "sales",
            listOf(
                t("cashier_id", true), t("customer_id"), r("total", true, "0"), r("discount", true, "0"),
                t("payment_type", true, "'naqd'"), r("paid_amount", true, "0"), t("status", true, "'completed'"),
                r("cash_received"), t("created_at", true), t("receipt_no"),
                // 'retail' (chakana) or 'wholesale' (optom): which of the two prices the sale used.
                t("price_type", true, "'retail'"),
            ),
            foreignKeys = mapOf("cashier_id" to "users", "customer_id" to "customers"),
            indexes = listOf("created_at", "customer_id"),
        ),
        Table(
            "sale_items",
            listOf(
                t("sale_id", true), t("product_id", true), t("product_name", true), r("qty", true), r("unit_price", true),
                r("cost_price_snapshot", true), r("subtotal", true), t("variant_id"), t("variant_label"),
            ),
            foreignKeys = mapOf("sale_id" to "sales", "product_id" to "products", "variant_id" to "product_variants"),
            indexes = listOf("sale_id", "product_id"),
        ),
        Table(
            "sale_payments",
            listOf(t("sale_id", true), t("payment_type", true), r("amount", true)),
            foreignKeys = mapOf("sale_id" to "sales"),
            indexes = listOf("sale_id"),
        ),
        Table(
            "refunds",
            listOf(t("sale_id", true), t("refunded_by", true), r("total_amount", true), t("reason"), t("created_at", true)),
            foreignKeys = mapOf("sale_id" to "sales", "refunded_by" to "users"),
            indexes = listOf("sale_id", "created_at"),
        ),
        Table(
            "refund_items",
            listOf(t("refund_id", true), t("sale_item_id", true), r("qty", true), r("amount", true)),
            foreignKeys = mapOf("refund_id" to "refunds", "sale_item_id" to "sale_items"),
            indexes = listOf("refund_id", "sale_item_id"),
        ),
        Table(
            "refund_payments",
            listOf(t("refund_id", true), t("payment_type", true), r("amount", true)),
            foreignKeys = mapOf("refund_id" to "refunds"),
            indexes = listOf("refund_id"),
        ),
        Table(
            "debt_transactions",
            listOf(
                t("customer_id", true), t("sale_id"), t("type", true), r("amount", true), r("balance_after", true),
                t("created_by", true), t("created_at", true),
            ),
            foreignKeys = mapOf("customer_id" to "customers", "sale_id" to "sales", "created_by" to "users"),
            indexes = listOf("customer_id", "created_at"),
        ),
        Table(
            "expenses",
            listOf(
                t("category_id"), r("amount", true), t("description"), t("expense_date", true), t("created_by", true),
                t("created_at", true),
            ),
            foreignKeys = mapOf("category_id" to "categories", "created_by" to "users"),
            deletable = true,
            indexes = listOf("expense_date"),
        ),
        Table(
            "attendance",
            listOf(t("user_id", true), t("clock_in", true), t("clock_out"), t("created_at", true)),
            foreignKeys = mapOf("user_id" to "users"),
            indexes = listOf("user_id"),
        ),
        Table(
            "activity_log",
            listOf(t("user_id"), t("action", true), t("meta_json"), t("created_at", true)),
            foreignKeys = mapOf("user_id" to "users"),
        ),
    )

    private val byName = TABLES.associateBy { it.name }

    fun table(name: String): Table = byName[name] ?: error("unknown table $name")

    fun has(name: String): Boolean = byName.containsKey(name)

    /** CREATE statements for the whole local database (synced tables + bookkeeping). */
    fun createStatements(): List<String> {
        val out = mutableListOf<String>()
        for (table in TABLES) {
            val cols = table.columns.joinToString(", ") { c ->
                buildString {
                    append(c.name).append(' ').append(c.type)
                    if (c.notNull) append(" NOT NULL")
                    if (c.default != null) append(" DEFAULT ").append(c.default)
                }
            }
            out += "CREATE TABLE IF NOT EXISTS ${table.name} (uuid TEXT PRIMARY KEY NOT NULL, $cols)"
            table.indexes.forEachIndexed { n, expr ->
                out += "CREATE INDEX IF NOT EXISTS idx_${table.name}_$n ON ${table.name}($expr)"
            }
        }
        // Local changes waiting to be pushed, in order. seq is what the
        // server de-duplicates on (devices.last_push_seq), so it only grows.
        out += """CREATE TABLE IF NOT EXISTS sync_outbox (
            seq INTEGER PRIMARY KEY AUTOINCREMENT,
            tbl TEXT NOT NULL,
            op TEXT NOT NULL,
            uuid TEXT NOT NULL,
            col TEXT,
            delta REAL,
            changed_at TEXT NOT NULL
        )"""
        out += "CREATE INDEX IF NOT EXISTS idx_sync_outbox_row ON sync_outbox(tbl, uuid)"
        // Device state: credentials, cursor, shop info, settings, preferences.
        out += "CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY NOT NULL, value TEXT)"
        return out
    }
}
