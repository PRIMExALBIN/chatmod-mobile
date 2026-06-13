import type { FastifyInstance, FastifyReply } from "fastify";
import { z } from "zod";
import { requireAuth } from "../../plugins/auth.js";
import type { AuthContext } from "../auth/sessionToken.js";
import {
  MockYouTubeLiveChatClient,
  type YouTubeBroadcastStatus,
  type YouTubeLiveBroadcast,
  type YouTubeLiveChatClient
} from "./youtubeClient.js";
import {
  buildYouTubeConnectUrl,
  createOAuthClient,
  exchangeYouTubeOAuthCode,
  isYouTubeOAuthConfigured,
  missingYouTubeOAuthEnv,
  verifyYouTubeOAuthState,
  youtubeOAuthScopes
} from "./youtubeOAuth.js";
import {
  getYouTubeLinkedAccountStatus,
  createYouTubeClientForAuth,
  storeYouTubeTokens,
  type YouTubeLinkedAccountStatus
} from "./youtubeTokenStore.js";
import { liveChatTextSchema } from "./youtubeMessageSafety.js";
import { fetchAuthenticatedYouTubeChannelIdentity } from "./googleYouTubeClient.js";

const discoverSchema = z.object({
  channelId: z.string().min(1),
  includeScheduled: z.boolean().default(true),
  maxResults: z.number().int().min(1).max(50).default(10)
});

const sendTestSchema = z.object({
  liveChatId: z.string().min(1),
  text: liveChatTextSchema
});

const listMessagesSchema = z.object({
  liveChatId: z.string().min(1),
  pageToken: z.string().min(1).max(512).optional()
});

const sendMessageSchema = z.object({
  liveChatId: z.string().min(1),
  text: liveChatTextSchema
});

const deleteMessageSchema = z.object({
  messageId: z.string().min(1).max(256),
  reason: z.string().min(1).max(300).default("manual_delete")
});

const hideUserSchema = z.object({
  liveChatId: z.string().min(1),
  authorChannelId: z.string().min(1).max(256),
  durationSeconds: z.number().int().min(1).max(86_400).optional(),
  reason: z.string().min(1).max(300).default("runtime_rule_action")
});

const unbanUserSchema = z.object({
  liveChatBanId: z.string().min(1).max(256),
  reason: z.string().min(1).max(300).default("manual_unban")
});

interface YouTubeRoutesOptions {
  accountStatusProvider?: (auth: AuthContext) => Promise<YouTubeLinkedAccountStatus>;
  oauthConfigured?: boolean;
}

