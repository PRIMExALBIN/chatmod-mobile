import type { AuditAction, Prisma, PrismaClient } from "@prisma/client";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { decryptSecret } from "../../lib/secretCipher.js";
import type { AuthContext } from "../auth/sessionToken.js";
import { decryptBackupConfigFromStorage } from "../backups/backupStore.js";
import { createOAuthClient, isYouTubeOAuthConfigured } from "../youtube/youtubeOAuth.js";
import { findUserIdForDevice } from "./deviceUser.js";

export interface AccountDataExport {
  exportedAt: string;
  account: {
    userId: string | null;
    deviceId: string;
    installId: string;
    displayName: string | null;
    email: string | null;
    createdAt: string | null;
    updatedAt: string | null;
  };
  devices: Array<{
    id: string;
    deviceId: string;
    installId: string;
    appVersion: string | null;
    lastSeenAt: string;
    createdAt: string;
  }>;
  linkedAccounts: Array<{
    id: string;
    provider: string;
    providerAccountId: string;
    channelId: string | null;
    channelTitle: string | null;
    hasAccessToken: boolean;
    hasRefreshToken: boolean;
    tokenExpiresAt: string | null;
    createdAt: string;
    updatedAt: string;
  }>;
  profiles: AccountProfileExport[];
  subscription: {
    id: string;
    source: string;
    status: string;
    productId: string | null;
    currentPeriodEndsAt: string | null;
    createdAt: string;
    updatedAt: string;
    entitlements: Array<{
      id: string;
      key: string;
      enabled: boolean;
      expiresAt: string | null;
    }>;
  } | null;
  supportEvents: Array<{
    id: string;
    deviceId: string | null;
    severity: string;
    message: string;
    details: unknown;
    createdAt: string;
  }>;
  apiErrors: Array<{
    id: string;
    sessionId: string | null;
    provider: string;
    code: string | null;
    message: string;
    metadata: unknown;
    createdAt: string;
  }>;
  auditLogs: Array<{
    id: string;
    action: AuditAction;
    metadata: unknown;
    createdAt: string;
  }>;
}

export interface AccountProfileExport {
  id: string;
  channelId: string;
  name: string;
  config: unknown;
  createdAt: string;
  updatedAt: string;
  discordWebhook: {
    configured: boolean;
    enabled: boolean;
    alertModerationActions: boolean;
    alertRuntimeStatus: boolean;
    createdAt: string | null;
    updatedAt: string | null;
  } | null;
  commands: Array<{
    id: string;
    name: string;
    response: string;
    aliases: unknown;
    cooldownSeconds: number;
    accessLevel: string;
    enabled: boolean;
    createdAt: string;
    updatedAt: string;
  }>;
  timers: Array<{
    id: string;
    name: string;
    message: string;
    intervalMinutes: number;
    minChatMessages: number;
    quietStartMinutes: number | null;
    quietEndMinutes: number | null;
    enabled: boolean;
    lastSentAt: string | null;
    createdAt: string;
    updatedAt: string;
  }>;
  backups: Array<{
    id: string;
    version: number;
    channelId: string;
    profileName: string;
    clientVersion: string | null;
    config: unknown;
    createdAt: string;
  }>;
  rulePresets: Array<{
    id: string;
    name: string;
    config: unknown;
    isDefault: boolean;
    createdAt: string;
    updatedAt: string;
  }>;
  whitelist: Array<{
    id: string;
    authorChannelId: string;
    displayName: string | null;
    temporaryUntil: string | null;
    createdAt: string;
  }>;
  userProfiles: Array<{
    id: string;
    authorChannelId: string;
    displayName: string;
    profileImageUrl: string | null;
    firstSeenAt: string;
    lastSeenAt: string;
    messageCount: number;
    notes: string | null;
    strikes: Array<{
      id: string;
      reason: string;
      createdAt: string;
    }>;
    moderationActions: Array<{
      id: string;
      actionType: string;
      liveChatId: string | null;
      reason: string;
      durationSeconds: number | null;
      createdAt: string;
      expiresAt: string | null;
    }>;
  }>;
  streamSessions: Array<{
    id: string;
    videoId: string;
    liveChatId: string;
    title: string | null;
    startedAt: string;
    endedAt: string | null;
    messages: Array<{
      id: string;
      youtubeMessageId: string;
      authorChannelId: string;
      authorName: string;
      text: string;
      receivedAt: string;
      createdAt: string;
    }>;
    actions: Array<{
      id: string;
      youtubeMessageId: string | null;
      authorChannelId: string | null;
      actionType: string;
      reason: string;
      confidence: number | null;
      metadata: unknown;
      createdAt: string;
    }>;
    runtimeEvents: Array<{
      id: string;
      type: string;
      message: string;
      metadata: unknown;
      createdAt: string;
    }>;
    apiErrors: Array<{
      id: string;
      provider: string;
      code: string | null;
      message: string;
      metadata: unknown;
      createdAt: string;
    }>;
  }>;
}

