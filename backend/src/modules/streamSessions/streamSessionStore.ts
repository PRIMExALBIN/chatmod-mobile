import type { Prisma, PrismaClient } from "@prisma/client";
import { nanoid } from "nanoid";
import { z } from "zod";
import { getPrismaClient, shouldUsePrisma } from "../../db/prisma.js";
import { notFound } from "../../lib/httpErrors.js";
import { resolveUserIdForDevice } from "../accounts/deviceUser.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const streamSessionInputSchema = z.object({
  profileId: z.string().min(1),
  videoId: z.string().min(1).max(128),
  liveChatId: z.string().min(1).max(256),
  title: z.string().min(1).max(200).nullable().optional(),
  startedAt: z.string().datetime().optional()
});

export const streamSessionEndSchema = z.object({
  endedAt: z.string().datetime().optional()
}).default({});

export const chatMessageLogInputSchema = z.object({
  youtubeMessageId: z.string().min(1).max(128),
  authorChannelId: z.string().min(1).max(128),
  authorName: z.string().min(1).max(120),
  text: z.string().min(1).max(2000),
  receivedAt: z.string().datetime().optional()
});

export const moderationActionLogInputSchema = z.object({
  clientActionId: z.string().min(1).max(160).nullable().optional(),
  youtubeMessageId: z.string().min(1).max(128).nullable().optional(),
  authorChannelId: z.string().min(1).max(128).nullable().optional(),
  actionType: z.enum([
    "allow",
    "flagForReview",
    "deleteMessage",
    "hideUser",
    "timeoutUser",
    "unbanUser",
    "warnUser",
    "strikeUser",
    "sendAutoReply"
  ]),
  reason: z.string().min(1).max(300),
  confidence: z.number().min(0).max(1).nullable().optional(),
  metadata: z.record(z.unknown()).optional()
});

export const moderationActionReviewSchema = z.object({
  reviewStatus: z.enum(["false_positive"]),
  reviewNote: z.string().max(300).nullable().optional()
});

export const runtimeEventInputSchema = z.object({
  type: z.string().min(1).max(80),
  message: z.string().min(1).max(500),
  metadata: z.record(z.unknown()).optional()
});

export type StreamSessionInput = z.infer<typeof streamSessionInputSchema>;
export type StreamSessionEndInput = z.infer<typeof streamSessionEndSchema>;
export type ChatMessageLogInput = z.infer<typeof chatMessageLogInputSchema>;
export type ModerationActionLogInput = z.infer<typeof moderationActionLogInputSchema>;
export type ModerationActionReviewInput = z.infer<typeof moderationActionReviewSchema>;
export type RuntimeEventInput = z.infer<typeof runtimeEventInputSchema>;

export interface StreamSessionRecord {
  id: string;
  profileId: string;
  videoId: string;
  liveChatId: string;
  title: string | null;
  startedAt: string;
  endedAt: string | null;
}

export interface ChatMessageLogRecord {
  id: string;
  sessionId: string;
  youtubeMessageId: string;
  authorChannelId: string;
  authorName: string;
  text: string;
  receivedAt: string;
  createdAt: string;
}

export interface ModerationActionLogRecord {
  id: string;
  sessionId: string;
  youtubeMessageId: string | null;
  authorChannelId: string | null;
  actionType: string;
  reason: string;
  confidence: number | null;
  metadata: Record<string, unknown> | null;
  reviewStatus: string | null;
  reviewedAt: string | null;
  reviewNote: string | null;
  createdAt: string;
}

export interface RuntimeEventRecord {
  id: string;
  sessionId: string;
  type: string;
  message: string;
  metadata: Record<string, unknown> | null;
  createdAt: string;
}

export interface StreamSessionLogBundle {
  session: StreamSessionRecord;
  messages: ChatMessageLogRecord[];
  actions: ModerationActionLogRecord[];
  runtimeEvents: RuntimeEventRecord[];
}

