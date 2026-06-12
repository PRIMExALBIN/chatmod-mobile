import { ZodError } from "zod";
import { parseRetentionCliOptions } from "../modules/maintenance/retentionPolicy.js";
import { loadEnv, localDevelopmentJwtSecret } from "./env.js";

export interface ProductionEnvPreflightOptions {
  requireGoogleOAuth: boolean;
  requireGooglePlay: boolean;
  allowLocalOrigins: boolean;
}

export interface ProductionEnvPreflightResult {
  passed: boolean;
  checks: string[];
  warnings: string[];
  failures: string[];
}

const placeholderPattern = /(replace|change-me|example|localhost|local-development|not-a-32-byte-key|chatmod:chatmod)/i;

export const defaultProductionEnvPreflightOptions: ProductionEnvPreflightOptions = {
  requireGoogleOAuth: true,
  requireGooglePlay: true,
  allowLocalOrigins: false
};

export function checkProductionEnv(
  source: NodeJS.ProcessEnv,
  options: ProductionEnvPreflightOptions = defaultProductionEnvPreflightOptions
): ProductionEnvPreflightResult {
  const checks: string[] = [];
  const warnings: string[] = [];
  const failures: string[] = [];

  let env: ReturnType<typeof loadEnv> | null = null;
  try {
    env = loadEnv(source);
    checks.push("Environment schema parses.");
  } catch (error) {
    failures.push(...envSchemaFailures(error));
  }

  if (!env) {
    return result(checks, warnings, failures);
  }

  if (env.NODE_ENV === "production") {
    checks.push("NODE_ENV is production.");
  } else {
    failures.push("NODE_ENV must be production for production preflight.");
  }

  checkDatabaseUrl(env.DATABASE_URL, options, checks, failures);
  checkCorsOrigin(env.CORS_ORIGIN, options, checks, failures);
  checkSecret("JWT_SECRET", env.JWT_SECRET, { minLength: 32, disallowLocalSecret: true }, checks, failures);
  checkEncryptionKeys(env.SECRET_ENCRYPTION_KEYS, checks, failures);
  checkOptionalAdminKey(env.ADMIN_API_KEY, checks, warnings, failures);
  checkAndroidCompatibilityEnv(env, options, checks, failures);
  checkRetentionEnv(source, checks, failures);

  if (options.requireGoogleOAuth) {
    checkGoogleOAuthEnv(env, options, checks, failures);
  } else {
    warnings.push("Google OAuth env checks were skipped by flag.");
  }

  if (options.requireGooglePlay) {
    checkGooglePlayEnv(env, checks, failures);
  } else {
    warnings.push("Google Play env checks were skipped by flag.");
  }

  if (!env.REDIS_URL) {
    warnings.push("REDIS_URL is unset. This is acceptable for beta if rate limits stay process-local.");
  }

  return result(checks, warnings, failures);
}

function checkDatabaseUrl(
  value: string | undefined,
  options: ProductionEnvPreflightOptions,
  checks: string[],
  failures: string[]
): void {
  if (!value) {
    failures.push("DATABASE_URL is required.");
    return;
  }

  if (isPlaceholder(value)) {
    failures.push("DATABASE_URL still contains placeholder or local development content.");
    return;
  }

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    failures.push("DATABASE_URL must be a valid PostgreSQL URL.");
    return;
  }

  if (url.protocol !== "postgresql:" && url.protocol !== "postgres:") {
    failures.push("DATABASE_URL must use postgres:// or postgresql://.");
  }
  if (!url.username || !url.password) {
    failures.push("DATABASE_URL must include database username and password.");
  }
  if (!options.allowLocalOrigins && isLocalHost(url.hostname)) {
    failures.push("DATABASE_URL must not point at localhost in production preflight.");
  }
  if (!failures.some((failure) => failure.startsWith("DATABASE_URL"))) {
    checks.push("DATABASE_URL is a non-local PostgreSQL URL.");
  }
}

function checkCorsOrigin(
  value: string | undefined,
  options: ProductionEnvPreflightOptions,
  checks: string[],
  failures: string[]
): void {
  const url = parseHttpUrl("CORS_ORIGIN", value, options, failures);
  if (!url) {
    return;
  }
  if (url.pathname !== "/" || url.search || url.hash) {
    failures.push("CORS_ORIGIN must be an origin only, without path, query, or hash.");
    return;
  }

  checks.push("CORS_ORIGIN is a production origin.");
}