export interface AccountDeletionResult {
  deleted: boolean;
  userId: string | null;
  deviceIds: string[];
  supportEventsDeleted: number;
  auditLogsDeleted: number;
  apiErrorsDeleted: number;
}

export interface AccountDisconnectResult {
  disconnected: boolean;
  removedAccounts: number;
  revocationAttempted: boolean;
  revokedTokens: number;
  revocationFailures: number;
}

export interface AccountPrivacyStore {
  exportCurrent(auth: AuthContext): Promise<AccountDataExport>;
  deleteCurrent(auth: AuthContext): Promise<AccountDeletionResult>;
  disconnectYouTube(auth: AuthContext): Promise<AccountDisconnectResult>;
}

const accountExportInclude = {
  devices: {
    orderBy: { createdAt: "asc" }
  },
  linkedAccounts: {
    orderBy: { createdAt: "asc" }
  },
  profiles: {
    orderBy: { createdAt: "asc" },
    include: {
      backups: { orderBy: { createdAt: "desc" } },
      commands: { orderBy: { createdAt: "asc" } },
      timers: { orderBy: { createdAt: "asc" } },
      discordWebhook: true,
      rulePresets: { orderBy: { createdAt: "asc" } },
      whitelist: { orderBy: { createdAt: "asc" } },
      userProfiles: {
        orderBy: { firstSeenAt: "asc" },
        include: {
          strikes: { orderBy: { createdAt: "asc" } },
          moderationActions: { orderBy: { createdAt: "asc" } }
        }
      },
      sessions: {
        orderBy: { startedAt: "desc" },
        include: {
          messages: { orderBy: { receivedAt: "asc" } },
          actions: { orderBy: { createdAt: "asc" } },
          runtimeEvents: { orderBy: { createdAt: "asc" } },
          apiErrors: { orderBy: { createdAt: "asc" } }
        }
      }
    }
  },
  subscription: {
    include: {
      entitlements: { orderBy: { key: "asc" } }
    }
  },
  auditLogs: {
    orderBy: { createdAt: "desc" }
  }
} satisfies Prisma.UserInclude;

type PrismaAccountExportUser = Prisma.UserGetPayload<{ include: typeof accountExportInclude }>;
type ApiErrorClient = Pick<PrismaClient, "apiError">;

export function createAccountPrivacyStore(): AccountPrivacyStore {
  return shouldUsePrisma() ? new PrismaAccountPrivacyStore(getPrismaClient()) : new InMemoryAccountPrivacyStore();
}

class InMemoryAccountPrivacyStore implements AccountPrivacyStore {
  async exportCurrent(auth: AuthContext): Promise<AccountDataExport> {
    return emptyAccountExport(auth, new Date());
  }

