import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { HttpError, notFound } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import {
  createStreamSessionStore,
  type ModerationActionLogRecord,
  type RuntimeEventRecord,
  type StreamSessionLogBundle,
  type StreamSessionRecord,
  type StreamSessionStore
} from "../streamSessions/streamSessionStore.js";
import {
  createOverlayConfigStore,
  overlayConfigUpdateSchema,
  overlayPublicTokenSchema,
  type OverlayConfigMutationResult,
  type OverlayConfigRecord,
  type OverlayConfigStore,
  type PublicOverlayConfig
} from "./overlayConfigStore.js";

const profileParamsSchema = z.object({
  profileId: z.string().min(1)
});

const publicTokenParamsSchema = z.object({
  token: overlayPublicTokenSchema
});

export interface OverlayRoutesOptions {
  store?: OverlayConfigStore;
  streamSessionStore?: StreamSessionStore;
  entitlementStore?: EntitlementStore;
  profileStore?: ProfileStore;
}

export async function overlayRoutes(app: FastifyInstance, options: OverlayRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createOverlayConfigStore();
  const streamSessionStore = options.streamSessionStore ?? createStreamSessionStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const profileStore = options.profileStore ?? createProfileStore();

  app.get("/profiles/:profileId", { preHandler: requireAuth }, async (request) => {
    const params = profileParamsSchema.parse(request.params);
    const entitlement = await entitlementStore.current(request.auth!);
    const config = await store.get(request.auth!, params.profileId);
    return {
      ...config,
      allowed: entitlement.features.obsOverlay,
      requiredPlan: entitlement.features.obsOverlay ? null : "pro"
    };
  });

  app.put("/profiles/:profileId", { preHandler: requireAuth }, async (request) => {
    const params = profileParamsSchema.parse(request.params);
    const body = overlayConfigUpdateSchema.parse(request.body ?? {});
    await assertOverlayAllowed(request.auth!, entitlementStore);
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: params.profileId
    });

    return overlayMutationResponse(request, await store.upsert(request.auth!, params.profileId, body));
  });

  app.post("/profiles/:profileId/rotate-token", { preHandler: requireAuth }, async (request) => {
    const params = profileParamsSchema.parse(request.params);
    await assertOverlayAllowed(request.auth!, entitlementStore);
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: params.profileId
    });

    return overlayMutationResponse(request, await store.rotateToken(request.auth!, params.profileId));
  });

  app.get("/public/:token", async (request, reply) => {
    const params = publicTokenParamsSchema.parse(request.params);
    const config = await store.findByPublicToken(params.token);
    if (!config) {
      throw notFound("Overlay not found.");
    }

    return reply
      .type("text/html; charset=utf-8")
      .header("Cache-Control", "no-store")
      .send(renderOverlayHtml(config));
  });

  app.get("/public/:token/state", async (request, reply) => {
    const params = publicTokenParamsSchema.parse(request.params);
    const config = await store.findByPublicToken(params.token);
    if (!config) {
      throw notFound("Overlay not found.");
    }

    return reply
      .header("Cache-Control", "no-store")
      .send(await overlayState(config, streamSessionStore));
  });
}

async function assertOverlayAllowed(auth: AuthContext, entitlementStore: EntitlementStore): Promise<void> {
  const entitlement = await entitlementStore.current(auth);
  if (entitlement.features.obsOverlay) {
    return;
  }

  throw new HttpError(403, "OBS/browser overlays require Pro or Creator plan.");
}

function overlayMutationResponse(
  request: FastifyRequest,
  result: OverlayConfigMutationResult
): OverlayConfigRecord & {
  publicUrl: string | null;
  publicPath: string | null;
  tokenRotated: boolean;
} {
  const publicPath = result.publicToken ? `/overlays/public/${result.publicToken}` : null;
  return {
    ...result.config,
    publicPath,
    publicUrl: result.publicToken ? publicUrlFor(request, result.publicToken) : null,
    tokenRotated: result.tokenRotated
  };
}

function publicUrlFor(request: FastifyRequest, token: string): string {
  const forwardedProto = firstHeader(request.headers["x-forwarded-proto"]);
  const forwardedHost = firstHeader(request.headers["x-forwarded-host"]);
  const proto = forwardedProto ?? "http";
  const host = forwardedHost ?? firstHeader(request.headers.host) ?? "localhost:4100";
  return `${proto}://${host}/overlays/public/${encodeURIComponent(token)}`;
}

function firstHeader(value: string | string[] | undefined): string | null {
  return Array.isArray(value) ? value[0] ?? null : value ?? null;
}

