package com.chatmod.mobile.runtime

import com.chatmod.mobile.domain.rules.ActionType
import com.chatmod.mobile.domain.rules.ChatMessage
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.RuleEngine
import com.chatmod.mobile.youtube.LiveChatMessage
import com.chatmod.mobile.youtube.LiveChatMessageType
import com.chatmod.mobile.youtube.YouTubeLiveChatClient
import org.json.JSONObject
import java.time.Instant

class BotCoordinator(
    private val youtube: YouTubeLiveChatClient,
    private val ruleEngine: RuleEngine,
    private val actionRateLimiter: ActionRateLimiter = ActionRateLimiter(),
    private val commandRuntime: CommandRuntime = CommandRuntime(),
    private val timerScheduler: TimerScheduler = TimerScheduler(),
    private val processedMessages: ProcessedMessageCache = ProcessedMessageCache(),
    private val abuseTracker: ChatAbuseTracker = ChatAbuseTracker(),
    private val outboundMessageGuard: OutboundMessageGuard = OutboundMessageGuard(),
    private val logRepository: BotLogSink? = null
) {
    suspend fun processOnce(
        liveChatId: String,
        profile: ModerationProfile,
        pageToken: String? = null,
        sessionId: String = liveChatId,
        streamTitle: String = "",
        streamStartedAtMillis: Long? = null,
        commands: List<BotCommand> = emptyList(),
        commandCooldownState: CommandCooldownState = CommandCooldownState(),
        timers: List<ScheduledTimer> = emptyList(),
        messagesSinceLastTimer: Int = 0,
        timersPaused: Boolean = false,
        rulePresetId: String? = null,
        rulePresetName: String? = null,
        rulePresetVersion: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): BotRunResult {
        val page = youtube.listMessages(liveChatId, pageToken)
        val newMessages = page.messages.filter { liveMessage -> processedMessages.markNew(liveMessage.id) }
        val skippedDuplicates = page.messages.size - newMessages.size
        var actionCount = 0
        var messagesDeleted = 0
        var usersHidden = 0
        var autoRepliesSent = 0
        var commandsSent = 0
        var timersSent = 0
        val commandsUsed = mutableMapOf<String, Int>()
        val timersUsed = mutableMapOf<String, Int>()
        val triggeredRules = mutableMapOf<String, Int>()
        var nextCommandCooldownState = commandCooldownState

        newMessages.forEach { liveMessage ->
            if (liveMessage.shouldLogAsChatMessage()) {
                logRepository?.recordChatMessage(
                    sessionId = sessionId,
                    youtubeMessageId = liveMessage.id,
                    authorChannelId = liveMessage.authorChannelId,
                    authorName = liveMessage.authorName,
                    text = liveMessage.text,
                    receivedAtIso = liveMessage.publishedAtIso
                )
            }

            if (liveMessage.shouldRecordSupplementalEvent()) {
                logRepository?.recordRuntimeEvent(
                    sessionId = sessionId,
                    type = liveMessage.runtimeEventType(),
                    message = liveMessage.runtimeEventMessage(),
                    metadataJson = liveMessage.runtimeMetadataJson()
                )
            }

            if (!liveMessage.shouldEvaluateRules()) {
                if (!liveMessage.shouldRecordSupplementalEvent()) {
                    logRepository?.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = liveMessage.runtimeEventType(),
                        message = liveMessage.runtimeEventMessage(),
                        metadataJson = liveMessage.runtimeMetadataJson()
                    )
                }
                return@forEach
            }

            val decision = ruleEngine.evaluate(
                message = ChatMessage(
                    id = liveMessage.id,
                    authorChannelId = liveMessage.authorChannelId,
                    authorName = liveMessage.authorName,
                    text = liveMessage.text,
                    isOwner = liveMessage.isOwner,
                    isModerator = liveMessage.isModerator,
                    isMember = liveMessage.isMember,
                    isVerified = liveMessage.isVerified,
                    timestampMillis = nowMillis,
                    streamStartedAtMillis = streamStartedAtMillis
                ),
                profile = profile
            )
            val moderationActions = (decision.actions + abuseTracker.evaluate(liveMessage, profile, nowMillis))
                .distinctBy { "${it.type}:${it.reason}" }

            var destructiveActionTaken = false
            var moderationActionMatched = false
            moderationActions.forEach { action ->
                var shouldAudit = false
                var auditReason = action.reason
                val executed = when (action.type) {
                    ActionType.DeleteMessage -> {
                        moderationActionMatched = true
                        if (actionRateLimiter.allow("deleteMessage")) {
                            youtube.deleteMessage(liveMessage.id)
                            shouldAudit = true
                            messagesDeleted += 1
                            true
                        } else {
                            shouldAudit = true
                            auditReason = "rate_limited:${action.reason}"
                            false
                        }
                    }
                    ActionType.HideUser -> {
                        moderationActionMatched = true
                        if (actionRateLimiter.allow("hideUser:${liveMessage.authorChannelId}")) {
                            youtube.hideUser(liveChatId, liveMessage.authorChannelId)
                            shouldAudit = true
                            usersHidden += 1
                            true
                        } else {
                            shouldAudit = true
                            auditReason = "rate_limited:${action.reason}"
                            false
                        }
                    }
                    ActionType.TimeoutUser -> {
                        moderationActionMatched = true
                        if (actionRateLimiter.allow("timeoutUser:${liveMessage.authorChannelId}")) {
                            youtube.hideUser(liveChatId, liveMessage.authorChannelId, DefaultTimeoutSeconds)
                            shouldAudit = true
                            usersHidden += 1
                            true
                        } else {
                            shouldAudit = true
                            auditReason = "rate_limited:${action.reason}"
                            false
                        }
                    }
                    ActionType.FlagForReview -> {
                        moderationActionMatched = true
                        shouldAudit = true
                        false
                    }
                    ActionType.SendAutoReply -> {
                        moderationActionMatched = true
                        val replyValidation = action.text?.let { outboundMessageGuard.validate(it) }
                        val acceptedReply = replyValidation?.acceptedText
                        if (
                            acceptedReply != null &&
                            actionRateLimiter.allowAll(
                                "sendMessage",
                                "sendAutoReply:${action.reason}",
                                "sendAutoReply:${liveMessage.authorChannelId}",
                                nowMillis = nowMillis
                            )
                        ) {
                            youtube.sendMessage(liveChatId, acceptedReply)
                            shouldAudit = true
                            autoRepliesSent += 1
                            logRepository?.recordRuntimeEvent(
                                sessionId = sessionId,
                                type = "auto_reply_sent",
                                message = "Sent moderation auto-reply",
                                metadataJson = runtimeMetadata(
                                    "reason" to action.reason,
                                    "authorChannelId" to liveMessage.authorChannelId,
                                    "authorName" to liveMessage.authorName
                                )
                            )
                            true
                        } else {
                            shouldAudit = true
                            auditReason = replyValidation?.rejectionReason?.let { "blocked_auto_reply:$it" }
                                ?: "rate_limited:${action.reason}"
                            false
                        }
                    }
                    ActionType.Allow -> false
                }

                if (executed) {
                    actionCount += 1
                    destructiveActionTaken = true
                }

                if (shouldAudit) {
                    auditReason.ruleSummaryKey()?.let { ruleKey -> triggeredRules.increment(ruleKey) }
                    logRepository?.recordModerationAction(
                        sessionId = sessionId,
                        youtubeMessageId = liveMessage.id,
                        authorChannelId = liveMessage.authorChannelId,
                        authorName = liveMessage.authorName,
                        messageText = liveMessage.text,
                        actionType = action.type.auditValue(),
                        reason = auditReason,
                        confidence = action.confidence,
                        metadataJson = runtimeMetadata(
                            "rule" to auditReason.ruleSummaryKey(),
                            "rulePresetId" to rulePresetId,
                            "rulePresetName" to rulePresetName,
                            "rulePresetVersion" to rulePresetVersion
                        )
                    )
                }
            }

            if (!destructiveActionTaken && !moderationActionMatched && liveMessage.allowsCommandResponse()) {
                val commandResult = commandRuntime.evaluate(
                    message = liveMessage,
                    commands = commands,
                    cooldownState = nextCommandCooldownState,
                    context = CommandContext(
                        streamTitle = streamTitle,
                        streamStartedAt = streamStartedAtMillis?.let { Instant.ofEpochMilli(it) },
                        now = Instant.ofEpochMilli(nowMillis)
                    )
                )
                val commandResponse = commandResult.response
                val commandValidation = commandResponse?.let { outboundMessageGuard.validate(it) }
                val acceptedCommandResponse = commandValidation?.acceptedText
                if (
                    commandResult.matched &&
                    acceptedCommandResponse != null &&
                    actionRateLimiter.allowAll(
                        "sendMessage",
                        "sendCommand:${commandResult.commandId}",
                        nowMillis = nowMillis
                    )
                ) {
                    youtube.sendMessage(liveChatId, acceptedCommandResponse)
                    commandsSent += 1
                    commandResult.commandId?.let { commandId ->
                        commandsUsed.increment(commandId)
                        nextCommandCooldownState = nextCommandCooldownState.recordUsage(
                            commandId = commandId,
                            authorChannelId = liveMessage.authorChannelId,
                            usedAtMillis = nowMillis
                        )
                    }
                    logRepository?.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = "command_sent",
                        message = "Sent ${commandResult.trigger}",
                        metadataJson = runtimeMetadata(
                            "commandId" to commandResult.commandId,
                            "trigger" to commandResult.trigger,
                            "authorChannelId" to liveMessage.authorChannelId,
                            "authorName" to liveMessage.authorName
                        )
                    )
                } else if (commandResult.matched && commandValidation?.rejectionReason != null) {
                    logRepository?.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = "outbound_message_blocked",
                        message = "Blocked invalid command response: ${commandValidation.rejectionReason}"
                    )
                }
            }
        }

        if (!page.chatEnded && !timersPaused) {
            val dueTimers = timerScheduler.rotatedDueTimers(
                timers = timers,
                messagesSinceLastTimer = messagesSinceLastTimer + newMessages.size,
                nowMillis = nowMillis,
                streamStartedAtMillis = streamStartedAtMillis
            )
            dueTimers.forEach { timer ->
                val timerValidation = outboundMessageGuard.validate(timer.message)
                val acceptedTimerMessage = timerValidation.acceptedText
                if (
                    acceptedTimerMessage != null &&
                    actionRateLimiter.allowAll(
                        "sendMessage",
                        "sendTimer:${timer.id}",
                        nowMillis = nowMillis
                    )
                ) {
                    youtube.sendMessage(liveChatId, acceptedTimerMessage)
                    timersSent += 1
                    timersUsed.increment(timer.id)
                    logRepository?.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = "timer_sent",
                        message = "Sent timer ${timer.id}",
                        metadataJson = runtimeMetadata(
                            "timerId" to timer.id,
                            "intervalMinutes" to timer.intervalMinutes,
                            "minChatMessages" to timer.minChatMessages
                        )
                    )
                } else if (timerValidation.rejectionReason != null) {
                    logRepository?.recordRuntimeEvent(
                        sessionId = sessionId,
                        type = "outbound_message_blocked",
                        message = "Blocked invalid timer message: ${timerValidation.rejectionReason}"
                    )
                }
            }
        }

        return BotRunResult(
            nextPageToken = page.nextPageToken,
            pollingIntervalMillis = page.pollingIntervalMillis,
            messagesProcessed = newMessages.size,
            duplicateMessagesSkipped = skippedDuplicates,
            actionsTaken = actionCount,
            messagesDeleted = messagesDeleted,
            usersHidden = usersHidden,
            autoRepliesSent = autoRepliesSent,
            commandsSent = commandsSent,
            commandsUsed = commandsUsed.toMap(),
            commandCooldownState = nextCommandCooldownState,
            timersSent = timersSent,
            timersUsed = timersUsed.toMap(),
            triggeredRules = triggeredRules.toMap(),
            streamEnded = page.chatEnded
        )
    }
}

