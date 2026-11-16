package uz.kassiron.app.core

import org.json.JSONObject

/** Device state in the `kv` table. Keys used across the app are listed in [Keys]. */
class Kv(private val db: Db) {
    fun get(key: String): String? = db.scalar("SELECT value FROM kv WHERE key = ?", key)?.toString()
    fun get(key: String, default: String): String = get(key) ?: default
    fun set(key: String, value: String?) {
        if (value == null) db.exec("DELETE FROM kv WHERE key = ?", key)
        else db.exec("INSERT INTO kv (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value)
    }
    fun long(key: String, default: Long = 0): Long = get(key)?.toLongOrNull() ?: default

    /** A shop setting as the server sent it (the settings table: key -> value). */
    fun shopSetting(key: String): String? = get(Keys.SHOP_SETTINGS)?.let {
        runCatching { JSONObject(it).let { o -> if (o.has(key) && !o.isNull(key)) o.getString(key) else null } }.getOrNull()
    }

    /** The shop as the server sent it: name, owner_full_name, phone, address, currency, receipt_printer_width, offline_days. */
    fun shop(): JSONObject = get(Keys.SHOP)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
}

object Keys {
    // Device identity (set by activation).
    const val DEVICE_UUID = "device_uuid"
    const val DEVICE_CODE = "device_code"        // "K3": receipt numbers are "K3-000145"
    const val DEVICE_SECRET = "device_secret"
    const val LICENSE = "license"
    const val BLOCKED_REASON = "blocked_reason"  // device_revoked | shop_blocked
    const val RECEIPT_SEQ = "receipt_seq"
    // Sync state.
    const val PULL_CURSOR = "pull_cursor"
    const val PULL_SNAP = "pull_snap"
    const val PUSHED_UPTO = "pushed_upto"
    const val LAST_SYNC_AT = "last_sync_at"      // epoch millis
    const val LAST_SYNC_ERROR = "last_sync_error"
    const val SERVER_TIME_OFFSET = "server_time_offset" // seconds, server - device
    const val SHOP = "shop"                      // JSON
    const val SHOP_SETTINGS = "shop_settings"    // JSON
    // Session / preferences.
    const val CURRENT_USER = "current_user"      // users.uuid of who is signed in
    const val LANG = "lang"                      // uz | ru
    const val PRINTER_ADDRESS = "printer_address" // Bluetooth MAC
    const val PRINTER_WIDTH = "printer_width"    // 58 | 80
}
