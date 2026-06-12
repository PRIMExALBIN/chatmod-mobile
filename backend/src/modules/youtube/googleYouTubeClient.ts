import { google, type youtube_v3 } from "googleapis";
import type { createOAuthClient } from "./youtubeOAuth.js";
import type {
  YouTubeBroadcastStatus,
  YouTubeLiveChatBanOptions,
  YouTubeLiveChatBanResult,
  YouTubeLiveBroadcast,
  YouTubeLiveChatClient,
  YouTubeLiveChatMessage,
  YouTubeLiveChatMessageType
} from "./youtubeClient.js";
import { withYouTubeApiErrors } from "./youtubeApiErrors.js";

export interface YouTubeApiResource {
  channels: {
    list(args: {
      part: string[];
      mine: boolean;
      maxResults: number;
    }): Promise<{
      data: {
        items?: Array<{
          id?: string | null;
          snippet?: {
            title?: string | null;
          } | null;
        }> | null;
      };
    }>;
  };
  liveBroadcasts: {
    list(args: {
      part: string[];
      broadcastStatus: "active" | "upcoming";
      mine: boolean;
      maxResults: number;
    }): Promise<{
      data: {
        items?: Array<{
          id?: string | null;
          snippet?: {
            liveChatId?: string | null;
            title?: string | null;
            scheduledStartTime?: string | null;
            actualStartTime?: string | null;
          } | null;
        }> | null;
      };
    }>;
  };
  liveChatMessages: {
    list(args: {
      liveChatId: string;
      pageToken?: string;
      part: string[];
    }): Promise<{
      data: {
        nextPageToken?: string | null;
        pollingIntervalMillis?: number | null;
        items?: youtube_v3.Schema$LiveChatMessage[] | null;
      };
    }>;
    insert(args: {
      part: string[];
      requestBody: {
        snippet: {
          liveChatId: string;
          type: "textMessageEvent";
          textMessageDetails: {
            messageText: string;
          };
        };
      };
    }): Promise<{
      data: {
        id?: string | null;
      };
    }>;
    delete(args: { id: string }): Promise<unknown>;
  };
  liveChatBans: {
    delete(args: { id: string }): Promise<unknown>;
    insert(args: {
      part: string[];
      requestBody: {
        snippet: {
          liveChatId: string;
          type: "permanent" | "temporary";
          banDurationSeconds?: string;
          bannedUserDetails: {
            channelId: string;
          };
        };
      };
    }): Promise<{
      data: {
        id?: string | null;
      };
    }>;
  };
}

export interface YouTubeChannelIdentity {
  channelId: string | null;
  channelTitle: string | null;
}

export class GoogleYouTubeLiveChatClient implements YouTubeLiveChatClient {
  private readonly youtube: YouTubeApiResource;

  constructor(auth: ReturnType<typeof createOAuthClient>, youtube?: YouTubeApiResource) {
    this.youtube = youtube ?? google.youtube({
      version: "v3",
      auth
    }) as YouTubeApiResource;
  }

  async listBroadcasts(_channelId: string, options: {
    statuses?: YouTubeBroadcastStatus[];
    maxResults?: number;
  } = {}): Promise<YouTubeLiveBroadcast[]> {
    const statuses = options.statuses ?? ["active", "upcoming"];
    const perStatusMax = Math.min(Math.max(options.maxResults ?? 10, 1), 50);
    const groups = await Promise.all(statuses.map(async (status) => {
      const response = await withYouTubeApiErrors(() => this.youtube.liveBroadcasts.list({
        part: ["snippet"],
        broadcastStatus: status,
        mine: true,
        maxResults: perStatusMax
      }));

      return (response.data.items ?? []).map((item) => mapLiveBroadcast(item, status));
    }));

    return groups
      .flat()
      .filter((broadcast) => Boolean(broadcast.videoId))
      .slice(0, options.maxResults ?? Number.POSITIVE_INFINITY);
  }

  async findActiveLiveChat(_channelId: string): Promise<{ liveChatId: string; videoId: string } | null> {
    const broadcast = (await this.listBroadcasts(_channelId, {
      statuses: ["active"],
      maxResults: 1
    }))[0];
    const liveChatId = broadcast?.liveChatId;
    const videoId = broadcast?.videoId;

    if (!liveChatId || !videoId) {
      return null;
    }

    return {
      liveChatId,
      videoId
    };
  }

  async listMessages(liveChatId: string, pageToken?: string): Promise<{
    messages: YouTubeLiveChatMessage[];
    nextPageToken?: string;
    pollingIntervalMillis: number;
  }> {
    const response = await withYouTubeApiErrors(() => this.youtube.liveChatMessages.list({
      liveChatId,
      pageToken,
      part: ["snippet", "authorDetails"]
    }));

    return {
      nextPageToken: response.data.nextPageToken ?? undefined,
      pollingIntervalMillis: response.data.pollingIntervalMillis ?? 5000,
      messages: (response.data.items ?? []).map(mapLiveChatMessage)
    };
  }

