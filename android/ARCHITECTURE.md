# KassirON Android — architecture

A native Android app (Kotlin, Jetpack Compose, Material 3) for shop owners
and cashiers. **It works fully offline and must feel instant**: every screen
reads the local SQLite database; the network is only used by background sync.

The website (PHP, `app/`) is the source of truth for business rules. When in
doubt, read the PHP model named in each section and do exactly what it does.

## Package layout and ownership

```
uz.kassiron.app
  KassirApp.kt, MainActivity.kt      scaffold (shared — do not change without need)
  core/   Db, Schema, Outbox, Kv, Session, Time, Money, Units   scaffold (shared)
  domain/Debts.kt                    scaffold (shared: Debts.record/balance, Customers.create/findOrCreate)
  ui/AppNav.kt (Routes)              scaffold (shared)
  ui/common/Common.kt                scaffold (shared helpers: dbState, io, AppTopBar, SearchField, AmountField, BigButton, EmptyState, ConfirmDialog)
  ui/theme/Theme.kt                  scaffold (shared)

  sync/**, ui/auth/**, ui/settings/SettingsScreens.kt          → SYNC agent
  domain/SaleService.kt, ui/till/**, ui/common/Scanner.kt,
    print/**, ui/settings/PrinterScreen.kt                     → TILL agent
  domain/ProductService.kt, domain/PurchaseService.kt,
    ui/products/**                                             → PRODUCTS agent
  domain/RefundService.kt, domain/ExpenseService.kt,
    domain/ReportService.kt, ui/customers/**, ui/sales/**,
    ui/expenses/**, ui/reports/**                              → BACKOFFICE agent
```

Each agent edits **only its own files**. Stubs exist for every screen so the
app always compiles; replace a stub keeping its function signature (AppNav
calls it). Need something from another area? Use its public contract
(below); don't edit its files. If a shared file truly needs a change, make
the smallest additive change and say so in your report.

Strings: Uzbek (Latin, default `res/values/`) and Russian (`res/values-ru/`),
in **your own** files `strings_<area>.xml` (e.g. `strings_till.xml`), keys
prefixed with your area (`till_…`). No hard-coded user-visible text. Use the
real Uzbek apostrophe forms as the website does (o‘, g‘ — or plain ' escaped
as \').

## Data model

`core/Schema.kt` mirrors the server's synced tables (`app/Core/SyncSchema.php`,
`database/migrations/schema.sql`) with two differences:

* the primary key is `uuid` (32 hex chars, `Ids.new()`); there are no
  integer ids and no shop_id;
* every foreign key column holds the **uuid** of the row it points to
  (`sales.customer_id` = a customers.uuid, `sale_items.product_id` = a
  products.uuid, `created_by` / `cashier_id` = a users.uuid).

Times: `created_at` is UTC `yyyy-MM-dd HH:mm:ss` (`Time.now()`); show with
`Time.local()`. "Today" / date filters: local dates, converted with
`Time.utcRange(from, to)` for created_at comparisons. `expense_date` is a
local date `yyyy-MM-dd`.

Money: REAL, rounded to 2 decimals (`Money.round2`), shown with
`Money.format()` ("15 000 so'm"); parse user input with `Money.parse()`.
Quantities: `Money.qty()`; whole numbers only unless `Units.allowsFraction(unit)`.

## Writing data: always db.write + Outbox

Every change to shop data happens inside `App.db.write { … }` and records
itself in the outbox in the same transaction (see `core/Outbox.kt`):

* `Outbox.upsert(db, table, uuid)` after inserting/updating a row (not for
  `users` — users are managed on the website only);
* stock: `UPDATE products SET stock_qty = stock_qty - ? WHERE uuid = ?` then
  `Outbox.delta(db, "products", uuid, "stock_qty", -qty)` (same for
  `product_variants`). **Never** change stock_qty any other way, never send it
  as a value;
* `Outbox.delete(db, "expenses", uuid)` — expenses are the only deletable rows.

`db.write` bumps `App.db.changes`; screens using `dbState { }` refresh by
themselves. After a user action that changed data, call
`App.sync.requestSync()`.

Never run database work on the main thread: `dbState { }` for reads in
composables, `scope.launch { io { … } }` for writes from click handlers.

## Permissions

`App.session.current()` is the signed-in `User`. Check `user.can(x)` with the
website's permissions: `sales`, `discount`, `products`, `prices`,
`customers`, `expenses`, `reports` (the owner can do everything). Hide what
the user may not do (e.g. cost prices and editing prices need `prices`; a
discount needs `discount`).

