import type { FastifyInstance } from "fastify";
import { requireAuth } from "../../plugins/auth.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertWithinEntitlementLimit } from "../entitlements/entitlementQuota.js";
import { createProfileStore, profileInputSchema, type ProfileStore } from "./profileStore.js";

export interface ProfileRoutesOptions {
  store?: ProfileStore;
  entitlementStore?: EntitlementStore;
}

export async function profileRoutes(app: FastifyInstance, options: ProfileRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createProfileStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();

  app.get("/", { preHandler: requireAuth }, async (request) => ({
    profiles: await store.list(request.auth!)
  }));

  app.post("/", { preHandler: requireAuth }, async (request, reply) => {
    const body = profileInputSchema.parse(request.body);
    const profiles = await store.list(request.auth!);
    await assertWithinEntitlementLimit({
      auth: request.auth!,
      entitlementStore,
      feature: "channelProfiles",
      currentCount: profiles.length,
      resourceLabel: "Channel profile"
    });
    return reply.status(201).send(await store.create(request.auth!, body));
  });
}
