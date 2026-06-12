package com.chatmod.mobile.data

import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.AccountDeletionResult
import com.chatmod.mobile.data.remote.AccountExportSummary
import com.chatmod.mobile.data.remote.BackupList
import com.chatmod.mobile.data.remote.ChatMessageLogRecord
import com.chatmod.mobile.data.remote.ChatMessageLogSyncRequest
import com.chatmod.mobile.data.remote.DeviceSession
import com.chatmod.mobile.data.remote.DeviceSessionRequest
import com.chatmod.mobile.data.remote.DiscordAlertRequest
import com.chatmod.mobile.data.remote.DiscordAlertResult
import com.chatmod.mobile.data.remote.DiscordWebhookConfig
import com.chatmod.mobile.data.remote.DiscordWebhookUpsertRequest
import com.chatmod.mobile.data.remote.EntitlementSnapshot
import com.chatmod.mobile.data.remote.GooglePlayPurchaseValidationRequest
import com.chatmod.mobile.data.remote.GooglePlayPurchaseValidationResult
import com.chatmod.mobile.data.remote.ApiErrorList
import com.chatmod.mobile.data.remote.ModerationActionLogRecord
import com.chatmod.mobile.data.remote.ModerationActionReviewRequest
import com.chatmod.mobile.data.remote.ModerationActionLogSyncRequest
import com.chatmod.mobile.data.remote.ProfileBackup
import com.chatmod.mobile.data.remote.ProfileBackupRequest
import com.chatmod.mobile.data.remote.RulePresetList
import com.chatmod.mobile.data.remote.RulePresetRecord
import com.chatmod.mobile.data.remote.RulePresetSyncRequest
import com.chatmod.mobile.data.remote.RulePresetTemplateList
import com.chatmod.mobile.data.remote.RuntimeEventRecord
import com.chatmod.mobile.data.remote.RuntimeEventSyncRequest
import com.chatmod.mobile.data.remote.SettingsBackupCommand
import com.chatmod.mobile.data.remote.SettingsBackupRequest
import com.chatmod.mobile.data.remote.SettingsBackupResult
import com.chatmod.mobile.data.remote.SettingsBackupTimer
import com.chatmod.mobile.data.remote.SettingsRestoreResult
import com.chatmod.mobile.data.remote.SupportEventList
import com.chatmod.mobile.data.remote.SupportEventRecord
import com.chatmod.mobile.data.remote.SupportEventRequest
import com.chatmod.mobile.data.remote.StreamSessionLogs
import com.chatmod.mobile.data.remote.StreamSessionAnalyticsSummary
import com.chatmod.mobile.data.remote.StreamSessionRecord
import com.chatmod.mobile.data.remote.StreamSessionSyncRequest
import com.chatmod.mobile.data.remote.YouTubeBroadcastList
import com.chatmod.mobile.data.remote.YouTubeDisconnectResult
import com.chatmod.mobile.data.remote.YouTubeLiveChatDiscovery
import com.chatmod.mobile.domain.rules.ModerationProfile