  async deleteCurrent(auth: AuthContext): Promise<AccountDeletionResult> {
    return {
      deleted: false,
      userId: null,
      deviceIds: [auth.deviceId],
      supportEventsDeleted: 0,
      auditLogsDeleted: 0,
      apiErrorsDeleted: 0
    };
  }

  async disconnectYouTube(): Promise<AccountDisconnectResult> {
    return {
      disconnected: false,
      removedAccounts: 0,
      revocationAttempted: false,
      revokedTokens: 0,
      revocationFailures: 0
    };
  }
}

class PrismaAccountPrivacyStore implements AccountPrivacyStore {
  constructor(private readonly prisma: PrismaClient) {}

  async exportCurrent(auth: AuthContext): Promise<AccountDataExport> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return emptyAccountExport(auth, new Date());
    }

    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      include: accountExportInclude
    });

    if (!user) {
      return emptyAccountExport(auth, new Date());
    }

    const deviceIds = collectDeviceIds(auth, user);
    const nestedSessionIds = collectSessionIds(user);
    const [supportEvents, apiErrors] = await Promise.all([
      this.prisma.supportEvent.findMany({
        where: { deviceId: { in: deviceIds } },
        orderBy: { createdAt: "desc" },
        take: 1000
      }),
      findAccountApiErrors(this.prisma, deviceIds, nestedSessionIds)
    ]);

    return mapUserExport(auth, user, supportEvents, apiErrors, new Date());
  }

  async deleteCurrent(auth: AuthContext): Promise<AccountDeletionResult> {
    const userId = await findUserIdForDevice(this.prisma, auth);

    if (!userId) {
      const supportEventsDeleted = await this.prisma.supportEvent.deleteMany({
        where: { deviceId: auth.deviceId }
      });

      return {
        deleted: false,
        userId: null,
        deviceIds: [auth.deviceId],
        supportEventsDeleted: supportEventsDeleted.count,
        auditLogsDeleted: 0,
        apiErrorsDeleted: 0
      };
    }

    return this.prisma.$transaction(async (tx) => {
      const devices = await tx.device.findMany({
        where: { userId },
        select: { deviceId: true }
      });
      const deviceIds = unique([auth.deviceId, ...devices.map((device) => device.deviceId)]);

      const supportEventsDeleted = await tx.supportEvent.deleteMany({
        where: { deviceId: { in: deviceIds } }
      });
      const auditLogsDeleted = await tx.auditLog.deleteMany({
        where: { userId }
      });
      const sessions = await tx.streamSession.findMany({
        where: { profile: { userId } },
        select: { id: true }
      });
      const apiErrorsDeleted = await deleteAccountApiErrors(
        tx,
        deviceIds,
        sessions.map((session) => session.id)
      );

      const userDeleted = await tx.user.deleteMany({
        where: { id: userId }
      });

      return {
        deleted: userDeleted.count > 0,
        userId,
        deviceIds,
        supportEventsDeleted: supportEventsDeleted.count,
        auditLogsDeleted: auditLogsDeleted.count,
        apiErrorsDeleted
      };
    });
  }

  async disconnectYouTube(auth: AuthContext): Promise<AccountDisconnectResult> {
    const userId = await findUserIdForDevice(this.prisma, auth);
    if (!userId) {
      return {
        disconnected: false,
        removedAccounts: 0,
        revocationAttempted: false,
        revokedTokens: 0,
        revocationFailures: 0
      };
    }

    const accounts = await this.prisma.linkedAccount.findMany({
      where: {
        userId,
        provider: "youtube"
      },
      select: {
        encryptedAccess: true,
        encryptedRefresh: true
      }
    });
    const revocation = await revokeYouTubeTokens(accounts);
    const deleted = await this.prisma.linkedAccount.deleteMany({
      where: {
        userId,
        provider: "youtube"
      }
    });

    return {
      disconnected: deleted.count > 0,
      removedAccounts: deleted.count,
      revocationAttempted: revocation.attempted,
      revokedTokens: revocation.revoked,
      revocationFailures: revocation.failed
    };
  }
}