private fun LiveChatMessage.shouldLogAsChatMessage(): Boolean {
    return text.isNotBlank() && type in setOf(
        LiveChatMessageType.Text,
        LiveChatMessageType.SuperChat,
        LiveChatMessageType.MemberMilestone
    )
}

private fun LiveChatMessage.shouldEvaluateRules(): Boolean {
    return text.isNotBlank() && type in setOf(
        LiveChatMessageType.Text,
        LiveChatMessageType.SuperChat,
        LiveChatMessageType.MemberMilestone
    )
}

private fun LiveChatMessage.allowsCommandResponse(): Boolean {
    return type == LiveChatMessageType.Text
}

private fun LiveChatMessage.shouldRecordSupplementalEvent(): Boolean {
    return type != LiveChatMessageType.Text
}

private fun LiveChatMessage.runtimeMetadataJson(): String {
    return runtimeMetadata(
        "messageId" to id,
        "messageType" to type.name,
        "authorChannelId" to authorChannelId,
        "authorName" to authorName,
        "isVerified" to isVerified,
        "targetMessageId" to targetMessageId,
        "targetChannelId" to targetChannelId,
        "purchaseAmountMicros" to purchaseAmountMicros,
        "purchaseCurrency" to purchaseCurrency
    )
}

private fun LiveChatMessage.runtimeEventType(): String {
    return when (type) {
        LiveChatMessageType.MessageDeleted -> "live_chat_message_deleted"
        LiveChatMessageType.UserBanned -> "live_chat_user_banned"
        LiveChatMessageType.SuperSticker,
        LiveChatMessageType.SuperChat -> "live_chat_purchase_event"
        LiveChatMessageType.NewMember,
        LiveChatMessageType.MemberMilestone -> "live_chat_member_event"
        LiveChatMessageType.System -> "live_chat_system_event"
        LiveChatMessageType.Text -> "live_chat_empty_text"
    }
}

