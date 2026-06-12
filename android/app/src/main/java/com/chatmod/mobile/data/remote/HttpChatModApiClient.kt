package com.chatmod.mobile.data.remote

import com.chatmod.mobile.domain.rules.ActionType
import com.chatmod.mobile.domain.rules.ChatMessage
import com.chatmod.mobile.domain.rules.LinkPolicy
import com.chatmod.mobile.domain.rules.ModerationAction
import com.chatmod.mobile.domain.rules.ModerationDecision
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.TemporaryTrustedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

class HttpChatModApiClient(
    private val baseUrl: String
) : ChatModApiClient {
    override suspend fun appCompatibility(platform: String, versionName: String, versionCode: Int): AppCompatibility {
        val response = requestJson(
            method = "GET",
            path = "/app/compatibility?platform=${platform.urlEncoded()}&versionName=${versionName.urlEncoded()}&versionCode=$versionCode"
        )

        return response.toAppCompatibility()
    }

    override suspend fun createDeviceSession(request: DeviceSessionRequest): DeviceSession {
        val response = requestJson(
            method = "POST",
            path = "/accounts/device-session",
            body = JSONObject()
                .put("deviceId", request.deviceId)
                .put("installId", request.installId)
                .put("appVersion", request.appVersion)
        )

        return DeviceSession(
            accessToken = response.getString("accessToken"),
            tokenType = response.optString("tokenType", "Bearer"),
            expiresInSeconds = response.optLong("expiresInSeconds", 3600)
        )
    }

    override suspend fun currentEntitlement(accessToken: String): EntitlementSnapshot {
        return requestJson(
            method = "GET",
            path = "/entitlements/current",
            accessToken = accessToken
        ).toEntitlementSnapshot()
    }

    override suspend fun validateGooglePlayPurchase(
        accessToken: String,
        request: GooglePlayPurchaseValidationRequest
    ): GooglePlayPurchaseValidationResult {
        val body = JSONObject()
            .put("productId", request.productId)
            .put("purchaseToken", request.purchaseToken)
        request.packageName?.let { body.put("packageName", it) }

        val response = requestJson(
            method = "POST",
            path = "/entitlements/google-play/validate",
            accessToken = accessToken,
            body = body
        )

        return GooglePlayPurchaseValidationResult(
            entitlement = response.getJSONObject("entitlement").toEntitlementSnapshot(),
            validationStatus = response.optJSONObject("validation")?.optString("status", "unknown") ?: "unknown"
        )
    }

    override suspend fun listChannelProfiles(accessToken: String): ChannelProfileList {
        val response = requestJson(
            method = "GET",
            path = "/profiles",
            accessToken = accessToken
        )
        val profiles = response.optJSONArray("profiles") ?: JSONArray()
        return ChannelProfileList(
            profiles = List(profiles.length()) { index ->
                profiles.getJSONObject(index).toChannelProfileRecord()
            }
        )
    }

    override suspend fun createChannelProfile(
        accessToken: String,
        request: ChannelProfileCreateRequest
    ): ChannelProfileRecord {
        return requestJson(
            method = "POST",
            path = "/profiles",
            accessToken = accessToken,
            body = request.toJson()
        ).toChannelProfileRecord()
    }

    override suspend fun backupModerationProfile(
        accessToken: String,
        request: ProfileBackupRequest
    ): ProfileBackup {
        val response = requestJson(
            method = "POST",
            path = "/backups/moderation-profile",
            accessToken = accessToken,
            body = JSONObject()
                .put("profileId", request.profileId)
                .put("channelId", request.channelId)
                .put("profileName", request.profileName)
                .put("config", request.config.toJsonObject())
                .put("clientVersion", request.clientVersion)
        )

        return ProfileBackup(
            id = response.getString("id"),
            profileName = response.optString("profileName"),
            version = response.optInt("version", 1),
            createdAt = response.optString("createdAt")
        )
    }

    override suspend fun createSettingsBackup(
        accessToken: String,
        request: SettingsBackupRequest
    ): SettingsBackupResult {
        val response = requestJson(
            method = "POST",
            path = "/backups/settings",
            accessToken = accessToken,
            body = JSONObject()
                .put("profileId", request.profileId)
                .put("channelId", request.channelId)
                .put("profileName", request.profileName)
                .put("commands", request.commands.toCommandJsonArray())
                .put("timers", request.timers.toTimerJsonArray())
                .put("clientVersion", request.clientVersion)
        )

        return SettingsBackupResult(
            id = response.getString("id"),
            profileName = response.optString("profileName"),
            version = response.optInt("version", 1),
            commandCount = response.optInt("commandCount", 0),
            timerCount = response.optInt("timerCount", 0),
            createdAt = response.optString("createdAt")
        )
    }

    override suspend fun restoreSettingsBackup(
        accessToken: String,
        backupId: String,
        targetProfileId: String?
    ): SettingsRestoreResult {
        val body = JSONObject()
        targetProfileId?.let { body.put("targetProfileId", it) }
        val response = requestJson(
            method = "POST",
            path = "/backups/$backupId/restore",
            accessToken = accessToken,
            body = body
        )
        val commands = response.optJSONArray("commands") ?: JSONArray()
        val timers = response.optJSONArray("timers") ?: JSONArray()

        return SettingsRestoreResult(
            restoredAt = response.optString("restoredAt"),
            backupId = response.optString("backupId", backupId),
            profileId = response.optString("profileId"),
            commands = List(commands.length()) { index ->
                commands.getJSONObject(index).toSettingsBackupCommand()
            },
            timers = List(timers.length()) { index ->
                timers.getJSONObject(index).toSettingsBackupTimer()
            }
        )
    }

    override suspend fun listBackups(accessToken: String): BackupList {
        val response = requestJson(
            method = "GET",
            path = "/backups",
            accessToken = accessToken
        )
        val backups = response.optJSONArray("backups") ?: JSONArray()

        return BackupList(
            backups = List(backups.length()) { index ->
                backups.getJSONObject(index).toCloudBackup()
            }
        )
    }

    override suspend fun deleteBackup(accessToken: String, backupId: String) {
        requestJson(
            method = "DELETE",
            path = "/backups/$backupId",
            accessToken = accessToken
        )
    }

    override suspend fun exportAccount(accessToken: String): AccountExportSummary {
        val response = requestJson(
            method = "GET",
            path = "/accounts/export",
            accessToken = accessToken
        )
        val profiles = response.optJSONArray("profiles") ?: JSONArray()
        val linkedAccounts = response.optJSONArray("linkedAccounts") ?: JSONArray()

        return AccountExportSummary(
            exportedAt = response.optString("exportedAt"),
            profileCount = profiles.length(),
            backupCount = countNestedArrayItems(profiles, "backups"),
            linkedAccountCount = linkedAccounts.length(),
            linkedAccounts = List(linkedAccounts.length()) { index ->
                linkedAccounts.getJSONObject(index).toLinkedAccountSummary()
            },
            supportEventCount = response.optJSONArray("supportEvents")?.length() ?: 0,
            auditLogCount = response.optJSONArray("auditLogs")?.length() ?: 0
        )
    }

    override suspend fun youtubeConnectUrl(accessToken: String): YouTubeConnectUrl {
        val response = requestJson(
            method = "GET",
            path = "/youtube/connect-url",
            accessToken = accessToken
        )
        return response.toYouTubeConnectUrl()
    }

    override suspend fun youtubeAccountStatus(accessToken: String): YouTubeAccountStatus {
        return requestJson(
            method = "GET",
            path = "/youtube/account",
            accessToken = accessToken
        ).toYouTubeAccountStatus()
    }

    override suspend fun disconnectYouTube(accessToken: String): YouTubeDisconnectResult {
        val response = requestJson(
            method = "POST",
            path = "/accounts/youtube/disconnect",
            accessToken = accessToken
        )

        return YouTubeDisconnectResult(
            disconnected = response.optBoolean("disconnected", false),
            removedAccounts = response.optInt("removedAccounts", 0),
            revocationAttempted = response.optBoolean("revocationAttempted", false),
            revokedTokens = response.optInt("revokedTokens", 0),
            revocationFailures = response.optInt("revocationFailures", 0)
        )
    }

    override suspend fun listYouTubeBroadcasts(
        accessToken: String,
        channelId: String,
        includeScheduled: Boolean
    ): YouTubeBroadcastList {
        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/broadcasts",
            accessToken = accessToken,
            body = youtubeDiscoveryBody(channelId, includeScheduled)
        )
        val broadcasts = response.optJSONArray("broadcasts") ?: JSONArray()

        return YouTubeBroadcastList(
            broadcasts = List(broadcasts.length()) { index ->
                broadcasts.getJSONObject(index).toYouTubeBroadcast()
            },
            source = response.optString("source", "backend")
        )
    }

    override suspend fun discoverYouTubeLiveChat(
        accessToken: String,
        channelId: String,
        includeScheduled: Boolean
    ): YouTubeLiveChatDiscovery {
        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/discover",
            accessToken = accessToken,
            body = youtubeDiscoveryBody(channelId, includeScheduled)
        )
        val broadcasts = response.optJSONArray("broadcasts") ?: JSONArray()

        return YouTubeLiveChatDiscovery(
            activeChat = response.optJSONObject("activeChat")?.toYouTubeActiveChat(),
            broadcasts = List(broadcasts.length()) { index ->
                broadcasts.getJSONObject(index).toYouTubeBroadcast()
            },
            activeBroadcastCount = response.optInt("activeBroadcastCount", 0),
            needsSelection = response.optBoolean("needsSelection", false),
            status = response.optString("status", "no_active_chat"),
            source = response.optString("source", "backend")
        )
    }

    override suspend fun sendYouTubeTestMessage(
        accessToken: String,
        request: YouTubeTestMessageRequest
    ): YouTubeTestMessageResult {
        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/send-test",
            accessToken = accessToken,
            body = JSONObject()
                .put("liveChatId", request.liveChatId)
                .put("text", request.text)
        )

        return YouTubeTestMessageResult(
            messageId = response.optString("messageId")
        )
    }

    override suspend fun listYouTubeLiveChatMessages(
        accessToken: String,
        request: YouTubeLiveChatMessagesRequest
    ): YouTubeLiveChatMessagePage {
        val body = JSONObject()
            .put("liveChatId", request.liveChatId)
        request.pageToken?.let { body.put("pageToken", it) }

        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/messages",
            accessToken = accessToken,
            body = body
        )
        val messages = response.optJSONArray("messages") ?: JSONArray()

        return YouTubeLiveChatMessagePage(
            messages = List(messages.length()) { index ->
                messages.getJSONObject(index).toYouTubeLiveChatMessageRecord()
            },
            nextPageToken = response.optNullableString("nextPageToken"),
            pollingIntervalMillis = response.optLong("pollingIntervalMillis", 5000L),
            source = response.optString("source", "backend")
        )
    }

    override suspend fun sendYouTubeLiveChatMessage(
        accessToken: String,
        request: YouTubeMessageSendRequest
    ): YouTubeMessageSendResult {
        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/messages/send",
            accessToken = accessToken,
            body = JSONObject()
                .put("liveChatId", request.liveChatId)
                .put("text", request.text)
        )

        return YouTubeMessageSendResult(
            messageId = response.optString("messageId"),
            liveChatId = response.optString("liveChatId", request.liveChatId),
            sentAt = response.optString("sentAt")
        )
    }

    override suspend fun deleteYouTubeLiveChatMessage(
        accessToken: String,
        request: YouTubeMessageDeleteRequest
    ): YouTubeMessageDeleteResult {
        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/messages/delete",
            accessToken = accessToken,
            body = JSONObject()
                .put("messageId", request.messageId)
                .put("reason", request.reason)
        )

        return YouTubeMessageDeleteResult(
            messageId = response.optString("messageId", request.messageId),
            actionType = response.optString("actionType", "deleteMessage"),
            reason = response.optString("reason", request.reason),
            deletedAt = response.optString("deletedAt")
        )
    }

    override suspend fun hideYouTubeLiveChatUser(
        accessToken: String,
        request: YouTubeUserHideRequest
    ): YouTubeUserHideResult {
        val body = JSONObject()
            .put("liveChatId", request.liveChatId)
            .put("authorChannelId", request.authorChannelId)
            .put("reason", request.reason)
        request.durationSeconds?.let { body.put("durationSeconds", it) }

        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/users/hide",
            accessToken = accessToken,
            body = body
        )

        return YouTubeUserHideResult(
            liveChatId = response.optString("liveChatId", request.liveChatId),
            authorChannelId = response.optString("authorChannelId", request.authorChannelId),
            liveChatBanId = response.optNullableString("liveChatBanId"),
            actionType = response.optString("actionType", if (request.durationSeconds == null) "hideUser" else "timeoutUser"),
            durationSeconds = response.optNullableInt("durationSeconds"),
            reason = response.optString("reason", request.reason),
            actedAt = response.optString("actedAt")
        )
    }

    override suspend fun unbanYouTubeLiveChatUser(
        accessToken: String,
        request: YouTubeUserUnbanRequest
    ): YouTubeUserUnbanResult {
        val response = requestJson(
            method = "POST",
            path = "/youtube/live-chat/users/unban",
            accessToken = accessToken,
            body = request.toJson()
        )

        return YouTubeUserUnbanResult(
            liveChatBanId = response.optString("liveChatBanId", request.liveChatBanId),
            actionType = response.optString("actionType", "unbanUser"),
            reason = response.optString("reason", request.reason),
            actedAt = response.optString("actedAt")
        )
    }

    override suspend fun discordWebhookConfig(accessToken: String, profileId: String): DiscordWebhookConfig {
        return requestJson(
            method = "GET",
            path = "/integrations/discord/webhook?profileId=${profileId.urlEncoded()}",
            accessToken = accessToken
        ).toDiscordWebhookConfig(profileId)
    }

    override suspend fun upsertDiscordWebhook(
        accessToken: String,
        request: DiscordWebhookUpsertRequest
    ): DiscordWebhookConfig {
        return requestJson(
            method = "PUT",
            path = "/integrations/discord/webhook",
            accessToken = accessToken,
            body = request.toJson()
        ).toDiscordWebhookConfig(request.profileId)
    }

    override suspend fun deleteDiscordWebhook(accessToken: String, profileId: String) {
        requestJson(
            method = "DELETE",
            path = "/integrations/discord/webhook?profileId=${profileId.urlEncoded()}",
            accessToken = accessToken
        )
    }

    override suspend fun testDiscordWebhook(accessToken: String, profileId: String): DiscordAlertResult {
        return requestJson(
            method = "POST",
            path = "/integrations/discord/test",
            accessToken = accessToken,
            body = JSONObject().put("profileId", profileId)
        ).toDiscordAlertResult(profileId)
    }

    override suspend fun sendDiscordAlert(accessToken: String, request: DiscordAlertRequest): DiscordAlertResult {
        return requestJson(
            method = "POST",
            path = "/integrations/discord/alerts",
            accessToken = accessToken,
            body = request.toJson()
        ).toDiscordAlertResult(request.profileId)
    }

    override suspend fun overlayConfig(accessToken: String, profileId: String): OverlayConfig {
        return requestJson(
            method = "GET",
            path = "/overlays/profiles/${profileId.urlEncoded()}",
            accessToken = accessToken
        ).toOverlayConfig(profileId)
    }

    override suspend fun upsertOverlayConfig(
        accessToken: String,
        request: OverlayConfigUpdateRequest
    ): OverlayConfigMutationResult {
        return requestJson(
            method = "PUT",
            path = "/overlays/profiles/${request.profileId.urlEncoded()}",
            accessToken = accessToken,
            body = request.toJson()
        ).toOverlayConfig(request.profileId)
    }

    override suspend fun rotateOverlayToken(accessToken: String, profileId: String): OverlayConfigMutationResult {
        return requestJson(
            method = "POST",
            path = "/overlays/profiles/${profileId.urlEncoded()}/rotate-token",
            accessToken = accessToken
        ).toOverlayConfig(profileId)
    }

    override suspend fun listTeamMembers(accessToken: String, profileId: String): TeamMemberList {
        val response = requestJson(
            method = "GET",
            path = "/team/profiles/${profileId.urlEncoded()}/members",
            accessToken = accessToken
        )
        val members = response.optJSONArray("members") ?: JSONArray()
        return TeamMemberList(
            profileId = response.optString("profileId", profileId),
            teamSeats = response.optInt("teamSeats", 1),
            extraSeats = response.optInt("extraSeats", 0),
            members = List(members.length()) { index -> members.getJSONObject(index).toTeamMemberRecord() }
        )
    }

    override suspend fun createTeamInvite(
        accessToken: String,
        profileId: String,
        request: TeamInviteCreateRequest
    ): TeamInviteResult {
        val response = requestJson(
            method = "POST",
            path = "/team/profiles/${profileId.urlEncoded()}/invites",
            accessToken = accessToken,
            body = request.toJson()
        )
        return TeamInviteResult(
            member = response.getJSONObject("member").toTeamMemberRecord(),
            inviteCode = response.optString("inviteCode")
        )
    }

    override suspend fun revokeTeamMember(accessToken: String, profileId: String, memberId: String): TeamMemberRecord {
        return requestJson(
            method = "DELETE",
            path = "/team/profiles/${profileId.urlEncoded()}/members/${memberId.urlEncoded()}",
            accessToken = accessToken
        ).getJSONObject("member").toTeamMemberRecord()
    }

    override suspend fun redeemTeamInvite(accessToken: String, request: TeamInviteRedeemRequest): TeamMembershipRecord {
        return requestJson(
            method = "POST",
            path = "/team/invites/redeem",
            accessToken = accessToken,
            body = request.toJson()
        ).getJSONObject("membership").toTeamMembershipRecord()
    }

    override suspend fun listTeamMemberships(accessToken: String): TeamMembershipList {
        val response = requestJson(
            method = "GET",
            path = "/team/memberships",
            accessToken = accessToken
        )
        val memberships = response.optJSONArray("memberships") ?: JSONArray()
        return TeamMembershipList(
            memberships = List(memberships.length()) { index -> memberships.getJSONObject(index).toTeamMembershipRecord() }
        )
    }

    override suspend fun deleteCurrentAccount(accessToken: String): AccountDeletionResult {
        val response = requestJson(
            method = "DELETE",
            path = "/accounts/current",
            accessToken = accessToken
        )
        val deviceIds = response.optJSONArray("deviceIds") ?: JSONArray()

        return AccountDeletionResult(
            deleted = response.optBoolean("deleted", false),
            userId = response.optNullableString("userId"),
            deviceIds = List(deviceIds.length()) { index -> deviceIds.getString(index) },
            supportEventsDeleted = response.optInt("supportEventsDeleted", 0),
            auditLogsDeleted = response.optInt("auditLogsDeleted", 0),
            apiErrorsDeleted = response.optInt("apiErrorsDeleted", 0)
        )
    }

    override suspend fun listSupportEvents(accessToken: String): SupportEventList {
        val response = requestJson(
            method = "GET",
            path = "/logs/support-events",
            accessToken = accessToken
        )
        val events = response.optJSONArray("events") ?: JSONArray()

        return SupportEventList(
            events = List(events.length()) { index ->
                events.getJSONObject(index).toSupportEventRecord()
            }
        )
    }

    override suspend fun listApiErrors(accessToken: String): ApiErrorList {
        val response = requestJson(
            method = "GET",
            path = "/logs/api-errors",
            accessToken = accessToken
        )
        val errors = response.optJSONArray("errors") ?: JSONArray()

        return ApiErrorList(
            errors = List(errors.length()) { index ->
                errors.getJSONObject(index).toApiErrorRecord()
            }
        )
    }

    override suspend fun recordSupportEvent(
        accessToken: String,
        request: SupportEventRequest
    ): SupportEventRecord {
        val response = requestJson(
            method = "POST",
            path = "/logs/support-events",
            accessToken = accessToken,
            body = JSONObject()
                .put("severity", request.severity)
                .put("message", request.message)
                .put("details", request.details.toJsonObject())
        )

        return response.toSupportEventRecord()
    }

    override suspend fun recordAnalyticsEvent(
        accessToken: String,
        request: AnalyticsEventRequest
    ): AnalyticsEventRecord {
        val body = JSONObject()
            .put("name", request.name)
            .put("consent", request.consent)
            .put("platform", request.platform)
            .put("properties", request.properties.toJsonObject())
        request.occurredAt?.let { body.put("occurredAt", it) }
        request.appVersion?.let { body.put("appVersion", it) }

        return requestJson(
            method = "POST",
            path = "/analytics/events",
            accessToken = accessToken,
            body = body
        ).toAnalyticsEventRecord()
    }

    override suspend fun listBetaFeedback(accessToken: String): BetaFeedbackList {
        val response = requestJson(
            method = "GET",
            path = "/feedback/beta",
            accessToken = accessToken
        )
        val feedback = response.optJSONArray("feedback") ?: JSONArray()

        return BetaFeedbackList(
            feedback = List(feedback.length()) { index ->
                feedback.getJSONObject(index).toBetaFeedbackRecord()
            }
        )
    }

    override suspend fun submitBetaFeedback(
        accessToken: String,
        request: BetaFeedbackRequest
    ): BetaFeedbackRecord {
        val body = JSONObject()
            .put("category", request.category)
            .put("message", request.message)
            .put("platform", request.platform)
            .put("context", request.context.toJsonObject())
        request.occurredAt?.let { body.put("occurredAt", it) }
        request.appVersion?.let { body.put("appVersion", it) }

        return requestJson(
            method = "POST",
            path = "/feedback/beta",
            accessToken = accessToken,
            body = body
        ).toBetaFeedbackRecord()
    }

    override suspend fun evaluateMessage(
        accessToken: String,
        message: ChatMessage,
        profile: ModerationProfile
    ): ModerationDecision {
        val response = requestJson(
            method = "POST",
            path = "/moderation/rules/evaluate",
            accessToken = accessToken,
            body = JSONObject()
                .put(
                    "message",
                    JSONObject()
                        .put("id", message.id)
                        .put("authorChannelId", message.authorChannelId)
                        .put("authorName", message.authorName)
                        .put("text", message.text)
                        .put("timestamp", Instant.ofEpochMilli(message.timestampMillis ?: System.currentTimeMillis()).toString())
                        .also { payload ->
                            message.streamStartedAtMillis?.let { startedAtMillis ->
                                payload.put("streamStartedAt", Instant.ofEpochMilli(startedAtMillis).toString())
                            }
                        }
                        .put("isOwner", message.isOwner)
                        .put("isModerator", message.isModerator)
                        .put("isMember", message.isMember)
                        .put("isVerified", message.isVerified)
                )
                .put(
                    "profile",
                    profile.toJson()
                )
        )

        val actions = response.optJSONArray("actions") ?: JSONArray()
        return ModerationDecision(
            matched = response.optBoolean("matched", false),
            actions = List(actions.length()) { index ->
                actions.getJSONObject(index).toModerationAction()
            }
        )
    }

    override suspend fun evaluateModerationSuggestion(
        accessToken: String,
        message: ChatMessage,
        profile: ModerationProfile,
        recentMessages: List<ChatMessage>,
        confidenceThreshold: Double
    ): ModerationSuggestionResult {
        return requestJson(
            method = "POST",
            path = "/moderation/suggestions/evaluate",
            accessToken = accessToken,
            body = JSONObject()
                .put("message", message.toJsonObject())
                .put("profile", profile.toJson())
                .put(
                    "context",
                    JSONObject().put(
                        "recentMessages",
                        JSONArray(recentMessages.map { recent -> recent.toJsonObject() })
                    )
                )
                .put(
                    "options",
                    JSONObject()
                        .put("confidenceThreshold", confidenceThreshold)
                        .put("manualApprovalRequired", true)
                )
        ).toModerationSuggestionResult()
    }

    override suspend fun listRulePresets(accessToken: String, profileId: String?): RulePresetList {
        val path = profileId?.let { "/rule-presets?profileId=${it.urlEncoded()}" } ?: "/rule-presets"
        val response = requestJson(
            method = "GET",
            path = path,
            accessToken = accessToken
        )
        val presets = response.optJSONArray("rulePresets") ?: JSONArray()

        return RulePresetList(
            rulePresets = List(presets.length()) { index ->
                presets.getJSONObject(index).toRulePresetRecord()
            }
        )
    }

    override suspend fun exportRulePresets(accessToken: String, profileId: String): RulePresetExportBundle {
        return requestJson(
            method = "GET",
            path = "/rule-presets/export?profileId=${profileId.urlEncoded()}",
            accessToken = accessToken
        ).toRulePresetExportBundle()
    }

    override suspend fun importRulePresets(
        accessToken: String,
        request: RulePresetImportRequest
    ): RulePresetImportResult {
        return requestJson(
            method = "POST",
            path = "/rule-presets/import",
            accessToken = accessToken,
            body = request.toJson()
        ).toRulePresetImportResult()
    }

    override suspend fun listRulePresetTemplates(accessToken: String): RulePresetTemplateList {
        val response = requestJson(
            method = "GET",
            path = "/rule-presets/templates",
            accessToken = accessToken
        )
        val templates = response.optJSONArray("rulePresetTemplates") ?: JSONArray()

        return RulePresetTemplateList(
            rulePresetTemplates = List(templates.length()) { index ->
                templates.getJSONObject(index).toRulePresetTemplateRecord()
            }
        )
    }

    override suspend fun saveRulePreset(
        accessToken: String,
        request: RulePresetSyncRequest
    ): RulePresetRecord {
        return requestJson("PUT", "/rule-presets/${request.id}", accessToken, request.toJson())
            .toRulePresetRecord()
    }

    override suspend fun deleteRulePreset(accessToken: String, presetId: String) {
        try {
            requestJson("DELETE", "/rule-presets/$presetId", accessToken)
        } catch (error: ChatModHttpException) {
            if (error.statusCode != 404) throw error
        }
    }

    override suspend fun upsertStreamSession(
        accessToken: String,
        sessionId: String,
        request: StreamSessionSyncRequest
    ): StreamSessionRecord {
        return requestJson("PUT", "/stream-sessions/$sessionId", accessToken, request.toJson())
            .toStreamSessionRecord()
    }

    override suspend fun endStreamSession(
        accessToken: String,
        sessionId: String,
        endedAtIso: String?
    ): StreamSessionRecord {
        val body = JSONObject()
        endedAtIso?.let { body.put("endedAt", it) }
        return requestJson("POST", "/stream-sessions/$sessionId/end", accessToken, body)
            .toStreamSessionRecord()
    }

    override suspend fun streamSessionLogs(accessToken: String, sessionId: String): StreamSessionLogs {
        val response = requestJson("GET", "/stream-sessions/$sessionId/logs", accessToken)
        val messages = response.optJSONArray("messages") ?: JSONArray()
        val actions = response.optJSONArray("actions") ?: JSONArray()
        val runtimeEvents = response.optJSONArray("runtimeEvents") ?: JSONArray()

        return StreamSessionLogs(
            session = response.getJSONObject("session").toStreamSessionRecord(),
            messages = List(messages.length()) { index ->
                messages.getJSONObject(index).toChatMessageLogRecord()
            },
            actions = List(actions.length()) { index ->
                actions.getJSONObject(index).toModerationActionLogRecord()
            },
            runtimeEvents = List(runtimeEvents.length()) { index ->
                runtimeEvents.getJSONObject(index).toRuntimeEventRecord()
            }
        )
    }

    override suspend fun streamSessionAnalyticsSummary(
        accessToken: String,
        profileId: String?,
        days: Int
    ): StreamSessionAnalyticsSummary {
        val query = mutableListOf("days=${days.coerceIn(1, 365)}")
        profileId?.takeIf { it.isNotBlank() }?.let { query.add("profileId=${it.urlEncoded()}") }
        return requestJson("GET", "/stream-sessions/analytics/summary?${query.joinToString("&")}", accessToken)
            .toStreamSessionAnalyticsSummary()
    }

    override suspend fun streamChatSummary(accessToken: String, sessionId: String): StreamChatSummary {
        return requestJson("GET", "/stream-sessions/${sessionId.urlEncoded()}/ai-summary", accessToken)
            .toStreamChatSummary()
    }

    override suspend fun listFaqEntries(accessToken: String, profileId: String): FaqEntryList {
        val response = requestJson("GET", "/faq-entries?profileId=${profileId.urlEncoded()}", accessToken)
        return FaqEntryList(
            faqEntries = response.optJSONArray("faqEntries").toObjectList { item -> item.toFaqEntryRecord() }
        )
    }

    override suspend fun saveFaqEntry(accessToken: String, request: FaqEntrySyncRequest): FaqEntryRecord {
        return requestJson(
            method = "PUT",
            path = "/faq-entries/${request.id.urlEncoded()}",
            accessToken = accessToken,
            body = request.toJson()
        ).toFaqEntryRecord()
    }

    override suspend fun deleteFaqEntry(accessToken: String, faqEntryId: String) {
        requestJson("DELETE", "/faq-entries/${faqEntryId.urlEncoded()}", accessToken)
    }

    override suspend fun suggestFaqReply(
        accessToken: String,
        request: FaqReplySuggestionRequest
    ): FaqReplySuggestionResult {
        return requestJson(
            method = "POST",
            path = "/faq-entries/suggest-reply",
            accessToken = accessToken,
            body = request.toJson()
        ).toFaqReplySuggestionResult()
    }

    override suspend fun recordStreamMessage(
        accessToken: String,
        sessionId: String,
        request: ChatMessageLogSyncRequest
    ): ChatMessageLogRecord {
        return requestJson("POST", "/stream-sessions/$sessionId/messages", accessToken, request.toJson())
            .toChatMessageLogRecord()
    }

    override suspend fun recordModerationActionLog(
        accessToken: String,
        sessionId: String,
        request: ModerationActionLogSyncRequest
    ): ModerationActionLogRecord {
        return requestJson("POST", "/stream-sessions/$sessionId/actions", accessToken, request.toJson())
            .toModerationActionLogRecord()
    }

    override suspend fun reviewModerationActionLog(
        accessToken: String,
        sessionId: String,
        actionId: String,
        request: ModerationActionReviewRequest
    ): ModerationActionLogRecord {
        return requestJson(
            "PATCH",
            "/stream-sessions/$sessionId/actions/$actionId/review",
            accessToken,
            request.toJson()
        ).toModerationActionLogRecord()
    }

    override suspend fun recordRuntimeEvent(
        accessToken: String,
        sessionId: String,
        request: RuntimeEventSyncRequest
    ): RuntimeEventRecord {
        return requestJson("POST", "/stream-sessions/$sessionId/runtime-events", accessToken, request.toJson())
            .toRuntimeEventRecord()
    }

    override suspend fun saveCommand(
        accessToken: String,
        request: CommandSyncRequest
    ): CommandSyncResult {
        val response = requestJson("PUT", "/commands/${request.id}", accessToken, request.toJson())

        return CommandSyncResult(
            id = response.getString("id"),
            syncedAt = response.optString("updatedAt", response.optString("createdAt"))
        )
    }

    override suspend fun sendCommandToLiveChat(
        accessToken: String,
        commandId: String,
        request: CommandManualSendRequest
    ): CommandManualSendResult {
        val response = requestJson(
            method = "POST",
            path = "/commands/$commandId/send",
            accessToken = accessToken,
            body = request.toJson()
        )

        return CommandManualSendResult(
            commandId = response.optString("commandId", commandId),
            commandName = response.optString("commandName"),
            liveChatId = response.optString("liveChatId", request.liveChatId),
            messageId = response.optString("messageId"),
            sentText = response.optString("sentText"),
            sentAt = response.optString("sentAt")
        )
    }

    override suspend fun deleteCommand(accessToken: String, commandId: String) {
        try {
            requestJson("DELETE", "/commands/$commandId", accessToken)
        } catch (error: ChatModHttpException) {
            if (error.statusCode != 404) throw error
        }
    }

    override suspend fun listUserProfiles(accessToken: String, profileId: String?): UserProfileList {
        val path = if (profileId.isNullOrBlank()) {
            "/user-profiles"
        } else {
            "/user-profiles?profileId=${profileId.urlEncoded()}"
        }
        val response = requestJson("GET", path, accessToken)
        val users = response.optJSONArray("users") ?: JSONArray()
        return UserProfileList(
            users = List(users.length()) { index ->
                users.getJSONObject(index).toUserProfileRecord()
            }
        )
    }

    override suspend fun warnUser(
        accessToken: String,
        request: UserWarningRequest
    ): UserWarningResult {
        return requestJson("POST", "/user-profiles/warnings", accessToken, request.toJson())
            .toUserWarningResult()
    }

    override suspend fun updateUserProfileNotes(
        accessToken: String,
        userProfileId: String,
        request: UserProfileNotesRequest
    ): UserProfileRecord {
        val response = requestJson(
            "PATCH",
            "/user-profiles/${userProfileId.urlEncoded()}/notes",
            accessToken,
            request.toJson()
        )
        return response.getJSONObject("user").toUserProfileRecord()
    }

    override suspend fun hideUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserHideRequest
    ): UserHideResult {
        val response = requestJson(
            "POST",
            "/user-profiles/${userProfileId.urlEncoded()}/hide",
            accessToken,
            request.toJson()
        )
        return UserHideResult(
            user = response.getJSONObject("user").toUserProfileRecord(),
            action = response.optJSONObject("action")?.toUserModerationActionRecord(),
            liveChatId = response.optString("liveChatId", request.liveChatId),
            liveChatBanId = response.optNullableString("liveChatBanId"),
            actionType = response.optString("actionType", "hideUser"),
            reason = response.optString("reason", request.reason),
            hiddenAt = response.optString("hiddenAt")
        )
    }

    override suspend fun timeoutUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserTimeoutRequest
    ): UserTimeoutResult {
        val response = requestJson(
            "POST",
            "/user-profiles/${userProfileId.urlEncoded()}/timeout",
            accessToken,
            request.toJson()
        )
        return UserTimeoutResult(
            user = response.getJSONObject("user").toUserProfileRecord(),
            action = response.optJSONObject("action")?.toUserModerationActionRecord(),
            liveChatId = response.optString("liveChatId", request.liveChatId),
            liveChatBanId = response.optNullableString("liveChatBanId"),
            durationSeconds = response.optInt("durationSeconds", request.durationSeconds),
            actionType = response.optString("actionType", "timeoutUser"),
            reason = response.optString("reason", request.reason),
            timedOutAt = response.optString("timedOutAt"),
            timedOutUntil = response.optString("timedOutUntil")
        )
    }

    override suspend fun unbanUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserUnbanRequest
    ): UserUnbanResult {
        val response = requestJson(
            method = "POST",
            path = "/user-profiles/$userProfileId/unban",
            accessToken = accessToken,
            body = request.toJson()
        )

        return UserUnbanResult(
            user = response.getJSONObject("user").toUserProfileRecord(),
            action = response.optJSONObject("action")?.toUserModerationActionRecord(),
            liveChatBanId = response.optString("liveChatBanId", request.liveChatBanId),
            actionType = response.optString("actionType", "unbanUser"),
            reason = response.optString("reason", request.reason),
            unbannedAt = response.optString("unbannedAt")
        )
    }

    override suspend fun whitelistUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserWhitelistRequest
    ): UserWhitelistResult {
        val response = requestJson(
            "POST",
            "/user-profiles/${userProfileId.urlEncoded()}/whitelist",
            accessToken,
            request.toJson()
        )
        return UserWhitelistResult(
            user = response.getJSONObject("user").toUserProfileRecord(),
            whitelist = response.getJSONObject("whitelist").toUserWhitelistRecord(),
            whitelistedAt = response.optString("whitelistedAt")
        )
    }

    override suspend fun saveTimer(
        accessToken: String,
        request: TimerSyncRequest
    ): TimerSyncResult {
        val response = requestJson("PUT", "/timers/${request.id}", accessToken, request.toJson())

        return TimerSyncResult(
            id = response.getString("id"),
            syncedAt = response.optString("updatedAt", response.optString("createdAt"))
        )
    }

    override suspend fun deleteTimer(accessToken: String, timerId: String) {
        try {
            requestJson("DELETE", "/timers/$timerId", accessToken)
        } catch (error: ChatModHttpException) {
            if (error.statusCode != 404) throw error
        }
    }

    private suspend fun requestJson(
        method: String,
        path: String,
        accessToken: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val methodName = method.uppercase()
        var attempt = 0
        var lastError: ChatModHttpException? = null
        var lastIoError: IOException? = null

        while (attempt < MaxAttempts) {
            try {
                return executeJsonRequest(methodName, path, accessToken, body)
            } catch (error: ChatModHttpException) {
                lastError = error
                if (!shouldRetry(methodName, error.statusCode, attempt)) {
                    throw error
                }
                delay(error.retryAfterMillis ?: retryDelayMillis(attempt))
            } catch (error: IOException) {
                lastIoError = error
                if (!shouldRetry(methodName, null, attempt)) {
                    throw error
                }
                delay(retryDelayMillis(attempt))
            }
            attempt += 1
        }

        lastError?.let { throw it }
        throw lastIoError ?: IOException("ChatMod request failed.")
    }

    private suspend fun executeJsonRequest(
        method: String,
        path: String,
        accessToken: String?,
        body: JSONObject?
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }

            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { stream ->
                    stream.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
        }

        val statusCode = connection.responseCode
        val responseText = (if (statusCode in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()
            ?.use { it.readText() }
            .orEmpty()
        val retryAfterMillis = connection.getHeaderField("Retry-After")?.toRetryAfterMillis()
        val errorPayload = responseText.toHttpErrorPayload()

        connection.disconnect()

        if (statusCode !in 200..299) {
            throw ChatModHttpException(
                statusCode = statusCode,
                message = errorPayload.message ?: responseText.ifBlank { "HTTP $statusCode" },
                errorCode = errorPayload.code,
                retryAfterMillis = retryAfterMillis
            )
        }

        if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
    }

    private companion object {
        const val MaxAttempts = 3
    }
}

