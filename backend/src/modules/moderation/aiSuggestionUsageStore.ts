import type { PrismaClient } from "@prisma/client";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

const AiSuggestionUsageKey = "ai_suggestions";
const DayMillis = 24 * 60 * 60 * 1000;

export interface AiSuggestionUsage {
  used: number;
  limit: number;
  remaining: number;
  resetAt: string;
}

export interface AiSuggestionUsageReservation extends AiSuggestionUsage {
  allowed: boolean;
}

export interface AiSuggestionUsageStore {
  reserve(auth: AuthContext, limit: number, now?: Date): Promise<AiSuggestionUsageReservation>;
}

export function createAiSuggestionUsageStore(): AiSuggestionUsageStore {
  return shouldUsePrisma() ? new PrismaAiSuggestionUsageStore(getPrismaClient()) : new InMemoryAiSuggestionUsageStore();
}

export class InMemoryAiSuggestionUsageStore implements AiSuggestionUsageStore {
  private readonly counts = new Map<string, number>();

  async reserve(auth: AuthContext, limit: number, now = new Date()): Promise<AiSuggestionUsageReservation> {
    const windowStart = dailyWindowStart(now);
    const resetAt = dailyResetAt(windowStart).toISOString();
    const mapKey = `${auth.deviceId}:${AiSuggestionUsageKey}:${windowStart.toISOString()}`;
    const currentCount = this.counts.get(mapKey) ?? 0;

    if (limit <= 0 || currentCount >= limit) {
      return usageReservation(false, currentCount, limit, resetAt);
    }

    const used = currentCount + 1;
    this.counts.set(mapKey, used);
    return usageReservation(true, used, limit, resetAt);
  }
}

class PrismaAiSuggestionUsageStore implements AiSuggestionUsageStore {
  constructor(private readonly prisma: PrismaClient) {}

  async reserve(auth: AuthContext, limit: number, now = new Date()): Promise<AiSuggestionUsageReservation> {
    const windowStart = dailyWindowStart(now);
    const resetAt = dailyResetAt(windowStart).toISOString();
    if (limit <= 0) {
      return usageReservation(false, 0, limit, resetAt);
    }

    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const where = {
      userId_key_windowStart: {
        userId,
        key: AiSuggestionUsageKey,
        windowStart
      }
    };

    return this.prisma.$transaction(async (tx) => {
      await tx.usageCounter.upsert({
        where,
        create: {
          userId,
          key: AiSuggestionUsageKey,
          windowStart,
          count: 0
        },
        update: {}
      });

      const reserved = await tx.usageCounter.updateMany({
        where: {
          userId,
          key: AiSuggestionUsageKey,
          windowStart,
          count: {
            lt: limit
          }
        },
        data: {
          count: {
            increment: 1
          }
        }
      });

      const counter = await tx.usageCounter.findUnique({
        where,
        select: {
          count: true
        }
      });
      const used = counter?.count ?? 0;

      return usageReservation(reserved.count === 1, used, limit, resetAt);
    });
  }
}

function dailyWindowStart(now: Date): Date {
  return new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
}

function dailyResetAt(windowStart: Date): Date {
  return new Date(windowStart.getTime() + DayMillis);
}

function usageReservation(allowed: boolean, used: number, limit: number, resetAt: string): AiSuggestionUsageReservation {
  return {
    allowed,
    used,
    limit,
    remaining: Math.max(0, limit - used),
    resetAt
  };
}
