PRAGMA foreign_keys = ON;

-- Columns and objects added on top of these tables by app/Core/Migrator.php
-- rather than here: the `uuid` column, its unique index and the trigger that
-- fills it on every table in App\Core\SyncSchema::TABLES (see
-- Migrator::addSyncIdentity()), plus any column added after a table first
-- shipped (Migrator::apply()).

CREATE TABLE IF NOT EXISTS shops (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    owner_full_name TEXT NOT NULL,
    phone TEXT NOT NULL,
    address TEXT,
    currency TEXT NOT NULL DEFAULT 'sum',
    receipt_printer_width INTEGER NOT NULL DEFAULT 80,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER REFERENCES shops(id) ON DELETE CASCADE,
    role TEXT NOT NULL,
    full_name TEXT NOT NULL,
    phone TEXT,
    login TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    lang TEXT NOT NULL DEFAULT 'uz',
    permissions_json TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TEXT,
    -- NULL means this employee isn't eligible for commission at all (distinct
    -- from a rate of 0, which means "eligible, currently 0%"). A percentage
    -- of their own sales revenue for a report period — see
    -- Report::cashierLeaderboard(). Owner-only to set (see EmployeeController).
    commission_rate REAL,
    -- Set the first time this user dismisses the one-time dashboard
    -- onboarding banner (see DashboardController::dismissOnboarding()); NULL
    -- until then, so the banner shows exactly once per account.
    onboarding_seen_at TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_users_shop ON users(shop_id);

