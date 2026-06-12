import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { summarizeStreamChat } from "./streamChatSummary.js";
import {
  chatMessageLogInputSchema,
  createStreamSessionStore,
  moderationActionLogInputSchema,
  moderationActionReviewSchema,
  runtimeEventInputSchema,
  streamSessionEndSchema,
  streamSessionInputSchema,
  type StreamSessionLogBundle,
  type StreamSessionStore
} from "./streamSessionStore.js";

export interface StreamSessionRoutesOptions {
  store?: StreamSessionStore;
  entitlementStore?: EntitlementStore;
}

export async function streamSessionRoutes(
  app: FastifyInstance,
  options: StreamSessionRoutesOptions = {}
): Promise<void> {
  const store = options.store ?? createStreamSessionStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();

  app.get("/", { preHandler: requireAuth }, async (request) => {
    const query = z.object({ profileId: z.string().min(1).optional() }).parse(request.query);
    return { sessions: await store.list(request.auth!, query.profileId) };
  });

  app.get("/analytics/summary", { preHandler: requireAuth }, async (request) => {
    const query = z.object({
      profileId: z.string().min(1).optional(),
      days: z.coerce.number().int().min(1).max(365).default(30),
      limit: z.coerce.number().int().min(1).max(100).default(50)
    }).parse(request.query);
    const sessions = (await store.list(request.auth!, query.profileId))
      .filter((session) => Date.parse(session.startedAt) >= Date.now() - query.days * 24 * 60 * 60 * 1000)
      .slice(0, query.limit);
    const bundles = await Promise.all(sessions.map((session) => store.getLogs(request.auth!, session.id)));
    return streamSessionAnalyticsSummary(bundles, query.days);
  });

  app.put("/:id", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = streamSessionInputSchema.parse(request.body);
    return store.upsert(request.auth!, params.id, body);
  });

  app.post("/:id/end", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = streamSessionEndSchema.parse(request.body ?? {});
    return store.end(request.auth!, params.id, body);
  });

  app.get("/:id/logs", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    return store.getLogs(request.auth!, params.id);
  });

  app.get("/:id/ai-summary", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const entitlement = await entitlementStore.current(request.auth!);
    if (!entitlement.features.aiSuggestions) {
      return reply.status(403).send({
        error: "AI_CHAT_SUMMARY_REQUIRED",
        message: "AI chat summaries require the Creator plan and use the free local heuristic provider."
      });
    }

    const logs = await store.getLogs(request.auth!, params.id);
    return summarizeStreamChat(logs);
  });

  app.get("/:id/export", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const query = z.object({
      format: z.enum(["json", "csv"]).default("json")
    }).parse(request.query);
    const logs = await store.getLogs(request.auth!, params.id);
    const filename = `chatmod-${safeFilename(logs.session.videoId || logs.session.id)}.${query.format}`;

    reply.header("content-disposition", `attachment; filename="${filename}"`);
    if (query.format === "csv") {
      return reply
        .type("text/csv; charset=utf-8")
        .send(streamSessionLogsToCsv(logs));
    }

    return {
      exportedAt: new Date().toISOString(),
      format: "json",
      ...logs
    };
  });

  app.post("/:id/messages", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = chatMessageLogInputSchema.parse(request.body);
    return reply.status(201).send(await store.recordMessage(request.auth!, params.id, body));
  });

  app.post("/:id/actions", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = moderationActionLogInputSchema.parse(request.body);
    return reply.status(201).send(await store.recordAction(request.auth!, params.id, body));
  });

  app.patch("/:id/actions/:actionId/review", { preHandler: requireAuth }, async (request) => {
    const params = z.object({
      id: z.string().min(1),
      actionId: z.string().min(1)
    }).parse(request.params);
    const body = moderationActionReviewSchema.parse(request.body);
    return store.updateActionReview(request.auth!, params.id, params.actionId, body);
  });

  app.post("/:id/runtime-events", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = runtimeEventInputSchema.parse(request.body);
    return reply.status(201).send(await store.recordRuntimeEvent(request.auth!, params.id, body));
  });
}