class ChatModRepository(
    private val api: ChatModApiClient
) {
    suspend fun createSession(deviceId: String, installId: String, appVersion: String): DeviceSession {
        return api.createDeviceSession(
            DeviceSessionRequest(
                deviceId = deviceId,
                installId = installId,
                appVersion = appVersion
            )
        )
    }

    suspend fun loadEntitlement(accessToken: String): EntitlementSnapshot {
        return api.currentEntitlement(accessToken)
    }

    suspend fun validatePurchase(
        accessToken: String,
        productId: String,
        purchaseToken: String,
        packageName: String? = null
    ): GooglePlayPurchaseValidationResult {
        return api.validateGooglePlayPurchase(
            accessToken = accessToken,
            request = GooglePlayPurchaseValidationRequest(
                productId = productId,
                purchaseToken = purchaseToken,
                packageName = packageName
            )
        )
    }

    suspend fun backupProfile(
        accessToken: String,
        profileId: String,
        channelId: String,
        profileName: String,
        config: Map<String, Any?>,
        appVersion: String
    ): ProfileBackup {
        return api.backupModerationProfile(
            accessToken = accessToken,
            request = ProfileBackupRequest(
                profileId = profileId,
                channelId = channelId,
                profileName = profileName,
                config = config,
                clientVersion = appVersion
            )
        )
    }

    suspend fun listBackups(accessToken: String): BackupList {
        return api.listBackups(accessToken)
    }

    suspend fun backupSettings(
        accessToken: String,
        profileId: String,
        channelId: String,
        profileName: String,
        commands: List<SettingsBackupCommand>,
        timers: List<SettingsBackupTimer>,
        appVersion: String
    ): SettingsBackupResult {
        return api.createSettingsBackup(
            accessToken = accessToken,
            request = SettingsBackupRequest(
                profileId = profileId,
                channelId = channelId,
                profileName = profileName,
                commands = commands,
                timers = timers,
                clientVersion = appVersion
            )
        )
    }

    suspend fun restoreSettingsBackup(
        accessToken: String,
        backupId: String,
        targetProfileId: String? = null
    ): SettingsRestoreResult {
        return api.restoreSettingsBackup(accessToken, backupId, targetProfileId)
    }

    suspend fun deleteBackup(accessToken: String, backupId: String) {
        api.deleteBackup(accessToken, backupId)
    }

    suspend fun exportAccount(accessToken: String): AccountExportSummary {
        return api.exportAccount(accessToken)
    }

    suspend fun disconnectYouTube(accessToken: String): YouTubeDisconnectResult {
        return api.disconnectYouTube(accessToken)
    }

    suspend fun discordWebhookConfig(accessToken: String, profileId: String): DiscordWebhookConfig {
        return api.discordWebhookConfig(accessToken, profileId)
    }

    suspend fun upsertDiscordWebhook(
        accessToken: String,
        profileId: String,
        webhookUrl: String?,
        enabled: Boolean,
        alertModerationActions: Boolean,
        alertRuntimeStatus: Boolean
    ): DiscordWebhookConfig {
        return api.upsertDiscordWebhook(
            accessToken = accessToken,
            request = DiscordWebhookUpsertRequest(
                profileId = profileId,
                webhookUrl = webhookUrl,
                enabled = enabled,
                alertModerationActions = alertModerationActions,
                alertRuntimeStatus = alertRuntimeStatus
            )
        )
    }

    suspend fun deleteDiscordWebhook(accessToken: String, profileId: String) {
        api.deleteDiscordWebhook(accessToken, profileId)
    }

    suspend fun testDiscordWebhook(accessToken: String, profileId: String): DiscordAlertResult {
        return api.testDiscordWebhook(accessToken, profileId)
    }

    suspend fun sendDiscordAlert(
        accessToken: String,
        profileId: String,
        eventType: String,
        title: String,
        detail: String,
        severity: String = "info",
        metadata: Map<String, Any?> = emptyMap()
    ): DiscordAlertResult {
        return api.sendDiscordAlert(
            accessToken = accessToken,
            request = DiscordAlertRequest(
                profileId = profileId,
                eventType = eventType,
                title = title,
                detail = detail,
                severity = severity,
                metadata = metadata
            )
        )
    }

    suspend fun listYouTubeBroadcasts(
        accessToken: String,
        channelId: String,
        includeScheduled: Boolean = true
    ): YouTubeBroadcastList {
        return api.listYouTubeBroadcasts(accessToken, channelId, includeScheduled)
    }

    suspend fun discoverYouTubeLiveChat(
        accessToken: String,
        channelId: String,
        includeScheduled: Boolean = true
    ): YouTubeLiveChatDiscovery {
        return api.discoverYouTubeLiveChat(accessToken, channelId, includeScheduled)
    }

    suspend fun deleteCurrentAccount(accessToken: String): AccountDeletionResult {
        return api.deleteCurrentAccount(accessToken)
    }

    suspend fun listSupportEvents(accessToken: String): SupportEventList {
        return api.listSupportEvents(accessToken)
    }

    suspend fun listApiErrors(accessToken: String): ApiErrorList {
        return api.listApiErrors(accessToken)
    }

    suspend fun recordSupportEvent(
        accessToken: String,
        severity: String,
        message: String,
        details: Map<String, Any?> = emptyMap()
    ): SupportEventRecord {
        return api.recordSupportEvent(
            accessToken,
            SupportEventRequest(
                severity = severity,
                message = message,
                details = details
            )
        )
    }

    suspend fun listRulePresets(accessToken: String, profileId: String? = null): RulePresetList {
        return api.listRulePresets(accessToken, profileId)
    }

    suspend fun listRulePresetTemplates(accessToken: String): RulePresetTemplateList {
        return api.listRulePresetTemplates(accessToken)
    }

    suspend fun saveRulePreset(
        accessToken: String,
        id: String,
        profileId: String,
        name: String,
        config: ModerationProfile,
        isDefault: Boolean
    ): RulePresetRecord {
        return api.saveRulePreset(
            accessToken = accessToken,
            request = RulePresetSyncRequest(
                id = id,
                profileId = profileId,
                name = name,
                config = config,
                isDefault = isDefault
            )
        )
    }

    suspend fun deleteRulePreset(accessToken: String, presetId: String) {
        api.deleteRulePreset(accessToken, presetId)
    }

    suspend fun upsertStreamSession(
        accessToken: String,
        sessionId: String,
        profileId: String,
        videoId: String,
        liveChatId: String,
        title: String?,
        startedAt: String? = null
    ): StreamSessionRecord {
        return api.upsertStreamSession(
            accessToken = accessToken,
            sessionId = sessionId,
            request = StreamSessionSyncRequest(
                profileId = profileId,
                videoId = videoId,
                liveChatId = liveChatId,
                title = title,
                startedAt = startedAt
            )
        )
    }

    suspend fun endStreamSession(
        accessToken: String,
        sessionId: String,
        endedAtIso: String? = null
    ): StreamSessionRecord {
        return api.endStreamSession(accessToken, sessionId, endedAtIso)
    }

    suspend fun streamSessionLogs(accessToken: String, sessionId: String): StreamSessionLogs {
        return api.streamSessionLogs(accessToken, sessionId)
    }

    suspend fun streamSessionAnalyticsSummary(
        accessToken: String,
        profileId: String? = null,
        days: Int = 30
    ): StreamSessionAnalyticsSummary {
        return api.streamSessionAnalyticsSummary(accessToken, profileId, days)
    }

    suspend fun recordStreamMessage(
        accessToken: String,
        sessionId: String,
        youtubeMessageId: String,
        authorChannelId: String,
        authorName: String,
        text: String,
        receivedAt: String? = null
    ): ChatMessageLogRecord {
        return api.recordStreamMessage(
            accessToken = accessToken,
            sessionId = sessionId,
            request = ChatMessageLogSyncRequest(
                youtubeMessageId = youtubeMessageId,
                authorChannelId = authorChannelId,
                authorName = authorName,
                text = text,
                receivedAt = receivedAt
            )
        )
    }

    suspend fun recordModerationActionLog(
        accessToken: String,
        sessionId: String,
        clientActionId: String? = null,
        youtubeMessageId: String?,
        authorChannelId: String?,
        actionType: String,
        reason: String,
        confidence: Double?,
        metadata: Map<String, Any?> = emptyMap()
    ): ModerationActionLogRecord {
        return api.recordModerationActionLog(
            accessToken = accessToken,
            sessionId = sessionId,
            request = ModerationActionLogSyncRequest(
                clientActionId = clientActionId,
                youtubeMessageId = youtubeMessageId,
                authorChannelId = authorChannelId,
                actionType = actionType,
                reason = reason,
                confidence = confidence,
                metadata = metadata
            )
        )
    }

    suspend fun reviewModerationActionLog(
        accessToken: String,
        sessionId: String,
        actionId: String,
        reviewStatus: String,
        reviewNote: String? = null
    ): ModerationActionLogRecord {
        return api.reviewModerationActionLog(
            accessToken = accessToken,
            sessionId = sessionId,
            actionId = actionId,
            request = ModerationActionReviewRequest(
                reviewStatus = reviewStatus,
                reviewNote = reviewNote
            )
        )
    }

    suspend fun recordRuntimeEvent(
        accessToken: String,
        sessionId: String,
        type: String,
        message: String,
        metadata: Map<String, Any?> = emptyMap()
    ): RuntimeEventRecord {
        return api.recordRuntimeEvent(
            accessToken = accessToken,
            sessionId = sessionId,
            request = RuntimeEventSyncRequest(
                type = type,
                message = message,
                metadata = metadata
            )
        )
    }
}