async function overlayState(config: PublicOverlayConfig, streamSessionStore: StreamSessionStore) {
  if (!config.enabled) {
    return emptyState(config, "disabled");
  }

  const sessions = await streamSessionStore.list(config.ownerAuth, config.profileId);
  const selected = selectOverlaySession(config, sessions);
  if (!selected) {
    return emptyState(config, "waiting_for_stream");
  }

  const logs = await streamSessionStore.getLogs(config.ownerAuth, selected.id);
  return {
    generatedAt: new Date().toISOString(),
    enabled: true,
    status: selected.endedAt ? "ended" : "live",
    overlay: publicOverlaySettings(config),
    session: {
      id: logs.session.id,
      title: logs.session.title,
      videoId: logs.session.videoId,
      startedAt: logs.session.startedAt,
      endedAt: logs.session.endedAt,
      live: !logs.session.endedAt
    },
    metrics: config.showViewerStats ? overlayMetrics(logs) : zeroMetrics(),
    recentActions: config.showModerationActions ? recentActions(logs.actions) : [],
    recentRuntimeEvents: config.showRuntimeStatus ? recentRuntimeEvents(logs.runtimeEvents) : [],
    recentMessages: config.showRecentChat ? recentMessages(logs) : []
  };
}

function emptyState(config: PublicOverlayConfig, status: "disabled" | "waiting_for_stream") {
  return {
    generatedAt: new Date().toISOString(),
    enabled: config.enabled,
    status,
    overlay: publicOverlaySettings(config),
    session: null,
    metrics: {
      ...zeroMetrics()
    },
    recentActions: [],
    recentRuntimeEvents: [],
    recentMessages: []
  };
}

function publicOverlaySettings(config: PublicOverlayConfig) {
  return {
    profileId: config.profileId,
    theme: config.theme,
    showModerationActions: config.showModerationActions,
    showRuntimeStatus: config.showRuntimeStatus,
    showViewerStats: config.showViewerStats,
    showRecentChat: config.showRecentChat
  };
}

function selectOverlaySession(
  config: PublicOverlayConfig,
  sessions: StreamSessionRecord[]
): StreamSessionRecord | null {
  if (config.activeSessionId) {
    const configuredSession = sessions.find((session) => session.id === config.activeSessionId);
    if (configuredSession) {
      return configuredSession;
    }
  }

  return sessions.find((session) => !session.endedAt) ?? sessions[0] ?? null;
}

function overlayMetrics(logs: StreamSessionLogBundle) {
  return {
    messages: logs.messages.length,
    uniqueChatters: new Set(logs.messages.map((message) => message.authorChannelId)).size,
    moderationActions: logs.actions.length,
    destructiveActions: logs.actions.filter(isDestructiveAction).length,
    spamAttempts: logs.actions.filter(isSpamAttemptAction).length,
    commandsSent: logs.runtimeEvents.filter((event) => event.type === "command_sent").length,
    timersSent: logs.runtimeEvents.filter((event) => event.type === "timer_sent").length,
    reconnectEvents: logs.runtimeEvents.filter(isReconnectEvent).length
  };
}

function zeroMetrics() {
  return {
    messages: 0,
    uniqueChatters: 0,
    moderationActions: 0,
    destructiveActions: 0,
    spamAttempts: 0,
    commandsSent: 0,
    timersSent: 0,
    reconnectEvents: 0
  };
}

function recentActions(actions: ModerationActionLogRecord[]) {
  return actions
    .slice(-6)
    .reverse()
    .map((action) => ({
      id: action.id,
      actionType: action.actionType,
      label: actionLabel(action.actionType),
      severity: actionSeverity(action.actionType),
      reason: action.reason,
      reviewStatus: action.reviewStatus,
      createdAt: action.createdAt
    }));
}

function recentRuntimeEvents(events: RuntimeEventRecord[]) {
  return events
    .slice(-6)
    .reverse()
    .map((event) => ({
      id: event.id,
      type: event.type,
      message: event.message,
      createdAt: event.createdAt
    }));
}

function recentMessages(logs: StreamSessionLogBundle) {
  return logs.messages
    .slice(-5)
    .reverse()
    .map((message) => ({
      id: message.id,
      authorName: message.authorName,
      text: message.text,
      receivedAt: message.receivedAt
    }));
}

function isDestructiveAction(action: ModerationActionLogRecord): boolean {
  return ["deleteMessage", "hideUser", "timeoutUser"].includes(action.actionType);
}

function isSpamAttemptAction(action: ModerationActionLogRecord): boolean {
  if (action.reviewStatus === "false_positive") {
    return false;
  }
  if (action.actionType === "allow" || action.reason.startsWith("manual_") || action.reason === "auto_reply") {
    return false;
  }
  return true;
}

function isReconnectEvent(event: RuntimeEventRecord): boolean {
  const text = `${event.type} ${event.message}`.toLowerCase();
  return text.includes("reconnect") || text.includes("backoff") || text.includes("waiting for network");
}

