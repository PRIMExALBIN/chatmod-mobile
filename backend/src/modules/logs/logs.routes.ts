import type { FastifyInstance } from "fastify";
import { requireAuth } from "../../plugins/auth.js";
import { isAnalyticsSupportEvent } from "../analytics/analytics.routes.js";
import { isBetaFeedbackSupportEvent, isBetaInterestSupportEvent } from "../feedback/feedback.routes.js";
import { createApiErrorStore, type ApiErrorStore } from "./apiErrorStore.js";
import { createLogStore, type LogStore, supportEventInputSchema } from "./logStore.js";

export interface LogRoutesOptions {
  supportStore?: LogStore;
  apiErrorStore?: ApiErrorStore;
}

export async function logRoutes(app: FastifyInstance, options: LogRoutesOptions = {}): Promise<void> {
  const store = options.supportStore ?? createLogStore();
  const apiErrorStore = options.apiErrorStore ?? createApiErrorStore();

  app.get("/support-events", { preHandler: requireAuth }, async (request) => ({
    events: (await store.list(request.auth!)).filter(
      (event) => !isAnalyticsSupportEvent(event) && !isBetaFeedbackSupportEvent(event) && !isBetaInterestSupportEvent(event)
    )
  }));

  app.get("/api-errors", { preHandler: requireAuth }, async (request) => ({
    errors: await apiErrorStore.list(request.auth!)
  }));

  app.post("/support-events", { preHandler: requireAuth }, async (request, reply) => {
    const body = supportEventInputSchema.parse(request.body);
    return reply.status(201).send(await store.create(request.auth!, body));
  });
}