export async function youtubeRoutes(app: FastifyInstance, options: YouTubeRoutesOptions = {}): Promise<void> {
  const youtube = new MockYouTubeLiveChatClient();
  const accountStatusProvider = options.accountStatusProvider ?? getYouTubeLinkedAccountStatus;
  const oauthConfigured = options.oauthConfigured ?? isYouTubeOAuthConfigured();

  app.get("/connect-url", { preHandler: requireAuth }, async (request) => {
    if (!oauthConfigured) {
      return {
        url: null,
        configured: false,
        requiredScopes: youtubeOAuthScopes,
        missingEnv: missingYouTubeOAuthEnv(),
        note: "Set Google OAuth env vars before production OAuth is enabled."
      };
    }

    return {
      url: await buildYouTubeConnectUrl(request.auth!),
      configured: true,
      requiredScopes: youtubeOAuthScopes,
      missingEnv: []
    };
  });

  app.get("/account", { preHandler: requireAuth }, async (request) => ({
    configured: oauthConfigured,
    source: oauthConfigured ? "youtube-api" : "mock",
    account: await accountStatusProvider(request.auth!)
  }));

  app.get("/oauth/callback", async (request, reply) => {
    if (!oauthConfigured) {
      return reply.status(503).send({
        error: "YOUTUBE_OAUTH_NOT_CONFIGURED",
        missingEnv: missingYouTubeOAuthEnv()
      });
    }

    const query = z.object({
      code: z.string().min(1),
      state: z.string().min(1)
    }).parse(request.query);
    const auth = await verifyYouTubeOAuthState(query.state);
    const tokens = await exchangeYouTubeOAuthCode(query.code);
    const channelIdentity = await fetchChannelIdentityForTokens(tokens);
    const storage = await storeYouTubeTokens(auth, tokens, channelIdentity ?? {});

    return {
      connected: Boolean(tokens.accessToken),
      deviceId: auth.deviceId,
      tokensStored: storage.stored,
      linkedAccountId: storage.linkedAccountId,
      channelId: channelIdentity?.channelId ?? null,
      channelTitle: channelIdentity?.channelTitle ?? null,
      tokenExpiresAt: tokens.expiryDate ? new Date(tokens.expiryDate).toISOString() : null,
      scopes: tokens.scope?.split(" ") ?? []
    };
  });

  app.post("/live-chat/discover", { preHandler: requireAuth }, async (request, reply) => {
    const body = discoverSchema.parse(request.body);
    const accountCheck = await accountCheckNoBlock(request.auth!, body.channelId);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }
    const discovery = await discoverBroadcasts(client, body);

    return {
      ...discovery,
      source: client === youtube ? "mock" : "youtube-api",
      account: accountCheck.account,
      requestedChannelId: body.channelId,
      channelMismatch: accountCheck.channelMismatch
    };
  });

  app.post("/live-chat/broadcasts", { preHandler: requireAuth }, async (request, reply) => {
    const body = discoverSchema.parse(request.body);
    const accountCheck = await accountCheckNoBlock(request.auth!, body.channelId);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }

    return {
      broadcasts: await client.listBroadcasts(body.channelId, {
        statuses: broadcastStatuses(body.includeScheduled),
        maxResults: body.maxResults
      }),
      source: client === youtube ? "mock" : "youtube-api",
      account: accountCheck.account,
      requestedChannelId: body.channelId,
      channelMismatch: accountCheck.channelMismatch
    };
  });

  app.post("/live-chat/messages", { preHandler: requireAuth }, async (request, reply) => {
    const body = listMessagesSchema.parse(request.body);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }
    const page = await client.listMessages(body.liveChatId, body.pageToken);

    return {
      ...page,
      source: client === youtube ? "mock" : "youtube-api"
    };
  });

  app.post("/live-chat/messages/send", { preHandler: requireAuth }, async (request, reply) => {
    const body = sendMessageSchema.parse(request.body);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }
    const result = await client.sendMessage(body.liveChatId, body.text);

    return {
      ...result,
      liveChatId: body.liveChatId,
      sentAt: new Date().toISOString()
    };
  });

  app.post("/live-chat/send-test", { preHandler: requireAuth }, async (request, reply) => {
    const body = sendTestSchema.parse(request.body);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }
    return client.sendMessage(body.liveChatId, body.text);
  });

  app.post("/live-chat/messages/delete", { preHandler: requireAuth }, async (request, reply) => {
    const body = deleteMessageSchema.parse(request.body);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }

    await client.deleteMessage(body.messageId);

    return {
      messageId: body.messageId,
      actionType: "deleteMessage",
      reason: body.reason,
      deletedAt: new Date().toISOString()
    };
  });

  app.post("/live-chat/users/hide", { preHandler: requireAuth }, async (request, reply) => {
    const body = hideUserSchema.parse(request.body);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }

    const result = await client.hideUser(body.liveChatId, body.authorChannelId, {
      durationSeconds: body.durationSeconds
    });

    return {
      liveChatId: body.liveChatId,
      authorChannelId: body.authorChannelId,
      liveChatBanId: result.liveChatBanId,
      actionType: body.durationSeconds ? "timeoutUser" : "hideUser",
      durationSeconds: body.durationSeconds ?? null,
      reason: body.reason,
      actedAt: new Date().toISOString()
    };
  });

  app.post("/live-chat/users/unban", { preHandler: requireAuth }, async (request, reply) => {
    const body = unbanUserSchema.parse(request.body);
    const client = await resolveClient(request.auth!, reply);
    if (!client) {
      return;
    }

    await client.unbanUser(body.liveChatBanId);

    return {
      liveChatBanId: body.liveChatBanId,
      actionType: "unbanUser",
      reason: body.reason,
      actedAt: new Date().toISOString()
    };
  });

  async function resolveClient(
    auth: AuthContext,
    reply: FastifyReply
  ): Promise<YouTubeLiveChatClient | null> {
    const client = await createYouTubeClientForAuth(auth);
    if (client) {
      return client;
    }

    if (oauthConfigured) {
      await reply.status(409).send({
        error: "YOUTUBE_NOT_CONNECTED",
        message: "Connect YouTube before using live chat actions."
      });
      return null;
    }

    return youtube;
  }

  async function accountCheckNoBlock(
    auth: AuthContext,
    requestedChannelId: string
  ): Promise<{
    account: YouTubeLinkedAccountStatus;
    channelMismatch: boolean;
  }> {
    const account = await accountStatusProvider(auth);
    return {
      account,
      channelMismatch: isRequestedChannelMismatch(account, requestedChannelId)
    };
  }

  async function assertRequestedChannelMatchesAccount(
    auth: AuthContext,
    reply: FastifyReply,
    requestedChannelId: string
  ): Promise<{
    account: YouTubeLinkedAccountStatus;
    channelMismatch: boolean;
  } | null> {
    const account = await accountStatusProvider(auth);
    const channelMismatch = isRequestedChannelMismatch(account, requestedChannelId);
    if (channelMismatch && oauthConfigured) {
      await reply.status(409).send({
        error: "YOUTUBE_CHANNEL_MISMATCH",
        message: "The selected channel does not match the connected YouTube account.",
        requestedChannelId,
        connectedChannelId: account.channelId,
        connectedChannelTitle: account.channelTitle,
        account
      });
      return null;
    }

    return {
      account,
      channelMismatch
    };
  }
}

