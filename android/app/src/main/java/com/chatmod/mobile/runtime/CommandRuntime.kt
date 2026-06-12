package com.chatmod.mobile.runtime

import com.chatmod.mobile.youtube.LiveChatMessage
import java.time.Instant

data class BotCommand(
    val id: String,
    val name: String,
    val response: String,
    val aliases: List<String>,
    val cooldownSeconds: Int,
    val accessLevel: CommandAccessLevel,
    val enabled: Boolean
)

enum class CommandAccessLevel {
    Everyone,
    Members,
    Mods,
    Owner
}

data class ParsedCommand(
    val trigger: String,
    val args: List<String>,
    val argText: String
)

data class CommandCooldownState(
    val commandLastUsedAt: Map<String, Long> = emptyMap(),
    val userCommandLastUsedAt: Map<String, Long> = emptyMap()
) {
    fun recordUsage(commandId: String, authorChannelId: String, usedAtMillis: Long): CommandCooldownState {
        val userCommandKey = "$authorChannelId:$commandId"
        return copy(
            commandLastUsedAt = commandLastUsedAt + (commandId to usedAtMillis),
            userCommandLastUsedAt = userCommandLastUsedAt + (userCommandKey to usedAtMillis)
        )
    }
}

data class CommandContext(
    val streamTitle: String = "",
    val streamStartedAt: Instant? = null,
    val now: Instant = Instant.now(),
    val random: () -> Int = { (0..999).random() }
)

data class CommandResult(
    val matched: Boolean,
    val commandId: String?,
    val trigger: String?,
    val response: String?,
    val reason: CommandResultReason,
    val cooldownRemainingSeconds: Int
)

enum class CommandResultReason {
    NotCommand,
    NotFound,
    Disabled,
    AccessDenied,
    Cooldown,
    Matched
}

class CommandRuntime {
    fun parse(text: String): ParsedCommand? {
        val match = Regex("""^!([a-z0-9_-]{1,32})(?:\s+(.*))?$""", RegexOption.IGNORE_CASE)
            .find(text.trim())
            ?: return null
        val argText = match.groupValues.getOrNull(2)?.trim().orEmpty()

        return ParsedCommand(
            trigger = "!${match.groupValues[1].lowercase()}",
            args = if (argText.isBlank()) emptyList() else argText.split(Regex("""\s+""")),
            argText = argText
        )
    }

    fun evaluate(
        message: LiveChatMessage,
        commands: List<BotCommand>,
        cooldownState: CommandCooldownState = CommandCooldownState(),
        context: CommandContext = CommandContext()
    ): CommandResult {
        val parsed = parse(message.text) ?: return noMatch(CommandResultReason.NotCommand)
        val command = commands.firstOrNull { it.matches(parsed.trigger) }
            ?: return noMatch(CommandResultReason.NotFound, parsed.trigger)

        if (!command.enabled) {
            return CommandResult(false, command.id, parsed.trigger, null, CommandResultReason.Disabled, 0)
        }

        if (!message.canUse(command.accessLevel)) {
            return CommandResult(false, command.id, parsed.trigger, null, CommandResultReason.AccessDenied, 0)
        }

        val cooldownRemaining = cooldownRemainingSeconds(message, command, cooldownState, context.now)
        if (cooldownRemaining > 0) {
            return CommandResult(false, command.id, parsed.trigger, null, CommandResultReason.Cooldown, cooldownRemaining)
        }

        return CommandResult(
            matched = true,
            commandId = command.id,
            trigger = parsed.trigger,
            response = render(command.response, message, parsed, context),
            reason = CommandResultReason.Matched,
            cooldownRemainingSeconds = 0
        )
    }

    private fun noMatch(reason: CommandResultReason, trigger: String? = null): CommandResult {
        return CommandResult(
            matched = false,
            commandId = null,
            trigger = trigger,
            response = null,
            reason = reason,
            cooldownRemainingSeconds = 0
        )
    }

    private fun BotCommand.matches(trigger: String): Boolean {
        return name.lowercase() == trigger || aliases.any { it.lowercase() == trigger }
    }

    private fun LiveChatMessage.canUse(accessLevel: CommandAccessLevel): Boolean {
        return when (accessLevel) {
            CommandAccessLevel.Everyone -> true
            CommandAccessLevel.Members -> isOwner || isModerator || isMember
            CommandAccessLevel.Mods -> isOwner || isModerator
            CommandAccessLevel.Owner -> isOwner
        }
    }

    private fun cooldownRemainingSeconds(
        message: LiveChatMessage,
        command: BotCommand,
        cooldownState: CommandCooldownState,
        now: Instant
    ): Int {
        if (command.cooldownSeconds <= 0) return 0

        val userCommandKey = "${message.authorChannelId}:${command.id}"
        val lastUsedAt = listOfNotNull(
            cooldownState.commandLastUsedAt[command.id],
            cooldownState.userCommandLastUsedAt[userCommandKey]
        ).maxOrNull() ?: return 0

        val elapsedSeconds = ((now.toEpochMilli() - lastUsedAt) / 1000L).toInt()
        return (command.cooldownSeconds - elapsedSeconds).coerceAtLeast(0)
    }

    private fun render(
        template: String,
        message: LiveChatMessage,
        parsed: ParsedCommand,
        context: CommandContext
    ): String {
        return template
            .replace("{username}", message.authorName)
            .replace("{args}", parsed.argText)
            .replace("{streamTitle}", context.streamTitle)
            .replace("{uptime}", formatUptime(context.streamStartedAt, context.now))
            .replace("{time}", context.now.toString())
            .replace("{random}", context.random().toString())
    }

    private fun formatUptime(streamStartedAt: Instant?, now: Instant): String {
        if (streamStartedAt == null) {
            return "not available yet"
        }

        val totalMinutes = ((now.toEpochMilli() - streamStartedAt.toEpochMilli()) / 60_000L).coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
