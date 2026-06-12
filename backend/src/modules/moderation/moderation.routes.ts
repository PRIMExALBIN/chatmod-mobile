import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertAdvancedModerationFiltersAllowed } from "./moderationFeatureGate.js";
import { chatMessageSchema, evaluateMessage, moderationProfileSchema } from "./ruleEngine.js";
import { evaluateModerationSuggestion, moderationSuggestionRequestSchema } from "./moderationSuggestions.js";
import {
  createAiSuggestionUsageStore,
  type AiSuggestionUsage,
  type AiSuggestionUsageReservation,
  type AiSuggestionUsageStore
} from "./aiSuggestionUsageStore.js";

const evaluateSchema = z.object({
  message: chatMessageSchema,
  profile: moderationProfileSchema
});

export interface ModerationRoutesOptions {
  entitlementStore?: EntitlementStore;
  aiSuggestionUsageStore?: AiSuggestionUsageStore;
}

export async function moderationRoutes(app: FastifyInstance, options: ModerationRoutesOptions = {}): Promise<void> {
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const aiSuggestionUsageStore = options.aiSuggestionUsageStore ?? createAiSuggestionUsageStore();

  app.post("/rules/evaluate", { preHandler: requireAuth }, async (request) => {
    const body = evaluateSchema.parse(request.body);
    await assertAdvancedModerationFiltersAllowed({
      auth: request.auth!,
      entitlementStore,
      profile: body.profile
    });
    return evaluateMessage(body.message, body.profile);
  });

  app.post("/suggestions/evaluate", { preHandler: requireAuth }, async (request, reply) => {
    const body = moderationSuggestionRequestSchema.parse(request.body);
    const entitlement = await entitlementStore.current(request.auth!);
    if (!entitlement.features.aiSuggestions) {
      return reply.status(403).send({
        error: "AI_SUGGESTIONS_REQUIRED",
        message: "AI moderation suggestions require the Creator plan and always require manual approval."
      });
    }

    const usage = await aiSuggestionUsageStore.reserve(request.auth!, entitlement.features.aiSuggestionDailyLimit);
    if (!usage.allowed) {
      reply.header("retry-after", retryAfterSeconds(usage).toString());
      return reply.status(429).send({
        error: "AI_SUGGESTION_LIMIT_REACHED",
        message: `Daily AI moderation suggestion limit reached for the ${entitlement.plan} plan.`,
        usage: publicUsage(usage)
      });
    }

    await assertAdvancedModerationFiltersAllowed({
      auth: request.auth!,
      entitlementStore,
      profile: body.profile
    });

    return {
      ...evaluateModerationSuggestion(body),
      usage: publicUsage(usage)
    };
  });
}

function publicUsage(usage: AiSuggestionUsageReservation): AiSuggestionUsage {
  return {
    used: usage.used,
    limit: usage.limit,
    remaining: usage.remaining,
    resetAt: usage.resetAt
  };
}

function retryAfterSeconds(usage: AiSuggestionUsageReservation): number {
  const resetAt = Date.parse(usage.resetAt);
  if (!Number.isFinite(resetAt)) {
    return 60;
  }

  return Math.max(1, Math.ceil((resetAt - Date.now()) / 1000));
}
