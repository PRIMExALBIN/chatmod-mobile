package com.chatmod.mobile.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.chatmod.mobile.domain.rules.LinkPolicy
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.TemporaryTrustedChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.chatModSettings by preferencesDataStore(name = "chatmod_settings")

data class BotSettings(
    val selectedProfileId: String?,
    val emergencyMode: Boolean,
    val linkLockdown: Boolean,
    val reducedMotion: Boolean,
    val highContrast: Boolean,
    val lowDataMode: Boolean,
    val shareUsageAnalytics: Boolean
)

data class ActiveBotRuntimeState(
    val sessionId: String,
    val liveChatId: String,
    val videoId: String,
    val streamTitle: String,
    val startedAtMillis: Long
)

data class LastSelectedStreamState(
    val liveChatId: String,
    val videoId: String,
    val streamTitle: String,
    val channelName: String,
    val selectedAtMillis: Long
)

data class ActiveRulePresetState(
    val id: String,
    val name: String,
    val config: ModerationProfile,
    val updatedAtMillis: Long
)

class SettingsStore(
    private val context: Context
) {
    val settings: Flow<BotSettings> = context.chatModSettings.data.map { preferences ->
        preferences.toBotSettings()
    }

    suspend fun currentSettings(): BotSettings {
        return context.chatModSettings.data.map { preferences -> preferences.toBotSettings() }.first()
    }

    val activeRuntime: Flow<ActiveBotRuntimeState?> = context.chatModSettings.data.map { preferences ->
        val sessionId = preferences[Keys.ActiveSessionId]
        val liveChatId = preferences[Keys.ActiveLiveChatId]
        val videoId = preferences[Keys.ActiveVideoId]
        if (sessionId.isNullOrBlank() || liveChatId.isNullOrBlank() || videoId.isNullOrBlank()) {
            null
        } else {
            ActiveBotRuntimeState(
                sessionId = sessionId,
                liveChatId = liveChatId,
                videoId = videoId,
                streamTitle = preferences[Keys.ActiveStreamTitle] ?: "Recovered stream",
                startedAtMillis = preferences[Keys.ActiveStartedAtMillis] ?: System.currentTimeMillis()
            )
        }
    }

    val lastSelectedStream: Flow<LastSelectedStreamState?> = context.chatModSettings.data.map { preferences ->
        val liveChatId = preferences[Keys.LastSelectedLiveChatId]
        val videoId = preferences[Keys.LastSelectedVideoId]
        if (liveChatId.isNullOrBlank() || videoId.isNullOrBlank()) {
            null
        } else {
            LastSelectedStreamState(
                liveChatId = liveChatId,
                videoId = videoId,
                streamTitle = preferences[Keys.LastSelectedStreamTitle] ?: "Selected stream",
                channelName = preferences[Keys.LastSelectedChannelName] ?: "Your Channel",
                selectedAtMillis = preferences[Keys.LastSelectedAtMillis] ?: System.currentTimeMillis()
            )
        }
    }

    val activeRulePreset: Flow<ActiveRulePresetState?> = context.chatModSettings.data.map { preferences ->
        preferences.toActiveRulePreset()
    }

    suspend fun currentActiveRulePreset(): ActiveRulePresetState? {
        return context.chatModSettings.data.map { preferences -> preferences.toActiveRulePreset() }.first()
    }

    suspend fun setSelectedProfile(profileId: String?) {
        context.chatModSettings.edit { preferences ->
            if (profileId == null) {
                preferences.remove(Keys.SelectedProfileId)
            } else {
                preferences[Keys.SelectedProfileId] = profileId
            }
        }
    }

    suspend fun setEmergencyMode(enabled: Boolean) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.EmergencyMode] = enabled
        }
    }

    suspend fun setLinkLockdown(enabled: Boolean) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.LinkLockdown] = enabled
        }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.ReducedMotion] = enabled
        }
    }

    suspend fun setHighContrast(enabled: Boolean) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.HighContrast] = enabled
        }
    }

    suspend fun setLowDataMode(enabled: Boolean) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.LowDataMode] = enabled
        }
    }

    suspend fun setShareUsageAnalytics(enabled: Boolean) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.ShareUsageAnalytics] = enabled
        }
    }

    suspend fun setActiveRuntime(state: ActiveBotRuntimeState) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.ActiveSessionId] = state.sessionId
            preferences[Keys.ActiveLiveChatId] = state.liveChatId
            preferences[Keys.ActiveVideoId] = state.videoId
            preferences[Keys.ActiveStreamTitle] = state.streamTitle
            preferences[Keys.ActiveStartedAtMillis] = state.startedAtMillis
        }
    }

    suspend fun clearActiveRuntime() {
        context.chatModSettings.edit { preferences ->
            preferences.remove(Keys.ActiveSessionId)
            preferences.remove(Keys.ActiveLiveChatId)
            preferences.remove(Keys.ActiveVideoId)
            preferences.remove(Keys.ActiveStreamTitle)
            preferences.remove(Keys.ActiveStartedAtMillis)
        }
    }

    suspend fun setLastSelectedStream(state: LastSelectedStreamState) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.LastSelectedLiveChatId] = state.liveChatId
            preferences[Keys.LastSelectedVideoId] = state.videoId
            preferences[Keys.LastSelectedStreamTitle] = state.streamTitle
            preferences[Keys.LastSelectedChannelName] = state.channelName
            preferences[Keys.LastSelectedAtMillis] = state.selectedAtMillis
        }
    }

    suspend fun setActiveRulePreset(id: String, name: String, config: ModerationProfile) {
        context.chatModSettings.edit { preferences ->
            preferences[Keys.ActiveRulePresetId] = id
            preferences[Keys.ActiveRulePresetName] = name
            preferences[Keys.ActiveRulePresetConfigJson] = config.toJson().toString()
            preferences[Keys.ActiveRulePresetUpdatedAtMillis] = System.currentTimeMillis()
        }
    }

    suspend fun clearActiveRulePreset() {
        context.chatModSettings.edit { preferences ->
            preferences.remove(Keys.ActiveRulePresetId)
            preferences.remove(Keys.ActiveRulePresetName)
            preferences.remove(Keys.ActiveRulePresetConfigJson)
            preferences.remove(Keys.ActiveRulePresetUpdatedAtMillis)
        }
    }

    suspend fun clear() {
        context.chatModSettings.edit { preferences ->
            preferences.clear()
        }
    }

    private fun Preferences.toBotSettings(): BotSettings {
        return BotSettings(
            selectedProfileId = this[Keys.SelectedProfileId],
            emergencyMode = this[Keys.EmergencyMode] ?: false,
            linkLockdown = this[Keys.LinkLockdown] ?: false,
            reducedMotion = this[Keys.ReducedMotion] ?: false,
            highContrast = this[Keys.HighContrast] ?: false,
            lowDataMode = this[Keys.LowDataMode] ?: false,
            shareUsageAnalytics = this[Keys.ShareUsageAnalytics] ?: false
        )
    }

    private fun Preferences.toActiveRulePreset(): ActiveRulePresetState? {
        val id = this[Keys.ActiveRulePresetId]
        val name = this[Keys.ActiveRulePresetName]
        val configJson = this[Keys.ActiveRulePresetConfigJson]
        if (id.isNullOrBlank() || name.isNullOrBlank() || configJson.isNullOrBlank()) {
            return null
        }

        val config = runCatching { JSONObject(configJson).toModerationProfile() }.getOrNull() ?: return null
        return ActiveRulePresetState(
            id = id,
            name = name,
            config = config,
            updatedAtMillis = this[Keys.ActiveRulePresetUpdatedAtMillis] ?: 0L
        )
    }

    private object Keys {
        val SelectedProfileId = stringPreferencesKey("selected_profile_id")
        val EmergencyMode = booleanPreferencesKey("emergency_mode")
        val LinkLockdown = booleanPreferencesKey("link_lockdown")
        val ReducedMotion = booleanPreferencesKey("reduced_motion")
        val HighContrast = booleanPreferencesKey("high_contrast")
        val LowDataMode = booleanPreferencesKey("low_data_mode")
        val ShareUsageAnalytics = booleanPreferencesKey("share_usage_analytics")
        val ActiveSessionId = stringPreferencesKey("active_session_id")
        val ActiveLiveChatId = stringPreferencesKey("active_live_chat_id")
        val ActiveVideoId = stringPreferencesKey("active_video_id")
        val ActiveStreamTitle = stringPreferencesKey("active_stream_title")
        val ActiveStartedAtMillis = longPreferencesKey("active_started_at_millis")
        val LastSelectedLiveChatId = stringPreferencesKey("last_selected_live_chat_id")
        val LastSelectedVideoId = stringPreferencesKey("last_selected_video_id")
        val LastSelectedStreamTitle = stringPreferencesKey("last_selected_stream_title")
        val LastSelectedChannelName = stringPreferencesKey("last_selected_channel_name")
        val LastSelectedAtMillis = longPreferencesKey("last_selected_at_millis")
        val ActiveRulePresetId = stringPreferencesKey("active_rule_preset_id")
        val ActiveRulePresetName = stringPreferencesKey("active_rule_preset_name")
        val ActiveRulePresetConfigJson = stringPreferencesKey("active_rule_preset_config_json")
        val ActiveRulePresetUpdatedAtMillis = longPreferencesKey("active_rule_preset_updated_at_millis")
    }
}

