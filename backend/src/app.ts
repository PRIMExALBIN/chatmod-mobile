import cors from "@fastify/cors";
import rateLimit from "@fastify/rate-limit";
import swagger from "@fastify/swagger";
import swaggerUi from "@fastify/swagger-ui";
import Fastify, { type FastifyInstance } from "fastify";
import { accountRoutes } from "./modules/accounts/accounts.routes.js";
import { adminRoutes } from "./modules/admin/admin.routes.js";
import { analyticsRoutes } from "./modules/analytics/analytics.routes.js";
import { appCompatibilityRoutes } from "./modules/app/appCompatibility.routes.js";
import { backupRoutes } from "./modules/backups/backups.routes.js";
import { createBackupStore } from "./modules/backups/backupStore.js";
import { commandRoutes } from "./modules/commands/commands.routes.js";
import { createCommandStore } from "./modules/commands/commandStore.js";
import { entitlementRoutes } from "./modules/entitlements/entitlements.routes.js";
import { createEntitlementStore } from "./modules/entitlements/entitlementStore.js";
import { faqRoutes } from "./modules/faq/faq.routes.js";
import { feedbackRoutes } from "./modules/feedback/feedback.routes.js";
import { healthRoutes } from "./modules/health/health.routes.js";
import { discordRoutes } from "./modules/integrations/discord.routes.js";
import { createDiscordWebhookStore } from "./modules/integrations/discordWebhookStore.js";
import type { DiscordWebhookSender } from "./modules/integrations/discordWebhookSender.js";
import { logRoutes } from "./modules/logs/logs.routes.js";
import { createApiErrorStore } from "./modules/logs/apiErrorStore.js";
import { createLogStore } from "./modules/logs/logStore.js";
import { moderationRoutes } from "./modules/moderation/moderation.routes.js";
import { createAiSuggestionUsageStore, type AiSuggestionUsageStore } from "./modules/moderation/aiSuggestionUsageStore.js";
import { overlayRoutes } from "./modules/overlays/overlays.routes.js";
import { createOverlayConfigStore } from "./modules/overlays/overlayConfigStore.js";
import { profileRoutes } from "./modules/profiles/profiles.routes.js";
import { createProfileStore } from "./modules/profiles/profileStore.js";
import { rulePresetRoutes } from "./modules/rulePresets/rulePresets.routes.js";
import { createStreamSessionStore } from "./modules/streamSessions/streamSessionStore.js";
import { streamSessionRoutes } from "./modules/streamSessions/streamSessions.routes.js";
import { teamRoutes } from "./modules/team/team.routes.js";
import { createTeamAccessStore } from "./modules/team/teamAccessStore.js";
import { timerRoutes } from "./modules/timers/timers.routes.js";
import { createTimerStore } from "./modules/timers/timerStore.js";
import type { AuthContext } from "./modules/auth/sessionToken.js";
import type { EntitlementStore } from "./modules/entitlements/entitlementStore.js";
import { userProfileRoutes } from "./modules/userProfiles/userProfiles.routes.js";
import { youtubeRoutes } from "./modules/youtube/youtube.routes.js";
import type { YouTubeLinkedAccountStatus } from "./modules/youtube/youtubeTokenStore.js";
import "./plugins/auth.js";
import { registerErrorHandler } from "./plugins/errorHandler.js";
import { registerSecurityHeaders } from "./plugins/securityHeaders.js";
import { env } from "./config/env.js";

export interface BuildAppOptions {
  logger?: boolean;
  adminApiKey?: string | null;
  youtubeAccountStatusProvider?: (auth: AuthContext) => Promise<YouTubeLinkedAccountStatus>;
  youtubeOAuthConfigured?: boolean;
  discordWebhookSender?: DiscordWebhookSender;
  entitlementStore?: EntitlementStore;
  aiSuggestionUsageStore?: AiSuggestionUsageStore;
}