class ChatModHttpException(
    val statusCode: Int,
    message: String,
    val errorCode: String? = null,
    val retryAfterMillis: Long? = null
) : RuntimeException(message)

private data class HttpErrorPayload(
    val code: String?,
    val message: String?
)

private fun shouldRetry(method: String, statusCode: Int?, attempt: Int): Boolean {
    if (attempt >= 2 || method !in setOf("GET", "PUT", "DELETE")) {
        return false
    }

    return statusCode == null || statusCode in setOf(408, 429, 500, 502, 503, 504)
}

private fun retryDelayMillis(attempt: Int): Long {
    return when (attempt) {
        0 -> 500L
        else -> 1_000L
    }
}

private fun String.toRetryAfterMillis(): Long? {
    val seconds = trim().toLongOrNull()
    return seconds?.coerceIn(0, 5)?.times(1_000L)
}

private fun String.toHttpErrorPayload(): HttpErrorPayload {
    if (isBlank()) {
        return HttpErrorPayload(code = null, message = null)
    }

    return runCatching {
        val json = JSONObject(this)
        HttpErrorPayload(
            code = json.optString("error").takeIf { it.isNotBlank() },
            message = json.optString("message").takeIf { it.isNotBlank() }
        )
    }.getOrDefault(HttpErrorPayload(code = null, message = null))
}

