import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const root = resolve(process.cwd());
const failures = [];
const warnings = [];
const passes = [];

const YAML = await loadYaml();

function pass(message) {
  passes.push(message);
}

function warn(message) {
  warnings.push(message);
}

function fail(message) {
  failures.push(message);
}

function assert(condition, message) {
  if (condition) {
    pass(message);
  } else {
    fail(message);
  }
}

function includesEvery(text, phrases, label) {
  for (const phrase of phrases) {
    assert(text.includes(phrase), `${label} includes ${phrase}`);
  }
}

function filePath(...parts) {
  return join(root, ...parts);
}

function readText(...parts) {
  return readFileSync(filePath(...parts), "utf8");
}

function readJson(...parts) {
  return JSON.parse(readText(...parts));
}

function fileExists(...parts) {
  return existsSync(filePath(...parts));
}

async function loadYaml() {
  try {
    return await import("yaml");
  } catch {
    fail("The production readiness check needs the `yaml` package available from npm install.");
    return null;
  }
}

function parseYaml(path, label) {
  if (!YAML) {
    return null;
  }
  try {
    const parsed = YAML.parse(readText(path));
    pass(`${label} parses as YAML`);
    return parsed;
  } catch (error) {
    fail(`${label} does not parse as YAML: ${error.message}`);
    return null;
  }
}

function parseEnvExample() {
  const env = {};
  for (const line of readText(".env.example").split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) {
      continue;
    }
    const equals = trimmed.indexOf("=");
    if (equals <= 0) {
      continue;
    }
    env[trimmed.slice(0, equals)] = trimmed.slice(equals + 1);
  }
  return env;
}

function findEnvVar(envVars, key) {
  return envVars.find((entry) => entry?.key === key);
}

function checkRootScripts() {
  const packageJson = readJson("package.json");
  const scripts = packageJson.scripts ?? {};
  const requiredScripts = [
    "test",
    "build",
    "backend:db:validate",
    "backend:db:deploy",
    "backend:env:check",
    "backend:retention:prune",
    "backend:smoke",
    "android:wrapper:check",
    "android:data:check",
    "android:di:check",
    "android:ux:check",
    "android:device:qa",
    "android:release:check",
    "launch-site:check",
    "promo-video:check",
    "release:evidence:check",
    "store:check",
    "production:check"
  ];

  for (const script of requiredScripts) {
    assert(Boolean(scripts[script]), `package.json exposes npm run ${script}`);
  }

  const backendPackageJson = readJson("backend/package.json");
  const backendScripts = backendPackageJson.scripts ?? {};
  assert(Boolean(backendScripts["retention:prune"]), "backend/package.json exposes npm run retention:prune");
  assert(Boolean(backendScripts["retention:prune:dev"]), "backend/package.json exposes npm run retention:prune:dev");
  assert(Boolean(backendScripts["env:check"]), "backend/package.json exposes npm run env:check");
  assert(Boolean(backendScripts["env:check:dev"]), "backend/package.json exposes npm run env:check:dev");
  assert(scripts["backend:env:check"]?.includes("run env:check --"), "Root env check script forwards CLI flags");
  assert(scripts["backend:retention:prune"]?.includes("run retention:prune --"), "Root retention prune script forwards CLI flags");
  assert(scripts["backend:retention:prune:dev"]?.includes("run retention:prune:dev --"), "Root retention dev script forwards CLI flags");
}

function checkRequiredFiles() {
  const requiredFiles = [
    "render.yaml",
    ".env.example",
    ".dockerignore",
    "backend/Dockerfile",
    "scripts/backend-smoke.mjs",
    "scripts/android-wrapper-check.mjs",
    "scripts/android-local-data-check.mjs",
    "scripts/android-di-check.mjs",
    "scripts/android-ux-check.mjs",
    "scripts/android-device-qa.mjs",
    "scripts/android-release-artifact-check.mjs",
    "scripts/launch-site-check.mjs",
    "scripts/promo-video-check.mjs",
    "scripts/release-evidence-check.mjs",
    "scripts/store-readiness-check.mjs",
    "backend/prisma/schema.prisma",
    "android/gradlew",
    "android/gradlew.bat",
    "android/gradle/wrapper/gradle-wrapper.jar",
    "android/gradle/wrapper/gradle-wrapper.properties",
    "shared/contracts/openapi.yaml",
    "docs/DEPLOYMENT.md",
    "docs/DATA_RETENTION.md",
    "docs/DEPLOYMENT_SMOKE_TESTS.md",
    "docs/PRODUCTION_ENV_PREFLIGHT.md",
    "docs/FREE_TIER_STACK.md",
    "docs/ANDROID_LOCAL_DATA.md",
    "docs/ANDROID_DEPENDENCY_INJECTION.md",
    "docs/ANDROID_UX_ACCESSIBILITY.md",
    "docs/ANDROID_DEVICE_QA.md",
    "docs/ANDROID_RELEASE_SIGNING.md",
    "docs/AI_ASSISTED_MODERATION.md",
    "docs/OBS_BROWSER_OVERLAY.md",
    "docs/TEAM_ACCESS.md",
    "docs/WEB_DASHBOARD.md",
    "docs/TUTORIAL_VIDEO.md",
    "docs/EXTERNAL_RELEASE_EVIDENCE.md",
    "docs/release-evidence.template.json",
    "docs/PLAY_STORE_LISTING.md",
    "docs/PLAY_DATA_SAFETY.md",
    "docs/PRODUCTION_RELEASE_CHECKLIST.md",
    "chatmod-mobile-promo/index.html",
    "chatmod-mobile-promo/DESIGN.md",
    "chatmod-mobile-promo/STORYBOARD.md",
    "chatmod-mobile-promo/SCRIPT.md",
    "chatmod-mobile-promo/narration.txt",
    "chatmod-mobile-promo/package.json",
    "chatmod-mobile-promo/package-lock.json",
    "chatmod-mobile-promo/renders/chatmod-mobile-tutorial.mp4",
    ".github/workflows/ci.yml"
  ];

  for (const file of requiredFiles) {
    assert(fileExists(file), `${file} exists`);
  }
}

