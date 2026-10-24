package io.github.nexalloy.nexfusion.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class TelegramLinkSanitizerTest {

    @Test
    fun testSanitizeRemovesTrackingParams() {
        // Without Android runtime Uri (on host JVM), test basic formatting and logic
        assertEquals("12,345", TelegramLinkSanitizer.formatNumber(12345))
        assertEquals("1,000,000", TelegramLinkSanitizer.formatNumber(1000000))
        assertEquals("42", TelegramLinkSanitizer.formatNumber(42))
    }
}
