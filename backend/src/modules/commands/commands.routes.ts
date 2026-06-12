import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { HttpError } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { evaluateCommand } from "./commandRuntime.js";
import { commandInputSchema, commandPatchSchema, createCommandStore, type CommandStore } from "./commandStore.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertWithinEntitlementLimit } from "../entitlements/entitlementQuota.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createYouTubeClientForAuth } from "../youtube/youtubeTokenStore.js";
import { liveChatTextSchema } from "../youtube/youtubeMessageSafety.js";
import { MockYouTubeLiveChatClient } from "../youtube/youtubeClient.js";
import { isYouTubeOAuthConfigured } from "../youtube/youtubeOAuth.js";

const commandEvaluationSchema = z.object({
  profileId: z.string().min(1),
  message: z.object({
    authorChannelId: z.string().min(1),
    authorName: z.string().min(1),
    text: z.string(),
    isOwner: z.boolean().optional(),
    isModerator: z.boolean().optional(),
    isMember: z.boolean().optional()
  }),
  cooldownState: z.object({
    commandLastUsedAt: z.record(z.string()).optional(),
    userCommandLastUsedAt: z.record(z.string()).optional()
  }).default({}),
  context: z.object({
    streamTitle: z.string().optional(),
    streamStartedAt: z.string().datetime().optional(),
    now: z.string().datetime().optional()
  }).default({})
});

const manualCommandSendSchema = z.object({
  liveChatId: z.string().min(1),
  streamTitle: z.string().max(160).optional(),
  streamStartedAt: z.string().datetime().optional()
});

export interface CommandRoutesOptions {
  store?: CommandStore;
  profileStore?: ProfileStore;
  entitlementStore?: EntitlementStore;
}

export async function commandRoutes(app: FastifyInstance, options: CommandRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createCommandStore();
  const profileStore = options.profileStore ?? createProfileStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const youtube = new MockYouTubeLiveChatClient();

  app.get("/", { preHandler: requireAuth }, async (request) => {
    const query = z.object({ profileId: z.string().min(1).optional() }).parse(request.query);
    return { commands: await store.list(request.auth!, query.profileId) };
  });

  app.post("/evaluate", { preHandler: requireAuth }, async (request) => {
    const body = commandEvaluationSchema.parse(request.body);
    const commands = await store.list(request.auth!, body.profileId);

    return evaluateCommand(body.message, commands, body.cooldownState, {
      streamTitle: body.context.streamTitle,
      streamStartedAt: body.context.streamStartedAt ? new Date(body.context.streamStartedAt) : undefined,
      now: body.context.now ? new Date(body.context.now) : undefined
    });
  });

  app.post("/:id/send", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = manualCommandSendSchema.parse(request.body);
    const command = await store.get(request.auth!, params.id);
    const evaluation = evaluateCommand(
      {
        authorChannelId: request.auth!.deviceId,
        authorName: "Creator",
        text: command.name,
        isOwner: true,
        isModerator: true,
        isMember: true
      },
      [command],
      {},
      {
        streamTitle: body.streamTitle,
        streamStartedAt: body.streamStartedAt ? new Date(body.streamStartedAt) : undefined
      }
    );

    if (!evaluation.matched || !evaluation.response) {
      throw new HttpError(409, evaluation.reason === "disabled" ? "Command is disabled." : "Command cannot be sent.");
    }

    const text = liveChatTextSchema.parse(evaluation.response);
    const client = await createYouTubeClientForAuth(request.auth!) ?? (
      isYouTubeOAuthConfigured() ? null : youtube
    );
    if (!client) {
      throw new HttpError(409, "Connect YouTube before using live chat actions.");
    }

    const sent = await client.sendMessage(body.liveChatId, text);
    return {
      commandId: command.id,
      commandName: command.name,
      liveChatId: body.liveChatId,
      messageId: sent.messageId,
      sentText: text,
      sentAt: new Date().toISOString()
    };
  });

  app.post("/", { preHandler: requireAuth }, async (request, reply) => {
    const body = commandInputSchema.parse(request.body);
    await assertCanCreateCommand({
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
    const body = commandInputSchema.parse(request.body);
    await assertCanCreateCommand({
      auth: request.auth!,
      store,
      entitlementStore,
      existingCommandId: params.id
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
    const patch = commandPatchSchema.parse(request.body);
    return store.update(request.auth!, params.id, patch);
  });

  app.delete("/:id", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    await store.delete(request.auth!, params.id);
    return reply.status(204).send();
  });
}

async function assertCanCreateCommand(input: {
  auth: AuthContext;
  store: CommandStore;
  entitlementStore: EntitlementStore;
  existingCommandId?: string;
}): Promise<void> {
  const commands = await input.store.list(input.auth);
  if (input.existingCommandId && commands.some((command) => command.id === input.existingCommandId)) {
    return;
  }

  await assertWithinEntitlementLimit({
    auth: input.auth,
    entitlementStore: input.entitlementStore,
    feature: "commandProfiles",
    currentCount: commands.length,
    resourceLabel: "Command"
  });
}
