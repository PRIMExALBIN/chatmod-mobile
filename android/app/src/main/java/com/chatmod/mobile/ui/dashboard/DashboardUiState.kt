package com.chatmod.mobile.ui.dashboard

data class DashboardUiState(
    val botRunning: Boolean = false,
    val streamTitle: String = "No active stream connected",
    val channelName: String = "Connect YouTube",
    val liveChatId: String? = null,
    val videoId: String? = null,
    val queue: List<QueueItem> = emptyList(),
    val rules: List<RuleSummary> = emptyList(),
    val profiles: ChannelProfilePanelState = ChannelProfilePanelState(),
    val rulePresets: RulePresetPanelState = RulePresetPanelState(),
    val faq: FaqPanelState = FaqPanelState(),
    val commands: List<CommandSummary> = emptyList(),
    val timers: List<TimerSummary> = emptyList(),
    val recentActions: List<ActionLogItem> = emptyList(),
    val syncStatus: SyncStatus = SyncStatus.Ready,
    val selectedTab: DashboardTab = DashboardTab.Queue,
    val billing: BillingSummary = BillingSummary(),
    val account: AccountPanelState = AccountPanelState(),
    val support: SupportPanelState = SupportPanelState(),
    val settings: SettingsPanelState = SettingsPanelState(),
    val appCompatibility: AppCompatibilityState = AppCompatibilityState(),
    val streamSelector: StreamSelectorState = StreamSelectorState(),
    val userHistory: UserHistoryPanelState = UserHistoryPanelState(),
    val logs: LogPanelState = LogPanelState(),
    val commandEditor: CommandEditorState? = null,
    val timerEditor: TimerEditorState? = null,
    val moderationConfirmation: ModerationConfirmation? = null,
    val pendingRuntimeRecovery: RuntimeRecoveryRequest? = null
)

data class RuntimeRecoveryRequest(
    val id: String
)

data class QueueItem(
    val id: String,
    val author: String,
    val authorChannelId: String,
    val message: String,
    val reason: String,
    val severity: Severity,
    val youtubeMessageId: String? = null,
    val profileImageUrl: String? = null,
    val assistantSuggestion: ModerationSuggestionSummary? = null,
    val faqSuggestion: FaqReplySuggestionSummary? = null,
    val isSuggestionLoading: Boolean = false
)

data class ModerationSuggestionSummary(
    val suggestedAction: String,
    val classification: List<String>,
    val confidencePercent: Int,
    val explanation: String,
    val topReason: String,
    val manualApprovalRequired: Boolean
)

data class FaqReplySuggestionSummary(
    val question: String,
    val replyText: String,
    val confidencePercent: Int,
    val explanation: String,
    val manualApprovalRequired: Boolean
)

data class RuleSummary(
    val name: String,
    val enabled: Boolean,
    val detail: String
)

data class ChannelProfilePanelState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val selectedProfileId: String? = null,
    val profiles: List<ChannelProfileSummary> = emptyList(),
    val createNameText: String = "",
    val createChannelIdText: String = "",
    val createErrorMessage: String? = null
)

data class ChannelProfileSummary(
    val id: String,
    val channelId: String,
    val name: String,
    val updatedAt: String
)

data class RulePresetPanelState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val selectedPresetId: String? = null,
    val importDialog: RulePresetImportDialogState? = null,
    val pendingShare: RulePresetShareRequest? = null,
    val templates: List<RulePresetTemplateSummary> = emptyList(),
    val presets: List<RulePresetSummary> = emptyList()
)

data class RulePresetImportDialogState(
    val bundleJson: String = "",
    val errorMessage: String? = null
)

data class RulePresetShareRequest(
    val id: String,
    val subject: String,
    val text: String
)

data class RulePresetTemplateSummary(
    val id: String,
    val name: String,
    val detail: String
)

data class RulePresetSummary(
    val id: String,
    val name: String,
    val detail: String,
    val isDefault: Boolean,
    val updatedAt: String
)

