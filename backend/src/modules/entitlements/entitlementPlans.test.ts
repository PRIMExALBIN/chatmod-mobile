import { describe, expect, it } from "vitest";
import { planForProduct, snapshotForPlan, starterSnapshot } from "./entitlementPlans.js";

describe("entitlementPlans", () => {
  it("maps product ids to plans", () => {
    expect(planForProduct(null)).toBe("starter");
    expect(planForProduct("chatmod_pro_monthly")).toBe("pro");
    expect(planForProduct("chatmod_creator_yearly")).toBe("creator");
  });

  it("returns starter features", () => {
    expect(starterSnapshot()).toMatchObject({
      plan: "starter",
      features: {
        commandProfiles: 3,
        timedMessages: 5,
        localHistoryLimit: 120,
        aiSuggestionDailyLimit: 0,
        obsOverlay: false,
        presetBundles: false,
        emergencyMode: false
      }
    });
  });

  it("returns creator features", () => {
    const snapshot = snapshotForPlan({
      plan: "creator",
      status: "active",
      source: "google-play",
      productId: "chatmod_creator_monthly",
      currentPeriodEndsAt: new Date("2026-07-07T10:00:00.000Z")
    });

    expect(snapshot).toMatchObject({
      plan: "creator",
      status: "active",
      source: "google-play",
      productId: "chatmod_creator_monthly",
      currentPeriodEndsAt: "2026-07-07T10:00:00.000Z",
      features: {
        commandProfiles: null,
        timedMessages: null,
        localHistoryLimit: 2000,
        presetBundles: true,
        teamSeats: 5,
        obsOverlay: true,
        aiSuggestions: true,
        aiSuggestionDailyLimit: 300
      }
    });
  });

  it("returns uncapped command and timer limits for pro", () => {
    const snapshot = snapshotForPlan({
      plan: "pro",
      status: "active",
      source: "google-play",
      productId: "chatmod_pro_monthly",
      currentPeriodEndsAt: new Date("2026-07-07T10:00:00.000Z")
    });

    expect(snapshot).toMatchObject({
      plan: "pro",
      features: {
        commandProfiles: null,
        timedMessages: null,
        localHistoryLimit: 1000,
        aiSuggestionDailyLimit: 0,
        presetBundles: true,
        obsOverlay: true,
        emergencyMode: true
      }
    });
  });

  it("keeps paid features for canceled subscriptions until the paid period ends", () => {
    const snapshot = snapshotForPlan({
      plan: "pro",
      status: "canceled",
      source: "google-play",
      productId: "chatmod_pro_monthly",
      currentPeriodEndsAt: new Date("2026-07-07T10:00:00.000Z"),
      now: new Date("2026-06-07T10:00:00.000Z")
    });

    expect(snapshot).toMatchObject({
      plan: "pro",
      status: "canceled",
      features: {
        emergencyMode: true,
        advancedFilters: true,
        presetBundles: true,
        localHistoryLimit: 1000
      }
    });
  });

  it("keeps paid features during grace or past-due periods before expiry", () => {
    const snapshot = snapshotForPlan({
      plan: "pro",
      status: "past_due",
      source: "google-play",
      productId: "chatmod_pro_monthly",
      currentPeriodEndsAt: new Date("2026-06-10T10:00:00.000Z"),
      now: new Date("2026-06-07T10:00:00.000Z")
    });

    expect(snapshot).toMatchObject({
      plan: "pro",
      status: "past_due",
      features: {
        emergencyMode: true,
        presetBundles: true,
        localHistoryLimit: 1000
      }
    });
  });

  it("falls back to starter features when a paid subscription is expired", () => {
    const snapshot = snapshotForPlan({
      plan: "creator",
      status: "expired",
      source: "google-play",
      productId: "chatmod_creator_monthly",
      currentPeriodEndsAt: new Date("2026-06-01T10:00:00.000Z"),
      now: new Date("2026-06-07T10:00:00.000Z")
    });

    expect(snapshot).toMatchObject({
      plan: "starter",
      status: "expired",
      productId: "chatmod_creator_monthly",
      features: {
        commandProfiles: 3,
        localHistoryLimit: 120,
        presetBundles: false,
        emergencyMode: false,
        aiSuggestions: false
      }
    });
  });
});
