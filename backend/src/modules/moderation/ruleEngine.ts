import { z } from "zod";

export const moderationProfileSchema = z.object({
  blockedTerms: z.array(z.string()).default([]),
  regexPatterns: z.array(z.string().max(160)).max(20).default([]),
  linkPolicy: z.enum(["allow", "flag", "delete"]).default("flag"),
  allowedDomains: z.array(z.string().max(120)).max(100).default([]),
  blockedDomains: z.array(z.string().max(120)).max(100).default([]),
  capsThreshold: z.number().min(0).max(1).default(0.75),
  maxRepeatedCharacters: z.number().int().min(3).max(20).default(6),
  maxEmojiCount: z.number().int().min(0).max(100).default(8),
  maxMentions: z.number().int().min(0).max(100).default(6),
  maxSymbolCount: z.number().int().min(0).max(200).default(16),
  trustedChannelIds: z.array(z.string().min(1).max(128)).max(500).default([]),
  temporaryTrustedChannels: z.array(z.object({
    channelId: z.string().min(1).max(128),
    expiresAt: z.string().datetime()
  })).max(500).default([]),
  ignoreMembers: z.boolean().default(false),
  raidMode: z.boolean().default(false),
  newChatterBurstThreshold: z.number().int().min(0).max(200).default(6),
  newChatterBurstWindowSeconds: z.number().int().min(5).max(600).default(30),
  firstStreamMinutesOnly: z.number().int().min(1).max(1440).nullable().default(null),
  autoReplyEnabled: z.boolean().default(false),
  autoReplyMessage: z.string().max(200).default("Please keep chat safe and on topic."),
  hideUserOnSevereMatch: z.boolean().default(false)
});

export const chatMessageSchema = z.object({
  id: z.string(),
  authorChannelId: z.string(),
  authorName: z.string(),
  text: z.string(),
  timestamp: z.string(),
  streamStartedAt: z.string().optional(),
  isOwner: z.boolean().optional(),
  isModerator: z.boolean().optional(),
  isMember: z.boolean().optional(),
  isVerified: z.boolean().optional()
});

export type ModerationProfile = z.infer<typeof moderationProfileSchema>;
export type ChatMessage = z.infer<typeof chatMessageSchema>;

export interface ModerationAction {
  type: "allow" | "flagForReview" | "deleteMessage" | "timeoutUser" | "hideUser" | "sendAutoReply";
  reason: string;
  confidence: number;
  text?: string;
}

export interface EvaluationResult {
  matched: boolean;
  actions: ModerationAction[];
}

const linkPattern = /\b(?:https?:\/\/|www\.)\S+/i;
const linkGlobalPattern = /\b(?:https?:\/\/|www\.)\S+/gi;
const mentionPattern = /@[\p{L}\p{N}_-]+/gu;
const emojiPattern = /\p{Extended_Pictographic}/gu;
const defaultProfile: ModerationProfile = moderationProfileSchema.parse({});