function checkRenderBlueprint() {
  const render = parseYaml("render.yaml", "render.yaml");
  if (!render) {
    return;
  }

  const services = Array.isArray(render.services) ? render.services : [];
  const service = services.find((candidate) => candidate?.name === "chatmod-backend");
  assert(Boolean(service), "render.yaml defines the chatmod-backend service");
  if (!service) {
    return;
  }

  assert(service.type === "web", "Render service is a web service");
  assert(service.runtime === "docker", "Render service uses the Docker runtime");
  assert(service.plan === "free", "Render service stays on the free plan for beta");
  assert(service.dockerfilePath === "./backend/Dockerfile", "Render service points at backend/Dockerfile");
  assert(service.dockerContext === ".", "Render Docker context is the repository root");
  assert(service.healthCheckPath === "/health/ready", "Render health check uses /health/ready");
  assert(!render.databases || render.databases.length === 0, "render.yaml does not create expiring Render Free Postgres");

  const envVars = Array.isArray(service.envVars) ? service.envVars : [];
  const fixedEnv = {
    NODE_ENV: "production",
    HOST: "0.0.0.0",
    PORT: 4100,
    JWT_ISSUER: "chatmod-mobile",
    JWT_AUDIENCE: "chatmod-mobile"
  };
  for (const [key, value] of Object.entries(fixedEnv)) {
    const entry = findEnvVar(envVars, key);
    assert(entry?.value === value, `Render ${key} is fixed to ${value}`);
  }

  const secretEnv = [
    "DATABASE_URL",
    "CORS_ORIGIN",
    "JWT_SECRET",
    "SECRET_ENCRYPTION_KEYS",
    "GOOGLE_OAUTH_CLIENT_ID",
    "GOOGLE_OAUTH_CLIENT_SECRET",
    "GOOGLE_OAUTH_REDIRECT_URI",
    "GOOGLE_PLAY_PACKAGE_NAME",
    "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64"
  ];
  for (const key of secretEnv) {
    const entry = findEnvVar(envVars, key);
    assert(entry?.sync === false, `Render ${key} is supplied through encrypted host env`);
    if (entry && "value" in entry) {
      fail(`Render ${key} must not have an inline value`);
    }
  }
}

function checkEnvExample() {
  const env = parseEnvExample();
  const requiredEnv = [
    "NODE_ENV",
    "PORT",
    "HOST",
    "DATABASE_URL",
    "CORS_ORIGIN",
    "JWT_SECRET",
    "SECRET_ENCRYPTION_KEYS",
    "GOOGLE_OAUTH_CLIENT_ID",
    "GOOGLE_OAUTH_CLIENT_SECRET",
    "GOOGLE_OAUTH_REDIRECT_URI",
    "GOOGLE_PLAY_PACKAGE_NAME",
    "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64",
    "ANDROID_MIN_SUPPORTED_VERSION_CODE",
    "ANDROID_MIN_SUPPORTED_VERSION_NAME",
    "ANDROID_LATEST_VERSION_CODE",
    "ANDROID_LATEST_VERSION_NAME",
    "ANDROID_UPDATE_URL",
    "SUPPORT_EVENT_RETENTION_DAYS",
    "API_ERROR_RETENTION_DAYS",
    "STREAM_LOG_RETENTION_DAYS",
    "BACKUP_VERSIONS_PER_PROFILE"
  ];

  for (const key of requiredEnv) {
    assert(Object.prototype.hasOwnProperty.call(env, key), `.env.example documents ${key}`);
  }

  if (env.NODE_ENV === "production") {
    fail(".env.example should not default local developers into production mode");
  } else {
    pass(".env.example defaults away from production mode");
  }

  const jwtSecret = env.JWT_SECRET ?? "";
  if (jwtSecret.length < 32 || jwtSecret.includes("replace")) {
    warn(".env.example uses a placeholder JWT_SECRET; production hosts must set a real 32+ character secret.");
  } else {
    pass(".env.example JWT_SECRET placeholder is long enough for local schema parsing");
  }
}

function checkDockerAndIgnoreFiles() {
  const dockerfile = readText("backend/Dockerfile");
  assert(dockerfile.includes("FROM node:24-alpine"), "Dockerfile pins a Node 24 Alpine base");
  assert(dockerfile.includes("npm run backend:build"), "Dockerfile builds the backend TypeScript output");
  assert(dockerfile.includes("prisma:generate"), "Dockerfile generates Prisma client during build");
  assert(dockerfile.includes("ENV NODE_ENV=production"), "Dockerfile runtime stage sets NODE_ENV=production");
  assert(dockerfile.includes("EXPOSE 4100"), "Dockerfile exposes the backend port");
  assert(dockerfile.includes('CMD ["node", "backend/dist/main.js"]'), "Dockerfile starts the compiled backend entrypoint");

  const dockerignore = readText(".dockerignore");
  assert(dockerignore.includes("node_modules"), ".dockerignore excludes node_modules");
  assert(dockerignore.includes(".env"), ".dockerignore excludes env files");
  assert(dockerignore.includes("backend/dist"), ".dockerignore excludes generated backend dist");

  const gitignore = readText(".gitignore");
  assert(gitignore.includes("*.jks"), ".gitignore excludes Android JKS keystores");
  assert(gitignore.includes("*.keystore"), ".gitignore excludes Android keystore files");
  assert(gitignore.includes("*.p12"), ".gitignore excludes PKCS12 keystores");
  assert(gitignore.includes("*.pfx"), ".gitignore excludes PFX keystores");
}

function checkAndroidBuildSource() {
  const rootBuild = readText("android/build.gradle.kts");
  const appBuild = readText("android/app/build.gradle.kts");
  const gradleProperties = readText("android/gradle.properties");
  const settings = readText("android/settings.gradle.kts");
  const wrapperProperties = readText("android/gradle/wrapper/gradle-wrapper.properties");

  assert(settings.includes("google()"), "Android settings include the Google Maven repository");
  assert(fileExists("android/gradlew"), "Android project includes a Gradle wrapper shell script");
  assert(fileExists("android/gradlew.bat"), "Android project includes a Gradle wrapper batch script");
  assert(fileExists("android/gradle/wrapper/gradle-wrapper.jar"), "Android project includes the Gradle wrapper jar");
  assert(wrapperProperties.includes("gradle-8.10.2-bin.zip"), "Android Gradle wrapper pins Gradle 8.10.2");
  assert(
    wrapperProperties.includes("distributionSha256Sum=31c55713e40233a8303827ceb42ca48a47267a0ad4bab9177123121e71524c26"),
    "Android Gradle wrapper verifies the Gradle 8.10.2 distribution checksum"
  );
  assert(rootBuild.includes('id("com.android.application") version "8.7.3" apply false'), "Android root build pins the Android Gradle plugin");
  assert(rootBuild.includes('id("org.jetbrains.kotlin.android") version "2.0.21" apply false'), "Android root build pins the Kotlin Android plugin");
  assert(rootBuild.includes('id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false'), "Android root build pins the Kotlin Compose compiler plugin");
  assert(rootBuild.includes('id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false'), "Android root build pins KSP for Room");
  assert(appBuild.includes('id("org.jetbrains.kotlin.plugin.compose")'), "Android app applies the Kotlin Compose compiler plugin");
  assert(appBuild.includes("CHATMOD_RELEASE_STORE_FILE"), "Android release signing reads keystore path from env");
  assert(appBuild.includes("CHATMOD_RELEASE_STORE_PASSWORD"), "Android release signing reads store password from env");
  assert(appBuild.includes("CHATMOD_RELEASE_KEY_ALIAS"), "Android release signing reads key alias from env");
  assert(appBuild.includes("CHATMOD_RELEASE_KEY_PASSWORD"), "Android release signing reads key password from env");
  assert(appBuild.includes('create("externalRelease")'), "Android app defines an external release signing config");
  assert(appBuild.includes("verifyReleaseSigningConfigured"), "Android app exposes a release signing verification task");
  assert(appBuild.includes("compileSdk = 35"), "Android app targets compileSdk 35");
  assert(appBuild.includes("targetSdk = 35"), "Android app targets SDK 35");
  assert(appBuild.includes('create("closedBeta")'), "Android app defines the closedBeta flavor");
  assert(appBuild.includes('applicationIdSuffix = ".beta"'), "Closed beta flavor installs beside production");
  assert(appBuild.includes("room.schemaLocation"), "Room schema generation is configured");
  assert(appBuild.includes("buildConfig = true"), "Android app enables BuildConfig through the module DSL");
  assert(!gradleProperties.includes("android.defaults.buildfeatures.buildconfig"), "Android Gradle properties avoid deprecated global BuildConfig flag");
}

