import { createHash, randomBytes } from "node:crypto";
import type { PrismaClient } from "@prisma/client";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { findUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const overlayThemeSchema = z.enum(["control_room", "transparent_minimal", "high_contrast"]);

export const overlayConfigUpdateSchema = z.object({
  enabled: z.boolean().optional(),
  theme: overlayThemeSchema.optional(),
  activeSessionId: z.string().min(1).max(160).nullable().optional(),
  showModerationActions: z.boolean().optional(),
  showRuntimeStatus: z.boolean().optional(),
  showViewerStats: z.boolean().optional(),
  showRecentChat: z.boolean().optional()
}).strict();

export const overlayPublicTokenSchema = z.string().regex(/^cmo_[A-Za-z0-9_-]{32,128}$/);

export type OverlayConfigUpdateInput = z.infer<typeof overlayConfigUpdateSchema>;
export type OverlayTheme = z.infer<typeof overlayThemeSchema>;

export interface OverlayConfigRecord {
  profileId: string;
  configured: boolean;
  enabled: boolean;
  theme: OverlayTheme;
  activeSessionId: string | null;
  showModerationActions: boolean;
  showRuntimeStatus: boolean;
  showViewerStats: boolean;
  showRecentChat: boolean;
  tokenPreview: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface OverlayConfigMutationResult {
  config: OverlayConfigRecord;
  publicToken: string | null;
  tokenRotated: boolean;
}

export interface PublicOverlayConfig extends OverlayConfigRecord {
  ownerAuth: AuthContext;
}

interface StoredOverlayConfig extends PublicOverlayConfig {
  publicTokenHash: string;
}

export interface OverlayConfigStore {
  get(auth: AuthContext, profileId: string): Promise<OverlayConfigRecord>;
  upsert(auth: AuthContext, profileId: string, input: OverlayConfigUpdateInput): Promise<OverlayConfigMutationResult>;
  rotateToken(auth: AuthContext, profileId: string): Promise<OverlayConfigMutationResult>;
  findByPublicToken(token: string): Promise<PublicOverlayConfig | null>;
}

export function createOverlayConfigStore(): OverlayConfigStore {
  return shouldUsePrisma() ? new PrismaOverlayConfigStore(getPrismaClient()) : new InMemoryOverlayConfigStore();
}

class InMemoryOverlayConfigStore implements OverlayConfigStore {
  private configs = new Map<string, StoredOverlayConfig>();
  private tokenIndex = new Map<string, string>();

  async get(auth: AuthContext, profileId: string): Promise<OverlayConfigRecord> {
    return publicConfig(this.configs.get(keyFor(auth, profileId)) ?? defaultStoredConfig(auth, profileId));
  }

  async upsert(
    auth: AuthContext,
    profileId: string,
    input: OverlayConfigUpdateInput
  ): Promise<OverlayConfigMutationResult> {
    const key = keyFor(auth, profileId);
    const existing = this.configs.get(key);
    const token = existing ? null : generatePublicToken();
    const tokenHash = existing?.publicTokenHash ?? hashPublicToken(token!);
    const now = new Date().toISOString();
    const row: StoredOverlayConfig = {
      ...(existing ?? defaultStoredConfig(auth, profileId)),
      ...overlayFields(existing ?? defaultStoredConfig(auth, profileId), input),
      ownerAuth: {
        subject: auth.subject,
        deviceId: auth.deviceId,
        installId: auth.installId
      },
      publicTokenHash: tokenHash,
      tokenPreview: existing?.tokenPreview ?? tokenPreview(token!),
      configured: true,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };

    this.configs.set(key, row);
    this.tokenIndex.set(row.publicTokenHash, key);
    return {
      config: publicConfig(row),
      publicToken: token,
      tokenRotated: Boolean(token)
    };
  }

  async rotateToken(auth: AuthContext, profileId: string): Promise<OverlayConfigMutationResult> {
    const key = keyFor(auth, profileId);
    const existing = this.configs.get(key) ?? defaultStoredConfig(auth, profileId);
    if (existing.configured) {
      this.tokenIndex.delete(existing.publicTokenHash);
    }

    const token = generatePublicToken();
    const now = new Date().toISOString();
    const row: StoredOverlayConfig = {
      ...existing,
      ownerAuth: {
        subject: auth.subject,
        deviceId: auth.deviceId,
        installId: auth.installId
      },
      publicTokenHash: hashPublicToken(token),
      tokenPreview: tokenPreview(token),
      configured: true,
      createdAt: existing.createdAt ?? now,
      updatedAt: now
    };

    this.configs.set(key, row);
    this.tokenIndex.set(row.publicTokenHash, key);
    return {
      config: publicConfig(row),
      publicToken: token,
      tokenRotated: true
    };
  }

  async findByPublicToken(token: string): Promise<PublicOverlayConfig | null> {
    const key = this.tokenIndex.get(hashPublicToken(token));
    const row = key ? this.configs.get(key) : null;
    return row ? publicOverlayConfig(row) : null;
  }
}

class PrismaOverlayConfigStore implements OverlayConfigStore {
  constructor(private readonly prisma: PrismaClient) {}

  async get(auth: AuthContext, profileId: string): Promise<OverlayConfigRecord> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return publicConfig(defaultStoredConfig(auth, profileId));
    }

    const row = await this.prisma.overlayConfig.findFirst({
      where: {
        profileId,
        profile: {
          userId
        }
      }
    });

    return row ? mapPrismaConfig(row) : publicConfig(defaultStoredConfig(auth, profileId));
  }

  async upsert(
    auth: AuthContext,
    profileId: string,
    input: OverlayConfigUpdateInput
  ): Promise<OverlayConfigMutationResult> {
    await this.assertProfileOwner(auth, profileId);
    const existing = await this.prisma.overlayConfig.findUnique({ where: { profileId } });
    const token = existing ? null : generatePublicToken();
    const row = existing
      ? await this.prisma.overlayConfig.update({
        where: { profileId },
        data: {
          ...prismaOverlayFields(input),
          ownerDeviceId: auth.deviceId,
          ownerInstallId: auth.installId
        }
      })
      : await this.prisma.overlayConfig.create({
        data: {
          profileId,
          ownerDeviceId: auth.deviceId,
          ownerInstallId: auth.installId,
          publicTokenHash: hashPublicToken(token!),
          tokenPreview: tokenPreview(token!),
          ...prismaOverlayFields(input)
        }
      });

    return {
      config: mapPrismaConfig(row),
      publicToken: token,
      tokenRotated: Boolean(token)
    };
  }

  async rotateToken(auth: AuthContext, profileId: string): Promise<OverlayConfigMutationResult> {
    await this.assertProfileOwner(auth, profileId);
    const existing = await this.prisma.overlayConfig.findUnique({ where: { profileId } });
    const token = generatePublicToken();
    const row = existing
      ? await this.prisma.overlayConfig.update({
        where: { profileId },
        data: {
          ownerDeviceId: auth.deviceId,
          ownerInstallId: auth.installId,
          publicTokenHash: hashPublicToken(token),
          tokenPreview: tokenPreview(token)
        }
      })
      : await this.prisma.overlayConfig.create({
        data: {
          profileId,
          ownerDeviceId: auth.deviceId,
          ownerInstallId: auth.installId,
          publicTokenHash: hashPublicToken(token),
          tokenPreview: tokenPreview(token)
        }
      });

    return {
      config: mapPrismaConfig(row),
      publicToken: token,
      tokenRotated: true
    };
  }

  async findByPublicToken(token: string): Promise<PublicOverlayConfig | null> {
    const row = await this.prisma.overlayConfig.findUnique({
      where: { publicTokenHash: hashPublicToken(token) }
    });
    return row ? publicOverlayConfig(mapPrismaConfigWithOwner(row)) : null;
  }

  private async assertProfileOwner(auth: AuthContext, profileId: string): Promise<void> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      throw notFound("Channel profile not found.");
    }

    const profile = await this.prisma.channelProfile.findFirst({
      where: {
        id: profileId,
        userId
      },
      select: { id: true }
    });
    if (!profile) {
      throw notFound("Channel profile not found.");
    }
  }
}

