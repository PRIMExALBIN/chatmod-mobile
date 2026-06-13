package com.chatmod.mobile.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chatmod.mobile.BuildConfig
import com.chatmod.mobile.data.local.BotSettings
import com.chatmod.mobile.data.local.LastSelectedStreamState
import com.chatmod.mobile.data.local.LocalPrivacyStore
import com.chatmod.mobile.data.local.SettingsStore
import com.chatmod.mobile.data.remote.AccountExportSummary
import com.chatmod.mobile.data.remote.AppCompatibility
import com.chatmod.mobile.data.remote.ApiErrorRecord
import com.chatmod.mobile.data.remote.BetaFeedbackRecord
import com.chatmod.mobile.data.remote.BetaFeedbackRequest
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModHttpException
import com.chatmod.mobile.data.remote.ChannelProfileCreateRequest
import com.chatmod.mobile.data.remote.ChannelProfileRecord
import com.chatmod.mobile.data.remote.CloudBackup
import com.chatmod.mobile.data.remote.CommandManualSendRequest
import com.chatmod.mobile.data.remote.DemoChatModApiClient
import com.chatmod.mobile.data.remote.DiscordWebhookConfig
import com.chatmod.mobile.data.remote.DiscordWebhookUpsertRequest
import com.chatmod.mobile.data.remote.EntitlementSnapshot
import com.chatmod.mobile.data.remote.FaqEntryRecord
import com.chatmod.mobile.data.remote.FaqEntrySyncRequest
import com.chatmod.mobile.data.remote.FaqReplySuggestionRequest
import com.chatmod.mobile.data.remote.FaqReplySuggestionResult
import com.chatmod.mobile.data.remote.GooglePlayPurchaseValidationRequest
import com.chatmod.mobile.data.remote.LinkedAccountSummary
import com.chatmod.mobile.data.remote.RulePresetExportBundle
import com.chatmod.mobile.data.remote.RulePresetImportRequest
import com.chatmod.mobile.data.remote.RulePresetRecord
import com.chatmod.mobile.data.remote.RulePresetSyncRequest
import com.chatmod.mobile.data.remote.RulePresetTemplateRecord
import com.chatmod.mobile.data.remote.ModerationActionReviewRequest
import com.chatmod.mobile.data.remote.ModerationSuggestionResult
import com.chatmod.mobile.data.remote.OverlayConfig
import com.chatmod.mobile.data.remote.OverlayConfigUpdateRequest
import com.chatmod.mobile.data.remote.SettingsBackupCommand
import com.chatmod.mobile.data.remote.SettingsBackupTimer
import com.chatmod.mobile.data.remote.StreamSessionAnalyticsSummary
import com.chatmod.mobile.data.remote.StreamChatSummary
import com.chatmod.mobile.data.remote.SupportEventRecord
import com.chatmod.mobile.data.remote.SupportEventRequest
import com.chatmod.mobile.data.remote.TeamInviteCreateRequest
import com.chatmod.mobile.data.remote.TeamInviteRedeemRequest
import com.chatmod.mobile.data.remote.TeamMemberRecord
import com.chatmod.mobile.data.remote.TeamMemberPermissions
import com.chatmod.mobile.data.remote.TeamMembershipRecord
import com.chatmod.mobile.data.remote.UserHideRequest
import com.chatmod.mobile.data.remote.UserModerationActionRecord
import com.chatmod.mobile.data.remote.UserProfileRecord
import com.chatmod.mobile.data.remote.UserProfileNotesRequest
import com.chatmod.mobile.data.remote.UserStrikeRecord
import com.chatmod.mobile.data.remote.UserTimeoutRequest
import com.chatmod.mobile.data.remote.UserUnbanRequest
import com.chatmod.mobile.data.remote.UserWarningRequest
import com.chatmod.mobile.data.remote.UserWhitelistRequest
import com.chatmod.mobile.data.remote.YouTubeBroadcast
import com.chatmod.mobile.data.remote.YouTubeConnectUrl
import com.chatmod.mobile.data.remote.YouTubeAccountStatus
import com.chatmod.mobile.data.remote.YouTubeMessageDeleteRequest
import com.chatmod.mobile.data.remote.YouTubeTestMessageRequest
import com.chatmod.mobile.domain.rules.ChatMessage
import com.chatmod.mobile.domain.rules.LinkPolicy
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.TemporaryTrustedChannel
import com.chatmod.mobile.runtime.BotLogSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.util.UUID

