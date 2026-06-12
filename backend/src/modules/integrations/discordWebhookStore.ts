import type { PrismaClient } from "@prisma/client";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { HttpError, notFound } from "../../lib/httpErrors.js";
import { decryptSecret, encryptSecret } from "../../lib/secretCipher.js";
import { findUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export interface DiscordWebhookConfigRecord {
  profileId: string;
  configured: boolean;
  enabled: boolean;
  alertModerationActions: boolean;
  alertRuntimeStatus: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface DiscordWebhookDeliveryConfig extends DiscordWebhookConfigRecord {
  webhookUrl: string | null;
}

export interface DiscordWebhookUpsertInput {
  profileId: string;
  webhookUrl?: string;
  enabled: boolean;
  alertModerationActions: boolean;
  alertRuntimeStatus: boolean;
}

export interface DiscordWebhookStore {
  get(auth: AuthContext, profileId: string): Promise<DiscordWebhookConfigRecord>;
  upsert(auth: AuthContext, input: DiscordWebhookUpsertInput): Promise<DiscordWebhookConfigRecord>;
  delete(auth: AuthContext, profileId: string): Promise<void>;
  deliveryConfig(auth: AuthContext, profileId: string): Promise<DiscordWebhookDeliveryConfig>;
}

export function createDiscordWebhookStore(): DiscordWebhookStore {
  return shouldUsePrisma() ? new PrismaDiscordWebhookStore(getPrismaClient()) : new InMemoryDiscordWebhookStore();
}

class InMemoryDiscordWebhookStore implements DiscordWebhookStore {
  private configs = new Map<string, DiscordWebhookDeliveryConfig>();

  async get(auth: AuthContext, profileId: string): Promise<DiscordWebhookConfigRecord> {
    return publicConfig(this.configs.get(keyFor(auth, profileId)) ?? defaultDeliveryConfig(profileId));
  }

  async upsert(auth: AuthContext, input: DiscordWebhookUpsertInput): Promise<DiscordWebhookConfigRecord> {
    const key = keyFor(auth, input.profileId);
    const existing = this.configs.get(key);
    const normalizedUrl = input.webhookUrl ? normalizeDiscordWebhookUrl(input.webhookUrl) : existing?.webhookUrl ?? null;
    if (!normalizedUrl) {
      throw new HttpError(400, "Add a Discord webhook URL before enabling alerts.");
    }

    const now = new Date().toISOString();
    const row: DiscordWebhookDeliveryConfig = {
      profileId: input.profileId,
      configured: true,
      webhookUrl: normalizedUrl,
      enabled: input.enabled,
      alertModerationActions: input.alertModerationActions,
      alertRuntimeStatus: input.alertRuntimeStatus,
      createdAt: existing?.createdAt ?? now,
      updatedAt: now
    };
    this.configs.set(key, row);

    return publicConfig(row);
  }

  async delete(auth: AuthContext, profileId: string): Promise<void> {
    this.configs.delete(keyFor(auth, profileId));
  }

  async deliveryConfig(auth: AuthContext, profileId: string): Promise<DiscordWebhookDeliveryConfig> {
    return this.configs.get(keyFor(auth, profileId)) ?? defaultDeliveryConfig(profileId);
  }
}

class PrismaDiscordWebhookStore implements DiscordWebhookStore {
  constructor(private readonly prisma: PrismaClient) {}

  async get(auth: AuthContext, profileId: string): Promise<DiscordWebhookConfigRecord> {
    return publicConfig(await this.findDeliveryConfig(auth, profileId));
  }

  async upsert(auth: AuthContext, input: DiscordWebhookUpsertInput): Promise<DiscordWebhookConfigRecord> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      throw notFound("Channel profile not found.");
    }

    const profile = await this.prisma.channelProfile.findFirst({
      where: {
        id: input.profileId,
        userId
      },
      select: {
        id: true
      }
    });
    if (!profile) {
      throw notFound("Channel profile not found.");
    }

    const existing = await this.prisma.discordWebhookConfig.findUnique({
      where: { profileId: input.profileId }
    });
    const normalizedUrl = input.webhookUrl ? normalizeDiscordWebhookUrl(input.webhookUrl) : null;
    const encryptedWebhookUrl = normalizedUrl ? encryptSecret(normalizedUrl) : existing?.encryptedWebhookUrl ?? null;
    if (!encryptedWebhookUrl) {
      throw new HttpError(400, "Add a Discord webhook URL before enabling alerts.");
    }

    const row = await this.prisma.discordWebhookConfig.upsert({
      where: { profileId: input.profileId },
      create: {
        profileId: input.profileId,
        encryptedWebhookUrl,
        enabled: input.enabled,
        alertModerationActions: input.alertModerationActions,
        alertRuntimeStatus: input.alertRuntimeStatus
      },
      update: {
        encryptedWebhookUrl,
        enabled: input.enabled,
        alertModerationActions: input.alertModerationActions,
        alertRuntimeStatus: input.alertRuntimeStatus
      }
    });

    return mapPrismaPublicConfig(row);
  }

  async delete(auth: AuthContext, profileId: string): Promise<void> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return;
    }

    await this.prisma.discordWebhookConfig.deleteMany({
      where: {
        profileId,
        profile: {
          userId
        }
      }
    });
  }

  async deliveryConfig(auth: AuthContext, profileId: string): Promise<DiscordWebhookDeliveryConfig> {
    return this.findDeliveryConfig(auth, profileId);
  }

  private async findDeliveryConfig(auth: AuthContext, profileId: string): Promise<DiscordWebhookDeliveryConfig> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return defaultDeliveryConfig(profileId);
    }

    const row = await this.prisma.discordWebhookConfig.findFirst({
      where: {
        profileId,
        profile: {
          userId
        }
      }
    });
    if (!row) {
      return defaultDeliveryConfig(profileId);
    }

    const webhookUrl = row.encryptedWebhookUrl ? decryptSecret(row.encryptedWebhookUrl) : null;
    return {
      ...mapPrismaPublicConfig(row),
      webhookUrl
    };
  }
}