function emptyAccountExport(auth: AuthContext, exportedAt: Date): AccountDataExport {
  return {
    exportedAt: exportedAt.toISOString(),
    account: {
      userId: null,
      deviceId: auth.deviceId,
      installId: auth.installId,
      displayName: null,
      email: null,
      createdAt: null,
      updatedAt: null
    },
    devices: [],
    linkedAccounts: [],
    profiles: [],
    subscription: null,
    supportEvents: [],
    apiErrors: [],
    auditLogs: []
  };
}

function mapUserExport(
  auth: AuthContext,
  user: PrismaAccountExportUser,
  supportEvents: Array<{
    id: string;
    deviceId: string | null;
    severity: string;
    message: string;
    detailsJson: Prisma.JsonValue;
    createdAt: Date;
  }>,
  apiErrors: AccountApiErrorRow[],
  exportedAt: Date
): AccountDataExport {
  const nestedSessionIds = new Set(collectSessionIds(user));
  return {
    exportedAt: exportedAt.toISOString(),
    account: {
      userId: user.id,
      deviceId: auth.deviceId,
      installId: auth.installId,
      displayName: user.displayName,
      email: user.email,
      createdAt: user.createdAt.toISOString(),
      updatedAt: user.updatedAt.toISOString()
    },
    devices: user.devices.map((device) => ({
      id: device.id,
      deviceId: device.deviceId,
      installId: device.installId,
      appVersion: device.appVersion,
      lastSeenAt: device.lastSeenAt.toISOString(),
      createdAt: device.createdAt.toISOString()
    })),
    linkedAccounts: user.linkedAccounts.map((account) => ({
      id: account.id,
      provider: account.provider,
      providerAccountId: account.providerAccountId,
      channelId: account.channelId,
      channelTitle: account.channelTitle,
      hasAccessToken: Boolean(account.encryptedAccess),
      hasRefreshToken: Boolean(account.encryptedRefresh),
      tokenExpiresAt: account.tokenExpiresAt?.toISOString() ?? null,
      createdAt: account.createdAt.toISOString(),
      updatedAt: account.updatedAt.toISOString()
    })),
    profiles: user.profiles.map(mapProfileExport),
    subscription: user.subscription
      ? {
        id: user.subscription.id,
        source: user.subscription.source,
        status: user.subscription.status,
        productId: user.subscription.productId,
        currentPeriodEndsAt: user.subscription.currentPeriodEndsAt?.toISOString() ?? null,
        createdAt: user.subscription.createdAt.toISOString(),
        updatedAt: user.subscription.updatedAt.toISOString(),
        entitlements: user.subscription.entitlements.map((entitlement) => ({
          id: entitlement.id,
          key: entitlement.key,
          enabled: entitlement.enabled,
          expiresAt: entitlement.expiresAt?.toISOString() ?? null
        }))
      }
      : null,
    supportEvents: supportEvents.map((event) => ({
      id: event.id,
      deviceId: event.deviceId,
      severity: event.severity,
      message: event.message,
      details: event.detailsJson,
      createdAt: event.createdAt.toISOString()
    })),
    apiErrors: apiErrors
      .filter((error) => !error.sessionId || !nestedSessionIds.has(error.sessionId))
      .map(mapApiErrorExport),
    auditLogs: user.auditLogs.map((log) => ({
      id: log.id,
      action: log.action,
      metadata: log.metadata,
      createdAt: log.createdAt.toISOString()
    }))
  };
}