function streamSessionLogsToCsv(logs: StreamSessionLogBundle): string {
  const rows: string[][] = [
    ["kind", "createdAt", "youtubeMessageId", "authorChannelId", "actionOrType", "reasonOrMessage", "textOrMetadata"]
  ];

  logs.messages.forEach((message) => {
    rows.push([
      "chat",
      message.receivedAt,
      message.youtubeMessageId,
      message.authorChannelId,
      "",
      "",
      `${message.authorName}: ${message.text}`
    ]);
  });

  logs.actions.forEach((action) => {
    rows.push([
      "moderation",
      action.createdAt,
      action.youtubeMessageId ?? "",
      action.authorChannelId ?? "",
      action.actionType,
      action.reason,
      JSON.stringify({
        confidence: action.confidence,
        metadata: action.metadata ?? {},
        reviewStatus: action.reviewStatus,
        reviewedAt: action.reviewedAt,
        reviewNote: action.reviewNote
      })
    ]);
  });

  logs.runtimeEvents.forEach((event) => {
    rows.push([
      "runtime",
      event.createdAt,
      "",
      "",
      event.type,
      event.message,
      JSON.stringify(event.metadata ?? {})
    ]);
  });

  return rows.map((row) => row.map(csvCell).join(",")).join("\n");
}

function streamSessionAnalyticsSummary(bundles: StreamSessionLogBundle[], rangeDays: number) {
  const byDay = new Map<string, {
    day: string;
    streamCount: number;
    messageCount: number;
    moderationActionCount: number;
    spamAttemptCount: number;
    reconnectEvents: number;
    uptimeMillis: number;
  }>();
  const chatters = new Map<string, { authorChannelId: string; authorName: string; messageCount: number; moderationActionCount: number }>();
  const commandUsage = new Map<string, { commandId: string; trigger: string | null; count: number }>();
  const ruleEffectiveness = new Map<string, {
    rule: string;
    matchCount: number;
    destructiveActionCount: number;
    falsePositiveCount: number;
  }>();
  const ruleEffectivenessByPreset = new Map<string, {
    presetId: string;
    presetName: string | null;
    presetVersion: string | null;
    rule: string;
    matchCount: number;
    destructiveActionCount: number;
    falsePositiveCount: number;
  }>();

  const byStream = bundles.map((bundle) => {
    const day = bundle.session.startedAt.slice(0, 10);
    const uptimeMillis = sessionUptimeMillis(bundle);
    const reconnectEvents = bundle.runtimeEvents.filter(isReconnectEvent).length;
    const spamAttemptCount = bundle.actions.filter(isSpamAttemptAction).length;
    const destructiveActionCount = bundle.actions.filter(isDestructiveAction).length;
    const uniqueChatters = new Set(bundle.messages.map((message) => message.authorChannelId)).size;
    const commandCount = countCommandUsage(bundle, commandUsage);
    const timerCount = countTimerUsage(bundle);

    const daySummary = byDay.get(day) ?? {
      day,
      streamCount: 0,
      messageCount: 0,
      moderationActionCount: 0,
      spamAttemptCount: 0,
      reconnectEvents: 0,
      uptimeMillis: 0
    };
    daySummary.streamCount += 1;
    daySummary.messageCount += bundle.messages.length;
    daySummary.moderationActionCount += bundle.actions.length;
    daySummary.spamAttemptCount += spamAttemptCount;
    daySummary.reconnectEvents += reconnectEvents;
    daySummary.uptimeMillis += uptimeMillis;
    byDay.set(day, daySummary);

    bundle.messages.forEach((message) => {
      const entry = chatters.get(message.authorChannelId) ?? {
        authorChannelId: message.authorChannelId,
        authorName: message.authorName,
        messageCount: 0,
        moderationActionCount: 0
      };
      entry.authorName = message.authorName || entry.authorName;
      entry.messageCount += 1;
      chatters.set(message.authorChannelId, entry);
    });
    bundle.actions.forEach((action) => {
      if (action.authorChannelId) {
        const entry = chatters.get(action.authorChannelId) ?? {
          authorChannelId: action.authorChannelId,
          authorName: action.authorChannelId,
          messageCount: 0,
          moderationActionCount: 0
        };
        entry.moderationActionCount += 1;
        chatters.set(action.authorChannelId, entry);
      }

      if (isRuleAction(action)) {
        const rule = ruleLabel(action.reason);
        const entry = ruleEffectiveness.get(rule) ?? {
          rule,
          matchCount: 0,
          destructiveActionCount: 0,
          falsePositiveCount: 0
        };
        entry.matchCount += 1;
        if (isDestructiveAction(action)) {
          entry.destructiveActionCount += 1;
        }
        if (action.reviewStatus === "false_positive") {
          entry.falsePositiveCount += 1;
        }
        ruleEffectiveness.set(rule, entry);

        const preset = rulePresetMetadata(action);
        const presetKey = `${preset.presetId}:${preset.presetVersion ?? "unversioned"}:${rule}`;
        const presetEntry = ruleEffectivenessByPreset.get(presetKey) ?? {
          ...preset,
          rule,
          matchCount: 0,
          destructiveActionCount: 0,
          falsePositiveCount: 0
        };
        presetEntry.matchCount += 1;
        if (isDestructiveAction(action)) {
          presetEntry.destructiveActionCount += 1;
        }
        if (action.reviewStatus === "false_positive") {
          presetEntry.falsePositiveCount += 1;
        }
        ruleEffectivenessByPreset.set(presetKey, presetEntry);
      }
    });

    return {
      sessionId: bundle.session.id,
      title: bundle.session.title,
      videoId: bundle.session.videoId,
      startedAt: bundle.session.startedAt,
      endedAt: bundle.session.endedAt,
      messageCount: bundle.messages.length,
      uniqueChatters,
      moderationActionCount: bundle.actions.length,
      destructiveActionCount,
      spamAttemptCount,
      commandCount,
      timerCount,
      reconnectEvents,
      uptimeMillis
    };
  });

  return {
    generatedAt: new Date().toISOString(),
    rangeDays,
    sessionCount: bundles.length,
    totalMessages: bundles.reduce((total, bundle) => total + bundle.messages.length, 0),
    totalModerationActions: bundles.reduce((total, bundle) => total + bundle.actions.length, 0),
    totalRuntimeEvents: bundles.reduce((total, bundle) => total + bundle.runtimeEvents.length, 0),
    totalUptimeMillis: byStream.reduce((total, stream) => total + stream.uptimeMillis, 0),
    reconnectEvents: byStream.reduce((total, stream) => total + stream.reconnectEvents, 0),
    byStream,
    byDay: Array.from(byDay.values()).sort((a, b) => a.day.localeCompare(b.day)),
    topChatters: Array.from(chatters.values())
      .sort((a, b) => (b.messageCount + b.moderationActionCount) - (a.messageCount + a.moderationActionCount))
      .slice(0, 10),
    commandUsage: Array.from(commandUsage.values())
      .sort((a, b) => b.count - a.count || a.commandId.localeCompare(b.commandId))
      .slice(0, 10),
    ruleEffectiveness: Array.from(ruleEffectiveness.values())
      .sort((a, b) => b.matchCount - a.matchCount || a.rule.localeCompare(b.rule))
      .slice(0, 10),
    ruleEffectivenessByPreset: Array.from(ruleEffectivenessByPreset.values())
      .sort((a, b) =>
        b.matchCount - a.matchCount ||
        a.presetId.localeCompare(b.presetId) ||
        a.rule.localeCompare(b.rule)
      )
      .slice(0, 20),
    spamAttemptsByDay: Array.from(byDay.values())
      .map((day) => ({ day: day.day, count: day.spamAttemptCount }))
      .sort((a, b) => a.day.localeCompare(b.day)),
    uptimeByStream: byStream.map((stream) => ({
      sessionId: stream.sessionId,
      title: stream.title,
      uptimeMillis: stream.uptimeMillis,
      reconnectEvents: stream.reconnectEvents
    }))
  };
}