export function normalizeDiscordWebhookUrl(value: string): string {
  let url: URL;
  try {
    url = new URL(value.trim());
  } catch {
    throw new HttpError(400, "Enter a valid Discord webhook URL.");
  }

  const allowedHosts = new Set(["discord.com", "discordapp.com"]);
  const pathParts = url.pathname.split("/").filter(Boolean);
  const validPath = pathParts[0] === "api"
    && pathParts[1] === "webhooks"
    && pathParts.length >= 4;

  if (url.protocol !== "https:" || !allowedHosts.has(url.hostname.toLowerCase()) || !validPath) {
    throw new HttpError(400, "Enter a valid Discord webhook URL.");
  }

  url.hash = "";
  return url.toString();
}

function mapPrismaPublicConfig(row: {
  profileId: string;
  encryptedWebhookUrl: string | null;
  enabled: boolean;
  alertModerationActions: boolean;
  alertRuntimeStatus: boolean;
  createdAt: Date;
  updatedAt: Date;
}): DiscordWebhookConfigRecord {
  return {
    profileId: row.profileId,
    configured: Boolean(row.encryptedWebhookUrl),
    enabled: row.enabled,
    alertModerationActions: row.alertModerationActions,
    alertRuntimeStatus: row.alertRuntimeStatus,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString()
  };
}

function publicConfig(config: DiscordWebhookDeliveryConfig): DiscordWebhookConfigRecord {
  const { webhookUrl: _webhookUrl, ...record } = config;
  return record;
}

function defaultDeliveryConfig(profileId: string): DiscordWebhookDeliveryConfig {
  return {
    profileId,
    configured: false,
    webhookUrl: null,
    enabled: false,
    alertModerationActions: true,
    alertRuntimeStatus: false,
    createdAt: null,
    updatedAt: null
  };
}

function keyFor(auth: AuthContext, profileId: string): string {
  return `${auth.deviceId}:${profileId}`;
}
