import type { FastifyInstance } from "fastify";
import { z } from "zod";
import { HttpError } from "../../lib/httpErrors.js";
import { requireAuth } from "../../plugins/auth.js";
import { isYouTubeOAuthConfigured } from "../youtube/youtubeOAuth.js";
import { createYouTubeClientForAuth } from "../youtube/youtubeTokenStore.js";
import { MockYouTubeLiveChatClient } from "../youtube/youtubeClient.js";
import { liveChatTextSchema } from "../youtube/youtubeMessageSafety.js";
import {
  createUserProfileStore,
  userProfileNotesInputSchema,
  userWhitelistInputSchema,
  userWarningInputSchema,
  type UserProfileStore
} from "./userProfileStore.js";

const warningRequestSchema = userWarningInputSchema.extend({
  liveChatId: z.string().min(1).max(256).optional(),
  warningText: liveChatTextSchema.optional()
});

const hideUserRequestSchema = z.object({
  liveChatId: z.string().min(1).max(256),
  reason: z.string().min(1).max(300).default("manual_hide")
});

const timeoutUserRequestSchema = z.object({
  liveChatId: z.string().min(1).max(256),
  durationSeconds: z.number().int().min(60).max(86400).default(300),
  reason: z.string().min(1).max(300).default("manual_timeout")
});

const unbanUserRequestSchema = z.object({
  liveChatBanId: z.string().min(1).max(256),
  liveChatId: z.string().min(1).max(256).nullable().optional(),
  reason: z.string().min(1).max(300).default("manual_unban")
});

export interface UserProfileRoutesOptions {
  store?: UserProfileStore;
}

export async function userProfileRoutes(
  app: FastifyInstance,
  options: UserProfileRoutesOptions = {}
): Promise<void> {
  const store = options.store ?? createUserProfileStore();
  const youtube = new MockYouTubeLiveChatClient();

  app.get("/", { preHandler: requireAuth }, async (request) => {
    const query = z.object({ profileId: z.string().min(1).optional() }).parse(request.query);
    return { users: await store.list(request.auth!, query.profileId) };
  });

  app.patch("/:id/notes", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = userProfileNotesInputSchema.parse(request.body);
    const user = await store.updateNotes(request.auth!, params.id, body);
    if (!user) {
      throw new HttpError(404, "User profile not found.");
    }

    return { user };
  });

  app.post("/:id/hide", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = hideUserRequestSchema.parse(request.body);
    const user = await store.get(request.auth!, params.id);
    if (!user) {
      throw new HttpError(404, "User profile not found.");
    }

    const client = await createYouTubeClientForAuth(request.auth!) ?? (
      isYouTubeOAuthConfigured() ? null : youtube
    );
    if (!client) {
      throw new HttpError(409, "Connect YouTube before using live chat actions.");
    }

    const hideResult = await client.hideUser(body.liveChatId, user.authorChannelId);
    const hiddenAt = new Date();
    const history = await store.recordModerationAction(request.auth!, params.id, {
      actionType: "hideUser",
      liveChatId: body.liveChatId,
      liveChatBanId: hideResult.liveChatBanId,
      reason: body.reason
    });
    if (!history) {
      throw new HttpError(404, "User profile not found.");
    }

    return {
      user: history.user,
      action: history.action,
      liveChatId: body.liveChatId,
      liveChatBanId: hideResult.liveChatBanId,
      reason: body.reason,
      actionType: "hideUser",
      hiddenAt: hiddenAt.toISOString()
    };
  });

  app.post("/:id/timeout", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = timeoutUserRequestSchema.parse(request.body);
    const user = await store.get(request.auth!, params.id);
    if (!user) {
      throw new HttpError(404, "User profile not found.");
    }

    const client = await createYouTubeClientForAuth(request.auth!) ?? (
      isYouTubeOAuthConfigured() ? null : youtube
    );
    if (!client) {
      throw new HttpError(409, "Connect YouTube before using live chat actions.");
    }

    const timedOutAt = new Date();
    const timedOutUntil = new Date(timedOutAt.getTime() + body.durationSeconds * 1000);
    const timeoutResult = await client.hideUser(body.liveChatId, user.authorChannelId, {
      durationSeconds: body.durationSeconds
    });
    const history = await store.recordModerationAction(request.auth!, params.id, {
      actionType: "timeoutUser",
      liveChatId: body.liveChatId,
      liveChatBanId: timeoutResult.liveChatBanId,
      reason: body.reason,
      durationSeconds: body.durationSeconds,
      expiresAt: timedOutUntil.toISOString()
    });
    if (!history) {
      throw new HttpError(404, "User profile not found.");
    }

    return {
      user: history.user,
      action: history.action,
      liveChatId: body.liveChatId,
      liveChatBanId: timeoutResult.liveChatBanId,
      durationSeconds: body.durationSeconds,
      reason: body.reason,
      actionType: "timeoutUser",
      timedOutAt: timedOutAt.toISOString(),
      timedOutUntil: timedOutUntil.toISOString()
    };
  });

  app.post("/:id/unban", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = unbanUserRequestSchema.parse(request.body);
    const user = await store.get(request.auth!, params.id);
    if (!user) {
      throw new HttpError(404, "User profile not found.");
    }

    const client = await createYouTubeClientForAuth(request.auth!) ?? (
      isYouTubeOAuthConfigured() ? null : youtube
    );
    if (!client) {
      throw new HttpError(409, "Connect YouTube before using live chat actions.");
    }

    await client.unbanUser(body.liveChatBanId);
    const unbannedAt = new Date();
    const history = await store.recordModerationAction(request.auth!, params.id, {
      actionType: "unbanUser",
      liveChatId: body.liveChatId ?? null,
      liveChatBanId: body.liveChatBanId,
      reason: body.reason
    });
    if (!history) {
      throw new HttpError(404, "User profile not found.");
    }

    return {
      user: history.user,
      action: history.action,
      liveChatBanId: body.liveChatBanId,
      reason: body.reason,
      actionType: "unbanUser",
      unbannedAt: unbannedAt.toISOString()
    };
  });

  app.post("/:id/whitelist", { preHandler: requireAuth }, async (request) => {
    const params = z.object({ id: z.string().min(1) }).parse(request.params);
    const body = userWhitelistInputSchema.parse(request.body ?? {});
    const result = await store.whitelist(request.auth!, params.id, body);
    if (!result) {
      throw new HttpError(404, "User profile not found.");
    }

    return {
      ...result,
      whitelistedAt: new Date().toISOString()
    };
  });

  app.post("/warnings", { preHandler: requireAuth }, async (request, reply) => {
    const body = warningRequestSchema.parse(request.body);
    let messageId: string | null = null;

    if (body.liveChatId && body.warningText) {
      const client = await createYouTubeClientForAuth(request.auth!) ?? (
        isYouTubeOAuthConfigured() ? null : youtube
      );
      if (!client) {
        throw new HttpError(409, "Connect YouTube before using live chat actions.");
      }

      const sent = await client.sendMessage(body.liveChatId, body.warningText);
      messageId = sent.messageId;
    }

    const warning = await store.warn(request.auth!, {
      profileId: body.profileId,
      authorChannelId: body.authorChannelId,
      displayName: body.displayName,
      profileImageUrl: body.profileImageUrl,
      reason: body.reason
    });

    return reply.status(201).send({
      ...warning,
      messageId,
      warnedAt: new Date().toISOString()
    });
  });
}
