/*
 * QR Code generator library (Kotlin port)
 *
 * Copyright (c) Project Nayuki. (MIT License)
 * https://www.nayuki.io/page/qr-code-generator-library
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 * Faithful Kotlin port of Nayuki's QrCode/QrSegment/BitBuffer (Java version),
 * covering QR Code Model 2, versions 1-40, all error correction levels.
 */
package qrmaster.web.qr

import kotlin.math.abs
import kotlin.math.ceil

class DataTooLongException(msg: String) : RuntimeException(msg)

enum class QrEcc(val formatBits: Int) {
    LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2)
}

class QrSegment(val mode: Mode, val numChars: Int, val data: BooleanArray) {
    enum class Mode(val modeBits: Int, private val numBitsCharCount: IntArray) {
        NUMERIC(0x1, intArrayOf(10, 12, 14)),
        ALPHANUMERIC(0x2, intArrayOf(9, 11, 13)),
        BYTE(0x4, intArrayOf(8, 16, 16)),
        KANJI(0x8, intArrayOf(8, 10, 12)),
        ECI(0x7, intArrayOf(0, 0, 0));

        fun numCharCountBits(ver: Int): Int {
            return numBitsCharCount[(ver + 7) / 17]
        }
    }

    fun getTotalBits(version: Int): Long {
        val ccbits = mode.numCharCountBits(version)
        if (numChars >= (1 shl ccbits)) return -1
        var result = 4L + ccbits + data.size
        return result
    }

    companion object {
        fun makeBytes(data: ByteArray): QrSegment {
            val bb = BooleanArray(data.size * 8)
            for (i in data.indices) {
                for (j in 0..7) {
                    bb[i * 8 + j] = ((data[i].toInt() ushr (7 - j)) and 1) != 0
                }
            }
            return QrSegment(Mode.BYTE, data.size, bb)
        }

        fun makeNumeric(text: String): QrSegment {
            val bb = BitBuffer()
            var accumData = 0
            var accumCount = 0
            for (c in text) {
                require(c in '0'..'9') { "String contains non-numeric characters" }
                accumData = accumData * 10 + (c - '0')
                accumCount++
                if (accumCount == 3) {
                    bb.appendBits(accumData, 10)
                    accumData = 0; accumCount = 0
                }
            }
            if (accumCount > 0) bb.appendBits(accumData, if (accumCount == 1) 4 else 7)
            return QrSegment(Mode.NUMERIC, text.length, bb.toBooleanArray())
        }

        fun makeAlphanumeric(text: String): QrSegment {
            val bb = BitBuffer()
            var accumData = 0
            var accumCount = 0
            for (c in text) {
                val idx = ALPHANUMERIC_CHARSET.indexOf(c)
                require(idx != -1) { "String contains unencodable characters in alphanumeric mode" }
                accumData = accumData * 45 + idx
                accumCount++
                if (accumCount == 2) {
                    bb.appendBits(accumData, 11)
                    accumData = 0; accumCount = 0
                }
            }
            if (accumCount > 0) bb.appendBits(accumData, 6)
            return QrSegment(Mode.ALPHANUMERIC, text.length, bb.toBooleanArray())
        }

        fun makeSegments(text: String): List<QrSegment> {
            if (text.isEmpty()) return listOf()
            if (isNumeric(text)) return listOf(makeNumeric(text))
            if (isAlphanumeric(text)) return listOf(makeAlphanumeric(text))
            return listOf(makeBytes(text.encodeToByteArray()))
        }

        fun isNumeric(text: String): Boolean = text.all { it in '0'..'9' }

        fun isAlphanumeric(text: String): Boolean = text.all { ALPHANUMERIC_CHARSET.indexOf(it) != -1 }

        const val ALPHANUMERIC_CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"
    }
}

class BitBuffer {
    private val bits = mutableListOf<Boolean>()
    val bitLength: Int get() = bits.size

    fun appendBits(value: Int, len: Int) {
        require(len in 0..31 && value ushr len == 0) { "Value out of range" }
        for (i in len - 1 downTo 0) bits.add(((value ushr i) and 1) != 0)
    }

