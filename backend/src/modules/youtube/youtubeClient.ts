export type YouTubeLiveChatMessageType =
  | "textMessageEvent"
  | "superChatEvent"
  | "superStickerEvent"
  | "newSponsorEvent"
  | "memberMilestoneChatEvent"
  | "messageDeletedEvent"
  | "userBannedEvent"
  | "systemEvent";

export interface YouTubeLiveChatMessage {
  id: string;
  authorChannelId: string;
  authorName: string;
  text: string;
  publishedAt: string;
  messageType: YouTubeLiveChatMessageType;
  isOwner?: boolean;
  isModerator?: boolean;
  isMember?: boolean;
  isVerified?: boolean;
  purchaseAmountMicros?: string | null;
  purchaseCurrency?: string | null;
  targetMessageId?: string | null;
  targetChannelId?: string | null;
}

export type YouTubeBroadcastStatus = "active" | "upcoming";

export interface YouTubeLiveBroadcast {
  videoId: string;
  liveChatId: string | null;
  title: string;
  status: YouTubeBroadcastStatus;
  scheduledStartTime: string | null;
  actualStartTime: string | null;
}

export interface YouTubeLiveChatBanOptions {
  durationSeconds?: number;
}

export interface YouTubeLiveChatBanResult {
  liveChatBanId: string | null;
}

export interface YouTubeLiveChatClient {
  listBroadcasts(channelId: string, options?: {
    statuses?: YouTubeBroadcastStatus[];
    maxResults?: number;
  }): Promise<YouTubeLiveBroadcast[]>;
  findActiveLiveChat(channelId: string): Promise<{ liveChatId: string; videoId: string } | null>;
  listMessages(liveChatId: string, pageToken?: string): Promise<{
    messages: YouTubeLiveChatMessage[];
    nextPageToken?: string;
    pollingIntervalMillis: number;
  }>;
  sendMessage(liveChatId: string, text: string): Promise<{ messageId: string }>;
  deleteMessage(messageId: string): Promise<void>;
  hideUser(liveChatId: string, channelId: string, options?: YouTubeLiveChatBanOptions): Promise<YouTubeLiveChatBanResult>;
  unbanUser(liveChatBanId: string): Promise<void>;
}

export class MockYouTubeLiveChatClient implements YouTubeLiveChatClient {
  async listBroadcasts(channelId: string, options: {
    statuses?: YouTubeBroadcastStatus[];
    maxResults?: number;
  } = {}): Promise<YouTubeLiveBroadcast[]> {
    const statuses = options.statuses ?? ["active", "upcoming"];
    const broadcasts: YouTubeLiveBroadcast[] = [
      {
        videoId: "demo-video",
        liveChatId: `live-chat-${channelId}`,
        title: "Demo live stream",
        status: "active",
        scheduledStartTime: new Date().toISOString(),
        actualStartTime: new Date().toISOString()
      },
      {
        videoId: "demo-upcoming-video",
        liveChatId: null,
        title: "Upcoming demo stream",
        status: "upcoming",
        scheduledStartTime: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        actualStartTime: null
      }
    ];

    return broadcasts
      .filter((broadcast) => statuses.includes(broadcast.status))
      .slice(0, options.maxResults ?? broadcasts.length);
  }

  async findActiveLiveChat(channelId: string): Promise<{ liveChatId: string; videoId: string }> {
    const activeBroadcast = (await this.listBroadcasts(channelId, { statuses: ["active"], maxResults: 1 }))[0];
    return {
      liveChatId: activeBroadcast.liveChatId ?? `live-chat-${channelId}`,
      videoId: activeBroadcast.videoId
    };
  }

  async listMessages(): Promise<{
    messages: YouTubeLiveChatMessage[];
    pollingIntervalMillis: number;
  }> {
    return {
      pollingIntervalMillis: 5000,
      messages: [
        {
          id: "demo-message-1",
          authorChannelId: "viewer-1",
          authorName: "Viewer",
          text: "hello chat",
          publishedAt: new Date().toISOString(),
          messageType: "textMessageEvent"
        }
      ]
    };
  }

  async sendMessage(_liveChatId: string, _text: string): Promise<{ messageId: string }> {
    return {
      messageId: "demo-sent-message"
    };
  }

  async deleteMessage(): Promise<void> {
    return;
  }

  async hideUser(): Promise<YouTubeLiveChatBanResult> {
    return {
      liveChatBanId: "demo-live-chat-ban"
    };
  }

  async unbanUser(): Promise<void> {
    return;
  }
}