function overlayFields(existing: OverlayConfigRecord, input: OverlayConfigUpdateInput): OverlayConfigRecord {
  return {
    ...existing,
    enabled: input.enabled ?? existing.enabled,
    theme: input.theme ?? existing.theme,
    activeSessionId: input.activeSessionId === undefined ? existing.activeSessionId : input.activeSessionId,
    showModerationActions: input.showModerationActions ?? existing.showModerationActions,
    showRuntimeStatus: input.showRuntimeStatus ?? existing.showRuntimeStatus,
    showViewerStats: input.showViewerStats ?? existing.showViewerStats,
    showRecentChat: input.showRecentChat ?? existing.showRecentChat
  };
}

function prismaOverlayFields(input: OverlayConfigUpdateInput) {
  return {
    enabled: input.enabled,
    theme: input.theme,
    activeSessionId: input.activeSessionId,
    showModerationActions: input.showModerationActions,
    showRuntimeStatus: input.showRuntimeStatus,
    showViewerStats: input.showViewerStats,
    showRecentChat: input.showRecentChat
  };
}

function publicConfig(config: StoredOverlayConfig): OverlayConfigRecord {
  const { ownerAuth: _ownerAuth, publicTokenHash: _publicTokenHash, ...record } = config;
  return record;
}

function publicOverlayConfig(config: StoredOverlayConfig): PublicOverlayConfig {
  const { publicTokenHash: _publicTokenHash, ...record } = config;
  return record;
}