CREATE TABLE IF NOT EXISTS employee_invites (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    token TEXT NOT NULL UNIQUE,
    preset_permissions_json TEXT,
    created_by INTEGER NOT NULL REFERENCES users(id),
    expires_at TEXT NOT NULL,
    used_at TEXT,
    used_by INTEGER REFERENCES users(id),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_invites_shop ON employee_invites(shop_id);

-- One open-or-closed shift per row: clock_in is set the moment "Ishga
-- keldim" is pressed, clock_out stays NULL until "Ishni tugatdim" is
-- pressed. A row with clock_out IS NULL is that user's currently open
-- shift — at most one at a time, enforced server-side in
-- Attendance::clockIn() (see its docblock), not just by hiding the button.
-- Both timestamps are stored exactly like every other created_at in this
-- app (SQLite datetime('now'), i.e. UTC) and read back through
-- tashkent_day_bounds_utc() for any period-range query.
CREATE TABLE IF NOT EXISTS attendance (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    clock_in TEXT NOT NULL,
    clock_out TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_attendance_shop ON attendance(shop_id, clock_in);
CREATE INDEX IF NOT EXISTS idx_attendance_user_open ON attendance(user_id, clock_out);

CREATE TABLE IF NOT EXISTS categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'product',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_categories_shop ON categories(shop_id);

CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    unit TEXT NOT NULL DEFAULT 'dona',
    cost_price REAL NOT NULL DEFAULT 0,
    sell_price REAL NOT NULL DEFAULT 0,
    stock_qty REAL NOT NULL DEFAULT 0,
    barcode TEXT,
    status TEXT NOT NULL DEFAULT 'active',
    -- NULL means "no low-stock alert configured" for this product; a shop-wide
    -- fallback threshold can be set in `settings` (key
    -- 'low_stock_threshold_default') and is used only when this is NULL.
    low_stock_threshold REAL,
    -- NULL means this product isn't purchased in packs/boxes — set when a
    -- product is bought e.g. by the "karobka" of 24 but stock_qty/sales are
    -- tracked per individual unit ("dona"). Surfaced as a multiplier in the
    -- purchase-recording flow (see PurchaseController) and as a hint on the
    -- product form; not a general multi-unit conversion graph.
    pack_size INTEGER,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_products_shop ON products(shop_id);
CREATE INDEX IF NOT EXISTS idx_products_name ON products(shop_id, name);

-- A specific size/color/etc. variant of a product. Each variant tracks its
-- own stock_qty (and, optionally, its own barcode/sell_price/cost_price —
-- NULL falls back to the parent product's own value) while sharing the
-- parent's name/category. When a product has any active variant, sales must
-- pick a specific variant rather than the parent directly (see Sale::create()
-- and sale_items.variant_id below); the parent's own stock_qty then simply
-- goes unused for that product.
CREATE TABLE IF NOT EXISTS product_variants (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    variant_label TEXT NOT NULL,
    stock_qty REAL NOT NULL DEFAULT 0,
    barcode TEXT,
    sell_price REAL,
    cost_price REAL,
    status TEXT NOT NULL DEFAULT 'active',
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_variants_product ON product_variants(product_id);
CREATE INDEX IF NOT EXISTS idx_variants_shop ON product_variants(shop_id);

CREATE TABLE IF NOT EXISTS suppliers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    phone TEXT,
    address TEXT,
    note TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_suppliers_shop ON suppliers(shop_id);

-- A single restock event from a supplier. Line items live in purchase_items;
-- recording a purchase increases each line's product stock_qty (atomic
-- conditional UPDATE, mirroring Refund's stock restore) and, only when the
-- shop owner explicitly checks the "update cost price" box in the UI, also
-- updates that product's cost_price to the new purchase price.
CREATE TABLE IF NOT EXISTS purchases (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    supplier_id INTEGER REFERENCES suppliers(id) ON DELETE SET NULL,
    total_amount REAL NOT NULL DEFAULT 0,
    note TEXT,
    created_by INTEGER NOT NULL REFERENCES users(id),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_purchases_shop ON purchases(shop_id, created_at);
CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchases(supplier_id);

CREATE TABLE IF NOT EXISTS purchase_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    purchase_id INTEGER NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(id),
    qty REAL NOT NULL,
    unit_cost REAL NOT NULL,
    subtotal REAL NOT NULL,
    variant_id INTEGER REFERENCES product_variants(id),
    variant_label TEXT
);
CREATE INDEX IF NOT EXISTS idx_purchase_items_purchase ON purchase_items(purchase_id);
CREATE INDEX IF NOT EXISTS idx_purchase_items_product ON purchase_items(product_id);

CREATE TABLE IF NOT EXISTS customers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL,
    phone TEXT,
    note TEXT,
    -- NULL means no credit limit configured — the owner can carry this
    -- customer's debt balance as high as they like. When set, it's the max
    -- debt_transactions balance_after this customer should be allowed to
    -- reach; enforcement is a client-side confirm-to-override warning in the
    -- POS flow (see sale.js), never a hard server-side block (see
    -- SaleController::newForm()/Sale::create() comments).
    credit_limit REAL,
    -- A single next-payment-due calendar date for this customer's running
    -- debt balance (like expenses.expense_date — a plain hand-set date, not
    -- a UTC created_at, so it's compared directly rather than through
    -- tashkent_day_bounds_utc()). One running balance has no natural
    -- per-transaction due date, so this deliberately tracks just one
    -- "pay by" date for the customer as a whole rather than one per
    -- debt_transactions row.
    debt_due_date TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_customers_shop ON customers(shop_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(shop_id, phone);
-- Enforces one customer per (shop, phone) at the database level, so two
-- concurrent findOrCreate() calls for the same new phone number can no longer
-- both insert a duplicate row (Customer::findOrCreate() catches the resulting
-- constraint violation and re-selects the winner's row). NULL phones are
-- exempt (SQLite treats NULLs as distinct in a UNIQUE index), so walk-in
-- customers without a phone number are unaffected.
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_shop_phone_unique ON customers(shop_id, phone);

CREATE TABLE IF NOT EXISTS sales (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    cashier_id INTEGER NOT NULL REFERENCES users(id),
    customer_id INTEGER REFERENCES customers(id) ON DELETE SET NULL,
    total REAL NOT NULL DEFAULT 0,
    discount REAL NOT NULL DEFAULT 0,
    payment_type TEXT NOT NULL DEFAULT 'naqd',
    paid_amount REAL NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'completed',
    cash_received REAL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_sales_shop ON sales(shop_id);
CREATE INDEX IF NOT EXISTS idx_sales_created ON sales(shop_id, created_at);
CREATE INDEX IF NOT EXISTS idx_sales_customer ON sales(customer_id);

CREATE TABLE IF NOT EXISTS sale_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(id),
    product_name TEXT NOT NULL,
    qty REAL NOT NULL,
    unit_price REAL NOT NULL,
    cost_price_snapshot REAL NOT NULL,
    subtotal REAL NOT NULL,
    -- NULL for a plain (variant-less) sale line. product_id always stays the
    -- parent product (so Report.php's existing top-products/revenue queries,
    -- which group by product_id/product_name, keep working unchanged even for
    -- variant sales); variant_id/variant_label additionally record which
    -- specific variant was sold, and are what Refund::create() and stock
    -- restore look at to know whether to credit the variant's own stock_qty
    -- or the parent product's.
    variant_id INTEGER REFERENCES product_variants(id),
    variant_label TEXT
);
CREATE INDEX IF NOT EXISTS idx_sale_items_sale ON sale_items(sale_id);
CREATE INDEX IF NOT EXISTS idx_sale_items_product ON sale_items(product_id);

-- One sale's total can be split across several payment methods (naqd/karta/qarz)
-- at once. Every sale gets at least one row here (even a plain single-method
-- sale) going forward; sales.payment_type/paid_amount stay in sync as a
-- derived summary (single type name, or 'aralash' when mixed) purely for
-- backward compatibility with code that only reads those two columns.
CREATE TABLE IF NOT EXISTS sale_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    payment_type TEXT NOT NULL,
    amount REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sale_payments_sale ON sale_payments(sale_id);

CREATE TABLE IF NOT EXISTS expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    category_id INTEGER REFERENCES categories(id) ON DELETE SET NULL,
    amount REAL NOT NULL,
    description TEXT,
    expense_date TEXT NOT NULL,
    created_by INTEGER NOT NULL REFERENCES users(id),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_expenses_shop ON expenses(shop_id, expense_date);

CREATE TABLE IF NOT EXISTS debt_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    sale_id INTEGER REFERENCES sales(id) ON DELETE SET NULL,
    type TEXT NOT NULL,
    amount REAL NOT NULL,
    balance_after REAL NOT NULL,
    created_by INTEGER NOT NULL REFERENCES users(id),
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_debt_customer ON debt_transactions(customer_id, created_at);
CREATE INDEX IF NOT EXISTS idx_debt_shop ON debt_transactions(shop_id);

-- A full or partial return against a completed sale. One sale can have several
-- refunds over time (repeated partial returns), so both the money total and
-- the per-line-item quantities live here rather than mutating the sale itself.
CREATE TABLE IF NOT EXISTS refunds (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sale_id INTEGER NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    refunded_by INTEGER NOT NULL REFERENCES users(id),
    total_amount REAL NOT NULL,
    reason TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_refunds_sale ON refunds(sale_id);
CREATE INDEX IF NOT EXISTS idx_refunds_shop ON refunds(shop_id, created_at);

-- Per-line-item breakdown of a refund. Summing qty/amount for a given
-- sale_item_id across every row here (across every refund of that sale) gives
-- "how much of this line item has already been refunded", which is what caps
-- how much of it can still be returned.
CREATE TABLE IF NOT EXISTS refund_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    refund_id INTEGER NOT NULL REFERENCES refunds(id) ON DELETE CASCADE,
    sale_item_id INTEGER NOT NULL REFERENCES sale_items(id),
    qty REAL NOT NULL,
    amount REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_refund_items_refund ON refund_items(refund_id);
CREATE INDEX IF NOT EXISTS idx_refund_items_sale_item ON refund_items(sale_item_id);

-- Which payment method(s) a refund went back through, mirroring
-- sale_payments: the refund's total is split across naqd/karta/qarz in the
-- same proportion the original sale was paid (Refund::allocateByPayment()),
-- so a refund of a card or debt sale is never counted as cash leaving the
-- drawer. The qarz share is exactly what's taken off the customer's ledger.
CREATE TABLE IF NOT EXISTS refund_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    refund_id INTEGER NOT NULL REFERENCES refunds(id) ON DELETE CASCADE,
    payment_type TEXT NOT NULL,
    amount REAL NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_refund_payments_refund ON refund_payments(refund_id);

CREATE TABLE IF NOT EXISTS activity_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER REFERENCES shops(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    meta_json TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_activity_shop ON activity_log(shop_id, created_at);

CREATE TABLE IF NOT EXISTS settings (
    shop_id INTEGER NOT NULL REFERENCES shops(id) ON DELETE CASCADE,
    key TEXT NOT NULL,
    value TEXT,
    PRIMARY KEY (shop_id, key)
);
