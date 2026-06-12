import { describe, expect, it } from "vitest";
import { InMemoryAiSuggestionUsageStore } from "./aiSuggestionUsageStore.js";

const auth = {
  subject: "usage-device",
  deviceId: "usage-device",
  installId: "usage-install"
};

describe("InMemoryAiSuggestionUsageStore", () => {
  it("reserves suggestions until the daily plan limit is exhausted", async () => {
    const store = new InMemoryAiSuggestionUsageStore();
    const now = new Date("2026-06-11T10:30:00.000Z");

    await expect(store.reserve(auth, 2, now)).resolves.toMatchObject({
      allowed: true,
      used: 1,
      limit: 2,
      remaining: 1,
      resetAt: "2026-06-12T00:00:00.000Z"
    });
    await expect(store.reserve(auth, 2, now)).resolves.toMatchObject({
      allowed: true,
      used: 2,
      remaining: 0
    });
    await expect(store.reserve(auth, 2, now)).resolves.toMatchObject({
      allowed: false,
      used: 2,
      remaining: 0
    });
  });

  it("resets usage on the next UTC day", async () => {
    const store = new InMemoryAiSuggestionUsageStore();

    await store.reserve(auth, 1, new Date("2026-06-11T23:59:00.000Z"));
    await expect(store.reserve(auth, 1, new Date("2026-06-12T00:00:01.000Z"))).resolves.toMatchObject({
      allowed: true,
      used: 1,
      resetAt: "2026-06-13T00:00:00.000Z"
    });
  });
});
