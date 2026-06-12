import { describe, expect, it } from "vitest";
import { buildApp } from "./app.js";
import { requireAuth } from "./plugins/auth.js";
import { YouTubeApiError } from "./modules/youtube/youtubeApiErrors.js";
import type { AuthContext } from "./modules/auth/sessionToken.js";
import { snapshotForPlan, type EntitlementSnapshot } from "./modules/entitlements/entitlementPlans.js";
import type { EntitlementStore, EntitlementUpdate } from "./modules/entitlements/entitlementStore.js";
import { InMemoryAiSuggestionUsageStore } from "./modules/moderation/aiSuggestionUsageStore.js";

describe("ChatMod Mobile API", () => {
  it("returns health", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/health"
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      status: "ok",
      service: "chatmod-mobile-api"
    });

    await app.close();
  });

  it("returns readiness without a configured database in test mode", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/health/ready"
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      status: "ok",
      dependencies: {
        database: "not_configured"
      }
    });

    await app.close();
  });

  it("completes the backend login flow with a device-session bearer token", async () => {
    const app = await buildApp();

    const missingAuthResponse = await app.inject({
      method: "GET",
      url: "/entitlements/current"
    });
    expect(missingAuthResponse.statusCode).toBe(401);
    expect(missingAuthResponse.headers["x-request-id"]).toBeTruthy();
    expect(missingAuthResponse.json()).toMatchObject({
      error: "UNAUTHORIZED",
      message: "Missing bearer token.",
      requestId: missingAuthResponse.headers["x-request-id"]
    });

    const sessionResponse = await app.inject({
      method: "POST",
      url: "/accounts/device-session",
      payload: {
        deviceId: "login-flow-device-1",
        installId: "login-flow-install-1",
        appVersion: "0.1.0"
      }
    });
    const session = sessionResponse.json<{
      accessToken: string;
      tokenType: string;
      expiresInSeconds: number;
      device: {
        deviceId: string;
        installId: string;
        appVersion: string;
      };
    }>();

    expect(sessionResponse.statusCode).toBe(200);
    expect(session).toMatchObject({
      tokenType: "Bearer",
      expiresInSeconds: 3600,
      device: {
        deviceId: "login-flow-device-1",
        installId: "login-flow-install-1",
        appVersion: "0.1.0"
      }
    });
    expect(session.accessToken).toEqual(expect.any(String));

    const authorization = `${session.tokenType} ${session.accessToken}`;
    const entitlementResponse = await app.inject({
      method: "GET",
      url: "/entitlements/current",
      headers: { authorization }
    });
    expect(entitlementResponse.statusCode).toBe(200);
    expect(entitlementResponse.json()).toMatchObject({
      plan: "starter",
      features: {
        customBotName: true,
        channelProfiles: 1
      }
    });

    const connectResponse = await app.inject({
      method: "GET",
      url: "/youtube/connect-url",
      headers: { authorization }
    });
    expect(connectResponse.statusCode).toBe(200);
    expect(connectResponse.json()).toMatchObject({
      url: null,
      configured: false,
      missingEnv: [
        "GOOGLE_OAUTH_CLIENT_ID",
        "GOOGLE_OAUTH_CLIENT_SECRET",
        "GOOGLE_OAUTH_REDIRECT_URI"
      ]
    });

    const invalidTokenResponse = await app.inject({
      method: "GET",
      url: "/entitlements/current",
      headers: { authorization: "Bearer not-a-real-token" }
    });
    expect(invalidTokenResponse.statusCode).toBe(401);
    expect(invalidTokenResponse.json()).toMatchObject({
      error: "UNAUTHORIZED",
      message: "Invalid or expired bearer token.",
      requestId: invalidTokenResponse.headers["x-request-id"]
    });

    await app.close();
  });

  it("returns Android app compatibility for the current beta build", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/app/compatibility?platform=android&versionName=0.1.0&versionCode=1"
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      platform: "android",
      currentVersionName: "0.1.0",
      currentVersionCode: 1,
      minimumSupportedVersionCode: 1,
      latestVersionCode: 1,
      status: "compatible",
      updateRequired: false,
      updateRecommended: false
    });

    await app.close();
  });

  it("requires an update for Android builds below the supported floor", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/app/compatibility?platform=android&versionName=0.0.1&versionCode=0"
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      status: "update_required",
      updateRequired: true,
      updateRecommended: false,
      message: "This ChatMod Mobile build is no longer supported. Update before starting the bot."
    });

    await app.close();
  });

  it("sets security headers", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/health"
    });

    expect(response.headers["x-content-type-options"]).toBe("nosniff");
    expect(response.headers["x-frame-options"]).toBe("SAMEORIGIN");
    expect(response.headers["referrer-policy"]).toBe("no-referrer");
    expect(response.headers["permissions-policy"]).toContain("camera=()");

    await app.close();
  });

  it("returns request ids for unhandled errors", async () => {
    const app = await buildApp();
    app.get("/test/unhandled-error", async () => {
      throw new Error("boom");
    });

    const response = await app.inject({
      method: "GET",
      url: "/test/unhandled-error"
    });
    const body = response.json();

    expect(response.statusCode).toBe(500);
    expect(response.headers["x-request-id"]).toBeTruthy();
    expect(body).toMatchObject({
      error: "INTERNAL_SERVER_ERROR",
      message: "Something went wrong."
    });
    expect(body.requestId).toBe(response.headers["x-request-id"]);

    await app.close();
  });

  it("returns public YouTube error codes and records them for support", async () => {
    const app = await buildApp();
    app.get("/test/youtube-rate-limit", { preHandler: requireAuth }, async () => {
      throw new YouTubeApiError(
        "YOUTUBE_RATE_LIMITED",
        429,
        "YouTube is rate limiting live chat requests. Wait before trying again.",
        "rateLimitExceeded",
        30000
      );
    });
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "GET",
      url: "/test/youtube-rate-limit",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(429);
    expect(response.headers["retry-after"]).toBe("30");
    expect(response.json()).toMatchObject({
      error: "YOUTUBE_RATE_LIMITED",
      message: "YouTube is rate limiting live chat requests. Wait before trying again.",
      requestId: response.headers["x-request-id"]
    });

    const errorsResponse = await app.inject({
      method: "GET",
      url: "/logs/api-errors",
      headers: { authorization }
    });

    expect(errorsResponse.statusCode).toBe(200);
    expect(errorsResponse.json()).toMatchObject({
      errors: [
        {
          provider: "youtube",
          code: "YOUTUBE_RATE_LIMITED",
          message: "YouTube is rate limiting live chat requests. Wait before trying again.",
          metadata: {
            requestId: response.headers["x-request-id"],
            statusCode: 429,
            method: "GET",
            url: "/test/youtube-rate-limit"
          }
        }
      ]
    });

    await app.close();
  });

  it("evaluates moderation rules", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "POST",
      url: "/moderation/rules/evaluate",
      headers: {
        authorization
      },
      payload: {
        message: {
          id: "message-1",
          authorChannelId: "viewer-1",
          authorName: "Viewer",
          text: "buy cheap views at www.example.com",
          timestamp: new Date().toISOString()
        },
        profile: {
          blockedTerms: ["cheap views"],
          linkPolicy: "delete",
          capsThreshold: 0.75,
          maxRepeatedCharacters: 5
        }
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      matched: true
    });

    await app.close();
  });

  it("rejects advanced moderation filters for starter entitlements", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "POST",
      url: "/moderation/rules/evaluate",
      headers: { authorization },
      payload: {
        message: {
          id: "message-advanced-1",
          authorChannelId: "viewer-1",
          authorName: "Viewer",
          text: "free nitro now",
          timestamp: new Date().toISOString()
        },
        profile: {
          regexPatterns: ["free\\s+nitro"]
        }
      }
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({
      message: "Advanced moderation filters require Pro or Creator plan: regexPatterns."
    });

    await app.close();
  });

  it("gates AI moderation suggestions to Creator and keeps them manual", async () => {
    const adminApiKey = "test-admin-key-with-at-least-32-chars";
    const app = await buildApp({ adminApiKey });
    const authorization = await createAuthorization(app, "ai-suggestion-device", "ai-suggestion-install");
    const payload = {
      message: {
        id: "message-ai-1",
        authorChannelId: "viewer-ai-1",
        authorName: "Viewer",
        text: "buy cheap views at www.example.com",
        timestamp: new Date().toISOString()
      },
      profile: {
        blockedTerms: ["cheap views"],
        linkPolicy: "delete"
      }
    };

    const lockedResponse = await app.inject({
      method: "POST",
      url: "/moderation/suggestions/evaluate",
      headers: { authorization },
      payload
    });
    expect(lockedResponse.statusCode).toBe(403);
    expect(lockedResponse.json()).toMatchObject({
      error: "AI_SUGGESTIONS_REQUIRED"
    });

    await app.inject({
      method: "POST",
      url: "/admin/support/entitlements/manual-adjust",
      headers: {
        "x-admin-api-key": adminApiKey
      },
      payload: {
        deviceId: "ai-suggestion-device",
        installId: "ai-suggestion-install",
        plan: "creator",
        status: "active",
        ticketId: "SUP-AI-1"
      }
    });

    const response = await app.inject({
      method: "POST",
      url: "/moderation/suggestions/evaluate",
      headers: { authorization },
      payload
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      provider: "local-heuristic",
      manualApprovalRequired: true,
      suggestedAction: "deleteMessage",
      classification: expect.arrayContaining(["policy"]),
      confidence: 0.96,
      usage: {
        used: 1,
        limit: 300,
        remaining: 299
      }
    });

    await app.close();
  });

  it("enforces Creator AI moderation suggestion daily usage limits", async () => {
    const creatorSnapshot = snapshotForPlan({
      plan: "creator",
      status: "active",
      source: "test",
      productId: "chatmod_creator_monthly",
      currentPeriodEndsAt: new Date("2026-07-11T10:00:00.000Z")
    });
    const app = await buildApp({
      entitlementStore: fakeEntitlementStore({
        ...creatorSnapshot,
        features: {
          ...creatorSnapshot.features,
          aiSuggestionDailyLimit: 1
        }
      }),
      aiSuggestionUsageStore: new InMemoryAiSuggestionUsageStore()
    });
    const authorization = await createAuthorization(app, "ai-limit-device", "ai-limit-install");
    const payload = {
      message: {
        id: "message-ai-limit-1",
        authorChannelId: "viewer-ai-limit-1",
        authorName: "Viewer",
        text: "same question again?",
        timestamp: new Date().toISOString()
      },
      profile: {},
      context: {
        recentMessages: [
          {
            id: "recent-ai-limit-1",
            authorChannelId: "viewer-ai-limit-2",
            authorName: "Viewer 2",
            text: "same question again?",
            timestamp: new Date().toISOString()
          },
          {
            id: "recent-ai-limit-2",
            authorChannelId: "viewer-ai-limit-3",
            authorName: "Viewer 3",
            text: "same question again?",
            timestamp: new Date().toISOString()
          }
        ]
      }
    };

    const firstResponse = await app.inject({
      method: "POST",
      url: "/moderation/suggestions/evaluate",
      headers: { authorization },
      payload
    });
    expect(firstResponse.statusCode).toBe(200);
    expect(firstResponse.json()).toMatchObject({
      usage: {
        used: 1,
        limit: 1,
        remaining: 0
      }
    });

    const secondResponse = await app.inject({
      method: "POST",
      url: "/moderation/suggestions/evaluate",
      headers: { authorization },
      payload
    });
    expect(secondResponse.statusCode).toBe(429);
    expect(secondResponse.headers["retry-after"]).toBeTruthy();
    expect(secondResponse.json()).toMatchObject({
      error: "AI_SUGGESTION_LIMIT_REACHED",
      usage: {
        used: 1,
        limit: 1,
        remaining: 0
      }
    });

    await app.close();
  });

  it("gates after-stream AI chat summaries to Creator and summarizes synced logs", async () => {
    const adminApiKey = "test-admin-key-with-at-least-32-chars";
    const app = await buildApp({ adminApiKey });
    const authorization = await createAuthorization(app, "ai-summary-device", "ai-summary-install");

    await app.inject({
      method: "PUT",
      url: "/stream-sessions/ai-summary-session-1",
      headers: { authorization },
      payload: {
        profileId: "profile-ai-summary",
        videoId: "video-ai-summary",
        liveChatId: "live-chat-ai-summary",
        title: "Friday creator Q&A",
        startedAt: "2026-06-11T18:00:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/ai-summary-session-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "summary-message-1",
        authorChannelId: "viewer-summary-a",
        authorName: "Viewer A",
        text: "When is the next stream?",
        receivedAt: "2026-06-11T18:01:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/ai-summary-session-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "summary-message-2",
        authorChannelId: "viewer-summary-b",
        authorName: "Viewer B",
        text: "When is the next stream?",
        receivedAt: "2026-06-11T18:02:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/ai-summary-session-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "summary-message-3",
        authorChannelId: "viewer-summary-a",
        authorName: "Viewer A",
        text: "Love the roadmap stream and creator tools",
        receivedAt: "2026-06-11T18:03:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/ai-summary-session-1/actions",
      headers: { authorization },
      payload: {
        clientActionId: "summary-action-1",
        youtubeMessageId: "summary-message-3",
        authorChannelId: "viewer-summary-c",
        actionType: "deleteMessage",
        reason: "blocked_term:cheap views",
        confidence: 0.94
      }
    });

    const lockedResponse = await app.inject({
      method: "GET",
      url: "/stream-sessions/ai-summary-session-1/ai-summary",
      headers: { authorization }
    });
    expect(lockedResponse.statusCode).toBe(403);
    expect(lockedResponse.json()).toMatchObject({
      error: "AI_CHAT_SUMMARY_REQUIRED"
    });

    await app.inject({
      method: "POST",
      url: "/admin/support/entitlements/manual-adjust",
      headers: {
        "x-admin-api-key": adminApiKey
      },
      payload: {
        deviceId: "ai-summary-device",
        installId: "ai-summary-install",
        plan: "creator",
        status: "active",
        ticketId: "SUP-AI-SUMMARY"
      }
    });

    const response = await app.inject({
      method: "GET",
      url: "/stream-sessions/ai-summary-session-1/ai-summary",
      headers: { authorization }
    });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      provider: "local-heuristic",
      sessionId: "ai-summary-session-1",
      title: "Friday creator Q&A",
      stats: {
        messageCount: 3,
        uniqueChatters: 2,
        moderationActionCount: 1,
        destructiveActionCount: 1
      },
      topQuestions: [
        {
          question: "When is the next stream?",
          count: 2
        }
      ],
      suggestedFollowUps: expect.arrayContaining([
        "Add or update an FAQ reply for: \"When is the next stream?\"."
      ])
    });

    await app.close();
  });

  it("gates AI FAQ replies to Creator and suggests from creator-provided entries", async () => {
    const adminApiKey = "test-admin-key-with-at-least-32-chars";
    const app = await buildApp({ adminApiKey });
    const authorization = await createAuthorization(app, "ai-faq-device", "ai-faq-install");

    const lockedResponse = await app.inject({
      method: "GET",
      url: "/faq-entries?profileId=profile-ai-faq",
      headers: { authorization }
    });
    expect(lockedResponse.statusCode).toBe(403);
    expect(lockedResponse.json()).toMatchObject({
      error: "AI_FAQ_REQUIRED"
    });

    await app.inject({
      method: "POST",
      url: "/admin/support/entitlements/manual-adjust",
      headers: {
        "x-admin-api-key": adminApiKey
      },
      payload: {
        deviceId: "ai-faq-device",
        installId: "ai-faq-install",
        plan: "creator",
        status: "active",
        ticketId: "SUP-AI-FAQ"
      }
    });

    const saveResponse = await app.inject({
      method: "PUT",
      url: "/faq-entries/faq-schedule",
      headers: { authorization },
      payload: {
        profileId: "profile-ai-faq",
        question: "When is the next stream?",
        answer: "Next stream is Friday at 7 PM. Check the schedule link after stream.",
        keywords: ["next stream", "schedule", "friday"],
        enabled: true
      }
    });
    expect(saveResponse.statusCode).toBe(200);
    expect(saveResponse.json()).toMatchObject({
      id: "faq-schedule",
      question: "When is the next stream?",
      keywords: ["next stream", "schedule", "friday"]
    });

    const listResponse = await app.inject({
      method: "GET",
      url: "/faq-entries?profileId=profile-ai-faq",
      headers: { authorization }
    });
    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      faqEntries: [
        {
          id: "faq-schedule",
          enabled: true
        }
      ]
    });

    const suggestionResponse = await app.inject({
      method: "POST",
      url: "/faq-entries/suggest-reply",
      headers: { authorization },
      payload: {
        profileId: "profile-ai-faq",
        message: {
          authorName: "Viewer",
          text: "What is the next stream schedule?"
        }
      }
    });
    expect(suggestionResponse.statusCode).toBe(200);
    expect(suggestionResponse.json()).toMatchObject({
      provider: "local-heuristic",
      matched: true,
      manualApprovalRequired: true,
      entryId: "faq-schedule",
      question: "When is the next stream?",
      replyText: "Next stream is Friday at 7 PM. Check the schedule link after stream.",
      matchedKeywords: expect.arrayContaining(["next stream", "schedule"])
    });

    await app.close();
  });

  it("protects entitlement routes", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/entitlements/current"
    });

    expect(response.statusCode).toBe(401);
    expect(response.headers["x-request-id"]).toBeTruthy();
    expect(response.json()).toMatchObject({
      error: "UNAUTHORIZED",
      requestId: response.headers["x-request-id"]
    });

    await app.close();
  });

  it("returns current starter entitlement", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "GET",
      url: "/entitlements/current",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      plan: "starter",
      status: "trialing",
      features: {
        customBotName: true,
        commandProfiles: 3,
        presetBundles: false,
        obsOverlay: false,
        localHistoryLimit: 120
      }
    });

    await app.close();
  });

  it("keeps Discord webhook settings private and sends gated alerts", async () => {
    const adminApiKey = "test-admin-key-with-at-least-32-chars";
    const sentPayloads: Array<{ webhookUrl: string; title: string }> = [];
    const app = await buildApp({
      adminApiKey,
      discordWebhookSender: {
        async send(webhookUrl, payload) {
          sentPayloads.push({
            webhookUrl,
            title: payload.embeds[0]?.title ?? ""
          });
        }
      }
    });

    const starterAuthorization = await createAuthorization(app, "discord-starter-device", "discord-starter-install");
    const starterResponse = await app.inject({
      method: "PUT",
      url: "/integrations/discord/webhook",
      headers: { authorization: starterAuthorization },
      payload: {
        profileId: "profile-discord-starter",
        webhookUrl: "https://discord.com/api/webhooks/123/test-token",
        enabled: true
      }
    });
    expect(starterResponse.statusCode).toBe(403);

    const proGrantResponse = await app.inject({
      method: "POST",
      url: "/admin/support/entitlements/manual-adjust",
      headers: {
        "x-admin-api-key": adminApiKey
      },
      payload: {
        deviceId: "discord-device",
        installId: "discord-install",
        plan: "pro",
        status: "active",
        ticketId: "SUP-DISCORD",
        note: "Enable Discord alert beta"
      }
    });
    expect(proGrantResponse.statusCode).toBe(200);
    const authorization = await createAuthorization(app, "discord-device", "discord-install");

    const invalidResponse = await app.inject({
      method: "PUT",
      url: "/integrations/discord/webhook",
      headers: { authorization },
      payload: {
        profileId: "profile-discord",
        webhookUrl: "https://example.com/api/webhooks/123/test-token",
        enabled: true
      }
    });
    expect(invalidResponse.statusCode).toBe(400);

    const saveResponse = await app.inject({
      method: "PUT",
      url: "/integrations/discord/webhook",
      headers: { authorization },
      payload: {
        profileId: "profile-discord",
        webhookUrl: "https://discord.com/api/webhooks/123/test-token",
        enabled: true,
        alertModerationActions: true,
        alertRuntimeStatus: false
      }
    });
    expect(saveResponse.statusCode).toBe(200);
    expect(saveResponse.json()).toMatchObject({
      profileId: "profile-discord",
      configured: true,
      enabled: true,
      alertModerationActions: true,
      alertRuntimeStatus: false
    });
    expect(saveResponse.body).not.toContain("test-token");

    const testResponse = await app.inject({
      method: "POST",
      url: "/integrations/discord/test",
      headers: { authorization },
      payload: {
        profileId: "profile-discord"
      }
    });
    expect(testResponse.statusCode).toBe(200);
    expect(testResponse.json()).toMatchObject({ sent: true });
    expect(sentPayloads).toHaveLength(1);
    expect(sentPayloads[0]).toMatchObject({
      webhookUrl: "https://discord.com/api/webhooks/123/test-token",
      title: "ChatMod Mobile test"
    });

    const skippedRuntimeResponse = await app.inject({
      method: "POST",
      url: "/integrations/discord/alerts",
      headers: { authorization },
      payload: {
        profileId: "profile-discord",
        eventType: "runtime_status",
        title: "Bot started",
        detail: "Runtime status alert",
        severity: "info"
      }
    });
    expect(skippedRuntimeResponse.statusCode).toBe(200);
    expect(skippedRuntimeResponse.json()).toMatchObject({
      sent: false,
      skippedReason: "runtime_alerts_disabled"
    });

    const moderationAlertResponse = await app.inject({
      method: "POST",
      url: "/integrations/discord/alerts",
      headers: { authorization },
      payload: {
        profileId: "profile-discord",
        eventType: "moderation_action",
        title: "Moderation action",
        detail: "1 action was taken by ChatMod Mobile.",
        severity: "warning",
        metadata: {
          actionsTaken: 1
        }
      }
    });
    expect(moderationAlertResponse.statusCode).toBe(200);
    expect(moderationAlertResponse.json()).toMatchObject({ sent: true });
    expect(sentPayloads).toHaveLength(2);

    const exportResponse = await app.inject({
      method: "GET",
      url: "/accounts/export",
      headers: { authorization }
    });
    expect(exportResponse.statusCode).toBe(200);
    expect(exportResponse.body).not.toContain("test-token");

    await app.close();
  });

  it("does not expose admin support routes until an admin key is configured", async () => {
    const app = await buildApp();
    const response = await app.inject({
      method: "GET",
      url: "/admin/support/users?deviceId=admin-device-hidden"
    });

    expect(response.statusCode).toBe(404);

    await app.close();
  });

  it("protects admin support routes and supports manual entitlement adjustments", async () => {
    const adminApiKey = "test-admin-key-with-at-least-32-chars";
    const app = await buildApp({ adminApiKey });

    const unauthenticatedResponse = await app.inject({
      method: "GET",
      url: "/admin/support/users?deviceId=admin-device-1"
    });
    expect(unauthenticatedResponse.statusCode).toBe(401);

    const wrongKeyResponse = await app.inject({
      method: "GET",
      url: "/admin/support/users?deviceId=admin-device-1",
      headers: {
        "x-admin-api-key": "wrong-admin-key"
      }
    });
    expect(wrongKeyResponse.statusCode).toBe(401);

    const adjustResponse = await app.inject({
      method: "POST",
      url: "/admin/support/entitlements/manual-adjust",
      headers: {
        "x-admin-api-key": adminApiKey
      },
      payload: {
        deviceId: "admin-device-1",
        installId: "admin-install-1",
        plan: "pro",
        status: "active",
        ticketId: "SUP-100",
        note: "Closed beta support grant"
      }
    });

    expect(adjustResponse.statusCode).toBe(200);
    expect(adjustResponse.json()).toMatchObject({
      deviceId: "admin-device-1",
      entitlement: {
        plan: "pro",
        source: "admin-manual",
        features: {
          advancedFilters: true,
          presetBundles: true,
          localHistoryLimit: 1000
        }
      }
    });

    const userAuthorization = await createAuthorization(app, "admin-device-1", "admin-install-1");
    const entitlementResponse = await app.inject({
      method: "GET",
      url: "/entitlements/current",
      headers: { authorization: userAuthorization }
    });
    expect(entitlementResponse.statusCode).toBe(200);
    expect(entitlementResponse.json()).toMatchObject({
      plan: "pro",
      source: "admin-manual"
    });

    const ticketResponse = await app.inject({
      method: "POST",
      url: "/admin/support/tickets/metadata",
      headers: {
        "x-admin-api-key": adminApiKey
      },
      payload: {
        deviceId: "admin-device-1",
        installId: "admin-install-1",
        ticketId: "SUP-100",
        status: "triaging",
        priority: "p1",
        tags: ["billing", "beta"],
        note: "Creator reports billing restore issue"
      }
    });

    expect(ticketResponse.statusCode).toBe(201);
    expect(ticketResponse.json()).toMatchObject({
      ticket: {
        severity: "warning",
        details: {
          eventType: "support_ticket_metadata",
          ticketId: "SUP-100",
          priority: "p1"
        }
      }
    });

    const lookupResponse = await app.inject({
      method: "GET",
      url: "/admin/support/users?deviceId=admin-device-1",
      headers: {
        "x-admin-api-key": adminApiKey
      }
    });

    expect(lookupResponse.statusCode).toBe(200);
    expect(lookupResponse.json()).toMatchObject({
      deviceId: "admin-device-1",
      entitlement: {
        plan: "pro"
      },
      supportEvents: expect.arrayContaining([
        expect.objectContaining({
          message: "Manual entitlement adjusted to pro"
        }),
        expect.objectContaining({
          message: "Support ticket SUP-100 metadata updated"
        })
      ])
    });

    const subscriptionLookupResponse = await app.inject({
      method: "GET",
      url: "/admin/support/subscriptions/admin-device-1",
      headers: {
        "x-admin-api-key": adminApiKey
      }
    });
    expect(subscriptionLookupResponse.statusCode).toBe(200);
    expect(subscriptionLookupResponse.json()).toMatchObject({
      deviceId: "admin-device-1",
      entitlement: {
        plan: "pro"
      }
    });

    await app.close();
  });

  it("enforces starter channel profile limits", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const firstResponse = await app.inject({
      method: "POST",
      url: "/profiles",
      headers: { authorization },
      payload: {
        channelId: "youtube-channel-1",
        name: "Main bot profile",
        config: {}
      }
    });
    expect(firstResponse.statusCode).toBe(201);

    const secondResponse = await app.inject({
      method: "POST",
      url: "/profiles",
      headers: { authorization },
      payload: {
        channelId: "youtube-channel-2",
        name: "Second bot profile",
        config: {}
      }
    });

    expect(secondResponse.statusCode).toBe(403);
    expect(secondResponse.json()).toMatchObject({
      error: "REQUEST_ERROR",
      message: "Channel profile limit reached for starter plan (1)."
    });

    await app.close();
  });

  it("enforces starter command and timer limits", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    for (const index of [1, 2, 3]) {
      const response = await app.inject({
        method: "POST",
        url: "/commands",
        headers: { authorization },
        payload: {
          profileId: "profile-limits",
          name: `!cmd${index}`,
          response: `Command ${index}`,
          aliases: [],
          cooldownSeconds: 30,
          accessLevel: "everyone",
          enabled: true
        }
      });
      expect(response.statusCode).toBe(201);
    }

    const commandLimitResponse = await app.inject({
      method: "POST",
      url: "/commands",
      headers: { authorization },
      payload: {
        profileId: "profile-limits",
        name: "!cmd4",
        response: "Command 4",
        aliases: [],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: true
      }
    });
    expect(commandLimitResponse.statusCode).toBe(403);
    expect(commandLimitResponse.json()).toMatchObject({
      message: "Command limit reached for starter plan (3)."
    });

    for (const index of [1, 2, 3, 4, 5]) {
      const response = await app.inject({
        method: "POST",
        url: "/timers",
        headers: { authorization },
        payload: {
          profileId: "profile-limits",
          name: `Timer ${index}`,
          message: `Timer message ${index}`,
          intervalMinutes: 15,
          minChatMessages: 0,
          enabled: true
        }
      });
      expect(response.statusCode).toBe(201);
    }

    const timerLimitResponse = await app.inject({
      method: "POST",
      url: "/timers",
      headers: { authorization },
      payload: {
        profileId: "profile-limits",
        name: "Timer 6",
        message: "Timer message 6",
        intervalMinutes: 15,
        minChatMessages: 0,
        enabled: true
      }
    });
    expect(timerLimitResponse.statusCode).toBe(403);
    expect(timerLimitResponse.json()).toMatchObject({
      message: "Timer limit reached for starter plan (5)."
    });

    await app.close();
  });

  it("enforces starter profile limits for implicit command sync profiles", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const firstResponse = await app.inject({
      method: "PUT",
      url: "/commands/cmd-primary-profile",
      headers: { authorization },
      payload: {
        profileId: "profile-primary",
        name: "!primary",
        response: "Primary profile command",
        aliases: [],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: true
      }
    });
    expect(firstResponse.statusCode).toBe(200);

    const secondResponse = await app.inject({
      method: "PUT",
      url: "/commands/cmd-secondary-profile",
      headers: { authorization },
      payload: {
        profileId: "profile-secondary",
        name: "!secondary",
        response: "Secondary profile command",
        aliases: [],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: true
      }
    });

    expect(secondResponse.statusCode).toBe(403);
    expect(secondResponse.json()).toMatchObject({
      message: "Channel profile limit reached for starter plan (1)."
    });

    await app.close();
  });

  it("exports current account data without stored secrets", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "GET",
      url: "/accounts/export",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      account: {
        deviceId: "test-device-123",
        installId: "test-install-123"
      },
      linkedAccounts: [],
      profiles: []
    });
    expect(JSON.stringify(response.json())).not.toContain("encryptedAccess");
    expect(JSON.stringify(response.json())).not.toContain("encryptedRefresh");

    await app.close();
  });

  it("handles YouTube disconnect and account deletion", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const disconnectResponse = await app.inject({
      method: "POST",
      url: "/accounts/youtube/disconnect",
      headers: { authorization }
    });

    expect(disconnectResponse.statusCode).toBe(200);
    expect(disconnectResponse.json()).toMatchObject({
      disconnected: false,
      removedAccounts: 0
    });

    const deleteResponse = await app.inject({
      method: "DELETE",
      url: "/accounts/current",
      headers: { authorization }
    });

    expect(deleteResponse.statusCode).toBe(200);
    expect(deleteResponse.json()).toMatchObject({
      deleted: false,
      deviceIds: ["test-device-123"]
    });

    await app.close();
  });

  it("reports missing Google Play billing configuration", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const configResponse = await app.inject({
      method: "GET",
      url: "/entitlements/google-play/config",
      headers: { authorization }
    });
    expect(configResponse.statusCode).toBe(200);
    expect(configResponse.json()).toMatchObject({
      configured: false
    });

    const validateResponse = await app.inject({
      method: "POST",
      url: "/entitlements/google-play/validate",
      headers: { authorization },
      payload: {
        productId: "chatmod_pro_monthly",
        purchaseToken: "test-token"
      }
    });

    expect(validateResponse.statusCode).toBe(503);
    expect(validateResponse.json()).toMatchObject({
      error: "GOOGLE_PLAY_BILLING_NOT_CONFIGURED"
    });

    await app.close();
  });

  it("reports missing OAuth configuration for YouTube connect URL", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "GET",
      url: "/youtube/connect-url",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      configured: false,
      url: null
    });

    await app.close();
  });

  it("reports connected YouTube account channel metadata", async () => {
    const app = await buildApp({
      youtubeOAuthConfigured: true,
      youtubeAccountStatusProvider: async () => ({
        connected: true,
        linkedAccountId: "linked-youtube-1",
        channelId: "channel-linked-1",
        channelTitle: "ChatMod Bot Channel",
        hasAccessToken: true,
        hasRefreshToken: true,
        tokenExpiresAt: "2026-07-08T10:00:00.000Z"
      })
    });
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "GET",
      url: "/youtube/account",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      configured: true,
      source: "youtube-api",
      account: {
        connected: true,
        channelId: "channel-linked-1",
        channelTitle: "ChatMod Bot Channel",
        hasRefreshToken: true
      }
    });

    await app.close();
  });

  it("rejects stream discovery when the requested channel differs from the connected YouTube account", async () => {
    const app = await buildApp({
      youtubeOAuthConfigured: true,
      youtubeAccountStatusProvider: async () => ({
        connected: true,
        linkedAccountId: "linked-youtube-1",
        channelId: "channel-linked-1",
        channelTitle: "ChatMod Bot Channel",
        hasAccessToken: true,
        hasRefreshToken: true,
        tokenExpiresAt: "2026-07-08T10:00:00.000Z"
      })
    });
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/discover",
      headers: { authorization },
      payload: {
        channelId: "different-channel"
      }
    });

    expect(response.statusCode).toBe(409);
    expect(response.json()).toMatchObject({
      error: "YOUTUBE_CHANNEL_MISMATCH",
      requestedChannelId: "different-channel",
      connectedChannelId: "channel-linked-1",
      connectedChannelTitle: "ChatMod Bot Channel"
    });

    await app.close();
  });

  it("lists YouTube broadcasts for stream selection in local mock mode", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/broadcasts",
      headers: { authorization },
      payload: {
        channelId: "youtube-channel-1",
        includeScheduled: true
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      source: "mock",
      broadcasts: [
        {
          videoId: "demo-video",
          liveChatId: "live-chat-youtube-channel-1",
          title: "Demo live stream",
          status: "active"
        },
        {
          videoId: "demo-upcoming-video",
          liveChatId: null,
          title: "Upcoming demo stream",
          status: "upcoming"
        }
      ]
    });

    await app.close();
  });

  it("returns stream selection metadata when discovering live chat", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/discover",
      headers: { authorization },
      payload: {
        channelId: "youtube-channel-1"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      source: "mock",
      status: "ready",
      activeBroadcastCount: 1,
      needsSelection: false,
      activeChat: {
        liveChatId: "live-chat-youtube-channel-1",
        videoId: "demo-video"
      }
    });
    expect(response.json<{ broadcasts: unknown[] }>().broadcasts.length).toBeGreaterThanOrEqual(1);

    await app.close();
  });

  it("lists YouTube live chat messages through the live runtime adapter", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/messages",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      source: "mock",
      pollingIntervalMillis: 5000,
      messages: [
        {
          id: "demo-message-1",
          authorChannelId: "viewer-1",
          authorName: "Viewer",
          text: "hello chat",
          messageType: "textMessageEvent"
        }
      ]
    });

    await app.close();
  });

  it("sends a YouTube runtime message through the live chat adapter", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/messages/send",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        text: "Keep chat friendly."
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      liveChatId: "live-chat-1",
      messageId: "demo-sent-message"
    });
    expect(response.json().sentAt).toEqual(expect.any(String));

    await app.close();
  });

  it("routes direct YouTube user timeout actions through the live chat adapter", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/users/hide",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        authorChannelId: "viewer-1",
        durationSeconds: 300,
        reason: "runtime_rule_action"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      liveChatId: "live-chat-1",
      authorChannelId: "viewer-1",
      liveChatBanId: "demo-live-chat-ban",
      actionType: "timeoutUser",
      durationSeconds: 300,
      reason: "runtime_rule_action"
    });
    expect(response.json().actedAt).toEqual(expect.any(String));

    await app.close();
  });

  it("routes direct YouTube user unban actions through the live chat adapter", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/users/unban",
      headers: { authorization },
      payload: {
        liveChatBanId: "demo-live-chat-ban",
        reason: "manual_unban"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      liveChatBanId: "demo-live-chat-ban",
      actionType: "unbanUser",
      reason: "manual_unban"
    });
    expect(response.json().actedAt).toEqual(expect.any(String));

    await app.close();
  });

  it("sends a YouTube test message through the live chat adapter", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/send-test",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        text: "ChatMod Mobile test message"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      messageId: "demo-sent-message"
    });

    await app.close();
  });

  it("rejects invalid YouTube test message text", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/send-test",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        text: "   "
      }
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    await app.close();
  });

  it("deletes a selected YouTube live chat message through the live chat adapter", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);
    const response = await app.inject({
      method: "POST",
      url: "/youtube/live-chat/messages/delete",
      headers: { authorization },
      payload: {
        messageId: "message-1",
        reason: "manual_queue_delete"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      messageId: "message-1",
      actionType: "deleteMessage",
      reason: "manual_queue_delete"
    });
    expect(response.json().deletedAt).toEqual(expect.any(String));

    await app.close();
  });

  it("creates and lists commands", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/commands",
      headers: { authorization },
      payload: {
        profileId: "profile-1",
        name: "!discord",
        response: "Join the Discord: https://example.com",
        aliases: ["!community"],
        cooldownSeconds: 45,
        accessLevel: "everyone",
        enabled: true
      }
    });

    expect(createResponse.statusCode).toBe(201);

    const listResponse = await app.inject({
      method: "GET",
      url: "/commands?profileId=profile-1",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      commands: [
        {
          name: "!discord",
          response: "Join the Discord: https://example.com"
        }
      ]
    });

    await app.close();
  });

  it("upserts commands with stable client ids", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "PUT",
      url: "/commands/cmd-local-1",
      headers: { authorization },
      payload: {
        profileId: "profile-sync-1",
        name: "!discord",
        response: "Join the Discord.",
        aliases: ["!community"],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: true
      }
    });

    expect(createResponse.statusCode).toBe(200);
    expect(createResponse.json()).toMatchObject({
      id: "cmd-local-1",
      response: "Join the Discord."
    });

    const updateResponse = await app.inject({
      method: "PUT",
      url: "/commands/cmd-local-1",
      headers: { authorization },
      payload: {
        profileId: "profile-sync-1",
        name: "!discord",
        response: "Updated Discord link.",
        aliases: [],
        cooldownSeconds: 45,
        accessLevel: "mods",
        enabled: true
      }
    });

    expect(updateResponse.statusCode).toBe(200);
    expect(updateResponse.json()).toMatchObject({
      id: "cmd-local-1",
      response: "Updated Discord link.",
      accessLevel: "mods"
    });

    await app.close();
  });

  it("rejects unsafe command response links", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "PUT",
      url: "/commands/cmd-unsafe-link",
      headers: { authorization },
      payload: {
        profileId: "profile-sync-1",
        name: "!debug",
        response: "Open http://localhost:4100/admin",
        aliases: [],
        cooldownSeconds: 30,
        accessLevel: "mods",
        enabled: true
      }
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "VALIDATION_ERROR",
      requestId: response.headers["x-request-id"]
    });

    const errorsResponse = await app.inject({
      method: "GET",
      url: "/logs/api-errors",
      headers: { authorization }
    });

    expect(errorsResponse.statusCode).toBe(200);
    expect(errorsResponse.json()).toMatchObject({
      errors: [
        {
          provider: "backend",
          code: "VALIDATION_ERROR",
          message: "Request payload did not match the expected schema.",
          metadata: {
            requestId: response.headers["x-request-id"],
            statusCode: 400,
            method: "PUT",
            url: "/commands/cmd-unsafe-link"
          }
        }
      ]
    });

    await app.close();
  });

  it("keeps commands scoped to the authenticated device", async () => {
    const app = await buildApp();
    const firstAuthorization = await createAuthorization(app, "test-device-123", "test-install-123");
    const secondAuthorization = await createAuthorization(app, "test-device-456", "test-install-456");

    await app.inject({
      method: "PUT",
      url: "/commands/cmd-private",
      headers: { authorization: firstAuthorization },
      payload: {
        profileId: "profile-private",
        name: "!private",
        response: "Only the first device should see this.",
        aliases: [],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: true
      }
    });

    const secondListResponse = await app.inject({
      method: "GET",
      url: "/commands?profileId=profile-private",
      headers: { authorization: secondAuthorization }
    });

    expect(secondListResponse.statusCode).toBe(200);
    expect(secondListResponse.json()).toMatchObject({
      commands: []
    });

    await app.close();
  });

  it("evaluates stored commands", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    await app.inject({
      method: "POST",
      url: "/commands",
      headers: { authorization },
      payload: {
        profileId: "profile-command-runtime",
        name: "!rules",
        response: "Rules for {username}: be kind.",
        aliases: ["!chat"],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: true
      }
    });

    const response = await app.inject({
      method: "POST",
      url: "/commands/evaluate",
      headers: { authorization },
      payload: {
        profileId: "profile-command-runtime",
        message: {
          authorChannelId: "viewer-1",
          authorName: "ViewerOne",
          text: "!chat"
        }
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      matched: true,
      response: "Rules for ViewerOne: be kind."
    });

    await app.close();
  });

  it("sends a stored command response to a selected live chat", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    await app.inject({
      method: "PUT",
      url: "/commands/cmd-send-rules",
      headers: { authorization },
      payload: {
        profileId: "profile-command-send",
        name: "!rules",
        response: "Rules for {streamTitle}: be kind.",
        aliases: ["!chat"],
        cooldownSeconds: 30,
        accessLevel: "mods",
        enabled: true
      }
    });

    const response = await app.inject({
      method: "POST",
      url: "/commands/cmd-send-rules/send",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        streamTitle: "Launch stream"
      }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      commandId: "cmd-send-rules",
      commandName: "!rules",
      liveChatId: "live-chat-1",
      messageId: "demo-sent-message",
      sentText: "Rules for Launch stream: be kind."
    });

    await app.close();
  });

  it("rejects manual sends for disabled commands", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    await app.inject({
      method: "PUT",
      url: "/commands/cmd-disabled-send",
      headers: { authorization },
      payload: {
        profileId: "profile-command-send",
        name: "!discord",
        response: "Join the Discord.",
        aliases: [],
        cooldownSeconds: 30,
        accessLevel: "everyone",
        enabled: false
      }
    });

    const response = await app.inject({
      method: "POST",
      url: "/commands/cmd-disabled-send/send",
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1"
      }
    });

    expect(response.statusCode).toBe(409);
    expect(response.json()).toMatchObject({
      error: "REQUEST_ERROR",
      message: "Command is disabled."
    });

    await app.close();
  });

  it("creates and lists channel profiles", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/profiles",
      headers: { authorization },
      payload: {
        channelId: "youtube-channel-1",
        name: "Main bot profile",
        config: {
          linkPolicy: "delete"
        }
      }
    });

    expect(createResponse.statusCode).toBe(201);

    const listResponse = await app.inject({
      method: "GET",
      url: "/profiles",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      profiles: [
        {
          channelId: "youtube-channel-1",
          name: "Main bot profile"
        }
      ]
    });

    await app.close();
  });

  it("records a user warning, strike, and optional live-chat warning message", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/user-profiles/warnings",
      headers: { authorization },
      payload: {
        profileId: "profile-user-warning",
        authorChannelId: "viewer-1",
        displayName: "Viewer One",
        profileImageUrl: "https://yt3.ggpht.com/viewer-one=s88-c-k-c0x00ffffff-no-rj",
        reason: "blocked_term:cheap views",
        liveChatId: "live-chat-1",
        warningText: "@Viewer One please keep chat friendly."
      }
    });

    expect(response.statusCode).toBe(201);
    expect(response.json()).toMatchObject({
      messageId: "demo-sent-message",
      user: {
        profileId: "profile-user-warning",
        authorChannelId: "viewer-1",
        displayName: "Viewer One",
        profileImageUrl: "https://yt3.ggpht.com/viewer-one=s88-c-k-c0x00ffffff-no-rj",
        strikeCount: 1,
        recentStrikes: [
          {
            reason: "blocked_term:cheap views"
          }
        ]
      },
      strike: {
        reason: "blocked_term:cheap views"
      }
    });

    const secondWarningResponse = await app.inject({
      method: "POST",
      url: "/user-profiles/warnings",
      headers: { authorization },
      payload: {
        profileId: "profile-user-warning",
        authorChannelId: "viewer-1",
        displayName: "Viewer One",
        profileImageUrl: "https://yt3.ggpht.com/viewer-one-updated=s88-c-k-c0x00ffffff-no-rj",
        reason: "manual_followup"
      }
    });

    expect(secondWarningResponse.statusCode).toBe(201);

    const listResponse = await app.inject({
      method: "GET",
      url: "/user-profiles?profileId=profile-user-warning",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      users: [
        {
          authorChannelId: "viewer-1",
          profileImageUrl: "https://yt3.ggpht.com/viewer-one-updated=s88-c-k-c0x00ffffff-no-rj",
          strikeCount: 2,
          recentStrikes: [
            {
              reason: "manual_followup"
            },
            {
              reason: "blocked_term:cheap views"
            }
          ]
        }
      ]
    });

    const userId = listResponse.json().users[0].id;
    const notesResponse = await app.inject({
      method: "PATCH",
      url: `/user-profiles/${userId}/notes`,
      headers: { authorization },
      payload: {
        notes: "Watch for repeat promo links."
      }
    });

    expect(notesResponse.statusCode).toBe(200);
    expect(notesResponse.json()).toMatchObject({
      user: {
        id: userId,
        notes: "Watch for repeat promo links.",
        strikeCount: 2
      }
    });

    const updatedListResponse = await app.inject({
      method: "GET",
      url: "/user-profiles?profileId=profile-user-warning",
      headers: { authorization }
    });

    expect(updatedListResponse.json()).toMatchObject({
      users: [
        {
          id: userId,
          notes: "Watch for repeat promo links.",
          strikeCount: 2
        }
      ]
    });

    const hideResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${userId}/hide`,
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        reason: "manual_profile_action"
      }
    });

    expect(hideResponse.statusCode).toBe(200);
    expect(hideResponse.json()).toMatchObject({
      actionType: "hideUser",
      liveChatId: "live-chat-1",
      liveChatBanId: "demo-live-chat-ban",
      reason: "manual_profile_action",
      user: {
        id: userId,
        authorChannelId: "viewer-1",
        recentModerationActions: [
          {
            actionType: "hideUser",
            liveChatId: "live-chat-1",
            liveChatBanId: "demo-live-chat-ban",
            reason: "manual_profile_action",
            durationSeconds: null,
            expiresAt: null
          }
        ]
      },
      action: {
        actionType: "hideUser",
        liveChatId: "live-chat-1",
        liveChatBanId: "demo-live-chat-ban",
        reason: "manual_profile_action",
        durationSeconds: null,
        expiresAt: null
      }
    });
    expect(hideResponse.json().hiddenAt).toEqual(expect.any(String));

    const timeoutResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${userId}/timeout`,
      headers: { authorization },
      payload: {
        liveChatId: "live-chat-1",
        durationSeconds: 300,
        reason: "manual_profile_timeout"
      }
    });

    expect(timeoutResponse.statusCode).toBe(200);
    expect(timeoutResponse.json()).toMatchObject({
      actionType: "timeoutUser",
      liveChatId: "live-chat-1",
      liveChatBanId: "demo-live-chat-ban",
      durationSeconds: 300,
      reason: "manual_profile_timeout",
      user: {
        id: userId,
        authorChannelId: "viewer-1",
        recentModerationActions: [
          {
            actionType: "timeoutUser",
            liveChatId: "live-chat-1",
            liveChatBanId: "demo-live-chat-ban",
            reason: "manual_profile_timeout",
            durationSeconds: 300,
            expiresAt: expect.any(String)
          },
          {
            actionType: "hideUser"
          }
        ]
      },
      action: {
        actionType: "timeoutUser",
        liveChatId: "live-chat-1",
        liveChatBanId: "demo-live-chat-ban",
        reason: "manual_profile_timeout",
        durationSeconds: 300,
        expiresAt: expect.any(String)
      }
    });
    expect(timeoutResponse.json().timedOutAt).toEqual(expect.any(String));
    expect(timeoutResponse.json().timedOutUntil).toEqual(expect.any(String));

    const unbanResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${userId}/unban`,
      headers: { authorization },
      payload: {
        liveChatBanId: "demo-live-chat-ban",
        liveChatId: "live-chat-1",
        reason: "manual_profile_unban"
      }
    });

    expect(unbanResponse.statusCode).toBe(200);
    expect(unbanResponse.json()).toMatchObject({
      actionType: "unbanUser",
      liveChatBanId: "demo-live-chat-ban",
      reason: "manual_profile_unban",
      user: {
        id: userId,
        recentModerationActions: [
          {
            actionType: "unbanUser",
            liveChatId: "live-chat-1",
            liveChatBanId: "demo-live-chat-ban",
            reason: "manual_profile_unban",
            durationSeconds: null,
            expiresAt: null
          },
          {
            actionType: "timeoutUser"
          },
          {
            actionType: "hideUser"
          }
        ]
      },
      action: {
        actionType: "unbanUser",
        liveChatId: "live-chat-1",
        liveChatBanId: "demo-live-chat-ban",
        reason: "manual_profile_unban",
        durationSeconds: null,
        expiresAt: null
      }
    });
    expect(unbanResponse.json().unbannedAt).toEqual(expect.any(String));

    const whitelistResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${userId}/whitelist`,
      headers: { authorization }
    });

    expect(whitelistResponse.statusCode).toBe(200);
    expect(whitelistResponse.json()).toMatchObject({
      user: {
        id: userId,
        authorChannelId: "viewer-1"
      },
      whitelist: {
        profileId: "profile-user-warning",
        authorChannelId: "viewer-1",
        displayName: "Viewer One",
        temporaryUntil: null
      }
    });
    expect(whitelistResponse.json().whitelistedAt).toEqual(expect.any(String));

    const temporaryWhitelistResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${userId}/whitelist`,
      headers: { authorization },
      payload: {
        durationSeconds: 3600
      }
    });

    expect(temporaryWhitelistResponse.statusCode).toBe(200);
    expect(temporaryWhitelistResponse.json()).toMatchObject({
      whitelist: {
        profileId: "profile-user-warning",
        authorChannelId: "viewer-1",
        displayName: "Viewer One",
        temporaryUntil: expect.any(String)
      }
    });
    expect(Date.parse(temporaryWhitelistResponse.json().whitelist.temporaryUntil)).toBeGreaterThan(Date.now());

    const historyResponse = await app.inject({
      method: "GET",
      url: "/user-profiles?profileId=profile-user-warning",
      headers: { authorization }
    });

    expect(historyResponse.statusCode).toBe(200);
    expect(historyResponse.json()).toMatchObject({
      users: [
        {
          id: userId,
          recentModerationActions: [
            {
              actionType: "unbanUser",
              liveChatBanId: "demo-live-chat-ban",
              reason: "manual_profile_unban",
              durationSeconds: null,
              expiresAt: null
            },
            {
              actionType: "timeoutUser",
              liveChatBanId: "demo-live-chat-ban",
              reason: "manual_profile_timeout",
              durationSeconds: 300,
              expiresAt: expect.any(String)
            },
            {
              actionType: "hideUser",
              liveChatBanId: "demo-live-chat-ban",
              reason: "manual_profile_action",
              durationSeconds: null,
              expiresAt: null
            }
          ]
        }
      ]
    });

    await app.close();
  });

  it("keeps warned user profiles scoped to the authenticated device", async () => {
    const app = await buildApp();
    const firstAuthorization = await createAuthorization(app, "warn-device-1", "warn-install-1");
    const secondAuthorization = await createAuthorization(app, "warn-device-2", "warn-install-2");

    await app.inject({
      method: "POST",
      url: "/user-profiles/warnings",
      headers: { authorization: firstAuthorization },
      payload: {
        profileId: "profile-user-warning",
        authorChannelId: "viewer-private",
        displayName: "Private Viewer",
        reason: "manual_warning"
      }
    });

    const response = await app.inject({
      method: "GET",
      url: "/user-profiles?profileId=profile-user-warning",
      headers: { authorization: secondAuthorization }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      users: []
    });

    const firstListResponse = await app.inject({
      method: "GET",
      url: "/user-profiles?profileId=profile-user-warning",
      headers: { authorization: firstAuthorization }
    });
    const privateUserId = firstListResponse.json().users[0].id;

    const notesResponse = await app.inject({
      method: "PATCH",
      url: `/user-profiles/${privateUserId}/notes`,
      headers: { authorization: secondAuthorization },
      payload: {
        notes: "Should not save"
      }
    });

    expect(notesResponse.statusCode).toBe(404);

    const hideResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${privateUserId}/hide`,
      headers: { authorization: secondAuthorization },
      payload: {
        liveChatId: "live-chat-private"
      }
    });

    expect(hideResponse.statusCode).toBe(404);

    const timeoutResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${privateUserId}/timeout`,
      headers: { authorization: secondAuthorization },
      payload: {
        liveChatId: "live-chat-private",
        durationSeconds: 300
      }
    });

    expect(timeoutResponse.statusCode).toBe(404);

    const whitelistResponse = await app.inject({
      method: "POST",
      url: `/user-profiles/${privateUserId}/whitelist`,
      headers: { authorization: secondAuthorization }
    });

    expect(whitelistResponse.statusCode).toBe(404);

    await app.close();
  });

  it("creates, syncs, defaults, and scopes rule presets", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "preset-device-1", "preset-install-1");
    const otherAuthorization = await createAuthorization(app, "preset-device-2", "preset-install-2");

    const createResponse = await app.inject({
      method: "POST",
      url: "/rule-presets",
      headers: { authorization },
      payload: {
        profileId: "profile-presets-1",
        name: "Family friendly",
        config: {
          blockedTerms: ["spoiler"],
          linkPolicy: "delete",
          capsThreshold: 0.72,
          maxRepeatedCharacters: 5
        },
        isDefault: true
      }
    });

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.json()).toMatchObject({
      profileId: "profile-presets-1",
      name: "Family friendly",
      isDefault: true,
      config: {
        linkPolicy: "delete"
      }
    });

    const upsertResponse = await app.inject({
      method: "PUT",
      url: "/rule-presets/preset-strict-links",
      headers: { authorization },
      payload: {
        profileId: "profile-presets-1",
        name: "Strict links",
        config: {
          blockedTerms: ["spam"],
          linkPolicy: "delete",
          capsThreshold: 0.6,
          maxRepeatedCharacters: 3
        },
        isDefault: true
      }
    });

    expect(upsertResponse.statusCode).toBe(200);
    expect(upsertResponse.json()).toMatchObject({
      id: "preset-strict-links",
      isDefault: true,
      config: {
        linkPolicy: "delete",
        capsThreshold: 0.6,
        maxRepeatedCharacters: 3
      }
    });

    const listResponse = await app.inject({
      method: "GET",
      url: "/rule-presets?profileId=profile-presets-1",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      rulePresets: [
        {
          name: "Family friendly",
          isDefault: false
        },
        {
          id: "preset-strict-links",
          name: "Strict links",
          isDefault: true
        }
      ]
    });

    const otherListResponse = await app.inject({
      method: "GET",
      url: "/rule-presets?profileId=profile-presets-1",
      headers: { authorization: otherAuthorization }
    });

    expect(otherListResponse.statusCode).toBe(200);
    expect(otherListResponse.json()).toMatchObject({
      rulePresets: []
    });

    const deleteResponse = await app.inject({
      method: "DELETE",
      url: "/rule-presets/preset-strict-links",
      headers: { authorization }
    });

    expect(deleteResponse.statusCode).toBe(204);

    await app.close();
  });

  it("exports and imports rule preset bundles with fresh imported ids", async () => {
    const adminApiKey = "test-admin-key-with-at-least-32-chars";
    const app = await buildApp({ adminApiKey });
    const authorization = await createAuthorization(app, "preset-export-device", "preset-export-install");

    const sourcePresetResponse = await app.inject({
      method: "PUT",
      url: "/rule-presets/preset-export-source",
      headers: { authorization },
      payload: {
        profileId: "profile-preset-export-source",
        name: "Exported stream safe",
        config: {
          blockedTerms: ["spoiler"],
          linkPolicy: "flag",
          capsThreshold: 0.7,
          maxRepeatedCharacters: 5
        },
        isDefault: true
      }
    });
    expect(sourcePresetResponse.statusCode).toBe(200);

    const starterExportResponse = await app.inject({
      method: "GET",
      url: "/rule-presets/export?profileId=profile-preset-export-source",
      headers: { authorization }
    });
    expect(starterExportResponse.statusCode).toBe(403);

    const adjustResponse = await app.inject({
      method: "POST",
      url: "/admin/support/entitlements/manual-adjust",
      headers: { "x-admin-api-key": adminApiKey },
      payload: {
        deviceId: "preset-export-device",
        installId: "preset-export-install",
        plan: "pro",
        status: "active",
        ticketId: "TEST-PRESET-EXPORT"
      }
    });
    expect(adjustResponse.statusCode).toBe(200);

    const exportResponse = await app.inject({
      method: "GET",
      url: "/rule-presets/export?profileId=profile-preset-export-source",
      headers: { authorization }
    });
    expect(exportResponse.statusCode).toBe(200);
    expect(exportResponse.json()).toMatchObject({
      formatVersion: 1,
      profileId: "profile-preset-export-source",
      rulePresets: [
        {
          id: "preset-export-source",
          name: "Exported stream safe",
          isDefault: true,
          config: {
            blockedTerms: ["spoiler"]
          }
        }
      ]
    });

    const importResponse = await app.inject({
      method: "POST",
      url: "/rule-presets/import",
      headers: { authorization },
      payload: {
        profileId: "profile-preset-export-source",
        bundle: exportResponse.json()
      }
    });
    expect(importResponse.statusCode).toBe(200);
    const imported = importResponse.json<{
      importedCount: number;
      rulePresets: Array<{ id: string; profileId: string; name: string; isDefault: boolean }>;
    }>();
    expect(imported.importedCount).toBe(1);
    expect(imported.rulePresets[0]).toMatchObject({
      profileId: "profile-preset-export-source",
      name: "Exported stream safe",
      isDefault: true
    });
    expect(imported.rulePresets[0]?.id).not.toBe("preset-export-source");

    const targetListResponse = await app.inject({
      method: "GET",
      url: "/rule-presets?profileId=profile-preset-export-source",
      headers: { authorization }
    });
    expect(targetListResponse.statusCode).toBe(200);
    const targetPresets = targetListResponse.json<{
      rulePresets: Array<{ id: string; name: string; isDefault: boolean }>;
    }>().rulePresets;
    expect(targetPresets).toHaveLength(2);
    expect(targetPresets.filter((preset) => preset.isDefault)).toHaveLength(1);
    expect(targetPresets.some((preset) => preset.id !== "preset-export-source" && preset.name === "Exported stream safe")).toBe(true);

    await app.close();
  });

  it("rejects imported rule preset bundles with multiple defaults", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "preset-import-invalid-device", "preset-import-invalid-install");

    const importResponse = await app.inject({
      method: "POST",
      url: "/rule-presets/import",
      headers: { authorization },
      payload: {
        profileId: "profile-preset-import-invalid",
        bundle: {
          formatVersion: 1,
          rulePresets: [
            {
              name: "Default one",
              config: { blockedTerms: ["one"] },
              isDefault: true
            },
            {
              name: "Default two",
              config: { blockedTerms: ["two"] },
              isDefault: true
            }
          ]
        }
      }
    });

    expect(importResponse.statusCode).toBe(400);
    expect(importResponse.body).toContain("Only one imported rule preset can be marked as default");

    await app.close();
  });

  it("rejects advanced rule preset filters for starter entitlements", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "preset-advanced-device", "preset-advanced-install");

    const response = await app.inject({
      method: "POST",
      url: "/rule-presets",
      headers: { authorization },
      payload: {
        profileId: "profile-presets-advanced",
        name: "Advanced filter test",
        config: {
          blockedTerms: ["spam"],
          regexPatterns: ["free\\s+nitro"],
          blockedDomains: ["grabify.link"]
        },
        isDefault: false
      }
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({
      message: "Advanced moderation filters require Pro or Creator plan: regexPatterns, blockedDomains."
    });

    const listResponse = await app.inject({
      method: "GET",
      url: "/rule-presets?profileId=profile-presets-advanced",
      headers: { authorization }
    });
    expect(listResponse.json()).toMatchObject({
      rulePresets: []
    });

    await app.close();
  });

  it("enforces starter profile limits for implicit rule preset profiles", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "preset-profile-limit-device", "preset-profile-limit-install");

    const firstResponse = await app.inject({
      method: "PUT",
      url: "/rule-presets/preset-primary-profile",
      headers: { authorization },
      payload: {
        profileId: "profile-primary-preset",
        name: "Primary preset",
        config: {
          blockedTerms: ["spam"]
        },
        isDefault: true
      }
    });
    expect(firstResponse.statusCode).toBe(200);

    const secondResponse = await app.inject({
      method: "PUT",
      url: "/rule-presets/preset-secondary-profile",
      headers: { authorization },
      payload: {
        profileId: "profile-secondary-preset",
        name: "Secondary preset",
        config: {
          blockedTerms: ["spam"]
        },
        isDefault: true
      }
    });

    expect(secondResponse.statusCode).toBe(403);
    expect(secondResponse.json()).toMatchObject({
      message: "Channel profile limit reached for starter plan (1)."
    });

    await app.close();
  });

  it("lists curated rule preset templates", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "preset-template-device", "preset-template-install");

    const response = await app.inject({
      method: "GET",
      url: "/rule-presets/templates",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.rulePresetTemplates).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: "family-friendly", name: "Family friendly" }),
        expect.objectContaining({ id: "gaming-default", name: "Gaming default" }),
        expect.objectContaining({ id: "education-qa", name: "Education/Q&A" }),
        expect.objectContaining({ id: "music-performance", name: "Music/live performance" })
      ])
    );
    expect(body.rulePresetTemplates.find((template: { id: string }) => template.id === "family-friendly"))
      .toMatchObject({
        config: {
          linkPolicy: "flag",
          capsThreshold: 0.72,
          maxRepeatedCharacters: 5
        }
      });

    await app.close();
  });

  it("creates, lists, and deletes profile backups", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/backups/moderation-profile",
      headers: { authorization },
      payload: {
        profileId: "profile-backup-1",
        channelId: "youtube-channel-1",
        profileName: "Main bot profile",
        config: {
          linkPolicy: "delete"
        },
        clientVersion: "0.1.0"
      }
    });

    expect(createResponse.statusCode).toBe(201);
    const backup = createResponse.json<{ id: string }>();

    const listResponse = await app.inject({
      method: "GET",
      url: "/backups",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      backups: [
        {
          id: backup.id,
          profileName: "Main bot profile",
          version: 1
        }
      ]
    });

    const deleteResponse = await app.inject({
      method: "DELETE",
      url: `/backups/${backup.id}`,
      headers: { authorization }
    });

    expect(deleteResponse.statusCode).toBe(204);

    const afterDeleteResponse = await app.inject({
      method: "GET",
      url: "/backups",
      headers: { authorization }
    });

    expect(afterDeleteResponse.statusCode).toBe(200);
    expect(afterDeleteResponse.json()).toMatchObject({
      backups: []
    });

    await app.close();
  });

  it("backs up and restores commands and timers", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const backupResponse = await app.inject({
      method: "POST",
      url: "/backups/settings",
      headers: { authorization },
      payload: {
        profileId: "profile-settings-1",
        channelId: "youtube-channel-1",
        profileName: "Main bot profile",
        commands: [
          {
            id: "cmd-rules-backup",
            name: "!rules",
            response: "Keep chat friendly.",
            aliases: ["!chat"],
            cooldownSeconds: 30,
            accessLevel: "everyone",
            enabled: true
          }
        ],
        timers: [
          {
            id: "timer-rules-backup",
            name: "Rules reminder",
            message: "Keep chat friendly.",
            intervalMinutes: 15,
            minChatMessages: 5,
            enabled: true
          }
        ],
        clientVersion: "0.1.0"
      }
    });

    expect(backupResponse.statusCode).toBe(201);
    expect(backupResponse.json()).toMatchObject({
      profileName: "Main bot profile",
      commandCount: 1,
      timerCount: 1
    });
    const backup = backupResponse.json<{ id: string }>();

    const restoreResponse = await app.inject({
      method: "POST",
      url: `/backups/${backup.id}/restore`,
      headers: { authorization },
      payload: {
        targetProfileId: "profile-settings-restored"
      }
    });

    expect(restoreResponse.statusCode).toBe(200);
    expect(restoreResponse.json()).toMatchObject({
      backupId: backup.id,
      profileId: "profile-settings-restored",
      commands: [
        {
          id: "cmd-rules-backup",
          profileId: "profile-settings-restored",
          name: "!rules"
        }
      ],
      timers: [
        {
          id: "timer-rules-backup",
          profileId: "profile-settings-restored",
          name: "Rules reminder"
        }
      ]
    });

    const commandListResponse = await app.inject({
      method: "GET",
      url: "/commands?profileId=profile-settings-restored",
      headers: { authorization }
    });
    const timerListResponse = await app.inject({
      method: "GET",
      url: "/timers?profileId=profile-settings-restored",
      headers: { authorization }
    });

    expect(commandListResponse.json()).toMatchObject({
      commands: [
        {
          id: "cmd-rules-backup",
          response: "Keep chat friendly."
        }
      ]
    });
    expect(timerListResponse.json()).toMatchObject({
      timers: [
        {
          id: "timer-rules-backup",
          intervalMinutes: 15
        }
      ]
    });

    await app.close();
  });

  it("rejects settings restores that exceed starter command limits", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const backupResponse = await app.inject({
      method: "POST",
      url: "/backups/settings",
      headers: { authorization },
      payload: {
        profileId: "profile-settings-limit",
        channelId: "youtube-channel-1",
        profileName: "Limit profile",
        commands: [1, 2, 3, 4].map((index) => ({
          id: `cmd-limit-${index}`,
          name: `!limit${index}`,
          response: `Limit command ${index}`,
          aliases: [],
          cooldownSeconds: 30,
          accessLevel: "everyone",
          enabled: true
        })),
        timers: [],
        clientVersion: "0.1.0"
      }
    });
    expect(backupResponse.statusCode).toBe(201);

    const backup = backupResponse.json<{ id: string }>();
    const restoreResponse = await app.inject({
      method: "POST",
      url: `/backups/${backup.id}/restore`,
      headers: { authorization }
    });

    expect(restoreResponse.statusCode).toBe(403);
    expect(restoreResponse.json()).toMatchObject({
      message: "Command limit reached for starter plan (3)."
    });

    await app.close();
  });

  it("rejects unsafe command links in settings backups", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/backups/settings",
      headers: { authorization },
      payload: {
        profileId: "profile-settings-unsafe",
        channelId: "youtube-channel-1",
        profileName: "Unsafe profile",
        commands: [
          {
            name: "!debug",
            response: "Open javascript:alert(1)",
            aliases: [],
            cooldownSeconds: 30,
            accessLevel: "mods",
            enabled: true
          }
        ],
        timers: []
      }
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    await app.close();
  });

  it("creates and lists timers", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/timers",
      headers: { authorization },
      payload: {
        profileId: "profile-1",
        name: "Rules reminder",
        message: "Keep chat friendly and stay on topic.",
        intervalMinutes: 15,
        minChatMessages: 10,
        enabled: true
      }
    });

    expect(createResponse.statusCode).toBe(201);

    const listResponse = await app.inject({
      method: "GET",
      url: "/timers?profileId=profile-1",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      timers: [
        {
          name: "Rules reminder",
          intervalMinutes: 15
        }
      ]
    });

    await app.close();
  });

  it("upserts timers with stable client ids", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "PUT",
      url: "/timers/timer-local-1",
      headers: { authorization },
      payload: {
        profileId: "profile-sync-1",
        name: "Rules reminder",
        message: "Keep chat friendly.",
        intervalMinutes: 15,
        minChatMessages: 10,
        enabled: true
      }
    });

    expect(createResponse.statusCode).toBe(200);
    expect(createResponse.json()).toMatchObject({
      id: "timer-local-1",
      intervalMinutes: 15
    });

    const updateResponse = await app.inject({
      method: "PUT",
      url: "/timers/timer-local-1",
      headers: { authorization },
      payload: {
        profileId: "profile-sync-1",
        name: "Rules reminder",
        message: "Updated timer copy.",
        intervalMinutes: 20,
        minChatMessages: 5,
        enabled: false
      }
    });

    expect(updateResponse.statusCode).toBe(200);
    expect(updateResponse.json()).toMatchObject({
      id: "timer-local-1",
      intervalMinutes: 20,
      enabled: false
    });

    await app.close();
  });

  it("finds due timers and marks them sent", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/timers",
      headers: { authorization },
      payload: {
        profileId: "profile-timer-runtime",
        name: "Rules reminder",
        message: "Keep chat friendly.",
        intervalMinutes: 15,
        minChatMessages: 2,
        enabled: true
      }
    });
    const timer = createResponse.json<{ id: string }>();

    const dueResponse = await app.inject({
      method: "POST",
      url: "/timers/due",
      headers: { authorization },
      payload: {
        profileId: "profile-timer-runtime",
        messagesSinceLastTimer: 3,
        now: "2026-06-07T10:00:00.000Z"
      }
    });

    expect(dueResponse.statusCode).toBe(200);
    expect(dueResponse.json()).toMatchObject({
      timers: [
        {
          name: "Rules reminder"
        }
      ]
    });

    const sentResponse = await app.inject({
      method: "POST",
      url: `/timers/${timer.id}/mark-sent`,
      headers: { authorization },
      payload: {
        sentAt: "2026-06-07T10:00:00.000Z"
      }
    });

    expect(sentResponse.statusCode).toBe(200);
    expect(sentResponse.json()).toMatchObject({
      lastSentAt: "2026-06-07T10:00:00.000Z"
    });

    await app.close();
  });

  it("records support events", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/logs/support-events",
      headers: { authorization },
      payload: {
        severity: "warning",
        message: "YouTube quota warning surfaced",
        details: {
          quotaRemaining: 12
        }
      }
    });

    expect(createResponse.statusCode).toBe(201);

    const listResponse = await app.inject({
      method: "GET",
      url: "/logs/support-events",
      headers: { authorization }
    });

    expect(listResponse.statusCode).toBe(200);
    expect(listResponse.json()).toMatchObject({
      events: [
        {
          severity: "warning",
          message: "YouTube quota warning surfaced"
        }
      ]
    });

    await app.close();
  });

  it("records opt-in usage analytics separately from support diagnostics", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/analytics/events",
      headers: { authorization },
      payload: {
        name: "bot_start",
        consent: true,
        appVersion: "0.1.0",
        properties: {
          source: "dashboard",
          commandCount: 2
        }
      }
    });

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.json()).toMatchObject({
      name: "bot_start",
      appVersion: "0.1.0",
      platform: "android",
      properties: {
        source: "dashboard",
        commandCount: 2
      }
    });

    const analyticsResponse = await app.inject({
      method: "GET",
      url: "/analytics/events",
      headers: { authorization }
    });
    expect(analyticsResponse.statusCode).toBe(200);
    expect(analyticsResponse.json()).toMatchObject({
      events: [
        {
          name: "bot_start"
        }
      ]
    });

    const supportResponse = await app.inject({
      method: "GET",
      url: "/logs/support-events",
      headers: { authorization }
    });
    expect(supportResponse.statusCode).toBe(200);
    expect(supportResponse.json()).toMatchObject({
      events: []
    });

    await app.close();
  });

  it("rejects analytics events without explicit opt-in consent", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/analytics/events",
      headers: { authorization },
      payload: {
        name: "app_open",
        properties: {
          source: "startup"
        }
      }
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    await app.close();
  });

  it("rejects analytics events with sensitive property names", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/analytics/events",
      headers: { authorization },
      payload: {
        name: "app_open",
        consent: true,
        properties: {
          chatMessage: "do not collect this"
        }
      }
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    await app.close();
  });

  it("records beta feedback separately from support diagnostics", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const createResponse = await app.inject({
      method: "POST",
      url: "/feedback/beta",
      headers: { authorization },
      payload: {
        category: "confusing",
        message: "The stream connection step needs clearer labels.",
        appVersion: "0.1.0",
        context: {
          selectedTab: "Support",
          botRunning: false,
          commandCount: 2
        }
      }
    });

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.json()).toMatchObject({
      category: "confusing",
      message: "The stream connection step needs clearer labels.",
      appVersion: "0.1.0",
      platform: "android",
      context: {
        selectedTab: "Support",
        botRunning: false,
        commandCount: 2
      }
    });

    const feedbackResponse = await app.inject({
      method: "GET",
      url: "/feedback/beta",
      headers: { authorization }
    });
    expect(feedbackResponse.statusCode).toBe(200);
    expect(feedbackResponse.json()).toMatchObject({
      feedback: [
        {
          category: "confusing",
          message: "The stream connection step needs clearer labels."
        }
      ]
    });

    const supportResponse = await app.inject({
      method: "GET",
      url: "/logs/support-events",
      headers: { authorization }
    });
    expect(supportResponse.statusCode).toBe(200);
    expect(supportResponse.json()).toMatchObject({
      events: []
    });

    await app.close();
  });

  it("rejects beta feedback with sensitive context fields", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app);

    const response = await app.inject({
      method: "POST",
      url: "/feedback/beta",
      headers: { authorization },
      payload: {
        category: "bug",
        message: "A bug happened.",
        context: {
          chatMessage: "do not collect this"
        }
      }
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    await app.close();
  });

  it("records launch-site beta interest through the backend support path", async () => {
    const app = await buildApp({ adminApiKey: "test-admin-key" });

    const createResponse = await app.inject({
      method: "POST",
      url: "/feedback/beta-interest",
      headers: {
        "user-agent": "chatmod-launch-test"
      },
      payload: {
        email: "Creator@Example.com",
        creatorName: "Creator One",
        channelUrl: "https://www.youtube.com/@creatorone",
        message: "I need help with spam during 200-viewer live streams.",
        source: "launch-site"
      }
    });

    expect(createResponse.statusCode).toBe(201);
    expect(createResponse.json()).toMatchObject({
      saved: true,
      receivedAt: expect.any(String)
    });

    const supportResponse = await app.inject({
      method: "GET",
      url: "/admin/support/devices/launch-site-beta-interest",
      headers: {
        "x-admin-api-key": "test-admin-key"
      }
    });

    expect(supportResponse.statusCode).toBe(200);
    expect(supportResponse.json()).toMatchObject({
      supportEvents: [
        {
          severity: "info",
          message: "Launch site beta interest submitted",
          details: {
            eventType: "beta_interest",
            email: "creator@example.com",
            creatorName: "Creator One",
            channelUrl: "https://www.youtube.com/@creatorone",
            note: "I need help with spam during 200-viewer live streams.",
            source: "launch-site",
            userAgent: "chatmod-launch-test"
          }
        }
      ]
    });

    await app.close();
  });

  it("rejects invalid launch-site beta interest payloads", async () => {
    const app = await buildApp();

    const badEmailResponse = await app.inject({
      method: "POST",
      url: "/feedback/beta-interest",
      payload: {
        email: "not-an-email"
      }
    });
    expect(badEmailResponse.statusCode).toBe(400);
    expect(badEmailResponse.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    const badChannelResponse = await app.inject({
      method: "POST",
      url: "/feedback/beta-interest",
      payload: {
        email: "creator@example.com",
        channelUrl: "https://example.com/not-youtube"
      }
    });
    expect(badChannelResponse.statusCode).toBe(400);
    expect(badChannelResponse.json()).toMatchObject({
      error: "VALIDATION_ERROR"
    });

    await app.close();
  });

  it("records stream session messages, moderation actions, runtime events, and end state", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "stream-device-1", "stream-install-1");
    const otherAuthorization = await createAuthorization(app, "stream-device-2", "stream-install-2");

    const startResponse = await app.inject({
      method: "PUT",
      url: "/stream-sessions/session-live-1",
      headers: { authorization },
      payload: {
        profileId: "profile-stream-1",
        videoId: "video-1",
        liveChatId: "live-chat-1",
        title: "Launch stream",
        startedAt: "2026-06-07T18:00:00.000Z"
      }
    });

    expect(startResponse.statusCode).toBe(200);
    expect(startResponse.json()).toMatchObject({
      id: "session-live-1",
      profileId: "profile-stream-1",
      videoId: "video-1",
      liveChatId: "live-chat-1",
      title: "Launch stream",
      startedAt: "2026-06-07T18:00:00.000Z",
      endedAt: null
    });

    const messageResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "yt-message-1",
        authorChannelId: "viewer-1",
        authorName: "Viewer One",
        text: "buy cheap views",
        receivedAt: "2026-06-07T18:01:00.000Z"
      }
    });
    expect(messageResponse.statusCode).toBe(201);

    const duplicateMessageResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "yt-message-1",
        authorChannelId: "viewer-1",
        authorName: "Viewer One",
        text: "buy cheap views now",
        receivedAt: "2026-06-07T18:01:05.000Z"
      }
    });
    expect(duplicateMessageResponse.statusCode).toBe(201);

    const actionResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/actions",
      headers: { authorization },
      payload: {
        clientActionId: "local-action-1",
        youtubeMessageId: "yt-message-1",
        authorChannelId: "viewer-1",
        actionType: "deleteMessage",
        reason: "blocked_term:cheap views",
        confidence: 0.96
      }
    });
    expect(actionResponse.statusCode).toBe(201);
    expect(actionResponse.json().id).toBe("local-action-1");

    const actionReviewResponse = await app.inject({
      method: "PATCH",
      url: "/stream-sessions/session-live-1/actions/local-action-1/review",
      headers: { authorization },
      payload: {
        reviewStatus: "false_positive",
        reviewNote: "Allowed creator phrase"
      }
    });
    expect(actionReviewResponse.statusCode).toBe(200);
    expect(actionReviewResponse.json()).toMatchObject({
      id: actionResponse.json().id,
      reviewStatus: "false_positive",
      reviewNote: "Allowed creator phrase",
      reviewedAt: expect.any(String)
    });

    const timeoutActionResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/actions",
      headers: { authorization },
      payload: {
        youtubeMessageId: null,
        authorChannelId: "viewer-2",
        actionType: "timeoutUser",
        reason: "manual_profile_timeout",
        confidence: null
      }
    });
    expect(timeoutActionResponse.statusCode).toBe(201);

    const autoReplyActionResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/actions",
      headers: { authorization },
      payload: {
        youtubeMessageId: "yt-message-1",
        authorChannelId: "viewer-1",
        actionType: "sendAutoReply",
        reason: "auto_reply",
        confidence: 0.65
      }
    });
    expect(autoReplyActionResponse.statusCode).toBe(201);

    const eventResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/runtime-events",
      headers: { authorization },
      payload: {
        type: "command_sent",
        message: "Sent !rules",
        metadata: {
          commandId: "cmd-rules"
        }
      }
    });
    expect(eventResponse.statusCode).toBe(201);

    const logsResponse = await app.inject({
      method: "GET",
      url: "/stream-sessions/session-live-1/logs",
      headers: { authorization }
    });

    expect(logsResponse.statusCode).toBe(200);
    expect(logsResponse.json()).toMatchObject({
      session: {
        id: "session-live-1"
      },
      messages: [
        {
          youtubeMessageId: "yt-message-1",
          text: "buy cheap views now",
          receivedAt: "2026-06-07T18:01:05.000Z"
        }
      ],
      actions: [
        {
          youtubeMessageId: "yt-message-1",
          authorChannelId: "viewer-1",
          actionType: "deleteMessage",
          reason: "blocked_term:cheap views",
          confidence: 0.96,
          reviewStatus: "false_positive",
          reviewNote: "Allowed creator phrase",
          reviewedAt: expect.any(String)
        },
        {
          youtubeMessageId: null,
          authorChannelId: "viewer-2",
          actionType: "timeoutUser",
          reason: "manual_profile_timeout",
          confidence: null
        },
        {
          youtubeMessageId: "yt-message-1",
          authorChannelId: "viewer-1",
          actionType: "sendAutoReply",
          reason: "auto_reply",
          confidence: 0.65
        }
      ],
      runtimeEvents: [
        {
          type: "command_sent",
          message: "Sent !rules",
          metadata: {
            commandId: "cmd-rules"
          }
        }
      ]
    });

    const exportResponse = await app.inject({
      method: "GET",
      url: "/stream-sessions/session-live-1/export?format=csv",
      headers: { authorization }
    });
    expect(exportResponse.statusCode).toBe(200);
    expect(exportResponse.headers["content-type"]).toContain("text/csv");
    expect(exportResponse.headers["content-disposition"]).toContain("chatmod-video-1.csv");
    expect(exportResponse.body).toContain("\"chat\"");
    expect(exportResponse.body).toContain("\"moderation\"");
    expect(exportResponse.body).toContain("\"sendAutoReply\"");
    expect(exportResponse.body).toContain("\"false_positive\"");
    expect(exportResponse.body).toContain("\"runtime\"");

    const otherLogsResponse = await app.inject({
      method: "GET",
      url: "/stream-sessions/session-live-1/logs",
      headers: { authorization: otherAuthorization }
    });
    expect(otherLogsResponse.statusCode).toBe(404);

    const endResponse = await app.inject({
      method: "POST",
      url: "/stream-sessions/session-live-1/end",
      headers: { authorization },
      payload: {
        endedAt: "2026-06-07T19:00:00.000Z"
      }
    });
    expect(endResponse.statusCode).toBe(200);
    expect(endResponse.json()).toMatchObject({
      endedAt: "2026-06-07T19:00:00.000Z"
    });

    await app.close();
  });

  it("serves a tokenized OBS browser overlay from stream-session audit state", async () => {
    const starterApp = await buildApp();
    const starterAuthorization = await createAuthorization(starterApp, "overlay-starter-device", "overlay-starter-install");
    const starterResponse = await starterApp.inject({
      method: "PUT",
      url: "/overlays/profiles/profile-overlay-starter",
      headers: { authorization: starterAuthorization },
      payload: {
        enabled: true
      }
    });
    expect(starterResponse.statusCode).toBe(403);
    await starterApp.close();

    const app = await buildApp({
      entitlementStore: fakeEntitlementStore(snapshotForPlan({
        plan: "pro",
        status: "active",
        source: "test",
        productId: "chatmod_pro_monthly",
        currentPeriodEndsAt: new Date("2026-07-11T10:00:00.000Z")
      }))
    });
    const authorization = await createAuthorization(app, "overlay-pro-device", "overlay-pro-install");

    const configResponse = await app.inject({
      method: "PUT",
      url: "/overlays/profiles/profile-overlay-1",
      headers: { authorization },
      payload: {
        enabled: true,
        activeSessionId: "overlay-session-1",
        theme: "transparent_minimal",
        showRecentChat: false
      }
    });
    expect(configResponse.statusCode).toBe(200);
    expect(configResponse.json()).toMatchObject({
      profileId: "profile-overlay-1",
      configured: true,
      enabled: true,
      theme: "transparent_minimal",
      tokenRotated: true,
      tokenPreview: expect.any(String),
      publicPath: expect.stringMatching(/^\/overlays\/public\/cmo_[A-Za-z0-9_-]+$/)
    });
    const publicPath = configResponse.json<{ publicPath: string }>().publicPath;

    await app.inject({
      method: "PUT",
      url: "/stream-sessions/overlay-session-1",
      headers: { authorization },
      payload: {
        profileId: "profile-overlay-1",
        videoId: "overlay-video-1",
        liveChatId: "overlay-live-chat-1",
        title: "OBS launch test",
        startedAt: "2026-06-11T20:00:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/overlay-session-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "overlay-message-1",
        authorChannelId: "overlay-viewer-1",
        authorName: "Viewer",
        text: "cheap views cheap views",
        receivedAt: "2026-06-11T20:00:05.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/overlay-session-1/actions",
      headers: { authorization },
      payload: {
        clientActionId: "overlay-action-1",
        youtubeMessageId: "overlay-message-1",
        authorChannelId: "overlay-viewer-1",
        actionType: "deleteMessage",
        reason: "blocked_term:cheap views",
        confidence: 0.91
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/overlay-session-1/runtime-events",
      headers: { authorization },
      payload: {
        type: "command_sent",
        message: "Sent !rules"
      }
    });

    const htmlResponse = await app.inject({
      method: "GET",
      url: publicPath
    });
    expect(htmlResponse.statusCode).toBe(200);
    expect(htmlResponse.headers["content-type"]).toContain("text/html");
    expect(htmlResponse.body).toContain("ChatMod Mobile Overlay");

    const stateResponse = await app.inject({
      method: "GET",
      url: `${publicPath}/state`
    });
    expect(stateResponse.statusCode).toBe(200);
    expect(stateResponse.json()).toMatchObject({
      enabled: true,
      status: "live",
      session: {
        id: "overlay-session-1",
        title: "OBS launch test"
      },
      metrics: {
        messages: 1,
        uniqueChatters: 1,
        moderationActions: 1,
        destructiveActions: 1,
        spamAttempts: 1,
        commandsSent: 1
      },
      recentActions: [
        {
          id: "overlay-action-1",
          actionType: "deleteMessage",
          label: "Deleted",
          severity: "warning"
        }
      ],
      recentMessages: []
    });

    const rotateResponse = await app.inject({
      method: "POST",
      url: "/overlays/profiles/profile-overlay-1/rotate-token",
      headers: { authorization }
    });
    expect(rotateResponse.statusCode).toBe(200);
    expect(rotateResponse.json().publicPath).not.toBe(publicPath);

    const oldStateResponse = await app.inject({
      method: "GET",
      url: `${publicPath}/state`
    });
    expect(oldStateResponse.statusCode).toBe(404);

    await app.close();
  });

  it("manages team moderator invites with plan seat limits and redeemable memberships", async () => {
    const starterApp = await buildApp();
    const starterAuthorization = await createAuthorization(starterApp, "team-starter-device", "team-starter-install");
    const blockedInviteResponse = await starterApp.inject({
      method: "POST",
      url: "/team/profiles/team-profile-starter/invites",
      headers: { authorization: starterAuthorization },
      payload: {
        displayName: "Helper mod"
      }
    });
    expect(blockedInviteResponse.statusCode).toBe(403);
    expect(blockedInviteResponse.json().message).toContain("Team moderator seat limit reached");
    await starterApp.close();

    const app = await buildApp({
      entitlementStore: fakeEntitlementStore(snapshotForPlan({
        plan: "pro",
        status: "active",
        source: "test",
        productId: "chatmod_pro_monthly",
        currentPeriodEndsAt: new Date("2026-07-11T10:00:00.000Z")
      }))
    });
    const ownerAuthorization = await createAuthorization(app, "team-owner-device", "team-owner-install");
    const moderatorAuthorization = await createAuthorization(app, "team-mod-device", "team-mod-install");

    const inviteResponse = await app.inject({
      method: "POST",
      url: "/team/profiles/team-profile-1/invites",
      headers: { authorization: ownerAuthorization },
      payload: {
        displayName: "Stream helper",
        permissions: {
          viewQueue: true,
          moderate: true,
          manageWarnings: true,
          viewAnalytics: false
        }
      }
    });
    expect(inviteResponse.statusCode).toBe(201);
    expect(inviteResponse.json()).toMatchObject({
      member: {
        profileId: "team-profile-1",
        displayName: "Stream helper",
        role: "moderator",
        status: "invited",
        memberDeviceId: null,
        permissions: {
          moderate: true,
          viewAnalytics: false
        }
      },
      inviteCode: expect.stringMatching(/^cmt_/)
    });
    const inviteCode = inviteResponse.json<{ inviteCode: string }>().inviteCode;
    const memberId = inviteResponse.json<{ member: { id: string } }>().member.id;

    const secondInviteResponse = await app.inject({
      method: "POST",
      url: "/team/profiles/team-profile-1/invites",
      headers: { authorization: ownerAuthorization },
      payload: {
        displayName: "Second helper"
      }
    });
    expect(secondInviteResponse.statusCode).toBe(403);

    const redeemResponse = await app.inject({
      method: "POST",
      url: "/team/invites/redeem",
      headers: { authorization: moderatorAuthorization },
      payload: {
        inviteCode,
        displayName: "Mobile helper"
      }
    });
    expect(redeemResponse.statusCode).toBe(200);
    expect(redeemResponse.json()).toMatchObject({
      membership: {
        id: memberId,
        profileId: "team-profile-1",
        displayName: "Mobile helper",
        status: "active",
        memberDeviceId: "team-mod-device",
        profileName: "Team profile",
        channelId: "team-profile-1"
      }
    });

    const membershipsResponse = await app.inject({
      method: "GET",
      url: "/team/memberships",
      headers: { authorization: moderatorAuthorization }
    });
    expect(membershipsResponse.statusCode).toBe(200);
    expect(membershipsResponse.json()).toMatchObject({
      memberships: [
        {
          id: memberId,
          profileId: "team-profile-1",
          status: "active"
        }
      ]
    });

    const ownerListResponse = await app.inject({
      method: "GET",
      url: "/team/profiles/team-profile-1/members",
      headers: { authorization: ownerAuthorization }
    });
    expect(ownerListResponse.statusCode).toBe(200);
    expect(ownerListResponse.json()).toMatchObject({
      teamSeats: 2,
      extraSeats: 1,
      members: [
        {
          id: memberId,
          status: "active",
          memberDeviceId: "team-mod-device"
        }
      ]
    });

    const revokeResponse = await app.inject({
      method: "DELETE",
      url: `/team/profiles/team-profile-1/members/${memberId}`,
      headers: { authorization: ownerAuthorization }
    });
    expect(revokeResponse.statusCode).toBe(200);
    expect(revokeResponse.json()).toMatchObject({
      member: {
        id: memberId,
        status: "revoked",
        revokedAt: expect.any(String)
      }
    });

    const revokedMembershipsResponse = await app.inject({
      method: "GET",
      url: "/team/memberships",
      headers: { authorization: moderatorAuthorization }
    });
    expect(revokedMembershipsResponse.statusCode).toBe(200);
    expect(revokedMembershipsResponse.json()).toMatchObject({
      memberships: []
    });

    await app.close();
  });

  it("summarizes cross-stream audit analytics without a paid analytics service", async () => {
    const app = await buildApp();
    const authorization = await createAuthorization(app, "analytics-stream-device-1", "analytics-stream-install-1");
    const otherAuthorization = await createAuthorization(app, "analytics-stream-device-2", "analytics-stream-install-2");

    await app.inject({
      method: "PUT",
      url: "/stream-sessions/analytics-session-1",
      headers: { authorization },
      payload: {
        profileId: "profile-analytics",
        videoId: "video-analytics-1",
        liveChatId: "live-chat-analytics-1",
        title: "Analytics stream one",
        startedAt: "2026-06-07T18:00:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "message-a1",
        authorChannelId: "viewer-a",
        authorName: "Viewer A",
        text: "cheap views now",
        receivedAt: "2026-06-07T18:01:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/messages",
      headers: { authorization },
      payload: {
        youtubeMessageId: "message-a2",
        authorChannelId: "viewer-b",
        authorName: "Viewer B",
        text: "hello",
        receivedAt: "2026-06-07T18:02:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/actions",
      headers: { authorization },
      payload: {
        clientActionId: "analytics-action-1",
        youtubeMessageId: "message-a1",
        authorChannelId: "viewer-a",
        actionType: "deleteMessage",
        reason: "blocked_term:cheap views",
        confidence: 0.95,
        metadata: {
          rulePresetId: "preset-gaming",
          rulePresetName: "Gaming default",
          rulePresetVersion: "1001"
        }
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/actions",
      headers: { authorization },
      payload: {
        clientActionId: "analytics-action-2",
        youtubeMessageId: "message-a2",
        authorChannelId: "viewer-b",
        actionType: "flagForReview",
        reason: "caps",
        confidence: 0.45,
        metadata: {
          localMetadataJson: JSON.stringify({
            rulePresetId: "preset-gaming",
            rulePresetName: "Gaming default",
            rulePresetVersion: "1001"
          })
        }
      }
    });
    await app.inject({
      method: "PATCH",
      url: "/stream-sessions/analytics-session-1/actions/analytics-action-2/review",
      headers: { authorization },
      payload: {
        reviewStatus: "false_positive",
        reviewNote: "Allowed chant"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/runtime-events",
      headers: { authorization },
      payload: {
        type: "command_sent",
        message: "Sent !rules",
        metadata: {
          commandId: "cmd-rules",
          trigger: "!rules"
        }
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/runtime-events",
      headers: { authorization },
      payload: {
        type: "runtime_reconnect_scheduled",
        message: "Reconnect backoff"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-1/end",
      headers: { authorization },
      payload: {
        endedAt: "2026-06-07T18:30:00.000Z"
      }
    });

    await app.inject({
      method: "PUT",
      url: "/stream-sessions/analytics-session-2",
      headers: { authorization },
      payload: {
        profileId: "profile-analytics",
        videoId: "video-analytics-2",
        liveChatId: "live-chat-analytics-2",
        title: "Analytics stream two",
        startedAt: "2026-06-08T20:00:00.000Z"
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-2/actions",
      headers: { authorization },
      payload: {
        youtubeMessageId: null,
        authorChannelId: "viewer-c",
        actionType: "unbanUser",
        reason: "manual_profile_unban",
        confidence: null
      }
    });
    await app.inject({
      method: "POST",
      url: "/stream-sessions/analytics-session-2/runtime-events",
      headers: { authorization },
      payload: {
        type: "runtime_session_summary",
        message: "Runtime session summary",
        metadata: {
          localMetadataJson: JSON.stringify({
            durationMillis: 900000,
            commandsUsedJson: JSON.stringify({ "cmd-uptime": 2 }),
            timersUsedJson: JSON.stringify({ "timer-promo": 1 })
          })
        }
      }
    });

    const response = await app.inject({
      method: "GET",
      url: "/stream-sessions/analytics/summary?profileId=profile-analytics&days=365",
      headers: { authorization }
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      rangeDays: 365,
      sessionCount: 2,
      totalMessages: 2,
      totalModerationActions: 3,
      reconnectEvents: 1,
      byStream: [
        {
          sessionId: "analytics-session-2",
          messageCount: 0,
          moderationActionCount: 1,
          spamAttemptCount: 0,
          commandCount: 2,
          timerCount: 1,
          uptimeMillis: 900000
        },
        {
          sessionId: "analytics-session-1",
          messageCount: 2,
          uniqueChatters: 2,
          moderationActionCount: 2,
          destructiveActionCount: 1,
          spamAttemptCount: 1,
          commandCount: 1,
          reconnectEvents: 1,
          uptimeMillis: 1800000
        }
      ],
      byDay: [
        {
          day: "2026-06-07",
          streamCount: 1,
          messageCount: 2,
          moderationActionCount: 2,
          spamAttemptCount: 1,
          reconnectEvents: 1,
          uptimeMillis: 1800000
        },
        {
          day: "2026-06-08",
          streamCount: 1,
          moderationActionCount: 1,
          spamAttemptCount: 0,
          uptimeMillis: 900000
        }
      ],
      commandUsage: [
        {
          commandId: "cmd-uptime",
          count: 2
        },
        {
          commandId: "cmd-rules",
          trigger: "!rules",
          count: 1
        }
      ],
      ruleEffectiveness: [
        {
          rule: "blocked_term",
          matchCount: 1,
          destructiveActionCount: 1,
          falsePositiveCount: 0
        },
        {
          rule: "caps",
          matchCount: 1,
          destructiveActionCount: 0,
          falsePositiveCount: 1
        }
      ],
      ruleEffectivenessByPreset: [
        {
          presetId: "preset-gaming",
          presetName: "Gaming default",
          presetVersion: "1001",
          rule: "blocked_term",
          matchCount: 1,
          destructiveActionCount: 1,
          falsePositiveCount: 0
        },
        {
          presetId: "preset-gaming",
          presetName: "Gaming default",
          presetVersion: "1001",
          rule: "caps",
          matchCount: 1,
          destructiveActionCount: 0,
          falsePositiveCount: 1
        }
      ],
      spamAttemptsByDay: [
        {
          day: "2026-06-07",
          count: 1
        },
        {
          day: "2026-06-08",
          count: 0
        }
      ]
    });

    const otherResponse = await app.inject({
      method: "GET",
      url: "/stream-sessions/analytics/summary?profileId=profile-analytics&days=365",
      headers: { authorization: otherAuthorization }
    });
    expect(otherResponse.statusCode).toBe(200);
    expect(otherResponse.json()).toMatchObject({
      sessionCount: 0,
      totalMessages: 0,
      totalModerationActions: 0
    });

    await app.close();
  });
});

async function createAuthorization(
  app: Awaited<ReturnType<typeof buildApp>>,
  deviceId = "test-device-123",
  installId = "test-install-123"
): Promise<string> {
  const response = await app.inject({
    method: "POST",
    url: "/accounts/device-session",
    payload: {
      deviceId,
      installId,
      appVersion: "0.1.0"
    }
  });

  const body = response.json<{ accessToken: string }>();
  return `Bearer ${body.accessToken}`;
}

function fakeEntitlementStore(snapshot: EntitlementSnapshot): EntitlementStore {
  return {
    async current() {
      return snapshot;
    },
    async upsert(_auth: AuthContext, _update: EntitlementUpdate) {
      return snapshot;
    }
  };
}
