package com.chatmod.mobile.domain.rules

import java.time.Instant

data class ChatMessage(
    val id: String,
    val authorChannelId: String,
    val authorName: String,
    val text: String,
    val isOwner: Boolean = false,
    val isModerator: Boolean = false,
    val isMember: Boolean = false,
    val isVerified: Boolean = false,
    val timestampMillis: Long? = null,
    val streamStartedAtMillis: Long? = null
)

data class ModerationProfile(
    val blockedTerms: List<String> = emptyList(),
    val regexPatterns: List<String> = emptyList(),
    val linkPolicy: LinkPolicy = LinkPolicy.Flag,
    val allowedDomains: List<String> = emptyList(),
    val blockedDomains: List<String> = emptyList(),
    val capsThreshold: Double = 0.75,
    val maxRepeatedCharacters: Int = 6,
    val maxEmojiCount: Int = 8,
    val maxMentions: Int = 6,
    val maxSymbolCount: Int = 16,
    val trustedChannelIds: List<String> = emptyList(),
    val temporaryTrustedChannels: List<TemporaryTrustedChannel> = emptyList(),
    val ignoreMembers: Boolean = false,
    val raidMode: Boolean = false,
    val newChatterBurstThreshold: Int = 6,
    val newChatterBurstWindowSeconds: Int = 30,
    val firstStreamMinutesOnly: Int? = null,
    val autoReplyEnabled: Boolean = false,
    val autoReplyMessage: String = "Please keep chat safe and on topic.",
    val hideUserOnSevereMatch: Boolean = false
)

data class TemporaryTrustedChannel(
    val channelId: String,
    val expiresAt: String
)

enum class LinkPolicy {
    Allow,
    Flag,
    Delete
}

data class ModerationDecision(
    val matched: Boolean,
    val actions: List<ModerationAction>
)

data class ModerationAction(
    val type: ActionType,
    val reason: String,
    val confidence: Double,
    val text: String? = null
)

enum class ActionType {
    Allow,
    FlagForReview,
    DeleteMessage,
    TimeoutUser,
    HideUser,
    SendAutoReply
}

class RuleEngine {
    fun evaluate(message: ChatMessage, profile: ModerationProfile): ModerationDecision {
        if (
            message.isOwner ||
            message.isModerator ||
            message.isVerified ||
            message.authorChannelId in profile.trustedChannelIds ||
            isTemporarilyTrusted(message, profile) ||
            (profile.ignoreMembers && message.isMember)
        ) {
            return ModerationDecision(
                matched = false,
                actions = listOf(
                    ModerationAction(
                        ActionType.Allow,
                        trustedReason(message, profile),
                        1.0
                    )
                )
            )
        }

        if (isOutsideFirstStreamWindow(message, profile)) {
            return ModerationDecision(
                matched = false,
                actions = listOf(
                    ModerationAction(
                        ActionType.Allow,
                        "outside_first_stream_window",
                        1.0
                    )
                )
            )
        }

        val effectiveProfile = profile.effectiveForRaidMode()
        val actions = mutableListOf<ModerationAction>()
        val normalizedText = message.text.normalize()

        effectiveProfile.blockedTerms.forEach { term ->
            val normalizedTerm = term.normalize()
            if (normalizedTerm.isNotBlank() && normalizedText.contains(normalizedTerm)) {
                actions += ModerationAction(ActionType.DeleteMessage, "blocked_term:$term", 0.96)
            }
        }

        effectiveProfile.regexPatterns.forEachIndexed { index, pattern ->
            if (matchesSafeRegex(pattern, message.text)) {
                actions += ModerationAction(ActionType.DeleteMessage, "regex_pattern:${index + 1}", 0.93)
            }
        }

        if (containsLink(message.text) && effectiveProfile.linkPolicy != LinkPolicy.Allow) {
            actions += ModerationAction(
                type = if (effectiveProfile.linkPolicy == LinkPolicy.Delete) ActionType.DeleteMessage else ActionType.FlagForReview,
                reason = "link_policy",
                confidence = if (effectiveProfile.linkPolicy == LinkPolicy.Delete) 0.90 else 0.75
            )
        }

        linkedDomains(message.text).forEach { domain ->
            when {
                matchesDomainList(domain, effectiveProfile.blockedDomains) -> {
                    actions += ModerationAction(ActionType.DeleteMessage, "blocked_domain:$domain", 0.94)
                }
                effectiveProfile.allowedDomains.isNotEmpty() && !matchesDomainList(domain, effectiveProfile.allowedDomains) -> {
                    actions += ModerationAction(
                        type = if (effectiveProfile.linkPolicy == LinkPolicy.Delete) ActionType.DeleteMessage else ActionType.FlagForReview,
                        reason = "domain_not_allowed:$domain",
                        confidence = if (effectiveProfile.linkPolicy == LinkPolicy.Delete) 0.90 else 0.76
                    )
                }
            }
        }

        if (capsRatio(message.text) >= effectiveProfile.capsThreshold && message.text.lettersOnly().length >= 8) {
            actions += ModerationAction(ActionType.FlagForReview, "excessive_caps", 0.70)
        }

        if (hasRepeatedCharacters(message.text, effectiveProfile.maxRepeatedCharacters)) {
            actions += ModerationAction(ActionType.FlagForReview, "repeated_characters", 0.68)
        }

        if (emojiCount(message.text) > effectiveProfile.maxEmojiCount) {
            actions += ModerationAction(ActionType.FlagForReview, "emoji_spam", 0.69)
        }

        if (mentionCount(message.text) > effectiveProfile.maxMentions) {
            actions += ModerationAction(ActionType.FlagForReview, "mention_spam", 0.72)
        }

        if (symbolCount(message.text) > effectiveProfile.maxSymbolCount && symbolRatio(message.text) >= 0.45) {
            actions += ModerationAction(ActionType.FlagForReview, "symbol_spam", 0.67)
        }

        return ModerationDecision(
            matched = actions.isNotEmpty(),
            actions = actions
                .withConfiguredHideUser(effectiveProfile)
                .withConfiguredAutoReply(effectiveProfile)
                .distinctBy { "${it.type}:${it.reason}" }
        )
    }

