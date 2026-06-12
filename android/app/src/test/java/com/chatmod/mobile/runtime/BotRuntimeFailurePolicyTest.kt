package com.chatmod.mobile.runtime

import com.chatmod.mobile.youtube.YouTubePermissionException
import com.chatmod.mobile.youtube.YouTubeRateLimitException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BotRuntimeFailurePolicyTest {
    @Test
    fun schedulesRetryForGenericFailures() {
        val decision = BotRuntimeFailurePolicy().decide(IllegalStateException("network failed"), 2)

        assertFalse(decision.terminal)
        assertEquals("runtime_reconnect_scheduled", decision.eventType)
        assertEquals(4_000L, decision.retryDelayMillis)
    }

    @Test
    fun schedulesYouTubeBackoffForRateLimits() {
        val decision = BotRuntimeFailurePolicy().decide(
            YouTubeRateLimitException(retryAfterMillis = 45_000L),
            1
        )

        assertFalse(decision.terminal)
        assertEquals("runtime_youtube_backoff_scheduled", decision.eventType)
        assertEquals(45_000L, decision.retryDelayMillis)
        assertEquals(45_000L, decision.retryAfterMillis)
    }

    @Test
    fun stopsForYouTubePermissionFailures() {
        val decision = BotRuntimeFailurePolicy().decide(YouTubePermissionException(), 1)

        assertTrue(decision.terminal)
        assertEquals("runtime_youtube_permission_blocked", decision.eventType)
        assertNull(decision.retryDelayMillis)
    }
}
