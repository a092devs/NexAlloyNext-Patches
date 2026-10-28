package io.github.nexalloy.nexfusion.linkedin

import kotlin.test.Test
import kotlin.test.assertEquals

class AsciiScanTest {

    @Test
    fun `finds the marker anywhere in the payload`() {
        val payload = byteArrayOf(0x12, 0x08) +
            "urn:li:sponsoredContentV2:".toByteArray() +
            byteArrayOf(0x00)
        assertEquals(true, containsAscii(payload, "urn:li:sponsoredContentV2:"))
    }

    @Test
    fun `matches at the last possible offset`() {
        assertEquals(true, containsAscii("abcmarker".toByteArray(), "marker"))
    }

    @Test
    fun `does not match a prefix or suffix alone`() {
        assertEquals(false, containsAscii("marke".toByteArray(), "marker"))
        assertEquals(false, containsAscii("arker".toByteArray(), "marker"))
    }

    @Test
    fun `empty needle and empty haystack match nothing`() {
        assertEquals(false, containsAscii("abc".toByteArray(), ""))
        assertEquals(false, containsAscii(ByteArray(0), "abc"))
    }

    @Test
    fun `hasExactString matches exact protobuf strings`() {
        val strBytes = "Suggested".toByteArray()
        val payload = byteArrayOf(0x0a, strBytes.size.toByte()) + strBytes
        assertEquals(true, hasExactString(payload, "Suggested", "Disarankan"))
        assertEquals(false, hasExactString(payload, "Suggest", "Disarankan"))
        assertEquals(false, hasExactString(null, "Suggested"))
    }
}
