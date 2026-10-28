package io.github.nexalloy.nexfusion.linkedin

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

// Kept in its own file so unit tests can load it without the patcher runtime, like LinkCleaner.

/** Byte-level ASCII search, the check upstream's ProtoScanner.containsAscii does on the payload. */
internal fun containsAscii(haystack: ByteArray, needle: String): Boolean {
    if (needle.isEmpty() || haystack.size < needle.length) return false
    outer@ for (start in 0..haystack.size - needle.length) {
        for (offset in needle.indices) {
            if (haystack[start + offset] != needle[offset].code.toByte()) continue@outer
        }
        return true
    }
    return false
}

/**
 * Checks whether any length-delimited string in the serialized protobuf matches any of [values] exactly.
 */
internal fun hasExactString(data: ByteArray?, vararg values: String): Boolean {
    if (data == null || values.isEmpty()) return false
    val strings = scanStrings(data)
    for (s in strings) {
        for (v in values) {
            if (s == v) return true
        }
    }
    return false
}

private const val MAX_PROTO_DEPTH = 48

internal fun scanStrings(data: ByteArray): List<String> {
    val results = mutableListOf<String>()
    parseProtoMessage(data, 0, data.size, 0, results)
    return results
}

private fun parseProtoMessage(
    b: ByteArray,
    start: Int,
    end: Int,
    depth: Int,
    outStrings: MutableList<String>
): Boolean {
    if (depth > MAX_PROTO_DEPTH) return false
    val foundStrings = mutableListOf<String>()
    var p = start
    while (p < end) {
        val tagResult = readVarint(b, p, end) ?: return false
        val tag = tagResult[0]
        p = tagResult[1].toInt()
        val field = (tag ushr 3).toInt()
        val wireType = (tag and 7L).toInt()
        if (field <= 0) return false

        when (wireType) {
            0 -> {
                val v = readVarint(b, p, end) ?: return false
                p = v[1].toInt()
            }
            1 -> p += 8
            5 -> p += 4
            2 -> {
                val lenResult = readVarint(b, p, end) ?: return false
                val len = lenResult[0]
                p = lenResult[1].toInt()
                if (len < 0 || len > end - p) return false
                val subEnd = p + len.toInt()
                val s = printableUtf8(b, p, subEnd)
                if (s != null) {
                    foundStrings.add(s)
                } else {
                    val childStrings = mutableListOf<String>()
                    var success = parseProtoMessage(b, p, subEnd, depth + 1, childStrings)
                    if (!success && subEnd > p + 1 && b[p] == 0.toByte()) {
                        childStrings.clear()
                        success = parseProtoMessage(b, p + 1, subEnd, depth + 1, childStrings)
                    }
                    if (success) {
                        foundStrings.addAll(childStrings)
                    } else {
                        salvageStrings(b, p, subEnd, foundStrings)
                    }
                }
                p = subEnd
            }
            else -> return false
        }
        if (p > end) return false
    }
    outStrings.addAll(foundStrings)
    return true
}

private fun salvageStrings(b: ByteArray, start: Int, end: Int, out: MutableList<String>) {
    var i = start
    while (i < end - 2) {
        val tag = b[i].toInt() and 0xff
        if ((tag and 7) == 2 && (tag ushr 3) > 0) {
            val lenResult = readVarint(b, i + 1, end)
            if (lenResult != null && lenResult[0] >= 1 && lenResult[0] <= end - lenResult[1]) {
                val strStart = lenResult[1].toInt()
                val strEnd = strStart + lenResult[0].toInt()
                val s = printableUtf8(b, strStart, strEnd)
                if (s != null) {
                    out.add(s)
                    i = strEnd
                    continue
                }
            }
        }
        i++
    }
}

private fun readVarint(b: ByteArray, start: Int, end: Int): LongArray? {
    var p = start
    var result = 0L
    var shift = 0
    while (shift < 64) {
        if (p >= end) return null
        val cur = b[p++]
        result = result or ((cur.toLong() and 0x7fL) shl shift)
        if ((cur.toInt() and 0x80) == 0) {
            return longArrayOf(result, p.toLong())
        }
        shift += 7
    }
    return null
}

private fun printableUtf8(b: ByteArray, start: Int, end: Int): String? {
    if (end <= start) return null
    for (i in start until end) {
        val c = b[i].toInt() and 0xff
        if (c < 0x20 || c == 0x7f) return null
    }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return try {
        decoder.decode(ByteBuffer.wrap(b, start, end - start)).toString()
    } catch (_: Exception) {
        null
    }
}
