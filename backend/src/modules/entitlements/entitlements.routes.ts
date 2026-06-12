import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import { createEntitlementStore, type EntitlementStore } from "./entitlementStore.js";
import { GooglePlayBillingValidator, isGooglePlayBillingConfigured, missingGooglePlayEnv } from "./googlePlayBilling.js";

const validatePurchaseSchema = z.object({
  packageName: z.string().min(1).optional(),
  productId: z.string().min(1),
  purchaseToken: z.string().min(1)
});

export interface EntitlementRoutesOptions {
  store?: EntitlementStore;
}

export async function entitlementRoutes(app: FastifyInstance, options: EntitlementRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createEntitlementStore();
  const validator = new GooglePlayBillingValidator();

  app.get("/current", { preHandler: requireAuth }, async (request) => store.current(request.auth!));

  app.get("/google-play/config", { preHandler: requireAuth }, async () => ({
    configured: isGooglePlayBillingConfigured(),
    missingEnv: missingGooglePlayEnv()
  }));

  app.post("/google-play/validate", { preHandler: requireAuth }, async (request, reply) => {
    if (!isGooglePlayBillingConfigured()) {
      return reply.status(503).send({
        error: "GOOGLE_PLAY_BILLING_NOT_CONFIGURED",
        missingEnv: missingGooglePlayEnv()
      });
    }

    const body = validatePurchaseSchema.parse(request.body);
    const validation = await validator.validateSubscription(body);
    const entitlement = await store.upsert(request.auth!, validation);

    return {
      validation: {
        productId: validation.productId,
        status: validation.status.toLowerCase(),
        source: validation.source,
        currentPeriodEndsAt: validation.currentPeriodEndsAt?.toISOString() ?? null,
        orderId: validation.orderId,
        autoRenewing: validation.autoRenewing
      },
      entitlement
    };
  });
}