function defaultStoredConfig(auth: AuthContext, profileId: string): StoredOverlayConfig {
  return {
    profileId,
    configured: false,
    enabled: false,
    theme: "control_room",
    activeSessionId: null,
    showModerationActions: true,
    showRuntimeStatus: true,
    showViewerStats: true,
    showRecentChat: false,
    tokenPreview: null,
    createdAt: null,
    updatedAt: null,
    ownerAuth: {
      subject: auth.subject,
      deviceId: auth.deviceId,
      installId: auth.installId
    },
    publicTokenHash: ""
  };
}

function mapPrismaConfig(row: {
  profileId: string;
  tokenPreview: string;
  enabled: boolean;
  theme: string;
  activeSessionId: string | null;
  showModerationActions: boolean;
  showRuntimeStatus: boolean;
  showViewerStats: boolean;
  showRecentChat: boolean;
  createdAt: Date;
  updatedAt: Date;
}): OverlayConfigRecord {
  return {
    profileId: row.profileId,
    configured: true,
    enabled: row.enabled,
    theme: overlayThemeSchema.safeParse(row.theme).success ? row.theme as OverlayTheme : "control_room",
    activeSessionId: row.activeSessionId,
    showModerationActions: row.showModerationActions,
    showRuntimeStatus: row.showRuntimeStatus,
    showViewerStats: row.showViewerStats,
    showRecentChat: row.showRecentChat,
    tokenPreview: row.tokenPreview,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}

function mapPrismaConfigWithOwner(row: {
  profileId: string;
  ownerDeviceId: string;
  ownerInstallId: string;
  publicTokenHash: string;
  tokenPreview: string;
  enabled: boolean;
  theme: string;
  activeSessionId: string | null;
  showModerationActions: boolean;
  showRuntimeStatus: boolean;
  showViewerStats: boolean;
  showRecentChat: boolean;
  createdAt: Date;
  updatedAt: Date;
}): StoredOverlayConfig {
  return {
    ...mapPrismaConfig(row),
    ownerAuth: {
      subject: row.ownerDeviceId,
      deviceId: row.ownerDeviceId,
      installId: row.ownerInstallId
    },
    publicTokenHash: row.publicTokenHash
  };
}

function generatePublicToken(): string {
  return `cmo_${randomBytes(32).toString("base64url")}`;
}

function hashPublicToken(token: string): string {
  return createHash("sha256").update(token, "utf8").digest("hex");
}

function tokenPreview(token: string): string {
  return `${token.slice(0, 8)}...${token.slice(-4)}`;
}

function keyFor(auth: AuthContext, profileId: string): string {
  return `${auth.deviceId}:${profileId}`;
}
