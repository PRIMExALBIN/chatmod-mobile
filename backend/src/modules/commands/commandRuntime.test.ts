import { describe, expect, it } from "vitest";
import { cooldownKeys, evaluateCommand, parseCommandMessage, type ChatCommandMessage } from "./commandRuntime.js";
import type { CommandRecord } from "./commandStore.js";

const baseCommand: CommandRecord = {
  id: "command-1",
  profileId: "profile-1",
  name: "!discord",
  response: "Join {username}: https://example.com {args}",
  aliases: ["!community"],
  cooldownSeconds: 30,
  accessLevel: "everyone",
  enabled: true,
  createdAt: new Date(0).toISOString(),
  updatedAt: new Date(0).toISOString()
};

const viewer: ChatCommandMessage = {
  authorChannelId: "viewer-1",
  authorName: "ViewerOne",
  text: "!discord"
};

describe("parseCommandMessage", () => {
  it("parses trigger and args", () => {
    expect(parseCommandMessage(" !Discord one two ")).toEqual({
      trigger: "!discord",
      args: ["one", "two"],
      argText: "one two"
    });
  });

  it("ignores normal chat", () => {
    expect(parseCommandMessage("hello chat")).toBeNull();
  });
});

describe("evaluateCommand", () => {
  it("matches primary command and renders variables", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord please" },
      [baseCommand],
      {},
      { now: new Date("2026-06-07T10:00:00.000Z") }
    );

    expect(result).toMatchObject({
      matched: true,
      commandId: "command-1",
      trigger: "!discord",
      reason: "matched",
      response: "Join ViewerOne: https://example.com please"
    });
  });

  it("matches aliases", () => {
    const result = evaluateCommand({ ...viewer, text: "!community" }, [baseCommand]);

    expect(result.matched).toBe(true);
    expect(result.commandId).toBe("command-1");
  });

  it("blocks disabled commands", () => {
    const result = evaluateCommand({ ...viewer, text: "!discord" }, [{ ...baseCommand, enabled: false }]);

    expect(result).toMatchObject({
      matched: false,
      reason: "disabled"
    });
  });

  it("blocks commands above viewer access", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord" },
      [{ ...baseCommand, accessLevel: "mods" }]
    );

    expect(result).toMatchObject({
      matched: false,
      reason: "access_denied"
    });
  });

  it("allows owner-only command for channel owner", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord", isOwner: true },
      [{ ...baseCommand, accessLevel: "owner" }]
    );

    expect(result.matched).toBe(true);
  });

  it("allows member-only command for members", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord", isMember: true },
      [{ ...baseCommand, accessLevel: "members" }]
    );

    expect(result.matched).toBe(true);
  });

  it("renders stream, time, and random variables", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord" },
      [
        {
          ...baseCommand,
          response: "{streamTitle} {time} {random}"
        }
      ],
      {},
      {
        streamTitle: "Build stream",
        now: new Date("2026-06-07T10:00:00.000Z"),
        random: () => 0.123
      }
    );

    expect(result.response).toBe("Build stream 2026-06-07T10:00:00.000Z 123");
  });

  it("renders uptime from stream start time", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord" },
      [
        {
          ...baseCommand,
          response: "Uptime: {uptime}"
        }
      ],
      {},
      {
        streamStartedAt: new Date("2026-06-07T08:45:00.000Z"),
        now: new Date("2026-06-07T10:10:00.000Z")
      }
    );

    expect(result.response).toBe("Uptime: 1h 25m");
  });

  it("returns cooldown keys for global and per-user tracking", () => {
    expect(cooldownKeys(viewer, baseCommand)).toEqual({
      commandKey: "command-1",
      userCommandKey: "viewer-1:command-1"
    });
  });

  it("enforces global command cooldown", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord" },
      [baseCommand],
      {
        commandLastUsedAt: {
          "command-1": "2026-06-07T10:00:00.000Z"
        }
      },
      { now: new Date("2026-06-07T10:00:10.000Z") }
    );

    expect(result).toMatchObject({
      matched: false,
      reason: "cooldown",
      cooldownRemainingSeconds: 20
    });
  });

  it("enforces per-user command cooldown", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord" },
      [baseCommand],
      {
        userCommandLastUsedAt: {
          "viewer-1:command-1": "2026-06-07T10:00:00.000Z"
        }
      },
      { now: new Date("2026-06-07T10:00:05.000Z") }
    );

    expect(result).toMatchObject({
      matched: false,
      reason: "cooldown",
      cooldownRemainingSeconds: 25
    });
  });

  it("uses the latest cooldown when global and per-user cooldowns both exist", () => {
    const result = evaluateCommand(
      { ...viewer, text: "!discord" },
      [baseCommand],
      {
        commandLastUsedAt: {
          "command-1": "2026-06-07T10:00:00.000Z"
        },
        userCommandLastUsedAt: {
          "viewer-1:command-1": "2026-06-07T10:00:20.000Z"
        }
      },
      { now: new Date("2026-06-07T10:00:25.000Z") }
    );

    expect(result).toMatchObject({
      matched: false,
      reason: "cooldown",
      cooldownRemainingSeconds: 25
    });
  });
});
