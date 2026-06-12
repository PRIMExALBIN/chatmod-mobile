import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const supportEventInputSchema = z.object({
  severity: z.enum(["info", "warning", "error"]),
  message: z.string().min(1).max(1000),
  details: z.record(z.unknown()).optional()
});

export type SupportEventInput = z.infer<typeof supportEventInputSchema>;

export interface SupportEventRecord {
  id: string;
  deviceId: string;
  severity: "info" | "warning" | "error";
  message: string;
  details: Record<string, unknown> | null;
  createdAt: string;
}

export interface LogStore {
  list(auth: AuthContext): Promise<SupportEventRecord[]>;
  create(auth: AuthContext, input: SupportEventInput): Promise<SupportEventRecord>;
}

export function createLogStore(): LogStore {
  return shouldUsePrisma() ? new PrismaLogStore(getPrismaClient()) : new InMemoryLogStore();
}

class InMemoryLogStore implements LogStore {
  private events = new Map<string, SupportEventRecord>();

  async list(auth: AuthContext): Promise<SupportEventRecord[]> {
    return Array.from(this.events.values()).filter((event) => event.deviceId === auth.deviceId);
  }

  async create(auth: AuthContext, input: SupportEventInput): Promise<SupportEventRecord> {
    const row: SupportEventRecord = {
      id: nanoid(),
      deviceId: auth.deviceId,
      severity: input.severity,
      message: input.message,
      details: input.details ?? null,
      createdAt: new Date().toISOString()
    };

    this.events.set(row.id, row);
    return row;
  }
}

class PrismaLogStore implements LogStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext): Promise<SupportEventRecord[]> {
    const rows = await this.prisma.supportEvent.findMany({
      where: { deviceId: auth.deviceId },
      orderBy: { createdAt: "desc" },
      take: 200
    });

    return rows.map(mapSupportEvent);
  }

  async create(auth: AuthContext, input: SupportEventInput): Promise<SupportEventRecord> {
    const row = await this.prisma.supportEvent.create({
      data: {
        deviceId: auth.deviceId,
        severity: input.severity,
        message: input.message,
        detailsJson: input.details ? (input.details as Prisma.InputJsonValue) : undefined
      }
    });

    return mapSupportEvent(row);
  }
}

function mapSupportEvent(row: {
  id: string;
  deviceId: string | null;
  severity: string;
  message: string;
  detailsJson: unknown;
  createdAt: Date;
}): SupportEventRecord {
  return {
    id: row.id,
    deviceId: row.deviceId ?? "unknown-device",
    severity: supportEventInputSchema.shape.severity.catch("info").parse(row.severity),
    message: row.message,
    details: typeof row.detailsJson === "object" && row.detailsJson !== null ? (row.detailsJson as Record<string, unknown>) : null,
    createdAt: row.createdAt.toISOString()
  };
}
