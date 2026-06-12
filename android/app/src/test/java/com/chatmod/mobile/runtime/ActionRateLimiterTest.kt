package com.chatmod.mobile.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionRateLimiterTest {
    @Test
    fun blocksRepeatedActionInsideWindow() {
        val limiter = ActionRateLimiter(minimumIntervalMillis = 1000)

        assertTrue(limiter.allow("deleteMessage", nowMillis = 1000))
        assertFalse(limiter.allow("deleteMessage", nowMillis = 1500))
        assertTrue(limiter.allow("deleteMessage", nowMillis = 2100))
    }

    @Test
    fun blocksSharedOutboundMessageKeyAcrossDifferentSenders() {
        val limiter = ActionRateLimiter(minimumIntervalMillis = 1000)

        assertTrue(limiter.allowAll("sendMessage", "sendCommand:discord", nowMillis = 1000))
        assertFalse(limiter.allowAll("sendMessage", "sendTimer:rules", nowMillis = 1500))
        assertTrue(limiter.allowAll("sendMessage", "sendTimer:rules", nowMillis = 2100))
    }

    @Test
    fun doesNotConsumeKeysWhenMultiKeyCheckIsBlocked() {
        val limiter = ActionRateLimiter(minimumIntervalMillis = 1000)

        assertTrue(limiter.allow("sendMessage", nowMillis = 1000))
        assertFalse(limiter.allowAll("sendMessage", "sendTimer:rules", nowMillis = 1500))
        assertTrue(limiter.allowAll("sendMessage", "sendTimer:rules", nowMillis = 2100))
    }
}
