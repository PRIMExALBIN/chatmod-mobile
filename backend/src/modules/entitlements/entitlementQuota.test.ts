import { describe, expect, it } from "vitest";
import type { AuthContext } from "../auth/sessionToken.js";
import { assertWithinEntitlementLimit } from "./entitlementQuota.js";
import type { EntitlementStore, EntitlementUpdate } from "./entitlementStore.js";
import { snapshotForPlan, type EntitlementSnapshot } from "./entitlementPlans.js";

const auth: AuthContext = {
  subject: "quota-device",
  deviceId: "quota-device",
  installId: "quota-install"
};

describe("entitlementQuota", () => {
  it("allows counts below the current plan limit", async () => {
    const store = fakeEntitlementStore(snapshotForPlan({
      plan: "creator",
      status: "active",
      source: "test",
      productId: "chatmod_creator_monthly",
      currentPeriodEndsAt: new Date("2026-07-08T10:00:00.000Z")
    }));

    await expect(assertWithinEntitlementLimit({
      auth,
      entitlementStore: store,
      feature: "channelProfiles",
      currentCount: 4,
      resourceLabel: "Channel profile"
    })).resolves.toBeUndefined();
  });

  it("allows uncapped paid-plan features", async () => {
    const store = fakeEntitlementStore(snapshotForPlan({
      plan: "pro",
      status: "active",
      source: "test",
      productId: "chatmod_pro_monthly",
      currentPeriodEndsAt: new Date("2026-07-08T10:00:00.000Z")
    }));

    await expect(assertWithinEntitlementLimit({
      auth,
      entitlementStore: store,
      feature: "commandProfiles",
      currentCount: 10_000,
      resourceLabel: "Command"
    })).resolves.toBeUndefined();
  });

  it("rejects counts at the current plan limit", async () => {
    const store = fakeEntitlementStore(snapshotForPlan({
      plan: "starter",
      status: "trialing",
      source: "test"
    }));

    await expect(assertWithinEntitlementLimit({
      auth,
      entitlementStore: store,
      feature: "commandProfiles",
      currentCount: 3,
      resourceLabel: "Command"
    })).rejects.toMatchObject({
      statusCode: 403,
      message: "Command limit reached for starter plan (3)."
    });
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