function checkOpenApiContract() {
  const openapi = parseYaml("shared/contracts/openapi.yaml", "OpenAPI contract");
  if (!openapi) {
    return;
  }

  const requiredPaths = [
    "/health/ready",
    "/accounts/device-session",
    "/entitlements/current",
    "/youtube/connect-url",
    "/youtube/live-chat/discover",
    "/moderation/suggestions/evaluate",
    "/faq-entries",
    "/faq-entries/suggest-reply",
    "/stream-sessions/{id}/ai-summary",
    "/overlays/profiles/{profileId}",
    "/overlays/profiles/{profileId}/rotate-token",
    "/overlays/public/{token}",
    "/overlays/public/{token}/state",
    "/team/profiles/{profileId}/members",
    "/team/profiles/{profileId}/invites",
    "/team/profiles/{profileId}/members/{memberId}",
    "/team/invites/redeem",
    "/team/memberships",
    "/rule-presets/export",
    "/rule-presets/import",
    "/integrations/discord/webhook",
    "/integrations/discord/test",
    "/integrations/discord/alerts",
    "/feedback/beta",
    "/feedback/beta-interest"
  ];
  for (const path of requiredPaths) {
    assert(Boolean(openapi.paths?.[path]), `OpenAPI documents ${path}`);
  }

  const entitlementFeatures = openapi.components?.schemas?.EntitlementSnapshot?.properties?.features;
  assert(
    entitlementFeatures?.properties?.discordAlerts?.type === "boolean",
    "OpenAPI entitlement features include discordAlerts"
  );
  assert(
    entitlementFeatures?.properties?.obsOverlay?.type === "boolean",
    "OpenAPI entitlement features include obsOverlay"
  );
  assert(
    entitlementFeatures?.properties?.teamSeats?.type === "integer",
    "OpenAPI entitlement features include teamSeats"
  );
  assert(
    entitlementFeatures?.properties?.localHistoryLimit?.type === "integer",
    "OpenAPI entitlement features include localHistoryLimit"
  );
  assert(
    entitlementFeatures?.properties?.presetBundles?.type === "boolean",
    "OpenAPI entitlement features include presetBundles"
  );
  assert(
    entitlementFeatures?.properties?.aiSuggestions?.type === "boolean",
    "OpenAPI entitlement features include aiSuggestions"
  );
  assert(
    entitlementFeatures?.properties?.aiSuggestionDailyLimit?.type === "integer",
    "OpenAPI entitlement features include aiSuggestionDailyLimit"
  );
  assert(
    openapi.components?.schemas?.ModerationSuggestionResult?.properties?.usage?.["$ref"] === "#/components/schemas/ModerationSuggestionUsage",
    "OpenAPI moderation suggestions include usage counters"
  );
  assert(
    Boolean(openapi.paths?.["/moderation/suggestions/evaluate"]?.post?.responses?.["429"]),
    "OpenAPI documents AI suggestion daily limit response"
  );
  assert(
    openapi.components?.schemas?.StreamChatSummary?.properties?.provider?.enum?.includes("local-heuristic"),
    "OpenAPI documents local after-stream chat summaries"
  );
  assert(
    openapi.components?.schemas?.FaqReplySuggestionResult?.properties?.manualApprovalRequired?.enum?.includes(true),
    "OpenAPI documents manual FAQ reply suggestions"
  );
  assert(
    openapi.components?.schemas?.OverlayState?.properties?.metrics?.properties?.spamAttempts?.type === "integer",
    "OpenAPI documents OBS overlay state metrics"
  );
  assert(
    Boolean(openapi.components?.schemas?.TeamMember),
    "OpenAPI documents team member records"
  );
  assert(
    Boolean(openapi.components?.schemas?.TeamInviteResult),
    "OpenAPI documents team invite results"
  );
  assert(
    Boolean(openapi.components?.schemas?.TeamMembership),
    "OpenAPI documents team memberships"
  );

  const entitlementPlans = readText("backend/src/modules/entitlements/entitlementPlans.ts");
  includesEvery(
    entitlementPlans,
    [
      "localHistoryLimit: number;",
      "aiSuggestionDailyLimit: number;",
      "presetBundles: boolean;",
      "obsOverlay: boolean;",
      "teamSeats: number;",
      "localHistoryLimit: 120",
      "localHistoryLimit: 1000",
      "localHistoryLimit: 2000",
      "aiSuggestionDailyLimit: 0",
      "aiSuggestionDailyLimit: 300",
      "presetBundles: false",
      "presetBundles: true",
      "obsOverlay: false",
      "obsOverlay: true",
      "teamSeats: 1",
      "teamSeats: 2",
      "teamSeats: 5"
    ],
    "Backend entitlement plan local history limits"
  );
}

function checkPrismaMigrations() {
  const schema = readText("backend/prisma/schema.prisma");
  const requiredModels = [
    "model User ",
    "model Device ",
    "model LinkedAccount ",
    "model ChannelProfile ",
    "model DiscordWebhookConfig ",
    "model FaqEntry ",
    "model OverlayConfig ",
    "model TeamMember ",
    "model StreamSession ",
    "model ModerationActionLog ",
    "model UsageCounter "
  ];
  for (const model of requiredModels) {
    assert(schema.includes(model), `Prisma schema includes ${model.trim()}`);
  }

  const migrationsDir = filePath("backend/prisma/migrations");
  const migrations = readdirSync(migrationsDir)
    .filter((name) => statSync(join(migrationsDir, name)).isDirectory())
    .sort();
  assert(migrations.length > 0, "Prisma migrations are checked in");

  for (const migration of migrations) {
    const sqlPath = join(migrationsDir, migration, "migration.sql");
    assert(existsSync(sqlPath), `Migration ${migration} includes migration.sql`);
    if (existsSync(sqlPath)) {
      assert(readFileSync(sqlPath, "utf8").trim().length > 0, `Migration ${migration} is not empty`);
    }
  }

  const discordMigration = migrations.find((migration) => migration.includes("discord_webhook_configs"));
  assert(Boolean(discordMigration), "Discord webhook schema has a checked-in migration");

  const retentionMigration = migrations.find((migration) => migration.includes("retention_indexes"));
  assert(Boolean(retentionMigration), "Retention pruning indexes have a checked-in migration");

  const overlayMigration = migrations.find((migration) => migration.includes("overlay_configs"));
  assert(Boolean(overlayMigration), "OBS overlay config schema has a checked-in migration");

  const teamMigration = migrations.find((migration) => migration.includes("team_members"));
  assert(Boolean(teamMigration), "Team member schema has a checked-in migration");

  assert(schema.includes("@@index([endedAt])"), "Prisma schema indexes stream sessions by endedAt for retention");
  assert(schema.includes("@@index([sessionId, createdAt])"), "Prisma schema indexes stream child rows by sessionId and createdAt");
  assert(schema.includes("@@index([profileId, createdAt])"), "Prisma schema indexes backups by profileId and createdAt");
  assert(schema.includes("@@index([createdAt])"), "Prisma schema includes createdAt indexes for retention pruning");
}

