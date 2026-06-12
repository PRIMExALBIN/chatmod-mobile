package com.chatmod.mobile.data.remote

import com.chatmod.mobile.domain.rules.ChatMessage
import com.chatmod.mobile.domain.rules.ModerationDecision
import com.chatmod.mobile.domain.rules.ModerationProfile

interface ChatModApiClient {
    suspend fun appCompatibility(platform: String, versionName: String, versionCode: Int): AppCompatibility
    suspend fun createDeviceSession(request: DeviceSessionRequest): DeviceSession
    suspend fun currentEntitlement(accessToken: String): EntitlementSnapshot
    suspend fun validateGooglePlayPurchase(accessToken: String, request: GooglePlayPurchaseValidationRequest): GooglePlayPurchaseValidationResult
    suspend fun listChannelProfiles(accessToken: String): ChannelProfileList
    suspend fun createChannelProfile(accessToken: String, request: ChannelProfileCreateRequest): ChannelProfileRecord
    suspend fun backupModerationProfile(accessToken: String, request: ProfileBackupRequest): ProfileBackup
    suspend fun createSettingsBackup(accessToken: String, request: SettingsBackupRequest): SettingsBackupResult
    suspend fun restoreSettingsBackup(accessToken: String, backupId: String, targetProfileId: String? = null): SettingsRestoreResult
    suspend fun listBackups(accessToken: String): BackupList
    suspend fun deleteBackup(accessToken: String, backupId: String)
    suspend fun exportAccount(accessToken: String): AccountExportSummary
    suspend fun youtubeConnectUrl(accessToken: String): YouTubeConnectUrl
    suspend fun youtubeAccountStatus(accessToken: String): YouTubeAccountStatus
    suspend fun disconnectYouTube(accessToken: String): YouTubeDisconnectResult
    suspend fun discordWebhookConfig(accessToken: String, profileId: String): DiscordWebhookConfig
    suspend fun upsertDiscordWebhook(accessToken: String, request: DiscordWebhookUpsertRequest): DiscordWebhookConfig
    suspend fun deleteDiscordWebhook(accessToken: String, profileId: String)
    suspend fun testDiscordWebhook(accessToken: String, profileId: String): DiscordAlertResult
    suspend fun sendDiscordAlert(accessToken: String, request: DiscordAlertRequest): DiscordAlertResult
    suspend fun overlayConfig(accessToken: String, profileId: String): OverlayConfig
    suspend fun upsertOverlayConfig(accessToken: String, request: OverlayConfigUpdateRequest): OverlayConfigMutationResult
    suspend fun rotateOverlayToken(accessToken: String, profileId: String): OverlayConfigMutationResult
    suspend fun listTeamMembers(accessToken: String, profileId: String): TeamMemberList
    suspend fun createTeamInvite(accessToken: String, profileId: String, request: TeamInviteCreateRequest): TeamInviteResult
    suspend fun revokeTeamMember(accessToken: String, profileId: String, memberId: String): TeamMemberRecord
    suspend fun redeemTeamInvite(accessToken: String, request: TeamInviteRedeemRequest): TeamMembershipRecord
    suspend fun listTeamMemberships(accessToken: String): TeamMembershipList
    suspend fun listYouTubeBroadcasts(accessToken: String, channelId: String, includeScheduled: Boolean = true): YouTubeBroadcastList
    suspend fun discoverYouTubeLiveChat(accessToken: String, channelId: String, includeScheduled: Boolean = true): YouTubeLiveChatDiscovery
    suspend fun listYouTubeLiveChatMessages(accessToken: String, request: YouTubeLiveChatMessagesRequest): YouTubeLiveChatMessagePage
    suspend fun sendYouTubeLiveChatMessage(accessToken: String, request: YouTubeMessageSendRequest): YouTubeMessageSendResult
    suspend fun sendYouTubeTestMessage(accessToken: String, request: YouTubeTestMessageRequest): YouTubeTestMessageResult
    suspend fun deleteYouTubeLiveChatMessage(accessToken: String, request: YouTubeMessageDeleteRequest): YouTubeMessageDeleteResult
    suspend fun hideYouTubeLiveChatUser(accessToken: String, request: YouTubeUserHideRequest): YouTubeUserHideResult
    suspend fun unbanYouTubeLiveChatUser(accessToken: String, request: YouTubeUserUnbanRequest): YouTubeUserUnbanResult
    suspend fun deleteCurrentAccount(accessToken: String): AccountDeletionResult
    suspend fun listSupportEvents(accessToken: String): SupportEventList
    suspend fun listApiErrors(accessToken: String): ApiErrorList
    suspend fun recordSupportEvent(accessToken: String, request: SupportEventRequest): SupportEventRecord
    suspend fun recordAnalyticsEvent(accessToken: String, request: AnalyticsEventRequest): AnalyticsEventRecord
    suspend fun listBetaFeedback(accessToken: String): BetaFeedbackList
    suspend fun submitBetaFeedback(accessToken: String, request: BetaFeedbackRequest): BetaFeedbackRecord
    suspend fun evaluateMessage(accessToken: String, message: ChatMessage, profile: ModerationProfile): ModerationDecision
    suspend fun evaluateModerationSuggestion(
        accessToken: String,
        message: ChatMessage,
        profile: ModerationProfile,
        recentMessages: List<ChatMessage> = emptyList(),
        confidenceThreshold: Double = 0.65
    ): ModerationSuggestionResult
    suspend fun listRulePresetTemplates(accessToken: String): RulePresetTemplateList
    suspend fun listRulePresets(accessToken: String, profileId: String? = null): RulePresetList
    suspend fun exportRulePresets(accessToken: String, profileId: String): RulePresetExportBundle
    suspend fun importRulePresets(accessToken: String, request: RulePresetImportRequest): RulePresetImportResult
    suspend fun saveRulePreset(accessToken: String, request: RulePresetSyncRequest): RulePresetRecord
    suspend fun deleteRulePreset(accessToken: String, presetId: String)
    suspend fun upsertStreamSession(accessToken: String, sessionId: String, request: StreamSessionSyncRequest): StreamSessionRecord
    suspend fun endStreamSession(accessToken: String, sessionId: String, endedAtIso: String? = null): StreamSessionRecord
    suspend fun streamSessionLogs(accessToken: String, sessionId: String): StreamSessionLogs
    suspend fun streamSessionAnalyticsSummary(
        accessToken: String,
        profileId: String? = null,
        days: Int = 30
    ): StreamSessionAnalyticsSummary
    suspend fun streamChatSummary(accessToken: String, sessionId: String): StreamChatSummary
    suspend fun listFaqEntries(accessToken: String, profileId: String): FaqEntryList
    suspend fun saveFaqEntry(accessToken: String, request: FaqEntrySyncRequest): FaqEntryRecord
    suspend fun deleteFaqEntry(accessToken: String, faqEntryId: String)
    suspend fun suggestFaqReply(accessToken: String, request: FaqReplySuggestionRequest): FaqReplySuggestionResult
    suspend fun recordStreamMessage(accessToken: String, sessionId: String, request: ChatMessageLogSyncRequest): ChatMessageLogRecord
    suspend fun recordModerationActionLog(accessToken: String, sessionId: String, request: ModerationActionLogSyncRequest): ModerationActionLogRecord
    suspend fun reviewModerationActionLog(
        accessToken: String,
        sessionId: String,
        actionId: String,
        request: ModerationActionReviewRequest
    ): ModerationActionLogRecord
    suspend fun recordRuntimeEvent(accessToken: String, sessionId: String, request: RuntimeEventSyncRequest): RuntimeEventRecord
    suspend fun saveCommand(accessToken: String, request: CommandSyncRequest): CommandSyncResult
    suspend fun sendCommandToLiveChat(accessToken: String, commandId: String, request: CommandManualSendRequest): CommandManualSendResult
    suspend fun deleteCommand(accessToken: String, commandId: String)
    suspend fun listUserProfiles(accessToken: String, profileId: String? = null): UserProfileList
    suspend fun warnUser(accessToken: String, request: UserWarningRequest): UserWarningResult
    suspend fun updateUserProfileNotes(accessToken: String, userProfileId: String, request: UserProfileNotesRequest): UserProfileRecord
    suspend fun hideUserProfile(accessToken: String, userProfileId: String, request: UserHideRequest): UserHideResult
    suspend fun timeoutUserProfile(accessToken: String, userProfileId: String, request: UserTimeoutRequest): UserTimeoutResult
    suspend fun unbanUserProfile(accessToken: String, userProfileId: String, request: UserUnbanRequest): UserUnbanResult
    suspend fun whitelistUserProfile(
        accessToken: String,
        userProfileId: String,
        request: UserWhitelistRequest = UserWhitelistRequest()
    ): UserWhitelistResult
    suspend fun saveTimer(accessToken: String, request: TimerSyncRequest): TimerSyncResult
    suspend fun deleteTimer(accessToken: String, timerId: String)
}