export interface StreamSessionStore {
  list(auth: AuthContext, profileId?: string): Promise<StreamSessionRecord[]>;
  upsert(auth: AuthContext, id: string, input: StreamSessionInput): Promise<StreamSessionRecord>;
  end(auth: AuthContext, id: string, input: StreamSessionEndInput): Promise<StreamSessionRecord>;
  getLogs(auth: AuthContext, id: string): Promise<StreamSessionLogBundle>;
  recordMessage(auth: AuthContext, sessionId: string, input: ChatMessageLogInput): Promise<ChatMessageLogRecord>;
  recordAction(auth: AuthContext, sessionId: string, input: ModerationActionLogInput): Promise<ModerationActionLogRecord>;
  updateActionReview(
    auth: AuthContext,
    sessionId: string,
    actionId: string,
    input: ModerationActionReviewInput
  ): Promise<ModerationActionLogRecord>;
  recordRuntimeEvent(auth: AuthContext, sessionId: string, input: RuntimeEventInput): Promise<RuntimeEventRecord>;
}

export function createStreamSessionStore(): StreamSessionStore {
  return shouldUsePrisma() ? new PrismaStreamSessionStore(getPrismaClient()) : new InMemoryStreamSessionStore();
}

class InMemoryStreamSessionStore implements StreamSessionStore {
  private sessions = new Map<string, StreamSessionRecord & { ownerKey: string }>();
  private messages = new Map<string, ChatMessageLogRecord & { ownerKey: string }>();
  private actions = new Map<string, ModerationActionLogRecord & { ownerKey: string }>();
  private runtimeEvents = new Map<string, RuntimeEventRecord & { ownerKey: string }>();

  async list(auth: AuthContext, profileId?: string): Promise<StreamSessionRecord[]> {
    return Array.from(this.sessions.values())
      .filter((session) => session.ownerKey === auth.deviceId)
      .filter((session) => (profileId ? session.profileId === profileId : true))
      .sort((a, b) => b.startedAt.localeCompare(a.startedAt))
      .map(stripOwner);
  }

  async upsert(auth: AuthContext, id: string, input: StreamSessionInput): Promise<StreamSessionRecord> {
    const existing = this.sessions.get(id);
    if (existing && existing.ownerKey !== auth.deviceId) {
      throw notFound("Stream session not found.");
    }

    const row: StreamSessionRecord & { ownerKey: string } = {
      id,
      ownerKey: auth.deviceId,
      profileId: input.profileId,
      videoId: input.videoId,
      liveChatId: input.liveChatId,
      title: input.title ?? null,
      startedAt: input.startedAt ?? existing?.startedAt ?? new Date().toISOString(),
      endedAt: existing?.endedAt ?? null
    };
    this.sessions.set(id, row);
    return stripOwner(row);
  }

  async end(auth: AuthContext, id: string, input: StreamSessionEndInput): Promise<StreamSessionRecord> {
    const existing = this.sessions.get(id);
    if (!existing || existing.ownerKey !== auth.deviceId) {
      throw notFound("Stream session not found.");
    }

    const row = {
      ...existing,
      endedAt: input.endedAt ?? new Date().toISOString()
    };
    this.sessions.set(id, row);
    return stripOwner(row);
  }

  async getLogs(auth: AuthContext, id: string): Promise<StreamSessionLogBundle> {
    const session = this.sessions.get(id);
    if (!session || session.ownerKey !== auth.deviceId) {
      throw notFound("Stream session not found.");
    }

    return {
      session: stripOwner(session),
      messages: Array.from(this.messages.values()).filter((row) => row.ownerKey === auth.deviceId && row.sessionId === id).map(stripOwner),
      actions: Array.from(this.actions.values()).filter((row) => row.ownerKey === auth.deviceId && row.sessionId === id).map(stripOwner),
      runtimeEvents: Array.from(this.runtimeEvents.values()).filter((row) => row.ownerKey === auth.deviceId && row.sessionId === id).map(stripOwner)
    };
  }

