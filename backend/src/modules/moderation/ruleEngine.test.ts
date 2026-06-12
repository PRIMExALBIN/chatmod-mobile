import { describe, expect, it } from "vitest";
import { evaluateMessage, type ChatMessage, type ModerationProfile } from "./ruleEngine.js";

const profile: ModerationProfile = {
  blockedTerms: ["scam", "cheap views"],
  regexPatterns: [],
  linkPolicy: "delete",
  allowedDomains: [],
  blockedDomains: [],
  capsThreshold: 0.75,
  maxRepeatedCharacters: 5,
  maxEmojiCount: 8,
  maxMentions: 6,
  maxSymbolCount: 16,
  trustedChannelIds: [],
  temporaryTrustedChannels: [],
  ignoreMembers: false,
  raidMode: false,
  newChatterBurstThreshold: 6,
  newChatterBurstWindowSeconds: 30,
  firstStreamMinutesOnly: null,
  autoReplyEnabled: false,
  autoReplyMessage: "Please keep chat safe and on topic.",
  hideUserOnSevereMatch: false
};

function message(text: string, overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: "message-1",
    authorChannelId: "viewer-1",
    authorName: "Viewer",
    text,
    timestamp: new Date().toISOString(),
    ...overrides
  };
}

describe("evaluateMessage", () => {
  it("deletes messages with blocked terms", () => {
    const result = evaluateMessage(message("This is a scam"), profile);

    expect(result.matched).toBe(true);
    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "deleteMessage",
        reason: "blocked_term:scam"
      })
    );
  });

  it("applies the configured link policy", () => {
    const result = evaluateMessage(message("check https://example.com"), profile);

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "deleteMessage",
        reason: "link_policy"
      })
    );
  });

  it("flags excessive caps without deleting by default", () => {
    const result = evaluateMessage(message("THIS IS TOO LOUD"));

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "flagForReview",
        reason: "excessive_caps"
      })
    );
  });

  it("allows channel owners and moderators", () => {
    const result = evaluateMessage(message("scam link", { isModerator: true }), profile);

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "trusted_author",
        confidence: 1
      }
    ]);
  });

  it("deletes messages matching custom regex patterns", () => {
    const result = evaluateMessage(
      message("free nitro giveaway"),
      {
        ...profile,
        regexPatterns: ["free\\s+nitro"]
      }
    );

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "deleteMessage",
        reason: "regex_pattern:1"
      })
    );
  });

  it("ignores invalid regex patterns", () => {
    const result = evaluateMessage(
      message("free nitro giveaway"),
      {
        ...profile,
        regexPatterns: ["["]
      }
    );

    expect(result.matched).toBe(false);
  });

  it("deletes explicitly blocked domains", () => {
    const result = evaluateMessage(
      message("check https://bad.example/path"),
      {
        ...profile,
        linkPolicy: "allow",
        blockedDomains: ["example"]
      }
    );

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "deleteMessage",
        reason: "blocked_domain:bad.example"
      })
    );
  });

  it("flags links outside the allowlist", () => {
    const result = evaluateMessage(
      message("watch www.notallowed.test"),
      {
        ...profile,
        linkPolicy: "flag",
        allowedDomains: ["youtube.com"]
      }
    );

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "flagForReview",
        reason: "domain_not_allowed:notallowed.test"
      })
    );
  });

  it("flags emoji and mention spam", () => {
    const result = evaluateMessage(
      message("@a @b @c 😀😀😀"),
      {
        ...profile,
        maxEmojiCount: 2,
        maxMentions: 2
      }
    );

    expect(result.actions).toContainEqual(expect.objectContaining({ reason: "emoji_spam" }));
    expect(result.actions).toContainEqual(expect.objectContaining({ reason: "mention_spam" }));
  });

  it("flags symbol spam", () => {
    const result = evaluateMessage(
      message("%%%% $$$$ #### !!!! @@@@"),
      {
        ...profile,
        maxSymbolCount: 8
      }
    );

    expect(result.actions).toContainEqual(expect.objectContaining({ reason: "symbol_spam" }));
  });

  it("applies raid mode stricter thresholds", () => {
    const result = evaluateMessage(
      message("THIS IS LOUD"),
      {
        ...profile,
        capsThreshold: 0.9,
        raidMode: true
      }
    );

    expect(result.actions).toContainEqual(expect.objectContaining({ reason: "excessive_caps" }));
  });

  it("can trust channel members when configured", () => {
    const result = evaluateMessage(
      message("this is a scam", { isMember: true }),
      {
        ...profile,
        ignoreMembers: true
      }
    );

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "trusted_member",
        confidence: 1
      }
    ]);
  });

  it("allows verified YouTube authors before applying filters", () => {
    const result = evaluateMessage(
      message("this is a scam", { isVerified: true }),
      profile
    );

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "trusted_verified",
        confidence: 1
      }
    ]);
  });

  it("allows whitelisted channel IDs before applying filters", () => {
    const result = evaluateMessage(
      message("this is a scam", { authorChannelId: "trusted-viewer" }),
      {
        ...profile,
        trustedChannelIds: ["trusted-viewer"]
      }
    );

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "trusted_channel",
        confidence: 1
      }
    ]);
  });

  it("allows unexpired temporary trusted channel IDs before applying filters", () => {
    const result = evaluateMessage(
      message("this is a scam", { authorChannelId: "temp-viewer" }),
      {
        ...profile,
        temporaryTrustedChannels: [
          {
            channelId: "temp-viewer",
            expiresAt: new Date(Date.now() + 60_000).toISOString()
          }
        ]
      }
    );

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "temporary_trusted_channel",
        confidence: 1
      }
    ]);
  });

  it("ignores expired temporary trusted channel IDs", () => {
    const result = evaluateMessage(
      message("this is a scam", { authorChannelId: "temp-viewer" }),
      {
        ...profile,
        temporaryTrustedChannels: [
          {
            channelId: "temp-viewer",
            expiresAt: new Date(Date.now() - 60_000).toISOString()
          }
        ]
      }
    );

    expect(result.matched).toBe(true);
    expect(result.actions).toContainEqual(
      expect.objectContaining({
        reason: "blocked_term:scam"
      })
    );
  });

  it("adds a configured auto-reply action when moderation rules match", () => {
    const result = evaluateMessage(
      message("this is a scam"),
      {
        ...profile,
        autoReplyEnabled: true,
        autoReplyMessage: "Please keep chat clean."
      }
    );

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "sendAutoReply",
        reason: "auto_reply",
        text: "Please keep chat clean."
      })
    );
  });

  it("does not add an auto-reply for trusted users", () => {
    const result = evaluateMessage(
      message("this is a scam", { isModerator: true }),
      {
        ...profile,
        autoReplyEnabled: true
      }
    );

    expect(result.matched).toBe(false);
    expect(result.actions).not.toContainEqual(
      expect.objectContaining({
        type: "sendAutoReply"
      })
    );
  });

  it("adds an opt-in hide-user action for severe rule matches", () => {
    const result = evaluateMessage(
      message("this is a scam"),
      {
        ...profile,
        hideUserOnSevereMatch: true
      }
    );

    expect(result.actions).toContainEqual(
      expect.objectContaining({
        type: "hideUser",
        reason: "severe_match_hide_user"
      })
    );
  });

  it("does not hide trusted users even when severe-match hiding is enabled", () => {
    const result = evaluateMessage(
      message("this is a scam", { isVerified: true }),
      {
        ...profile,
        hideUserOnSevereMatch: true
      }
    );

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "trusted_verified",
        confidence: 1
      }
    ]);
  });

  it("applies rules during the configured first stream minutes", () => {
    const result = evaluateMessage(
      message("this is a scam", {
        timestamp: "2026-06-07T18:05:00.000Z",
        streamStartedAt: "2026-06-07T18:00:00.000Z"
      }),
      {
        ...profile,
        firstStreamMinutesOnly: 10
      }
    );

    expect(result.matched).toBe(true);
    expect(result.actions).toContainEqual(
      expect.objectContaining({
        reason: "blocked_term:scam"
      })
    );
  });

  it("skips rules outside the configured first stream minutes", () => {
    const result = evaluateMessage(
      message("this is a scam", {
        timestamp: "2026-06-07T18:11:00.000Z",
        streamStartedAt: "2026-06-07T18:00:00.000Z"
      }),
      {
        ...profile,
        firstStreamMinutesOnly: 10
      }
    );

    expect(result.matched).toBe(false);
    expect(result.actions).toEqual([
      {
        type: "allow",
        reason: "outside_first_stream_window",
        confidence: 1
      }
    ]);
  });
});
