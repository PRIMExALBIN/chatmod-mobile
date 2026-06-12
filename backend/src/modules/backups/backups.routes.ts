import type { FastifyInstance } from "fastify";
import { nanoid } from "nanoid";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import { commandInputSchema, createCommandStore, type CommandStore } from "../commands/commandStore.js";
import { createEntitlementStore, type EntitlementStore } from "../entitlements/entitlementStore.js";
import { assertWithinEntitlementLimit } from "../entitlements/entitlementQuota.js";
import { ensureProfileForSyncedResource } from "../profiles/profileProvisioning.js";
import { createProfileStore, type ProfileStore } from "../profiles/profileStore.js";
import { createTimerStore, timerInputSchema, type TimerStore } from "../timers/timerStore.js";
import { createBackupStore, profileBackupSchema, type BackupStore } from "./backupStore.js";

const commandBackupItemSchema = commandInputSchema
  .omit({ profileId: true })
  .extend({ id: z.string().min(1).max(128).optional() });

const timerBackupItemSchema = timerInputSchema
  .omit({ profileId: true })
  .extend({ id: z.string().min(1).max(128).optional() });

const settingsBackupSchema = z.object({
  profileId: z.string().min(1),
  channelId: z.string().min(1),
  profileName: z.string().min(1),
  commands: z.array(commandBackupItemSchema).max(200).default([]),
  timers: z.array(timerBackupItemSchema).max(200).default([]),
  clientVersion: z.string().optional()
});

const settingsBackupConfigSchema = z.object({
  kind: z.literal("chatmod-settings-v1"),
  commands: z.array(commandBackupItemSchema),
  timers: z.array(timerBackupItemSchema)
});

const restoreBackupSchema = z.object({
  targetProfileId: z.string().min(1).optional()
}).default({});

export interface BackupRoutesOptions {
  store?: BackupStore;
  commandStore?: CommandStore;
  timerStore?: TimerStore;
  profileStore?: ProfileStore;
  entitlementStore?: EntitlementStore;
}

export async function backupRoutes(app: FastifyInstance, options: BackupRoutesOptions = {}): Promise<void> {
  const store = options.store ?? createBackupStore();
  const commandStore = options.commandStore ?? createCommandStore();
  const timerStore = options.timerStore ?? createTimerStore();
  const profileStore = options.profileStore ?? createProfileStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();

  app.get("/", { preHandler: requireAuth }, async (request) => ({
    backups: await store.list(request.auth!)
  }));

  app.post("/moderation-profile", { preHandler: requireAuth }, async (request, reply) => {
    const body = profileBackupSchema.parse(request.body);
    return reply.status(201).send(await store.create(request.auth!, body));
  });

  app.post("/settings", { preHandler: requireAuth }, async (request, reply) => {
    const body = settingsBackupSchema.parse(request.body);
    const backup = await store.create(request.auth!, {
      profileId: body.profileId,
      channelId: body.channelId,
      profileName: body.profileName,
      clientVersion: body.clientVersion,
      config: {
        kind: "chatmod-settings-v1",
        commands: body.commands,
        timers: body.timers
      }
    });

    return reply.status(201).send({
      ...backup,
      commandCount: body.commands.length,
      timerCount: body.timers.length
    });
  });

  app.post("/:id/restore", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = restoreBackupSchema.parse(request.body ?? {});
    const backup = await store.get(request.auth!, params.id);
    const config = settingsBackupConfigSchema.parse(backup.config);
    const profileId = body.targetProfileId ?? backup.profileId;

    const existingCommands = await commandStore.list(request.auth!);
    const existingCommandIds = new Set(existingCommands.map((command) => command.id));
    const newCommandCount = config.commands.filter((command) => !command.id || !existingCommandIds.has(command.id)).length;
    await assertWithinEntitlementLimit({
      auth: request.auth!,
      entitlementStore,
      feature: "commandProfiles",
      currentCount: existingCommands.length,
      additionalCount: newCommandCount,
      resourceLabel: "Command"
    });

    const existingTimers = await timerStore.list(request.auth!);
    const existingTimerIds = new Set(existingTimers.map((timer) => timer.id));
    const newTimerCount = config.timers.filter((timer) => !timer.id || !existingTimerIds.has(timer.id)).length;
    await assertWithinEntitlementLimit({
      auth: request.auth!,
      entitlementStore,
      feature: "timedMessages",
      currentCount: existingTimers.length,
      additionalCount: newTimerCount,
      resourceLabel: "Timer"
    });
    await ensureProfileForSyncedResource({
      auth: request.auth!,
      profileStore,
      entitlementStore,
      profileId
    });

    const commands = [];
    for (const command of config.commands) {
      const input = commandInputSchema.parse({
        ...command,
        profileId
      });
      commands.push(command.id
        ? await commandStore.upsertWithId(request.auth!, command.id, input)
        : await commandStore.upsertWithId(request.auth!, `cmd-${nanoid()}`, input));
    }

    const timers = [];
    for (const timer of config.timers) {
      const input = timerInputSchema.parse({
        ...timer,
        profileId
      });
      timers.push(timer.id
        ? await timerStore.upsertWithId(request.auth!, timer.id, input)
        : await timerStore.upsertWithId(request.auth!, `timer-${nanoid()}`, input));
    }

    return {
      restoredAt: new Date().toISOString(),
      backupId: backup.id,
      profileId,
      commands,
      timers
    };
  });

  app.delete("/:id", { preHandler: requireAuth }, async (request, reply) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    await store.delete(request.auth!, params.id);
    return reply.status(204).send();
  });
}
