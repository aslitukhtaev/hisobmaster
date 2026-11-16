package uz.kassiron.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Times are stored like the server stores them: UTC "yyyy-MM-dd HH:mm:ss"
 * (created_at) and shown in the phone's time zone. Business days (today's
 * sales, expense_date) use the device's local date.
 */
object Time {
    private fun fmt(pattern: String, utc: Boolean) = SimpleDateFormat(pattern, Locale.US).apply {
        if (utc) timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Now, UTC, for created_at. */
    fun now(): String = fmt("yyyy-MM-dd HH:mm:ss", true).format(Date())

    /** Now with milliseconds, UTC, for sync_outbox.changed_at (last write wins on the server). */
    fun nowMillis(): String = fmt("yyyy-MM-dd HH:mm:ss.SSS", true).format(Date())

    /** Today's local date "yyyy-MM-dd". */
    fun today(): String = fmt("yyyy-MM-dd", false).format(Date())

    /** A local date "yyyy-MM-dd" moved by [days]. */
    fun addDays(localDate: String, days: Int): String {
        val f = fmt("yyyy-MM-dd", false)
        val c = Calendar.getInstance().apply { time = f.parse(localDate)!!; add(Calendar.DAY_OF_MONTH, days) }
        return f.format(c.time)
    }

    fun parseUtc(value: String?): Date? {
        if (value.isNullOrBlank()) return null
        return runCatching { fmt("yyyy-MM-dd HH:mm:ss", true).parse(value.take(19)) }.getOrNull()
    }

    /** A UTC created_at shown in local time, e.g. "16.11.2026 14:05". */
    fun local(utc: String?, pattern: String = "dd.MM.yyyy HH:mm"): String =
        parseUtc(utc)?.let { fmt(pattern, false).format(it) } ?: ""

    /** "2026-11-16" -> "16.11.2026". */
    fun showDate(localDate: String?): String = localDate?.takeIf { it.length >= 10 }?.let {
        "${it.substring(8, 10)}.${it.substring(5, 7)}.${it.substring(0, 4)}"
    } ?: ""

    /** UTC bounds [from, to) of the local days from..to inclusive ("yyyy-MM-dd"), for created_at range queries. */
    fun utcRange(fromLocalDate: String, toLocalDate: String): Pair<String, String> {
        val local = fmt("yyyy-MM-dd", false)
        val start = local.parse(fromLocalDate)!!
        val end = Calendar.getInstance().apply { time = local.parse(toLocalDate)!!; add(Calendar.DAY_OF_MONTH, 1) }.time
        val utc = fmt("yyyy-MM-dd HH:mm:ss", true)
        return utc.format(start) to utc.format(end)
    }
}

object Ids {
    /** A new row uuid: 32 lowercase hex characters, like the server's lower(hex(randomblob(16))). */
    fun new(): String = UUID.randomUUID().toString().replace("-", "")
}
