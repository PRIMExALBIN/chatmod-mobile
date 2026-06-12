import type { PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const userWarningInputSchema = z.object({
  profileId: z.string().min(1),
  authorChannelId: z.string().min(1).max(128),
  displayName: z.string().min(1).max(120),
  profileImageUrl: z.string().url().max(500).nullable().optional(),
  reason: z.string().min(1).max(300)
});

export type UserWarningInput = z.infer<typeof userWarningInputSchema>;

export const userProfileNotesInputSchema = z.object({
  notes: z.string().trim().max(500).nullable()
});

export type UserProfileNotesInput = z.infer<typeof userProfileNotesInputSchema>;

export const userWhitelistInputSchema = z.object({
  durationSeconds: z.number().int().min(60).max(604800).nullable().optional()
});

export type UserWhitelistInput = z.infer<typeof userWhitelistInputSchema>;

export interface UserModerationActionInput {
  actionType: "hideUser" | "timeoutUser" | "unbanUser";
  liveChatId?: string | null;
  liveChatBanId?: string | null;
  reason: string;
  durationSeconds?: number | null;
  expiresAt?: string | null;
}

export interface UserWhitelistRecord {
  id: string;
  profileId: string;
  authorChannelId: string;
  displayName: string | null;
  temporaryUntil: string | null;
  createdAt: string;
}

export interface UserProfileSummary {
  id: string;
  profileId: string;
  authorChannelId: string;
  displayName: string;
  profileImageUrl: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
  messageCount: number;
  notes: string | null;
  strikeCount: number;
  recentStrikes: UserStrikeRecord[];
  recentModerationActions: UserModerationActionRecord[];
}

export interface UserStrikeRecord {
  id: string;
  userProfileId: string;
  reason: string;
  createdAt: string;
}

export interface UserWarningRecord {
  user: UserProfileSummary;
  strike: UserStrikeRecord;
}

export interface UserModerationActionRecord {
  id: string;
  userProfileId: string;
  actionType: "hideUser" | "timeoutUser" | "unbanUser";
  liveChatId: string | null;
  liveChatBanId: string | null;
  reason: string;
  durationSeconds: number | null;
  createdAt: string;
  expiresAt: string | null;
}

export interface UserModerationActionResult {
  user: UserProfileSummary;
  action: UserModerationActionRecord;
}

export interface UserProfileStore {
  list(auth: AuthContext, profileId?: string): Promise<UserProfileSummary[]>;
  get(auth: AuthContext, userProfileId: string): Promise<UserProfileSummary | null>;
  warn(auth: AuthContext, input: UserWarningInput): Promise<UserWarningRecord>;
  updateNotes(auth: AuthContext, userProfileId: string, input: UserProfileNotesInput): Promise<UserProfileSummary | null>;
  recordModerationAction(
    auth: AuthContext,
    userProfileId: string,
    input: UserModerationActionInput
  ): Promise<UserModerationActionResult | null>;
  whitelist(
    auth: AuthContext,
    userProfileId: string,
    input?: UserWhitelistInput
  ): Promise<{ user: UserProfileSummary; whitelist: UserWhitelistRecord } | null>;
}

export function createUserProfileStore(): UserProfileStore {
  return shouldUsePrisma() ? new PrismaUserProfileStore(getPrismaClient()) : new InMemoryUserProfileStore();
}

class InMemoryUserProfileStore implements UserProfileStore {
  private users = new Map<string, UserProfileSummary & { ownerKey: string }>();
  private strikes = new Map<string, UserStrikeRecord & { ownerKey: string }>();
  private moderationActions = new Map<string, UserModerationActionRecord & { ownerKey: string }>();
  private whitelistEntries = new Map<string, UserWhitelistRecord & { ownerKey: string }>();

  async list(auth: AuthContext, profileId?: string): Promise<UserProfileSummary[]> {
    return Array.from(this.users.values())
      .filter((user) => user.ownerKey === auth.deviceId)
      .filter((user) => (profileId ? user.profileId === profileId : true))
      .map(({ ownerKey: _ownerKey, ...user }) => user)
      .sort((a, b) => b.lastSeenAt.localeCompare(a.lastSeenAt));
  }

  async get(auth: AuthContext, userProfileId: string): Promise<UserProfileSummary | null> {
    const user = Array.from(this.users.values())
      .find((candidate) => candidate.ownerKey === auth.deviceId && candidate.id === userProfileId);
    if (!user) {
      return null;
    }

    const { ownerKey: _ownerKey, ...publicUser } = user;
    return publicUser;
  }

  async warn(auth: AuthContext, input: UserWarningInput): Promise<UserWarningRecord> {
    const now = new Date().toISOString();
    const key = `${auth.deviceId}:${input.profileId}:${input.authorChannelId}`;
    const existing = this.users.get(key);
    const strikeCount = this.strikeCount(auth.deviceId, existing?.id);
    const user: UserProfileSummary & { ownerKey: string } = {
      id: existing?.id ?? nanoid(),
      profileId: input.profileId,
      authorChannelId: input.authorChannelId,
      displayName: input.displayName,
      profileImageUrl: input.profileImageUrl ?? existing?.profileImageUrl ?? null,
      firstSeenAt: existing?.firstSeenAt ?? now,
      lastSeenAt: now,
      messageCount: existing?.messageCount ?? 0,
      notes: existing?.notes ?? null,
      strikeCount: strikeCount + 1,
      recentStrikes: existing?.recentStrikes ?? [],
      recentModerationActions: existing?.recentModerationActions ?? [],
      ownerKey: auth.deviceId
    };
    const strike: UserStrikeRecord & { ownerKey: string } = {
      id: nanoid(),
      userProfileId: user.id,
      reason: input.reason,
      createdAt: now,
      ownerKey: auth.deviceId
    };
    const { ownerKey: _strikeOwnerKey, ...publicStrike } = strike;
    user.recentStrikes = [publicStrike, ...user.recentStrikes].slice(0, 5);
    this.users.set(key, user);
    this.strikes.set(strike.id, strike);

    const { ownerKey: _userOwnerKey, ...publicUser } = user;
    return {
      user: publicUser,
      strike: publicStrike
    };
  }

  async updateNotes(
    auth: AuthContext,
    userProfileId: string,
    input: UserProfileNotesInput
  ): Promise<UserProfileSummary | null> {
    const entry = Array.from(this.users.entries())
      .find(([, user]) => user.ownerKey === auth.deviceId && user.id === userProfileId);
    if (!entry) {
      return null;
    }

    const [key, user] = entry;
    const updated: UserProfileSummary & { ownerKey: string } = {
      ...user,
      notes: input.notes?.trim() || null
    };
    this.users.set(key, updated);

    const { ownerKey: _ownerKey, ...publicUser } = updated;
    return publicUser;
  }

  async whitelist(
    auth: AuthContext,
    userProfileId: string,
    input: UserWhitelistInput = {}
  ): Promise<{ user: UserProfileSummary; whitelist: UserWhitelistRecord } | null> {
    const user = await this.get(auth, userProfileId);
    if (!user) {
      return null;
    }

    const now = new Date();
    const temporaryUntil = input.durationSeconds
      ? new Date(now.getTime() + input.durationSeconds * 1000).toISOString()
      : null;
    const key = `${auth.deviceId}:${user.profileId}:${user.authorChannelId}`;
    const existing = this.whitelistEntries.get(key);
    const whitelist: UserWhitelistRecord & { ownerKey: string } = {
      id: existing?.id ?? nanoid(),
      profileId: user.profileId,
      authorChannelId: user.authorChannelId,
      displayName: user.displayName,
      temporaryUntil,
      createdAt: existing?.createdAt ?? now.toISOString(),
      ownerKey: auth.deviceId
    };
    this.whitelistEntries.set(key, whitelist);

    const { ownerKey: _ownerKey, ...publicWhitelist } = whitelist;
    return {
      user,
      whitelist: publicWhitelist
    };
  }

  async recordModerationAction(
    auth: AuthContext,
    userProfileId: string,
    input: UserModerationActionInput
  ): Promise<UserModerationActionResult | null> {
    const entry = Array.from(this.users.entries())
      .find(([, user]) => user.ownerKey === auth.deviceId && user.id === userProfileId);
    if (!entry) {
      return null;
    }

    const [key, user] = entry;
    const action: UserModerationActionRecord & { ownerKey: string } = {
      id: nanoid(),
      userProfileId,
      actionType: input.actionType,
      liveChatId: input.liveChatId ?? null,
      liveChatBanId: input.liveChatBanId ?? null,
      reason: input.reason,
      durationSeconds: input.durationSeconds ?? null,
      createdAt: new Date().toISOString(),
      expiresAt: input.expiresAt ?? null,
      ownerKey: auth.deviceId
    };
    const { ownerKey: _actionOwnerKey, ...publicAction } = action;
    const updated: UserProfileSummary & { ownerKey: string } = {
      ...user,
      recentModerationActions: [publicAction, ...user.recentModerationActions].slice(0, 5)
    };
    this.users.set(key, updated);
    this.moderationActions.set(action.id, action);

    const { ownerKey: _userOwnerKey, ...publicUser } = updated;
    return {
      user: publicUser,
      action: publicAction
    };
  }

  private strikeCount(ownerKey: string, userProfileId?: string): number {
    if (!userProfileId) {
      return 0;
    }

    return Array.from(this.strikes.values())
      .filter((strike) => strike.ownerKey === ownerKey && strike.userProfileId === userProfileId)
      .length;
  }
}

class PrismaUserProfileStore implements UserProfileStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext, profileId?: string): Promise<UserProfileSummary[]> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const rows = await this.prisma.userProfile.findMany({
      where: {
        profile: {
          userId
        },
        ...(profileId ? { profileId } : {})
      },
      include: {
        strikes: {
          orderBy: { createdAt: "desc" },
          take: 5
        },
        moderationActions: {
          orderBy: { createdAt: "desc" },
          take: 5
        }
      },
      orderBy: { lastSeenAt: "desc" }
    });

    return rows.map((row) => mapUserProfile(row));
  }

  async get(auth: AuthContext, userProfileId: string): Promise<UserProfileSummary | null> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const row = await this.prisma.userProfile.findFirst({
      where: {
        id: userProfileId,
        profile: {
          userId
        }
      },
      include: {
        strikes: {
          orderBy: { createdAt: "desc" },
          take: 5
        },
        moderationActions: {
          orderBy: { createdAt: "desc" },
          take: 5
        }
      }
    });
    if (!row) {
      return null;
    }
    const strikeCount = await this.prisma.strike.count({
      where: {
        userProfileId: row.id
      }
    });

    return mapUserProfile(row, strikeCount);
  }

  async warn(auth: AuthContext, input: UserWarningInput): Promise<UserWarningRecord> {
    await this.ensureProfile(auth, input.profileId);
    const now = new Date();
    const user = await this.prisma.userProfile.upsert({
      where: {
        profileId_authorChannelId: {
          profileId: input.profileId,
          authorChannelId: input.authorChannelId
        }
      },
      create: {
        profileId: input.profileId,
        authorChannelId: input.authorChannelId,
        displayName: input.displayName,
        profileImageUrl: input.profileImageUrl ?? null,
        firstSeenAt: now,
        lastSeenAt: now
      },
      update: {
        displayName: input.displayName,
        profileImageUrl: input.profileImageUrl ?? undefined,
        lastSeenAt: now
      },
      include: {
        strikes: {
          orderBy: { createdAt: "desc" },
          take: 4
        },
        moderationActions: {
          orderBy: { createdAt: "desc" },
          take: 5
        }
      }
    });
    const strike = await this.prisma.strike.create({
      data: {
        userProfileId: user.id,
        reason: input.reason
      }
    });
    const strikeCount = await this.prisma.strike.count({
      where: {
        userProfileId: user.id
      }
    });

    return {
      user: mapUserProfile(user, strikeCount, [mapStrike(strike), ...user.strikes.map(mapStrike)].slice(0, 5)),
      strike: mapStrike(strike)
    };
  }

  async updateNotes(
    auth: AuthContext,
    userProfileId: string,
    input: UserProfileNotesInput
  ): Promise<UserProfileSummary | null> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.userProfile.findFirst({
      where: {
        id: userProfileId,
        profile: {
          userId
        }
      },
      select: { id: true }
    });
    if (!existing) {
      return null;
    }

    const user = await this.prisma.userProfile.update({
      where: { id: userProfileId },
      data: {
        notes: input.notes?.trim() || null
      },
      include: {
        strikes: {
          orderBy: { createdAt: "desc" },
          take: 5
        },
        moderationActions: {
          orderBy: { createdAt: "desc" },
          take: 5
        }
      }
    });
    const strikeCount = await this.prisma.strike.count({
      where: {
        userProfileId: user.id
      }
    });

    return mapUserProfile(user, strikeCount);
  }

  async whitelist(
    auth: AuthContext,
    userProfileId: string,
    input: UserWhitelistInput = {}
  ): Promise<{ user: UserProfileSummary; whitelist: UserWhitelistRecord } | null> {
    const user = await this.get(auth, userProfileId);
    if (!user) {
      return null;
    }

    await this.ensureProfile(auth, user.profileId);
    const now = new Date();
    const temporaryUntil = input.durationSeconds
      ? new Date(now.getTime() + input.durationSeconds * 1000)
      : null;
    const row = await this.prisma.whitelistEntry.upsert({
      where: {
        profileId_authorChannelId: {
          profileId: user.profileId,
          authorChannelId: user.authorChannelId
        }
      },
      create: {
        profileId: user.profileId,
        authorChannelId: user.authorChannelId,
        displayName: user.displayName,
        temporaryUntil
      },
      update: {
        displayName: user.displayName,
        temporaryUntil
      }
    });

    return {
      user,
      whitelist: mapWhitelist(row)
    };
  }

  async recordModerationAction(
    auth: AuthContext,
    userProfileId: string,
    input: UserModerationActionInput
  ): Promise<UserModerationActionResult | null> {
    const user = await this.get(auth, userProfileId);
    if (!user) {
      return null;
    }

    const action = await this.prisma.userModerationAction.create({
      data: {
        userProfileId,
        actionType: input.actionType,
        liveChatId: input.liveChatId ?? null,
        liveChatBanId: input.liveChatBanId ?? null,
        reason: input.reason,
        durationSeconds: input.durationSeconds ?? null,
        expiresAt: input.expiresAt ? new Date(input.expiresAt) : null
      }
    });
    const mappedAction = mapModerationAction(action);

    return {
      user: {
        ...user,
        recentModerationActions: [mappedAction, ...user.recentModerationActions].slice(0, 5)
      },
      action: mappedAction
    };
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

function mapUserProfile(row: {
  id: string;
  profileId: string;
  authorChannelId: string;
  displayName: string;
  profileImageUrl: string | null;
  firstSeenAt: Date;
  lastSeenAt: Date;
  messageCount: number;
  notes: string | null;
  strikes: StrikeRow[];
  moderationActions: ModerationActionRow[];
}, strikeCount = row.strikes.length, recentStrikes = row.strikes.map(mapStrike)): UserProfileSummary {
  return {
    id: row.id,
    profileId: row.profileId,
    authorChannelId: row.authorChannelId,
    displayName: row.displayName,
    profileImageUrl: row.profileImageUrl,
    firstSeenAt: row.firstSeenAt.toISOString(),
    lastSeenAt: row.lastSeenAt.toISOString(),
    messageCount: row.messageCount,
    notes: row.notes,
    strikeCount,
    recentStrikes,
    recentModerationActions: row.moderationActions.map(mapModerationAction)
  };
}

interface StrikeRow {
  id: string;
  userProfileId: string;
  reason: string;
  createdAt: Date;
}

function mapStrike(row: StrikeRow): UserStrikeRecord {
  return {
    id: row.id,
    userProfileId: row.userProfileId,
    reason: row.reason,
    createdAt: row.createdAt.toISOString()
  };
}

interface ModerationActionRow {
  id: string;
  userProfileId: string;
  actionType: string;
  liveChatId: string | null;
  liveChatBanId: string | null;
  reason: string;
  durationSeconds: number | null;
  createdAt: Date;
  expiresAt: Date | null;
}

function mapModerationAction(row: ModerationActionRow): UserModerationActionRecord {
  const actionType = row.actionType === "timeoutUser"
    ? "timeoutUser"
    : row.actionType === "unbanUser"
      ? "unbanUser"
      : "hideUser";
  return {
    id: row.id,
    userProfileId: row.userProfileId,
    actionType,
    liveChatId: row.liveChatId,
    liveChatBanId: row.liveChatBanId,
    reason: row.reason,
    durationSeconds: row.durationSeconds,
    createdAt: row.createdAt.toISOString(),
    expiresAt: row.expiresAt?.toISOString() ?? null
  };
}

function mapWhitelist(row: {
  id: string;
  profileId: string;
  authorChannelId: string;
  displayName: string | null;
  temporaryUntil: Date | null;
  createdAt: Date;
}): UserWhitelistRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    authorChannelId: row.authorChannelId,
    displayName: row.displayName,
    temporaryUntil: row.temporaryUntil?.toISOString() ?? null,
    createdAt: row.createdAt.toISOString()
  };
}
