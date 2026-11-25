package uz.kassiron.app.sync

import org.json.JSONArray
import org.json.JSONObject
import uz.kassiron.app.core.Db
import uz.kassiron.app.core.Keys
import uz.kassiron.app.core.Time
import uz.kassiron.app.core.Kv
import uz.kassiron.app.core.Schema
import kotlin.math.round

/**
 * The device side of the sync protocol — a line-by-line port of the desktop
 * client (app/Desktop/DesktopSync.php) to this database, where rows are
 * keyed by uuid and foreign keys already hold uuids:
 *
 *  1. push: the outbox (core/Outbox.kt) in batches of ≤ 500, each row read at
 *     its current state; the server skips seqs it already applied, so a batch
 *     whose answer got lost is simply sent again.
 *  2. pull: pages of the server's changes, each applied in one transaction
 *     together with the cursor it leads to.
 *
 * Everything here is blocking: call from Dispatchers.IO, one run at a time
 * (SyncManager holds the lock).
 */
internal class SyncEngine(private val db: Db, private val kv: Kv, private val api: Api) {

    companion object {
        const val BATCH = 500
    }

    fun auth(): String? {
        val uuid = kv.get(Keys.DEVICE_UUID) ?: return null
        val secret = kv.get(Keys.DEVICE_SECRET) ?: return null
        return "Device $uuid:$secret"
    }

    private fun authOrThrow(): String = auth() ?: throw SyncException("not_activated")

    // ---------------------------------------------------------------- push

    /** Sends every local change; returns how many changes went. */
    /** Refused changes reported by the last push call (folded into Kv with the outbox cleanup). */
    private var rejected = 0L

    fun push(): Int {
        var total = 0
        while (true) {
            val entries = db.query("SELECT * FROM sync_outbox ORDER BY seq LIMIT ?", BATCH)
            if (entries.isEmpty()) return total

            // A row is read at its current state when sent, so several upserts
            // of it in one batch go once — at the first position (it still
            // arrives before rows that point to it), stamped with the latest
            // change time for the server's last-write-wins.
            val latestUpsert = HashMap<String, String>()
            // The columns those upserts changed, together; null = the whole row (a new
            // row, or one queued before columns were recorded).
            val changedCols = HashMap<String, MutableSet<String>?>()
            for (e in entries) {
                if (e.str("op") != "upsert") continue
                val key = e.str("tbl") + ":" + e.str("uuid")
                latestUpsert[key] = e.str("changed_at")
                val cols = e.strOrNull("cols")
                if (!changedCols.containsKey(key)) changedCols[key] = if (cols == null) null else HashSet()
                if (cols == null) changedCols[key] = null
                else changedCols[key]?.addAll(cols.split(',').filter(String::isNotEmpty))
            }

            val changes = JSONArray()
            val sent = HashSet<String>()
            for (e in entries) {
                val table = e.str("tbl")
                val uuid = e.str("uuid")
                if (!Schema.has(table) || !Schema.table(table).pushable) continue
                val base = JSONObject()
                    .put("seq", e.long("seq"))
                    .put("tbl", table)
                    .put("uuid", uuid)
                    .put("changed_at", e.str("changed_at"))

                when (e.str("op")) {
                    "upsert" -> {
                        val key = "$table:$uuid"
                        if (!sent.add(key)) continue
                        val only = changedCols[key]
                        val row = wireRow(table, uuid, only) ?: continue // gone since, or nothing that travels
                        base.put("changed_at", latestUpsert[key] ?: e.str("changed_at"))
                        if (only != null) base.put("partial", true)
                        changes.put(base.put("op", "upsert").put("row", row))
                    }
                    "delta" -> changes.put(
                        base.put("op", "delta").put("col", e.str("col")).put("delta", e.dbl("delta")),
                    )
                    "delete" -> changes.put(base.put("op", "delete"))
                }
            }

            if (changes.length() > 0) {
                // The server applies the whole batch or answers with an error
                // (thrown), so on success everything read is done — including
                // entries there was nothing to send for.
                val response = api.post("/api/sync/push", JSONObject().put("changes", changes), authOrThrow())
                storeLicense(response)
                rejected = response.optLong("rejected", 0)
            }

            val last = entries.last().long("seq")
            db.write {
                exec("DELETE FROM sync_outbox WHERE seq <= ?", last)
                kv.set(Keys.PUSHED_UPTO, last.toString())
                // The server keeps what it couldn't take (sync_rejects); the owner must hear about it.
                if (rejected > 0) kv.set(Keys.SYNC_REJECTED, (kv.long(Keys.SYNC_REJECTED) + rejected).toString())
            }
            rejected = 0
            total += changes.length()

            if (entries.size < BATCH) return total
        }
    }

