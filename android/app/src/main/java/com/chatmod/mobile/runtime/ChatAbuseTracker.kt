package com.chatmod.mobile.runtime

import com.chatmod.mobile.domain.rules.ActionType
import com.chatmod.mobile.domain.rules.ModerationAction
import com.chatmod.mobile.domain.rules.ModerationProfile
import com.chatmod.mobile.youtube.LiveChatMessage
import java.time.Instant
import java.util.ArrayDeque

class ChatAbuseTracker(
    private val repeatedMessageThreshold: Int = 3,
    private val repeatedMessageWindowMillis: Long = 30_000L,
    private val floodMessageThreshold: Int = 8,
    private val floodWindowMillis: Long = 10_000L
) {
    private val recentByAuthor = mutableMapOf<String, ArrayDeque<RecentMessage>>()
    private val firstSeenByAuthor = mutableMapOf<String, Long>()
    private val recentNewChatters = ArrayDeque<RecentChatter>()

    init {
        require(repeatedMessageThreshold > 1) { "repeatedMessageThreshold must be greater than one" }
        require(repeatedMessageWindowMillis > 0) { "repeatedMessageWindowMillis must be positive" }
        require(floodMessageThreshold > 1) { "floodMessageThreshold must be greater than one" }
        require(floodWindowMillis > 0) { "floodWindowMillis must be positive" }
    }

    fun evaluate(
        message: LiveChatMessage,
        profile: ModerationProfile,
        nowMillis: Long
    ): List<ModerationAction> {
        if (
            message.isOwner ||
            message.isModerator ||
            message.isVerified ||
            message.authorChannelId in profile.trustedChannelIds ||
            isTemporarilyTrusted(message, profile, nowMillis) ||
            (profile.ignoreMembers && message.isMember)
        ) {
            return emptyList()
        }

        val normalizedText = message.text.normalizeForAbuseTracking()
        if (normalizedText.isBlank()) {
            return emptyList()
        }

        val isNewAuthor = message.authorChannelId !in firstSeenByAuthor
        if (isNewAuthor) {
            firstSeenByAuthor[message.authorChannelId] = nowMillis
            trimNewChatters(profile, nowMillis)
            recentNewChatters.addLast(RecentChatter(message.authorChannelId, nowMillis))
        }

        val history = recentByAuthor.getOrPut(message.authorChannelId) { ArrayDeque() }
        trim(history, nowMillis)
        history.addLast(RecentMessage(normalizedText, nowMillis))

        val actions = mutableListOf<ModerationAction>()
        val effectiveRepeatedMessageThreshold = effectiveRepeatedMessageThreshold(profile)
        val effectiveFloodMessageThreshold = effectiveFloodMessageThreshold(profile)
        val repeatedCount = history.count { recent ->
            recent.normalizedText == normalizedText &&
                nowMillis - recent.seenAtMillis <= repeatedMessageWindowMillis
        }
        val floodCount = history.count { recent ->
            nowMillis - recent.seenAtMillis <= floodWindowMillis
        }

        if (repeatedCount >= effectiveRepeatedMessageThreshold) {
            actions += ModerationAction(ActionType.DeleteMessage, "repeated_message_spam", 0.88)
            if (profile.raidMode) {
                actions += ModerationAction(ActionType.TimeoutUser, "raid_repeated_message_timeout", 0.86)
            }
        }
        if (floodCount >= effectiveFloodMessageThreshold) {
            actions += ModerationAction(ActionType.FlagForReview, "message_flood", if (profile.raidMode) 0.82 else 0.74)
            if (profile.raidMode) {
                actions += ModerationAction(ActionType.TimeoutUser, "raid_message_flood_timeout", 0.84)
            }
        }
        if (isNewAuthor && newChatterBurstCount(profile, nowMillis) >= effectiveNewChatterBurstThreshold(profile)) {
            actions += ModerationAction(
                type = ActionType.FlagForReview,
                reason = "suspicious_new_user_burst",
                confidence = if (profile.raidMode) 0.86 else 0.78
            )
        }

        return actions
    }

    private fun trim(history: ArrayDeque<RecentMessage>, nowMillis: Long) {
        val cutoffMillis = nowMillis - maxOf(repeatedMessageWindowMillis, floodWindowMillis)
        while (history.peekFirst()?.seenAtMillis?.let { seenAtMillis -> seenAtMillis < cutoffMillis } == true) {
            history.removeFirst()
        }
    }

    private fun trimNewChatters(profile: ModerationProfile, nowMillis: Long) {
        val cutoffMillis = nowMillis - newChatterBurstWindowMillis(profile)
        while (recentNewChatters.peekFirst()?.seenAtMillis?.let { seenAtMillis -> seenAtMillis < cutoffMillis } == true) {
            recentNewChatters.removeFirst()
        }
    }

    private fun newChatterBurstCount(profile: ModerationProfile, nowMillis: Long): Int {
        trimNewChatters(profile, nowMillis)
        return recentNewChatters
            .map { recent -> recent.authorChannelId }
            .distinct()
            .size
    }

    private fun effectiveRepeatedMessageThreshold(profile: ModerationProfile): Int {
        return if (profile.raidMode) maxOf(2, repeatedMessageThreshold - 1) else repeatedMessageThreshold
    }

    private fun effectiveFloodMessageThreshold(profile: ModerationProfile): Int {
        return if (profile.raidMode) maxOf(3, floodMessageThreshold / 2) else floodMessageThreshold
    }

    private fun effectiveNewChatterBurstThreshold(profile: ModerationProfile): Int {
        if (profile.raidMode && profile.newChatterBurstThreshold <= 1) {
            return 3
        }

        if (profile.newChatterBurstThreshold <= 1) {
            return Int.MAX_VALUE
        }

        return if (profile.raidMode) {
            maxOf(2, profile.newChatterBurstThreshold / 2)
        } else {
            profile.newChatterBurstThreshold
        }
    }

    private fun newChatterBurstWindowMillis(profile: ModerationProfile): Long {
        val seconds = profile.newChatterBurstWindowSeconds.coerceIn(5, 600)
        val effectiveSeconds = if (profile.raidMode) minOf(seconds, 20) else seconds
        return effectiveSeconds * 1_000L
    }

    private fun isTemporarilyTrusted(
        message: LiveChatMessage,
        profile: ModerationProfile,
        nowMillis: Long
    ): Boolean {
        val now = Instant.ofEpochMilli(nowMillis)
        return profile.temporaryTrustedChannels.any { trusted ->
            trusted.channelId == message.authorChannelId &&
                runCatching { Instant.parse(trusted.expiresAt).isAfter(now) }.getOrDefault(false)
        }
    }
}

private data class RecentMessage(
    val normalizedText: String,
    val seenAtMillis: Long
)

private data class RecentChatter(
    val authorChannelId: String,
    val seenAtMillis: Long
)

private fun String.normalizeForAbuseTracking(): String {
    return trim().lowercase().replace(Regex("""\s+"""), " ")
}
