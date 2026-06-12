import type { PrismaClient } from "@prisma/client";

const dayMillis = 24 * 60 * 60 * 1000;

export interface RetentionOptions {
  apply: boolean;
  supportEventDays: number;
  apiErrorDays: number;
  streamLogDays: number;
  backupVersionsPerProfile: number;
  now?: Date;
}

export interface RetentionCliOptions extends RetentionOptions {
  json: boolean;
}

export interface RetentionSummary {
  mode: "dry-run" | "apply";
  cutoffs: {
    supportEventsBefore: string;
    apiErrorsBefore: string;
    streamLogsBefore: string;
  };
  scanned: {
    endedSessionsPastRetention: number;
    backupRows: number;
  };
  pruned: {
    supportEvents: number;
    apiErrors: number;
    chatMessages: number;
    moderationActions: number;
    runtimeEvents: number;
    backups: number;
  };
}

export const defaultRetentionOptions: Omit<RetentionOptions, "apply"> = {
  supportEventDays: 90,
  apiErrorDays: 30,
  streamLogDays: 60,
  backupVersionsPerProfile: 10
};

export type RetentionPrismaClient = Pick<
  PrismaClient,
  "supportEvent" |
  "apiError" |
  "streamSession" |
  "chatMessageLog" |
  "moderationActionLog" |
  "botRuntimeEvent" |
  "backup"
>;

export async function pruneRetentionData(
  prisma: RetentionPrismaClient,
  options: RetentionOptions
): Promise<RetentionSummary> {
  const now = options.now ?? new Date();
  const supportEventsBefore = cutoffForDays(now, options.supportEventDays);
  const apiErrorsBefore = cutoffForDays(now, options.apiErrorDays);
  const streamLogsBefore = cutoffForDays(now, options.streamLogDays);
  const oldSessionRows = await prisma.streamSession.findMany({
    where: {
      endedAt: {
        lt: streamLogsBefore
      }
    },
    select: {
      id: true
    }
  });
  const oldSessionIds = oldSessionRows.map((row) => row.id);
  const backupRows = await prisma.backup.findMany({
    select: {
      id: true,
      profileId: true,
      createdAt: true
    }
  });
  const oldBackupIds = backupIdsBeyondLimit(backupRows, options.backupVersionsPerProfile);

  return {
    mode: options.apply ? "apply" : "dry-run",
    cutoffs: {
      supportEventsBefore: supportEventsBefore.toISOString(),
      apiErrorsBefore: apiErrorsBefore.toISOString(),
      streamLogsBefore: streamLogsBefore.toISOString()
    },
    scanned: {
      endedSessionsPastRetention: oldSessionIds.length,
      backupRows: backupRows.length
    },
    pruned: {
      supportEvents: await deleteOrCountSupportEvents(prisma, supportEventsBefore, options.apply),
      apiErrors: await deleteOrCountApiErrors(prisma, apiErrorsBefore, options.apply),
      chatMessages: await deleteOrCountChatMessages(prisma, oldSessionIds, options.apply),
      moderationActions: await deleteOrCountModerationActions(prisma, oldSessionIds, options.apply),
      runtimeEvents: await deleteOrCountRuntimeEvents(prisma, oldSessionIds, options.apply),
      backups: await deleteOrCountBackups(prisma, oldBackupIds, options.apply)
    }
  };
}

export function parseRetentionCliOptions(
  args: string[] = [],
  env: NodeJS.ProcessEnv = process.env
): RetentionCliOptions {
  const values = new Map<string, string>();
  let apply = false;
  let json = false;

  for (const arg of args) {
    if (arg === "--apply") {
      apply = true;
      continue;
    }
    if (arg === "--dry-run") {
      apply = false;
      continue;
    }
    if (arg === "--json") {
      json = true;
      continue;
    }

    const match = /^--([a-z0-9-]+)=(.+)$/i.exec(arg);
    if (!match) {
      throw new Error(`Unknown retention option: ${arg}`);
    }
    values.set(match[1], match[2]);
  }

  return {
    apply,
    json,
    supportEventDays: retentionInteger({
      label: "support event retention days",
      value: values.get("support-event-days") ?? env.SUPPORT_EVENT_RETENTION_DAYS,
      fallback: defaultRetentionOptions.supportEventDays,
      min: 7,
      max: 3650
    }),
    apiErrorDays: retentionInteger({
      label: "API error retention days",
      value: values.get("api-error-days") ?? env.API_ERROR_RETENTION_DAYS,
      fallback: defaultRetentionOptions.apiErrorDays,
      min: 7,
      max: 3650
    }),
    streamLogDays: retentionInteger({
      label: "stream log retention days",
      value: values.get("stream-log-days") ?? env.STREAM_LOG_RETENTION_DAYS,
      fallback: defaultRetentionOptions.streamLogDays,
      min: 7,
      max: 3650
    }),
    backupVersionsPerProfile: retentionInteger({
      label: "backup versions per profile",
      value: values.get("backup-versions-per-profile") ?? env.BACKUP_VERSIONS_PER_PROFILE,
      fallback: defaultRetentionOptions.backupVersionsPerProfile,
      min: 1,
      max: 200
    })
  };
}