  async recordMessage(auth: AuthContext, sessionId: string, input: ChatMessageLogInput): Promise<ChatMessageLogRecord> {
    this.assertSessionOwner(auth, sessionId);
    const key = `${sessionId}:${input.youtubeMessageId}`;
    const existing = this.messages.get(key);
    const now = new Date().toISOString();
    const row: ChatMessageLogRecord & { ownerKey: string } = {
      id: existing?.id ?? nanoid(),
      ownerKey: auth.deviceId,
      sessionId,
      youtubeMessageId: input.youtubeMessageId,
      authorChannelId: input.authorChannelId,
      authorName: input.authorName,
      text: input.text,
      receivedAt: input.receivedAt ?? existing?.receivedAt ?? now,
      createdAt: existing?.createdAt ?? now
    };
    this.messages.set(key, row);
    return stripOwner(row);
  }

  async recordAction(auth: AuthContext, sessionId: string, input: ModerationActionLogInput): Promise<ModerationActionLogRecord> {
    this.assertSessionOwner(auth, sessionId);
    const id = input.clientActionId ?? nanoid();
    const existing = this.actions.get(id);
    if (existing && (existing.ownerKey !== auth.deviceId || existing.sessionId !== sessionId)) {
      throw notFound("Moderation action log not found.");
    }
    const row: ModerationActionLogRecord & { ownerKey: string } = {
      id,
      ownerKey: auth.deviceId,
      sessionId,
      youtubeMessageId: input.youtubeMessageId ?? null,
      authorChannelId: input.authorChannelId ?? null,
      actionType: input.actionType,
      reason: input.reason,
      confidence: input.confidence ?? null,
      metadata: input.metadata ?? existing?.metadata ?? null,
      reviewStatus: existing?.reviewStatus ?? null,
      reviewedAt: existing?.reviewedAt ?? null,
      reviewNote: existing?.reviewNote ?? null,
      createdAt: existing?.createdAt ?? new Date().toISOString()
    };
    this.actions.set(row.id, row);
    return stripOwner(row);
  }

  async updateActionReview(
    auth: AuthContext,
    sessionId: string,
    actionId: string,
    input: ModerationActionReviewInput
  ): Promise<ModerationActionLogRecord> {
    this.assertSessionOwner(auth, sessionId);
    const existing = this.actions.get(actionId);
    if (!existing || existing.ownerKey !== auth.deviceId || existing.sessionId !== sessionId) {
      throw notFound("Moderation action log not found.");
    }

    const row: ModerationActionLogRecord & { ownerKey: string } = {
      ...existing,
      reviewStatus: input.reviewStatus,
      reviewedAt: new Date().toISOString(),
      reviewNote: input.reviewNote ?? null
    };
    this.actions.set(row.id, row);
    return stripOwner(row);
  }

  async recordRuntimeEvent(auth: AuthContext, sessionId: string, input: RuntimeEventInput): Promise<RuntimeEventRecord> {
    this.assertSessionOwner(auth, sessionId);
    const row: RuntimeEventRecord & { ownerKey: string } = {
      id: nanoid(),
      ownerKey: auth.deviceId,
      sessionId,
      type: input.type,
      message: input.message,
      metadata: input.metadata ?? null,
      createdAt: new Date().toISOString()
    };
    this.runtimeEvents.set(row.id, row);
    return stripOwner(row);
  }

  private assertSessionOwner(auth: AuthContext, sessionId: string): void {
    const session = this.sessions.get(sessionId);
    if (!session || session.ownerKey !== auth.deviceId) {
      throw notFound("Stream session not found.");
    }
  }
}

class PrismaStreamSessionStore implements StreamSessionStore {
  constructor(private readonly prisma: PrismaClient) {}

  async list(auth: AuthContext, profileId?: string): Promise<StreamSessionRecord[]> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const rows = await this.prisma.streamSession.findMany({
      where: {
        profile: {
          userId
        },
        ...(profileId ? { profileId } : {})
      },
      orderBy: { startedAt: "desc" }
    });