async function fetchChannelIdentityForTokens(tokens: {
  accessToken: string | null;
  refreshToken: string | null;
  expiryDate: number | null;
}): Promise<Awaited<ReturnType<typeof fetchAuthenticatedYouTubeChannelIdentity>> | null> {
  if (!tokens.accessToken && !tokens.refreshToken) {
    return null;
  }

  const client = createOAuthClient();
  client.setCredentials({
    access_token: tokens.accessToken ?? undefined,
    refresh_token: tokens.refreshToken ?? undefined,
    expiry_date: tokens.expiryDate ?? undefined
  });

  return fetchAuthenticatedYouTubeChannelIdentity(client).catch(() => null);
}

async function discoverBroadcasts(
  client: YouTubeLiveChatClient,
  body: z.infer<typeof discoverSchema>
): Promise<{
  activeChat: { liveChatId: string; videoId: string } | null;
  broadcasts: YouTubeLiveBroadcast[];
  activeBroadcastCount: number;
  needsSelection: boolean;
  status: "ready" | "no_active_chat" | "multiple_active_chats";
}> {
  const broadcasts = await client.listBroadcasts(body.channelId, {
    statuses: broadcastStatuses(body.includeScheduled),
    maxResults: body.maxResults
  });
  const activeBroadcasts = broadcasts.filter((broadcast) => broadcast.status === "active" && broadcast.liveChatId);

  if (activeBroadcasts.length === 0) {
    return {
      activeChat: null,
      broadcasts,
      activeBroadcastCount: 0,
      needsSelection: false,
      status: "no_active_chat"
    };
  }

  if (activeBroadcasts.length > 1) {
    return {
      activeChat: null,
      broadcasts,
      activeBroadcastCount: activeBroadcasts.length,
      needsSelection: true,
      status: "multiple_active_chats"
    };
  }

  const activeBroadcast = activeBroadcasts[0];
  return {
    activeChat: {
      liveChatId: activeBroadcast.liveChatId!,
      videoId: activeBroadcast.videoId
    },
    broadcasts,
    activeBroadcastCount: 1,
    needsSelection: false,
    status: "ready"
  };
}

function broadcastStatuses(includeScheduled: boolean): YouTubeBroadcastStatus[] {
  return includeScheduled ? ["active", "upcoming"] : ["active"];
}

export function isRequestedChannelMismatch(
  account: Pick<YouTubeLinkedAccountStatus, "connected" | "channelId">,
  requestedChannelId: string
): boolean {
  if (!account.connected || !account.channelId) {
    return false;
  }

  return normalizeChannelId(account.channelId) !== normalizeChannelId(requestedChannelId);
}

function normalizeChannelId(value: string): string {
  return value.trim().toLowerCase();
}