private fun LiveChatMessage.runtimeEventMessage(): String {
    return when (type) {
        LiveChatMessageType.MessageDeleted -> "YouTube reported a deleted live-chat message"
        LiveChatMessageType.UserBanned -> "YouTube reported a live-chat user ban"
        LiveChatMessageType.SuperSticker -> "YouTube reported a Super Sticker"
        LiveChatMessageType.SuperChat -> "YouTube reported a Super Chat"
        LiveChatMessageType.NewMember -> "YouTube reported a new channel member"
        LiveChatMessageType.MemberMilestone -> "YouTube reported a member milestone"
        LiveChatMessageType.System -> "YouTube reported a system live-chat event"
        LiveChatMessageType.Text -> "YouTube reported an empty text message"
    }
}

private fun ActionType.auditValue(): String {
    return when (this) {
        ActionType.Allow -> "allow"
        ActionType.FlagForReview -> "flagForReview"
        ActionType.DeleteMessage -> "deleteMessage"
        ActionType.TimeoutUser -> "timeoutUser"
        ActionType.HideUser -> "hideUser"
        ActionType.SendAutoReply -> "sendAutoReply"
    }
}

private const val DefaultTimeoutSeconds = 300

private fun MutableMap<String, Int>.increment(key: String) {
    this[key] = (this[key] ?: 0) + 1
}

