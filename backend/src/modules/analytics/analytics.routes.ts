import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import type { LogStore, SupportEventRecord } from "../logs/logStore.js";

const analyticsEventNameSchema = z.enum([
  "app_open",
  "bot_start",
  "bot_stop",
  "tab_selected",
  "command_saved",
  "timer_saved",
  "settings_changed",
  "diagnostic_sent"
]);

const analyticsPropertyValueSchema = z.union([
  z.string().max(160),
  z.number().finite(),
  z.boolean(),
  z.null()
]);

const analyticsEventSchema = z.object({
  name: analyticsEventNameSchema,
  consent: z.literal(true),
  occurredAt: z.string().datetime().optional(),
  appVersion: z.string().min(1).max(40).optional(),
  platform: z.enum(["android"]).default("android"),
  properties: z.record(analyticsPropertyValueSchema).default({})
}).superRefine((value, context) => {
  for (const key of Object.keys(value.properties)) {
    if (!/^[a-zA-Z0-9_.-]{1,48}$/.test(key)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["properties", key],
        message: "Analytics property keys must be short URL-safe identifiers."
      });
    }

    if (/(token|secret|password|email|message|chat|text|body|url)/i.test(key)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["properties", key],
        message: "Analytics properties cannot contain sensitive content fields."
      });
    }
  }
});

export type AnalyticsEventInput = z.infer<typeof analyticsEventSchema>;

export async function analyticsRoutes(
  app: FastifyInstance,
  options: { store: LogStore }
): Promise<void> {
  app.get("/events", { preHandler: requireAuth }, async (request) => ({
    events: (await options.store.list(request.auth!))
      .filter(isAnalyticsSupportEvent)
      .map(toAnalyticsEvent)
  }));

  app.post("/events", { preHandler: requireAuth }, async (request, reply) => {
    const body = analyticsEventSchema.parse(request.body);
    const created = await options.store.create(request.auth!, {
      severity: "info",
      message: "Usage analytics event",
      details: {
        eventType: "usage_analytics",
        name: body.name,
        consent: true,
        occurredAt: body.occurredAt ?? new Date().toISOString(),
        appVersion: body.appVersion ?? null,
        platform: body.platform,
        properties: body.properties
      }
    });

    return reply.status(201).send(toAnalyticsEvent(created));
  });
}

export function isAnalyticsSupportEvent(event: SupportEventRecord): boolean {
  return event.details?.eventType === "usage_analytics";
}

function toAnalyticsEvent(event: SupportEventRecord): {
  id: string;
  name: string;
  occurredAt: string;
  appVersion: string | null;
  platform: "android";
  properties: Record<string, unknown>;
  createdAt: string;
} {
  const details = event.details ?? {};
  return {
    id: event.id,
    name: typeof details.name === "string" ? details.name : "app_open",
    occurredAt: typeof details.occurredAt === "string" ? details.occurredAt : event.createdAt,
    appVersion: typeof details.appVersion === "string" ? details.appVersion : null,
    platform: "android",
    properties: isRecord(details.properties) ? details.properties : {},
    createdAt: event.createdAt
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