function mapProfileExport(profile: PrismaAccountExportUser["profiles"][number]): AccountProfileExport {
  return {
    id: profile.id,
    channelId: profile.channelId,
    name: profile.name,
    config: profile.configJson,
    createdAt: profile.createdAt.toISOString(),
    updatedAt: profile.updatedAt.toISOString(),
    discordWebhook: profile.discordWebhook
      ? {
        configured: Boolean(profile.discordWebhook.encryptedWebhookUrl),
        enabled: profile.discordWebhook.enabled,
        alertModerationActions: profile.discordWebhook.alertModerationActions,
        alertRuntimeStatus: profile.discordWebhook.alertRuntimeStatus,
        createdAt: profile.discordWebhook.createdAt.toISOString(),
        updatedAt: profile.discordWebhook.updatedAt.toISOString()
      }
      : null,
    commands: profile.commands.map((command) => ({
      id: command.id,
      name: command.name,
      response: command.response,
      aliases: command.aliasesJson,
      cooldownSeconds: command.cooldownSeconds,
      accessLevel: command.accessLevel,
      enabled: command.enabled,
      createdAt: command.createdAt.toISOString(),
      updatedAt: command.updatedAt.toISOString()
    })),
    timers: profile.timers.map((timer) => ({
      id: timer.id,
      name: timer.name,
      message: timer.message,
      intervalMinutes: timer.intervalMinutes,
      minChatMessages: timer.minChatMessages,
      quietStartMinutes: timer.quietStartMinutes,
      quietEndMinutes: timer.quietEndMinutes,
      enabled: timer.enabled,
      lastSentAt: timer.lastSentAt?.toISOString() ?? null,
      createdAt: timer.createdAt.toISOString(),
      updatedAt: timer.updatedAt.toISOString()
    })),
    backups: profile.backups.map((backup) => ({
      id: backup.id,
      version: backup.version,
      channelId: backup.channelId,
      profileName: backup.profileName,
      clientVersion: backup.clientVersion,
      config: decryptBackupConfigFromStorage(backup.configJson),
      createdAt: backup.createdAt.toISOString()
    })),
    rulePresets: profile.rulePresets.map((preset) => ({
      id: preset.id,
      name: preset.name,
      config: preset.configJson,
      isDefault: preset.isDefault,
      createdAt: preset.createdAt.toISOString(),
      updatedAt: preset.updatedAt.toISOString()
    })),
    whitelist: profile.whitelist.map((entry) => ({
      id: entry.id,
      authorChannelId: entry.authorChannelId,
      displayName: entry.displayName,
      temporaryUntil: entry.temporaryUntil?.toISOString() ?? null,
      createdAt: entry.createdAt.toISOString()
    })),
    userProfiles: profile.userProfiles.map((userProfile) => ({
      id: userProfile.id,
      authorChannelId: userProfile.authorChannelId,
      displayName: userProfile.displayName,
      profileImageUrl: userProfile.profileImageUrl,
      firstSeenAt: userProfile.firstSeenAt.toISOString(),
      lastSeenAt: userProfile.lastSeenAt.toISOString(),
      messageCount: userProfile.messageCount,
      notes: userProfile.notes,
      strikes: userProfile.strikes.map((strike) => ({
        id: strike.id,
        reason: strike.reason,
        createdAt: strike.createdAt.toISOString()
      })),
      moderationActions: userProfile.moderationActions.map((action) => ({
        id: action.id,
        actionType: action.actionType,
        liveChatId: action.liveChatId,
        reason: action.reason,
        durationSeconds: action.durationSeconds,
        createdAt: action.createdAt.toISOString(),
        expiresAt: action.expiresAt?.toISOString() ?? null
      }))
    })),
    streamSessions: profile.sessions.map((session) => ({
      id: session.id,
      videoId: session.videoId,
      liveChatId: session.liveChatId,
      title: session.title,
      startedAt: session.startedAt.toISOString(),
      endedAt: session.endedAt?.toISOString() ?? null,
      messages: session.messages.map((message) => ({
        id: message.id,
        youtubeMessageId: message.youtubeMessageId,
        authorChannelId: message.authorChannelId,
        authorName: message.authorName,
        text: message.text,
        receivedAt: message.receivedAt.toISOString(),
        createdAt: message.createdAt.toISOString()
      })),
      actions: session.actions.map((action) => ({
        id: action.id,
        youtubeMessageId: action.youtubeMessageId,
        authorChannelId: action.authorChannelId,
        actionType: action.actionType,
        reason: action.reason,
        confidence: action.confidence,
        metadata: action.metadata,
        createdAt: action.createdAt.toISOString()
      })),
      runtimeEvents: session.runtimeEvents.map((event) => ({
        id: event.id,
        type: event.type,
        message: event.message,
        metadata: event.metadata,
        createdAt: event.createdAt.toISOString()
      })),
      apiErrors: session.apiErrors.map((error) => ({
        id: error.id,
        provider: error.provider,
        code: error.code,
        message: error.message,
        metadata: error.metadata,
        createdAt: error.createdAt.toISOString()
      }))
    }))
  };
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}