## Contracts between areas

* `App.sync` (`sync/SyncManager.kt`): `state` (SyncState), `requestSync()`,
  `activate(login, password, name)`, `activateWithCode(code, name)`,
  `deactivate()`. **SYNC agent implements; signatures are fixed.**
* `BarcodeScannerDialog(onResult, onDismiss, continuous)` in
  `ui/common/Scanner.kt` — **TILL agent implements**; products/purchases use
  it for barcode fields.
* `Debts` / `Customers` in `domain/Debts.kt` (scaffold, ready).
* Routes in `ui/AppNav.kt`: e.g. the till opens
  `Routes.productEdit("new", barcode)` for an unknown scanned code.

## Sync protocol (SYNC agent) — server: app/Sync/SyncService.php, ApiController.php

Mirror the desktop client `app/Desktop/DesktopSync.php` exactly.

* `POST {SERVER}/api/device/activate` JSON `{login, password, device_uuid,
  fingerprint, name, app_version}` (or `{pair_code, device_uuid, fingerprint,
  name, app_version}` for code activation) → `{device:{uuid,code}, secret,
  license, shop, settings}`. `device_uuid`: 32 hex, new per activation
  attempt; `fingerprint`: sha256 hex (64) of ANDROID_ID + package name.
  Then a full pull.
* Auth header on every other call: `Authorization: Device <uuid>:<secret>`
  (also send it as `X-Device-Auth`), plus `X-App-Version`.
* `POST /api/sync/push` `{changes:[…]}` (≤ 500 per batch) → `{applied_seq,
  rejected, license}`. Change = `{seq, tbl, op:"upsert", uuid, changed_at,
  row:{…}}` | `{seq, tbl, op:"delta", uuid, col, delta, changed_at}` |
  `{seq, tbl, op:"delete", uuid, changed_at}`. For upsert send the row's
  current local values (all Schema columns except counters, FK values are
  already uuids). Collapse several upserts of one row in a batch into the
  last one (as DesktopSync does). seq = sync_outbox.seq. After success delete
  outbox rows with seq ≤ applied_seq and store PUSHED_UPTO.
* `POST /api/sync/pull` `{cursor, snap}` → `{changes, cursor, snap, more,
  shop, settings, license, server_time}`; loop while `more`. Apply rows: insert
  or update by uuid; ignore unknown columns; **counters** become server value
  + the sum of pending local outbox deltas for that row; a row with a pending
  local upsert keeps its local values (counters still updated); `delete` op
  deletes. Apply each page in one `db.write` (outbox untouched). Store
  cursor/snap/shop/settings/license in Kv.
* Errors: HTTP status + `{error: code}`. `device_revoked` / `shop_blocked` →
  `SyncState.blockedReason` (the app shows BlockedScreen and stops selling);
  network failures are normal (offline) — retry later, never bother the user.
* Push then pull; run in background (WorkManager periodic 15 min + an
  expedited one-off on `requestSync()`, debounced ~3 s; also every ~60 s while
  the app is in the foreground). Never overlap two runs.
* License: server returns a signed token every sync; if no successful sync for
  `shop.offline_days` days, block selling (like the desktop app) with a clear
  message to connect to the internet.

## Sign-in (SYNC agent)

1. **Activation** (first launch): owner's login + password, or the 6-digit
   code from the website. Then the initial download with progress.
2. **Login**: list of active users of the shop (big avatars with names), pick
   one, enter password; check with jBCrypt against `users.password_hash`
   (PHP `$2y$` → replace prefix with `$2a$`). Remember the user
   (`App.session.signIn`). Users whose `password_set` = 0 (signed up with
   Google/Telegram): if this phone was activated by that owner, let them in
   without a password the first time and ask to set one on the website.
3. Switch user / sign out from Settings.

## The till (TILL agent) — server: app/Models/Sale.php::create, app/views/sales/new.php, public/assets/js/sale.js

The fastest possible selling screen, optimized for one hand and for a
cashier who sells all day:

* search by name / barcode (instant, local), big product tiles or rows with
  price and stock, camera scanning (continuous with beep), hardware/Bluetooth
  scanner support (keyboard wedge: digits + Enter into the search field);
* cart with +/- and qty edit (fractional for kg/litr), remove, clear;
  variants: picking a product with active variants asks which one;
* payment: Naqd / Karta / Qarzga (no QR button), received cash and change,
  split naqd + karta, discount (needs `discount` permission), customer picker
  with search by name or phone + create new (for debt);