export function backupIdsBeyondLimit(
  rows: Array<{ id: string; profileId: string; createdAt: Date }>,
  keepPerProfile: number
): string[] {
  if (!Number.isInteger(keepPerProfile) || keepPerProfile < 1) {
    throw new Error("backupVersionsPerProfile must be at least 1.");
  }

  const seenByProfile = new Map<string, number>();
  const pruneIds: string[] = [];
  const sortedRows = [...rows].sort((left, right) => {
    const profileCompare = left.profileId.localeCompare(right.profileId);
    if (profileCompare !== 0) {
      return profileCompare;
    }

    const createdCompare = right.createdAt.getTime() - left.createdAt.getTime();
    if (createdCompare !== 0) {
      return createdCompare;
    }

    return right.id.localeCompare(left.id);
  });

  for (const row of sortedRows) {
    const seen = seenByProfile.get(row.profileId) ?? 0;
    seenByProfile.set(row.profileId, seen + 1);
    if (seen >= keepPerProfile) {
      pruneIds.push(row.id);
    }
  }

  return pruneIds;
}

export function cutoffForDays(now: Date, days: number): Date {
  return new Date(now.getTime() - days * dayMillis);
}

function retentionInteger(input: {
  label: string;
  value: string | undefined;
  fallback: number;
  min: number;
  max: number;
}): number {
  if (input.value === undefined || input.value.trim().length === 0) {
    return input.fallback;
  }

  const parsed = Number(input.value);
  if (!Number.isInteger(parsed) || parsed < input.min || parsed > input.max) {
    throw new Error(`${input.label} must be an integer from ${input.min} to ${input.max}.`);
  }

  return parsed;
}

async function deleteOrCountSupportEvents(
  prisma: RetentionPrismaClient,
  before: Date,
  apply: boolean
): Promise<number> {
  const where = {
    createdAt: {
      lt: before
    }
  };
  if (!apply) {
    return prisma.supportEvent.count({ where });
  }

  return (await prisma.supportEvent.deleteMany({ where })).count;
}

async function deleteOrCountApiErrors(
  prisma: RetentionPrismaClient,
  before: Date,
  apply: boolean
): Promise<number> {
  const where = {
    createdAt: {
      lt: before
    }
  };
  if (!apply) {
    return prisma.apiError.count({ where });
  }

  return (await prisma.apiError.deleteMany({ where })).count;
}

async function deleteOrCountChatMessages(
  prisma: RetentionPrismaClient,
  sessionIds: string[],
  apply: boolean
): Promise<number> {
  if (sessionIds.length === 0) {
    return 0;
  }

  const where = {
    sessionId: {
      in: sessionIds
    }
  };
  if (!apply) {
    return prisma.chatMessageLog.count({ where });
  }

  return (await prisma.chatMessageLog.deleteMany({ where })).count;
}

async function deleteOrCountModerationActions(
  prisma: RetentionPrismaClient,
  sessionIds: string[],
  apply: boolean
): Promise<number> {
  if (sessionIds.length === 0) {
    return 0;
  }

  const where = {
    sessionId: {
      in: sessionIds
    }
  };
  if (!apply) {
    return prisma.moderationActionLog.count({ where });
  }

  return (await prisma.moderationActionLog.deleteMany({ where })).count;
}

async function deleteOrCountRuntimeEvents(
  prisma: RetentionPrismaClient,
  sessionIds: string[],
  apply: boolean
): Promise<number> {
  if (sessionIds.length === 0) {
    return 0;
  }

  const where = {
    sessionId: {
      in: sessionIds
    }
  };
  if (!apply) {
    return prisma.botRuntimeEvent.count({ where });
  }

  return (await prisma.botRuntimeEvent.deleteMany({ where })).count;
}

async function deleteOrCountBackups(
  prisma: RetentionPrismaClient,
  backupIds: string[],
  apply: boolean
): Promise<number> {
  if (backupIds.length === 0) {
    return 0;
  }

  const where = {
    id: {
      in: backupIds
    }
  };
  if (!apply) {
    return backupIds.length;
  }

  return (await prisma.backup.deleteMany({ where })).count;
}
