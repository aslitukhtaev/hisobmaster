package uz.kassiron.app.domain

/**
 * The shop's own barcodes, for products that came without one: EAN-13 made of
 * "2" (the range no manufacturer uses) + a 2-digit device number + a 9-digit
 * sequence + the EAN-13 check digit. The website makes the same codes with
 * device number 00; each phone / computer has its own number (from its code,
 * "K3" -> 03), so codes made offline on two devices rarely meet — and a meeting
 * is still caught by the "barcode already taken" check when saving.
 */
object BarcodeGen {
    const val MAX_SEQUENCE = 999_999_999L

    /** The EAN-13 check digit of the first 12 digits. */
    fun checkDigit(first12: String): Int {
        require(first12.length == 12 && first12.all(Char::isDigit)) { "12 digits needed" }
        var sum = 0
        first12.forEachIndexed { i, c -> sum += (c - '0') * (if (i % 2 == 0) 1 else 3) }
        return (10 - sum % 10) % 10
    }

    fun isValidEan13(code: String): Boolean =
        code.length == 13 && code.all(Char::isDigit) && checkDigit(code.substring(0, 12)) == code[12] - '0'

    /** "K3" -> "03", "K12" -> "12", "K105" -> "05" (modulo 100); no digits -> "00". */
    fun deviceDigits(deviceCode: String?): String {
        val n = deviceCode?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }?.toBigInteger()?.mod(100.toBigInteger())?.toInt() ?: 0
        return n.toString().padStart(2, '0')
    }

    fun build(device: String, sequence: Long): String {
        val first12 = "2" + device + sequence.toString().padStart(9, '0')
        return first12 + checkDigit(first12)
    }

    /**
     * The next code for [deviceCode]: one past the highest sequence among the
     * [taken] codes of this device, skipping any code that is taken anyway.
     * Throws when the device has used all its 999 999 999 codes.
     */
    fun next(deviceCode: String?, taken: Set<String>): String = nextMany(deviceCode, taken, 1).first()

    /** [count] distinct new codes, none of them in [taken]. */
    fun nextMany(deviceCode: String?, taken: Set<String>, count: Int): List<String> {
        val device = deviceDigits(deviceCode)
        val prefix = "2$device"
        var seq = (taken.asSequence()
            .filter { it.length == 13 && it.startsWith(prefix) && it.all(Char::isDigit) }
            .maxOfOrNull { it.substring(3, 12).toLong() } ?: 0L) + 1
        val out = ArrayList<String>(count)
        while (out.size < count) {
            check(seq <= MAX_SEQUENCE) { "barcode sequence exhausted" }
            val code = build(device, seq++)
            if (code !in taken) out += code
        }
        return out
    }
}
