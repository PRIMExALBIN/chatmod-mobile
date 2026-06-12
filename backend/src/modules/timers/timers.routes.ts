import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertWithinEntitlementLimit } from "../entitlements/entitlementQuota.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { dueTimers } from "./timerRuntime.js";
import { createTimerStore, timerInputSchema, timerPatchSchema, type TimerStore } from "./timerStore.js";

const dueTimerSchema = z.object({
  profileId: z.string().min(1),
  messagesSinceLastTimer: z.number().int().min(0),
  now: z.string().datetime().optional(),
  streamStartedAt: z.string().datetime().optional()
});

const markSentSchema = z.object({
  sentAt: z.string().datetime().optional()
});

export interface TimerRoutesOptions {
  store?: TimerStore;
  profileStore?: ProfileStore;
  entitlementStore?: EntitlementStore;
}

export async function timerRoutes(app: FastifyInstance, options: TimerRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createTimerStore();
  const profileStore = options.profileStore ?? createProfileStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();

  app.get("/", { preHandler: requireAuth }, async (request) => {
    const query = z.object({ profileId: z.string().min(1).optional() }).parse(request.query);
    return { timers: await store.list(request.auth!, query.profileId) };
  });

  app.post("/due", { preHandler: requireAuth }, async (request) => {
    const body = dueTimerSchema.parse(request.body);
    const timers = await store.list(request.auth!, body.profileId);

    return {
      timers: dueTimers(timers, {
        messagesSinceLastTimer: body.messagesSinceLastTimer,
        now: body.now ? new Date(body.now) : new Date(),
        streamStartedAt: body.streamStartedAt ? new Date(body.streamStartedAt) : null
      })
    };
  });

  app.post("/", { preHandler: requireAuth }, async (request, reply) => {
    const body = timerInputSchema.parse(request.body);
    await assertCanCreateTimer({
      auth: request.auth!,
      store,
      entitlementStore
    });
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: body.profileId
    });
    return reply.status(201).send(await store.create(request.auth!, body));
  });

  app.put("/:id", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = timerInputSchema.parse(request.body);
    await assertCanCreateTimer({
      auth: request.auth!,
      store,
      entitlementStore,
      existingTimerId: params.id
    });
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: body.profileId
    });
    return store.upsertWithId(request.auth!, params.id, body);
  });

  app.patch("/:id", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const patch = timerPatchSchema.parse(request.body);
    return store.update(request.auth!, params.id, patch);
  });

  app.post("/:id/mark-sent", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = markSentSchema.parse(request.body);

    return store.markSent(request.auth!, params.id, body.sentAt ? new Date(body.sentAt) : new Date());
  });

  app.delete("/:id", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    await store.delete(request.auth!, params.id);
    return reply.status(204).send();
  });
}

async function assertCanCreateTimer(input: {
  auth: AuthContext;
  store: TimerStore;
  entitlementStore: EntitlementStore;
  existingTimerId?: string;
}): Promise<void> {
  const timers = await input.store.list(input.auth);
  if (input.existingTimerId && timers.some((timer) => timer.id === input.existingTimerId)) {
    return;
  }

  await assertWithinEntitlementLimit({
    auth: input.auth,
    entitlementStore: input.entitlementStore,
    feature: "timedMessages",
    currentCount: timers.length,
    resourceLabel: "Timer"
  });
}
