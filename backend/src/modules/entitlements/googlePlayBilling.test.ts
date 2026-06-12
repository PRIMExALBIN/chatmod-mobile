import { describe, expect, it } from "vitest";
import { statusFromPurchase } from "./googlePlayBilling.js";

describe("statusFromPurchase", () => {
  const now = new Date("2026-06-07T10:00:00.000Z");
  const futureExpiry = new Date("2026-07-07T10:00:00.000Z");

  it("marks received payments active", () => {
    expect(statusFromPurchase({
      currentPeriodEndsAt: futureExpiry,
      paymentState: 1,
      now
    })).toBe("ACTIVE");
  });

  it("marks free trials as trialing", () => {
    expect(statusFromPurchase({
      currentPeriodEndsAt: futureExpiry,
      paymentState: 2,
      now
    })).toBe("TRIALING");
  });

  it("marks billing-problem cancellations as past due while the period is valid", () => {
    expect(statusFromPurchase({
      currentPeriodEndsAt: futureExpiry,
      cancelReason: 1,
      paymentState: null,
      now
    })).toBe("PAST_DUE");
  });

  it("marks user cancellations as canceled while the period is valid", () => {
    expect(statusFromPurchase({
      currentPeriodEndsAt: futureExpiry,
      cancelReason: 0,
      now
    })).toBe("CANCELED");
  });

  it("marks purchases expired when the paid period has passed", () => {
    expect(statusFromPurchase({
      currentPeriodEndsAt: new Date("2026-06-01T10:00:00.000Z"),
      cancelReason: 0,
      paymentState: 1,
      now
    })).toBe("EXPIRED");
  });
});
