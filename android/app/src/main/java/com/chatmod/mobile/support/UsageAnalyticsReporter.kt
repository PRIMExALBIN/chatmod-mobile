package com.chatmod.mobile.support

import com.chatmod.mobile.BuildConfig
import com.chatmod.mobile.data.local.SettingsStore
import com.chatmod.mobile.data.remote.AnalyticsEventRequest
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModHttpException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant

class UsageAnalyticsReporter(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val api: ChatModApiClient,
    private val accessTokenProvider: suspend () -> String?,
    private val refreshAccessTokenProvider: suspend () -> String?
) {
    fun track(name: String, properties: Map<String, Any?> = emptyMap()) {
        scope.launch {
            if (!settingsStore.currentSettings().shareUsageAnalytics) {
                return@launch
            }

            val safeProperties = properties
                .filterKeys { key -> key.matches(SafePropertyKey) && !SensitivePropertyKey.containsMatchIn(key) }
                .mapValues { (_, value) -> value.toAnalyticsValue() }
                .filterValues { value -> value !== UnsupportedAnalyticsValue }

            val request = AnalyticsEventRequest(
                name = name,
                consent = true,
                occurredAt = Instant.now().toString(),
                appVersion = BuildConfig.VERSION_NAME,
                properties = safeProperties
            )

            send(request)
        }
    }

    private suspend fun send(request: AnalyticsEventRequest): Boolean {
        val accessToken = accessTokenProvider() ?: return false
        val first = runCatching { api.recordAnalyticsEvent(accessToken, request) }
        if (first.isSuccess) return true
        if (!first.exceptionOrNull().isUnauthorized()) return false

        val refreshedAccessToken = refreshAccessTokenProvider() ?: return false
        return runCatching { api.recordAnalyticsEvent(refreshedAccessToken, request) }.isSuccess
    }

    private companion object {
        val SafePropertyKey = Regex("""^[a-zA-Z0-9_.-]{1,48}$""")
        val SensitivePropertyKey = Regex("""(token|secret|password|email|message|chat|text|body|url)""", RegexOption.IGNORE_CASE)
    }
}

private fun Any?.toAnalyticsValue(): Any? {
    return when (this) {
        null -> null
        is String -> take(160)
        is Double -> if (isFinite()) this else UnsupportedAnalyticsValue
        is Float -> if (isFinite()) this else UnsupportedAnalyticsValue
        is Number -> this
        is Boolean -> this
        else -> UnsupportedAnalyticsValue
    }
}

private val UnsupportedAnalyticsValue = Any()

private fun Throwable?.isUnauthorized(): Boolean {
    return this is ChatModHttpException && statusCode == 401
}