data class FaqPanelState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val entries: List<FaqEntrySummary> = emptyList(),
    val editingId: String? = null,
    val questionText: String = "",
    val answerText: String = "",
    val keywordsText: String = "",
    val enabled: Boolean = true,
    val errorMessage: String? = null
)

data class FaqEntrySummary(
    val id: String,
    val question: String,
    val answer: String,
    val keywords: List<String>,
    val enabled: Boolean
)

data class CommandSummary(
    val id: String,
    val name: String,
    val response: String,
    val aliases: List<String>,
    val cooldownSeconds: Int,
    val accessLevel: CommandAccessLevel,
    val enabled: Boolean
)

data class TimerSummary(
    val id: String,
    val name: String,
    val message: String,
    val intervalMinutes: Int,
    val minChatMessages: Int,
    val quietStartMinutes: Int? = null,
    val quietEndMinutes: Int? = null,
    val enabled: Boolean
)

data class CommandEditorState(
    val mode: EditorMode,
    val id: String? = null,
    val name: String = "!",
    val response: String = "",
    val aliasesText: String = "",
    val cooldownSecondsText: String = "30",
    val accessLevel: CommandAccessLevel = CommandAccessLevel.Everyone,
    val enabled: Boolean = true,
    val errorMessage: String? = null
)

data class TimerEditorState(
    val mode: EditorMode,
    val id: String? = null,
    val name: String = "",
    val message: String = "",
    val intervalMinutesText: String = "15",
    val minChatMessagesText: String = "0",
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutesText: String = "",
    val quietEndMinutesText: String = "",
    val enabled: Boolean = true,
    val errorMessage: String? = null
)

data class ActionLogItem(
    val id: String,
    val label: String,
    val detail: String
)

data class BillingSummary(
    val plan: String = "starter",
    val status: String = "trialing",
    val source: String = "demo",
    val productId: String? = null,
    val currentPeriodEndsAt: String? = null,
    val channelProfiles: Int = 1,
    val commandProfiles: String = "3",
    val timedMessages: String = "5",
    val localHistoryLimit: Int = StarterLocalHistoryLimit,
    val cloudBackups: Boolean = true,
    val emergencyMode: Boolean = false,
    val advancedFilters: Boolean = false,
    val presetBundles: Boolean = false,
    val obsOverlay: Boolean = false,
    val aiSuggestions: Boolean = false,
    val aiSuggestionDailyLimit: Int = 0
)

data class AccountPanelState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val lastExport: AccountExportReceipt? = null,
    val youtubeConnect: YouTubeConnectState = YouTubeConnectState(),
    val pendingBrowserLaunch: BrowserLaunchRequest? = null,
    val cloudBackups: List<CloudBackupSummary> = emptyList(),
    val pendingConfirmation: AccountConfirmation? = null
)

data class YouTubeConnectState(
    val configured: Boolean? = null,
    val authUrlAvailable: Boolean = false,
    val requiredScopes: List<String> = emptyList(),
    val missingEnv: List<String> = emptyList(),
    val note: String? = null
)

data class BrowserLaunchRequest(
    val id: String,
    val url: String
)

data class AccountExportReceipt(
    val exportedAt: String,
    val profileCount: Int,
    val backupCount: Int,
    val linkedAccountCount: Int,
    val linkedAccounts: List<LinkedAccountReceipt> = emptyList(),
    val supportEventCount: Int,
    val auditLogCount: Int
)

data class LinkedAccountReceipt(
    val provider: String,
    val providerAccountId: String,
    val channelId: String?,
    val channelTitle: String?,
    val tokenExpiresAt: String?
)

data class CloudBackupSummary(
    val id: String,
    val profileName: String,
    val channelId: String,
    val version: Int,
    val clientVersion: String?,
    val createdAt: String
)

data class SupportPanelState(
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val events: List<SupportEventSummary> = emptyList(),
    val apiErrors: List<ApiErrorSummary> = emptyList(),
    val feedbackCategory: BetaFeedbackCategory = BetaFeedbackCategory.Bug,
    val feedbackMessage: String = "",
    val feedbackStatusMessage: String? = null,
    val feedback: List<BetaFeedbackSummary> = emptyList()
)

