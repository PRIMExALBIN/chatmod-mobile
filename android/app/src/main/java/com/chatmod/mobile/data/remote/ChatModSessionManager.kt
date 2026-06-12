package com.chatmod.mobile.data.remote

import android.content.Context
import android.provider.Settings
import com.chatmod.mobile.BuildConfig
import java.util.UUID

class ChatModSessionManager(
    private val context: Context,
    private val api: ChatModApiClient
) {
    private var accessToken: String? = null
    private var expiresAtMillis: Long = 0L

    suspend fun currentAccessToken(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val cached = accessToken
        if (!forceRefresh && cached != null && now < expiresAtMillis - RefreshBufferMillis) {
            return cached
        }

        return runCatching {
            val session = api.createDeviceSession(
                DeviceSessionRequest(
                    deviceId = deviceId(),
                    installId = installId(),
                    appVersion = BuildConfig.VERSION_NAME
                )
            )

            accessToken = session.accessToken
            expiresAtMillis = now + session.expiresInSeconds * 1000L
            session.accessToken
        }.getOrNull()
    }

    suspend fun refreshedAccessToken(): String? {
        accessToken = null
        expiresAtMillis = 0L
        return currentAccessToken(forceRefresh = true)
    }

    private fun deviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: installId()
    }

    private fun installId(): String {
        val prefs = context.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)
        val existing = prefs.getString(InstallIdKey, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(InstallIdKey, generated).apply()
        return generated
    }

    private companion object {
        const val PrefsName = "chatmod_session"
        const val InstallIdKey = "install_id"
        const val RefreshBufferMillis = 5L * 60L * 1000L
    }
}
