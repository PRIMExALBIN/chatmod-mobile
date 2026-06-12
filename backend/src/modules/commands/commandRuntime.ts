import type { CommandRecord } from "./commandStore.js";

export interface ChatCommandMessage {
  authorChannelId: string;
  authorName: string;
  text: string;
  isOwner?: boolean;
  isModerator?: boolean;
  isMember?: boolean;
}

export interface CommandRuntimeContext {
  streamTitle?: string;
  streamStartedAt?: Date;
  now?: Date;
  random?: () => number;
}

export interface CommandCooldownState {
  commandLastUsedAt?: Record<string, string>;
  userCommandLastUsedAt?: Record<string, string>;
}

export interface ParsedCommand {
  trigger: string;
  args: string[];
  argText: string;
}

export interface CommandEvaluation {
  matched: boolean;
  commandId: string | null;
  trigger: string | null;
  response: string | null;
  reason: "not_command" | "not_found" | "disabled" | "access_denied" | "cooldown" | "matched";
  cooldownRemainingSeconds: number;
}

const commandPattern = /^!([a-z0-9_-]{1,32})(?:\s+(.*))?$/i;

export function parseCommandMessage(text: string): ParsedCommand | null {
  const match = text.trim().match(commandPattern);
  if (!match) {
    return null;
  }

  const argText = match[2]?.trim() ?? "";

  return {
    trigger: `!${match[1].toLowerCase()}`,
    args: argText.length > 0 ? argText.split(/\s+/g) : [],
    argText
  };
}

export function evaluateCommand(
  message: ChatCommandMessage,
  commands: CommandRecord[],
  cooldownState: CommandCooldownState = {},
  context: CommandRuntimeContext = {}
): CommandEvaluation {
  const parsed = parseCommandMessage(message.text);
  if (!parsed) {
    return noMatch("not_command");
  }

  const command = commands.find((candidate) => commandMatches(candidate, parsed.trigger));
  if (!command) {
    return noMatch("not_found", parsed.trigger);
  }

  if (!command.enabled) {
    return {
      matched: false,
      commandId: command.id,
      trigger: parsed.trigger,
      response: null,
      reason: "disabled",
      cooldownRemainingSeconds: 0
    };
  }

  if (!canUseCommand(message, command.accessLevel)) {
    return {
      matched: false,
      commandId: command.id,
      trigger: parsed.trigger,
      response: null,
      reason: "access_denied",
      cooldownRemainingSeconds: 0
    };
  }

  const cooldownRemainingSeconds = remainingCooldownSeconds(message, command, cooldownState, context.now ?? new Date());
  if (cooldownRemainingSeconds > 0) {
    return {
      matched: false,
      commandId: command.id,
      trigger: parsed.trigger,
      response: null,
      reason: "cooldown",
      cooldownRemainingSeconds
    };
  }

  return {
    matched: true,
    commandId: command.id,
    trigger: parsed.trigger,
    response: renderCommandResponse(command.response, message, parsed, context),
    reason: "matched",
    cooldownRemainingSeconds: 0
  };
}

export function cooldownKeys(message: ChatCommandMessage, command: CommandRecord): {
  commandKey: string;
  userCommandKey: string;
} {
  return {
    commandKey: command.id,
    userCommandKey: `${message.authorChannelId}:${command.id}`
  };
}

function noMatch(reason: CommandEvaluation["reason"], trigger: string | null = null): CommandEvaluation {
  return {
    matched: false,
    commandId: null,
    trigger,
    response: null,
    reason,
    cooldownRemainingSeconds: 0
  };
}

function commandMatches(command: CommandRecord, trigger: string): boolean {
  return command.name.toLowerCase() === trigger || command.aliases.some((alias) => alias.toLowerCase() === trigger);
}

function canUseCommand(message: ChatCommandMessage, accessLevel: CommandRecord["accessLevel"]): boolean {
  if (accessLevel === "everyone") {
    return true;
  }
  if (accessLevel === "members") {
    return Boolean(message.isMember || message.isModerator || message.isOwner);
  }
  if (accessLevel === "mods") {
    return Boolean(message.isModerator || message.isOwner);
  }
  if (accessLevel === "owner") {
    return Boolean(message.isOwner);
  }

  return false;
}

function remainingCooldownSeconds(
  message: ChatCommandMessage,
  command: CommandRecord,
  cooldownState: CommandCooldownState,
  now: Date
): number {
  if (command.cooldownSeconds <= 0) {
    return 0;
  }

  const keys = cooldownKeys(message, command);
  const lastUsedAt = [
    cooldownState.commandLastUsedAt?.[keys.commandKey],
    cooldownState.userCommandLastUsedAt?.[keys.userCommandKey]
  ]
    .filter(Boolean)
    .map((value) => new Date(value!))
    .filter((value) => !Number.isNaN(value.getTime()))
    .sort((a, b) => b.getTime() - a.getTime())[0];

  if (!lastUsedAt) {
    return 0;
  }

  const elapsedSeconds = Math.floor((now.getTime() - lastUsedAt.getTime()) / 1000);
  return Math.max(0, command.cooldownSeconds - elapsedSeconds);
}

function renderCommandResponse(
  template: string,
  message: ChatCommandMessage,
  parsed: ParsedCommand,
  context: CommandRuntimeContext
): string {
  const now = context.now ?? new Date();
  const random = Math.floor((context.random?.() ?? Math.random()) * 1000).toString();

  return template
    .replaceAll("{username}", message.authorName)
    .replaceAll("{args}", parsed.argText)
    .replaceAll("{streamTitle}", context.streamTitle ?? "")
    .replaceAll("{uptime}", formatUptime(context.streamStartedAt, now))
    .replaceAll("{time}", now.toISOString())
    .replaceAll("{random}", random);
}

function formatUptime(streamStartedAt: Date | undefined, now: Date): string {
  if (!streamStartedAt || Number.isNaN(streamStartedAt.getTime())) {
    return "not available yet";
  }

  const totalMinutes = Math.max(0, Math.floor((now.getTime() - streamStartedAt.getTime()) / 60000));
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours > 0) {
    return `${hours}h ${minutes}m`;
  }
  return `${minutes}m`;
}
