import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { liveChatTextSchema } from "../youtube/youtubeMessageSafety.js";

const keywordSchema = z.string()
  .trim()
  .min(2)
  .max(40)
  .transform((value) => value.toLowerCase());

export const faqEntryInputSchema = z.object({
  profileId: z.string().min(1),
  question: z.string().trim().min(3).max(160),
  answer: liveChatTextSchema,
  keywords: z.array(keywordSchema).max(20).default([]),
  enabled: z.boolean().default(true)
});

export type FaqEntryInput = z.infer<typeof faqEntryInputSchema>;

export interface FaqEntryRecord extends FaqEntryInput {
  id: string;
  createdAt: string;
  updatedAt: string;
}

export interface FaqEntryStore {
  list(auth: AuthContext, profileId: string): Promise<FaqEntryRecord[]>;
  get(auth: AuthContext, id: string): Promise<FaqEntryRecord>;
  upsertWithId(auth: AuthContext, id: string, input: FaqEntryInput): Promise<FaqEntryRecord>;
  delete(auth: AuthContext, id: string): Promise<void>;
}

export function createFaqEntryStore(): FaqEntryStore {
  return shouldUsePrisma() ? new PrismaFaqEntryStore(getPrismaClient()) : new InMemoryFaqEntryStore();
}

class InMemoryFaqEntryStore implements FaqEntryStore {
  private readonly entries = new Map<string, FaqEntryRecord & { ownerKey: string }>();

  async list(auth: AuthContext, profileId: string): Promise<FaqEntryRecord[]> {
    return Array.from(this.entries.values())
      .filter((entry) => entry.ownerKey === auth.deviceId && entry.profileId === profileId)
      .sort((left, right) => left.question.localeCompare(right.question))
      .map(stripOwner);
  }

  async get(auth: AuthContext, id: string): Promise<FaqEntryRecord> {
    const existing = this.entries.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("FAQ entry not found.");
    }

    return stripOwner(existing);
  }

  async upsertWithId(auth: AuthContext, id: string, input: FaqEntryInput): Promise<FaqEntryRecord> {
    const now = new Date().toISOString();
    const existing = this.entries.get(id);
    if (existing && existing.ownerKey !== auth.deviceId) {
      throw notFound("FAQ entry not found.");
    }

    const row: FaqEntryRecord & { ownerKey: string } = {
      ...input,
      id: id || nanoid(),
      ownerKey: auth.deviceId,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };
    this.entries.set(row.id, row);
    return stripOwner(row);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const existing = this.entries.get(id);
    if (existing?.ownerKey === auth.deviceId) {
      this.entries.delete(id);
    }
  }
}

class PrismaFaqEntryStore implements FaqEntryStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext, profileId: string): Promise<FaqEntryRecord[]> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const rows = await this.prisma.faqEntry.findMany({
      where: {
        profileId,
        profile: {
          userId
        }
      },
      orderBy: { question: "asc" }
    });

    return rows.map(mapFaqEntry);
  }

  async get(auth: AuthContext, id: string): Promise<FaqEntryRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const row = await this.prisma.faqEntry.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
    if (!row) {
      throw notFound("FAQ entry not found.");
    }

    return mapFaqEntry(row);
  }

  async upsertWithId(auth: AuthContext, id: string, input: FaqEntryInput): Promise<FaqEntryRecord> {
    await this.ensureProfile(auth, input.profileId);
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.faqEntry.findUnique({
      where: { id },
      select: {
        profile: {
          select: { userId: true }
        }
      }
    });
    if (existing && existing.profile.userId !== userId) {
      throw notFound("FAQ entry not found.");
    }

    const data = {
      profileId: input.profileId,
      question: input.question,
      answer: input.answer,
      keywordsJson: input.keywords as Prisma.InputJsonValue,
      enabled: input.enabled
    };
    const row = existing
      ? await this.prisma.faqEntry.update({
        where: { id },
        data
      })
      : await this.prisma.faqEntry.create({
        data: {
          id,
          ...data
        }
      });

    return mapFaqEntry(row);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    await this.prisma.faqEntry.deleteMany({
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

function mapFaqEntry(row: {
  id: string;
  profileId: string;
  question: string;
  answer: string;
  keywordsJson: unknown;
  enabled: boolean;
  createdAt: Date;
  updatedAt: Date;
}): FaqEntryRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    question: row.question,
    answer: row.answer,
    keywords: Array.isArray(row.keywordsJson) ? row.keywordsJson.filter(isString) : [],
    enabled: row.enabled,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}

function stripOwner<T extends { ownerKey: string }>(row: T): Omit<T, "ownerKey"> {
  const { ownerKey: _ownerKey, ...record } = row;
  return record;
}

function isString(value: unknown): value is string {
  return typeof value === "string";
}
