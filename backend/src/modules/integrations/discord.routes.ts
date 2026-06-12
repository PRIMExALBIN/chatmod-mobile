import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { HttpError } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import {
  createDiscordWebhookStore,
  type DiscordWebhookConfigRecord,
  type DiscordWebhookStore
} from "./discordWebhookStore.js";
import {
  buildDiscordAlertPayload,
  FetchDiscordWebhookSender,
  type DiscordWebhookSender
} from "./discordWebhookSender.js";

const profileQuerySchema = z.object({
  profileId: z.string().min(1)
});

const upsertWebhookSchema = z.object({
  profileId: z.string().min(1),
  webhookUrl: z.string().trim().min(1).optional(),
  enabled: z.boolean().default(true),
  alertModerationActions: z.boolean().default(true),
  alertRuntimeStatus: z.boolean().default(false)
});

const testWebhookSchema = z.object({
  profileId: z.string().min(1)
});

const discordAlertSchema = z.object({
  profileId: z.string().min(1),
  eventType: z.enum(["moderation_action", "runtime_status", "system"]),
  title: z.string().trim().min(1).max(120),
  detail: z.string().trim().min(1).max(1000),
  severity: z.enum(["info", "warning", "critical"]).default("info"),
  metadata: z.record(z.unknown()).default({})
});

export interface DiscordRoutesOptions {
  store?: DiscordWebhookStore;
  sender?: DiscordWebhookSender;
  entitlementStore?: EntitlementStore;
  profileStore?: ProfileStore;
}

export async function discordRoutes(app: FastifyInstance, options: DiscordRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createDiscordWebhookStore();
  const sender = options.sender ?? new FetchDiscordWebhookSender();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const profileStore = options.profileStore ?? createProfileStore();

  app.get("/webhook", { preHandler: requireAuth }, async (request) => {
    const query = profileQuerySchema.parse(request.query);
    return store.get(request.auth!, query.profileId);
  });

  app.put("/webhook", { preHandler: requireAuth }, async (request) => {
    const body = upsertWebhookSchema.parse(request.body);
    await assertDiscordAlertsAllowed(request.auth!, entitlementStore);
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: body.profileId
    });

    return store.upsert(request.auth!, body);
  });

  app.delete("/webhook", { preHandler: requireAuth }, async (request, reply) => {
    const query = profileQuerySchema.parse(request.query);
    await store.delete(request.auth!, query.profileId);
    return reply.status(204).send();
  });

  app.post("/test", { preHandler: requireAuth }, async (request) => {
    const body = testWebhookSchema.parse(request.body);
    await assertDiscordAlertsAllowed(request.auth!, entitlementStore);
    const config = await store.deliveryConfig(request.auth!, body.profileId);

    if (!config.configured || !config.webhookUrl) {
      throw new HttpError(409, "Add a Discord webhook before sending a test alert.");
    }
    if (!config.enabled) {
      throw new HttpError(409, "Enable Discord alerts before sending a test alert.");
    }

    await sender.send(
      config.webhookUrl,
      buildDiscordAlertPayload({
        title: "ChatMod Mobile test",
        detail: "Discord alerts are connected for this bot profile.",
        severity: "info",
        eventType: "system",
        profileId: body.profileId
      })
    );

    return {
      sent: true,
      sentAt: new Date().toISOString(),
      profileId: body.profileId
    };
  });

  app.post("/alerts", { preHandler: requireAuth }, async (request) => {
    const body = discordAlertSchema.parse(request.body);
    await assertDiscordAlertsAllowed(request.auth!, entitlementStore);
    const config = await store.deliveryConfig(request.auth!, body.profileId);
    const skippedReason = skippedDiscordAlertReason(config, body.eventType);

    if (skippedReason) {
      return {
        sent: false,
        skippedReason,
        profileId: body.profileId
      };
    }

    await sender.send(
      config.webhookUrl!,
      buildDiscordAlertPayload({
        title: body.title,
        detail: body.detail,
        severity: body.severity,
        eventType: body.eventType,
        profileId: body.profileId,
        metadata: body.metadata
      })
    );

    return {
      sent: true,
      sentAt: new Date().toISOString(),
      profileId: body.profileId
    };
  });
}

async function assertDiscordAlertsAllowed(auth: AuthContext, entitlementStore: EntitlementStore): Promise<void> {
  const entitlement = await entitlementStore.current(auth);
  if (entitlement.features.discordAlerts) {
    return;
  }

  throw new HttpError(403, "Discord alerts require Pro or Creator plan.");
}

function skippedDiscordAlertReason(
  config: DiscordWebhookConfigRecord & { webhookUrl: string | null },
  eventType: "moderation_action" | "runtime_status" | "system"
): string | null {
  if (!config.configured || !config.webhookUrl) {
    return "not_configured";
  }
  if (!config.enabled) {
    return "disabled";
  }
  if (eventType === "moderation_action" && !config.alertModerationActions) {
    return "moderation_alerts_disabled";
  }
  if (eventType === "runtime_status" && !config.alertRuntimeStatus) {
    return "runtime_alerts_disabled";
  }

  return null;
}
