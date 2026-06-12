package com.chatmod.mobile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chatmod.mobile.billing.PlayBillingManager
import com.chatmod.mobile.data.local.LocalPrivacyStore
import com.chatmod.mobile.data.local.SettingsStore
import com.chatmod.mobile.data.remote.ChatModApiClient
import com.chatmod.mobile.data.remote.ChatModSessionManager
import com.chatmod.mobile.runtime.BotLogSink
import com.chatmod.mobile.runtime.BotForegroundService
import com.chatmod.mobile.support.UsageAnalyticsReporter
import com.chatmod.mobile.ui.ChatModApp
import com.chatmod.mobile.ui.dashboard.DashboardCommandTimerStore
import com.chatmod.mobile.ui.dashboard.DashboardViewModel
import com.chatmod.mobile.ui.dashboard.RoomDashboardLogStore
import com.chatmod.mobile.ui.theme.ChatModTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val dashboardCommandTimerStore: DashboardCommandTimerStore by inject()
    private val apiClient: ChatModApiClient by inject()
    private val sessionManager: ChatModSessionManager by inject()
    private val localPrivacyStore: LocalPrivacyStore by inject()
    private val settingsStore: SettingsStore by inject()
    private val dashboardLogStore: RoomDashboardLogStore by inject()
    private val botLogSink: BotLogSink by inject()
    private val analyticsReporter: UsageAnalyticsReporter by inject()
    private val playBillingManager: PlayBillingManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_ChatModMobile)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val batteryOptimizationIgnored = isBatteryOptimizationIgnored()

        setContent {
            val viewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModel.Factory(
                    commandTimerStore = dashboardCommandTimerStore,
                    entitlementApi = apiClient,
                    accessTokenProvider = { sessionManager.currentAccessToken() },
                    refreshAccessTokenProvider = { sessionManager.refreshedAccessToken() },
                    localPrivacyStore = localPrivacyStore,
                    settingsStore = settingsStore,
                    logStore = dashboardLogStore,
                    botLogSink = botLogSink,
                    batteryOptimizationIgnored = batteryOptimizationIgnored
                )
            )
            val state by viewModel.state.collectAsState()
            val playBillingState by playBillingManager.state.collectAsState()
            val pendingBrowserLaunch = state.account.pendingBrowserLaunch
            val pendingLogShare = state.logs.pendingShare
            val pendingRulePresetShare = state.rulePresets.pendingShare
            val pendingRuntimeRecovery = state.pendingRuntimeRecovery

            LaunchedEffect(Unit) {
                playBillingManager.start()
                playBillingManager.purchases.collect { purchase ->
                    val validated = viewModel.validatePlayBillingPurchase(
                        productId = purchase.productId,
                        purchaseToken = purchase.purchaseToken,
                        packageName = purchase.packageName
                    )
                    if (validated) {
                        playBillingManager.acknowledgePurchase(purchase.purchaseToken)
                        analyticsReporter.track(
                            "purchase_validated",
                            mapOf(
                                "productId" to purchase.productId,
                                "alreadyAcknowledged" to purchase.alreadyAcknowledged
                            )
                        )
                    }
                }
            }

            LaunchedEffect(pendingBrowserLaunch?.id) {
                val request = pendingBrowserLaunch ?: return@LaunchedEffect
                val launched = runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(request.url)))
                }.isSuccess
                viewModel.finishBrowserLaunch(request.id, launched)
            }

            LaunchedEffect(pendingLogShare?.id) {
                val request = pendingLogShare ?: return@LaunchedEffect
                val launched = runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("text/csv")
                        .putExtra(Intent.EXTRA_SUBJECT, request.subject)
                        .putExtra(Intent.EXTRA_TEXT, request.text)
                    startActivity(Intent.createChooser(shareIntent, "Export ChatMod logs"))
                }.isSuccess
                viewModel.finishLogExportShare(request.id, launched)
            }

            LaunchedEffect(pendingRulePresetShare?.id) {
                val request = pendingRulePresetShare ?: return@LaunchedEffect
                val launched = runCatching {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_SUBJECT, request.subject)
                        .putExtra(Intent.EXTRA_TEXT, request.text)
                    startActivity(Intent.createChooser(shareIntent, "Export ChatMod presets"))
                }.isSuccess
                viewModel.finishRulePresetExportShare(request.id, launched)
            }

            LaunchedEffect(pendingRuntimeRecovery?.id) {
                val request = pendingRuntimeRecovery ?: return@LaunchedEffect
                val recoveryIntent = Intent(this@MainActivity, BotForegroundService::class.java)
                val launched = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(recoveryIntent)
                    } else {
                        startService(recoveryIntent)
                    }
                }.isSuccess
                viewModel.finishRuntimeRecovery(request.id, launched)
            }

            ChatModTheme(highContrast = state.settings.highContrast) {
                ChatModApp(
                    state = state,
                    playBilling = playBillingState,
                    onToggleBot = {
                        val currentState = state
                        val changed = viewModel.toggleBot()
                        if (changed && currentState.botRunning) {
                            analyticsReporter.track("bot_stop")
                            startService(
                                Intent(this, BotForegroundService::class.java)
                                    .setAction(BotForegroundService.ActionStop)
                            )
                        } else if (changed) {
                            analyticsReporter.track(
                                "bot_start",
                                mapOf(
                                    "hasLiveChatId" to (currentState.liveChatId != null),
                                    "commandCount" to currentState.commands.size,
                                    "timerCount" to currentState.timers.size
                                )
                            )
                            startForegroundService(
                                Intent(this, BotForegroundService::class.java)
                                    .setAction(BotForegroundService.ActionStart)
                                    .putExtra(
                                        BotForegroundService.ExtraSessionId,
                                        "${currentState.liveChatId ?: "local-stream"}-${System.currentTimeMillis()}"
                                    )
                                    .putExtra(
                                        BotForegroundService.ExtraLiveChatId,
                                        currentState.liveChatId ?: "demo-live-chat"
                                    )
                                    .putExtra(
                                        BotForegroundService.ExtraVideoId,
                                        currentState.videoId ?: "demo-video"
                                    )
                                    .putExtra(
                                        BotForegroundService.ExtraStreamTitle,
                                        currentState.streamTitle
                                    )
                            )
                        }
                    },
                    onSendSubscriberOnlyRecommendation = viewModel::sendSubscriberOnlyRecommendation,
                    onResolveQueueItem = viewModel::resolveQueueItem,
                    onDeleteQueueItem = viewModel::requestDeleteQueueItemMessage,
                    onQuickBlockQueueItem = viewModel::quickBlockPhraseFromQueueItem,
                    onWarnQueueItem = viewModel::warnQueueItem,
                    onSuggestQueueItem = viewModel::suggestQueueItemAction,
                    onSelectTab = { tab ->
                        viewModel.selectTab(tab)
                        analyticsReporter.track("tab_selected", mapOf("tab" to tab.name))
                    },
                    onRefreshUserWarnings = viewModel::refreshUserWarnings,
                    onOpenUserProfile = viewModel::openUserProfile,
                    onUserProfileNotesChange = viewModel::updateUserProfileNotes,
                    onSaveUserProfileNotes = viewModel::saveUserProfileNotes,
                    onHideUserProfile = viewModel::requestHideSelectedUserProfile,
                    onTimeoutUserProfile = viewModel::requestTimeoutSelectedUserProfile,
                    onUnbanUserProfile = viewModel::requestUnbanSelectedUserProfile,
                    onWhitelistUserProfile = viewModel::whitelistSelectedUserProfile,
                    onTemporaryWhitelistUserProfile = viewModel::temporaryWhitelistSelectedUserProfile,
                    onDismissUserProfile = viewModel::dismissUserProfile,
                    onStreamChannelIdChange = viewModel::updateStreamChannelId,
                    onUseConnectedYouTubeChannel = viewModel::useConnectedYouTubeChannel,
                    onRefreshStreams = viewModel::refreshStreamSelector,
                    onSelectStream = viewModel::selectStream,
                    onConnectionTestMessageChange = viewModel::updateConnectionTestMessage,
                    onSendConnectionTestMessage = viewModel::sendConnectionTestMessage,
                    onRunModeratorPermissionCheck = viewModel::runModeratorPermissionCheck,
                    onRefreshChannelProfiles = viewModel::refreshChannelProfiles,
                    onSelectChannelProfile = viewModel::selectChannelProfile,
                    onChannelProfileNameChange = viewModel::updateChannelProfileName,
                    onChannelProfileChannelIdChange = viewModel::updateChannelProfileChannelId,
                    onCreateChannelProfile = viewModel::createChannelProfile,
                    onRefreshRulePresets = viewModel::refreshRulePresets,
                    onSaveCurrentRulePreset = viewModel::saveCurrentRulePreset,
                    onExportRulePresets = viewModel::exportRulePresets,
                    onStartRulePresetImport = viewModel::startRulePresetImport,
                    onRulePresetImportTextChange = viewModel::updateRulePresetImportText,
                    onConfirmRulePresetImport = viewModel::confirmRulePresetImport,
                    onDismissRulePresetImport = viewModel::dismissRulePresetImport,
                    onToggleAutoReply = viewModel::toggleAutoReplyForActivePreset,
                    onToggleFirstStreamMinutesOnly = viewModel::toggleFirstStreamMinutesOnlyForActivePreset,
                    onToggleHideUserOnSevereMatch = viewModel::toggleHideUserOnSevereMatchForActivePreset,
                    onSelectRulePreset = viewModel::selectRulePreset,
                    onApplyRuleTemplate = viewModel::applyRulePresetTemplate,
                    onCreateCommand = viewModel::startCreateCommand,
                    onEditCommand = viewModel::startEditCommand,
                    onDeleteCommand = viewModel::deleteCommand,
                    onSendCommand = viewModel::sendCommandNow,
                    onRefreshFaqEntries = viewModel::refreshFaqEntries,
                    onStartCreateFaqEntry = viewModel::startCreateFaqEntry,
                    onStartEditFaqEntry = viewModel::startEditFaqEntry,
                    onFaqQuestionChange = viewModel::updateFaqQuestion,
                    onFaqAnswerChange = viewModel::updateFaqAnswer,
                    onFaqKeywordsChange = viewModel::updateFaqKeywords,
                    onFaqEnabledChange = viewModel::updateFaqEnabled,
                    onSaveFaqEntry = viewModel::saveFaqEntry,
                    onDeleteFaqEntry = viewModel::deleteFaqEntry,
                    onCommandEditorChange = viewModel::updateCommandEditor,
                    onSaveCommand = {
                        if (viewModel.saveCommandEditor()) {
                            analyticsReporter.track("command_saved")
                        }
                    },
                    onDismissCommandEditor = viewModel::dismissCommandEditor,
                    onCreateTimer = viewModel::startCreateTimer,
                    onEditTimer = viewModel::startEditTimer,
                    onDeleteTimer = viewModel::deleteTimer,
                    onPauseAllTimers = viewModel::pauseAllTimers,
                    onResumeAllTimers = viewModel::resumeAllTimers,
                    onTimerEditorChange = viewModel::updateTimerEditor,
                    onSaveTimer = {
                        if (viewModel.saveTimerEditor()) {
                            analyticsReporter.track("timer_saved")
                        }
                    },
                    onDismissTimerEditor = viewModel::dismissTimerEditor,
                    onRefreshBillingProducts = playBillingManager::refreshProducts,
                    onPurchaseProduct = { productId ->
                        analyticsReporter.track("purchase_started", mapOf("productId" to productId))
                        playBillingManager.launchPurchase(this@MainActivity, productId)
                    },
                    onRestorePurchases = {
                        analyticsReporter.track("purchase_restore_started")
                        playBillingManager.restorePurchases()
                    },
                    onSimulateProPurchase = viewModel::simulateProPurchase,
                    onRefreshBackups = viewModel::refreshCloudBackups,
                    onCreateSettingsBackup = viewModel::createSettingsBackup,
                    onExportAccount = viewModel::exportAccountData,
                    onConnectYouTube = viewModel::connectYouTube,
                    onDisconnectYouTube = viewModel::requestDisconnectYouTube,
                    onDeleteAccount = viewModel::requestDeleteAccount,
                    onWipeLocalData = viewModel::requestWipeLocalData,
                    onDeleteBackup = viewModel::requestDeleteBackup,
                    onRestoreBackup = viewModel::requestRestoreBackup,
                    onConfirmAccountAction = viewModel::confirmAccountAction,
                    onDismissAccountAction = viewModel::dismissAccountConfirmation,
                    onConfirmModerationAction = viewModel::confirmModerationAction,
                    onDismissModerationAction = viewModel::dismissModerationConfirmation,
                    onRefreshSupportEvents = viewModel::refreshSupportEvents,
                    onSendSupportDiagnostic = {
                        viewModel.sendSupportDiagnostic()
                        analyticsReporter.track("diagnostic_sent")
                    },
                    onFeedbackCategoryChange = viewModel::updateFeedbackCategory,
                    onFeedbackMessageChange = viewModel::updateFeedbackMessage,
                    onSubmitBetaFeedback = viewModel::submitBetaFeedback,
                    onSelectLogFilter = viewModel::selectLogFilter,
                    onRefreshProAnalytics = viewModel::refreshProAnalytics,
                    onExportLogs = viewModel::exportLogs,
                    onMarkFalsePositive = viewModel::markFalsePositive,
                    onTuneFalsePositive = viewModel::tuneRuleFromFalsePositive,
                    onEmergencyModeChange = viewModel::setEmergencyMode,
                    onLinkLockdownChange = viewModel::setLinkLockdown,
                    onReducedMotionChange = viewModel::setReducedMotion,
                    onHighContrastChange = viewModel::setHighContrast,
                    onLowDataModeChange = viewModel::setLowDataMode,
                    onShareUsageAnalyticsChange = viewModel::setShareUsageAnalytics,
                    onDiscordWebhookUrlChange = viewModel::updateDiscordWebhookUrl,
                    onDiscordEnabledChange = viewModel::setDiscordWebhookEnabled,
                    onDiscordModerationAlertsChange = viewModel::setDiscordAlertModerationActions,
                    onDiscordRuntimeAlertsChange = viewModel::setDiscordAlertRuntimeStatus,
                    onSaveDiscordWebhook = viewModel::saveDiscordWebhook,
                    onTestDiscordWebhook = viewModel::testDiscordWebhook,
                    onDeleteDiscordWebhook = viewModel::deleteDiscordWebhook,
                    onOverlayEnabledChange = viewModel::setOverlayEnabled,
                    onOverlayModerationActionsChange = viewModel::setOverlayShowModerationActions,
                    onOverlayRuntimeStatusChange = viewModel::setOverlayShowRuntimeStatus,
                    onOverlayViewerStatsChange = viewModel::setOverlayShowViewerStats,
                    onOverlayRecentChatChange = viewModel::setOverlayShowRecentChat,
                    onSaveOverlayConfig = viewModel::saveOverlayConfig,
                    onRotateOverlayToken = viewModel::rotateOverlayToken,
                    onRefreshOverlayConfig = viewModel::refreshOverlayConfig,
                    onTeamInviteNameChange = viewModel::updateTeamInviteName,
                    onTeamRedeemCodeChange = viewModel::updateTeamRedeemCode,
                    onCreateTeamInvite = viewModel::createTeamInvite,
                    onRedeemTeamInvite = viewModel::redeemTeamInvite,
                    onRevokeTeamMember = viewModel::revokeTeamMember,
                    onRefreshTeamAccess = viewModel::refreshTeamAccess
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        playBillingManager.start()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            playBillingManager.end()
        }
        super.onDestroy()
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        return runCatching {
            getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(packageName)
        }.getOrDefault(true)
    }
}