    /** All Schema columns of the row except counters (those travel as deltas), or null when it's gone. */
    /** The row as sent; [only]: just these columns (an edit's changes), null = all. */
    private fun wireRow(table: String, uuid: String, only: Set<String>? = null): JSONObject? {
        val t = Schema.table(table)
        val row = db.queryOne("SELECT * FROM $table WHERE uuid = ?", uuid) ?: return null
        val out = JSONObject()
        for (c in t.columns) {
            if (c.name in t.counters) continue
            if (only != null && c.name !in only) continue
            out.put(c.name, row[c.name] ?: JSONObject.NULL)
        }
        return if (only != null && out.length() == 0) null else out
    }

    // ---------------------------------------------------------------- pull

    /**
     * Downloads everything that changed since the stored cursor (the whole
     * shop the first time). [onPage] gets the running total of rows received.
     */
    fun pull(onPage: (Int) -> Unit = {}): Int {
        var total = 0
        do {
            val cursor = kv.get(Keys.PULL_CURSOR)?.toLongOrNull()
            val snap = kv.get(Keys.PULL_SNAP)?.let { runCatching { JSONObject(it) }.getOrNull() }
            val body = JSONObject()
                .put("cursor", cursor ?: JSONObject.NULL)
                .put("snap", snap ?: JSONObject.NULL)
            val response = api.post("/api/sync/pull", body, authOrThrow())
            val changes = response.optJSONArray("changes") ?: JSONArray()

            // An empty page (the usual minute-by-minute check) changes nothing
            // on screen: write the cursor without making every list re-read.
            val shopBefore = kv.get(Keys.SHOP) to kv.get(Keys.SHOP_SETTINGS)
            val quiet = changes.length() == 0 &&
                response.optJSONObject("shop")?.toString().let { it == null || it == shopBefore.first } &&
                (!response.has("settings") || (response.optJSONObject("settings") ?: JSONObject()).toString() == shopBefore.second)
            db.write(notify = !quiet) {
                applyChanges(changes)
                storeShopState(response)
                kv.set(Keys.PULL_CURSOR, if (response.isNull("cursor")) null else response.optLong("cursor").toString())
                kv.set(Keys.PULL_SNAP, response.optJSONObject("snap")?.toString())
                storeLicense(response)
            }

            total += changes.length()
            onPage(total)
        } while (response.optBoolean("more", false))
        return total
    }

    /**
     * Applies rows received from the server. A counter (stock) becomes the
     * server's value plus what this phone changed that the server hasn't seen
     * yet; a row this phone edited but hasn't sent keeps its local values (the
     * server decides between the two edits when it arrives). Call inside
     * db.write; the outbox is not touched.
     */
    fun applyChanges(changes: JSONArray) {
        if (changes.length() == 0) return
        val pending = PendingLocal.read(db)
        val known = HashSet<String>() // "table:uuid" seen to exist, for the parent check
        val deferred = ArrayList<JSONObject>()

        for (i in 0 until changes.length()) {
            val change = changes.optJSONObject(i) ?: continue
            if (!applyOne(change, pending, known, force = false)) deferred += change
        }
        // A row whose parent came later in the same page. Foreign keys aren't
        // enforced on this device, so a row whose parent never came (removed
        // on the server) is still kept: losing it would be worse, and screens
        // cope with a missing reference.
        for (change in deferred) applyOne(change, pending, known, force = true)
    }

    /** False when a row it points to isn't here yet (retried once at the end of the page). */
    private fun applyOne(change: JSONObject, pending: PendingLocal, known: HashSet<String>, force: Boolean): Boolean {
        val table = change.optString("tbl")
        val uuid = change.optString("uuid")
        if (!Schema.has(table) || uuid.isEmpty()) return true
        val t = Schema.table(table)

        if (change.optString("op") == "delete") {
            db.delete(table, uuid)
            known.remove("$table:$uuid")
            return true
        }

        val row = change.optJSONObject("row") ?: JSONObject()
        val values = HashMap<String, Any?>()
        for (c in t.columns) {
            if (c.name in t.counters || !row.has(c.name)) continue // unknown columns are simply never read
            val value = convert(row.opt(c.name), c.type)
            val parent = t.foreignKeys[c.name]
            if (!force && parent != null && value != null && !exists(parent, value.toString(), known)) return false
            values[c.name] = value
        }

        val existing = exists(table, uuid, known)

        val counters = HashMap<String, Any?>()
        for (col in t.counters) {
            val server = (convert(row.opt(col), "REAL") as? Double) ?: 0.0
            val local = if (existing) pending.delta(table, uuid, col) else 0.0
            counters[col] = round((server + local) * 1_000_000) / 1_000_000
        }

        if (!existing) {
            val insert = HashMap<String, Any?>(values)
            insert.putAll(counters)
            // NOT NULL columns the server left out or sent as null.
            for (c in t.columns) {
                if (c.notNull && insert[c.name] == null) insert[c.name] = fallback(c)
            }
            insert["uuid"] = uuid
            db.insert(table, insert)
            known += "$table:$uuid"
            return true
        }

        // A local edit not pushed yet keeps its own columns; the server's values
        // of the others (e.g. an optom price set on the website) still land here.
        val update: HashMap<String, Any?> = when {
            !pending.hasUpsert(table, uuid) -> HashMap(values)
            else -> pending.upsertCols(table, uuid)?.let { mine -> HashMap(values.filterKeys { it !in mine }) } ?: HashMap()
        }
        update.putAll(counters)
        for (c in t.columns) {
            if (c.notNull && update.containsKey(c.name) && update[c.name] == null) update[c.name] = fallback(c)
        }
        db.update(table, uuid, update)
        return true
    }

