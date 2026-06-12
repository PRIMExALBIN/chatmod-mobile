import type { PrismaClient } from "@prisma/client";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { decryptSecret, encryptSecret } from "../../lib/secretCipher.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { GoogleYouTubeLiveChatClient } from "./googleYouTubeClient.js";
import { createOAuthClient, type YouTubeOAuthTokens } from "./youtubeOAuth.js";

export interface TokenStorageResult {
  stored: boolean;
  linkedAccountId: string | null;
}

export interface YouTubeAccountLinkMetadata {
  channelId?: string | null;
  channelTitle?: string | null;
}

export interface YouTubeTokenRefreshEvent {
  access_token?: string | null;
  refresh_token?: string | null;
  expiry_date?: number | null;
}

export interface YouTubeLinkedAccountStatus {
  connected: boolean;
  linkedAccountId: string | null;
  channelId: string | null;
  channelTitle: string | null;
  hasAccessToken: boolean;
  hasRefreshToken: boolean;
  tokenExpiresAt: string | null;
}

interface TokenRefreshEmitter {
  on(event: "tokens", listener: (tokens: YouTubeTokenRefreshEvent) => void): void;
}

export async function storeYouTubeTokens(
  auth: AuthContext,
  tokens: YouTubeOAuthTokens,
  metadata: YouTubeAccountLinkMetadata = {}
): Promise<TokenStorageResult> {
  if (!shouldUsePrisma()) {
    return {
      stored: false,
      linkedAccountId: null
    };
  }

  const prisma = getPrismaClient();
  const userId = await resolveUserIdForDevice(prisma, auth);
  const row = await prisma.linkedAccount.upsert({
    where: {
      provider_providerAccountId: {
        provider: "youtube",
        providerAccountId: auth.deviceId
      }
    },
    create: {
      userId,
      provider: "youtube",
      providerAccountId: auth.deviceId,
      ...youtubeLinkedAccountMetadataCreateData(metadata),
      encryptedAccess: encryptSecret(tokens.accessToken),
      encryptedRefresh: encryptSecret(tokens.refreshToken),
      tokenExpiresAt: tokens.expiryDate ? new Date(tokens.expiryDate) : null
    },
    update: {
      userId,
      ...youtubeLinkedAccountMetadataUpdateData(metadata),
      encryptedAccess: encryptSecret(tokens.accessToken),
      encryptedRefresh: tokens.refreshToken ? encryptSecret(tokens.refreshToken) : undefined,
      tokenExpiresAt: tokens.expiryDate ? new Date(tokens.expiryDate) : null
    },
    select: { id: true }
  });

  return {
    stored: true,
    linkedAccountId: row.id
  };
}

export function youtubeLinkedAccountMetadataCreateData(metadata: YouTubeAccountLinkMetadata): {
  channelId: string | null;
  channelTitle: string | null;
} {
  return {
    channelId: metadata.channelId ?? null,
    channelTitle: metadata.channelTitle ?? null
  };
}

export function youtubeLinkedAccountMetadataUpdateData(metadata: YouTubeAccountLinkMetadata): {
  channelId?: string;
  channelTitle?: string;
} {
  return {
    ...(metadata.channelId ? { channelId: metadata.channelId } : {}),
    ...(metadata.channelTitle ? { channelTitle: metadata.channelTitle } : {})
  };
}

export async function createYouTubeClientForAuth(auth: AuthContext): Promise<GoogleYouTubeLiveChatClient | null> {
  if (!shouldUsePrisma()) {
    return null;
  }

  const prisma = getPrismaClient();
  const tokenSet = await loadTokenSet(prisma, auth);
  if (!tokenSet?.accessToken && !tokenSet?.refreshToken) {
    return null;
  }

  const client = createOAuthClient();
  client.setCredentials({
    access_token: tokenSet.accessToken ?? undefined,
    refresh_token: tokenSet.refreshToken ?? undefined,
    expiry_date: tokenSet.expiryDate ?? undefined
  });
  attachYouTubeTokenRefreshPersistence(client, auth, prisma);

  return new GoogleYouTubeLiveChatClient(client);
}