data class AppCompatibility(
    val platform: String,
    val currentVersionName: String,
    val currentVersionCode: Int,
    val minimumSupportedVersionName: String,
    val minimumSupportedVersionCode: Int,
    val latestVersionName: String,
    val latestVersionCode: Int,
    val status: String,
    val updateRequired: Boolean,
    val updateRecommended: Boolean,
    val message: String,
    val downloadUrl: String?
)

data class DeviceSessionRequest(
    val deviceId: String,
    val installId: String,
    val appVersion: String
)

data class DeviceSession(
    val accessToken: String,
    val tokenType: String,
    val expiresInSeconds: Long
)

data class EntitlementSnapshot(
    val plan: String,
    val status: String,
    val source: String,
    val productId: String?,
    val currentPeriodEndsAt: String?,
    val features: Map<String, Any?>
)

data class ModerationSuggestionResult(
    val provider: String,
    val manualApprovalRequired: Boolean,
    val suggestedAction: String,
    val classification: List<String>,
    val confidence: Double,
    val confidenceThreshold: Double,
    val reasons: List<ModerationSuggestionReason>,
    val explanation: String,
    val usage: ModerationSuggestionUsage? = null
)

data class ModerationSuggestionUsage(
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val resetAt: String
)

data class ModerationSuggestionReason(
    val code: String,
    val label: String,
    val detail: String,
    val confidence: Double
)