private fun String.urlEncoded(): String {
    return URLEncoder.encode(this, Charsets.UTF_8.name())
}

private fun JSONObject.toEntitlementSnapshot(): EntitlementSnapshot {
    return EntitlementSnapshot(
        plan = getString("plan"),
        status = getString("status"),
        source = optString("source", "backend"),
        productId = optNullableString("productId"),
        currentPeriodEndsAt = optNullableString("currentPeriodEndsAt"),
        features = optJSONObject("features")?.toMap() ?: emptyMap()
    )
}

private fun JSONObject.toChannelProfileRecord(): ChannelProfileRecord {
    return ChannelProfileRecord(
        id = getString("id"),
        channelId = optString("channelId"),
        name = optString("name", "Channel profile"),
        config = optJSONObject("config")?.toMap() ?: emptyMap(),
        createdAt = optString("createdAt"),
        updatedAt = optString("updatedAt")
    )
}

private fun JSONObject.toLinkedAccountSummary(): LinkedAccountSummary {
    return LinkedAccountSummary(
        provider = optString("provider"),
        providerAccountId = optString("providerAccountId"),
        channelId = optNullableString("channelId"),
        channelTitle = optNullableString("channelTitle"),
        tokenExpiresAt = optNullableString("tokenExpiresAt")
    )
}