private fun ModerationProfile.toJson(): JSONObject {
    return JSONObject()
        .put("blockedTerms", JSONArray(blockedTerms))
        .put("regexPatterns", JSONArray(regexPatterns))
        .put("linkPolicy", linkPolicy.toApiValue())
        .put("allowedDomains", JSONArray(allowedDomains))
        .put("blockedDomains", JSONArray(blockedDomains))
        .put("capsThreshold", capsThreshold)
        .put("maxRepeatedCharacters", maxRepeatedCharacters)
        .put("maxEmojiCount", maxEmojiCount)
        .put("maxMentions", maxMentions)
        .put("maxSymbolCount", maxSymbolCount)
        .put("trustedChannelIds", JSONArray(trustedChannelIds))
        .put("temporaryTrustedChannels", temporaryTrustedChannels.toJson())
        .put("ignoreMembers", ignoreMembers)
        .put("raidMode", raidMode)
        .put("newChatterBurstThreshold", newChatterBurstThreshold)
        .put("newChatterBurstWindowSeconds", newChatterBurstWindowSeconds)
        .also { body -> firstStreamMinutesOnly?.let { body.put("firstStreamMinutesOnly", it) } }
        .put("autoReplyEnabled", autoReplyEnabled)
        .put("autoReplyMessage", autoReplyMessage)
        .put("hideUserOnSevereMatch", hideUserOnSevereMatch)
}