  async sendMessage(liveChatId: string, text: string): Promise<{ messageId: string }> {
    const response = await withYouTubeApiErrors(() => this.youtube.liveChatMessages.insert({
      part: ["snippet"],
      requestBody: {
        snippet: {
          liveChatId,
          type: "textMessageEvent",
          textMessageDetails: {
            messageText: text
          }
        }
      }
    }));

    return {
      messageId: response.data.id ?? ""
    };
  }

  async deleteMessage(messageId: string): Promise<void> {
    await withYouTubeApiErrors(() => this.youtube.liveChatMessages.delete({
      id: messageId
    }));
  }

  async hideUser(
    liveChatId: string,
    channelId: string,
    options: YouTubeLiveChatBanOptions = {}
  ): Promise<YouTubeLiveChatBanResult> {
    const durationSeconds = options.durationSeconds;
    const response = await withYouTubeApiErrors(() => this.youtube.liveChatBans.insert({
      part: ["snippet"],
      requestBody: {
        snippet: {
          liveChatId,
          type: durationSeconds ? "temporary" : "permanent",
          ...(durationSeconds ? { banDurationSeconds: String(durationSeconds) } : {}),
          bannedUserDetails: {
            channelId
          }
        }
      }
    }));

    return {
      liveChatBanId: response.data.id ?? null
    };
  }

  async unbanUser(liveChatBanId: string): Promise<void> {
    await withYouTubeApiErrors(() => this.youtube.liveChatBans.delete({
      id: liveChatBanId
    }));
  }
}

export async function fetchAuthenticatedYouTubeChannelIdentity(
  auth: ReturnType<typeof createOAuthClient>,
  youtube: Pick<YouTubeApiResource, "channels"> = google.youtube({
    version: "v3",
    auth
  }) as Pick<YouTubeApiResource, "channels">
): Promise<YouTubeChannelIdentity | null> {
  const response = await withYouTubeApiErrors(() => youtube.channels.list({
    part: ["snippet"],
    mine: true,
    maxResults: 1
  }));
  const channel = response.data.items?.[0];
  if (!channel?.id) {
    return null;
  }

  return {
    channelId: channel.id,
    channelTitle: channel.snippet?.title ?? null
  };
}

function mapLiveBroadcast(
  item: {
    id?: string | null;
    snippet?: {
      liveChatId?: string | null;
      title?: string | null;
      scheduledStartTime?: string | null;
      actualStartTime?: string | null;
    } | null;
  },
  status: YouTubeBroadcastStatus
): YouTubeLiveBroadcast {
  return {
    videoId: item.id ?? "",
    liveChatId: item.snippet?.liveChatId ?? null,
    title: item.snippet?.title ?? "Untitled stream",
    status,
    scheduledStartTime: item.snippet?.scheduledStartTime ?? null,
    actualStartTime: item.snippet?.actualStartTime ?? null
  };
}

function mapLiveChatMessage(item: youtube_v3.Schema$LiveChatMessage): YouTubeLiveChatMessage {
  const messageType = normalizeLiveChatMessageType(item.snippet?.type);
  return {
    id: item.id ?? "",
    authorChannelId: item.authorDetails?.channelId ?? "",
    authorName: item.authorDetails?.displayName ?? "Unknown viewer",
    text: liveChatMessageText(item, messageType),
    publishedAt: item.snippet?.publishedAt ?? new Date().toISOString(),
    messageType,
    isOwner: item.authorDetails?.isChatOwner ?? false,
    isModerator: item.authorDetails?.isChatModerator ?? false,
    isMember: (item.authorDetails?.isChatSponsor ?? false)
      || messageType === "newSponsorEvent"
      || messageType === "memberMilestoneChatEvent",
    isVerified: item.authorDetails?.isVerified ?? false,
    purchaseAmountMicros: item.snippet?.superChatDetails?.amountMicros
      ?? item.snippet?.superStickerDetails?.amountMicros
      ?? null,
    purchaseCurrency: item.snippet?.superChatDetails?.currency
      ?? item.snippet?.superStickerDetails?.currency
      ?? null,
    targetMessageId: item.snippet?.messageDeletedDetails?.deletedMessageId ?? null,
    targetChannelId: item.snippet?.userBannedDetails?.bannedUserDetails?.channelId ?? null
  };
}

function normalizeLiveChatMessageType(type: string | null | undefined): YouTubeLiveChatMessageType {
  switch (type) {
    case "textMessageEvent":
    case "superChatEvent":
    case "superStickerEvent":
    case "newSponsorEvent":
    case "memberMilestoneChatEvent":
    case "messageDeletedEvent":
    case "userBannedEvent":
      return type;
    default:
      return "systemEvent";
  }
}

function liveChatMessageText(
  item: youtube_v3.Schema$LiveChatMessage,
  messageType: YouTubeLiveChatMessageType
): string {
  if (messageType === "messageDeletedEvent" || messageType === "userBannedEvent") {
    return item.snippet?.displayMessage ?? "";
  }

  return item.snippet?.textMessageDetails?.messageText
    ?? item.snippet?.superChatDetails?.userComment
    ?? item.snippet?.memberMilestoneChatDetails?.userComment
    ?? item.snippet?.displayMessage
    ?? "";
}
