import { jwtVerify, SignJWT } from "jose";
import { env, type AppEnv } from "../../config/env.js";

export interface DeviceSessionClaims {
  deviceId: string;
  installId: string;
}

export interface AuthContext extends DeviceSessionClaims {
  subject: string;
}

type JwtConfig = Pick<AppEnv, "JWT_AUDIENCE" | "JWT_ISSUER" | "JWT_SECRET">;

export async function issueSessionToken(
  claims: DeviceSessionClaims,
  config: JwtConfig = env
): Promise<string> {
  return new SignJWT({
    tokenType: "device_session",
    deviceId: claims.deviceId,
    installId: claims.installId
  })
    .setProtectedHeader({ alg: "HS256" })
    .setIssuedAt()
    .setIssuer(config.JWT_ISSUER)
    .setAudience(config.JWT_AUDIENCE)
    .setSubject(claims.deviceId)
    .setExpirationTime("1h")
    .sign(secretKey(config.JWT_SECRET));
}

export async function verifySessionToken(token: string, config: JwtConfig = env): Promise<AuthContext> {
  const verified = await jwtVerify(token, secretKey(config.JWT_SECRET), {
    issuer: config.JWT_ISSUER,
    audience: config.JWT_AUDIENCE
  });

  const { sub, deviceId, installId, tokenType } = verified.payload;

  if (
    tokenType !== "device_session" ||
    typeof sub !== "string" ||
    typeof deviceId !== "string" ||
    typeof installId !== "string"
  ) {
    throw new Error("Invalid session token.");
  }

  return {
    subject: sub,
    deviceId,
    installId
  };
}

function secretKey(secret: string): Uint8Array {
  return new TextEncoder().encode(secret);
}