function countCommandUsage(
  bundle: StreamSessionLogBundle,
  commandUsage: Map<string, { commandId: string; trigger: string | null; count: number }>
): number {
  const commandEvents = bundle.runtimeEvents.filter((event) => event.type === "command_sent");
  if (commandEvents.length > 0) {
    commandEvents.forEach((event) => {
      const metadata = normalizedMetadata(event.metadata);
      const commandId = stringValue(metadata.commandId) ?? stringValue(metadata.trigger) ?? "unknown-command";
      const trigger = stringValue(metadata.trigger);
      incrementCommand(commandUsage, commandId, trigger, 1);
    });
    return commandEvents.length;
  }

  return bundle.runtimeEvents
    .filter((event) => event.type === "runtime_session_summary")
    .reduce((total, event) => total + countSummaryMap(event, "commandsUsedJson", commandUsage), 0);
}

function countTimerUsage(bundle: StreamSessionLogBundle): number {
  const timerEvents = bundle.runtimeEvents.filter((event) => event.type === "timer_sent");
  if (timerEvents.length > 0) {
    return timerEvents.length;
  }

  return bundle.runtimeEvents
    .filter((event) => event.type === "runtime_session_summary")
    .reduce((total, event) => total + sumMetadataCountMap(event, "timersUsedJson"), 0);
}

