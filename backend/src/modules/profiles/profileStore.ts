import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const profileInputSchema = z.object({
  channelId: z.string().min(1),
  name: z.string().min(1).max(120),
  config: z.record(z.unknown()).default({})
});

export type ProfileInput = z.infer<typeof profileInputSchema>;

export interface ChannelProfileRecord {
  id: string;
  ownerKey: string;
  channelId: string;
  name: string;
  config: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

export interface ProfileStore {
  list(auth: AuthContext): Promise<ChannelProfileRecord[]>;
  create(auth: AuthContext, input: ProfileInput): Promise<ChannelProfileRecord>;
  ensureDefault(auth: AuthContext, profileId: string): Promise<ChannelProfileRecord>;
}

export function createProfileStore(): ProfileStore {
  return shouldUsePrisma() ? new PrismaProfileStore(getPrismaClient()) : new InMemoryProfileStore();
}

class InMemoryProfileStore implements ProfileStore {
  private profiles = new Map<string, ChannelProfileRecord>();

  async list(auth: AuthContext): Promise<ChannelProfileRecord[]> {
    return Array.from(this.profiles.values()).filter((profile) => profile.ownerKey === auth.deviceId);
  }

  async create(auth: AuthContext, input: ProfileInput): Promise<ChannelProfileRecord> {
    const now = new Date().toISOString();
    const row: ChannelProfileRecord = {
      id: nanoid(),
      ownerKey: auth.deviceId,
      channelId: input.channelId,
      name: input.name,
      config: input.config,
      createdAt: now,
      updatedAt: now
    };

    this.profiles.set(row.id, row);
    return row;
  }

  async ensureDefault(auth: AuthContext, profileId: string): Promise<ChannelProfileRecord> {
    const existing = Array.from(this.profiles.values())
      .find((profile) => profile.id === profileId && profile.ownerKey === auth.deviceId);
    if (existing) {
      return existing;
    }

    const now = new Date().toISOString();
    const row: ChannelProfileRecord = {
      id: profileId,
      ownerKey: auth.deviceId,
      channelId: profileId,
      name: "Local default profile",
      config: {},
      createdAt: now,
      updatedAt: now
    };

    this.profiles.set(`${auth.deviceId}:${row.id}`, row);
    return row;
  }
}

class PrismaProfileStore implements ProfileStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext): Promise<ChannelProfileRecord[]> {
    const userId = await this.resolveUserId(auth);
    const rows = await this.prisma.channelProfile.findMany({
      where: { userId },
      orderBy: { createdAt: "asc" }
    });

    return rows.map(mapProfile);
  }

  async create(auth: AuthContext, input: ProfileInput): Promise<ChannelProfileRecord> {
    const userId = await this.resolveUserId(auth);
    const row = await this.prisma.channelProfile.create({
      data: {
        userId,
        channelId: input.channelId,
        name: input.name,
        configJson: input.config as Prisma.InputJsonValue
      }
    });

    return mapProfile(row);
  }

  async ensureDefault(auth: AuthContext, profileId: string): Promise<ChannelProfileRecord> {
    const userId = await this.resolveUserId(auth);
    const existing = await this.prisma.channelProfile.findFirst({
      where: {
        id: profileId,
        userId
      }
    });

    if (existing) {
      return mapProfile(existing);
    }

    const row = await this.prisma.channelProfile.create({
      data: {
        id: profileId,
        userId,
        channelId: profileId,
        name: "Local default profile",
        configJson: {}
      }
    });

    return mapProfile(row);
  }

  private async resolveUserId(auth: AuthContext): Promise<string> {
    return resolveUserIdForDevice(this.prisma, auth);
  }
}

function mapProfile(row: {
  id: string;
  userId: string;
  channelId: string;
  name: string;
  configJson: unknown;
  createdAt: Date;
  updatedAt: Date;
}): ChannelProfileRecord {
  return {
    id: row.id,
    ownerKey: row.userId,
    channelId: row.channelId,
    name: row.name,
    config: typeof row.configJson === "object" && row.configJson !== null ? (row.configJson as Record<string, unknown>) : {},
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}