data class SupportEventSummary(
    val id: String,
    val severity: String,
    val message: String,
    val createdAt: String
)

data class ApiErrorSummary(
    val id: String,
    val provider: String,
    val code: String?,
    val message: String,
    val requestId: String?,
    val createdAt: String
)

data class BetaFeedbackSummary(
    val id: String,
    val category: String,
    val message: String,
    val createdAt: String
)

data class SettingsPanelState(
    val selectedProfileId: String? = null,
    val emergencyMode: Boolean = false,
    val linkLockdown: Boolean = false,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
    val lowDataMode: Boolean = false,
    val shareUsageAnalytics: Boolean = false,
    val batteryOptimizationIgnored: Boolean? = null,
    val statusMessage: String? = null,
    val discordWebhookConfigured: Boolean = false,
    val discordWebhookEnabled: Boolean = false,
    val discordWebhookUrlText: String = "",
    val discordAlertModerationActions: Boolean = true,
    val discordAlertRuntimeStatus: Boolean = false,
    val discordStatusMessage: String? = null,
    val isDiscordBusy: Boolean = false,
    val overlayConfigured: Boolean = false,
    val overlayEnabled: Boolean = false,
    val overlayTheme: String = "control_room",
    val overlayShowModerationActions: Boolean = true,
    val overlayShowRuntimeStatus: Boolean = true,
    val overlayShowViewerStats: Boolean = true,
    val overlayShowRecentChat: Boolean = false,
    val overlayTokenPreview: String? = null,
    val overlayPublicUrl: String? = null,
    val overlayAllowed: Boolean? = null,
    val overlayRequiredPlan: String? = null,
    val overlayStatusMessage: String? = null,
    val isOverlayBusy: Boolean = false,
    val teamSeats: Int = 1,
    val teamExtraSeats: Int = 0,
    val teamMembers: List<TeamMemberSummary> = emptyList(),
    val teamMemberships: List<TeamMembershipSummary> = emptyList(),
    val teamInviteNameText: String = "",
    val teamRedeemCodeText: String = "",
    val teamLastInviteCode: String? = null,
    val teamStatusMessage: String? = null,
    val isTeamBusy: Boolean = false
)

data class TeamMemberSummary(
    val id: String,
    val displayName: String,
    val role: String,
    val status: String,
    val inviteCodePreview: String,
    val acceptedAt: String?,
    val revokedAt: String?
)

data class TeamMembershipSummary(
    val id: String,
    val profileId: String,
    val profileName: String,
    val channelId: String,
    val role: String,
    val status: String
)

data class AppCompatibilityState(
    val checked: Boolean = false,
    val currentVersionName: String = "",
    val currentVersionCode: Int = 0,
    val latestVersionName: String? = null,
    val minimumSupportedVersionName: String? = null,
    val status: String = "checking",
    val updateRequired: Boolean = false,
    val updateRecommended: Boolean = false,
    val message: String? = null,
    val downloadUrl: String? = null
)

data class StreamSelectorState(
    val channelId: String = "demo-channel",
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val source: String = "demo",
    val discoveryStatus: String = "idle",
    val activeBroadcastCount: Int = 0,
    val needsSelection: Boolean = false,
    val connectedChannelId: String? = null,
    val connectedChannelTitle: String? = null,
    val channelMismatch: Boolean = false,
    val testMessage: String = "ChatMod Mobile test message",
    val isTestingConnection: Boolean = false,
    val testStatusMessage: String? = null,
    val lastTestMessageId: String? = null,
    val isCheckingModeratorPermissions: Boolean = false,
    val permissionCheckStatusMessage: String? = null,
    val lastPermissionCheckAt: String? = null,
    val broadcasts: List<StreamCandidateSummary> = emptyList()
)

data class StreamCandidateSummary(
    val videoId: String,
    val liveChatId: String?,
    val title: String,
    val status: String,
    val scheduledStartTime: String?,
    val actualStartTime: String?
)

