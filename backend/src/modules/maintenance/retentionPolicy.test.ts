import { describe, expect, it } from "vitest";
import {
  backupIdsBeyondLimit,
  cutoffForDays,
  defaultRetentionOptions,
  parseRetentionCliOptions
} from "./retentionPolicy.js";

describe("retentionPolicy", () => {
  it("uses conservative dry-run defaults", () => {
    const options = parseRetentionCliOptions([], {});

    expect(options).toMatchObject({
      apply: false,
      json: false,
      supportEventDays: defaultRetentionOptions.supportEventDays,
      apiErrorDays: defaultRetentionOptions.apiErrorDays,
      streamLogDays: defaultRetentionOptions.streamLogDays,
      backupVersionsPerProfile: defaultRetentionOptions.backupVersionsPerProfile
    });
  });

  it("accepts explicit apply/json flags and env overrides", () => {
    const options = parseRetentionCliOptions(["--apply", "--json", "--stream-log-days=45"], {
      SUPPORT_EVENT_RETENTION_DAYS: "120",
      API_ERROR_RETENTION_DAYS: "14",
      BACKUP_VERSIONS_PER_PROFILE: "4"
    });

    expect(options).toMatchObject({
      apply: true,
      json: true,
      supportEventDays: 120,
      apiErrorDays: 14,
      streamLogDays: 45,
      backupVersionsPerProfile: 4
    });
  });

  it("rejects dangerously short retention windows", () => {
    expect(() => parseRetentionCliOptions(["--support-event-days=1"], {}))
      .toThrow("support event retention days must be an integer from 7 to 3650.");
  });

  it("calculates date cutoffs in whole days", () => {
    expect(cutoffForDays(new Date("2026-06-09T12:00:00.000Z"), 30).toISOString())
      .toBe("2026-05-10T12:00:00.000Z");
  });

  it("keeps the newest backup versions per profile", () => {
    const pruneIds = backupIdsBeyondLimit([
      { id: "a-oldest", profileId: "profile-a", createdAt: new Date("2026-01-01T00:00:00.000Z") },
      { id: "a-newest", profileId: "profile-a", createdAt: new Date("2026-03-01T00:00:00.000Z") },
      { id: "a-middle", profileId: "profile-a", createdAt: new Date("2026-02-01T00:00:00.000Z") },
      { id: "b-old", profileId: "profile-b", createdAt: new Date("2026-01-01T00:00:00.000Z") },
      { id: "b-new", profileId: "profile-b", createdAt: new Date("2026-02-01T00:00:00.000Z") }
    ], 2);

    expect(pruneIds).toEqual(["a-oldest"]);
  });
});