    private fun List<ModerationAction>.withConfiguredHideUser(profile: ModerationProfile): List<ModerationAction> {
        if (!profile.hideUserOnSevereMatch || any { it.type == ActionType.HideUser }) {
            return this
        }

        val hasSevereDelete = any { it.type == ActionType.DeleteMessage && it.confidence >= 0.90 }
        if (!hasSevereDelete) {
            return this
        }

        return this + ModerationAction(
            type = ActionType.HideUser,
            reason = "severe_match_hide_user",
            confidence = 0.90
        )
    }

    private fun List<ModerationAction>.withConfiguredAutoReply(profile: ModerationProfile): List<ModerationAction> {
        val text = safeAutoReplyText(profile.autoReplyMessage)
        if (!profile.autoReplyEnabled || isEmpty() || text == null) {
            return this
        }

        return this + ModerationAction(
            type = ActionType.SendAutoReply,
            reason = "auto_reply",
            confidence = 0.65,
            text = text
        )
    }

    private fun safeAutoReplyText(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.length > 200 || trimmed.any { it.isUnsafeControlCharacter() }) {
            return null
        }

        return trimmed
    }

    private fun trustedReason(message: ChatMessage, profile: ModerationProfile): String {
        if (message.authorChannelId in profile.trustedChannelIds) {
            return "trusted_channel"
        }

        if (isTemporarilyTrusted(message, profile)) {
            return "temporary_trusted_channel"
        }

        if (message.isVerified) {
            return "trusted_verified"
        }

        return if (profile.ignoreMembers && message.isMember) "trusted_member" else "trusted_author"
    }

    private fun isTemporarilyTrusted(
        message: ChatMessage,
        profile: ModerationProfile,
        now: Instant = Instant.now()
    ): Boolean {
        return profile.temporaryTrustedChannels.any { trusted ->
            trusted.channelId == message.authorChannelId &&
                runCatching { Instant.parse(trusted.expiresAt).isAfter(now) }.getOrDefault(false)
        }
    }

    private fun isOutsideFirstStreamWindow(message: ChatMessage, profile: ModerationProfile): Boolean {
        val firstMinutes = profile.firstStreamMinutesOnly ?: return false
        val streamStartedAt = message.streamStartedAtMillis ?: return true
        val timestamp = message.timestampMillis ?: return true
        val elapsedMinutes = ((timestamp - streamStartedAt) / 60_000L).toInt()
        return elapsedMinutes >= firstMinutes
    }

    private fun containsLink(text: String): Boolean {
        return Regex("""\b(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun linkedDomains(text: String): List<String> {
        return Regex("""\b(?:https?://|www\.)\S+""", RegexOption.IGNORE_CASE)
            .findAll(text)
            .mapNotNull { match -> extractDomain(match.value) }
            .toList()
    }

    private fun capsRatio(text: String): Double {
        val letters = text.lettersOnly()
        if (letters.isEmpty()) return 0.0

        return letters.count { it.isUpperCase() }.toDouble() / letters.length
    }

    private fun hasRepeatedCharacters(text: String, maxRepeatedCharacters: Int): Boolean {
        var previousCodePoint: Int? = null
        var runLength = 0
        val codePoints = text.codePoints().iterator()
        while (codePoints.hasNext()) {
            val codePoint = codePoints.nextInt()
            val normalizedCodePoint = Character.toLowerCase(codePoint)
            if (normalizedCodePoint == previousCodePoint) {
                runLength += 1
            } else {
                previousCodePoint = normalizedCodePoint
                runLength = 1
            }
            if (runLength > maxRepeatedCharacters) {
                return true
            }
        }
        return false
    }

    private fun matchesSafeRegex(pattern: String, text: String): Boolean {
        if (pattern.isBlank() || pattern.length > 160) {
            return false
        }

        return runCatching {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
        }.getOrDefault(false)
    }

    private fun extractDomain(link: String): String? {
        val withScheme = if (link.lowercase().startsWith("www.")) "https://$link" else link
        return runCatching {
            java.net.URI(withScheme).host?.normalizeDomain()
        }.getOrNull()
    }

    private fun matchesDomainList(domain: String, domains: List<String>): Boolean {
        return domains
            .map { it.normalizeDomain() }
            .filter { it.isNotBlank() }
            .any { allowedDomain -> domain == allowedDomain || domain.endsWith(".$allowedDomain") }
    }

    private fun emojiCount(text: String): Int {
        return text.codePoints().toArray().count { codePoint ->
            Character.getType(codePoint) == Character.OTHER_SYMBOL.toInt()
        }
    }

    private fun mentionCount(text: String): Int {
        return Regex("""@[\p{L}\p{N}_-]+""").findAll(text).count()
    }

    private fun symbolCount(text: String): Int {
        return text.count { character -> !character.isLetterOrDigit() && !character.isWhitespace() }
    }

    private fun symbolRatio(text: String): Double {
        if (text.isBlank()) return 0.0

        return symbolCount(text).toDouble() / text.length
    }
}

private fun String.normalize(): String = trim().lowercase().replace(Regex("""\s+"""), " ")

private fun String.lettersOnly(): String = filter { it.isLetter() }

private fun Char.isUnsafeControlCharacter(): Boolean {
    return code in 0x00..0x1F || code == 0x7F
}

private fun ModerationProfile.effectiveForRaidMode(): ModerationProfile {
    if (!raidMode) {
        return this
    }

    return copy(
        linkPolicy = if (linkPolicy == LinkPolicy.Allow) LinkPolicy.Flag else linkPolicy,
        capsThreshold = minOf(capsThreshold, 0.60),
        maxRepeatedCharacters = minOf(maxRepeatedCharacters, 4),
        maxEmojiCount = minOf(maxEmojiCount, 5),
        maxMentions = minOf(maxMentions, 3),
        maxSymbolCount = minOf(maxSymbolCount, 10),
        newChatterBurstThreshold = if (newChatterBurstThreshold <= 1) 3 else minOf(newChatterBurstThreshold, 3),
        newChatterBurstWindowSeconds = minOf(newChatterBurstWindowSeconds, 20)
    )
}

private fun String.normalizeDomain(): String {
    return trim()
        .lowercase()
        .removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .substringBefore("/")
        .substringBefore(":")
}
