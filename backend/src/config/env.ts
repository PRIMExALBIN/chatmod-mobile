import "dotenv/config";
import { z } from "zod";

export const localDevelopmentJwtSecret = "local-development-secret-change-me";

const envSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().min(1).max(65535).default(4100),
  HOST: z.string().default("0.0.0.0"),
  DATABASE_URL: z.string().optional(),
  REDIS_URL: z.string().optional(),
  CORS_ORIGIN: z.string().optional(),
  JWT_ISSUER: z.string().default("chatmod-mobile-local"),
  JWT_AUDIENCE: z.string().default("chatmod-mobile"),
  JWT_SECRET: z.string().min(24).default(localDevelopmentJwtSecret),
  SECRET_ENCRYPTION_KEYS: z.string().optional(),
  ADMIN_API_KEY: z.string().optional(),
  GOOGLE_OAUTH_CLIENT_ID: z.string().optional(),
  GOOGLE_OAUTH_CLIENT_SECRET: z.string().optional(),
  GOOGLE_OAUTH_REDIRECT_URI: z.string().url().optional(),
  GOOGLE_PLAY_PACKAGE_NAME: z.string().optional(),
  GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64: z.string().optional(),
  ANDROID_MIN_SUPPORTED_VERSION_CODE: z.coerce.number().int().min(1).default(1),
  ANDROID_MIN_SUPPORTED_VERSION_NAME: z.string().min(1).default("0.1.0"),
  ANDROID_LATEST_VERSION_CODE: z.coerce.number().int().min(1).default(1),
  ANDROID_LATEST_VERSION_NAME: z.string().min(1).default("0.1.0"),
  ANDROID_UPDATE_URL: z.string().url().optional()
}).superRefine((value, context) => {
  const keyValidation = validateSecretEncryptionKeys(value.SECRET_ENCRYPTION_KEYS);
  if (!keyValidation.valid) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["SECRET_ENCRYPTION_KEYS"],
      message: keyValidation.message
    });
  }

  if (value.NODE_ENV !== "production") {
    return;
  }

  if (value.ADMIN_API_KEY && value.ADMIN_API_KEY.length < 32) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["ADMIN_API_KEY"],
      message: "ADMIN_API_KEY must be at least 32 characters when configured."
    });
  }

  if (!value.DATABASE_URL) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["DATABASE_URL"],
      message: "DATABASE_URL is required in production."
    });
  }

  if (value.JWT_SECRET === localDevelopmentJwtSecret || value.JWT_SECRET.length < 32) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["JWT_SECRET"],
      message: "JWT_SECRET must be a production secret of at least 32 characters."
    });
  }

  if (!value.CORS_ORIGIN) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["CORS_ORIGIN"],
      message: "CORS_ORIGIN is required in production."
    });
  }

  if (!value.SECRET_ENCRYPTION_KEYS) {
    context.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["SECRET_ENCRYPTION_KEYS"],
      message: "SECRET_ENCRYPTION_KEYS is required in production."
    });
  }
});

export type AppEnv = z.infer<typeof envSchema>;

export function loadEnv(source: NodeJS.ProcessEnv = process.env): AppEnv {
  return envSchema.parse(source);
}

export const env = loadEnv();

function validateSecretEncryptionKeys(value: string | undefined): { valid: boolean; message: string } {
  if (!value) {
    return { valid: true, message: "" };
  }

  const entries = value.split(",").map((entry) => entry.trim()).filter(Boolean);
  if (entries.length === 0) {
    return { valid: false, message: "SECRET_ENCRYPTION_KEYS must include at least one key." };
  }

  const ids = new Set<string>();
  for (const entry of entries) {
    const [id, encodedKey, ...extra] = entry.split(":");
    if (!id || !encodedKey || extra.length > 0) {
      return { valid: false, message: "SECRET_ENCRYPTION_KEYS entries must use key-id:base64url-32-byte-key." };
    }
    if (!/^[a-zA-Z0-9_-]{1,32}$/.test(id)) {
      return { valid: false, message: "SECRET_ENCRYPTION_KEYS key ids must be 1-32 URL-safe characters." };
    }
    if (ids.has(id)) {
      return { valid: false, message: "SECRET_ENCRYPTION_KEYS key ids must be unique." };
    }
    ids.add(id);

    const key = Buffer.from(encodedKey, "base64url");
    if (key.length !== 32) {
      return { valid: false, message: "SECRET_ENCRYPTION_KEYS values must decode to 32 bytes." };
    }
  }

  return { valid: true, message: "" };
}
