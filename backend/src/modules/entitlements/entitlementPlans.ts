import type { SubscriptionStatus } from "@prisma/client";

export type EntitlementPlan = "starter" | "pro" | "creator";
export type EntitlementFeatureLimit = number | null;

export interface EntitlementSnapshot {
  plan: EntitlementPlan;
  status: Lowercase<SubscriptionStatus> | "none";
  source: string;
  productId: string | null;
  currentPeriodEndsAt: string | null;
  features: {
    customBotName: boolean;
    channelProfiles: number;
    commandProfiles: EntitlementFeatureLimit;
    timedMessages: EntitlementFeatureLimit;
    localHistoryLimit: number;
    cloudBackups: boolean;
    teamSeats: number;
    emergencyMode: boolean;
    advancedFilters: boolean;
    presetBundles: boolean;
    discordAlerts: boolean;
    obsOverlay: boolean;
    aiSuggestions: boolean;
    aiSuggestionDailyLimit: number;
  };
}

export function planForProduct(productId: string | null | undefined): EntitlementPlan {
  if (!productId) {
    return "starter";
  }

  const normalized = productId.toLowerCase();
  if (normalized.includes("creator")) {
    return "creator";
  }
  if (normalized.includes("pro")) {
    return "pro";
  }

  return "starter";
}

export function snapshotForPlan(input: {
  plan: EntitlementPlan;
  status: EntitlementSnapshot["status"];
  source: string;
  productId?: string | null;
  currentPeriodEndsAt?: Date | string | null;
  now?: Date;
}): EntitlementSnapshot {
  const featureMap = {
    starter: {
      customBotName: true,
      channelProfiles: 1,
      commandProfiles: 3,
      timedMessages: 5,
      localHistoryLimit: 120,
      cloudBackups: true,
      teamSeats: 1,
      emergencyMode: false,
      advancedFilters: false,
      presetBundles: false,
      discordAlerts: false,
      obsOverlay: false,
      aiSuggestions: false,
      aiSuggestionDailyLimit: 0
    },
    pro: {
      customBotName: true,
      channelProfiles: 1,
      commandProfiles: null,
      timedMessages: null,
      localHistoryLimit: 1000,
      cloudBackups: true,
      teamSeats: 2,
      emergencyMode: true,
      advancedFilters: true,
      presetBundles: true,
      discordAlerts: true,
      obsOverlay: true,
      aiSuggestions: false,
      aiSuggestionDailyLimit: 0
    },
    creator: {
      customBotName: true,
      channelProfiles: 5,
      commandProfiles: null,
      timedMessages: null,
      localHistoryLimit: 2000,
      cloudBackups: true,
      teamSeats: 5,
      emergencyMode: true,
      advancedFilters: true,
      presetBundles: true,
      discordAlerts: true,
      obsOverlay: true,
      aiSuggestions: true,
      aiSuggestionDailyLimit: 300
    }
  } satisfies Record<EntitlementPlan, EntitlementSnapshot["features"]>;

  const currentPeriodEndsAt = normalizeDate(input.currentPeriodEndsAt);
  const effectivePlan = paidAccessPlan({
    plan: input.plan,
    status: input.status,
    currentPeriodEndsAt,
    now: input.now
  });

  return {
    plan: effectivePlan,
    status: input.status,
    source: input.source,
    productId: input.productId ?? null,
    currentPeriodEndsAt,
    features: featureMap[effectivePlan]
  };
}

export function starterSnapshot(source = "local-dev"): EntitlementSnapshot {
  return snapshotForPlan({
    plan: "starter",
    status: "trialing",
    source
  });
}

function normalizeDate(value: Date | string | null | undefined): string | null {
  if (!value) {
    return null;
  }

  return value instanceof Date ? value.toISOString() : value;
}

function paidAccessPlan(input: {
  plan: EntitlementPlan;
  status: EntitlementSnapshot["status"];
  currentPeriodEndsAt: string | null;
  now?: Date;
}): EntitlementPlan {
  if (input.plan === "starter") {
    return "starter";
  }

  if (input.status === "expired" || input.status === "none") {
    return "starter";
  }

  if (input.currentPeriodEndsAt) {
    const expiresAt = Date.parse(input.currentPeriodEndsAt);
    const now = input.now?.getTime() ?? Date.now();
    if (Number.isFinite(expiresAt) && expiresAt <= now) {
      return "starter";
    }
  }

  if (input.status === "active" || input.status === "trialing" || input.status === "past_due" || input.status === "canceled") {
    return input.plan;
  }

  return "starter";
}
