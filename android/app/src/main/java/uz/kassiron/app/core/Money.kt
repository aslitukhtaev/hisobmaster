package uz.kassiron.app.core

import kotlin.math.abs
import kotlin.math.round

/** Amounts as the website shows them: "15 000 so'm", "1 250,5 so'm". */
object Money {
    /** Like PHP's round($v, 2) (half away from zero on the decimal value: 1.005 -> 1.01), so totals match the server. */
    fun round2(v: Double): Double {
        if (!v.isFinite()) return v
        return java.math.BigDecimal(v.toString()).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
    }

    fun format(v: Double, withCurrency: Boolean = true): String {
        val r = round2(v)
        val negative = r < 0
        val whole = abs(r).toLong()
        val cents = round((abs(r) - whole) * 100).toLong()
        val digits = whole.toString().reversed().chunked(3).joinToString(" ").reversed()
        val fraction = if (cents > 0) "," + cents.toString().padStart(2, '0').trimEnd('0') else ""
        return (if (negative) "-" else "") + digits + fraction + (if (withCurrency) " so'm" else "")
    }

    /** "15 000", "1,5", "1.5" -> 15000.0 / 1.5; null when not a number. */
    fun parse(text: String): Double? {
        val clean = text.replace(" ", "").replace(" ", "").replace(",", ".").trim()
        if (clean.isEmpty()) return null
        return clean.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** A quantity: "2", "1,5". */
    fun qty(v: Double): String = if (abs(v - round(v)) < 0.000001) round(v).toLong().toString()
        else v.toString().trimEnd('0').trimEnd('.').replace('.', ',')
}

/** Units whose quantity may be fractional (the server's FRACTIONAL_UNITS). */
object Units {
    private val FRACTIONAL = setOf("kg", "g", "gr", "gramm", "litr", "l", "ml", "metr", "m", "sm", "кг", "г", "гр", "л", "литр", "мл", "м", "метр", "см")
    val COMMON = listOf("dona", "kg", "litr", "metr", "quti", "pachka", "g", "ml")
    fun allowsFraction(unit: String?): Boolean = unit?.trim()?.lowercase() in FRACTIONAL
    fun fits(qty: Double, unit: String?): Boolean = allowsFraction(unit) || abs(qty - round(qty)) < 0.000001
}
