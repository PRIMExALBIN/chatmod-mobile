package com.chatmod.mobile.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RuleEngineTest {
    private val engine = RuleEngine()
    private val profile = ModerationProfile(
        blockedTerms = listOf("scam", "cheap views"),
        linkPolicy = LinkPolicy.Delete,
        capsThreshold = 0.75,
        maxRepeatedCharacters = 5
    )

    @Test
    fun blockedTermsDeleteMessages() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "this is a scam"),
            profile
        )

        assertTrue(decision.matched)
        assertTrue(decision.actions.any { it.type == ActionType.DeleteMessage && it.reason == "blocked_term:scam" })
    }

    @Test
    fun moderatorsAreTrusted() {
        val decision = engine.evaluate(
            ChatMessage("m1", "mod-1", "Mod", "scam link", isModerator = true),
            profile
        )

        assertEquals(false, decision.matched)
        assertEquals(ActionType.Allow, decision.actions.single().type)
    }

    @Test
    fun customRegexPatternsDeleteMessages() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "free nitro giveaway"),
            profile.copy(regexPatterns = listOf("""free\s+nitro"""))
        )

        assertTrue(decision.actions.any { it.type == ActionType.DeleteMessage && it.reason == "regex_pattern:1" })
    }

    @Test
    fun invalidRegexPatternsAreIgnored() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "free nitro giveaway"),
            profile.copy(regexPatterns = listOf("["))
        )

        assertEquals(false, decision.matched)
    }

    @Test
    fun domainBlocklistDeletesLinkedMessages() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "check https://bad.example/path"),
            profile.copy(linkPolicy = LinkPolicy.Allow, blockedDomains = listOf("example"))
        )

        assertTrue(decision.actions.any { it.type == ActionType.DeleteMessage && it.reason == "blocked_domain:bad.example" })
    }

    @Test
    fun domainAllowlistFlagsUnknownDomains() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "watch www.notallowed.test"),
            profile.copy(linkPolicy = LinkPolicy.Flag, allowedDomains = listOf("youtube.com"))
        )

        assertTrue(decision.actions.any { it.type == ActionType.FlagForReview && it.reason == "domain_not_allowed:notallowed.test" })
    }

    @Test
    fun emojiAndMentionSpamAreFlagged() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "@a @b @c 😀😀😀"),
            profile.copy(maxEmojiCount = 2, maxMentions = 2)
        )

        assertTrue(decision.actions.any { it.reason == "emoji_spam" })
        assertTrue(decision.actions.any { it.reason == "mention_spam" })
    }

    @Test
    fun symbolSpamIsFlagged() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "%%%% $$$$ #### !!!! @@@@"),
            profile.copy(maxSymbolCount = 8)
        )

        assertTrue(decision.actions.any { it.type == ActionType.FlagForReview && it.reason == "symbol_spam" })
    }

    @Test
    fun raidModeAppliesStricterThresholds() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "THIS IS LOUD"),
            profile.copy(capsThreshold = 0.90, raidMode = true)
        )

        assertTrue(decision.actions.any { it.type == ActionType.FlagForReview && it.reason == "excessive_caps" })
    }

    @Test
    fun configuredMembersAreTrusted() {
        val decision = engine.evaluate(
            ChatMessage("m1", "member-1", "Member", "this is a scam", isMember = true),
            profile.copy(ignoreMembers = true)
        )

        assertEquals(false, decision.matched)
        assertEquals("trusted_member", decision.actions.single().reason)
    }

    @Test
    fun trustedChannelIdsAreAllowedBeforeFilters() {
        val decision = engine.evaluate(
            ChatMessage("m1", "trusted-viewer", "Trusted Viewer", "this is a scam"),
            profile.copy(trustedChannelIds = listOf("trusted-viewer"))
        )

        assertEquals(false, decision.matched)
        assertEquals("trusted_channel", decision.actions.single().reason)
    }

    @Test
    fun verifiedAuthorsAreAllowedBeforeFilters() {
        val decision = engine.evaluate(
            ChatMessage("m1", "verified-viewer", "Verified Viewer", "this is a scam", isVerified = true),
            profile
        )

        assertEquals(false, decision.matched)
        assertEquals("trusted_verified", decision.actions.single().reason)
    }

    @Test
    fun temporaryTrustedChannelIdsAreAllowedBeforeFilters() {
        val decision = engine.evaluate(
            ChatMessage("m1", "temp-viewer", "Temp Viewer", "this is a scam"),
            profile.copy(
                temporaryTrustedChannels = listOf(
                    TemporaryTrustedChannel(
                        channelId = "temp-viewer",
                        expiresAt = Instant.now().plusSeconds(60).toString()
                    )
                )
            )
        )

        assertEquals(false, decision.matched)
        assertEquals("temporary_trusted_channel", decision.actions.single().reason)
    }

    @Test
    fun configuredAutoReplyAddsReplyActionForMatchedRules() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "this is a scam"),
            profile.copy(
                autoReplyEnabled = true,
                autoReplyMessage = "Please keep chat clean."
            )
        )

        assertTrue(
            decision.actions.any {
                it.type == ActionType.SendAutoReply &&
                    it.reason == "auto_reply" &&
                    it.text == "Please keep chat clean."
            }
        )
    }

    @Test
    fun configuredAutoReplyDoesNotReplyToTrustedUsers() {
        val decision = engine.evaluate(
            ChatMessage("m1", "mod-1", "Mod", "this is a scam", isModerator = true),
            profile.copy(autoReplyEnabled = true)
        )

        assertEquals(false, decision.matched)
        assertTrue(decision.actions.none { it.type == ActionType.SendAutoReply })
    }

    @Test
    fun severeMatchHidingAddsHideUserActionWhenEnabled() {
        val decision = engine.evaluate(
            ChatMessage("m1", "viewer-1", "Viewer", "this is a scam"),
            profile.copy(hideUserOnSevereMatch = true)
        )

        assertTrue(
            decision.actions.any {
                it.type == ActionType.HideUser &&
                    it.reason == "severe_match_hide_user"
            }
        )
    }

    @Test
    fun severeMatchHidingDoesNotHideTrustedUsers() {
        val decision = engine.evaluate(
            ChatMessage("m1", "verified-viewer", "Verified Viewer", "this is a scam", isVerified = true),
            profile.copy(hideUserOnSevereMatch = true)
        )

        assertEquals(false, decision.matched)
        assertEquals("trusted_verified", decision.actions.single().reason)
        assertTrue(decision.actions.none { it.type == ActionType.HideUser })
    }

    @Test
    fun firstStreamMinutesTargetingAppliesRulesInsideWindow() {
        val decision = engine.evaluate(
            ChatMessage(
                id = "m1",
                authorChannelId = "viewer-1",
                authorName = "Viewer",
                text = "this is a scam",
                timestampMillis = 5 * 60_000L,
                streamStartedAtMillis = 0L
            ),
            profile.copy(firstStreamMinutesOnly = 10)
        )

        assertTrue(decision.matched)
        assertTrue(decision.actions.any { it.reason == "blocked_term:scam" })
    }

    @Test
    fun firstStreamMinutesTargetingSkipsRulesOutsideWindow() {
        val decision = engine.evaluate(
            ChatMessage(
                id = "m1",
                authorChannelId = "viewer-1",
                authorName = "Viewer",
                text = "this is a scam",
                timestampMillis = 11 * 60_000L,
                streamStartedAtMillis = 0L
            ),
            profile.copy(firstStreamMinutesOnly = 10)
        )

        assertEquals(false, decision.matched)
        assertEquals("outside_first_stream_window", decision.actions.single().reason)
    }

    @Test
    fun expiredTemporaryTrustedChannelIdsAreIgnored() {
        val decision = engine.evaluate(
            ChatMessage("m1", "temp-viewer", "Temp Viewer", "this is a scam"),
            profile.copy(
                temporaryTrustedChannels = listOf(
                    TemporaryTrustedChannel(
                        channelId = "temp-viewer",
                        expiresAt = Instant.now().minusSeconds(60).toString()
                    )
                )
            )
        )

        assertTrue(decision.matched)
        assertTrue(decision.actions.any { it.reason == "blocked_term:scam" })
    }
}