data class GooglePlayPurchaseValidationRequest(
    val productId: String,
    val purchaseToken: String,
    val packageName: String? = null
)

data class GooglePlayPurchaseValidationResult(
    val entitlement: EntitlementSnapshot,
    val validationStatus: String
)

data class ChannelProfileList(
    val profiles: List<ChannelProfileRecord>
)

data class ChannelProfileRecord(
    val id: String,
    val channelId: String,
    val name: String,
    val config: Map<String, Any?>,
    val createdAt: String,
    val updatedAt: String
)

data class ChannelProfileCreateRequest(
    val channelId: String,
    val name: String,
    val config: Map<String, Any?> = emptyMap()
)

data class ProfileBackupRequest(
    val profileId: String,
    val channelId: String,
    val profileName: String,
    val config: Map<String, Any?>,
    val clientVersion: String
)

data class ProfileBackup(
    val id: String,
    val profileName: String = "",
    val version: Int,
    val createdAt: String
)

data class SettingsBackupRequest(
    val profileId: String,
    val channelId: String,
    val profileName: String,
    val commands: List<SettingsBackupCommand>,
    val timers: List<SettingsBackupTimer>,
    val clientVersion: String
)

data class SettingsBackupCommand(
    val id: String?,
    val name: String,
    val response: String,
    val aliases: List<String>,
    val cooldownSeconds: Int,
    val accessLevel: String,
    val enabled: Boolean
)

data class SettingsBackupTimer(
    val id: String?,
    val name: String,
    val message: String,
    val intervalMinutes: Int,
    val minChatMessages: Int,
    val quietStartMinutes: Int? = null,
    val quietEndMinutes: Int? = null,
    val enabled: Boolean
)

data class SettingsBackupResult(
    val id: String,
    val profileName: String,
    val version: Int,
    val commandCount: Int,
    val timerCount: Int,
    val createdAt: String
)

data class SettingsRestoreResult(
    val restoredAt: String,
    val backupId: String,
    val profileId: String,
    val commands: List<SettingsBackupCommand>,
    val timers: List<SettingsBackupTimer>
)