data class UserHistoryPanelState(
    val users: List<UserHistorySummary> = emptyList(),
    val localHistoryLimit: Int = StarterLocalHistoryLimit,
    val availableLocalHistoryUsers: Int = 0,
    val warnedUsers: List<UserWarningHistorySummary> = emptyList(),
    val isLoadingWarnings: Boolean = false,
    val statusMessage: String? = null,
    val warningStatusMessage: String? = null,
    val selectedProfile: UserProfileDrawerState? = null
)

data class UserHistorySummary(
    val channelId: String,
    val displayName: String,
    val profileImageUrl: String?,
    val messageCount: Int,
    val moderationActionCount: Int,
    val destructiveActionCount: Int,
    val warningActionCount: Int,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val lastMessagePreview: String?,
    val severity: Severity
)

data class UserWarningHistorySummary(
    val id: String,
    val channelId: String,
    val displayName: String,
    val profileImageUrl: String?,
    val messageCount: Int,
    val strikeCount: Int,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val notes: String?,
    val recentStrikes: List<UserStrikeHistorySummary>,
    val recentModerationActions: List<UserModerationActionHistorySummary>
)

data class UserProfileDrawerState(
    val id: String,
    val channelId: String,
    val displayName: String,
    val profileImageUrl: String?,
    val messageCount: Int,
    val strikeCount: Int,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val notesText: String,
    val recentStrikes: List<UserStrikeHistorySummary>,
    val recentModerationActions: List<UserModerationActionHistorySummary>,
    val isSavingNotes: Boolean = false,
    val isHidingUser: Boolean = false,
    val isTimingOutUser: Boolean = false,
    val isUnbanningUser: Boolean = false,
    val isWhitelistingUser: Boolean = false,
    val statusMessage: String? = null
)

data class UserStrikeHistorySummary(
    val id: String,
    val reason: String,
    val createdAt: String
)

data class UserModerationActionHistorySummary(
    val id: String,
    val actionType: String,
    val liveChatId: String?,
    val liveChatBanId: String?,
    val reason: String,
    val durationSeconds: Int?,
    val createdAt: String,
    val expiresAt: String?
)

data class LogPanelState(
    val entries: List<LogEntrySummary> = emptyList(),
    val filter: LogEntryFilter = LogEntryFilter.All,
    val statusMessage: String? = null,
    val localHistoryLimit: Int = StarterLocalHistoryLimit,
    val availableLocalHistoryEntries: Int = 0,
    val proAnalytics: ProAnalyticsPanelState = ProAnalyticsPanelState(),
    val aiChatSummary: AiChatSummaryPanelState = AiChatSummaryPanelState(),
    val pendingShare: LogExportShareRequest? = null
)

data class ProAnalyticsPanelState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val generatedAt: String? = null,
    val rangeDays: Int = 30,
    val sessionCount: Int = 0,
    val totalMessages: Int = 0,
    val totalModerationActions: Int = 0,
    val totalRuntimeEvents: Int = 0,
    val totalUptimeMillis: Long = 0L,
    val reconnectEvents: Int = 0,
    val dayTrends: List<ProAnalyticsDaySummary> = emptyList(),
    val audienceTrends: List<ProAnalyticsAudienceSummary> = emptyList(),
    val commandTrends: List<ProAnalyticsCommandSummary> = emptyList(),
    val ruleTrends: List<ProAnalyticsRuleSummary> = emptyList(),
    val rulePresetTrends: List<ProAnalyticsRulePresetSummary> = emptyList(),
    val streamUptime: List<ProAnalyticsUptimeSummary> = emptyList()
)

data class ProAnalyticsDaySummary(
    val day: String,
    val messageCount: Int,
    val moderationActionCount: Int,
    val spamAttemptCount: Int,
    val reconnectEvents: Int
)

data class ProAnalyticsAudienceSummary(
    val authorChannelId: String,
    val authorName: String,
    val messageCount: Int,
    val moderationActionCount: Int
)

data class ProAnalyticsCommandSummary(
    val label: String,
    val count: Int
)

data class ProAnalyticsRuleSummary(
    val rule: String,
    val matchCount: Int,
    val destructiveActionCount: Int,
    val falsePositiveCount: Int
)