private fun JSONObject.toModerationProfile(): ModerationProfile {
    return ModerationProfile(
        blockedTerms = optStringList("blockedTerms"),
        regexPatterns = optStringList("regexPatterns"),
        linkPolicy = optString("linkPolicy", "flag").toLinkPolicy(),
        allowedDomains = optStringList("allowedDomains"),
        blockedDomains = optStringList("blockedDomains"),
        capsThreshold = optDouble("capsThreshold", 0.75),
        maxRepeatedCharacters = optInt("maxRepeatedCharacters", 6),
        maxEmojiCount = optInt("maxEmojiCount", 8),
        maxMentions = optInt("maxMentions", 6),
        maxSymbolCount = optInt("maxSymbolCount", 16),
        trustedChannelIds = optStringList("trustedChannelIds"),
        temporaryTrustedChannels = optTemporaryTrustedChannels("temporaryTrustedChannels"),
        ignoreMembers = optBoolean("ignoreMembers", false),
        raidMode = optBoolean("raidMode", false),
        newChatterBurstThreshold = optInt("newChatterBurstThreshold", 6),
        newChatterBurstWindowSeconds = optInt("newChatterBurstWindowSeconds", 30),
        firstStreamMinutesOnly = optNullableInt("firstStreamMinutesOnly"),
        autoReplyEnabled = optBoolean("autoReplyEnabled", false),
        autoReplyMessage = optString("autoReplyMessage", "Please keep chat safe and on topic."),
        hideUserOnSevereMatch = optBoolean("hideUserOnSevereMatch", false)
    )
}

private fun LinkPolicy.toApiValue(): String {
    return when (this) {
        LinkPolicy.Allow -> "allow"
        LinkPolicy.Flag -> "flag"
        LinkPolicy.Delete -> "delete"
    }
}

private fun String.toLinkPolicy(): LinkPolicy {
    return when (lowercase()) {
        "allow" -> LinkPolicy.Allow
        "delete" -> LinkPolicy.Delete
        else -> LinkPolicy.Flag
    }
}

private fun JSONObject.optStringList(name: String): List<String> {
    val values = optJSONArray(name) ?: return emptyList()
    return List(values.length()) { index -> values.optString(index) }
        .filter { it.isNotBlank() }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

private fun JSONObject.optTemporaryTrustedChannels(name: String): List<TemporaryTrustedChannel> {
    val values = optJSONArray(name) ?: return emptyList()
    return List(values.length()) { index -> values.optJSONObject(index) }
        .mapNotNull { value ->
            val channelId = value?.optString("channelId")?.takeIf { it.isNotBlank() }
            val expiresAt = value?.optString("expiresAt")?.takeIf { it.isNotBlank() }
            if (channelId == null || expiresAt == null) {
                null
            } else {
                TemporaryTrustedChannel(channelId = channelId, expiresAt = expiresAt)
            }
        }
}

private fun List<TemporaryTrustedChannel>.toJson(): JSONArray {
    return JSONArray().also { target ->
        forEach { trusted ->
            target.put(
                JSONObject()
                    .put("channelId", trusted.channelId)
                    .put("expiresAt", trusted.expiresAt)
            )
        }
    }
}
