package com.chatmod.mobile.runtime

import com.chatmod.mobile.youtube.LiveChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CommandRuntimeTest {
    private val runtime = CommandRuntime()
    private val command = BotCommand(
        id = "command-1",
        name = "!discord",
        response = "Join {username}: {args}",
        aliases = listOf("!community"),
        cooldownSeconds = 30,
        accessLevel = CommandAccessLevel.Everyone,
        enabled = true
    )

    @Test
    fun parsesCommandWithArgs() {
        val parsed = runtime.parse(" !Discord one two ")

        assertEquals("!discord", parsed?.trigger)
        assertEquals(listOf("one", "two"), parsed?.args)
    }

    @Test
    fun matchesAliasAndRendersResponse() {
        val result = runtime.evaluate(
            message = message("!community please"),
            commands = listOf(command),
            context = CommandContext(now = Instant.parse("2026-06-07T10:00:00Z"))
        )

        assertTrue(result.matched)
        assertEquals("Join ViewerOne: please", result.response)
    }

    @Test
    fun blocksCooldown() {
        val result = runtime.evaluate(
            message = message("!discord"),
            commands = listOf(command),
            cooldownState = CommandCooldownState(
                commandLastUsedAt = mapOf("command-1" to 1000L)
            ),
            context = CommandContext(now = Instant.ofEpochMilli(11_000L))
        )

        assertEquals(CommandResultReason.Cooldown, result.reason)
        assertEquals(20, result.cooldownRemainingSeconds)
    }

    @Test
    fun recordsGlobalAndUserCooldownUsage() {
        val state = CommandCooldownState().recordUsage(
            commandId = "command-1",
            authorChannelId = "viewer-1",
            usedAtMillis = 1000L
        )

        assertEquals(1000L, state.commandLastUsedAt["command-1"])
        assertEquals(1000L, state.userCommandLastUsedAt["viewer-1:command-1"])
    }

    @Test
    fun rendersUptimeFromStreamStartTime() {
        val result = runtime.evaluate(
            message = message("!discord"),
            commands = listOf(command.copy(response = "Uptime: {uptime}")),
            context = CommandContext(
                streamStartedAt = Instant.parse("2026-06-07T08:45:00Z"),
                now = Instant.parse("2026-06-07T10:10:00Z")
            )
        )

        assertEquals("Uptime: 1h 25m", result.response)
    }

    private fun message(text: String): LiveChatMessage {
        return LiveChatMessage(
            id = "message-1",
            authorChannelId = "viewer-1",
            authorName = "ViewerOne",
            text = text,
            publishedAtIso = "2026-06-07T10:00:00Z"
        )
    }
}