private fun String.ruleSummaryKey(): String? {
    return removePrefix("rate_limited:")
        .substringBefore(":")
        .takeIf { it.isNotBlank() }
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

data class BotRunResult(
    val nextPageToken: String?,
    val pollingIntervalMillis: Long,
    val messagesProcessed: Int,
    val duplicateMessagesSkipped: Int,
    val actionsTaken: Int,
    val messagesDeleted: Int,
    val usersHidden: Int,
    val autoRepliesSent: Int = 0,
    val commandsSent: Int,
    val commandsUsed: Map<String, Int>,
    val commandCooldownState: CommandCooldownState,
    val timersSent: Int,
    val timersUsed: Map<String, Int>,
    val triggeredRules: Map<String, Int> = emptyMap(),
    val streamEnded: Boolean
)

class ProcessedMessageCache(
    private val maxEntries: Int = 5_000
) {
    init {
        require(maxEntries > 0) { "maxEntries must be greater than zero" }
    }

    private val seen = LinkedHashSet<String>()

    fun markNew(messageId: String): Boolean {
        if (seen.contains(messageId)) {
            return false
        }

        seen += messageId
        trim()
        return true
    }

    private fun trim() {
        while (seen.size > maxEntries) {
            val iterator = seen.iterator()
            iterator.next()
            iterator.remove()
        }
    }
}

class ActionRateLimiter(
    private val minimumIntervalMillis: Long = 750
) {
    private val lastActionAt = mutableMapOf<String, Long>()

    fun allow(key: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val previous = lastActionAt[key]
        if (previous != null && nowMillis - previous < minimumIntervalMillis) {
            return false
        }

        lastActionAt[key] = nowMillis
        return true
    }

    fun allowAll(vararg keys: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val blocked = keys.any { key ->
            val previous = lastActionAt[key]
            previous != null && nowMillis - previous < minimumIntervalMillis
        }
        if (blocked) {
            return false
        }

        keys.forEach { key ->
            lastActionAt[key] = nowMillis
        }
        return true
    }
}