function checkSecret(
  name: string,
  value: string | undefined,
  input: { minLength: number; disallowLocalSecret?: boolean },
  checks: string[],
  failures: string[]
): void {
  if (!value) {
    failures.push(`${name} is required.`);
    return;
  }
  if (value.length < input.minLength) {
    failures.push(`${name} must be at least ${input.minLength} characters.`);
  }
  if (isPlaceholder(value) || (input.disallowLocalSecret && value === localDevelopmentJwtSecret)) {
    failures.push(`${name} contains placeholder or local development content.`);
  }

  if (!failures.some((failure) => failure.startsWith(name))) {
    checks.push(`${name} is present and non-placeholder.`);
  }
}

function checkEncryptionKeys(value: string | undefined, checks: string[], failures: string[]): void {
  if (!value) {
    failures.push("SECRET_ENCRYPTION_KEYS is required.");
    return;
  }
  if (isPlaceholder(value)) {
    failures.push("SECRET_ENCRYPTION_KEYS contains placeholder content.");
    return;
  }

  const entries = value.split(",").map((entry) => entry.trim()).filter(Boolean);
  if (entries.length === 0) {
    failures.push("SECRET_ENCRYPTION_KEYS must include at least one key.");
    return;
  }

  checks.push(`SECRET_ENCRYPTION_KEYS has ${entries.length} key(s).`);
}

function checkOptionalAdminKey(
  value: string | undefined,
  checks: string[],
  warnings: string[],
  failures: string[]
): void {
  if (!value) {
    warnings.push("ADMIN_API_KEY is unset; /admin support routes will stay disabled.");
    return;
  }

  checkSecret("ADMIN_API_KEY", value, { minLength: 32 }, checks, failures);
}

function checkAndroidCompatibilityEnv(
  env: ReturnType<typeof loadEnv>,
  options: ProductionEnvPreflightOptions,
  checks: string[],
  failures: string[]
): void {
  if (env.ANDROID_LATEST_VERSION_CODE < env.ANDROID_MIN_SUPPORTED_VERSION_CODE) {
    failures.push("ANDROID_LATEST_VERSION_CODE must be greater than or equal to ANDROID_MIN_SUPPORTED_VERSION_CODE.");
  } else {
    checks.push("Android version floor/latest values are ordered.");
  }

  if (env.ANDROID_UPDATE_URL) {
    const updateUrl = parseHttpUrl("ANDROID_UPDATE_URL", env.ANDROID_UPDATE_URL, options, failures);
    if (updateUrl && updateUrl.protocol === "https:") {
      checks.push("ANDROID_UPDATE_URL is HTTPS.");
    }
  }
}

function checkRetentionEnv(source: NodeJS.ProcessEnv, checks: string[], failures: string[]): void {
  try {
    const retention = parseRetentionCliOptions([], source);
    checks.push(
      `Retention windows parse: support=${retention.supportEventDays}d api=${retention.apiErrorDays}d stream=${retention.streamLogDays}d backups=${retention.backupVersionsPerProfile}.`
    );
  } catch (error) {
    failures.push(error instanceof Error ? error.message : "Retention environment values are invalid.");
  }
}

function checkGoogleOAuthEnv(
  env: ReturnType<typeof loadEnv>,
  options: ProductionEnvPreflightOptions,
  checks: string[],
  failures: string[]
): void {
  checkSecret("GOOGLE_OAUTH_CLIENT_ID", env.GOOGLE_OAUTH_CLIENT_ID, { minLength: 20 }, checks, failures);
  checkSecret("GOOGLE_OAUTH_CLIENT_SECRET", env.GOOGLE_OAUTH_CLIENT_SECRET, { minLength: 16 }, checks, failures);

  if (env.GOOGLE_OAUTH_CLIENT_ID && !env.GOOGLE_OAUTH_CLIENT_ID.endsWith(".apps.googleusercontent.com")) {
    failures.push("GOOGLE_OAUTH_CLIENT_ID should end with .apps.googleusercontent.com.");
  }

  const redirectUrl = parseHttpUrl("GOOGLE_OAUTH_REDIRECT_URI", env.GOOGLE_OAUTH_REDIRECT_URI, options, failures);
  if (redirectUrl) {
    if (redirectUrl.pathname !== "/youtube/oauth/callback") {
      failures.push("GOOGLE_OAUTH_REDIRECT_URI must end with /youtube/oauth/callback.");
    }
    if (!failures.some((failure) => failure.startsWith("GOOGLE_OAUTH_REDIRECT_URI"))) {
      checks.push("GOOGLE_OAUTH_REDIRECT_URI uses the backend OAuth callback path.");
    }
  }
}

