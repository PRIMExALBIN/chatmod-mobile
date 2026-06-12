package com.chatmod.mobile.runtime

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject

class BotSessionStatsTest {
    @Test
    fun aggregatesRunResultsIntoSummaryMetadata() {
        val stats = BotSessionStats(startedAtMillis = 1_000L)

        stats.record(
            BotRunResult(
                nextPageToken = "next",
                pollingIntervalMillis = 5_000L,
                messagesProcessed = 2,
                duplicateMessagesSkipped = 1,
                actionsTaken = 1,
                messagesDeleted = 1,
                usersHidden = 1,
                autoRepliesSent = 1,
                commandsSent = 1,
                commandsUsed = mapOf("command-rules" to 1),
                commandCooldownState = CommandCooldownState(),
                timersSent = 1,
                timersUsed = mapOf("timer-socials" to 1),
                triggeredRules = mapOf("blocked_term" to 2, "link_policy" to 1),
                streamEnded = false
            )
        )

        val metadata = stats.metadataPairs(11_000L).toMap()

        assertEquals(10_000L, metadata["durationMillis"])
        assertEquals(2, metadata["messagesProcessed"])
        assertEquals(1, metadata["duplicateMessagesSkipped"])
        assertEquals(1, metadata["moderationActionsTaken"])
        assertEquals(1, metadata["messagesDeleted"])
        assertEquals(1, metadata["usersTimedOutOrHidden"])
        assertEquals(1, metadata["autoRepliesSent"])
        assertEquals(1, metadata["commandsSent"])
        assertEquals(1, metadata["timersSent"])
        assertEquals("""{"command-rules":1}""", metadata["commandsUsedJson"])
        assertEquals("""{"timer-socials":1}""", metadata["timersUsedJson"])
        val topTriggeredRules = JSONObject(metadata["topTriggeredRulesJson"] as String)
        assertEquals(2, topTriggeredRules.getInt("blocked_term"))
        assertEquals(1, topTriggeredRules.getInt("link_policy"))
    }
}
