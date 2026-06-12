package com.chatmod.mobile.runtime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.chatmod.mobile.BuildConfig
import com.chatmod.mobile.ChatModApplication
import com.chatmod.mobile.R
import com.chatmod.mobile.data.local.ActiveRulePresetState
import com.chatmod.mobile.data.local.ActiveBotRuntimeState
import com.chatmod.mobile.data.local.entity.CommandEntity
import com.chatmod.mobile.data.local.entity.TimerEntity
import com.chatmod.mobile.data.remote.DiscordAlertRequest
import com.chatmod.mobile.domain.rules.LinkPolicy
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.RuleEngine
import com.chatmod.mobile.youtube.BackendYouTubeLiveChatClient
import com.chatmod.mobile.youtube.MockYouTubeLiveChatClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

class BotForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager: ConnectivityManager by lazy {
        getSystemService(ConnectivityManager::class.java)
    }
    private val wakeLock: PowerManager.WakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WakeLockTag)
            .apply { setReferenceCounted(false) }
    }
    @Volatile
    private var networkAvailable = true
    @Volatile
    private var emergencyMode = false
    @Volatile
    private var linkLockdown = false
    @Volatile
    private var lowDataMode = false
    @Volatile
    private var activeRulePreset: ActiveRulePresetState? = null
    @Volatile
    private var activeProfileId: String = ChatModApplication.DefaultProfileId
    @Volatile
    private var runtimeStatusOverride: String? = null
    private var botJob: Job? = null
    private var activeSessionId: String? = null
    private var activeSessionStats: BotSessionStats? = null
    private var explicitStopRequested = false
    private var networkCallbackRegistered = false
    private val failurePolicy = BotRuntimeFailurePolicy()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkAvailable = true
            recordServiceEvent(
                type = "network_available",
                message = "Network connection available"
            )
            updateRuntimeNotification()
        }

        override fun onLost(network: Network) {
            networkAvailable = isNetworkAvailable()
            if (!networkAvailable) {
                recordServiceEvent(
                    type = "network_lost",
                    message = "Network connection lost"
                )
            }
            updateRuntimeNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ActionStop -> {
                explicitStopRequested = true
                stopBotLoop(clearRuntimeState = true, finishSession = true)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ActionEnableEmergency,
            ActionDisableEmergency,
            ActionEnableLinkLockdown,
            ActionDisableLinkLockdown -> {
                handleLiveControlAction(intent.action ?: return START_STICKY)
                return START_STICKY
            }
        }

        explicitStopRequested = false
        startForeground(NotificationIds.BotService, buildNotification())
        startBotLoop(intent)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopBotLoop(
            clearRuntimeState = explicitStopRequested,
            finishSession = explicitStopRequested
        )
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            NotificationChannels.BotRuntime,
            "ChatMod bot runtime",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the YouTube Live moderation bot running."
        }

        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, NotificationChannels.BotRuntime)
            .setSmallIcon(R.drawable.ic_chatmod_mark)
            .setContentTitle("ChatMod Mobile is running")
            .setContentText(notificationStatusText())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_chatmod_mark,
                "Stop",
                servicePendingIntent(ActionStop, NotificationRequestCodes.StopBot)
            )
            .addAction(
                R.drawable.ic_chatmod_mark,
                if (emergencyMode) "Emergency Off" else "Emergency On",
                servicePendingIntent(
                    if (emergencyMode) ActionDisableEmergency else ActionEnableEmergency,
                    NotificationRequestCodes.ToggleEmergency
                )
            )
            .addAction(
                R.drawable.ic_chatmod_mark,
                if (linkLockdown) "Unlock Links" else "Lock Links",
                servicePendingIntent(
                    if (linkLockdown) ActionDisableLinkLockdown else ActionEnableLinkLockdown,
                    NotificationRequestCodes.ToggleLinkLockdown
                )
            )
            .build()
    }

    private fun startBotLoop(intent: Intent?) {
        if (botJob?.isActive == true) {
            return
        }

        acquireWakeLock()
        botJob = serviceScope.launch {
            val app = application as ChatModApplication
            runCatching { app.settingsStore.currentSettings() }.getOrNull()?.let { settings ->
                activeProfileId = settings.selectedProfileId ?: ChatModApplication.DefaultProfileId
                emergencyMode = settings.emergencyMode
                linkLockdown = settings.linkLockdown
                lowDataMode = settings.lowDataMode
                updateRuntimeNotification()
            }
            activeRulePreset = runCatching { app.settingsStore.currentActiveRulePreset() }.getOrNull()
            val settingsJob = launch {
                runCatching {
                    app.settingsStore.settings.collect { settings ->
                        val shouldRefreshNotification = emergencyMode != settings.emergencyMode ||
                            linkLockdown != settings.linkLockdown
                        activeProfileId = settings.selectedProfileId ?: ChatModApplication.DefaultProfileId
                        emergencyMode = settings.emergencyMode
                        linkLockdown = settings.linkLockdown
                        lowDataMode = settings.lowDataMode
                        if (shouldRefreshNotification) {
                            updateRuntimeNotification()
                        }
                    }
                }
            }
            val presetJob = launch {
                runCatching {
                    app.settingsStore.activeRulePreset.collect { preset ->
                        activeRulePreset = preset
                    }
                }
            }
            val runtimeConfig = resolveRuntimeConfig(app, intent)
            val liveChatId = runtimeConfig.state.liveChatId
            val videoId = runtimeConfig.state.videoId
            val streamTitle = runtimeConfig.state.streamTitle
            val sessionId = runtimeConfig.state.sessionId
            activeSessionId = sessionId
            activeSessionStats = BotSessionStats(runtimeConfig.state.startedAtMillis)
            app.settingsStore.setActiveRuntime(runtimeConfig.state)

            app.cloudSyncQueue.enqueueStreamSessionUpsert(
                sessionId = sessionId,
                profileId = activeProfileId,
                videoId = videoId,
                liveChatId = liveChatId,
                title = streamTitle
            )
            app.botLogRepository.recordRuntimeEvent(
                sessionId = sessionId,
                type = "runtime_started",
                message = "Bot runtime started"
            )
            if (runtimeConfig.restored) {
                app.botLogRepository.recordRuntimeEvent(
                    sessionId = sessionId,
                    type = "runtime_recovered_after_restart",
                    message = "Recovered bot runtime after service restart",
                    metadataJson = runtimeMetadata(
                        "liveChatId" to liveChatId,
                        "startedAtMillis" to runtimeConfig.state.startedAtMillis
                    )
                )
            }
            if (!isIgnoringBatteryOptimizations()) {
                app.botLogRepository.recordRuntimeEvent(
                    sessionId = sessionId,
                    type = "battery_optimization_active",
                    message = "Android battery optimization may pause the bot runtime"
                )
            }

            val coordinator = BotCoordinator(
                youtube = if (BuildConfig.CHATMOD_USE_DEMO_API) {
                    MockYouTubeLiveChatClient()
                } else {
                    BackendYouTubeLiveChatClient(
                        api = app.apiClient,
                        accessTokenProvider = { app.sessionManager.currentAccessToken() },
                        refreshAccessTokenProvider = { app.sessionManager.refreshedAccessToken() }
                    )
                },
                ruleEngine = RuleEngine(),
                logRepository = app.botLogRepository
            )
            var pageToken: String? = null
            var commandCooldownState = CommandCooldownState()
            var consecutiveFailures = 0
            var lastHeartbeatAt = 0L
            var messagesSinceLastTimer = 0
            var waitingForNetworkLogged = false

            while (isActive) {
                if (!networkAvailable) {
                    if (!waitingForNetworkLogged) {
                        waitingForNetworkLogged = true
                        app.botLogRepository.recordRuntimeEvent(
                            sessionId = sessionId,
                            type = "runtime_waiting_for_network",
                            message = "Waiting for network connection",
                            metadataJson = runtimeMetadata("liveChatId" to liveChatId)
                        )
                    }
                    delay(NetworkWaitRetryMillis)
                    continue
                }
                waitingForNetworkLogged = false

                val nowMillis = System.currentTimeMillis()
                if (nowMillis - lastHeartbeatAt >= HeartbeatIntervalMillis) {
                    lastHeartbeatAt = nowMillis
                    acquireWakeLock()
                    app.botLogRepository.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = "runtime_heartbeat",
                        message = "Bot runtime heartbeat",
                        metadataJson = runtimeMetadata(
                            "consecutiveFailures" to consecutiveFailures,
                            "emergencyMode" to emergencyMode,
                            "linkLockdown" to linkLockdown,
                            "lowDataMode" to lowDataMode,
                            "liveChatId" to liveChatId
                        )
                    )
                }

                val loopProfileId = activeProfileId
                val commands = app.commandDao
                    .enabledForProfile(loopProfileId)
                    .map { it.toBotCommand() }
                val timers = app.timerDao
                    .enabledForProfile(loopProfileId)
                    .map { it.toScheduledTimer() }

                val result = runCatching {
                    val preset = activeRulePreset
                    coordinator.processOnce(
                        liveChatId = liveChatId,
                        profile = runtimeModerationProfile(preset?.config),
                        pageToken = pageToken,
                        sessionId = sessionId,
                        streamTitle = streamTitle,
                        streamStartedAtMillis = runtimeConfig.state.startedAtMillis,
                        commands = commands,
                        commandCooldownState = commandCooldownState,
                        timers = timers,
                        messagesSinceLastTimer = messagesSinceLastTimer,
                        timersPaused = emergencyMode,
                        rulePresetId = preset?.id ?: DefaultRuntimePresetId,
                        rulePresetName = preset?.name ?: DefaultRuntimePresetName,
                        rulePresetVersion = preset?.updatedAtMillis?.toString() ?: DefaultRuntimePresetVersion,
                        nowMillis = nowMillis
                    )
                }

                val runResult = result.getOrNull()
                if (runResult != null) {
                    setRuntimeNotificationStatus(null)
                    consecutiveFailures = 0
                    activeSessionStats?.record(runResult)
                    if (runResult.actionsTaken > 0) {
                        launch {
                            sendDiscordModerationAlert(
                                app = app,
                                profileId = loopProfileId,
                                streamTitle = streamTitle,
                                runResult = runResult
                            )
                        }
                    }
                    if (runResult.streamEnded) {
                        settingsJob.cancel()
                        presetJob.cancel()
                        handleStreamEnded(sessionId)
                        return@launch
                    }
                    pageToken = runResult.nextPageToken
                    commandCooldownState = runResult.commandCooldownState
                    messagesSinceLastTimer += runResult.messagesProcessed
                    if (runResult.timersSent > 0) {
                        messagesSinceLastTimer = 0
                        runResult.timersUsed.keys.forEach { timerId ->
                            app.timerDao.markSent(timerId, nowMillis)
                        }
                    }
                    delay(pollingDelayMillis(runResult.pollingIntervalMillis))
                } else {
                    val error = result.exceptionOrNull() ?: IllegalStateException("Bot runtime failed")
                    consecutiveFailures += 1
                    val decision = failurePolicy.decide(error, consecutiveFailures)
                    app.botLogRepository.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = decision.eventType,
                        message = decision.message,
                        metadataJson = runtimeMetadata(
                            "consecutiveFailures" to consecutiveFailures,
                            "retryInMillis" to decision.retryDelayMillis,
                            "retryAfterMillis" to decision.retryAfterMillis,
                            "errorType" to error.javaClass.simpleName,
                            "liveChatId" to liveChatId
                        )
                    )
                    if (decision.terminal) {
                        settingsJob.cancel()
                        presetJob.cancel()
                        handleTerminalRuntimeFailure(sessionId)
                        return@launch
                    }
                    setRuntimeNotificationStatus(decision.notificationRetryText())
                    delay(decision.retryDelayMillis ?: NetworkWaitRetryMillis)
                }
            }
            settingsJob.cancel()
            presetJob.cancel()
        }
    }

    private fun handleLiveControlAction(action: String) {
        val app = application as? ChatModApplication ?: return
        when (action) {
            ActionEnableEmergency -> {
                emergencyMode = true
                persistLiveControlChange(app, emergencyMode = true)
                recordServiceEvent("emergency_mode_enabled", "Emergency mode enabled from notification")
            }
            ActionDisableEmergency -> {
                emergencyMode = false
                persistLiveControlChange(app, emergencyMode = false)
                recordServiceEvent("emergency_mode_disabled", "Emergency mode disabled from notification")
            }
            ActionEnableLinkLockdown -> {
                linkLockdown = true
                persistLiveControlChange(app, linkLockdown = true)
                recordServiceEvent("link_lockdown_enabled", "Link lockdown enabled from notification")
            }
            ActionDisableLinkLockdown -> {
                linkLockdown = false
                persistLiveControlChange(app, linkLockdown = false)
                recordServiceEvent("link_lockdown_disabled", "Link lockdown disabled from notification")
            }
        }
        updateRuntimeNotification()
    }

    private fun persistLiveControlChange(
        app: ChatModApplication,
        emergencyMode: Boolean? = null,
        linkLockdown: Boolean? = null
    ) {
        serviceScope.launch {
            emergencyMode?.let { app.settingsStore.setEmergencyMode(it) }
            linkLockdown?.let { app.settingsStore.setLinkLockdown(it) }
        }
    }

    private fun updateRuntimeNotification() {
        if (botJob?.isActive != true) {
            return
        }

        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NotificationIds.BotService, buildNotification())
        }
    }

    private fun setRuntimeNotificationStatus(status: String?) {
        if (runtimeStatusOverride == status) {
            return
        }

        runtimeStatusOverride = status
        updateRuntimeNotification()
    }

    private fun notificationStatusText(): String {
        if (!networkAvailable) {
            return "Waiting for network connection."
        }
        runtimeStatusOverride?.let { status -> return status }
        if (emergencyMode && linkLockdown) {
            return "Emergency mode and link lockdown are active."
        }
        if (emergencyMode) {
            return "Emergency mode active; timers are paused."
        }
        if (linkLockdown) {
            return "Link lockdown active; chat links are deleted."
        }

        return "Monitoring YouTube Live chat from this phone."
    }

    private fun runtimeModerationProfile(activePreset: ModerationProfile?): ModerationProfile {
        val baseProfile = activePreset ?: ModerationProfile(
            blockedTerms = listOf("scam", "cheap views"),
            maxSymbolCount = if (emergencyMode) 10 else 16
        )

        return baseProfile.copy(
            linkPolicy = if (emergencyMode || linkLockdown) LinkPolicy.Delete else baseProfile.linkPolicy,
            capsThreshold = if (emergencyMode) minOf(baseProfile.capsThreshold, 0.60) else baseProfile.capsThreshold,
            maxRepeatedCharacters = if (emergencyMode) minOf(baseProfile.maxRepeatedCharacters, 4) else baseProfile.maxRepeatedCharacters,
            maxEmojiCount = if (emergencyMode) minOf(baseProfile.maxEmojiCount, 5) else baseProfile.maxEmojiCount,
            maxMentions = if (emergencyMode) minOf(baseProfile.maxMentions, 3) else baseProfile.maxMentions,
            maxSymbolCount = if (emergencyMode) minOf(baseProfile.maxSymbolCount, 10) else baseProfile.maxSymbolCount,
            raidMode = emergencyMode || baseProfile.raidMode,
            newChatterBurstThreshold = if (emergencyMode) {
                if (baseProfile.newChatterBurstThreshold <= 1) 3 else minOf(baseProfile.newChatterBurstThreshold, 3)
            } else {
                baseProfile.newChatterBurstThreshold
            },
            newChatterBurstWindowSeconds = if (emergencyMode) {
                minOf(baseProfile.newChatterBurstWindowSeconds.coerceAtLeast(5), 20)
            } else {
                baseProfile.newChatterBurstWindowSeconds
            }
        )
    }

    private suspend fun sendDiscordModerationAlert(
        app: ChatModApplication,
        profileId: String,
        streamTitle: String,
        runResult: BotRunResult
    ) {
        val accessToken = app.sessionManager.currentAccessToken()
            ?: app.sessionManager.refreshedAccessToken()
            ?: return
        val hiddenOrTimedOut = runResult.usersHidden
        val deleted = runResult.messagesDeleted
        val autoReplies = runResult.autoRepliesSent

        runCatching {
            app.apiClient.sendDiscordAlert(
                accessToken = accessToken,
                request = DiscordAlertRequest(
                    profileId = profileId,
                    eventType = "moderation_action",
                    title = "Moderation action",
                    detail = discordModerationAlertDetail(
                        streamTitle = streamTitle,
                        actionsTaken = runResult.actionsTaken,
                        deleted = deleted,
                        hiddenOrTimedOut = hiddenOrTimedOut,
                        autoReplies = autoReplies
                    ),
                    severity = if (hiddenOrTimedOut > 0) "warning" else "info",
                    metadata = mapOf(
                        "actionsTaken" to runResult.actionsTaken,
                        "messagesDeleted" to deleted,
                        "usersHidden" to hiddenOrTimedOut,
                        "autoRepliesSent" to autoReplies,
                        "messagesProcessed" to runResult.messagesProcessed,
                        "streamTitle" to streamTitle.take(120)
                    )
                )
            )
        }
    }

    private fun discordModerationAlertDetail(
        streamTitle: String,
        actionsTaken: Int,
        deleted: Int,
        hiddenOrTimedOut: Int,
        autoReplies: Int
    ): String {
        val title = streamTitle.ifBlank { "active stream" }
        return "$actionsTaken action(s) on $title: $deleted deleted, $hiddenOrTimedOut hidden/timed out, $autoReplies auto-replies."
    }

    private fun stopBotLoop(clearRuntimeState: Boolean, finishSession: Boolean) {
        val sessionId = activeSessionId
        val sessionStats = activeSessionStats
        botJob?.cancel()
        botJob = null
        activeSessionId = null
        activeSessionStats = null
        releaseWakeLock()
        if (clearRuntimeState) {
            clearPersistedRuntimeState()
        }
        if (finishSession) {
            finishCloudSession(sessionId, sessionStats)
        }
    }

    private suspend fun resolveRuntimeConfig(app: ChatModApplication, intent: Intent?): BotRuntimeConfig {
        if (intent?.action == ActionStart) {
            return BotRuntimeConfig(
                state = intent.toRuntimeState(startedAtMillis = System.currentTimeMillis()),
                restored = false
            )
        }

        val restoredState = runCatching { app.settingsStore.activeRuntime.first() }.getOrNull()
        if (restoredState != null) {
            return BotRuntimeConfig(state = restoredState, restored = true)
        }

        return BotRuntimeConfig(
            state = ActiveBotRuntimeState(
                sessionId = DemoLiveChatId,
                liveChatId = DemoLiveChatId,
                videoId = DemoVideoId,
                streamTitle = DemoStreamTitle,
                startedAtMillis = System.currentTimeMillis()
            ),
            restored = false
        )
    }

    private fun Intent.toRuntimeState(startedAtMillis: Long): ActiveBotRuntimeState {
        val liveChatId = nonBlankExtra(ExtraLiveChatId) ?: DemoLiveChatId
        return ActiveBotRuntimeState(
            sessionId = nonBlankExtra(ExtraSessionId) ?: liveChatId,
            liveChatId = liveChatId,
            videoId = nonBlankExtra(ExtraVideoId) ?: DemoVideoId,
            streamTitle = nonBlankExtra(ExtraStreamTitle) ?: DemoStreamTitle,
            startedAtMillis = startedAtMillis
        )
    }

    private fun Intent.nonBlankExtra(key: String): String? {
        return getStringExtra(key)?.takeIf { it.isNotBlank() }
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, BotForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun acquireWakeLock() {
        runCatching {
            wakeLock.acquire(WakeLockTimeoutMillis)
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return runCatching {
            getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(packageName)
        }.getOrDefault(true)
    }

    private fun registerNetworkCallback() {
        networkAvailable = isNetworkAvailable()
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) {
            return
        }

        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun recordServiceEvent(type: String, message: String) {
        val sessionId = activeSessionId ?: return
        val app = application as? ChatModApplication ?: return
        serviceScope.launch {
            app.botLogRepository.recordRuntimeEvent(
                sessionId = sessionId,
                type = type,
                message = message
            )
        }
    }

    private fun clearPersistedRuntimeState() {
        val app = application as? ChatModApplication ?: return
        app.applicationScope.launch {
            app.settingsStore.clearActiveRuntime()
        }
    }

    private fun finishCloudSession(sessionId: String?, stats: BotSessionStats?) {
        if (sessionId == null) {
            return
        }

        val app = application as? ChatModApplication ?: return
        app.applicationScope.launch {
            app.botLogRepository.recordRuntimeEvent(
                sessionId = sessionId,
                type = "runtime_stopped",
                message = "Bot runtime stopped"
            )
            recordSessionSummary(app, sessionId, stats)
            app.cloudSyncQueue.enqueueStreamSessionEnd(sessionId = sessionId)
        }
    }

    private fun handleStreamEnded(sessionId: String) {
        val sessionStats = activeSessionStats
        activeSessionId = null
        activeSessionStats = null
        releaseWakeLock()
        val app = application as? ChatModApplication
        app?.applicationScope?.launch {
            recordSessionSummary(app, sessionId, sessionStats)
            app.botLogRepository.recordRuntimeEvent(
                sessionId = sessionId,
                type = "stream_ended",
                message = "Live chat ended; bot runtime stopped"
            )
            app.settingsStore.clearActiveRuntime()
            app.cloudSyncQueue.enqueueStreamSessionEnd(sessionId = sessionId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleTerminalRuntimeFailure(sessionId: String) {
        val sessionStats = activeSessionStats
        activeSessionId = null
        activeSessionStats = null
        releaseWakeLock()
        val app = application as? ChatModApplication
        app?.applicationScope?.launch {
            recordSessionSummary(app, sessionId, sessionStats)
            app.settingsStore.clearActiveRuntime()
            app.cloudSyncQueue.enqueueStreamSessionEnd(sessionId = sessionId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun recordSessionSummary(
        app: ChatModApplication,
        sessionId: String,
        stats: BotSessionStats?
    ) {
        if (stats == null) {
            return
        }

        app.botLogRepository.recordRuntimeEvent(
            sessionId = sessionId,
            type = "runtime_session_summary",
            message = "Bot session summary",
            metadataJson = runtimeMetadata(
                *stats.metadataPairs(System.currentTimeMillis()).toTypedArray()
            )
        )
    }

    private fun pollingDelayMillis(youtubeDelayMillis: Long): Long {
        val baseDelay = youtubeDelayMillis.coerceAtLeast(MinPollingDelayMillis)
        if (!lowDataMode) {
            return baseDelay
        }

        return (baseDelay * LowDataPollingMultiplier).coerceAtMost(MaxLowDataPollingDelayMillis)
    }

    private fun runtimeMetadata(vararg values: Pair<String, Any?>): String {
        val metadata = JSONObject()
        values.forEach { (key, value) ->
            if (value != null) {
                metadata.put(key, value)
            }
        }
        return metadata.toString()
    }

    private fun CommandEntity.toBotCommand(): BotCommand {
        return BotCommand(
            id = id,
            name = name,
            response = response,
            aliases = aliasesJson.lines().filter { it.isNotBlank() },
            cooldownSeconds = cooldownSeconds,
            accessLevel = runCatching { CommandAccessLevel.valueOf(accessLevel) }
                .getOrDefault(CommandAccessLevel.Everyone),
            enabled = enabled
        )
    }

    private fun TimerEntity.toScheduledTimer(): ScheduledTimer {
        return ScheduledTimer(
            id = id,
            message = message,
            intervalMinutes = intervalMinutes,
            minChatMessages = minChatMessages,
            quietStartMinutes = quietStartMinutes,
            quietEndMinutes = quietEndMinutes,
            enabled = enabled,
            lastSentAtMillis = lastSentAt
        )
    }

    companion object {
        const val ActionStart = "com.chatmod.mobile.action.START_BOT"
        const val ActionStop = "com.chatmod.mobile.action.STOP_BOT"
        const val ActionEnableEmergency = "com.chatmod.mobile.action.ENABLE_EMERGENCY"
        const val ActionDisableEmergency = "com.chatmod.mobile.action.DISABLE_EMERGENCY"
        const val ActionEnableLinkLockdown = "com.chatmod.mobile.action.ENABLE_LINK_LOCKDOWN"
        const val ActionDisableLinkLockdown = "com.chatmod.mobile.action.DISABLE_LINK_LOCKDOWN"

        const val ExtraSessionId = "com.chatmod.mobile.extra.SESSION_ID"
        const val ExtraLiveChatId = "com.chatmod.mobile.extra.LIVE_CHAT_ID"
        const val ExtraVideoId = "com.chatmod.mobile.extra.VIDEO_ID"
        const val ExtraStreamTitle = "com.chatmod.mobile.extra.STREAM_TITLE"

        private const val HeartbeatIntervalMillis = 60_000L
        private const val WakeLockTimeoutMillis = 150_000L
        private const val WakeLockTag = "ChatModMobile:BotRuntime"
        private const val NetworkWaitRetryMillis = 5_000L
        private const val MinPollingDelayMillis = 1_000L
        private const val LowDataPollingMultiplier = 2
        private const val MaxLowDataPollingDelayMillis = 30_000L
        private const val DemoLiveChatId = "demo-live-chat"
        private const val DemoVideoId = "demo-video"
        private const val DemoStreamTitle = "Demo stream"
        private const val DefaultRuntimePresetId = "runtime-default"
        private const val DefaultRuntimePresetName = "Runtime default"
        private const val DefaultRuntimePresetVersion = "built-in"
    }
}

private data class BotRuntimeConfig(
    val state: ActiveBotRuntimeState,
    val restored: Boolean
)

private fun BotRuntimeFailureDecision.notificationRetryText(): String {
    return when (eventType) {
        "runtime_youtube_backoff_scheduled" -> "YouTube asked us to slow down; retrying soon."
        "runtime_reconnect_scheduled" -> "Reconnecting to YouTube Live chat."
        else -> "Recovering bot runtime."
    }
}

private object NotificationRequestCodes {
    const val StopBot = 2001
    const val ToggleEmergency = 2002
    const val ToggleLinkLockdown = 2003
}

object NotificationChannels {
    const val BotRuntime = "chatmod_bot_runtime"
}

object NotificationIds {
    const val BotService = 1001
}