export async function getYouTubeLinkedAccountStatus(auth: AuthContext): Promise<YouTubeLinkedAccountStatus> {
  if (!shouldUsePrisma()) {
    return emptyYouTubeLinkedAccountStatus();
  }

  const row = await getPrismaClient().linkedAccount.findUnique({
    where: {
      provider_providerAccountId: {
        provider: "youtube",
        providerAccountId: auth.deviceId
      }
    },
    select: {
      id: true,
      channelId: true,
      channelTitle: true,
      encryptedAccess: true,
      encryptedRefresh: true,
      tokenExpiresAt: true
    }
  });

  if (!row) {
    return emptyYouTubeLinkedAccountStatus();
  }

  const hasAccessToken = Boolean(row.encryptedAccess);
  const hasRefreshToken = Boolean(row.encryptedRefresh);

  return {
    connected: hasAccessToken || hasRefreshToken,
    linkedAccountId: row.id,
    channelId: row.channelId,
    channelTitle: row.channelTitle,
    hasAccessToken,
    hasRefreshToken,
    tokenExpiresAt: row.tokenExpiresAt?.toISOString() ?? null
  };
}

export function emptyYouTubeLinkedAccountStatus(): YouTubeLinkedAccountStatus {
  return {
    connected: false,
    linkedAccountId: null,
    channelId: null,
    channelTitle: null,
    hasAccessToken: false,
    hasRefreshToken: false,
    tokenExpiresAt: null
  };
}

export function attachYouTubeTokenRefreshPersistence(
  client: ReturnType<typeof createOAuthClient>,
  auth: AuthContext,
  prisma: PrismaClient = getPrismaClient()
): void {
  (client as unknown as TokenRefreshEmitter).on("tokens", (tokens) => {
    void persistRefreshedYouTubeTokens(prisma, auth, tokens).catch(() => undefined);
  });
}

export async function persistRefreshedYouTubeTokens(
  prisma: PrismaClient,
  auth: AuthContext,
  tokens: YouTubeTokenRefreshEvent
): Promise<boolean> {
  const data = refreshedTokenUpdateData(tokens);
  if (!data) {
    return false;
  }

  const result = await prisma.linkedAccount.updateMany({
    where: {
      provider: "youtube",
      providerAccountId: auth.deviceId
    },
    data
  });

  return result.count > 0;
}

async function loadTokenSet(
  prisma: PrismaClient,
  auth: AuthContext
): Promise<{
  accessToken: string | null;
  refreshToken: string | null;
  expiryDate: number | null;
} | null> {
  const row = await prisma.linkedAccount.findUnique({
    where: {
      provider_providerAccountId: {
        provider: "youtube",
        providerAccountId: auth.deviceId
      }
    },
    select: {
      encryptedAccess: true,
      encryptedRefresh: true,
      tokenExpiresAt: true
    }
  });

  if (!row) {
    return null;
  }

  return {
    accessToken: decryptSecret(row.encryptedAccess),
    refreshToken: decryptSecret(row.encryptedRefresh),
    expiryDate: row.tokenExpiresAt?.getTime() ?? null
  };
}

function refreshedTokenUpdateData(tokens: YouTubeTokenRefreshEvent): {
  encryptedAccess?: string | null;
  encryptedRefresh?: string | null;
  tokenExpiresAt?: Date;
} | null {
  const data: {
    encryptedAccess?: string | null;
    encryptedRefresh?: string | null;
    tokenExpiresAt?: Date;
  } = {};

  if (tokens.access_token) {
    data.encryptedAccess = encryptSecret(tokens.access_token);
  }
  if (tokens.refresh_token) {
    data.encryptedRefresh = encryptSecret(tokens.refresh_token);
  }
  if (tokens.expiry_date) {
    data.tokenExpiresAt = new Date(tokens.expiry_date);
  }

  return Object.keys(data).length > 0 ? data : null;
}
