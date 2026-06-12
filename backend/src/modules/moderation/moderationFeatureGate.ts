import { HttpError } from "../../lib/httpErrors.js";
import type { AuthContext } from "../auth/sessionToken.js";
import type { EntitlementStore } from "../entitlements/entitlementStore.js";
import { moderationProfileSchema, type ModerationProfile } from "./ruleEngine.js";

const defaultProfile = moderationProfileSchema.parse({});

export function advancedModerationFilterFields(profile: ModerationProfile): string[] {
  const fields: string[] = [];

  if (profile.regexPatterns.length > 0) {
    fields.push("regexPatterns");
  }
  if (profile.allowedDomains.length > 0) {
    fields.push("allowedDomains");
  }
  if (profile.blockedDomains.length > 0) {
    fields.push("blockedDomains");
  }
  if (profile.maxEmojiCount !== defaultProfile.maxEmojiCount) {
    fields.push("maxEmojiCount");
  }
  if (profile.maxMentions !== defaultProfile.maxMentions) {
    fields.push("maxMentions");
  }
  if (profile.maxSymbolCount !== defaultProfile.maxSymbolCount) {
    fields.push("maxSymbolCount");
  }
  if (profile.ignoreMembers) {
    fields.push("ignoreMembers");
  }
  if (profile.raidMode) {
    fields.push("raidMode");
  }
  if (profile.newChatterBurstThreshold !== defaultProfile.newChatterBurstThreshold) {
    fields.push("newChatterBurstThreshold");
  }
  if (profile.newChatterBurstWindowSeconds !== defaultProfile.newChatterBurstWindowSeconds) {
    fields.push("newChatterBurstWindowSeconds");
  }

  return fields;
}

export async function assertAdvancedModerationFiltersAllowed(input: {
  auth: AuthContext;
  entitlementStore: EntitlementStore;
  profile: ModerationProfile;
}): Promise<void> {
  const fields = advancedModerationFilterFields(input.profile);
  if (fields.length === 0) {
    return;
  }

  const entitlement = await input.entitlementStore.current(input.auth);
  if (entitlement.features.advancedFilters) {
    return;
  }

  throw new HttpError(
    403,
    `Advanced moderation filters require Pro or Creator plan: ${fields.join(", ")}.`
  );
}
