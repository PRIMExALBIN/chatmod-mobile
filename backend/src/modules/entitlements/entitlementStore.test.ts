import { describe, expect, it } from "vitest";
import { InMemoryEntitlementStore } from "./entitlementStore.js";

describe("InMemoryEntitlementStore", () => {
  it("updates entitlement snapshots from purchase validation results", async () => {
    const store = new InMemoryEntitlementStore();
    const auth = {
      subject: "device-1",
      deviceId: "device-1",
      installId: "install-1"
    };

    const updated = await store.upsert(auth, {
      source: "google-play",
      productId: "chatmod_pro_monthly",
      status: "ACTIVE",
      currentPeriodEndsAt: new Date("2026-07-07T10:00:00.000Z")
    });

    expect(updated).toMatchObject({
      plan: "pro",
      status: "active",
      source: "google-play",
      productId: "chatmod_pro_monthly",
      features: {
        emergencyMode: true,
        advancedFilters: true,
        presetBundles: true,
        obsOverlay: true,
        localHistoryLimit: 1000
      }
    });

    await expect(store.current(auth)).resolves.toMatchObject({
      plan: "pro"
    });
  });

  it("does not grant paid features for expired purchase validation results", async () => {
    const store = new InMemoryEntitlementStore();
    const auth = {
      subject: "device-1",
      deviceId: "device-1",
      installId: "install-1"
    };

    const updated = await store.upsert(auth, {
      source: "google-play",
      productId: "chatmod_creator_monthly",
      status: "EXPIRED",
      currentPeriodEndsAt: new Date("2020-01-01T00:00:00.000Z")
    });

    expect(updated).toMatchObject({
      plan: "starter",
      status: "expired",
      productId: "chatmod_creator_monthly",
      features: {
        commandProfiles: 3,
        localHistoryLimit: 120,
        presetBundles: false,
        obsOverlay: false,
        aiSuggestions: false
      }
    });
  });
});
