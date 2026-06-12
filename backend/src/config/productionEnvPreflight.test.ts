import { describe, expect, it } from "vitest";
import { checkProductionEnv } from "./productionEnvPreflight.js";

const encryptionKeys = "k202606:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE";
const playServiceAccount = Buffer.from(JSON.stringify({
  type: "service_account",
  project_id: "chatmod-prod",
  private_key_id: "key-id",
  private_key: "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n",
  client_email: "chatmod-play@chatmod-prod.iam.gserviceaccount.com",
  client_id: "1234567890"
})).toString("base64");

describe("production env preflight", () => {
  it("accepts a complete production-like environment", () => {
    const result = checkProductionEnv(validProductionEnv());

    expect(result.failures).toEqual([]);
    expect(result.passed).toBe(true);
    expect(result.checks).toEqual(expect.arrayContaining([
      "NODE_ENV is production.",
      "DATABASE_URL is a non-local PostgreSQL URL.",
      "CORS_ORIGIN is a production origin.",
      "GOOGLE_OAUTH_REDIRECT_URI uses the backend OAuth callback path.",
      "Google Play service account JSON shape is valid."
    ]));
  });

  it("rejects placeholder/local production values", () => {
    const result = checkProductionEnv({
      ...validProductionEnv(),
      DATABASE_URL: "postgresql://chatmod:chatmod@localhost:5432/chatmod",
      CORS_ORIGIN: "http://localhost:4100",
      JWT_SECRET: "replace-with-a-real-production-jwt-secret-value",
      SECRET_ENCRYPTION_KEYS: "replace:AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE"
    });

    expect(result.passed).toBe(false);
    expect(result.failures).toEqual(expect.arrayContaining([
      expect.stringContaining("DATABASE_URL"),
      expect.stringContaining("CORS_ORIGIN"),
      expect.stringContaining("JWT_SECRET"),
      expect.stringContaining("SECRET_ENCRYPTION_KEYS")
    ]));
  });

  it("can explicitly skip Google integrations for local prototype checks", () => {
    const result = checkProductionEnv({
      ...validProductionEnv(),
      GOOGLE_OAUTH_CLIENT_ID: undefined,
      GOOGLE_OAUTH_CLIENT_SECRET: undefined,
      GOOGLE_OAUTH_REDIRECT_URI: undefined,
      GOOGLE_PLAY_PACKAGE_NAME: undefined,
      GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64: undefined
    }, {
      requireGoogleOAuth: false,
      requireGooglePlay: false,
      allowLocalOrigins: false
    });

    expect(result.passed).toBe(true);
    expect(result.warnings).toEqual(expect.arrayContaining([
      "Google OAuth env checks were skipped by flag.",
      "Google Play env checks were skipped by flag."
    ]));
  });

  it("rejects malformed Google Play service account JSON", () => {
    const result = checkProductionEnv({
      ...validProductionEnv(),
      GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64: Buffer.from(JSON.stringify({
        type: "authorized_user"
      })).toString("base64")
    });

    expect(result.passed).toBe(false);
    expect(result.failures).toEqual(expect.arrayContaining([
      "Google Play service account JSON must have type=service_account.",
      "Google Play service account JSON must include a service account client_email.",
      "Google Play service account JSON must include a private_key.",
      "Google Play service account JSON must include project_id."
    ]));
  });
});

function validProductionEnv(): NodeJS.ProcessEnv {
  return {
    NODE_ENV: "production",
    PORT: "4100",
    HOST: "0.0.0.0",
    DATABASE_URL: "postgresql://chatmod_user:strong-password@db.neon.tech:5432/chatmod?sslmode=require",
    CORS_ORIGIN: "https://chatmod.app",
    JWT_ISSUER: "chatmod-mobile",
    JWT_AUDIENCE: "chatmod-mobile",
    JWT_SECRET: "production-jwt-signing-key-with-40-characters",
    SECRET_ENCRYPTION_KEYS: encryptionKeys,
    GOOGLE_OAUTH_CLIENT_ID: "1234567890-chatmod.apps.googleusercontent.com",
    GOOGLE_OAUTH_CLIENT_SECRET: "google-oauth-secret-123456",
    GOOGLE_OAUTH_REDIRECT_URI: "https://api.chatmod.app/youtube/oauth/callback",
    GOOGLE_PLAY_PACKAGE_NAME: "com.chatmod.mobile",
    GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64: playServiceAccount,
    ANDROID_MIN_SUPPORTED_VERSION_CODE: "1",
    ANDROID_MIN_SUPPORTED_VERSION_NAME: "0.1.0",
    ANDROID_LATEST_VERSION_CODE: "2",
    ANDROID_LATEST_VERSION_NAME: "0.1.1",
    ANDROID_UPDATE_URL: "https://chatmod.app/download",
    SUPPORT_EVENT_RETENTION_DAYS: "90",
    API_ERROR_RETENTION_DAYS: "30",
    STREAM_LOG_RETENTION_DAYS: "60",
    BACKUP_VERSIONS_PER_PROFILE: "10"
  };
}
