package com.chatmod.mobile.runtime

import com.chatmod.mobile.youtube.YouTubeQuotaExceededException
import com.chatmod.mobile.youtube.YouTubeRateLimitException
import org.junit.Assert.assertEquals
import org.junit.Test

class BotRetryPolicyTest {
    @Test
    fun usesExponentialBackoffForGenericFailures() {
        val policy = BotRetryPolicy()

        assertEquals(2_000L, policy.retryDelayMillis(IllegalStateException("offline"), 1))
        assertEquals(4_000L, policy.retryDelayMillis(IllegalStateException("offline"), 2))
        assertEquals(30_000L, policy.retryDelayMillis(IllegalStateException("offline"), 9))
    }

    @Test
    fun usesRetryAfterForYouTubeRateLimits() {
        val policy = BotRetryPolicy()

        assertEquals(
            45_000L,
            policy.retryDelayMillis(YouTubeRateLimitException(retryAfterMillis = 45_000L), 1)
        )
    }

    @Test
    fun usesDefaultBackoffForYouTubeRetryErrorsWithoutRetryAfter() {
        val policy = BotRetryPolicy(defaultYouTubeBackoffMillis = 30_000L)

        assertEquals(30_000L, policy.retryDelayMillis(YouTubeRateLimitException(), 1))
    }

    @Test
    fun clampsYouTubeRetryAfterToProtectTheRuntimeLoop() {
        val policy = BotRetryPolicy(
            minYouTubeBackoffMillis = 1_000L,
            maxYouTubeBackoffMillis = 120_000L
        )

        assertEquals(1_000L, policy.retryDelayMillis(YouTubeRateLimitException(retryAfterMillis = 0L), 1))
        assertEquals(
            120_000L,
            policy.retryDelayMillis(YouTubeQuotaExceededException(retryAfterMillis = 600_000L), 1)
        )
    }
}
