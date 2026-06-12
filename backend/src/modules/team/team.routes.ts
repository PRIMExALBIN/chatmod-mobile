import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { HttpError } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import {
  createTeamAccessStore,
  teamInviteCreateSchema,
  teamInviteRedeemSchema,
  type TeamAccessStore
} from "./teamAccessStore.js";

const profileParamsSchema = z.object({
  profileId: z.string().min(1)
});

const memberParamsSchema = profileParamsSchema.extend({
  memberId: z.string().min(1)
});

export interface TeamRoutesOptions {
  store?: TeamAccessStore;
  entitlementStore?: EntitlementStore;
  profileStore?: ProfileStore;
}

export async function teamRoutes(app: FastifyInstance, options: TeamRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createTeamAccessStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const profileStore = options.profileStore ?? createProfileStore();

  app.get("/profiles/:profileId/members", { preHandler: requireAuth }, async (request) => {
    const params = profileParamsSchema.parse(request.params);
    const entitlement = await entitlementStore.current(request.auth!);
    return {
      profileId: params.profileId,
      teamSeats: entitlement.features.teamSeats,
      extraSeats: extraTeamSeats(entitlement.features.teamSeats),
      members: await store.listForProfile(request.auth!, params.profileId)
    };
  });

  app.post("/profiles/:profileId/invites", { preHandler: requireAuth }, async (request, reply) => {
    const params = profileParamsSchema.parse(request.params);
    const body = teamInviteCreateSchema.parse(request.body);
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: params.profileId
    });
    await assertTeamSeatAvailable(request.auth!, params.profileId, entitlementStore, store);

    return reply.status(201).send(await store.createInvite(request.auth!, params.profileId, body));
  });

  app.delete("/profiles/:profileId/members/:memberId", { preHandler: requireAuth }, async (request) => {
    const params = memberParamsSchema.parse(request.params);
    return {
      member: await store.revoke(request.auth!, params.profileId, params.memberId)
    };
  });

  app.post("/invites/redeem", { preHandler: requireAuth }, async (request) => {
    const body = teamInviteRedeemSchema.parse(request.body);
    return {
      membership: await store.redeem(request.auth!, body)
    };
  });

  app.get("/memberships", { preHandler: requireAuth }, async (request) => ({
    memberships: await store.memberships(request.auth!)
  }));
}

async function assertTeamSeatAvailable(
  auth: AuthContext,
  profileId: string,
  entitlementStore: EntitlementStore,
  store: TeamAccessStore
): Promise<void> {
  const entitlement = await entitlementStore.current(auth);
  const extraSeats = extraTeamSeats(entitlement.features.teamSeats);
  const currentMembers = await store.countOpenSeats(auth, profileId);
  if (currentMembers < extraSeats) {
    return;
  }

  throw new HttpError(
    403,
    `Team moderator seat limit reached for ${entitlement.plan} plan (${extraSeats}).`
  );
}

function extraTeamSeats(teamSeats: number): number {
  return Math.max(0, teamSeats - 1);
}