function actionLabel(actionType: string): string {
  const labels: Record<string, string> = {
    allow: "Allowed",
    flagForReview: "Flagged",
    deleteMessage: "Deleted",
    hideUser: "Hidden",
    timeoutUser: "Timed out",
    unbanUser: "Unbanned",
    warnUser: "Warned",
    strikeUser: "Strike",
    sendAutoReply: "Replied"
  };
  return labels[actionType] ?? actionType;
}

function actionSeverity(actionType: string): "info" | "warning" | "critical" {
  if (actionType === "hideUser" || actionType === "timeoutUser") {
    return "critical";
  }
  if (actionType === "deleteMessage" || actionType === "flagForReview" || actionType === "warnUser" || actionType === "strikeUser") {
    return "warning";
  }
  return "info";
}

function renderOverlayHtml(config: PublicOverlayConfig): string {
  const themeClass = config.theme === "high_contrast"
    ? "theme-high-contrast"
    : config.theme === "transparent_minimal"
      ? "theme-transparent-minimal"
      : "theme-control-room";

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>ChatMod Mobile Overlay</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: rgba(5, 8, 12, 0.76);
      --panel: rgba(18, 24, 31, 0.82);
      --line: rgba(244, 247, 251, 0.16);
      --text: #f6f8fb;
      --muted: #aeb9c8;
      --hot: #ff5c7a;
      --ok: #55d6a5;
      --warn: #ffd166;
      --ink: #0b0f14;
      font-family: "Aptos", "Segoe UI Variable", "Segoe UI", sans-serif;
    }
    * { box-sizing: border-box; }
    html, body {
      width: 100%;
      min-height: 100%;
      margin: 0;
      background: transparent;
      overflow: hidden;
    }
    body {
      color: var(--text);
      padding: 18px;
      letter-spacing: 0;
    }
    .overlay {
      width: min(720px, 100vw - 36px);
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--bg);
      box-shadow: 0 22px 70px rgba(0, 0, 0, 0.28);
      backdrop-filter: blur(18px);
      padding: 16px;
    }
    .theme-transparent-minimal .overlay {
      width: min(560px, 100vw - 36px);
      background: rgba(5, 8, 12, 0.42);
      box-shadow: none;
      backdrop-filter: blur(10px);
    }
    .theme-high-contrast {
      --bg: rgba(0, 0, 0, 0.92);
      --panel: #101010;
      --line: #ffffff;
      --muted: #ffffff;
      --hot: #ff3158;
      --ok: #00ff99;
      --warn: #ffe100;
    }
    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 14px;
      margin-bottom: 14px;
    }
    .brand {
      min-width: 0;
    }
    .kicker {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--muted);
      font-size: 12px;
      font-weight: 700;
      text-transform: uppercase;
    }
    .pulse {
      width: 9px;
      height: 9px;
      border-radius: 999px;
      background: var(--warn);
      box-shadow: 0 0 0 5px rgba(255, 209, 102, 0.14);
    }
    .is-live .pulse {
      background: var(--ok);
      box-shadow: 0 0 0 5px rgba(85, 214, 165, 0.16);
      animation: pulse 1.7s ease-in-out infinite;
    }
    h1 {
      margin: 5px 0 0;
      font-size: 22px;
      line-height: 1.08;
      font-weight: 780;
      letter-spacing: 0;
      max-width: 520px;
      overflow-wrap: anywhere;
    }
    .status {
      border: 1px solid var(--line);
      border-radius: 999px;
      padding: 8px 11px;
      color: var(--text);
      font-size: 12px;
      font-weight: 760;
      background: rgba(255, 255, 255, 0.06);
      white-space: nowrap;
    }
    .metrics {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 8px;
      margin-bottom: 10px;
    }
    .metric, .feed {
      border: 1px solid var(--line);
      border-radius: 8px;
      background: var(--panel);
    }
    .metric {
      min-height: 74px;
      padding: 10px;
    }
    .label {
      color: var(--muted);
      font-size: 11px;
      font-weight: 720;
      text-transform: uppercase;
    }
    .value {
      margin-top: 5px;
      font-size: 25px;
      line-height: 1;
      font-weight: 820;
      font-variant-numeric: tabular-nums;
    }
    .feed {
      display: grid;
      grid-template-columns: 1.15fr 0.85fr;
      gap: 0;
      min-height: 134px;
      overflow: hidden;
    }
    .rail {
      padding: 11px;
    }
    .rail + .rail {
      border-left: 1px solid var(--line);
    }
    .row {
      display: grid;
      grid-template-columns: 78px 1fr;
      gap: 9px;
      align-items: baseline;
      padding: 7px 0;
      border-top: 1px solid rgba(244, 247, 251, 0.09);
      font-size: 13px;
    }
    .row:first-of-type {
      border-top: 0;
    }
    .tag {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 999px;
      min-height: 23px;
      padding: 3px 7px;
      color: var(--ink);
      background: var(--ok);
      font-size: 10px;
      font-weight: 840;
      text-transform: uppercase;
      white-space: nowrap;
    }
    .tag.warning { background: var(--warn); }
    .tag.critical { background: var(--hot); color: #fff; }
    .detail {
      min-width: 0;
      color: var(--text);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .empty {
      color: var(--muted);
      font-size: 13px;
      padding-top: 8px;
    }
    @keyframes pulse {
      50% { transform: scale(1.22); }
    }
    @media (max-width: 560px) {
      body { padding: 10px; }
      .overlay { width: calc(100vw - 20px); padding: 12px; }
      header { align-items: flex-start; flex-direction: column; gap: 9px; }
      .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .feed { grid-template-columns: 1fr; }
      .rail + .rail { border-left: 0; border-top: 1px solid var(--line); }
    }
  </style>
</head>
<body class="${themeClass}">
  <section class="overlay" id="overlay">
    <header>
      <div class="brand">
        <div class="kicker"><span class="pulse"></span><span>ChatMod Mobile</span></div>
        <h1 id="title">Waiting for stream</h1>
      </div>
      <div class="status" id="status">Connecting</div>
    </header>
    <div class="metrics" id="metrics"></div>
    <div class="feed">
      <div class="rail">
        <div class="label">Moderation</div>
        <div id="actions"></div>
      </div>
      <div class="rail">
        <div class="label">Runtime</div>
        <div id="runtime"></div>
      </div>
    </div>
  </section>
  <script>
    const stateUrl = location.pathname.replace(/\\/$/, "") + "/state";
    const overlay = document.getElementById("overlay");
    const title = document.getElementById("title");
    const status = document.getElementById("status");
    const metrics = document.getElementById("metrics");
    const actions = document.getElementById("actions");
    const runtime = document.getElementById("runtime");

    const metricLabels = [
      ["messages", "Messages"],
      ["uniqueChatters", "Chatters"],
      ["destructiveActions", "Actions"],
      ["spamAttempts", "Spam"]
    ];

    function text(value) {
      return value == null || value === "" ? "None" : String(value);
    }

    function row(tag, severity, detail) {
      const element = document.createElement("div");
      element.className = "row";
      const badge = document.createElement("span");
      badge.className = "tag " + (severity || "info");
      badge.textContent = tag;
      const copy = document.createElement("span");
      copy.className = "detail";
      copy.textContent = text(detail);
      element.append(badge, copy);
      return element;
    }

    function empty(label) {
      const element = document.createElement("div");
      element.className = "empty";
      element.textContent = label;
      return element;
    }

    function drawMetrics(state) {
      metrics.replaceChildren();
      for (const [key, label] of metricLabels) {
        const item = document.createElement("div");
        item.className = "metric";
        const labelElement = document.createElement("div");
        labelElement.className = "label";
        labelElement.textContent = label;
        const valueElement = document.createElement("div");
        valueElement.className = "value";
        valueElement.textContent = Number(state.metrics[key] || 0).toLocaleString();
        item.append(labelElement, valueElement);
        metrics.append(item);
      }
    }

    function drawList(target, rows, emptyLabel, mapper) {
      target.replaceChildren();
      if (!rows || rows.length === 0) {
        target.append(empty(emptyLabel));
        return;
      }
      rows.slice(0, 4).forEach((item) => target.append(mapper(item)));
    }

    async function refresh() {
      try {
        const response = await fetch(stateUrl, { cache: "no-store" });
        if (!response.ok) throw new Error("state");
        const state = await response.json();
        overlay.classList.toggle("is-live", state.status === "live");
        title.textContent = state.session?.title || (state.status === "disabled" ? "Overlay paused" : "Waiting for stream");
        status.textContent = state.status === "live" ? "Live" : state.status === "ended" ? "Ended" : state.status === "disabled" ? "Paused" : "Standby";
        metrics.hidden = state.overlay && state.overlay.showViewerStats === false;
        drawMetrics(state);
        drawList(actions, state.recentActions, "No moderation actions", (item) => row(item.label, item.severity, item.reason));
        drawList(runtime, state.recentRuntimeEvents, "No runtime events", (item) => row(item.type.replaceAll("_", " "), "info", item.message));
      } catch {
        overlay.classList.remove("is-live");
        title.textContent = "Overlay reconnecting";
        status.textContent = "Retrying";
      }
    }

    refresh();
    setInterval(refresh, 2500);
  </script>
</body>
</html>`;
}
