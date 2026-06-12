package com.chatmod.mobile.runtime

import com.chatmod.mobile.youtube.YouTubeApiRetryException
import com.chatmod.mobile.youtube.YouTubeAuthenticationException
import com.chatmod.mobile.youtube.YouTubePermissionException

data class BotRuntimeFailureDecision(
    val eventType: String,
    val message: String,
    val terminal: Boolean,
    val retryDelayMillis: Long? = null,
    val retryAfterMillis: Long? = null
)

class BotRuntimeFailurePolicy(
    private val retryPolicy: BotRetryPolicy = BotRetryPolicy()
) {
    fun decide(error: Throwable, consecutiveFailures: Int): BotRuntimeFailureDecision {
        return when (error) {
            is YouTubePermissionException -> terminal(
                eventType = "runtime_youtube_permission_blocked",
                error = error
            )
            is YouTubeAuthenticationException -> terminal(
                eventType = "runtime_youtube_auth_required",
                error = error
            )
            is YouTubeApiRetryException -> retry(
                eventType = "runtime_youtube_backoff_scheduled",
                error = error,
                consecutiveFailures = consecutiveFailures,
                retryAfterMillis = error.retryAfterMillis
            )
            else -> retry(
                eventType = "runtime_reconnect_scheduled",
                error = error,
                consecutiveFailures = consecutiveFailures
            )
        }
    }

    private fun terminal(eventType: String, error: Throwable): BotRuntimeFailureDecision {
        return BotRuntimeFailureDecision(
            eventType = eventType,
            message = error.runtimeMessage(),
            terminal = true
        )
    }

    private fun retry(
        eventType: String,
        error: Throwable,
        consecutiveFailures: Int,
        retryAfterMillis: Long? = null
    ): BotRuntimeFailureDecision {
        return BotRuntimeFailureDecision(
            eventType = eventType,
            message = error.runtimeMessage(),
            terminal = false,
            retryDelayMillis = retryPolicy.retryDelayMillis(error, consecutiveFailures),
            retryAfterMillis = retryAfterMillis
        )
    }

    private fun Throwable.runtimeMessage(): String {
        return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
    }
}
