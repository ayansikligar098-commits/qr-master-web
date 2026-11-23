package qrmaster.web.ui

import qrmaster.web.qr.QrCode

/** Minimal pure-Kotlin PNG encoder (grayscale, zlib stored blocks). No dependencies. */
object PngEncoder {

    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1 }
        c
    }

    private fun crc32(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var c = -1
        for (i in offset until offset + length) {
            c = CRC_TABLE[(c xor data[i].toInt()) and 0xFF] xor (c ushr 8)
        }
        return c xor -1
    }

    private fun adler32(data: ByteArray): Long {
        var a = 1L
        var b = 0L
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        return (b shl 16) or a
    }

    private class ByteSink {
        private val list = mutableListOf<Byte>()
        fun b(v: Int) { list.add(v.toByte()) }
        fun bytes(arr: ByteArray) { for (x in arr) list.add(x) }
        fun u32(v: Int) { b(v ushr 24); b(v ushr 16); b(v ushr 8); b(v) }
        fun u16(v: Int) { b(v ushr 8); b(v) }
        fun toByteArray(): ByteArray = ByteArray(list.size) { list[it] }
    }

    private fun chunk(out: ByteSink, type: String, data: ByteArray) {
        out.u32(data.size)
        val typeBytes = type.encodeToByteArray()
        out.bytes(typeBytes)
        out.bytes(data)
        val crcData = ByteArray(typeBytes.size + data.size)
        typeBytes.copyInto(crcData, 0)
        data.copyInto(crcData, typeBytes.size)
        out.u32(crc32(crcData))
    }

    /** Encodes a grayscale image. pixel(x, y) returns 0 (black) .. 255 (white). */
    fun encodeGray(width: Int, height: Int, pixel: (Int, Int) -> Int): ByteArray {
        // Raw image data: filter byte 0 per row
        val raw = ByteArray((width + 1) * height)
        var p = 0
        for (y in 0 until height) {
            raw[p++] = 0
            for (x in 0 until width) {
                raw[p++] = pixel(x, y).toByte()
            }
        }
        // zlib stream with stored (uncompressed) DEFLATE blocks
        val zlib = ByteSink()
        zlib.b(0x78); zlib.b(0x01)
        var offset = 0
        while (true) {
            val len = minOf(65535, raw.size - offset)
            val last = offset + len >= raw.size
            zlib.b(if (last) 1 else 0)
            zlib.u16le(len)
            zlib.u16le(len xor 0xFFFF)
            for (i in 0 until len) zlib.b(raw[offset + i].toInt())
            offset += len
            if (last) break
        }
        zlib.u32(adler32(raw).toInt())

        val ihdr = ByteSink()
        ihdr.u32(width); ihdr.u32(height)
        ihdr.b(8)  // bit depth
        ihdr.b(0)  // color type grayscale
        ihdr.b(0); ihdr.b(0); ihdr.b(0)

        val out = ByteSink()
        out.bytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        chunk(out, "IHDR", ihdr.toByteArray())
        chunk(out, "IDAT", zlib.toByteArray())
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun ByteSink.u16le(v: Int) { b(v); b(v ushr 8) }

    /** Renders a QR code to a PNG of about targetPx pixels (exact size depends on module count). */
    fun encodeQr(qr: QrCode, targetPx: Int = 1024, borderModules: Int = 4): ByteArray {
        val total = qr.size + borderModules * 2
        val scale = maxOf(1, targetPx / total)
        val dim = total * scale
        return encodeGray(dim, dim) { x, y ->
            val mx = x / scale - borderModules
            val my = y / scale - borderModules
            if (mx in 0 until qr.size && my in 0 until qr.size && qr.getModule(mx, my)) 0 else 255
        }
    }
}