    private fun exists(table: String, uuid: String, known: HashSet<String>): Boolean {
        val key = "$table:$uuid"
        if (key in known) return true
        val found = db.scalar("SELECT 1 FROM $table WHERE uuid = ?", uuid) != null
        if (found) known += key
        return found
    }

    /** A JSON value as stored in a column of [type]. */
    private fun convert(value: Any?, type: String): Any? = when {
        value == null || value == JSONObject.NULL -> null
        value is Boolean -> if (value) 1L else 0L
        type == "INTEGER" -> when (value) {
            is Number -> value.toLong()
            else -> value.toString().toDoubleOrNull()?.toLong() ?: value.toString()
        }
        type == "REAL" -> when (value) {
            is Number -> value.toDouble()
            else -> value.toString().toDoubleOrNull() ?: value.toString()
        }
        else -> when (value) {
            is JSONObject, is JSONArray -> value.toString()
            is Double -> if (value == Math.floor(value) && !value.isInfinite() && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()
            else -> value.toString()
        }
    }

    private fun fallback(c: Schema.Column): Any = c.default?.let { d ->
        if (d.startsWith("'")) d.trim('\'') else d.toLongOrNull() ?: d.toDoubleOrNull() ?: d
    } ?: if (c.type == "TEXT") "" else 0L

    // ---------------------------------------------------------- shop state

    fun storeShopState(response: JSONObject) {
        response.optJSONObject("shop")?.let { kv.set(Keys.SHOP, it.toString()) }
        if (response.has("settings")) {
            // PHP sends an empty key/value list as [] rather than {}.
            val settings = response.optJSONObject("settings") ?: JSONObject()
            kv.set(Keys.SHOP_SETTINGS, settings.toString())
        }
    }

    fun storeLicense(response: JSONObject) {
        response.optString("license").takeIf { it.isNotBlank() }?.let { kv.set(Keys.LICENSE, it) }
        if (response.has("server_time")) {
            val offset = response.optLong("server_time") - System.currentTimeMillis() / 1000
            kv.set(Keys.SERVER_TIME_OFFSET, offset.toString())
            Time.setServerOffset(offset)
        }
    }

    /** Local changes not on the server yet, read once per page. */
    private class PendingLocal(private val upserts: Map<String, Set<String>?>, private val deltas: Map<String, Double>) {
        fun hasUpsert(table: String, uuid: String) = upserts.containsKey("$table:$uuid")
        /** The columns a pending local edit changed; null = the whole row (a new row). */
        fun upsertCols(table: String, uuid: String): Set<String>? = upserts["$table:$uuid"]
        fun delta(table: String, uuid: String, col: String) = deltas["$table:$uuid:$col"] ?: 0.0

        companion object {
            fun read(db: Db): PendingLocal {
                val upserts = HashMap<String, MutableSet<String>?>()
                for (r in db.query("SELECT tbl, uuid, cols FROM sync_outbox WHERE op = 'upsert'")) {
                    val key = r.str("tbl") + ":" + r.str("uuid")
                    val cols = r.strOrNull("cols")
                    if (!upserts.containsKey(key)) upserts[key] = if (cols == null) null else HashSet()
                    if (cols == null) upserts[key] = null
                    else upserts[key]?.addAll(cols.split(',').filter(String::isNotEmpty))
                }
                val deltas = db.query(
                    "SELECT tbl, uuid, col, SUM(delta) AS d FROM sync_outbox WHERE op = 'delta' GROUP BY tbl, uuid, col",
                ).associate { it.str("tbl") + ":" + it.str("uuid") + ":" + it.str("col") to it.dbl("d") }
                return PendingLocal(upserts, deltas)
            }
        }
    }
}