function checkDocsAndCi() {
  const deployment = readText("docs/DEPLOYMENT.md");
  const dataRetention = readText("docs/DATA_RETENTION.md");
  const deploymentSmoke = readText("docs/DEPLOYMENT_SMOKE_TESTS.md");
  const envPreflight = readText("docs/PRODUCTION_ENV_PREFLIGHT.md");
  const androidDi = readText("docs/ANDROID_DEPENDENCY_INJECTION.md");
  const androidUx = readText("docs/ANDROID_UX_ACCESSIBILITY.md");
  const androidDeviceQa = readText("docs/ANDROID_DEVICE_QA.md");
  const androidReleaseSigning = readText("docs/ANDROID_RELEASE_SIGNING.md");
  const aiModeration = readText("docs/AI_ASSISTED_MODERATION.md");
  const obsOverlay = readText("docs/OBS_BROWSER_OVERLAY.md");
  const teamAccess = readText("docs/TEAM_ACCESS.md");
  const webDashboard = readText("docs/WEB_DASHBOARD.md");
  const tutorialVideo = readText("docs/TUTORIAL_VIDEO.md");
  const externalEvidence = readText("docs/EXTERNAL_RELEASE_EVIDENCE.md");
  const evidenceTemplate = readText("docs/release-evidence.template.json");
  const freeTier = readText("docs/FREE_TIER_STACK.md");
  const billingDocs = readText("docs/BILLING.md");
  const pricingDocs = readText("docs/PRICING.md");
  const playListing = readText("docs/PLAY_STORE_LISTING.md");
  const playDataSafety = readText("docs/PLAY_DATA_SAFETY.md");
  const release = readText("docs/PRODUCTION_RELEASE_CHECKLIST.md");
  const ci = readText(".github/workflows/ci.yml");
  const smokeScript = readText("scripts/backend-smoke.mjs");
  const envPreflightSource = readText("backend/src/config/productionEnvPreflight.ts");
  const androidWrapperCheck = readText("scripts/android-wrapper-check.mjs");
  const androidDiCheck = readText("scripts/android-di-check.mjs");
  const androidUxCheck = readText("scripts/android-ux-check.mjs");
  const androidDeviceQaCheck = readText("scripts/android-device-qa.mjs");
  const androidReleaseCheck = readText("scripts/android-release-artifact-check.mjs");
  const backendAppTest = readText("backend/src/app.test.ts");
  const moderationRoutes = readText("backend/src/modules/moderation/moderation.routes.ts");
  const moderationSuggestions = readText("backend/src/modules/moderation/moderationSuggestions.ts");
  const chatAbuseTracker = readText("android/app/src/main/java/com/chatmod/mobile/runtime/ChatAbuseTracker.kt");
  const androidApiClient = readText("android/app/src/main/java/com/chatmod/mobile/data/remote/ChatModApiClient.kt");
  const androidDashboard = readText("android/app/src/main/java/com/chatmod/mobile/ui/dashboard/DashboardScreen.kt");
  const playBillingManager = readText("android/app/src/main/java/com/chatmod/mobile/billing/PlayBillingManager.kt");
  const webAdminHtml = readText("launch-site/admin.html");
  const webAdminScript = readText("launch-site/admin-dashboard.js");
  const launchSiteCheck = readText("scripts/launch-site-check.mjs");
  const storeCheck = readText("scripts/store-readiness-check.mjs");
  const promoVideoCheck = readText("scripts/promo-video-check.mjs");
  const releaseEvidenceCheck = readText("scripts/release-evidence-check.mjs");

  assert(deployment.includes("Neon Free Postgres"), "Deployment docs name Neon Free Postgres");
  assert(deployment.includes("Render Blueprint"), "Deployment docs describe Render Blueprint setup");
  assert(deployment.includes("npm run production:check"), "Deployment docs include the production readiness command");
  assert(deployment.includes("npm run backend:db:deploy"), "Deployment docs include migration deployment");
  assert(deployment.includes("npm run backend:env:check"), "Deployment docs include production env preflight");
  assert(deployment.includes("npm run backend:retention:prune"), "Deployment docs include retention pruning dry run");
  assert(deployment.includes("npm run backend:smoke"), "Deployment docs include backend smoke testing");
  assert(deployment.includes("npm run android:wrapper:check"), "Deployment docs include Android wrapper source validation");
  assert(deployment.includes("npm run android:data:check"), "Deployment docs include Android local data source validation");
  assert(deployment.includes("npm run android:di:check"), "Deployment docs include Android DI source validation");
  assert(deployment.includes("npm run store:check"), "Deployment docs include store readiness validation");
  assert(deployment.includes("npm run android:device:qa -- --print-plan"), "Deployment docs include Android device QA plan validation");

  assert(dataRetention.includes("dry-run by default"), "Data retention docs state pruning is dry-run by default");
  assert(dataRetention.includes("SUPPORT_EVENT_RETENTION_DAYS"), "Data retention docs list support-event retention env");
  assert(dataRetention.includes("BACKUP_VERSIONS_PER_PROFILE"), "Data retention docs list backup retention env");

  assert(deploymentSmoke.includes("/health/ready"), "Deployment smoke docs include readiness checks");
  assert(deploymentSmoke.includes("--require-database"), "Deployment smoke docs include hosted database requirement mode");
  assert(smokeScript.includes("/feedback/beta-interest"), "Backend smoke script checks public beta-interest validation");
  assert(smokeScript.includes("x-request-id"), "Backend smoke script checks request ID headers");

  assert(androidDi.includes("npm run android:di:check"), "Android DI docs include the source check command");
  assert(androidDi.includes("Koin"), "Android DI docs identify Koin as the DI framework");
  assert(androidDi.includes("ChatModCoreModule"), "Android DI docs describe the Koin module graph");
  assert(androidDi.includes("Manual QA Still Required"), "Android DI docs keep runtime QA separate from source validation");

  const androidLocalData = readText("docs/ANDROID_LOCAL_DATA.md");
  assert(androidLocalData.includes("npm run android:data:check"), "Android local data docs include the source check command");
  assert(androidLocalData.includes("Room"), "Android local data docs include Room");
  assert(androidLocalData.includes("DataStore"), "Android local data docs include DataStore");
  assert(androidLocalData.includes("Manual QA Still Required"), "Android local data docs keep device QA separate from source validation");

  assert(androidUx.includes("npm run android:ux:check"), "Android UX docs include the source check command");
  assert(androidUx.includes("Screen reader"), "Android UX docs include screen reader requirements");
  assert(androidUx.includes("48dp"), "Android UX docs include touch-target requirements");
  assert(androidUx.includes("loading, empty, error, offline, reconnecting, and success"), "Android UX docs include production state coverage");
  assert(androidUx.includes("Manual QA Still Required"), "Android UX docs keep device QA separate from source validation");

  assert(androidDeviceQa.includes("npm run android:device:qa -- --print-plan"), "Android device QA docs include the print-plan command");
  assert(androidDeviceQa.includes("npm run android:device:qa -- --install --launch"), "Android device QA docs include the install and launch command");
  assert(androidDeviceQa.includes("--capture-evidence --clear-logcat"), "Android device QA docs include screenshot/logcat evidence capture");
  assert(androidDeviceQa.includes("android/app/build/qa-evidence"), "Android device QA docs name the evidence output directory");
  assert(androidDeviceQa.includes("Manual QA Still Required"), "Android device QA docs keep manual proof gates explicit");
  assert(androidDeviceQa.includes("No paid mobile testing service is required for beta"), "Android device QA docs preserve free tooling posture");
  assert(androidReleaseSigning.includes("CHATMOD_RELEASE_STORE_FILE"), "Android release signing docs document keystore file env");
  assert(androidReleaseSigning.includes("verifyReleaseSigningConfigured"), "Android release signing docs include signing verification task");
  assert(androidReleaseSigning.includes("assembleClosedBetaRelease"), "Android release signing docs include closed-beta release artifact command");
  assert(androidReleaseSigning.includes("apksigner verify"), "Android release signing docs include APK signature verification");
  assert(androidReleaseSigning.includes("not the final Play upload key"), "Android release signing docs keep local proof key separate from Play upload key");

  assert(aiModeration.includes("local-heuristic"), "AI moderation docs name the free local provider");
  assert(aiModeration.includes("Every suggestion requires manual approval"), "AI moderation docs require manual approval");
  assert(aiModeration.includes("Creator entitlement gates"), "AI moderation docs document Creator entitlement gating");
  assert(aiModeration.includes("confidence threshold"), "AI moderation docs document confidence thresholds");
  assert(aiModeration.includes("does not require OpenAI, Gemini, hosted inference"), "AI moderation docs preserve no-paid-model beta posture");

  assert(obsOverlay.includes("OBS browser-source overlay"), "OBS overlay docs describe the browser-source overlay");
  assert(obsOverlay.includes("stored only as a SHA-256 hash"), "OBS overlay docs document hashed public tokens");
  assert(obsOverlay.includes("Recent chat text is off by default"), "OBS overlay docs document privacy-default hidden chat text");
  assert(obsOverlay.includes("Android Settings tab uses these routes"), "OBS overlay docs document Android Settings controls");
  assert(obsOverlay.includes("No paid streaming overlay provider"), "OBS overlay docs preserve no-paid-overlay-service posture");

  assert(teamAccess.includes("Team Moderator Access"), "Team access docs describe the feature");
  assert(teamAccess.includes("teamSeats"), "Team access docs document the entitlement");
  assert(teamAccess.includes("stored only as SHA-256 hashes") || teamAccess.includes("stored only as a SHA-256 hash"), "Team access docs document hashed invite codes");
  assert(teamAccess.includes("Android Settings tab"), "Team access docs document Android Settings controls");
  assert(teamAccess.includes("No paid workspace"), "Team access docs preserve no-paid-workspace posture");

  assert(webDashboard.includes("Web Dashboard"), "Web dashboard docs describe the feature");
  assert(webDashboard.includes("Cloudflare Pages Free"), "Web dashboard docs preserve static free hosting posture");
  assert(webDashboard.includes("ADMIN_API_KEY"), "Web dashboard docs document admin route gating");
  assert(webDashboard.includes("x-admin-api-key"), "Web dashboard docs document the admin API key header");
  assert(webDashboard.includes("Does not store the key"), "Web dashboard docs document runtime-only admin secret handling");
  assert(webDashboard.includes("Keyboard And Accessibility"), "Web dashboard docs document keyboard accessibility");
  assert(webDashboard.includes("npm run launch-site:check"), "Web dashboard docs include the source gate");
  assert(tutorialVideo.includes("chatmod-mobile-promo/renders/chatmod-mobile-tutorial.mp4"), "Tutorial video docs name the rendered MP4");
  assert(tutorialVideo.includes("HyperFrames"), "Tutorial video docs document HyperFrames source");
  assert(tutorialVideo.includes("ffmpeg-static"), "Tutorial video docs document free local FFmpeg tooling");
  assert(tutorialVideo.includes("not the OAuth review demo video"), "Tutorial video docs keep OAuth review demo separate");
  assert(externalEvidence.includes("npm run release:evidence:check -- --print-required"), "External evidence docs include the print-required command");
  assert(externalEvidence.includes("--require-complete"), "External evidence docs document complete evidence validation");
  assert(externalEvidence.includes("release-evidence/"), "External evidence docs use the ignored local evidence folder");
  assert(externalEvidence.includes("Do not commit"), "External evidence docs include secret-safe handling rules");
  for (const gateId of [
    "backend-free-tier-deployment",
    "google-youtube-oauth-live",
    "android-device-qa",
    "play-console-store-policy",
    "creator-beta-acceptance"
  ]) {
    assert(evidenceTemplate.includes(gateId), `Evidence template includes ${gateId}`);
  }

  assert(envPreflight.includes("NODE_ENV=production"), "Production env preflight docs require NODE_ENV=production");
  assert(envPreflight.includes("Google OAuth"), "Production env preflight docs include Google OAuth checks");
  assert(envPreflight.includes("Google Play"), "Production env preflight docs include Google Play checks");
  assert(envPreflightSource.includes("checkGoogleOAuthEnv"), "Production env preflight source checks Google OAuth config");
  assert(envPreflightSource.includes("checkGooglePlayEnv"), "Production env preflight source checks Google Play config");
  assert(envPreflightSource.includes("parseRetentionCliOptions"), "Production env preflight source validates retention env");

  assert(freeTier.includes("Render Free web service"), "Free-tier docs choose Render Free for backend beta");
  assert(freeTier.includes("Neon Free"), "Free-tier docs choose Neon Free for durable Postgres");
  assert(freeTier.includes("Cloudflare Pages Free"), "Free-tier docs choose Cloudflare Pages Free for static site");
  assert(freeTier.includes("Date checked: 2026-06-11"), "Free-tier docs record a current provider review date");
  assert(freeTier.includes("15 idle minutes"), "Free-tier docs account for Render Free idle spin-down");
  assert(freeTier.includes("750 free instance hours"), "Free-tier docs account for Render Free monthly instance hours");
  assert(freeTier.includes("100 CU-hours monthly per project"), "Free-tier docs account for Neon Free compute limits");
  assert(freeTier.includes("0.5 GB storage per project"), "Free-tier docs account for Neon Free storage limits");
  assert(freeTier.includes("500 builds/month"), "Free-tier docs account for Cloudflare Pages Free build limits");
  assert(freeTier.includes("unlimited static requests"), "Free-tier docs account for Cloudflare Pages static request posture");
  assert(freeTier.includes("500K monthly commands"), "Free-tier docs account for Upstash Redis Free command limits");
  assert(freeTier.includes("Use Neon Free, not Render Free Postgres"), "Free-tier docs reject expiring Render Free Postgres for source-of-truth data");
  assert(freeTier.includes("Render Free Key Value is in-memory only"), "Free-tier docs reject Render Key Value for durable state");
  assert(freeTier.includes("Google Play Console requires a one-time registration fee"), "Free-tier docs call out unavoidable Play Console cost");
  assert(freeTier.includes("Lifetime purchase is not offered for MVP"), "Free-tier docs keep Lifetime out of MVP billing");
  assert(freeTier.includes("Retention pruning"), "Free-tier docs include retention pruning before paid storage");
  assert(freeTier.includes("OBS/browser overlays use the existing backend"), "Free-tier docs include no-paid OBS overlay posture");
  assert(freeTier.includes("Team moderator access uses the existing backend"), "Free-tier docs include no-paid team access posture");
  assert(freeTier.includes("Admin web dashboard uses the existing launch-site static host"), "Free-tier docs include no-paid web dashboard posture");

  assert(playListing.includes("ChatMod Mobile"), "Play listing draft names ChatMod Mobile");
  assert(playListing.includes("docs/PLAY_DATA_SAFETY.md"), "Play listing draft references the Data Safety worksheet");
  assert(playListing.includes("Lifetime purchase is not offered"), "Play listing draft avoids Lifetime marketing copy");
  assert(playListing.includes("https://support.google.com/googleplay/android-developer/answer/9859152"), "Play listing cites Google Play field limits");
  assert(playListing.includes("https://support.google.com/googleplay/android-developer/answer/10787469"), "Play listing cites Google Play Data safety guidance");
  assert(playDataSafety.includes("Data safety form guidance"), "Play Data Safety worksheet includes official form guidance");
  assert(playDataSafety.includes("Android Manifest Permission Inventory"), "Play Data Safety worksheet includes manifest permission inventory");
  assert(playDataSafety.includes("android.permission.INTERNET"), "Play Data Safety worksheet documents Android permissions");
  assert(playDataSafety.includes("not sold"), "Play Data Safety worksheet documents data is not sold");
  assert(billingDocs.includes("Lifetime purchase is not offered"), "Billing docs document no Lifetime SKU for MVP");
  assert(pricingDocs.includes("chatmod_lifetime"), "Pricing docs block accidental Lifetime SKU creation");
  assert(!playBillingManager.includes("chatmod_lifetime"), "Android billing source does not query a Lifetime SKU");
  assert(!playBillingManager.includes("BillingClient.ProductType.INAPP"), "Android billing source does not query one-time products");

  assert(release.includes("npm run production:check"), "Release checklist includes the production readiness command");
  assert(release.includes("Release signing source wiring uses external env vars or Gradle properties"), "Release checklist captures external signing source wiring");
  assert(release.includes("npm run backend:env:check"), "Release checklist includes production env preflight gate");
  assert(release.includes("npm run backend:retention:prune"), "Release checklist includes retention dry-run gate");
  assert(release.includes("npm run backend:smoke"), "Release checklist includes deployment smoke gate");
  assert(release.includes("npm run android:wrapper:check"), "Release checklist includes Android wrapper source gate");
  assert(release.includes("npm run android:data:check"), "Release checklist includes Android local data source gate");
  assert(release.includes("npm run android:di:check"), "Release checklist includes Android DI source gate");
  assert(release.includes("npm run android:ux:check"), "Release checklist includes Android UX source gate");
  assert(release.includes("npm run android:device:qa -- --print-plan"), "Release checklist includes Android device QA runner source gate");
  assert(release.includes("Android device QA evidence capture is wired"), "Release checklist captures Android QA evidence capture source wiring");
  assert(release.includes("External release evidence manifest and validator are wired"), "Release checklist captures external evidence manifest source wiring");
  assert(release.includes("Signed closed-beta release artifact produced"), "Release checklist captures signed release artifact proof");
  assert(release.includes("npm run store:check"), "Release checklist includes store readiness gate");
  assert(release.includes("Backend login/session integration test"), "Release checklist captures login flow integration proof");
  assert(release.includes("Lifetime purchase is explicitly not offered"), "Release checklist captures Lifetime purchase decision");
  assert(release.includes("Android Settings controls"), "Release checklist includes Android OBS overlay controls");
  assert(release.includes("OBS browser source verified against the deployed backend URL"), "Release checklist keeps deployed OBS verification open");
  assert(release.includes("Team moderator access source is wired"), "Release checklist captures team access source wiring");
  assert(release.includes("Team invite/redeem/revoke flow verified"), "Release checklist keeps two-device team QA open");
  assert(release.includes("Static admin web dashboard source is wired"), "Release checklist captures web dashboard source wiring");
  assert(release.includes("Static admin web dashboard verified against deployed backend"), "Release checklist keeps deployed web dashboard verification open");
  assert(release.includes("Tutorial video source/render artifact is prepared"), "Release checklist captures tutorial video source wiring");
  assert(ci.includes("npm run production:check"), "CI runs the production readiness command");
  assert(ci.includes("npm run promo-video:check"), "CI runs the promo video source check");
  assert(ci.includes("npm run release:evidence:check"), "CI runs the release evidence source check");
  assert(ci.includes("npm run android:wrapper:check"), "CI runs the Android wrapper source check");
  assert(ci.includes("npm run android:data:check"), "CI runs the Android local data source check");
  assert(ci.includes("npm run android:di:check"), "CI runs the Android DI source check");
  assert(ci.includes("npm run android:ux:check"), "CI runs the Android UX source check");
  assert(ci.includes("npm run android:device:qa -- --print-plan"), "CI prints the Android device QA plan without requiring hardware");
  assert(ci.includes("npm run android:release:check"), "CI runs Android release signing source check");
  assert(ci.includes("npm run launch-site:check"), "CI runs the launch-site check");
  assert(ci.includes("npm run store:check"), "CI runs the store readiness check");
  assert(storeCheck.includes("Google Play field limits"), "Store readiness checker verifies Play listing source limits");
  assert(storeCheck.includes("extractAndroidPermissions"), "Store readiness checker compares Data Safety docs with Android manifest permissions");
  assert(promoVideoCheck.includes("chatmod-mobile-tutorial.mp4"), "Promo video checker verifies the tutorial MP4 artifact");
  assert(promoVideoCheck.includes("ffmpeg-static"), "Promo video checker verifies local FFmpeg tooling");
  assert(releaseEvidenceCheck.includes("backend-free-tier-deployment"), "Release evidence checker verifies backend deployment evidence gate");
  assert(releaseEvidenceCheck.includes("google-youtube-oauth-live"), "Release evidence checker verifies Google/YouTube evidence gate");
  assert(releaseEvidenceCheck.includes("android-device-qa"), "Release evidence checker verifies Android device QA evidence gate");
  assert(releaseEvidenceCheck.includes("play-console-store-policy"), "Release evidence checker verifies Play Console evidence gate");
  assert(releaseEvidenceCheck.includes("creator-beta-acceptance"), "Release evidence checker verifies creator beta acceptance gate");
  assert(releaseEvidenceCheck.includes("forbiddenPatterns"), "Release evidence checker guards against committed secrets");
  assert(releaseEvidenceCheck.includes("--require-complete"), "Release evidence checker supports complete external evidence validation");
  assert(ci.includes("Smoke built backend"), "CI smoke-tests the compiled backend");
  assert(ci.includes("npm run backend:smoke -- --base-url=http://127.0.0.1:4115 --allow-missing-database"), "CI runs backend smoke against compiled backend");
  assert(ci.includes("Smoke backend Docker image"), "CI smoke-tests the backend Docker image");
  assert(ci.includes("npm run backend:smoke -- --base-url=http://127.0.0.1:4116 --allow-missing-database"), "CI runs backend smoke against Docker image");
  assert(ci.includes("android:"), "CI defines an Android build job");
  assert(ci.includes("actions/setup-java@v5"), "Android CI configures JDK 17 with setup-java");
  assert(ci.includes("android-actions/setup-android@v3"), "Android CI configures the Android SDK");
  assert(ci.includes("gradle/actions/setup-gradle@v6"), "Android CI configures Gradle from a free GitHub Actions runner");
  assert(ci.includes("chmod +x ./gradlew"), "Android CI makes the checked-in Gradle wrapper executable on Linux runners");
  assert(ci.includes("./gradlew testInternalDebugUnitTest assembleClosedBetaDebug"), "Android CI uses the checked-in Gradle wrapper");
  assert(androidWrapperCheck.includes("wrapperJarSha256"), "Android wrapper checker verifies the wrapper jar hash");
  assert(androidWrapperCheck.includes("distributionSha256Sum"), "Android wrapper checker verifies the distribution checksum");
  const androidLocalDataCheck = readText("scripts/android-local-data-check.mjs");
  assert(androidLocalDataCheck.includes("fallbackToDestructiveMigration"), "Android local data checker guards against destructive Room migration fallback");
  assert(androidLocalDataCheck.includes("preferencesDataStore"), "Android local data checker verifies DataStore settings source");
  assert(androidLocalDataCheck.includes("PendingSyncDrainWorker"), "Android local data checker verifies pending sync recovery source");
  assert(androidDiCheck.includes("koin-android"), "Android DI checker verifies the Koin Android dependency");
  assert(androidDiCheck.includes("ChatModCoreModule"), "Android DI checker verifies the Koin module graph");
  assert(androidDiCheck.includes("MainActivity no longer reaches into ChatModApplication"), "Android DI checker verifies Activity-level injection");
  assert(androidUxCheck.includes("minimumTouchTarget"), "Android UX checker verifies minimum touch-target source");
  assert(androidUxCheck.includes("stateDescription"), "Android UX checker verifies screen-reader state descriptions");
  assert(androidUxCheck.includes("HighContrastLightColors"), "Android UX checker verifies high-contrast theme source");
  assert(androidUxCheck.includes("checkDashboardStateCoverage"), "Android UX checker verifies loading, empty, error, offline, reconnecting, and success states");
  assert(!chatAbuseTracker.includes("peekFirst().seenAtMillis"), "Android abuse tracker avoids nullable peekFirst release warnings");
  assert(chatAbuseTracker.includes("peekFirst()?.seenAtMillis"), "Android abuse tracker trims queues with null-safe peekFirst calls");
  assert(backendAppTest.includes("completes the backend login flow with a device-session bearer token"), "Backend tests cover the login/session integration flow");
  assert(backendAppTest.includes("/accounts/device-session"), "Backend login flow test issues a device session");
  assert(backendAppTest.includes("/youtube/connect-url"), "Backend login flow test covers OAuth connect-url readiness copy");
  assert(moderationRoutes.includes("/suggestions/evaluate"), "Backend exposes moderation suggestion evaluation route");
  assert(moderationRoutes.includes("AI_SUGGESTIONS_REQUIRED"), "Backend gates moderation suggestions behind aiSuggestions entitlement");
  assert(moderationRoutes.includes("AI_SUGGESTION_LIMIT_REACHED"), "Backend returns a public AI suggestion daily limit error");
  assert(moderationRoutes.includes("retry-after"), "Backend returns Retry-After for AI suggestion daily limits");
  assert(moderationSuggestions.includes("manualApprovalRequired: true"), "Moderation suggestion engine requires manual approval");
  assert(moderationSuggestions.includes("repeated_question"), "Moderation suggestion engine detects repeated questions");
  assert(moderationSuggestions.includes("toxicity"), "Moderation suggestion engine classifies toxicity risk");
  assert(readText("backend/src/modules/moderation/aiSuggestionUsageStore.ts").includes("usageCounter"), "Backend has a Prisma-backed AI suggestion usage counter");
  assert(readText("backend/src/modules/streamSessions/streamChatSummary.ts").includes("suggestedFollowUps"), "Backend summarizes after-stream chat logs with follow-up suggestions");
  assert(readText("backend/src/modules/faq/faqReplyEngine.ts").includes("creator FAQ"), "Backend suggests FAQ replies from creator-provided entries");
  assert(readText("backend/src/modules/overlays/overlayConfigStore.ts").includes("hashPublicToken"), "Backend stores OBS overlay public tokens as hashes");
  assert(readText("backend/src/modules/overlays/overlays.routes.ts").includes("renderOverlayHtml"), "Backend renders OBS overlay HTML");
  assert(readText("backend/src/modules/overlays/overlays.routes.ts").includes("showRecentChat"), "Backend keeps OBS overlay chat text behind an explicit setting");
  assert(readText("backend/src/modules/team/teamAccessStore.ts").includes("hashInviteCode"), "Backend stores team invite codes as hashes");
  assert(readText("backend/src/modules/team/team.routes.ts").includes("teamSeats"), "Backend gates team invite seats by entitlement");
  assert(androidApiClient.includes("upsertOverlayConfig"), "Android API client exposes OBS overlay configuration");
  assert(androidDashboard.includes("OBS overlay"), "Android dashboard exposes OBS overlay Settings controls");
  assert(androidApiClient.includes("createTeamInvite"), "Android API client exposes team invite creation");
  assert(androidApiClient.includes("redeemTeamInvite"), "Android API client exposes team invite redemption");
  assert(androidApiClient.includes("revokeTeamMember"), "Android API client exposes team member revocation");
  assert(androidDashboard.includes("Team access"), "Android dashboard exposes team access Settings controls");
  assert(androidApiClient.includes("evaluateModerationSuggestion"), "Android API client exposes moderation suggestions");
  assert(androidApiClient.includes("suggestFaqReply"), "Android API client exposes FAQ reply suggestions");
  assert(androidApiClient.includes("streamChatSummary"), "Android API client exposes after-stream chat summaries");
  assert(androidApiClient.includes("ModerationSuggestionUsage"), "Android API client parses moderation suggestion usage");
  assert(androidDashboard.includes("Icons.AutoMirrored.Filled.Rule"), "Android dashboard uses AutoMirrored Rule icons for release-safe directional icons");
  assert(androidDashboard.includes("Icons.AutoMirrored.Filled.Send"), "Android dashboard uses AutoMirrored Send icons for release-safe directional icons");
  assert(!androidDashboard.includes("Icons.Default.Rule"), "Android dashboard avoids deprecated default Rule icon");
  assert(!androidDashboard.includes("Icons.Default.Send"), "Android dashboard avoids deprecated default Send icon");
  assert(androidDashboard.includes("Review assistant"), "Android dashboard renders review assistant suggestions");
  assert(androidDashboard.includes("FAQ replies"), "Android dashboard renders creator FAQ reply controls");
  assert(androidDashboard.includes("FAQ reply"), "Android dashboard renders queue FAQ reply suggestions");
  assert(androidDashboard.includes("aiSuggestionDailyLimit"), "Android dashboard displays the review-assistant daily limit");
  assert(androidDashboard.includes("After-stream summary"), "Android dashboard renders after-stream chat summaries");
  assert(androidDashboard.includes("Manual approval"), "Android dashboard shows manual approval on suggestions");
  assert(webAdminHtml.includes('data-admin-dashboard'), "Static web dashboard declares an admin dashboard root");
  assert(webAdminHtml.includes('class="skip-link"'), "Static web dashboard includes a keyboard skip link");
  assert(webAdminHtml.includes("No checked-in admin key"), "Static web dashboard documents runtime-only admin key handling");
  assert(webAdminScript.includes("/admin/support/users"), "Static web dashboard loads admin support snapshots");
  assert(webAdminScript.includes("/admin/support/entitlements/manual-adjust"), "Static web dashboard supports entitlement adjustment");
  assert(webAdminScript.includes("/admin/support/tickets/metadata"), "Static web dashboard supports ticket metadata");
  assert(webAdminScript.includes('credentials: "omit"'), "Static web dashboard avoids ambient credentials");
  assert(!webAdminScript.includes("localStorage"), "Static web dashboard avoids durable local storage for admin data");
  assert(androidDeviceQaCheck.includes("--print-plan"), "Android device QA runner has a CI-safe print-plan mode");
  assert(androidDeviceQaCheck.includes("adb"), "Android device QA runner uses ADB for local device verification");
  assert(androidDeviceQaCheck.includes("--install"), "Android device QA runner supports APK install");
  assert(androidDeviceQaCheck.includes("--launch"), "Android device QA runner supports launcher smoke testing");
  assert(androidDeviceQaCheck.includes("--capture-evidence"), "Android device QA runner supports evidence capture");
  assert(androidDeviceQaCheck.includes("exec-out"), "Android device QA runner captures screenshots with adb exec-out");
  assert(androidDeviceQaCheck.includes("logcat"), "Android device QA runner captures logcat evidence");
  assert(androidDeviceQaCheck.includes("qa-evidence"), "Android device QA runner writes timestamped QA evidence folders");
  assert(androidReleaseCheck.includes("--require-artifact"), "Android release artifact checker can require a local signed APK");
  assert(androidReleaseCheck.includes("apksigner"), "Android release artifact checker uses apksigner");
  assert(androidReleaseCheck.includes("closedBetaRelease"), "Android release artifact checker verifies the closedBetaRelease variant");
  assert(androidReleaseCheck.includes("APK Signature Scheme v2"), "Android release artifact checker verifies APK Signature Scheme v2");
  assert(ci.includes("testInternalDebugUnitTest"), "Android CI runs local unit tests");
  assert(ci.includes("assembleClosedBetaDebug"), "Android CI builds an installable closed beta debug APK");
  assert(ci.includes("chatmodUseDemoApi=true"), "Android CI uses demo API mode instead of external services");
}

