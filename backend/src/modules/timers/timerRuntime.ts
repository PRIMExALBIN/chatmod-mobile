import type { TimerRecord } from "./timerStore.js";

export interface TimerRuntimeState {
  messagesSinceLastTimer: number;
  now: Date;
  streamStartedAt?: Date | null;
}

export function dueTimers(timers: TimerRecord[], state: TimerRuntimeState): TimerRecord[] {
  return timers.filter((timer) => isTimerDue(timer, state));
}

export function selectRotatedTimer(
  timers: TimerRecord[],
  state: TimerRuntimeState,
  random: () => number = Math.random
): TimerRecord | null {
  const due = dueTimers(timers, state);
  if (due.length === 0) {
    return null;
  }

  const randomValue = Math.min(Math.max(random(), 0), 0.999_999_999);
  return due[Math.floor(randomValue * due.length)] ?? due[0];
}

export function isTimerDue(timer: TimerRecord, state: TimerRuntimeState): boolean {
  if (!timer.enabled) {
    return false;
  }

  if (state.messagesSinceLastTimer < timer.minChatMessages) {
    return false;
  }

  if (isInQuietWindow(timer, state)) {
    return false;
  }

  if (!timer.lastSentAt) {
    return true;
  }

  const lastSentAt = new Date(timer.lastSentAt);
  if (Number.isNaN(lastSentAt.getTime())) {
    return true;
  }

  return state.now.getTime() - lastSentAt.getTime() >= timer.intervalMinutes * 60_000;
}

function isInQuietWindow(timer: TimerRecord, state: TimerRuntimeState): boolean {
  if (timer.quietStartMinutes === null || timer.quietEndMinutes === null) {
    return false;
  }

  if (timer.quietEndMinutes <= timer.quietStartMinutes) {
    return false;
  }

  const startedAt = state.streamStartedAt;
  if (!startedAt || Number.isNaN(startedAt.getTime())) {
    return false;
  }

  const elapsedMinutes = Math.floor((state.now.getTime() - startedAt.getTime()) / 60_000);
  return elapsedMinutes >= timer.quietStartMinutes && elapsedMinutes < timer.quietEndMinutes;
}
