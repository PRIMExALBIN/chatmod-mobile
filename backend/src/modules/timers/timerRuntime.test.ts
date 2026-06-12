import { describe, expect, it } from "vitest";
import { dueTimers, isTimerDue, selectRotatedTimer } from "./timerRuntime.js";
import type { TimerRecord } from "./timerStore.js";

const timer: TimerRecord = {
  id: "timer-1",
  profileId: "profile-1",
  name: "Rules reminder",
  message: "Keep chat friendly.",
  intervalMinutes: 15,
  minChatMessages: 5,
  quietStartMinutes: null,
  quietEndMinutes: null,
  enabled: true,
  lastSentAt: "2026-06-07T10:00:00.000Z",
  createdAt: new Date(0).toISOString(),
  updatedAt: new Date(0).toISOString()
};

describe("timerRuntime", () => {
  it("returns due timers past interval and activity threshold", () => {
    expect(
      dueTimers([timer], {
        messagesSinceLastTimer: 5,
        now: new Date("2026-06-07T10:15:00.000Z")
      }).map((row) => row.id)
    ).toEqual(["timer-1"]);
  });

  it("blocks timers when activity is too low", () => {
    expect(
      isTimerDue(timer, {
        messagesSinceLastTimer: 4,
        now: new Date("2026-06-07T10:20:00.000Z")
      })
    ).toBe(false);
  });

  it("blocks timers inside interval", () => {
    expect(
      isTimerDue(timer, {
        messagesSinceLastTimer: 5,
        now: new Date("2026-06-07T10:14:59.000Z")
      })
    ).toBe(false);
  });

  it("allows never-sent timers when activity threshold passes", () => {
    expect(
      isTimerDue({ ...timer, lastSentAt: null }, {
        messagesSinceLastTimer: 10,
        now: new Date("2026-06-07T10:01:00.000Z")
      })
    ).toBe(true);
  });

  it("blocks timers inside a stream-relative quiet window", () => {
    expect(
      isTimerDue(
        { ...timer, lastSentAt: null, quietStartMinutes: 10, quietEndMinutes: 20 },
        {
          messagesSinceLastTimer: 10,
          streamStartedAt: new Date("2026-06-07T10:00:00.000Z"),
          now: new Date("2026-06-07T10:15:00.000Z")
        }
      )
    ).toBe(false);
  });

  it("allows quiet-window timers after the quiet window ends", () => {
    expect(
      isTimerDue(
        { ...timer, lastSentAt: null, quietStartMinutes: 10, quietEndMinutes: 20 },
        {
          messagesSinceLastTimer: 10,
          streamStartedAt: new Date("2026-06-07T10:00:00.000Z"),
          now: new Date("2026-06-07T10:20:00.000Z")
        }
      )
    ).toBe(true);
  });

  it("selects one due timer by randomized index", () => {
    const selected = selectRotatedTimer(
      [
        { ...timer, id: "timer-1", lastSentAt: null },
        { ...timer, id: "timer-2", lastSentAt: null }
      ],
      {
        messagesSinceLastTimer: 10,
        now: new Date("2026-06-07T10:20:00.000Z")
      },
      () => 0.75
    );

    expect(selected?.id).toBe("timer-2");
  });

  it("returns null when no timers are due for rotation", () => {
    expect(
      selectRotatedTimer(
        [
          {
            ...timer,
            lastSentAt: "2026-06-07T10:19:00.000Z"
          }
        ],
        {
          messagesSinceLastTimer: 10,
          now: new Date("2026-06-07T10:20:00.000Z")
        },
        () => 0
      )
    ).toBeNull();
  });

  it("clamps randomized timer selection to the due list", () => {
    const selected = selectRotatedTimer(
      [
        { ...timer, id: "timer-1", lastSentAt: null },
        { ...timer, id: "timer-2", lastSentAt: null }
      ],
      {
        messagesSinceLastTimer: 10,
        now: new Date("2026-06-07T10:20:00.000Z")
      },
      () => 1
    );

    expect(selected?.id).toBe("timer-2");
  });
});
