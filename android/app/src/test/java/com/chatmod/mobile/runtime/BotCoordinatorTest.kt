package com.chatmod.mobile.runtime

import com.chatmod.mobile.domain.rules.LinkPolicy
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.RuleEngine
import com.chatmod.mobile.youtube.ActiveLiveChat
import com.chatmod.mobile.youtube.LiveChatMessage
import com.chatmod.mobile.youtube.LiveChatMessageType
import com.chatmod.mobile.youtube.LiveChatPage
import com.chatmod.mobile.youtube.YouTubeLiveChatClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotCoordinatorTest {
    @Test
    fun recordsMessagesAndExecutedModerationActions() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                blockedTerms = listOf("scam"),
                linkPolicy = LinkPolicy.Allow
            )
        )

        assertEquals(1, result.messagesProcessed)
        assertEquals(1, result.actionsTaken)
        assertEquals(1, result.messagesDeleted)
        assertEquals(mapOf("blocked_term" to 1), result.triggeredRules)
        assertEquals(listOf("message-1"), youtube.deletedMessageIds)
        assertEquals(listOf("message-1"), logs.messages.map { it.youtubeMessageId })
        assertEquals("deleteMessage", logs.actions.single().actionType)
        assertEquals("blocked_term:scam", logs.actions.single().reason)
    }

    @Test
    fun executesRuleDrivenHideUserActions() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                blockedTerms = listOf("scam"),
                linkPolicy = LinkPolicy.Allow,
                hideUserOnSevereMatch = true
            )
        )

        assertEquals(1, result.messagesDeleted)
        assertEquals(1, result.usersHidden)
        assertEquals(2, result.actionsTaken)
        assertEquals(listOf("message-1"), youtube.deletedMessageIds)
        assertEquals(listOf("viewer-1"), youtube.hiddenUserIds)
        assertTrue(logs.actions.any { it.actionType == "deleteMessage" && it.reason == "blocked_term:scam" })
        assertTrue(logs.actions.any { it.actionType == "hideUser" && it.reason == "severe_match_hide_user" })
    }

    @Test
    fun reportsStreamEndedFromLiveChatPage() = runBlocking {
        val coordinator = BotCoordinator(
            youtube = FakeYouTubeLiveChatClient(
                LiveChatPage(
                    messages = emptyList(),
                    nextPageToken = null,
                    pollingIntervalMillis = 5000,
                    chatEnded = true
                )
            ),
            ruleEngine = RuleEngine()
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile()
        )

        assertEquals(true, result.streamEnded)
        assertEquals(0, result.messagesProcessed)
    }

    @Test
    fun sendsConfiguredAutoReplyForMatchedModerationRule() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                blockedTerms = listOf("scam"),
                linkPolicy = LinkPolicy.Allow,
                autoReplyEnabled = true,
                autoReplyMessage = "Please keep chat clean."
            )
        )

        assertEquals(1, result.autoRepliesSent)
        assertEquals(listOf("Please keep chat clean."), youtube.sentMessages)
        assertTrue(logs.actions.any { it.actionType == "sendAutoReply" && it.reason == "auto_reply" })
        assertTrue(logs.events.any { it.type == "auto_reply_sent" })
    }

    @Test
    fun blocksInvalidAutoReplyTextBeforeSending() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine()
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                blockedTerms = listOf("scam"),
                linkPolicy = LinkPolicy.Allow,
                autoReplyEnabled = true,
                autoReplyMessage = "x".repeat(201)
            )
        )

        assertEquals(0, result.autoRepliesSent)
        assertEquals(emptyList<String>(), youtube.sentMessages)
    }

    @Test
    fun appliesModerationOnlyDuringConfiguredFirstStreamMinutes() = runBlocking {
        val insideWindowClient = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "inside-window-message",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:05:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val outsideWindowClient = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "outside-window-message",
                        authorChannelId = "viewer-2",
                        authorName = "ViewerTwo",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:11:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val profile = ModerationProfile(
            blockedTerms = listOf("scam"),
            linkPolicy = LinkPolicy.Allow,
            firstStreamMinutesOnly = 10
        )

        val insideResult = BotCoordinator(
            youtube = insideWindowClient,
            ruleEngine = RuleEngine()
        ).processOnce(
            liveChatId = "live-chat-1",
            profile = profile,
            streamStartedAtMillis = 0L,
            nowMillis = 5 * 60_000L
        )
        val outsideResult = BotCoordinator(
            youtube = outsideWindowClient,
            ruleEngine = RuleEngine()
        ).processOnce(
            liveChatId = "live-chat-1",
            profile = profile,
            streamStartedAtMillis = 0L,
            nowMillis = 11 * 60_000L
        )

        assertEquals(1, insideResult.messagesDeleted)
        assertEquals(listOf("inside-window-message"), insideWindowClient.deletedMessageIds)
        assertEquals(0, outsideResult.messagesDeleted)
        assertEquals(emptyList<String>(), outsideWindowClient.deletedMessageIds)
    }

    @Test
    fun skipsDuplicateMessagesAcrossPolls() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )
        val profile = ModerationProfile(
            blockedTerms = listOf("scam"),
            linkPolicy = LinkPolicy.Allow
        )

        val first = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = profile
        )
        val second = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = profile,
            pageToken = first.nextPageToken
        )

        assertEquals(1, first.messagesProcessed)
        assertEquals(0, first.duplicateMessagesSkipped)
        assertEquals(0, second.messagesProcessed)
        assertEquals(1, second.duplicateMessagesSkipped)
        assertEquals(listOf("message-1"), youtube.deletedMessageIds)
        assertEquals(listOf("message-1"), logs.messages.map { it.youtubeMessageId })
        assertEquals(1, logs.actions.size)
    }

    @Test
    fun auditsFlagForReviewModerationDecisions() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "check www.example.com",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(linkPolicy = LinkPolicy.Flag)
        )

        assertEquals(1, result.messagesProcessed)
        assertEquals(0, result.actionsTaken)
        assertEquals(emptyList<String>(), youtube.deletedMessageIds)
        assertEquals("flagForReview", logs.actions.single().actionType)
        assertEquals("link_policy", logs.actions.single().reason)
    }

    @Test
    fun auditsSuspiciousNewUserBurstsWithoutDeletingMessages() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "hello",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    ),
                    LiveChatMessage(
                        id = "message-2",
                        authorChannelId = "viewer-2",
                        authorName = "ViewerTwo",
                        text = "hi",
                        publishedAtIso = "2026-06-07T10:00:01Z"
                    ),
                    LiveChatMessage(
                        id = "message-3",
                        authorChannelId = "viewer-3",
                        authorName = "ViewerThree",
                        text = "hey",
                        publishedAtIso = "2026-06-07T10:00:02Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                linkPolicy = LinkPolicy.Allow,
                newChatterBurstThreshold = 3,
                newChatterBurstWindowSeconds = 30
            ),
            nowMillis = 1_000L
        )

        assertEquals(3, result.messagesProcessed)
        assertEquals(0, result.actionsTaken)
        assertEquals(emptyList<String>(), youtube.deletedMessageIds)
        assertEquals(mapOf("suspicious_new_user_burst" to 1), result.triggeredRules)
        assertEquals("flagForReview", logs.actions.single().actionType)
        assertEquals("suspicious_new_user_burst", logs.actions.single().reason)
    }

    @Test
    fun auditsRateLimitedModerationAttempts() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "this is a scam",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    ),
                    LiveChatMessage(
                        id = "message-2",
                        authorChannelId = "viewer-2",
                        authorName = "ViewerTwo",
                        text = "another scam",
                        publishedAtIso = "2026-06-07T10:00:01Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 10_000L),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                blockedTerms = listOf("scam"),
                linkPolicy = LinkPolicy.Allow
            ),
            nowMillis = 1_000L
        )

        assertEquals(1, result.actionsTaken)
        assertEquals(1, result.messagesDeleted)
        assertEquals(mapOf("blocked_term" to 2), result.triggeredRules)
        assertEquals(listOf("message-1"), youtube.deletedMessageIds)
        assertEquals(listOf("blocked_term:scam", "rate_limited:blocked_term:scam"), logs.actions.map { it.reason })
        assertEquals(listOf("deleteMessage", "deleteMessage"), logs.actions.map { it.actionType })
    }

    @Test
    fun executesRaidModeTimeoutActions() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "same",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    ),
                    LiveChatMessage(
                        id = "message-2",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "same",
                        publishedAtIso = "2026-06-07T10:00:01Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(raidMode = true),
            nowMillis = 3_000L
        )

        assertEquals(2, result.actionsTaken)
        assertEquals(listOf("viewer-1" to 300), youtube.timedOutUserIds)
        assertTrue(logs.actions.any { it.actionType == "timeoutUser" && it.reason == "raid_repeated_message_timeout" })
    }

    @Test
    fun carriesCommandCooldownStateAcrossPolls() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "!discord",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            ),
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-2",
                        authorChannelId = "viewer-2",
                        authorName = "ViewerTwo",
                        text = "!discord",
                        publishedAtIso = "2026-06-07T10:00:05Z"
                    )
                ),
                nextPageToken = "next-page-2",
                pollingIntervalMillis = 5000
            )
        )
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0)
        )
        val commands = listOf(
            BotCommand(
                id = "command-discord",
                name = "!discord",
                response = "Join the Discord",
                aliases = emptyList(),
                cooldownSeconds = 30,
                accessLevel = CommandAccessLevel.Everyone,
                enabled = true
            )
        )

        val first = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            commands = commands,
            nowMillis = 1_000L
        )
        val second = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            commands = commands,
            commandCooldownState = first.commandCooldownState,
            pageToken = first.nextPageToken,
            nowMillis = 10_000L
        )

        assertEquals(1, first.commandsSent)
        assertEquals(0, second.commandsSent)
        assertEquals(mapOf("command-discord" to 1), first.commandsUsed)
        assertEquals(listOf("Join the Discord"), youtube.sentMessages)
    }

    @Test
    fun appliesCommandCooldownWithinTheSamePollPage() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "!rules",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    ),
                    LiveChatMessage(
                        id = "message-2",
                        authorChannelId = "viewer-2",
                        authorName = "ViewerTwo",
                        text = "!rules",
                        publishedAtIso = "2026-06-07T10:00:01Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0)
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            commands = listOf(
                BotCommand(
                    id = "command-rules",
                    name = "!rules",
                    response = "Keep chat friendly",
                    aliases = emptyList(),
                    cooldownSeconds = 30,
                    accessLevel = CommandAccessLevel.Everyone,
                    enabled = true
                )
            ),
            nowMillis = 1_000L
        )

        assertEquals(1, result.commandsSent)
        assertEquals(mapOf("command-rules" to 1), result.commandsUsed)
        assertEquals(listOf("Keep chat friendly"), youtube.sentMessages)
    }

    @Test
    fun blocksInvalidOutboundCommandMessages() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "message-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "!blank",
                        publishedAtIso = "2026-06-07T10:00:00Z"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            commands = listOf(
                BotCommand(
                    id = "command-blank",
                    name = "!blank",
                    response = "   ",
                    aliases = emptyList(),
                    cooldownSeconds = 0,
                    accessLevel = CommandAccessLevel.Everyone,
                    enabled = true
                )
            ),
            nowMillis = 1_000L
        )

        assertEquals(0, result.commandsSent)
        assertEquals(emptyList<String>(), youtube.sentMessages)
        assertEquals("outbound_message_blocked", logs.events.single().type)
    }

    @Test
    fun sendsDueTimersAndRecordsUsage() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = emptyList(),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            timers = listOf(
                ScheduledTimer(
                    id = "timer-rules",
                    message = "Keep chat friendly.",
                    intervalMinutes = 10,
                    minChatMessages = 3,
                    enabled = true,
                    lastSentAtMillis = null
                )
            ),
            messagesSinceLastTimer = 3,
            nowMillis = 1_000L
        )

        assertEquals(1, result.timersSent)
        assertEquals(mapOf("timer-rules" to 1), result.timersUsed)
        assertEquals(listOf("Keep chat friendly."), youtube.sentMessages)
        assertEquals("timer_sent", logs.events.single().type)
    }

    @Test
    fun sendsOnlyOneDueTimerWhenMultipleTimersAreReady() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = emptyList(),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0)
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            timers = listOf(
                ScheduledTimer(
                    id = "timer-rules",
                    message = "Keep chat friendly.",
                    intervalMinutes = 10,
                    minChatMessages = 3,
                    enabled = true,
                    lastSentAtMillis = null
                ),
                ScheduledTimer(
                    id = "timer-socials",
                    message = "Follow after stream.",
                    intervalMinutes = 10,
                    minChatMessages = 3,
                    enabled = true,
                    lastSentAtMillis = null
                )
            ),
            messagesSinceLastTimer = 3,
            nowMillis = 1_000L
        )

        assertEquals(1, result.timersSent)
        assertEquals(1, result.timersUsed.values.sum())
        assertEquals(1, youtube.sentMessages.size)
        assertTrue(youtube.sentMessages.single() in setOf("Keep chat friendly.", "Follow after stream."))
    }

    @Test
    fun pausesDueTimersDuringEmergencyMode() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = emptyList(),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            actionRateLimiter = ActionRateLimiter(minimumIntervalMillis = 0)
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(),
            timers = listOf(
                ScheduledTimer(
                    id = "timer-rules",
                    message = "Keep chat friendly.",
                    intervalMinutes = 10,
                    minChatMessages = 3,
                    enabled = true,
                    lastSentAtMillis = null
                )
            ),
            messagesSinceLastTimer = 3,
            timersPaused = true,
            nowMillis = 1_000L
        )

        assertEquals(0, result.timersSent)
        assertEquals(emptyMap<String, Int>(), result.timersUsed)
        assertEquals(emptyList<String>(), youtube.sentMessages)
    }

    @Test
    fun recordsDeletedMessageEventsWithoutModeratingThem() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "delete-event-1",
                        authorChannelId = "moderator-1",
                        authorName = "Moderator",
                        text = "A message was deleted.",
                        publishedAtIso = "2026-06-07T10:00:00Z",
                        type = LiveChatMessageType.MessageDeleted,
                        isModerator = true,
                        targetMessageId = "message-1"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(blockedTerms = listOf("deleted"))
        )

        assertEquals(1, result.messagesProcessed)
        assertEquals(0, result.actionsTaken)
        assertEquals(emptyList<String>(), youtube.deletedMessageIds)
        assertEquals(emptyList<MessageLog>(), logs.messages)
        assertEquals("live_chat_message_deleted", logs.events.single().type)
        assertTrue(logs.events.single().metadataJson?.contains("message-1") == true)
    }

    @Test
    fun moderatesSuperChatTextAndRecordsPurchaseEvent() = runBlocking {
        val youtube = FakeYouTubeLiveChatClient(
            LiveChatPage(
                messages = listOf(
                    LiveChatMessage(
                        id = "super-1",
                        authorChannelId = "viewer-1",
                        authorName = "ViewerOne",
                        text = "boost this scam",
                        publishedAtIso = "2026-06-07T10:00:00Z",
                        type = LiveChatMessageType.SuperChat,
                        isMember = true,
                        purchaseAmountMicros = 5_000_000,
                        purchaseCurrency = "USD"
                    )
                ),
                nextPageToken = "next-page",
                pollingIntervalMillis = 5000
            )
        )
        val logs = RecordingLogSink()
        val coordinator = BotCoordinator(
            youtube = youtube,
            ruleEngine = RuleEngine(),
            logRepository = logs
        )

        val result = coordinator.processOnce(
            liveChatId = "live-chat-1",
            sessionId = "session-1",
            profile = ModerationProfile(
                blockedTerms = listOf("scam"),
                linkPolicy = LinkPolicy.Allow
            )
        )

        assertEquals(1, result.messagesProcessed)
        assertEquals(1, result.messagesDeleted)
        assertEquals(listOf("super-1"), logs.messages.map { it.youtubeMessageId })
        assertEquals("live_chat_purchase_event", logs.events.single().type)
        assertTrue(logs.events.single().metadataJson?.contains("5000000") == true)
    }

    private class FakeYouTubeLiveChatClient(
        vararg pages: LiveChatPage
    ) : YouTubeLiveChatClient {
        private val pages = pages.toList()
        private var pageIndex = 0
        val deletedMessageIds = mutableListOf<String>()
        val sentMessages = mutableListOf<String>()
        val hiddenUserIds = mutableListOf<String>()
        val timedOutUserIds = mutableListOf<Pair<String, Int>>()

        override suspend fun findActiveLiveChat(channelId: String): ActiveLiveChat? = null

        override suspend fun listMessages(liveChatId: String, pageToken: String?): LiveChatPage {
            val page = pages.getOrElse(pageIndex) { pages.last() }
            pageIndex += 1
            return page
        }

        override suspend fun sendMessage(liveChatId: String, text: String): String {
            sentMessages += text
            return "sent-message"
        }

        override suspend fun deleteMessage(messageId: String) {
            deletedMessageIds += messageId
        }

        override suspend fun hideUser(liveChatId: String, authorChannelId: String, durationSeconds: Int?) {
            if (durationSeconds == null) {
                hiddenUserIds += authorChannelId
            } else {
                timedOutUserIds += authorChannelId to durationSeconds
            }
        }
    }

    private class RecordingLogSink : BotLogSink {
        val messages = mutableListOf<MessageLog>()
        val actions = mutableListOf<ActionLog>()
        val events = mutableListOf<RuntimeLog>()

        override suspend fun recordChatMessage(
            sessionId: String,
            youtubeMessageId: String,
            authorChannelId: String,
            authorName: String,
            text: String,
            receivedAtIso: String?
        ) {
            messages += MessageLog(
                sessionId = sessionId,
                youtubeMessageId = youtubeMessageId,
                authorChannelId = authorChannelId,
                authorName = authorName,
                text = text,
                receivedAtIso = receivedAtIso
            )
        }

        override suspend fun recordModerationAction(
            sessionId: String,
            youtubeMessageId: String?,
            authorChannelId: String?,
            authorName: String?,
            messageText: String?,
            actionType: String,
            reason: String,
            confidence: Double?,
            logId: String?,
            metadataJson: String?
        ) {
            actions += ActionLog(
                sessionId = sessionId,
                youtubeMessageId = youtubeMessageId,
                authorChannelId = authorChannelId,
                authorName = authorName,
                messageText = messageText,
                actionType = actionType,
                reason = reason,
                confidence = confidence
            )
        }

        override suspend fun recordRuntimeEvent(
            sessionId: String,
            type: String,
            message: String,
            metadataJson: String?
        ) {
            events += RuntimeLog(
                sessionId = sessionId,
                type = type,
                message = message,
                metadataJson = metadataJson
            )
        }
    }

    private data class MessageLog(
        val sessionId: String,
        val youtubeMessageId: String,
        val authorChannelId: String,
        val authorName: String,
        val text: String,
        val receivedAtIso: String?
    )

    private data class ActionLog(
        val sessionId: String,
        val youtubeMessageId: String?,
        val authorChannelId: String?,
        val authorName: String?,
        val messageText: String?,
        val actionType: String,
        val reason: String,
        val confidence: Double?
    )

    private data class RuntimeLog(
        val sessionId: String,
        val type: String,
        val message: String,
        val metadataJson: String?
    )
}
