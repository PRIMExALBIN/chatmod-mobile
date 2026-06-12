import { describe, expect, it } from "vitest";
import {
  GoogleYouTubeLiveChatClient,
  fetchAuthenticatedYouTubeChannelIdentity,
  type YouTubeApiResource
} from "./googleYouTubeClient.js";
import type { createOAuthClient } from "./youtubeOAuth.js";

describe("GoogleYouTubeLiveChatClient", () => {
  it("fetches the authenticated channel identity for account metadata", async () => {
    const calls: unknown[] = [];
    await expect(fetchAuthenticatedYouTubeChannelIdentity(fakeAuth(), fakeYouTube({
      channelsList: async (args) => {
        calls.push(args);
        return {
          data: {
            items: [
              {
                id: "channel-linked-1",
                snippet: {
                  title: "Creator Bot Channel"
                }
              }
            ]
          }
        };
      }
    }))).resolves.toEqual({
      channelId: "channel-linked-1",
      channelTitle: "Creator Bot Channel"
    });
    expect(calls).toEqual([
      {
        part: ["snippet"],
        mine: true,
        maxResults: 1
      }
    ]);
  });

  it("returns null when the authenticated channel cannot be identified", async () => {
    await expect(fetchAuthenticatedYouTubeChannelIdentity(fakeAuth(), fakeYouTube({
      channelsList: async () => ({
        data: {
          items: []
        }
      })
    }))).resolves.toBeNull();
  });

  it("discovers the active live chat", async () => {
    const calls: unknown[] = [];
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveBroadcastsList: async (args) => {
        calls.push(args);
        return {
          data: {
            items: [
              {
                id: "video-1",
                snippet: {
                  liveChatId: "live-chat-1"
                }
              }
            ]
          }
        };
      }
    }));

    await expect(client.findActiveLiveChat("channel-1")).resolves.toEqual({
      liveChatId: "live-chat-1",
      videoId: "video-1"
    });
    expect(calls).toEqual([
      {
        part: ["snippet"],
        broadcastStatus: "active",
        mine: true,
        maxResults: 1
      }
    ]);
  });

  it("lists active and scheduled broadcasts for stream selection", async () => {
    const calls: unknown[] = [];
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveBroadcastsList: async (args) => {
        calls.push(args);
        return {
          data: {
            items: args.broadcastStatus === "active"
              ? [
                {
                  id: "active-video-1",
                  snippet: {
                    title: "Live now",
                    liveChatId: "active-chat-1",
                    actualStartTime: "2026-06-07T10:00:00.000Z"
                  }
                }
              ]
              : [
                {
                  id: "scheduled-video-1",
                  snippet: {
                    title: "Later stream",
                    scheduledStartTime: "2026-06-07T20:00:00.000Z"
                  }
                }
              ]
          }
        };
      }
    }));

    await expect(client.listBroadcasts("channel-1", { maxResults: 10 })).resolves.toEqual([
      {
        videoId: "active-video-1",
        liveChatId: "active-chat-1",
        title: "Live now",
        status: "active",
        scheduledStartTime: null,
        actualStartTime: "2026-06-07T10:00:00.000Z"
      },
      {
        videoId: "scheduled-video-1",
        liveChatId: null,
        title: "Later stream",
        status: "upcoming",
        scheduledStartTime: "2026-06-07T20:00:00.000Z",
        actualStartTime: null
      }
    ]);
    expect(calls).toEqual([
      {
        part: ["snippet"],
        broadcastStatus: "active",
        mine: true,
        maxResults: 10
      },
      {
        part: ["snippet"],
        broadcastStatus: "upcoming",
        mine: true,
        maxResults: 10
      }
    ]);
  });

  it("returns null when active broadcast lacks a chat id", async () => {
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveBroadcastsList: async () => ({
        data: {
          items: [
            {
              id: "video-1",
              snippet: {}
            }
          ]
        }
      })
    }));

    await expect(client.findActiveLiveChat("channel-1")).resolves.toBeNull();
  });

  it("maps live chat messages and keeps polling metadata", async () => {
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveChatMessagesList: async (args) => {
        expect(args).toMatchObject({
          liveChatId: "live-chat-1",
          pageToken: "page-1",
          part: ["snippet", "authorDetails"]
        });
        return {
          data: {
            nextPageToken: "page-2",
            pollingIntervalMillis: 7000,
            items: [
              {
                id: "message-1",
                snippet: {
                  type: "textMessageEvent",
                  publishedAt: "2026-06-07T10:00:00.000Z",
                  textMessageDetails: {
                    messageText: "hello chat"
                  }
                },
                authorDetails: {
                  channelId: "viewer-1",
                  displayName: "Viewer One",
                  isChatOwner: true,
                  isChatModerator: false,
                  isChatSponsor: false,
                  isVerified: true
                }
              },
              {
                id: "super-1",
                snippet: {
                  type: "superChatEvent",
                  publishedAt: "2026-06-07T10:00:01.000Z",
                  superChatDetails: {
                    userComment: "boost this question",
                    amountMicros: "5000000",
                    currency: "USD"
                  }
                },
                authorDetails: {
                  channelId: "viewer-2",
                  displayName: "Viewer Two",
                  isChatOwner: false,
                  isChatModerator: false,
                  isChatSponsor: true
                }
              },
              {
                id: "deleted-1",
                snippet: {
                  type: "messageDeletedEvent",
                  publishedAt: "2026-06-07T10:00:02.000Z",
                  displayMessage: "A message was deleted.",
                  messageDeletedDetails: {
                    deletedMessageId: "message-1"
                  }
                },
                authorDetails: {
                  channelId: "moderator-1",
                  displayName: "Moderator",
                  isChatOwner: false,
                  isChatModerator: true,
                  isChatSponsor: false
                }
              }
            ]
          }
        };
      }
    }));

    await expect(client.listMessages("live-chat-1", "page-1")).resolves.toEqual({
      nextPageToken: "page-2",
      pollingIntervalMillis: 7000,
      messages: [
        {
          id: "message-1",
          authorChannelId: "viewer-1",
          authorName: "Viewer One",
          text: "hello chat",
          publishedAt: "2026-06-07T10:00:00.000Z",
          messageType: "textMessageEvent",
          isOwner: true,
          isModerator: false,
          isMember: false,
          isVerified: true,
          purchaseAmountMicros: null,
          purchaseCurrency: null,
          targetMessageId: null,
          targetChannelId: null
        },
        {
          id: "super-1",
          authorChannelId: "viewer-2",
          authorName: "Viewer Two",
          text: "boost this question",
          publishedAt: "2026-06-07T10:00:01.000Z",
          messageType: "superChatEvent",
          isOwner: false,
          isModerator: false,
          isMember: true,
          isVerified: false,
          purchaseAmountMicros: "5000000",
          purchaseCurrency: "USD",
          targetMessageId: null,
          targetChannelId: null
        },
        {
          id: "deleted-1",
          authorChannelId: "moderator-1",
          authorName: "Moderator",
          text: "A message was deleted.",
          publishedAt: "2026-06-07T10:00:02.000Z",
          messageType: "messageDeletedEvent",
          isOwner: false,
          isModerator: true,
          isMember: false,
          isVerified: false,
          purchaseAmountMicros: null,
          purchaseCurrency: null,
          targetMessageId: "message-1",
          targetChannelId: null
        }
      ]
    });
  });

  it("normalizes quota errors with retry guidance", async () => {
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveChatMessagesList: async () => {
        throw {
          code: 403,
          errors: [{ reason: "quotaExceeded" }],
          response: {
            headers: {
              "retry-after": "30"
            }
          }
        };
      }
    }));

    await expect(client.listMessages("live-chat-1")).rejects.toMatchObject({
      publicCode: "YOUTUBE_QUOTA_EXCEEDED",
      statusCode: 429,
      provider: "youtube",
      reason: "quotaExceeded",
      retryAfterMillis: 30000
    });
  });

  it("sends text messages using the YouTube insert shape", async () => {
    const inserts: unknown[] = [];
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveChatMessagesInsert: async (args) => {
        inserts.push(args);
        return {
          data: {
            id: "sent-message-1"
          }
        };
      }
    }));

    await expect(client.sendMessage("live-chat-1", "Keep it friendly.")).resolves.toEqual({
      messageId: "sent-message-1"
    });
    expect(inserts).toEqual([
      {
        part: ["snippet"],
        requestBody: {
          snippet: {
            liveChatId: "live-chat-1",
            type: "textMessageEvent",
            textMessageDetails: {
              messageText: "Keep it friendly."
            }
          }
        }
      }
    ]);
  });

  it("normalizes ended-chat write failures", async () => {
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveChatMessagesInsert: async () => {
        throw {
          response: {
            status: 403,
            data: {
              error: {
                errors: [{ reason: "liveChatEnded" }]
              }
            }
          }
        };
      }
    }));

    await expect(client.sendMessage("live-chat-1", "Still here?")).rejects.toMatchObject({
      publicCode: "YOUTUBE_LIVE_CHAT_ENDED",
      statusCode: 409,
      provider: "youtube",
      reason: "liveChatEnded"
    });
  });

  it("deletes messages, permanently hides users, and temporarily times out users", async () => {
    const deletes: unknown[] = [];
    const bans: unknown[] = [];
    const unbans: unknown[] = [];
    const client = new GoogleYouTubeLiveChatClient(fakeAuth(), fakeYouTube({
      liveChatMessagesDelete: async (args) => {
        deletes.push(args);
      },
      liveChatBansInsert: async (args) => {
        bans.push(args);
        return { data: { id: `ban-${bans.length}` } };
      },
      liveChatBansDelete: async (args) => {
        unbans.push(args);
      }
    }));

    await client.deleteMessage("message-1");
    await expect(client.hideUser("live-chat-1", "viewer-1")).resolves.toEqual({ liveChatBanId: "ban-1" });
    await expect(client.hideUser("live-chat-1", "viewer-2", { durationSeconds: 300 })).resolves.toEqual({
      liveChatBanId: "ban-2"
    });
    await client.unbanUser("ban-1");

    expect(deletes).toEqual([{ id: "message-1" }]);
    expect(bans).toEqual([
      {
        part: ["snippet"],
        requestBody: {
          snippet: {
            liveChatId: "live-chat-1",
            type: "permanent",
            bannedUserDetails: {
              channelId: "viewer-1"
            }
          }
        }
      },
      {
        part: ["snippet"],
        requestBody: {
          snippet: {
            liveChatId: "live-chat-1",
            type: "temporary",
            banDurationSeconds: "300",
            bannedUserDetails: {
              channelId: "viewer-2"
            }
          }
        }
      }
    ]);
    expect(unbans).toEqual([{ id: "ban-1" }]);
  });
});

