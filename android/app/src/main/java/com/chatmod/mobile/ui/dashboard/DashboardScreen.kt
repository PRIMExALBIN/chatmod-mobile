package com.chatmod.mobile.ui.dashboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chatmod.mobile.billing.PlayBillingProduct
import com.chatmod.mobile.billing.PlayBillingUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: DashboardUiState,
    playBilling: PlayBillingUiState,
    onToggleBot: () -> Unit,
    onSendSubscriberOnlyRecommendation: () -> Unit,
    onResolveQueueItem: (String) -> Unit,
    onDeleteQueueItem: (String) -> Unit,
    onQuickBlockQueueItem: (String) -> Unit,
    onWarnQueueItem: (String) -> Unit,
    onSuggestQueueItem: (String) -> Unit,
    onSelectTab: (DashboardTab) -> Unit,
    onRefreshUserWarnings: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onUserProfileNotesChange: (String) -> Unit,
    onSaveUserProfileNotes: () -> Unit,
    onHideUserProfile: () -> Unit,
    onTimeoutUserProfile: () -> Unit,
    onUnbanUserProfile: (String, String?) -> Unit,
    onWhitelistUserProfile: () -> Unit,
    onTemporaryWhitelistUserProfile: () -> Unit,
    onDismissUserProfile: () -> Unit,
    onStreamChannelIdChange: (String) -> Unit,
    onUseConnectedYouTubeChannel: () -> Unit,
    onRefreshStreams: () -> Unit,
    onSelectStream: (String) -> Unit,
    onConnectionTestMessageChange: (String) -> Unit,
    onSendConnectionTestMessage: () -> Unit,
    onRunModeratorPermissionCheck: () -> Unit,
    onRefreshChannelProfiles: () -> Unit,
    onSelectChannelProfile: (String) -> Unit,
    onChannelProfileNameChange: (String) -> Unit,
    onChannelProfileChannelIdChange: (String) -> Unit,
    onCreateChannelProfile: () -> Unit,
    onRefreshRulePresets: () -> Unit,
    onSaveCurrentRulePreset: () -> Unit,
    onExportRulePresets: () -> Unit,
    onStartRulePresetImport: () -> Unit,
    onRulePresetImportTextChange: (String) -> Unit,
    onConfirmRulePresetImport: () -> Unit,
    onDismissRulePresetImport: () -> Unit,
    onToggleAutoReply: () -> Unit,
    onToggleFirstStreamMinutesOnly: () -> Unit,
    onToggleHideUserOnSevereMatch: () -> Unit,
    onSelectRulePreset: (String) -> Unit,
    onApplyRuleTemplate: (String) -> Unit,
    onCreateCommand: () -> Unit,
    onEditCommand: (String) -> Unit,
    onDeleteCommand: (String) -> Unit,
    onSendCommand: (String) -> Unit,
    onRefreshFaqEntries: () -> Unit,
    onStartCreateFaqEntry: () -> Unit,
    onStartEditFaqEntry: (String) -> Unit,
    onFaqQuestionChange: (String) -> Unit,
    onFaqAnswerChange: (String) -> Unit,
    onFaqKeywordsChange: (String) -> Unit,
    onFaqEnabledChange: (Boolean) -> Unit,
    onSaveFaqEntry: () -> Unit,
    onDeleteFaqEntry: (String) -> Unit,
    onCommandEditorChange: (CommandEditorState) -> Unit,
    onSaveCommand: () -> Unit,
    onDismissCommandEditor: () -> Unit,
    onCreateTimer: () -> Unit,
    onEditTimer: (String) -> Unit,
    onDeleteTimer: (String) -> Unit,
    onPauseAllTimers: () -> Unit,
    onResumeAllTimers: () -> Unit,
    onTimerEditorChange: (TimerEditorState) -> Unit,
    onSaveTimer: () -> Unit,
    onDismissTimerEditor: () -> Unit,
    onRefreshBillingProducts: () -> Unit,
    onPurchaseProduct: (String) -> Unit,
    onRestorePurchases: () -> Unit,
    onSimulateProPurchase: () -> Unit,
    onRefreshBackups: () -> Unit,
    onCreateSettingsBackup: () -> Unit,
    onExportAccount: () -> Unit,
    onConnectYouTube: () -> Unit,
    onDisconnectYouTube: () -> Unit,
    onDeleteAccount: () -> Unit,
    onWipeLocalData: () -> Unit,
    onDeleteBackup: (String) -> Unit,
    onRestoreBackup: (String) -> Unit,
    onConfirmAccountAction: () -> Unit,
    onDismissAccountAction: () -> Unit,
    onConfirmModerationAction: () -> Unit,
    onDismissModerationAction: () -> Unit,
    onRefreshSupportEvents: () -> Unit,
    onSendSupportDiagnostic: () -> Unit,
    onFeedbackCategoryChange: (BetaFeedbackCategory) -> Unit,
    onFeedbackMessageChange: (String) -> Unit,
    onSubmitBetaFeedback: () -> Unit,
    onSelectLogFilter: (LogEntryFilter) -> Unit,
    onRefreshProAnalytics: () -> Unit,
    onExportLogs: () -> Unit,
    onMarkFalsePositive: (String) -> Unit,
    onTuneFalsePositive: (String) -> Unit,
    onEmergencyModeChange: (Boolean) -> Unit,
    onLinkLockdownChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onLowDataModeChange: (Boolean) -> Unit,
    onShareUsageAnalyticsChange: (Boolean) -> Unit,
    onDiscordWebhookUrlChange: (String) -> Unit,
    onDiscordEnabledChange: (Boolean) -> Unit,
    onDiscordModerationAlertsChange: (Boolean) -> Unit,
    onDiscordRuntimeAlertsChange: (Boolean) -> Unit,
    onSaveDiscordWebhook: () -> Unit,
    onTestDiscordWebhook: () -> Unit,
    onDeleteDiscordWebhook: () -> Unit,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onOverlayModerationActionsChange: (Boolean) -> Unit,
    onOverlayRuntimeStatusChange: (Boolean) -> Unit,
    onOverlayViewerStatsChange: (Boolean) -> Unit,
    onOverlayRecentChatChange: (Boolean) -> Unit,
    onSaveOverlayConfig: () -> Unit,
    onRotateOverlayToken: () -> Unit,
    onRefreshOverlayConfig: () -> Unit,
    onTeamInviteNameChange: (String) -> Unit,
    onTeamRedeemCodeChange: (String) -> Unit,
    onCreateTeamInvite: () -> Unit,
    onRedeemTeamInvite: () -> Unit,
    onRevokeTeamMember: (String) -> Unit,
    onRefreshTeamAccess: () -> Unit
) {
    var showLiveControls by rememberSaveable { mutableStateOf(false) }
    var liveWorkspacePageName by rememberSaveable {
        mutableStateOf(initialLiveWorkspacePage(state.selectedTab).name)
    }
    val selectedLiveWorkspacePage = liveWorkspacePageName.toLiveWorkspacePage()
    val haptic = LocalHapticFeedback.current

    fun performActionHaptic() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val onToggleBotWithHaptic = {
        performActionHaptic()
        onToggleBot()
    }
    val streamRefreshActive = state.selectedTab == DashboardTab.Queue
    val streamRefreshLoading = streamRefreshActive && state.streamSelector.isLoading
    fun selectLiveWorkspacePage(page: LiveWorkspacePage) {
        liveWorkspacePageName = page.name
        page.dashboardTab?.let(onSelectTab)
    }

    LaunchedEffect(state.selectedTab) {
        state.selectedTab.toLiveWorkspacePage()?.let { page ->
            liveWorkspacePageName = page.name
        }
    }

    if (showLiveControls) {
        LiveControlBottomSheet(
            state = state,
            onDismiss = { showLiveControls = false },
            onToggleBot = onToggleBotWithHaptic,
            onSendSubscriberOnlyRecommendation = {
                performActionHaptic()
                onSendSubscriberOnlyRecommendation()
            },
            onEmergencyModeChange = onEmergencyModeChange,
            onLinkLockdownChange = onLinkLockdownChange
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ChatMod Mobile", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.channelName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onToggleBotWithHaptic) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (state.botRunning) "Stop bot" else "Start bot"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            DashboardBottomNavigation(
                selected = state.selectedTab,
                onSelectTab = onSelectTab
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = streamRefreshLoading,
            onRefresh = {
                if (streamRefreshActive) {
                    onRefreshStreams()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    LiveStatusBand(
                        state = state,
                        onToggleBot = onToggleBotWithHaptic,
                        onOpenLiveControls = { showLiveControls = true }
                    )
                }

                item {
                    SetupChecklistPanel(
                        state = state,
                        onAction = { action ->
                            performActionHaptic()
                            when (action) {
                                SetupAction.ReviewPermissions -> onSelectTab(DashboardTab.Account)
                                SetupAction.ConnectYouTube -> {
                                    onSelectTab(DashboardTab.Account)
                                    onConnectYouTube()
                                }
                                SetupAction.RefreshStreams -> {
                                    onSelectTab(DashboardTab.Queue)
                                    onRefreshStreams()
                                }
                                SetupAction.SendTestMessage -> {
                                    onSelectTab(DashboardTab.Queue)
                                    onSendConnectionTestMessage()
                                }
                                SetupAction.RunPermissionCheck -> {
                                    onSelectTab(DashboardTab.Queue)
                                    onRunModeratorPermissionCheck()
                                }
                                SetupAction.OpenRules -> {
                                    onSelectTab(DashboardTab.Rules)
                                    onRefreshRulePresets()
                                }
                                SetupAction.CreateCommand -> {
                                    onSelectTab(DashboardTab.Commands)
                                    onCreateCommand()
                                }
                                SetupAction.CreateTimer -> {
                                    onSelectTab(DashboardTab.Timers)
                                    onCreateTimer()
                                }
                            }
                        }
                    )
                }

                item {
                    DashboardTabs(selected = state.selectedTab, onSelectTab = onSelectTab)
                }

                when (state.selectedTab) {
                    DashboardTab.Queue,
                    DashboardTab.Feed -> {
                        item {
                            LiveWorkspacePanel(
                                state = state,
                                selectedPage = selectedLiveWorkspacePage,
                                onSelectPage = { page -> selectLiveWorkspacePage(page) },
                                onChannelIdChange = onStreamChannelIdChange,
                                onUseConnectedChannel = onUseConnectedYouTubeChannel,
                                onRefreshStreams = onRefreshStreams,
                                onSelectStream = onSelectStream,
                                onTestMessageChange = onConnectionTestMessageChange,
                                onSendTestMessage = onSendConnectionTestMessage,
                                onRunModeratorPermissionCheck = onRunModeratorPermissionCheck,
                                onOpenUserProfile = onOpenUserProfile,
                                onDeleteQueueItem = { id ->
                                    performActionHaptic()
                                    onDeleteQueueItem(id)
                                },
                                onQuickBlockQueueItem = onQuickBlockQueueItem,
                                onWarnQueueItem = onWarnQueueItem,
                                onSuggestQueueItem = onSuggestQueueItem,
                                onResolveQueueItem = onResolveQueueItem,
                                onToggleBot = onToggleBotWithHaptic,
                                onSendSubscriberOnlyRecommendation = {
                                    performActionHaptic()
                                    onSendSubscriberOnlyRecommendation()
                                },
                                onEmergencyModeChange = onEmergencyModeChange,
                                onLinkLockdownChange = onLinkLockdownChange
                            )
                        }
                    }
                    DashboardTab.Users -> {
                        item {
                            UserHistoryPanel(
                                history = state.userHistory,
                                onRefreshWarnings = onRefreshUserWarnings,
                                onOpenProfile = onOpenUserProfile
                            )
                        }
                    }
                    DashboardTab.Rules -> {
                        item {
                            ChannelProfilePanel(
                                profiles = state.profiles,
                                profileLimit = state.billing.channelProfiles,
                                onRefreshProfiles = onRefreshChannelProfiles,
                                onSelectProfile = onSelectChannelProfile,
                                onProfileNameChange = onChannelProfileNameChange,
                                onProfileChannelIdChange = onChannelProfileChannelIdChange,
                                onCreateProfile = {
                                    performActionHaptic()
                                    onCreateChannelProfile()
                                }
                            )
                        }
                        item {
                            RuleHealthPanel(
                                rules = state.rules,
                                presets = state.rulePresets,
                                presetBundlesAvailable = state.billing.presetBundles,
                                onRefreshPresets = onRefreshRulePresets,
                                onSaveCurrentPreset = onSaveCurrentRulePreset,
                                onExportPresets = {
                                    performActionHaptic()
                                    onExportRulePresets()
                                },
                                onStartImport = {
                                    performActionHaptic()
                                    onStartRulePresetImport()
                                },
                                onToggleAutoReply = onToggleAutoReply,
                                onToggleFirstStreamMinutesOnly = onToggleFirstStreamMinutesOnly,
                                onToggleHideUserOnSevereMatch = onToggleHideUserOnSevereMatch,
                                onSelectPreset = onSelectRulePreset,
                                onApplyTemplate = onApplyRuleTemplate
                            )
                        }
                    }
                    DashboardTab.Commands -> {
                        item {
                            CommandsPanel(
                                commands = state.commands,
                                faq = state.faq,
                                aiFaqAvailable = state.billing.aiSuggestions,
                                selectedLiveChatId = state.liveChatId,
                                onCreate = onCreateCommand,
                                onEdit = onEditCommand,
                                onDelete = { id ->
                                    performActionHaptic()
                                    onDeleteCommand(id)
                                },
                                onSend = onSendCommand,
                                onRefreshFaqEntries = onRefreshFaqEntries,
                                onStartCreateFaqEntry = onStartCreateFaqEntry,
                                onStartEditFaqEntry = onStartEditFaqEntry,
                                onFaqQuestionChange = onFaqQuestionChange,
                                onFaqAnswerChange = onFaqAnswerChange,
                                onFaqKeywordsChange = onFaqKeywordsChange,
                                onFaqEnabledChange = onFaqEnabledChange,
                                onSaveFaqEntry = {
                                    performActionHaptic()
                                    onSaveFaqEntry()
                                },
                                onDeleteFaqEntry = { id ->
                                    performActionHaptic()
                                    onDeleteFaqEntry(id)
                                }
                            )
                        }
                    }
                    DashboardTab.Timers -> {
                        item {
                            TimersPanel(
                                timers = state.timers,
                                onCreate = onCreateTimer,
                                onEdit = onEditTimer,
                                onDelete = { id ->
                                    performActionHaptic()
                                    onDeleteTimer(id)
                                },
                                onPauseAll = onPauseAllTimers,
                                onResumeAll = onResumeAllTimers
                            )
                        }
                    }
                    DashboardTab.Billing -> {
                        item {
                            BillingPanel(
                                billing = state.billing,
                                playBilling = playBilling,
                                onRefreshBillingProducts = onRefreshBillingProducts,
                                onPurchaseProduct = onPurchaseProduct,
                                onRestorePurchases = onRestorePurchases,
                                onSimulateProPurchase = onSimulateProPurchase
                            )
                        }
                    }
                    DashboardTab.Settings -> {
                        item {
                            SettingsPanel(
                                settings = state.settings,
                                appCompatibility = state.appCompatibility,
                                logs = state.logs,
                                onEmergencyModeChange = onEmergencyModeChange,
                                onLinkLockdownChange = onLinkLockdownChange,
                                onReducedMotionChange = onReducedMotionChange,
                                onHighContrastChange = onHighContrastChange,
                                onLowDataModeChange = onLowDataModeChange,
                                onShareUsageAnalyticsChange = onShareUsageAnalyticsChange,
                                onDiscordWebhookUrlChange = onDiscordWebhookUrlChange,
                                onDiscordEnabledChange = onDiscordEnabledChange,
                                onDiscordModerationAlertsChange = onDiscordModerationAlertsChange,
                                onDiscordRuntimeAlertsChange = onDiscordRuntimeAlertsChange,
                                onSaveDiscordWebhook = onSaveDiscordWebhook,
                                onTestDiscordWebhook = onTestDiscordWebhook,
                                onDeleteDiscordWebhook = onDeleteDiscordWebhook,
                                onOverlayEnabledChange = onOverlayEnabledChange,
                                onOverlayModerationActionsChange = onOverlayModerationActionsChange,
                                onOverlayRuntimeStatusChange = onOverlayRuntimeStatusChange,
                                onOverlayViewerStatsChange = onOverlayViewerStatsChange,
                                onOverlayRecentChatChange = onOverlayRecentChatChange,
                                onSaveOverlayConfig = onSaveOverlayConfig,
                                onRotateOverlayToken = onRotateOverlayToken,
                                onRefreshOverlayConfig = onRefreshOverlayConfig,
                                onTeamInviteNameChange = onTeamInviteNameChange,
                                onTeamRedeemCodeChange = onTeamRedeemCodeChange,
                                onCreateTeamInvite = onCreateTeamInvite,
                                onRedeemTeamInvite = onRedeemTeamInvite,
                                onRevokeTeamMember = onRevokeTeamMember,
                                onRefreshTeamAccess = onRefreshTeamAccess
                            )
                        }
                    }
                    DashboardTab.Account -> {
                        item {
                            AccountPanel(
                                account = state.account,
                                onRefreshBackups = onRefreshBackups,
                                onCreateSettingsBackup = onCreateSettingsBackup,
                                onExportAccount = onExportAccount,
                                onConnectYouTube = onConnectYouTube,
                                onDisconnectYouTube = onDisconnectYouTube,
                                onDeleteAccount = {
                                    performActionHaptic()
                                    onDeleteAccount()
                                },
                                onWipeLocalData = onWipeLocalData,
                                onDeleteBackup = { id ->
                                    performActionHaptic()
                                    onDeleteBackup(id)
                                },
                                onRestoreBackup = onRestoreBackup
                            )
                        }
                    }
                    DashboardTab.Support -> {
                        item {
                            SupportPanel(
                                support = state.support,
                                onRefresh = onRefreshSupportEvents,
                                onSendDiagnostic = onSendSupportDiagnostic,
                                onFeedbackCategoryChange = onFeedbackCategoryChange,
                                onFeedbackMessageChange = onFeedbackMessageChange,
                                onSubmitFeedback = onSubmitBetaFeedback
                            )
                        }
                    }
                    DashboardTab.Logs -> {
                        item {
                            LogsPanel(
                                logs = state.logs,
                                recentActions = state.recentActions,
                                onSelectFilter = onSelectLogFilter,
                                onRefreshProAnalytics = onRefreshProAnalytics,
                                onExportLogs = {
                                    performActionHaptic()
                                    onExportLogs()
                                },
                                onMarkFalsePositive = { id ->
                                    performActionHaptic()
                                    onMarkFalsePositive(id)
                                },
                                onTuneFalsePositive = { id ->
                                    performActionHaptic()
                                    onTuneFalsePositive(id)
                                }
                            )
                        }
                    }
                }
            }
        }

        state.commandEditor?.let { editor ->
            CommandEditorDialog(
                editor = editor,
                onChange = onCommandEditorChange,
                onSave = onSaveCommand,
                onDismiss = onDismissCommandEditor
            )
        }

        state.timerEditor?.let { editor ->
            TimerEditorDialog(
                editor = editor,
                onChange = onTimerEditorChange,
                onSave = onSaveTimer,
                onDismiss = onDismissTimerEditor
            )
        }

        state.rulePresets.importDialog?.let { dialog ->
            RulePresetImportDialog(
                state = dialog,
                loading = state.rulePresets.isLoading,
                onTextChange = onRulePresetImportTextChange,
                onImport = onConfirmRulePresetImport,
                onDismiss = onDismissRulePresetImport
            )
        }

        state.account.pendingConfirmation?.let { action ->
            AccountConfirmationDialog(
                action = action,
                onConfirm = onConfirmAccountAction,
                onDismiss = onDismissAccountAction
            )
        }

        state.moderationConfirmation?.let { action ->
            ModerationConfirmationDialog(
                action = action,
                onConfirm = onConfirmModerationAction,
                onDismiss = onDismissModerationAction
            )
        }
    }

    state.userHistory.selectedProfile?.let { profile ->
        UserProfileDialog(
            profile = profile,
            onNotesChange = onUserProfileNotesChange,
            onSaveNotes = onSaveUserProfileNotes,
            onHideUser = {
                performActionHaptic()
                onHideUserProfile()
            },
            onTimeoutUser = {
                performActionHaptic()
                onTimeoutUserProfile()
            },
            onUnbanUser = { liveChatBanId, liveChatId ->
                performActionHaptic()
                onUnbanUserProfile(liveChatBanId, liveChatId)
            },
            onWhitelistUser = onWhitelistUserProfile,
            onTemporaryWhitelistUser = onTemporaryWhitelistUserProfile,
            onDismiss = onDismissUserProfile
        )
    }
}

