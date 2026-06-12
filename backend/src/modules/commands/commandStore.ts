import type { PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { validateCommandResponseSafety } from "./commandSafety.js";

const commandResponseSchema = z.string().min(1).max(500).superRefine((value, context) => {
  const result = validateCommandResponseSafety(value);
  if (!result.safe) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      message: result.reason ?? "Command response contains an unsafe link."
    });
  }
});

export const commandInputSchema = z.object({
  profileId: z.string().min(1),
  name: z.string().regex(/^![a-z0-9_-]{1,32}$/i),
  response: commandResponseSchema,
  aliases: z.array(z.string().regex(/^![a-z0-9_-]{1,32}$/i)).default([]),
  cooldownSeconds: z.number().int().min(0).max(3600).default(30),
  accessLevel: z.enum(["everyone", "members", "mods", "owner"]).default("everyone"),
  enabled: z.boolean().default(true)
});

export const commandPatchSchema = commandInputSchema.partial().omit({ profileId: true });

export type CommandInput = z.infer<typeof commandInputSchema>;
export type CommandPatch = z.infer<typeof commandPatchSchema>;

export interface CommandRecord extends CommandInput {
  id: string;
  createdAt: string;
  updatedAt: string;
}

export interface CommandStore {
  list(auth: AuthContext, profileId?: string): Promise<CommandRecord[]>;
  get(auth: AuthContext, id: string): Promise<CommandRecord>;
  create(auth: AuthContext, input: CommandInput): Promise<CommandRecord>;
  upsertWithId(auth: AuthContext, id: string, input: CommandInput): Promise<CommandRecord>;
  update(auth: AuthContext, id: string, patch: CommandPatch): Promise<CommandRecord>;
  delete(auth: AuthContext, id: string): Promise<void>;
}

export function createCommandStore(): CommandStore {
  return shouldUsePrisma() ? new PrismaCommandStore(getPrismaClient()) : new InMemoryCommandStore();
}

class InMemoryCommandStore implements CommandStore {
  private commands = new Map<string, CommandRecord & { ownerKey: string }>();

  async list(auth: AuthContext, profileId?: string): Promise<CommandRecord[]> {
    return Array.from(this.commands.values())
      .filter((command) => command.ownerKey === auth.deviceId)
      .filter((command) => (profileId ? command.profileId === profileId : true))
      .map(({ ownerKey: _ownerKey, ...command }) => command);
  }

  async get(auth: AuthContext, id: string): Promise<CommandRecord> {
    const existing = this.commands.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("Command not found.");
    }

    const { ownerKey: _ownerKey, ...command } = existing;
    return command;
  }

  async create(auth: AuthContext, input: CommandInput): Promise<CommandRecord> {
    const now = new Date().toISOString();
    const row: CommandRecord & { ownerKey: string } = {
      ...input,
      id: nanoid(),
      ownerKey: auth.deviceId,
      createdAt: now,
      updatedAt: now
    };

    this.commands.set(row.id, row);
    const { ownerKey: _ownerKey, ...command } = row;
    return command;
  }

  async upsertWithId(auth: AuthContext, id: string, input: CommandInput): Promise<CommandRecord> {
    const now = new Date().toISOString();
    const existing = this.commands.get(id);

    if (existing && existing.ownerKey !== auth.deviceId) {
      throw notFound("Command not found.");
    }

    const row: CommandRecord & { ownerKey: string } = {
      ...input,
      id,
      ownerKey: auth.deviceId,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };

    this.commands.set(id, row);
    const { ownerKey: _ownerKey, ...command } = row;
    return command;
  }

  async update(auth: AuthContext, id: string, patch: CommandPatch): Promise<CommandRecord> {
    const existing = this.commands.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("Command not found.");
    }

    const updated = {
      ...existing,
      ...patch,
      updatedAt: new Date().toISOString()
    };
    this.commands.set(id, updated);
    const { ownerKey: _ownerKey, ...command } = updated;
    return command;
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const existing = this.commands.get(id);
    if (existing?.ownerKey === auth.deviceId) {
      this.commands.delete(id);
    }
  }
}