private fun JSONObject.toYouTubeConnectUrl(): YouTubeConnectUrl {
    val requiredScopes = optJSONArray("requiredScopes") ?: JSONArray()
    val missingEnv = optJSONArray("missingEnv") ?: JSONArray()
    return YouTubeConnectUrl(
        url = optNullableString("url"),
        configured = optBoolean("configured", false),
        requiredScopes = List(requiredScopes.length()) { index -> requiredScopes.optString(index) }
            .filter { it.isNotBlank() },
        missingEnv = List(missingEnv.length()) { index -> missingEnv.optString(index) }
            .filter { it.isNotBlank() },
        note = optNullableString("note")
    )
}

private fun JSONObject.toYouTubeAccountStatus(): YouTubeAccountStatus {
    return YouTubeAccountStatus(
        configured = optBoolean("configured", false),
        source = optString("source", "backend"),
        account = optJSONObject("account")?.toYouTubeLinkedAccountStatus()
            ?: YouTubeLinkedAccountStatus(
                connected = false,
                linkedAccountId = null,
                channelId = null,
                channelTitle = null,
                hasAccessToken = false,
                hasRefreshToken = false,
                tokenExpiresAt = null
            )
    )
}

private fun JSONObject.toYouTubeLinkedAccountStatus(): YouTubeLinkedAccountStatus {
    return YouTubeLinkedAccountStatus(
        connected = optBoolean("connected", false),
        linkedAccountId = optNullableString("linkedAccountId"),
        channelId = optNullableString("channelId"),
        channelTitle = optNullableString("channelTitle"),
        hasAccessToken = optBoolean("hasAccessToken", false),
        hasRefreshToken = optBoolean("hasRefreshToken", false),
        tokenExpiresAt = optNullableString("tokenExpiresAt")
    )
}

