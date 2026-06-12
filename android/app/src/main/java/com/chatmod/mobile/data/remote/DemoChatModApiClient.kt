package com.chatmod.mobile.data.remote

import com.chatmod.mobile.domain.rules.ChatMessage
import com.chatmod.mobile.domain.rules.ActionType
import com.chatmod.mobile.domain.rules.LinkPolicy
import com.chatmod.mobile.domain.rules.ModerationDecision
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.RuleEngine
import java.time.Instant
import java.util.UUID

private const val StarterLocalHistoryLimitValue = 120
private const val ProLocalHistoryLimitValue = 1000
private const val CreatorLocalHistoryLimitValue = 2000
private const val CreatorAiSuggestionDailyLimitValue = 300

class DemoChatModApiClient(
    private val ruleEngine: RuleEngine = RuleEngine()
) : ChatModApiClient {
    private var aiSuggestionUsageCount = 0
    private val supportEvents = mutableListOf<SupportEventRecord>()
    private val analyticsEvents = mutableListOf<AnalyticsEventRecord>()
    private val betaFeedback = mutableListOf<BetaFeedbackRecord>()
    private val rulePresets = mutableMapOf<String, RulePresetRecord>()
    private val streamSessions = mutableMapOf<String, StreamSessionRecord>()
    private val streamMessages = mutableListOf<ChatMessageLogRecord>()
    private val moderationActions = mutableListOf<ModerationActionLogRecord>()
    private val runtimeEvents = mutableListOf<RuntimeEventRecord>()
    private val faqEntries = mutableMapOf<String, FaqEntryRecord>()
    private val userProfiles = mutableMapOf<String, UserProfileRecord>()
    private val userProfileStrikes = mutableMapOf<String, MutableList<UserStrikeRecord>>()
    private val userProfileModerationActions = mutableMapOf<String, MutableList<UserModerationActionRecord>>()
    private val whitelistEntries = mutableMapOf<String, UserWhitelistRecord>()
    private val overlayConfigs = mutableMapOf<String, OverlayConfig>()
    private val teamMembers = mutableMapOf<String, TeamMemberRecord>()
    private val teamInviteCodes = mutableMapOf<String, String>()
    private val channelProfiles = mutableMapOf(
        "local-default-profile" to ChannelProfileRecord(
            id = "local-default-profile",
            channelId = "demo-channel",
            name = "Primary channel",
            config = emptyMap(),
            createdAt = Instant.now().toString(),
            updatedAt = Instant.now().toString()
        )
    )
    private val settingsBackups = mutableMapOf<String, SettingsBackupRequest>()
    private val discordConfigs = mutableMapOf<String, DiscordWebhookConfig>()
    private val cloudBackups = mutableListOf(
        CloudBackup(
            id = "demo-profile-backup",
            profileName = "Demo profile",
            channelId = "demo-channel",
            version = 1,
            clientVersion = "0.1.0",
            createdAt = Instant.now().toString()
        )
    )

    override suspend fun appCompatibility(platform: String, versionName: String, versionCode: Int): AppCompatibility {
        return AppCompatibility(
            platform = platform,
            currentVersionName = versionName,
            currentVersionCode = versionCode,
            minimumSupportedVersionName = "0.1.0",
            minimumSupportedVersionCode = 1,
            latestVersionName = "0.1.0",
            latestVersionCode = 1,
            status = "compatible",
            updateRequired = false,
            updateRecommended = false,
            message = "ChatMod Mobile is compatible.",
            downloadUrl = null
        )
    }

    override suspend fun createDeviceSession(request: DeviceSessionRequest): DeviceSession {
        return DeviceSession(
            accessToken = "demo-access-token",
            tokenType = "Bearer",
            expiresInSeconds = 3600
        )
    }

    override suspend fun currentEntitlement(accessToken: String): EntitlementSnapshot {
        return EntitlementSnapshot(
            plan = "starter",
            status = "trialing",
            source = "demo",
            productId = null,
            currentPeriodEndsAt = null,
            features = mapOf(
                "customBotName" to true,
                "channelProfiles" to 1,
                "commandProfiles" to 3,
                "timedMessages" to 5,
                "localHistoryLimit" to StarterLocalHistoryLimitValue,
                "cloudBackups" to true,
                "teamSeats" to 1,
                "presetBundles" to false,
                "discordAlerts" to true,
                "obsOverlay" to false,
                "aiSuggestions" to false,
                "aiSuggestionDailyLimit" to 0
            )
        )
    }

    override suspend fun validateGooglePlayPurchase(
        accessToken: String,
        request: GooglePlayPurchaseValidationRequest
    ): GooglePlayPurchaseValidationResult {
        val creatorPlan = request.productId.contains("creator")
        return GooglePlayPurchaseValidationResult(
            entitlement = EntitlementSnapshot(
                plan = if (creatorPlan) "creator" else "pro",
                status = "active",
                source = "demo-google-play",
                productId = request.productId,
                currentPeriodEndsAt = Instant.now().plusSeconds(30L * 24L * 60L * 60L).toString(),
                features = mapOf(
                    "customBotName" to true,
                    "channelProfiles" to if (creatorPlan) 5 else 1,
                    "commandProfiles" to null,
                    "timedMessages" to null,
                    "localHistoryLimit" to if (creatorPlan) CreatorLocalHistoryLimitValue else ProLocalHistoryLimitValue,
                    "cloudBackups" to true,
                    "teamSeats" to if (creatorPlan) 5 else 2,
                    "emergencyMode" to true,
                    "advancedFilters" to true,
                    "presetBundles" to true,
                    "discordAlerts" to true,
                    "obsOverlay" to true,
                    "aiSuggestions" to creatorPlan,
                    "aiSuggestionDailyLimit" to if (creatorPlan) CreatorAiSuggestionDailyLimitValue else 0
                )
            ),
            validationStatus = "active"
        )
    }

    override suspend fun listChannelProfiles(accessToken: String): ChannelProfileList {
        return ChannelProfileList(
            profiles = channelProfiles.values.sortedBy { it.createdAt }
        )
    }

    override suspend fun createChannelProfile(
        accessToken: String,
        request: ChannelProfileCreateRequest
    ): ChannelProfileRecord {
        val now = Instant.now().toString()
        val profile = ChannelProfileRecord(
            id = "profile-${UUID.randomUUID()}",
            channelId = request.channelId,
            name = request.name,
            config = request.config,
            createdAt = now,
            updatedAt = now
        )
        channelProfiles[profile.id] = profile
        return profile
    }

    override suspend fun backupModerationProfile(
        accessToken: String,
        request: ProfileBackupRequest
    ): ProfileBackup {
        return ProfileBackup(
            id = "demo-profile-backup",
            profileName = request.profileName,
            version = 1,
            createdAt = Instant.now().toString()
        )
    }

    override suspend fun createSettingsBackup(
        accessToken: String,
        request: SettingsBackupRequest
    ): SettingsBackupResult {
        val id = "demo-settings-backup-${settingsBackups.size + 1}"
        settingsBackups[id] = request
        val createdAt = Instant.now().toString()
        cloudBackups.add(
            0,
            CloudBackup(
                id = id,
                profileName = request.profileName,
                channelId = request.channelId,
                version = settingsBackups.size,
                clientVersion = request.clientVersion,
                createdAt = createdAt
            )
        )

        return SettingsBackupResult(
            id = id,
            profileName = request.profileName,
            version = settingsBackups.size,
            commandCount = request.commands.size,
            timerCount = request.timers.size,
            createdAt = createdAt
        )
    }

    override suspend fun restoreSettingsBackup(
        accessToken: String,
        backupId: String,
        targetProfileId: String?
    ): SettingsRestoreResult {
        val backup = settingsBackups[backupId]
        val profileId = targetProfileId ?: backup?.profileId ?: "local-default-profile"
        return SettingsRestoreResult(
            restoredAt = Instant.now().toString(),
            backupId = backupId,
            profileId = profileId,
            commands = backup?.commands.orEmpty(),
            timers = backup?.timers.orEmpty()
        )
    }

    override suspend fun listBackups(accessToken: String): BackupList {
        return BackupList(backups = cloudBackups.toList())
    }

    override suspend fun deleteBackup(accessToken: String, backupId: String) {
        settingsBackups.remove(backupId)
        cloudBackups.removeAll { it.id == backupId }
    }

    override suspend fun exportAccount(accessToken: String): AccountExportSummary {
        return AccountExportSummary(
            exportedAt = Instant.now().toString(),
            profileCount = 1,
            backupCount = 1,
            linkedAccountCount = 1,
            linkedAccounts = listOf(
                LinkedAccountSummary(
                    provider = "youtube",
                    providerAccountId = "demo-device",
                    channelId = "demo-bot-channel",
                    channelTitle = "ChatMod Demo Bot",
                    tokenExpiresAt = Instant.now().plusSeconds(3_600).toString()
                )
            ),
            supportEventCount = 0,
            auditLogCount = 0
        )
    }

    override suspend fun disconnectYouTube(accessToken: String): YouTubeDisconnectResult {
        return YouTubeDisconnectResult(
            disconnected = false,
            removedAccounts = 0,
            revocationAttempted = false,
            revokedTokens = 0,
            revocationFailures = 0
        )
    }

    override suspend fun discordWebhookConfig(accessToken: String, profileId: String): DiscordWebhookConfig {
        return discordConfigs[profileId] ?: DiscordWebhookConfig(
            profileId = profileId,
            configured = false,
            enabled = false,
            alertModerationActions = true,
            alertRuntimeStatus = false
        )
    }

    override suspend fun upsertDiscordWebhook(
        accessToken: String,
        request: DiscordWebhookUpsertRequest
    ): DiscordWebhookConfig {
        val now = Instant.now().toString()
        val existing = discordConfigs[request.profileId]
        val configured = existing?.configured == true || !request.webhookUrl.isNullOrBlank()
        val config = DiscordWebhookConfig(
            profileId = request.profileId,
            configured = configured,
            enabled = request.enabled && configured,
            alertModerationActions = request.alertModerationActions,
            alertRuntimeStatus = request.alertRuntimeStatus,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        discordConfigs[request.profileId] = config
        return config
    }

    override suspend fun deleteDiscordWebhook(accessToken: String, profileId: String) {
        discordConfigs.remove(profileId)
    }

    override suspend fun testDiscordWebhook(accessToken: String, profileId: String): DiscordAlertResult {
        val config = discordWebhookConfig(accessToken, profileId)
        return DiscordAlertResult(
            sent = config.configured && config.enabled,
            skippedReason = if (config.configured && config.enabled) null else "not_configured",
            sentAt = if (config.configured && config.enabled) Instant.now().toString() else null,
            profileId = profileId
        )
    }

    override suspend fun sendDiscordAlert(accessToken: String, request: DiscordAlertRequest): DiscordAlertResult {
        val config = discordWebhookConfig(accessToken, request.profileId)
        val skippedReason = when {
            !config.configured -> "not_configured"
            !config.enabled -> "disabled"
            request.eventType == "moderation_action" && !config.alertModerationActions -> "moderation_alerts_disabled"
            request.eventType == "runtime_status" && !config.alertRuntimeStatus -> "runtime_alerts_disabled"
            else -> null
        }
        return DiscordAlertResult(
            sent = skippedReason == null,
            skippedReason = skippedReason,
            sentAt = if (skippedReason == null) Instant.now().toString() else null,
            profileId = request.profileId
        )
    }

    override suspend fun overlayConfig(accessToken: String, profileId: String): OverlayConfig {
        return overlayConfigs[profileId] ?: OverlayConfig(
            profileId = profileId,
            configured = false,
            enabled = false,
            theme = "control_room",
            activeSessionId = null,
            showModerationActions = true,
            showRuntimeStatus = true,
            showViewerStats = true,
            showRecentChat = false,
            allowed = true,
            requiredPlan = null
        )
    }

    override suspend fun upsertOverlayConfig(
        accessToken: String,
        request: OverlayConfigUpdateRequest
    ): OverlayConfigMutationResult {
        val now = Instant.now().toString()
        val existing = overlayConfigs[request.profileId]
        val publicUrl = existing?.publicUrl ?: demoOverlayUrl(request.profileId)
        val config = OverlayConfig(
            profileId = request.profileId,
            configured = true,
            enabled = request.enabled,
            theme = request.theme,
            activeSessionId = request.activeSessionId,
            showModerationActions = request.showModerationActions,
            showRuntimeStatus = request.showRuntimeStatus,
            showViewerStats = request.showViewerStats,
            showRecentChat = request.showRecentChat,
            tokenPreview = existing?.tokenPreview ?: "cmo_demo...${request.profileId.takeLast(4).ifBlank { "demo" }}",
            publicUrl = publicUrl,
            publicPath = "/overlays/public/demo-${request.profileId}",
            tokenRotated = existing == null,
            allowed = true,
            requiredPlan = null,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        overlayConfigs[request.profileId] = config
        return config
    }

    override suspend fun rotateOverlayToken(accessToken: String, profileId: String): OverlayConfigMutationResult {
        val existing = overlayConfig(accessToken, profileId)
        val now = Instant.now().toString()
        val tokenSuffix = UUID.randomUUID().toString().take(8)
        val config = existing.copy(
            configured = true,
            tokenPreview = "cmo_demo...$tokenSuffix",
            publicUrl = "https://demo.chatmod.local/overlays/public/cmo_demo_$tokenSuffix",
            publicPath = "/overlays/public/cmo_demo_$tokenSuffix",
            tokenRotated = true,
            createdAt = existing.createdAt ?: now,
            updatedAt = now
        )
        overlayConfigs[profileId] = config
        return config
    }

    override suspend fun listTeamMembers(accessToken: String, profileId: String): TeamMemberList {
        val members = teamMembers.values
            .filter { member -> member.profileId == profileId }
            .sortedByDescending { member -> member.updatedAt }
        return TeamMemberList(
            profileId = profileId,
            teamSeats = 2,
            extraSeats = 1,
            members = members
        )
    }

    override suspend fun createTeamInvite(
        accessToken: String,
        profileId: String,
        request: TeamInviteCreateRequest
    ): TeamInviteResult {
        val openSeats = teamMembers.values.count { member -> member.profileId == profileId && member.status != "revoked" }
        if (openSeats >= 1) {
            throw ChatModHttpException(403, "Team moderator seat limit reached for demo Pro plan (1).")
        }

        val now = Instant.now().toString()
        val id = "team-${UUID.randomUUID()}"
        val inviteCode = "cmt_demo_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val member = TeamMemberRecord(
            id = id,
            profileId = profileId,
            displayName = request.displayName,
            role = request.role,
            status = "invited",
            inviteCodePreview = "${inviteCode.take(8)}...${inviteCode.takeLast(4)}",
            memberDeviceId = null,
            permissions = request.permissions,
            acceptedAt = null,
            revokedAt = null,
            createdAt = now,
            updatedAt = now
        )
        teamMembers[id] = member
        teamInviteCodes[inviteCode] = id
        return TeamInviteResult(member = member, inviteCode = inviteCode)
    }

    override suspend fun revokeTeamMember(accessToken: String, profileId: String, memberId: String): TeamMemberRecord {
        val existing = teamMembers[memberId] ?: throw ChatModHttpException(404, "Team member not found.")
        val now = Instant.now().toString()
        val revoked = existing.copy(
            status = "revoked",
            revokedAt = now,
            updatedAt = now
        )
        teamMembers[memberId] = revoked
        return revoked
    }

    override suspend fun redeemTeamInvite(accessToken: String, request: TeamInviteRedeemRequest): TeamMembershipRecord {
        val id = teamInviteCodes[request.inviteCode] ?: throw ChatModHttpException(404, "Team invite not found.")
        val existing = teamMembers[id] ?: throw ChatModHttpException(404, "Team invite not found.")
        if (existing.status == "revoked") {
            throw ChatModHttpException(404, "Team invite not found.")
        }
        val now = Instant.now().toString()
        val active = existing.copy(
            displayName = request.displayName ?: existing.displayName,
            status = "active",
            memberDeviceId = "demo-team-device",
            acceptedAt = existing.acceptedAt ?: now,
            updatedAt = now
        )
        teamMembers[id] = active
        return active.toDemoMembership()
    }

    override suspend fun listTeamMemberships(accessToken: String): TeamMembershipList {
        return TeamMembershipList(
            memberships = teamMembers.values
                .filter { member -> member.status == "active" }
                .sortedByDescending { member -> member.updatedAt }
                .map { member -> member.toDemoMembership() }
        )
    }

    override suspend fun youtubeConnectUrl(accessToken: String): YouTubeConnectUrl {
        return YouTubeConnectUrl(
            url = null,
            configured = false,
            requiredScopes = listOf(
                "https://www.googleapis.com/auth/youtube.readonly",
                "https://www.googleapis.com/auth/youtube.force-ssl"
            ),
            missingEnv = listOf(
                "GOOGLE_OAUTH_CLIENT_ID",
                "GOOGLE_OAUTH_CLIENT_SECRET",
                "GOOGLE_OAUTH_REDIRECT_URI"
            ),
            note = "Demo mode uses a local sample account. Configure Google OAuth env vars on the backend to open real sign-in."
        )
    }

    private fun demoOverlayUrl(profileId: String): String {
        return "https://demo.chatmod.local/overlays/public/cmo_demo_${profileId.takeLast(8)}"
    }

    override suspend fun youtubeAccountStatus(accessToken: String): YouTubeAccountStatus {
        return YouTubeAccountStatus(
            configured = false,
            source = "demo",
            account = YouTubeLinkedAccountStatus(
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

    override suspend fun listYouTubeBroadcasts(
        accessToken: String,
        channelId: String,
        includeScheduled: Boolean
    ): YouTubeBroadcastList {
        return YouTubeBroadcastList(
            broadcasts = demoBroadcasts(channelId, includeScheduled),
            source = "demo"
        )
    }

    override suspend fun discoverYouTubeLiveChat(
        accessToken: String,
        channelId: String,
        includeScheduled: Boolean
    ): YouTubeLiveChatDiscovery {
        val broadcasts = demoBroadcasts(channelId, includeScheduled)
        val active = broadcasts.firstOrNull { it.status == "active" && it.liveChatId != null }
        return YouTubeLiveChatDiscovery(
            activeChat = active?.let {
                YouTubeActiveChat(
                    liveChatId = it.liveChatId.orEmpty(),
                    videoId = it.videoId
                )
            },
            broadcasts = broadcasts,
            activeBroadcastCount = broadcasts.count { it.status == "active" && it.liveChatId != null },
            needsSelection = false,
            status = if (active == null) "no_active_chat" else "ready",
            source = "demo"
        )
    }

    override suspend fun sendYouTubeTestMessage(
        accessToken: String,
        request: YouTubeTestMessageRequest
    ): YouTubeTestMessageResult {
        return YouTubeTestMessageResult(messageId = "demo-test-message-${request.liveChatId.length}")
    }

    override suspend fun listYouTubeLiveChatMessages(
        accessToken: String,
        request: YouTubeLiveChatMessagesRequest
    ): YouTubeLiveChatMessagePage {
        return YouTubeLiveChatMessagePage(
            messages = if (request.pageToken == null) {
                listOf(
                    YouTubeLiveChatMessageRecord(
                        id = "demo-message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "buy cheap views at www.example.com",
                        publishedAt = Instant.now().toString(),
                        messageType = "textMessageEvent"
                    )
                )
            } else {
                emptyList()
            },
            nextPageToken = request.pageToken ?: "next-demo-page",
            pollingIntervalMillis = 5000,
            source = "demo"
        )
    }

    override suspend fun sendYouTubeLiveChatMessage(
        accessToken: String,
        request: YouTubeMessageSendRequest
    ): YouTubeMessageSendResult {
        return YouTubeMessageSendResult(
            messageId = "demo-sent-message",
            liveChatId = request.liveChatId,
            sentAt = Instant.now().toString()
        )
    }

    override suspend fun deleteYouTubeLiveChatMessage(
        accessToken: String,
        request: YouTubeMessageDeleteRequest
    ): YouTubeMessageDeleteResult {
        return YouTubeMessageDeleteResult(
            messageId = request.messageId,
            actionType = "deleteMessage",
            reason = request.reason,
            deletedAt = Instant.now().toString()
        )
    }

    override suspend fun hideYouTubeLiveChatUser(
        accessToken: String,
        request: YouTubeUserHideRequest
    ): YouTubeUserHideResult {
        return YouTubeUserHideResult(
            liveChatId = request.liveChatId,
            authorChannelId = request.authorChannelId,
            liveChatBanId = "demo-live-chat-ban-${request.authorChannelId}",
            actionType = if (request.durationSeconds == null) "hideUser" else "timeoutUser",
            durationSeconds = request.durationSeconds,
            reason = request.reason,
            actedAt = Instant.now().toString()
        )
    }

    override suspend fun unbanYouTubeLiveChatUser(
        accessToken: String,
        request: YouTubeUserUnbanRequest
    ): YouTubeUserUnbanResult {
        return YouTubeUserUnbanResult(
            liveChatBanId = request.liveChatBanId,
            actionType = "unbanUser",
            reason = request.reason,
            actedAt = Instant.now().toString()
        )
    }

    override suspend fun deleteCurrentAccount(accessToken: String): AccountDeletionResult {
        return AccountDeletionResult(
            deleted = false,
            userId = null,
            deviceIds = listOf("demo-device"),
            supportEventsDeleted = 0,
            auditLogsDeleted = 0,
            apiErrorsDeleted = 0
        )
    }

    override suspend fun listSupportEvents(accessToken: String): SupportEventList {
        return SupportEventList(events = supportEvents.toList())
    }

    override suspend fun listApiErrors(accessToken: String): ApiErrorList {
        return ApiErrorList(errors = emptyList())
    }

    override suspend fun recordSupportEvent(
        accessToken: String,
        request: SupportEventRequest
    ): SupportEventRecord {
        val event = SupportEventRecord(
            id = "demo-support-${supportEvents.size + 1}",
            severity = request.severity,
            message = request.message,
            details = request.details,
            createdAt = Instant.now().toString()
        )
        supportEvents.add(0, event)
        return event
    }

    override suspend fun recordAnalyticsEvent(
        accessToken: String,
        request: AnalyticsEventRequest
    ): AnalyticsEventRecord {
        val now = Instant.now().toString()
        val event = AnalyticsEventRecord(
            id = "demo-analytics-${analyticsEvents.size + 1}",
            name = request.name,
            occurredAt = request.occurredAt ?: now,
            appVersion = request.appVersion,
            platform = request.platform,
            properties = request.properties,
            createdAt = now
        )
        analyticsEvents.add(0, event)
        return event
    }

    override suspend fun listBetaFeedback(accessToken: String): BetaFeedbackList {
        return BetaFeedbackList(feedback = betaFeedback.toList())
    }

    override suspend fun submitBetaFeedback(
        accessToken: String,
        request: BetaFeedbackRequest
    ): BetaFeedbackRecord {
        val now = Instant.now().toString()
        val record = BetaFeedbackRecord(
            id = "demo-feedback-${betaFeedback.size + 1}",
            category = request.category,
            message = request.message,
            occurredAt = request.occurredAt ?: now,
            appVersion = request.appVersion,
            platform = request.platform,
            context = request.context,
            createdAt = now
        )
        betaFeedback.add(0, record)
        return record
    }

    override suspend fun evaluateMessage(
        accessToken: String,
        message: ChatMessage,
        profile: ModerationProfile
    ): ModerationDecision {
        return ruleEngine.evaluate(message, profile)
    }

    override suspend fun evaluateModerationSuggestion(
        accessToken: String,
        message: ChatMessage,
        profile: ModerationProfile,
        recentMessages: List<ChatMessage>,
        confidenceThreshold: Double
    ): ModerationSuggestionResult {
        if (aiSuggestionUsageCount >= CreatorAiSuggestionDailyLimitValue) {
            throw ChatModHttpException(
                statusCode = 429,
                message = "Daily AI moderation suggestion limit reached for the Creator plan.",
                errorCode = "AI_SUGGESTION_LIMIT_REACHED"
            )
        }
        aiSuggestionUsageCount += 1
        val decision = ruleEngine.evaluate(message, profile)
        val actions = decision.actions.filterNot { action -> action.type == ActionType.SendAutoReply }
        val strongest = actions.maxByOrNull { action -> action.confidence }
        val repeatedQuestion = recentMessages.count { recent -> recent.text.normalizedQuestion() == message.text.normalizedQuestion() } >= 2 &&
            message.text.normalizedQuestion() != null
        val confidence = maxOf(
            strongest?.confidence ?: 0.0,
            if (repeatedQuestion) 0.72 else 0.0
        )
        val suggestedAction = if (confidence >= confidenceThreshold) {
            strongest?.type?.apiValue ?: if (repeatedQuestion) "flagForReview" else "allow"
        } else {
            "allow"
        }
        val reasons = buildList {
            strongest?.let { action ->
                add(
                    ModerationSuggestionReason(
                        code = action.reason,
                        label = action.reason.suggestionLabel(),
                        detail = "${action.reason.suggestionLabel()} produced a ${action.type.apiValue} recommendation.",
                        confidence = action.confidence
                    )
                )
            }
            if (repeatedQuestion) {
                add(
                    ModerationSuggestionReason(
                        code = "repeated_question",
                        label = "Repeated question",
                        detail = "This question appeared repeatedly in the recent chat window.",
                        confidence = 0.72
                    )
                )
            }
        }.sortedByDescending { reason -> reason.confidence }

        return ModerationSuggestionResult(
            provider = "local-heuristic",
            manualApprovalRequired = true,
            suggestedAction = suggestedAction,
            classification = if (suggestedAction == "allow") {
                listOf("safe")
            } else {
                reasons.map { reason ->
                    when {
                        reason.code == "repeated_question" -> "repeated_question"
                        reason.code.contains("term") || reason.code.contains("domain") || reason.code.contains("policy") -> "policy"
                        else -> "spam"
                    }
                }.distinct()
            },
            confidence = confidence,
            confidenceThreshold = confidenceThreshold,
            reasons = reasons,
            explanation = if (suggestedAction == "allow") {
                "No confident suggestion. Keep the message unless stream context says otherwise."
            } else {
                "${suggestedAction.suggestionActionLabel()} is suggested for manual review."
            },
            usage = ModerationSuggestionUsage(
                used = aiSuggestionUsageCount,
                limit = CreatorAiSuggestionDailyLimitValue,
                remaining = (CreatorAiSuggestionDailyLimitValue - aiSuggestionUsageCount).coerceAtLeast(0),
                resetAt = Instant.now().plusSeconds(24L * 60L * 60L).toString()
            )
        )
    }

    override suspend fun listRulePresetTemplates(accessToken: String): RulePresetTemplateList {
        return RulePresetTemplateList(rulePresetTemplates = DefaultRulePresetTemplates)
    }

    override suspend fun listRulePresets(accessToken: String, profileId: String?): RulePresetList {
        return RulePresetList(
            rulePresets = rulePresets.values
                .filter { preset -> profileId == null || preset.profileId == profileId }
                .sortedBy { preset -> preset.createdAt }
        )
    }

    override suspend fun exportRulePresets(accessToken: String, profileId: String): RulePresetExportBundle {
        return RulePresetExportBundle(
            formatVersion = 1,
            exportedAt = Instant.now().toString(),
            profileId = profileId,
            rulePresets = rulePresets.values
                .filter { preset -> preset.profileId == profileId }
                .sortedBy { preset -> preset.createdAt }
        )
    }

    override suspend fun importRulePresets(
        accessToken: String,
        request: RulePresetImportRequest
    ): RulePresetImportResult {
        val now = Instant.now().toString()
        val imported = request.bundle.rulePresets.map { preset ->
            if (preset.isDefault) {
                rulePresets.keys.toList().forEach { key ->
                    val existing = rulePresets[key] ?: return@forEach
                    if (existing.profileId == request.profileId) {
                        rulePresets[key] = existing.copy(isDefault = false, updatedAt = now)
                    }
                }
            }

            RulePresetRecord(
                id = "imported-${UUID.randomUUID()}",
                profileId = request.profileId,
                name = preset.name,
                config = preset.config,
                isDefault = preset.isDefault,
                createdAt = now,
                updatedAt = now
            ).also { importedPreset ->
                rulePresets[importedPreset.id] = importedPreset
            }
        }

        return RulePresetImportResult(
            importedAt = now,
            profileId = request.profileId,
            importedCount = imported.size,
            rulePresets = imported
        )
    }

    override suspend fun saveRulePreset(
        accessToken: String,
        request: RulePresetSyncRequest
    ): RulePresetRecord {
        val existing = rulePresets[request.id]
        val now = Instant.now().toString()
        if (request.isDefault) {
            rulePresets.keys.toList().forEach { key ->
                val preset = rulePresets[key] ?: return@forEach
                if (preset.profileId == request.profileId) {
                    rulePresets[key] = preset.copy(isDefault = false, updatedAt = now)
                }
            }
        }

        val preset = RulePresetRecord(
            id = request.id,
            profileId = request.profileId,
            name = request.name,
            config = request.config,
            isDefault = request.isDefault,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        rulePresets[request.id] = preset
        return preset
    }

    override suspend fun deleteRulePreset(accessToken: String, presetId: String) {
        rulePresets.remove(presetId)
    }

    override suspend fun upsertStreamSession(
        accessToken: String,
        sessionId: String,
        request: StreamSessionSyncRequest
    ): StreamSessionRecord {
        val existing = streamSessions[sessionId]
        val session = StreamSessionRecord(
            id = sessionId,
            profileId = request.profileId,
            videoId = request.videoId,
            liveChatId = request.liveChatId,
            title = request.title,
            startedAt = request.startedAt ?: existing?.startedAt ?: Instant.now().toString(),
            endedAt = existing?.endedAt
        )
        streamSessions[sessionId] = session
        return session
    }

    override suspend fun endStreamSession(
        accessToken: String,
        sessionId: String,
        endedAtIso: String?
    ): StreamSessionRecord {
        val existing = streamSessions[sessionId] ?: StreamSessionRecord(
            id = sessionId,
            profileId = "local-default-profile",
            videoId = "demo-video",
            liveChatId = "demo-live-chat",
            title = "Demo stream",
            startedAt = Instant.now().toString(),
            endedAt = null
        )
        val ended = existing.copy(endedAt = endedAtIso ?: Instant.now().toString())
        streamSessions[sessionId] = ended
        return ended
    }

    override suspend fun streamSessionLogs(accessToken: String, sessionId: String): StreamSessionLogs {
        val session = streamSessions[sessionId] ?: upsertStreamSession(
            accessToken = accessToken,
            sessionId = sessionId,
            request = StreamSessionSyncRequest(
                profileId = "local-default-profile",
                videoId = "demo-video",
                liveChatId = "demo-live-chat",
                title = "Demo stream"
            )
        )

        return StreamSessionLogs(
            session = session,
            messages = streamMessages.filter { it.sessionId == sessionId },
            actions = moderationActions.filter { it.sessionId == sessionId },
            runtimeEvents = runtimeEvents.filter { it.sessionId == sessionId }
        )
    }

    override suspend fun streamSessionAnalyticsSummary(
        accessToken: String,
        profileId: String?,
        days: Int
    ): StreamSessionAnalyticsSummary {
        val cutoffMillis = Instant.now().minusSeconds(days.coerceIn(1, 365).toLong() * 24L * 60L * 60L).toEpochMilli()
        val sessions = streamSessions.values
            .filter { session -> profileId == null || session.profileId == profileId }
            .filter { session -> session.startedAt.toEpochMillisOrNull()?.let { it >= cutoffMillis } ?: true }
            .sortedByDescending { session -> session.startedAt }
        val byStream = sessions.map { session ->
            val messages = streamMessages.filter { message -> message.sessionId == session.id }
            val actions = moderationActions.filter { action -> action.sessionId == session.id }
            val events = runtimeEvents.filter { event -> event.sessionId == session.id }
            StreamAnalyticsByStream(
                sessionId = session.id,
                title = session.title,
                videoId = session.videoId,
                startedAt = session.startedAt,
                endedAt = session.endedAt,
                messageCount = messages.size,
                uniqueChatters = messages.map { it.authorChannelId }.distinct().size,
                moderationActionCount = actions.size,
                destructiveActionCount = actions.count { it.actionType in DestructiveAnalyticsActions },
                spamAttemptCount = actions.count { it.isDemoSpamAttempt() },
                commandCount = events.count { it.type == "command_sent" },
                timerCount = events.count { it.type == "timer_sent" },
                reconnectEvents = events.count { it.isDemoReconnectEvent() },
                uptimeMillis = session.demoUptimeMillis(events)
            )
        }
        val byDay = byStream
            .groupBy { stream -> stream.startedAt.take(10) }
            .map { (day, streams) ->
                StreamAnalyticsByDay(
                    day = day,
                    streamCount = streams.size,
                    messageCount = streams.sumOf { it.messageCount },
                    moderationActionCount = streams.sumOf { it.moderationActionCount },
                    spamAttemptCount = streams.sumOf { it.spamAttemptCount },
                    reconnectEvents = streams.sumOf { it.reconnectEvents },
                    uptimeMillis = streams.sumOf { it.uptimeMillis }
                )
            }
            .sortedBy { it.day }

        return StreamSessionAnalyticsSummary(
            generatedAt = Instant.now().toString(),
            rangeDays = days.coerceIn(1, 365),
            sessionCount = sessions.size,
            totalMessages = byStream.sumOf { it.messageCount },
            totalModerationActions = byStream.sumOf { it.moderationActionCount },
            totalRuntimeEvents = runtimeEvents.count { event -> sessions.any { session -> session.id == event.sessionId } },
            totalUptimeMillis = byStream.sumOf { it.uptimeMillis },
            reconnectEvents = byStream.sumOf { it.reconnectEvents },
            byStream = byStream,
            byDay = byDay,
            topChatters = demoTopChatters(sessions),
            commandUsage = demoCommandUsage(sessions),
            ruleEffectiveness = demoRuleEffectiveness(sessions),
            ruleEffectivenessByPreset = demoRuleEffectivenessByPreset(sessions),
            spamAttemptsByDay = byDay.map { day -> StreamAnalyticsSpamDay(day = day.day, count = day.spamAttemptCount) },
            uptimeByStream = byStream.map { stream ->
                StreamAnalyticsUptime(
                    sessionId = stream.sessionId,
                    title = stream.title,
                    uptimeMillis = stream.uptimeMillis,
                    reconnectEvents = stream.reconnectEvents
                )
            }
        )
    }

    override suspend fun streamChatSummary(accessToken: String, sessionId: String): StreamChatSummary {
        val logs = streamSessionLogs(accessToken, sessionId)
        val topQuestions = logs.messages
            .mapNotNull { message -> message.text.normalizedQuestion()?.let { question -> question to message.text.trim() } }
            .groupBy({ it.first }, { it.second })
            .map { (_, questions) ->
                StreamChatSummaryQuestion(
                    question = questions.first().replace(Regex("\\s+"), " ").take(160),
                    count = questions.size
                )
            }
            .filter { question -> question.count >= 2 }
            .sortedWith(compareByDescending<StreamChatSummaryQuestion> { it.count }.thenBy { it.question })
            .take(5)
        val chatterSummaries = logs.messages
            .groupBy { message -> message.authorChannelId }
            .map { (authorChannelId, messages) ->
                StreamChatSummaryChatter(
                    authorChannelId = authorChannelId,
                    authorName = messages.lastOrNull()?.authorName.orEmpty().ifBlank { authorChannelId },
                    messageCount = messages.size
                )
            }
            .sortedWith(compareByDescending<StreamChatSummaryChatter> { it.messageCount }.thenBy { it.authorName })
        val topChatters = chatterSummaries
            .take(5)
        val destructiveActionCount = logs.actions.count { action -> action.actionType in DestructiveAnalyticsActions }
        val keywords = logs.messages.demoTopKeywords()
        val highlights = buildList {
            if (keywords.isNotEmpty()) add("Most discussed terms: ${keywords.take(5).joinToString(", ")}.")
            topChatters.firstOrNull()?.let { chatter -> add("Most active chatter: ${chatter.authorName} with ${chatter.messageCount} message(s).") }
            topQuestions.firstOrNull()?.let { question -> add("Repeated question: \"${question.question}\" appeared ${question.count} time(s).") }
            if (destructiveActionCount > 0) add("$destructiveActionCount destructive moderation action(s) were logged.")
            if (isEmpty() && logs.messages.isNotEmpty()) add("Chat activity was low and did not produce strong repeated themes.")
        }
        val moderationNotes = logs.actions.demoModerationNotes()
        val suggestedFollowUps = buildList {
            topQuestions.firstOrNull()?.let { question -> add("Add or update an FAQ reply for: \"${question.question}\".") }
            if (destructiveActionCount > 0) add("Review destructive moderation actions before reusing this preset.")
            if (moderationNotes.any { note -> note.contains("false positive", ignoreCase = true) }) add("Tune rules that produced false positives.")
            if (logs.messages.isEmpty()) add("Sync live chat logs before relying on an after-stream summary.")
            if (isEmpty()) add("Keep the current preset; no urgent follow-up was detected.")
        }

        return StreamChatSummary(
            provider = "local-heuristic",
            sessionId = logs.session.id,
            generatedAt = Instant.now().toString(),
            title = logs.session.title,
            summary = if (logs.messages.isEmpty()) {
                "No synced chat messages were available for this stream yet."
            } else {
                "Local chat summary for ${logs.session.title ?: "this stream"}: ${logs.messages.size} messages from ${chatterSummaries.size} chatter(s)."
            },
            highlights = highlights,
            topQuestions = topQuestions,
            topChatters = topChatters,
            moderationNotes = moderationNotes,
            suggestedFollowUps = suggestedFollowUps,
            stats = StreamChatSummaryStats(
                messageCount = logs.messages.size,
                uniqueChatters = chatterSummaries.size,
                moderationActionCount = logs.actions.size,
                destructiveActionCount = destructiveActionCount
            )
        )
    }

    override suspend fun listFaqEntries(accessToken: String, profileId: String): FaqEntryList {
        return FaqEntryList(
            faqEntries = faqEntries.values
                .filter { entry -> entry.profileId == profileId }
                .sortedBy { entry -> entry.question }
        )
    }

    override suspend fun saveFaqEntry(accessToken: String, request: FaqEntrySyncRequest): FaqEntryRecord {
        val now = Instant.now().toString()
        val existing = faqEntries[request.id]
        val record = FaqEntryRecord(
            id = request.id,
            profileId = request.profileId,
            question = request.question.trim(),
            answer = request.answer.trim(),
            keywords = request.keywords.map { keyword -> keyword.trim().lowercase() }.filter { it.isNotBlank() }.distinct(),
            enabled = request.enabled,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        faqEntries[record.id] = record
        return record
    }

    override suspend fun deleteFaqEntry(accessToken: String, faqEntryId: String) {
        faqEntries.remove(faqEntryId)
    }

    override suspend fun suggestFaqReply(
        accessToken: String,
        request: FaqReplySuggestionRequest
    ): FaqReplySuggestionResult {
        val scored = faqEntries.values
            .filter { entry -> entry.profileId == request.profileId && entry.enabled }
            .map { entry -> entry to entry.demoFaqConfidence(request.messageText) }
            .sortedWith(compareByDescending<Pair<FaqEntryRecord, Double>> { it.second }.thenBy { it.first.question })
            .firstOrNull()
        val matched = scored != null && scored.second >= request.minConfidence
        val entry = scored?.first
        return FaqReplySuggestionResult(
            provider = "local-heuristic",
            matched = matched,
            manualApprovalRequired = true,
            entryId = entry?.id?.takeIf { matched },
            question = entry?.question?.takeIf { matched },
            replyText = entry?.answer?.takeIf { matched },
            confidence = scored?.second ?: 0.0,
            matchedKeywords = if (matched && entry != null) {
                entry.keywords.filter { keyword -> request.messageText.normalizedFaqText().contains(keyword.normalizedFaqText()) }
            } else {
                emptyList()
            },
            explanation = if (matched && entry != null) {
                "Suggested from creator FAQ: \"${entry.question}\"."
            } else {
                "No saved FAQ answer matched the viewer message strongly enough."
            }
        )
    }

    override suspend fun recordStreamMessage(
        accessToken: String,
        sessionId: String,
        request: ChatMessageLogSyncRequest
    ): ChatMessageLogRecord {
        val existingIndex = streamMessages.indexOfFirst {
            it.sessionId == sessionId && it.youtubeMessageId == request.youtubeMessageId
        }
        val existing = streamMessages.getOrNull(existingIndex)
        val record = ChatMessageLogRecord(
            id = existing?.id ?: "demo-message-${streamMessages.size + 1}",
            sessionId = sessionId,
            youtubeMessageId = request.youtubeMessageId,
            authorChannelId = request.authorChannelId,
            authorName = request.authorName,
            text = request.text,
            receivedAt = request.receivedAt ?: existing?.receivedAt ?: Instant.now().toString(),
            createdAt = existing?.createdAt ?: Instant.now().toString()
        )
        if (existingIndex >= 0) {
            streamMessages[existingIndex] = record
        } else {
            streamMessages.add(record)
        }
        return record
    }

    override suspend fun recordModerationActionLog(
        accessToken: String,
        sessionId: String,
        request: ModerationActionLogSyncRequest
    ): ModerationActionLogRecord {
        val actionId = request.clientActionId ?: "demo-action-${moderationActions.size + 1}"
        val existingIndex = moderationActions.indexOfFirst { it.id == actionId }
        val existing = moderationActions.getOrNull(existingIndex)
        val record = ModerationActionLogRecord(
            id = actionId,
            sessionId = sessionId,
            youtubeMessageId = request.youtubeMessageId,
            authorChannelId = request.authorChannelId,
            actionType = request.actionType,
            reason = request.reason,
            confidence = request.confidence,
            metadata = request.metadata,
            reviewStatus = existing?.reviewStatus,
            reviewedAt = existing?.reviewedAt,
            reviewNote = existing?.reviewNote,
            createdAt = existing?.createdAt ?: Instant.now().toString()
        )
        if (existingIndex >= 0) {
            moderationActions[existingIndex] = record
        } else {
            moderationActions.add(record)
        }
        return record
    }

    override suspend fun reviewModerationActionLog(
        accessToken: String,
        sessionId: String,
        actionId: String,
        request: ModerationActionReviewRequest
    ): ModerationActionLogRecord {
        val existingIndex = moderationActions.indexOfFirst { it.id == actionId && it.sessionId == sessionId }
        val existing = moderationActions.getOrNull(existingIndex) ?: ModerationActionLogRecord(
            id = actionId,
            sessionId = sessionId,
            youtubeMessageId = null,
            authorChannelId = null,
            actionType = "flagForReview",
            reason = "reviewed_rule_match",
            confidence = null,
            createdAt = Instant.now().toString()
        )
        val updated = existing.copy(
            reviewStatus = request.reviewStatus,
            reviewedAt = Instant.now().toString(),
            reviewNote = request.reviewNote
        )
        if (existingIndex >= 0) {
            moderationActions[existingIndex] = updated
        } else {
            moderationActions.add(updated)
        }
        return updated
    }

    override suspend fun recordRuntimeEvent(
        accessToken: String,
        sessionId: String,
        request: RuntimeEventSyncRequest
    ): RuntimeEventRecord {
        val record = RuntimeEventRecord(
            id = "demo-runtime-${runtimeEvents.size + 1}",
            sessionId = sessionId,
            type = request.type,
            message = request.message,
            metadata = request.metadata,
            createdAt = Instant.now().toString()
        )
        runtimeEvents.add(record)
        return record
    }

    override suspend fun saveCommand(
        accessToken: String,
        request: CommandSyncRequest
    ): CommandSyncResult {
        return CommandSyncResult(
            id = request.id,
            syncedAt = Instant.now().toString()
        )
    }

    override suspend fun sendCommandToLiveChat(
        accessToken: String,
        commandId: String,
        request: CommandManualSendRequest
    ): CommandManualSendResult {
        return CommandManualSendResult(
            commandId = commandId,
            commandName = "!demo",
            liveChatId = request.liveChatId,
            messageId = "demo-command-message-${commandId.length}",
            sentText = "Demo command sent",
            sentAt = Instant.now().toString()
        )
    }

    override suspend fun deleteCommand(accessToken: String, commandId: String) = Unit

    override suspend fun listUserProfiles(accessToken: String, profileId: String?): UserProfileList {
        return UserProfileList(
            users = userProfiles.values
                .filter { user -> profileId == null || user.profileId == profileId }
                .sortedByDescending { user -> user.lastSeenAt }
        )
    }

    override suspend fun warnUser(
        accessToken: String,
        request: UserWarningRequest
    ): UserWarningResult {
        val now = Instant.now().toString()
        val userProfileId = "demo-user-${request.profileId}-${request.authorChannelId}"
        val existing = userProfiles[userProfileId]
        val strike = UserStrikeRecord(
            id = "demo-strike-${request.authorChannelId.length}-${System.currentTimeMillis()}",
            userProfileId = userProfileId,
            reason = request.reason,
            createdAt = now
        )
        val strikes = userProfileStrikes.getOrPut(userProfileId) { mutableListOf() }
        strikes.add(0, strike)
        val user = UserProfileRecord(
            id = userProfileId,
            profileId = request.profileId,
            authorChannelId = request.authorChannelId,
            displayName = request.displayName,
            profileImageUrl = request.profileImageUrl ?: existing?.profileImageUrl,
            firstSeenAt = existing?.firstSeenAt ?: now,
            lastSeenAt = now,
            messageCount = existing?.messageCount ?: 0,
            notes = existing?.notes,
            strikeCount = strikes.size,
            recentStrikes = strikes.take(5),
            recentModerationActions = existing?.recentModerationActions ?: emptyList()
        )
        userProfiles[userProfileId] = user

        return UserWarningResult(
            user = user,
            strike = strike,
            messageId = if (!request.liveChatId.isNullOrBlank() && !request.warningText.isNullOrBlank()) {
                "demo-warning-message-${request.liveChatId.length}"
            } else {
                null
            },
            warnedAt = now
        )
    }

    override suspend fun updateUserProfileNotes(
        accessToken: String,
        userProfileId: String,
        request: UserProfileNotesRequest
    ): UserProfileRecord {
        val existing = userProfiles[userProfileId]
        val updated = (existing ?: UserProfileRecord(
            id = userProfileId,
            profileId = "local-default-profile",
            authorChannelId = userProfileId,
            displayName = "Viewer",
            profileImageUrl = null,
            firstSeenAt = Instant.now().toString(),
            lastSeenAt = Instant.now().toString(),
            messageCount = 0,
            notes = null,
            strikeCount = 0,
            recentStrikes = emptyList(),
            recentModerationActions = emptyList()
        )).copy(notes = request.notes?.takeIf { it.isNotBlank() })
        userProfiles[userProfileId] = updated
        return updated
    }

    override suspend fun hideUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserHideRequest
    ): UserHideResult {
        val user = userProfiles[userProfileId] ?: UserProfileRecord(
            id = userProfileId,
            profileId = "local-default-profile",
            authorChannelId = userProfileId,
            displayName = "Viewer",
            profileImageUrl = null,
            firstSeenAt = Instant.now().toString(),
            lastSeenAt = Instant.now().toString(),
            messageCount = 0,
            notes = null,
            strikeCount = 0,
            recentStrikes = emptyList(),
            recentModerationActions = emptyList()
        )
        val now = Instant.now().toString()
        val action = UserModerationActionRecord(
            id = "demo-action-hide-${System.currentTimeMillis()}",
            userProfileId = userProfileId,
            actionType = "hideUser",
            liveChatId = request.liveChatId,
            liveChatBanId = "demo-live-chat-ban-$userProfileId",
            reason = request.reason,
            durationSeconds = null,
            createdAt = now,
            expiresAt = null
        )
        val actions = userProfileModerationActions.getOrPut(userProfileId) { mutableListOf() }
        actions.add(0, action)
        val updatedUser = user.copy(recentModerationActions = actions.take(5))
        userProfiles[userProfileId] = updatedUser
        return UserHideResult(
            user = updatedUser,
            action = action,
            liveChatId = request.liveChatId,
            liveChatBanId = action.liveChatBanId,
            actionType = "hideUser",
            reason = request.reason,
            hiddenAt = now
        )
    }

    override suspend fun timeoutUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserTimeoutRequest
    ): UserTimeoutResult {
        val now = Instant.now()
        val user = userProfiles[userProfileId] ?: UserProfileRecord(
            id = userProfileId,
            profileId = "local-default-profile",
            authorChannelId = userProfileId,
            displayName = "Viewer",
            profileImageUrl = null,
            firstSeenAt = now.toString(),
            lastSeenAt = now.toString(),
            messageCount = 0,
            notes = null,
            strikeCount = 0,
            recentStrikes = emptyList(),
            recentModerationActions = emptyList()
        )
        val expiresAt = now.plusSeconds(request.durationSeconds.toLong()).toString()
        val action = UserModerationActionRecord(
            id = "demo-action-timeout-${System.currentTimeMillis()}",
            userProfileId = userProfileId,
            actionType = "timeoutUser",
            liveChatId = request.liveChatId,
            liveChatBanId = "demo-live-chat-ban-$userProfileId",
            reason = request.reason,
            durationSeconds = request.durationSeconds,
            createdAt = now.toString(),
            expiresAt = expiresAt
        )
        val actions = userProfileModerationActions.getOrPut(userProfileId) { mutableListOf() }
        actions.add(0, action)
        val updatedUser = user.copy(recentModerationActions = actions.take(5))
        userProfiles[userProfileId] = updatedUser
        return UserTimeoutResult(
            user = updatedUser,
            action = action,
            liveChatId = request.liveChatId,
            liveChatBanId = action.liveChatBanId,
            durationSeconds = request.durationSeconds,
            actionType = "timeoutUser",
            reason = request.reason,
            timedOutAt = now.toString(),
            timedOutUntil = expiresAt
        )
    }

    override suspend fun unbanUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserUnbanRequest
    ): UserUnbanResult {
        val now = Instant.now().toString()
        val user = userProfiles[userProfileId] ?: UserProfileRecord(
            id = userProfileId,
            profileId = "local-default-profile",
            authorChannelId = userProfileId,
            displayName = "Viewer",
            profileImageUrl = null,
            firstSeenAt = now,
            lastSeenAt = now,
            messageCount = 0,
            notes = null,
            strikeCount = 0,
            recentStrikes = emptyList(),
            recentModerationActions = emptyList()
        )
        val action = UserModerationActionRecord(
            id = "demo-action-unban-${System.currentTimeMillis()}",
            userProfileId = userProfileId,
            actionType = "unbanUser",
            liveChatId = request.liveChatId,
            liveChatBanId = request.liveChatBanId,
            reason = request.reason,
            durationSeconds = null,
            createdAt = now,
            expiresAt = null
        )
        val actions = userProfileModerationActions.getOrPut(userProfileId) { mutableListOf() }
        actions.add(0, action)
        val updatedUser = user.copy(recentModerationActions = actions.take(5))
        userProfiles[userProfileId] = updatedUser
        return UserUnbanResult(
            user = updatedUser,
            action = action,
            liveChatBanId = request.liveChatBanId,
            actionType = "unbanUser",
            reason = request.reason,
            unbannedAt = now
        )
    }

    override suspend fun whitelistUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserWhitelistRequest
    ): UserWhitelistResult {
        val nowInstant = Instant.now()
        val now = nowInstant.toString()
        val user = userProfiles[userProfileId] ?: UserProfileRecord(
            id = userProfileId,
            profileId = "local-default-profile",
            authorChannelId = userProfileId,
            displayName = "Viewer",
            profileImageUrl = null,
            firstSeenAt = now,
            lastSeenAt = now,
            messageCount = 0,
            notes = null,
            strikeCount = 0,
            recentStrikes = emptyList(),
            recentModerationActions = emptyList()
        )
        userProfiles[userProfileId] = user
        val whitelist = UserWhitelistRecord(
            id = whitelistEntries[user.authorChannelId]?.id ?: "demo-whitelist-${user.authorChannelId.length}",
            profileId = user.profileId,
            authorChannelId = user.authorChannelId,
            displayName = user.displayName,
            temporaryUntil = request.durationSeconds?.let { nowInstant.plusSeconds(it.toLong()).toString() },
            createdAt = whitelistEntries[user.authorChannelId]?.createdAt ?: now
        )
        whitelistEntries[user.authorChannelId] = whitelist
        return UserWhitelistResult(
            user = user,
            whitelist = whitelist,
            whitelistedAt = now
        )
    }

    override suspend fun saveTimer(
        accessToken: String,
        request: TimerSyncRequest
    ): TimerSyncResult {
        return TimerSyncResult(
            id = request.id,
            syncedAt = Instant.now().toString()
        )
    }

    override suspend fun deleteTimer(accessToken: String, timerId: String) = Unit

    private fun demoBroadcasts(channelId: String, includeScheduled: Boolean): List<YouTubeBroadcast> {
        val now = Instant.now()
        val broadcasts = listOf(
            YouTubeBroadcast(
                videoId = "demo-video",
                liveChatId = "live-chat-$channelId",
                title = "Demo live stream",
                status = "active",
                scheduledStartTime = now.minusSeconds(600).toString(),
                actualStartTime = now.toString()
            ),
            YouTubeBroadcast(
                videoId = "demo-upcoming-video",
                liveChatId = null,
                title = "Upcoming demo stream",
                status = "upcoming",
                scheduledStartTime = now.plusSeconds(3600).toString(),
                actualStartTime = null
            )
        )

        return if (includeScheduled) broadcasts else broadcasts.filter { it.status == "active" }
    }

    private fun demoTopChatters(sessions: List<StreamSessionRecord>): List<StreamAnalyticsChatter> {
        val sessionIds = sessions.map { it.id }.toSet()
        val chatters = linkedMapOf<String, StreamAnalyticsChatter>()
        streamMessages
            .filter { message -> message.sessionId in sessionIds }
            .forEach { message ->
                val existing = chatters[message.authorChannelId]
                chatters[message.authorChannelId] = StreamAnalyticsChatter(
                    authorChannelId = message.authorChannelId,
                    authorName = message.authorName,
                    messageCount = (existing?.messageCount ?: 0) + 1,
                    moderationActionCount = existing?.moderationActionCount ?: 0
                )
            }
        moderationActions
            .filter { action -> action.sessionId in sessionIds && !action.authorChannelId.isNullOrBlank() }
            .forEach { action ->
                val channelId = action.authorChannelId ?: return@forEach
                val existing = chatters[channelId]
                chatters[channelId] = StreamAnalyticsChatter(
                    authorChannelId = channelId,
                    authorName = existing?.authorName ?: channelId,
                    messageCount = existing?.messageCount ?: 0,
                    moderationActionCount = (existing?.moderationActionCount ?: 0) + 1
                )
            }

        return chatters.values
            .sortedByDescending { chatter -> chatter.messageCount + chatter.moderationActionCount }
            .take(10)
    }

    private fun demoCommandUsage(sessions: List<StreamSessionRecord>): List<StreamAnalyticsCommand> {
        val sessionIds = sessions.map { it.id }.toSet()
        val counts = linkedMapOf<String, StreamAnalyticsCommand>()
        runtimeEvents
            .filter { event -> event.sessionId in sessionIds && event.type == "command_sent" }
            .forEach { event ->
                val commandId = (event.metadata?.get("commandId") as? String)
                    ?: (event.metadata?.get("trigger") as? String)
                    ?: "unknown-command"
                val trigger = event.metadata?.get("trigger") as? String
                val existing = counts[commandId]
                counts[commandId] = StreamAnalyticsCommand(
                    commandId = commandId,
                    trigger = trigger ?: existing?.trigger,
                    count = (existing?.count ?: 0) + 1
                )
            }

        return counts.values
            .sortedByDescending { command -> command.count }
            .take(10)
    }

    private fun demoRuleEffectiveness(sessions: List<StreamSessionRecord>): List<StreamAnalyticsRule> {
        val sessionIds = sessions.map { it.id }.toSet()
        val rules = linkedMapOf<String, StreamAnalyticsRule>()
        moderationActions
            .filter { action -> action.sessionId in sessionIds && action.isDemoRuleAction() }
            .forEach { action ->
                val rule = action.reason.demoRuleLabel()
                val existing = rules[rule]
                rules[rule] = StreamAnalyticsRule(
                    rule = rule,
                    matchCount = (existing?.matchCount ?: 0) + 1,
                    destructiveActionCount = (existing?.destructiveActionCount ?: 0) +
                        if (action.actionType in DestructiveAnalyticsActions) 1 else 0,
                    falsePositiveCount = (existing?.falsePositiveCount ?: 0) +
                        if (action.reviewStatus == "false_positive") 1 else 0
                )
            }

        return rules.values
            .sortedByDescending { rule -> rule.matchCount }
            .take(10)
    }

    private fun demoRuleEffectivenessByPreset(sessions: List<StreamSessionRecord>): List<StreamAnalyticsRulePreset> {
        val sessionIds = sessions.map { it.id }.toSet()
        val rules = linkedMapOf<String, StreamAnalyticsRulePreset>()
        moderationActions
            .filter { action -> action.sessionId in sessionIds && action.isDemoRuleAction() }
            .forEach { action ->
                val metadata = action.normalizedMetadata()
                val presetId = metadata["rulePresetId"] as? String ?: metadata["presetId"] as? String ?: "demo-preset"
                val presetName = metadata["rulePresetName"] as? String ?: metadata["presetName"] as? String ?: "Demo preset"
                val presetVersion = metadata["rulePresetVersion"] as? String ?: metadata["presetVersion"] as? String
                val rule = action.reason.demoRuleLabel()
                val key = "$presetId:${presetVersion ?: "unversioned"}:$rule"
                val existing = rules[key]
                rules[key] = StreamAnalyticsRulePreset(
                    presetId = presetId,
                    presetName = presetName,
                    presetVersion = presetVersion,
                    rule = rule,
                    matchCount = (existing?.matchCount ?: 0) + 1,
                    destructiveActionCount = (existing?.destructiveActionCount ?: 0) +
                        if (action.actionType in DestructiveAnalyticsActions) 1 else 0,
                    falsePositiveCount = (existing?.falsePositiveCount ?: 0) +
                        if (action.reviewStatus == "false_positive") 1 else 0
                )
            }

        return rules.values
            .sortedByDescending { rule -> rule.matchCount }
            .take(20)
    }
}

private val DefaultRulePresetTemplates = listOf(
    RulePresetTemplateRecord(
        id = "family-friendly",
        name = "Family friendly",
        description = "Balanced protection for younger audiences, schools, and brand-safe streams.",
        config = ModerationProfile(
            blockedTerms = listOf("cheap views", "free subs", "sub4sub", "giveaway scam"),
            linkPolicy = LinkPolicy.Flag,
            blockedDomains = listOf("grabify.link"),
            capsThreshold = 0.72,
            maxRepeatedCharacters = 5,
            maxEmojiCount = 6,
            maxMentions = 4,
            maxSymbolCount = 12,
            ignoreMembers = true,
            newChatterBurstThreshold = 5,
            newChatterBurstWindowSeconds = 30
        )
    ),
    RulePresetTemplateRecord(
        id = "gaming-default",
        name = "Gaming default",
        description = "Fast chat defaults for gameplay streams with room for hype and emotes.",
        config = ModerationProfile(
            blockedTerms = listOf("cheap views", "free skins", "rank boost", "sub4sub"),
            linkPolicy = LinkPolicy.Flag,
            capsThreshold = 0.78,
            maxRepeatedCharacters = 6,
            maxEmojiCount = 10,
            maxMentions = 5,
            maxSymbolCount = 18,
            newChatterBurstThreshold = 6,
            newChatterBurstWindowSeconds = 25
        )
    ),
    RulePresetTemplateRecord(
        id = "education-qa",
        name = "Education/Q&A",
        description = "Tighter signal-to-noise for lessons, workshops, and question-heavy streams.",
        config = ModerationProfile(
            blockedTerms = listOf("cheap views", "free subs", "sub4sub"),
            linkPolicy = LinkPolicy.Flag,
            capsThreshold = 0.68,
            maxRepeatedCharacters = 4,
            maxEmojiCount = 4,
            maxMentions = 3,
            maxSymbolCount = 10,
            ignoreMembers = true,
            newChatterBurstThreshold = 4,
            newChatterBurstWindowSeconds = 40
        )
    ),
    RulePresetTemplateRecord(
        id = "music-performance",
        name = "Music/live performance",
        description = "Permissive enough for applause and lyrics chat while still catching spam.",
        config = ModerationProfile(
            blockedTerms = listOf("cheap views", "free subs", "sub4sub"),
            linkPolicy = LinkPolicy.Flag,
            capsThreshold = 0.82,
            maxRepeatedCharacters = 8,
            maxEmojiCount = 14,
            maxMentions = 5,
            maxSymbolCount = 22,
            ignoreMembers = true,
            newChatterBurstThreshold = 7,
            newChatterBurstWindowSeconds = 30
        )
    ),
    RulePresetTemplateRecord(
        id = "high-security-raid-mode",
        name = "High-security raid mode",
        description = "Emergency posture for raids, bot bursts, and aggressive link spam.",
        config = ModerationProfile(
            blockedTerms = listOf("cheap views", "free subs", "sub4sub", "spam raid"),
            linkPolicy = LinkPolicy.Delete,
            blockedDomains = listOf("grabify.link", "iplogger.org"),
            capsThreshold = 0.6,
            maxRepeatedCharacters = 3,
            maxEmojiCount = 5,
            maxMentions = 3,
            maxSymbolCount = 10,
            raidMode = true,
            newChatterBurstThreshold = 3,
            newChatterBurstWindowSeconds = 20
        )
    )
)

private val DestructiveAnalyticsActions = setOf("deleteMessage", "hideUser", "timeoutUser")

private fun String.toEpochMillisOrNull(): Long? {
    return runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
}

private fun StreamSessionRecord.demoUptimeMillis(events: List<RuntimeEventRecord>): Long {
    val started = startedAt.toEpochMillisOrNull()
    val ended = endedAt?.toEpochMillisOrNull()
    if (started != null && ended != null && ended >= started) {
        return ended - started
    }

    return events
        .filter { event -> event.type == "runtime_session_summary" }
        .mapNotNull { event -> event.metadata?.get("durationMillis") as? Number }
        .maxOfOrNull { duration -> duration.toLong() }
        ?: 0L
}

private fun RuntimeEventRecord.isDemoReconnectEvent(): Boolean {
    val text = "$type $message".lowercase()
    return text.contains("reconnect") || text.contains("backoff") || text.contains("waiting for network")
}

private fun ModerationActionLogRecord.isDemoSpamAttempt(): Boolean {
    return isDemoRuleAction() && reviewStatus != "false_positive"
}

private fun ModerationActionLogRecord.isDemoRuleAction(): Boolean {
    return actionType != "allow" &&
        !reason.startsWith("manual_") &&
        reason != "auto_reply"
}

private fun List<ChatMessageLogRecord>.demoTopKeywords(): List<String> {
    val stopWords = setOf("about", "again", "chat", "from", "have", "just", "like", "stream", "that", "this", "what", "when", "with", "your")
    return flatMap { message ->
        Regex("[\\p{L}\\p{N}]{4,}").findAll(message.text.lowercase()).map { match -> match.value }.toList()
    }
        .filterNot { word -> word in stopWords }
        .groupingBy { word -> word }
        .eachCount()
        .filterValues { count -> count >= 2 }
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(8)
        .map { it.first }
}

private fun List<ModerationActionLogRecord>.demoModerationNotes(): List<String> {
    if (isEmpty()) {
        return listOf("No moderation actions were synced for this stream.")
    }

    val actionCount = size
    val destructiveActionCount = count { action -> action.actionType in DestructiveAnalyticsActions }
    val falsePositiveCount = count { action -> action.reviewStatus == "false_positive" }
    val topReason = groupingBy { action -> action.reason.substringBefore(":").ifBlank { action.reason } }
        .eachCount()
        .toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .firstOrNull()
    return buildList {
        add("$actionCount moderation action(s) were logged; $destructiveActionCount were destructive.")
        topReason?.let { (reason, count) -> add("Top moderation trigger: $reason ($count match(es)).") }
        if (falsePositiveCount > 0) add("$falsePositiveCount action(s) were marked false positive and should inform preset tuning.")
    }
}

private fun FaqEntryRecord.demoFaqConfidence(messageText: String): Double {
    val messageTokens = messageText.normalizedFaqText().split(" ").filter { it.length >= 3 }
    val entryTokens = (question.normalizedFaqText().split(" ") + keywords.flatMap { it.normalizedFaqText().split(" ") })
        .filter { it.length >= 3 }
        .toSet()
    if (entryTokens.isEmpty()) return 0.0

    val overlap = messageTokens.count { token -> token in entryTokens }.toDouble() / entryTokens.size.toDouble()
    val keywordBoost = keywords.count { keyword -> messageText.normalizedFaqText().contains(keyword.normalizedFaqText()) }
        .coerceAtMost(3) * 0.12
    val questionBoost = if (messageText.contains("?")) 0.08 else 0.0
    return minOf(0.92, overlap + keywordBoost + questionBoost)
}

private fun String.normalizedFaqText(): String {
    return trim()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s?]"), " ")
        .replace(Regex("\\s+"), " ")
}

private fun ModerationActionLogRecord.normalizedMetadata(): Map<String, Any?> {
    val localMetadata = metadata
        ?.get("localMetadataJson")
        ?.let { value -> value as? String }
        ?.let { json -> runCatching { org.json.JSONObject(json).toMap() }.getOrNull() }
        .orEmpty()
    return metadata.orEmpty() + localMetadata
}

private fun org.json.JSONObject.toMap(): Map<String, Any?> {
    return keys().asSequence().associateWith { key -> opt(key) }
}

private fun TeamMemberRecord.toDemoMembership(): TeamMembershipRecord {
    return TeamMembershipRecord(
        member = this,
        profileName = "Demo team profile",
        channelId = profileId,
        ownerDeviceId = "demo-owner-device"
    )
}

private fun String.demoRuleLabel(): String {
    return substringBefore(":").ifBlank { this }
}

private val ActionType.apiValue: String
    get() = when (this) {
        ActionType.Allow -> "allow"
        ActionType.FlagForReview -> "flagForReview"
        ActionType.DeleteMessage -> "deleteMessage"
        ActionType.TimeoutUser -> "timeoutUser"
        ActionType.HideUser -> "hideUser"
        ActionType.SendAutoReply -> "sendAutoReply"
    }

private fun String.normalizedQuestion(): String? {
    val normalized = trim()
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s?]"), "")
        .replace(Regex("\\s+"), " ")
    return normalized.takeIf { value -> value.endsWith("?") && value.length >= 12 }
}

private fun String.suggestionLabel(): String {
    return when {
        startsWith("blocked_term:") -> "Blocked phrase"
        startsWith("blocked_domain:") -> "Blocked domain"
        startsWith("domain_not_allowed:") -> "Unapproved domain"
        this == "link_policy" -> "Link policy"
        this == "excessive_caps" -> "Excessive caps"
        this == "repeated_characters" -> "Repeated characters"
        this == "emoji_spam" -> "Emoji spam"
        this == "mention_spam" -> "Mention spam"
        this == "symbol_spam" -> "Symbol spam"
        else -> replace("_", " ")
    }
}

private fun String.suggestionActionLabel(): String {
    return when (this) {
        "deleteMessage" -> "Delete message"
        "timeoutUser" -> "Timeout"
        "hideUser" -> "Hide user"
        "flagForReview" -> "Review"
        else -> "No action"
    }
}