    return rows.map(mapSession);
  }

  async upsert(auth: AuthContext, id: string, input: StreamSessionInput): Promise<StreamSessionRecord> {
    await this.ensureProfile(auth, input.profileId);
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.streamSession.findUnique({
      where: { id },
      select: {
        id: true,
        profile: {
          select: { userId: true }
        }
      }
    });

    if (existing && existing.profile.userId !== userId) {
      throw notFound("Stream session not found.");
    }

    const row = existing
      ? await this.prisma.streamSession.update({
        where: { id },
        data: {
          profileId: input.profileId,
          videoId: input.videoId,
          liveChatId: input.liveChatId,
          title: input.title ?? null,
          startedAt: input.startedAt ? new Date(input.startedAt) : undefined
        }
      })
      : await this.prisma.streamSession.create({
        data: {
          id,
          profileId: input.profileId,
          videoId: input.videoId,
          liveChatId: input.liveChatId,
          title: input.title ?? null,
          startedAt: input.startedAt ? new Date(input.startedAt) : undefined
        }
      });

    return mapSession(row);
  }

  async end(auth: AuthContext, id: string, input: StreamSessionEndInput): Promise<StreamSessionRecord> {
    await this.assertSessionOwner(auth, id);
    const row = await this.prisma.streamSession.update({
      where: { id },
      data: {
        endedAt: input.endedAt ? new Date(input.endedAt) : new Date()
      }
    });

    return mapSession(row);
  }

  async getLogs(auth: AuthContext, id: string): Promise<StreamSessionLogBundle> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const row = await this.prisma.streamSession.findFirst({
      where: {
        id,
        profile: {
          userId
        }
      },
      include: {
        messages: { orderBy: { receivedAt: "asc" } },
        actions: { orderBy: { createdAt: "asc" } },
        runtimeEvents: { orderBy: { createdAt: "asc" } }
      }
    });
    if (!row) {
      throw notFound("Stream session not found.");
    }

    return {
      session: mapSession(row),
      messages: row.messages.map(mapMessage),
      actions: row.actions.map(mapAction),
      runtimeEvents: row.runtimeEvents.map(mapRuntimeEvent)
    };
  }

  async recordMessage(auth: AuthContext, sessionId: string, input: ChatMessageLogInput): Promise<ChatMessageLogRecord> {
    await this.assertSessionOwner(auth, sessionId);
    const row = await this.prisma.chatMessageLog.upsert({
      where: {
        sessionId_youtubeMessageId: {
          sessionId,
          youtubeMessageId: input.youtubeMessageId
        }
      },
      create: {
        sessionId,
        youtubeMessageId: input.youtubeMessageId,
        authorChannelId: input.authorChannelId,
        authorName: input.authorName,
        text: input.text,
        receivedAt: input.receivedAt ? new Date(input.receivedAt) : new Date()
      },
      update: {
        authorChannelId: input.authorChannelId,
        authorName: input.authorName,
        text: input.text,
        receivedAt: input.receivedAt ? new Date(input.receivedAt) : undefined
      }
    });

    return mapMessage(row);
  }

  async recordAction(auth: AuthContext, sessionId: string, input: ModerationActionLogInput): Promise<ModerationActionLogRecord> {
    await this.assertSessionOwner(auth, sessionId);
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    if (input.clientActionId) {
      const existing = await this.prisma.moderationActionLog.findUnique({
        where: { id: input.clientActionId },
        select: {
          sessionId: true,
          session: {
            select: {
              profile: {
                select: { userId: true }
              }
            }
          }
        }
      });
      if (existing && (existing.sessionId !== sessionId || existing.session.profile.userId !== userId)) {
        throw notFound("Moderation action log not found.");
      }
    }
    const data = {
      sessionId,
      youtubeMessageId: input.youtubeMessageId ?? null,
      authorChannelId: input.authorChannelId ?? null,
      actionType: input.actionType,
      reason: input.reason,
      confidence: input.confidence ?? null,
      metadata: input.metadata ? (input.metadata as Prisma.InputJsonValue) : undefined
    };
    const row = input.clientActionId
      ? await this.prisma.moderationActionLog.upsert({
        where: { id: input.clientActionId },
        create: {
          id: input.clientActionId,
          ...data
        },
        update: data
      })
      : await this.prisma.moderationActionLog.create({ data });

    return mapAction(row);
  }

  async updateActionReview(
    auth: AuthContext,
    sessionId: string,
    actionId: string,
    input: ModerationActionReviewInput
  ): Promise<ModerationActionLogRecord> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.moderationActionLog.findFirst({
      where: {
        id: actionId,
        sessionId,
        session: {
          profile: {
            userId
          }
        }
      },
      select: { id: true }
    });
    if (!existing) {
      throw notFound("Moderation action log not found.");
    }

    const row = await this.prisma.moderationActionLog.update({
      where: { id: actionId },
      data: {
        reviewStatus: input.reviewStatus,
        reviewedAt: new Date(),
        reviewNote: input.reviewNote ?? null
      }
    });

    return mapAction(row);
  }

  async recordRuntimeEvent(auth: AuthContext, sessionId: string, input: RuntimeEventInput): Promise<RuntimeEventRecord> {
    await this.assertSessionOwner(auth, sessionId);
    const row = await this.prisma.botRuntimeEvent.create({
      data: {
        sessionId,
        type: input.type,
        message: input.message,
        metadata: input.metadata ? (input.metadata as Prisma.InputJsonValue) : undefined
      }
    });

    return mapRuntimeEvent(row);
  }

  private async assertSessionOwner(auth: AuthContext, sessionId: string): Promise<void> {
    const userId = await resolveUserIdForDevice(this.prisma, auth);
    const existing = await this.prisma.streamSession.findFirst({
      where: {
        id: sessionId,
        profile: {
          userId
        }
      },
      select: { id: true }
    });

    if (!existing) {
      throw notFound("Stream session not found.");
    }
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

function stripOwner<T extends { ownerKey: string }>(row: T): Omit<T, "ownerKey"> {
  const { ownerKey: _ownerKey, ...record } = row;
  return record;
}

function mapSession(row: {
  id: string;
  profileId: string;
  videoId: string;
  liveChatId: string;
  title: string | null;
  startedAt: Date;
  endedAt: Date | null;
}): StreamSessionRecord {
  return {
    id: row.id,
    profileId: row.profileId,
    videoId: row.videoId,
    liveChatId: row.liveChatId,
    title: row.title,
    startedAt: row.startedAt.toISOString(),
    endedAt: row.endedAt?.toISOString() ?? null
  };
}

function mapMessage(row: {
  id: string;
  sessionId: string;
  youtubeMessageId: string;
  authorChannelId: string;
  authorName: string;
  text: string;
  receivedAt: Date;
  createdAt: Date;
}): ChatMessageLogRecord {
  return {
    id: row.id,
    sessionId: row.sessionId,
    youtubeMessageId: row.youtubeMessageId,
    authorChannelId: row.authorChannelId,
    authorName: row.authorName,
    text: row.text,
    receivedAt: row.receivedAt.toISOString(),
    createdAt: row.createdAt.toISOString()
  };
}

function mapAction(row: {
  id: string;
  sessionId: string;
  youtubeMessageId: string | null;
  authorChannelId: string | null;
  actionType: string;
  reason: string;
  confidence: number | null;
  metadata: unknown;
  reviewStatus?: string | null;
  reviewedAt?: Date | null;
  reviewNote?: string | null;
  createdAt: Date;
}): ModerationActionLogRecord {
  return {
    id: row.id,
    sessionId: row.sessionId,
    youtubeMessageId: row.youtubeMessageId,
    authorChannelId: row.authorChannelId,
    actionType: row.actionType,
    reason: row.reason,
    confidence: row.confidence,
    metadata: row.metadata && typeof row.metadata === "object" && !Array.isArray(row.metadata)
      ? row.metadata as Record<string, unknown>
      : null,
    reviewStatus: row.reviewStatus ?? null,
    reviewedAt: row.reviewedAt?.toISOString() ?? null,
    reviewNote: row.reviewNote ?? null,
    createdAt: row.createdAt.toISOString()
  };
}

function mapRuntimeEvent(row: {
  id: string;
  sessionId: string;
  type: string;
  message: string;
  metadata: unknown;
  createdAt: Date;
}): RuntimeEventRecord {
  return {
    id: row.id,
    sessionId: row.sessionId,
    type: row.type,
    message: row.message,
    metadata: typeof row.metadata === "object" && row.metadata !== null ? (row.metadata as Record<string, unknown>) : null,
    createdAt: row.createdAt.toISOString()
  };
}
