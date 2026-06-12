import { describe, expect, it } from "vitest";
import type { AuthContext } from "../auth/sessionToken.js";
import type { EntitlementSnapshot } from "../entitlements/entitlementPlans.js";
import { snapshotForPlan } from "../entitlements/entitlementPlans.js";
import type { EntitlementStore, EntitlementUpdate } from "../entitlements/entitlementStore.js";
import {
  advancedModerationFilterFields,
  assertAdvancedModerationFiltersAllowed
} from "./moderationFeatureGate.js";
import { moderationProfileSchema } from "./ruleEngine.js";

const auth: AuthContext = {
  subject: "moderation-feature-device",
  deviceId: "moderation-feature-device",
  installId: "moderation-feature-install"
};

describe("moderationFeatureGate", () => {
  it("treats blocked terms, link policy, caps, and repeats as starter-safe filters", () => {
    const profile = moderationProfileSchema.parse({
      blockedTerms: ["spam"],
      linkPolicy: "delete",
      capsThreshold: 0.68,
      maxRepeatedCharacters: 4
    });

    expect(advancedModerationFilterFields(profile)).toEqual([]);
  });

  it("detects advanced moderation filters", () => {
    const profile = moderationProfileSchema.parse({
      regexPatterns: ["free\\s+nitro"],
      blockedDomains: ["grabify.link"],
      maxEmojiCount: 4,
      ignoreMembers: true,
      raidMode: true
    });

    expect(advancedModerationFilterFields(profile)).toEqual([
      "regexPatterns",
      "blockedDomains",
      "maxEmojiCount",
      "ignoreMembers",
      "raidMode"
    ]);
  });

  it("rejects advanced filters for starter plans", async () => {
    const profile = moderationProfileSchema.parse({
      allowedDomains: ["youtube.com"]
    });

    await expect(assertAdvancedModerationFiltersAllowed({
      auth,
      entitlementStore: fakeEntitlementStore(snapshotForPlan({
        plan: "starter",
        status: "trialing",
        source: "test"
      })),
      profile
    })).rejects.toMatchObject({
      statusCode: 403,
      message: "Advanced moderation filters require Pro or Creator plan: allowedDomains."
    });
  });

  it("allows advanced filters for pro plans", async () => {
    const profile = moderationProfileSchema.parse({
      regexPatterns: ["free\\s+nitro"]
    });

    await expect(assertAdvancedModerationFiltersAllowed({
      auth,
      entitlementStore: fakeEntitlementStore(snapshotForPlan({
        plan: "pro",
        status: "active",
        source: "test",
        productId: "chatmod_pro_monthly",
        currentPeriodEndsAt: new Date("2026-07-08T10:00:00.000Z")
      })),
      profile
    })).resolves.toBeUndefined();
  });
});

function fakeEntitlementStore(snapshot: EntitlementSnapshot): EntitlementStore {
  return {
    async current() {
      return snapshot;
    },
    async upsert(_auth: AuthContext, _update: EntitlementUpdate) {
      return snapshot;
    }
  };
}
