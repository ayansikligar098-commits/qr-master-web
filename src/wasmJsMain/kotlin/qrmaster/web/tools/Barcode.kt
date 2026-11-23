package qrmaster.web.tools

// Real 1D barcode encoders — pure Kotlin, no libraries.
// Output is a module string of '1' (bar) / '0' (space) including quiet zones,
// ready to be drawn on a Canvas or exported to PNG. Encodings follow the
// published symbology specifications (Code 128 ISO/IEC 15417 pattern table,
// EAN-13/UPC-A L/G/R codes, Code 39 character table).

object BarcodeLogic {

    data class Encoded(val modules: String, val displayText: String, val kind: String)

    // ── EAN-13 ──────────────────────────────────────────────────────────────
    private val EAN_L = arrayOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011"
    )
    private val EAN_G = arrayOf(
        "0100111", "0110011", "0011011", "0100001", "0011101",
        "0111001", "0000101", "0010001", "0001001", "0010111"
    )
    private val EAN_R = arrayOf(
        "1110010", "1100110", "1101100", "1000010", "1011100",
        "1001110", "1010000", "1000100", "1001000", "1110100"
    )
    /** Parity pattern for the first digit (positions 1..6 of the left group). */
    private val EAN_PARITY = arrayOf(
        "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
        "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
    )

    fun ean13Checksum(first12: String): Int {
        var sum = 0
        for (i in first12.indices) {
            val d = first12[i] - '0'
            sum += if (i % 2 == 0) d else d * 3 // positions are 1-based odd/even from the left of 13-digit code: index 0 = pos 1
        }
        // EAN-13: starting from the leftmost digit of the full 13, odd positions ×1, even ×3
        // For the 12-digit payload, index 0 is position 1 (odd) → ×1. Correct as above.
        return (10 - (sum % 10)) % 10
    }

    /** Accepts 12 digits (checksum computed) or 13 digits (checksum verified). */
    fun encodeEan13(input: String): Encoded? {
        val digits = input.filter { it.isDigit() }
        val full = when {
            digits.length == 12 -> digits + ean13Checksum(digits).toString()
            digits.length == 13 -> {
                if (ean13Checksum(digits.substring(0, 12)) != digits[12] - '0') return null
                digits
            }
            else -> return null
        }
        val sb = StringBuilder()
        sb.append("0000000000") // quiet zone
        sb.append("101")
        val parity = EAN_PARITY[full[0] - '0']
        for (i in 1..6) {
            val d = full[i] - '0'
            sb.append(if (parity[i - 1] == 'L') EAN_L[d] else EAN_G[d])
        }
        sb.append("01010")
        for (i in 7..12) {
            sb.append(EAN_R[full[i] - '0'])
        }
        sb.append("101")
        sb.append("0000000000")
        return Encoded(sb.toString(), full, "EAN-13")
    }

    /** UPC-A: 11 digits (+checksum) or 12 digits; encoded as EAN-13 with a leading 0. */
    fun encodeUpcA(input: String): Encoded? {
        val digits = input.filter { it.isDigit() }
        val payload = when (digits.length) {
            11 -> digits
            12 -> {
                // verify: UPC checksum over the first 11
                val p11 = digits.substring(0, 11)
                if (upcChecksum(p11) != digits[11] - '0') return null
                p11
            }
            else -> return null
        }
        val ean = encodeEan13("0" + payload) ?: return null
        val full = payload + upcChecksum(payload).toString()
        return Encoded(ean.modules, full, "UPC-A")
    }

    private fun upcChecksum(first11: String): Int {
        var sum = 0
        for (i in first11.indices) {
            val d = first11[i] - '0'
            sum += if (i % 2 == 0) d * 3 else d // UPC: odd positions (1-based) ×3
        }
        return (10 - (sum % 10)) % 10
    }

    // ── Code 39 ─────────────────────────────────────────────────────────────
    private val C39 = mapOf(
        '0' to "nnnwwnwnn", '1' to "wnnwnnnnw", '2' to "nnwwnnnnw", '3' to "wnwwnnnnn",
        '4' to "nnnwwnnnw", '5' to "wnnwwnnnn", '6' to "nnwwwnnnn", '7' to "nnnwnnwnw",
        '8' to "wnnwnnwnn", '9' to "nnwwnnwnn", 'A' to "wnnnnwnnw", 'B' to "nnwnnwnnw",
        'C' to "wnwnnwnnn", 'D' to "nnnnwwnnw", 'E' to "wnnnwwnnn", 'F' to "nnwnwwnnn",
        'G' to "nnnnnwwnw", 'H' to "wnnnnwwnn", 'I' to "nnwnnwwnn", 'J' to "nnnnwwwnn",
        'K' to "wnnnnnnww", 'L' to "nnwnnnnww", 'M' to "wnwnnnnwn", 'N' to "nnnnwnnww",
        'O' to "wnnnwnnwn", 'P' to "nnwnwnnwn", 'Q' to "nnnnnnwww", 'R' to "wnnnnnwwn",
        'S' to "nnwnnnwwn", 'T' to "nnnnwnwwn", 'U' to "wwnnnnnnw", 'V' to "nwwnnnnnw",
        'W' to "wwwnnnnnn", 'X' to "nwnnwnnnw", 'Y' to "wwnnwnnnn", 'Z' to "nwwnwnnnn",
        '-' to "nwnnnnwnw", '.' to "wwnnnnwnn", ' ' to "nwwnnnwnn", '$' to "nwnwnwnnn",
        '/' to "nwnwnnnwn", '+' to "nwnnnwnwn", '%' to "nnnwnwnwn", '*' to "nwnnwnwnn"
    )

    fun encodeCode39(input: String): Encoded? {
        val text = input.uppercase()
        if (text.isEmpty() || text.any { it !in C39 }) return null
        val sb = StringBuilder()
        sb.append("0000000000")
        val full = "*$text*"
        for ((idx, ch) in full.withIndex()) {
            if (idx > 0) sb.append('0') // inter-character gap (narrow space)
            val pattern = C39.getValue(ch)
            var bar = true
            for (p in pattern) {
                val width = if (p == 'w') 2 else 1
                repeat(width) { sb.append(if (bar) '1' else '0') }
                bar = !bar
            }
        }
        sb.append("0000000000")
        return Encoded(sb.toString(), text, "Code 39")
    }

    // ── Code 128 ────────────────────────────────────────────────────────────
    // Width patterns for values 0..106 (bar,space,bar,space,bar,space widths).
    private val C128_PATTERNS = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112"
    )

    private const val C128_START_B = 104
    private const val C128_START_C = 105
    private const val C128_CODE_C = 99
    private const val C128_CODE_B = 100
    private const val C128_STOP = 106

    /**
     * Encodes text (printable ASCII 32..126) as Code 128 with automatic
     * B/C code-set selection: runs of at least 4 digits use the compact
     * digit-pair set C. Returns null for unsupported characters.
     */
    fun encodeCode128(input: String): Encoded? {
        if (input.isEmpty() || input.any { it.code < 32 || it.code > 126 }) return null
        val values = mutableListOf<Int>()
        var i = 0
        fun digitRun(from: Int): Int {
            var n = 0
            while (from + n < input.length && input[from + n].isDigit()) n++
            return n
        }
        // Decide the start set: C when the text starts with an even run of
        // >= 4 digits (or is entirely digits of even length >= 2).
        var inC = false
        val startRun = digitRun(0)
        if ((startRun >= 4 && startRun % 2 == 0) || (startRun == input.length && startRun >= 2 && startRun % 2 == 0)) {
            values.add(C128_START_C)
            inC = true
        } else {
            values.add(C128_START_B)
        }
        while (i < input.length) {
            if (inC) {
                if (i + 1 < input.length && input[i].isDigit() && input[i + 1].isDigit()) {
                    values.add((input[i] - '0') * 10 + (input[i + 1] - '0'))
                    i += 2
                } else {
                    values.add(C128_CODE_B)
                    inC = false
                }
            } else {
                val run = digitRun(i)
                // Switch to C for a run of >= 4 digits; when the run is odd,
                // emit one digit in B first so the rest pairs up evenly.
                if (run >= 4) {
                    if (run % 2 == 1) {
                        values.add(input[i].code - 32)
                        i++
                    }
                    values.add(C128_CODE_C)
                    inC = true
                } else {
                    values.add(input[i].code - 32)
                    i++
                }
            }
        }
        // Checksum: start value + sum(value_k * position_k), positions from 1.
        var sum = values[0]
        for (k in 1 until values.size) sum += values[k] * k
        values.add(sum % 103)
        values.add(C128_STOP)

        val sb = StringBuilder()
        sb.append("0000000000")
        for (v in values) {
            val pattern = C128_PATTERNS[v]
            var bar = true
            for (ch in pattern) {
                repeat(ch - '0') { sb.append(if (bar) '1' else '0') }
                bar = !bar
            }
        }
        sb.append("0000000000")
        return Encoded(sb.toString(), input, "Code 128")
    }

    fun encode(kind: String, input: String): Encoded? = when (kind) {
        "Code 128" -> encodeCode128(input)
        "EAN-13" -> encodeEan13(input)
        "UPC-A" -> encodeUpcA(input)
        "Code 39" -> encodeCode39(input)
        else -> null
    }

    fun hintFor(kind: String): String = when (kind) {
        "Code 128" -> "Any text, numbers or symbols (letters, digits, punctuation)."
        "EAN-13" -> "12 digits (checksum is added) or a full 13-digit code."
        "UPC-A" -> "11 digits (checksum is added) or a full 12-digit code."
        "Code 39" -> "Uppercase letters, digits and - . $ / + % space."
        else -> ""
    }
}
