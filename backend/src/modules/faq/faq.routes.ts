import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { HttpError } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createFaqEntryStore, faqEntryInputSchema, type FaqEntryStore } from "./faqStore.js";
import { suggestFaqReply } from "./faqReplyEngine.js";

const faqReplySuggestionSchema = z.object({
  profileId: z.string().min(1),
  message: z.object({
    text: z.string().min(1).max(2000),
    authorName: z.string().min(1).max(120).optional()
  }),
  minConfidence: z.number().min(0).max(1).default(0.45)
});

export interface FaqRoutesOptions {
  store?: FaqEntryStore;
  profileStore?: ProfileStore;
  entitlementStore?: EntitlementStore;
}

export async function faqRoutes(app: FastifyInstance, options: FaqRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createFaqEntryStore();
  const profileStore = options.profileStore ?? createProfileStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();

  app.get("/", { preHandler: requireAuth }, async (request) => {
    await assertFaqAllowed(request.auth!, entitlementStore);
    const query = z.object({ profileId: z.string().min(1) }).parse(request.query);
    return { faqEntries: await store.list(request.auth!, query.profileId) };
  });

  app.put("/:id", { preHandler: requireAuth }, async (request) => {
    await assertFaqAllowed(request.auth!, entitlementStore);
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = faqEntryInputSchema.parse(request.body);
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: body.profileId
    });
    return store.upsertWithId(request.auth!, params.id, body);
  });

  app.delete("/:id", { preHandler: requireAuth }, async (request, reply) => {
    await assertFaqAllowed(request.auth!, entitlementStore);
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    await store.delete(request.auth!, params.id);
    return reply.status(204).send();
  });

  app.post("/suggest-reply", { preHandler: requireAuth }, async (request, reply) => {
    await assertFaqAllowed(request.auth!, entitlementStore);
    const body = faqReplySuggestionSchema.parse(request.body);
    const entries = await store.list(request.auth!, body.profileId);
    const suggestion = suggestFaqReply({
      entries,
      messageText: body.message.text,
      minConfidence: body.minConfidence
    });

    return reply.send(suggestion);
  });
}

async function assertFaqAllowed(auth: AuthContext, entitlementStore: EntitlementStore): Promise<void> {
  const entitlement = await entitlementStore.current(auth);
  if (entitlement.features.aiSuggestions) {
    return;
  }

  throw Object.assign(new HttpError(403, "AI FAQ replies require the Creator plan."), {
    publicCode: "AI_FAQ_REQUIRED"
  });
}
