package uz.kassiron.app.print

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    /** [dots]: the printable width — 384 on 58 mm paper, 576 on 80 mm (8 dots per mm). */
    fun receipt(lines: List<ReceiptLine>, dots: Int = 384) = apply {
        for (l in lines) {
            val img = l.image
            if (img != null) {
                align(true)
                if (img.logo) image(img.png, (dots * 0.7).roundToInt(), 22 * 8)
                else (if (dots >= 576) 34 * 8 else 30 * 8).let { image(img.png, it, it) }
                continue
            }
            align(l.center); bold(l.bold); big(l.big)
            line(l.text)
        }
        big(false); bold(false); align(false)
        feed(3)
        cut()
    }

    /**
     * A PNG as a black-and-white raster (GS v 0), scaled to fit [maxW] x [maxH]
     * dots keeping its shape (never enlarged past twice its size). Sent in
     * bands of 128 rows, which cheap printers' buffers take. A picture that
     * doesn't decode is skipped: the receipt still prints.
     */
    fun image(png: ByteArray, maxW: Int, maxH: Int) = apply {
        val src = runCatching { BitmapFactory.decodeByteArray(png, 0, png.size) }.getOrNull() ?: return@apply
        if (src.width <= 0 || src.height <= 0) return@apply
        val scale = min(min(maxW.toFloat() / src.width, maxH.toFloat() / src.height), 2f)
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        val bmp = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val rowBytes = (w + 7) / 8
        var y0 = 0
        while (y0 < h) {
            val band = min(128, h - y0)
            bytes(0x1D, 0x76, 0x30, 0x00, rowBytes and 0xFF, (rowBytes shr 8) and 0xFF, band and 0xFF, (band shr 8) and 0xFF)
            val data = ByteArray(rowBytes * band)
            for (y in 0 until band) {
                for (x in 0 until w) {
                    if (isInk(pixels[(y0 + y) * w + x])) {
                        val i = y * rowBytes + x / 8
                        data[i] = (data[i].toInt() or (0x80 shr (x % 8))).toByte()
                    }
                }
            }
            out.write(data)
            y0 += band
        }
        line()
    }

    private fun isInk(argb: Int): Boolean {
        if (Color.alpha(argb) < 128) return false // transparent = paper
        val lum = (Color.red(argb) * 299 + Color.green(argb) * 587 + Color.blue(argb) * 114) / 1000
        return lum < 128
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
                c == '·' -> append('-')
                CYR.containsKey(c.lowercaseChar()) -> {
                    val t = CYR.getValue(c.lowercaseChar())
                    append(if (c.isUpperCase()) t.replaceFirstChar { it.uppercaseChar() } else t)
                }
                else -> append('?')
            }
        }
    }
}
