package com.chatmod.mobile.runtime

import com.chatmod.mobile.domain.rules.ActionType
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.domain.rules.TemporaryTrustedChannel
import com.chatmod.mobile.youtube.LiveChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatAbuseTrackerTest {
    @Test
    fun deletesRepeatedMessagesInsideWindow() {
        val tracker = ChatAbuseTracker(repeatedMessageThreshold = 3)
        val profile = ModerationProfile()

        tracker.evaluate(message("message-1", "same text"), profile, nowMillis = 1_000L)
        tracker.evaluate(message("message-2", "same   text"), profile, nowMillis = 2_000L)
        val actions = tracker.evaluate(message("message-3", "SAME TEXT"), profile, nowMillis = 3_000L)

        assertTrue(actions.any { it.type == ActionType.DeleteMessage && it.reason == "repeated_message_spam" })
    }

    @Test
    fun flagsMessageFloodsInsideWindow() {
        val tracker = ChatAbuseTracker(floodMessageThreshold = 3, floodWindowMillis = 10_000L)
        val profile = ModerationProfile()

        tracker.evaluate(message("message-1", "one"), profile, nowMillis = 1_000L)
        tracker.evaluate(message("message-2", "two"), profile, nowMillis = 2_000L)
        val actions = tracker.evaluate(message("message-3", "three"), profile, nowMillis = 3_000L)

        assertTrue(actions.any { it.type == ActionType.FlagForReview && it.reason == "message_flood" })
    }

    @Test
    fun flagsSuspiciousNewUserBurstsInsideWindow() {
        val tracker = ChatAbuseTracker()
        val profile = ModerationProfile(
            newChatterBurstThreshold = 3,
            newChatterBurstWindowSeconds = 30
        )

        tracker.evaluate(message("message-1", "hello", authorChannelId = "viewer-1"), profile, nowMillis = 1_000L)
        tracker.evaluate(message("message-2", "hello", authorChannelId = "viewer-2"), profile, nowMillis = 2_000L)
        val actions = tracker.evaluate(message("message-3", "hello", authorChannelId = "viewer-3"), profile, nowMillis = 3_000L)

        assertTrue(actions.any { it.type == ActionType.FlagForReview && it.reason == "suspicious_new_user_burst" })
    }

    @Test
    fun ignoresNewUserBurstsOutsideWindow() {
        val tracker = ChatAbuseTracker()
        val profile = ModerationProfile(
            newChatterBurstThreshold = 3,
            newChatterBurstWindowSeconds = 5
        )

        tracker.evaluate(message("message-1", "hello", authorChannelId = "viewer-1"), profile, nowMillis = 1_000L)
        tracker.evaluate(message("message-2", "hello", authorChannelId = "viewer-2"), profile, nowMillis = 7_000L)
        val actions = tracker.evaluate(message("message-3", "hello", authorChannelId = "viewer-3"), profile, nowMillis = 13_000L)

        assertTrue(actions.none { it.reason == "suspicious_new_user_burst" })
    }

    @Test
    fun raidModeUsesStricterFloodThresholds() {
        val tracker = ChatAbuseTracker(floodMessageThreshold = 6, floodWindowMillis = 10_000L)
        val profile = ModerationProfile(raidMode = true)

        tracker.evaluate(message("message-1", "one"), profile, nowMillis = 1_000L)
        tracker.evaluate(message("message-2", "two"), profile, nowMillis = 2_000L)
        val actions = tracker.evaluate(message("message-3", "three"), profile, nowMillis = 3_000L)

        assertTrue(actions.any { it.type == ActionType.FlagForReview && it.reason == "message_flood" })
    }

    @Test
    fun raidModeTimeoutsRepeatAndFloodSpam() {
        val tracker = ChatAbuseTracker(
            repeatedMessageThreshold = 3,
            floodMessageThreshold = 6,
            floodWindowMillis = 10_000L
        )
        val profile = ModerationProfile(raidMode = true)

        tracker.evaluate(message("message-1", "same"), profile, nowMillis = 1_000L)
        tracker.evaluate(message("message-2", "same"), profile, nowMillis = 2_000L)
        val actions = tracker.evaluate(message("message-3", "same"), profile, nowMillis = 3_000L)

        assertTrue(actions.any { it.type == ActionType.TimeoutUser && it.reason == "raid_repeated_message_timeout" })
        assertTrue(actions.any { it.type == ActionType.TimeoutUser && it.reason == "raid_message_flood_timeout" })
    }

    @Test
    fun ignoresTrustedMembersWhenProfileAllowsIt() {
        val tracker = ChatAbuseTracker(repeatedMessageThreshold = 2)
        val profile = ModerationProfile(ignoreMembers = true)

        tracker.evaluate(memberMessage("message-1", "same"), profile, nowMillis = 1_000L)
        val actions = tracker.evaluate(memberMessage("message-2", "same"), profile, nowMillis = 2_000L)

        assertEquals(emptyList<ActionType>(), actions.map { it.type })
    }

    @Test
    fun ignoresTrustedChannelIds() {
        val tracker = ChatAbuseTracker(repeatedMessageThreshold = 2)
        val profile = ModerationProfile(trustedChannelIds = listOf("viewer-1"))

        tracker.evaluate(message("message-1", "same"), profile, nowMillis = 1_000L)
        val actions = tracker.evaluate(message("message-2", "same"), profile, nowMillis = 2_000L)

        assertEquals(emptyList<ActionType>(), actions.map { it.type })
    }

    @Test
    fun ignoresVerifiedAuthors() {
        val tracker = ChatAbuseTracker(repeatedMessageThreshold = 2)
        val profile = ModerationProfile()

        tracker.evaluate(message("message-1", "same").copy(isVerified = true), profile, nowMillis = 1_000L)
        val actions = tracker.evaluate(message("message-2", "same").copy(isVerified = true), profile, nowMillis = 2_000L)

        assertEquals(emptyList<ActionType>(), actions.map { it.type })
    }

    @Test
    fun ignoresUnexpiredTemporaryTrustedChannelIds() {
        val tracker = ChatAbuseTracker(repeatedMessageThreshold = 2)
        val profile = ModerationProfile(
            temporaryTrustedChannels = listOf(
                TemporaryTrustedChannel(
                    channelId = "viewer-1",
                    expiresAt = "2026-06-07T10:01:00Z"
                )
            )
        )

        tracker.evaluate(message("message-1", "same"), profile, nowMillis = 1_000L)
        val actions = tracker.evaluate(message("message-2", "same"), profile, nowMillis = 2_000L)

        assertEquals(emptyList<ActionType>(), actions.map { it.type })
    }

    private fun message(
        id: String,
        text: String,
        authorChannelId: String = "viewer-1"
    ): LiveChatMessage {
        return LiveChatMessage(
            id = id,
            authorChannelId = authorChannelId,
            authorName = "ViewerOne",
            text = text,
            publishedAtIso = "2026-06-07T10:00:00Z"
        )
    }

    private fun memberMessage(id: String, text: String): LiveChatMessage {
        return message(id, text).copy(isMember = true)
    }
}