class PrismaCommandStore implements CommandStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext, profileId?: string): Promise<CommandRecord[]> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const rows = await this.prisma.command.findMany({
      where: {
        profile: {
          userId
        },
        ...(profileId ? { profileId } : {})
      },
      orderBy: { createdAt: "asc" }
    });

    return rows.map(mapCommand);
  }

  async get(auth: AuthContext, id: string): Promise<CommandRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const row = await this.prisma.command.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });

    if (!row) {
      throw notFound("Command not found.");
    }

    return mapCommand(row);
  }

  async create(auth: AuthContext, input: CommandInput): Promise<CommandRecord> {
    await this.ensureProfile(auth, input.profileId);
    const row = await this.prisma.command.create({
      data: {
        profileId: input.profileId,
        name: input.name,
        response: input.response,
        aliasesJson: input.aliases,
        cooldownSeconds: input.cooldownSeconds,
        accessLevel: input.accessLevel,
        enabled: input.enabled
      }
    });

    return mapCommand(row);
  }

  async upsertWithId(auth: AuthContext, id: string, input: CommandInput): Promise<CommandRecord> {
    await this.ensureProfile(auth, input.profileId);
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.command.findUnique({
      where: { id },
      select: {
        id: true,
        profile: {
          select: { userId: true }
        }
      }
    });

    if (existing && existing.profile.userId !== userId) {
      throw notFound("Command not found.");
    }

    const row = existing
      ? await this.prisma.command.update({
        where: { id },
        data: {
          profileId: input.profileId,
          name: input.name,
          response: input.response,
          aliasesJson: input.aliases,
          cooldownSeconds: input.cooldownSeconds,
          accessLevel: input.accessLevel,
          enabled: input.enabled
        }
      })
      : await this.prisma.command.create({
        data: {
          id,
          profileId: input.profileId,
          name: input.name,
          response: input.response,
          aliasesJson: input.aliases,
          cooldownSeconds: input.cooldownSeconds,
          accessLevel: input.accessLevel,
          enabled: input.enabled
        }
      });

    return mapCommand(row);
  }

  async update(auth: AuthContext, id: string, patch: CommandPatch): Promise<CommandRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.command.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
    if (!existing) {
      throw notFound("Command not found.");
    }

    const row = await this.prisma.command.update({
      where: { id },
      data: {
        name: patch.name,
        response: patch.response,
        aliasesJson: patch.aliases,
        cooldownSeconds: patch.cooldownSeconds,
        accessLevel: patch.accessLevel,
        enabled: patch.enabled
      }
    });

    return mapCommand(row);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    await this.prisma.command.deleteMany({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
  }

  private async ensureProfile(auth: AuthContext, profileId: string): Promise<void> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.channelProfile.findFirst({
      where: {
        id: profileId,
        userId
      },
      select: { id: true }
    });

    if (existing) {
      return;
    }

    await this.prisma.channelProfile.create({
      data: {
        id: profileId,
        userId,
        channelId: profileId,
        name: "Local default profile",
        configJson: {}
      }
    });
  }
}

function mapCommand(row: {
  id: string;
  profileId: string;
  name: string;
  response: string;
  aliasesJson: unknown;
  cooldownSeconds: number;
  accessLevel: string;
  enabled: boolean;
  createdAt: Date;
  updatedAt: Date;
}): CommandRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    name: row.name,
    response: row.response,
    aliases: Array.isArray(row.aliasesJson) ? row.aliasesJson.filter(isString) : [],
    cooldownSeconds: row.cooldownSeconds,
    accessLevel: commandInputSchema.shape.accessLevel.catch("everyone").parse(row.accessLevel),
    enabled: row.enabled,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}

function isString(value: unknown): value is string {
  return typeof value === "string";
}