    fun appendData(data: BooleanArray) {
        for (b in data) bits.add(b)
    }

    fun getBit(index: Int): Int = if (bits[index]) 1 else 0

    fun toBooleanArray(): BooleanArray = BooleanArray(bits.size) { bits[it] }
}

class QrCode private constructor(
    val version: Int,
    val errorCorrectionLevel: QrEcc,
    private val maskValue: Int,
    private val modules: Array<BooleanArray>
) {
    val size: Int = version * 4 + 17
    val mask: Int get() = maskValue

    fun getModule(x: Int, y: Int): Boolean {
        return x in 0 until size && y in 0 until size && modules[y][x]
    }

    companion object {
        const val MIN_VERSION = 1
        const val MAX_VERSION = 40

        fun encodeText(text: String, ecl: QrEcc): QrCode {
            val segs = QrSegment.makeSegments(text)
            return encodeSegments(segs, ecl)
        }

        fun encodeSegments(segs: List<QrSegment>, eclIn: QrEcc): QrCode {
            return encodeSegments(segs, eclIn, MIN_VERSION, MAX_VERSION, -1, true)
        }

        fun encodeSegments(segs: List<QrSegment>, eclIn: QrEcc, minVersion: Int, maxVersion: Int, mask: Int, boostEcl: Boolean): QrCode {
            require(MIN_VERSION <= minVersion && minVersion <= maxVersion && maxVersion <= MAX_VERSION && mask in -1..7) { "Invalid value" }
            var ecl = eclIn
            var version = minVersion
            var dataUsedBits: Long
            while (true) {
                val dataCapacityBits = getNumDataCodewords(version, ecl) * 8
                dataUsedBits = getTotalBits(segs, version)
                if (dataUsedBits != -1L && dataUsedBits <= dataCapacityBits) break
                if (version >= maxVersion) {
                    val msg = if (dataUsedBits != -1L) "Data length = $dataUsedBits bits, Max capacity = $dataCapacityBits bits" else "Segment too long"
                    throw DataTooLongException(msg)
                }
                version++
            }
            for (newEcl in QrEcc.values()) {
                if (boostEcl && dataUsedBits <= getNumDataCodewords(version, newEcl) * 8) ecl = newEcl
            }
            val bb = BitBuffer()
            for (seg in segs) {
                bb.appendBits(seg.mode.modeBits, 4)
                bb.appendBits(seg.numChars, seg.mode.numCharCountBits(version))
                bb.appendData(seg.data)
            }
            val dataCapacityBits = getNumDataCodewords(version, ecl) * 8
            bb.appendBits(0, minOf(4, dataCapacityBits - bb.bitLength))
            bb.appendBits(0, (8 - bb.bitLength % 8) % 8)
            var padByte = 0xEC
            while (bb.bitLength < dataCapacityBits) {
                bb.appendBits(padByte, 8)
                padByte = padByte xor 0xEC xor 0x11
            }
            val dataCodewords = ByteArray(bb.bitLength / 8)
            for (i in 0 until bb.bitLength) {
                dataCodewords[i ushr 3] = (dataCodewords[i ushr 3].toInt() or (bb.getBit(i) shl (7 - (i and 7)))).toByte()
            }
            return buildQr(version, ecl, dataCodewords, mask)
        }

        private fun getTotalBits(segs: List<QrSegment>, version: Int): Long {
            var result = 0L
            for (seg in segs) {
                val t = seg.getTotalBits(version)
                if (t == -1L) return -1
                result += t
                if (result > Int.MAX_VALUE) return -1
            }
            return result
        }

        private fun buildQr(ver: Int, ecl: QrEcc, dataCodewords: ByteArray, mskIn: Int): QrCode {
            val size = ver * 4 + 17
            val modules = Array(size) { BooleanArray(size) }
            val isFunction = Array(size) { BooleanArray(size) }
            var msk = mskIn

            fun setFunctionModule(x: Int, y: Int, isDark: Boolean) {
                if (x in 0 until size && y in 0 until size) {
                    modules[y][x] = isDark
                    isFunction[y][x] = true
                }
            }

            // Timing patterns
            for (i in 0 until size) {
                setFunctionModule(6, i, i % 2 == 0)
                setFunctionModule(i, 6, i % 2 == 0)
            }
            // Finder patterns
            fun drawFinderPattern(x: Int, y: Int) {
                for (dy in -4..4) {
                    for (dx in -4..4) {
                        val dist = maxOf(abs(dx), abs(dy))
                        val xx = x + dx; val yy = y + dy
                        if (xx in 0 until size && yy in 0 until size)
                            setFunctionModule(xx, yy, dist != 2 && dist != 4)
                    }
                }
            }
            drawFinderPattern(3, 3)
            drawFinderPattern(size - 4, 3)
            drawFinderPattern(3, size - 4)

            // Alignment patterns
            val alignPos = getAlignmentPatternPositions(ver, size)
            val num = alignPos.size
            for (i in 0 until num) {
                for (j in 0 until num) {
                    if (!(i == 0 && j == 0 || i == 0 && j == num - 1 || i == num - 1 && j == 0)) {
                        val cx = alignPos[j]; val cy = alignPos[i]
                        for (dy in -2..2) for (dx in -2..2)
                            setFunctionModule(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
                    }
                }
            }

            // Dummy format bits (drawn for real later)
            fun drawFormatBits(mask: Int) {
                val data = ecl.formatBits shl 3 or mask
                var rem = data
                for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
                val bits = (data shl 10 or rem) xor 0x5412
                for (i in 0..6) setFunctionModule(8, i, getBit(bits, i))
                for (i in 7..8) setFunctionModule(8, i, getBit(bits, i))
                for (i in 9..14) setFunctionModule(8, 14 - i, getBit(bits, i))
                for (i in 0..7) setFunctionModule(size - 1 - i, 8, getBit(bits, i))
                for (i in 8..14) setFunctionModule(8, size - 15 + i, getBit(bits, i))
                setFunctionModule(8, size - 8, true)
            }
            drawFormatBits(0)

            // Version info
            if (ver >= 7) {
                var rem = ver
                for (i in 0 until 12) rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25)
                val bits = ver shl 12 or rem
                for (i in 0 until 18) {
                    val bit = getBit(bits, i)
                    setFunctionModule(size - 11 + i % 3, i / 3, bit)
                    setFunctionModule(i / 3, size - 11 + i % 3, bit)
                }
            }

            // Add ECC and interleave
            val allCodewords = addEccAndInterleave(dataCodewords, ver, ecl)
            // Draw codewords
            run {
                var i = 0
                var right = size - 1
                while (right >= 1) {
                    if (right == 6) right = 5
                    for (vert in 0 until size) {
                        for (j in 0..1) {
                            val x = right - j
                            val upward = ((right + 1) and 2) == 0
                            val y = if (upward) size - 1 - vert else vert
                            if (!isFunction[y][x] && i < allCodewords.size * 8) {
                                modules[y][x] = ((allCodewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                                i++
                            }
                        }
                    }
                    right -= 2
                }
            }

            if (msk == -1) {
                var bestPenalty = Int.MAX_VALUE
                for (cand in 0..7) {
                    applyMask(modules, isFunction, size, cand)
                    drawFormatBitsReal(modules, size, ecl, cand)
                    val pen = getPenaltyScore(modules, size)
                    if (pen < bestPenalty) { msk = cand; bestPenalty = pen }
                    applyMask(modules, isFunction, size, cand) // undo
                }
            }
            applyMask(modules, isFunction, size, msk)
            drawFormatBitsReal(modules, size, ecl, msk)
            return QrCode(ver, ecl, msk, modules)
        }

        private fun drawFormatBitsReal(modules: Array<BooleanArray>, size: Int, ecl: QrEcc, mask: Int) {
            val data = ecl.formatBits shl 3 or mask
            var rem = data
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = (data shl 10 or rem) xor 0x5412
            fun set(x: Int, y: Int, v: Boolean) { if (x in 0 until size && y in 0 until size) modules[y][x] = v }
            for (i in 0..6) set(8, i, getBit(bits, i))
            for (i in 7..8) set(8, i, getBit(bits, i))
            for (i in 9..14) set(8, 14 - i, getBit(bits, i))
            for (i in 0..7) set(size - 1 - i, 8, getBit(bits, i))
            for (i in 8..14) set(8, size - 15 + i, getBit(bits, i))
            set(8, size - 8, true)
        }

        private fun applyMask(modules: Array<BooleanArray>, isFunction: Array<BooleanArray>, size: Int, mask: Int) {
            for (y in 0 until size) {
                for (x in 0 until size) {
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> (x * y) % 2 + (x * y) % 3 == 0
                        6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                        7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                        else -> throw IllegalArgumentException()
                    }
                    if (invert && !isFunction[y][x]) modules[y][x] = !modules[y][x]
                }
            }
        }

        private fun getPenaltyScore(modules: Array<BooleanArray>, size: Int): Int {
            var result = 0
            val runHistory = IntArray(7)
            // Rule 1: rows
            for (y in 0 until size) {
                var runColor = false
                var runX = 0
                runHistory.fill(0)
                for (x in 0 until size) {
                    if (modules[y][x] == runColor) {
                        runX++
                        if (runX == 5) result += PENALTY_N1
                        else if (runX > 5) result++
                    } else {
                        pushRunHistory(runHistory, runX)
                        if (!runColor) result += getPenaltyRule3(runHistory, size) * PENALTY_N3
                        runColor = modules[y][x]
                        runX = 1
                    }
                }
                result += terminateAndGetPenalty(runHistory, runColor, runX, size) * PENALTY_N3
            }
            // Rule 1: columns
            for (x in 0 until size) {
                var runColor = false
                var runY = 0
                runHistory.fill(0)
                for (y in 0 until size) {
                    if (modules[y][x] == runColor) {
                        runY++
                        if (runY == 5) result += PENALTY_N1
                        else if (runY > 5) result++
                    } else {
                        pushRunHistory(runHistory, runY)
                        if (!runColor) result += getPenaltyRule3(runHistory, size) * PENALTY_N3
                        runColor = modules[y][x]
                        runY = 1
                    }
                }
                result += terminateAndGetPenalty(runHistory, runColor, runY, size) * PENALTY_N3
            }
            // Rule 2: 2x2 blocks
            for (y in 0 until size - 1) {
                for (x in 0 until size - 1) {
                    val c = modules[y][x]
                    if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += PENALTY_N2
                }
            }
            // Rule 4: balance
            var black = 0
            for (row in modules) for (b in row) if (b) black++
            val total = size * size
            val k = (abs(black * 20L - total * 10L) + total - 1) / total - 1
            result += (k * PENALTY_N4).toInt()
            return result
        }

        private fun terminateAndGetPenalty(runHistory: IntArray, runColor: Boolean, runXIn: Int, size: Int): Int {
            var runX = runXIn
            if (runColor) {
                pushRunHistory(runHistory, runX)
                runX = 0
            }
            pushRunHistory(runHistory, runX + size)
            return getPenaltyRule3(runHistory, size)
        }

        private fun getPenaltyRule3(runHistory: IntArray, size: Int): Int {
            val n = runHistory[1]
            if (n <= 0) return 0
            val core = runHistory[1] == runHistory[2] && runHistory[2] == runHistory[3] && runHistory[3] == runHistory[4] && runHistory[4] == runHistory[5]
            if (!core) return 0
            val beforeOk = runHistory[0] >= n * 4 && runHistory[6] >= n
            val afterOk = runHistory[6] >= n * 4 && runHistory[0] >= n
            return if (beforeOk || afterOk) 1 else 0
        }

        private fun pushRunHistory(runHistory: IntArray, value: Int) {
            for (i in runHistory.size - 1 downTo 1) runHistory[i] = runHistory[i - 1]
            runHistory[0] = value
        }

        private fun addEccAndInterleave(data: ByteArray, ver: Int, ecl: QrEcc): ByteArray {
            val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver]
            val blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver]
            val rawCodewords = getNumRawDataModules(ver) / 8
            val numShortBlocks = numBlocks - rawCodewords % numBlocks
            val shortBlockLen = rawCodewords / numBlocks
            val blocks = mutableListOf<ByteArray>()
            val divisor = reedSolomonComputeDivisor(blockEccLen)
            var k = 0
            for (i in 0 until numBlocks) {
                val datLen = shortBlockLen - blockEccLen + (if (i >= numShortBlocks) 1 else 0)
                val block = ByteArray(datLen)
                data.copyInto(block, 0, k, k + datLen)
                k += datLen
                val ecc = reedSolomonComputeRemainder(block, divisor)
                blocks.add(block)
                blocks.add(ecc) // placeholder order fixed below
            }
            // Interleave: blocks list currently alternates data,ecc — rebuild properly
            val datBlocks = mutableListOf<ByteArray>()
            val eccBlocks = mutableListOf<ByteArray>()
            k = 0
            for (i in 0 until numBlocks) {
                val datLen = shortBlockLen - blockEccLen + (if (i >= numShortBlocks) 1 else 0)
                val block = data.copyOfRange(k, k + datLen)
                k += datLen
                datBlocks.add(block)
                eccBlocks.add(reedSolomonComputeRemainder(block, divisor))
            }
            val result = ByteArray(rawCodewords)
            var idx = 0
            for (i in 0 until maxOf(shortBlockLen - blockEccLen + 1, 0)) {
                for ((j, blk) in datBlocks.withIndex()) {
                    if (i < blk.size) { result[idx] = blk[i]; idx++ }
                }
            }
            for (i in 0 until blockEccLen) {
                for (blk in eccBlocks) { result[idx] = blk[i]; idx++ }
            }
            return result
        }

        private fun reedSolomonComputeDivisor(degree: Int): IntArray {
            val result = IntArray(degree)
            result[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in result.indices) {
                    result[j] = reedSolomonMultiply(result[j], root)
                    if (j + 1 < result.size) result[j] = result[j] xor result[j + 1]
                }
                root = reedSolomonMultiply(root, 0x02)
            }
            return result
        }

        private fun reedSolomonComputeRemainder(data: ByteArray, divisor: IntArray): ByteArray {
            val result = IntArray(divisor.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor result[0]
                result.copyInto(result, 0, 1, result.size)
                result[result.size - 1] = 0
                for ((i, coef) in divisor.withIndex()) {
                    result[i] = result[i] xor reedSolomonMultiply(coef, factor)
                }
            }
            return ByteArray(result.size) { result[it].toByte() }
        }

        private fun reedSolomonMultiply(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                if (((y ushr i) and 1) != 0) z = z xor x
            }
            return z
        }

        internal fun getNumRawDataModules(ver: Int): Int {
            var result = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val numAlign = ver / 7 + 2
                result -= (25 * numAlign - 10) * numAlign - 55
                if (ver >= 7) result -= 36
            }
            return result
        }

        internal fun getNumDataCodewords(ver: Int, ecl: QrEcc): Int {
            return getNumRawDataModules(ver) / 8 - ECC_CODEWORDS_PER_BLOCK[ecl.ordinal][ver] * NUM_ERROR_CORRECTION_BLOCKS[ecl.ordinal][ver]
        }

        private fun getAlignmentPatternPositions(ver: Int, size: Int): IntArray {
            if (ver == 1) return intArrayOf()
            val num = ver / 7 + 2
            val step = if (ver == 32) 26 else (ver * 4 + num * 2 + 1) / (num * 2 - 2) * 2
            val result = IntArray(num)
            result[0] = 6
            var pos = size - 7
            for (i in num - 1 downTo 1) {
                result[i] = pos
                pos -= step
            }
            return result
        }

        private fun getBit(x: Int, i: Int): Boolean = ((x ushr i) and 1) != 0

        private const val PENALTY_N1 = 3
        private const val PENALTY_N2 = 3
        private const val PENALTY_N3 = 40
        private const val PENALTY_N4 = 10

        private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
            intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
            intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
            intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)
        )

        private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
            intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
            intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
            intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
            intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81)
        )
    }
}