private fun JSONObject.toDiscordWebhookConfig(fallbackProfileId: String): DiscordWebhookConfig {
    return DiscordWebhookConfig(
        profileId = optString("profileId", fallbackProfileId),
        configured = optBoolean("configured", false),
        enabled = optBoolean("enabled", false),
        alertModerationActions = optBoolean("alertModerationActions", true),
        alertRuntimeStatus = optBoolean("alertRuntimeStatus", false),
        createdAt = optNullableString("createdAt"),
        updatedAt = optNullableString("updatedAt")
    )
}

private fun JSONObject.toDiscordAlertResult(fallbackProfileId: String): DiscordAlertResult {
    return DiscordAlertResult(
        sent = optBoolean("sent", false),
        skippedReason = optNullableString("skippedReason"),
        sentAt = optNullableString("sentAt"),
        profileId = optString("profileId", fallbackProfileId)
    )
}

private fun JSONObject.toOverlayConfig(fallbackProfileId: String): OverlayConfig {
    return OverlayConfig(
        profileId = optString("profileId", fallbackProfileId),
        configured = optBoolean("configured", false),
        enabled = optBoolean("enabled", false),
        theme = optString("theme", "control_room"),
        activeSessionId = optNullableString("activeSessionId"),
        showModerationActions = optBoolean("showModerationActions", true),
        showRuntimeStatus = optBoolean("showRuntimeStatus", true),
        showViewerStats = optBoolean("showViewerStats", true),
        showRecentChat = optBoolean("showRecentChat", false),
        tokenPreview = optNullableString("tokenPreview"),
        publicUrl = optNullableString("publicUrl"),
        publicPath = optNullableString("publicPath"),
        tokenRotated = optBoolean("tokenRotated", false),
        allowed = if (has("allowed")) optBoolean("allowed") else null,
        requiredPlan = optNullableString("requiredPlan"),
        createdAt = optNullableString("createdAt"),
        updatedAt = optNullableString("updatedAt")
    )
}

private fun JSONObject.toTeamMemberPermissions(): TeamMemberPermissions {
    return TeamMemberPermissions(
        viewQueue = optBoolean("viewQueue", true),
        moderate = optBoolean("moderate", true),
        manageWarnings = optBoolean("manageWarnings", true),
        viewAnalytics = optBoolean("viewAnalytics", false)
    )
}

private fun JSONObject.toTeamMemberRecord(): TeamMemberRecord {
    return TeamMemberRecord(
        id = optString("id"),
        profileId = optString("profileId"),
        displayName = optString("displayName", "Team member"),
        role = optString("role", "moderator"),
        status = optString("status", "invited"),
        inviteCodePreview = optString("inviteCodePreview"),
        memberDeviceId = optNullableString("memberDeviceId"),
        permissions = optJSONObject("permissions")?.toTeamMemberPermissions() ?: TeamMemberPermissions(),
        acceptedAt = optNullableString("acceptedAt"),
        revokedAt = optNullableString("revokedAt"),
        createdAt = optString("createdAt"),
        updatedAt = optString("updatedAt")
    )
}

private fun JSONObject.toTeamMembershipRecord(): TeamMembershipRecord {
    return TeamMembershipRecord(
        member = toTeamMemberRecord(),
        profileName = optString("profileName", "Team profile"),
        channelId = optString("channelId"),
        ownerDeviceId = optString("ownerDeviceId")
    )
}

private fun JSONObject.toAppCompatibility(): AppCompatibility {
    return AppCompatibility(
        platform = optString("platform", "android"),
        currentVersionName = optString("currentVersionName"),
        currentVersionCode = optInt("currentVersionCode"),
        minimumSupportedVersionName = optString("minimumSupportedVersionName"),
        minimumSupportedVersionCode = optInt("minimumSupportedVersionCode"),
        latestVersionName = optString("latestVersionName"),
        latestVersionCode = optInt("latestVersionCode"),
        status = optString("status", "unknown"),
        updateRequired = optBoolean("updateRequired", false),
        updateRecommended = optBoolean("updateRecommended", false),
        message = optString("message", "Compatibility check completed."),
        downloadUrl = optNullableString("downloadUrl")
    )
}

private fun JSONObject.toModerationAction(): ModerationAction {
    return ModerationAction(
        type = optString("type").toActionType(),
        reason = optString("reason", "backend_rule"),
        confidence = optDouble("confidence", 0.0),
        text = optNullableString("text")
    )
}

private fun ChatMessage.toJsonObject(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("authorChannelId", authorChannelId)
        .put("authorName", authorName)
        .put("text", text)
        .put("timestamp", Instant.ofEpochMilli(timestampMillis ?: System.currentTimeMillis()).toString())
        .put("isOwner", isOwner)
        .put("isModerator", isModerator)
        .put("isMember", isMember)
        .put("isVerified", isVerified)
        .also { payload ->
            streamStartedAtMillis?.let { startedAtMillis ->
                payload.put("streamStartedAt", Instant.ofEpochMilli(startedAtMillis).toString())
            }
        }
}

private fun JSONObject.toModerationSuggestionResult(): ModerationSuggestionResult {
    val reasons = optJSONArray("reasons") ?: JSONArray()
    val classification = optJSONArray("classification") ?: JSONArray()
    return ModerationSuggestionResult(
        provider = optString("provider", "local-heuristic"),
        manualApprovalRequired = optBoolean("manualApprovalRequired", true),
        suggestedAction = optString("suggestedAction", "allow"),
        classification = List(classification.length()) { index -> classification.optString(index) },
        confidence = optDouble("confidence", 0.0),
        confidenceThreshold = optDouble("confidenceThreshold", 0.65),
        reasons = List(reasons.length()) { index ->
            reasons.getJSONObject(index).toModerationSuggestionReason()
        },
        explanation = optString("explanation"),
        usage = optJSONObject("usage")?.toModerationSuggestionUsage()
    )
}

