import type { PrismaClient } from "@prisma/client";
import { describe, expect, it } from "vitest";
import { decryptSecret } from "../../lib/secretCipher.js";
import type { AuthContext } from "../auth/sessionToken.js";
import {
  persistRefreshedYouTubeTokens,
  youtubeLinkedAccountMetadataCreateData,
  youtubeLinkedAccountMetadataUpdateData
} from "./youtubeTokenStore.js";

describe("YouTube token refresh persistence", () => {
  it("maps linked account metadata for create and update without clearing existing metadata on missing values", () => {
    expect(youtubeLinkedAccountMetadataCreateData({
      channelId: "channel-1",
      channelTitle: "Creator Channel"
    })).toEqual({
      channelId: "channel-1",
      channelTitle: "Creator Channel"
    });
    expect(youtubeLinkedAccountMetadataCreateData({})).toEqual({
      channelId: null,
      channelTitle: null
    });
    expect(youtubeLinkedAccountMetadataUpdateData({
      channelId: "channel-1",
      channelTitle: "Creator Channel"
    })).toEqual({
      channelId: "channel-1",
      channelTitle: "Creator Channel"
    });
    expect(youtubeLinkedAccountMetadataUpdateData({})).toEqual({});
  });

  it("persists refreshed access tokens without clearing the refresh token", async () => {
    const updates: unknown[] = [];
    const prisma = fakePrisma(updates, 1);
    const auth = authContext();

    await expect(persistRefreshedYouTubeTokens(prisma, auth, {
      access_token: "ya29.new-access-token",
      expiry_date: Date.parse("2026-06-07T20:00:00.000Z")
    })).resolves.toBe(true);

    expect(updates).toHaveLength(1);
    const update = updates[0] as {
      where: { provider: string; providerAccountId: string };
      data: {
        encryptedAccess?: string | null;
        encryptedRefresh?: string | null;
        tokenExpiresAt?: Date;
      };
    };
    expect(update.where).toEqual({
      provider: "youtube",
      providerAccountId: "device-refresh-1"
    });
    expect(update.data.encryptedAccess).not.toContain("ya29.new-access-token");
    expect(decryptSecret(update.data.encryptedAccess ?? null)).toBe("ya29.new-access-token");
    expect(update.data.encryptedRefresh).toBeUndefined();
    expect(update.data.tokenExpiresAt?.toISOString()).toBe("2026-06-07T20:00:00.000Z");
  });

  it("persists replacement refresh tokens when Google sends one", async () => {
    const updates: unknown[] = [];
    const prisma = fakePrisma(updates, 1);

    await expect(persistRefreshedYouTubeTokens(prisma, authContext(), {
      refresh_token: "refresh-token-2"
    })).resolves.toBe(true);

    const update = updates[0] as {
      data: {
        encryptedAccess?: string | null;
        encryptedRefresh?: string | null;
      };
    };
    expect(update.data.encryptedAccess).toBeUndefined();
    expect(decryptSecret(update.data.encryptedRefresh ?? null)).toBe("refresh-token-2");
  });

  it("ignores empty token refresh events", async () => {
    const updates: unknown[] = [];
    const prisma = fakePrisma(updates, 1);

    await expect(persistRefreshedYouTubeTokens(prisma, authContext(), {})).resolves.toBe(false);
    expect(updates).toEqual([]);
  });
});

function authContext(): AuthContext {
  return {
    subject: "device-refresh-1",
    deviceId: "device-refresh-1",
    installId: "install-refresh-1"
  };
}

function fakePrisma(updates: unknown[], count: number): PrismaClient {
  return {
    linkedAccount: {
      updateMany: async (args: unknown) => {
        updates.push(args);
        return { count };
      }
    }
  } as unknown as PrismaClient;
}