export function evaluateMessage(
  message: ChatMessage,
  profile: ModerationProfile = defaultProfile
): EvaluationResult {
  if (
    message.isOwner ||
    message.isModerator ||
    message.isVerified ||
    trustedChannelIds(profile).includes(message.authorChannelId) ||
    isTemporarilyTrusted(message, profile) ||
    (profile.ignoreMembers && message.isMember)
  ) {
    return {
      matched: false,
      actions: [
        {
          type: "allow",
          reason: trustedReason(message, profile),
          confidence: 1
        }
      ]
    };
  }

  if (isOutsideFirstStreamWindow(message, profile)) {
    return {
      matched: false,
      actions: [
        {
          type: "allow",
          reason: "outside_first_stream_window",
          confidence: 1
        }
      ]
    };
  }

  const effectiveProfile = effectiveForRaidMode(profile);
  const actions: ModerationAction[] = [];
  const normalizedText = normalize(message.text);

  for (const term of effectiveProfile.blockedTerms) {
    const normalizedTerm = normalize(term);
    if (normalizedTerm.length > 0 && normalizedText.includes(normalizedTerm)) {
      actions.push({
        type: "deleteMessage",
        reason: `blocked_term:${term}`,
        confidence: 0.96
      });
    }
  }

  effectiveProfile.regexPatterns.forEach((pattern, index) => {
    if (matchesSafeRegex(pattern, message.text)) {
      actions.push({
        type: "deleteMessage",
        reason: `regex_pattern:${index + 1}`,
        confidence: 0.93
      });
    }
  });

  if (linkPattern.test(message.text) && effectiveProfile.linkPolicy !== "allow") {
    actions.push({
      type: effectiveProfile.linkPolicy === "delete" ? "deleteMessage" : "flagForReview",
      reason: "link_policy",
      confidence: effectiveProfile.linkPolicy === "delete" ? 0.9 : 0.75
    });
  }

  for (const domain of linkedDomains(message.text)) {
    if (matchesDomainList(domain, effectiveProfile.blockedDomains)) {
      actions.push({
        type: "deleteMessage",
        reason: `blocked_domain:${domain}`,
        confidence: 0.94
      });
    } else if (
      effectiveProfile.allowedDomains.length > 0 &&
      !matchesDomainList(domain, effectiveProfile.allowedDomains)
    ) {
      actions.push({
        type: effectiveProfile.linkPolicy === "delete" ? "deleteMessage" : "flagForReview",
        reason: `domain_not_allowed:${domain}`,
        confidence: effectiveProfile.linkPolicy === "delete" ? 0.9 : 0.76
      });
    }
  }

  if (capsRatio(message.text) >= effectiveProfile.capsThreshold && lettersOnly(message.text).length >= 8) {
    actions.push({
      type: "flagForReview",
      reason: "excessive_caps",
      confidence: 0.7
    });
  }

  if (hasRepeatedCharacters(message.text, effectiveProfile.maxRepeatedCharacters)) {
    actions.push({
      type: "flagForReview",
      reason: "repeated_characters",
      confidence: 0.68
    });
  }

  if (emojiCount(message.text) > effectiveProfile.maxEmojiCount) {
    actions.push({
      type: "flagForReview",
      reason: "emoji_spam",
      confidence: 0.69
    });
  }

  if (mentionCount(message.text) > effectiveProfile.maxMentions) {
    actions.push({
      type: "flagForReview",
      reason: "mention_spam",
      confidence: 0.72
    });
  }

  if (symbolCount(message.text) > effectiveProfile.maxSymbolCount && symbolRatio(message.text) >= 0.45) {
    actions.push({
      type: "flagForReview",
      reason: "symbol_spam",
      confidence: 0.67
    });
  }

  return {
    matched: actions.length > 0,
    actions: dedupeActions(withConfiguredAutoReply(withConfiguredHideUser(actions, effectiveProfile), effectiveProfile))
  };
}

function withConfiguredHideUser(actions: ModerationAction[], profile: ModerationProfile): ModerationAction[] {
  if (!profile.hideUserOnSevereMatch || actions.some((action) => action.type === "hideUser")) {
    return actions;
  }

  const hasSevereDelete = actions.some((action) => action.type === "deleteMessage" && action.confidence >= 0.9);
  if (!hasSevereDelete) {
    return actions;
  }

  return [
    ...actions,
    {
      type: "hideUser",
      reason: "severe_match_hide_user",
      confidence: 0.9
    }
  ];
}

function withConfiguredAutoReply(actions: ModerationAction[], profile: ModerationProfile): ModerationAction[] {
  const text = safeAutoReplyText(profile.autoReplyMessage);
  if (!profile.autoReplyEnabled || actions.length === 0 || !text) {
    return actions;
  }

  return [
    ...actions,
    {
      type: "sendAutoReply",
      reason: "auto_reply",
      confidence: 0.65,
      text
    }
  ];
}

function safeAutoReplyText(text: string | undefined): string | null {
  const trimmed = text?.trim() ?? "";
  if (!trimmed || trimmed.length > 200 || /[\u0000-\u001F\u007F]/u.test(trimmed)) {
    return null;
  }

  return trimmed;
}

function effectiveForRaidMode(profile: ModerationProfile): ModerationProfile {
  if (!profile.raidMode) {
    return profile;
  }

  return {
    ...profile,
    linkPolicy: profile.linkPolicy === "allow" ? "flag" : profile.linkPolicy,
    capsThreshold: Math.min(profile.capsThreshold, 0.6),
    maxRepeatedCharacters: Math.min(profile.maxRepeatedCharacters, 4),
    maxEmojiCount: Math.min(profile.maxEmojiCount, 5),
    maxMentions: Math.min(profile.maxMentions, 3),
    maxSymbolCount: Math.min(profile.maxSymbolCount, 10),
    newChatterBurstThreshold:
      profile.newChatterBurstThreshold <= 1 ? 3 : Math.min(profile.newChatterBurstThreshold, 3),
    newChatterBurstWindowSeconds: Math.min(profile.newChatterBurstWindowSeconds, 20)
  };
}