function checkGooglePlayEnv(
  env: ReturnType<typeof loadEnv>,
  checks: string[],
  failures: string[]
): void {
  if (!env.GOOGLE_PLAY_PACKAGE_NAME) {
    failures.push("GOOGLE_PLAY_PACKAGE_NAME is required.");
  } else if (!/^com\.chatmod\.mobile(\.[a-z0-9_]+)?$/i.test(env.GOOGLE_PLAY_PACKAGE_NAME)) {
    failures.push("GOOGLE_PLAY_PACKAGE_NAME should use the ChatMod package namespace.");
  } else {
    checks.push("GOOGLE_PLAY_PACKAGE_NAME uses the ChatMod namespace.");
  }

  if (!env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64) {
    failures.push("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 is required.");
    return;
  }
  if (isPlaceholder(env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64)) {
    failures.push("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 contains placeholder content.");
    return;
  }

  let decoded: string;
  try {
    decoded = Buffer.from(env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64, "base64").toString("utf8");
  } catch {
    failures.push("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 must be base64-encoded JSON.");
    return;
  }

  let json: Record<string, unknown>;
  try {
    json = JSON.parse(decoded) as Record<string, unknown>;
  } catch {
    failures.push("GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 must decode to JSON.");
    return;
  }

  if (json.type !== "service_account") {
    failures.push("Google Play service account JSON must have type=service_account.");
  }
  if (typeof json.client_email !== "string" || !json.client_email.endsWith(".gserviceaccount.com")) {
    failures.push("Google Play service account JSON must include a service account client_email.");
  }
  if (typeof json.private_key !== "string" || !json.private_key.includes("BEGIN PRIVATE KEY")) {
    failures.push("Google Play service account JSON must include a private_key.");
  }
  if (typeof json.project_id !== "string" || json.project_id.length === 0) {
    failures.push("Google Play service account JSON must include project_id.");
  }

  if (!failures.some((failure) => failure.startsWith("Google Play service account"))) {
    checks.push("Google Play service account JSON shape is valid.");
  }
}

function parseHttpUrl(
  name: string,
  value: string | undefined,
  options: ProductionEnvPreflightOptions,
  failures: string[]
): URL | null {
  if (!value) {
    failures.push(`${name} is required.`);
    return null;
  }
  if (isPlaceholder(value)) {
    failures.push(`${name} contains placeholder or local development content.`);
    return null;
  }

  let url: URL;
  try {
    url = new URL(value);
  } catch {
    failures.push(`${name} must be a valid URL.`);
    return null;
  }

  if (url.protocol !== "https:" && !(options.allowLocalOrigins && url.protocol === "http:" && isLocalHost(url.hostname))) {
    failures.push(`${name} must use HTTPS.`);
  }
  if (!options.allowLocalOrigins && isLocalHost(url.hostname)) {
    failures.push(`${name} must not point at localhost in production preflight.`);
  }

  return url;
}

function envSchemaFailures(error: unknown): string[] {
  if (error instanceof ZodError) {
    return error.issues.map((issue) => `${issue.path.join(".") || "env"}: ${issue.message}`);
  }

  return [error instanceof Error ? error.message : "Environment schema did not parse."];
}

function result(checks: string[], warnings: string[], failures: string[]): ProductionEnvPreflightResult {
  return {
    passed: failures.length === 0,
    checks,
    warnings,
    failures
  };
}

function isPlaceholder(value: string): boolean {
  return placeholderPattern.test(value);
}

function isLocalHost(hostname: string): boolean {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1" || hostname.endsWith(".localhost");
}
