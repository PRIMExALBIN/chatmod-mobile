import type { FastifyInstance } from "fastify";
import { nanoid } from "nanoid";
import { z } from "zod";
import { HttpError } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertAdvancedModerationFiltersAllowed } from "../moderation/moderationFeatureGate.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import {
  createRulePresetStore,
  rulePresetInputSchema,
  rulePresetPatchSchema,
  type RulePresetStore
} from "./rulePresetStore.js";
import { rulePresetTemplates } from "./rulePresetTemplates.js";

const RulePresetBundleVersion = 1;

const rulePresetBundlePresetSchema = z.object({
  id: z.string().min(1).max(120).optional(),
  name: z.string().min(1).max(80),
  config: rulePresetInputSchema.shape.config,
  isDefault: z.boolean().default(false)
});

const rulePresetImportSchema = z.object({
  profileId: z.string().min(1),
  bundle: z.object({
    formatVersion: z.literal(RulePresetBundleVersion).default(RulePresetBundleVersion),
    rulePresets: z.array(rulePresetBundlePresetSchema).min(1).max(25)
  })
}).superRefine((value, context) => {
  const defaultCount = value.bundle.rulePresets.filter((preset) => preset.isDefault).length;
  if (defaultCount > 1) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["bundle", "rulePresets"],
      message: "Only one imported rule preset can be marked as default."
    });
  }
});

export interface RulePresetRoutesOptions {
  store?: RulePresetStore;
  profileStore?: ProfileStore;
  entitlementStore?: EntitlementStore;
}

export async function rulePresetRoutes(
  app: FastifyInstance,
  options: RulePresetRoutesOptions = {}
): Promise<void> {
  const store = options.store ?? createRulePresetStore();
  const profileStore = options.profileStore ?? createProfileStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();

  app.get("/", { preHandler: requireAuth }, async (request) => {
    const query = z.object({ profileId: z.string().min(1).optional() }).parse(request.query);
    return { rulePresets: await store.list(request.auth!, query.profileId) };
  });

  app.get("/templates", { preHandler: requireAuth }, async () => {
    return { rulePresetTemplates };
  });

  app.get("/export", { preHandler: requireAuth }, async (request) => {
    const query = z.object({ profileId: z.string().min(1) }).parse(request.query);
    await assertPresetBundlesAllowed(request.auth!, entitlementStore);
    const rulePresets = await store.list(request.auth!, query.profileId);
    return {
      formatVersion: RulePresetBundleVersion,
      exportedAt: new Date().toISOString(),
      profileId: query.profileId,
      rulePresets
    };
  });

  app.post("/import", { preHandler: requireAuth }, async (request) => {
    const body = rulePresetImportSchema.parse(request.body);
    await assertPresetBundlesAllowed(request.auth!, entitlementStore);
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId: body.profileId
    });

    for (const preset of body.bundle.rulePresets) {
      await assertAdvancedModerationFiltersAllowed({
        auth: request.auth!,
        entitlementStore,
        profile: preset.config
      });
    }

    const imported = [];
    for (const preset of body.bundle.rulePresets) {
      imported.push(await store.upsertWithId(request.auth!, `imported-${nanoid()}`, {
        profileId: body.profileId,
        name: preset.name,
        config: preset.config,
        isDefault: preset.isDefault
      }));
    }

    return {
      importedAt: new Date().toISOString(),
      profileId: body.profileId,
      importedCount: imported.length,
      rulePresets: imported
    };
  });

  app.post("/", { preHandler: requireAuth }, async (request, reply) => {
    const body = rulePresetInputSchema.parse(request.body);
    await assertAdvancedModerationFiltersAllowed({
      auth: request.auth!,
      entitlementStore,
      profile: body.config
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
    const body = rulePresetInputSchema.parse(request.body);
    await assertAdvancedModerationFiltersAllowed({
      auth: request.auth!,
      entitlementStore,
      profile: body.config
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
    const patch = rulePresetPatchSchema.parse(request.body);
    if (patch.config) {
      await assertAdvancedModerationFiltersAllowed({
        auth: request.auth!,
        entitlementStore,
        profile: patch.config
      });
    }
    return store.update(request.auth!, params.id, patch);
  });

  app.delete("/:id", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    await store.delete(request.auth!, params.id);
    return reply.status(204).send();
  });
}

async function assertPresetBundlesAllowed(auth: AuthContext, entitlementStore: EntitlementStore): Promise<void> {
  const entitlement = await entitlementStore.current(auth);
  if (entitlement.features.presetBundles) {
    return;
  }

  throw new HttpError(403, "Rule preset import/export requires Pro or Creator plan.");
}
