package uz.kassiron.app.print

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * ESC/POS bytes for cheap 58/80 mm thermal printers. Text is either plain
 * ASCII (Cyrillic transliterated to Latin — works on every printer) or
 * CP866 (code page 17 on most Xprinter/Gprinter-style models).
 */
class EscPos(private val cp866: Boolean) {
    private val out = ByteArrayOutputStream()

    init {
        bytes(0x1B, 0x40) // ESC @ — reset
        if (cp866) bytes(0x1B, 0x74, 17) // ESC t 17 — PC866 Cyrillic
    }

    private fun bytes(vararg b: Int) = b.forEach { out.write(it) }

    fun align(center: Boolean) = apply { bytes(0x1B, 0x61, if (center) 1 else 0) }
    fun bold(on: Boolean) = apply { bytes(0x1B, 0x45, if (on) 1 else 0) }
    fun big(on: Boolean) = apply { bytes(0x1D, 0x21, if (on) 0x11 else 0x00) }

    fun text(s: String) = apply { out.write(encode(s)) }
    fun line(s: String = "") = apply { text(s); out.write('\n'.code) }
    fun feed(lines: Int) = apply { bytes(0x1B, 0x64, lines.coerceIn(0, 10)) }

    /** Feeds the paper past the cutter and cuts (partially) — ignored by printers without one. */
    fun cut() = apply { bytes(0x1D, 0x56, 66, 0) }

    fun build(): ByteArray = out.toByteArray()

    fun receipt(lines: List<ReceiptLine>) = apply {
        for (l in lines) {
            align(l.center); bold(l.bold); big(l.big)
            line(l.text)
        }
        big(false); bold(false); align(false)
        feed(3)
        cut()
    }

    private fun encode(s: String): ByteArray {
        val normalized = Text.normalizeQuotes(s)
        return if (cp866) normalized.toByteArray(CP866) else Text.toAscii(normalized).toByteArray(Charsets.US_ASCII)
    }

    companion object {
        private val CP866: Charset = runCatching { Charset.forName("IBM866") }.getOrDefault(Charsets.US_ASCII)
    }
}

/** Text clean-up for printers that only know ASCII. */
object Text {
    /** O‘zbek apostrophes and typographic quotes -> plain ASCII ones; № -> N. */
    fun normalizeQuotes(s: String): String = buildString(s.length) {
        for (c in s) append(
            when (c) {
                '‘', '’', 'ʻ', 'ʼ', '`', '´' -> '\''
                '“', '”', '«', '»' -> '"'
                '—', '–' -> '-'
                '№' -> 'N'
                ' ', ' ' -> ' '
                else -> c
            }
        )
    }

    private val CYR = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "j", 'з' to "z",
        'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r",
        'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "x", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
        'ъ' to "'", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o'", 'қ' to "q", 'ғ' to "g'", 'ҳ' to "h",
    )

    /** Transliterates Cyrillic to Latin and drops anything else outside ASCII. */
    fun toAscii(s: String): String = buildString(s.length) {
        for (c in normalizeQuotes(s)) {
            when {
                c.code < 128 -> append(c)
                CYR.containsKey(c.lowercaseChar()) -> {
                    val t = CYR.getValue(c.lowercaseChar())
                    append(if (c.isUpperCase()) t.replaceFirstChar { it.uppercaseChar() } else t)
                }
                else -> append('?')
            }
        }
    }
}