data class BackupList(
    val backups: List<CloudBackup>
)

data class CloudBackup(
    val id: String,
    val profileName: String,
    val channelId: String,
    val version: Int,
    val clientVersion: String?,
    val createdAt: String
)

data class AccountExportSummary(
    val exportedAt: String,
    val profileCount: Int,
    val backupCount: Int,
    val linkedAccountCount: Int,
    val linkedAccounts: List<LinkedAccountSummary> = emptyList(),
    val supportEventCount: Int,
    val auditLogCount: Int
)

data class LinkedAccountSummary(
    val provider: String,
    val providerAccountId: String,
    val channelId: String?,
    val channelTitle: String?,
    val tokenExpiresAt: String?
)

data class YouTubeConnectUrl(
    val url: String?,
    val configured: Boolean,
    val requiredScopes: List<String>,
    val missingEnv: List<String>,
    val note: String?
)

data class YouTubeAccountStatus(
    val configured: Boolean,
    val source: String,
    val account: YouTubeLinkedAccountStatus
)

data class YouTubeLinkedAccountStatus(
    val connected: Boolean,
    val linkedAccountId: String?,
    val channelId: String?,
    val channelTitle: String?,
    val hasAccessToken: Boolean,
    val hasRefreshToken: Boolean,
    val tokenExpiresAt: String?
)

data class YouTubeDisconnectResult(
    val disconnected: Boolean,
    val removedAccounts: Int,
    val revocationAttempted: Boolean,
    val revokedTokens: Int,
    val revocationFailures: Int
)

