import { google } from "googleapis";
import { jwtVerify, SignJWT } from "jose";
import { env } from "../../config/env.js";
import type { AuthContext } from "../auth/sessionToken.js";

export const youtubeOAuthScopes = [
  "https://www.googleapis.com/auth/youtube.readonly",
  "https://www.googleapis.com/auth/youtube.force-ssl"
] as const;

export interface YouTubeOAuthTokens {
  accessToken: string | null;
  refreshToken: string | null;
  expiryDate: number | null;
  scope: string | null;
}

export function isYouTubeOAuthConfigured(): boolean {
  return Boolean(
    env.GOOGLE_OAUTH_CLIENT_ID &&
      env.GOOGLE_OAUTH_CLIENT_SECRET &&
      env.GOOGLE_OAUTH_REDIRECT_URI
  );
}

export function missingYouTubeOAuthEnv(): string[] {
  const missing: string[] = [];

  if (!env.GOOGLE_OAUTH_CLIENT_ID) {
    missing.push("GOOGLE_OAUTH_CLIENT_ID");
  }
  if (!env.GOOGLE_OAUTH_CLIENT_SECRET) {
    missing.push("GOOGLE_OAUTH_CLIENT_SECRET");
  }
  if (!env.GOOGLE_OAUTH_REDIRECT_URI) {
    missing.push("GOOGLE_OAUTH_REDIRECT_URI");
  }

  return missing;
}

export async function issueYouTubeOAuthState(auth: AuthContext): Promise<string> {
  return new SignJWT({
    tokenType: "youtube_oauth_state",
    deviceId: auth.deviceId,
    installId: auth.installId
  })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setIssuer(env.JWT_ISSUER)
    .setAudience(env.JWT_AUDIENCE)
    .setSubject(auth.subject)
    .setExpirationTime("10m")
    .sign(secretKey());
}

export async function verifyYouTubeOAuthState(state: string): Promise<AuthContext> {
  const verified = await jwtVerify(state, secretKey(), {
    issuer: env.JWT_ISSUER,
    audience: env.JWT_AUDIENCE
  });

  const { sub, tokenType, deviceId, installId } = verified.payload;
  if (
    tokenType !== "youtube_oauth_state" ||
    typeof sub !== "string" ||
    typeof deviceId !== "string" ||
    typeof installId !== "string"
  ) {
    throw new Error("Invalid YouTube OAuth state.");
  }

  return {
    subject: sub,
    deviceId,
    installId
  };
}

export async function buildYouTubeConnectUrl(auth: AuthContext): Promise<string> {
  const client = createOAuthClient();
  const state = await issueYouTubeOAuthState(auth);

  return client.generateAuthUrl({
    access_type: "offline",
    prompt: "consent",
    scope: [...youtubeOAuthScopes],
    state
  });
}

export async function exchangeYouTubeOAuthCode(code: string): Promise<YouTubeOAuthTokens> {
  const client = createOAuthClient();
  const response = await client.getToken(code);

  return {
    accessToken: response.tokens.access_token ?? null,
    refreshToken: response.tokens.refresh_token ?? null,
    expiryDate: response.tokens.expiry_date ?? null,
    scope: response.tokens.scope ?? null
  };
}

export function createOAuthClient() {
  return new google.auth.OAuth2(
    env.GOOGLE_OAUTH_CLIENT_ID,
    env.GOOGLE_OAUTH_CLIENT_SECRET,
    env.GOOGLE_OAUTH_REDIRECT_URI
  );
}

function secretKey(): Uint8Array {
  return new TextEncoder().encode(env.JWT_SECRET);
}