private fun JSONObject.toModerationSuggestionUsage(): ModerationSuggestionUsage {
    return ModerationSuggestionUsage(
        used = optInt("used", 0),
        limit = optInt("limit", 0),
        remaining = optInt("remaining", 0),
        resetAt = optString("resetAt")
    )
}

private fun JSONObject.toModerationSuggestionReason(): ModerationSuggestionReason {
    return ModerationSuggestionReason(
        code = optString("code"),
        label = optString("label"),
        detail = optString("detail"),
        confidence = optDouble("confidence", 0.0)
    )
}

private fun JSONObject.toCloudBackup(): CloudBackup {
    return CloudBackup(
        id = getString("id"),
        profileName = optString("profileName"),
        channelId = optString("channelId"),
        version = optInt("version", 1),
        clientVersion = optNullableString("clientVersion"),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toSupportEventRecord(): SupportEventRecord {
    return SupportEventRecord(
        id = getString("id"),
        severity = optString("severity", "info"),
        message = optString("message"),
        details = optJSONObject("details")?.toMap(),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toAnalyticsEventRecord(): AnalyticsEventRecord {
    return AnalyticsEventRecord(
        id = getString("id"),
        name = optString("name"),
        occurredAt = optString("occurredAt"),
        appVersion = optNullableString("appVersion"),
        platform = optString("platform", "android"),
        properties = optJSONObject("properties")?.toMap() ?: emptyMap(),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toBetaFeedbackRecord(): BetaFeedbackRecord {
    return BetaFeedbackRecord(
        id = getString("id"),
        category = optString("category", "other"),
        message = optString("message"),
        occurredAt = optString("occurredAt"),
        appVersion = optNullableString("appVersion"),
        platform = optString("platform", "android"),
        context = optJSONObject("context")?.toMap() ?: emptyMap(),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toApiErrorRecord(): ApiErrorRecord {
    return ApiErrorRecord(
        id = getString("id"),
        provider = optString("provider", "backend"),
        code = optNullableString("code"),
        message = optString("message"),
        metadata = optJSONObject("metadata")?.toMap(),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toYouTubeActiveChat(): YouTubeActiveChat {
    return YouTubeActiveChat(
        liveChatId = getString("liveChatId"),
        videoId = getString("videoId")
    )
}

private fun JSONObject.toYouTubeBroadcast(): YouTubeBroadcast {
    return YouTubeBroadcast(
        videoId = getString("videoId"),
        liveChatId = optNullableString("liveChatId"),
        title = optString("title"),
        status = optString("status", "active"),
        scheduledStartTime = optNullableString("scheduledStartTime"),
        actualStartTime = optNullableString("actualStartTime")
    )
}

private fun JSONObject.toYouTubeLiveChatMessageRecord(): YouTubeLiveChatMessageRecord {
    return YouTubeLiveChatMessageRecord(
        id = getString("id"),
        authorChannelId = optString("authorChannelId"),
        authorName = optString("authorName"),
        text = optString("text"),
        publishedAt = optString("publishedAt"),
        messageType = optString("messageType", "textMessageEvent"),
        isOwner = optBoolean("isOwner", false),
        isModerator = optBoolean("isModerator", false),
        isMember = optBoolean("isMember", false),
        isVerified = optBoolean("isVerified", false),
        purchaseAmountMicros = optNullableString("purchaseAmountMicros"),
        purchaseCurrency = optNullableString("purchaseCurrency"),
        targetMessageId = optNullableString("targetMessageId"),
        targetChannelId = optNullableString("targetChannelId")
    )
}

private fun JSONObject.toRulePresetRecord(): RulePresetRecord {
    return RulePresetRecord(
        id = getString("id"),
        profileId = getString("profileId"),
        name = optString("name"),
        config = optJSONObject("config")?.toModerationProfile() ?: ModerationProfile(),
        isDefault = optBoolean("isDefault", false),
        createdAt = optString("createdAt"),
        updatedAt = optString("updatedAt")
    )
}

private fun JSONObject.toRulePresetExportBundle(): RulePresetExportBundle {
    val presets = optJSONArray("rulePresets") ?: JSONArray()
    return RulePresetExportBundle(
        formatVersion = optInt("formatVersion", 1),
        exportedAt = optString("exportedAt"),
        profileId = optString("profileId"),
        rulePresets = List(presets.length()) { index ->
            presets.getJSONObject(index).toRulePresetRecord()
        }
    )
}

private fun JSONObject.toRulePresetImportResult(): RulePresetImportResult {
    val presets = optJSONArray("rulePresets") ?: JSONArray()
    return RulePresetImportResult(
        importedAt = optString("importedAt"),
        profileId = optString("profileId"),
        importedCount = optInt("importedCount", presets.length()),
        rulePresets = List(presets.length()) { index ->
            presets.getJSONObject(index).toRulePresetRecord()
        }
    )
}

private fun JSONObject.toRulePresetTemplateRecord(): RulePresetTemplateRecord {
    return RulePresetTemplateRecord(
        id = getString("id"),
        name = optString("name"),
        description = optString("description"),
        config = optJSONObject("config")?.toModerationProfile() ?: ModerationProfile()
    )
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

private fun RulePresetSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("name", name)
        .put("config", config.toJson())
        .put("isDefault", isDefault)
}

private fun ChannelProfileCreateRequest.toJson(): JSONObject {
    return JSONObject()
        .put("channelId", channelId)
        .put("name", name)
        .put("config", config.toJsonObject())
}

private fun RulePresetImportRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("bundle", bundle.toJson())
}

private fun RulePresetExportBundle.toJson(): JSONObject {
    return JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAt", exportedAt)
        .put("profileId", profileId)
        .put("rulePresets", JSONArray(rulePresets.map { preset -> preset.toJson() }))
}

private fun RulePresetRecord.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("profileId", profileId)
        .put("name", name)
        .put("config", config.toJson())
        .put("isDefault", isDefault)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
}

private fun JSONObject.toStreamSessionRecord(): StreamSessionRecord {
    return StreamSessionRecord(
        id = getString("id"),
        profileId = getString("profileId"),
        videoId = getString("videoId"),
        liveChatId = getString("liveChatId"),
        title = optNullableString("title"),
        startedAt = optString("startedAt"),
        endedAt = optNullableString("endedAt")
    )
}

private fun JSONObject.toChatMessageLogRecord(): ChatMessageLogRecord {
    return ChatMessageLogRecord(
        id = getString("id"),
        sessionId = getString("sessionId"),
        youtubeMessageId = getString("youtubeMessageId"),
        authorChannelId = getString("authorChannelId"),
        authorName = getString("authorName"),
        text = optString("text"),
        receivedAt = optString("receivedAt"),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toModerationActionLogRecord(): ModerationActionLogRecord {
    return ModerationActionLogRecord(
        id = getString("id"),
        sessionId = getString("sessionId"),
        youtubeMessageId = optNullableString("youtubeMessageId"),
        authorChannelId = optNullableString("authorChannelId"),
        actionType = optString("actionType"),
        reason = optString("reason"),
        confidence = if (has("confidence") && !isNull("confidence")) optDouble("confidence") else null,
        metadata = optJSONObject("metadata")?.toMap(),
        reviewStatus = optNullableString("reviewStatus"),
        reviewedAt = optNullableString("reviewedAt"),
        reviewNote = optNullableString("reviewNote"),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toRuntimeEventRecord(): RuntimeEventRecord {
    return RuntimeEventRecord(
        id = getString("id"),
        sessionId = getString("sessionId"),
        type = optString("type"),
        message = optString("message"),
        metadata = optJSONObject("metadata")?.toMap(),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toStreamSessionAnalyticsSummary(): StreamSessionAnalyticsSummary {
    return StreamSessionAnalyticsSummary(
        generatedAt = optString("generatedAt"),
        rangeDays = optInt("rangeDays", 30),
        sessionCount = optInt("sessionCount", 0),
        totalMessages = optInt("totalMessages", 0),
        totalModerationActions = optInt("totalModerationActions", 0),
        totalRuntimeEvents = optInt("totalRuntimeEvents", 0),
        totalUptimeMillis = optLong("totalUptimeMillis", 0L),
        reconnectEvents = optInt("reconnectEvents", 0),
        byStream = optJSONArray("byStream").toObjectList { item -> item.toStreamAnalyticsByStream() },
        byDay = optJSONArray("byDay").toObjectList { item -> item.toStreamAnalyticsByDay() },
        topChatters = optJSONArray("topChatters").toObjectList { item -> item.toStreamAnalyticsChatter() },
        commandUsage = optJSONArray("commandUsage").toObjectList { item -> item.toStreamAnalyticsCommand() },
        ruleEffectiveness = optJSONArray("ruleEffectiveness").toObjectList { item -> item.toStreamAnalyticsRule() },
        ruleEffectivenessByPreset = optJSONArray("ruleEffectivenessByPreset").toObjectList { item -> item.toStreamAnalyticsRulePreset() },
        spamAttemptsByDay = optJSONArray("spamAttemptsByDay").toObjectList { item -> item.toStreamAnalyticsSpamDay() },
        uptimeByStream = optJSONArray("uptimeByStream").toObjectList { item -> item.toStreamAnalyticsUptime() }
    )
}

private fun JSONObject.toStreamChatSummary(): StreamChatSummary {
    return StreamChatSummary(
        provider = optString("provider", "local-heuristic"),
        sessionId = optString("sessionId"),
        generatedAt = optString("generatedAt"),
        title = optNullableString("title"),
        summary = optString("summary"),
        highlights = optJSONArray("highlights").toStringList(),
        topQuestions = optJSONArray("topQuestions").toObjectList { item ->
            StreamChatSummaryQuestion(
                question = item.optString("question"),
                count = item.optInt("count", 0)
            )
        },
        topChatters = optJSONArray("topChatters").toObjectList { item ->
            StreamChatSummaryChatter(
                authorChannelId = item.optString("authorChannelId"),
                authorName = item.optString("authorName"),
                messageCount = item.optInt("messageCount", 0)
            )
        },
        moderationNotes = optJSONArray("moderationNotes").toStringList(),
        suggestedFollowUps = optJSONArray("suggestedFollowUps").toStringList(),
        stats = optJSONObject("stats")?.let { stats ->
            StreamChatSummaryStats(
                messageCount = stats.optInt("messageCount", 0),
                uniqueChatters = stats.optInt("uniqueChatters", 0),
                moderationActionCount = stats.optInt("moderationActionCount", 0),
                destructiveActionCount = stats.optInt("destructiveActionCount", 0)
            )
        } ?: StreamChatSummaryStats(
            messageCount = 0,
            uniqueChatters = 0,
            moderationActionCount = 0,
            destructiveActionCount = 0
        )
    )
}

private fun JSONObject.toFaqEntryRecord(): FaqEntryRecord {
    return FaqEntryRecord(
        id = optString("id"),
        profileId = optString("profileId"),
        question = optString("question"),
        answer = optString("answer"),
        keywords = optJSONArray("keywords").toStringList(),
        enabled = optBoolean("enabled", true),
        createdAt = optString("createdAt"),
        updatedAt = optString("updatedAt")
    )
}

private fun JSONObject.toFaqReplySuggestionResult(): FaqReplySuggestionResult {
    return FaqReplySuggestionResult(
        provider = optString("provider", "local-heuristic"),
        matched = optBoolean("matched", false),
        manualApprovalRequired = optBoolean("manualApprovalRequired", true),
        entryId = optNullableString("entryId"),
        question = optNullableString("question"),
        replyText = optNullableString("replyText"),
        confidence = optDouble("confidence", 0.0),
        matchedKeywords = optJSONArray("matchedKeywords").toStringList(),
        explanation = optString("explanation")
    )
}

private fun JSONObject.toStreamAnalyticsByStream(): StreamAnalyticsByStream {
    return StreamAnalyticsByStream(
        sessionId = optString("sessionId"),
        title = optNullableString("title"),
        videoId = optString("videoId"),
        startedAt = optString("startedAt"),
        endedAt = optNullableString("endedAt"),
        messageCount = optInt("messageCount", 0),
        uniqueChatters = optInt("uniqueChatters", 0),
        moderationActionCount = optInt("moderationActionCount", 0),
        destructiveActionCount = optInt("destructiveActionCount", 0),
        spamAttemptCount = optInt("spamAttemptCount", 0),
        commandCount = optInt("commandCount", 0),
        timerCount = optInt("timerCount", 0),
        reconnectEvents = optInt("reconnectEvents", 0),
        uptimeMillis = optLong("uptimeMillis", 0L)
    )
}

private fun JSONObject.toStreamAnalyticsByDay(): StreamAnalyticsByDay {
    return StreamAnalyticsByDay(
        day = optString("day"),
        streamCount = optInt("streamCount", 0),
        messageCount = optInt("messageCount", 0),
        moderationActionCount = optInt("moderationActionCount", 0),
        spamAttemptCount = optInt("spamAttemptCount", 0),
        reconnectEvents = optInt("reconnectEvents", 0),
        uptimeMillis = optLong("uptimeMillis", 0L)
    )
}

private fun JSONObject.toStreamAnalyticsChatter(): StreamAnalyticsChatter {
    return StreamAnalyticsChatter(
        authorChannelId = optString("authorChannelId"),
        authorName = optString("authorName"),
        messageCount = optInt("messageCount", 0),
        moderationActionCount = optInt("moderationActionCount", 0)
    )
}

private fun JSONObject.toStreamAnalyticsCommand(): StreamAnalyticsCommand {
    return StreamAnalyticsCommand(
        commandId = optString("commandId"),
        trigger = optNullableString("trigger"),
        count = optInt("count", 0)
    )
}

private fun JSONObject.toStreamAnalyticsRule(): StreamAnalyticsRule {
    return StreamAnalyticsRule(
        rule = optString("rule"),
        matchCount = optInt("matchCount", 0),
        destructiveActionCount = optInt("destructiveActionCount", 0),
        falsePositiveCount = optInt("falsePositiveCount", 0)
    )
}

private fun JSONObject.toStreamAnalyticsRulePreset(): StreamAnalyticsRulePreset {
    return StreamAnalyticsRulePreset(
        presetId = optString("presetId"),
        presetName = optNullableString("presetName"),
        presetVersion = optNullableString("presetVersion"),
        rule = optString("rule"),
        matchCount = optInt("matchCount", 0),
        destructiveActionCount = optInt("destructiveActionCount", 0),
        falsePositiveCount = optInt("falsePositiveCount", 0)
    )
}

private fun JSONObject.toStreamAnalyticsSpamDay(): StreamAnalyticsSpamDay {
    return StreamAnalyticsSpamDay(
        day = optString("day"),
        count = optInt("count", 0)
    )
}

private fun JSONObject.toStreamAnalyticsUptime(): StreamAnalyticsUptime {
    return StreamAnalyticsUptime(
        sessionId = optString("sessionId"),
        title = optNullableString("title"),
        uptimeMillis = optLong("uptimeMillis", 0L),
        reconnectEvents = optInt("reconnectEvents", 0)
    )
}

private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    val array = this ?: return emptyList()
    return List(array.length()) { index -> mapper(array.getJSONObject(index)) }
}

private fun JSONArray?.toStringList(): List<String> {
    val array = this ?: return emptyList()
    return List(array.length()) { index -> array.optString(index) }
        .filter { value -> value.isNotBlank() }
}

private fun StreamSessionSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("videoId", videoId)
        .put("liveChatId", liveChatId)
        .put("title", title)
        .also { body -> startedAt?.let { body.put("startedAt", it) } }
}

private fun ChatMessageLogSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("youtubeMessageId", youtubeMessageId)
        .put("authorChannelId", authorChannelId)
        .put("authorName", authorName)
        .put("text", text)
        .also { body -> receivedAt?.let { body.put("receivedAt", it) } }
}

private fun ModerationActionLogSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .also { body -> clientActionId?.let { body.put("clientActionId", it) } }
        .put("youtubeMessageId", youtubeMessageId)
        .put("authorChannelId", authorChannelId)
        .put("actionType", actionType)
        .put("reason", reason)
        .put("confidence", confidence)
        .also { body ->
            if (metadata.isNotEmpty()) {
                body.put("metadata", metadata.toJsonObject())
            }
        }
}

private fun ModerationActionReviewRequest.toJson(): JSONObject {
    return JSONObject()
        .put("reviewStatus", reviewStatus)
        .also { body -> reviewNote?.let { body.put("reviewNote", it) } }
}

private fun RuntimeEventSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("type", type)
        .put("message", message)
        .put("metadata", metadata.toJsonObject())
}

private fun FaqEntrySyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("question", question)
        .put("answer", answer)
        .put("keywords", JSONArray(keywords))
        .put("enabled", enabled)
}

private fun FaqReplySuggestionRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put(
            "message",
            JSONObject()
                .put("text", messageText)
                .also { body -> authorName?.takeIf { it.isNotBlank() }?.let { body.put("authorName", it) } }
        )
        .put("minConfidence", minConfidence)
}

private fun DiscordWebhookUpsertRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("enabled", enabled)
        .put("alertModerationActions", alertModerationActions)
        .put("alertRuntimeStatus", alertRuntimeStatus)
        .also { body ->
            webhookUrl?.takeIf { it.isNotBlank() }?.let { body.put("webhookUrl", it) }
        }
}

private fun DiscordAlertRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("eventType", eventType)
        .put("title", title)
        .put("detail", detail)
        .put("severity", severity)
        .put("metadata", metadata.toJsonObject())
}

private fun OverlayConfigUpdateRequest.toJson(): JSONObject {
    return JSONObject()
        .put("enabled", enabled)
        .put("theme", theme)
        .put("showModerationActions", showModerationActions)
        .put("showRuntimeStatus", showRuntimeStatus)
        .put("showViewerStats", showViewerStats)
        .put("showRecentChat", showRecentChat)
        .also { body ->
            activeSessionId?.takeIf { it.isNotBlank() }?.let { body.put("activeSessionId", it) }
        }
}

private fun TeamMemberPermissions.toJson(): JSONObject {
    return JSONObject()
        .put("viewQueue", viewQueue)
        .put("moderate", moderate)
        .put("manageWarnings", manageWarnings)
        .put("viewAnalytics", viewAnalytics)
}

private fun TeamInviteCreateRequest.toJson(): JSONObject {
    return JSONObject()
        .put("displayName", displayName)
        .put("role", role)
        .put("permissions", permissions.toJson())
}

private fun TeamInviteRedeemRequest.toJson(): JSONObject {
    return JSONObject()
        .put("inviteCode", inviteCode)
        .also { body ->
            displayName?.takeIf { it.isNotBlank() }?.let { body.put("displayName", it) }
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

private fun CommandSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("name", name)
        .put("response", response)
        .put("aliases", JSONArray(aliases))
        .put("cooldownSeconds", cooldownSeconds)
        .put("accessLevel", accessLevel)
        .put("enabled", enabled)
}

private fun CommandManualSendRequest.toJson(): JSONObject {
    return JSONObject()
        .put("liveChatId", liveChatId)
        .also { body ->
            streamTitle?.takeIf { it.isNotBlank() }?.let { body.put("streamTitle", it) }
            streamStartedAt?.takeIf { it.isNotBlank() }?.let { body.put("streamStartedAt", it) }
        }
}

private fun UserWarningRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("authorChannelId", authorChannelId)
        .put("displayName", displayName)
        .put("reason", reason)
        .also { body ->
            profileImageUrl?.takeIf { it.isNotBlank() }?.let { body.put("profileImageUrl", it) }
            liveChatId?.takeIf { it.isNotBlank() }?.let { body.put("liveChatId", it) }
            warningText?.takeIf { it.isNotBlank() }?.let { body.put("warningText", it) }
        }
}

private fun UserProfileNotesRequest.toJson(): JSONObject {
    return JSONObject()
        .put("notes", notes?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
}

private fun UserHideRequest.toJson(): JSONObject {
    return JSONObject()
        .put("liveChatId", liveChatId)
        .put("reason", reason)
}

private fun UserTimeoutRequest.toJson(): JSONObject {
    return JSONObject()
        .put("liveChatId", liveChatId)
        .put("durationSeconds", durationSeconds)
        .put("reason", reason)
}

private fun YouTubeUserUnbanRequest.toJson(): JSONObject {
    return JSONObject()
        .put("liveChatBanId", liveChatBanId)
        .put("reason", reason)
}

private fun UserUnbanRequest.toJson(): JSONObject {
    return JSONObject()
        .put("liveChatBanId", liveChatBanId)
        .put("reason", reason)
        .also { body ->
            liveChatId?.takeIf { it.isNotBlank() }?.let { body.put("liveChatId", it) }
        }
}

private fun UserWhitelistRequest.toJson(): JSONObject {
    return JSONObject().also { body ->
        durationSeconds?.let { body.put("durationSeconds", it) }
    }
}

private fun JSONObject.toUserWarningResult(): UserWarningResult {
    val user = getJSONObject("user")
    val strike = getJSONObject("strike")
    return UserWarningResult(
        user = user.toUserProfileRecord(),
        strike = strike.toUserStrikeRecord(),
        messageId = optNullableString("messageId"),
        warnedAt = optString("warnedAt")
    )
}

private fun JSONObject.toUserProfileRecord(): UserProfileRecord {
    val strikes = optJSONArray("recentStrikes") ?: JSONArray()
    val moderationActions = optJSONArray("recentModerationActions") ?: JSONArray()
    return UserProfileRecord(
        id = optString("id"),
        profileId = optString("profileId"),
        authorChannelId = optString("authorChannelId"),
        displayName = optString("displayName"),
        profileImageUrl = optNullableString("profileImageUrl"),
        firstSeenAt = optString("firstSeenAt"),
        lastSeenAt = optString("lastSeenAt"),
        messageCount = optInt("messageCount", 0),
        notes = optNullableString("notes"),
        strikeCount = optInt("strikeCount", 0),
        recentStrikes = List(strikes.length()) { index ->
            strikes.getJSONObject(index).toUserStrikeRecord()
        },
        recentModerationActions = List(moderationActions.length()) { index ->
            moderationActions.getJSONObject(index).toUserModerationActionRecord()
        }
    )
}

private fun JSONObject.toUserStrikeRecord(): UserStrikeRecord {
    return UserStrikeRecord(
        id = optString("id"),
        userProfileId = optString("userProfileId"),
        reason = optString("reason"),
        createdAt = optString("createdAt")
    )
}

private fun JSONObject.toUserModerationActionRecord(): UserModerationActionRecord {
    return UserModerationActionRecord(
        id = optString("id"),
        userProfileId = optString("userProfileId"),
        actionType = optString("actionType"),
        liveChatId = optNullableString("liveChatId"),
        liveChatBanId = optNullableString("liveChatBanId"),
        reason = optString("reason"),
        durationSeconds = if (has("durationSeconds") && !isNull("durationSeconds")) optInt("durationSeconds") else null,
        createdAt = optString("createdAt"),
        expiresAt = optNullableString("expiresAt")
    )
}

private fun JSONObject.toUserWhitelistRecord(): UserWhitelistRecord {
    return UserWhitelistRecord(
        id = optString("id"),
        profileId = optString("profileId"),
        authorChannelId = optString("authorChannelId"),
        displayName = optNullableString("displayName"),
        temporaryUntil = optNullableString("temporaryUntil"),
        createdAt = optString("createdAt")
    )
}

private fun TimerSyncRequest.toJson(): JSONObject {
    return JSONObject()
        .put("profileId", profileId)
        .put("name", name)
        .put("message", message)
        .put("intervalMinutes", intervalMinutes)
        .put("minChatMessages", minChatMessages)
        .put("enabled", enabled)
        .also { body ->
            quietStartMinutes?.let { body.put("quietStartMinutes", it) }
            quietEndMinutes?.let { body.put("quietEndMinutes", it) }
        }
}

private fun youtubeDiscoveryBody(channelId: String, includeScheduled: Boolean): JSONObject {
    return JSONObject()
        .put("channelId", channelId)
        .put("includeScheduled", includeScheduled)
}

private fun List<SettingsBackupCommand>.toCommandJsonArray(): JSONArray {
    return JSONArray().also { target ->
        forEach { command ->
            target.put(
                JSONObject()
                    .put("name", command.name)
                    .put("response", command.response)
                    .put("aliases", JSONArray(command.aliases))
                    .put("cooldownSeconds", command.cooldownSeconds)
                    .put("accessLevel", command.accessLevel)
                    .put("enabled", command.enabled)
                    .also { body -> command.id?.let { body.put("id", it) } }
            )
        }
    }
}

private fun List<SettingsBackupTimer>.toTimerJsonArray(): JSONArray {
    return JSONArray().also { target ->
        forEach { timer ->
            target.put(
                JSONObject()
                    .put("name", timer.name)
                    .put("message", timer.message)
                    .put("intervalMinutes", timer.intervalMinutes)
                    .put("minChatMessages", timer.minChatMessages)
                    .put("enabled", timer.enabled)
                    .also { body ->
                        timer.id?.let { body.put("id", it) }
                        timer.quietStartMinutes?.let { body.put("quietStartMinutes", it) }
                        timer.quietEndMinutes?.let { body.put("quietEndMinutes", it) }
                    }
            )
        }
    }
}

private fun JSONObject.toSettingsBackupCommand(): SettingsBackupCommand {
    val aliases = optJSONArray("aliases") ?: JSONArray()
    return SettingsBackupCommand(
        id = optNullableString("id"),
        name = optString("name"),
        response = optString("response"),
        aliases = List(aliases.length()) { index -> aliases.getString(index) },
        cooldownSeconds = optInt("cooldownSeconds", 30),
        accessLevel = optString("accessLevel", "everyone"),
        enabled = optBoolean("enabled", true)
    )
}

private fun JSONObject.toSettingsBackupTimer(): SettingsBackupTimer {
    return SettingsBackupTimer(
        id = optNullableString("id"),
        name = optString("name"),
        message = optString("message"),
        intervalMinutes = optInt("intervalMinutes", 15),
        minChatMessages = optInt("minChatMessages", 0),
        quietStartMinutes = optNullableInt("quietStartMinutes"),
        quietEndMinutes = optNullableInt("quietEndMinutes"),
        enabled = optBoolean("enabled", true)
    )
}

private fun JSONObject.optNullableInt(name: String): Int? {
    return if (has(name) && !isNull(name)) optInt(name) else null
}

private fun JSONObject.optNullableString(name: String): String? {
    return if (has(name) && !isNull(name)) optString(name) else null
}

private fun JSONObject.optStringList(name: String): List<String> {
    val values = optJSONArray(name) ?: return emptyList()
    return List(values.length()) { index -> values.getString(index) }
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

private fun JSONObject.toMap(): Map<String, Any?> {
    return keys().asSequence().associateWith { key -> normalizeJsonValue(get(key)) }
}

private fun Map<String, Any?>.toJsonObject(): JSONObject {
    return JSONObject().also { target ->
        forEach { (key, value) -> target.put(key, normalizeJsonValue(value)) }
    }
}

private fun countNestedArrayItems(items: JSONArray, key: String): Int {
    var count = 0
    for (index in 0 until items.length()) {
        count += items.optJSONObject(index)?.optJSONArray(key)?.length() ?: 0
    }
    return count
}

private fun normalizeJsonValue(value: Any?): Any? {
    return when (value) {
        JSONObject.NULL -> null
        is Map<*, *> -> JSONObject().also { target ->
            value.forEach { (key, nestedValue) ->
                if (key is String) target.put(key, normalizeJsonValue(nestedValue))
            }
        }
        is Iterable<*> -> JSONArray().also { target ->
            value.forEach { target.put(normalizeJsonValue(it)) }
        }
        else -> value
    }
}

private fun LinkPolicy.toApiValue(): String {
    return when (this) {
        LinkPolicy.Allow -> "allow"
        LinkPolicy.Flag -> "flag"
        LinkPolicy.Delete -> "delete"
    }
}

private fun String.toLinkPolicy(): LinkPolicy {
    return when (this) {
        "allow" -> LinkPolicy.Allow
        "delete" -> LinkPolicy.Delete
        else -> LinkPolicy.Flag
    }
}

private fun String.toActionType(): ActionType {
    return when (this) {
        "allow" -> ActionType.Allow
        "flagForReview" -> ActionType.FlagForReview
        "deleteMessage" -> ActionType.DeleteMessage
        "timeoutUser" -> ActionType.TimeoutUser
        "hideUser" -> ActionType.HideUser
        "sendAutoReply" -> ActionType.SendAutoReply
        else -> ActionType.FlagForReview
    }
}