data class ProAnalyticsRulePresetSummary(
    val presetId: String,
    val presetName: String?,
    val presetVersion: String?,
    val rule: String,
    val matchCount: Int,
    val destructiveActionCount: Int,
    val falsePositiveCount: Int
)

data class ProAnalyticsUptimeSummary(
    val sessionId: String,
    val title: String?,
    val uptimeMillis: Long,
    val reconnectEvents: Int
)

data class AiChatSummaryPanelState(
    val isLoading: Boolean = false,
    val statusMessage: String? = "Load Pro trends to summarize the newest synced stream.",
    val sessionId: String? = null,
    val generatedAt: String? = null,
    val title: String? = null,
    val summary: String? = null,
    val highlights: List<String> = emptyList(),
    val topQuestions: List<AiChatSummaryQuestion> = emptyList(),
    val moderationNotes: List<String> = emptyList(),
    val suggestedFollowUps: List<String> = emptyList(),
    val messageCount: Int = 0,
    val uniqueChatters: Int = 0,
    val destructiveActionCount: Int = 0
)

data class AiChatSummaryQuestion(
    val question: String,
    val count: Int
)

data class LogExportShareRequest(
    val id: String,
    val subject: String,
    val text: String
)

data class LogEntrySummary(
    val id: String,
    val sessionId: String? = null,
    val kind: LogEntryKind,
    val title: String,
    val detail: String,
    val createdAtMillis: Long,
    val severity: Severity,
    val reviewCandidate: Boolean = false,
    val actionType: String? = null,
    val reason: String? = null,
    val subjectKey: String? = null,
    val subjectLabel: String? = null,
    val metadataJson: String? = null,
    val reviewStatus: String? = null,
    val reviewedAtMillis: Long? = null,
    val reviewNote: String? = null
)

data class RuleMatchSummary(
    val label: String,
    val count: Int,
    val severity: Severity
)

data class SessionModerationSummary(
    val timedOutOrHiddenUsers: Int,
    val timedOutUsers: Int,
    val hiddenUsers: Int
)

sealed class AccountConfirmation {
    object DisconnectYouTube : AccountConfirmation()
    object DeleteAccount : AccountConfirmation()
    object WipeLocalData : AccountConfirmation()
    data class DeleteBackup(val id: String, val label: String) : AccountConfirmation()
    data class RestoreBackup(val id: String, val label: String) : AccountConfirmation()
}

sealed class ModerationConfirmation {
    data class DeleteQueueMessage(val queueItemId: String, val author: String, val message: String) : ModerationConfirmation()
    data class TimeoutUser(val userProfileId: String, val displayName: String) : ModerationConfirmation()
    data class HideUser(val userProfileId: String, val displayName: String) : ModerationConfirmation()
    data class UnbanUser(
        val userProfileId: String,
        val displayName: String,
        val liveChatBanId: String,
        val liveChatId: String?
    ) : ModerationConfirmation()
}

enum class Severity {
    Info,
    Warning,
    Danger
}

enum class LogEntryKind(val label: String) {
    Chat("Chat"),
    RuleMatch("Rule match"),
    Moderation("Moderation"),
    Runtime("Runtime")
}

enum class LogEntryFilter(val label: String) {
    All("All"),
    Chat("Chat"),
    RuleMatch("Rules"),
    Moderation("Moderation"),
    Runtime("Runtime")
}

enum class SyncStatus {
    Ready,
    Syncing,
    Reconnecting,
    Offline,
    Failed
}

enum class DashboardTab {
    Queue,
    Feed,
    Users,
    Rules,
    Commands,
    Timers,
    Billing,
    Settings,
    Account,
    Support,
    Logs
}

enum class BetaFeedbackCategory(val label: String, val apiValue: String) {
    Bug("Bug", "bug"),
    Idea("Idea", "idea"),
    Confusing("Confusing", "confusing"),
    Pricing("Pricing", "pricing"),
    Other("Other", "other")
}

enum class CommandAccessLevel(val label: String) {
    Everyone("Everyone"),
    Members("Members"),
    Mods("Mods"),
    Owner("Owner")
}

enum class EditorMode {
    Create,
    Edit
}