function checkLaunchSite() {
  const index = readText("launch-site/index.html");
  const admin = readText("launch-site/admin.html");
  const script = readText("launch-site/script.js");
  const adminScript = readText("launch-site/admin-dashboard.js");
  const launchReadme = readText("launch-site/README.md");
  const privacy = readText("launch-site/privacy.html");
  const launchSiteCheck = readText("scripts/launch-site-check.mjs");

  assert(index.includes('data-beta-api="/feedback/beta-interest"'), "Launch site beta form points to backend beta-interest endpoint");
  assert(index.includes("role=\"status\""), "Launch site beta form exposes an accessible status region");
  assert(script.includes("fetch(betaInterestEndpoint()"), "Launch site beta form submits with fetch");
  assert(script.includes("window.CHATMOD_BETA_API_URL"), "Launch site supports deploy-time backend URL override");
  assert(!script.includes("Saved locally for this static preview"), "Launch site no longer claims beta interest is saved locally");
  assert(admin.includes("Support dashboard"), "Launch site includes the static admin dashboard");
  assert(admin.includes("Cloudflare Pages Free-ready"), "Admin dashboard preserves static free hosting posture");
  assert(adminScript.includes("/admin/support/devices/"), "Admin dashboard can load device and beta-interest snapshots");
  assert(adminScript.includes('"x-admin-api-key"'), "Admin dashboard sends the admin API key header");
  assert(!adminScript.includes("localStorage"), "Admin dashboard avoids durable browser storage");
  assert(launchReadme.includes("POST /feedback/beta-interest"), "Launch site README documents beta-interest backend wiring");
  assert(launchReadme.includes("admin.html"), "Launch site README documents the admin dashboard");
  assert(launchReadme.includes("CORS_ORIGIN"), "Launch site README documents backend CORS setup");
  assert(privacy.includes("Beta-interest email"), "Launch site privacy summary discloses beta-interest data");
  assert(launchSiteCheck.includes("data-beta-api=\"/feedback/beta-interest\""), "Launch-site checker verifies beta endpoint wiring");
  assert(launchSiteCheck.includes("admin-dashboard.js"), "Launch-site checker verifies admin dashboard source");
  assert(launchSiteCheck.includes("Launch policy draft"), "Launch-site checker rejects draft policy labels");
  assert(launchSiteCheck.includes("role=\"status\""), "Launch-site checker verifies accessible beta status");
}

function printResults() {
  for (const message of passes) {
    console.log(`PASS ${message}`);
  }
  for (const message of warnings) {
    console.warn(`WARN ${message}`);
  }
  for (const message of failures) {
    console.error(`FAIL ${message}`);
  }

  console.log(`\nProduction readiness source check: ${passes.length} passed, ${warnings.length} warning(s), ${failures.length} failure(s).`);
  if (failures.length > 0) {
    process.exit(1);
  }
}

checkRequiredFiles();
checkRootScripts();
checkRenderBlueprint();
checkEnvExample();
checkDockerAndIgnoreFiles();
checkAndroidBuildSource();
checkOpenApiContract();
checkPrismaMigrations();
checkDocsAndCi();
checkLaunchSite();
printResults();