function fakeAuth(): ReturnType<typeof createOAuthClient> {
  return {} as ReturnType<typeof createOAuthClient>;
}

function fakeYouTube(overrides: {
  channelsList?: YouTubeApiResource["channels"]["list"];
  liveBroadcastsList?: YouTubeApiResource["liveBroadcasts"]["list"];
  liveChatMessagesList?: YouTubeApiResource["liveChatMessages"]["list"];
  liveChatMessagesInsert?: YouTubeApiResource["liveChatMessages"]["insert"];
  liveChatMessagesDelete?: YouTubeApiResource["liveChatMessages"]["delete"];
  liveChatBansInsert?: YouTubeApiResource["liveChatBans"]["insert"];
  liveChatBansDelete?: YouTubeApiResource["liveChatBans"]["delete"];
}): YouTubeApiResource {
  return {
    channels: {
      list: overrides.channelsList ?? (async () => ({ data: { items: [] } }))
    },
    liveBroadcasts: {
      list: overrides.liveBroadcastsList ?? (async () => ({ data: { items: [] } }))
    },
    liveChatMessages: {
      list: overrides.liveChatMessagesList ?? (async () => ({ data: { items: [] } })),
      insert: overrides.liveChatMessagesInsert ?? (async () => ({ data: { id: "sent-message" } })),
      delete: overrides.liveChatMessagesDelete ?? (async () => undefined)
    },
    liveChatBans: {
      insert: overrides.liveChatBansInsert ?? (async () => ({ data: { id: "ban-id" } })),
      delete: overrides.liveChatBansDelete ?? (async () => undefined)
    }
  };
}