type AccountApiErrorRow = {
  id: string;
  sessionId: string | null;
  provider: string;
  code: string | null;
  message: string;
  metadata: Prisma.JsonValue;
  createdAt: Date;
};

function collectDeviceIds(auth: AuthContext, user: PrismaAccountExportUser): string[] {
  return unique([auth.deviceId, ...user.devices.map((device) => device.deviceId)]);
}

function collectSessionIds(user: PrismaAccountExportUser): string[] {
  return user.profiles.flatMap((profile) => profile.sessions.map((session) => session.id));
}

async function findAccountApiErrors(
  client: ApiErrorClient,
  deviceIds: string[],
  sessionIds: string[]
): Promise<AccountApiErrorRow[]> {
  const where = buildApiErrorAccountWhere(deviceIds, sessionIds);
  if (!where) {
    return [];
  }

  return client.apiError.findMany({
    where,
    orderBy: { createdAt: "desc" }
  });
}

async function deleteAccountApiErrors(
  client: ApiErrorClient,
  deviceIds: string[],
  sessionIds: string[]
): Promise<number> {
  const where = buildApiErrorAccountWhere(deviceIds, sessionIds);
  if (!where) {
    return 0;
  }

  const deleted = await client.apiError.deleteMany({ where });
  return deleted.count;
}

function buildApiErrorAccountWhere(deviceIds: string[], sessionIds: string[]): Prisma.ApiErrorWhereInput | null {
  const filters: Prisma.ApiErrorWhereInput[] = [];
  if (sessionIds.length > 0) {
    filters.push({ sessionId: { in: sessionIds } });
  }

  for (const deviceId of deviceIds) {
    filters.push({
      metadata: {
        path: ["deviceId"],
        equals: deviceId
      }
    });
  }

  return filters.length > 0 ? { OR: filters } : null;
}

function mapApiErrorExport(error: AccountApiErrorRow): AccountDataExport["apiErrors"][number] {
  return {
    id: error.id,
    sessionId: error.sessionId,
    provider: error.provider,
    code: error.code,
    message: error.message,
    metadata: error.metadata,
    createdAt: error.createdAt.toISOString()
  };
}

async function revokeYouTubeTokens(
  accounts: Array<{
    encryptedAccess: string | null;
    encryptedRefresh: string | null;
  }>
): Promise<{ attempted: boolean; revoked: number; failed: number }> {
  if (!isYouTubeOAuthConfigured() || accounts.length === 0) {
    return {
      attempted: false,
      revoked: 0,
      failed: 0
    };
  }

  const tokens = unique(
    accounts.flatMap((account) => [
      decryptSecret(account.encryptedRefresh),
      decryptSecret(account.encryptedAccess)
    ]).filter((token): token is string => Boolean(token))
  );
  const client = createOAuthClient();
  let revoked = 0;
  let failed = 0;

  for (const token of tokens) {
    try {
      await client.revokeToken(token);
      revoked += 1;
    } catch {
      failed += 1;
    }
  }

  return {
    attempted: tokens.length > 0,
    revoked,
    failed
  };
}
