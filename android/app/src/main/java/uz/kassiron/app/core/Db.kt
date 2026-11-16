package uz.kassiron.app.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row of a query: column name -> value (String, Long, Double or null). */
class Row(private val values: Map<String, Any?>) {
    operator fun get(column: String): Any? = values[column]
    fun str(column: String): String = values[column]?.toString() ?: ""
    fun strOrNull(column: String): String? = values[column]?.toString()
    fun dbl(column: String): Double = when (val v = values[column]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    fun dblOrNull(column: String): Double? = when (val v = values[column]) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull()
        else -> null
    }
    fun long(column: String): Long = when (val v = values[column]) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull() ?: 0L
        else -> 0L
    }
    fun has(column: String) = values.containsKey(column)
    fun toMap(): Map<String, Any?> = values
}

/**
 * The local database. One instance for the app (KassirApp.db); every call is
 * synchronous — call from Dispatchers.IO (see `io { }` in Async.kt).
 *
 * Writers that change shop data must go through [write]: it runs the block
 * in a transaction and, once committed, bumps [changes] so open screens
 * re-read what they show. Sync applies the server's rows the same way.
 */
class Db(context: Context) : SQLiteOpenHelper(context, "kassiron.db", null, VERSION) {

    companion object {
        /** Bump with a migration in onUpgrade() whenever Schema changes. */
        const val VERSION = 1
    }

    private val _changes = MutableStateFlow(0L)

    /** Increments after every committed write: observe to refresh lists. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        Schema.createStatements().forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Columns added to Schema later: ALTER TABLE ... ADD COLUMN here, per version.
        Schema.createStatements().forEach(db::execSQL)
    }

    private val db: SQLiteDatabase get() = writableDatabase

    fun query(sql: String, vararg args: Any?): List<Row> {
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c -> return readAll(c) }
    }

    fun queryOne(sql: String, vararg args: Any?): Row? = query(sql, *args).firstOrNull()

    /** First column of the first row (null when there is none). */
    fun scalar(sql: String, vararg args: Any?): Any? {
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            if (!c.moveToFirst()) return null
            return value(c, 0)
        }
    }

    fun scalarDouble(sql: String, vararg args: Any?): Double = (scalar(sql, *args) as? Number)?.toDouble()
        ?: scalar(sql, *args)?.toString()?.toDoubleOrNull() ?: 0.0

    fun scalarLong(sql: String, vararg args: Any?): Long = (scalar(sql, *args) as? Number)?.toLong()
        ?: scalar(sql, *args)?.toString()?.toLongOrNull() ?: 0L

    fun exec(sql: String, vararg args: Any?) {
        db.execSQL(sql, args.map { a -> if (a is Boolean) (if (a) 1 else 0) else a }.toTypedArray())
    }

    fun insert(table: String, values: Map<String, Any?>) {
        db.insertOrThrow(table, null, contentValues(values))
    }

    /** INSERT OR REPLACE by primary key (uuid). */
    fun upsert(table: String, values: Map<String, Any?>) {
        db.insertWithOnConflict(table, null, contentValues(values), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun update(table: String, uuid: String, values: Map<String, Any?>) {
        if (values.isEmpty()) return
        db.update(table, contentValues(values), "uuid = ?", arrayOf(uuid))
    }

    fun delete(table: String, uuid: String) {
        db.delete(table, "uuid = ?", arrayOf(uuid))
    }

    /** Runs [block] in one transaction; observers are told once it commits. Nested calls join the outer one. */
    fun <T> write(block: Db.() -> T): T {
        val outer = !db.inTransaction()
        db.beginTransactionNonExclusive()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
            if (outer) _changes.value = _changes.value + 1
        }
    }

    private fun contentValues(values: Map<String, Any?>) = ContentValues().apply {
        for ((k, v) in values) {
            when (v) {
                null -> putNull(k)
                is String -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
                is Double -> put(k, v)
                is Float -> put(k, v.toDouble())
                is Boolean -> put(k, if (v) 1 else 0)
                else -> put(k, v.toString())
            }
        }
    }

    private fun readAll(c: Cursor): List<Row> {
        val out = ArrayList<Row>(c.count)
        val names = c.columnNames
        while (c.moveToNext()) {
            val map = HashMap<String, Any?>(names.size * 2)
            for (i in names.indices) map[names[i]] = value(c, i)
            out += Row(map)
        }
        return out
    }

    private fun value(c: Cursor, i: Int): Any? = when (c.getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
        else -> c.getString(i)
    }
}