class DashboardViewModel(
    private val commandTimerStore: DashboardCommandTimerStore = InMemoryDashboardCommandTimerStore(
        initialCommands = DefaultCommands,
        initialTimers = DefaultTimers
    ),
    private val backendApi: ChatModApiClient = DemoChatModApiClient(),
    private val accessTokenProvider: suspend () -> String? = { "demo-access-token" },
    private val refreshAccessTokenProvider: suspend () -> String? = accessTokenProvider,
    private val localPrivacyStore: LocalPrivacyStore? = null,
    private val settingsStore: SettingsStore? = null,
    private val logStore: DashboardLogStore = InMemoryDashboardLogStore(),
    private val botLogSink: BotLogSink? = null,
    batteryOptimizationIgnored: Boolean? = null
) : ViewModel() {
    private var lastBackendSessionFailure: Throwable? = null
    private var latestLocalLogEntries: List<LogEntrySummary> = emptyList()
    private var latestLocalUsers: List<UserHistorySummary> = emptyList()

    private val _state = MutableStateFlow(
        DashboardUiState(
            streamTitle = "Saturday build stream",
            channelName = "Your Channel",
            liveChatId = "demo-live-chat",
            videoId = "demo-video",
            queue = listOf(
                QueueItem(
                    id = "q1",
                    author = "ViewerOne",
                    authorChannelId = "viewer-one-channel",
                    message = "buy cheap views at www.example.com",
                    reason = "Link policy and blocked term",
                    severity = Severity.Danger,
                    youtubeMessageId = "message-q1",
                    profileImageUrl = "https://yt3.ggpht.com/viewer-one=s88-c-k-c0x00ffffff-no-rj"
                ),
                QueueItem(
                    id = "q2",
                    author = "NightChat",
                    authorChannelId = "night-chat-channel",
                    message = "THIS IS TOO LOUD",
                    reason = "Excessive caps",
                    severity = Severity.Warning,
                    youtubeMessageId = "message-q2",
                    profileImageUrl = "https://yt3.ggpht.com/night-chat=s88-c-k-c0x00ffffff-no-rj"
                )
            ),
            rules = DefaultModerationProfile.toRuleSummaries(),
            settings = SettingsPanelState(batteryOptimizationIgnored = batteryOptimizationIgnored),
            recentActions = listOf(
                ActionLogItem("a1", "Deleted message", "Blocked term: cheap views"),
                ActionLogItem("a2", "Flagged message", "Caps guard")
            )
        )
    )

    val state: StateFlow<DashboardUiState> = _state

    private var rulePresetTemplateCache: List<RulePresetTemplateRecord> = emptyList()

    init {
        viewModelScope.launch {
            commandTimerStore.commands.collect { commands ->
                _state.update { current -> current.copy(commands = commands) }
            }
        }
        viewModelScope.launch {
            commandTimerStore.timers.collect { timers ->
                _state.update { current -> current.copy(timers = timers) }
            }
        }
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.settings.collect { settings ->
                    _state.update { current ->
                        current.copy(
                            settings = settings.toPanelState(
                                statusMessage = current.settings.statusMessage,
                                batteryOptimizationIgnored = current.settings.batteryOptimizationIgnored,
                                existing = current.settings
                            )
                        )
                    }
                }
            }
            viewModelScope.launch {
                store.lastSelectedStream.collect { selectedStream ->
                    if (selectedStream != null) {
                        _state.update { current ->
                            current.copy(
                                streamTitle = selectedStream.streamTitle,
                                channelName = selectedStream.channelName,
                                liveChatId = selectedStream.liveChatId,
                                videoId = selectedStream.videoId
                            )
                        }
                    }
                }
            }
            viewModelScope.launch {
                store.activeRuntime.collect { runtime ->
                    _state.update { current ->
                        if (runtime == null) {
                            current.copy(
                                botRunning = false,
                                pendingRuntimeRecovery = null,
                                settings = current.settings.copy(
                                    statusMessage = if (current.botRunning) {
                                        "Bot runtime stopped"
                                    } else {
                                        current.settings.statusMessage
                                    }
                                )
                            )
                        } else {
                            val shouldRecover = !current.botRunning
                            current.copy(
                                botRunning = true,
                                liveChatId = runtime.liveChatId,
                                videoId = runtime.videoId,
                                streamTitle = runtime.streamTitle,
                                settings = current.settings.copy(
                                    statusMessage = if (shouldRecover) {
                                        "Recovering active bot runtime"
                                    } else {
                                        current.settings.statusMessage
                                    }
                                ),
                                pendingRuntimeRecovery = if (shouldRecover) {
                                    RuntimeRecoveryRequest(id = "runtime-recovery-${System.currentTimeMillis()}")
                                } else {
                                    current.pendingRuntimeRecovery
                                }
                            )
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            logStore.entries.collect { entries ->
                latestLocalLogEntries = entries
                _state.update { current -> current.withLocalLogEntries(entries) }
            }
        }
        viewModelScope.launch {
            logStore.userHistory.collect { users ->
                latestLocalUsers = users
                _state.update { current -> current.withLocalUserHistory(users) }
            }
        }
        checkAppCompatibility()
        viewModelScope.launch {
            val entitlement = withBackendSession { accessToken -> backendApi.currentEntitlement(accessToken) }
            if (entitlement != null) {
                val billing = entitlement.toBillingSummary()
                _state.update { current ->
                    current.withBillingSummary(
                        billing = billing,
                        allLogEntries = latestLocalLogEntries,
                        allUsers = latestLocalUsers
                    )
                }
            }
        }
        refreshCloudBackups()
        refreshStreamSelector()
        refreshChannelProfiles()
        refreshRulePresets()
        refreshUserWarnings()
        refreshDiscordWebhook()
        refreshOverlayConfig()
        refreshTeamAccess()
        refreshSupportEvents()
    }

    fun refreshChannelProfiles() {
        viewModelScope.launch {
            val previousSelected = activeProfileId()
            _state.update { current ->
                current.copy(
                    profiles = current.profiles.copy(
                        isLoading = true,
                        statusMessage = "Loading channel profiles",
                        createErrorMessage = null
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.listChannelProfiles(accessToken).profiles
            }

            if (result == null) {
                _state.update { current ->
                    val fallback = current.profiles.profiles.ifEmpty {
                        listOf(DefaultChannelProfileSummary)
                    }
                    val selected = current.profiles.selectedProfileId?.takeIf { profileId ->
                        fallback.any { profile -> profile.id == profileId }
                    } ?: current.settings.selectedProfileId?.takeIf { profileId ->
                        fallback.any { profile -> profile.id == profileId }
                    } ?: fallback.firstOrNull()?.id ?: DefaultProfileId
                    current.copy(
                        profiles = current.profiles.copy(
                            isLoading = false,
                            statusMessage = "Could not load profiles",
                            profiles = fallback,
                            selectedProfileId = selected
                        )
                    )
                }
                return@launch
            }

            val summaries = result.map { profile -> profile.toSummary() }
                .ifEmpty { listOf(DefaultChannelProfileSummary) }
            val selected = currentSelectedProfileId(summaries)
            persistSelectedProfile(selected)
            _state.update { current ->
                current.copy(
                    profiles = current.profiles.copy(
                        isLoading = false,
                        statusMessage = profileStatusMessage(summaries, selected),
                        selectedProfileId = selected,
                        profiles = summaries,
                        createErrorMessage = null
                    )
                )
            }
            if (selected != previousSelected) {
                refreshRulePresets()
                refreshUserWarnings()
                refreshDiscordWebhook()
                refreshOverlayConfig()
                refreshTeamAccess()
                refreshProAnalytics()
            }
        }
    }

    fun selectChannelProfile(profileId: String) {
        val selected = _state.value.profiles.profiles.firstOrNull { profile -> profile.id == profileId } ?: return
        persistSelectedProfile(selected.id)
        _state.update { current ->
            current.copy(
                profiles = current.profiles.copy(
                    selectedProfileId = selected.id,
                    statusMessage = "Using ${selected.name}"
                ),
                settings = current.settings.copy(selectedProfileId = selected.id),
                userHistory = current.userHistory.copy(selectedProfile = null)
            )
        }
        refreshRulePresets()
        refreshUserWarnings()
        refreshDiscordWebhook()
        refreshOverlayConfig()
        refreshTeamAccess()
        refreshProAnalytics()
    }

    fun updateChannelProfileName(value: String) {
        _state.update { current ->
            current.copy(
                profiles = current.profiles.copy(
                    createNameText = value.take(80),
                    createErrorMessage = null
                )
            )
        }
    }

    fun updateChannelProfileChannelId(value: String) {
        _state.update { current ->
            current.copy(
                profiles = current.profiles.copy(
                    createChannelIdText = value.take(120),
                    createErrorMessage = null
                )
            )
        }
    }

    fun createChannelProfile() {
        val snapshot = _state.value
        val name = snapshot.profiles.createNameText.trim()
        val channelId = snapshot.profiles.createChannelIdText.trim()
        val channelProfileLimit = snapshot.billing.channelProfiles

        val error = when {
            name.isBlank() -> "Name the profile first"
            channelId.isBlank() -> "Add the YouTube channel ID"
            snapshot.profiles.profiles.size >= channelProfileLimit -> "Your plan allows $channelProfileLimit profile(s)"
            else -> null
        }
        if (error != null) {
            _state.update { current ->
                current.copy(profiles = current.profiles.copy(createErrorMessage = error))
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    profiles = current.profiles.copy(
                        isLoading = true,
                        statusMessage = "Creating profile",
                        createErrorMessage = null
                    )
                )
            }

            val created = withBackendSession { accessToken ->
                backendApi.createChannelProfile(
                    accessToken = accessToken,
                    request = ChannelProfileCreateRequest(
                        channelId = channelId,
                        name = name
                    )
                )
            }

            if (created == null) {
                _state.update { current ->
                    current.copy(
                        profiles = current.profiles.copy(
                            isLoading = false,
                            createErrorMessage = channelProfileFailureMessage()
                        )
                    )
                }
                return@launch
            }

            val createdSummary = created.toSummary()
            persistSelectedProfile(createdSummary.id)
            _state.update { current ->
                val profiles = (current.profiles.profiles.filterNot { profile -> profile.id == createdSummary.id } + createdSummary)
                    .sortedBy { profile -> profile.name.lowercase() }
                current.copy(
                    profiles = current.profiles.copy(
                        isLoading = false,
                        statusMessage = "Created ${createdSummary.name}",
                        selectedProfileId = createdSummary.id,
                        profiles = profiles,
                        createNameText = "",
                        createChannelIdText = "",
                        createErrorMessage = null
                    ),
                    settings = current.settings.copy(selectedProfileId = createdSummary.id),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "profile-created-${createdSummary.id}",
                            label = "Created channel profile",
                            detail = createdSummary.name
                        )
                    ) + current.recentActions
                )
            }
            refreshRulePresets()
            refreshUserWarnings()
            refreshDiscordWebhook()
            refreshOverlayConfig()
            refreshTeamAccess()
            refreshProAnalytics()
        }
    }

    fun toggleBot(): Boolean {
        val previous = _state.value
        if (!previous.botRunning && previous.liveChatId.isNullOrBlank()) {
            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Failed,
                    streamSelector = current.streamSelector.copy(statusMessage = "Select an active stream before starting"),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "stream-required-${System.currentTimeMillis()}",
                            label = "Stream required",
                            detail = "Select an active YouTube Live chat before starting the bot"
                        )
                    ) + current.recentActions
                )
            }
            return false
        }

        if (!previous.botRunning && previous.appCompatibility.updateRequired) {
            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Failed,
                    recentActions = listOf(
                        ActionLogItem(
                            id = "app-update-${System.currentTimeMillis()}",
                            label = "Update required",
                            detail = previous.appCompatibility.message ?: "Update ChatMod Mobile before starting the bot"
                        )
                    ) + current.recentActions
                )
            }
            return false
        }

        val starting = !previous.botRunning
        _state.update { current ->
            current.copy(
                botRunning = !current.botRunning,
                syncStatus = SyncStatus.Ready
            )
        }
        if (starting) {
            rememberSelectedStream(previous)
        }
        return true
    }

    private fun checkAppCompatibility() {
        viewModelScope.launch {
            val compatibility = runCatching {
                backendApi.appCompatibility(
                    platform = "android",
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE
                )
            }.getOrNull()

            _state.update { current ->
                if (compatibility == null) {
                    current.copy(
                        appCompatibility = AppCompatibilityState(
                            checked = false,
                            currentVersionName = BuildConfig.VERSION_NAME,
                            currentVersionCode = BuildConfig.VERSION_CODE,
                            status = "unavailable",
                            message = "Compatibility check unavailable"
                        )
                    )
                } else {
                    val updateAction = if (compatibility.updateRequired) {
                        listOf(
                            ActionLogItem(
                                id = "app-compatibility-${System.currentTimeMillis()}",
                                label = "Update required",
                                detail = compatibility.message
                            )
                        )
                    } else {
                        emptyList()
                    }
                    current.copy(
                        appCompatibility = compatibility.toUiState(),
                        syncStatus = if (compatibility.updateRequired) SyncStatus.Failed else current.syncStatus,
                        recentActions = updateAction + current.recentActions
                    )
                }
            }
        }
    }

    fun resolveQueueItem(id: String) {
        _state.update { current ->
            current.copy(
                queue = current.queue.filterNot { it.id == id },
                recentActions = listOf(
                    ActionLogItem(
                        id = "resolved-$id",
                        label = "Resolved queue item",
                        detail = "Handled from mobile dashboard"
                    )
                ) + current.recentActions
            )
        }
    }

    fun requestDeleteQueueItemMessage(id: String) {
        val item = _state.value.queue.firstOrNull { it.id == id } ?: return
        if (item.youtubeMessageId.isNullOrBlank()) {
            deleteQueueItemMessage(id)
            return
        }

        _state.update { current ->
            current.copy(
                moderationConfirmation = ModerationConfirmation.DeleteQueueMessage(
                    queueItemId = item.id,
                    author = item.author,
                    message = item.message
                )
            )
        }
    }

    fun deleteQueueItemMessage(id: String) {
        viewModelScope.launch {
            val snapshot = _state.value
            val item = snapshot.queue.firstOrNull { it.id == id } ?: return@launch
            val liveChatId = snapshot.liveChatId?.takeIf { it.isNotBlank() }
            val messageId = item.youtubeMessageId?.takeIf { it.isNotBlank() }
            if (messageId == null) {
                _state.update { current ->
                    current.copy(
                        recentActions = listOf(
                            ActionLogItem(
                                id = "delete-unavailable-$id-${System.currentTimeMillis()}",
                                label = "Delete unavailable",
                                detail = "No YouTube message id for ${item.author}"
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            _state.update { current -> current.copy(syncStatus = SyncStatus.Syncing) }

            val result = withBackendSession { accessToken ->
                backendApi.deleteYouTubeLiveChatMessage(
                    accessToken = accessToken,
                    request = YouTubeMessageDeleteRequest(
                        messageId = messageId,
                        reason = "manual_queue_delete:${item.reason}".take(300)
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "delete-failed-$id-${System.currentTimeMillis()}",
                                label = "Delete failed",
                                detail = item.author
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            if (liveChatId != null) {
                runCatching {
                    recordManualModerationAction(
                        authorChannelId = item.authorChannelId,
                        authorName = item.author,
                        liveChatId = liveChatId,
                        actionType = "deleteMessage",
                        reason = "manual_queue_delete:${item.reason}".take(300),
                        youtubeMessageId = messageId,
                        messageText = item.message
                    )
                }
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    queue = current.queue.filterNot { queueItem -> queueItem.id == id },
                    recentActions = listOf(
                        ActionLogItem(
                            id = "delete-${result.messageId}-${System.currentTimeMillis()}",
                            label = "Deleted message",
                            detail = "${item.author}: ${item.reason}"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun quickBlockPhraseFromQueueItem(id: String) {
        val item = _state.value.queue.firstOrNull { it.id == id } ?: return
        val phrase = item.message.suggestBlockedPhrase()
        if (phrase.isBlank()) {
            _state.update { current ->
                current.copy(
                    recentActions = listOf(
                        ActionLogItem(
                            id = "quick-block-empty-$id-${System.currentTimeMillis()}",
                            label = "Phrase not added",
                            detail = "Could not find a useful phrase in ${item.author}'s message"
                        )
                    ) + current.recentActions
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Adding \"$phrase\" to blocked terms"
                    )
                )
            }

            val existingPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            }
            if (existingPresets == null && _state.value.rulePresets.presets.isNotEmpty()) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Could not load active preset"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "quick-block-load-failed-$id-${System.currentTimeMillis()}",
                                label = "Blocked phrase failed",
                                detail = "Could not load active preset before adding \"$phrase\""
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }
            val activePreset = existingPresets
                ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
                ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
                ?: existingPresets?.firstOrNull()
            val baseProfile = activePreset?.config ?: DefaultModerationProfile
            val updatedProfile = baseProfile.copy(
                blockedTerms = (baseProfile.blockedTerms + phrase)
                    .distinctBy { term -> term.trim().lowercase() }
            )
            val alreadyBlocked = updatedProfile.blockedTerms.size == baseProfile.blockedTerms.size

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = activePreset?.id ?: StarterPresetId,
                        profileId = activePreset?.profileId ?: activeProfileId(),
                        name = activePreset?.name ?: "Starter moderation",
                        config = updatedProfile,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Could not add blocked phrase"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "quick-block-failed-$id-${System.currentTimeMillis()}",
                                label = "Blocked phrase failed",
                                detail = phrase
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = if (alreadyBlocked) {
                    "\"$phrase\" was already blocked"
                } else {
                    "Blocked \"$phrase\""
                }
            )
            _state.update { current ->
                current.copy(
                    queue = current.queue.filterNot { queueItem -> queueItem.id == id },
                    recentActions = listOf(
                        ActionLogItem(
                            id = "quick-block-$id-${System.currentTimeMillis()}",
                            label = if (alreadyBlocked) "Phrase already blocked" else "Added blocked phrase",
                            detail = phrase
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun warnQueueItem(id: String) {
        viewModelScope.launch {
            val snapshot = _state.value
            val item = snapshot.queue.firstOrNull { it.id == id } ?: return@launch
            val liveChatId = snapshot.liveChatId?.takeIf { it.isNotBlank() }

            _state.update { current -> current.copy(syncStatus = SyncStatus.Syncing) }

            val result = withBackendSession { accessToken ->
                backendApi.warnUser(
                    accessToken = accessToken,
                    request = UserWarningRequest(
                        profileId = activeProfileId(),
                        authorChannelId = item.authorChannelId,
                        displayName = item.author,
                        reason = item.reason,
                        profileImageUrl = item.profileImageUrl,
                        liveChatId = liveChatId,
                        warningText = liveChatId?.let { item.warningText() }
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "warn-failed-$id-${System.currentTimeMillis()}",
                                label = "Warning failed",
                                detail = item.author
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    queue = current.queue.filterNot { queueItem -> queueItem.id == id },
                    recentActions = listOf(
                        ActionLogItem(
                            id = "warn-${result.strike.id}",
                            label = if (result.messageId.isNullOrBlank()) "Strike recorded" else "User warned",
                            detail = "${result.user.displayName} now has ${result.user.strikeCount} strike(s)"
                        )
                    ) + current.recentActions
                )
            }
            refreshUserWarnings()
        }
    }

    fun suggestQueueItemAction(id: String) {
        val item = _state.value.queue.firstOrNull { queueItem -> queueItem.id == id } ?: return
        if (!_state.value.billing.aiSuggestions) {
            _state.update { current ->
                current.copy(
                    recentActions = listOf(
                        ActionLogItem(
                            id = "suggestion-locked-$id-${System.currentTimeMillis()}",
                            label = "Review assistant locked",
                            detail = "Creator plan is required for AI moderation suggestions."
                        )
                    ) + current.recentActions
                )
            }
            return
        }

        viewModelScope.launch {
            _state.updateQueueItem(id) { queueItem -> queueItem.copy(isSuggestionLoading = true) }
            val result = withBackendSession { accessToken ->
                backendApi.evaluateModerationSuggestion(
                    accessToken = accessToken,
                    message = item.toChatMessage(),
                    profile = DefaultModerationProfile,
                    recentMessages = latestLocalLogEntries
                        .filter { entry -> entry.kind == LogEntryKind.Chat }
                        .take(20)
                        .map { entry -> entry.toChatMessageForSuggestion() }
                )
            }
            val faqResult = withBackendSession { accessToken ->
                backendApi.suggestFaqReply(
                    accessToken = accessToken,
                    request = FaqReplySuggestionRequest(
                        profileId = activeProfileId(),
                        messageText = item.message,
                        authorName = item.author
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        queue = current.queue.map { queueItem ->
                            if (queueItem.id == id) {
                                queueItem.copy(isSuggestionLoading = false)
                            } else {
                                queueItem
                            }
                        },
                        recentActions = listOf(
                            ActionLogItem(
                                id = "suggestion-failed-$id-${System.currentTimeMillis()}",
                                label = "Suggestion failed",
                                detail = "Could not load review assistant guidance."
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    queue = current.queue.map { queueItem ->
                        if (queueItem.id == id) {
                            queueItem.copy(
                                assistantSuggestion = result.toUiSummary(),
                                faqSuggestion = faqResult?.takeIf { suggestion -> suggestion.matched }?.toUiSummary(),
                                isSuggestionLoading = false
                            )
                        } else {
                            queueItem
                        }
                    },
                    recentActions = listOf(
                        ActionLogItem(
                            id = "suggestion-$id-${System.currentTimeMillis()}",
                            label = "Suggestion ready",
                            detail = if (faqResult?.matched == true) {
                                "${item.author}: FAQ reply ready"
                            } else {
                                "${item.author}: ${result.suggestedAction}"
                            }
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun selectTab(tab: DashboardTab) {
        _state.update { current ->
            current.copy(selectedTab = tab)
        }
        if (tab == DashboardTab.Support) {
            refreshSupportEvents()
        } else if (tab == DashboardTab.Queue && _state.value.streamSelector.broadcasts.isEmpty()) {
            refreshStreamSelector()
        } else if (tab == DashboardTab.Users) {
            refreshUserWarnings()
        } else if (tab == DashboardTab.Rules && _state.value.rulePresets.presets.isEmpty()) {
            refreshRulePresets()
        } else if (tab == DashboardTab.Commands && _state.value.faq.entries.isEmpty()) {
            refreshFaqEntries()
        } else if (tab == DashboardTab.Settings) {
            refreshDiscordWebhook()
            refreshOverlayConfig()
            refreshTeamAccess()
        } else if (tab == DashboardTab.Logs && _state.value.logs.proAnalytics.generatedAt == null) {
            refreshProAnalytics()
        }
    }

    fun refreshUserWarnings() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    userHistory = current.userHistory.copy(
                        isLoadingWarnings = true,
                        warningStatusMessage = "Loading warnings"
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.listUserProfiles(accessToken, activeProfileId()).users
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        userHistory = current.userHistory.copy(
                            isLoadingWarnings = false,
                            warningStatusMessage = "Warning history unavailable"
                        )
                    )
                }
                return@launch
            }

            val warnedUsers = result
                .filter { user -> user.strikeCount > 0 || user.recentStrikes.isNotEmpty() }
                .map { user -> user.toWarningHistorySummary() }
            _state.update { current ->
                current.copy(
                    userHistory = current.userHistory.copy(
                        warnedUsers = warnedUsers,
                        isLoadingWarnings = false,
                        warningStatusMessage = if (warnedUsers.isEmpty()) {
                            "No warned users yet"
                        } else {
                            "${warnedUsers.size} warned users"
                        }
                    )
                )
            }
        }
    }

    fun openUserProfile(userProfileId: String) {
        val profile = _state.value.userHistory.warnedUsers.firstOrNull { user ->
            user.id == userProfileId || user.channelId == userProfileId
        }
        if (profile == null) {
            _state.update { current ->
                current.copy(
                    userHistory = current.userHistory.copy(
                        warningStatusMessage = "Warn the chatter first to enable profile actions"
                    )
                )
            }
            return
        }
        _state.update { current ->
            current.copy(
                userHistory = current.userHistory.copy(
                    selectedProfile = profile.toDrawerState()
                )
            )
        }
    }

    fun updateUserProfileNotes(notes: String) {
        _state.update { current ->
            current.copy(
                userHistory = current.userHistory.copy(
                    selectedProfile = current.userHistory.selectedProfile?.copy(
                        notesText = notes.take(500),
                        statusMessage = null
                    )
                )
            )
        }
    }

    fun dismissUserProfile() {
        _state.update { current ->
            current.copy(
                userHistory = current.userHistory.copy(selectedProfile = null)
            )
        }
    }

    fun saveUserProfileNotes() {
        viewModelScope.launch {
            val selected = _state.value.userHistory.selectedProfile ?: return@launch
            _state.update { current ->
                current.copy(
                    userHistory = current.userHistory.copy(
                        selectedProfile = current.userHistory.selectedProfile?.copy(
                            isSavingNotes = true,
                            statusMessage = "Saving notes"
                        )
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.updateUserProfileNotes(
                    accessToken = accessToken,
                    userProfileId = selected.id,
                    request = UserProfileNotesRequest(notes = selected.notesText.trim().ifBlank { null })
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                isSavingNotes = false,
                                statusMessage = "Notes could not be saved"
                            )
                        )
                    )
                }
                return@launch
            }

            val updatedSummary = result.toWarningHistorySummary()
            _state.update { current ->
                current.copy(
                    userHistory = current.userHistory.copy(
                        warnedUsers = current.userHistory.warnedUsers.map { user ->
                            if (user.id == updatedSummary.id) updatedSummary else user
                        },
                        selectedProfile = updatedSummary.toDrawerState(statusMessage = "Notes saved")
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "user-notes-${result.id}-${System.currentTimeMillis()}",
                            label = "Saved user notes",
                            detail = result.displayName
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun requestHideSelectedUserProfile() {
        val selected = _state.value.userHistory.selectedProfile ?: return
        _state.update { current ->
            current.copy(
                moderationConfirmation = ModerationConfirmation.HideUser(
                    userProfileId = selected.id,
                    displayName = selected.displayName
                )
            )
        }
    }

    fun hideSelectedUserProfile() {
        viewModelScope.launch {
            val selected = _state.value.userHistory.selectedProfile ?: return@launch
            val liveChatId = _state.value.liveChatId?.takeIf { it.isNotBlank() }
            if (liveChatId == null) {
                _state.update { current ->
                    current.copy(
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                statusMessage = "Select an active live chat before hiding a user"
                            )
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Syncing,
                    userHistory = current.userHistory.copy(
                        selectedProfile = current.userHistory.selectedProfile?.copy(
                            isHidingUser = true,
                            statusMessage = "Hiding user from live chat"
                        )
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.hideUserProfile(
                    accessToken = accessToken,
                    userProfileId = selected.id,
                    request = UserHideRequest(
                        liveChatId = liveChatId,
                        reason = "manual_profile_action"
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                isHidingUser = false,
                                statusMessage = "Could not hide user"
                            )
                        )
                    )
                }
                return@launch
            }

            runCatching {
                recordManualProfileModerationAction(
                    selected = selected,
                    liveChatId = liveChatId,
                    actionType = "hideUser",
                    reason = "manual_profile_action"
                )
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    userHistory = current.userHistory.copy(
                        selectedProfile = result.user.toWarningHistorySummary().toDrawerState(
                            statusMessage = if (result.liveChatBanId.isNullOrBlank()) {
                                "User hidden, but YouTube did not return an unban handle"
                            } else {
                                "User hidden from live chat"
                            }
                        ).copy(
                            isHidingUser = false,
                        )
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "hide-user-${result.user.id}-${System.currentTimeMillis()}",
                            label = "Hid user",
                            detail = "${result.user.displayName} from selected live chat"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun requestTimeoutSelectedUserProfile() {
        val selected = _state.value.userHistory.selectedProfile ?: return
        _state.update { current ->
            current.copy(
                moderationConfirmation = ModerationConfirmation.TimeoutUser(
                    userProfileId = selected.id,
                    displayName = selected.displayName
                )
            )
        }
    }

    fun timeoutSelectedUserProfile() {
        viewModelScope.launch {
            val selected = _state.value.userHistory.selectedProfile ?: return@launch
            val liveChatId = _state.value.liveChatId?.takeIf { it.isNotBlank() }
            if (liveChatId == null) {
                _state.update { current ->
                    current.copy(
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                statusMessage = "Select an active live chat before timing out a user"
                            )
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Syncing,
                    userHistory = current.userHistory.copy(
                        selectedProfile = current.userHistory.selectedProfile?.copy(
                            isTimingOutUser = true,
                            statusMessage = "Timing out user for 5 minutes"
                        )
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.timeoutUserProfile(
                    accessToken = accessToken,
                    userProfileId = selected.id,
                    request = UserTimeoutRequest(
                        liveChatId = liveChatId,
                        durationSeconds = ManualTimeoutSeconds,
                        reason = "manual_profile_timeout"
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                isTimingOutUser = false,
                                statusMessage = "Could not time out user"
                            )
                        )
                    )
                }
                return@launch
            }

            runCatching {
                recordManualProfileModerationAction(
                    selected = selected,
                    liveChatId = liveChatId,
                    actionType = "timeoutUser",
                    reason = "manual_profile_timeout"
                )
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    userHistory = current.userHistory.copy(
                        selectedProfile = result.user.toWarningHistorySummary().toDrawerState(
                            statusMessage = if (result.liveChatBanId.isNullOrBlank()) {
                                "User timed out, but YouTube did not return an unban handle"
                            } else {
                                "User timed out for 5 minutes"
                            }
                        ).copy(
                            isTimingOutUser = false,
                        )
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "timeout-user-${result.user.id}-${System.currentTimeMillis()}",
                            label = "Timed out user",
                            detail = "${result.user.displayName} for 5 minutes"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun requestUnbanSelectedUserProfile(liveChatBanId: String, liveChatId: String?) {
        val selected = _state.value.userHistory.selectedProfile ?: return
        _state.update { current ->
            current.copy(
                moderationConfirmation = ModerationConfirmation.UnbanUser(
                    userProfileId = selected.id,
                    displayName = selected.displayName,
                    liveChatBanId = liveChatBanId,
                    liveChatId = liveChatId
                )
            )
        }
    }

    private fun unbanSelectedUserProfile(liveChatBanId: String, liveChatId: String?) {
        viewModelScope.launch {
            val selected = _state.value.userHistory.selectedProfile ?: return@launch
            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Syncing,
                    userHistory = current.userHistory.copy(
                        selectedProfile = current.userHistory.selectedProfile?.copy(
                            isUnbanningUser = true,
                            statusMessage = "Unbanning user"
                        )
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.unbanUserProfile(
                    accessToken = accessToken,
                    userProfileId = selected.id,
                    request = UserUnbanRequest(
                        liveChatBanId = liveChatBanId,
                        liveChatId = liveChatId,
                        reason = "manual_profile_unban"
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                isUnbanningUser = false,
                                statusMessage = "Could not unban user"
                            )
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    userHistory = current.userHistory.copy(
                        selectedProfile = result.user.toWarningHistorySummary().toDrawerState(
                            statusMessage = "User unbanned where YouTube accepts this ban"
                        ).copy(
                            isUnbanningUser = false
                        )
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "unban-user-${result.user.id}-${System.currentTimeMillis()}",
                            label = "Unbanned user",
                            detail = result.user.displayName
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    private suspend fun recordManualProfileModerationAction(
        selected: UserProfileDrawerState,
        liveChatId: String,
        actionType: String,
        reason: String
    ) {
        recordManualModerationAction(
            authorChannelId = selected.channelId,
            authorName = selected.displayName,
            liveChatId = liveChatId,
            actionType = actionType,
            reason = reason
        )
    }

    private suspend fun recordManualModerationAction(
        authorChannelId: String,
        authorName: String,
        liveChatId: String,
        actionType: String,
        reason: String,
        youtubeMessageId: String? = null,
        messageText: String? = null
    ) {
        val sink = botLogSink ?: return
        val runtime = settingsStore?.activeRuntime?.first() ?: return
        if (runtime.liveChatId != liveChatId) {
            return
        }

        sink.recordModerationAction(
            sessionId = runtime.sessionId,
            youtubeMessageId = youtubeMessageId,
            authorChannelId = authorChannelId,
            authorName = authorName,
            messageText = messageText,
            actionType = actionType,
            reason = reason,
            confidence = null
        )
    }

    private suspend fun recordRuntimeRecommendationEvent(liveChatId: String) {
        val sink = botLogSink ?: return
        val runtime = settingsStore?.activeRuntime?.first() ?: return
        if (runtime.liveChatId != liveChatId) {
            return
        }

        sink.recordRuntimeEvent(
            sessionId = runtime.sessionId,
            type = "subscriber_only_recommendation_sent",
            message = "Sent subscribers-only recommendation message"
        )
    }

    fun dismissModerationConfirmation() {
        _state.update { current -> current.copy(moderationConfirmation = null) }
    }

    fun confirmModerationAction() {
        val action = _state.value.moderationConfirmation ?: return
        _state.update { current -> current.copy(moderationConfirmation = null) }

        when (action) {
            is ModerationConfirmation.DeleteQueueMessage -> deleteQueueItemMessage(action.queueItemId)
            is ModerationConfirmation.HideUser -> hideSelectedUserProfile()
            is ModerationConfirmation.TimeoutUser -> timeoutSelectedUserProfile()
            is ModerationConfirmation.UnbanUser -> unbanSelectedUserProfile(action.liveChatBanId, action.liveChatId)
        }
    }

    fun whitelistSelectedUserProfile() {
        whitelistSelectedUserProfile(durationSeconds = null)
    }

    fun temporaryWhitelistSelectedUserProfile() {
        whitelistSelectedUserProfile(durationSeconds = TemporaryWhitelistSeconds)
    }

    private fun whitelistSelectedUserProfile(durationSeconds: Int?) {
        viewModelScope.launch {
            val selected = _state.value.userHistory.selectedProfile ?: return@launch
            val temporary = durationSeconds != null
            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Syncing,
                    userHistory = current.userHistory.copy(
                        selectedProfile = current.userHistory.selectedProfile?.copy(
                            isWhitelistingUser = true,
                            statusMessage = if (temporary) "Trusting user for 1 hour" else "Adding trusted user"
                        )
                    )
                )
            }

            val whitelistResult = withBackendSession { accessToken ->
                backendApi.whitelistUserProfile(
                    accessToken = accessToken,
                    userProfileId = selected.id,
                    request = UserWhitelistRequest(durationSeconds = durationSeconds)
                )
            }

            if (whitelistResult == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        userHistory = current.userHistory.copy(
                            selectedProfile = current.userHistory.selectedProfile?.copy(
                                isWhitelistingUser = false,
                                statusMessage = "Could not whitelist user"
                            )
                        )
                    )
                }
                return@launch
            }

            val updatedPreset = if (temporary) {
                whitelistResult.whitelist.temporaryUntil?.let { expiresAt ->
                    addTemporaryTrustedChannelToActivePreset(whitelistResult.user.authorChannelId, expiresAt)
                }
            } else {
                addTrustedChannelToActivePreset(whitelistResult.user.authorChannelId)
            }
            _state.update { current ->
                current.copy(
                    syncStatus = if (updatedPreset == null) SyncStatus.Failed else SyncStatus.Ready,
                    userHistory = current.userHistory.copy(
                        selectedProfile = current.userHistory.selectedProfile?.copy(
                            isWhitelistingUser = false,
                            statusMessage = if (temporary && updatedPreset != null) {
                                "Temporary trust saved until ${whitelistResult.whitelist.temporaryUntil?.shortDateTimeLabel() ?: "expiry"}"
                            } else if (temporary) {
                                "Temporary trust saved, but rule preset could not be updated"
                            } else if (updatedPreset == null) {
                                "Whitelisted, but rule preset could not be updated"
                            } else {
                                "User added to trusted list"
                            }
                        )
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "whitelist-user-${whitelistResult.user.id}-${System.currentTimeMillis()}",
                            label = if (temporary) "Temporarily trusted user" else "Whitelisted user",
                            detail = if (temporary) {
                                "${whitelistResult.user.displayName} for 1 hour"
                            } else {
                                whitelistResult.user.displayName
                            }
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    private suspend fun addTrustedChannelToActivePreset(authorChannelId: String): RulePresetRecord? {
        val existingPresets = withBackendSession { accessToken ->
            backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
        }
        val activePreset = existingPresets
            ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
            ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
            ?: existingPresets?.firstOrNull()
        val baseProfile = activePreset?.config ?: DefaultModerationProfile
        val updatedProfile = baseProfile.copy(
            trustedChannelIds = (baseProfile.trustedChannelIds + authorChannelId)
                .distinctBy { channelId -> channelId.trim().lowercase() }
        )

        val preset = withBackendSession { accessToken ->
            backendApi.saveRulePreset(
                accessToken = accessToken,
                request = RulePresetSyncRequest(
                    id = activePreset?.id ?: StarterPresetId,
                    profileId = activePreset?.profileId ?: activeProfileId(),
                    name = activePreset?.name ?: "Starter moderation",
                    config = updatedProfile,
                    isDefault = true
                )
            )
        } ?: return null

        applyRulePresetUpdate(
            preset = preset,
            statusMessage = "Trusted ${authorChannelId.take(16)}"
        )
        return preset
    }

    private suspend fun addTemporaryTrustedChannelToActivePreset(authorChannelId: String, expiresAt: String): RulePresetRecord? {
        val existingPresets = withBackendSession { accessToken ->
            backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
        }
        val activePreset = existingPresets
            ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
            ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
            ?: existingPresets?.firstOrNull()
        val baseProfile = activePreset?.config ?: DefaultModerationProfile
        val updatedProfile = baseProfile.copy(
            temporaryTrustedChannels = (
                baseProfile.temporaryTrustedChannels
                    .filterNot { trusted -> trusted.channelId == authorChannelId || trusted.isExpired() } +
                    TemporaryTrustedChannel(channelId = authorChannelId, expiresAt = expiresAt)
                ).takeLast(500)
        )

        val preset = withBackendSession { accessToken ->
            backendApi.saveRulePreset(
                accessToken = accessToken,
                request = RulePresetSyncRequest(
                    id = activePreset?.id ?: StarterPresetId,
                    profileId = activePreset?.profileId ?: activeProfileId(),
                    name = activePreset?.name ?: "Starter moderation",
                    config = updatedProfile,
                    isDefault = true
                )
            )
        } ?: return null

        applyRulePresetUpdate(
            preset = preset,
            statusMessage = "Trusted ${authorChannelId.take(16)} until ${expiresAt.shortDateTimeLabel()}"
        )
        return preset
    }

    fun refreshRulePresets() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Loading presets"
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                RulePresetLoadResult(
                    presets = backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets,
                    templates = backendApi.listRulePresetTemplates(accessToken).rulePresetTemplates
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Could not load presets"
                        )
                    )
                }
                return@launch
            }

            rulePresetTemplateCache = result.templates
            val defaultPreset = result.presets.firstOrNull { it.isDefault } ?: result.presets.firstOrNull()
            defaultPreset?.let { preset -> persistActiveRulePreset(preset) }
            _state.update { current ->
                current.copy(
                    rules = defaultPreset?.config?.toRuleSummaries() ?: current.rules,
                    rulePresets = current.rulePresets.copy(
                        isLoading = false,
                        statusMessage = rulePresetStatusMessage(result.presets),
                        selectedPresetId = defaultPreset?.id,
                        templates = result.templates.map { it.toSummary() },
                        presets = result.presets.map { it.toSummary() }
                    )
                )
            }
        }
    }

    fun exportRulePresets() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Preparing preset export"
                    )
                )
            }

            val bundle = withBackendSession { accessToken ->
                backendApi.exportRulePresets(accessToken, activeProfileId())
            }

            if (bundle == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Preset export failed"
                        )
                    )
                }
                return@launch
            }

            val requestId = "rule-preset-export-${System.currentTimeMillis()}"
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = false,
                        statusMessage = if (bundle.rulePresets.isEmpty()) {
                            "No presets to export"
                        } else {
                            "Preparing ${bundle.rulePresets.size} preset export"
                        },
                        pendingShare = if (bundle.rulePresets.isEmpty()) {
                            null
                        } else {
                            RulePresetShareRequest(
                                id = requestId,
                                subject = "ChatMod rule presets ${bundle.exportedAt}",
                                text = bundle.toPrettyJson()
                            )
                        }
                    )
                )
            }
        }
    }

    fun finishRulePresetExportShare(id: String, launched: Boolean) {
        _state.update { current ->
            if (current.rulePresets.pendingShare?.id != id) {
                current
            } else {
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        pendingShare = null,
                        statusMessage = if (launched) "Preset export ready to share" else "No app available for preset export"
                    ),
                    recentActions = if (launched) {
                        listOf(
                            ActionLogItem(
                                id = "preset-export-finished-$id",
                                label = "Exported rule presets",
                                detail = "${current.rulePresets.presets.size} presets"
                            )
                        ) + current.recentActions
                    } else {
                        current.recentActions
                    }
                )
            }
        }
    }

    fun startRulePresetImport() {
        _state.update { current ->
            current.copy(
                rulePresets = current.rulePresets.copy(
                    importDialog = RulePresetImportDialogState()
                )
            )
        }
    }

    fun updateRulePresetImportText(text: String) {
        _state.update { current ->
            current.copy(
                rulePresets = current.rulePresets.copy(
                    importDialog = current.rulePresets.importDialog?.copy(
                        bundleJson = text,
                        errorMessage = null
                    )
                )
            )
        }
    }

    fun dismissRulePresetImport() {
        _state.update { current ->
            current.copy(
                rulePresets = current.rulePresets.copy(importDialog = null)
            )
        }
    }

    fun confirmRulePresetImport() {
        val importText = _state.value.rulePresets.importDialog?.bundleJson.orEmpty()
        val bundle = runCatching { importText.toRulePresetExportBundle() }.getOrElse { error ->
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        importDialog = current.rulePresets.importDialog?.copy(
                            errorMessage = error.message ?: "Preset bundle is not valid JSON"
                        )
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Importing presets"
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.importRulePresets(
                    accessToken = accessToken,
                    request = RulePresetImportRequest(
                        profileId = activeProfileId(),
                        bundle = bundle
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Preset import failed",
                            importDialog = current.rulePresets.importDialog?.copy(
                                errorMessage = "Backend could not import this preset bundle"
                            )
                        )
                    )
                }
                return@launch
            }

            val allPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            } ?: result.rulePresets
            val selectedPreset = result.rulePresets.firstOrNull { it.isDefault }
                ?: allPresets.firstOrNull { it.isDefault }
                ?: result.rulePresets.firstOrNull()
                ?: allPresets.firstOrNull()
            selectedPreset?.let { preset -> persistActiveRulePreset(preset) }

            _state.update { current ->
                current.copy(
                    rules = selectedPreset?.config?.toRuleSummaries() ?: current.rules,
                    rulePresets = current.rulePresets.copy(
                        isLoading = false,
                        statusMessage = "Imported ${result.importedCount} presets",
                        selectedPresetId = selectedPreset?.id ?: current.rulePresets.selectedPresetId,
                        importDialog = null,
                        presets = allPresets.map { it.toSummary() }
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "preset-import-${System.currentTimeMillis()}",
                            label = "Imported rule presets",
                            detail = "${result.importedCount} presets"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun saveCurrentRulePreset() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Saving custom preset"
                    )
                )
            }

            val existingPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            }
            if (existingPresets == null && _state.value.rulePresets.presets.isNotEmpty()) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Could not load active preset"
                        )
                    )
                }
                return@launch
            }
            val activePreset = existingPresets
                ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
                ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
                ?: existingPresets?.firstOrNull()
            val customId = if (activePreset?.id?.startsWith(CustomPresetPrefix) == true) {
                activePreset.id
            } else {
                "$CustomPresetPrefix${System.currentTimeMillis()}"
            }
            val customName = if (activePreset?.id?.startsWith(CustomPresetPrefix) == true) {
                activePreset.name
            } else {
                "Custom preset"
            }

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = customId,
                        profileId = activePreset?.profileId ?: activeProfileId(),
                        name = customName,
                        config = activePreset?.config ?: DefaultModerationProfile,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Custom preset save failed"
                        )
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = "Custom preset saved"
            )
        }
    }

    fun toggleAutoReplyForActivePreset() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Updating auto-reply"
                    )
                )
            }

            val existingPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            }
            if (existingPresets == null && _state.value.rulePresets.presets.isNotEmpty()) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Auto-reply update needs backend sync"
                        )
                    )
                }
                return@launch
            }

            val activePreset = existingPresets
                ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
                ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
                ?: existingPresets?.firstOrNull()
            val baseProfile = activePreset?.config ?: DefaultModerationProfile
            val enabled = !baseProfile.autoReplyEnabled
            val updatedProfile = baseProfile.copy(
                autoReplyEnabled = enabled,
                autoReplyMessage = baseProfile.autoReplyMessage.ifBlank { DefaultAutoReplyMessage }
            )

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = activePreset?.id ?: StarterPresetId,
                        profileId = activePreset?.profileId ?: activeProfileId(),
                        name = activePreset?.name ?: "Starter moderation",
                        config = updatedProfile,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Auto-reply update failed"
                        )
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = if (enabled) "Auto-reply enabled" else "Auto-reply disabled"
            )
        }
    }

    fun toggleFirstStreamMinutesOnlyForActivePreset() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Updating stream window"
                    )
                )
            }

            val existingPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            }
            if (existingPresets == null && _state.value.rulePresets.presets.isNotEmpty()) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Stream-window update needs backend sync"
                        )
                    )
                }
                return@launch
            }

            val activePreset = existingPresets
                ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
                ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
                ?: existingPresets?.firstOrNull()
            val baseProfile = activePreset?.config ?: DefaultModerationProfile
            val enabled = baseProfile.firstStreamMinutesOnly == null
            val updatedProfile = baseProfile.copy(
                firstStreamMinutesOnly = if (enabled) DefaultFirstStreamMinutesOnly else null
            )

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = activePreset?.id ?: StarterPresetId,
                        profileId = activePreset?.profileId ?: activeProfileId(),
                        name = activePreset?.name ?: "Starter moderation",
                        config = updatedProfile,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Stream-window update failed"
                        )
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = if (enabled) {
                    "Rules limited to first ${DefaultFirstStreamMinutesOnly}m"
                } else {
                    "Rules active for full stream"
                }
            )
        }
    }

    fun toggleHideUserOnSevereMatchForActivePreset() {
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Updating auto-hide"
                    )
                )
            }

            val existingPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            }
            if (existingPresets == null && _state.value.rulePresets.presets.isNotEmpty()) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Auto-hide update needs backend sync"
                        )
                    )
                }
                return@launch
            }

            val activePreset = existingPresets
                ?.firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
                ?: existingPresets?.firstOrNull { preset -> preset.isDefault }
                ?: existingPresets?.firstOrNull()
            val baseProfile = activePreset?.config ?: DefaultModerationProfile
            val enabled = !baseProfile.hideUserOnSevereMatch
            val updatedProfile = baseProfile.copy(
                hideUserOnSevereMatch = enabled
            )

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = activePreset?.id ?: StarterPresetId,
                        profileId = activePreset?.profileId ?: activeProfileId(),
                        name = activePreset?.name ?: "Starter moderation",
                        config = updatedProfile,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Auto-hide update failed"
                        )
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = if (enabled) "Auto-hide enabled for severe matches" else "Auto-hide disabled"
            )
        }
    }

    fun applyRulePresetTemplate(templateId: String) {
        viewModelScope.launch {
            val selected = rulePresetTemplateCache.firstOrNull { template -> template.id == templateId }
            val selectedName = selected?.name
                ?: _state.value.rulePresets.templates.firstOrNull { template -> template.id == templateId }?.name
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = selectedName?.let { "Applying $it" } ?: "Loading template"
                    )
                )
            }

            val template = selected ?: loadRulePresetTemplates().firstOrNull { record -> record.id == templateId }
            if (template == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Template unavailable"
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(statusMessage = "Applying ${template.name}")
                )
            }

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = "$TemplatePresetPrefix${template.id}",
                        profileId = activeProfileId(),
                        name = template.name,
                        config = template.config,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Template apply failed"
                        )
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = "Using ${template.name}"
            )
        }
    }

    private suspend fun loadRulePresetTemplates(): List<RulePresetTemplateRecord> {
        val templates = withBackendSession { accessToken ->
            backendApi.listRulePresetTemplates(accessToken).rulePresetTemplates
        } ?: return rulePresetTemplateCache

        rulePresetTemplateCache = templates
        _state.update { current ->
            current.copy(
                rulePresets = current.rulePresets.copy(
                    templates = templates.map { template -> template.toSummary() }
                )
            )
        }
        return templates
    }


    fun selectRulePreset(presetId: String) {
        viewModelScope.launch {
            val selected = _state.value.rulePresets.presets.firstOrNull { it.id == presetId } ?: return@launch
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Switching to ${selected.name}"
                    )
                )
            }

            val existing = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets.firstOrNull { it.id == presetId }
            }

            if (existing == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Preset unavailable"
                        )
                    )
                }
                return@launch
            }

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = existing.id,
                        profileId = existing.profileId,
                        name = existing.name,
                        config = existing.config,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Preset switch failed"
                        )
                    )
                }
                return@launch
            }

            applyRulePresetUpdate(
                preset = preset,
                statusMessage = "Using ${preset.name}"
            )
        }
    }

    fun updateStreamChannelId(channelId: String) {
        _state.update { current ->
            current.copy(
                streamSelector = current.streamSelector.copy(
                    channelId = channelId.take(120),
                    statusMessage = null,
                    channelMismatch = false,
                    testStatusMessage = null,
                    lastTestMessageId = null,
                    isCheckingModeratorPermissions = false,
                    permissionCheckStatusMessage = null,
                    lastPermissionCheckAt = null
                )
            )
        }
    }

    fun useConnectedYouTubeChannel() {
        val connectedChannelId = _state.value.streamSelector.connectedChannelId?.trim().orEmpty()
        if (connectedChannelId.isBlank()) {
            _state.update { current ->
                current.copy(
                    streamSelector = current.streamSelector.copy(
                        statusMessage = "Connect YouTube to select a channel"
                    )
                )
            }
            return
        }

        _state.update { current ->
            val label = current.streamSelector.connectedChannelTitle
                ?.takeIf { it.isNotBlank() }
                ?: connectedChannelId
            current.copy(
                streamTitle = if (current.streamSelector.channelMismatch) "No active stream connected" else current.streamTitle,
                videoId = if (current.streamSelector.channelMismatch) null else current.videoId,
                liveChatId = if (current.streamSelector.channelMismatch) null else current.liveChatId,
                syncStatus = if (current.streamSelector.channelMismatch) SyncStatus.Offline else current.syncStatus,
                streamSelector = current.streamSelector.copy(
                    channelId = connectedChannelId.take(120),
                    channelMismatch = false,
                    discoveryStatus = if (current.streamSelector.discoveryStatus == "channel_mismatch") {
                        "idle"
                    } else {
                        current.streamSelector.discoveryStatus
                    },
                    statusMessage = "Using connected channel: $label",
                    testStatusMessage = null,
                    lastTestMessageId = null,
                    isCheckingModeratorPermissions = false,
                    permissionCheckStatusMessage = null,
                    lastPermissionCheckAt = null,
                    broadcasts = if (current.streamSelector.channelMismatch) emptyList() else current.streamSelector.broadcasts
                )
            )
        }
    }

    fun refreshStreamSelector() {
        viewModelScope.launch {
            val channelId = _state.value.streamSelector.channelId.trim()
            if (channelId.isBlank()) {
                _state.update { current ->
                    current.copy(
                        streamSelector = current.streamSelector.copy(
                            isLoading = false,
                            statusMessage = "Enter a channel ID"
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    streamSelector = current.streamSelector.copy(
                        isLoading = true,
                        statusMessage = "Checking account"
                    )
                )
            }

            val accountStatus = withBackendSession { accessToken ->
                backendApi.youtubeAccountStatus(accessToken)
            }

            if (accountStatus == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = backendFailureStatus(lastBackendSessionFailure),
                        streamSelector = current.streamSelector.copy(
                            isLoading = false,
                            discoveryStatus = streamFailureDiscoveryStatus(lastBackendSessionFailure),
                            activeBroadcastCount = 0,
                            needsSelection = false,
                            channelMismatch = false,
                            statusMessage = streamLookupFailureMessage(lastBackendSessionFailure)
                        )
                    )
                }
                return@launch
            }

            val connectedChannelId = accountStatus.connectedChannelIdOrNull()
            val connectedChannelTitle = accountStatus.connectedChannelTitleOrNull()
            val isMismatch = accountStatus.hasChannelMismatch(channelId)

            _state.update { current ->
                current.copy(
                    streamSelector = current.streamSelector.copy(
                        statusMessage = if (isMismatch) {
                            "Bot watching: ${accountStatus.connectedChannelLabel()} -> $channelId"
                        } else {
                            "Finding streams"
                        },
                        source = accountStatus.source,
                        connectedChannelId = connectedChannelId,
                        connectedChannelTitle = connectedChannelTitle,
                        channelMismatch = isMismatch
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.discoverYouTubeLiveChat(
                    accessToken = accessToken,
                    channelId = channelId,
                    includeScheduled = true
                )
            }

            if (result == null) {
                val channelMismatch = lastBackendSessionFailure.isYouTubeChannelMismatch()
                _state.update { current ->
                    current.copy(
                        streamTitle = if (channelMismatch) "No active stream connected" else current.streamTitle,
                        videoId = if (channelMismatch) null else current.videoId,
                        liveChatId = if (channelMismatch) null else current.liveChatId,
                        syncStatus = if (channelMismatch) SyncStatus.Offline else backendFailureStatus(lastBackendSessionFailure),
                        streamSelector = current.streamSelector.copy(
                            isLoading = false,
                            discoveryStatus = streamFailureDiscoveryStatus(lastBackendSessionFailure),
                            activeBroadcastCount = 0,
                            needsSelection = false,
                            connectedChannelId = connectedChannelId,
                            connectedChannelTitle = connectedChannelTitle,
                            channelMismatch = channelMismatch,
                            statusMessage = streamLookupFailureMessage(lastBackendSessionFailure),
                            broadcasts = if (channelMismatch) emptyList() else current.streamSelector.broadcasts
                        )
                    )
                }
                return@launch
            }

            val broadcasts = result.broadcasts.map { it.toSummary() }
            val activeStream = result.activeChat?.let { activeChat ->
                broadcasts.firstOrNull { stream -> stream.videoId == activeChat.videoId }
            }
            _state.update { current ->
                val selectedStillActive = broadcasts.any { stream ->
                    stream.videoId == current.videoId && !stream.liveChatId.isNullOrBlank()
                }
                val detectedNewActiveStream = activeStream != null &&
                    (current.videoId != activeStream.videoId || current.liveChatId != activeStream.liveChatId)
                val clearConnectionCheck = detectedNewActiveStream || (!selectedStillActive && activeStream == null)
                current.copy(
                    streamTitle = activeStream?.title ?: if (selectedStillActive) current.streamTitle else "No active stream connected",
                    videoId = activeStream?.videoId ?: current.videoId.takeIf { selectedStillActive },
                    liveChatId = activeStream?.liveChatId ?: current.liveChatId.takeIf { selectedStillActive },
                    syncStatus = when {
                        activeStream != null -> SyncStatus.Ready
                        selectedStillActive -> current.syncStatus
                        else -> SyncStatus.Offline
                    },
                    streamSelector = current.streamSelector.copy(
                        isLoading = false,
                        source = result.source,
                        discoveryStatus = result.status,
                        activeBroadcastCount = result.activeBroadcastCount,
                        needsSelection = result.needsSelection,
                        connectedChannelId = connectedChannelId,
                        connectedChannelTitle = connectedChannelTitle,
                        channelMismatch = false,
                        statusMessage = streamSelectorMessage(
                            broadcasts = broadcasts,
                            status = result.status,
                            source = result.source,
                            needsSelection = result.needsSelection,
                            activeBroadcastCount = result.activeBroadcastCount
                        ),
                        testStatusMessage = if (clearConnectionCheck) null else current.streamSelector.testStatusMessage,
                        lastTestMessageId = if (clearConnectionCheck) null else current.streamSelector.lastTestMessageId,
                        isCheckingModeratorPermissions = false,
                        permissionCheckStatusMessage = if (clearConnectionCheck) null else current.streamSelector.permissionCheckStatusMessage,
                        lastPermissionCheckAt = if (clearConnectionCheck) null else current.streamSelector.lastPermissionCheckAt,
                        broadcasts = broadcasts
                    ),
                    recentActions = if (!detectedNewActiveStream) {
                        current.recentActions
                    } else {
                        listOf(
                            ActionLogItem(
                                id = "stream-detected-${activeStream.videoId}-${System.currentTimeMillis()}",
                                label = "Active stream detected",
                                detail = activeStream.title
                            )
                        ) + current.recentActions
                    }
                )
            }
        }
    }

    fun selectStream(videoId: String) {
        val snapshot = _state.value
        val stream = snapshot.streamSelector.broadcasts.firstOrNull { it.videoId == videoId } ?: return
        val channelName = snapshot.streamSelector.channelId.trim().ifBlank { "Your Channel" }
        val liveChatId = stream.liveChatId

        if (liveChatId.isNullOrBlank()) {
            _state.update { current ->
                current.copy(
                    streamTitle = stream.title,
                    channelName = channelName,
                    videoId = stream.videoId,
                    liveChatId = null,
                    syncStatus = SyncStatus.Offline,
                    streamSelector = current.streamSelector.copy(
                        statusMessage = "Scheduled stream selected; refresh when live",
                        testStatusMessage = null,
                        lastTestMessageId = null,
                        isCheckingModeratorPermissions = false,
                        permissionCheckStatusMessage = null,
                        lastPermissionCheckAt = null
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "stream-scheduled-${System.currentTimeMillis()}",
                            label = "Scheduled stream selected",
                            detail = stream.title
                        )
                    ) + current.recentActions
                )
            }
            return
        }
        val activeLiveChatId = liveChatId.orEmpty().takeIf { it.isNotBlank() } ?: return

        _state.update { current ->
            current.copy(
                streamTitle = stream.title,
                channelName = channelName,
                videoId = stream.videoId,
                liveChatId = activeLiveChatId,
                syncStatus = SyncStatus.Ready,
                streamSelector = current.streamSelector.copy(
                    statusMessage = "Active stream selected",
                    testStatusMessage = null,
                    lastTestMessageId = null,
                    isCheckingModeratorPermissions = false,
                    permissionCheckStatusMessage = null,
                    lastPermissionCheckAt = null
                ),
                recentActions = listOf(
                    ActionLogItem(
                        id = "stream-selected-${System.currentTimeMillis()}",
                        label = "Selected stream",
                        detail = stream.title
                    )
                ) + current.recentActions
            )
        }
        persistSelectedStream(
            liveChatId = activeLiveChatId,
            videoId = stream.videoId,
            streamTitle = stream.title,
            channelName = channelName
        )
    }

    fun updateConnectionTestMessage(message: String) {
        _state.update { current ->
            current.copy(
                streamSelector = current.streamSelector.copy(
                    testMessage = message.take(200),
                    testStatusMessage = null,
                    lastTestMessageId = null,
                    isCheckingModeratorPermissions = false,
                    permissionCheckStatusMessage = null,
                    lastPermissionCheckAt = null
                )
            )
        }
    }

    fun sendConnectionTestMessage() {
        viewModelScope.launch {
            val snapshot = _state.value
            val liveChatId = snapshot.liveChatId?.takeIf { it.isNotBlank() }
            val text = snapshot.streamSelector.testMessage.trim()

            if (liveChatId == null) {
                _state.update { current ->
                    current.copy(
                        streamSelector = current.streamSelector.copy(
                            testStatusMessage = "Select an active stream first"
                        )
                    )
                }
                return@launch
            }

            if (text.isBlank()) {
                _state.update { current ->
                    current.copy(
                        streamSelector = current.streamSelector.copy(
                            testStatusMessage = "Test message cannot be blank"
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    streamSelector = current.streamSelector.copy(
                        isTestingConnection = true,
                        testStatusMessage = "Sending test message",
                        isCheckingModeratorPermissions = false,
                        permissionCheckStatusMessage = null,
                        lastPermissionCheckAt = null
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.sendYouTubeTestMessage(
                    accessToken = accessToken,
                    request = YouTubeTestMessageRequest(
                        liveChatId = liveChatId,
                        text = text
                    )
                )
            }

            if (result == null || result.messageId.isBlank()) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        streamSelector = current.streamSelector.copy(
                            isTestingConnection = false,
                            testStatusMessage = "Test message failed",
                            isCheckingModeratorPermissions = false,
                            permissionCheckStatusMessage = null,
                            lastPermissionCheckAt = null
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    streamSelector = current.streamSelector.copy(
                        isTestingConnection = false,
                        testStatusMessage = "Test message sent",
                        lastTestMessageId = result.messageId,
                        isCheckingModeratorPermissions = false,
                        permissionCheckStatusMessage = "Run moderator check to verify delete access",
                        lastPermissionCheckAt = null
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "connection-test-${result.messageId}",
                            label = "YouTube test sent",
                            detail = result.messageId
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun runModeratorPermissionCheck() {
        viewModelScope.launch {
            val messageId = _state.value.streamSelector.lastTestMessageId?.takeIf { it.isNotBlank() }
            if (messageId == null) {
                _state.update { current ->
                    current.copy(
                        streamSelector = current.streamSelector.copy(
                            permissionCheckStatusMessage = "Send a test message first"
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    streamSelector = current.streamSelector.copy(
                        isCheckingModeratorPermissions = true,
                        permissionCheckStatusMessage = "Checking moderator delete access"
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.deleteYouTubeLiveChatMessage(
                    accessToken = accessToken,
                    request = YouTubeMessageDeleteRequest(
                        messageId = messageId,
                        reason = "moderator_permission_check"
                    )
                )
            }

            if (result == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        streamSelector = current.streamSelector.copy(
                            isCheckingModeratorPermissions = false,
                            permissionCheckStatusMessage = "Moderator check failed"
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                val checkedAt = result.deletedAt.ifBlank { Instant.now().toString() }
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    streamSelector = current.streamSelector.copy(
                        isCheckingModeratorPermissions = false,
                        permissionCheckStatusMessage = "Moderator delete check passed",
                        lastPermissionCheckAt = checkedAt
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "moderator-check-${result.messageId}-${System.currentTimeMillis()}",
                            label = "Moderator check passed",
                            detail = "Deleted test message ${result.messageId}"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun sendSubscriberOnlyRecommendation() {
        viewModelScope.launch {
            val snapshot = _state.value
            val liveChatId = snapshot.liveChatId?.takeIf { it.isNotBlank() }

            if (liveChatId == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "subscriber-recommendation-no-stream-${System.currentTimeMillis()}",
                                label = "Recommendation not sent",
                                detail = "Select an active stream before sending"
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            _state.update { current -> current.copy(syncStatus = SyncStatus.Syncing) }
            val result = withBackendSession { accessToken ->
                backendApi.sendYouTubeTestMessage(
                    accessToken = accessToken,
                    request = YouTubeTestMessageRequest(
                        liveChatId = liveChatId,
                        text = SubscriberOnlyRecommendationMessage
                    )
                )
            }

            if (result == null || result.messageId.isBlank()) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "subscriber-recommendation-failed-${System.currentTimeMillis()}",
                                label = "Recommendation failed",
                                detail = "Could not send subscribers-only recommendation"
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            recordRuntimeRecommendationEvent(liveChatId)
            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    recentActions = listOf(
                        ActionLogItem(
                            id = "subscriber-recommendation-${result.messageId}",
                            label = "Recommendation sent",
                            detail = "Suggested subscribers-only or members-only mode"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun selectLogFilter(filter: LogEntryFilter) {
        _state.update { current ->
            current.copy(logs = current.logs.copy(filter = filter))
        }
    }

    fun exportLogs() {
        val current = _state.value
        val entries = current.logs.entries
            .filter { entry -> current.logs.filter.matchesForExport(entry.kind) }
            .sortedByDescending { entry -> entry.createdAtMillis }

        if (entries.isEmpty()) {
            _state.update { state ->
                state.copy(logs = state.logs.copy(statusMessage = "No logs to export"))
            }
            return
        }

        val exportedAt = Instant.now().toString()
        val subject = "ChatMod logs ${current.logs.filter.label} $exportedAt"
        val text = entries.toCsvExport(exportedAt = exportedAt, filter = current.logs.filter)
        val requestId = "log-export-${System.currentTimeMillis()}"

        _state.update { state ->
            state.copy(
                logs = state.logs.copy(
                    statusMessage = "Preparing log export",
                    pendingShare = LogExportShareRequest(
                        id = requestId,
                        subject = subject,
                        text = text
                    )
                )
            )
        }
    }

    fun finishLogExportShare(id: String, launched: Boolean) {
        _state.update { current ->
            if (current.logs.pendingShare?.id != id) {
                current
            } else {
                current.copy(
                    logs = current.logs.copy(
                        pendingShare = null,
                        statusMessage = if (launched) "Log export ready to share" else "No app available for log export"
                    ),
                    recentActions = if (launched) {
                        listOf(
                            ActionLogItem(
                                id = "log-export-finished-$id",
                                label = "Exported logs",
                                detail = current.logs.filter.label
                            )
                        ) + current.recentActions
                    } else {
                        current.recentActions
                    }
                )
            }
        }
    }

    fun finishRuntimeRecovery(id: String, launched: Boolean) {
        _state.update { current ->
            if (current.pendingRuntimeRecovery?.id != id) {
                current
            } else {
                current.copy(
                    pendingRuntimeRecovery = null,
                    botRunning = if (launched) current.botRunning else false,
                    syncStatus = if (launched) current.syncStatus else SyncStatus.Failed,
                    settings = current.settings.copy(
                        statusMessage = if (launched) {
                            "Active bot runtime recovered"
                        } else {
                            "Open ChatMod and start the bot again"
                        }
                    ),
                    recentActions = if (launched) {
                        listOf(
                            ActionLogItem(
                                id = "runtime-recovered-$id",
                                label = "Runtime recovered",
                                detail = "Foreground service restart requested"
                            )
                        ) + current.recentActions
                    } else {
                        current.recentActions
                    }
                )
            }
        }
    }

    fun refreshProAnalytics() {
        viewModelScope.launch {
            val profileId = activeProfileId()
            _state.update { current ->
                current.copy(
                    logs = current.logs.copy(
                        proAnalytics = current.logs.proAnalytics.copy(
                            isLoading = true,
                            statusMessage = "Loading 30-day cross-stream trends"
                        ),
                        aiChatSummary = current.logs.aiChatSummary.copy(
                            isLoading = current.billing.aiSuggestions,
                            statusMessage = if (current.billing.aiSuggestions) {
                                "Loading after-stream chat summary"
                            } else {
                                "Creator plan unlocks after-stream chat summaries."
                            }
                        )
                    )
                )
            }

            val summary = withBackendSession { accessToken ->
                backendApi.streamSessionAnalyticsSummary(
                    accessToken = accessToken,
                    profileId = profileId,
                    days = ProAnalyticsRangeDays
                )
            }

            if (summary == null) {
                _state.update { current ->
                    current.copy(
                        logs = current.logs.copy(
                            proAnalytics = current.logs.proAnalytics.copy(
                                isLoading = false,
                                statusMessage = "Cloud analytics could not be loaded"
                            )
                        )
                    )
                }
                return@launch
            }

            val newestSessionId = summary.byStream.firstOrNull()?.sessionId
            val aiSummary = if (_state.value.billing.aiSuggestions && newestSessionId != null) {
                withBackendSession { accessToken ->
                    backendApi.streamChatSummary(
                        accessToken = accessToken,
                        sessionId = newestSessionId
                    )
                }
            } else {
                null
            }

            _state.update { current ->
                current.copy(
                    logs = current.logs.copy(
                        proAnalytics = summary.toProAnalyticsPanelState(),
                        aiChatSummary = when {
                            !current.billing.aiSuggestions -> current.logs.aiChatSummary.copy(
                                isLoading = false,
                                statusMessage = "Creator plan unlocks after-stream chat summaries."
                            )
                            newestSessionId == null -> AiChatSummaryPanelState(
                                statusMessage = "Sync a completed stream to generate an after-stream summary."
                            )
                            aiSummary != null -> aiSummary.toAiChatSummaryPanelState()
                            else -> current.logs.aiChatSummary.copy(
                                isLoading = false,
                                statusMessage = "After-stream chat summary could not be loaded."
                            )
                        }
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "pro-analytics-${System.currentTimeMillis()}",
                            label = "Refreshed analytics",
                            detail = "${summary.sessionCount} streams in ${summary.rangeDays} days"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun markFalsePositive(logId: String) {
        val entry = _state.value.logs.entries.firstOrNull { it.id == logId && it.reviewCandidate }
        if (entry == null) {
            _state.update { current ->
                current.copy(logs = current.logs.copy(statusMessage = "Review item is no longer available"))
            }
            return
        }

        viewModelScope.launch {
            val note = "Marked from Logs false-positive review"
            val review = saveFalsePositiveReview(logId, entry, note)
            _state.update { current ->
                if (!review.localSaved) {
                    current.copy(logs = current.logs.copy(statusMessage = "Could not save false-positive review"))
                } else {
                    current.copy(
                        logs = current.logs.copy(
                            statusMessage = if (review.cloudSynced) {
                                "Marked false positive"
                            } else {
                                "Marked false positive locally"
                            }
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "false-positive-$logId-${System.currentTimeMillis()}",
                                label = "Marked false positive",
                                detail = if (review.cloudSynced) {
                                    "${entry.ruleLabelForReview()} synced to cloud audit log"
                                } else {
                                    "${entry.ruleLabelForReview()} saved on this phone"
                                }
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun tuneRuleFromFalsePositive(logId: String) {
        val entry = _state.value.logs.entries.firstOrNull { it.id == logId && it.reviewCandidate }
        if (entry == null) {
            _state.update { current ->
                current.copy(logs = current.logs.copy(statusMessage = "Review item is no longer available"))
            }
            return
        }

        val reason = entry.reason?.removePrefix("rate_limited:")?.takeIf { it.isNotBlank() }
        if (reason == null) {
            _state.update { current ->
                current.copy(logs = current.logs.copy(statusMessage = "No rule reason available to tune"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    rulePresets = current.rulePresets.copy(
                        isLoading = true,
                        statusMessage = "Preparing rule tune"
                    )
                )
            }

            val existingPresets = withBackendSession { accessToken ->
                backendApi.listRulePresets(accessToken, activeProfileId()).rulePresets
            }
            if (existingPresets == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Could not load active preset"
                        ),
                        logs = current.logs.copy(statusMessage = "Preset tuning needs backend sync"),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "rule-tune-load-failed-$logId-${System.currentTimeMillis()}",
                                label = "Rule tune failed",
                                detail = entry.ruleLabelForReview()
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            val activePreset = existingPresets
                .firstOrNull { preset -> preset.id == _state.value.rulePresets.selectedPresetId }
                ?: existingPresets.firstOrNull { preset -> preset.isDefault }
                ?: existingPresets.firstOrNull()
            val baseProfile = activePreset?.config ?: DefaultModerationProfile
            val tuning = baseProfile.tunedForFalsePositive(reason)
            if (tuning == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "No safe one-tap tune for this rule"
                        ),
                        logs = current.logs.copy(statusMessage = "No safe one-tap tune for ${entry.ruleLabelForReview()}")
                    )
                }
                return@launch
            }

            val preset = withBackendSession { accessToken ->
                backendApi.saveRulePreset(
                    accessToken = accessToken,
                    request = RulePresetSyncRequest(
                        id = activePreset?.id ?: StarterPresetId,
                        profileId = activePreset?.profileId ?: activeProfileId(),
                        name = activePreset?.name ?: "Starter moderation",
                        config = tuning.profile,
                        isDefault = true
                    )
                )
            }

            if (preset == null) {
                _state.update { current ->
                    current.copy(
                        rulePresets = current.rulePresets.copy(
                            isLoading = false,
                            statusMessage = "Preset tuning failed"
                        ),
                        logs = current.logs.copy(statusMessage = "Could not save tuned preset")
                    )
                }
                return@launch
            }

            val review = saveFalsePositiveReview(
                logId = logId,
                entry = entry,
                note = "Tuned preset from false positive: ${tuning.detail}"
            )
            applyRulePresetUpdate(
                preset = preset,
                statusMessage = "Tuned ${tuning.detail}"
            )
            _state.update { current ->
                current.copy(
                    logs = current.logs.copy(
                        statusMessage = if (review.localSaved) {
                            "Tuned preset and marked false positive"
                        } else {
                            "Tuned preset; review status not saved"
                        }
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "rule-tune-$logId-${System.currentTimeMillis()}",
                            label = "Tuned noisy rule",
                            detail = if (review.cloudSynced) {
                                "${tuning.detail} synced"
                            } else {
                                tuning.detail
                            }
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    private suspend fun saveFalsePositiveReview(
        logId: String,
        entry: LogEntrySummary,
        note: String
    ): FalsePositiveReviewSave {
        val sessionId = entry.sessionId
        val localSaved = logStore.markFalsePositive(
            logId = logId,
            note = note
        )
        val cloudSynced = if (localSaved && sessionId != null) {
            withBackendSession { accessToken ->
                backendApi.reviewModerationActionLog(
                    accessToken = accessToken,
                    sessionId = sessionId,
                    actionId = logId,
                    request = ModerationActionReviewRequest(
                        reviewStatus = FalsePositiveReviewStatus,
                        reviewNote = note
                    )
                )
            } != null
        } else {
            false
        }

        return FalsePositiveReviewSave(localSaved = localSaved, cloudSynced = cloudSynced)
    }

    fun setEmergencyMode(enabled: Boolean) {
        updatePersistedSetting(
            savingMessage = if (enabled) "Saving emergency mode" else "Saving normal mode",
            savedLabel = if (enabled) "Emergency mode enabled" else "Emergency mode disabled",
            savedDetail = if (enabled) "Live controls saved for this phone" else "Live controls returned to normal",
            localUpdate = { it.copy(emergencyMode = enabled) },
            persist = { it.setEmergencyMode(enabled) }
        )
    }

    fun setLinkLockdown(enabled: Boolean) {
        updatePersistedSetting(
            savingMessage = if (enabled) "Saving link lockdown" else "Saving link policy",
            savedLabel = if (enabled) "Link lockdown enabled" else "Link lockdown disabled",
            savedDetail = if (enabled) "Links are locked down in local settings" else "Normal link policy restored",
            localUpdate = { it.copy(linkLockdown = enabled) },
            persist = { it.setLinkLockdown(enabled) }
        )
    }

    fun setReducedMotion(enabled: Boolean) {
        updatePersistedSetting(
            savingMessage = if (enabled) "Saving reduced motion" else "Saving motion preference",
            savedLabel = if (enabled) "Reduced motion enabled" else "Reduced motion disabled",
            savedDetail = if (enabled) "Motion preference saved locally" else "Expressive motion preference restored",
            localUpdate = { it.copy(reducedMotion = enabled) },
            persist = { it.setReducedMotion(enabled) }
        )
    }

    fun setHighContrast(enabled: Boolean) {
        updatePersistedSetting(
            savingMessage = if (enabled) "Saving high contrast" else "Saving contrast preference",
            savedLabel = if (enabled) "High contrast enabled" else "High contrast disabled",
            savedDetail = if (enabled) "Sharper status colors and surfaces saved locally" else "Dynamic color preference restored",
            localUpdate = { it.copy(highContrast = enabled) },
            persist = { it.setHighContrast(enabled) }
        )
    }

    fun setLowDataMode(enabled: Boolean) {
        updatePersistedSetting(
            savingMessage = if (enabled) "Saving low-data mode" else "Saving normal data mode",
            savedLabel = if (enabled) "Low-data mode enabled" else "Low-data mode disabled",
            savedDetail = if (enabled) "Bot polling will use fewer network checks" else "Bot polling returned to normal",
            localUpdate = { it.copy(lowDataMode = enabled) },
            persist = { it.setLowDataMode(enabled) }
        )
    }

    fun setShareUsageAnalytics(enabled: Boolean) {
        updatePersistedSetting(
            savingMessage = if (enabled) "Saving analytics choice" else "Turning analytics off",
            savedLabel = if (enabled) "Usage analytics enabled" else "Usage analytics disabled",
            savedDetail = if (enabled) "Private beta events can be sent" else "Private beta events stay on this phone",
            localUpdate = { it.copy(shareUsageAnalytics = enabled) },
            persist = { it.setShareUsageAnalytics(enabled) }
        )
    }

    fun refreshDiscordWebhook() {
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isDiscordBusy = true,
                        discordStatusMessage = "Loading Discord alerts"
                    )
                )
            }

            val config = withBackendSession { accessToken ->
                backendApi.discordWebhookConfig(accessToken, profileId)
            }

            _state.update { current ->
                if (config == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isDiscordBusy = false,
                            discordStatusMessage = discordFailureMessage("load Discord alerts")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.withDiscordConfig(
                            config = config,
                            statusMessage = if (config.configured) "Discord alerts loaded" else "Discord alerts not connected",
                            clearUrlText = false
                        )
                    )
                }
            }
        }
    }

    fun updateDiscordWebhookUrl(value: String) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    discordWebhookUrlText = value.take(500),
                    discordStatusMessage = null
                )
            )
        }
    }

    fun setDiscordWebhookEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    discordWebhookEnabled = enabled,
                    discordStatusMessage = null
                )
            )
        }
    }

    fun setDiscordAlertModerationActions(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    discordAlertModerationActions = enabled,
                    discordStatusMessage = null
                )
            )
        }
    }

    fun setDiscordAlertRuntimeStatus(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    discordAlertRuntimeStatus = enabled,
                    discordStatusMessage = null
                )
            )
        }
    }

    fun saveDiscordWebhook() {
        val snapshot = _state.value.settings
        val profileId = activeProfileId()
        val webhookUrl = snapshot.discordWebhookUrlText.trim().takeIf { it.isNotBlank() }
        if (!snapshot.discordWebhookConfigured && webhookUrl == null) {
            _state.update { current ->
                current.copy(settings = current.settings.copy(discordStatusMessage = "Paste a Discord webhook URL first"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isDiscordBusy = true,
                        discordStatusMessage = "Saving Discord alerts"
                    )
                )
            }

            val config = withBackendSession { accessToken ->
                backendApi.upsertDiscordWebhook(
                    accessToken = accessToken,
                    request = DiscordWebhookUpsertRequest(
                        profileId = profileId,
                        webhookUrl = webhookUrl,
                        enabled = snapshot.discordWebhookEnabled,
                        alertModerationActions = snapshot.discordAlertModerationActions,
                        alertRuntimeStatus = snapshot.discordAlertRuntimeStatus
                    )
                )
            }

            _state.update { current ->
                if (config == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isDiscordBusy = false,
                            discordStatusMessage = discordFailureMessage("save Discord alerts")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.withDiscordConfig(
                            config = config,
                            statusMessage = "Discord alerts saved",
                            clearUrlText = true
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "discord-save-${System.currentTimeMillis()}",
                                label = "Discord alerts saved",
                                detail = if (config.enabled) "Webhook enabled for this profile" else "Webhook saved but disabled"
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun testDiscordWebhook() {
        val snapshot = _state.value.settings
        if (!snapshot.discordWebhookConfigured) {
            _state.update { current ->
                current.copy(settings = current.settings.copy(discordStatusMessage = "Save a Discord webhook before testing"))
            }
            return
        }

        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isDiscordBusy = true,
                        discordStatusMessage = "Sending Discord test"
                    )
                )
            }

            val result = withBackendSession { accessToken ->
                backendApi.testDiscordWebhook(accessToken, profileId)
            }

            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isDiscordBusy = false,
                        discordStatusMessage = when {
                            result == null -> discordFailureMessage("send Discord test")
                            result.sent -> "Discord test sent"
                            else -> "Discord test skipped: ${result.skippedReason.discordSkippedReasonLabel()}"
                        }
                    )
                )
            }
        }
    }

    fun deleteDiscordWebhook() {
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isDiscordBusy = true,
                        discordStatusMessage = "Removing Discord webhook"
                    )
                )
            }

            val deleted = withBackendSession { accessToken ->
                backendApi.deleteDiscordWebhook(accessToken, profileId)
                true
            }

            _state.update { current ->
                if (deleted == true) {
                    current.copy(
                        settings = current.settings.copy(
                            discordWebhookConfigured = false,
                            discordWebhookEnabled = false,
                            discordWebhookUrlText = "",
                            discordAlertModerationActions = true,
                            discordAlertRuntimeStatus = false,
                            isDiscordBusy = false,
                            discordStatusMessage = "Discord webhook removed"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "discord-delete-${System.currentTimeMillis()}",
                                label = "Discord webhook removed",
                                detail = "External alerts disabled for this profile"
                            )
                        ) + current.recentActions
                    )
                } else {
                    current.copy(
                        settings = current.settings.copy(
                            isDiscordBusy = false,
                            discordStatusMessage = discordFailureMessage("remove Discord webhook")
                        )
                    )
                }
            }
        }
    }

    fun refreshOverlayConfig() {
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isOverlayBusy = true,
                        overlayStatusMessage = "Loading OBS overlay"
                    )
                )
            }

            val config = withBackendSession { accessToken ->
                backendApi.overlayConfig(accessToken, profileId)
            }

            _state.update { current ->
                if (config == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isOverlayBusy = false,
                            overlayStatusMessage = overlayFailureMessage("load OBS overlay")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.withOverlayConfig(
                            config = config,
                            statusMessage = overlayLoadedMessage(config)
                        )
                    )
                }
            }
        }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    overlayEnabled = enabled,
                    overlayStatusMessage = null
                )
            )
        }
    }

    fun setOverlayShowModerationActions(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    overlayShowModerationActions = enabled,
                    overlayStatusMessage = null
                )
            )
        }
    }

    fun setOverlayShowRuntimeStatus(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    overlayShowRuntimeStatus = enabled,
                    overlayStatusMessage = null
                )
            )
        }
    }

    fun setOverlayShowViewerStats(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    overlayShowViewerStats = enabled,
                    overlayStatusMessage = null
                )
            )
        }
    }

    fun setOverlayShowRecentChat(enabled: Boolean) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    overlayShowRecentChat = enabled,
                    overlayStatusMessage = null
                )
            )
        }
    }

    fun saveOverlayConfig() {
        val snapshot = _state.value.settings
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isOverlayBusy = true,
                        overlayStatusMessage = "Saving OBS overlay"
                    )
                )
            }

            val config = withBackendSession { accessToken ->
                backendApi.upsertOverlayConfig(
                    accessToken = accessToken,
                    request = OverlayConfigUpdateRequest(
                        profileId = profileId,
                        enabled = snapshot.overlayEnabled,
                        theme = snapshot.overlayTheme,
                        activeSessionId = null,
                        showModerationActions = snapshot.overlayShowModerationActions,
                        showRuntimeStatus = snapshot.overlayShowRuntimeStatus,
                        showViewerStats = snapshot.overlayShowViewerStats,
                        showRecentChat = snapshot.overlayShowRecentChat
                    )
                )
            }

            _state.update { current ->
                if (config == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isOverlayBusy = false,
                            overlayStatusMessage = overlayFailureMessage("save OBS overlay")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.withOverlayConfig(
                            config = config,
                            statusMessage = overlaySavedMessage(config)
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "overlay-save-${System.currentTimeMillis()}",
                                label = "OBS overlay saved",
                                detail = if (config.enabled) "Browser source enabled" else "Browser source paused"
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun rotateOverlayToken() {
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isOverlayBusy = true,
                        overlayStatusMessage = "Rotating OBS overlay URL"
                    )
                )
            }

            val config = withBackendSession { accessToken ->
                backendApi.rotateOverlayToken(accessToken, profileId)
            }

            _state.update { current ->
                if (config == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isOverlayBusy = false,
                            overlayStatusMessage = overlayFailureMessage("rotate OBS overlay URL")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.withOverlayConfig(
                            config = config,
                            statusMessage = "Fresh OBS URL ready"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "overlay-rotate-${System.currentTimeMillis()}",
                                label = "OBS overlay URL rotated",
                                detail = "Old browser source URL invalidated"
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun refreshTeamAccess() {
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isTeamBusy = true,
                        teamStatusMessage = "Loading team access"
                    )
                )
            }

            val memberList = withBackendSession { accessToken ->
                backendApi.listTeamMembers(accessToken, profileId)
            }
            val memberships = withBackendSession { accessToken ->
                backendApi.listTeamMemberships(accessToken).memberships
            }

            _state.update { current ->
                current.copy(
                    settings = current.settings.copy(
                        isTeamBusy = false,
                        teamSeats = memberList?.teamSeats ?: current.settings.teamSeats,
                        teamExtraSeats = memberList?.extraSeats ?: current.settings.teamExtraSeats,
                        teamMembers = memberList?.members?.map { member -> member.toSummary() } ?: current.settings.teamMembers,
                        teamMemberships = memberships?.map { membership -> membership.toSummary() } ?: current.settings.teamMemberships,
                        teamStatusMessage = when {
                            memberList == null && memberships == null -> teamFailureMessage("load team access")
                            memberList?.members?.isEmpty() == false -> "Team access loaded"
                            memberships?.isNotEmpty() == true -> "Team memberships loaded"
                            else -> "No team members yet"
                        }
                    )
                )
            }
        }
    }

    fun updateTeamInviteName(value: String) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    teamInviteNameText = value.take(80),
                    teamStatusMessage = null
                )
            )
        }
    }

    fun updateTeamRedeemCode(value: String) {
        _state.update { current ->
            current.copy(
                settings = current.settings.copy(
                    teamRedeemCodeText = value.take(140),
                    teamStatusMessage = null
                )
            )
        }
    }

    fun createTeamInvite() {
        val profileId = activeProfileId()
        val displayName = _state.value.settings.teamInviteNameText.trim()
        if (displayName.isBlank()) {
            _state.update { current ->
                current.copy(settings = current.settings.copy(teamStatusMessage = "Enter a team member name first"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(settings = current.settings.copy(isTeamBusy = true, teamStatusMessage = "Creating team invite"))
            }

            val invite = withBackendSession { accessToken ->
                backendApi.createTeamInvite(
                    accessToken = accessToken,
                    profileId = profileId,
                    request = TeamInviteCreateRequest(
                        displayName = displayName,
                        permissions = TeamMemberPermissions()
                    )
                )
            }

            _state.update { current ->
                if (invite == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isTeamBusy = false,
                            teamStatusMessage = teamFailureMessage("create team invite")
                        )
                    )
                } else {
                    val members = (listOf(invite.member.toSummary()) + current.settings.teamMembers)
                        .distinctBy { member -> member.id }
                    current.copy(
                        settings = current.settings.copy(
                            isTeamBusy = false,
                            teamMembers = members,
                            teamInviteNameText = "",
                            teamLastInviteCode = invite.inviteCode,
                            teamStatusMessage = "Invite code ready"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "team-invite-${System.currentTimeMillis()}",
                                label = "Team invite created",
                                detail = invite.member.displayName
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun redeemTeamInvite() {
        val inviteCode = _state.value.settings.teamRedeemCodeText.trim()
        if (inviteCode.isBlank()) {
            _state.update { current ->
                current.copy(settings = current.settings.copy(teamStatusMessage = "Paste a team invite code first"))
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(settings = current.settings.copy(isTeamBusy = true, teamStatusMessage = "Redeeming team invite"))
            }

            val membership = withBackendSession { accessToken ->
                backendApi.redeemTeamInvite(
                    accessToken = accessToken,
                    request = TeamInviteRedeemRequest(inviteCode = inviteCode)
                )
            }

            _state.update { current ->
                if (membership == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isTeamBusy = false,
                            teamStatusMessage = teamFailureMessage("redeem team invite")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.copy(
                            isTeamBusy = false,
                            teamRedeemCodeText = "",
                            teamMemberships = (listOf(membership.toSummary()) + current.settings.teamMemberships)
                                .distinctBy { item -> item.id },
                            teamStatusMessage = "Joined ${membership.profileName}"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "team-redeem-${System.currentTimeMillis()}",
                                label = "Team invite redeemed",
                                detail = membership.profileName
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun revokeTeamMember(memberId: String) {
        val profileId = activeProfileId()
        viewModelScope.launch {
            _state.update { current ->
                current.copy(settings = current.settings.copy(isTeamBusy = true, teamStatusMessage = "Revoking team access"))
            }

            val revoked = withBackendSession { accessToken ->
                backendApi.revokeTeamMember(accessToken, profileId, memberId)
            }

            _state.update { current ->
                if (revoked == null) {
                    current.copy(
                        settings = current.settings.copy(
                            isTeamBusy = false,
                            teamStatusMessage = teamFailureMessage("revoke team access")
                        )
                    )
                } else {
                    current.copy(
                        settings = current.settings.copy(
                            isTeamBusy = false,
                            teamMembers = current.settings.teamMembers.map { member ->
                                if (member.id == memberId) revoked.toSummary() else member
                            },
                            teamStatusMessage = "Team access revoked"
                        ),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "team-revoke-${System.currentTimeMillis()}",
                                label = "Team access revoked",
                                detail = revoked.displayName
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    fun refreshFaqEntries() {
        if (!_state.value.billing.aiSuggestions) {
            _state.update { current ->
                current.copy(
                    faq = current.faq.copy(
                        isLoading = false,
                        statusMessage = "Creator plan unlocks FAQ reply suggestions."
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update { current ->
                current.copy(faq = current.faq.copy(isLoading = true, statusMessage = "Loading FAQ entries"))
            }
            val entries = withBackendSession { accessToken ->
                backendApi.listFaqEntries(accessToken, activeProfileId()).faqEntries
            }
            _state.update { current ->
                if (entries == null) {
                    current.copy(faq = current.faq.copy(isLoading = false, statusMessage = "FAQ entries could not be loaded"))
                } else {
                    current.copy(
                        faq = current.faq.copy(
                            isLoading = false,
                            entries = entries.map { entry -> entry.toSummary() },
                            statusMessage = if (entries.isEmpty()) "Add creator FAQ answers for repeated questions" else "FAQ knowledge base synced"
                        )
                    )
                }
            }
        }
    }

    fun startCreateFaqEntry() {
        _state.update { current ->
            current.copy(
                faq = current.faq.copy(
                    editingId = null,
                    questionText = "",
                    answerText = "",
                    keywordsText = "",
                    enabled = true,
                    errorMessage = null
                )
            )
        }
    }

    fun startEditFaqEntry(id: String) {
        _state.update { current ->
            val entry = current.faq.entries.firstOrNull { faqEntry -> faqEntry.id == id } ?: return@update current
            current.copy(
                faq = current.faq.copy(
                    editingId = entry.id,
                    questionText = entry.question,
                    answerText = entry.answer,
                    keywordsText = entry.keywords.joinToString(", "),
                    enabled = entry.enabled,
                    errorMessage = null
                )
            )
        }
    }

    fun updateFaqQuestion(value: String) {
        _state.update { current -> current.copy(faq = current.faq.copy(questionText = value, errorMessage = null)) }
    }

    fun updateFaqAnswer(value: String) {
        _state.update { current -> current.copy(faq = current.faq.copy(answerText = value, errorMessage = null)) }
    }

    fun updateFaqKeywords(value: String) {
        _state.update { current -> current.copy(faq = current.faq.copy(keywordsText = value, errorMessage = null)) }
    }

    fun updateFaqEnabled(value: Boolean) {
        _state.update { current -> current.copy(faq = current.faq.copy(enabled = value, errorMessage = null)) }
    }

    fun saveFaqEntry() {
        val faq = _state.value.faq
        val question = faq.questionText.trim()
        val answer = faq.answerText.trim()
        val keywords = faq.keywordsText
            .split(",")
            .map { keyword -> keyword.trim().lowercase() }
            .filter { keyword -> keyword.isNotBlank() }
            .distinct()
        val error = when {
            !_state.value.billing.aiSuggestions -> "Creator plan is required for FAQ replies."
            question.length < 3 -> "Add the viewer question."
            answer.isBlank() -> "Add the reply ChatMod should suggest."
            answer.length > 200 -> "FAQ replies must be 200 characters or fewer."
            keywords.any { keyword -> keyword.length < 2 } -> "Keywords must be at least 2 characters."
            else -> null
        }
        if (error != null) {
            _state.update { current -> current.copy(faq = current.faq.copy(errorMessage = error)) }
            return
        }

        viewModelScope.launch {
            val id = faq.editingId ?: "faq-${UUID.randomUUID()}"
            val saved = withBackendSession { accessToken ->
                backendApi.saveFaqEntry(
                    accessToken = accessToken,
                    request = FaqEntrySyncRequest(
                        id = id,
                        profileId = activeProfileId(),
                        question = question,
                        answer = answer,
                        keywords = keywords,
                        enabled = faq.enabled
                    )
                )
            }
            if (saved == null) {
                _state.update { current -> current.copy(faq = current.faq.copy(statusMessage = "FAQ entry could not be saved")) }
                return@launch
            }

            _state.update { current ->
                val updatedEntries = (current.faq.entries.filterNot { entry -> entry.id == saved.id } + saved.toSummary())
                    .sortedBy { entry -> entry.question }
                current.copy(
                    faq = current.faq.copy(
                        entries = updatedEntries,
                        editingId = null,
                        questionText = "",
                        answerText = "",
                        keywordsText = "",
                        enabled = true,
                        errorMessage = null,
                        statusMessage = "FAQ entry saved"
                    ),
                    recentActions = listOf(ActionLogItem("faq-${saved.id}", "Saved FAQ reply", saved.question)) + current.recentActions
                )
            }
        }
    }

    fun deleteFaqEntry(id: String) {
        val entry = _state.value.faq.entries.firstOrNull { faqEntry -> faqEntry.id == id } ?: return
        viewModelScope.launch {
            val deleted = withBackendSession { accessToken ->
                backendApi.deleteFaqEntry(accessToken, id)
                true
            } ?: false
            if (!deleted) {
                _state.update { current -> current.copy(faq = current.faq.copy(statusMessage = "FAQ entry could not be deleted")) }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    faq = current.faq.copy(
                        entries = current.faq.entries.filterNot { faqEntry -> faqEntry.id == id },
                        statusMessage = "FAQ entry deleted"
                    ),
                    recentActions = listOf(ActionLogItem("delete-faq-$id", "Deleted FAQ reply", entry.question)) + current.recentActions
                )
            }
        }
    }

    fun startCreateCommand() {
        _state.update { current ->
            current.copy(commandEditor = CommandEditorState(mode = EditorMode.Create))
        }
    }

    fun startEditCommand(id: String) {
        _state.update { current ->
            val command = current.commands.firstOrNull { it.id == id } ?: return@update current
            current.copy(
                commandEditor = CommandEditorState(
                    mode = EditorMode.Edit,
                    id = command.id,
                    name = command.name,
                    response = command.response,
                    aliasesText = command.aliases.joinToString(", "),
                    cooldownSecondsText = command.cooldownSeconds.toString(),
                    accessLevel = command.accessLevel,
                    enabled = command.enabled
                )
            )
        }
    }

    fun updateCommandEditor(editor: CommandEditorState) {
        _state.update { current ->
            current.copy(commandEditor = editor.copy(errorMessage = null))
        }
    }

    fun dismissCommandEditor() {
        _state.update { current ->
            current.copy(commandEditor = null)
        }
    }

    fun saveCommandEditor(): Boolean {
        val editor = _state.value.commandEditor ?: return false
        val normalizedName = editor.name.trim().lowercase()
        val cooldown = editor.cooldownSecondsText.toIntOrNull()
        val aliases = editor.aliasesText
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        val error = when {
            !normalizedName.matches(CommandPattern) -> "Use a command like !discord."
            editor.response.trim().isBlank() -> "Add the reply the bot should send."
            cooldown == null || cooldown < 0 -> "Cooldown must be 0 or more seconds."
            aliases.any { !it.matches(CommandPattern) } -> "Aliases must look like !rules."
            else -> null
        }

        if (error != null) {
            _state.update { current -> current.copy(commandEditor = editor.copy(errorMessage = error)) }
            return false
        }

        val validCooldown = requireNotNull(cooldown)
        val saved = CommandSummary(
            id = editor.id ?: "cmd-${UUID.randomUUID()}",
            name = normalizedName,
            response = editor.response.trim(),
            aliases = aliases.distinct().filterNot { it == normalizedName },
            cooldownSeconds = validCooldown,
            accessLevel = editor.accessLevel,
            enabled = editor.enabled
        )

        viewModelScope.launch {
            commandTimerStore.upsertCommand(saved)
            _state.update { current ->
                current.copy(
                    commandEditor = null,
                    recentActions = listOf(
                        ActionLogItem(
                            id = "command-${saved.id}",
                            label = if (editor.mode == EditorMode.Edit) "Updated command" else "Created command",
                            detail = saved.name
                        )
                    ) + current.recentActions
                )
            }
        }
        return true
    }

    fun deleteCommand(id: String) {
        val command = _state.value.commands.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            commandTimerStore.deleteCommand(id)
            _state.update { current ->
                current.copy(
                    recentActions = listOf(
                        ActionLogItem("delete-$id", "Deleted command", command.name)
                    ) + current.recentActions
                )
            }
        }
    }

    fun sendCommandNow(id: String) {
        viewModelScope.launch {
            val snapshot = _state.value
            val command = snapshot.commands.firstOrNull { it.id == id } ?: return@launch
            val liveChatId = snapshot.liveChatId?.takeIf { it.isNotBlank() }

            if (liveChatId == null) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "command-send-no-stream-${System.currentTimeMillis()}",
                                label = "Command not sent",
                                detail = "Select an active stream before sending ${command.name}"
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            if (!command.enabled) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "command-send-disabled-${command.id}-${System.currentTimeMillis()}",
                                label = "Command disabled",
                                detail = "${command.name} is off"
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            _state.update { current -> current.copy(syncStatus = SyncStatus.Syncing) }
            val activeRuntimeStartedAt = settingsStore?.activeRuntime?.first()?.startedAtMillis
            val result = withBackendSession { accessToken ->
                backendApi.sendCommandToLiveChat(
                    accessToken = accessToken,
                    commandId = command.id,
                    request = CommandManualSendRequest(
                        liveChatId = liveChatId,
                        streamTitle = snapshot.streamTitle.takeIf { it.isNotBlank() },
                        streamStartedAt = activeRuntimeStartedAt?.let { Instant.ofEpochMilli(it).toString() }
                    )
                )
            }

            if (result == null || result.messageId.isBlank()) {
                _state.update { current ->
                    current.copy(
                        syncStatus = SyncStatus.Failed,
                        recentActions = listOf(
                            ActionLogItem(
                                id = "command-send-failed-${command.id}-${System.currentTimeMillis()}",
                                label = "Command send failed",
                                detail = command.name
                            )
                        ) + current.recentActions
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Ready,
                    recentActions = listOf(
                        ActionLogItem(
                            id = "command-send-${result.messageId}",
                            label = "Sent command",
                            detail = "${command.name} to ${snapshot.streamTitle.ifBlank { "selected stream" }}"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun startCreateTimer() {
        _state.update { current ->
            current.copy(timerEditor = TimerEditorState(mode = EditorMode.Create))
        }
    }

    fun startEditTimer(id: String) {
        _state.update { current ->
            val timer = current.timers.firstOrNull { it.id == id } ?: return@update current
            current.copy(
                timerEditor = TimerEditorState(
                    mode = EditorMode.Edit,
                    id = timer.id,
                    name = timer.name,
                    message = timer.message,
                    intervalMinutesText = timer.intervalMinutes.toString(),
                    minChatMessagesText = timer.minChatMessages.toString(),
                    quietHoursEnabled = timer.quietStartMinutes != null && timer.quietEndMinutes != null,
                    quietStartMinutesText = timer.quietStartMinutes?.toString().orEmpty(),
                    quietEndMinutesText = timer.quietEndMinutes?.toString().orEmpty(),
                    enabled = timer.enabled
                )
            )
        }
    }

    fun updateTimerEditor(editor: TimerEditorState) {
        _state.update { current ->
            current.copy(timerEditor = editor.copy(errorMessage = null))
        }
    }

    fun dismissTimerEditor() {
        _state.update { current ->
            current.copy(timerEditor = null)
        }
    }

    fun saveTimerEditor(): Boolean {
        val editor = _state.value.timerEditor ?: return false
        val interval = editor.intervalMinutesText.toIntOrNull()
        val minMessages = editor.minChatMessagesText.toIntOrNull()
        val quietStart = if (editor.quietHoursEnabled) editor.quietStartMinutesText.toIntOrNull() else null
        val quietEnd = if (editor.quietHoursEnabled) editor.quietEndMinutesText.toIntOrNull() else null
        val quietStartValue = quietStart ?: 0
        val quietEndValue = quietEnd ?: 0

        val error = when {
            editor.name.trim().isBlank() -> "Name this timer."
            editor.message.trim().isBlank() -> "Add the message the bot should send."
            interval == null || interval < 1 -> "Interval must be at least 1 minute."
            minMessages == null || minMessages < 0 -> "Minimum messages must be 0 or more."
            editor.quietHoursEnabled && quietStart == null -> "Quiet start must be a stream minute."
            editor.quietHoursEnabled && quietEnd == null -> "Quiet end must be a stream minute."
            editor.quietHoursEnabled && quietStartValue < 0 -> "Quiet start must be 0 or more."
            editor.quietHoursEnabled && quietEndValue <= quietStartValue -> "Quiet end must be after quiet start."
            editor.quietHoursEnabled && quietEndValue > MaxQuietWindowMinute -> "Quiet end must be within 24 hours."
            else -> null
        }

        if (error != null) {
            _state.update { current -> current.copy(timerEditor = editor.copy(errorMessage = error)) }
            return false
        }

        val validInterval = requireNotNull(interval)
        val validMinMessages = requireNotNull(minMessages)
        val saved = TimerSummary(
            id = editor.id ?: "timer-${UUID.randomUUID()}",
            name = editor.name.trim(),
            message = editor.message.trim(),
            intervalMinutes = validInterval,
            minChatMessages = validMinMessages,
            quietStartMinutes = quietStart,
            quietEndMinutes = quietEnd,
            enabled = editor.enabled
        )

        viewModelScope.launch {
            commandTimerStore.upsertTimer(saved)
            _state.update { current ->
                current.copy(
                    timerEditor = null,
                    recentActions = listOf(
                        ActionLogItem(
                            id = "timer-${saved.id}",
                            label = if (editor.mode == EditorMode.Edit) "Updated timer" else "Created timer",
                            detail = saved.name
                        )
                    ) + current.recentActions
                )
            }
        }
        return true
    }

    fun deleteTimer(id: String) {
        val timer = _state.value.timers.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            commandTimerStore.deleteTimer(id)
            _state.update { current ->
                current.copy(
                    recentActions = listOf(
                        ActionLogItem("delete-$id", "Deleted timer", timer.name)
                    ) + current.recentActions
                )
            }
        }
    }

    fun simulateProPurchase() {
        viewModelScope.launch {
            validateGooglePlayPurchase(
                productId = "chatmod_pro_monthly",
                purchaseToken = "demo-purchase-token",
                packageName = null,
                successLabel = "Updated entitlement",
                failureDetail = "Backend billing validation is not configured yet"
            )
        }
    }

    suspend fun validatePlayBillingPurchase(
        productId: String,
        purchaseToken: String,
        packageName: String?
    ): Boolean {
        return validateGooglePlayPurchase(
            productId = productId,
            purchaseToken = purchaseToken,
            packageName = packageName,
            successLabel = "Purchase validated",
            failureDetail = "Could not validate Google Play purchase"
        )
    }

    private suspend fun validateGooglePlayPurchase(
        productId: String,
        purchaseToken: String,
        packageName: String?,
        successLabel: String,
        failureDetail: String
    ): Boolean {
        val result = withBackendSession { accessToken ->
            backendApi.validateGooglePlayPurchase(
                accessToken = accessToken,
                request = GooglePlayPurchaseValidationRequest(
                    productId = productId,
                    purchaseToken = purchaseToken,
                    packageName = packageName
                )
            )
        }

        if (result == null) {
            _state.update { current ->
                current.copy(
                    syncStatus = SyncStatus.Failed,
                    recentActions = listOf(
                        ActionLogItem(
                            id = "billing-${System.currentTimeMillis()}",
                            label = "Purchase validation failed",
                            detail = failureDetail
                        )
                    ) + current.recentActions
                )
            }
            return false
        }

        val billing = result.entitlement.toBillingSummary()
        _state.update { current ->
            current.withBillingSummary(
                billing = billing,
                allLogEntries = latestLocalLogEntries,
                allUsers = latestLocalUsers
            ).copy(
                syncStatus = SyncStatus.Ready,
                recentActions = listOf(
                    ActionLogItem(
                        id = "billing-${System.currentTimeMillis()}",
                        label = successLabel,
                        detail = "${result.entitlement.plan} (${result.validationStatus})"
                    )
                ) + current.recentActions
            )
        }
        return true
    }

    fun refreshCloudBackups() {
        viewModelScope.launch {
            val backups = withBackendSession { accessToken ->
                backendApi.listBackups(accessToken).backups.map { it.toSummary() }
            } ?: return@launch

            _state.update { current ->
                current.copy(
                    account = current.account.copy(
                        cloudBackups = backups,
                        statusMessage = if (backups.isEmpty()) "No cloud backups yet" else "Cloud backups loaded"
                    )
                )
            }
        }
    }

    fun exportAccountData() {
        viewModelScope.launch {
            setAccountBusy(true, "Preparing account export")
            val export = withAccountBackendSession { accessToken -> backendApi.exportAccount(accessToken) }

            if (export == null) {
                setAccountBusy(false, "Account export failed")
                return@launch
            }

            _state.update { current ->
                current.copy(
                    account = current.account.copy(
                        isBusy = false,
                        statusMessage = "Account export ready",
                        lastExport = export.toReceipt()
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "account-export-${System.currentTimeMillis()}",
                            label = "Exported account data",
                            detail = "${export.profileCount} profiles, ${export.backupCount} backups"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun connectYouTube() {
        viewModelScope.launch {
            setAccountBusy(true, "Preparing YouTube sign-in")
            val connect = withAccountBackendSession { accessToken ->
                backendApi.youtubeConnectUrl(accessToken)
            }

            if (connect == null) {
                setAccountBusy(false, "YouTube sign-in unavailable")
                return@launch
            }

            val launchUrl = connect.url?.takeIf { connect.configured && it.isNotBlank() }
            _state.update { current ->
                current.copy(
                    account = current.account.copy(
                        isBusy = false,
                        statusMessage = if (launchUrl != null) {
                            "Opening Google sign-in"
                        } else {
                            "Google OAuth is not configured on this backend"
                        },
                        youtubeConnect = connect.toState(),
                        pendingBrowserLaunch = launchUrl?.let { url ->
                            BrowserLaunchRequest(
                                id = "youtube-connect-${System.currentTimeMillis()}",
                                url = url
                            )
                        }
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "youtube-connect-${System.currentTimeMillis()}",
                            label = if (launchUrl != null) "YouTube sign-in started" else "YouTube sign-in unavailable",
                            detail = connect.note ?: if (launchUrl != null) {
                                "Continue in the browser"
                            } else {
                                connect.missingEnv.joinToString(", ").ifBlank { "Backend OAuth configuration missing" }
                            }
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun finishBrowserLaunch(id: String, launched: Boolean) {
        _state.update { current ->
            if (current.account.pendingBrowserLaunch?.id != id) {
                current
            } else {
                current.copy(
                    account = current.account.copy(
                        pendingBrowserLaunch = null,
                        statusMessage = if (launched) {
                            "Complete Google sign-in in your browser, then return to ChatMod Mobile"
                        } else {
                            "No browser found for Google sign-in"
                        }
                    )
                )
            }
        }
    }

    fun createSettingsBackup() {
        viewModelScope.launch {
            val snapshot = _state.value
            val profileId = activeProfileId()
            setAccountBusy(true, "Creating settings backup")
            val backup = withAccountBackendSession { accessToken ->
                backendApi.createSettingsBackup(
                    accessToken = accessToken,
                    request = com.chatmod.mobile.data.remote.SettingsBackupRequest(
                        profileId = profileId,
                        channelId = snapshot.liveChatId ?: profileId,
                        profileName = "${snapshot.channelName} settings",
                        commands = snapshot.commands.map { it.toBackupCommand() },
                        timers = snapshot.timers.map { it.toBackupTimer() },
                        clientVersion = "0.1.0"
                    )
                )
            }

            if (backup == null) {
                setAccountBusy(false, "Settings backup failed")
                return@launch
            }

            _state.update { current ->
                current.copy(
                    account = current.account.copy(
                        isBusy = false,
                        statusMessage = "Settings backup created",
                        cloudBackups = listOf(
                            CloudBackupSummary(
                                id = backup.id,
                                profileName = backup.profileName.ifBlank { "Settings backup" },
                                channelId = snapshot.liveChatId ?: profileId,
                                version = backup.version,
                                clientVersion = "0.1.0",
                                createdAt = backup.createdAt
                            )
                        ) + current.account.cloudBackups.filterNot { it.id == backup.id }
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "backup-settings-${backup.id}",
                            label = "Created settings backup",
                            detail = "${backup.commandCount} commands, ${backup.timerCount} timers"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun pauseAllTimers() {
        setAllTimersEnabled(enabled = false, actionLabel = "Paused timers")
    }

    fun resumeAllTimers() {
        setAllTimersEnabled(enabled = true, actionLabel = "Resumed timers")
    }

    private fun setAllTimersEnabled(enabled: Boolean, actionLabel: String) {
        val timers = _state.value.timers
        if (timers.isEmpty()) {
            _state.update { current ->
                current.copy(
                    recentActions = listOf(
                        ActionLogItem(
                            id = "timers-empty-${System.currentTimeMillis()}",
                            label = actionLabel,
                            detail = "No timers configured"
                        )
                    ) + current.recentActions
                )
            }
            return
        }

        viewModelScope.launch {
            var changedCount = 0
            timers.forEach { timer ->
                if (timer.enabled != enabled) {
                    commandTimerStore.upsertTimer(timer.copy(enabled = enabled))
                    changedCount += 1
                }
            }

            _state.update { current ->
                current.copy(
                    recentActions = listOf(
                        ActionLogItem(
                            id = "timers-toggle-${System.currentTimeMillis()}",
                            label = actionLabel,
                            detail = "$changedCount timers updated"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun requestDisconnectYouTube() {
        setPendingAccountConfirmation(AccountConfirmation.DisconnectYouTube)
    }

    fun requestDeleteAccount() {
        setPendingAccountConfirmation(AccountConfirmation.DeleteAccount)
    }

    fun requestWipeLocalData() {
        setPendingAccountConfirmation(AccountConfirmation.WipeLocalData)
    }

    fun requestDeleteBackup(id: String) {
        val backup = _state.value.account.cloudBackups.firstOrNull { it.id == id } ?: return
        setPendingAccountConfirmation(
            AccountConfirmation.DeleteBackup(
                id = backup.id,
                label = "${backup.profileName} v${backup.version}"
            )
        )
    }

    fun requestRestoreBackup(id: String) {
        val backup = _state.value.account.cloudBackups.firstOrNull { it.id == id } ?: return
        setPendingAccountConfirmation(
            AccountConfirmation.RestoreBackup(
                id = backup.id,
                label = "${backup.profileName} v${backup.version}"
            )
        )
    }

    fun dismissAccountConfirmation() {
        setPendingAccountConfirmation(null)
    }

    fun confirmAccountAction() {
        val action = _state.value.account.pendingConfirmation ?: return
        _state.update { current ->
            current.copy(account = current.account.copy(pendingConfirmation = null))
        }

        viewModelScope.launch {
            if (action == AccountConfirmation.WipeLocalData) {
                wipeLocalData()
                return@launch
            }

            when (action) {
                AccountConfirmation.DisconnectYouTube -> disconnectYouTube()
                AccountConfirmation.DeleteAccount -> deleteCurrentAccount()
                AccountConfirmation.WipeLocalData -> Unit
                is AccountConfirmation.DeleteBackup -> deleteCloudBackup(action)
                is AccountConfirmation.RestoreBackup -> restoreCloudBackup(action)
            }
        }
    }

    fun refreshSupportEvents() {
        viewModelScope.launch {
            setSupportBusy(true, "Loading support data")
            val events = withSupportBackendSession { accessToken ->
                backendApi.listSupportEvents(accessToken).events.map { it.toSummary() }
            }
            val apiErrors = withSupportBackendSession { accessToken ->
                backendApi.listApiErrors(accessToken).errors.map { it.toSummary() }
            }
            val feedback = withSupportBackendSession { accessToken ->
                backendApi.listBetaFeedback(accessToken).feedback.map { it.toSummary() }
            }

            if (events == null && apiErrors == null && feedback == null) {
                setSupportBusy(false, "Could not load support data")
                return@launch
            }

            _state.update { current ->
                current.copy(
                    support = current.support.copy(
                        isBusy = false,
                        statusMessage = supportLoadMessage(events.orEmpty(), apiErrors.orEmpty(), feedback.orEmpty()),
                        events = events ?: current.support.events,
                        apiErrors = apiErrors ?: current.support.apiErrors,
                        feedback = feedback ?: current.support.feedback
                    )
                )
            }
        }
    }

    fun sendSupportDiagnostic() {
        viewModelScope.launch {
            val snapshot = _state.value
            setSupportBusy(true, "Sending diagnostic")
            val event = withSupportBackendSession { accessToken ->
                backendApi.recordSupportEvent(
                    accessToken = accessToken,
                    request = SupportEventRequest(
                        severity = if (snapshot.syncStatus == SyncStatus.Failed) "warning" else "info",
                        message = "Creator-sent mobile diagnostic",
                        details = mapOf(
                            "selectedTab" to snapshot.selectedTab.name,
                            "botRunning" to snapshot.botRunning,
                            "syncStatus" to snapshot.syncStatus.name,
                            "commandCount" to snapshot.commands.size,
                            "timerCount" to snapshot.timers.size,
                            "queueCount" to snapshot.queue.size,
                            "localLogCount" to snapshot.logs.entries.size,
                            "apiErrorCount" to snapshot.support.apiErrors.size,
                            "billingPlan" to snapshot.billing.plan,
                            "hasLiveChatId" to (snapshot.liveChatId != null)
                        )
                    )
                )
            }

            if (event == null) {
                setSupportBusy(false, "Diagnostic send failed")
                return@launch
            }

            _state.update { current ->
                current.copy(
                    support = current.support.copy(
                        isBusy = false,
                        statusMessage = "Diagnostic sent",
                        events = listOf(event.toSummary()) + current.support.events
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "support-${event.id}",
                            label = "Sent support diagnostic",
                            detail = event.createdAt
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    fun updateFeedbackCategory(category: BetaFeedbackCategory) {
        _state.update { current ->
            current.copy(
                support = current.support.copy(
                    feedbackCategory = category,
                    feedbackStatusMessage = null
                )
            )
        }
    }

    fun updateFeedbackMessage(message: String) {
        _state.update { current ->
            current.copy(
                support = current.support.copy(
                    feedbackMessage = message.take(1000),
                    feedbackStatusMessage = null
                )
            )
        }
    }

    fun submitBetaFeedback() {
        viewModelScope.launch {
            val snapshot = _state.value
            val feedbackMessage = snapshot.support.feedbackMessage.trim()
            if (feedbackMessage.isBlank()) {
                _state.update { current ->
                    current.copy(
                        support = current.support.copy(feedbackStatusMessage = "Write a note before sending")
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    support = current.support.copy(
                        isBusy = true,
                        feedbackStatusMessage = "Sending feedback"
                    )
                )
            }

            val feedback = withSupportBackendSession { accessToken ->
                backendApi.submitBetaFeedback(
                    accessToken = accessToken,
                    request = BetaFeedbackRequest(
                        category = snapshot.support.feedbackCategory.apiValue,
                        message = feedbackMessage,
                        appVersion = BuildConfig.VERSION_NAME,
                        context = mapOf(
                            "selectedTab" to snapshot.selectedTab.name,
                            "botRunning" to snapshot.botRunning,
                            "syncStatus" to snapshot.syncStatus.name,
                            "commandCount" to snapshot.commands.size,
                            "timerCount" to snapshot.timers.size,
                            "queueCount" to snapshot.queue.size,
                            "billingPlan" to snapshot.billing.plan
                        )
                    )
                )
            }

            if (feedback == null) {
                _state.update { current ->
                    current.copy(
                        support = current.support.copy(
                            isBusy = false,
                            feedbackStatusMessage = "Feedback send failed"
                        )
                    )
                }
                return@launch
            }

            _state.update { current ->
                current.copy(
                    support = current.support.copy(
                        isBusy = false,
                        feedbackMessage = "",
                        feedbackStatusMessage = "Feedback sent",
                        feedback = listOf(feedback.toSummary()) + current.support.feedback
                    ),
                    recentActions = listOf(
                        ActionLogItem(
                            id = "feedback-${feedback.id}",
                            label = "Sent beta feedback",
                            detail = "${feedback.category} - ${feedback.createdAt}"
                        )
                    ) + current.recentActions
                )
            }
        }
    }

    private suspend fun disconnectYouTube() {
        setAccountBusy(true, "Disconnecting YouTube")
        val result = withAccountBackendSession { accessToken -> backendApi.disconnectYouTube(accessToken) }

        if (result == null) {
            setAccountBusy(false, "YouTube disconnect failed")
            return
        }

        _state.update { current ->
            current.copy(
                account = current.account.copy(
                    isBusy = false,
                    statusMessage = if (result.disconnected) "YouTube disconnected" else "No YouTube account was connected"
                ),
                recentActions = listOf(
                    ActionLogItem(
                        id = "disconnect-youtube-${System.currentTimeMillis()}",
                        label = "Disconnected YouTube",
                        detail = "Removed ${result.removedAccounts} account rows, revoked ${result.revokedTokens} tokens"
                    )
                ) + current.recentActions
            )
        }
    }

    private suspend fun deleteCurrentAccount() {
        setAccountBusy(true, "Deleting account data")
        val result = withAccountBackendSession { accessToken -> backendApi.deleteCurrentAccount(accessToken) }

        if (result == null) {
            setAccountBusy(false, "Account deletion failed")
            return
        }

        _state.update { current ->
            current.copy(
                account = current.account.copy(
                    isBusy = false,
                    statusMessage = if (result.deleted) "Cloud account data deleted" else "No cloud account data found",
                    cloudBackups = emptyList(),
                    lastExport = null
                ),
                recentActions = listOf(
                    ActionLogItem(
                        id = "delete-account-${System.currentTimeMillis()}",
                        label = "Deleted account data",
                        detail = "${result.deviceIds.size} device ids checked, ${result.supportEventsDeleted} support events removed"
                    )
                ) + current.recentActions
            )
        }
    }

    private suspend fun deleteCloudBackup(action: AccountConfirmation.DeleteBackup) {
        setAccountBusy(true, "Deleting backup")
        val deleted = withAccountBackendSession { accessToken ->
            backendApi.deleteBackup(accessToken, action.id)
            true
        } == true

        if (!deleted) {
            setAccountBusy(false, "Backup deletion failed")
            return
        }

        _state.update { current ->
            current.copy(
                account = current.account.copy(
                    isBusy = false,
                    statusMessage = "Backup deleted",
                    cloudBackups = current.account.cloudBackups.filterNot { it.id == action.id }
                ),
                recentActions = listOf(
                    ActionLogItem(
                        id = "delete-backup-${System.currentTimeMillis()}",
                        label = "Deleted cloud backup",
                        detail = action.label
                    )
                ) + current.recentActions
            )
        }
    }

    private suspend fun restoreCloudBackup(action: AccountConfirmation.RestoreBackup) {
        val profileId = activeProfileId()
        setAccountBusy(true, "Restoring backup")
        val result = withAccountBackendSession { accessToken ->
            backendApi.restoreSettingsBackup(accessToken, action.id, profileId)
        }

        if (result == null) {
            setAccountBusy(false, "Backup restore failed")
            return
        }

        result.commands.forEach { command ->
            commandTimerStore.upsertCommand(command.toSummary())
        }
        result.timers.forEach { timer ->
            commandTimerStore.upsertTimer(timer.toSummary())
        }

        _state.update { current ->
            current.copy(
                account = current.account.copy(
                    isBusy = false,
                    statusMessage = "Backup restored"
                ),
                recentActions = listOf(
                    ActionLogItem(
                        id = "restore-backup-${System.currentTimeMillis()}",
                        label = "Restored settings backup",
                        detail = "${result.commands.size} commands, ${result.timers.size} timers"
                    )
                ) + current.recentActions
            )
        }
    }

    private suspend fun wipeLocalData() {
        val store = localPrivacyStore
        if (store == null) {
            setAccountBusy(false, "Local wipe is unavailable in this build")
            return
        }

        setAccountBusy(true, "Wiping local data")
        val result = runCatching { store.wipeLocalData() }.getOrNull()
        if (result == null) {
            setAccountBusy(false, "Local wipe failed")
            return
        }

        _state.update { current ->
            current.copy(
                account = current.account.copy(
                    isBusy = false,
                    statusMessage = "Local data wiped",
                    lastExport = null
                ),
                recentActions = listOf(
                    ActionLogItem(
                            id = "wipe-local-${System.currentTimeMillis()}",
                            label = "Wiped local data",
                            detail = "${result.commandsDeleted} commands, ${result.timersDeleted} timers, ${result.chatMessagesDeleted + result.moderationLogsDeleted + result.runtimeEventsDeleted} logs/events, ${result.pendingSyncJobsDeleted} queued sync jobs, ${result.crashReportsDeleted} crash reports"
                        )
                    ) + current.recentActions
                )
        }
    }

    private fun rememberSelectedStream(snapshot: DashboardUiState) {
        val store = settingsStore ?: return
        val liveChatId = snapshot.liveChatId?.takeIf { it.isNotBlank() } ?: return
        val videoId = snapshot.videoId?.takeIf { it.isNotBlank() } ?: return
        persistSelectedStream(
            liveChatId = liveChatId,
            videoId = videoId,
            streamTitle = snapshot.streamTitle.ifBlank { "Selected stream" },
            channelName = snapshot.channelName.ifBlank { "Your Channel" }
        )
    }

    private fun persistSelectedStream(
        liveChatId: String,
        videoId: String,
        streamTitle: String,
        channelName: String
    ) {
        val store = settingsStore ?: return
        viewModelScope.launch {
            store.setLastSelectedStream(
                LastSelectedStreamState(
                    liveChatId = liveChatId,
                    videoId = videoId,
                    streamTitle = streamTitle.ifBlank { "Selected stream" },
                    channelName = channelName.ifBlank { "Your Channel" },
                    selectedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private fun activeProfileId(): String {
        return _state.value.profiles.selectedProfileId
            ?: _state.value.settings.selectedProfileId
            ?: DefaultProfileId
    }

    private fun currentSelectedProfileId(profiles: List<ChannelProfileSummary>): String {
        val preferred = _state.value.profiles.selectedProfileId
            ?: _state.value.settings.selectedProfileId
        return profiles.firstOrNull { profile -> profile.id == preferred }?.id
            ?: profiles.firstOrNull()?.id
            ?: DefaultProfileId
    }

    private fun persistSelectedProfile(profileId: String) {
        val store = settingsStore ?: return
        viewModelScope.launch {
            store.setSelectedProfile(profileId)
        }
    }

    private fun profileStatusMessage(profiles: List<ChannelProfileSummary>, selectedProfileId: String): String {
        val selected = profiles.firstOrNull { profile -> profile.id == selectedProfileId }
        return selected?.let { "Using ${it.name}" }
            ?: "${profiles.size} profile(s) available"
    }

    private suspend fun <T> withAccountBackendSession(block: suspend (String) -> T): T? {
        val result = withBackendSession(block)
        if (result == null) {
            setAccountBusy(false, "Backend session unavailable")
        }
        return result
    }

    private suspend fun <T> withSupportBackendSession(block: suspend (String) -> T): T? {
        val result = withBackendSession(block)
        if (result == null) {
            setSupportBusy(false, "Backend session unavailable")
        }
        return result
    }

    private suspend fun <T> withBackendSession(block: suspend (String) -> T): T? {
        val accessToken = accessTokenProvider()
        if (accessToken == null) {
            lastBackendSessionFailure = null
            return null
        }

        val first = runCatching { block(accessToken) }
        if (first.isSuccess) {
            lastBackendSessionFailure = null
            return first.getOrNull()
        }

        val firstError = first.exceptionOrNull()
        if (!firstError.isUnauthorized()) {
            lastBackendSessionFailure = firstError
            if (firstError.isLikelyNetworkOffline()) {
                _state.update { current -> current.copy(syncStatus = SyncStatus.Offline) }
            }
            return null
        }

        _state.update { current -> current.copy(syncStatus = SyncStatus.Reconnecting) }
        val refreshedAccessToken = refreshAccessTokenProvider()
        if (refreshedAccessToken == null) {
            lastBackendSessionFailure = firstError
            return null
        }

        val second = runCatching { block(refreshedAccessToken) }
        if (second.isSuccess) {
            lastBackendSessionFailure = null
            return second.getOrNull()
        }

        lastBackendSessionFailure = second.exceptionOrNull()
        return null
    }

    private fun discordFailureMessage(action: String): String {
        val error = lastBackendSessionFailure
        if (error is ChatModHttpException && error.statusCode == 403) {
            return "Discord alerts require Pro or Creator"
        }
        return "Could not $action"
    }

    private fun overlayFailureMessage(action: String): String {
        val error = lastBackendSessionFailure
        if (error is ChatModHttpException && error.statusCode == 403) {
            return "OBS overlay requires Pro or Creator"
        }
        return "Could not $action"
    }

    private fun teamFailureMessage(action: String): String {
        val error = lastBackendSessionFailure
        if (error is ChatModHttpException && error.statusCode == 403) {
            return "Team access requires an available Pro or Creator seat"
        }
        return "Could not $action"
    }

    private fun channelProfileFailureMessage(): String {
        val error = lastBackendSessionFailure
        if (error is ChatModHttpException && error.statusCode == 403) {
            return "Upgrade to Creator for more channel profiles"
        }
        return "Could not create profile"
    }

    private fun setAccountBusy(isBusy: Boolean, message: String) {
        _state.update { current ->
            current.copy(
                account = current.account.copy(
                    isBusy = isBusy,
                    statusMessage = message
                )
            )
        }
    }

    private fun setPendingAccountConfirmation(action: AccountConfirmation?) {
        _state.update { current ->
            current.copy(
                account = current.account.copy(pendingConfirmation = action)
            )
        }
    }

    private fun setSupportBusy(isBusy: Boolean, message: String) {
        _state.update { current ->
            current.copy(
                support = current.support.copy(
                    isBusy = isBusy,
                    statusMessage = message
                )
            )
        }
    }

    private fun applyRulePresetUpdate(preset: RulePresetRecord, statusMessage: String) {
        persistActiveRulePreset(preset)
        _state.update { current ->
            val presets = (current.rulePresets.presets.filterNot { it.id == preset.id } + preset.toSummary())
                .map { summary -> summary.copy(isDefault = summary.id == preset.id) }
                .sortedWith(compareBy<RulePresetSummary> { if (it.isDefault) 0 else 1 }.thenBy { it.name })

            current.copy(
                rules = preset.config.toRuleSummaries(),
                rulePresets = current.rulePresets.copy(
                    isLoading = false,
                    statusMessage = statusMessage,
                    selectedPresetId = preset.id,
                    presets = presets
                ),
                recentActions = listOf(
                    ActionLogItem(
                        id = "rule-preset-${preset.id}-${System.currentTimeMillis()}",
                        label = "Rule preset updated",
                        detail = preset.name
                    )
                ) + current.recentActions
            )
        }
    }

    private fun persistActiveRulePreset(preset: RulePresetRecord) {
        val store = settingsStore ?: return
        viewModelScope.launch {
            store.setActiveRulePreset(
                id = preset.id,
                name = preset.name,
                config = preset.config
            )
        }
    }

    private fun updatePersistedSetting(
        savingMessage: String,
        savedLabel: String,
        savedDetail: String,
        localUpdate: (SettingsPanelState) -> SettingsPanelState,
        persist: suspend (SettingsStore) -> Unit
    ) {
        val store = settingsStore
        if (store == null) {
            _state.update { current ->
                current.copy(settings = current.settings.copy(statusMessage = "Settings are unavailable in this build"))
            }
            return
        }

        _state.update { current ->
            current.copy(settings = localUpdate(current.settings).copy(statusMessage = savingMessage))
        }

        viewModelScope.launch {
            val saved = runCatching { persist(store) }.isSuccess
            _state.update { current ->
                if (!saved) {
                    current.copy(settings = current.settings.copy(statusMessage = "Setting could not be saved"))
                } else {
                    current.copy(
                        settings = current.settings.copy(statusMessage = savedLabel),
                        recentActions = listOf(
                            ActionLogItem(
                                id = "settings-${System.currentTimeMillis()}",
                                label = savedLabel,
                                detail = savedDetail
                            )
                        ) + current.recentActions
                    )
                }
            }
        }
    }

    private companion object {
        val CommandPattern = Regex("""^![a-z0-9_-]{1,32}$""")
        const val DefaultProfileId = "local-default-profile"
        val DefaultChannelProfileSummary = ChannelProfileSummary(
            id = DefaultProfileId,
            channelId = "local-channel",
            name = "Primary channel",
            updatedAt = ""
        )
        const val ManualTimeoutSeconds = 300
        const val TemporaryWhitelistSeconds = 3600
        const val StarterPresetId = "starter-moderation"
        const val CustomPresetPrefix = "custom-moderation-"
        const val TemplatePresetPrefix = "template-moderation-"
        const val MaxQuietWindowMinute = 1440
        const val SubscriberOnlyRecommendationMessage =
            "Chat is moving fast. If spam continues, consider subscribers-only or members-only mode for a few minutes."
        const val DefaultAutoReplyMessage = "Please keep chat safe and on topic."
        const val DefaultFirstStreamMinutesOnly = 10
        const val ProAnalyticsRangeDays = 30
        val DefaultModerationProfile = ModerationProfile(
            blockedTerms = listOf("scam", "cheap views"),
            linkPolicy = LinkPolicy.Delete,
            capsThreshold = 0.75,
            maxRepeatedCharacters = 5,
            maxEmojiCount = 8,
            maxMentions = 6,
            maxSymbolCount = 16,
            raidMode = false,
            newChatterBurstThreshold = 6,
            newChatterBurstWindowSeconds = 30,
            firstStreamMinutesOnly = null,
            ignoreMembers = false,
            autoReplyEnabled = false,
            autoReplyMessage = DefaultAutoReplyMessage,
            hideUserOnSevereMatch = false
        )
        val DefaultCommands = listOf(
            CommandSummary(
                id = "cmd-discord",
                name = "!discord",
                response = "Add your Discord invite in this command before enabling it.",
                aliases = listOf("!community"),
                cooldownSeconds = 45,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = false
            ),
            CommandSummary(
                id = "cmd-rules",
                name = "!rules",
                response = "Keep chat friendly and on topic.",
                aliases = emptyList(),
                cooldownSeconds = 30,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = true
            ),
            CommandSummary(
                id = "cmd-socials",
                name = "!socials",
                response = "Add your social links in this command before enabling it.",
                aliases = listOf("!links"),
                cooldownSeconds = 60,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = false
            ),
            CommandSummary(
                id = "cmd-schedule",
                name = "!schedule",
                response = "Add your stream schedule in this command before enabling it.",
                aliases = listOf("!next"),
                cooldownSeconds = 60,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = false
            ),
            CommandSummary(
                id = "cmd-commands",
                name = "!commands",
                response = "Available commands: !rules, !commands, !uptime. Edit templates for !discord, !socials, and !schedule.",
                aliases = listOf("!help"),
                cooldownSeconds = 45,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = true
            ),
            CommandSummary(
                id = "cmd-uptime",
                name = "!uptime",
                response = "Stream uptime: {uptime}.",
                aliases = emptyList(),
                cooldownSeconds = 45,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = true
            )
        )
        val DefaultTimers = listOf(
            TimerSummary(
                id = "timer-rules",
                name = "Rules reminder",
                message = "Keep chat friendly and on topic.",
                intervalMinutes = 15,
                minChatMessages = 10,
                enabled = true
            ),
            TimerSummary(
                id = "timer-socials",
                name = "Socials",
                message = "Follow for stream updates after the live.",
                intervalMinutes = 25,
                minChatMessages = 15,
                enabled = false
            )
        )
    }

    class Factory(
        private val commandTimerStore: DashboardCommandTimerStore,
        private val entitlementApi: ChatModApiClient = DemoChatModApiClient(),
        private val accessTokenProvider: suspend () -> String? = { "demo-access-token" },
        private val refreshAccessTokenProvider: suspend () -> String? = accessTokenProvider,
        private val localPrivacyStore: LocalPrivacyStore? = null,
        private val settingsStore: SettingsStore? = null,
        private val logStore: DashboardLogStore = InMemoryDashboardLogStore(),
        private val botLogSink: BotLogSink? = null,
        private val batteryOptimizationIgnored: Boolean? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(
                    commandTimerStore = commandTimerStore,
                    backendApi = entitlementApi,
                    accessTokenProvider = accessTokenProvider,
                    refreshAccessTokenProvider = refreshAccessTokenProvider,
                    localPrivacyStore = localPrivacyStore,
                    settingsStore = settingsStore,
                    logStore = logStore,
                    botLogSink = botLogSink,
                    batteryOptimizationIgnored = batteryOptimizationIgnored
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}

private fun AccountExportSummary.toReceipt(): AccountExportReceipt {
    return AccountExportReceipt(
        exportedAt = exportedAt,
        profileCount = profileCount,
        backupCount = backupCount,
        linkedAccountCount = linkedAccountCount,
        linkedAccounts = linkedAccounts.map { account -> account.toReceipt() },
        supportEventCount = supportEventCount,
        auditLogCount = auditLogCount
    )
}

private fun LinkedAccountSummary.toReceipt(): LinkedAccountReceipt {
    return LinkedAccountReceipt(
        provider = provider,
        providerAccountId = providerAccountId,
        channelId = channelId,
        channelTitle = channelTitle,
        tokenExpiresAt = tokenExpiresAt
    )
}

private fun CloudBackup.toSummary(): CloudBackupSummary {
    return CloudBackupSummary(
        id = id,
        profileName = profileName.ifBlank { "Unnamed profile" },
        channelId = channelId,
        version = version,
        clientVersion = clientVersion,
        createdAt = createdAt
    )
}

private fun YouTubeBroadcast.toSummary(): StreamCandidateSummary {
    return StreamCandidateSummary(
        videoId = videoId,
        liveChatId = liveChatId,
        title = title.ifBlank { "Untitled stream" },
        status = status,
        scheduledStartTime = scheduledStartTime,
        actualStartTime = actualStartTime
    )
}

private fun SupportEventRecord.toSummary(): SupportEventSummary {
    return SupportEventSummary(
        id = id,
        severity = severity,
        message = message,
        createdAt = createdAt
    )
}

private fun ApiErrorRecord.toSummary(): ApiErrorSummary {
    return ApiErrorSummary(
        id = id,
        provider = provider,
        code = code,
        message = message,
        requestId = metadata?.get("requestId") as? String,
        createdAt = createdAt
    )
}

private fun BetaFeedbackRecord.toSummary(): BetaFeedbackSummary {
    return BetaFeedbackSummary(
        id = id,
        category = category,
        message = message,
        createdAt = createdAt
    )
}

private fun StreamSessionAnalyticsSummary.toProAnalyticsPanelState(): ProAnalyticsPanelState {
    return ProAnalyticsPanelState(
        isLoading = false,
        statusMessage = if (sessionCount == 0) {
            "No synced streams in the last $rangeDays days"
        } else {
            "Loaded $sessionCount streams from synced audit logs"
        },
        generatedAt = generatedAt,
        rangeDays = rangeDays,
        sessionCount = sessionCount,
        totalMessages = totalMessages,
        totalModerationActions = totalModerationActions,
        totalRuntimeEvents = totalRuntimeEvents,
        totalUptimeMillis = totalUptimeMillis,
        reconnectEvents = reconnectEvents,
        dayTrends = byDay.map { day ->
            ProAnalyticsDaySummary(
                day = day.day,
                messageCount = day.messageCount,
                moderationActionCount = day.moderationActionCount,
                spamAttemptCount = day.spamAttemptCount,
                reconnectEvents = day.reconnectEvents
            )
        },
        audienceTrends = topChatters.map { chatter ->
            ProAnalyticsAudienceSummary(
                authorChannelId = chatter.authorChannelId,
                authorName = chatter.authorName,
                messageCount = chatter.messageCount,
                moderationActionCount = chatter.moderationActionCount
            )
        },
        commandTrends = commandUsage.map { command ->
            ProAnalyticsCommandSummary(
                label = command.trigger ?: command.commandId,
                count = command.count
            )
        },
        ruleTrends = ruleEffectiveness.map { rule ->
            ProAnalyticsRuleSummary(
                rule = rule.rule,
                matchCount = rule.matchCount,
                destructiveActionCount = rule.destructiveActionCount,
                falsePositiveCount = rule.falsePositiveCount
            )
        },
        rulePresetTrends = ruleEffectivenessByPreset.map { rule ->
            ProAnalyticsRulePresetSummary(
                presetId = rule.presetId,
                presetName = rule.presetName,
                presetVersion = rule.presetVersion,
                rule = rule.rule,
                matchCount = rule.matchCount,
                destructiveActionCount = rule.destructiveActionCount,
                falsePositiveCount = rule.falsePositiveCount
            )
        },
        streamUptime = uptimeByStream.map { stream ->
            ProAnalyticsUptimeSummary(
                sessionId = stream.sessionId,
                title = stream.title,
                uptimeMillis = stream.uptimeMillis,
                reconnectEvents = stream.reconnectEvents
            )
        }
    )
}

private fun StreamChatSummary.toAiChatSummaryPanelState(): AiChatSummaryPanelState {
    return AiChatSummaryPanelState(
        isLoading = false,
        statusMessage = "Generated by local review assistant",
        sessionId = sessionId,
        generatedAt = generatedAt,
        title = title,
        summary = summary,
        highlights = highlights,
        topQuestions = topQuestions.map { question ->
            AiChatSummaryQuestion(
                question = question.question,
                count = question.count
            )
        },
        moderationNotes = moderationNotes,
        suggestedFollowUps = suggestedFollowUps,
        messageCount = stats.messageCount,
        uniqueChatters = stats.uniqueChatters,
        destructiveActionCount = stats.destructiveActionCount
    )
}

private fun RulePresetRecord.toSummary(): RulePresetSummary {
    return RulePresetSummary(
        id = id,
        name = name.ifBlank { "Unnamed preset" },
        detail = config.rulePresetDetail(),
        isDefault = isDefault,
        updatedAt = updatedAt
    )
}

private fun ChannelProfileRecord.toSummary(): ChannelProfileSummary {
    return ChannelProfileSummary(
        id = id,
        channelId = channelId,
        name = name.ifBlank { "Primary channel" },
        updatedAt = updatedAt
    )
}

private fun FaqEntryRecord.toSummary(): FaqEntrySummary {
    return FaqEntrySummary(
        id = id,
        question = question,
        answer = answer,
        keywords = keywords,
        enabled = enabled
    )
}

private fun RulePresetExportBundle.toPrettyJson(): String {
    return JSONObject()
        .put("formatVersion", formatVersion)
        .put("exportedAt", exportedAt)
        .put("profileId", profileId)
        .put("rulePresets", JSONArray(rulePresets.map { preset -> preset.toJsonObject() }))
        .toString(2)
}

private fun RulePresetRecord.toJsonObject(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("profileId", profileId)
        .put("name", name)
        .put("config", config.toJsonObject())
        .put("isDefault", isDefault)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
}

private fun String.toRulePresetExportBundle(): RulePresetExportBundle {
    val body = JSONObject(trim())
    val version = body.optInt("formatVersion", 1)
    require(version == 1) { "Only ChatMod preset bundle version 1 is supported" }

    val presets = body.optJSONArray("rulePresets") ?: error("Preset bundle must include rulePresets")
    require(presets.length() > 0) { "Preset bundle does not contain any presets" }
    require(presets.length() <= 25) { "Preset bundle can include at most 25 presets" }

    val exportedAt = body.optString("exportedAt").ifBlank { Instant.now().toString() }
    val profileId = body.optString("profileId").ifBlank { "local-default-profile" }
    val records = List(presets.length()) { index ->
        presets.getJSONObject(index).toRulePresetRecordFromBundle(index, profileId, exportedAt)
    }
    require(records.count { it.isDefault } <= 1) { "Only one imported preset can be default" }

    return RulePresetExportBundle(
        formatVersion = version,
        exportedAt = exportedAt,
        profileId = profileId,
        rulePresets = records
    )
}

private fun JSONObject.toRulePresetRecordFromBundle(index: Int, fallbackProfileId: String, fallbackTime: String): RulePresetRecord {
    val config = optJSONObject("config")?.toModerationProfileFromBundle() ?: ModerationProfile()
    val name = optString("name").trim().ifBlank { "Imported preset ${index + 1}" }.take(80)
    return RulePresetRecord(
        id = optString("id").ifBlank { "import-source-${index + 1}" },
        profileId = optString("profileId").ifBlank { fallbackProfileId },
        name = name,
        config = config,
        isDefault = optBoolean("isDefault", false),
        createdAt = optString("createdAt").ifBlank { fallbackTime },
        updatedAt = optString("updatedAt").ifBlank { fallbackTime }
    )
}

private fun JSONObject.toModerationProfileFromBundle(): ModerationProfile {
    return ModerationProfile(
        blockedTerms = optStringList("blockedTerms"),
        regexPatterns = optStringList("regexPatterns"),
        linkPolicy = optString("linkPolicy", "flag").toRulePresetLinkPolicy(),
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

private fun ModerationProfile.toJsonObject(): JSONObject {
    return JSONObject()
        .put("blockedTerms", JSONArray(blockedTerms))
        .put("regexPatterns", JSONArray(regexPatterns))
        .put("linkPolicy", linkPolicy.toRulePresetApiValue())
        .put("allowedDomains", JSONArray(allowedDomains))
        .put("blockedDomains", JSONArray(blockedDomains))
        .put("capsThreshold", capsThreshold)
        .put("maxRepeatedCharacters", maxRepeatedCharacters)
        .put("maxEmojiCount", maxEmojiCount)
        .put("maxMentions", maxMentions)
        .put("maxSymbolCount", maxSymbolCount)
        .put("trustedChannelIds", JSONArray(trustedChannelIds))
        .put("temporaryTrustedChannels", JSONArray(temporaryTrustedChannels.map { it.toJsonObject() }))
        .put("ignoreMembers", ignoreMembers)
        .put("raidMode", raidMode)
        .put("newChatterBurstThreshold", newChatterBurstThreshold)
        .put("newChatterBurstWindowSeconds", newChatterBurstWindowSeconds)
        .also { json -> firstStreamMinutesOnly?.let { json.put("firstStreamMinutesOnly", it) } }
        .put("autoReplyEnabled", autoReplyEnabled)
        .put("autoReplyMessage", autoReplyMessage)
        .put("hideUserOnSevereMatch", hideUserOnSevereMatch)
}

private fun TemporaryTrustedChannel.toJsonObject(): JSONObject {
    return JSONObject()
        .put("channelId", channelId)
        .put("expiresAt", expiresAt)
}

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optString(index).trim() }
        .filter { value -> value.isNotBlank() }
}

private fun JSONObject.optTemporaryTrustedChannels(name: String): List<TemporaryTrustedChannel> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optJSONObject(index) }
        .mapNotNull { item ->
            val channelId = item?.optString("channelId")?.trim().orEmpty()
            val expiresAt = item?.optString("expiresAt")?.trim().orEmpty()
            if (channelId.isBlank() || expiresAt.isBlank()) {
                null
            } else {
                TemporaryTrustedChannel(channelId = channelId, expiresAt = expiresAt)
            }
        }
}

private fun JSONObject.optNullableInt(name: String): Int? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optInt(name)
}

private fun String.toRulePresetLinkPolicy(): LinkPolicy {
    return when (lowercase()) {
        "allow" -> LinkPolicy.Allow
        "delete" -> LinkPolicy.Delete
        else -> LinkPolicy.Flag
    }
}

private fun LinkPolicy.toRulePresetApiValue(): String {
    return when (this) {
        LinkPolicy.Allow -> "allow"
        LinkPolicy.Flag -> "flag"
        LinkPolicy.Delete -> "delete"
    }
}

private fun RulePresetTemplateRecord.toSummary(): RulePresetTemplateSummary {
    return RulePresetTemplateSummary(
        id = id,
        name = name.ifBlank { "Unnamed template" },
        detail = description.ifBlank { config.rulePresetDetail() }
    )
}

private fun AppCompatibility.toUiState(): AppCompatibilityState {
    return AppCompatibilityState(
        checked = true,
        currentVersionName = currentVersionName,
        currentVersionCode = currentVersionCode,
        latestVersionName = latestVersionName,
        minimumSupportedVersionName = minimumSupportedVersionName,
        status = status,
        updateRequired = updateRequired,
        updateRecommended = updateRecommended,
        message = message,
        downloadUrl = downloadUrl
    )
}

private fun supportLoadMessage(
    events: List<SupportEventSummary>,
    apiErrors: List<ApiErrorSummary>,
    feedback: List<BetaFeedbackSummary>
): String {
    if (events.isEmpty() && apiErrors.isEmpty() && feedback.isEmpty()) {
        return "No diagnostics, API errors, or feedback yet"
    }

    return "${events.size} diagnostics, ${apiErrors.size} API errors, ${feedback.size} feedback notes"
}

private fun rulePresetStatusMessage(presets: List<RulePresetRecord>): String {
    if (presets.isEmpty()) {
        return "No saved presets yet"
    }

    val defaultPreset = presets.firstOrNull { it.isDefault }
    return if (defaultPreset == null) {
        "${presets.size} presets loaded"
    } else {
        "Using ${defaultPreset.name}"
    }
}

private fun ModerationProfile.toRuleSummaries(): List<RuleSummary> {
    val activeTemporaryTrustedCount = temporaryTrustedChannels.count { trusted -> !trusted.isExpired() }
    val trustedUserCount = trustedChannelIds.size + activeTemporaryTrustedCount
    return listOf(
        RuleSummary(
            name = "Blocked terms",
            enabled = blockedTerms.isNotEmpty(),
            detail = if (blockedTerms.isEmpty()) "No blocked terms" else "${blockedTerms.size} terms active"
        ),
        RuleSummary(
            name = "Regex patterns",
            enabled = regexPatterns.isNotEmpty(),
            detail = if (regexPatterns.isEmpty()) "No regex patterns" else "${regexPatterns.size} patterns active"
        ),
        RuleSummary(
            name = "Link policy",
            enabled = linkPolicy != LinkPolicy.Allow,
            detail = when (linkPolicy) {
                LinkPolicy.Allow -> "Links allowed"
                LinkPolicy.Flag -> "Flag links for review"
                LinkPolicy.Delete -> "Delete links from non-trusted chatters"
            }
        ),
        RuleSummary(
            name = "Caps guard",
            enabled = capsThreshold < 1.0,
            detail = "Flag above ${(capsThreshold * 100).toInt()} percent caps"
        ),
        RuleSummary(
            name = "Repeated characters",
            enabled = maxRepeatedCharacters > 0,
            detail = "Flag after $maxRepeatedCharacters repeated characters"
        ),
        RuleSummary(
            name = "Emoji and mention spam",
            enabled = maxEmojiCount > 0 || maxMentions > 0,
            detail = "Emoji max $maxEmojiCount, mentions max $maxMentions"
        ),
        RuleSummary(
            name = "New chatter burst",
            enabled = newChatterBurstThreshold > 1,
            detail = if (newChatterBurstThreshold > 1) {
                "Flag $newChatterBurstThreshold first-time chatters in ${newChatterBurstWindowSeconds}s"
            } else {
                "First-time chatter burst guard off"
            }
        ),
        RuleSummary(
            name = "Raid mode",
            enabled = raidMode,
            detail = if (raidMode) "Stricter spam, link, and burst thresholds" else "Normal thresholds"
        ),
        RuleSummary(
            name = "Auto-reply",
            enabled = autoReplyEnabled,
            detail = if (autoReplyEnabled) {
                autoReplyMessage.ifBlank { "Default moderation reply" }.take(90)
            } else {
                "No moderation reply is sent"
            }
        ),
        RuleSummary(
            name = "Auto-hide",
            enabled = hideUserOnSevereMatch,
            detail = if (hideUserOnSevereMatch) {
                "Hide users on severe rule matches"
            } else {
                "No automatic hide action"
            }
        ),
        RuleSummary(
            name = "First minutes only",
            enabled = firstStreamMinutesOnly != null,
            detail = firstStreamMinutesOnly?.let { minutes ->
                "Rules apply during first ${minutes}m of a stream"
            } ?: "Rules apply for the full stream"
        ),
        RuleSummary(
            name = "Trusted users",
            enabled = trustedUserCount > 0,
            detail = if (trustedUserCount == 0) {
                "No whitelisted channel IDs"
            } else {
                "$trustedUserCount channels bypass filters"
            }
        ),
        RuleSummary(
            name = "Member bypass",
            enabled = ignoreMembers,
            detail = if (ignoreMembers) "Trusted members bypass rules" else "Members follow normal rules"
        )
    )
}

private fun ModerationProfile.rulePresetDetail(): String {
    val enabledFilters = toRuleSummaries().count { it.enabled }
    val linkLabel = when (linkPolicy) {
        LinkPolicy.Allow -> "links allowed"
        LinkPolicy.Flag -> "links flagged"
        LinkPolicy.Delete -> "links deleted"
    }
    return "$enabledFilters filters, $linkLabel, ${blockedTerms.size} blocked terms"
}

private data class FalsePositiveReviewSave(
    val localSaved: Boolean,
    val cloudSynced: Boolean
)

private data class RulePresetLoadResult(
    val presets: List<RulePresetRecord>,
    val templates: List<RulePresetTemplateRecord>
)

private data class RuleTuningResult(
    val profile: ModerationProfile,
    val detail: String
)

private fun ModerationProfile.tunedForFalsePositive(reason: String): RuleTuningResult? {
    val key = reason.substringBefore(":")
    val value = reason.substringAfter(":", missingDelimiterValue = "").trim()

    return when (key) {
        "domain_not_allowed" -> {
            val domain = value.normalizeRuleDomain().takeIf { it.isNotBlank() } ?: return null
            val updatedDomains = (allowedDomains + domain)
                .distinctBy { item -> item.normalizeRuleDomain() }
                .take(100)
            RuleTuningResult(
                profile = copy(allowedDomains = updatedDomains),
                detail = if (updatedDomains.size == allowedDomains.size) {
                    "$domain already allowed"
                } else {
                    "Allowed $domain"
                }
            )
        }
        "link_policy" -> {
            if (linkPolicy == LinkPolicy.Allow) {
                null
            } else {
                RuleTuningResult(
                    profile = copy(linkPolicy = LinkPolicy.Allow),
                    detail = "Allowed links"
                )
            }
        }
        "excessive_caps" -> {
            val next = (capsThreshold + 0.10).coerceAtMost(1.0)
            if (next == capsThreshold) {
                null
            } else {
                RuleTuningResult(
                    profile = copy(capsThreshold = next),
                    detail = "Raised caps threshold to ${(next * 100).toInt()} percent"
                )
            }
        }
        "repeated_characters" -> tuneIntLimit(
            current = maxRepeatedCharacters,
            next = (maxRepeatedCharacters + 2).coerceAtMost(20),
            detail = { value -> "Raised repeated-character limit to $value" },
            copyProfile = { value -> copy(maxRepeatedCharacters = value) }
        )
        "emoji_spam" -> tuneIntLimit(
            current = maxEmojiCount,
            next = (maxEmojiCount + 2).coerceAtMost(100),
            detail = { value -> "Raised emoji limit to $value" },
            copyProfile = { value -> copy(maxEmojiCount = value) }
        )
        "mention_spam" -> tuneIntLimit(
            current = maxMentions,
            next = (maxMentions + 2).coerceAtMost(100),
            detail = { value -> "Raised mention limit to $value" },
            copyProfile = { value -> copy(maxMentions = value) }
        )
        "symbol_spam" -> tuneIntLimit(
            current = maxSymbolCount,
            next = (maxSymbolCount + 4).coerceAtMost(200),
            detail = { value -> "Raised symbol limit to $value" },
            copyProfile = { value -> copy(maxSymbolCount = value) }
        )
        "suspicious_new_user_burst" -> tuneIntLimit(
            current = newChatterBurstThreshold,
            next = (newChatterBurstThreshold + 2).coerceAtMost(200),
            detail = { value -> "Raised new-chatter burst threshold to $value" },
            copyProfile = { value -> copy(newChatterBurstThreshold = value) }
        )
        else -> null
    }
}

private fun tuneIntLimit(
    current: Int,
    next: Int,
    detail: (Int) -> String,
    copyProfile: (Int) -> ModerationProfile
): RuleTuningResult? {
    if (next == current) {
        return null
    }

    return RuleTuningResult(
        profile = copyProfile(next),
        detail = detail(next)
    )
}

private fun String.normalizeRuleDomain(): String {
    return trim()
        .lowercase()
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .substringBefore("/")
        .substringBefore(":")
}

private fun LogEntryFilter.matchesForExport(kind: LogEntryKind): Boolean {
    return when (this) {
        LogEntryFilter.All -> true
        LogEntryFilter.Chat -> kind == LogEntryKind.Chat
        LogEntryFilter.RuleMatch -> kind == LogEntryKind.RuleMatch
        LogEntryFilter.Moderation -> kind == LogEntryKind.Moderation
        LogEntryFilter.Runtime -> kind == LogEntryKind.Runtime
    }
}

private fun List<LogEntrySummary>.toCsvExport(exportedAt: String, filter: LogEntryFilter): String {
    val rows = mutableListOf(
        listOf("exportedAt", exportedAt),
        listOf("filter", filter.label),
        emptyList(),
        listOf("kind", "createdAt", "title", "detail", "actionType", "reason", "reviewStatus", "subjectKey")
    )
    forEach { entry ->
        rows += listOf(
            entry.kind.label,
            Instant.ofEpochMilli(entry.createdAtMillis).toString(),
            entry.title,
            entry.detail,
            entry.actionType.orEmpty(),
            entry.reason.orEmpty(),
            entry.reviewStatus.orEmpty(),
            entry.subjectKey.orEmpty()
        )
    }
    return rows.joinToString("\n") { row -> row.joinToString(",") { value -> value.csvCell() } }
}

private fun String.csvCell(): String {
    return "\"${replace("\"", "\"\"")}\""
}

private fun LogEntrySummary.ruleLabelForReview(): String {
    return title.removePrefix("Rule match:")
        .trim()
        .ifBlank { "Rule match reviewed" }
}

private fun streamSelectorMessage(
    broadcasts: List<StreamCandidateSummary>,
    status: String,
    source: String,
    needsSelection: Boolean,
    activeBroadcastCount: Int
): String {
    if (broadcasts.isEmpty()) {
        return "No active or scheduled streams found"
    }

    val activeCount = broadcasts.count { stream -> stream.status == "active" && !stream.liveChatId.isNullOrBlank() }
    val scheduledCount = broadcasts.count { stream -> stream.status == "upcoming" }
    return when {
        needsSelection -> "$activeBroadcastCount active streams found - select one"
        status == "no_active_chat" && scheduledCount > 0 -> "$scheduledCount scheduled streams - refresh when live"
        status == "no_active_chat" -> "No active live chat found - $source"
        activeCount == 1 -> "1 active stream, $scheduledCount scheduled - $source"
        activeCount > 1 -> "$activeCount active streams, $scheduledCount scheduled - $source"
        else -> "$scheduledCount scheduled streams - $source"
    }
}

private fun streamLookupFailureMessage(error: Throwable?): String {
    val httpError = error as? ChatModHttpException
    when (httpError?.errorCode) {
        "YOUTUBE_CHANNEL_MISMATCH" -> return "Selected channel does not match connected YouTube account"
        "YOUTUBE_LIVE_CHAT_ENDED" -> return "Stream ended - select another stream"
        "YOUTUBE_LIVE_CHAT_UNAVAILABLE" -> return "Live chat is disabled or unavailable for this stream"
        "YOUTUBE_PERMISSION_DENIED" -> return if (httpError.statusCode == 401) {
            "YouTube sign-in expired - reconnect account"
        } else {
            "Private/unlisted stream or bot permission issue - check access"
        }
        "YOUTUBE_API_UNAVAILABLE" -> return "YouTube API unavailable - retry shortly"
        "YOUTUBE_API_ERROR" -> return "YouTube API error - try refreshing"
        "YOUTUBE_RATE_LIMITED" -> return "YouTube rate limited stream lookup - wait before refreshing"
        "YOUTUBE_QUOTA_EXCEEDED" -> return "YouTube API quota exhausted - try after quota reset"
        "UNAUTHORIZED" -> return "Backend session expired - reconnect account"
    }

    return when (backendFailureStatus(error)) {
        SyncStatus.Offline -> "Network offline - stream lookup paused"
        SyncStatus.Reconnecting -> "Backend busy - retry stream lookup shortly"
        SyncStatus.Failed -> "Stream lookup failed"
        else -> "Stream lookup unavailable"
    }
}

private fun streamFailureDiscoveryStatus(error: Throwable?): String {
    val httpError = error as? ChatModHttpException
    return when (httpError?.errorCode) {
        "YOUTUBE_CHANNEL_MISMATCH" -> "channel_mismatch"
        "YOUTUBE_LIVE_CHAT_ENDED" -> "stream_ended"
        "YOUTUBE_LIVE_CHAT_UNAVAILABLE" -> "live_chat_disabled"
        "YOUTUBE_PERMISSION_DENIED" -> if (httpError.statusCode == 401) "oauth_expired" else "permission_needed"
        "YOUTUBE_API_UNAVAILABLE", "YOUTUBE_API_ERROR" -> "youtube_api_error"
        "YOUTUBE_RATE_LIMITED", "YOUTUBE_QUOTA_EXCEEDED" -> "youtube_rate_limited"
        "UNAUTHORIZED" -> "oauth_expired"
        else -> when (backendFailureStatus(error)) {
            SyncStatus.Offline -> "offline"
            SyncStatus.Reconnecting -> "reconnecting"
            else -> "lookup_failed"
        }
    }
}

private fun YouTubeAccountStatus.connectedChannelIdOrNull(): String? {
    return account.channelId?.trim()?.takeIf { it.isNotBlank() }
}

private fun YouTubeAccountStatus.connectedChannelTitleOrNull(): String? {
    return account.channelTitle?.trim()?.takeIf { it.isNotBlank() }
}

private fun YouTubeAccountStatus.connectedChannelLabel(): String {
    return connectedChannelTitleOrNull()
        ?: connectedChannelIdOrNull()
        ?: "connected account"
}

private fun YouTubeAccountStatus.hasChannelMismatch(requestedChannelId: String): Boolean {
    val connectedChannelId = connectedChannelIdOrNull() ?: return false
    return account.connected && normalizeYouTubeChannelId(connectedChannelId) != normalizeYouTubeChannelId(requestedChannelId)
}

private fun Throwable?.isYouTubeChannelMismatch(): Boolean {
    return this is ChatModHttpException && errorCode == "YOUTUBE_CHANNEL_MISMATCH"
}

private fun normalizeYouTubeChannelId(value: String): String {
    return value.trim().lowercase()
}

private fun backendFailureStatus(error: Throwable?): SyncStatus {
    return when {
        error.isLikelyNetworkOffline() -> SyncStatus.Offline
        error is ChatModHttpException && error.statusCode in TransientHttpStatusCodes -> SyncStatus.Reconnecting
        else -> SyncStatus.Failed
    }
}

private fun CommandSummary.toBackupCommand(): SettingsBackupCommand {
    return SettingsBackupCommand(
        id = id,
        name = name,
        response = response,
        aliases = aliases,
        cooldownSeconds = cooldownSeconds,
        accessLevel = accessLevel.toApiValue(),
        enabled = enabled
    )
}

private fun TimerSummary.toBackupTimer(): SettingsBackupTimer {
    return SettingsBackupTimer(
        id = id,
        name = name,
        message = message,
        intervalMinutes = intervalMinutes,
        minChatMessages = minChatMessages,
        quietStartMinutes = quietStartMinutes,
        quietEndMinutes = quietEndMinutes,
        enabled = enabled
    )
}

private fun SettingsBackupCommand.toSummary(): CommandSummary {
    return CommandSummary(
        id = id ?: "cmd-${UUID.randomUUID()}",
        name = name,
        response = response,
        aliases = aliases,
        cooldownSeconds = cooldownSeconds,
        accessLevel = accessLevel.toCommandAccessLevel(),
        enabled = enabled
    )
}

private fun SettingsBackupTimer.toSummary(): TimerSummary {
    return TimerSummary(
        id = id ?: "timer-${UUID.randomUUID()}",
        name = name,
        message = message,
        intervalMinutes = intervalMinutes,
        minChatMessages = minChatMessages,
        quietStartMinutes = quietStartMinutes,
        quietEndMinutes = quietEndMinutes,
        enabled = enabled
    )
}

private fun CommandAccessLevel.toApiValue(): String {
    return when (this) {
        CommandAccessLevel.Everyone -> "everyone"
        CommandAccessLevel.Members -> "members"
        CommandAccessLevel.Mods -> "mods"
        CommandAccessLevel.Owner -> "owner"
    }
}

private fun String.toCommandAccessLevel(): CommandAccessLevel {
    return when (lowercase()) {
        "members" -> CommandAccessLevel.Members
        "mods" -> CommandAccessLevel.Mods
        "owner" -> CommandAccessLevel.Owner
        else -> CommandAccessLevel.Everyone
    }
}

private fun Throwable?.isUnauthorized(): Boolean {
    return this is ChatModHttpException && statusCode == 401
}

private fun Throwable?.isLikelyNetworkOffline(): Boolean {
    return this is IOException
}

private val TransientHttpStatusCodes = setOf(408, 429, 500, 502, 503, 504)

private fun String.suggestBlockedPhrase(): String {
    val normalized = lowercase()
        .replace(Regex("""\b(?:https?://|www\.)\S+"""), " ")
        .replace(Regex("""[^a-z0-9\s_-]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (normalized.isBlank()) {
        return ""
    }

    val words = normalized
        .split(" ")
        .map { it.trim('-', '_') }
        .filter { word ->
            word.length >= 3 && word !in BlockPhraseStopWords
        }

    return when {
        words.size >= 3 -> words.take(3).joinToString(" ")
        words.size == 2 -> words.joinToString(" ")
        words.size == 1 -> words.single()
        else -> normalized.take(80)
    }
}

private fun QueueItem.warningText(): String {
    val safeAuthor = author.sanitizeLiveChatText().ifBlank { "viewer" }
    val safeReason = reason.sanitizeLiveChatText()
    return "@$safeAuthor please keep chat friendly. $safeReason"
        .sanitizeLiveChatText()
        .take(200)
}

private fun UserProfileRecord.toWarningHistorySummary(): UserWarningHistorySummary {
    return UserWarningHistorySummary(
        id = id,
        channelId = authorChannelId,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        messageCount = messageCount,
        strikeCount = strikeCount,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        notes = notes,
        recentStrikes = recentStrikes.map { strike -> strike.toHistorySummary() },
        recentModerationActions = recentModerationActions.map { action -> action.toHistorySummary() }
    )
}

private fun UserWarningHistorySummary.toDrawerState(statusMessage: String? = null): UserProfileDrawerState {
    return UserProfileDrawerState(
        id = id,
        channelId = channelId,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        messageCount = messageCount,
        strikeCount = strikeCount,
        firstSeenAt = firstSeenAt,
        lastSeenAt = lastSeenAt,
        notesText = notes.orEmpty(),
        recentStrikes = recentStrikes,
        recentModerationActions = recentModerationActions,
        statusMessage = statusMessage
    )
}

private fun UserStrikeRecord.toHistorySummary(): UserStrikeHistorySummary {
    return UserStrikeHistorySummary(
        id = id,
        reason = reason,
        createdAt = createdAt
    )
}

private fun UserModerationActionRecord.toHistorySummary(): UserModerationActionHistorySummary {
    return UserModerationActionHistorySummary(
        id = id,
        actionType = actionType,
        liveChatId = liveChatId,
        liveChatBanId = liveChatBanId,
        reason = reason,
        durationSeconds = durationSeconds,
        createdAt = createdAt,
        expiresAt = expiresAt
    )
}

private fun String.sanitizeLiveChatText(): String {
    return replace(Regex("""[\u0000-\u001F\u007F]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun String.shortDateTimeLabel(): String {
    val date = take(10)
    val time = drop(11).take(5)
    return if (date.length == 10 && time.length == 5) "$date $time" else ifBlank { "expiry" }
}

private fun TemporaryTrustedChannel.isExpired(now: Instant = Instant.now()): Boolean {
    return runCatching { !Instant.parse(expiresAt).isAfter(now) }.getOrDefault(true)
}

private val BlockPhraseStopWords = setOf(
    "a",
    "an",
    "and",
    "are",
    "as",
    "at",
    "be",
    "for",
    "from",
    "have",
    "here",
    "is",
    "it",
    "of",
    "on",
    "or",
    "our",
    "that",
    "the",
    "this",
    "to",
    "with",
    "you",
    "your"
)

private fun BotSettings.toPanelState(
    statusMessage: String?,
    batteryOptimizationIgnored: Boolean?,
    existing: SettingsPanelState? = null
): SettingsPanelState {
    return SettingsPanelState(
        selectedProfileId = selectedProfileId,
        emergencyMode = emergencyMode,
        linkLockdown = linkLockdown,
        reducedMotion = reducedMotion,
        highContrast = highContrast,
        lowDataMode = lowDataMode,
        shareUsageAnalytics = shareUsageAnalytics,
        batteryOptimizationIgnored = batteryOptimizationIgnored,
        statusMessage = statusMessage,
        discordWebhookConfigured = existing?.discordWebhookConfigured ?: false,
        discordWebhookEnabled = existing?.discordWebhookEnabled ?: false,
        discordWebhookUrlText = existing?.discordWebhookUrlText.orEmpty(),
        discordAlertModerationActions = existing?.discordAlertModerationActions ?: true,
        discordAlertRuntimeStatus = existing?.discordAlertRuntimeStatus ?: false,
        discordStatusMessage = existing?.discordStatusMessage,
        isDiscordBusy = existing?.isDiscordBusy ?: false,
        overlayConfigured = existing?.overlayConfigured ?: false,
        overlayEnabled = existing?.overlayEnabled ?: false,
        overlayTheme = existing?.overlayTheme ?: "control_room",
        overlayShowModerationActions = existing?.overlayShowModerationActions ?: true,
        overlayShowRuntimeStatus = existing?.overlayShowRuntimeStatus ?: true,
        overlayShowViewerStats = existing?.overlayShowViewerStats ?: true,
        overlayShowRecentChat = existing?.overlayShowRecentChat ?: false,
        overlayTokenPreview = existing?.overlayTokenPreview,
        overlayPublicUrl = existing?.overlayPublicUrl,
        overlayAllowed = existing?.overlayAllowed,
        overlayRequiredPlan = existing?.overlayRequiredPlan,
        overlayStatusMessage = existing?.overlayStatusMessage,
        isOverlayBusy = existing?.isOverlayBusy ?: false,
        teamSeats = existing?.teamSeats ?: 1,
        teamExtraSeats = existing?.teamExtraSeats ?: 0,
        teamMembers = existing?.teamMembers ?: emptyList(),
        teamMemberships = existing?.teamMemberships ?: emptyList(),
        teamInviteNameText = existing?.teamInviteNameText.orEmpty(),
        teamRedeemCodeText = existing?.teamRedeemCodeText.orEmpty(),
        teamLastInviteCode = existing?.teamLastInviteCode,
        teamStatusMessage = existing?.teamStatusMessage,
        isTeamBusy = existing?.isTeamBusy ?: false
    )
}

private fun SettingsPanelState.withDiscordConfig(
    config: DiscordWebhookConfig,
    statusMessage: String?,
    clearUrlText: Boolean
): SettingsPanelState {
    return copy(
        discordWebhookConfigured = config.configured,
        discordWebhookEnabled = config.enabled,
        discordWebhookUrlText = if (clearUrlText) "" else discordWebhookUrlText,
        discordAlertModerationActions = config.alertModerationActions,
        discordAlertRuntimeStatus = config.alertRuntimeStatus,
        isDiscordBusy = false,
        discordStatusMessage = statusMessage
    )
}

private fun SettingsPanelState.withOverlayConfig(
    config: OverlayConfig,
    statusMessage: String?
): SettingsPanelState {
    return copy(
        overlayConfigured = config.configured,
        overlayEnabled = config.enabled,
        overlayTheme = config.theme,
        overlayShowModerationActions = config.showModerationActions,
        overlayShowRuntimeStatus = config.showRuntimeStatus,
        overlayShowViewerStats = config.showViewerStats,
        overlayShowRecentChat = config.showRecentChat,
        overlayTokenPreview = config.tokenPreview,
        overlayPublicUrl = config.publicUrl ?: overlayPublicUrl,
        overlayAllowed = config.allowed,
        overlayRequiredPlan = config.requiredPlan,
        isOverlayBusy = false,
        overlayStatusMessage = statusMessage
    )
}

private fun overlayLoadedMessage(config: OverlayConfig): String {
    return when {
        config.allowed == false -> "OBS overlay requires Pro or Creator"
        config.enabled && config.configured -> "OBS overlay enabled"
        config.configured -> "OBS overlay saved, currently paused"
        else -> "OBS overlay not configured"
    }
}

private fun overlaySavedMessage(config: OverlayConfig): String {
    val urlMessage = if (config.publicUrl.isNullOrBlank()) "" else " URL ready."
    return if (config.enabled) {
        "OBS overlay enabled.$urlMessage"
    } else {
        "OBS overlay paused.$urlMessage"
    }
}

private fun TeamMemberRecord.toSummary(): TeamMemberSummary {
    return TeamMemberSummary(
        id = id,
        displayName = displayName,
        role = role,
        status = status,
        inviteCodePreview = inviteCodePreview,
        acceptedAt = acceptedAt,
        revokedAt = revokedAt
    )
}

private fun TeamMembershipRecord.toSummary(): TeamMembershipSummary {
    return TeamMembershipSummary(
        id = member.id,
        profileId = member.profileId,
        profileName = profileName,
        channelId = channelId,
        role = member.role,
        status = member.status
    )
}

private fun String?.discordSkippedReasonLabel(): String {
    return when (this) {
        "not_configured" -> "not configured"
        "disabled" -> "disabled"
        "moderation_alerts_disabled" -> "moderation alerts off"
        "runtime_alerts_disabled" -> "runtime alerts off"
        else -> this ?: "not sent"
    }
}

private fun YouTubeConnectUrl.toState(): YouTubeConnectState {
    return YouTubeConnectState(
        configured = configured,
        authUrlAvailable = !url.isNullOrBlank(),
        requiredScopes = requiredScopes,
        missingEnv = missingEnv,
        note = note
    )
}

private fun DashboardUiState.withBillingSummary(
    billing: BillingSummary,
    allLogEntries: List<LogEntrySummary>,
    allUsers: List<UserHistorySummary>
): DashboardUiState {
    return copy(billing = billing)
        .withLocalLogEntries(allLogEntries)
        .withLocalUserHistory(allUsers)
}

private fun MutableStateFlow<DashboardUiState>.updateQueueItem(
    id: String,
    transform: (QueueItem) -> QueueItem
) {
    update { current ->
        current.copy(
            queue = current.queue.map { queueItem ->
                if (queueItem.id == id) transform(queueItem) else queueItem
            }
        )
    }
}

private fun QueueItem.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        authorChannelId = authorChannelId,
        authorName = author,
        text = message,
        timestampMillis = System.currentTimeMillis()
    )
}

private fun LogEntrySummary.toChatMessageForSuggestion(): ChatMessage {
    return ChatMessage(
        id = id,
        authorChannelId = subjectKey ?: "local-log",
        authorName = subjectLabel ?: title,
        text = detail,
        timestampMillis = createdAtMillis
    )
}

private fun ModerationSuggestionResult.toUiSummary(): ModerationSuggestionSummary {
    return ModerationSuggestionSummary(
        suggestedAction = suggestedAction.suggestionActionLabel(),
        classification = classification.map { value -> value.replace("_", " ") },
        confidencePercent = (confidence * 100).toInt().coerceIn(0, 100),
        explanation = explanation,
        topReason = reasons.firstOrNull()?.label ?: "No strong signal",
        manualApprovalRequired = manualApprovalRequired
    )
}

private fun FaqReplySuggestionResult.toUiSummary(): FaqReplySuggestionSummary {
    return FaqReplySuggestionSummary(
        question = question ?: "Saved FAQ",
        replyText = replyText.orEmpty(),
        confidencePercent = (confidence * 100).toInt().coerceIn(0, 100),
        explanation = explanation,
        manualApprovalRequired = manualApprovalRequired
    )
}

private fun String.suggestionActionLabel(): String {
    return when (this) {
        "deleteMessage" -> "Delete message"
        "timeoutUser" -> "Timeout"
        "hideUser" -> "Hide user"
        "flagForReview" -> "Review"
        "allow" -> "Allow"
        else -> replaceFirstChar { it.uppercase() }
    }
}

private fun DashboardUiState.withLocalLogEntries(entries: List<LogEntrySummary>): DashboardUiState {
    val limit = billing.localHistoryLimit.normalizedLocalHistoryLimit()
    val visibleEntries = entries.take(limit)
    return copy(
        logs = logs.copy(
            entries = visibleEntries,
            localHistoryLimit = limit,
            availableLocalHistoryEntries = entries.size,
            statusMessage = localLogStatusMessage(
                visibleCount = visibleEntries.size,
                availableCount = entries.size,
                limit = limit,
                billing = billing
            )
        )
    )
}

private fun DashboardUiState.withLocalUserHistory(users: List<UserHistorySummary>): DashboardUiState {
    val limit = billing.localHistoryLimit.normalizedLocalHistoryLimit()
    val visibleUsers = users.take(limit)
    return copy(
        userHistory = userHistory.copy(
            users = visibleUsers,
            localHistoryLimit = limit,
            availableLocalHistoryUsers = users.size,
            statusMessage = localUserHistoryStatusMessage(
                visibleCount = visibleUsers.size,
                availableCount = users.size,
                limit = limit,
                billing = billing
            )
        )
    )
}

private fun localLogStatusMessage(
    visibleCount: Int,
    availableCount: Int,
    limit: Int,
    billing: BillingSummary
): String {
    if (availableCount == 0) {
        return "No local log entries yet"
    }

    val capLabel = billing.localHistoryCapLabel(limit)
    val hiddenCount = (availableCount - visibleCount).coerceAtLeast(0)
    return if (hiddenCount > 0) {
        "$visibleCount of $availableCount local log entries shown - $capLabel"
    } else {
        "$visibleCount local log entries - $capLabel"
    }
}

private fun localUserHistoryStatusMessage(
    visibleCount: Int,
    availableCount: Int,
    limit: Int,
    billing: BillingSummary
): String {
    if (availableCount == 0) {
        return "No viewer history yet"
    }

    val capLabel = billing.localHistoryCapLabel(limit)
    val hiddenCount = (availableCount - visibleCount).coerceAtLeast(0)
    return if (hiddenCount > 0) {
        "$visibleCount of $availableCount viewers shown - $capLabel"
    } else {
        "$visibleCount recent viewers - $capLabel"
    }
}

private fun BillingSummary.localHistoryCapLabel(limit: Int): String {
    return "${plan.displayPlanName()} $limit-row history"
}

private fun String.displayPlanName(): String {
    return replaceFirstChar { it.uppercase() }
}

private fun Int.normalizedLocalHistoryLimit(): Int {
    return coerceIn(StarterLocalHistoryLimit, CreatorLocalHistoryLimit)
}

private fun EntitlementSnapshot.toBillingSummary(): BillingSummary {
    val fallbackHistoryLimit = plan.localHistoryFallbackLimit()
    return BillingSummary(
        plan = plan,
        status = status,
        source = source,
        productId = productId,
        currentPeriodEndsAt = currentPeriodEndsAt,
        channelProfiles = features.intFeature("channelProfiles", 1),
        commandProfiles = features.limitLabel("commandProfiles", 3),
        timedMessages = features.limitLabel("timedMessages", 5),
        localHistoryLimit = features.intFeature("localHistoryLimit", fallbackHistoryLimit).normalizedLocalHistoryLimit(),
        cloudBackups = features["cloudBackups"] as? Boolean ?: false,
        emergencyMode = features["emergencyMode"] as? Boolean ?: false,
        advancedFilters = features["advancedFilters"] as? Boolean ?: false,
        presetBundles = features["presetBundles"] as? Boolean ?: false,
        obsOverlay = features["obsOverlay"] as? Boolean ?: false,
        aiSuggestions = features["aiSuggestions"] as? Boolean ?: false,
        aiSuggestionDailyLimit = features.intFeature("aiSuggestionDailyLimit", 0).coerceAtLeast(0)
    )
}

private fun String.localHistoryFallbackLimit(): Int {
    return when (lowercase()) {
        "creator" -> CreatorLocalHistoryLimit
        "pro" -> ProLocalHistoryLimit
        else -> StarterLocalHistoryLimit
    }
}

private fun Map<String, Any?>.intFeature(name: String, fallback: Int): Int {
    return when (val value = this[name]) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: fallback
        else -> fallback
    }
}

private fun Map<String, Any?>.limitLabel(name: String, fallback: Int): String {
    if (!containsKey(name)) return fallback.toString()
    return when (val value = this[name]) {
        null -> "Unlimited"
        is Number -> value.toInt().toString()
        is String -> if (value.equals("unlimited", ignoreCase = true)) "Unlimited" else value
        else -> fallback.toString()
    }
}