function trustedReason(message: ChatMessage, profile: ModerationProfile): string {
  if (trustedChannelIds(profile).includes(message.authorChannelId)) {
    return "trusted_channel";
  }

  if (isTemporarilyTrusted(message, profile)) {
    return "temporary_trusted_channel";
  }

  if (message.isVerified) {
    return "trusted_verified";
  }

  return profile.ignoreMembers && message.isMember ? "trusted_member" : "trusted_author";
}

function trustedChannelIds(profile: ModerationProfile): string[] {
  return profile.trustedChannelIds ?? [];
}

function isTemporarilyTrusted(message: ChatMessage, profile: ModerationProfile, now = new Date()): boolean {
  return (profile.temporaryTrustedChannels ?? []).some((trusted) => {
    const expiresAt = Date.parse(trusted.expiresAt);
    return trusted.channelId === message.authorChannelId && Number.isFinite(expiresAt) && expiresAt > now.getTime();
  });
}

function isOutsideFirstStreamWindow(message: ChatMessage, profile: ModerationProfile): boolean {
  if (!profile.firstStreamMinutesOnly) {
    return false;
  }

  if (!message.streamStartedAt) {
    return true;
  }

  const streamStartedAt = Date.parse(message.streamStartedAt);
  const messageTimestamp = Date.parse(message.timestamp);
  if (!Number.isFinite(streamStartedAt) || !Number.isFinite(messageTimestamp)) {
    return true;
  }

  const elapsedMinutes = Math.floor((messageTimestamp - streamStartedAt) / 60_000);
  return elapsedMinutes >= profile.firstStreamMinutesOnly;
}

function normalize(value: string): string {
  return value.trim().toLowerCase().replace(/\s+/g, " ");
}

function lettersOnly(value: string): string {
  return value.replace(/[^a-z]/gi, "");
}

function capsRatio(value: string): number {
  const letters = lettersOnly(value);
  if (letters.length === 0) {
    return 0;
  }

  const uppercaseCount = [...letters].filter((character) => character === character.toUpperCase()).length;
  return uppercaseCount / letters.length;
}

function hasRepeatedCharacters(value: string, maxRepeatedCharacters: number): boolean {
  const pattern = new RegExp(`(.)\\1{${maxRepeatedCharacters},}`, "i");
  return pattern.test(value);
}

function matchesSafeRegex(pattern: string, value: string): boolean {
  if (pattern.trim().length === 0 || pattern.length > 160) {
    return false;
  }

  try {
    return new RegExp(pattern, "i").test(value);
  } catch {
    return false;
  }
}

function linkedDomains(value: string): string[] {
  const matches = value.match(linkGlobalPattern) ?? [];
  return matches.map(extractDomain).filter((domain): domain is string => domain !== null);
}

function extractDomain(value: string): string | null {
  const withScheme = value.toLowerCase().startsWith("www.") ? `https://${value}` : value;
  try {
    return normalizeDomain(new URL(withScheme).hostname);
  } catch {
    return null;
  }
}

function normalizeDomain(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/^https?:\/\//, "")
    .replace(/^www\./, "")
    .split("/")[0]
    .split(":")[0];
}

function matchesDomainList(domain: string, domains: string[]): boolean {
  return domains
    .map(normalizeDomain)
    .filter((value) => value.length > 0)
    .some((allowedDomain) => domain === allowedDomain || domain.endsWith(`.${allowedDomain}`));
}

function emojiCount(value: string): number {
  return value.match(emojiPattern)?.length ?? 0;
}

function mentionCount(value: string): number {
  return value.match(mentionPattern)?.length ?? 0;
}

function symbolCount(value: string): number {
  return [...value].filter((character) => !/\p{L}|\p{N}|\s/u.test(character)).length;
}

function symbolRatio(value: string): number {
  if (value.trim().length === 0) {
    return 0;
  }

  return symbolCount(value) / [...value].length;
}

function dedupeActions(actions: ModerationAction[]): ModerationAction[] {
  const seen = new Set<string>();
  return actions.filter((action) => {
    const key = `${action.type}:${action.reason}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}
