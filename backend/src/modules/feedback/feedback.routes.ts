import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import type { LogStore, SupportEventRecord } from "../logs/logStore.js";

const betaInterestDeviceId = "launch-site-beta-interest";
const betaInterestInstallId = "public-beta-form";

const betaFeedbackCategorySchema = z.enum(["bug", "idea", "confusing", "pricing", "other"]);

const optionalTrimmedString = (maxLength: number) =>
  z.preprocess(
    (value) => (typeof value === "string" && value.trim().length === 0 ? undefined : value),
    z.string().trim().max(maxLength).optional()
  );

const betaFeedbackContextValueSchema = z.union([
  z.string().max(160),
  z.number().finite(),
  z.boolean(),
  z.null()
]);

const betaFeedbackInputSchema = z.object({
  category: betaFeedbackCategorySchema,
  message: z.string().trim().min(1).max(1000),
  occurredAt: z.string().datetime().optional(),
  appVersion: z.string().min(1).max(40).optional(),
  platform: z.enum(["android"]).default("android"),
  context: z.record(betaFeedbackContextValueSchema).default({})
}).superRefine((value, context) => {
  for (const key of Object.keys(value.context)) {
    if (!/^[a-zA-Z0-9_.-]{1,48}$/.test(key)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["context", key],
        message: "Feedback context keys must be short URL-safe identifiers."
      });
    }

    if (/(token|secret|password|email|message|chat|text|body|url)/i.test(key)) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["context", key],
        message: "Feedback context cannot contain sensitive content fields."
      });
    }
  }
});

export type BetaFeedbackInput = z.infer<typeof betaFeedbackInputSchema>;

const betaInterestInputSchema = z.object({
  email: z.string().trim().email().max(254).transform((value) => value.toLowerCase()),
  creatorName: optionalTrimmedString(80),
  channelUrl: optionalTrimmedString(300),
  message: optionalTrimmedString(500),
  source: z.literal("launch-site").default("launch-site")
}).superRefine((value, context) => {
  if (!value.channelUrl) {
    return;
  }

  let parsed: URL;
  try {
    parsed = new URL(value.channelUrl);
  } catch {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["channelUrl"],
      message: "Channel URL must be a valid YouTube URL."
    });
    return;
  }

  const hostname = parsed.hostname.toLowerCase();
  const isYouTubeHost =
    hostname === "youtube.com" ||
    hostname.endsWith(".youtube.com") ||
    hostname === "youtu.be" ||
    hostname.endsWith(".youtu.be");

  if ((parsed.protocol !== "https:" && parsed.protocol !== "http:") || !isYouTubeHost) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["channelUrl"],
      message: "Channel URL must point to YouTube."
    });
  }
});

export type BetaInterestInput = z.infer<typeof betaInterestInputSchema>;

export async function feedbackRoutes(
  app: FastifyInstance,
  options: { store: LogStore }
): Promise<void> {
  app.get("/beta", { preHandler: requireAuth }, async (request) => ({
    feedback: (await options.store.list(request.auth!))
      .filter(isBetaFeedbackSupportEvent)
      .map(toBetaFeedback)
  }));

  app.post("/beta", { preHandler: requireAuth }, async (request, reply) => {
    const body = betaFeedbackInputSchema.parse(request.body);
    const created = await options.store.create(request.auth!, {
      severity: "info",
      message: body.message,
      details: {
        eventType: "beta_feedback",
        category: body.category,
        occurredAt: body.occurredAt ?? new Date().toISOString(),
        appVersion: body.appVersion ?? null,
        platform: body.platform,
        context: body.context
      }
    });

    return reply.status(201).send(toBetaFeedback(created));
  });

  app.post("/beta-interest", {
    config: {
      rateLimit: {
        max: 12,
        timeWindow: "1 minute"
      }
    }
  }, async (request, reply) => {
    const body = betaInterestInputSchema.parse(request.body);
    const occurredAt = new Date().toISOString();
    const userAgent = request.headers["user-agent"];
    const created = await options.store.create({
      subject: "launch-site",
      deviceId: betaInterestDeviceId,
      installId: betaInterestInstallId
    }, {
      severity: "info",
      message: "Launch site beta interest submitted",
      details: {
        eventType: "beta_interest",
        email: body.email,
        creatorName: body.creatorName ?? null,
        channelUrl: body.channelUrl ?? null,
        note: body.message ?? null,
        source: body.source,
        occurredAt,
        userAgent: typeof userAgent === "string" ? userAgent.slice(0, 160) : null
      }
    });

    return reply.status(201).send({
      saved: true,
      id: created.id,
      receivedAt: created.createdAt
    });
  });
}

export function isBetaFeedbackSupportEvent(event: SupportEventRecord): boolean {
  return event.details?.eventType === "beta_feedback";
}

export function isBetaInterestSupportEvent(event: SupportEventRecord): boolean {
  return event.details?.eventType === "beta_interest";
}

function toBetaFeedback(event: SupportEventRecord): {
  id: string;
  category: BetaFeedbackInput["category"];
  message: string;
  occurredAt: string;
  appVersion: string | null;
  platform: "android";
  context: Record<string, unknown>;
  createdAt: string;
} {
  const details = event.details ?? {};
  return {
    id: event.id,
    category: betaFeedbackCategorySchema.catch("other").parse(details.category),
    message: event.message,
    occurredAt: typeof details.occurredAt === "string" ? details.occurredAt : event.createdAt,
    appVersion: typeof details.appVersion === "string" ? details.appVersion : null,
    platform: "android",
    context: isRecord(details.context) ? details.context : {},
    createdAt: event.createdAt
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