export async function buildApp(options: BuildAppOptions = {}): Promise<FastifyInstance> {
  const app = Fastify({
    logger: options.logger ?? false
  });

  await app.register(cors, {
    origin: env.NODE_ENV === "production" ? env.CORS_ORIGIN : true,
    credentials: true
  });

  await app.register(rateLimit, {
    max: 240,
    timeWindow: "1 minute"
  });

  await app.register(swagger, {
    openapi: {
      info: {
        title: "ChatMod Mobile API",
        description: "Backend API for a phone-hosted YouTube Live moderation bot.",
        version: "0.1.0"
      }
    }
  });

  await app.register(swaggerUi, {
    routePrefix: "/docs"
  });

  const commandStore = createCommandStore();
  const timerStore = createTimerStore();
  const backupStore = createBackupStore();
  const entitlementStore = options.entitlementStore ?? createEntitlementStore();
  const aiSuggestionUsageStore = options.aiSuggestionUsageStore ?? createAiSuggestionUsageStore();
  const profileStore = createProfileStore();
  const streamSessionStore = createStreamSessionStore();
  const overlayConfigStore = createOverlayConfigStore();
  const teamAccessStore = createTeamAccessStore();
  const apiErrorStore = createApiErrorStore();
  const supportStore = createLogStore();
  const discordWebhookStore = createDiscordWebhookStore();
  registerErrorHandler(app, { apiErrorStore });
  registerSecurityHeaders(app);
  app.decorateRequest("auth", null);

  await app.register(healthRoutes, { prefix: "/health" });
  await app.register(appCompatibilityRoutes, { prefix: "/app" });
  await app.register(accountRoutes, { prefix: "/accounts" });
  await app.register(entitlementRoutes, { prefix: "/entitlements", store: entitlementStore });
  await app.register(profileRoutes, { prefix: "/profiles", store: profileStore, entitlementStore });
  await app.register(backupRoutes, { prefix: "/backups", store: backupStore, commandStore, timerStore, profileStore, entitlementStore });
  await app.register(commandRoutes, { prefix: "/commands", store: commandStore, profileStore, entitlementStore });
  await app.register(faqRoutes, { prefix: "/faq-entries", profileStore, entitlementStore });
  await app.register(timerRoutes, { prefix: "/timers", store: timerStore, profileStore, entitlementStore });
  await app.register(userProfileRoutes, { prefix: "/user-profiles" });
  await app.register(rulePresetRoutes, { prefix: "/rule-presets", profileStore, entitlementStore });
  await app.register(streamSessionRoutes, { prefix: "/stream-sessions", store: streamSessionStore, entitlementStore });
  await app.register(overlayRoutes, {
    prefix: "/overlays",
    store: overlayConfigStore,
    streamSessionStore,
    entitlementStore,
    profileStore
  });
  await app.register(teamRoutes, { prefix: "/team", store: teamAccessStore, entitlementStore, profileStore });
  await app.register(logRoutes, { prefix: "/logs", apiErrorStore, supportStore });
  await app.register(analyticsRoutes, { prefix: "/analytics", store: supportStore });
  await app.register(feedbackRoutes, { prefix: "/feedback", store: supportStore });
  await app.register(discordRoutes, {
    prefix: "/integrations/discord",
    store: discordWebhookStore,
    sender: options.discordWebhookSender,
    entitlementStore,
    profileStore
  });
  await app.register(youtubeRoutes, {
    prefix: "/youtube",
    accountStatusProvider: options.youtubeAccountStatusProvider,
    oauthConfigured: options.youtubeOAuthConfigured
  });
  await app.register(moderationRoutes, { prefix: "/moderation", entitlementStore, aiSuggestionUsageStore });
  const adminApiKey = options.adminApiKey ?? env.ADMIN_API_KEY;
  if (adminApiKey) {
    await app.register(adminRoutes, {
      prefix: "/admin",
      adminApiKey,
      entitlementStore,
      supportStore,
      apiErrorStore
    });
  }

  return app;
}
