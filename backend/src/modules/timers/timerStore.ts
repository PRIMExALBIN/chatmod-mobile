import type { PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const timerInputSchema = z.object({
  profileId: z.string().min(1),
  name: z.string().min(1).max(80),
  message: z.string().min(1).max(500),
  intervalMinutes: z.number().int().min(1).max(240),
  minChatMessages: z.number().int().min(0).max(500).default(0),
  quietStartMinutes: z.number().int().min(0).max(1440).nullable().default(null),
  quietEndMinutes: z.number().int().min(1).max(1440).nullable().default(null),
  enabled: z.boolean().default(true)
});

export const timerPatchSchema = timerInputSchema.partial().omit({ profileId: true });

export type TimerInput = z.infer<typeof timerInputSchema>;
export type TimerPatch = z.infer<typeof timerPatchSchema>;

export interface TimerRecord extends TimerInput {
  id: string;
  lastSentAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TimerStore {
  list(auth: AuthContext, profileId?: string): Promise<TimerRecord[]>;
  create(auth: AuthContext, input: TimerInput): Promise<TimerRecord>;
  upsertWithId(auth: AuthContext, id: string, input: TimerInput): Promise<TimerRecord>;
  update(auth: AuthContext, id: string, patch: TimerPatch): Promise<TimerRecord>;
  markSent(auth: AuthContext, id: string, sentAt: Date): Promise<TimerRecord>;
  delete(auth: AuthContext, id: string): Promise<void>;
}

export function createTimerStore(): TimerStore {
  return shouldUsePrisma() ? new PrismaTimerStore(getPrismaClient()) : new InMemoryTimerStore();
}

class InMemoryTimerStore implements TimerStore {
  private timers = new Map<string, TimerRecord & { ownerKey: string }>();

  async list(auth: AuthContext, profileId?: string): Promise<TimerRecord[]> {
    return Array.from(this.timers.values())
      .filter((timer) => timer.ownerKey === auth.deviceId)
      .filter((timer) => (profileId ? timer.profileId === profileId : true))
      .map(({ ownerKey: _ownerKey, ...timer }) => timer);
  }

  async create(auth: AuthContext, input: TimerInput): Promise<TimerRecord> {
    const now = new Date().toISOString();
    const row: TimerRecord & { ownerKey: string } = {
      ...input,
      id: nanoid(),
      ownerKey: auth.deviceId,
      lastSentAt: null,
      createdAt: now,
      updatedAt: now
    };

    this.timers.set(row.id, row);
    const { ownerKey: _ownerKey, ...timer } = row;
    return timer;
  }

  async upsertWithId(auth: AuthContext, id: string, input: TimerInput): Promise<TimerRecord> {
    const now = new Date().toISOString();
    const existing = this.timers.get(id);

    if (existing && existing.ownerKey !== auth.deviceId) {
      throw notFound("Timer not found.");
    }

    const row: TimerRecord & { ownerKey: string } = {
      ...input,
      id,
      ownerKey: auth.deviceId,
      lastSentAt: existing?.lastSentAt ?? null,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };

    this.timers.set(id, row);
    const { ownerKey: _ownerKey, ...timer } = row;
    return timer;
  }

  async update(auth: AuthContext, id: string, patch: TimerPatch): Promise<TimerRecord> {
    const existing = this.timers.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("Timer not found.");
    }

    const updated = {
      ...existing,
      ...patch,
      updatedAt: new Date().toISOString()
    };
    this.timers.set(id, updated);
    const { ownerKey: _ownerKey, ...timer } = updated;
    return timer;
  }

  async markSent(auth: AuthContext, id: string, sentAt: Date): Promise<TimerRecord> {
    const existing = this.timers.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("Timer not found.");
    }

    const updated = {
      ...existing,
      lastSentAt: sentAt.toISOString(),
      updatedAt: sentAt.toISOString()
    };
    this.timers.set(id, updated);
    const { ownerKey: _ownerKey, ...timer } = updated;
    return timer;
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const existing = this.timers.get(id);
    if (existing?.ownerKey === auth.deviceId) {
      this.timers.delete(id);
    }
  }
}

