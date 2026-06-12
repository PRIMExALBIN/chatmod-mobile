import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const apiErrorInputSchema = z.object({
  provider: z.string().min(1).max(80).default("backend"),
  code: z.string().min(1).max(120).nullable().optional(),
  message: z.string().min(1).max(1000),
  sessionId: z.string().min(1).nullable().optional(),
  metadata: z.record(z.unknown()).optional()
});

export type ApiErrorInput = z.infer<typeof apiErrorInputSchema>;

export interface ApiErrorRecord {
  id: string;
  sessionId: string | null;
  provider: string;
  code: string | null;
  message: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface ApiErrorStore {
  list(auth: AuthContext): Promise<ApiErrorRecord[]>;
  create(input: ApiErrorInput): Promise<ApiErrorRecord>;
}

export function createApiErrorStore(): ApiErrorStore {
  return shouldUsePrisma() ? new PrismaApiErrorStore(getPrismaClient()) : new InMemoryApiErrorStore();
}

class InMemoryApiErrorStore implements ApiErrorStore {
  private errors = new Map<string, ApiErrorRecord>();

  async list(auth: AuthContext): Promise<ApiErrorRecord[]> {
    return Array.from(this.errors.values())
      .filter((error) => error.metadata?.deviceId === auth.deviceId)
      .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
      .slice(0, 200);
  }

  async create(input: ApiErrorInput): Promise<ApiErrorRecord> {
    const body = apiErrorInputSchema.parse(input);
    const row: ApiErrorRecord = {
      id: nanoid(),
      sessionId: body.sessionId ?? null,
      provider: body.provider,
      code: body.code ?? null,
      message: body.message,
      metadata: body.metadata ?? null,
      createdAt: new Date().toISOString()
    };

    this.errors.set(row.id, row);
    return row;
  }
}

class PrismaApiErrorStore implements ApiErrorStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext): Promise<ApiErrorRecord[]> {
    const rows = await this.prisma.apiError.findMany({
      where: {
        metadata: {
          path: ["deviceId"],
          equals: auth.deviceId
        }
      },
      orderBy: { createdAt: "desc" },
      take: 200
    });

    return rows.map(mapApiError);
  }

  async create(input: ApiErrorInput): Promise<ApiErrorRecord> {
    const body = apiErrorInputSchema.parse(input);
    const row = await this.prisma.apiError.create({
      data: {
        provider: body.provider,
        code: body.code ?? null,
        message: body.message,
        sessionId: body.sessionId ?? null,
        metadata: body.metadata ? (body.metadata as Prisma.InputJsonValue) : undefined
      }
    });

    return mapApiError(row);
  }
}

function mapApiError(row: {
  id: string;
  sessionId: string | null;
  provider: string;
  code: string | null;
  message: string;
  metadata: unknown;
  createdAt: Date;
}): ApiErrorRecord {
  return {
    id: row.id,
    sessionId: row.sessionId,
    provider: row.provider,
    code: row.code,
    message: row.message,
    metadata: typeof row.metadata === "object" && row.metadata !== null ? (row.metadata as Record<string, unknown>) : null,
    createdAt: row.createdAt.toISOString()
  };
}
