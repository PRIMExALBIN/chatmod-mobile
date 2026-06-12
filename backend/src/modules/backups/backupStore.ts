import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { decryptSecret, encryptSecret } from "../../lib/secretCipher.js";
import { findUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

const encryptedBackupConfigKind = "chatmod-encrypted-backup-config-v1";

export const profileBackupSchema = z.object({
  profileId: z.string().min(1),
  channelId: z.string().min(1),
  profileName: z.string().min(1),
  config: z.record(z.unknown()),
  clientVersion: z.string().optional()
});

export type ProfileBackupInput = z.infer<typeof profileBackupSchema>;

export interface BackupRecord {
  id: string;
  profileId: string;
  channelId: string;
  profileName: string;
  config: Record<string, unknown>;
  version: number;
  clientVersion: string | null;
  createdAt: string;
}

export interface BackupStore {
  list(auth: AuthContext): Promise<BackupRecord[]>;
  get(auth: AuthContext, id: string): Promise<BackupRecord>;
  create(auth: AuthContext, input: ProfileBackupInput): Promise<BackupRecord>;
  delete(auth: AuthContext, id: string): Promise<void>;
}

export function createBackupStore(): BackupStore {
  return shouldUsePrisma() ? new PrismaBackupStore(getPrismaClient()) : new InMemoryBackupStore();
}

class InMemoryBackupStore implements BackupStore {
  private backups = new Map<string, Array<BackupRecord & { ownerKey: string }>>();

  async list(auth: AuthContext): Promise<BackupRecord[]> {
    return Array.from(this.backups.values())
      .flat()
      .filter((backup) => backup.ownerKey === auth.deviceId)
      .map(mapInMemoryBackup);
  }

  async get(auth: AuthContext, id: string): Promise<BackupRecord> {
    const backup = Array.from(this.backups.values())
      .flat()
      .find((row) => row.id === id && row.ownerKey === auth.deviceId);
    if (!backup) {
      throw notFound("Backup not found.");
    }

    return mapInMemoryBackup(backup);
  }

  async create(auth: AuthContext, input: ProfileBackupInput): Promise<BackupRecord> {
    const rows = this.backups.get(input.profileId) ?? [];
    const row: BackupRecord & { ownerKey: string } = {
      ...input,
      config: cloneJsonObject(input.config),
      id: nanoid(),
      ownerKey: auth.deviceId,
      version: rows.length + 1,
      clientVersion: input.clientVersion ?? null,
      createdAt: new Date().toISOString()
    };

    rows.push(row);
    this.backups.set(input.profileId, rows);

    return mapInMemoryBackup(row);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    for (const [profileId, rows] of this.backups.entries()) {
      this.backups.set(
        profileId,
        rows.filter((backup) => backup.id !== id || backup.ownerKey !== auth.deviceId)
      );
    }
  }
}

class PrismaBackupStore implements BackupStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext): Promise<BackupRecord[]> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return [];
    }

    const rows = await this.prisma.backup.findMany({
      where: {
        profile: {
          userId
        }
      },
      orderBy: { createdAt: "desc" }
    });

    return rows.map(mapBackup);
  }

  async create(auth: AuthContext, input: ProfileBackupInput): Promise<BackupRecord> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      throw notFound("Channel profile not found.");
    }

    const profile = await this.prisma.channelProfile.findFirst({
      where: {
        id: input.profileId,
        userId
      },
      select: { id: true }
    });

    if (!profile) {
      throw notFound("Channel profile not found.");
    }

    const version = (await this.prisma.backup.count({ where: { profileId: input.profileId } })) + 1;
    const row = await this.prisma.backup.create({
      data: {
        profileId: input.profileId,
        version,
        channelId: input.channelId,
        profileName: input.profileName,
        clientVersion: input.clientVersion ?? null,
        configJson: encryptBackupConfigForStorage(input.config) as Prisma.InputJsonValue
      }
    });

    return mapBackup(row);
  }

  async get(auth: AuthContext, id: string): Promise<BackupRecord> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      throw notFound("Backup not found.");
    }

    const row = await this.prisma.backup.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
    if (!row) {
      throw notFound("Backup not found.");
    }

    return mapBackup(row);
  }

  async delete(auth: AuthContext, id: string): Promise<void> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return;
    }

    await this.prisma.backup.deleteMany({
      where: {
        id,
        profile: {
          userId
        }
      }
    });
  }
}

function mapBackup(row: {
  id: string;
  profileId: string;
  version: number;
  channelId: string;
  profileName: string;
  clientVersion: string | null;
  configJson: unknown;
  createdAt: Date;
}): BackupRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    version: row.version,
    channelId: row.channelId,
    profileName: row.profileName,
    config: decryptBackupConfigFromStorage(row.configJson),
    clientVersion: row.clientVersion,
    createdAt: row.createdAt.toISOString()
  };
}

export function encryptBackupConfigForStorage(config: Record<string, unknown>): Record<string, unknown> {
  const ciphertext = encryptSecret(JSON.stringify(cloneJsonObject(config)));
  if (!ciphertext) {
    throw new Error("Backup config encryption failed.");
  }

  return {
    kind: encryptedBackupConfigKind,
    ciphertext
  };
}

export function decryptBackupConfigFromStorage(configJson: unknown): Record<string, unknown> {
  if (!isRecord(configJson)) {
    return {};
  }

  if (configJson.kind !== encryptedBackupConfigKind) {
    return cloneJsonObject(configJson);
  }

  const ciphertext = typeof configJson.ciphertext === "string" ? configJson.ciphertext : null;
  const plaintext = decryptSecret(ciphertext);
  if (!plaintext) {
    return {};
  }

  return parseJsonObject(plaintext);
}

function mapInMemoryBackup(row: BackupRecord & { ownerKey: string }): BackupRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    channelId: row.channelId,
    profileName: row.profileName,
    config: cloneJsonObject(row.config),
    version: row.version,
    clientVersion: row.clientVersion,
    createdAt: row.createdAt
  };
}

function cloneJsonObject(value: Record<string, unknown>): Record<string, unknown> {
  return JSON.parse(JSON.stringify(value)) as Record<string, unknown>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function parseJsonObject(value: string): Record<string, unknown> {
  const parsed = JSON.parse(value) as unknown;
  return isRecord(parsed) ? cloneJsonObject(parsed) : {};
}