class PrismaTimerStore implements TimerStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext, profileId?: string): Promise<TimerRecord[]> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const rows = await this.prisma.timer.findMany({
      where: {
        profile: {
          userId
        },
        ...(profileId ? { profileId } : {})
      },
      orderBy: { createdAt: "asc" }
    });

    return rows.map(mapTimer);
  }

  async create(auth: AuthContext, input: TimerInput): Promise<TimerRecord> {
    await this.ensureProfile(auth, input.profileId);
    const row = await this.prisma.timer.create({
      data: {
        profileId: input.profileId,
        name: input.name,
        message: input.message,
        intervalMinutes: input.intervalMinutes,
        minChatMessages: input.minChatMessages,
        quietStartMinutes: input.quietStartMinutes,
        quietEndMinutes: input.quietEndMinutes,
        enabled: input.enabled
      }
    });

    return mapTimer(row);
  }

  async upsertWithId(auth: AuthContext, id: string, input: TimerInput): Promise<TimerRecord> {
    await this.ensureProfile(auth, input.profileId);
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.timer.findUnique({
      where: { id },
      select: {
        id: true,
        profile: {
          select: { userId: true }
        }
      }
    });

    if (existing && existing.profile.userId !== userId) {
      throw notFound("Timer not found.");
    }

    const row = existing
      ? await this.prisma.timer.update({
        where: { id },
        data: {
          profileId: input.profileId,
          name: input.name,
          message: input.message,
          intervalMinutes: input.intervalMinutes,
          minChatMessages: input.minChatMessages,
          quietStartMinutes: input.quietStartMinutes,
          quietEndMinutes: input.quietEndMinutes,
          enabled: input.enabled
        }
      })
      : await this.prisma.timer.create({
        data: {
          id,
          profileId: input.profileId,
          name: input.name,
          message: input.message,
          intervalMinutes: input.intervalMinutes,
          minChatMessages: input.minChatMessages,
          quietStartMinutes: input.quietStartMinutes,
          quietEndMinutes: input.quietEndMinutes,
          enabled: input.enabled
        }
      });

    return mapTimer(row);
  }

  async update(auth: AuthContext, id: string, patch: TimerPatch): Promise<TimerRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.timer.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
    if (!existing) {
      throw notFound("Timer not found.");
    }

    const row = await this.prisma.timer.update({
      where: { id },
      data: {
        name: patch.name,
        message: patch.message,
        intervalMinutes: patch.intervalMinutes,
        minChatMessages: patch.minChatMessages,
        quietStartMinutes: patch.quietStartMinutes,
        quietEndMinutes: patch.quietEndMinutes,
        enabled: patch.enabled
      }
    });

    return mapTimer(row);
  }

  async markSent(auth: AuthContext, id: string, sentAt: Date): Promise<TimerRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.timer.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
    if (!existing) {
      throw notFound("Timer not found.");
    }

    const row = await this.prisma.timer.update({
      where: { id },
      data: {
        lastSentAt: sentAt
      }
    });

    return mapTimer(row);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    await this.prisma.timer.deleteMany({
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

function mapTimer(row: {
  id: string;
  profileId: string;
  name: string;
  message: string;
  intervalMinutes: number;
  minChatMessages: number;
  quietStartMinutes: number | null;
  quietEndMinutes: number | null;
  enabled: boolean;
  lastSentAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
}): TimerRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    name: row.name,
    message: row.message,
    intervalMinutes: row.intervalMinutes,
    minChatMessages: row.minChatMessages,
    quietStartMinutes: row.quietStartMinutes,
    quietEndMinutes: row.quietEndMinutes,
    enabled: row.enabled,
    lastSentAt: row.lastSentAt?.toISOString() ?? null,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}