@Composable
private fun ModerationConfirmationDialog(
    action: ModerationConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.confirmTitle()) },
        text = {
            Text(
                action.confirmMessage(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(action.confirmButton())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LiveStatusBand(
    state: DashboardUiState,
    onToggleBot: () -> Unit,
    onOpenLiveControls: () -> Unit
) {
    val liveColor = if (state.botRunning) {
        MaterialTheme.colorScheme.secondary
    } else {
        MaterialTheme.colorScheme.error
    }

    ElevatedCard(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Live status"
            stateDescription = liveStatusStateDescription(state)
        },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(liveColor, RoundedCornerShape(12.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.botRunning) "Bot running" else "Bot stopped",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        state.streamTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onOpenLiveControls,
                    modifier = Modifier.minimumTouchTarget()
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Open live controls"
                    )
                }
                Button(onClick = onToggleBot) {
                    Icon(
                        imageVector = if (state.botRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (state.botRunning) "Pause" else "Start")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadOnlyStatusChip(
                    label = state.liveChatId ?: "No chat",
                    icon = Icons.Default.Link,
                    contentDescription = if (state.liveChatId == null) {
                        "No live chat selected"
                    } else {
                        "Selected live chat ${state.liveChatId}"
                    }
                )
                ReadOnlyStatusChip(
                    label = syncStatusLabel(state.syncStatus),
                    icon = Icons.Default.Sync,
                    contentDescription = "Sync status ${syncStatusLabel(state.syncStatus)}"
                )
                ReadOnlyStatusChip(
                    label = botHealthLabel(state),
                    icon = botHealthIcon(state),
                    contentDescription = "Bot health ${botHealthLabel(state)}",
                    warning = botHealthIcon(state) == Icons.Default.Warning
                )
                ReadOnlyStatusChip(
                    label = "${state.queue.size} queued",
                    icon = Icons.AutoMirrored.Filled.Rule,
                    contentDescription = "${state.queue.size} moderation items queued"
                )
                ReadOnlyStatusChip(
                    label = apiWarningLabel(state),
                    icon = if (hasYouTubeApiWarning(state)) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = "YouTube API status ${apiWarningLabel(state)}",
                    warning = hasYouTubeApiWarning(state)
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun LiveWorkspacePanel(
    state: DashboardUiState,
    selectedPage: LiveWorkspacePage,
    onSelectPage: (LiveWorkspacePage) -> Unit,
    onChannelIdChange: (String) -> Unit,
    onUseConnectedChannel: () -> Unit,
    onRefreshStreams: () -> Unit,
    onSelectStream: (String) -> Unit,
    onTestMessageChange: (String) -> Unit,
    onSendTestMessage: () -> Unit,
    onRunModeratorPermissionCheck: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDeleteQueueItem: (String) -> Unit,
    onQuickBlockQueueItem: (String) -> Unit,
    onWarnQueueItem: (String) -> Unit,
    onSuggestQueueItem: (String) -> Unit,
    onResolveQueueItem: (String) -> Unit,
    onToggleBot: () -> Unit,
    onSendSubscriberOnlyRecommendation: () -> Unit,
    onEmergencyModeChange: (Boolean) -> Unit,
    onLinkLockdownChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Live workspace"
                stateDescription = selectedPage.label
            },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TabRow(
            selectedTabIndex = LiveWorkspacePage.entries.indexOf(selectedPage),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            LiveWorkspacePage.entries.forEach { page ->
                Tab(
                    selected = selectedPage == page,
                    onClick = { onSelectPage(page) },
                    modifier = Modifier.minimumTouchTarget(),
                    icon = {
                        Icon(
                            page.icon,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            page.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }

        when (selectedPage) {
            LiveWorkspacePage.Queue -> LiveQueuePanel(
                state = state,
                onChannelIdChange = onChannelIdChange,
                onUseConnectedChannel = onUseConnectedChannel,
                onRefreshStreams = onRefreshStreams,
                onSelectStream = onSelectStream,
                onTestMessageChange = onTestMessageChange,
                onSendTestMessage = onSendTestMessage,
                onRunModeratorPermissionCheck = onRunModeratorPermissionCheck,
                onOpenUserProfile = onOpenUserProfile,
                onDeleteQueueItem = onDeleteQueueItem,
                onQuickBlockQueueItem = onQuickBlockQueueItem,
                onWarnQueueItem = onWarnQueueItem,
                onSuggestQueueItem = onSuggestQueueItem,
                onResolveQueueItem = onResolveQueueItem
            )
            LiveWorkspacePage.Feed -> LiveFeedPanel(logs = state.logs)
            LiveWorkspacePage.Controls -> InlineLiveControlsPanel(
                state = state,
                onToggleBot = onToggleBot,
                onSendSubscriberOnlyRecommendation = onSendSubscriberOnlyRecommendation,
                onEmergencyModeChange = onEmergencyModeChange,
                onLinkLockdownChange = onLinkLockdownChange
            )
        }
    }
}

@Composable
private fun LiveQueuePanel(
    state: DashboardUiState,
    onChannelIdChange: (String) -> Unit,
    onUseConnectedChannel: () -> Unit,
    onRefreshStreams: () -> Unit,
    onSelectStream: (String) -> Unit,
    onTestMessageChange: (String) -> Unit,
    onSendTestMessage: () -> Unit,
    onRunModeratorPermissionCheck: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onDeleteQueueItem: (String) -> Unit,
    onQuickBlockQueueItem: (String) -> Unit,
    onWarnQueueItem: (String) -> Unit,
    onSuggestQueueItem: (String) -> Unit,
    onResolveQueueItem: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StreamSelectorPanel(
            selector = state.streamSelector,
            selectedVideoId = state.videoId,
            selectedLiveChatId = state.liveChatId,
            onChannelIdChange = onChannelIdChange,
            onUseConnectedChannel = onUseConnectedChannel,
            onRefresh = onRefreshStreams,
            onSelectStream = onSelectStream,
            onTestMessageChange = onTestMessageChange,
            onSendTestMessage = onSendTestMessage,
            onRunModeratorPermissionCheck = onRunModeratorPermissionCheck
        )

        if (state.queue.isEmpty()) {
            EmptyWorkspaceMessage(
                title = "Queue clear",
                detail = "Flagged messages and manual review items appear here while the bot is live."
            )
        } else {
            state.queue.forEach { item ->
                QueueRow(
                    item = item,
                    aiSuggestionsAvailable = state.billing.aiSuggestions,
                    onOpenProfile = { onOpenUserProfile(item.authorChannelId) },
                    onDelete = { onDeleteQueueItem(item.id) },
                    onQuickBlock = { onQuickBlockQueueItem(item.id) },
                    onWarn = { onWarnQueueItem(item.id) },
                    onSuggest = { onSuggestQueueItem(item.id) },
                    onResolve = { onResolveQueueItem(item.id) }
                )
            }
        }
    }
}

@Composable
private fun InlineLiveControlsPanel(
    state: DashboardUiState,
    onToggleBot: () -> Unit,
    onSendSubscriberOnlyRecommendation: () -> Unit,
    onEmergencyModeChange: (Boolean) -> Unit,
    onLinkLockdownChange: (Boolean) -> Unit
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        LiveControlContent(
            state = state,
            onToggleBot = onToggleBot,
            onSendSubscriberOnlyRecommendation = onSendSubscriberOnlyRecommendation,
            onEmergencyModeChange = onEmergencyModeChange,
            onLinkLockdownChange = onLinkLockdownChange,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptyWorkspaceMessage(
    title: String,
    detail: String
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
private fun LiveControlBottomSheet(
    state: DashboardUiState,
    onDismiss: () -> Unit,
    onToggleBot: () -> Unit,
    onSendSubscriberOnlyRecommendation: () -> Unit,
    onEmergencyModeChange: (Boolean) -> Unit,
    onLinkLockdownChange: (Boolean) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LiveControlContent(
            state = state,
            onToggleBot = onToggleBot,
            onSendSubscriberOnlyRecommendation = onSendSubscriberOnlyRecommendation,
            onEmergencyModeChange = onEmergencyModeChange,
            onLinkLockdownChange = onLinkLockdownChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LiveControlContent(
    state: DashboardUiState,
    onToggleBot: () -> Unit,
    onSendSubscriberOnlyRecommendation: () -> Unit,
    onEmergencyModeChange: (Boolean) -> Unit,
    onLinkLockdownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = null,
                tint = if (state.botRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text("Live controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    botHealthLabel(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onToggleBot) {
                Icon(
                    if (state.botRunning) Icons.Default.PowerSettingsNew else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (state.botRunning) "Pause" else "Start")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadOnlyStatusChip(
                label = if (state.settings.emergencyMode) "Emergency on" else "Normal mode",
                icon = if (state.settings.emergencyMode) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = if (state.settings.emergencyMode) "Emergency mode is on" else "Emergency mode is off",
                warning = state.settings.emergencyMode
            )
            ReadOnlyStatusChip(
                label = if (state.settings.linkLockdown) "Links locked" else "Links by rules",
                icon = Icons.Default.Link,
                contentDescription = if (state.settings.linkLockdown) "Link lockdown is on" else "Links are moderated by rules"
            )
            ReadOnlyStatusChip(
                label = syncStatusLabel(state.syncStatus),
                icon = Icons.Default.Sync,
                contentDescription = "Sync status ${syncStatusLabel(state.syncStatus)}"
            )
        }

        LiveControlSwitchRow(
            label = "Emergency mode",
            detail = if (state.settings.emergencyMode) {
                "Strict thresholds; timers paused"
            } else {
                "Normal thresholds"
            },
            checked = state.settings.emergencyMode,
            onCheckedChange = onEmergencyModeChange
        )
        LiveControlSwitchRow(
            label = "Link lockdown",
            detail = if (state.settings.linkLockdown) {
                "Live-chat links delete"
            } else {
                "Profile link policy"
            },
            checked = state.settings.linkLockdown,
            onCheckedChange = onLinkLockdownChange
        )

        OutlinedButton(
            onClick = onSendSubscriberOnlyRecommendation,
            enabled = !state.liveChatId.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Recommend subscribers-only")
        }
    }
}

@Composable
private fun LiveControlSwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun botHealthLabel(state: DashboardUiState): String {
    return when {
        state.appCompatibility.updateRequired -> "Update required"
        state.syncStatus == SyncStatus.Failed -> "Needs attention"
        state.syncStatus == SyncStatus.Reconnecting -> "Reconnecting"
        state.syncStatus == SyncStatus.Offline -> "Offline"
        state.liveChatId.isNullOrBlank() -> "No live chat"
        state.botRunning -> "Healthy"
        else -> "Ready"
    }
}

private fun botHealthIcon(state: DashboardUiState): ImageVector {
    return when {
        state.appCompatibility.updateRequired ||
            state.syncStatus == SyncStatus.Failed ||
            state.syncStatus == SyncStatus.Reconnecting ||
            state.syncStatus == SyncStatus.Offline ||
            state.liveChatId.isNullOrBlank() -> Icons.Default.Warning
        else -> Icons.Default.CheckCircle
    }
}

@Composable
private fun ReadOnlyStatusChip(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    warning: Boolean = false
) {
    Row(
        modifier = Modifier
            .minimumTouchTarget()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                stateDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Modifier.minimumTouchTarget(): Modifier {
    return sizeIn(minWidth = 48.dp, minHeight = 48.dp)
}

private fun liveStatusStateDescription(state: DashboardUiState): String {
    return listOf(
        if (state.botRunning) "Bot running" else "Bot stopped",
        syncStatusLabel(state.syncStatus),
        botHealthLabel(state),
        apiWarningLabel(state)
    ).joinToString(", ")
}

private fun syncStatusLabel(status: SyncStatus): String {
    return when (status) {
        SyncStatus.Ready -> "Ready"
        SyncStatus.Syncing -> "Syncing"
        SyncStatus.Reconnecting -> "Reconnecting"
        SyncStatus.Offline -> "Network offline"
        SyncStatus.Failed -> "Needs attention"
    }
}

private fun apiWarningLabel(state: DashboardUiState): String {
    val warningCode = state.support.apiErrors.firstOrNull { error ->
        error.code?.contains("YOUTUBE_RATE", ignoreCase = true) == true ||
            error.code?.contains("YOUTUBE_QUOTA", ignoreCase = true) == true
    }?.code.orEmpty()

    return when {
        warningCode.contains("YOUTUBE_RATE", ignoreCase = true) -> "Rate limited"
        warningCode.contains("YOUTUBE_QUOTA", ignoreCase = true) -> "Quota warning"
        state.support.apiErrors.isNotEmpty() -> "${state.support.apiErrors.size} API alerts"
        else -> "No API alerts"
    }
}

private fun hasYouTubeApiWarning(state: DashboardUiState): Boolean {
    return state.support.apiErrors.any { error ->
        error.code?.contains("YOUTUBE_RATE", ignoreCase = true) == true ||
            error.code?.contains("YOUTUBE_QUOTA", ignoreCase = true) == true
    }
}

@Composable
private fun SetupChecklistPanel(state: DashboardUiState, onAction: (SetupAction) -> Unit) {
    val steps = setupChecklistSteps(state)
    val completed = steps.count { it.complete }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bot setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        setupChecklistStatus(completed, steps.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ReadOnlyStatusChip(
                    label = "$completed/${steps.size}",
                    icon = if (completed == steps.size) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "$completed of ${steps.size} setup steps complete",
                    warning = completed != steps.size
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                steps.forEach { step ->
                    SetupChecklistRow(step = step, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun SetupChecklistRow(step: SetupChecklistStep, onAction: (SetupAction) -> Unit) {
    val action = step.action
    val actionLabel = step.actionLabel

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (step.complete) Icons.Default.CheckCircle else step.icon,
                contentDescription = null,
                tint = if (step.complete) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(step.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!step.complete && action != null && actionLabel != null) {
            TextButton(
                onClick = { onAction(action) },
                modifier = Modifier.padding(start = 34.dp)
            ) {
                Text(actionLabel)
            }
        }
    }
}

private data class SetupChecklistStep(
    val label: String,
    val detail: String,
    val complete: Boolean,
    val icon: ImageVector,
    val actionLabel: String? = null,
    val action: SetupAction? = null
)

private enum class SetupAction {
    ReviewPermissions,
    ConnectYouTube,
    RefreshStreams,
    SendTestMessage,
    RunPermissionCheck,
    OpenRules,
    CreateCommand,
    CreateTimer
}

private fun setupChecklistSteps(state: DashboardUiState): List<SetupChecklistStep> {
    val channelReady = state.streamSelector.channelId.isNotBlank() && state.channelName != "Connect YouTube"
    val liveChatReady = !state.liveChatId.isNullOrBlank()
    val testMessageReady = !state.streamSelector.lastTestMessageId.isNullOrBlank()
    val moderatorCheckReady = !state.streamSelector.lastPermissionCheckAt.isNullOrBlank()
    val backendReady = state.syncStatus == SyncStatus.Ready || state.syncStatus == SyncStatus.Syncing
    val permissionsExplained = state.account.youtubeConnect.requiredScopes.isNotEmpty() ||
        state.account.youtubeConnect.configured == true ||
        channelReady
    val rulesReady = state.rulePresets.presets.isNotEmpty() || state.rules.isNotEmpty()
    val firstAutomationReady = state.commands.isNotEmpty() && state.timers.isNotEmpty()

    return listOf(
        SetupChecklistStep(
            label = "What this phone hosts",
            detail = "ChatMod runs the live bot here, while the backend keeps account, backup, billing, and audit services synced.",
            complete = true,
            icon = Icons.Default.PowerSettingsNew
        ),
        SetupChecklistStep(
            label = "YouTube permissions",
            detail = "Read the active livestream chat and moderate messages as the connected bot channel.",
            complete = permissionsExplained,
            icon = Icons.AutoMirrored.Filled.Rule,
            actionLabel = "Review scopes",
            action = SetupAction.ReviewPermissions
        ),
        SetupChecklistStep(
            label = "App session",
            detail = when (state.syncStatus) {
                SyncStatus.Ready -> "Backend session ready"
                SyncStatus.Syncing -> "Backend session syncing"
                SyncStatus.Reconnecting -> "Refreshing backend session"
                SyncStatus.Offline -> "Network offline"
                SyncStatus.Failed -> "Backend session needs attention"
            },
            complete = backendReady,
            icon = Icons.Default.Sync
        ),
        SetupChecklistStep(
            label = "Google sign-in",
            detail = if (channelReady) "ChatMod sends as ${state.channelName}" else "Connect the dedicated YouTube bot channel",
            complete = channelReady,
            icon = Icons.Default.Person,
            actionLabel = "Connect YouTube",
            action = SetupAction.ConnectYouTube
        ),
        SetupChecklistStep(
            label = "Live chat",
            detail = state.liveChatId ?: streamSelectorEmptyMessage(state.streamSelector),
            complete = liveChatReady,
            icon = Icons.Default.Link,
            actionLabel = "Find stream",
            action = SetupAction.RefreshStreams
        ),
        SetupChecklistStep(
            label = "Moderator role",
            detail = state.streamSelector.permissionCheckStatusMessage
                ?: if (moderatorCheckReady) {
                    "Moderator delete action verified"
                } else {
                    "Add the bot channel as a YouTube moderator, then check delete access"
                },
            complete = moderatorCheckReady,
            icon = Icons.AutoMirrored.Filled.Rule,
            actionLabel = if (testMessageReady) "Check tools" else "Send test first",
            action = if (testMessageReady) SetupAction.RunPermissionCheck else SetupAction.SendTestMessage
        ),
        SetupChecklistStep(
            label = "Test message",
            detail = state.streamSelector.lastTestMessageId ?: connectionTestStatus(state.streamSelector),
            complete = testMessageReady,
            icon = Icons.AutoMirrored.Filled.Send,
            actionLabel = "Send test",
            action = SetupAction.SendTestMessage
        ),
        SetupChecklistStep(
            label = "First rule preset",
            detail = if (rulesReady) "Moderation rules are ready to tune" else "Start with a preset, then tune it from stream logs",
            complete = rulesReady,
            icon = Icons.AutoMirrored.Filled.Rule,
            actionLabel = "Open rules",
            action = SetupAction.OpenRules
        ),
        SetupChecklistStep(
            label = "First command and timer",
            detail = if (firstAutomationReady) "Commands and timed messages are ready" else "Add one command and one timer before the first beta stream",
            complete = firstAutomationReady,
            icon = Icons.Default.Schedule,
            actionLabel = if (state.commands.isEmpty()) "Create command" else "Create timer",
            action = if (state.commands.isEmpty()) SetupAction.CreateCommand else SetupAction.CreateTimer
        )
    )
}

private fun setupChecklistStatus(completed: Int, total: Int): String {
    return if (completed == total) {
        "Ready for live moderation"
    } else {
        "${total - completed} setup step(s) remaining"
    }
}

@Composable
private fun DashboardBottomNavigation(selected: DashboardTab, onSelectTab: (DashboardTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        PrimaryDashboardTabs.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelectTab(tab) },
                icon = {
                    Icon(
                        dashboardTabIcon(tab),
                        contentDescription = null
                    )
                },
                label = { Text(tab.shortLabel()) }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DashboardTabs(selected: DashboardTab, onSelectTab: (DashboardTab) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardTab.entries.forEach { tab ->
            FilterChip(
                modifier = Modifier.minimumTouchTarget(),
                selected = selected == tab,
                onClick = { onSelectTab(tab) },
                label = { Text(tab.name) }
            )
        }
    }
}

private val PrimaryDashboardTabs = listOf(
    DashboardTab.Queue,
    DashboardTab.Feed,
    DashboardTab.Users,
    DashboardTab.Rules,
    DashboardTab.Logs
)

private enum class LiveWorkspacePage(
    val label: String,
    val icon: ImageVector,
    val dashboardTab: DashboardTab?
) {
    Queue("Queue", Icons.Default.Warning, DashboardTab.Queue),
    Feed("Feed", Icons.Default.Link, DashboardTab.Feed),
    Controls("Controls", Icons.Default.PowerSettingsNew, null)
}

private fun initialLiveWorkspacePage(tab: DashboardTab): LiveWorkspacePage {
    return tab.toLiveWorkspacePage() ?: LiveWorkspacePage.Queue
}

private fun DashboardTab.toLiveWorkspacePage(): LiveWorkspacePage? {
    return when (this) {
        DashboardTab.Queue -> LiveWorkspacePage.Queue
        DashboardTab.Feed -> LiveWorkspacePage.Feed
        else -> null
    }
}

private fun String.toLiveWorkspacePage(): LiveWorkspacePage {
    return LiveWorkspacePage.entries.firstOrNull { page -> page.name == this } ?: LiveWorkspacePage.Queue
}

private fun dashboardTabIcon(tab: DashboardTab): ImageVector {
    return when (tab) {
        DashboardTab.Queue -> Icons.Default.Warning
        DashboardTab.Feed -> Icons.Default.Link
        DashboardTab.Users -> Icons.Default.Person
        DashboardTab.Rules -> Icons.AutoMirrored.Filled.Rule
        DashboardTab.Logs -> Icons.Default.Sync
        DashboardTab.Commands -> Icons.AutoMirrored.Filled.Send
        DashboardTab.Timers -> Icons.Default.Schedule
        DashboardTab.Billing -> Icons.Default.CheckCircle
        DashboardTab.Settings -> Icons.Default.PowerSettingsNew
        DashboardTab.Account -> Icons.Default.Person
        DashboardTab.Support -> Icons.Default.Warning
    }
}

private fun DashboardTab.shortLabel(): String {
    return when (this) {
        DashboardTab.Queue -> "Queue"
        DashboardTab.Feed -> "Feed"
        DashboardTab.Users -> "Users"
        DashboardTab.Rules -> "Rules"
        DashboardTab.Logs -> "Logs"
        else -> name
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun StreamSelectorPanel(
    selector: StreamSelectorState,
    selectedVideoId: String?,
    selectedLiveChatId: String?,
    onChannelIdChange: (String) -> Unit,
    onUseConnectedChannel: () -> Unit,
    onRefresh: () -> Unit,
    onSelectStream: (String) -> Unit,
    onTestMessageChange: (String) -> Unit,
    onSendTestMessage: () -> Unit,
    onRunModeratorPermissionCheck: () -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live stream selector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    selector.statusMessage?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(onClick = onRefresh, enabled = !selector.isLoading) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (selector.isLoading) "Loading" else "Refresh")
                }
            }

            OutlinedTextField(
                value = selector.channelId,
                onValueChange = onChannelIdChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !selector.isLoading,
                label = { Text("Channel ID") }
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadOnlyStatusChip(
                    label = streamDiscoveryLabel(selector),
                    icon = streamDiscoveryIcon(selector),
                    contentDescription = "Stream discovery ${streamDiscoveryLabel(selector)}",
                    warning = selector.discoveryStatus == "error" || selector.channelMismatch
                )
                ReadOnlyStatusChip(
                    label = "${selector.activeBroadcastCount} active",
                    icon = Icons.Default.PlayArrow,
                    contentDescription = "${selector.activeBroadcastCount} active broadcasts found"
                )
                ReadOnlyStatusChip(
                    label = selector.source,
                    icon = Icons.Default.Sync,
                    contentDescription = "Stream source ${selector.source}"
                )
                connectedChannelChipLabel(selector)?.let { label ->
                    ReadOnlyStatusChip(
                        label = label,
                        icon = if (selector.channelMismatch) Icons.Default.Warning else Icons.Default.Person,
                        contentDescription = label,
                        warning = selector.channelMismatch
                    )
                }
                if (canUseConnectedChannel(selector)) {
                    AssistChip(
                        onClick = onUseConnectedChannel,
                        label = { Text("Use connected channel") },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                    )
                }
            }

            if (selector.broadcasts.isEmpty()) {
                Text(
                    streamSelectorEmptyMessage(selector),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    selector.broadcasts.forEach { stream ->
                        StreamCandidateRow(
                            stream = stream,
                            selected = stream.videoId == selectedVideoId,
                            loading = selector.isLoading,
                            onSelect = { onSelectStream(stream.videoId) }
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test connection", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = selector.testMessage,
                    onValueChange = onTestMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !selector.isTestingConnection,
                    label = { Text("Message") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        connectionTestStatus(selector),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = onSendTestMessage,
                        enabled = !selector.isTestingConnection &&
                            !selectedLiveChatId.isNullOrBlank() &&
                            selector.testMessage.isNotBlank()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (selector.isTestingConnection) "Sending" else "Test")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        moderatorPermissionStatus(selector),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedButton(
                        onClick = onRunModeratorPermissionCheck,
                        enabled = !selector.isCheckingModeratorPermissions &&
                            !selector.lastTestMessageId.isNullOrBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (selector.isCheckingModeratorPermissions) "Checking" else "Check tools")
                    }
                }
            }
        }
    }
}

private fun streamDiscoveryLabel(selector: StreamSelectorState): String {
    return when (selector.discoveryStatus) {
        "ready" -> "Live chat ready"
        "multiple_active_chats" -> "Select stream"
        "no_active_chat" -> "No active chat"
        "live_chat_disabled" -> "Chat disabled"
        "stream_ended" -> "Stream ended"
        "oauth_expired" -> "Reconnect YouTube"
        "channel_mismatch" -> "Wrong channel"
        "permission_needed" -> "Check access"
        "youtube_api_error" -> "YouTube error"
        "youtube_rate_limited" -> "YouTube limited"
        "offline" -> "Offline"
        "reconnecting" -> "Reconnecting"
        "idle" -> "Not checked"
        else -> selector.discoveryStatus.replace("_", " ")
    }
}

private fun streamDiscoveryIcon(selector: StreamSelectorState): ImageVector {
    return when (selector.discoveryStatus) {
        "ready" -> Icons.Default.CheckCircle
        "multiple_active_chats" -> Icons.Default.Link
        "channel_mismatch" -> Icons.Default.Warning
        "reconnecting" -> Icons.Default.Sync
        else -> Icons.Default.Warning
    }
}

private fun streamSelectorEmptyMessage(selector: StreamSelectorState): String {
    return when (selector.discoveryStatus) {
        "idle" -> "Refresh to find active and scheduled streams."
        "no_active_chat" -> "No active or scheduled streams found for this channel."
        "live_chat_disabled" -> "Live chat is disabled or unavailable for this stream."
        "stream_ended" -> "That stream has ended. Refresh or select another scheduled stream."
        "oauth_expired" -> "Reconnect YouTube, then refresh stream detection."
        "channel_mismatch" -> "The Channel ID does not match the connected YouTube account. Use ${selector.connectedChannelTitle ?: selector.connectedChannelId ?: "the connected channel"}."
        "permission_needed" -> "Private/unlisted streams need the right channel account; bot actions need moderator access."
        "youtube_api_error" -> "YouTube API failed. Try again shortly."
        "youtube_rate_limited" -> "YouTube limited lookups for now. Wait a bit before refreshing."
        "offline" -> "Network is offline. Stream detection will work again when connected."
        "reconnecting" -> "Backend is reconnecting. Try refreshing in a moment."
        else -> "No streams matched this channel."
    }
}

private fun connectedChannelChipLabel(selector: StreamSelectorState): String? {
    val label = selector.connectedChannelTitle?.takeIf { it.isNotBlank() }
        ?: selector.connectedChannelId?.takeIf { it.isNotBlank() }
        ?: return null
    return "Connected: $label"
}

private fun canUseConnectedChannel(selector: StreamSelectorState): Boolean {
    val connectedChannelId = selector.connectedChannelId?.trim()?.takeIf { it.isNotBlank() } ?: return false
    return selector.channelMismatch ||
        selector.channelId.trim().lowercase() != connectedChannelId.lowercase()
}

@Composable
private fun StreamCandidateRow(
    stream: StreamCandidateSummary,
    selected: Boolean,
    loading: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (stream.status == "active") Icons.Default.PlayArrow else Icons.Default.Schedule,
            contentDescription = null,
            tint = if (stream.status == "active") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(stream.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stream.streamMetaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onSelect, enabled = !loading) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.Link,
                contentDescription = null
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(if (selected) "Selected" else "Select")
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LiveFeedPanel(logs: LogPanelState) {
    val entries = logs.entries.take(40)
    val chatCount = entries.count { it.kind == LogEntryKind.Chat }
    val moderationCount = entries.count { it.kind == LogEntryKind.Moderation }
    val ruleMatchCount = entries.count { it.kind == LogEntryKind.RuleMatch }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Live chat feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        logs.statusMessage ?: "Waiting for chat activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ReadOnlyStatusChip(
                    label = "$chatCount chat",
                    icon = Icons.Default.Link,
                    contentDescription = "$chatCount chat messages in this feed"
                )
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadOnlyStatusChip(
                    label = "$moderationCount actions",
                    icon = Icons.AutoMirrored.Filled.Rule,
                    contentDescription = "$moderationCount moderation actions in this feed"
                )
                ReadOnlyStatusChip(
                    label = "$ruleMatchCount rules",
                    icon = Icons.Default.CheckCircle,
                    contentDescription = "$ruleMatchCount rule matches in this feed"
                )
                ReadOnlyStatusChip(
                    label = "${entries.size} latest",
                    icon = Icons.Default.Sync,
                    contentDescription = "${entries.size} latest log entries shown"
                )
                ReadOnlyStatusChip(
                    label = "${logs.localHistoryLimit} cap",
                    icon = Icons.Default.Schedule,
                    contentDescription = "Local history keeps up to ${logs.localHistoryLimit} log entries for this plan"
                )
            }

            if (entries.isEmpty()) {
                Text(
                    "Start the bot to see live chat, moderation actions, and runtime health here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    entries.forEach { entry ->
                        LiveFeedRow(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveFeedRow(entry: LogEntrySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            entry.feedIcon(),
            contentDescription = null,
            tint = entry.severityColor()
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.feedTitle(), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.feedDetail(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                entry.relativeTimeLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun UserHistoryPanel(
    history: UserHistoryPanelState,
    onRefreshWarnings: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    val users = history.users.take(80)
    val warnedUsers = history.warnedUsers.take(50)
    val flaggedUsers = users.count { it.severity != Severity.Info }
    val totalMessages = users.sumOf { it.messageCount }
    val totalStrikes = warnedUsers.sumOf { it.strikeCount }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("User history", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        history.warningStatusMessage ?: history.statusMessage ?: "No viewer history yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onRefreshWarnings, enabled = !history.isLoadingWarnings) {
                    Text(if (history.isLoadingWarnings) "Loading" else "Refresh")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadOnlyStatusChip(
                    label = "$flaggedUsers flagged",
                    icon = Icons.Default.Warning,
                    contentDescription = "$flaggedUsers flagged users",
                    warning = flaggedUsers > 0
                )
                ReadOnlyStatusChip(
                    label = "${users.size} viewers",
                    icon = Icons.Default.Person,
                    contentDescription = "${users.size} viewers in user history"
                )
                ReadOnlyStatusChip(
                    label = "$totalMessages messages",
                    icon = Icons.Default.Link,
                    contentDescription = "$totalMessages total messages in user history"
                )
                ReadOnlyStatusChip(
                    label = "$totalStrikes strikes",
                    icon = Icons.Default.Warning,
                    contentDescription = "$totalStrikes user strikes",
                    warning = totalStrikes > 0
                )
                ReadOnlyStatusChip(
                    label = "${history.localHistoryLimit} cap",
                    icon = Icons.Default.Schedule,
                    contentDescription = "Viewer history keeps up to ${history.localHistoryLimit} users for this plan"
                )
            }

            if (warnedUsers.isNotEmpty()) {
                Text("Warning history", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    warnedUsers.forEach { user ->
                        UserWarningHistoryRow(
                            user = user,
                            onOpenProfile = { onOpenProfile(user.id) }
                        )
                    }
                }
            } else {
                Text(
                    "Warnings and strikes appear here after a queue warning is recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (users.isEmpty()) {
                Text(
                    "Viewer history appears after the bot ingests chat messages or records moderation actions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    users.forEach { user ->
                        UserHistoryRow(user = user)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerAvatar(
    displayName: String,
    profileImageUrl: String?,
    severity: Severity,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, profileImageUrl) {
        value = loadAvatarImage(profileImageUrl)
    }
    val ringColor = when (severity) {
        Severity.Info -> MaterialTheme.colorScheme.secondary
        Severity.Warning -> MaterialTheme.colorScheme.tertiary
        Severity.Danger -> MaterialTheme.colorScheme.error
    }
    val avatarBitmap = bitmap

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, ringColor.copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = "$displayName profile image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                displayName.avatarInitials(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private suspend fun loadAvatarImage(profileImageUrl: String?): ImageBitmap? {
    val safeUrl = profileImageUrl
        ?.takeIf { it.startsWith("https://") }
        ?: return null

    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(safeUrl).openConnection().apply {
                connectTimeout = 2_000
                readTimeout = 2_000
            }
            connection.getInputStream().use { input ->
                BitmapFactory.decodeStream(input)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

private fun String.avatarInitials(): String {
    val parts = trim()
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() }

    return parts
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "?" }
}

@Composable
private fun UserWarningHistoryRow(
    user: UserWarningHistorySummary,
    onOpenProfile: () -> Unit
) {
    val latestStrike = user.recentStrikes.firstOrNull()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ViewerAvatar(
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            severity = if (user.strikeCount >= 3) Severity.Danger else Severity.Warning,
            modifier = Modifier.size(36.dp)
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${user.strikeCount} strikes - ${user.messageCount} messages - last ${user.lastSeenAt.shortDateTimeLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            latestStrike?.let { strike ->
                Text(
                    "Latest: ${strike.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                user.channelId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpenProfile) {
            Icon(Icons.Default.Edit, contentDescription = "Open ${user.displayName} profile")
        }
    }
}

@Composable
private fun UserProfileDialog(
    profile: UserProfileDrawerState,
    onNotesChange: (String) -> Unit,
    onSaveNotes: () -> Unit,
    onHideUser: () -> Unit,
    onTimeoutUser: () -> Unit,
    onUnbanUser: (String, String?) -> Unit,
    onWhitelistUser: () -> Unit,
    onTemporaryWhitelistUser: () -> Unit,
    onDismiss: () -> Unit
) {
    val moderationActionsEnabled = !profile.isSavingNotes &&
        !profile.isHidingUser &&
        !profile.isTimingOutUser &&
        !profile.isUnbanningUser &&
        !profile.isWhitelistingUser
    val reversibleAction = profile.latestReversibleModerationAction()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ViewerAvatar(
                    displayName = profile.displayName,
                    profileImageUrl = profile.profileImageUrl,
                    severity = if (profile.strikeCount >= 3) Severity.Danger else Severity.Warning,
                    modifier = Modifier.size(44.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${profile.strikeCount} strikes",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (profile.strikeCount >= 3) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureRow("Channel ID", profile.channelId)
                FeatureRow("Messages", profile.messageCount.toString())
                FeatureRow("First seen", profile.firstSeenAt.shortDateTimeLabel())
                FeatureRow("Last seen", profile.lastSeenAt.shortDateTimeLabel())

                OutlinedTextField(
                    value = profile.notesText,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = moderationActionsEnabled,
                    label = { Text("Moderator notes") },
                    minLines = 3,
                    maxLines = 5
                )

                profile.statusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onWhitelistUser,
                    enabled = moderationActionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (profile.isWhitelistingUser) "Adding trusted user" else "Whitelist user")
                }

                OutlinedButton(
                    onClick = onTemporaryWhitelistUser,
                    enabled = moderationActionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (profile.isWhitelistingUser) "Updating trust" else "Trust 1h")
                }

                Button(
                    onClick = onTimeoutUser,
                    enabled = moderationActionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (profile.isTimingOutUser) "Timing out user" else "Timeout 5m")
                }

                Button(
                    onClick = onHideUser,
                    enabled = moderationActionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (profile.isHidingUser) "Hiding user" else "Hide user")
                }

                reversibleAction?.let { action ->
                    OutlinedButton(
                        onClick = { action.liveChatBanId?.let { banId -> onUnbanUser(banId, action.liveChatId) } },
                        enabled = moderationActionsEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (profile.isUnbanningUser) "Unbanning user" else "Unban last action")
                    }
                }

                if (profile.recentStrikes.isNotEmpty()) {
                    Text("Recent strikes", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    profile.recentStrikes.take(5).forEach { strike ->
                        Text(
                            "${strike.createdAt.shortDateTimeLabel()} - ${strike.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (profile.recentModerationActions.isNotEmpty()) {
                    Text("Timeout and ban history", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    profile.recentModerationActions.take(5).forEach { action ->
                        Text(
                            action.historyLine(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveNotes,
                enabled = moderationActionsEnabled
            ) {
                Text(if (profile.isSavingNotes) "Saving" else "Save notes")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = moderationActionsEnabled
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun UserHistoryRow(user: UserHistorySummary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ViewerAvatar(
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
            severity = user.severity,
            modifier = Modifier.size(36.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                user.userMetaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            user.lastMessagePreview?.takeIf { it.isNotBlank() }?.let { preview ->
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                user.channelId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChannelProfilePanel(
    profiles: ChannelProfilePanelState,
    profileLimit: Int,
    onRefreshProfiles: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onProfileNameChange: (String) -> Unit,
    onProfileChannelIdChange: (String) -> Unit,
    onCreateProfile: () -> Unit
) {
    val canCreateProfile = profiles.profiles.size < profileLimit && !profiles.isLoading
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Channel profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        profiles.statusMessage ?: "${profiles.profiles.size}/$profileLimit profile slots used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onRefreshProfiles, enabled = !profiles.isLoading) {
                    Text("Refresh")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                profiles.profiles.forEach { profile ->
                    FilterChip(
                        selected = profiles.selectedProfileId == profile.id,
                        onClick = { onSelectProfile(profile.id) },
                        enabled = !profiles.isLoading,
                        label = {
                            Text(
                                profile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = profiles.createNameText,
                    onValueChange = onProfileNameChange,
                    enabled = canCreateProfile,
                    label = { Text("Profile name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = profiles.createChannelIdText,
                    onValueChange = onProfileChannelIdChange,
                    enabled = canCreateProfile,
                    label = { Text("YouTube channel ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCreateProfile,
                    enabled = canCreateProfile,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (profiles.isLoading) "Creating" else "Create profile")
                }
                Text(
                    "${profiles.profiles.size}/$profileLimit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            profiles.createErrorMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun RuleHealthPanel(
    rules: List<RuleSummary>,
    presets: RulePresetPanelState,
    presetBundlesAvailable: Boolean,
    onRefreshPresets: () -> Unit,
    onSaveCurrentPreset: () -> Unit,
    onExportPresets: () -> Unit,
    onStartImport: () -> Unit,
    onToggleAutoReply: () -> Unit,
    onToggleFirstStreamMinutesOnly: () -> Unit,
    onToggleHideUserOnSevereMatch: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onApplyTemplate: (String) -> Unit
) {
    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Rule profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        presets.statusMessage ?: "Local starter rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onRefreshPresets, enabled = !presets.isLoading) {
                    Text("Refresh")
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.presets.forEach { preset ->
                    FilterChip(
                        selected = presets.selectedPresetId == preset.id,
                        onClick = { onSelectPreset(preset.id) },
                        enabled = !presets.isLoading,
                        label = { Text(if (preset.isDefault) "${preset.name} default" else preset.name) }
                    )
                }
            }

            if (presets.templates.isNotEmpty()) {
                Text(
                    "Templates",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.templates.forEach { template ->
                        AssistChip(
                            onClick = { onApplyTemplate(template.id) },
                            enabled = !presets.isLoading,
                            label = {
                                Text(
                                    template.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Add, contentDescription = null)
                            }
                        )
                    }
                }
            }

            if (presets.presets.isEmpty()) {
                Text(
                    "No saved presets yet. Apply a template or save a custom profile to sync it with the backend.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                presets.presets.forEach { preset ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (preset.isDefault) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Rule,
                            contentDescription = null,
                            tint = if (preset.isDefault) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                preset.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveCurrentPreset,
                    enabled = !presets.isLoading
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (presets.isLoading) "Saving" else "Save custom")
                }
                OutlinedButton(
                    onClick = onExportPresets,
                    enabled = !presets.isLoading && presetBundlesAvailable && presets.presets.isNotEmpty()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Export JSON")
                }
                OutlinedButton(
                    onClick = onStartImport,
                    enabled = !presets.isLoading && presetBundlesAvailable
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Import JSON")
                }
                OutlinedButton(
                    onClick = onToggleAutoReply,
                    enabled = !presets.isLoading
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (rules.any { it.name == "Auto-reply" && it.enabled }) "Reply on" else "Reply off")
                }
                OutlinedButton(
                    onClick = onToggleFirstStreamMinutesOnly,
                    enabled = !presets.isLoading
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (rules.any { it.name == "First minutes only" && it.enabled }) "First 10m on" else "First 10m off")
                }
                OutlinedButton(
                    onClick = onToggleHideUserOnSevereMatch,
                    enabled = !presets.isLoading
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (rules.any { it.name == "Auto-hide" && it.enabled }) "Auto-hide on" else "Auto-hide off")
                }
            }

            if (rules.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("No active rules", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Preset rules are empty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                rules.forEach { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (rule.enabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (rule.enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(rule.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    aiSuggestionsAvailable: Boolean,
    onOpenProfile: () -> Unit,
    onDelete: () -> Unit,
    onQuickBlock: () -> Unit,
    onWarn: () -> Unit,
    onSuggest: () -> Unit,
    onResolve: () -> Unit
) {
    val accent = when (item.severity) {
        Severity.Info -> MaterialTheme.colorScheme.primary
        Severity.Warning -> Color(red = 0.78f, green = 0.52f, blue = 0.08f)
        Severity.Danger -> MaterialTheme.colorScheme.error
    }

    OutlinedCard(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, RoundedCornerShape(10.dp))
                    .padding(top = 4.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .minimumTouchTarget()
                    .clickable(
                        onClickLabel = "Open ${item.author} profile",
                        onClick = onOpenProfile
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Open ${item.author} profile. ${item.reason}"
                        stateDescription = "Severity ${item.severity.name.lowercase()}"
                    },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(item.author, fontWeight = FontWeight.SemiBold)
                Text(item.message, style = MaterialTheme.typography.bodyMedium)
                Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.assistantSuggestion?.let { suggestion ->
                    AssistantSuggestionBlock(suggestion = suggestion)
                }
                item.faqSuggestion?.let { suggestion ->
                    FaqReplySuggestionBlock(suggestion = suggestion)
                }
            }
            Column {
                IconButton(
                    onClick = onSuggest,
                    modifier = Modifier.minimumTouchTarget(),
                    enabled = !item.isSuggestionLoading
                ) {
                    Icon(
                        if (item.isSuggestionLoading) Icons.Default.Sync else Icons.AutoMirrored.Filled.Rule,
                        contentDescription = if (aiSuggestionsAvailable) {
                            "Suggest action for ${item.author}"
                        } else {
                            "Creator plan required for suggestions"
                        }
                    )
                }
                IconButton(
                    onClick = onWarn,
                    modifier = Modifier.minimumTouchTarget()
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Warn ${item.author}")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumTouchTarget(),
                    enabled = !item.youtubeMessageId.isNullOrBlank()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete ${item.author}'s message")
                }
                IconButton(
                    onClick = onQuickBlock,
                    modifier = Modifier.minimumTouchTarget()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = "Add phrase to blocked terms")
                }
                IconButton(
                    onClick = onResolve,
                    modifier = Modifier.minimumTouchTarget()
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Resolve")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AssistantSuggestionBlock(suggestion: ModerationSuggestionSummary) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text(
                    "Review assistant",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${suggestion.confidencePercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "${suggestion.suggestedAction} - ${suggestion.topReason}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                suggestion.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                suggestion.classification.forEach { label ->
                    ReadOnlyStatusChip(
                        label = label,
                        icon = Icons.Default.CheckCircle,
                        contentDescription = "Suggestion classification $label"
                    )
                }
                if (suggestion.manualApprovalRequired) {
                    ReadOnlyStatusChip(
                        label = "Manual approval",
                        icon = Icons.Default.Warning,
                        contentDescription = "Suggestion requires manual approval"
                    )
                }
            }
        }
    }
}

@Composable
private fun FaqReplySuggestionBlock(suggestion: FaqReplySuggestionSummary) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
        )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "FAQ reply",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${suggestion.confidencePercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(suggestion.question, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(suggestion.replyText, style = MaterialTheme.typography.bodySmall)
            Text(
                suggestion.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (suggestion.manualApprovalRequired) {
                ReadOnlyStatusChip(
                    label = "Manual send",
                    icon = Icons.Default.Warning,
                    contentDescription = "FAQ reply requires manual approval before sending"
                )
            }
        }
    }
}

@Composable
private fun CommandsPanel(
    commands: List<CommandSummary>,
    faq: FaqPanelState,
    aiFaqAvailable: Boolean,
    selectedLiveChatId: String?,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSend: (String) -> Unit,
    onRefreshFaqEntries: () -> Unit,
    onStartCreateFaqEntry: () -> Unit,
    onStartEditFaqEntry: (String) -> Unit,
    onFaqQuestionChange: (String) -> Unit,
    onFaqAnswerChange: (String) -> Unit,
    onFaqKeywordsChange: (String) -> Unit,
    onFaqEnabledChange: (Boolean) -> Unit,
    onSaveFaqEntry: () -> Unit,
    onDeleteFaqEntry: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Commands",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Add")
            }
        }
        if (commands.isEmpty()) {
            OutlinedCard(shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("No commands yet", fontWeight = FontWeight.Medium)
                        Text(
                            "Command triggers are empty.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onCreate) {
                        Text("Add")
                    }
                }
            }
        }
        commands.forEach { command ->
            OutlinedCard(shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(command.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(if (command.enabled) "On" else "Off", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { onSend(command.id) },
                            modifier = Modifier.minimumTouchTarget(),
                            enabled = command.enabled && !selectedLiveChatId.isNullOrBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send ${command.name}")
                        }
                        IconButton(
                            onClick = { onEdit(command.id) },
                            modifier = Modifier.minimumTouchTarget()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${command.name}")
                        }
                        IconButton(
                            onClick = { onDelete(command.id) },
                            modifier = Modifier.minimumTouchTarget()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${command.name}")
                        }
                    }
                    Text(command.response, style = MaterialTheme.typography.bodyMedium)
                    if (command.aliases.isNotEmpty()) {
                        Text(
                            command.aliases.joinToString(prefix = "Aliases: "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "${command.accessLevel.label} - Cooldown ${command.cooldownSeconds}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        FaqKnowledgeBasePanel(
            faq = faq,
            aiFaqAvailable = aiFaqAvailable,
            onRefresh = onRefreshFaqEntries,
            onCreate = onStartCreateFaqEntry,
            onEdit = onStartEditFaqEntry,
            onQuestionChange = onFaqQuestionChange,
            onAnswerChange = onFaqAnswerChange,
            onKeywordsChange = onFaqKeywordsChange,
            onEnabledChange = onFaqEnabledChange,
            onSave = onSaveFaqEntry,
            onDelete = onDeleteFaqEntry
        )
    }
}

@Composable
private fun FaqKnowledgeBasePanel(
    faq: FaqPanelState,
    aiFaqAvailable: Boolean,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onQuestionChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onKeywordsChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDelete: (String) -> Unit
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("FAQ replies", fontWeight = FontWeight.SemiBold)
                    Text(
                        faq.statusMessage ?: if (aiFaqAvailable) "Creator knowledge base for repeated questions" else "Creator plan required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onRefresh, enabled = aiFaqAvailable && !faq.isLoading) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (faq.isLoading) "Loading" else "Refresh")
                }
            }

            if (!aiFaqAvailable) {
                Text(
                    "Upgrade to Creator to save FAQ answers and show manual reply suggestions in the queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            OutlinedTextField(
                value = faq.questionText,
                onValueChange = onQuestionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Viewer question") },
                singleLine = true
            )
            OutlinedTextField(
                value = faq.answerText,
                onValueChange = onAnswerChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Suggested reply") },
                minLines = 2
            )
            OutlinedTextField(
                value = faq.keywordsText,
                onValueChange = onKeywordsChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Keywords") },
                singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = faq.enabled, onCheckedChange = onEnabledChange)
            }
            faq.errorMessage?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (faq.editingId == null) "Save FAQ" else "Update FAQ")
                }
                OutlinedButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("New")
                }
            }

            if (faq.entries.isEmpty()) {
                Text(
                    "No FAQ answers saved yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            faq.entries.forEach { entry ->
                FaqEntryRow(entry = entry, onEdit = onEdit, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun FaqEntryRow(
    entry: FaqEntrySummary,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.question, fontWeight = FontWeight.Medium)
            Text(entry.answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.keywords.isNotEmpty()) {
                Text(
                    entry.keywords.joinToString(prefix = "Keywords: "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(if (entry.enabled) "On" else "Off", color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onEdit(entry.id) }, modifier = Modifier.minimumTouchTarget()) {
            Icon(Icons.Default.Edit, contentDescription = "Edit FAQ reply")
        }
        IconButton(onClick = { onDelete(entry.id) }, modifier = Modifier.minimumTouchTarget()) {
            Icon(Icons.Default.Delete, contentDescription = "Delete FAQ reply")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimersPanel(
    timers: List<TimerSummary>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Timers",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Add")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPauseAll, enabled = timers.any { it.enabled }) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Pause all")
            }
            TextButton(onClick = onResumeAll, enabled = timers.any { !it.enabled }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Resume all")
            }
        }
        if (timers.isEmpty()) {
            OutlinedCard(shape = RoundedCornerShape(8.dp)) {
                Text(
                    "No timers yet",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        timers.forEach { timer ->
            OutlinedCard(shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timer.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text(if (timer.enabled) "On" else "Off", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { onEdit(timer.id) },
                            modifier = Modifier.minimumTouchTarget()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit ${timer.name}")
                        }
                        IconButton(
                            onClick = { onDelete(timer.id) },
                            modifier = Modifier.minimumTouchTarget()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete ${timer.name}")
                        }
                    }
                    Text(timer.message, style = MaterialTheme.typography.bodyMedium)
                    val quietLabel = timer.quietWindowLabel()
                    Text(
                        "Every ${timer.intervalMinutes} min - after ${timer.minChatMessages} messages${quietLabel?.let { " - $it" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun TimerSummary.quietWindowLabel(): String? {
    val start = quietStartMinutes ?: return null
    val end = quietEndMinutes ?: return null
    if (end <= start) {
        return null
    }

    return "quiet min $start-$end"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BillingPanel(
    billing: BillingSummary,
    playBilling: PlayBillingUiState,
    onRefreshBillingProducts: () -> Unit,
    onPurchaseProduct: (String) -> Unit,
    onRestorePurchases: () -> Unit,
    onSimulateProPurchase: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Billing",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(onClick = onRefreshBillingProducts) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("Refresh")
            }
        }
        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadOnlyStatusChip(
                        label = if (playBilling.isAvailable) "Play connected" else "Play unavailable",
                        icon = if (playBilling.isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "Play Billing status",
                        warning = !playBilling.isAvailable
                    )
                    if (playBilling.isLoading) {
                        ReadOnlyStatusChip(
                            label = "Loading",
                            icon = Icons.Default.Sync,
                            contentDescription = "Play Billing loading"
                        )
                    }
                }
                Text(
                    billing.plan.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${billing.status} via ${billing.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                billing.currentPeriodEndsAt?.let {
                    Text(
                        "Renews or expires $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Play plans",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                playBilling.statusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (playBilling.pendingPurchaseCount > 0) {
                    Text(
                        "${playBilling.pendingPurchaseCount} pending purchase(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (playBilling.products.isEmpty()) {
                    Text(
                        "Play products appear after this build is installed from a Play testing track with configured subscriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    playBilling.products.forEach { product ->
                        BillingProductRow(
                            product = product,
                            enabled = playBilling.isAvailable && !playBilling.isLoading,
                            onPurchaseProduct = onPurchaseProduct
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRestorePurchases, enabled = !playBilling.isLoading) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Restore purchases")
                    }
                    OutlinedButton(onClick = onSimulateProPurchase) {
                        Text("Validate demo")
                    }
                }
            }
        }
        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeatureRow("Channel profiles", billing.channelProfiles.toString())
                FeatureRow("Command profiles", billing.commandProfiles)
                FeatureRow("Timed messages", billing.timedMessages)
                FeatureRow("Local history", "${billing.localHistoryLimit} rows")
                FeatureRow("Cloud backups", if (billing.cloudBackups) "On" else "Off")
                FeatureRow("Emergency mode", if (billing.emergencyMode) "On" else "Off")
                FeatureRow("Advanced filters", if (billing.advancedFilters) "On" else "Off")
                FeatureRow("Preset bundles", if (billing.presetBundles) "On" else "Off")
                FeatureRow("OBS overlay", if (billing.obsOverlay) "On" else "Pro")
                FeatureRow("Review assistant", if (billing.aiSuggestions) "${billing.aiSuggestionDailyLimit}/day" else "Creator")
            }
        }
    }
}

@Composable
private fun BillingProductRow(
    product: PlayBillingProduct,
    enabled: Boolean,
    onPurchaseProduct: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text(
                    product.price,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { onPurchaseProduct(product.productId) },
                enabled = enabled && !product.offerToken.isNullOrBlank()
            ) {
                Text(product.ctaLabel())
            }
        }
        Text(
            product.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (product.description.isNotBlank()) {
            Text(
                product.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun PlayBillingProduct.ctaLabel(): String {
    return when (productId) {
        "chatmod_pro_monthly" -> "Go Pro"
        "chatmod_creator_monthly" -> "Go Creator"
        else -> "Buy"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPanel(
    settings: SettingsPanelState,
    appCompatibility: AppCompatibilityState,
    logs: LogPanelState,
    onEmergencyModeChange: (Boolean) -> Unit,
    onLinkLockdownChange: (Boolean) -> Unit,
    onReducedMotionChange: (Boolean) -> Unit,
    onHighContrastChange: (Boolean) -> Unit,
    onLowDataModeChange: (Boolean) -> Unit,
    onShareUsageAnalyticsChange: (Boolean) -> Unit,
    onDiscordWebhookUrlChange: (String) -> Unit,
    onDiscordEnabledChange: (Boolean) -> Unit,
    onDiscordModerationAlertsChange: (Boolean) -> Unit,
    onDiscordRuntimeAlertsChange: (Boolean) -> Unit,
    onSaveDiscordWebhook: () -> Unit,
    onTestDiscordWebhook: () -> Unit,
    onDeleteDiscordWebhook: () -> Unit,
    onOverlayEnabledChange: (Boolean) -> Unit,
    onOverlayModerationActionsChange: (Boolean) -> Unit,
    onOverlayRuntimeStatusChange: (Boolean) -> Unit,
    onOverlayViewerStatsChange: (Boolean) -> Unit,
    onOverlayRecentChatChange: (Boolean) -> Unit,
    onSaveOverlayConfig: () -> Unit,
    onRotateOverlayToken: () -> Unit,
    onRefreshOverlayConfig: () -> Unit,
    onTeamInviteNameChange: (String) -> Unit,
    onTeamRedeemCodeChange: (String) -> Unit,
    onCreateTeamInvite: () -> Unit,
    onRedeemTeamInvite: () -> Unit,
    onRevokeTeamMember: (String) -> Unit,
    onRefreshTeamAccess: () -> Unit
) {
    val canSaveDiscord = !settings.isDiscordBusy &&
        (settings.discordWebhookConfigured || settings.discordWebhookUrlText.isNotBlank())
    val canTestDiscord = !settings.isDiscordBusy && settings.discordWebhookConfigured
    val canSaveOverlay = !settings.isOverlayBusy && settings.overlayAllowed != false
    val canRotateOverlay = canSaveOverlay && settings.overlayConfigured
    val canCreateTeamInvite = !settings.isTeamBusy && settings.teamInviteNameText.isNotBlank()
    val canRedeemTeamInvite = !settings.isTeamBusy && settings.teamRedeemCodeText.isNotBlank()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Settings",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (settings.emergencyMode || settings.linkLockdown) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (settings.emergencyMode || settings.linkLockdown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local controls", fontWeight = FontWeight.SemiBold)
                        Text(
                            settings.statusMessage ?: "Settings are stored on this phone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FeatureRow("Local history", "${logs.entries.size}/${logs.localHistoryLimit} rows")
                if (logs.availableLocalHistoryEntries > logs.entries.size) {
                    FeatureRow("Room available", "${logs.availableLocalHistoryEntries} rows")
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (settings.discordWebhookEnabled && settings.discordWebhookConfigured) Icons.AutoMirrored.Filled.Send else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (settings.discordWebhookEnabled && settings.discordWebhookConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Discord alerts", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                settings.discordWebhookEnabled && settings.discordWebhookConfigured -> "Enabled for this profile"
                                settings.discordWebhookConfigured -> "Webhook saved, alerts disabled"
                                else -> "No webhook saved"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedTextField(
                    value = settings.discordWebhookUrlText,
                    onValueChange = onDiscordWebhookUrlChange,
                    enabled = !settings.isDiscordBusy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (settings.discordWebhookConfigured) "Replace webhook URL" else "Webhook URL") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") }
                )
                SettingSwitchRow(
                    label = "Enable alerts",
                    detail = "Send external alerts from this bot profile",
                    checked = settings.discordWebhookEnabled,
                    onCheckedChange = onDiscordEnabledChange
                )
                SettingSwitchRow(
                    label = "Moderation actions",
                    detail = "Notify when the bot deletes or hides chat",
                    checked = settings.discordAlertModerationActions,
                    onCheckedChange = onDiscordModerationAlertsChange
                )
                SettingSwitchRow(
                    label = "Runtime status",
                    detail = "Notify for runtime health changes and test alerts",
                    checked = settings.discordAlertRuntimeStatus,
                    onCheckedChange = onDiscordRuntimeAlertsChange
                )
                settings.discordStatusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Could not") || message.contains("require")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onSaveDiscordWebhook, enabled = canSaveDiscord) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Save")
                    }
                    OutlinedButton(onClick = onTestDiscordWebhook, enabled = canTestDiscord) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Test")
                    }
                    TextButton(
                        onClick = onDeleteDiscordWebhook,
                        enabled = canTestDiscord
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Delete")
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (settings.overlayEnabled && settings.overlayConfigured) Icons.Default.PlayArrow else Icons.Default.Link,
                        contentDescription = null,
                        tint = if (settings.overlayEnabled && settings.overlayConfigured) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("OBS overlay", fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                settings.overlayAllowed == false -> "Pro or Creator required"
                                settings.overlayEnabled && settings.overlayConfigured -> "Browser source enabled"
                                settings.overlayConfigured -> "Browser source saved, overlay paused"
                                else -> "No overlay URL created"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FeatureRow("Theme", settings.overlayTheme.overlayThemeLabel())
                FeatureRow("URL", settings.overlayPublicUrl ?: settings.overlayTokenPreview ?: "Create or rotate to show")
                SettingSwitchRow(
                    label = "Enable overlay",
                    detail = "Allow the browser source URL to show live state",
                    checked = settings.overlayEnabled,
                    onCheckedChange = onOverlayEnabledChange
                )
                SettingSwitchRow(
                    label = "Moderation actions",
                    detail = "Show recent delete, timeout, warn, and review actions",
                    checked = settings.overlayShowModerationActions,
                    onCheckedChange = onOverlayModerationActionsChange
                )
                SettingSwitchRow(
                    label = "Runtime status",
                    detail = "Show command sends and reconnect/backoff events",
                    checked = settings.overlayShowRuntimeStatus,
                    onCheckedChange = onOverlayRuntimeStatusChange
                )
                SettingSwitchRow(
                    label = "Viewer stats",
                    detail = "Show counts for messages, chatters, actions, and spam",
                    checked = settings.overlayShowViewerStats,
                    onCheckedChange = onOverlayViewerStatsChange
                )
                SettingSwitchRow(
                    label = "Recent chat text",
                    detail = "Off by default because the OBS URL is public",
                    checked = settings.overlayShowRecentChat,
                    onCheckedChange = onOverlayRecentChatChange
                )
                settings.overlayStatusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Could not") || message.contains("requires")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onSaveOverlayConfig, enabled = canSaveOverlay) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Save")
                    }
                    OutlinedButton(onClick = onRotateOverlayToken, enabled = canRotateOverlay) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Rotate URL")
                    }
                    TextButton(onClick = onRefreshOverlayConfig, enabled = !settings.isOverlayBusy) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Refresh")
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Team access", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${settings.teamMembers.count { it.status != "revoked" }}/${settings.teamExtraSeats} extra seats used",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FeatureRow("Plan seats", settings.teamSeats.toString())
                settings.teamLastInviteCode?.let { inviteCode ->
                    FeatureRow("Invite code", inviteCode)
                }
                OutlinedTextField(
                    value = settings.teamInviteNameText,
                    onValueChange = onTeamInviteNameChange,
                    enabled = !settings.isTeamBusy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("New moderator name") },
                    placeholder = { Text("Stream helper") }
                )
                OutlinedTextField(
                    value = settings.teamRedeemCodeText,
                    onValueChange = onTeamRedeemCodeChange,
                    enabled = !settings.isTeamBusy,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Redeem invite code") },
                    placeholder = { Text("cmt_...") }
                )
                settings.teamStatusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (message.startsWith("Could not") || message.contains("requires")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onCreateTeamInvite, enabled = canCreateTeamInvite) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Invite")
                    }
                    OutlinedButton(onClick = onRedeemTeamInvite, enabled = canRedeemTeamInvite) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Redeem")
                    }
                    TextButton(onClick = onRefreshTeamAccess, enabled = !settings.isTeamBusy) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Refresh")
                    }
                }
                if (settings.teamMembers.isNotEmpty()) {
                    Text("Profile team", fontWeight = FontWeight.SemiBold)
                    settings.teamMembers.forEach { member ->
                        TeamMemberRow(member = member, onRevoke = { onRevokeTeamMember(member.id) })
                    }
                }
                if (settings.teamMemberships.isNotEmpty()) {
                    Text("Joined teams", fontWeight = FontWeight.SemiBold)
                    settings.teamMemberships.forEach { membership ->
                        FeatureRow(membership.profileName, "${membership.role} - ${membership.status}")
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Live controls", fontWeight = FontWeight.SemiBold)
                SettingSwitchRow(
                    label = "Emergency mode",
                    detail = "Keep high-pressure controls armed for the active stream",
                    checked = settings.emergencyMode,
                    onCheckedChange = onEmergencyModeChange
                )
                SettingSwitchRow(
                    label = "Link lockdown",
                    detail = "Use the strict link setting for non-mod chat",
                    checked = settings.linkLockdown,
                    onCheckedChange = onLinkLockdownChange
                )
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BatteryOptimizationStatus(settings.batteryOptimizationIgnored)
                FeatureRow("Version", appCompatibility.versionLabel())
                FeatureRow("Update status", appCompatibility.updateLabel())
                Text("App preference", fontWeight = FontWeight.SemiBold)
                SettingSwitchRow(
                    label = "Reduced motion",
                    detail = "Prefer calmer transitions while streaming",
                    checked = settings.reducedMotion,
                    onCheckedChange = onReducedMotionChange
                )
                SettingSwitchRow(
                    label = "High contrast",
                    detail = "Use stronger surfaces, outlines, and status colors",
                    checked = settings.highContrast,
                    onCheckedChange = onHighContrastChange
                )
                SettingSwitchRow(
                    label = "Low-data mode",
                    detail = "Reduce background polling when mobile data matters",
                    checked = settings.lowDataMode,
                    onCheckedChange = onLowDataModeChange
                )
                SettingSwitchRow(
                    label = "Share usage analytics",
                    detail = "Send private beta product events without chat text or tokens",
                    checked = settings.shareUsageAnalytics,
                    onCheckedChange = onShareUsageAnalyticsChange
                )
                appCompatibility.noticeMessage()?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Profile", fontWeight = FontWeight.SemiBold)
                FeatureRow("Selected profile", settings.selectedProfileId ?: "Default local profile")
                FeatureRow("Storage", "Android DataStore")
            }
        }
    }
}

@Composable
private fun BatteryOptimizationStatus(ignored: Boolean?) {
    val warning = ignored == false
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            if (warning) Icons.Default.Warning else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (warning) "Battery may pause bot" else "Battery status ready",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when (ignored) {
                    false -> "Use unrestricted battery for long streams."
                    true -> "Battery optimization exemption active."
                    null -> "Battery status unavailable."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun AccountPanel(
    account: AccountPanelState,
    onRefreshBackups: () -> Unit,
    onCreateSettingsBackup: () -> Unit,
    onExportAccount: () -> Unit,
    onConnectYouTube: () -> Unit,
    onDisconnectYouTube: () -> Unit,
    onDeleteAccount: () -> Unit,
    onWipeLocalData: () -> Unit,
    onDeleteBackup: (String) -> Unit,
    onRestoreBackup: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Account",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onExportAccount, enabled = !account.isBusy) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Export")
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (account.isBusy) Icons.Default.Sync else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (account.isBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Privacy controls", fontWeight = FontWeight.SemiBold)
                        Text(
                            account.statusMessage ?: "Backend account controls are ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                account.lastExport?.let { receipt ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Last export", fontWeight = FontWeight.SemiBold)
                        FeatureRow("Exported", receipt.exportedAt)
                        FeatureRow("Profiles", receipt.profileCount.toString())
                        FeatureRow("Backups", receipt.backupCount.toString())
                        FeatureRow("Linked accounts", receipt.linkedAccountCount.toString())
                        receipt.linkedAccounts.forEach { linkedAccount ->
                            LinkedAccountReceiptRow(linkedAccount = linkedAccount)
                        }
                        FeatureRow("Support events", receipt.supportEventCount.toString())
                        FeatureRow("Audit logs", receipt.auditLogCount.toString())
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cloud backups", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onCreateSettingsBackup, enabled = !account.isBusy) {
                        Text("Backup")
                    }
                    TextButton(onClick = onRefreshBackups, enabled = !account.isBusy) {
                        Text("Refresh")
                    }
                }

                if (account.cloudBackups.isEmpty()) {
                    Text(
                        "No cloud backups saved for this account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    account.cloudBackups.forEach { backup ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backup.profileName, fontWeight = FontWeight.Medium)
                                Text(
                                    "v${backup.version} - ${backup.createdAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                enabled = !account.isBusy,
                                modifier = Modifier.minimumTouchTarget(),
                                onClick = { onRestoreBackup(backup.id) }
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = "Restore backup ${backup.profileName}")
                            }
                            IconButton(
                                enabled = !account.isBusy,
                                modifier = Modifier.minimumTouchTarget(),
                                onClick = { onDeleteBackup(backup.id) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete backup ${backup.profileName}")
                            }
                        }
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Connected services", fontWeight = FontWeight.SemiBold)
                Text(
                    "Connect opens Google sign-in for YouTube read and live-chat moderation scopes. Google tokens stay encrypted on the backend; this phone only keeps a ChatMod session token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                YouTubeConnectStatus(account.youtubeConnect)
                TextButton(onClick = onConnectYouTube, enabled = !account.isBusy) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Connect YouTube")
                }
                Text(
                    "Disconnect removes stored YouTube tokens from ChatMod Mobile and asks Google to revoke them when OAuth is configured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDisconnectYouTube, enabled = !account.isBusy) {
                    Icon(Icons.Default.Link, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Disconnect YouTube")
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Danger zone", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Text(
                    "Local wipe only clears this phone. Account deletion removes cloud data after you export anything you need to keep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onWipeLocalData, enabled = !account.isBusy) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Wipe local data")
                }
                TextButton(onClick = onDeleteAccount, enabled = !account.isBusy) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Delete account data", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun YouTubeConnectStatus(connect: YouTubeConnectState) {
    val configured = connect.configured
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FeatureRow(
            "OAuth status",
            when (configured) {
                true -> if (connect.authUrlAvailable) "Ready to open Google sign-in" else "Configured, URL unavailable"
                false -> "Backend OAuth not configured"
                null -> "Not checked"
            }
        )
        if (connect.requiredScopes.isNotEmpty()) {
            FeatureRow("YouTube scopes", youtubeScopeSummary(connect.requiredScopes))
        }
        if (connect.missingEnv.isNotEmpty()) {
            Text(
                "Missing backend env: ${connect.missingEnv.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        connect.note?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LinkedAccountReceiptRow(linkedAccount: LinkedAccountReceipt) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            linkedAccount.provider.accountProviderLabel(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            linkedAccount.channelTitle
                ?: linkedAccount.channelId
                ?: linkedAccount.providerAccountId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        linkedAccount.channelId?.let { channelId ->
            Text(
                channelId,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun String.accountProviderLabel(): String {
    return when (lowercase()) {
        "youtube" -> "YouTube channel"
        else -> replaceFirstChar { it.uppercase() }
    }
}

private fun youtubeScopeSummary(scopes: List<String>): String {
    val readable = scopes.mapNotNull { scope ->
        when {
            scope.endsWith("/auth/youtube.readonly") -> "read channel and stream metadata"
            scope.endsWith("/auth/youtube.force-ssl") -> "manage live-chat messages"
            else -> null
        }
    }.distinct()

    return if (readable.isEmpty()) {
        "${scopes.size} YouTube scopes"
    } else {
        readable.joinToString(", ")
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SupportPanel(
    support: SupportPanelState,
    onRefresh: () -> Unit,
    onSendDiagnostic: () -> Unit,
    onFeedbackCategoryChange: (BetaFeedbackCategory) -> Unit,
    onFeedbackMessageChange: (String) -> Unit,
    onSubmitFeedback: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Support",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Button(onClick = onSendDiagnostic, enabled = !support.isBusy) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Send")
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (support.isBusy) Icons.Default.Sync else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (support.isBusy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Diagnostics", fontWeight = FontWeight.SemiBold)
                        Text(
                            support.statusMessage ?: "Send a diagnostic snapshot when something feels off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    "Diagnostics include app state such as bot running, sync status, counts, and plan. They do not include message bodies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Beta feedback", fontWeight = FontWeight.SemiBold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BetaFeedbackCategory.entries.forEach { category ->
                        FilterChip(
                            selected = support.feedbackCategory == category,
                            onClick = { onFeedbackCategoryChange(category) },
                            enabled = !support.isBusy,
                            label = { Text(category.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = support.feedbackMessage,
                    onValueChange = onFeedbackMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !support.isBusy,
                    minLines = 3,
                    maxLines = 6,
                    label = { Text("What should we fix or build?") }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "${support.feedbackMessage.length}/1000",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onSubmitFeedback,
                        enabled = !support.isBusy && support.feedbackMessage.isNotBlank()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Send feedback")
                    }
                }
                support.feedbackStatusMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Recent feedback", fontWeight = FontWeight.SemiBold)

                if (support.feedback.isEmpty()) {
                    Text(
                        "No beta notes submitted yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    support.feedback.take(5).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.category.feedbackCategoryLabel(), fontWeight = FontWeight.Medium)
                                Text(
                                    item.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    item.createdAt,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent diagnostics", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onRefresh, enabled = !support.isBusy) {
                        Text("Refresh")
                    }
                }

                if (support.events.isEmpty()) {
                    Text(
                        "No diagnostics sent yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    support.events.forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                if (event.severity == "error") Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (event.severity == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.message, fontWeight = FontWeight.Medium)
                                Text(
                                    "${event.severity} - ${event.createdAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Recent API errors", fontWeight = FontWeight.SemiBold)

                if (support.apiErrors.isEmpty()) {
                    Text(
                        "No backend API errors recorded for this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    support.apiErrors.take(5).forEach { error ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(error.code ?: error.provider, fontWeight = FontWeight.Medium)
                                Text(
                                    error.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    listOfNotNull(error.requestId, error.createdAt).joinToString(" - "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.feedbackCategoryLabel(): String {
    return BetaFeedbackCategory.entries.firstOrNull { category -> category.apiValue == this }?.label ?: "Other"
}

private fun StreamCandidateSummary.streamMetaLine(): String {
    val statusLabel = when (status) {
        "active" -> "Active"
        "upcoming" -> "Scheduled"
        else -> status
    }
    val time = actualStartTime ?: scheduledStartTime
    val chat = if (liveChatId.isNullOrBlank()) "No chat yet" else "Live chat ready"
    return listOfNotNull(statusLabel, time, chat).joinToString(" - ")
}

private fun connectionTestStatus(selector: StreamSelectorState): String {
    val message = selector.testStatusMessage
    val messageId = selector.lastTestMessageId
    return when {
        !messageId.isNullOrBlank() && message == "Test message sent" -> "$message - $messageId"
        !message.isNullOrBlank() -> message
        else -> "Sends one message to the selected live chat."
    }
}

private fun moderatorPermissionStatus(selector: StreamSelectorState): String {
    val checkedAt = selector.lastPermissionCheckAt
    return when {
        !checkedAt.isNullOrBlank() -> "Delete check passed at $checkedAt"
        !selector.permissionCheckStatusMessage.isNullOrBlank() -> selector.permissionCheckStatusMessage
        selector.lastTestMessageId.isNullOrBlank() -> "Send a test message before checking moderator tools."
        else -> "Deletes the test message to verify the moderation action path."
    }
}

private fun LogEntrySummary.feedIcon(): ImageVector {
    return when (kind) {
        LogEntryKind.Chat -> Icons.Default.Link
        LogEntryKind.RuleMatch -> Icons.Default.CheckCircle
        LogEntryKind.Moderation -> Icons.AutoMirrored.Filled.Rule
        LogEntryKind.Runtime -> Icons.Default.Sync
    }
}

private fun LogEntrySummary.feedTitle(): String {
    return when (kind) {
        LogEntryKind.Chat -> detail.substringBefore(":").takeIf { it.isNotBlank() } ?: "Chat"
        LogEntryKind.RuleMatch -> title
        else -> title
    }
}

private fun LogEntrySummary.feedDetail(): String {
    return when (kind) {
        LogEntryKind.Chat -> detail.substringAfter(": ", detail)
        LogEntryKind.RuleMatch -> detail
        else -> detail
    }
}

private fun UserHistorySummary.userIcon(): ImageVector {
    return when (severity) {
        Severity.Danger -> Icons.Default.Warning
        Severity.Warning -> Icons.AutoMirrored.Filled.Rule
        Severity.Info -> Icons.Default.Person
    }
}

private fun UserHistorySummary.userMetaLine(): String {
    return listOf(
        "$messageCount messages",
        "$moderationActionCount actions",
        riskLabel(),
        "last ${lastSeenMillis.relativeTimeLabel()}"
    ).joinToString(" - ")
}

private fun UserHistorySummary.riskLabel(): String {
    return when {
        destructiveActionCount > 0 -> "$destructiveActionCount destructive"
        warningActionCount > 0 -> "$warningActionCount warnings"
        moderationActionCount > 0 -> "reviewed"
        else -> "clean"
    }
}

private fun UserModerationActionHistorySummary.historyLine(): String {
    val label = when (actionType) {
        "timeoutUser" -> durationSeconds?.let { "Timed out ${it / 60}m" } ?: "Timed out"
        "hideUser" -> "Hidden"
        "unbanUser" -> "Unbanned"
        else -> actionType.replaceFirstChar { it.uppercase() }
    }
    val expiry = expiresAt?.takeIf { it.isNotBlank() }?.let { " until ${it.shortDateTimeLabel()}" }.orEmpty()
    return "$label - ${createdAt.shortDateTimeLabel()}$expiry - $reason"
}

private fun UserProfileDrawerState.latestReversibleModerationAction(): UserModerationActionHistorySummary? {
    val unbannedIds = recentModerationActions
        .filter { action -> action.actionType == "unbanUser" }
        .mapNotNull { action -> action.liveChatBanId }
        .toSet()
    return recentModerationActions.firstOrNull { action ->
        action.liveChatBanId != null &&
            action.liveChatBanId !in unbannedIds &&
            (action.actionType == "hideUser" || action.actionType == "timeoutUser")
    }
}

@Composable
private fun UserHistorySummary.severityColor(): Color {
    return when (severity) {
        Severity.Info -> MaterialTheme.colorScheme.secondary
        Severity.Warning -> MaterialTheme.colorScheme.tertiary
        Severity.Danger -> MaterialTheme.colorScheme.error
    }
}

private fun LogEntrySummary.relativeTimeLabel(nowMillis: Long = System.currentTimeMillis()): String {
    return createdAtMillis.relativeTimeLabel(nowMillis)
}

private fun Long.relativeTimeLabel(nowMillis: Long = System.currentTimeMillis()): String {
    val ageSeconds = ((nowMillis - this) / 1000L).coerceAtLeast(0L)
    return when {
        ageSeconds < 60 -> "Just now"
        ageSeconds < 3600 -> "${ageSeconds / 60L}m ago"
        ageSeconds < 86400 -> "${ageSeconds / 3600L}h ago"
        else -> "${ageSeconds / 86400L}d ago"
    }
}

private fun String.shortDateTimeLabel(): String {
    val date = take(10)
    val time = drop(11).take(5)
    return if (date.length == 10 && time.length == 5) "$date $time" else ifBlank { "unknown" }
}

@Composable
private fun LogEntrySummary.severityColor(): Color {
    return when (severity) {
        Severity.Info -> MaterialTheme.colorScheme.secondary
        Severity.Warning -> MaterialTheme.colorScheme.tertiary
        Severity.Danger -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun FeatureRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TeamMemberRow(member: TeamMemberSummary, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.displayName, fontWeight = FontWeight.SemiBold)
            Text(
                "${member.role} - ${member.status} - ${member.inviteCodePreview}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onRevoke, enabled = member.status != "revoked") {
            Icon(Icons.Default.Delete, contentDescription = null)
            Spacer(modifier = Modifier.size(6.dp))
            Text("Revoke")
        }
    }
}

private fun String.overlayThemeLabel(): String {
    return when (this) {
        "transparent_minimal" -> "Transparent minimal"
        "high_contrast" -> "High contrast"
        else -> "Control room"
    }
}

private fun AppCompatibilityState.versionLabel(): String {
    val versionName = currentVersionName.ifBlank { "Unknown" }
    return if (currentVersionCode > 0) "$versionName ($currentVersionCode)" else versionName
}

private fun AppCompatibilityState.updateLabel(): String {
    return when {
        updateRequired -> "Required"
        updateRecommended -> "Available"
        status == "unavailable" -> "Unavailable"
        !checked -> "Checking"
        else -> "Compatible"
    }
}

private fun AppCompatibilityState.noticeMessage(): String? {
    return message?.takeIf { updateRequired || updateRecommended || status == "unavailable" }
}

@Composable
private fun AccountConfirmationDialog(
    action: AccountConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.confirmTitle()) },
        text = {
            Text(
                action.confirmMessage(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(action.confirmButton())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RulePresetImportDialog(
    state: RulePresetImportDialogState,
    loading: Boolean,
    onTextChange: (String) -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import presets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.bundleJson,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    label = { Text("Preset bundle JSON") },
                    supportingText = {
                        Text(state.errorMessage ?: "Paste a ChatMod rule preset export bundle.")
                    },
                    isError = state.errorMessage != null,
                    minLines = 6,
                    maxLines = 10,
                    enabled = !loading
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = !loading && state.bundleJson.isNotBlank()
            ) {
                Text(if (loading) "Importing" else "Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !loading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CommandEditorDialog(
    editor: CommandEditorState,
    onChange: (CommandEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.mode == EditorMode.Create) "New command" else "Edit command") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onChange(editor.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Trigger") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.response,
                    onValueChange = { onChange(editor.copy(response = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Reply") },
                    minLines = 3
                )
                OutlinedTextField(
                    value = editor.aliasesText,
                    onValueChange = { onChange(editor.copy(aliasesText = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Aliases") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.cooldownSecondsText,
                    onValueChange = { onChange(editor.copy(cooldownSecondsText = it.filter(Char::isDigit))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cooldown seconds") },
                    singleLine = true
                )
                AccessLevelChips(
                    selected = editor.accessLevel,
                    onSelected = { onChange(editor.copy(accessLevel = it)) }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", modifier = Modifier.weight(1f))
                    Switch(
                        checked = editor.enabled,
                        onCheckedChange = { onChange(editor.copy(enabled = it)) }
                    )
                }
                editor.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun AccountConfirmation.confirmTitle(): String {
    return when (this) {
        AccountConfirmation.DisconnectYouTube -> "Disconnect YouTube?"
        AccountConfirmation.DeleteAccount -> "Delete account data?"
        AccountConfirmation.WipeLocalData -> "Wipe local data?"
        is AccountConfirmation.DeleteBackup -> "Delete backup?"
        is AccountConfirmation.RestoreBackup -> "Restore backup?"
    }
}

private fun AccountConfirmation.confirmMessage(): String {
    return when (this) {
        AccountConfirmation.DisconnectYouTube -> "ChatMod Mobile will remove stored YouTube tokens for this account. You can reconnect later."
        AccountConfirmation.DeleteAccount -> "This deletes cloud account data, linked accounts, profiles, backups, subscription rows, support events, and account audit logs."
        AccountConfirmation.WipeLocalData -> "This clears commands, timers, local chat message logs, moderation logs, runtime events, queued sync jobs, and settings from this phone. Cloud data is not changed."
        is AccountConfirmation.DeleteBackup -> "Delete $label from cloud backups? Local settings on this phone are not changed."
        is AccountConfirmation.RestoreBackup -> "Restore $label into this phone's active local profile? Existing commands and timers with the same IDs will be updated."
    }
}

private fun AccountConfirmation.confirmButton(): String {
    return when (this) {
        AccountConfirmation.DisconnectYouTube -> "Disconnect"
        AccountConfirmation.DeleteAccount -> "Delete"
        AccountConfirmation.WipeLocalData -> "Wipe"
        is AccountConfirmation.DeleteBackup -> "Delete"
        is AccountConfirmation.RestoreBackup -> "Restore"
    }
}

private fun ModerationConfirmation.confirmTitle(): String {
    return when (this) {
        is ModerationConfirmation.DeleteQueueMessage -> "Delete live message?"
        is ModerationConfirmation.TimeoutUser -> "Timeout viewer?"
        is ModerationConfirmation.HideUser -> "Hide viewer?"
        is ModerationConfirmation.UnbanUser -> "Unban viewer?"
    }
}

private fun ModerationConfirmation.confirmMessage(): String {
    return when (this) {
        is ModerationConfirmation.DeleteQueueMessage -> "Delete ${author}'s selected YouTube Live chat message? The local queue item clears after YouTube accepts the delete."
        is ModerationConfirmation.TimeoutUser -> "Time out $displayName from the selected live chat for 5 minutes. They can chat again after the timeout expires."
        is ModerationConfirmation.HideUser -> "Permanently hide $displayName from the selected live chat where YouTube permits it. Use this only for clear abuse."
        is ModerationConfirmation.UnbanUser -> "Undo the last ChatMod-created ban or timeout for $displayName where YouTube still accepts this saved ban id."
    }
}

private fun ModerationConfirmation.confirmButton(): String {
    return when (this) {
        is ModerationConfirmation.DeleteQueueMessage -> "Delete"
        is ModerationConfirmation.TimeoutUser -> "Timeout 5m"
        is ModerationConfirmation.HideUser -> "Hide user"
        is ModerationConfirmation.UnbanUser -> "Unban"
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun AccessLevelChips(
    selected: CommandAccessLevel,
    onSelected: (CommandAccessLevel) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CommandAccessLevel.entries.forEach { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelected(level) },
                label = { Text(level.label) }
            )
        }
    }
}

@Composable
private fun TimerEditorDialog(
    editor: TimerEditorState,
    onChange: (TimerEditorState) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.mode == EditorMode.Create) "New timer" else "Edit timer") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = { onChange(editor.copy(name = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.message,
                    onValueChange = { onChange(editor.copy(message = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Message") },
                    minLines = 3
                )
                OutlinedTextField(
                    value = editor.intervalMinutesText,
                    onValueChange = { onChange(editor.copy(intervalMinutesText = it.filter(Char::isDigit))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Interval minutes") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = editor.minChatMessagesText,
                    onValueChange = { onChange(editor.copy(minChatMessagesText = it.filter(Char::isDigit))) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Minimum chat messages") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quiet window", modifier = Modifier.weight(1f))
                    Switch(
                        checked = editor.quietHoursEnabled,
                        onCheckedChange = { enabled ->
                            onChange(
                                editor.copy(
                                    quietHoursEnabled = enabled,
                                    quietStartMinutesText = if (enabled) editor.quietStartMinutesText else "",
                                    quietEndMinutesText = if (enabled) editor.quietEndMinutesText else ""
                                )
                            )
                        }
                    )
                }
                if (editor.quietHoursEnabled) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = editor.quietStartMinutesText,
                            onValueChange = { onChange(editor.copy(quietStartMinutesText = it.filter(Char::isDigit))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Quiet start min") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editor.quietEndMinutesText,
                            onValueChange = { onChange(editor.copy(quietEndMinutesText = it.filter(Char::isDigit))) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Resume min") },
                            singleLine = true
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enabled", modifier = Modifier.weight(1f))
                    Switch(
                        checked = editor.enabled,
                        onCheckedChange = { onChange(editor.copy(enabled = it)) }
                    )
                }
                editor.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LogsPanel(
    logs: LogPanelState,
    recentActions: List<ActionLogItem>,
    onSelectFilter: (LogEntryFilter) -> Unit,
    onRefreshProAnalytics: () -> Unit,
    onExportLogs: () -> Unit,
    onMarkFalsePositive: (String) -> Unit,
    onTuneFalsePositive: (String) -> Unit
) {
    val visibleEntries = logs.entries.filter { logs.filter.matches(it.kind) }
    val sessionModerationSummary = logs.entries.sessionModerationSummary()
    val topRuleMatches = logs.entries.topRuleMatches(limit = 5)
    val falsePositiveCandidates = logs.entries.falsePositiveReviewCandidates(limit = 5)
    val activeChatters = logs.entries.mostActiveChatters(limit = 5)
    val commandUsage = logs.entries.mostUsedCommands(limit = 5)
    val ruleEffectiveness = logs.entries.ruleEffectivenessSummary()
    val spamTrend = logs.entries.spamAttemptsTrend(bucketCount = 6)
    val uptimeReconnect = logs.entries.uptimeReconnectSummary()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                "Logs",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedButton(
                onClick = onExportLogs,
                enabled = logs.entries.isNotEmpty()
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("Export")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LogEntryFilter.entries.forEach { filter ->
                FilterChip(
                    selected = logs.filter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text(filter.label) }
                )
            }
        }

        OutlinedCard(shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (logs.entries.isEmpty()) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (logs.entries.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Local log store", fontWeight = FontWeight.SemiBold)
                        Text(
                            logs.statusMessage ?: "Room log store is connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (sessionModerationSummary.timedOutOrHiddenUsers > 0) {
            SessionModerationSummaryCard(summary = sessionModerationSummary)
        }

        ProAnalyticsCard(
            analytics = logs.proAnalytics,
            onRefresh = onRefreshProAnalytics
        )

        AiChatSummaryCard(summary = logs.aiChatSummary)

        if (topRuleMatches.isNotEmpty()) {
            TopTriggeredRulesCard(rules = topRuleMatches)
        }

        if (activeChatters.isNotEmpty()) {
            ActiveChattersCard(chatters = activeChatters)
        }

        if (commandUsage.isNotEmpty()) {
            CommandUsageCard(commands = commandUsage)
        }

        if (ruleEffectiveness.totalRuleMatches > 0 || spamTrend.totalAttempts > 0) {
            RuleAnalyticsCard(
                effectiveness = ruleEffectiveness,
                trend = spamTrend
            )
        }

        if (uptimeReconnect.sessionCount > 0 || uptimeReconnect.reconnectEvents > 0) {
            UptimeReconnectCard(summary = uptimeReconnect)
        }

        if (falsePositiveCandidates.isNotEmpty()) {
            FalsePositiveReviewCard(
                entries = falsePositiveCandidates,
                onMarkFalsePositive = onMarkFalsePositive,
                onTuneFalsePositive = onTuneFalsePositive
            )
        }

        if (visibleEntries.isEmpty()) {
            OutlinedCard(shape = RoundedCornerShape(8.dp)) {
                Text(
                    if (logs.entries.isEmpty()) "No local log entries yet." else "No entries match this filter.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            visibleEntries.forEach { entry ->
                LogEntryRow(entry = entry)
            }
        }

        if (recentActions.isNotEmpty()) {
            OutlinedCard(shape = RoundedCornerShape(8.dp)) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Recent UI actions", fontWeight = FontWeight.SemiBold)
                    recentActions.take(4).forEach { action ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(action.label, fontWeight = FontWeight.Medium)
                            Text(
                                action.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiChatSummaryCard(summary: AiChatSummaryPanelState) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("After-stream summary", fontWeight = FontWeight.SemiBold)
                    Text(
                        summary.statusMessage ?: "Local heuristic summary from synced chat logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when {
                summary.isLoading -> Text(
                    "Loading summary...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                summary.summary == null -> Text(
                    "Load Pro trends after a synced stream ends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> {
                    Text(summary.summary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReadOnlyStatusChip(
                            label = "${summary.messageCount} messages",
                            icon = Icons.Default.CheckCircle,
                            contentDescription = "After stream summary includes ${summary.messageCount} messages"
                        )
                        ReadOnlyStatusChip(
                            label = "${summary.uniqueChatters} chatters",
                            icon = Icons.Default.Person,
                            contentDescription = "After stream summary includes ${summary.uniqueChatters} chatters"
                        )
                        ReadOnlyStatusChip(
                            label = "${summary.destructiveActionCount} actions",
                            icon = if (summary.destructiveActionCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = "After stream summary includes ${summary.destructiveActionCount} destructive moderation actions",
                            warning = summary.destructiveActionCount > 0
                        )
                    }
                    summary.highlights.take(3).forEach { highlight ->
                        Text(
                            highlight,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    summary.topQuestions.firstOrNull()?.let { question ->
                        SummaryMetricRow("Top question", "${question.question} (${question.count})")
                    }
                    summary.suggestedFollowUps.take(2).forEach { followUp ->
                        Text(
                            followUp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProAnalyticsCard(
    analytics: ProAnalyticsPanelState,
    onRefresh: () -> Unit
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pro trends", fontWeight = FontWeight.SemiBold)
                    Text(
                        analytics.statusMessage ?: "Cross-stream analytics from synced audit logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = !analytics.isLoading
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (analytics.generatedAt == null) "Load" else "Refresh")
                }
            }

            if (analytics.isLoading) {
                Text(
                    "Loading trends...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!analytics.hasTrendData()) {
                Text(
                    "Sync stream sessions to unlock account-wide trends.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SummaryMetricRow("Range", "${analytics.rangeDays} days")
                SummaryMetricRow("Streams", analytics.sessionCount.toString())
                SummaryMetricRow("Messages", analytics.totalMessages.toString())
                SummaryMetricRow("Moderation actions", analytics.totalModerationActions.toString())
                SummaryMetricRow("Total uptime", formatDuration(analytics.totalUptimeMillis))
                SummaryMetricRow("Reconnects", analytics.reconnectEvents.toString())

                AnalyticsTrendSection(
                    title = "Message trend",
                    rows = analytics.dayTrends.takeLast(7).map { day ->
                        TrendMetricRow(
                            label = day.day.shortDayLabel(),
                            value = day.messageCount,
                            detail = "${day.spamAttemptCount} spam"
                        )
                    }
                )
                AnalyticsTrendSection(
                    title = "Audience trend",
                    rows = analytics.audienceTrends.take(5).map { chatter ->
                        TrendMetricRow(
                            label = chatter.authorName.ifBlank { chatter.authorChannelId },
                            value = chatter.messageCount + chatter.moderationActionCount,
                            detail = "${chatter.messageCount} msg"
                        )
                    }
                )
                AnalyticsTrendSection(
                    title = "Command trend",
                    rows = analytics.commandTrends.take(5).map { command ->
                        TrendMetricRow(
                            label = command.label,
                            value = command.count,
                            detail = "${command.count} uses"
                        )
                    }
                )
                AnalyticsTrendSection(
                    title = "Rule effectiveness",
                    rows = analytics.ruleTrends.take(5).map { rule ->
                        TrendMetricRow(
                            label = rule.rule,
                            value = rule.matchCount,
                            detail = "${rule.destructiveActionCount} acted / ${rule.falsePositiveCount} false"
                        )
                    }
                )
                AnalyticsTrendSection(
                    title = "Rule by preset/version",
                    rows = analytics.rulePresetTrends.take(6).map { rule ->
                        TrendMetricRow(
                            label = rule.presetRuleLabel(),
                            value = rule.matchCount,
                            detail = "${rule.destructiveActionCount} acted / ${rule.falsePositiveCount} false"
                        )
                    }
                )
                AnalyticsTrendSection(
                    title = "Stream uptime",
                    rows = analytics.streamUptime.take(5).map { stream ->
                        TrendMetricRow(
                            label = stream.title ?: stream.sessionId,
                            value = (stream.uptimeMillis / 60_000L).toInt().coerceAtLeast(0),
                            detail = "${stream.reconnectEvents} reconnects"
                        )
                    }
                )

                analytics.generatedAt?.let { generatedAt ->
                    Text(
                        "Generated $generatedAt",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun ProAnalyticsPanelState.hasTrendData(): Boolean {
    return sessionCount > 0 ||
        totalMessages > 0 ||
        totalModerationActions > 0 ||
        dayTrends.isNotEmpty() ||
        audienceTrends.isNotEmpty() ||
        commandTrends.isNotEmpty() ||
        ruleTrends.isNotEmpty() ||
        rulePresetTrends.isNotEmpty() ||
        streamUptime.isNotEmpty()
}

private fun String.shortDayLabel(): String {
    return if (length >= 10) {
        substring(5, 10)
    } else {
        this
    }
}

private data class TrendMetricRow(
    val label: String,
    val value: Int,
    val detail: String
)

private fun ProAnalyticsRulePresetSummary.presetRuleLabel(): String {
    val name = presetName?.takeIf { it.isNotBlank() } ?: presetId
    val version = presetVersion
        ?.takeIf { it.isNotBlank() }
        ?.let { " rev ${it.takeLast(6)}" }
        .orEmpty()
    return "$name$version - $rule"
}

@Composable
private fun AnalyticsTrendSection(
    title: String,
    rows: List<TrendMetricRow>
) {
    if (rows.isEmpty()) {
        return
    }
    val maxValue = rows.maxOf { row -> row.value }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        rows.forEach { row ->
            TrendMetricBar(row = row, maxValue = maxValue)
        }
    }
}

@Composable
private fun TrendMetricBar(
    row: TrendMetricRow,
    maxValue: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                row.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                row.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((row.value.toFloat() / maxValue.toFloat()).coerceIn(0.04f, 1f))
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun ActiveChattersCard(chatters: List<ChatterActivitySummary>) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnalyticsCardHeader(
                icon = Icons.Default.Person,
                title = "Most active chatters",
                detail = "Ranked from local chat and moderation logs"
            )
            chatters.forEach { chatter ->
                SummaryMetricRow(
                    label = chatter.name,
                    value = "${chatter.messageCount} msg${if (chatter.moderationCount > 0) " / ${chatter.moderationCount} mod" else ""}"
                )
            }
        }
    }
}

@Composable
private fun CommandUsageCard(commands: List<CommandUsageSummary>) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnalyticsCardHeader(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Most used commands",
                detail = "Counted from local command runtime events"
            )
            commands.forEach { command ->
                SummaryMetricRow(command.label, command.count.toString())
            }
        }
    }
}

@Composable
private fun RuleAnalyticsCard(
    effectiveness: RuleEffectivenessSummary,
    trend: SpamTrendSummary
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnalyticsCardHeader(
                icon = Icons.AutoMirrored.Filled.Rule,
                title = "Rule analytics",
                detail = "Effectiveness and spam attempts from local rule logs"
            )
            SummaryMetricRow("Rule matches", effectiveness.totalRuleMatches.toString())
            SummaryMetricRow("Deleted/hidden", effectiveness.destructiveActions.toString())
            SummaryMetricRow("False positives", effectiveness.falsePositives.toString())
            SummaryMetricRow("Review rate", "${effectiveness.reviewRatePercent}%")
            if (trend.buckets.isNotEmpty()) {
                Text(
                    "Spam attempts over time",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                trend.buckets.forEach { bucket ->
                    SummaryMetricRow(bucket.label, bucket.count.toString())
                }
            }
        }
    }
}

@Composable
private fun UptimeReconnectCard(summary: UptimeReconnectSummary) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnalyticsCardHeader(
                icon = Icons.Default.Sync,
                title = "Uptime and reconnects",
                detail = "Runtime health from local session events"
            )
            SummaryMetricRow("Completed sessions", summary.sessionCount.toString())
            SummaryMetricRow("Last session", formatDuration(summary.lastSessionDurationMillis))
            SummaryMetricRow("Total uptime", formatDuration(summary.totalDurationMillis))
            SummaryMetricRow("Reconnect/backoff events", summary.reconnectEvents.toString())
        }
    }
}

@Composable
private fun AnalyticsCardHeader(
    icon: ImageVector,
    title: String,
    detail: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionModerationSummaryCard(summary: SessionModerationSummary) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Session summary", fontWeight = FontWeight.SemiBold)
                    Text(
                        "User-level moderation actions from local logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SummaryMetricRow(
                label = "Users timed out/hidden",
                value = summary.timedOutOrHiddenUsers.toString()
            )
            SummaryMetricRow(
                label = "Timed out",
                value = summary.timedOutUsers.toString()
            )
            SummaryMetricRow(
                label = "Hidden/banned",
                value = summary.hiddenUsers.toString()
            )
        }
    }
}

@Composable
private fun SummaryMetricRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FalsePositiveReviewCard(
    entries: List<LogEntrySummary>,
    onMarkFalsePositive: (String) -> Unit,
    onTuneFalsePositive: (String) -> Unit
) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("False positive review", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Flagged rule matches that were held for review",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            entries.forEach { entry ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            entry.ruleMatchLabel(),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.relativeTimeLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        entry.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { onTuneFalsePositive(entry.id) }) {
                            Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Tune preset")
                        }
                        TextButton(onClick = { onMarkFalsePositive(entry.id) }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("False positive")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopTriggeredRulesCard(rules: List<RuleMatchSummary>) {
    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.size(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Top triggered rules", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Ranked from local rule-match logs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            rules.forEach { rule ->
                val accent = when (rule.severity) {
                    Severity.Info -> MaterialTheme.colorScheme.primary
                    Severity.Warning -> Color(red = 0.78f, green = 0.52f, blue = 0.08f)
                    Severity.Danger -> MaterialTheme.colorScheme.error
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(accent, RoundedCornerShape(9.dp))
                    )
                    Text(
                        rule.label,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${rule.count} ${if (rule.count == 1) "hit" else "hits"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntrySummary) {
    val accent = when (entry.severity) {
        Severity.Info -> MaterialTheme.colorScheme.primary
        Severity.Warning -> Color(red = 0.78f, green = 0.52f, blue = 0.08f)
        Severity.Danger -> MaterialTheme.colorScheme.error
    }

    OutlinedCard(shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(accent, RoundedCornerShape(10.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(
                        entry.kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(entry.detail, style = MaterialTheme.typography.bodyMedium)
                Text(
                    formatRelativeTime(entry.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.reviewStatus == FalsePositiveReviewStatus) {
                    Text(
                        "Marked false positive${entry.reviewedAtMillis?.let { " ${formatRelativeTime(it)}" } ?: ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

private fun LogEntryFilter.matches(kind: LogEntryKind): Boolean {
    return when (this) {
        LogEntryFilter.All -> true
        LogEntryFilter.Chat -> kind == LogEntryKind.Chat
        LogEntryFilter.RuleMatch -> kind == LogEntryKind.RuleMatch
        LogEntryFilter.Moderation -> kind == LogEntryKind.Moderation
        LogEntryFilter.Runtime -> kind == LogEntryKind.Runtime
    }
}

private fun List<LogEntrySummary>.sessionModerationSummary(): SessionModerationSummary {
    val timeoutEntries = filter { entry -> entry.actionType.equals("timeoutUser", ignoreCase = true) }
    val hiddenEntries = filter { entry -> entry.actionType.equals("hideUser", ignoreCase = true) }
    val timedOutOrHiddenEntries = timeoutEntries + hiddenEntries

    return SessionModerationSummary(
        timedOutOrHiddenUsers = timedOutOrHiddenEntries.distinctSubjectCount(),
        timedOutUsers = timeoutEntries.distinctSubjectCount(),
        hiddenUsers = hiddenEntries.distinctSubjectCount()
    )
}

private fun List<LogEntrySummary>.distinctSubjectCount(): Int {
    return map { entry -> entry.subjectKey ?: entry.id }.distinct().size
}

private fun List<LogEntrySummary>.topRuleMatches(limit: Int): List<RuleMatchSummary> {
    return filter { entry -> entry.kind == LogEntryKind.RuleMatch }
        .filterNot { entry -> entry.reviewStatus == FalsePositiveReviewStatus }
        .groupBy { entry -> entry.ruleMatchLabel() }
        .map { (label, entries) ->
            RuleMatchSummary(
                label = label,
                count = entries.size,
                severity = entries.maxByOrNull { entry -> entry.severity.ordinal }?.severity ?: Severity.Info
            )
        }
        .sortedWith(
            compareByDescending<RuleMatchSummary> { it.count }
                .thenByDescending { it.severity.ordinal }
                .thenBy { it.label }
        )
        .take(limit)
}

private fun List<LogEntrySummary>.falsePositiveReviewCandidates(limit: Int): List<LogEntrySummary> {
    return filter { entry -> entry.kind == LogEntryKind.RuleMatch && entry.reviewCandidate }
        .sortedByDescending { entry -> entry.createdAtMillis }
        .take(limit)
}

private fun List<LogEntrySummary>.mostActiveChatters(limit: Int): List<ChatterActivitySummary> {
    val chatters = linkedMapOf<String, ChatterActivityAccumulator>()
    forEach { entry ->
        if (entry.kind != LogEntryKind.Chat && entry.kind != LogEntryKind.Moderation && entry.kind != LogEntryKind.RuleMatch) {
            return@forEach
        }

        val key = entry.subjectKey ?: entry.subjectLabel ?: entry.detail.substringBefore(":").ifBlank { entry.id }
        val name = entry.subjectLabel ?: entry.detail.substringBefore(":").ifBlank { key }
        val accumulator = chatters.getOrPut(key) { ChatterActivityAccumulator(name = name) }
        accumulator.name = name
        accumulator.lastSeenMillis = maxOf(accumulator.lastSeenMillis, entry.createdAtMillis)
        if (entry.kind == LogEntryKind.Chat) {
            accumulator.messageCount += 1
        } else {
            accumulator.moderationCount += 1
        }
    }

    return chatters.values
        .map { accumulator ->
            ChatterActivitySummary(
                name = accumulator.name,
                messageCount = accumulator.messageCount,
                moderationCount = accumulator.moderationCount,
                lastSeenMillis = accumulator.lastSeenMillis
            )
        }
        .filter { chatter -> chatter.messageCount > 0 || chatter.moderationCount > 0 }
        .sortedWith(
            compareByDescending<ChatterActivitySummary> { it.messageCount + it.moderationCount }
                .thenByDescending { it.moderationCount }
                .thenByDescending { it.lastSeenMillis }
        )
        .take(limit)
}

private fun List<LogEntrySummary>.mostUsedCommands(limit: Int): List<CommandUsageSummary> {
    val eventCounts = linkedMapOf<String, CommandUsageAccumulator>()
    filter { entry -> entry.kind == LogEntryKind.Runtime && entry.title.equals("Command sent", ignoreCase = true) }
        .forEach { entry ->
            val metadata = entry.metadataObject()
            val commandId = metadata?.optString("commandId")?.takeIf { it.isNotBlank() }
            val trigger = metadata?.optString("trigger")?.takeIf { it.isNotBlank() }
            val key = commandId ?: trigger ?: entry.detail
            val accumulator = eventCounts.getOrPut(key) { CommandUsageAccumulator(label = trigger ?: commandId ?: "Unknown command") }
            accumulator.label = trigger ?: commandId ?: accumulator.label
            accumulator.count += 1
        }

    val counts = if (eventCounts.isNotEmpty()) eventCounts else completedCommandSummaryCounts()
    return counts.values
        .map { accumulator -> CommandUsageSummary(label = accumulator.label, count = accumulator.count) }
        .sortedWith(compareByDescending<CommandUsageSummary> { it.count }.thenBy { it.label })
        .take(limit)
}

private fun List<LogEntrySummary>.completedCommandSummaryCounts(): Map<String, CommandUsageAccumulator> {
    val counts = linkedMapOf<String, CommandUsageAccumulator>()
    filter { entry -> entry.kind == LogEntryKind.Runtime && entry.title.equals("Runtime session summary", ignoreCase = true) }
        .forEach { entry ->
            val summaryCounts = entry.metadataObject()
                ?.optString("commandsUsedJson")
                ?.takeIf { it.isNotBlank() }
                ?.jsonObjectOrNull()
                ?: return@forEach

            summaryCounts
                .keys()
                .asSequence()
                .forEach { commandId ->
                    val count = summaryCounts.optInt(commandId, 0)
                    if (count > 0) {
                        val accumulator = counts.getOrPut(commandId) { CommandUsageAccumulator(label = commandId) }
                        accumulator.count += count
                    }
                }
        }
    return counts
}

private fun List<LogEntrySummary>.ruleEffectivenessSummary(): RuleEffectivenessSummary {
    val ruleEntries = filter { entry -> entry.kind == LogEntryKind.RuleMatch }
    val falsePositives = ruleEntries.count { entry -> entry.reviewStatus == FalsePositiveReviewStatus }
    val destructive = ruleEntries.count { entry ->
        val action = entry.actionType.orEmpty().lowercase()
        action.contains("delete") || action.contains("hide") || action.contains("ban")
    }
    val reviewRate = if (ruleEntries.isEmpty()) 0 else ((falsePositives * 100.0) / ruleEntries.size).toInt()

    return RuleEffectivenessSummary(
        totalRuleMatches = ruleEntries.size,
        destructiveActions = destructive,
        falsePositives = falsePositives,
        reviewRatePercent = reviewRate
    )
}

private fun List<LogEntrySummary>.spamAttemptsTrend(
    bucketCount: Int,
    nowMillis: Long = System.currentTimeMillis()
): SpamTrendSummary {
    val ruleEntries = filter { entry ->
        entry.kind == LogEntryKind.RuleMatch && entry.reviewStatus != FalsePositiveReviewStatus
    }
    if (ruleEntries.isEmpty()) {
        return SpamTrendSummary(totalAttempts = 0, buckets = emptyList())
    }

    val bucketSizeMillis = 10 * 60_000L
    val buckets = (bucketCount - 1 downTo 0).map { index ->
        val start = nowMillis - ((index + 1) * bucketSizeMillis)
        val end = nowMillis - (index * bucketSizeMillis)
        val count = ruleEntries.count { entry -> entry.createdAtMillis in start until end }
        TrendBucket(
            label = if (index == 0) "0-10m ago" else "${index * 10}-${(index + 1) * 10}m ago",
            count = count
        )
    }

    return SpamTrendSummary(
        totalAttempts = ruleEntries.size,
        buckets = buckets
    )
}

private fun List<LogEntrySummary>.uptimeReconnectSummary(): UptimeReconnectSummary {
    val sessionSummaries = filter { entry ->
        entry.kind == LogEntryKind.Runtime && entry.title.equals("Runtime session summary", ignoreCase = true)
    }
    val durations = sessionSummaries.mapNotNull { entry ->
        entry.metadataObject()?.optLong("durationMillis")?.takeIf { it > 0 }
    }
    val lastDuration = sessionSummaries
        .maxByOrNull { entry -> entry.createdAtMillis }
        ?.metadataObject()
        ?.optLong("durationMillis")
        ?.takeIf { it > 0 }
        ?: 0L
    val reconnectEvents = count { entry ->
        if (entry.kind != LogEntryKind.Runtime) {
            false
        } else {
            val title = entry.title.lowercase()
            title.contains("reconnect") || title.contains("backoff") || title.contains("waiting for network")
        }
    }

    return UptimeReconnectSummary(
        sessionCount = sessionSummaries.size,
        lastSessionDurationMillis = lastDuration,
        totalDurationMillis = durations.sum(),
        reconnectEvents = reconnectEvents
    )
}

private fun LogEntrySummary.metadataObject(): JSONObject? {
    return metadataJson?.jsonObjectOrNull()
}

private fun String.jsonObjectOrNull(): JSONObject? {
    return runCatching { JSONObject(this) }.getOrNull()
}

private fun formatDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours == 0L) {
        "${minutes}m"
    } else {
        "${hours}h ${minutes}m"
    }
}

private fun LogEntrySummary.ruleMatchLabel(): String {
    return title.removePrefix("Rule match:")
        .trim()
        .ifBlank { "Unknown rule" }
}

private data class ChatterActivityAccumulator(
    var name: String,
    var messageCount: Int = 0,
    var moderationCount: Int = 0,
    var lastSeenMillis: Long = 0L
)

private data class ChatterActivitySummary(
    val name: String,
    val messageCount: Int,
    val moderationCount: Int,
    val lastSeenMillis: Long
)

private data class CommandUsageAccumulator(
    var label: String,
    var count: Int = 0
)

private data class CommandUsageSummary(
    val label: String,
    val count: Int
)

private data class RuleEffectivenessSummary(
    val totalRuleMatches: Int,
    val destructiveActions: Int,
    val falsePositives: Int,
    val reviewRatePercent: Int
)

private data class SpamTrendSummary(
    val totalAttempts: Int,
    val buckets: List<TrendBucket>
)

private data class TrendBucket(
    val label: String,
    val count: Int
)

private data class UptimeReconnectSummary(
    val sessionCount: Int,
    val lastSessionDurationMillis: Long,
    val totalDurationMillis: Long,
    val reconnectEvents: Int
)

private fun formatRelativeTime(createdAtMillis: Long): String {
    val elapsedMinutes = ((System.currentTimeMillis() - createdAtMillis) / 60_000).coerceAtLeast(0)
    return when {
        elapsedMinutes < 1 -> "Just now"
        elapsedMinutes < 60 -> "${elapsedMinutes}m ago"
        elapsedMinutes < 1_440 -> "${elapsedMinutes / 60}h ago"
        else -> "${elapsedMinutes / 1_440}d ago"
    }
}

@Composable
private fun RecentActions(actions: List<ActionLogItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Recent actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (actions.isEmpty()) {
            Text(
                "No actions yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            actions.forEach { action ->
                Column {
                    Text(action.label, fontWeight = FontWeight.Medium)
                    Text(action.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}