function countSummaryMap(
  event: { metadata: Record<string, unknown> | null },
  key: string,
  commandUsage: Map<string, { commandId: string; trigger: string | null; count: number }>
): number {
  const counts = metadataCountMap(event, key);
  Object.entries(counts).forEach(([commandId, count]) => incrementCommand(commandUsage, commandId, null, count));
  return Object.values(counts).reduce((total, count) => total + count, 0);
}

function incrementCommand(
  commandUsage: Map<string, { commandId: string; trigger: string | null; count: number }>,
  commandId: string,
  trigger: string | null,
  count: number
): void {
  const entry = commandUsage.get(commandId) ?? { commandId, trigger, count: 0 };
  entry.trigger = trigger ?? entry.trigger;
  entry.count += count;
  commandUsage.set(commandId, entry);
}

function sessionUptimeMillis(bundle: StreamSessionLogBundle): number {
  const startedAt = Date.parse(bundle.session.startedAt);
  const endedAt = bundle.session.endedAt ? Date.parse(bundle.session.endedAt) : Number.NaN;
  if (Number.isFinite(startedAt) && Number.isFinite(endedAt) && endedAt >= startedAt) {
    return endedAt - startedAt;
  }

  return bundle.runtimeEvents
    .filter((event) => event.type === "runtime_session_summary")
    .map((event) => numberValue(normalizedMetadata(event.metadata).durationMillis) ?? 0)
    .reduce((max, value) => Math.max(max, value), 0);
}

function isReconnectEvent(event: { type: string; message: string }): boolean {
  const text = `${event.type} ${event.message}`.toLowerCase();
  return text.includes("reconnect") || text.includes("backoff") || text.includes("waiting for network");
}

function isDestructiveAction(action: { actionType: string }): boolean {
  return ["deleteMessage", "hideUser", "timeoutUser"].includes(action.actionType);
}

function isSpamAttemptAction(action: { actionType: string; reason: string; reviewStatus: string | null }): boolean {
  return isRuleAction(action) && action.reviewStatus !== "false_positive";
}

function isRuleAction(action: { actionType: string; reason: string }): boolean {
  if (action.actionType === "allow" || action.reason.startsWith("manual_")) {
    return false;
  }
  return action.reason !== "auto_reply";
}

function ruleLabel(reason: string): string {
  return reason.split(":")[0]?.trim() || reason;
}

function rulePresetMetadata(action: { metadata: Record<string, unknown> | null }): {
  presetId: string;
  presetName: string | null;
  presetVersion: string | null;
} {
  const metadata = normalizedMetadata(action.metadata);
  const presetId = stringValue(metadata.rulePresetId) ?? stringValue(metadata.presetId) ?? "unknown-preset";
  return {
    presetId,
    presetName: stringValue(metadata.rulePresetName) ?? stringValue(metadata.presetName),
    presetVersion: stringValue(metadata.rulePresetVersion) ?? stringValue(metadata.presetVersion)
  };
}

function sumMetadataCountMap(event: { metadata: Record<string, unknown> | null }, key: string): number {
  return Object.values(metadataCountMap(event, key)).reduce((total, count) => total + count, 0);
}

function metadataCountMap(event: { metadata: Record<string, unknown> | null }, key: string): Record<string, number> {
  const value = normalizedMetadata(event.metadata)[key];
  const parsed = typeof value === "string" ? safeJsonObject(value) : value;
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    return {};
  }

  const counts: Record<string, number> = {};
  Object.entries(parsed).forEach(([entryKey, entryValue]) => {
    const count = numberValue(entryValue) ?? 0;
    if (count > 0) {
      counts[entryKey] = count;
    }
  });
  return counts;
}

function normalizedMetadata(metadata: Record<string, unknown> | null): Record<string, unknown> {
  if (!metadata) {
    return {};
  }

  const localMetadata = typeof metadata.localMetadataJson === "string"
    ? safeJsonObject(metadata.localMetadataJson)
    : null;
  return {
    ...metadata,
    ...(localMetadata ?? {})
  };
}

function safeJsonObject(value: string): Record<string, unknown> | null {
  try {
    const parsed: unknown = JSON.parse(value);
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : null;
  } catch {
    return null;
  }
}

function stringValue(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}

function numberValue(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === "string" && value.trim() && Number.isFinite(Number(value))) {
    return Number(value);
  }
  return null;
}

function csvCell(value: string): string {
  return `"${value.replaceAll("\"", "\"\"")}"`;
}

function safeFilename(value: string): string {
  return value.replace(/[^a-z0-9_-]+/gi, "-").replace(/^-+|-+$/g, "").slice(0, 80) || "stream-log";
}
