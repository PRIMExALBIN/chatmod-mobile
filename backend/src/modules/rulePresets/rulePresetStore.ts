import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { moderationProfileSchema } from "../moderation/ruleEngine.js";

export const rulePresetInputSchema = z.object({
  profileId: z.string().min(1),
  name: z.string().min(1).max(80),
  config: moderationProfileSchema.default({}),
  isDefault: z.boolean().default(false)
});

export const rulePresetPatchSchema = rulePresetInputSchema.partial().omit({ profileId: true });

export type RulePresetInput = z.infer<typeof rulePresetInputSchema>;
export type RulePresetPatch = z.infer<typeof rulePresetPatchSchema>;

export interface RulePresetRecord extends RulePresetInput {
  id: string;
  createdAt: string;
  updatedAt: string;
}

export interface RulePresetStore {
  list(auth: AuthContext, profileId?: string): Promise<RulePresetRecord[]>;
  create(auth: AuthContext, input: RulePresetInput): Promise<RulePresetRecord>;
  upsertWithId(auth: AuthContext, id: string, input: RulePresetInput): Promise<RulePresetRecord>;
  update(auth: AuthContext, id: string, patch: RulePresetPatch): Promise<RulePresetRecord>;
  delete(auth: AuthContext, id: string): Promise<void>;
}

export function createRulePresetStore(): RulePresetStore {
  return shouldUsePrisma() ? new PrismaRulePresetStore(getPrismaClient()) : new InMemoryRulePresetStore();
}

class InMemoryRulePresetStore implements RulePresetStore {
  private presets = new Map<string, RulePresetRecord & { ownerKey: string }>();

  async list(auth: AuthContext, profileId?: string): Promise<RulePresetRecord[]> {
    return Array.from(this.presets.values())
      .filter((preset) => preset.ownerKey === auth.deviceId)
      .filter((preset) => (profileId ? preset.profileId === profileId : true))
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
      .map(({ ownerKey: _ownerKey, ...preset }) => preset);
  }

  async create(auth: AuthContext, input: RulePresetInput): Promise<RulePresetRecord> {
    return this.write(auth, nanoid(), input);
  }

  async upsertWithId(auth: AuthContext, id: string, input: RulePresetInput): Promise<RulePresetRecord> {
    const existing = this.presets.get(id);
    if (existing && existing.ownerKey !== auth.deviceId) {
      throw notFound("Rule preset not found.");
    }

    return this.write(auth, id, input, existing);
  }

  async update(auth: AuthContext, id: string, patch: RulePresetPatch): Promise<RulePresetRecord> {
    const existing = this.presets.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("Rule preset not found.");
    }

    const updatedInput = rulePresetInputSchema.parse({
      profileId: existing.profileId,
      name: patch.name ?? existing.name,
      config: patch.config ?? existing.config,
      isDefault: patch.isDefault ?? existing.isDefault
    });

    return this.write(auth, id, updatedInput, existing);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const existing = this.presets.get(id);
    if (existing?.ownerKey === auth.deviceId) {
      this.presets.delete(id);
    }
  }

  private write(
    auth: AuthContext,
    id: string,
    input: RulePresetInput,
    existing?: RulePresetRecord & { ownerKey: string }
  ): RulePresetRecord {
    if (input.isDefault) {
      for (const [presetId, preset] of this.presets.entries()) {
        if (preset.ownerKey === auth.deviceId && preset.profileId === input.profileId && presetId !== id) {
          this.presets.set(presetId, {
            ...preset,
            isDefault: false,
            updatedAt: new Date().toISOString()
          });
        }
      }
    }

    const now = new Date().toISOString();
    const row: RulePresetRecord & { ownerKey: string } = {
      ...input,
      id,
      ownerKey: auth.deviceId,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };
    this.presets.set(id, row);

    const { ownerKey: _ownerKey, ...preset } = row;
    return preset;
  }
}

class PrismaRulePresetStore implements RulePresetStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext, profileId?: string): Promise<RulePresetRecord[]> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const rows = await this.prisma.rulePreset.findMany({
      where: {
        profile: {
          userId
        },
        ...(profileId ? { profileId } : {})
      },
      orderBy: { createdAt: "asc" }
    });

    return rows.map(mapRulePreset);
  }

  async create(auth: AuthContext, input: RulePresetInput): Promise<RulePresetRecord> {
    await this.ensureProfile(auth, input.profileId);
    return this.prisma.$transaction(async (tx) => {
      if (input.isDefault) {
        await unsetDefaultPresets(tx, input.profileId);
      }

      const row = await tx.rulePreset.create({
        data: {
          profileId: input.profileId,
          name: input.name,
          configJson: input.config as Prisma.InputJsonValue,
          isDefault: input.isDefault
        }
      });

      return mapRulePreset(row);
    });
  }

  async upsertWithId(auth: AuthContext, id: string, input: RulePresetInput): Promise<RulePresetRecord> {
    await this.ensureProfile(auth, input.profileId);
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.rulePreset.findUnique({
      where: { id },
      select: {
        id: true,
        profile: {
          select: { userId: true }
        }
      }
    });

    if (existing && existing.profile.userId !== userId) {
      throw notFound("Rule preset not found.");
    }

    return this.prisma.$transaction(async (tx) => {
      if (input.isDefault) {
        await unsetDefaultPresets(tx, input.profileId, id);
      }

      const row = existing
        ? await tx.rulePreset.update({
          where: { id },
          data: {
            profileId: input.profileId,
            name: input.name,
            configJson: input.config as Prisma.InputJsonValue,
            isDefault: input.isDefault
          }
        })
        : await tx.rulePreset.create({
          data: {
            id,
            profileId: input.profileId,
            name: input.name,
            configJson: input.config as Prisma.InputJsonValue,
            isDefault: input.isDefault
          }
        });

      return mapRulePreset(row);
    });
  }

  async update(auth: AuthContext, id: string, patch: RulePresetPatch): Promise<RulePresetRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.rulePreset.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
    if (!existing) {
      throw notFound("Rule preset not found.");
    }

    const input = rulePresetInputSchema.parse({
      profileId: existing.profileId,
      name: patch.name ?? existing.name,
      config: patch.config ?? existing.configJson,
      isDefault: patch.isDefault ?? existing.isDefault
    });

    return this.prisma.$transaction(async (tx) => {
      if (input.isDefault) {
        await unsetDefaultPresets(tx, existing.profileId, id);
      }

      const row = await tx.rulePreset.update({
        where: { id },
        data: {
          name: input.name,
          configJson: input.config as Prisma.InputJsonValue,
          isDefault: input.isDefault
        }
      });

      return mapRulePreset(row);
    });
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    await this.prisma.rulePreset.deleteMany({
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

type RulePresetTransaction = Pick<PrismaClient, "rulePreset">;

async function unsetDefaultPresets(
  client: RulePresetTransaction,
  profileId: string,
  exceptId?: string
): Promise<void> {
  await client.rulePreset.updateMany({
    where: {
      profileId,
      ...(exceptId ? { id: { not: exceptId } } : {})
    },
    data: {
      isDefault: false
    }
  });
}

function mapRulePreset(row: {
  id: string;
  profileId: string;
  name: string;
  configJson: unknown;
  isDefault: boolean;
  createdAt: Date;
  updatedAt: Date;
}): RulePresetRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    name: row.name,
    config: moderationProfileSchema.parse(row.configJson ?? {}),
    isDefault: row.isDefault,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}