* `SaleService.create(...)` reproduces Sale::create exactly: price/cost from
  variant or product, line subtotal round2, discount rules
  (`discount_too_large`), karta applied first and exact, naqd capped with
  change in cash_received, qarz = rest (requires customer), paid_amount,
  payment_type label (naqd/karta/qarz/aralash), sale_payments rows (only
  amounts > 0), receipt_no = "<device_code>-<6 digit seq>" (Kv RECEIPT_SEQ),
  stock decrement through Outbox.delta (insufficient stock: allow selling
  into negative? — NO: the website refuses; refuse with a clear message),
  Debts.record(..., QARZ, ...) for the qarz part, all in one db.write, then
  requestSync();
* after a sale the till is immediately ready for the next one, with a small
  "last sale" bar (change, print, view receipt);
* receipt screen (`ReceiptScreen`), share as text/image, and printing to a
  Bluetooth ESC/POS thermal printer (58/80 mm), printer chosen in
  `PrinterScreen`.

## Products (PRODUCTS agent) — server: Product.php, ProductVariant.php, Purchase.php, QuickEntryController.php

Products list with search, category filter, low stock filter; edit/create
(name, barcode with scan button, unit, sell price, cost price (needs
`prices`), stock for a new product only (then via purchases), category, low
stock threshold, status active/inactive, variants list with their own
barcode/price/stock); quick entry of many products in a table-like list (the
website's /products/quick); categories; suppliers; purchases ("kirim"): add
items (search/scan), qty and unit cost, supplier, note → stock increases via
Outbox.delta, optionally updates cost price like Purchase.php does. Stock
for a new product is written as a delta too (insert row with stock 0, then
delta +qty) so it syncs correctly.

## Back office (BACKOFFICE agent) — server: Customer.php, DebtTransaction.php, Refund.php, Expense.php, Report.php, Attendance.php

* Customers ("Qarzdorlar"): list with balances (debtors first), search by
  name/phone, detail with ledger (running balance as historyByCustomer does),
  record a payment (tolov), add old debt, edit, call/SMS buttons; quick old
  debts entry (the website's /customers/quick-debts).
* Sales: list by day with totals, filters (date range, payment type),
  detail (items, payments, refunds), refund flow reproducing
  Refund.php (allocation by payment, stock back via delta, debt refund entry).
* Expenses: list by date range with total, add/edit/delete, categories of
  type 'expense'.
* Dashboard (HOME): today's revenue, sales count, debt total, low stock,
  quick actions; Reports: period summary like Report.php (revenue, profit,
  payment breakdown incl. refunds, expenses, top products, cashier
  leaderboard). Attendance (clock in/out) as the website does.

## Quality bar

* Instant: no spinner for local data; lists use `LazyColumn` with keys;
  queries use indexes (see Schema indexes); search debounced lightly
  (≈150 ms) or not at all if fast enough.
* Big touch targets (≥ 48 dp), large money figures, works in light and dark.
* Every action gives feedback (snackbar / haptic), errors in plain Uzbek.
* No crashes on empty data, missing references (a product deleted on the
  server), or rotation.
* Build: `bash android/build.sh debug` (must pass before you finish). Set
  `KASSIRON_ANDROID_WORK` to your own folder (e.g. `$HOME/.kassiron-android/work-till`)
  so parallel builds don't collide.

---

# DESIGN v2 — the approved look (binding)

The owner approved **android/design/mockup.html** (generated by
`android/design/build.py`). Every screen must look like its mockup screen, in
the light ("Kunduzgi") and dark ("Tungi") theme. Open the HTML in a browser (or
read build.py, which has the exact structure, sizes and texts of each screen).

## Use the kit, nothing else

* Colours and text styles: `K.c.*` / `K.t.*` (`ui/theme/Theme.kt`). Never use
  `MaterialTheme.colorScheme`, `Brand.*`, `Color(0x…)` or default Material
  components (TopAppBar, OutlinedTextField, Button, FilterChip, Card,
  NavigationBar, ListItem, FAB…) in screens.
* Building blocks: `ui/kit/Kit.kt` — KScreen, KTopBar, KIconButton, KSearch,
  KField, KAmountField, KCodeInput, KChips/KChip, KValueChip, KSegmented,
  KChoiceTiles, KSwitch, KStepper, KCard, KRow + KSub, KList, KTag, KAvatar,
  KToneIcon, KAlert, KKpi, KHero, KGroup/KGroupRow, KKeyValue, KTile, KEmpty,
  KProgress, KBar, KBottomBar, KDivider, KSheet/KSheetTitle, KConfirm, KButton
  (Primary / Alt / Danger), KFab, KMoney, KSection.
* Kit.kt is shared: don't edit it. Need something more? Put a small private
  composable in your own area file, built from K.c / K.t, and say so.
* Icons: `Icons.Outlined.*` / `Icons.AutoMirrored.Outlined.*` only (thin, like
  the mockup). 20dp in rows and buttons, 22dp in the bottom bar.
* Layout: screen edge 14dp (`Edge`), 8dp between list rows, 10–14dp between
  blocks. One Primary button per screen. Lists are `LazyColumn` with
  `contentPadding` so the last row is not hidden by a FAB / bottom bar.
* Money always with `KMoney` (number Onest 800 + small "so‘m"); quantities with
  `Money.qty`; dates `dd.MM.yyyy`, times `HH:mm`.
* Both themes must be checked. The theme follows the phone, or Settings →
  Mavzu (system / kunduzgi / tungi), stored in Kv "theme" + `ThemeMode.set()`.

## Navigation (final)

Bottom bar for everyone (filtered by permission): **Bosh · Kassa · Sotuvlar ·
Mahsulotlar · Qarzdorlar**. "Ko‘proq" (`Routes.MORE`) opens from the grid icon
at the top right of Bosh. Detail screens hide the bottom bar automatically.

## Screenshots — check your screens against the mockup

`ui` code is verified with JVM screenshots (no emulator):

    KASSIRON_ANDROID_WORK=$HOME/.kassiron-android/work-<area> bash android/build.sh shots '*<Area>Shots*'

writes `android/shots/<name>_light.png` and `_dark.png`. Write your own
`app/src/test/java/uz/kassiron/app/shots/<Area>Shots.kt` (copy SampleShots.kt;
`Shots.seed()` creates a realistic shop), capture **every** screen and state
you own (empty, filled, error, sheet open where possible), then LOOK at each
PNG (Read the file) and fix until it matches the mockup in both themes: no
clipped text, no overlapping, nothing touching the screen edge, consistent
spacing. Delete android/shots/ PNGs you don't need before committing (the
folder is git-ignored anyway).

# YOZUVLAR — text rules (very important to the owner)

Every visible text is in `res/values/strings_<area>.xml` (Uzbek, Latin) and
`res/values-ru/strings_<area>.xml` (Russian). Review **all** your strings
against these rules, also the ones written before.

* Uzbek letters: **o‘ g‘** with U+2018 (‘), never ASCII `'` or `` ` `` or ʻ;
  tutuq belgisi with U+2019 (’): ta’minotchi, ma’lumot, sur’at, e’lon. So `so‘m`,
  `To‘lash`, `Qo‘shish`, `O‘chirish`, `Bog‘lanish`, `Ko‘proq`.
* Short and plain, the words a shopkeeper uses. Buttons are verbs in the
  infinitive-noun form: Saqlash, Sotish, To‘lash, Qo‘shish, O‘chirish,
  Tahrirlash, Bekor qilish, Qaytarish, Chop etish, Ulashish, Kirish, Ulash.
* No "iltimos", no "!", no "muvaffaqiyatli". Errors say what to do:
  "Narxni kiriting", "Mijozni tanlang", "Internet yo‘q — sotuvlar saqlanadi".
* Sentence case: only the first word capitalized ("Yangi mahsulot", not
  "Yangi Mahsulot").
* Glossary (use exactly these): Kassa, Sotuvlar, Mahsulotlar, Qarzdorlar, Bosh,
  Ko‘proq, Xarajatlar, Hisobotlar, Kirim, Ta’minotchilar, Kategoriyalar,
  Tez kiritish, Eski qarzlar, Davomat, Printer, Sozlamalar, Mijoz, Chek,
  Naqd, Karta, Qarz (payment type), Qarzga (on credit), Qaytim, Chegirma,
  Qoldiq, Tannarx, Sotish narxi, Shtrix-kod, Birlik, Kategoriya, Ta’minotchi,
  Tushum, Foyda, Qaytarilgan, To‘lov qabul qilish, Telefonni ulash, so‘m, dona.
* Russian: natural, short, "сум", "Касса", "Продажи", "Товары", "Должники",
  "Главная", "Ещё". Same rules (no "пожалуйста", no "!").
* Placeholders show a real example ("Masalan: Baraka market", "+998 90 123 45 67").
