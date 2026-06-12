package com.chatmod.mobile.runtime

import com.chatmod.mobile.youtube.YouTubeApiRetryException

class BotRetryPolicy(
    private val baseReconnectDelayMillis: Long = 2_000L,
    private val maxReconnectDelayMillis: Long = 30_000L,
    private val maxReconnectSteps: Int = 5,
    private val defaultYouTubeBackoffMillis: Long = 30_000L,
    private val minYouTubeBackoffMillis: Long = 1_000L,
    private val maxYouTubeBackoffMillis: Long = 15 * 60_000L
) {
    init {
        require(baseReconnectDelayMillis > 0) { "baseReconnectDelayMillis must be positive" }
        require(maxReconnectDelayMillis >= baseReconnectDelayMillis) {
            "maxReconnectDelayMillis must be at least baseReconnectDelayMillis"
        }
        require(maxReconnectSteps > 0) { "maxReconnectSteps must be positive" }
        require(defaultYouTubeBackoffMillis > 0) { "defaultYouTubeBackoffMillis must be positive" }
        require(minYouTubeBackoffMillis > 0) { "minYouTubeBackoffMillis must be positive" }
        require(maxYouTubeBackoffMillis >= minYouTubeBackoffMillis) {
            "maxYouTubeBackoffMillis must be at least minYouTubeBackoffMillis"
        }
    }

    fun retryDelayMillis(error: Throwable, consecutiveFailures: Int): Long {
        val youtubeRetry = error as? YouTubeApiRetryException
        if (youtubeRetry != null) {
            return youtubeBackoffDelayMillis(youtubeRetry.retryAfterMillis)
        }

        return reconnectDelayMillis(consecutiveFailures)
    }

    fun reconnectDelayMillis(consecutiveFailures: Int): Long {
        val multiplier = 1L shl (consecutiveFailures.coerceIn(1, maxReconnectSteps) - 1)
        return (baseReconnectDelayMillis * multiplier).coerceAtMost(maxReconnectDelayMillis)
    }

    private fun youtubeBackoffDelayMillis(retryAfterMillis: Long?): Long {
        return (retryAfterMillis ?: defaultYouTubeBackoffMillis)
            .coerceAtLeast(minYouTubeBackoffMillis)
            .coerceAtMost(maxYouTubeBackoffMillis)
    }
}
