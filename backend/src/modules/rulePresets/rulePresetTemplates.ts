import type { ModerationProfile } from "../moderation/ruleEngine.js";
import { moderationProfileSchema } from "../moderation/ruleEngine.js";

export interface RulePresetTemplate {
  id: string;
  name: string;
  description: string;
  config: ModerationProfile;
}

export const rulePresetTemplates: RulePresetTemplate[] = [
  template(
    "family-friendly",
    "Family friendly",
    "Balanced protection for younger audiences, schools, and brand-safe streams.",
    {
      blockedTerms: ["cheap views", "free subs", "sub4sub", "giveaway scam"],
      linkPolicy: "flag",
      capsThreshold: 0.72,
      maxRepeatedCharacters: 5
    }
  ),
  template(
    "gaming-default",
    "Gaming default",
    "Fast chat defaults for gameplay streams with room for hype and emotes.",
    {
      blockedTerms: ["cheap views", "free skins", "rank boost", "sub4sub"],
      linkPolicy: "flag",
      capsThreshold: 0.78,
      maxRepeatedCharacters: 6
    }
  ),
  template(
    "education-qa",
    "Education/Q&A",
    "Tighter signal-to-noise for lessons, workshops, and question-heavy streams.",
    {
      blockedTerms: ["cheap views", "free subs", "sub4sub"],
      linkPolicy: "flag",
      capsThreshold: 0.68,
      maxRepeatedCharacters: 4
    }
  ),
  template(
    "music-performance",
    "Music/live performance",
    "Permissive enough for applause and lyrics chat while still catching spam.",
    {
      blockedTerms: ["cheap views", "free subs", "sub4sub"],
      linkPolicy: "flag",
      capsThreshold: 0.82,
      maxRepeatedCharacters: 8
    }
  ),
  template(
    "high-security-raid-mode",
    "High-security raid mode",
    "Emergency posture for raids, bot bursts, and aggressive link spam.",
    {
      blockedTerms: ["cheap views", "free subs", "sub4sub", "spam raid"],
      linkPolicy: "delete",
      blockedDomains: ["grabify.link", "iplogger.org"],
      capsThreshold: 0.6,
      maxRepeatedCharacters: 3,
      maxEmojiCount: 5,
      maxMentions: 3,
      maxSymbolCount: 10,
      raidMode: true,
      newChatterBurstThreshold: 3,
      newChatterBurstWindowSeconds: 20
    }
  )
];

function template(
  id: string,
  name: string,
  description: string,
  config: Partial<ModerationProfile>
): RulePresetTemplate {
  return {
    id,
    name,
    description,
    config: moderationProfileSchema.parse(config)
  };
}