data class DiscordWebhookConfig(
    val profileId: String,
    val configured: Boolean,
    val enabled: Boolean,
    val alertModerationActions: Boolean,
    val alertRuntimeStatus: Boolean,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class DiscordWebhookUpsertRequest(
    val profileId: String,
    val webhookUrl: String? = null,
    val enabled: Boolean,
    val alertModerationActions: Boolean,
    val alertRuntimeStatus: Boolean
)

data class DiscordAlertRequest(
    val profileId: String,
    val eventType: String,
    val title: String,
    val detail: String,
    val severity: String = "info",
    val metadata: Map<String, Any?> = emptyMap()
)

data class DiscordAlertResult(
    val sent: Boolean,
    val skippedReason: String? = null,
    val sentAt: String? = null,
    val profileId: String
)

data class OverlayConfig(
    val profileId: String,
    val configured: Boolean,
    val enabled: Boolean,
    val theme: String,
    val activeSessionId: String?,
    val showModerationActions: Boolean,
    val showRuntimeStatus: Boolean,
    val showViewerStats: Boolean,
    val showRecentChat: Boolean,
    val tokenPreview: String? = null,
    val publicUrl: String? = null,
    val publicPath: String? = null,
    val tokenRotated: Boolean = false,
    val allowed: Boolean? = null,
    val requiredPlan: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class OverlayConfigUpdateRequest(
    val profileId: String,
    val enabled: Boolean,
    val theme: String = "control_room",
    val activeSessionId: String? = null,
    val showModerationActions: Boolean = true,
    val showRuntimeStatus: Boolean = true,
    val showViewerStats: Boolean = true,
    val showRecentChat: Boolean = false
)

typealias OverlayConfigMutationResult = OverlayConfig

data class TeamMemberPermissions(
    val viewQueue: Boolean = true,
    val moderate: Boolean = true,
    val manageWarnings: Boolean = true,
    val viewAnalytics: Boolean = false
)

data class TeamMemberRecord(
    val id: String,
    val profileId: String,
    val displayName: String,
    val role: String,
    val status: String,
    val inviteCodePreview: String,
    val memberDeviceId: String?,
    val permissions: TeamMemberPermissions,
    val acceptedAt: String?,
    val revokedAt: String?,
    val createdAt: String,
    val updatedAt: String
)

data class TeamMemberList(
    val profileId: String,
    val teamSeats: Int,
    val extraSeats: Int,
    val members: List<TeamMemberRecord>
)

data class TeamInviteCreateRequest(
    val displayName: String,
    val role: String = "moderator",
    val permissions: TeamMemberPermissions = TeamMemberPermissions()
)

data class TeamInviteResult(
    val member: TeamMemberRecord,
    val inviteCode: String
)

data class TeamInviteRedeemRequest(
    val inviteCode: String,
    val displayName: String? = null
)

data class TeamMembershipRecord(
    val member: TeamMemberRecord,
    val profileName: String,
    val channelId: String,
    val ownerDeviceId: String
)

data class TeamMembershipList(
    val memberships: List<TeamMembershipRecord>
)

data class YouTubeBroadcastList(
    val broadcasts: List<YouTubeBroadcast>,
    val source: String
)

data class YouTubeLiveChatDiscovery(
    val activeChat: YouTubeActiveChat?,
    val broadcasts: List<YouTubeBroadcast>,
    val activeBroadcastCount: Int,
    val needsSelection: Boolean,
    val status: String,
    val source: String
)

data class YouTubeActiveChat(
    val liveChatId: String,
    val videoId: String
)

data class YouTubeBroadcast(
    val videoId: String,
    val liveChatId: String?,
    val title: String,
    val status: String,
    val scheduledStartTime: String?,
    val actualStartTime: String?
)

data class YouTubeLiveChatMessagesRequest(
    val liveChatId: String,
    val pageToken: String? = null
)

data class YouTubeLiveChatMessagePage(
    val messages: List<YouTubeLiveChatMessageRecord>,
    val nextPageToken: String?,
    val pollingIntervalMillis: Long,
    val source: String
)

data class YouTubeLiveChatMessageRecord(
    val id: String,
    val authorChannelId: String,
    val authorName: String,
    val text: String,
    val publishedAt: String,
    val messageType: String,
    val isOwner: Boolean = false,
    val isModerator: Boolean = false,
    val isMember: Boolean = false,
    val isVerified: Boolean = false,
    val purchaseAmountMicros: String? = null,
    val purchaseCurrency: String? = null,
    val targetMessageId: String? = null,
    val targetChannelId: String? = null
)

data class YouTubeMessageSendRequest(
    val liveChatId: String,
    val text: String
)

data class YouTubeMessageSendResult(
    val messageId: String,
    val liveChatId: String,
    val sentAt: String
)

data class YouTubeTestMessageRequest(
    val liveChatId: String,
    val text: String
)

data class YouTubeTestMessageResult(
    val messageId: String
)

data class YouTubeMessageDeleteRequest(
    val messageId: String,
    val reason: String = "manual_queue_delete"
)

data class YouTubeMessageDeleteResult(
    val messageId: String,
    val actionType: String,
    val reason: String,
    val deletedAt: String
)

data class YouTubeUserHideRequest(
    val liveChatId: String,
    val authorChannelId: String,
    val durationSeconds: Int? = null,
    val reason: String = "runtime_rule_action"
)

data class YouTubeUserHideResult(
    val liveChatId: String,
    val authorChannelId: String,
    val liveChatBanId: String?,
    val actionType: String,
    val durationSeconds: Int?,
    val reason: String,
    val actedAt: String
)

data class YouTubeUserUnbanRequest(
    val liveChatBanId: String,
    val reason: String = "manual_unban"
)

data class YouTubeUserUnbanResult(
    val liveChatBanId: String,
    val actionType: String,
    val reason: String,
    val actedAt: String
)

data class AccountDeletionResult(
    val deleted: Boolean,
    val userId: String?,
    val deviceIds: List<String>,
    val supportEventsDeleted: Int,
    val auditLogsDeleted: Int,
    val apiErrorsDeleted: Int
)

data class SupportEventRequest(
    val severity: String,
    val message: String,
    val details: Map<String, Any?> = emptyMap()
)

data class SupportEventList(
    val events: List<SupportEventRecord>
)

data class ApiErrorList(
    val errors: List<ApiErrorRecord>
)

data class ApiErrorRecord(
    val id: String,
    val provider: String,
    val code: String?,
    val message: String,
    val metadata: Map<String, Any?>?,
    val createdAt: String
)

data class SupportEventRecord(
    val id: String,
    val severity: String,
    val message: String,
    val details: Map<String, Any?>?,
    val createdAt: String
)

data class AnalyticsEventRequest(
    val name: String,
    val consent: Boolean = true,
    val occurredAt: String? = null,
    val appVersion: String? = null,
    val platform: String = "android",
    val properties: Map<String, Any?> = emptyMap()
)

data class AnalyticsEventRecord(
    val id: String,
    val name: String,
    val occurredAt: String,
    val appVersion: String?,
    val platform: String,
    val properties: Map<String, Any?>,
    val createdAt: String
)

data class BetaFeedbackRequest(
    val category: String,
    val message: String,
    val occurredAt: String? = null,
    val appVersion: String? = null,
    val platform: String = "android",
    val context: Map<String, Any?> = emptyMap()
)

data class BetaFeedbackList(
    val feedback: List<BetaFeedbackRecord>
)

data class BetaFeedbackRecord(
    val id: String,
    val category: String,
    val message: String,
    val occurredAt: String,
    val appVersion: String?,
    val platform: String,
    val context: Map<String, Any?>,
    val createdAt: String
)

data class RulePresetList(
    val rulePresets: List<RulePresetRecord>
)

data class RulePresetTemplateList(
    val rulePresetTemplates: List<RulePresetTemplateRecord>
)

data class RulePresetTemplateRecord(
    val id: String,
    val name: String,
    val description: String,
    val config: ModerationProfile
)

data class RulePresetRecord(
    val id: String,
    val profileId: String,
    val name: String,
    val config: ModerationProfile,
    val isDefault: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class RulePresetExportBundle(
    val formatVersion: Int,
    val exportedAt: String,
    val profileId: String,
    val rulePresets: List<RulePresetRecord>
)

data class RulePresetImportRequest(
    val profileId: String,
    val bundle: RulePresetExportBundle
)

data class RulePresetImportResult(
    val importedAt: String,
    val profileId: String,
    val importedCount: Int,
    val rulePresets: List<RulePresetRecord>
)

data class RulePresetSyncRequest(
    val id: String,
    val profileId: String,
    val name: String,
    val config: ModerationProfile,
    val isDefault: Boolean
)

data class StreamSessionSyncRequest(
    val profileId: String,
    val videoId: String,
    val liveChatId: String,
    val title: String?,
    val startedAt: String? = null
)

data class StreamSessionRecord(
    val id: String,
    val profileId: String,
    val videoId: String,
    val liveChatId: String,
    val title: String?,
    val startedAt: String,
    val endedAt: String?
)

data class StreamSessionLogs(
    val session: StreamSessionRecord,
    val messages: List<ChatMessageLogRecord>,
    val actions: List<ModerationActionLogRecord>,
    val runtimeEvents: List<RuntimeEventRecord>
)

data class StreamSessionAnalyticsSummary(
    val generatedAt: String,
    val rangeDays: Int,
    val sessionCount: Int,
    val totalMessages: Int,
    val totalModerationActions: Int,
    val totalRuntimeEvents: Int,
    val totalUptimeMillis: Long,
    val reconnectEvents: Int,
    val byStream: List<StreamAnalyticsByStream>,
    val byDay: List<StreamAnalyticsByDay>,
    val topChatters: List<StreamAnalyticsChatter>,
    val commandUsage: List<StreamAnalyticsCommand>,
    val ruleEffectiveness: List<StreamAnalyticsRule>,
    val ruleEffectivenessByPreset: List<StreamAnalyticsRulePreset>,
    val spamAttemptsByDay: List<StreamAnalyticsSpamDay>,
    val uptimeByStream: List<StreamAnalyticsUptime>
)

data class StreamChatSummary(
    val provider: String,
    val sessionId: String,
    val generatedAt: String,
    val title: String?,
    val summary: String,
    val highlights: List<String>,
    val topQuestions: List<StreamChatSummaryQuestion>,
    val topChatters: List<StreamChatSummaryChatter>,
    val moderationNotes: List<String>,
    val suggestedFollowUps: List<String>,
    val stats: StreamChatSummaryStats
)

data class StreamChatSummaryQuestion(
    val question: String,
    val count: Int
)

data class StreamChatSummaryChatter(
    val authorChannelId: String,
    val authorName: String,
    val messageCount: Int
)

data class StreamChatSummaryStats(
    val messageCount: Int,
    val uniqueChatters: Int,
    val moderationActionCount: Int,
    val destructiveActionCount: Int
)

data class FaqEntryList(
    val faqEntries: List<FaqEntryRecord>
)

data class FaqEntrySyncRequest(
    val id: String,
    val profileId: String,
    val question: String,
    val answer: String,
    val keywords: List<String>,
    val enabled: Boolean
)

data class FaqEntryRecord(
    val id: String,
    val profileId: String,
    val question: String,
    val answer: String,
    val keywords: List<String>,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class FaqReplySuggestionRequest(
    val profileId: String,
    val messageText: String,
    val authorName: String? = null,
    val minConfidence: Double = 0.45
)

data class FaqReplySuggestionResult(
    val provider: String,
    val matched: Boolean,
    val manualApprovalRequired: Boolean,
    val entryId: String?,
    val question: String?,
    val replyText: String?,
    val confidence: Double,
    val matchedKeywords: List<String>,
    val explanation: String
)

data class StreamAnalyticsByStream(
    val sessionId: String,
    val title: String?,
    val videoId: String,
    val startedAt: String,
    val endedAt: String?,
    val messageCount: Int,
    val uniqueChatters: Int,
    val moderationActionCount: Int,
    val destructiveActionCount: Int,
    val spamAttemptCount: Int,
    val commandCount: Int,
    val timerCount: Int,
    val reconnectEvents: Int,
    val uptimeMillis: Long
)

data class StreamAnalyticsByDay(
    val day: String,
    val streamCount: Int,
    val messageCount: Int,
    val moderationActionCount: Int,
    val spamAttemptCount: Int,
    val reconnectEvents: Int,
    val uptimeMillis: Long
)

data class StreamAnalyticsChatter(
    val authorChannelId: String,
    val authorName: String,
    val messageCount: Int,
    val moderationActionCount: Int
)

data class StreamAnalyticsCommand(
    val commandId: String,
    val trigger: String?,
    val count: Int
)

data class StreamAnalyticsRule(
    val rule: String,
    val matchCount: Int,
    val destructiveActionCount: Int,
    val falsePositiveCount: Int
)

data class StreamAnalyticsRulePreset(
    val presetId: String,
    val presetName: String?,
    val presetVersion: String?,
    val rule: String,
    val matchCount: Int,
    val destructiveActionCount: Int,
    val falsePositiveCount: Int
)

data class StreamAnalyticsSpamDay(
    val day: String,
    val count: Int
)

data class StreamAnalyticsUptime(
    val sessionId: String,
    val title: String?,
    val uptimeMillis: Long,
    val reconnectEvents: Int
)

data class ChatMessageLogSyncRequest(
    val youtubeMessageId: String,
    val authorChannelId: String,
    val authorName: String,
    val text: String,
    val receivedAt: String? = null
)

data class ChatMessageLogRecord(
    val id: String,
    val sessionId: String,
    val youtubeMessageId: String,
    val authorChannelId: String,
    val authorName: String,
    val text: String,
    val receivedAt: String,
    val createdAt: String
)

data class ModerationActionLogSyncRequest(
    val clientActionId: String? = null,
    val youtubeMessageId: String?,
    val authorChannelId: String?,
    val actionType: String,
    val reason: String,
    val confidence: Double?,
    val metadata: Map<String, Any?> = emptyMap()
)

data class ModerationActionLogRecord(
    val id: String,
    val sessionId: String,
    val youtubeMessageId: String?,
    val authorChannelId: String?,
    val actionType: String,
    val reason: String,
    val confidence: Double?,
    val metadata: Map<String, Any?>? = null,
    val reviewStatus: String? = null,
    val reviewedAt: String? = null,
    val reviewNote: String? = null,
    val createdAt: String
)

data class ModerationActionReviewRequest(
    val reviewStatus: String,
    val reviewNote: String? = null
)

data class RuntimeEventSyncRequest(
    val type: String,
    val message: String,
    val metadata: Map<String, Any?> = emptyMap()
)

data class RuntimeEventRecord(
    val id: String,
    val sessionId: String,
    val type: String,
    val message: String,
    val metadata: Map<String, Any?>?,
    val createdAt: String
)

data class CommandSyncRequest(
    val id: String,
    val profileId: String,
    val name: String,
    val response: String,
    val aliases: List<String>,
    val cooldownSeconds: Int,
    val accessLevel: String,
    val enabled: Boolean
)

data class CommandSyncResult(
    val id: String,
    val syncedAt: String
)

data class CommandManualSendRequest(
    val liveChatId: String,
    val streamTitle: String? = null,
    val streamStartedAt: String? = null
)

data class CommandManualSendResult(
    val commandId: String,
    val commandName: String,
    val liveChatId: String,
    val messageId: String,
    val sentText: String,
    val sentAt: String
)

data class UserWarningRequest(
    val profileId: String,
    val authorChannelId: String,
    val displayName: String,
    val reason: String,
    val profileImageUrl: String? = null,
    val liveChatId: String? = null,
    val warningText: String? = null
)

data class UserWarningResult(
    val user: UserProfileRecord,
    val strike: UserStrikeRecord,
    val messageId: String?,
    val warnedAt: String
)

data class UserProfileList(
    val users: List<UserProfileRecord>
)

data class UserProfileRecord(
    val id: String,
    val profileId: String,
    val authorChannelId: String,
    val displayName: String,
    val profileImageUrl: String?,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val messageCount: Int,
    val notes: String?,
    val strikeCount: Int,
    val recentStrikes: List<UserStrikeRecord>,
    val recentModerationActions: List<UserModerationActionRecord>
)

data class UserProfileNotesRequest(
    val notes: String?
)

data class UserHideRequest(
    val liveChatId: String,
    val reason: String = "manual_profile_action"
)

data class UserHideResult(
    val user: UserProfileRecord,
    val action: UserModerationActionRecord?,
    val liveChatId: String,
    val liveChatBanId: String?,
    val actionType: String,
    val reason: String,
    val hiddenAt: String
)

data class UserTimeoutRequest(
    val liveChatId: String,
    val durationSeconds: Int = 300,
    val reason: String = "manual_profile_timeout"
)

data class UserTimeoutResult(
    val user: UserProfileRecord,
    val action: UserModerationActionRecord?,
    val liveChatId: String,
    val liveChatBanId: String?,
    val durationSeconds: Int,
    val actionType: String,
    val reason: String,
    val timedOutAt: String,
    val timedOutUntil: String
)

data class UserUnbanRequest(
    val liveChatBanId: String,
    val liveChatId: String? = null,
    val reason: String = "manual_profile_unban"
)

data class UserUnbanResult(
    val user: UserProfileRecord,
    val action: UserModerationActionRecord?,
    val liveChatBanId: String,
    val actionType: String,
    val reason: String,
    val unbannedAt: String
)

data class UserWhitelistResult(
    val user: UserProfileRecord,
    val whitelist: UserWhitelistRecord,
    val whitelistedAt: String
)

data class UserWhitelistRequest(
    val durationSeconds: Int? = null
)

data class UserWhitelistRecord(
    val id: String,
    val profileId: String,
    val authorChannelId: String,
    val displayName: String?,
    val temporaryUntil: String?,
    val createdAt: String
)

data class UserStrikeRecord(
    val id: String,
    val userProfileId: String,
    val reason: String,
    val createdAt: String
)

data class UserModerationActionRecord(
    val id: String,
    val userProfileId: String,
    val actionType: String,
    val liveChatId: String?,
    val liveChatBanId: String?,
    val reason: String,
    val durationSeconds: Int?,
    val createdAt: String,
    val expiresAt: String?
)

data class TimerSyncRequest(
    val id: String,
    val profileId: String,
    val name: String,
    val message: String,
    val intervalMinutes: Int,
    val minChatMessages: Int,
    val quietStartMinutes: Int? = null,
    val quietEndMinutes: Int? = null,
    val enabled: Boolean
)

data class TimerSyncResult(
    val id: String,
    val syncedAt: String
)
